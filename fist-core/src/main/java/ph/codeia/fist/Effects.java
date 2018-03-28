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
     * Called when the machine executes an ENTER or REENTER command and when
     * the machine is started.
     *
     * @param s The new state.
     */
    void onEnter(S s);

    /**
     * Called only when the machine executes an ENTER command before {@code
     * #onEnter(S)} is called. Default implementation does nothing.
     * <p>
     * This is meant for producing output during a transition that is not
     * supposed to be a part of the state, specifically logging, user
     * notification or any other side effect. Useful for {@link Mu} actions;
     * I believe this is inferior to direct calls used in {@link Mi} actions.
     *
     * @param oldState The previous state being replaced
     * @param newState The new state
     */
    default void onExit(S oldState, S newState) {
    }

    /**
     * Called when the machine executes a RAISE command. The default
     * implementation rethrows the exception wrapped in a RuntimeException.
     *
     * @param e The error
     */
    default void handle(Throwable e) {
        throw e instanceof RuntimeException ?
                (RuntimeException) e :
                new RuntimeException(e);
    }
}
