package ph.codeia.fist;

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

import ph.codeia.fist.mealy.Mi;
import ph.codeia.fist.moore.Mu;

public abstract class AsyncFst<S> implements Fst<S> {

    public static class Builder implements Fst.Builder {
        private static final ExecutorService DEFAULT_WORKER = Executors.newSingleThreadExecutor();
        private static final ExecutorService DEFAULT_JOINER = Executors.newSingleThreadExecutor();

        private ExecutorService worker = DEFAULT_WORKER;
        private ExecutorService joiner = DEFAULT_JOINER;
        private long timeoutMillis = 60_000;

        public Builder timeout(long millis) {
            timeoutMillis = millis;
            return this;
        }

        public Builder timeout(long duration, TimeUnit unit) {
            timeoutMillis = unit.toMillis(duration);
            return this;
        }

        public Builder workOn(ExecutorService worker) {
            this.worker = worker;
            return this;
        }

        public Builder joinOn(ExecutorService joiner) {
            this.joiner = joiner;
            return this;
        }

        @Override
        public <S> Fst<S> build(S state) {
            return new UnconfinedFst<>(state, this);
        }
    }

    private final Queue<Future<Mu.Action<S>>> muBacklog = new ConcurrentLinkedQueue<>();
    private final Queue<Future> miBacklog = new ConcurrentLinkedQueue<>();
    private final Queue<AtomicReference<Future<?>>> pending = new ConcurrentLinkedQueue<>();
    private final ExecutorService worker;
    private final ExecutorService joiner;
    private final long timeout;
    private boolean isRunning;
    private S state;

    protected AsyncFst(S state, Builder builder) {
        this.state = state;
        worker = builder.worker;
        joiner = builder.joiner;
        timeout = builder.timeoutMillis;
    }

    public AsyncFst(S state) {
        this(state, new Builder());
    }

    protected abstract void runOnMainThread(Runnable proc);

    @SafeVarargs
    @Override
    public final void start(Effects<S>... effects) {
        if (isRunning) return;
        isRunning = true;
        for (Effects<S> e : effects) {
            WeakReference<Effects<S>> weakEffects = new WeakReference<>(e);
            for (Future<Mu.Action<S>> work : muBacklog) {
                joinMoore(work, weakEffects);
            }
            for (Future work : miBacklog) {
                //noinspection unchecked
                joinMealy(work, weakEffects);
            }
            e.onEnter(state);
        }
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
        runOnMainThread(() -> action.apply(state).run(new Mu.Machine<S>() {
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
            public void forward(Mu.Action<S> action) {
                action.apply(state).run(this);
            }

            @Override
            public void async(Callable<Mu.Action<S>> thunk) {
                Future<Mu.Action<S>> work = worker.submit(thunk);
                muBacklog.add(work);
                joinMoore(work, new WeakReference<>(effects));
            }

            @Override
            public void raise(Throwable e) {
                effects.handle(e);
            }
        }));
    }

    @Override
    public <E extends Effects<S>> void exec(E effects, Mi.Action<S, E> action) {
        if (!isRunning) return;
        runOnMainThread(() -> action.apply(state, effects).run(new Mi.Machine<S, E>() {
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
                miBacklog.add(work);
                joinMealy(work, new WeakReference<>(effects));
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

    private void joinMoore(
            Future<Mu.Action<S>> work,
            WeakReference<Effects<S>> weakEffects
    ) {
        AtomicReference<Future<?>> joinRef = new AtomicReference<>();
        joinRef.set(joiner.submit(() -> {
            try {
                Mu.Action<S> action = work.get(timeout, TimeUnit.MILLISECONDS);
                miBacklog.remove(work);
                Effects<S> effects = weakEffects.get();
                if (effects != null) {
                    exec(effects, action);
                }
            }
            catch (InterruptedException ignored) {
            }
            catch (ExecutionException e) {
                miBacklog.remove(work);
                handle(e.getCause(), weakEffects);
            }
            catch (TimeoutException e) {
                miBacklog.remove(work);
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

    private <E extends Effects<S>> void joinMealy(
            Future<Mi.Action<S, E>> work,
            WeakReference<E> weakEffects
    ) {
        AtomicReference<Future<?>> joinRef = new AtomicReference<>();
        joinRef.set(joiner.submit(() -> {
            try {
                Mi.Action<S, E> action = work.get(timeout, TimeUnit.MILLISECONDS);
                E effects = weakEffects.get();
                if (effects == null) {
                    miBacklog.remove(work);
                }
                else try {
                    exec(effects, action);
                    miBacklog.remove(work);
                }
                catch (ClassCastException ignored) {
                }
            }
            catch (InterruptedException ignored) {
            }
            catch (ExecutionException e) {
                miBacklog.remove(work);
                handle(e.getCause(), weakEffects);
            }
            catch (TimeoutException e) {
                miBacklog.remove(work);
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

    private void handle(Throwable e, WeakReference<? extends Effects<S>> weakEffects) {
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
