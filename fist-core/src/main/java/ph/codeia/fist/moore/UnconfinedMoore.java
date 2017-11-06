package ph.codeia.fist.moore;

/*
 * This file is a part of the fist project.
 */

public class UnconfinedMoore<S> extends AsyncMoore<S> {

    public UnconfinedMoore(S state) {
        super(state);
    }

    @Override
    protected void runOnMainThread(Runnable proc) {
        proc.run();
    }
}
