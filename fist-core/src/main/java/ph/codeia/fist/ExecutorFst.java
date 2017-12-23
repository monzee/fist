package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Executor;

/**
 * An {@link AsyncFst} implementation that can be adapted to various platforms
 * by passing a function that executes a runnable in the platform's main thread.
 *
 * @param <S> The state type
 */
public class ExecutorFst<S> extends AsyncFst<S> {

    /**
     * An {@link AsyncFst.Builder} that takes a main thread executor.
     */
    public static class Builder extends AsyncFst.Builder {
        private final Executor main;

        /**
         * @param main The main thread executor
         */
        public Builder(Executor main) {
            this.main = main;
        }

        @Override
        public <S> Fst<S> build(S state) {
            return new ExecutorFst<>(state, this);
        }
    }

    private final Executor main;

    private ExecutorFst(S state, Builder builder) {
        super(state, builder);
        main = builder.main;
    }

    /**
     * @param state The initial state
     * @param main The main thread executor
     */
    public ExecutorFst(S state, Executor main) {
        this(state, new Builder(main));
    }

    @Override
    protected void runOnMainThread(Runnable proc) {
        main.execute(proc);
    }
}
