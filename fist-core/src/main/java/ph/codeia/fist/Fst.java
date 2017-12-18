package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import ph.codeia.fist.mealy.Mi;
import ph.codeia.fist.moore.Mu;

@SuppressWarnings("NewApi")
public interface Fst<S> {

    interface Actor<S, E extends Effects<S>> {
        void start();
        void stop();
        void exec(Mu.Action<S> action);
        void exec(Mi.Action<S, E> action);

        default <T> T project(Fn.Func<S, T> projection) {
            return new Mu.Action<S>() {
                T t;
                { exec(this); }

                @Override
                public Mu<S> apply(S state) {
                    t = projection.apply(state);
                    return Mu.noop();
                }
            }.t;
        }

        default void inspect(Fn.Proc<S> proc) {
            project(state -> {
                proc.receive(state);
                return null;
            });
        }
    }

    interface Builder {
        <S> Fst<S> build(S state);
    }

    void start(Effects<S>... effects);
    void stop();
    void exec(Effects<S> effects, Mu.Action<S> action);
    <E extends Effects<S>> void exec(E effects, Mi.Action<S, E> action);
    <T> T project(Fn.Func<S, T> projection);

    default void inspect(Fn.Proc<S> proc) {
        project(state -> {
            proc.receive(state);
            return null;
        });
    }

    default <E extends Effects<S>> Actor<S, E> bind(E effects) {
        return new Actor<S, E>() {
            @Override
            public void start() {
                //noinspection unchecked
                Fst.this.start(effects);
            }

            @Override
            public void stop() {
                Fst.this.stop();
            }

            @Override
            public void exec(Mu.Action<S> action) {
                Fst.this.exec(effects, action);
            }

            @Override
            public void exec(Mi.Action<S, E> action) {
                Fst.this.exec(effects, action);
            }

            @Override
            public <T> T project(Fn.Func<S, T> projection) {
                return Fst.this.project(projection);
            }
        };
    }
}
