package ph.codeia.fist

/*
 * This file is a part of the fist project.
 */


@DslMarker
annotation class ActionDsl


@ActionDsl
class MealyScope<S, E : Effects<S>> {
    val noop: Mi<S, E> by lazy(LazyThreadSafetyMode.NONE) { Mi.noop<S, E>() }
    val reenter: Mi<S, E> by lazy(LazyThreadSafetyMode.NONE) { Mi.reenter<S, E>() }
    val enter: (S) -> Mi<S, E> = { Mi.enter(it) }
    val forward: (Mi.Action<S, E>) -> Mi<S, E> = { Mi.forward(it) }
    val raise: (Throwable) -> Mi<S, E> = { Mi.raise(it) }

    inline fun async(crossinline block: () -> Mi.Action<S, E>): Mi<S, E> = Mi.async {
        block()
    }

    inline fun defer(crossinline block: Mi.Continuation<S, E>.() -> Unit): Mi<S, E> = Mi.defer {
        block(it)
    }

    operator fun Mi<S, E>.plus(next: Mi<S, E>): Mi<S, E> = then(next)

    operator fun Mi<S, E>.plus(next: Mi.Action<S, E>): Mi<S, E> = then(next)
}


@ActionDsl
class MooreScope<S> {
    val noop: Mu<S> by lazy(LazyThreadSafetyMode.NONE) { Mu.noop<S>() }
    val reenter: Mu<S> by lazy(LazyThreadSafetyMode.NONE) { Mu.reenter<S>() }
    val enter: (S) -> Mu<S> = { Mu.enter(it) }
    val forward: (Mu.Action<S>) -> Mu<S> = { Mu.forward(it) }
    val raise: (Throwable) -> Mu<S> = { Mu.raise(it) }

    inline fun async(crossinline block: () -> Mu.Action<S>): Mu<S> = Mu.async {
        block()
    }

    inline fun defer(crossinline block: Mu.Continuation<S>.() -> Unit): Mu<S> = Mu.defer {
        block(it)
    }

    operator fun Mu<S>.plus(next: Mu<S>): Mu<S> = then(next)

    operator fun Mu<S>.plus(next: Mu.Action<S>): Mu<S> = then(next)
}
