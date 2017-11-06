package ph.codeia.fist.content;

/*
 * This file is a part of the fist project.
 */

import ph.codeia.fist.mealy.Mi;

@SuppressWarnings("NewApi")
public interface Loader<T> {

    T onFetch() throws Exception;

    static <T> Mi.Action<Loadable<T>, Loadable.Ui<T>> reset() {
        return (content, view) -> {
            content.state = Loadable.State.NOTHING;
            content.data = null;
            return Mi.reenter();
        };
    }

    default Mi.Action<Loadable<T>, Loadable.Ui<T>> load() {
        return (content, view) -> {
            final boolean canFetch;
            switch (content.state) {
            case BEGIN:
                canFetch = view.canFetch(Loadable.Ui.Event.INIT, null);
                break;
            case NOTHING:
                canFetch = view.canFetch(Loadable.Ui.Event.LOAD, null);
                break;
            case LOADED:
                canFetch = view.canFetch(Loadable.Ui.Event.REFRESH, content.data);
                break;
            default:
                canFetch = false;
                break;
            }
            if (!canFetch) return Mi.noop();
            content.state = content.state == Loadable.State.LOADED ?
                    Loadable.State.REFRESHING :
                    Loadable.State.LOADING;
            return Mi.Action.pure(this::fetch).after(Mi.reenter());
        };
    }

    default Mi.Action<Loadable<T>, Loadable.Ui<T>> fetch() throws Exception {
        T t = onFetch();
        return (content, view) -> {
            content.data = t;
            content.state = t == null ? Loadable.State.NOTHING : Loadable.State.LOADED;
            view.didFetch(t == null);
            return Mi.reenter();
        };
    }
}
