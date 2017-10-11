package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.lang.ref.WeakReference;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AsyncMachine<S, O extends Fst.ErrorHandler, A extends Fst.Action<S, O, A>>
        implements Fst.Machine<S, O, A>
{
    private final Queue<Future<A>> backlog = new ConcurrentLinkedQueue<>();
    private final Queue<AtomicReference<Future<?>>> joins = new ConcurrentLinkedQueue<>();
    private final ExecutorService joiner;
    private final ExecutorService workers;
    private final A enter;
    private S state;
    private boolean isStarted = false;  // volatile?

    /**
     * @param enter The entry function
     * @param state The initial state
     * @param joiner This should be a single thread executor
     * @param workers Thread pool for background tasks
     */
    public AsyncMachine(A enter, S state, ExecutorService joiner, ExecutorService workers) {
        this.enter = enter;
        this.state = state;
        this.joiner = joiner;
        this.workers = workers;
    }

    /**
     * Creates separate (single) thread contexts for task execution and result
     * retrieval.
     * <p>
     * This is the minimum required threads to enable task timeouts and should
     * be enough for most systems that only occasionally need to do background
     * tasks and do not need to have tasks running in parallel.
     *
     * @param enter The entry function
     * @param state The initial state
     * @see #AsyncMachine(Fst.Action, Object, ExecutorService, ExecutorService)
     */
    public AsyncMachine(A enter, S state) {
        this(enter, state, Executors.newSingleThreadExecutor(), Executors.newSingleThreadExecutor());
    }

    /**
     * Runs a procedure on the platform's main/UI thread.
     *
     * @param r The procedure to run.
     */
    public abstract void runOnMainThread(Runnable r);

    @Override
    public void start(O actor) {
        isStarted = true;
        WeakReference<O> weakActor = new WeakReference<>(actor);
        for (Future<A> job : backlog) {
            join(weakActor, job);
        }
        exec(actor, enter);
    }

    @Override
    public void stop() {
        isStarted = false;
        for (AtomicReference<Future<?>> join : joins) {
            Future<?> future = join.get();
            if (future != null) {
                future.cancel(true);
            }
        }
    }

    @Override
    public void exec(O actor, A action) {
        if (!isStarted) {
            return;
        }
        runOnMainThread(() -> {
            WeakReference<O> weakActor = new WeakReference<>(actor);
            action.apply(state, actor).match(new Fst.Case<S, A>() {
                @Override
                public void enter(S newState) {
                    state = newState;
                    enter.apply(state, weakActor.get()).match(this);
                }

                @Override
                public void reenter() {
                    enter.apply(state, weakActor.get()).match(this);
                }

                @Override
                public void move(S newState) {
                    state = newState;
                }

                @Override
                public void raise(Throwable error) {
                    O actor = weakActor.get();
                    assert actor != null;
                    actor.handle(error);
                }

                @Override
                public void noop() {
                }

                @Override
                public void forward(A newAction) {
                    newAction.apply(state, weakActor.get()).match(this);
                }

                @Override
                public void async(Callable<A> thunk) {
                    Future<A> job = workers.submit(thunk);
                    backlog.add(job);
                    join(weakActor, job);
                }

                @Override
                public void async(S intermediate, Callable<A> thunk) {
                    enter(intermediate);
                    async(thunk);
                }

                @Override
                public void stream(Fst.Daemon<S> proc, A receiver) {
                    AtomicReference<Future<?>> join = new AtomicReference<>();
                    joins.add(join);
                    join.set(workers.submit(() -> {
                        try {
                            proc.accept(new Channel(receiver, this));
                        }
                        catch (InterruptedException ignored) {
                        }
                        catch (Exception e) {
                            runOnMainThread(() -> {
                                O actor = weakActor.get();
                                if (actor != null) {
                                    actor.handle(e);
                                }
                            });
                        }
                        finally {
                            joins.remove(join);
                            join.set(null);
                        }
                    }));
                }

                class Channel implements Fst.Channel<S>, Runnable {
                    final Deferred<S> barrier = new Deferred<>();
                    final A receiver;
                    final Fst.Case<S, A> selector;
                    Fst.Mutate<S> mutate;
                    O actor;

                    Channel(A receiver, Fst.Case<S, A> selector) {
                        this.receiver = receiver;
                        this.selector = selector;
                    }

                    @Override
                    public void run() {
                        try {
                            receiver.apply(mutate.fold(state), actor).match(selector);
                            barrier.ok(state);
                        }
                        catch (RuntimeException e) {
                            barrier.err(e);
                        }
                    }

                    @Override
                    public S send(Fst.Mutate<S> f) throws ExecutionException, InterruptedException {
                        actor = weakActor.get();
                        if (actor == null) {
                            throw new CancellationException("Actor is gone.");
                        }
                        mutate = f;
                        runOnMainThread(this);
                        try {
                            return barrier.take();
                        }
                        finally {
                            actor = null;
                            mutate = null;
                        }
                    }
                }
            });
        });
    }

    private void join(WeakReference<O> weakActor, Future<A> job) {
        AtomicReference<Future<?>> join = new AtomicReference<>();
        joins.add(join);
        join.set(joiner.submit(() -> {
            try {
                A action = job.get(60, TimeUnit.SECONDS);
                backlog.remove(job);  // should this be inside the if?
                O actor = weakActor.get();
                if (actor != null) {
                    exec(actor, action);
                }
            }
            catch (InterruptedException ignored) {
            }
            catch (ExecutionException | TimeoutException e) {
                backlog.remove(job);
                job.cancel(true);  // interrupt timed out task
                runOnMainThread(() -> {
                    O actor = weakActor.get();
                    if (actor != null) {
                        actor.handle(e);
                    }
                });
            }
            finally {
                joins.remove(join);
                join.set(null);
            }
        }));
    }
}
