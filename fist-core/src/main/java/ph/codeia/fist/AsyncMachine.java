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
    private final Queue<Future<?>> joins = new ConcurrentLinkedQueue<>();
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
        for (Future<?> join : joins) {
            join.cancel(true);
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

                public void async(Fst.Producer<A> producer) {

                }

                @Override
                public void stream(Fst.Daemon<S> proc, A receiver) {
                    AtomicReference<Future<?>> join = new AtomicReference<>();
                    Fst.Channel<S> tx = mutator -> {
                        O actor = weakActor.get();
                        if (actor == null) {
                            throw new CancellationException("Actor is gone.");
                        }
                        // TODO: allocation in a potentially tight loop;
                        // figure out how to do this with plain monitors
                        Deferred<S> ds = new Deferred<>();
                        runOnMainThread(() -> {
                            receiver.apply(mutator.fold(state), actor)
                                    .match(this);
                            ds.ok(state);
                        });
                        return ds.get();
                    };
                    synchronized (joins) {
                        join.set(workers.submit(() -> {
                            try {
                                proc.accept(tx);
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
                                synchronized (joins) {
                                    joins.remove(join.get());
                                }
                                join.set(null);
                            }
                        }));
                        joins.add(join.get());
                    }
                }
            });
        });
    }

    private void join(WeakReference<O> weakActor, Future<A> job) {
        AtomicReference<Future<?>> join = new AtomicReference<>();
        synchronized (joins) {
            join.set(joiner.submit(() -> {
                try {
                    A action = job.get(60, TimeUnit.SECONDS);
                    backlog.remove(job);
                    O actor = weakActor.get();
                    if (actor != null) {
                        exec(actor, action);
                    }
                    // should i just lose the result if the view
                    // gets GC'd or put the new action in the backlog?
                    // this would really only happen when you forget
                    // to stop the machine before exiting
                }
                catch (InterruptedException ignored) {
                }
                catch (ExecutionException|TimeoutException e) {
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
                    synchronized (joins) {
                        joins.remove(join.get());
                    }
                    join.set(null);
                }
            }));
            joins.add(join.get());
        }
    }
}
