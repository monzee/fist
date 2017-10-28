package ph.codeia.fist.content;

/*
 * This file is a part of the fist project.
 */

import ph.codeia.fist.Fst;

public interface Loadable<T> extends Fst.Moore<Loadable<T>, ContentView<T>, LoadEvent<T>> {

    class Case<T> implements ContentView<T> {
        public Fst<Loadable<T>, LoadEvent<T>> otherwise() {
            return Fst.raise(new IllegalStateException("unhandled case"));
        }

        @Override
        public boolean canFetch(Event from, T current) {
            return true;
        }

        @Override
        public void didFetch(boolean isEmpty) {
        }

        @Override
        public Fst<Loadable<T>, LoadEvent<T>> loading() {
            return otherwise();
        }

        @Override
        public Fst<Loadable<T>, LoadEvent<T>> loaded(T content) {
            return otherwise();
        }

        @Override
        public Fst<Loadable<T>, LoadEvent<T>> empty() {
            return otherwise();
        }

        @Override
        public Fst<Loadable<T>, LoadEvent<T>> refreshing(T oldContent) {
            return otherwise();
        }

        @Override
        public void handle(Throwable error) {
        }
    }

}
