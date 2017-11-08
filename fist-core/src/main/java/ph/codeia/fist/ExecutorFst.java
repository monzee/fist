package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Executor;

public class ExecutorFst<S> extends AsyncFst<S> {

    public static class Builder extends AsyncFst.Builder {
        private final Executor main;

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

    public ExecutorFst(S state, Executor main) {
        this(state, new Builder(main));
    }

    @Override
    protected void runOnMainThread(Runnable proc) {
        main.execute(proc);
    }
}
