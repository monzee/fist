package ph.codeia.fist.deprecated;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;

public class BlockingMachine<S, O extends Fst.ErrorHandler, A extends Fst.Action<S, O, A>>
        implements Fst.Machine<S, O, A>
{
    private final A enter;
    private S state;
    private boolean isStarted = false;

    public BlockingMachine(A enter, S state) {
        this.enter = enter;
        this.state = state;
    }

    @Override
    public void start(O actor) {
        isStarted = true;
        exec(actor, enter);
    }

    @Override
    public void stop() {
        isStarted = false;
    }

    @Override
    public void exec(O actor, A action) {
        if (!isStarted) {
            return;
        }
        action.apply(state, actor).match(new Fst.Case<S, A>() {
            @Override
            public void noop() {
            }

            @Override
            public void enter(S newState) {
                state = newState;
                exec(actor, enter);
            }

            @Override
            public void reenter() {
                exec(actor, enter);
            }

            @Override
            public void move(S newState) {
                state = newState;
            }

            @Override
            public void raise(Throwable error) {
                actor.handle(error);
            }

            @Override
            public void forward(A newAction) {
                exec(actor, newAction);
            }

            @Override
            public void async(Callable<A> thunk) {
                try {
                    exec(actor, thunk.call());
                }
                catch (Exception e) {
                    actor.handle(e);
                }
            }

            @Override
            public void async(S intermediate, Callable<A> thunk) {
                enter(intermediate);
                async(thunk);
            }

            @Override
            public void stream(Fst.Daemon<S> proc, A receiver) {

            }
        });
    }
}
