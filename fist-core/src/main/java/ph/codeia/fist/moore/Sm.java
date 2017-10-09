package ph.codeia.fist.moore;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;

/**
 * Strict Moore finite state transducer.
 * <p>
 * The big difference from the other FST type (Mealy) is that the transition
 * methods of the machine do not receive an actor instance. The new state is
 * just computed from the old state and some input and no output is generated.
 * <p>
 * The result is that you cannot have side effects in your actions. That's
 * the idea at least, it's up to the programmer to obey or not. It's always
 * possible to invoke some outside method that produces observable system
 * changes from inside an action.
 * <p>
 * For this abstraction to be of any use at all, it should be able to generate
 * output. In the Moore description, there exists a function
 * {@code G :: State -> Output}, meaning every possible state has a
 * corresponding output. In this implementation, the output is not a value but
 * a side effect, so we use the dual of the Output type and bring it to the
 * other side of the arrow. This is the actor ({@code O}) type parameter and we
 * constrain the state ({@code S}) type to be able to produce some output from
 * an actor. The machine then invokes {@link State#render(Object)} after every
 * transition to produce an output.
 *
 * @param <S> The state type
 * @param <A> The action type
 * @author Mon Zafra
 * @since 0.1.0
 */
public final class Sm<S extends Sm.State<S, ?, A>, A extends Sm.Action<S, A>> {

    public interface State<S extends State<S, O, A>, O, A extends Action<S, A>> {
        Sm<S, A> render(O actor);
    }

    public interface Action<S extends State<S, ?, A>, A extends Action<S, A>> {
        Sm<S, A> apply(S state);
    }

    public interface Machine<S extends State<S, O, A>, O, A extends Action<S, A>> {
        void start(O actor);
        void stop();
        void exec(O actor, A action);
    }

    public interface Case<S extends State<S, ?, A>, A extends Action<S, A>> {
        void noop();
        void enter(S newState);
        void reenter();
        void move(S newState);
        void async(Callable<A> thunk);
        void async(S intermediate, Callable<A> thunk);
        void parallel(Sm<S, A>... cmds);
        void peek(S state);
    }

    private interface Cmd<S extends State<S, ?, A>, A extends Action<S, A>> {
        void match(Case<S, A> select);
    }

    public static <S extends State<S, ?, A>, A extends Action<S, A>>
    Sm<S, A> noop() {
        return new Sm<>(Case::noop);
    }

    public static <S extends State<S, ?, A>, A extends Action<S, A>>
    Sm<S, A> enter(S newState) {
        return new Sm<>(e -> e.enter(newState));
    }

    public static <S extends State<S, ?, A>, A extends Action<S, A>>
    Sm<S, A> reenter() {
        return new Sm<>(Case::reenter);
    }

    public static <S extends State<S, ?, A>, A extends Action<S, A>>
    Sm<S, A> move(S newState) {
        return new Sm<>(e -> e.move(newState));
    }

    public static <S extends State<S, ?, A>, A extends Action<S, A>>
    Sm<S, A> async(Callable<A> thunk) {
        return new Sm<>(e -> e.async(thunk));
    }

    public static <S extends State<S, ?, A>, A extends Action<S, A>>
    Sm<S, A> async(S intermediate, Callable<A> thunk) {
        return new Sm<>(e -> e.async(intermediate, thunk));
    }

    @SafeVarargs
    public static <S extends State<S, ?, A>, A extends Action<S, A>>
    Sm<S, A> parallel(Sm<S, A>... cmds) {
        return new Sm<>(e -> e.parallel(cmds));
    }

    public static <S extends State<S, ?, A>, A extends Action<S, A>>
    Sm<S, A> peek(S state) {
        return new Sm<>(e -> e.peek(state));
    }

    private final Cmd<S, A> cmd;
    private Sm(Cmd<S, A> cmd) {
        this.cmd = cmd;
    }

    public void match(Case<S, A> select) {
        cmd.match(select);
    }

    void exec(S s, A action) {
        action.apply(s).match(new Case<S, A>() {
            @Override
            public void noop() {

            }

            @Override
            public void enter(S newState) {

            }

            @Override
            public void reenter() {

            }

            @Override
            public void move(S newState) {

            }

            @Override
            public void async(Callable<A> thunk) {

            }

            @Override
            public void async(S intermediate, Callable<A> thunk) {

            }

            @Override
            public void parallel(Sm<S, A>... cmds) {
                for (Sm<S, A> c : cmds) {
                    c.exec(s, action);
                }
            }

            @Override
            public void peek(S state) {

            }
        });
    }
}

