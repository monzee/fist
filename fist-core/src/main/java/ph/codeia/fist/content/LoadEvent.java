package ph.codeia.fist.content;

/*
 * This file is a part of the fist project.
 */

import ph.codeia.fist.Fst;

public interface LoadEvent<T> extends Fst.Action<Loadable<T>, ContentView<T>, LoadEvent<T>> {}
