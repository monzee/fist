package ph.codeia.fist.mealy;

/*
 * This file is a part of the fist project.
 */

import ph.codeia.fist.Effects;

public class UnconfinedMealy<S, E extends Effects<S>> extends AsyncMealy<S, E> {

    public UnconfinedMealy(S state) {
        super(state);
    }

    @Override
    protected void runOnMainThread(Runnable proc) {
        proc.run();
    }
}
