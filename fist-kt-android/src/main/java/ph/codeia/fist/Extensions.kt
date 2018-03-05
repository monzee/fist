package ph.codeia.fist


/*
 * This file is a part of the fist project.
 */

inline fun <S, E : Effects<S>> action(
    crossinline block: MealyScope<S, E>.(S, E) -> Mi<S, E>
): Mi.Action<S, E> = Mi.Action { state, effects ->
    block(MealyScope(), state, effects)
}


inline fun <S> action(
    crossinline block: MooreScope<S>.(S) -> Mu<S>
): Mu.Action<S> = Mu.Action {
    block(MooreScope(), it)
}


inline operator fun <S, T> Fst<S>.invoke(
    crossinline block: (S) -> T
): T = project { block(it) }


inline operator fun <S, T> Fst.Binding<S, *>.invoke(
    crossinline block: (S) -> T
): T = project { block(it) }


operator fun <S, E : Effects<S>> Fst.Binding<S, E>.plusAssign(action: Mi.Action<S, E>) {
    exec(action)
}


operator fun <S> Fst.Binding<S, *>.plusAssign(action: Mu.Action<S>) {
    exec(action)
}


operator fun <S> Fst<S>.plusAssign(effectsToAction: Pair<Effects<S>, Mu.Action<S>>) {
    val (effects, action) = effectsToAction
    exec(effects, action)
}


operator fun <S> Effects<S>.times(action: Mu.Action<S>): Pair<Effects<S>, Mu.Action<S>> {
    return Pair(this, action)
}


operator fun <S> Mu.Action<S>.times(effects: Effects<S>): Pair<Effects<S>, Mu.Action<S>> {
    return Pair(effects, this)
}


operator fun <S, E : Effects<S>> Mi.Action<S, E>.plus(next: Mi.Action<S, E>): Mi.Action<S, E> {
    return then(next)
}


operator fun <S> Mu.Action<S>.plus(next: Mu.Action<S>): Mu.Action<S> {
    return then(next)
}

