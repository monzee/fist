package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;

/**
 * A synchronous state machine.
 * <p>
 * This is the simplest possible implementation of {@link Fst} where all async
 * actions are executed and awaited in the same thread as the caller. This is
 * also always-on, so an action is guaranteed to run immediately as soon as
 * {@code exec} is called. Actions cannot be deferred by stopping and starting
 * as this class does not maintain any sort of queue.
 *
 * @param <S> The state type
 */
public class BlockingFst<S> implements Fst<S> {

    private S state;

    /**
     * @param state The initial state
     */
    public BlockingFst(S state) {
        this.state = state;
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
                    effects.onEnter(state);
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
                    effects.handle(e);
                }
            });
        }
        catch (RuntimeException e) {
            effects.handle(e);
        }
    }

    @Override
    public <E extends Effects<S>> void exec(E effects, Mi.Action<S, E> action) {
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
                    effects.handle(e);
                }
            });
        }
        catch (RuntimeException e) {
            effects.handle(e);
        }
    }

    @Override
    public <T> T project(Fn.Func<S, T> projection) {
        return projection.apply(state);
    }
}
