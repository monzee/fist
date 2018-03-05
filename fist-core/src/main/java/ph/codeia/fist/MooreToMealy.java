package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;

/**
 * Converts a {@link Mu.Action} to a {@link Mi.Action}.
 *
 * @param <S> The state type
 * @param <E> The receiver type
 */
public class MooreToMealy<S, E extends Effects<S>> implements Mi.Action<S, E> {

    private final Mu.Action<S> source;

    /**
     * @param source The Moore action to convert
     */
    public MooreToMealy(Mu.Action<S> source) {
        this.source = source;
    }

    @Override
    public Mi<S, E> apply(S state, E ignored) {
        return new Mu.Case<S>() {
            Mi<S, E> command;

            {
                source.apply(state).run(this);
            }

            @Override
            public void noop() {
                command = Mi.noop();
            }

            @Override
            public void enter(S newState) {
                command = Mi.enter(newState);
            }

            @Override
            public void reenter() {
                command = Mi.reenter();
            }

            @Override
            public void forward(Mu.Action<S> action) {
                command = Mi.forward(new MooreToMealy<>(action));
            }

            @Override
            public void async(Callable<Mu.Action<S>> block) {
                command = Mi.async(() -> new MooreToMealy<>(block.call()));
            }

            @Override
            public void defer(Fn.Proc<Mu.Continuation<S>> block) {
                command = Mi.defer(inner -> block.receive(
                        nextAction -> inner.resume(new MooreToMealy<>(nextAction))
                ));
            }

            @Override
            public void raise(Throwable e) {
                command = Mi.raise(e);
            }
        }.command;
    }

}
