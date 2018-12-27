package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.lang.ref.WeakReference;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
        private static final Executor DEFAULT_WORKER = Executors.newSingleThreadExecutor();

        private Executor worker = DEFAULT_WORKER;
        private Executor receiver = Executors.newSingleThreadExecutor();
        private long timeoutMillis = 60_000;

        /**
         * Sets the maximum time to wait for async actions to complete.
         * <p>
         * Default timeout is 60 seconds.
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
         * <p>
         * Default timeout is 60 seconds.
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
         * Sets the executor for async actions.
         *
         * @param worker The executor to submit async actions to
         * @return this
         */
        public Builder workOn(Executor worker) {
            this.worker = worker;
            return this;
        }

        /**
         * Sets the executor for awaiting the result of async actions.
         * <p>
         * Setting this to something other than a single thread executor might
         * cause some async results to be lost between start-stop phases.
         *
         * @param receiver The executor to submit await actions to
         * @return this
         */
        public Builder receiveOn(Executor receiver) {
            this.receiver = receiver;
            return this;
        }

        @Override
        public <S> Fst<S> build(S state) {
            return new UnconfinedFst<>(state, this);
        }
    }

    private final AtomicInteger pendingCount = new AtomicInteger(0);
    private final BlockingDeque<Job<S>> backlog = new LinkedBlockingDeque<>();
    private final Job<S> poisonPill = Job.Case::stop;
    private final Executor worker;
    private final Executor receiver;
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
        receiver = builder.receiver;
        timeout = builder.timeoutMillis;
        backlog.offerLast(poisonPill);
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
    public void start(Effects<S> effects) {
        if (isRunning) {
            return;
        }
        isRunning = true;
        effects.onEnter(state);
        WeakReference<Effects<S>> weakEffects = new WeakReference<>(effects);
        receiver.execute(new Job.Case<S>() {
            boolean stop = false;
            Job<S> next;

            @Override
            public void run() {
                try {
                    while (!stop) {
                        next = backlog.takeFirst();
                        next.match(this);
                    }
                }
                catch (InterruptedException ignored) {
                }
            }

            @Override
            public void moore(Mu.Action<S> action) {
                Effects<S> fx = weakEffects.get();
                if (fx == null) {
                    stop = true;
                    backlog.offerFirst(next);
                }
                else {
                    exec(fx, action);
                }
            }

            @Override
            public void mealy(Class<? extends Effects> target, Mi.Action action) {
                Effects<S> fx = weakEffects.get();
                if (fx == null) {
                    stop = true;
                    backlog.offerFirst(next);
                }
                else if (target.isAssignableFrom(fx.getClass())) {
                    //noinspection unchecked
                    exec(fx, action);
                }
                // else re-enqueue later?
            }

            @Override
            public void stop() {
                stop = true;
            }
        });
    }

    @Override
    public void stop() {
        if (!isRunning) {
            return;
        }
        isRunning = false;
        if (pendingCount.get() == 0) {
            backlog.offerLast(poisonPill);
        }
    }

    @Override
    public void exec(Effects<S> effects, Mu.Action<S> action) {
        if (!isRunning) {
            addToBacklog(e -> e.moore(action));
            return;
        }
        runOnMainThread(() -> {
            try {
                action.apply(state).run(new Mu.Case<S>() {
                    @Override
                    public void noop() {
                    }

                    @Override
                    public void reenter() {
                        effects.onEnter(state);
                    }

                    @Override
                    public void enter(S newState) {
                        effects.onExit(state, newState);
                        state = newState;
                        effects.onEnter(newState);
                    }

                    @Override
                    public void forward(Mu.Action<S> action) {
                        action.apply(state).run(this);
                    }

                    @Override
                    public void async(Callable<Mu.Action<S>> block) {
                        new Pending<>(effects).awaitMoore(block);
                    }

                    @Override
                    public void defer(Fn.Proc<Mu.Continuation<S>> block) {
                        Deferred<Mu.Action<S>> next = new Deferred<>();
                        block.receive(next::offer);
                        async(next);
                    }

                    @Override
                    public void raise(Throwable e) {
                        effects.handle(e);
                    }
                });
            }
            catch (RuntimeException e) {
                effects.handle(e);
            }
        });
    }

    @Override
    public <E extends Effects<S>> void exec(E effects, Mi.Action<S, E> action) {
        if (!isRunning) {
            Class<? extends Effects> cls = effects.getClass();
            addToBacklog(e -> e.mealy(cls, action));
            return;
        }
        runOnMainThread(() -> {
            try {
                action.apply(state, effects).run(new Mi.Case<S, E>() {
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
                        effects.onEnter(newState);
                    }

                    @Override
                    public void forward(Mi.Action<S, E> action) {
                        action.apply(state, effects).run(this);
                    }

                    @Override
                    public void async(Callable<Mi.Action<S, E>> block) {
                        new Pending<>(effects).awaitMealy(block);
                    }

                    @Override
                    public void defer(Fn.Proc<Mi.Continuation<S, E>> block) {
                        Deferred<Mi.Action<S, E>> next = new Deferred<>();
                        block.receive(next::offer);
                        async(next);
                    }

                    @Override
                    public void raise(Throwable e) {
                        effects.handle(e);
                    }
                });
            }
            catch (RuntimeException e) {
                effects.handle(e);
            }
        });
    }

    @Override
    public <T> T project(Fn.Func<S, T> projection) {
        return projection.apply(state);
    }

    private void addToBacklog(Job<S> next) {
        if (pendingCount.get() > 0) {
            backlog.offerLast(next);
        }
        else {
            backlog.removeLast();  // assert _ == poisonPill
            backlog.offerLast(next);
            backlog.offerLast(poisonPill);
        }
    }

    private class Pending<E extends Effects<S>> {
        final AtomicBoolean done = new AtomicBoolean(false);
        final WeakReference<E> weakEffects;
        final Class<? extends Effects> fxClass;

        Pending(E effects) {
            weakEffects = new WeakReference<>(effects);
            fxClass = effects.getClass();
        }

        void awaitMoore(Callable<Mu.Action<S>> block) {
            pendingCount.incrementAndGet();
            if (timeout > 0) {
                Timer.INSTANCE.schedule(() -> {
                    if (!done.getAndSet(true)) {
                        execOrSend(Mu.Action.pure(Mu.raise(new TimeoutException())));
                    }
                }, timeout, TimeUnit.MILLISECONDS);
            }
            worker.execute(() -> {
                Mu.Action<S> action;
                try {
                    action = block.call();
                }
                catch (Exception e) {
                    action = Mu.Action.pure(Mu.raise(e));
                }
                if (!done.getAndSet(true)) {
                    execOrSend(action);
                }
            });
        }

        void awaitMealy(Callable<Mi.Action<S, E>> block) {
            pendingCount.incrementAndGet();
            if (timeout > 0) {
                Timer.INSTANCE.schedule(() -> {
                    if (!done.getAndSet(true)) {
                        execOrSend(Mi.Action.pure(Mi.raise(new TimeoutException())));
                    }
                }, timeout, TimeUnit.MILLISECONDS);
            }
            worker.execute(() -> {
                Mi.Action<S, E> action;
                try {
                    action = block.call();
                }
                catch (Exception e) {
                    action = Mi.Action.pure(Mi.raise(e));
                }
                if (!done.getAndSet(true)) {
                    execOrSend(action);
                }
            });
        }

        void execOrSend(Mu.Action<S> action) {
            Effects<S> fx = weakEffects.get();
            int remaining = pendingCount.decrementAndGet();
            if (fx != null && isRunning) {
                exec(fx, action);
            }
            else {
                backlog.offerLast(e -> e.moore(action));
                if (remaining == 0) {
                    backlog.offerLast(poisonPill);
                }
            }
        }

        void execOrSend(Mi.Action<S, E> action) {
            E fx = weakEffects.get();
            int remaining = pendingCount.decrementAndGet();
            if (fx != null && isRunning) {
                exec(fx, action);
            }
            else {
                backlog.offerLast(e -> e.mealy(fxClass, action));
                if (remaining == 0) {
                    backlog.offerLast(poisonPill);
                }
            }
        }
    }

    private static class Timer {
        static final ScheduledExecutorService INSTANCE =
                Executors.newSingleThreadScheduledExecutor();
    }

    private interface Job<S> {
        void match(Case<S> e);

        interface Case<S> extends Runnable {
            void moore(Mu.Action<S> action);
            void mealy(Class<? extends Effects> target, Mi.Action action);
            void stop();
        }
    }
}
