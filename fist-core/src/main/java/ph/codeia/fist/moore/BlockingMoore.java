package ph.codeia.fist.moore;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;

import ph.codeia.fist.Effects;
import ph.codeia.fist.Fn;
import ph.codeia.fist.Mu;

public class BlockingMoore<S> implements Mu.Runner<S> {

    private S state;
    private boolean isRunning;

    public BlockingMoore(S state) {
        this.state = state;
    }

    @Override
    public void start(Effects<S> effects) {
        if (isRunning) return;
        isRunning = true;
        effects.onEnter(state);
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    @Override
    public void exec(Effects<S> effects, Mu.Action<S> action) {
        if (!isRunning) return;
        action.apply(state).run(new Mu.Case<S>() {
            @Override
            public void noop() {
            }

            @Override
            public void enter(S newState) {
                state = newState;
                effects.onEnter(state);
            }

            @Override
            public void reenter() {
                effects.onEnter(state);
            }

            @Override
            public void forward(Mu.Action<S> action) {
                exec(effects, action);
            }

            @Override
            public void async(Callable<Mu.Action<S>> block) {
                try {
                    exec(effects, block.call());
                }
                catch (Exception e) {
                    raise(e);
                }
            }

            @Override
            public void defer(Fn.Proc<Mu.Continuation<S>> block) {

            }

            @Override
            public void raise(Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public <T> T project(Fn.Func<S, T> projection) {
        return projection.apply(state);
    }
}
