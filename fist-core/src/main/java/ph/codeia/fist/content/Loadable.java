package ph.codeia.fist.content;

/*
 * This file is a part of the fist project.
 */

import ph.codeia.fist.mealy.Mi;

@SuppressWarnings("NewApi")
public class Loadable<T> {

    public interface Ui<T> extends Mi.Effects<Loadable<T>> {
        enum Event {INIT, LOAD, REFRESH}

        void begin();

        void loading();

        void loaded(T content);

        void refreshing(T oldContent);

        void empty();

        default boolean canFetch(Event from, T current) {
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

    enum State {BEGIN, LOADING, LOADED, NOTHING, REFRESHING}

    State state = State.BEGIN;
    T data;
}
