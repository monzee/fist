package ph.codeia.fist.content;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;

import ph.codeia.fist.Fst;

public abstract class ContentLoader<T> implements Loadable<T> {

    public static <T> ContentLoader<T> of(Callable<T> fetch) {
        return new ContentLoader<T>() {
            @Override
            protected T onFetch() throws Exception {
                return fetch.call();
            }
        };
    }

    @Override
    public Fst<Loadable<T>, LoadEvent<T>> render(ContentView<T> actor) {
        if (actor.canFetch(ContentView.Event.BEGIN, null)) {
            return Fst.async(ContentView::loading, this::fetch);
        }
        else {
            return Fst.noop();
        }
    }

    public LoadEvent<T> load() {
        return (state, actor) -> state.render(new Case<T>() {
            @Override
            public Fst<Loadable<T>, LoadEvent<T>> empty() {
                if (actor.canFetch(Event.LOAD, null)) {
                    return Fst.async(ContentView::loading, ContentLoader.this::fetch);
                }
                else {
                    return Fst.noop();
                }
            }

            @Override
            public Fst<Loadable<T>, LoadEvent<T>> loaded(T content) {
                if (actor.canFetch(Event.REFRESH, content)) {
                    return Fst.async(e -> e.refreshing(content), ContentLoader.this::fetch);
                }
                else {
                    return Fst.noop();
                }
            }

            @Override
            public Fst<Loadable<T>, LoadEvent<T>> otherwise() {
                return Fst.noop();
            }
        });
    }

    public LoadEvent<T> reset() {
        return (state, actor) -> Fst.enter(ContentView::empty);
    }

    protected abstract T onFetch() throws Exception;

    protected LoadEvent<T> fetch() {
        try {
            T t = onFetch();
            if (t != null) {
                return (s, actor) -> {
                    actor.didFetch(false);
                    return Fst.enter(e -> e.loaded(t));
                };
            }
            else {
                return (s, actor) -> {
                    actor.didFetch(true);
                    return Fst.enter(ContentView::empty);
                };
            }
        }
        catch (Exception e) {
            return (s, a) -> Fst.raise(e);
        }
    }

}
