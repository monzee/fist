package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class ExecutorMachine<S, O extends Fst.ErrorHandler, A extends Fst.Action<S,O,A>>
        extends AsyncMachine<S, O, A>
{
    private final Executor main;

    public ExecutorMachine(
            A enter, S state, Executor main,
            ExecutorService joiner,
            ExecutorService workers
    ) {
        super(enter, state, joiner, workers);
        this.main = main;
    }

    public ExecutorMachine(A enter, S state, Executor main) {
        super(enter, state);
        this.main = main;
    }

    @Override
    public void runOnMainThread(Runnable r) {
        main.execute(r);
    }
}
