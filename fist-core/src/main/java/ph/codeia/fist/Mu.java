package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;

/**
 * Associated Moore machine types, factories and convenience functions.
 * <p>
 * This is almost exactly the same as {@link Mi}, except 1) Moore actions are
 * not coupled to a particular receiver type so only one type variable is
 * needed (the state type) and 2) it is possible to turn a Moore action to a
 * Mealy action without losing information, so there's an additional method
 * {@link Action#toMealy()}.
 *
 * @param <S> The state type
 * @see Mi
 */
@SuppressWarnings("NewApi")
public final class Mu<S> {

    /**
     * Creates a command that produces no output or state changes.
     *
     * @param <S> The state type
     * @return a command object
     * @see Case#noop()
     */
    public static <S> Mu<S> noop() {
        return new Mu<>(Case::noop);
    }

    /**
     * Creates a command that re-emits the current state. Mostly used with
     * mutable state types.
     *
     * @param <S> The state type
     * @return a command object
     * @see Case#reenter()
     */
    public static <S> Mu<S> reenter() {
        return new Mu<>(Case::reenter);
    }

    /**
     * Creates a command that emits a new state.
     *
     * @param newState The new state
     * @param <S> The state type
     * @return a command object
     * @see Case#enter(Object)
     */
    public static <S> Mu<S> enter(S newState) {
        return new Mu<>(sm -> sm.enter(newState));
    }

    /**
     * Creates a command that emits a sequence of states.
     * <p>
     * Only the last state "sticks", i.e. visible to the next action.
     * Technically, they all stick but they are immediately replaced by the
     * next state in the sequence.
     * <p>
     * This is used for "side states", i.e. states that don't really
     * represent a state that the system might be in at some point in time
     * but are just there to produce outputs. Examples are logging and
     * toasts/snackbars.
     *
     * @param states The sequence of states to enter
     * @param <S> The state type
     * @return a command object
     * @see #enter(Object)
     */
    @SafeVarargs
    public static <S> Mu<S> enterMany(S... states) {
        Mu<S> acc = noop();
        for (S state : states) if (state != null) {
            acc = acc.then(enter(state));
        }
        return acc;
    }

    /**
     * Creates a command that runs an action that emits another command.
     *
     * @param action The action to execute
     * @param <S> The state type
     * @return a command object
     * @see Case#forward(Action)
     */
    public static <S> Mu<S> forward(Action<S> action) {
        return new Mu<>(sm -> sm.forward(action));
    }

    /**
     * Creates a command that runs an action in the background.
     *
     * @param block The async action to execute
     * @param <S> The state type
     * @return a command object
     * @see Case#async(Callable)
     */
    public static <S> Mu<S> async(Callable<Action<S>> block) {
        return new Mu<>(sm -> sm.async(block));
    }

    /**
     * Creates a command that allows a service to do work on its own thread and
     * send back an action to the state machine once.
     *
     * @param block The block that does some work and calls back to the state
     *              machine with the result.
     * @param <S> The state type
     * @return a command object
     * @see Case#defer(Fn.Proc)
     */
    public static <S> Mu<S> defer(Fn.Proc<Continuation<S>> block) {
        return new Mu<>(sm -> sm.defer(block));
    }

    /**
     * Creates a command that indicates that an error has occurred during
     * a transition.
     *
     * @param e The error
     * @param <S> The state type
     * @return a command object
     * @see Case#raise(Throwable)
     */
    public static <S> Mu<S> raise(Throwable e) {
        return new Mu<>(sm -> sm.raise(e));
    }

    private final Command<S> command;

    private Mu(Command<S> command) {
        this.command = command;
    }

    /**
     * Runs the command against the selector.
     *
     * @param selector The selector object/pattern
     */
    public void run(Case<S> selector) {
        command.run(selector);
    }

    /**
     * Combines two command objects into one.
     *
     * @param next The next command to execute after this
     * @return a command object
     */
    public Mu<S> then(Mu<S> next) {
        return new Mu<>(sm -> {
            run(sm);
            next.run(sm);
        });
    }

    /**
     * Executes an action after this command.
     *
     * @param action The next action
     * @return a command object
     */
    public Mu<S> then(Action<S> action) {
        return then(forward(action));
    }

    /**
     * Executes an async action after this command.
     *
     * @param thunk The async action
     * @return a command object
     */
    public Mu<S> then(Callable<Action<S>> thunk) {
        return then(async(thunk));
    }

    /**
     * Reversed version of {@link #then(Mu)}. Sometimes useful for eliding
     * type parameters at the call site.
     *
     * @param prev The command to execute before this
     * @return a command object
     */
    public Mu<S> after(Mu<S> prev) {
        return prev.then(this);
    }

    /**
     * Reversed version of {@link #then(Action)}.
     *
     * @param action The action to execute before this
     * @return a command object
     * @see #after(Mu)
     */
    public Mu<S> after(Action<S> action) {
        return forward(action).then(this);
    }

    /**
     * Reversed version of {@link #then(Callable)}.
     *
     * @param thunk The async action to execute before this
     * @return a command object
     * @see #after(Mu)
     */
    public Mu<S> after(Callable<Action<S>> thunk) {
        return async(thunk).then(this);
    }

    private interface Command<S> {
        void run(Case<S> selector);
    }

    /**
     * Represents Moore state transitions.
     * <p>
     * A Moore action is a simpler and purer version of {@link Mi.Action Mealy
     * action}. The output of a Moore machine depends only on the current state
     * whereas a Mealy machine can produce additional side effects for each
     * action. This is reflected in the transition types; a Moore action only
     * receives a reference to the current state and is expected to return a new
     * state wrapped in a command object. Output is generated by the receiver
     * object only during {@link Effects#onEnter(Object)}.
     *
     * @param <S> The state type
     */
    public interface Action<S> {
        /**
         * Computes a new state from the current state.
         *
         * @param state The current state
         * @return a command object
         */
        Mu<S> apply(S state);

        /**
         * Produces an action that replaces the state unconditionally.
         *
         * @param state The new state
         * @param <S> The state type
         * @return an action object
         */
        static <S> Action<S> pure(S state) {
            return s -> Mu.enter(state);
        }

        /**
         * Produces an action that does not depend on the current state.
         *
         * @param command The command object
         * @param <S> The state type
         * @return an action object
         */
        static <S> Action<S> pure(Mu<S> command) {
            return s -> command;
        }

        /**
         * Produces an async action that does not depend on the current state.
         *
         * @param thunk The async action
         * @param <S> The state type
         * @return an action object
         */
        static <S> Action<S> pure(Callable<Action<S>> thunk) {
            return s -> Mu.async(thunk);
        }

        /**
         * Produces an action that computes a new state from the current state.
         *
         * @param f The state transformer
         * @param <S> The state type
         * @return an action object
         */
        static <S> Action<S> pure(Fn.Func<S, S> f) {
            return s -> Mu.enter(f.apply(s));
        }

        /**
         * Sets the state of the machine after executing this.
         *
         * @param state The new state
         * @return an action object
         */
        default Action<S> then(S state) {
            return s -> apply(s).then(pure(state));
        }

        /**
         * Sends a command to the machine after executing this.
         *
         * @param command The next command
         * @return an action object
         */
        default Action<S> then(Mu<S> command) {
            return s -> apply(s).then(pure(command));
        }

        /**
         * Executes an action after this.
         *
         * @param action The next action
         * @return an action object
         */
        default Action<S> then(Action<S> action) {
            return s -> apply(s).then(action);
        }

        /**
         * Executes an async action after this.
         *
         * @param thunk The next async action
         * @return an action object
         */
        default Action<S> then(Callable<Action<S>> thunk) {
            return s -> apply(s).then(thunk);
        }

        /**
         * Reversed version of {@link #then(Object)}. Sometimes useful for
         * eliding type parameters at the call site.
         *
         * @param state The state to set before executing this
         * @return an action object
         */
        default Action<S> after(S state) {
            return pure(state).then(this);
        }

        /**
         * Reversed version of {@link #then(Mu)}.
         *
         * @param command The command to send before executing this
         * @return a command object
         * @see #after(Object)
         */
        default Mu<S> after(Mu<S> command) {
            return command.then(this);
        }

        /**
         * Reversed version of {@link #then(Action)}.
         *
         * @param action The action to execute before this
         * @return an action object
         * @see #after(Object)
         */
        default Action<S> after(Action<S> action) {
            return action.then(this);
        }

        /**
         * Reversed version of {@link #then(Callable)}.
         *
         * @param thunk The async action to execute before executing this
         * @return an action thunk
         * @see #after(Object)
         */
        default Callable<Action<S>> after(Callable<Action<S>> thunk) {
            return () -> thunk.call().then(this);
        }

        /**
         * Converts a Moore command to a Mealy command.
         *
         * @param <E> The receiver type
         * @return a Mealy command object
         */
        default <E extends Effects<S>> Mi.Action<S, E> toMealy() {
            return new MooreToMealy<>(this);
        }
    }

    /**
     * Represents the actions that a state machine must take in response to a
     * command.
     *
     * @param <S> The state type
     */
    public interface Case<S> {
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
        void forward(Action<S> action);

        /**
         * Submits the block for background execution then executes the
         * resulting action.
         *
         * @param block The task to do in the background
         */
        void async(Callable<Action<S>> block);

        /**
         * Promise/CompletableFuture-style async for services that make their
         * results available through callbacks.
         *
         * @param block The task that invokes an async action
         */
        void defer(Fn.Proc<Continuation<S>> block);

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
     */
    public interface Continuation<S> {
        /**
         * @param nextAction The action to send to the state machine
         */
        void resume(Action<S> nextAction);

        /**
         * Shortcut for {@code resume(_ -> Mu.enter(newState))}.
         *
         * @param newState The new state to send to the state machine
         */
        default void ok(S newState) {
            resume(Action.pure(newState));
        }

        /**
         * Shortcut for {@code resume(_ -> Mu.raise(error))}.
         *
         * @param error The exception to raise
         */
        default void fail(Exception error) {
            resume(Action.pure(raise(error)));
        }
    }
}

