package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

/**
 * An {@link AsyncFst} with no notion of main thread. Runnables that are meant
 * to be run in the main thread are simply called in the same thread as the
 * caller.
 *
 * @param <S> The state type
 */
public class UnconfinedFst<S> extends AsyncFst<S> {

    /**
     * @param state The initial state
     * @param builder The builder containing the machine configuration
     */
    UnconfinedFst(S state, Builder builder) {
        super(state, builder);
    }

    /**
     * @param state The initial state
     */
    public UnconfinedFst(S state) {
        super(state);
    }

    @Override
    protected void runOnMainThread(Runnable proc) {
        proc.run();
    }
}
