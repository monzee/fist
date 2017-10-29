package ph.codeia.fist.moore;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;

/**
 * Strict Moore finite state transducer.
 * <p>
 * The big difference from the other FST type (Mealy) is that the transition
 * methods of the machine are not supposed to produce output and thus do not
 * receive an actor instance. The transitions are just for computing the new
 * state given some input.
 * <p>
 * The result is that you cannot have side effects in your actions. That's
 * the idea at least, it's up to the programmer to obey or not. It's always
 * possible to invoke some outside method that produces observable changes
 * from inside an action.
 * <p>
 * For this abstraction to be of any use at all, it should be able to generate
 * output at some point. In the Moore description, there exists a function
 * {@code G :: State -> Output}, meaning every possible state has a
 * corresponding output. In this implementation, the output is not a value but
 * a side effect, so we use the dual of the Output type and bring it to the
 * other side of the arrow. This is the actor ({@code O}) type parameter and we
 * constrain the state ({@code S}) type to be able to produce some output from
 * an actor. The machine then invokes {@code State#render(Object)} after every
 * transition to produce an output.
 *
 * @param <S> The state type
 * @author Mon Zafra
 * @since 0.1.0
 */
public final class Mu<S> {

    public static <S> Mu<S> noop() {
        return new Mu<>(Processor::noop);
    }

    public static <S> Mu<S> enter(S newState) {
        return new Mu<>(sm -> sm.enter(newState));
    }

    public static <S> Mu<S> reenter() {
        return new Mu<>(Processor::reenter);
    }

    public static <S> Mu<S> reduce(Action<S> action) {
        return new Mu<>(sm -> sm.reduce(action));
    }

    public static <S> Mu<S> reduce(Callable<Action<S>> thunk) {
        return new Mu<>(sm -> sm.reduce(thunk));
    }

    public static <S> Mu<S> raise(Throwable e) {
        return new Mu<>(sm -> sm.raise(e));
    }

    public static <S> Actor<S> bind(Runner<S> runner, Context<S> context) {
        return new Actor<S>() {
            @Override
            public void start() {
                runner.start(context);
            }

            @Override
            public void stop() {
                runner.stop();
            }

            @Override
            public void exec(Action<S> action) {
                runner.exec(context, action);
            }
        };
    }

    private final Command<S> command;

    private Mu(Command<S> command) {
        this.command = command;
    }

    public void run(Processor<S> machine) {
        command.run(machine);
    }

    public Mu<S> then(Mu<S> next) {
        return new Processor<S>() {
            Mu<S> merged = Mu.this;

            {
                next.run(this);
            }

            @Override
            public void noop() {
            }

            @Override
            public void enter(S newState) {
                merged = new Mu<>(sm -> {
                    run(sm);
                    sm.enter(newState);
                });
            }

            @Override
            public void reenter() {
                merged = new Mu<>(sm -> {
                    run(sm);
                    sm.reenter();
                });
            }

            @Override
            public void reduce(Action<S> action) {
                merged = new Mu<>(sm -> {
                    run(sm);
                    sm.reduce(action);
                });
            }

            @Override
            public void reduce(Callable<Action<S>> thunk) {
                merged = new Mu<>(sm -> {
                    run(sm);
                    sm.reduce(thunk);
                });
            }

            @Override
            public void raise(Throwable e) {
                merged = new Mu<>(sm -> {
                    run(sm);
                    sm.raise(e);
                });
            }
        }.merged;
    }

    public Mu<S> then(Action<S> action) {
        return then(reduce(action));
    }

    public Mu<S> then(Callable<Action<S>> thunk) {
        return then(reduce(thunk));
    }

    private interface Command<S> {
        void run(Processor<S> sm);
    }

    public interface Action<S> {
        Mu<S> apply(S state);
    }

    public interface Processor<S> {
        void noop();
        void enter(S newState);
        void reenter();
        void reduce(Action<S> action);
        void reduce(Callable<Action<S>> thunk);
        void raise(Throwable e);
    }

    public interface Runner<S> {
        void start(Context<S> context);
        void stop();
        void exec(Context<S> context, Action<S> action);
        <T> T inspect(Function<S, T> projection);
    }

    public interface Context<S> {
        void onEnter(S s);
    }

    public interface Actor<S> {
        void start();
        void stop();
        void exec(Action<S> action);
    }

    public interface ErrorHandler {
        void handle(Throwable e);

        ErrorHandler IGNORE = e -> {};

        ErrorHandler RETHROW = e -> {
            throw new RuntimeException(e);
        };
    }

    public interface Function<S, T> {
        T apply(S s);
    }
}

