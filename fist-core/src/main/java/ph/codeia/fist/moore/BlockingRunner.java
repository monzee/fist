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
    public void start(Mu.Context<S> context) {
        if (isRunning) return;
        isRunning = true;
        context.onEnter(state);
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    @Override
    public void exec(Mu.Context<S> context, Mu.Action<S> action) {
        if (!isRunning) return;
        action.apply(state).run(new Mu.Processor<S>() {
            @Override
            public void noop() {
            }

            @Override
            public void enter(S newState) {
                state = newState;
                context.onEnter(state);
            }

            @Override
            public void reenter() {
                context.onEnter(state);
            }

            @Override
            public void reduce(Mu.Action<S> action) {
                exec(context, action);
            }

            @Override
            public void reduce(Callable<Mu.Action<S>> thunk) {
                try {
                    exec(context, thunk.call());
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
