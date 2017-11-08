package ph.codeia.fist.moore;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;

import ph.codeia.fist.Effects;
import ph.codeia.fist.mealy.Mi;

public class MealyBridge<S, E extends Effects<S>> implements Mi.Action<S, E> {

    private final Mu.Action<S> source;

    public MealyBridge(Mu.Action<S> source) {
        this.source = source;
    }

    @Override
    public Mi<S, E> apply(S state, E ignored) {
        return new Mu.Machine<S>() {
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
                command = Mi.forward(new MealyBridge<>(action));
            }

            @Override
            public void async(Callable<Mu.Action<S>> thunk) {
                command = Mi.async(() -> new MealyBridge<>(thunk.call()));
            }

            @Override
            public void raise(Throwable e) {
                command = Mi.raise(e);
            }
        }.command;
    }

}
