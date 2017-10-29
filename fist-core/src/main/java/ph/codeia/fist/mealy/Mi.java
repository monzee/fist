package ph.codeia.fist.mealy;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;

import ph.codeia.fist.moore.Mu;

@SuppressWarnings("NewApi")
public final class Mi<S, C> {

    public static <S, C> Mi<S, C> noop() {
        return new Mi<>(Machine::noop);
    }

    public static <S, C> Mi<S, C> reenter() {
        return new Mi<>(Machine::reenter);
    }

    public static <S, C> Mi<S, C> enter(S newState) {
        return new Mi<>(sm -> sm.enter(newState));
    }

    public static <S, C> Mi<S, C> reduce(Action<S, C> action) {
        return new Mi<>(sm -> sm.reduce(action));
    }

    public static <S, C> Mi<S, C> reduce(Callable<Action<S, C>> thunk) {
        return new Mi<>(sm -> sm.reduce(thunk));
    }

    public static <S, C> Mi<S, C> raise(Throwable e) {
        return new Mi<>(sm -> sm.raise(e));
    }

    public static <S, C extends Effects<S>> Actor<S, C> bind(Runner<S, C> runner, C context) {
        return new Actor<S, C>() {
            @Override
            public void start() {
                runner.start(context);
            }

            @Override
            public void stop() {
                runner.stop();
            }

            @Override
            public void exec(Action<S, C> action) {
                runner.exec(context, action);
            }
        };
    }

    private final Command<S, C> command;

    private Mi(Command<S, C> command) {
        this.command = command;
    }

    public void run(Machine<S, C> sm) {
        command.run(sm);
    }

    public Mi<S, C> then(Mi<S, C> next) {
        return new Mi<>(command.then(next.command));
    }

    public Mi<S, C> then(Mi.Action<S, C> action) {
        return then(Mi.reduce(action));
    }

    public Mi<S, C> then(Callable<Mi.Action<S, C>> thunk) {
        return then(Mi.reduce(thunk));
    }

    private interface Command<S, C> {
        void run(Machine<S, C> sm);

        default Command<S, C> then(Command<S, C> next) {
            return new Machine<S, C>() {
                Command<S, C> merged = Command.this;
                {
                    next.run(this);
                }

                @Override
                public void noop() {
                }

                @Override
                public void reenter() {
                    merged = sm -> {
                        run(sm);
                        sm.reenter();
                    };
                }

                @Override
                public void enter(S newState) {
                    merged = sm -> {
                        run(sm);
                        sm.enter(newState);
                    };
                }

                @Override
                public void reduce(Action<S, C> action) {
                    merged = sm -> {
                        run(sm);
                        sm.reduce(action);
                    };
                }

                @Override
                public void reduce(Callable<Action<S, C>> thunk) {
                    merged = sm -> {
                        run(sm);
                        sm.reduce(thunk);
                    };
                }

                @Override
                public void raise(Throwable e) {
                    merged = sm -> {
                        run(sm);
                        sm.raise(e);
                    };
                }
            }.merged;
        }
    }

    public interface Action<S, C> {
        Mi<S, C> apply(S s, C c);

        static <S, C> Action<S, C> effect(Proc<C> proc) {
            return (s, c) -> {
                proc.receive(c);
                return Mi.noop();
            };
        }

        static <S, C> Action<S, C> pure(S state) {
            return (s, c) -> Mi.enter(state);
        }

        static <S, C> Action<S, C> pure(Mi<S, C> command) {
            return (s, c) -> command;
        }

        static <S, C> Action<S, C> pure(Callable<Action<S, C>> thunk) {
            return (s, c) -> Mi.reduce(thunk);
        }

        static <S, C> Action<S, C> pure(Mu.Function<S, S> f) {
            return (s, c) -> Mi.enter(f.apply(s));
        }

        static <S, C> Action<S, C> zero() {
            return (s, c) -> Mi.noop();
        }

        default Action<S, C> then(Action<S, C> action) {
            return (s, c) -> apply(s, c).then(action);
        }

        default Action<S, C> then(Callable<Action<S, C>> action) {
            return (s, c) -> apply(s, c).then(action);
        }

        default Action<S, C> then(Mi<S, C> command) {
            return (s, c) -> apply(s, c).then(pure(command));
        }

        default Action<S, C> then(S state) {
            return (s, c) -> apply(s, c).then(pure(state));
        }

        default Action<S, C> after(Action<S, C> action) {
            return action.then(this);
        }

        default Mi<S, C> after(Mi<S, C> command) {
            return command.then(this);
        }
    }

    public interface Machine<S, C> {
        void noop();
        void reenter();
        void enter(S newState);
        void reduce(Action<S, C> action);
        void reduce(Callable<Action<S, C>> thunk);
        void raise(Throwable e);
    }

    public interface Runner<S, C extends Effects<S>> {
        void start(C context);
        void stop();
        void exec(C context, Action<S, C> action);
        <T> T inspect(Mu.Function<S, T> projection);
    }

    public interface Effects<S> {
        void onEnter(S s);
        default void handle(Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public interface Actor<S, C extends Effects<S>> {
        void start();
        void stop();
        void exec(Action<S, C> action);
    }

    public interface Proc<T> {
        void receive(T t);
    }

}
