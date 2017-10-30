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
@SuppressWarnings("NewApi")
public final class Mu<S> {

    public static <S> Mu<S> noop() {
        return new Mu<>(Machine::noop);
    }

    public static <S> Mu<S> enter(S newState) {
        return new Mu<>(sm -> sm.enter(newState));
    }

    public static <S> Mu<S> reenter() {
        return new Mu<>(Machine::reenter);
    }

    public static <S> Mu<S> reduce(Action<S> action) {
        return new Mu<>(sm -> sm.forward(action));
    }

    public static <S> Mu<S> reduce(Callable<Action<S>> thunk) {
        return new Mu<>(sm -> sm.async(thunk));
    }

    public static <S> Mu<S> raise(Throwable e) {
        return new Mu<>(sm -> sm.raise(e));
    }

    public static <S> Actor<S> bind(Runner<S> runner, Effects<S> effects) {
        return new Actor<S>() {
            @Override
            public void start() {
                runner.start(effects);
            }

            @Override
            public void stop() {
                runner.stop();
            }

            @Override
            public void exec(Action<S> action) {
                runner.exec(effects, action);
            }
        };
    }

    private final Command<S> command;

    private Mu(Command<S> command) {
        this.command = command;
    }

    public void run(Machine<S> machine) {
        command.run(machine);
    }

    public Mu<S> then(Mu<S> next) {
        return new Machine<S>() {
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
            public void forward(Action<S> action) {
                merged = new Mu<>(sm -> {
                    run(sm);
                    sm.forward(action);
                });
            }

            @Override
            public void async(Callable<Action<S>> thunk) {
                merged = new Mu<>(sm -> {
                    run(sm);
                    sm.async(thunk);
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
        void run(Machine<S> sm);
    }

    public interface Action<S> {
        Mu<S> apply(S state);

        static <S> Action<S> pure(S state) {
            return s -> Mu.enter(state);
        }

        static <S> Action<S> pure(Mu<S> command) {
            return s -> command;
        }

        static <S> Action<S> pure(Callable<Action<S>> thunk) {
            return s -> Mu.reduce(thunk);
        }

        static <S> Action<S> zero() {
            return s -> Mu.noop();
        }

        default Action<S> then(S state) {
            return s -> apply(s).then(pure(state));
        }

        default Action<S> then(Mu<S> command) {
            return s -> apply(s).then(pure(command));
        }

        default Action<S> then(Action<S> action) {
            return s -> apply(s).then(action);
        }

        default Action<S> then(Callable<Action<S>> thunk) {
            return s -> apply(s).then(thunk);
        }

        default Action<S> after(Action<S> action) {
            return action.then(this);
        }

        default Mu<S> after(Mu<S> command) {
            return command.then(this);
        }
    }

    public interface Machine<S> {
        void noop();
        void enter(S newState);
        void reenter();
        void forward(Action<S> action);
        void async(Callable<Action<S>> thunk);
        void raise(Throwable e);
    }

    public interface Runner<S> {
        void start(Effects<S> effects);
        void stop();
        void exec(Effects<S> effects, Action<S> action);
        <T> T inspect(Function<S, T> projection);
    }

    public interface Effects<S> {
        void onEnter(S s);

        default void handle(Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public interface Actor<S> {
        void start();
        void stop();
        void exec(Action<S> action);
    }

    public interface Function<S, T> {
        T apply(S s);
    }
}

