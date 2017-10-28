package ph.codeia.fist.moore;

/*
 * This file is a part of the fist project.
 */

import java.lang.ref.WeakReference;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AsyncRunner<S> implements Cmd.Runner<S> {

    private S state;
    private boolean isRunning;
    private final ExecutorService joiner = Executors.newSingleThreadExecutor();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Queue<Future<Cmd.Action<S>>> backlog = new ConcurrentLinkedQueue<>();
    private final Queue<AtomicReference<Future<?>>> pending = new ConcurrentLinkedQueue<>();

    public AsyncRunner(S state) {
        this.state = state;
    }

    protected abstract void runOnMainThread(Runnable proc);

    protected abstract void handle(Throwable e, Cmd.Context<S> context);

    @Override
    public void start(Cmd.Context<S> context) {
        if (isRunning) return;
        isRunning = true;
        WeakReference<Cmd.Context<S>> weakContext = new WeakReference<>(context);
        for (Future<Cmd.Action<S>> work : backlog) {
            join(weakContext, work);
        }
        context.onEnter(state);
    }

    @Override
    public void stop() {
        if (!isRunning) return;
        isRunning = false;
        for (AtomicReference<Future<?>> joinRef : pending) {
            Future<?> join = joinRef.get();
            if (join != null) {
                join.cancel(true);
            }
        }
    }

    @Override
    public void exec(Cmd.Context<S> context, Cmd.Action<S> action) {
        if (!isRunning) return;
        runOnMainThread(() -> action.apply(state).run(new Cmd.Processor<S>() {
            @Override
            public void noop() {
            }

            @Override
            public void enter(S newState) {
                state = newState;
                context.onEnter(state);
            }

            @Override
            public void reenter() {
                context.onEnter(state);
            }

            @Override
            public void reduce(Cmd.Action<S> action) {
                action.apply(state).run(this);
            }

            @Override
            public void reduce(Callable<Cmd.Action<S>> thunk) {
                Future<Cmd.Action<S>> work = worker.submit(thunk);
                backlog.add(work);
                join(new WeakReference<>(context), work);
            }

            @Override
            public void raise(Throwable e) {
                handle(e, context);
            }
        }));
    }

    private void join(
            WeakReference<Cmd.Context<S>> weakContext,
            Future<Cmd.Action<S>> work
    ) {
        AtomicReference<Future<?>> joinRef = new AtomicReference<>();
        joinRef.set(joiner.submit(() -> {
            try {
                Cmd.Action<S> action = work.get(60, TimeUnit.SECONDS);
                backlog.remove(work);
                Cmd.Context<S> adapter = weakContext.get();
                if (adapter != null) {
                    exec(adapter, action);
                }
            }
            catch (InterruptedException ignored) {
            }
            catch (ExecutionException | TimeoutException e) {
                backlog.remove(work);
                work.cancel(true);
                handle(e, weakContext.get());
            }
            finally {
                pending.remove(joinRef);
                joinRef.set(null);
            }
        }));
        pending.add(joinRef);
    }

}
