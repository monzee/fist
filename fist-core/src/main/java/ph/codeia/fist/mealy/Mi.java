package ph.codeia.fist.mealy;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;

import ph.codeia.fist.Effects;
import ph.codeia.fist.Fn;

@SuppressWarnings("NewApi")
public final class Mi<S, E> {

    public static <S, E> Mi<S, E> noop() {
        return new Mi<>(Fst::noop);
    }

    public static <S, E> Mi<S, E> reenter() {
        return new Mi<>(Fst::reenter);
    }

    public static <S, E> Mi<S, E> enter(S newState) {
        return new Mi<>(sm -> sm.enter(newState));
    }

    public static <S, E> Mi<S, E> forward(Action<S, E> action) {
        return new Mi<>(sm -> sm.forward(action));
    }

    public static <S, E> Mi<S, E> async(Callable<Action<S, E>> thunk) {
        return new Mi<>(sm -> sm.async(thunk));
    }

    public static <S, E> Mi<S, E> raise(Throwable e) {
        return new Mi<>(sm -> sm.raise(e));
    }

    public static <S, E extends Effects<S>> Actor<S, E> bind(Runner<S, E> runner, E effects) {
        return new Actor<S, E>() {
            @Override
            public void start() {
                runner.start(effects);
            }

            @Override
            public void stop() {
                runner.stop();
            }

            @Override
            public void exec(Action<S, E> action) {
                runner.exec(effects, action);
            }
        };
    }

    private final Command<S, E> command;

    private Mi(Command<S, E> command) {
        this.command = command;
    }

    public void run(Fst<S, E> fst) {
        command.run(fst);
    }

    public Mi<S, E> then(Mi<S, E> next) {
        return new Mi<>(fst -> {
            run(fst);
            next.run(fst);
        });
    }

    public Mi<S, E> then(Mi.Action<S, E> action) {
        return then(Mi.forward(action));
    }

    public Mi<S, E> then(Callable<Mi.Action<S, E>> thunk) {
        return then(Mi.async(thunk));
    }

    private interface Command<S, E> {
        void run(Fst<S, E> fst);
    }

    public interface Action<S, E> {
        Mi<S, E> apply(S s, E e);

        static <S, E> Action<S, E> effect(Fn.Proc<E> proc) {
            return (s, e) -> {
                proc.receive(e);
                return Mi.noop();
            };
        }

        static <S, E> Action<S, E> effect(Fn.BiProc<S, E> proc) {
            return (s, e) -> {
                proc.receive(s, e);
                return Mi.noop();
            };
        }

        static <S, E> Action<S, E> pure(S state) {
            return (s, e) -> Mi.enter(state);
        }

        static <S, E> Action<S, E> pure(Mi<S, E> command) {
            return (s, e) -> command;
        }

        static <S, E> Action<S, E> pure(Callable<Action<S, E>> thunk) {
            return (s, e) -> Mi.async(thunk);
        }

        static <S, E> Action<S, E> pure(Fn.Func<S, S> f) {
            return (s, e) -> Mi.enter(f.apply(s));
        }

        static <S, E> Action<S, E> zero() {
            return (s, e) -> Mi.noop();
        }

        default Action<S, E> then(Action<S, E> action) {
            return (s, e) -> apply(s, e).then(action);
        }

        default Action<S, E> then(Callable<Action<S, E>> thunk) {
            return (s, e) -> apply(s, e).then(thunk);
        }

        default Action<S, E> then(Mi<S, E> command) {
            return (s, e) -> apply(s, e).then(pure(command));
        }

        default Action<S, E> then(S state) {
            return (s, e) -> apply(s, e).then(pure(state));
        }

        default Mi<S, E> after(Mi<S, E> command) {
            return command.then(this);
        }
    }

    public interface Fst<S, E> {
        void noop();
        void reenter();
        void enter(S newState);
        void forward(Action<S, E> action);
        void async(Callable<Action<S, E>> thunk);
        void raise(Throwable e);
    }

    public interface Runner<S, E extends Effects<S>> {
        void start(E effects);
        void stop();
        void exec(E effects, Action<S, E> action);
        <T> T project(Fn.Func<S, T> projection);

        default void inspect(Fn.Proc<S> consumer) {
            project(s -> {
                consumer.receive(s);
                return null;
            });
        }
    }

    public interface Actor<S, E extends Effects<S>> {
        void start();
        void stop();
        void exec(Action<S, E> action);
    }

}
