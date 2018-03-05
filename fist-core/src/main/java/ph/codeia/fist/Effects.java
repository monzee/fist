package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

/**
 * The state receiver or listener which gets notified by the state machine
 * after state transitions.
 *
 * @param <S> The state type
 * @see Mi.Action
 * @see Fst#exec(Effects, Mu.Action)
 */
@SuppressWarnings("NewApi")
public interface Effects<S> {
    /**
     * Called when the machine executes an ENTER or REENTER command.
     *
     * @param s The new state.
     */
    void onEnter(S s);

    default void onExit(S oldState, S newState) {
    }

    /**
     * Called when the machine executes a RAISE command. The default
     * implementation rethrows the exception wrapped in a RuntimeException.
     *
     * @param e The error
     */
    default void handle(Throwable e) {
        throw new RuntimeException(e);
    }
}
