package ph.codeia.fist.content;

/*
 * This file is a part of the fist project.
 */

import ph.codeia.fist.Fst;

public abstract class ContentLoader<T> implements Loadable<T> {

    @Override
    public Fst<Loadable<T>, LoadEvent<T>> render(ContentView<T> actor) {
        if (actor.willFetch(ContentView.Event.BEGIN, null)) {
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
                if (actor.willFetch(Event.LOAD, null)) {
                    return Fst.async(ContentView::loading, ContentLoader.this::fetch);
                }
                else {
                    return Fst.noop();
                }
            }

            @Override
            public Fst<Loadable<T>, LoadEvent<T>> loaded(T content) {
                if (actor.willFetch(Event.REFRESH, content)) {
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
                return (s, v) -> {
                    v.didFetch(false);
                    return Fst.enter(e -> e.loaded(t));
                };
            }
            else {
                return (s, v) -> {
                    v.didFetch(true);
                    return Fst.enter(ContentView::empty);
                };
            }
        }
        catch (Exception e) {
            return (s, v) -> Fst.raise(e);
        }
    }

}
