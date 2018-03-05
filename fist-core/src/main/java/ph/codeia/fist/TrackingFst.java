package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

public class TrackingFst<S> implements Fst<S> {
    private enum Kind {NOOP, REENTER, ENTER, RAISE}
    private S state;
    private Throwable error;
    private Kind lastCmd;

    public TrackingFst(S initialState) {
        state = initialState;
    }

    @Override
    public void start(Effects<S> effects) {
        effects.onEnter(state);
    }

    @Override
    public void stop() {
    }

    @Override
    public void exec(Effects<S> effects, Mu.Action<S> action) {
        action.apply(state).run(new Mu.Case<S>() {
            @Override
            public void noop() {
                lastCmd = Kind.NOOP;
            }

            @Override
            public void reenter() {
                lastCmd = Kind.REENTER;
                effects.onEnter(state);
            }

            @Override
            public void enter(S newState) {
                lastCmd = Kind.ENTER;
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
                try {
                    forward(block.call());
                }
                catch (Exception e) {
                    raise(e);
                }
            }

            @Override
            public void defer(Fn.Proc<Mu.Continuation<S>> block) {
                BlockingQueue<Mu.Action<S>> next = new ArrayBlockingQueue<>(1);
                block.receive(next::offer);
                async(next::take);
            }

            @Override
            public void raise(Throwable e) {
                lastCmd = Kind.RAISE;
                error = e;
            }
        });
    }

    @Override
    public <E extends Effects<S>> void exec(E effects, Mi.Action<S, E> action) {
        action.apply(state, effects).run(new Mi.Case<S, E>() {
            @Override
            public void noop() {
                lastCmd = Kind.NOOP;
            }

            @Override
            public void reenter() {
                lastCmd = Kind.REENTER;
                effects.onEnter(state);
            }

            @Override
            public void enter(S newState) {
                lastCmd = Kind.ENTER;
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
                try {
                    forward(block.call());
                }
                catch (Exception e) {
                    raise(e);
                }
            }

            @Override
            public void defer(Fn.Proc<Mi.Continuation<S, E>> block) {
                BlockingQueue<Mi.Action<S, E>> next = new ArrayBlockingQueue<>(1);
                block.receive(next::offer);
                async(next::take);
            }

            @Override
            public void raise(Throwable e) {
                lastCmd = Kind.RAISE;
                error = e;
            }
        });
    }

    @Override
    public <T> T project(Fn.Func<S, T> projection) {
        return projection.apply(state);
    }

    public boolean didNothing() {
        return lastCmd == Kind.NOOP;
    }

    public boolean didNothing(Fn.Func<S, Boolean> assertion) {
        return didNothing() && assertion.apply(state);
    }

    public boolean didReenter() {
        return lastCmd == Kind.REENTER;
    }

    public boolean didReenter(Fn.Func<S, Boolean> assertion) {
        return didReenter() && assertion.apply(state);
    }

    public boolean didEnter() {
        return lastCmd == Kind.ENTER;
    }

    public boolean didEnter(Fn.Func<S, Boolean> assertion) {
        return didEnter() && assertion.apply(state);
    }

    public boolean didRaise() {
        return lastCmd == Kind.RAISE;
    }

    public boolean didRaise(Fn.Func<Throwable, Boolean> assertion) {
        return didRaise() && assertion.apply(error);
    }
}
