package ph.codeia.fist.content;

/*
 * This file is a part of the fist project.
 */

import ph.codeia.fist.Fst;

public interface ContentView<T> extends Fst.ErrorHandler {
    enum Event { BEGIN, LOAD, REFRESH }

    /**
     * Hook to determine whether to continue/start fetching content.
     *
     * @param from The action that triggered the fetch
     * @param current The current content; null if {@code from} is not equal
     *                to {@code Event.REFRESH}
     * @return true to start fetching, false to abort
     */
    boolean canFetch(Event from, T current);

    /**
     * Executed after a successful fetch before entering {@link #loaded(Object)}
     * or {@link #empty()}.
     *
     * @param isEmpty If the fetch returned something
     */
    void didFetch(boolean isEmpty);

    Fst<Loadable<T>, LoadEvent<T>> loading();
    Fst<Loadable<T>, LoadEvent<T>> loaded(T content);
    Fst<Loadable<T>, LoadEvent<T>> empty();
    Fst<Loadable<T>, LoadEvent<T>> refreshing(T oldContent);
}
