package ph.codeia.fist.moore;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;

public class BlockingRunner<S> implements Mu.Runner<S> {

    private S state;
    private boolean isRunning;

    public BlockingRunner(S state) {
        this.state = state;
    }

    @Override
    public void start(Mu.Effects<S> effects) {
        if (isRunning) return;
        isRunning = true;
        effects.onEnter(state);
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    @Override
    public void exec(Mu.Effects<S> effects, Mu.Action<S> action) {
        if (!isRunning) return;
        action.apply(state).run(new Mu.Machine<S>() {
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
            public void async(Callable<Mu.Action<S>> thunk) {
                try {
                    exec(effects, thunk.call());
                }
                catch (Exception e) {
                    raise(e);
                }
            }

            @Override
            public void raise(Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public <T> T inspect(Mu.Function<S, T> projection) {
        return projection.apply(state);
    }
}
