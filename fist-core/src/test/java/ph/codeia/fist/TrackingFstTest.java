package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import org.junit.Test;

import java.util.Objects;

import static org.junit.Assert.*;

public class TrackingFstTest {
    enum Kind {
        FIZZ, BUZZ, FIZZBUZZ, NUMBER
    }

    class FizzBuzz {
        final Kind output;
        final int n;

        FizzBuzz() {
            this(1);
        }

        FizzBuzz(int n) {
            this.n = n;
            output = n % 15 == 0 ? Kind.FIZZBUZZ
                    : n % 3 == 0 ? Kind.FIZZ
                    : n % 5 == 0 ? Kind.BUZZ
                    : Kind.NUMBER;
        }

        FizzBuzz next() {
            return new FizzBuzz(n + 1);
        }
    }

    final TrackingFst<FizzBuzz> fst = new TrackingFst<>(new FizzBuzz());
    final Effects<FizzBuzz> noop = fizzBuzz -> {};
    final Effects<FizzBuzz> print = fizzBuzz -> {
        switch (fizzBuzz.output) {
            case FIZZ: System.out.println("fizz"); break;
            case BUZZ: System.out.println("buzz"); break;
            case FIZZBUZZ: System.out.println("fizzbuzz"); break;
            default: System.out.println(fizzBuzz.n);
        }
    };
    final Mu.Action<FizzBuzz> next = Mu.Action.pure(FizzBuzz::next);

    @Test
    public void starts_with_empty_queue() {
        assertTrue(fst.isEmpty(fb -> fb.n == 1));
    }

    @Test
    public void remembers_noop() {
        fst.exec(noop, Mu.Action.pure(Mu.noop()));
        assertTrue(fst.didNothing(fb -> fb.n == 1));
    }

    @Test
    public void remembers_enter() {
        fst.exec(noop, next);
        assertTrue(fst.didEnter(fb -> fb.n == 2));
    }

    @Test
    public void remembers_reenter() {
        fst.exec(noop, Mu.Action.pure(Mu.reenter()));
        assertTrue(fst.didReenter(fb -> fb.n == 1));
    }

    @Test
    public void remembers_raise() {
        fst.exec(noop, Mu.Action.pure(Mu.raise(new IllegalStateException())));
        assertTrue(fst.didRaise(e -> e instanceof IllegalStateException));
    }

    @Test
    public void remembers_async_errors() {
        fst.exec(noop, o -> Mu.async(() -> {
            int n = 1 / 0;
            return next;
        }));
        assertTrue(fst.didRaise(e -> e instanceof ArithmeticException));
    }

    @Test
    public void remembers_composite_commands() {
        fst.exec(noop, fb -> Mu.enter(fb.next()).then(Mu.noop()).then(Mu.reenter()).then(Mu.enter(null)));
        assertTrue(fst.didEnter());
        assertTrue(fst.didNothing());
        assertTrue(fst.didReenter());
        assertTrue(fst.didEnter(Objects::isNull));
        assertTrue(fst.isEmpty());
    }

    @Test
    public void remembers_composite_actions() {
        fst.exec(noop, next.then(next).then(next).then(next));
        assertTrue(fst.didEnter());
        assertTrue(fst.didEnter());
        assertTrue(fst.didEnter());
        assertTrue(fst.didEnter(fb -> fb.output == Kind.BUZZ));
        assertTrue(fst.isEmpty());
    }
}

