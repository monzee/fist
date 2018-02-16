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
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Asynchronous state machine base implementation.
 *
 * @param <S> The state type
 */
public abstract class AsyncFst<S> implements Fst<S> {

    /**
     * Builder object to customize an {@link AsyncFst} instance.
     * <p>
     * By default, async actions are executed in a single thread and
     * awaited/joined in a (separate) single thread. The latter is probably
     * what you want, but you might want to set the worker executor to a
     * cached thread pool executor with more than 1 thread e.g. if you need to
     * run actions in parallel.
     */
    public static class Builder implements Fst.Builder {
        private static final ExecutorService DEFAULT_WORKER = Executors.newSingleThreadExecutor();
        private static final ExecutorService DEFAULT_JOINER = Executors.newSingleThreadExecutor();

        private ExecutorService worker = DEFAULT_WORKER;
        private ExecutorService joiner = DEFAULT_JOINER;
        private long timeoutMillis = 60_000;

        /**
         * Sets the maximum time to wait for async actions to complete.
         *
         * @param millis The timeout in milliseconds
         * @return this
         */
        public Builder timeout(long millis) {
            timeoutMillis = millis;
            return this;
        }

        /**
         * Sets the maximum time to wait for async actions to complete.
         *
         * @param duration The timeout duration in whatever unit
         * @param unit The time unit
         * @return this
         * @see #timeout(long)
         */
        public Builder timeout(long duration, TimeUnit unit) {
            return timeout(unit.toMillis(duration));
        }

        /**
         * Sets the executor service for async actions.
         *
         * @param worker The executor service to submit async actions to
         * @return this
         */
        public Builder workOn(ExecutorService worker) {
            this.worker = worker;
            return this;
        }

        /**
         * Sets the executor service for awaiting the result of async actions.
         *
         * @param joiner The executor service to submit await actions to
         * @return this
         */
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
    private volatile boolean isRunning;
    private volatile S state;

    /**
     * @param state The initial state
     * @param builder The object containing the executors and configuration.
     */
    protected AsyncFst(S state, Builder builder) {
        this.state = state;
        worker = builder.worker;
        joiner = builder.joiner;
        timeout = builder.timeoutMillis;
    }

    /**
     * @param state The initial state
     */
    protected AsyncFst(S state) {
        this(state, new Builder());
    }

    /**
     * Runs a procedure in the platform's main or UI thread.
     *
     * @param proc The procedure to run in the main thread
     */
    protected abstract void runOnMainThread(Runnable proc);

    @Override
    public final void start(Effects<S> effects) {
        if (isRunning) return;
        isRunning = true;
        effects.onEnter(state);  // TODO: force main thread?
        WeakReference<Effects<S>> weakEffects = new WeakReference<>(effects);
        for (Future<Mu.Action<S>> work : muBacklog) {
            joinMoore(work, weakEffects);
        }
        for (Future work : miBacklog) {
            //noinspection unchecked
            joinMealy(work, weakEffects);
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
        if (!isRunning) {
            FutureTask<Mu.Action<S>> task = new FutureTask<>(() -> action);
            task.run();
            muBacklog.add(task);
            return;
        }
        runOnMainThread(() -> action.apply(state).run(new Mu.OnCommand<S>() {
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
        if (!isRunning) {
            FutureTask<Mi.Action<S, E>> task = new FutureTask<>(() -> action);
            task.run();
            miBacklog.add(task);
            return;
        }
        runOnMainThread(() -> action.apply(state, effects).run(new Mi.OnCommand<S, E>() {
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
        pending.add(joinRef);
        joinRef.set(joiner.submit(() -> {
            try {
                Mu.Action<S> action = timeout > 0 ?
                        work.get(timeout, TimeUnit.MILLISECONDS) :
                        work.get();
                muBacklog.remove(work);
                Effects<S> effects = weakEffects.get();
                if (effects != null) {
                    exec(effects, action);
                }
            }
            catch (InterruptedException ignored) {
            }
            catch (ExecutionException e) {
                muBacklog.remove(work);
                handle(e.getCause(), weakEffects);
            }
            catch (TimeoutException e) {
                muBacklog.remove(work);
                work.cancel(true);
                handle(e, weakEffects);
            }
            finally {
                pending.remove(joinRef);
                joinRef.set(null);
            }
        }));
    }

    private <E extends Effects<S>> void joinMealy(
            Future<Mi.Action<S, E>> work,
            WeakReference<E> weakEffects
    ) {
        AtomicReference<Future<?>> joinRef = new AtomicReference<>();
        pending.add(joinRef);
        joinRef.set(joiner.submit(() -> {
            try {
                Mi.Action<S, E> action = timeout > 0 ?
                        work.get(timeout, TimeUnit.MILLISECONDS) :
                        work.get();
                E effects = weakEffects.get();
                if (effects == null) {
                    miBacklog.remove(work);
                }
                else try {
                    exec(effects, action);
                    miBacklog.remove(work);
                }
                catch (ClassCastException ignored) {
                    // attempted to join a future that was initiated by a
                    // different actor type. this could happen only when
                    // the machine is stopped in the middle of a task and
                    // then started again and #exec(E, Mi.Action) was
                    // called with multiple types of E. must not take the
                    // future off the queue in this case and let something
                    // else join it.
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
