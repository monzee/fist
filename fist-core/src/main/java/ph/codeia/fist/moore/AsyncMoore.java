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

import ph.codeia.fist.Effects;
import ph.codeia.fist.Fn;
import ph.codeia.fist.Mu;

public abstract class AsyncMoore<S> implements Mu.Runner<S> {

    private S state;
    private boolean isRunning;
    private final ExecutorService joiner = Executors.newSingleThreadExecutor();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Queue<Future<Mu.Action<S>>> backlog = new ConcurrentLinkedQueue<>();
    private final Queue<AtomicReference<Future<?>>> pending = new ConcurrentLinkedQueue<>();

    public AsyncMoore(S state) {
        this.state = state;
    }

    protected abstract void runOnMainThread(Runnable proc);

    @Override
    public void start(Effects<S> effects) {
        if (isRunning) return;
        isRunning = true;
        WeakReference<Effects<S>> weakEffects = new WeakReference<>(effects);
        for (Future<Mu.Action<S>> work : backlog) {
            join(weakEffects, work);
        }
        effects.onEnter(state);
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
    public void exec(Effects<S> effects, Mu.Action<S> action) {
        if (!isRunning) return;
        runOnMainThread(() -> action.apply(state).run(new Mu.OnCommand<S>() {
            @Override
            public void noop() {
            }

            @Override
            public void enter(S newState) {
                state = newState;
                effects.onEnter(state);
            }

            @Override
            public void reenter() {
                effects.onEnter(state);
            }

            @Override
            public void forward(Mu.Action<S> action) {
                action.apply(state).run(this);
            }

            @Override
            public void async(Callable<Mu.Action<S>> thunk) {
                Future<Mu.Action<S>> work = worker.submit(thunk);
                backlog.add(work);
                join(new WeakReference<>(effects), work);
            }

            @Override
            public void raise(Throwable e) {
                effects.handle(e);
            }
        }));
    }

    @Override
    public <T> T project(Fn.Func<S, T> projection) {
        return projection.apply(state);
    }

    private void join(
            WeakReference<Effects<S>> weakEffects,
            Future<Mu.Action<S>> work
    ) {
        AtomicReference<Future<?>> joinRef = new AtomicReference<>();
        joinRef.set(joiner.submit(() -> {
            try {
                Mu.Action<S> action = work.get(60, TimeUnit.SECONDS);
                backlog.remove(work);
                Effects<S> effects = weakEffects.get();
                if (effects != null) {
                    exec(effects, action);
                }
            }
            catch (InterruptedException ignored) {
            }
            catch (ExecutionException e) {
                backlog.remove(work);
                handle(e.getCause(), weakEffects);
            }
            catch (TimeoutException e) {
                backlog.remove(work);
                work.cancel(true);
                handle(e, weakEffects);
            }
            finally {
                pending.remove(joinRef);
                joinRef.set(null);
            }
        }));
        pending.add(joinRef);
    }

    private void handle(Throwable e, WeakReference<Effects<S>> weakEffects) {
        runOnMainThread(() -> {
            Effects<S> effects = weakEffects.get();
            if (effects != null) {
                effects.handle(e);
            }
            else {
                throw new RuntimeException(e);
            }
        });
    }

}
