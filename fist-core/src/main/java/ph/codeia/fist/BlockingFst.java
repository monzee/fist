package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;

import ph.codeia.fist.mealy.Mi;
import ph.codeia.fist.moore.Mu;

public class BlockingFst<S> implements Fst<S> {
    private S state;
    private boolean isRunning;

    public BlockingFst(S state) {
        this.state = state;
    }

    @SafeVarargs
    @Override
    public final void start(Effects<S>... effects) {
        if (isRunning) return;
        isRunning = true;
        for (Effects<S> e : effects) {
            e.onEnter(state);
        }
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    @Override
    public void exec(Effects<S> effects, Mu.Action<S> action) {
        if (!isRunning) return;
        action.apply(state).run(new Mu.Machine<S>() {
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
                reenter();
            }

            @Override
            public void forward(Mu.Action<S> action) {
                action.apply(state).run(this);
            }

            @Override
            public void async(Callable<Mu.Action<S>> thunk) {
                try {
                    forward(thunk.call());
                }
                catch (Exception e) {
                    raise(e);
                }
            }

            @Override
            public void raise(Throwable e) {
                effects.handle(e);
            }
        });
    }

    @Override
    public <E extends Effects<S>> void exec(E effects, Mi.Action<S, E> action) {
        if (!isRunning) return;
        action.apply(state, effects).run(new Mi.Machine<S, E>() {
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
                reenter();
            }

            @Override
            public void forward(Mi.Action<S, E> action) {
                action.apply(state, effects).run(this);
            }

            @Override
            public void async(Callable<Mi.Action<S, E>> thunk) {
                try {
                    forward(thunk.call());
                }
                catch (Exception e) {
                    raise(e);
                }
            }

            @Override
            public void raise(Throwable e) {
                effects.handle(e);
            }
        });
    }

    @Override
    public <T> T project(Fn.Func<S, T> projection) {
        return projection.apply(state);
    }
}
