package ph.codeia.fist.content;

/*
 * This file is a part of the fist project.
 */

import ph.codeia.fist.Effects;
import ph.codeia.fist.Fst;
import ph.codeia.fist.Mu;

@SuppressWarnings("NewApi")
public class Loadable<T> {

    public interface Ui<T> extends Effects<Loadable<T>> {
        enum Event { INIT, LOAD, REFRESH }

        void begin();

        void loading();

        void loaded(T content);

        void refreshing(T oldContent);

        void empty();

        default boolean shouldFetch(Event from, T current) {
            return true;
        }

        default void didFetch(boolean isEmpty) {
        }

        @Override
        default void onEnter(Loadable<T> content) {
            switch (content.state) {
            case BEGIN:
                begin();
                break;
            case LOADING:
                loading();
                break;
            case LOADED:
                loaded(content.data);
                break;
            case NOTHING:
                empty();
                break;
            case REFRESHING:
                refreshing(content.data);
                break;
            }
        }
    }

    public static <T> Fst<Loadable<T>> of(Fst.Builder builder) {
        return builder.build(new Loadable<T>());
    }

    enum State { BEGIN, LOADING, LOADED, NOTHING, REFRESHING }

    State state = State.BEGIN;
    T data;

    public Mu<Loadable<T>> reset() {
        state = State.NOTHING;
        data = null;
        return Mu.reenter();
    }
}
