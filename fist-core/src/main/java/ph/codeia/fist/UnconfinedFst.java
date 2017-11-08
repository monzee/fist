package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

public class UnconfinedFst<S> extends AsyncFst<S> {

    UnconfinedFst(S state, Builder builder) {
        super(state, builder);
    }

    public UnconfinedFst(S state) {
        super(state);
    }

    @Override
    protected void runOnMainThread(Runnable proc) {
        proc.run();
    }
}
