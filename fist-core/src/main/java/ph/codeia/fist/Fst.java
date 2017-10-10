package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/**
 * FST - Finite State Transducer.
 * <p>
 * S, Σ, Λ, s0::S, T::S*Σ -> S, G::S*Σ -> Λ (Mealy)
 * S, Σ, Λ, s0::S, T::S*Σ -> S*Λ (alt Mealy)
 * S, Σ, Λ, s0::S, T::S*Σ -> S, G::S -> Λ (Moore)
 *
 * @param <S> The state type
 * @param <A> The action type
 * @author Mon Zafra
 * @since 0.1.0
 */
public final class Fst<S, A extends Fst.Action<? super S, ?, ? extends A>> {

    /**
     * @param <S> The state type
     * @param <O> The actor type
     * @param <A> The action type
     */
    public interface Action<S, O, A extends Action<? super S, ? extends O, ? extends A>> {
        Fst<S, A> apply(S state, O actor);
    }

    /**
     * A Moore-Mealy hybrid state type for a Mealy machine.
     * <p>
     * Generates output in the same way as a {@link ph.codeia.fist.moore.Sm
     * Moore state} but executed in a Mealy machine, meaning the actions also
     * receive an actor instance and can produce side effects in addition to the
     * main output generated by the entry function.
     * <p>
     * I find this to be a fairly pragmatic compromise and leads to pretty
     * clear mapping from state to output without losing the ability to do things
     * like show toasts or log debug info in presenter/controller actions. Though
     * it is a little awkward "pattern matching" against the actor type (which you
     * will do a lot in actions to unwrap the state) because you would need to
     * implement the impure branches even though they will never be called. See
     * {@link ph.codeia.fist.content.Loadable.Case} to see what I mean. Code
     * generation may solve this.
     *
     * @param <S> The state type
     * @param <O> The actor type
     * @param <A> The action type
     * @see ph.codeia.fist.moore.Sm
     */
    public interface Moore<S extends Moore<S, O, A>, O, A extends Action<? super S, ? extends O, ? extends A>> {
        Fst<S, A> render(O actor);
    }

    /**
     * A Mealy machine.
     *
     * @param <S> The state type
     * @param <O> The actor type
     * @param <A> The action type
     */
    public interface Machine<S, O, A extends Action<? super S, ? extends O, ? extends A>> {
        void start(O actor);
        void stop();
        void exec(O actor, A action);
    }

    public interface Producer<T> {
        void accept(Deferred<T> result);
    }

    public interface Mutate<T> {
        T fold(T t);
    }

    public interface Channel<T> {
        T send(Mutate<T> f) throws ExecutionException, InterruptedException, CancellationException;
    }

    public interface Daemon<T> {
        void accept(Channel<T> t) throws Exception;
    }

    public interface Case<S, A extends Action<? super S, ?, ? extends A>> {
        void enter(S newState);
        void reenter();
        void move(S newState);
        void raise(Throwable error);
        void noop();
        void forward(A newAction);
        void async(Callable<A> thunk);
        void async(S intermediate, Callable<A> thunk);
        void stream(Daemon<S> proc, A receiver);
    }

    /**
     * Handles errors that happen in a background task or explicitly sent by
     * an action.
     * <p>
     * Runtime errors that happen during {@link Action#apply(Object, Object)}
     * are not caught by any of the default machine implementations. These
     * actions typically occur in the main thread of the platform and any
     * runtime exception would crash the application. You could of course make
     * your own implementation that can handle these. I believe that these
     * types of errors should just cause a crash.
     */
    public interface ErrorHandler {
        void handle(Throwable error);
    }

    private interface Cmd<S, A extends Action<? super S, ?, ? extends A>> {
        void match(Case<S, A> of);
    }

    /**
     * Sets the machine's state and runs the entry function with the new value.
     *
     * @param newState The state to jump to
     * @param <S> The state type
     * @param <A> The actor type
     * @return a command object for the state machine
     * @see #reenter()
     */
    public static <S, A extends Action<S, ?, A>> Fst<S, A> enter(S newState) {
        return new Fst<>(e -> e.enter(newState));
    }

    /**
     * Same as {@link #enter(Object)} but reuses the machine's current state.
     * <p>
     * Meant for systems with mutable states so that you don't have to pass the
     * same object over and over.
     * <p>
     * <strong>BE CAREFUL NOT TO MAKE THE STATE MACHINE LOOP ENDLESSLY!</strong>
     * It could also happen with {@link #enter(Object)} but it's much more
     * likely to happen here inadvertently. Make sure the state is mutated in
     * such a way that causes the entry function to eventually return a
     * terminal command ({@link #noop()}, {@link #move(Object)} or
     * {@link #raise(Throwable)}).
     *
     * @param <S> The state type
     * @param <A> The actor type
     * @return a command object for the state machine
     */
    public static <S, A extends Action<S, ?, A>> Fst<S, A> reenter() {
        return new Fst<>(Case::reenter);
    }

    /**
     * Invokes the machine's error handler.
     *
     * @param error The error
     * @param <S> The state type
     * @param <A> The actor type
     * @return a command object for the state machine
     * @see ErrorHandler
     */
    public static <S, A extends Action<S, ?, A>> Fst<S, A> raise(Throwable error) {
        return new Fst<>(e -> e.raise(error));
    }

    /**
     * Sets the machine's state without invoking the enter function.
     *
     * @param newState The state to move to
     * @param <S> The state type
     * @param <A> The actor type
     * @return a command object for the state machine
     */
    public static <S, A extends Action<S, ?, A>> Fst<S, A> move(S newState) {
        return new Fst<>(e -> e.move(newState));
    }

    /**
     * Ends the transition sequence and returns to the caller of {@link
     * Machine#exec(Object, Action)}.
     *
     * @param <S> The state type
     * @param <A> The actor type
     * @return a command object for the state machine
     */
    public static <S, A extends Action<S, ?, A>> Fst<S, A> noop() {
        return new Fst<>(Case::noop);
    }

    /**
     * Executes a second action using the same state and actor.
     *
     * @param newAction The next action to execute
     * @param <S> The state type
     * @param <A> The actor type
     * @return a command object for the state machine
     */
    public static <S, A extends Action<S, ?, A>> Fst<S, A> forward(A newAction) {
        return new Fst<>(e -> e.forward(newAction));
    }

    /**
     * Executes the thunk in a worker thread and execs the returned action.
     *
     * @param thunk Executed in the background to generate an action to be sent
     *              to the machine afterwards.
     * @param <S> The state type
     * @param <A> The action type
     * @return a command object for the state machine
     */
    public static <S, A extends Action<S, ?, A>> Fst<S, A> async(Callable<A> thunk) {
        return new Fst<>(e -> e.async(thunk));
    }

    /**
     * Atomic enter + async.
     *
     * @param intermediate The state to enter before executing the task
     * @param thunk Executed in the background to generate an action to be sent
     *              to the machine afterwards.
     * @param <S> The state type
     * @param <A> The action type
     * @return a command object for the state machine
     * @see #async(Callable)
     * @see #enter(Object)
     */
    public static <S, A extends Action<S, ?, A>> Fst<S, A> async(S intermediate, Callable<A> thunk) {
        return new Fst<>(e -> e.async(intermediate, thunk));
    }

    public static <S, A extends Action<S, ?, A>> Fst<S, A> async(Producer<A> producer) {
        Deferred<A> result = new Deferred<>();
        producer.accept(result);
        return async(result::get);
    }

    public static <S, A extends Action<S, ?, A>> Fst<S, A> async(S intermediate, Producer<A> producer) {
        Deferred<A> result = new Deferred<>();
        producer.accept(result);
        return async(intermediate, result::get);
    }

    /**
     * HIGHLY EXPERIMENTAL; USE AT YOUR OWN RISK.
     *
     * @param <S> The state type
     * @param <A> The action type
     * @param proc A long-running task that may send multiple state changes to
     *             the receiver
     * @param receiver The action to perform for every send made by the daemon
     * @return a command object for the state machine
     */
    public static <S, A extends Action<S, ?, A>> Fst<S, A> stream(Daemon<S> proc, A receiver) {
        return new Fst<>(e -> e.stream(proc, receiver));
    }

    private final Cmd<S, A> cmd;
    private Fst(Cmd<S, A> cmd) {
        this.cmd = cmd;
    }

    /**
     * Unwraps the command object.
     *
     * @param of The command selector/pattern
     */
    public void match(Case<S, A> of) {
        cmd.match(of);
    }
}
