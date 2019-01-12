package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;

/**
 * Mealy state machine commands and static factories.
 * <p>
 * This class represents a sum type over the commands understood by the state
 * machine. In ML, this would be something like
 * <pre>
 *     data Mi s e = Noop | Reenter | Enter s | Raise Throwable
 *                 | Forward (s -&gt; e -&gt; Mi s e)
 *                 | Async (() -&gt; s -&gt; e -&gt; Mi s e)
 *                 | Defer (((s -&gt; e -&gt; Mi s e) -&gt; ()) -&gt; ())
 * </pre>
 * This class is also a container/namespace for:
 * <ul>
 * <li> static factories for each of the branches above
 * <li> the {@link Action} type representing state transitions
 * <li> static action factories
 * <li> convenience methods for composing commands and actions
 * </ul>
 *
 * @param <S> The state type
 * @param <E> The receiver type
 */
@SuppressWarnings("NewApi")
public final class Mi<S, E> {

    /**
     * Creates a command that produces no output or state changes.
     *
     * @param <S> The state type
     * @param <E> The receiver type
     * @return a command object for the state machine
     * @see Case#noop()
     */
    public static <S, E> Mi<S, E> noop() {
        return new Mi<>(Case::noop);
    }

    /**
     * Creates a command that re-emits the current state. Mostly used with
     * mutable state types.
     *
     * @param <S> The state type
     * @param <E> The receiver type
     * @return a command object for the state machine
     * @see Case#reenter()
     */
    public static <S, E> Mi<S, E> reenter() {
        return new Mi<>(Case::reenter);
    }

    public static <S, E> Mi<S, E> reenterThen(Callable<Action<S, E>> block) {
        Mi<S, E> start = reenter();
        return start.then(async(block));
    }

    public static <S, E> Mi<S, E> reenterThen(Fn.Proc<Continuation<S, E>> block) {
        Mi<S, E> start = reenter();
        return start.then(defer(block));
    }

    /**
     * Creates an action that emits a new state.
     *
     * @param newState The new state
     * @param <S> The state type
     * @param <E> The receiver type
     * @return a command object for the state machine
     * @see Case#enter(Object)
     */
    public static <S, E> Mi<S, E> enter(S newState) {
        return new Mi<>(on -> on.enter(newState));
    }

    public static <S, E> Mi<S, E> enterThen(S newState, Callable<Action<S, E>> block) {
        Mi<S, E> start = enter(newState);
        return start.then(async(block));
    }

    public static <S, E> Mi<S, E> enterThen(S newState, Fn.Proc<Continuation<S, E>> block) {
        Mi<S, E> start = enter(newState);
        return start.then(defer(block));
    }

    /**
     * Creates a command that runs an action that emits another command.
     *
     * @param action The next action
     * @param <S> The state type
     * @param <E> The receiver type
     * @return a command object for the state machine
     * @see Case#forward(Action)
     */
    public static <S, E> Mi<S, E> forward(Action<S, E> action) {
        return new Mi<>(on -> on.forward(action));
    }

    /**
     * Creates a command that runs an action in the background.
     *
     * @param block The background task to execute
     * @param <S> The state type
     * @param <E> The receiver type
     * @return a command object for the state machine
     * @see Case#async(Callable)
     */
    public static <S, E> Mi<S, E> async(Callable<Action<S, E>> block) {
        return new Mi<>(on -> on.async(block));
    }

    /**
     * Creates a command that allows a service to do work on its own thread and
     * send back an action to the state machine once.
     *
     * @param block The block that does some work and calls back to the state
     *              machine with the result.
     * @param <S> The state type
     * @param <E> The receiver type
     * @return a command object
     * @see Case#defer(Fn.Proc)
     */
    public static <S, E> Mi<S, E> defer(Fn.Proc<Continuation<S, E>> block) {
        return new Mi<>(on -> on.defer(block));
    }

    /**
     * Creates a command that indicates that an error has occurred during
     * a transition.
     *
     * @param e The error
     * @param <S> The state type
     * @param <E> The receiver type
     * @return a command object for the state machine
     * @see Case#raise(Throwable)
     */
    public static <S, E> Mi<S, E> raise(Throwable e) {
        return new Mi<>(on -> on.raise(e));
    }

    private final Command<S, E> command;

    private Mi(Command<S, E> command) {
        this.command = command;
    }

    /**
     * Runs the command against the selector.
     *
     * @param selector The selector object/pattern
     */
    public void run(Case<S, E> selector) {
        command.run(selector);
    }

    /**
     * Combines two command objects into one.
     *
     * @param next The next command to execute after this
     * @return a command object
     */
    public Mi<S, E> then(Mi<S, E> next) {
        return new Mi<>(machine -> {
            run(machine);
            next.run(machine);
        });
    }

    /**
     * Executes an action after this command.
     *
     * @param action The next action
     * @return a command object
     */
    public Mi<S, E> then(Action<S, E> action) {
        return then(forward(action));
    }

    /**
     * Executes an async action after this command.
     *
     * @param thunk The async action
     * @return a command object
     */
    public Mi<S, E> then(Callable<Action<S, E>> thunk) {
        return then(async(thunk));
    }

    /**
     * Reversed version of {@link #then(Mi)}. Sometimes useful for eliding
     * type parameters at the call site.
     *
     * @param prev The command to execute before this
     * @return a command object
     */
    public Mi<S, E> after(Mi<S, E> prev) {
        return prev.then(this);
    }

    /**
     * Reversed version of {@link #then(Action)}.
     *
     * @param action The action to execute before this
     * @return a command object
     * @see #after(Mi)
     */
    public Mi<S, E> after(Action<S, E> action) {
        return forward(action).then(this);
    }

    /**
     * Reversed version of {@link #then(Callable)}.
     *
     * @param thunk The async action to execute before this
     * @return a command object
     * @see #after(Mi)
     */
    public Mi<S, E> after(Callable<Action<S, E>> thunk) {
        return async(thunk).then(this);
    }

    private interface Command<S, E> {
        void run(Case<S, E> selector);
    }

    /**
     * A type representing state transitions.
     * <p>
     * A Mealy transition is a function from a tuple of state and some input to
     * a tuple of state and some output (T::S*Σ -&gt; S*Λ). The output in this
     * formulation is not a value but the side effect caused by calling some
     * method of the receiver E. The input is not reflected in the signature; it
     * is captured from the environment where the action is defined (i.e. the
     * free variables in the body of {@link #apply(Object, Object)}).
     *
     * @param <S> The state type
     * @param <E> The receiver type
     */
    public interface Action<S, E> {
        /**
         * Computes a new state from the current state and may produce an
         * output using a receiver object.
         * <p>
         * The new state must be wrapped in a state machine command.
         *
         * @param s The current state
         * @param e The state receiver
         * @return a command object for the state machine
         * @see Case
         */
        Mi<S, E> apply(S s, E e);

        /**
         * Produces a side effect without touching the current state.
         *
         * @param proc The side effect
         * @param <S> The state type
         * @param <E> The receiver type
         * @return a noop action
         */
        static <S, E> Action<S, E> effect(Fn.Proc<E> proc) {
            return (s, e) -> {
                proc.receive(e);
                return Mi.noop();
            };
        }

        /**
         * Produces a side effect from the current state without modifying it.
         * <p>
         * Any state modification will not be reflected by the receiver after
         * call since this ultimately produces a {@link #noop()} command. So
         * don't modify the state here.
         *
         * @param proc The biconsumer
         * @param <S> The state type
         * @param <E> The receiver type
         * @return a noop action
         */
        static <S, E> Action<S, E> effect(Fn.BiProc<S, E> proc) {
            return (s, e) -> {
                proc.receive(s, e);
                return Mi.noop();
            };
        }

        /**
         * Produces an action that replaces the state unconditionally.
         *
         * @param state The new state
         * @param <S> The state type
         * @param <E> The receiver type
         * @return an action object
         */
        static <S, E> Action<S, E> pure(S state) {
            return (s, e) -> Mi.enter(state);
        }

        /**
         * Produces an action that does not depend on the current state.
         *
         * @param command The command to execute
         * @param <S> The state type
         * @param <E> The receiver type
         * @return an action object
         */
        static <S, E> Action<S, E> pure(Mi<S, E> command) {
            return (s, e) -> command;
        }

        /**
         * Produces an async action that does not depend on the current state.
         *
         * @param thunk The async action
         * @param <S> The state type
         * @param <E> The receiver type
         * @return an action object
         */
        static <S, E> Action<S, E> pure(Callable<Action<S, E>> thunk) {
            return (s, e) -> Mi.async(thunk);
        }

        /**
         * Produces an action that computes a new state from the current state
         * without producing a side effect from the receiver.
         *
         * @param f The state transformer
         * @param <S> The state type
         * @param <E> The receiver type
         * @return an action object
         */
        static <S, E> Action<S, E> pure(Fn.Func<S, S> f) {
            return (s, e) -> Mi.enter(f.apply(s));
        }

        /**
         * Sets the state of the machine after executing this.
         *
         * @param state The next state
         * @return an action object
         */
        default Action<S, E> then(S state) {
            return (s, e) -> apply(s, e).then(pure(state));
        }

        /**
         * Sends a command to the machine after executing this.
         *
         * @param command The next command
         * @return an action object
         */
        default Action<S, E> then(Mi<S, E> command) {
            return (s, e) -> apply(s, e).then(pure(command));
        }

        /**
         * Executes an action after this.
         *
         * @param action The next action
         * @return an action object
         */
        default Action<S, E> then(Action<S, E> action) {
            return (s, e) -> apply(s, e).then(action);
        }

        /**
         * Executes an async action after this.
         *
         * @param thunk The async action
         * @return an action object
         */
        default Action<S, E> then(Callable<Action<S, E>> thunk) {
            return (s, e) -> apply(s, e).then(thunk);
        }

        /**
         * Reversed version of {@link #then(Object)}. Sometimes useful for
         * eliding type parameters at the call site.
         *
         * @param state The state to set before executing this
         * @return an action object
         */
        default Action<S, E> after(S state) {
            return Action.<S, E> pure(state).then(this);
        }

        /**
         * Reversed version of {@link #then(Mi)}.
         *
         * @param command The command to send before executing this
         * @return a command object
         * @see #after(Object)
         */
        default Mi<S, E> after(Mi<S, E> command) {
            return command.then(this);
        }

        /**
         * Reversed version of {@link #then(Action)}.
         *
         * @param action The action to execute before this
         * @return an action object
         * @see #after(Object)
         */
        default Action<S, E> after(Action<S, E> action) {
            return action.then(this);
        }

        /**
         * Reversed version of {@link #then(Callable)}.
         *
         * @param thunk The async action to execute before executing this
         * @return an action thunk
         * @see #after(Object)
         */
        default Callable<Action<S, E>> after(Callable<Action<S, E>> thunk) {
            return () -> thunk.call().then(this);
        }
    }

    /**
     * Represents the actions that a state machine must take in response to a
     * command.
     *
     * @param <S> The state type
     * @param <E> The receiver type
     */
    public interface Case<S, E> {
        /**
         * The receiver will not be notified after the action.
         */
        void noop();

        /**
         * The receiver will be notified with the same (possibly mutated) state
         * instance after the action.
         */
        void reenter();

        /**
         * The receiver will be notified with a new state instance.
         *
         * @param newState The new state
         */
        void enter(S newState);

        /**
         * Executes another action.
         *
         * @param action The next action
         */
        void forward(Action<S, E> action);

        /**
         * Submits the block for background execution then executes the
         * resulting action.
         *
         * @param block The task to do in the background
         */
        void async(Callable<Action<S, E>> block);

        /**
         * Promise/CompletableFuture-style async for services that make their
         * results available through callbacks.
         *
         * @param block The task that invokes an async action
         */
        void defer(Fn.Proc<Continuation<S, E>> block);

        /**
         * Indicates that an error has occurred during an action.
         *
         * @param e The error
         */
        void raise(Throwable e);
    }

    /**
     * Argument passed to {@link #defer} calls that sends an action back to
     * the state machine.
     *
     * @param <S> The state type
     * @param <E> The receiver type
     */
    public interface Continuation<S, E> {
        /**
         * @param nextAction The action to send to the state machine
         */
        void resume(Action<S, E> nextAction);

        /**
         * Shortcut for {@code resume((_, _) -> Mi.enter(newState))}.
         *
         * @param newState The new state to send to the state machine
         */
        default void ok(S newState) {
            resume(Action.pure(newState));
        }

        /**
         * Shortcut for {@code resume((_, _) -> Mi.raise(error))}.
         *
         * @param error The exception to raise
         */
        default void fail(Exception error) {
            resume(Action.pure(raise(error)));
        }
    }
}
