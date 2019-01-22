package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Callable;


public class SteppingFst<S, E extends Effects<S>> implements Fst.Binding<S, E> {

    public static <S> SteppingFst<S, ?> of(S initialState) {
        return new SteppingFst<>(initialState, o -> {});
    }

    private final Queue<Runnable> steps = new ArrayDeque<>();
    private final E effects;
    private S state;

    public SteppingFst(S initialState, E effects) {
        this.effects = effects;
        state = initialState;
    }

    public S step() {
        if (!steps.isEmpty()) {
            steps.remove().run();
            return state;
        }
        throw new IllegalStateException("nothing to do");
    }

    public S drain() {
        while (!steps.isEmpty()) {
            steps.remove().run();
        }
        return state;
    }

    @Override
    public void start() {
        effects.onEnter(state);
    }

    @Override
    public void stop() {
    }

    @Override
    public void exec(Mu.Action<S> action) {
        steps.add(() -> action.apply(state).run(new Mu.Case<S>() {
            boolean firstCall = true;

            @Override
            public void noop() {
            }

            @Override
            public void reenter() {
                if (firstCall) {
                    effects.onEnter(state);
                }
                else {
                    steps.add(() -> effects.onEnter(state));
                }
                firstCall = false;
            }

            @Override
            public void enter(S newState) {
                if (firstCall) {
                    state = newState;
                    effects.onEnter(newState);
                }
                else {
                    steps.add(() -> {
                        state = newState;
                        effects.onEnter(newState);
                    });
                }
                firstCall = false;
            }

            @Override
            public void forward(Mu.Action<S> action) {
                action.apply(state).run(this);
            }

            @Override
            public void async(Callable<Mu.Action<S>> block) {
                if (firstCall) {
                    try {
                        forward(block.call());
                    }
                    catch (Exception e) {
                        effects.handle(e);
                    }
                }
                else {
                    steps.add(() -> {
                        try {
                            forward(block.call());
                        }
                        catch (Exception e) {
                            effects.handle(e);
                        }
                    });
                }
                firstCall = false;
            }

            @Override
            public void defer(Fn.Proc<Mu.Continuation<S>> block) {
                Deferred<Mu.Action<S>> next = new Deferred<>();
                block.receive(next::offer);
                async(next);
            }

            @Override
            public void raise(Throwable e) {
                if (firstCall) {
                    effects.handle(e);
                }
                else {
                    steps.add(() -> effects.handle(e));
                }
                firstCall = false;
            }
        }));
    }

    @Override
    public void exec(Mi.Action<S, E> action) {
        steps.add(() -> action.apply(state, effects).run(new Mi.Case<S, E>() {
            boolean firstCall = true;

            @Override
            public void noop() {
            }

            @Override
            public void reenter() {
                if (firstCall) {
                    effects.onEnter(state);
                }
                else {
                    steps.add(() -> effects.onEnter(state));
                }
                firstCall = false;
            }

            @Override
            public void enter(S newState) {
                if (firstCall) {
                    state = newState;
                    effects.onEnter(newState);
                }
                else {
                    steps.add(() -> {
                        state = newState;
                        effects.onEnter(newState);
                    });
                }
                firstCall = false;
            }

            @Override
            public void forward(Mi.Action<S, E> action) {
                action.apply(state, effects).run(this);
            }

            @Override
            public void async(Callable<Mi.Action<S, E>> block) {
                if (firstCall) {
                    try {
                        forward(block.call());
                    }
                    catch (Exception e) {
                        effects.handle(e);
                    }
                }
                else {
                    steps.add(() -> {
                        try {
                            forward(block.call());
                        }
                        catch (Exception e) {
                            effects.handle(e);
                        }
                    });
                }
                firstCall = false;
            }

            @Override
            public void defer(Fn.Proc<Mi.Continuation<S, E>> block) {
                Deferred<Mi.Action<S, E>> next = new Deferred<>();
                block.receive(next::offer);
                async(next);
            }

            @Override
            public void raise(Throwable e) {
                if (firstCall) {
                    effects.handle(e);
                }
                else {
                    steps.add(() -> effects.handle(e));
                }
                firstCall = false;
            }
        }));
    }

    @Override
    public <T> T project(Fn.Func<S, T> projection) {
        return projection.apply(state);
    }
}

