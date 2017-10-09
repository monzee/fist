package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

public class BoundMachine<S, O, A extends Fst.Action<S, O, A>> {
    private final Fst.Machine<S, O, A> delegate;
    private final O actor;

    public BoundMachine(Fst.Machine<S, O, A> delegate, O actor) {
        this.delegate = delegate;
        this.actor = actor;
    }

    public void start() {
        delegate.start(actor);
    }

    public void stop() {
        delegate.stop();
    }

    public void exec(A action) {
        delegate.exec(actor, action);
    }
}
