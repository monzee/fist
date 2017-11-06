package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

public interface Fn {
    interface Proc<T> {
        void receive(T t);
    }

    interface BiProc<T, U> {
        void receive(T t, U u);
    }

    interface Func<S, T> {
        T apply(S s);
    }
}
