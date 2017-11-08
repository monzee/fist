package ph.codeia.fist.moore;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;

import ph.codeia.fist.Effects;
import ph.codeia.fist.Fn;
import ph.codeia.fist.mealy.Mi;

/**
 * Strict Moore finite state transducer.
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

    public static <S> Mu<S> forward(Action<S> action) {
        return new Mu<>(sm -> sm.forward(action));
    }

    public static <S> Mu<S> async(Callable<Action<S>> thunk) {
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
        return new Mu<>(sm -> {
            run(sm);
            next.run(sm);
        });
    }

    public Mu<S> then(Action<S> action) {
        return then(forward(action));
    }

    public Mu<S> then(Callable<Action<S>> thunk) {
        return then(async(thunk));
    }

    public Mu<S> after(Mu<S> prev) {
        return prev.then(this);
    }

    public Mu<S> after(Action<S> action) {
        return forward(action).then(this);
    }

    public Mu<S> after(Callable<Action<S>> thunk) {
        return async(thunk).then(this);
    }

    private interface Command<S> {
        void run(Machine<S> machine);
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
            return s -> Mu.async(thunk);
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

        default Mu<S> after(Mu<S> command) {
            return command.then(this);
        }

        default <E extends Effects<S>> Mi.Action<S, E> toMealy() {
            return new MealyBridge<>(this);
        }
    }

    public interface Machine<S> {
        void noop();
        void reenter();
        void enter(S newState);
        void forward(Action<S> action);
        void async(Callable<Action<S>> thunk);
        void raise(Throwable e);
    }

    public interface Runner<S> {
        void start(Effects<S> effects);
        void stop();
        void exec(Effects<S> effects, Action<S> action);
        <T> T project(Fn.Func<S, T> projection);

        default void inspect(Fn.Proc<S> consumer) {
            project(s -> {
                consumer.receive(s);
                return null;
            });
        }
    }

    public interface Actor<S> {
        void start();
        void stop();
        void exec(Action<S> action);
    }

}

