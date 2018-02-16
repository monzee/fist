package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import org.junit.Test;

import static org.junit.Assert.*;

public class MonoidalCompositionTest {

    static Mu.Action<Integer> plus(int n) {
        return Mu.Action.pure(m -> m + n);
    }

    static class Noop implements Effects<Integer> {
        @Override
        public void onEnter(Integer integer) {
        }
    }

    static class Counter implements Effects<Integer> {
        int count = 0;

        @Override
        public void onEnter(Integer integer) {
            count++;
        }
    }

    static <E extends Effects<Integer>> Mi.Action<Integer, E> times(int n) {
        return Mi.Action.pure(m -> m * n);
    }

    static final Noop NOOP = new Noop();

    @Test
    public void action_then_action() {
        Fst.Binding<Integer, Noop> fst = new BlockingFst<>(0).bind(NOOP);
        fst.start();
        fst.exec(plus(1).then(plus(10)));
        fst.inspect(n -> assertEquals(11, n.intValue()));
        Mi.Action<Integer, Noop> x3 = times(3);
        fst.exec(x3.then(times(5)));
        fst.inspect(n -> assertEquals(165, n.intValue()));
    }

    @Test
    public void cmd_then_action() {
        Fst.Binding<Integer, Noop> fst = new BlockingFst<>(0).bind(NOOP);
        fst.start();
        fst.exec(n -> Mu.enter(1).then(plus(2)));
        fst.inspect(n -> assertEquals(3, n.intValue()));
        fst.exec((n, _e) -> Mi.<Integer, Noop> noop().then(times(5)));
        fst.inspect(n -> assertEquals(15, n.intValue()));
    }

    @Test
    public void action_then_cmd() {
        Counter c = new Counter();
        Fst.Binding<Integer, Counter> fst = new BlockingFst<>(0).bind(c);
        fst.start();
        assertEquals(1, c.count);
        fst.exec(plus(1).then(Mu.forward(plus(1023))));
        fst.inspect(n -> assertEquals(1024, n.intValue()));
        Mi.Action<Integer, Counter> x2 = times(2);
        fst.exec(x2.then(Mi.reenter()).then(times(2)));
        fst.inspect(n -> assertEquals(4096, n.intValue()));
        assertEquals(6, c.count);
    }

    @Test
    public void action_then_state() {
        Counter c = new Counter();
        Fst.Binding<Integer, Counter> fst = new BlockingFst<>(0).bind(c);
        fst.start();
        fst.exec(plus(1).then(123));
        fst.inspect(n -> assertEquals(123, n.intValue()));
        assertEquals(3, c.count);
        Mi.Action<Integer, Counter> x1 = times(1);
        fst.exec(x1.then(0));
        fst.inspect(n -> assertEquals(0, n.intValue()));
        assertEquals(5, c.count);
    }

    @Test
    public void cmd_then_cmd() {
        Counter c = new Counter();
        Fst.Binding<Integer, Counter> fst = new BlockingFst<>(0).bind(c);
        fst.start();
        fst.exec(_s -> Mu.<Integer> reenter().then(Mu.reenter()));
        assertEquals(3, c.count);
        fst.exec((_s, _e) -> Mi.<Integer, Counter> reenter().then(Mi.reenter()));
        assertEquals(5, c.count);
    }

    @Test
    public void cmd_then_thunk() {
        Counter c = new Counter();
        Fst.Binding<Integer, Counter> fst = new BlockingFst<>(0).bind(c);
        fst.start();
        fst.exec(_n -> Mu.<Integer> reenter().then(() -> _m -> Mu.reenter()));
        assertEquals(3, c.count);
        fst.exec((_s, _e) -> Mi.<Integer, Counter> enter(2).then(() -> times(3)));
        fst.inspect(n -> assertEquals(6, n.intValue()));
        assertEquals(5, c.count);
    }

    @Test
    public void action_then_thunk() {
        Fst.Binding<Integer, Noop> fst = new BlockingFst<>(0).bind(NOOP);
        fst.start();
        fst.exec(plus(1).then(() -> plus(10)));
        fst.inspect(n -> assertEquals(11, n.intValue()));
        Mi.Action<Integer, Noop> x5 = times(5);
        fst.exec(x5.then(() -> times(2)));
        fst.inspect(n -> assertEquals(110, n.intValue()));
    }

    @Test
    public void cmd_after_action() {
        Counter c = new Counter();
        Fst.Binding<Integer, Counter> fst = new BlockingFst<>(0).bind(c);
        fst.start();
        fst.exec(_s -> Mu.<Integer> reenter().after(plus(1)));
        fst.exec((_s, _e) -> Mi.<Integer, Counter> forward(times(2)).after(times(10)));
        fst.inspect(n -> assertEquals(20, n.intValue()));
        assertEquals(5, c.count);
    }

    @Test
    public void action_after_action() {
        Fst.Binding<Integer, Noop> fst = new BlockingFst<>(0).bind(NOOP);
        fst.start();
        fst.exec(plus(3).after(plus(1)));
        fst.inspect(n -> assertEquals(4, n.intValue()));
        Mi.Action<Integer, Noop> x3 = times(3);
        fst.exec(x3.after(times(4)));
        fst.inspect(n -> assertEquals(48, n.intValue()));
    }

    @Test
    public void action_after_command() {
        Fst.Binding<Integer, Noop> fst = new BlockingFst<>(0).bind(NOOP);
        fst.start();
        fst.exec(_n -> plus(4).after(Mu.enter(1)));
        fst.inspect(n -> assertEquals(5, n.intValue()));
        Mi.Action<Integer, Noop> x3 = times(3);
        fst.exec((_s, _e) -> x3.after(Mi.enter(10)));
        fst.inspect(n -> assertEquals(30, n.intValue()));
    }

    @Test
    public void action_after_state() {
        Fst.Binding<Integer, Noop> fst = new BlockingFst<>(0).bind(NOOP);
        fst.start();
        fst.exec(plus(3).after(5));
        fst.inspect(n -> assertEquals(8, n.intValue()));
        Mi.Action<Integer, Noop> x2 = times(2);
        fst.exec(x2.after(3));
        fst.inspect(n -> assertEquals(6, n.intValue()));
    }

    @Test
    public void cmd_after_cmd() {
        Counter c = new Counter();
        Fst.Binding<Integer, Counter> fst = new BlockingFst<>(0).bind(c);
        fst.start();
        fst.exec(_s -> Mu.<Integer> reenter().after(Mu.reenter()));
        assertEquals(3, c.count);
        fst.exec((_s, _e) -> Mi.<Integer, Counter> reenter().after(Mi.reenter()));
        assertEquals(5, c.count);
    }

    @Test
    public void cmd_after_thunk() {
        Counter c = new Counter();
        Fst.Binding<Integer, Counter> fst = new BlockingFst<>(0).bind(c);
        fst.start();
        fst.exec(_n -> Mu.<Integer> reenter().after(() -> _m -> Mu.reenter()));
        assertEquals(3, c.count);
        fst.exec((_s, _e) -> Mi.<Integer, Counter> enter(7).after(() -> times(3)));
        fst.inspect(n -> assertEquals(7, n.intValue()));
        assertEquals(5, c.count);
    }

    @Test
    public void action_after_thunk() {
        Fst.Binding<Integer, Noop> fst = new BlockingFst<>(0).bind(NOOP);
        fst.start();
        fst.exec(_s -> Mu.async(plus(1).after(() -> plus(10))));
        fst.inspect(n -> assertEquals(11, n.intValue()));
        Mi.Action<Integer, Noop> x5 = times(5);
        fst.exec((_s, _e) -> Mi.async(x5.after(() -> times(2))));
        fst.inspect(n -> assertEquals(110, n.intValue()));
    }
}
