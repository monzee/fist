package ph.codeia.fist.mealy;

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

import ph.codeia.fist.moore.Mu;

public abstract class AsyncMealy<S, C extends Mi.Effects<S>> implements Mi.Runner<S, C> {
    private S state;
    private boolean isRunning;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService joiner = Executors.newSingleThreadExecutor();
    private final Queue<Future<Mi.Action<S, C>>> backlog = new ConcurrentLinkedQueue<>();
    private final Queue<AtomicReference<Future<?>>> pending = new ConcurrentLinkedQueue<>();

    public AsyncMealy(S state) {
        this.state = state;
    }

    protected abstract void runOnMainThread(Runnable proc);

    @Override
    public void start(C context) {
        if (isRunning) return;
        isRunning = true;
        WeakReference<C> weakContext = new WeakReference<>(context);
        for (Future<Mi.Action<S, C>> work : backlog) {
            join(work, weakContext);
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
    public void exec(C context, Mi.Action<S, C> action) {
        if (!isRunning) return;
        runOnMainThread(() -> action.apply(state, context).run(new Mi.Machine<S, C>() {
            @Override
            public void noop() {
            }

            @Override
            public void reenter() {
                context.onEnter(state);
            }

            @Override
            public void enter(S newState) {
                state = newState;
                context.onEnter(state);
            }

            @Override
            public void reduce(Mi.Action<S, C> action) {
                action.apply(state, context).run(this);
            }

            @Override
            public void reduce(Callable<Mi.Action<S, C>> thunk) {
                Future<Mi.Action<S, C>> work = worker.submit(thunk);
                backlog.add(work);
                join(work, new WeakReference<>(context));
            }

            @Override
            public void raise(Throwable e) {
                context.handle(e);
            }
        }));
    }

    @Override
    public <T> T inspect(Mu.Function<S, T> projection) {
        return projection.apply(state);
    }

    private void join(Future<Mi.Action<S, C>> work, WeakReference<C> weakContext) {
        AtomicReference<Future<?>> joinRef = new AtomicReference<>();
        joinRef.set(joiner.submit(() -> {
            try {
                Mi.Action<S, C> action = work.get(60, TimeUnit.SECONDS);
                backlog.remove(work);
                C context = weakContext.get();
                if (context != null) {
                    exec(context, action);
                }
            }
            catch (InterruptedException ignored) {
            }
            catch (ExecutionException | TimeoutException e) {
                backlog.remove(work);
                work.cancel(true);
                runOnMainThread(() -> {
                    C context = weakContext.get();
                    if (context != null) {
                        context.handle(e);
                    }
                    else {
                        throw new RuntimeException(e);
                    }
                });
            }
            finally {
                pending.remove(joinRef);
                joinRef.set(null);
            }
        }));
        pending.add(joinRef);
    }
}
