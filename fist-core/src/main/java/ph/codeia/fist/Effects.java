package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

@SuppressWarnings("NewApi")
public interface Effects<S> {
    Throwable STOP_SIGNAL = new Throwable();

    void onEnter(S s);

    default void handle(Throwable e) {
        throw new RuntimeException(e);
    }
}
