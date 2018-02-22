package ph.codeia.fist


/*
 * This file is a part of the fist project.
 */

inline fun <S, E : Effects<S>> action(
    crossinline block: MealyScope<S, E>.(S, E) -> Mi<S, E>
): Mi.Action<S, E> = Mi.Action { state, effects ->
    MealyScope<S, E>().block(state, effects)
}


inline fun <S> action(
    crossinline block: MooreScope<S>.(S) -> Mu<S>
): Mu.Action<S> = Mu.Action {
    MooreScope<S>().block(it)
}


inline fun <S, E : Effects<S>> task(
    crossinline block: MealyScope<S, E>.(S) -> Mi<S, E>
): Mi.Action<S, E> = Mi.Action { state, _ ->
    Mi.async {
        val result = block(MealyScope(), state)
        Mi.Action<S, E> { _, _ -> result }
    }
}


inline fun <S> task(
    crossinline block: MooreScope<S>.(S) -> Mu<S>
): Mu.Action<S> = Mu.Action { state ->
    Mu.async {
        val result = block(MooreScope(), state)
        Mu.Action<S> { result }
    }
}


inline fun <S, T> Fst.Binding<S, *>.mapState(
    crossinline block: (S) -> T
): T = project { block(it) }


inline operator fun <S> Fst.Binding<S, *>.invoke(
    crossinline block: (S) -> Unit
) = inspect { block(it) }


operator fun <S, E : Effects<S>> Fst.Binding<S, E>.plusAssign(action: Mi.Action<S, E>) {
    exec(action)
}


operator fun <S> Fst.Binding<S, *>.plusAssign(action: Mu.Action<S>) {
    exec(action)
}

