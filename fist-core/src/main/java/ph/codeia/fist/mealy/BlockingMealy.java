package ph.codeia.fist.mealy;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;

import ph.codeia.fist.Effects;
import ph.codeia.fist.Fn;
import ph.codeia.fist.Mi;

public class BlockingMealy<S, E extends Effects<S>> implements Mi.Runner<S, E> {

    private S state;
    private boolean isRunning;

    public BlockingMealy(S state) {
        this.state = state;
    }

    @Override
    public void start(E effects) {
        isRunning = true;
        effects.onEnter(state);
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    @Override
    public void exec(E effects, Mi.Action<S, E> action) {
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
