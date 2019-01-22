package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class SteppingFstMooreTest {
    @Test
    public void does_not_execute_action_until_step_is_called() {
        SteppingFst<Integer, ?> stepper = SteppingFst.of(0);
        AtomicBoolean called = new AtomicBoolean(false);
        stepper.exec(Mu.Action.pure(123));
        stepper.exec(o -> {
            called.set(true);
            return Mu.enter(456);
        });
        stepper.inspect(n -> assertEquals(0, n.intValue()));
        assertEquals(123, stepper.step().intValue());
        assertFalse(called.get());
        assertEquals(456, stepper.step().intValue());
        assertTrue(called.get());
    }

    @Test(expected = IllegalStateException.class)
    public void throws_when_queue_is_empty() {
        SteppingFst<Void, ?> stepper = SteppingFst.of(null);
        stepper.step();
    }

    @Test
    public void async_is_run_synchronously() {
        SteppingFst<Void, ?> stepper = SteppingFst.of(null);
        AtomicBoolean called = new AtomicBoolean(false);
        stepper.exec(n -> Mu.async(() -> {
            called.set(true);
            return o -> Mu.noop();
        }));
        assertFalse(called.get());
        stepper.step();
        assertTrue(called.get());
    }

    @Test
    public void defer_is_awaited_synchronously() {
        SteppingFst<Void, ?> stepper = SteppingFst.of(null);
        AtomicBoolean called = new AtomicBoolean(false);
        ExecutorService E = Executors.newSingleThreadExecutor();
        stepper.exec(n -> Mu.defer(continuation -> E.execute(() -> {
            called.set(true);
            continuation.resume(o -> Mu.noop());
        })));
        assertFalse(called.get());
        stepper.step();
        assertTrue(called.get());
        E.shutdown();
    }

    @Test
    public void forwarded_action_is_run_on_the_same_step() {
        SteppingFst<Integer, ?> stepper = SteppingFst.of(0);
        AtomicBoolean called = new AtomicBoolean(false);
        stepper.exec(o -> Mu.enter(123).then(Mu.forward(n -> {
            called.set(true);
            return Mu.enter(456);
        })));
        assertFalse(called.get());
        assertEquals(123, stepper.step().intValue());
        assertTrue(called.get());
        assertEquals(456, stepper.step().intValue());
    }

    @Test
    public void breaks_down_composite_commands_into_multiple_steps() {
        SteppingFst<Integer, ?> stepper = SteppingFst.of(123);
        stepper.exec(o -> Mu.enter(123).then(Mu.reenter()).then(Mu.enter(456)));
        assertEquals(123, stepper.step().intValue());
        assertEquals(123, stepper.step().intValue());
        assertEquals(456, stepper.step().intValue());
    }

    @Test
    public void noop_does_not_add_a_step() {
        SteppingFst<Integer, ?> stepper = SteppingFst.of(123);
        stepper.exec(o -> Mu.enter(123).then(Mu.noop()).then(Mu.noop()).then(Mu.enter(456)));
        assertEquals(123, stepper.step().intValue());
        assertEquals(456, stepper.step().intValue());
    }

    @Test
    public void composite_command_in_async() {
        SteppingFst<Integer, ?> stepper = SteppingFst.of(0);
        stepper.exec(Mu.Action.pure(() -> o -> Mu.enterMany(123, 456, 789)));
        assertEquals(123, stepper.step().intValue());
        assertEquals(456, stepper.step().intValue());
        assertEquals(789, stepper.step().intValue());
    }

    @Test
    public void composite_command_in_defer() {
        SteppingFst<String, ?> stepper = SteppingFst.of("");
        stepper.exec(o -> Mu.defer(cont -> cont.resume(p -> Mu.enterMany("foo", "bar", "baz"))));
        assertEquals("foo", stepper.step());
        assertEquals("bar", stepper.step());
        assertEquals("baz", stepper.step());
    }

    @Test
    public void mutating_actions() {
        State s = new State();
        SteppingFst<State, ?> stepper = SteppingFst.of(s);
        Mu.Action<State> times2 = state -> {
            state.n *= 2;
            return Mu.noop();
        };
        stepper.exec(times2);
        stepper.exec(times2);
        stepper.exec(times2);
        stepper.step();
        assertEquals(2, s.n);
        stepper.step();
        assertEquals(4, s.n);
        stepper.step();
        assertEquals(8, s.n);
    }

    @Test
    public void execute_all_pending_steps() {
        SteppingFst<Integer, ?> stepper = SteppingFst.of(1);
        for (int i = 0; i < 5; i++) {
            stepper.exec(Mu.Action.pure(n -> n * 2));
        }
        assertEquals(32, stepper.drain().intValue());
    }

    private static class State {
        int n = 1;
    }
}

