package ph.codeia.fist.mealy;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Executor;

import ph.codeia.fist.Effects;

public class ExecutorMealy<S, E extends Effects<S>> extends AsyncMealy<S, E> {

    private final Executor main;

    public ExecutorMealy(S state, Executor main) {
        super(state);
        this.main = main;
    }

    @Override
    protected void runOnMainThread(Runnable proc) {
        main.execute(proc);
    }
}
