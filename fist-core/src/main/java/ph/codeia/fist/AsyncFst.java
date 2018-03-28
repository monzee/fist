package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

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
        private static final Executor DEFAULT_JOINER = Executors.newSingleThreadExecutor();

        private Executor worker = DEFAULT_WORKER;
        private Executor joiner = DEFAULT_JOINER;
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
         * @param joiner The executor to submit await actions to
         * @return this
         */
        public Builder joinOn(Executor joiner) {
            this.joiner = joiner;
            return this;
        }

        @Override
        public <S> Fst<S> build(S state) {
            return new UnconfinedFst<>(state, this);
        }
    }

    private final Executor joiner;
    private final Executor worker;
    private final BlockingQueue<Mu.Action<S>> deferred = new LinkedBlockingQueue<>();
    private final Map<Class<? extends Effects>, BlockingQueue<Mi.Action>> deferredByClass = new WeakHashMap<>();
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
    public void start(Effects<S> effects) {
        if (isRunning) return;
        isRunning = true;
        effects.onEnter(state);  // TODO: force main thread?
        WeakReference<Effects<S>> weakFx = new WeakReference<>(effects);
        Class<? extends Effects> key = effects.getClass();
        joiner.execute(() -> {
            for (Mu.Action<S> action; null != (action = deferred.poll());) {
                Effects<S> fx = weakFx.get();
                if (fx == null) return;
                exec(fx, action);
            }
            BlockingQueue<Mi.Action> deferredMealy = deferredByClass.get(key);
            if (deferredMealy != null) {
                for (Mi.Action action; null != (action = deferredMealy.poll());) {
                    Effects<S> fx = weakFx.get();
                    if (fx == null) break;
                    //noinspection unchecked
                    exec(fx, action);
                }
            }
        });
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    @Override
    public void exec(Effects<S> effects, Mu.Action<S> action) {
        if (!isRunning) {
            deferred.offer(action);
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
                        AtomicBoolean timedOut = new AtomicBoolean(false);
                        //noinspection UnnecessaryLocalVariable
                        BlockingQueue<Mu.Action<S>> queue = deferred;
                        // local copy to prevent capture of AsyncFst.this which
                        // i believe also causes Mu.Case.this to be captured
                        // which itself captures Effects, leaking it during the
                        // async call
                        worker.execute(() -> {
                            Mu.Action<S> action;
                            try {
                                action = block.call();
                            }
                            catch (Exception e) {
                                action = Mu.Action.pure(Mu.raise(e));
                            }
                            synchronized (timedOut) {
                                if (!timedOut.get()) {
                                    queue.offer(action);
                                }
                            }
                        });
                        join(new WeakReference<>(effects), timedOut);
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
        Class<? extends Effects> key = effects.getClass();
        if (!isRunning) {
            getOrMakeQueueFor(key).offer(action);
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
                        effects.onExit(state, newState);
                        state = newState;
                        effects.onEnter(newState);
                    }

                    @Override
                    public void forward(Mi.Action<S, E> action) {
                        action.apply(state, effects).run(this);
                    }

                    @Override
                    public void async(Callable<Mi.Action<S, E>> block) {
                        AtomicBoolean timedOut = new AtomicBoolean(false);
                        BlockingQueue<Mi.Action> queue = getOrMakeQueueFor(key);
                        worker.execute(() -> {
                            Mi.Action<S, E> action;
                            try {
                                action = block.call();
                            }
                            catch (Exception e) {
                                action = Mi.Action.pure(Mi.raise(e));
                            }
                            synchronized (timedOut) {
                                if (!timedOut.get()) {
                                    queue.offer(action);
                                }
                            }
                        });
                        join(new WeakReference<>(effects), queue, timedOut);
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

    // TODO: timeout is a lie; it is only meaningful when using a serial work executor
    // the timeout for a single task can be indefinitely extended by executing additional
    // tasks that finish well within the timeout while the previous task is still running.
    // imagine we have an executor that can execute 2 tasks simultaneously (results may
    // arrive in any order) and a single-threaded joiner:
    // - a very slow task that is supposed to timeout is enqueued
    // - just before it's about to timeout, a second task is enqueued and it finishes instantly
    // - the joiner will see the result enqueued by the second task and processed accordingly
    // - a new wait block will be executed by the joiner which is meant to wait for the second
    //   task, but ends up waiting for the first task which is still running. the timeout
    //   period for the first task is effectively doubled.
    // - rinse and repeat. the waiting time for the first task will be increased again by
    //   the same amount.
    private void join(WeakReference<Effects<S>> weakFx, AtomicBoolean timedOut) {
        joiner.execute(() -> {
            try {
                Mu.Action<S> action;
                if (timeout <= 0) {
                    action = deferred.take();
                }
                else {
                    action = deferred.poll(timeout, TimeUnit.MILLISECONDS);
                    if (action == null) synchronized (timedOut) {
                        // query one more time. it's possible for the other thread
                        // to acquire the lock first and enqueue an action just as
                        // the timeout expires
                        action = deferred.poll();
                        if (action == null) {
                            // still nothing; meaning we got the lock first. tell
                            // the other thread not to enqueue its action
                            timedOut.set(true);
                            action = Mu.Action.pure(Mu.raise(
                                    new TimeoutException(timeoutMsg(timeout))
                            ));
                        }
                    }
                }
                Effects<S> fx = weakFx.get();
                if (fx != null) {
                    exec(fx, action);
                }
                else {
                    deferred.offer(action);
                }
            }
            catch (InterruptedException ignored) {
            }
        });
    }

    private void join(
            WeakReference<Effects<S>> weakFx,
            BlockingQueue<Mi.Action> queue,
            AtomicBoolean timedOut
    ) {
        joiner.execute(() -> {
            try {
                Mi.Action action;
                if (timeout <= 0) {
                    action = queue.take();
                }
                else {
                    action = queue.poll(timeout, TimeUnit.MILLISECONDS);
                    if (action == null) synchronized (timedOut) {
                        action = queue.poll();
                        if (action == null) {
                            timedOut.set(true);
                            action = Mi.Action.pure(Mi.raise(
                                    new TimeoutException(timeoutMsg(timeout))
                            ));
                        }
                    }
                }
                Effects<S> fx = weakFx.get();
                if (fx != null) {
                    //noinspection unchecked
                    exec(fx, action);
                }
                else {
                    queue.offer(action);
                }
            }
            catch (InterruptedException ignored) {
            }
        });
    }

    private BlockingQueue<Mi.Action> getOrMakeQueueFor(Class<? extends Effects> key) {
        synchronized (deferredByClass) {
            BlockingQueue<Mi.Action> value = deferredByClass.get(key);
            if (value == null) {
                value = new LinkedBlockingQueue<>();
                deferredByClass.put(key, value);
            }
            return value;
        }
    }

    private static String timeoutMsg(long millis) {
        return "Task was unable to finish within " + millis + "ms";
    }
}
