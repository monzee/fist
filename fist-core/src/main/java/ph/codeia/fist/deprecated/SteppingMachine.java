package ph.codeia.fist.deprecated;

/*
 * This file is a part of the fist project.
 */

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Callable;

public class SteppingMachine<S, O extends Fst.ErrorHandler, A extends Fst.Action<S, O, A>>
        implements Fst.Machine<S, O, A>
{
    private final Queue<A> actions = new ArrayDeque<>();
    private final A enter;
    private S state;
    private boolean isStarted = false;

    public SteppingMachine(A enter, S state) {
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
        if (isStarted) {
            exec(action);
        }
    }

    public void exec(A action) {
        actions.add(action);
    }

    public void drain(O actor) {
        //noinspection StatementWithEmptyBody
        while (step(actor));
    }

    public boolean step(O actor) {
        A action = actions.remove();
        if (action == null) {
            return false;
        }
        action.apply(state, actor).match(new Fst.Case<S, A>() {
            @Override
            public void noop() {
            }

            @Override
            public void enter(S newState) {
                state = newState;
                exec(enter);
            }

            @Override
            public void reenter() {
                exec(enter);
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
                exec(newAction);
            }

            @Override
            public void async(Callable<A> thunk) {
                try {
                    exec(thunk.call());
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
        return true;
    }
}
