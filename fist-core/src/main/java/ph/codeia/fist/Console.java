package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */


/**
 * An {@link Effects} extension that allows a {@link Mi.Action mealy action} to
 * log and echo messages.
 *
 * @param <S> The state type
 */
public interface Console<S> extends Effects<S> {
    /**
     * Displays a message to the user. Usually implemented in Android as a
     * toast or snackbar.
     *
     * @param text The message to show
     */
    void echo(String text);

    /**
     * Records a message and optionally an exception on the log for later
     * inspection.
     *
     * @param ex The exception to log
     * @param text A string that might have printf variables
     * @param fmtArgs Optional printf args
     */
    void log(Throwable ex, String text, Object... fmtArgs);

    /**
     * Convenience method to printf a string before showing.
     *
     * @param text A string that might have printf variables
     * @param fmtArgs Optional printf args
     */
    default void echo(String text, Object... fmtArgs) {
        echo(String.format(text, fmtArgs));
    }

    /**
     * Logs an error.
     *
     * @param ex The exception to log
     * @see #log(Throwable, String, Object...)
     */
    default void log(Throwable ex) {
        log(ex, "");
    }

    /**
     * Logs a message, possibly with printf variables.
     *
     * @param text A string that might have printf variables
     * @param fmtArgs Optional printf args
     */
    default void log(String text, Object... fmtArgs) {
        log(null, text, fmtArgs);
    }
}

