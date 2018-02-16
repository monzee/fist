package ph.codeia.fist.lifecycle

import android.arch.lifecycle.LifecycleOwner
import ph.codeia.fist.AndroidFst
import ph.codeia.fist.LifecycleBinder
import ph.codeia.fist.Effects
import ph.codeia.fist.Fst


/*
 * This file is a part of the fist project.
 */

fun <S, E : Effects<S>> LifecycleOwner.bind(
        state: S,
        effects: E
) = bind(AndroidFst(state), effects)

fun <S, E : Effects<S>> LifecycleOwner.bind(
        fst: Fst<S>,
        effects: E
) = LifecycleBinder.of(this).bind(fst, effects)

inline fun <S> LifecycleOwner.bind(
        fst: Fst<S>,
        crossinline block: (S) -> Unit
) = LifecycleBinder.of(this).bind(fst, Effects<S> { block(it) })
