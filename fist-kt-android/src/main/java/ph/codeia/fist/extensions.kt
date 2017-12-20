package ph.codeia.fist

import kotlin.LazyThreadSafetyMode.NONE


/*
 * This file is a part of the fist project.
 */

inline fun <S, E : Effects<S>> action(
        crossinline block: MealyScope<S, E>.(S, E) -> Mi<S, E>
) = Mi.Action<S, E> { state, effects ->
    MealyScope<S, E>().block(state, effects)
}

inline fun <S> action(
        crossinline block: MooreScope<S>.(S) -> Mu<S>
) = Mu.Action<S> {
    MooreScope<S>().block(it)
}

@DslMarker
annotation class ActionDsl

@ActionDsl
class MealyScope<S, E : Effects<S>> {
    val noop: Mi<S, E> by lazy(NONE) { Mi.noop<S, E>() }
    val reenter: Mi<S, E> by lazy(NONE) { Mi.reenter<S, E>() }
    val enter: (S) -> Mi<S, E> = { Mi.enter(it) }
    val forward: (Mi.Action<S, E>) -> Mi<S, E> = { Mi.forward(it) }
    val raise: (Throwable) -> Mi<S, E> = { Mi.raise(it) }
    fun forward(action: (S, E) -> Mi<S, E>) = Mi.forward(action)
    inline fun background(
            crossinline block: () -> Mi.Action<S, E>
    ): Mi<S, E> = Mi.async { block() }
    infix fun Mi<S, E>.then(next: Mi.Action<S, E>) = then(next)
    operator fun Mi<S, E>.plus(nextAsync: () -> Mi.Action<S, E>) = then(nextAsync)
}

@ActionDsl
class MooreScope<S> {
    val noop: Mu<S> by lazy(NONE) { Mu.noop<S>() }
    val reenter: Mu<S> by lazy(NONE) { Mu.reenter<S>() }
    val enter: (S) -> Mu<S> = { Mu.enter(it) }
    val forward: (Mu.Action<S>) -> Mu<S> = { Mu.forward(it) }
    val raise: (Throwable) -> Mu<S> = { Mu.raise(it) }
    inline fun background(
            crossinline block: () -> Mu.Action<S>
    ): Mu<S> = Mu.async { block() }
}

inline fun <S, E : Effects<S>, T> Fst.Actor<S, E>.exec(
        action: Mi.Action<S, E>,
        crossinline projection: (S) -> T
): T {
    exec(action)
    return project { projection(it) }
}

inline fun <S, E : Effects<S>, T> Fst.Actor<S, E>.exec(
        action: Mu.Action<S>,
        crossinline projection: (S) -> T
): T {
    exec(action)
    return project { projection(it) }
}

