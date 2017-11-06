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

import ph.codeia.fist.Effects;
import ph.codeia.fist.Fn;

public abstract class AsyncMealy<S, E extends Effects<S>> implements Mi.Runner<S, E> {
    private S state;
    private boolean isRunning;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService joiner = Executors.newSingleThreadExecutor();
    private final Queue<Future<Mi.Action<S, E>>> backlog = new ConcurrentLinkedQueue<>();
    private final Queue<AtomicReference<Future<?>>> pending = new ConcurrentLinkedQueue<>();

    public AsyncMealy(S state) {
        this.state = state;
    }

    protected abstract void runOnMainThread(Runnable proc);

    @Override
    public void start(E effects) {
        if (isRunning) return;
        isRunning = true;
        WeakReference<E> weakEffects = new WeakReference<>(effects);
        for (Future<Mi.Action<S, E>> work : backlog) {
            join(work, weakEffects);
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
    public void exec(E effects, Mi.Action<S, E> action) {
        if (!isRunning) return;
        runOnMainThread(() -> action.apply(state, effects).run(new Mi.Fst<S, E>() {
            @Override
            public void noop() {
            }

            @Override
            public void reenter() {
                effects.onEnter(state);
            }

            @Override
            public void enter(S newState) {
                state = newState;
                effects.onEnter(state);
            }

            @Override
            public void forward(Mi.Action<S, E> action) {
                action.apply(state, effects).run(this);
            }

            @Override
            public void async(Callable<Mi.Action<S, E>> thunk) {
                Future<Mi.Action<S, E>> work = worker.submit(thunk);
                backlog.add(work);
                join(work, new WeakReference<>(effects));
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

    private void join(Future<Mi.Action<S, E>> work, WeakReference<E> weakEffects) {
        AtomicReference<Future<?>> joinRef = new AtomicReference<>();
        joinRef.set(joiner.submit(() -> {
            try {
                Mi.Action<S, E> action = work.get(60, TimeUnit.SECONDS);
                backlog.remove(work);
                E effects = weakEffects.get();
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

    private void handle(Throwable e, WeakReference<E> weakEffects) {
        runOnMainThread(() -> {
            E effects = weakEffects.get();
            if (effects != null) {
                effects.handle(e);
            }
            else {
                throw new RuntimeException(e);
            }
        });
    }
}
