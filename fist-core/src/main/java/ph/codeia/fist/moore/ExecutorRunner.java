package ph.codeia.fist.moore;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Executor;

public class ExecutorRunner<S> extends AsyncRunner<S> {
    private final Executor main;

    public ExecutorRunner(S state, Executor main) {
        super(state);
        this.main = main;
    }

    @Override
    protected void runOnMainThread(Runnable proc) {
        main.execute(proc);
    }

    @Override
    protected void handle(Throwable e, Mu.Context<S> context) {
        main.execute(() -> {
            throw new RuntimeException(e);
        });
    }
}
