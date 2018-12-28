package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

/**
 * "Namespace" for functional interfaces.
 */
public interface Fn {
    /**
     * A unary void function.
     *
     * @param <T> The parameter type
     */
    interface Proc<T> {
        void receive(T t);
    }

    /**
     * A binary void function.
     *
     * @param <T> The type of the first argument
     * @param <U> The type of the second argument
     */
    interface BiProc<T, U> {
        void receive(T t, U u);
    }

    /**
     * A unary function.
     *
     * @param <S> The parameter type
     * @param <T> The return type
     */
    interface Func<S, T> {
        T apply(S s);
    }

    /**
     * Produces a value.
     *
     * @param <T> The type of the value produced
     */
    interface Supplier<T> {
        T get();
    }
}
