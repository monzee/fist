package ph.codeia.fist.moore;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Executor;

public class ExecutorMoore<S> extends AsyncMoore<S> {
    private final Executor main;

    public ExecutorMoore(S state, Executor main) {
        super(state);
        this.main = main;
    }

    @Override
    protected void runOnMainThread(Runnable proc) {
        main.execute(proc);
    }
}
