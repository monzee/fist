package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Callable;

/**
 * Similar to {@link BlockingFst} but remembers the last command executed for
 * testing purposes.
 * <p>
 * Only remembers the terminal commands NOOP, REENTER, ENTER and RAISE. All
 * forwarding commands are followed to completion before saving the last
 * result.
 *
 * @param <S> The state type.
 */
public class TrackingFst<S> implements Fst<S> {
    private enum Kind {NOOP, REENTER, ENTER, RAISE}
    private final Queue<Kind> path = new ArrayDeque<>();
    private S state;
    private Throwable error;

    public TrackingFst(S initialState) {
        state = initialState;
    }

    /**
     * Asserts that no action has been executed yet.
     *
     * @return true if the queue is empty
     */
    public boolean isEmpty() {
        return path.isEmpty();
    }

    /**
     * Asserts that no action has been executed yet and that the current state
     * satisfies the predicate.
     *
     * @param assertion The predicate
     * @return true if the queue is empty and the predicate is true
     */
    public boolean isEmpty(Fn.Func<S, Boolean> assertion) {
        return isEmpty() && assertion.apply(state);
    }

    /**
     * Asserts that the last action enqueued a NOOP.
     *
     * @return true if the top of the queue is a NOOP
     */
    public boolean didNothing() {
        return path.poll() == Kind.NOOP;
    }

    /**
     * Asserts that the last action enqueued a NOOP and that the current state
     * satisfies the predicate.
     *
     * @param assertion The predicate
     * @return true if the top of the queue is a NOOP and the predicate is true
     */
    public boolean didNothing(Fn.Func<S, Boolean> assertion) {
        return didNothing() && assertion.apply(state);
    }

    /**
     * Asserts that the last action enqueued a REENTER.
     *
     * @return true if the top of the queue is a REENTER
     */
    public boolean didReenter() {
        return path.poll() == Kind.REENTER;
    }

    /**
     * Asserts that the last action enqueued a REENTER and that the current state
     * satisfies the predicate.
     *
     * @param assertion The predicate
     * @return true if the top of the queue is a REENTER and the predicate is true
     */
    public boolean didReenter(Fn.Func<S, Boolean> assertion) {
        return didReenter() && assertion.apply(state);
    }

    /**
     * Asserts that the last action enqueued an ENTER.
     *
     * @return true if the top of the queue is an ENTER
     */
    public boolean didEnter() {
        return path.poll() == Kind.ENTER;
    }

    /**
     * Asserts that the last action enqueued an ENTER and that the current state
     * satisfies the predicate.
     *
     * @param assertion The predicate
     * @return true if the top of the queue is an ENTER and the predicate is true
     */
    public boolean didEnter(Fn.Func<S, Boolean> assertion) {
        return didEnter() && assertion.apply(state);
    }

    /**
     * Asserts that the last action enqueued a RAISE.
     *
     * @return true if the top of the queue is a RAISE
     */
    public boolean didRaise() {
        return path.poll() == Kind.RAISE;
    }

    /**
     * Asserts that the last action enqueued a RAISE and that the exception
     * satisfies the predicate.
     *
     * @param assertion The predicate
     * @return true if the top of the queue is a RAISE and the predicate is true
     */
    public boolean didRaise(Fn.Func<Throwable, Boolean> assertion) {
        return didRaise() && assertion.apply(error);
    }

    @Override
    public void start(Effects<S> effects) {
        effects.onEnter(state);
    }

    @Override
    public void stop() {
        path.clear();
    }

    @Override
    public void exec(Effects<S> effects, Mu.Action<S> action) {
        action.apply(state).run(new Mu.Case<S>() {
            @Override
            public void noop() {
                path.add(Kind.NOOP);
            }

            @Override
            public void reenter() {
                path.add(Kind.REENTER);
                effects.onEnter(state);
            }

            @Override
            public void enter(S newState) {
                path.add(Kind.ENTER);
                state = newState;
                effects.onEnter(newState);
            }

            @Override
            public void forward(Mu.Action<S> action) {
                action.apply(state).run(this);
            }

            @Override
            public void async(Callable<Mu.Action<S>> block) {
                try {
                    forward(block.call());
                }
                catch (Exception e) {
                    raise(e);
                }
            }

            @Override
            public void defer(Fn.Proc<Mu.Continuation<S>> block) {
                Deferred<Mu.Action<S>> next = new Deferred<>();
                block.receive(next::offer);
                async(next);
            }

            @Override
            public void raise(Throwable e) {
                path.add(Kind.RAISE);
                error = e;
            }
        });
    }

    @Override
    public <E extends Effects<S>> void exec(E effects, Mi.Action<S, E> action) {
        action.apply(state, effects).run(new Mi.Case<S, E>() {
            @Override
            public void noop() {
                path.add(Kind.NOOP);
            }

            @Override
            public void reenter() {
                path.add(Kind.REENTER);
                effects.onEnter(state);
            }

            @Override
            public void enter(S newState) {
                path.add(Kind.ENTER);
                state = newState;
                effects.onEnter(newState);
            }

            @Override
            public void forward(Mi.Action<S, E> action) {
                action.apply(state, effects).run(this);
            }

            @Override
            public void async(Callable<Mi.Action<S, E>> block) {
                try {
                    forward(block.call());
                }
                catch (Exception e) {
                    raise(e);
                }
            }

            @Override
            public void defer(Fn.Proc<Mi.Continuation<S, E>> block) {
                Deferred<Mi.Action<S, E>> next = new Deferred<>();
                block.receive(next::offer);
                async(next);
            }

            @Override
            public void raise(Throwable e) {
                path.add(Kind.RAISE);
                error = e;
            }
        });
    }

    @Override
    public <T> T project(Fn.Func<S, T> projection) {
        return projection.apply(state);
    }
}

