package ph.codeia.fist;

import org.junit.Test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

import static org.junit.Assert.*;

/*
 * This file is a part of the fist project.
 */

public class AsyncFstTest {

    static class Foo implements Effects<Integer> {
        int last;
        String lastFoo;

        void foo(String s) {
            lastFoo = s;
        }

        @Override
        public void onEnter(Integer n) {
            last = n;
        }
    }

    static class Bar implements Effects<Integer> {
        int last;
        String lastBar;

        void bar(String s) {
            lastBar = s;
        }

        @Override
        public void onEnter(Integer n) {
            last = n;
        }
    }

    @Test
    public void synchronous_mealy_actions() {
        Fst<Integer> sm = new UnconfinedFst<>(0);
        sm.inspect(n -> assertEquals(0, n.intValue()));
        Foo foo = new Foo();
        sm.start(foo);
        assertEquals(0, foo.last);
        assertNull(foo.lastFoo);

        sm.exec(foo, n -> Mu.enter(n + 1));
        sm.inspect(n -> assertEquals(1, n.intValue()));
        assertEquals(1, foo.last);

        sm.exec(foo, (n, f) -> {
            f.foo("foo");
            return Mi.noop();
        });
        assertEquals("foo", foo.lastFoo);
        assertEquals(1, foo.last);

        Bar bar = new Bar();
        sm.exec(bar, Mi.Action.effect(b -> b.bar("bar")));
        assertEquals("bar", bar.lastBar);
        assertEquals(0, bar.last);
    }

    @Test(timeout = 1000)
    public void async_mealy_actions() throws InterruptedException, BrokenBarrierException {
        Fst<Integer> sm = new UnconfinedFst<>(0);
        Foo foo = new Foo();
        Bar bar = new Bar();
        sm.start(foo);
        CyclicBarrier barrier = new CyclicBarrier(2);
        sm.exec(foo, Mi.Action.pure(() -> Mi.Action.effect(f -> {
            f.foo("a");
            try {
                barrier.await();
            }
            catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
        })));
        barrier.await();
        assertEquals("a", foo.lastFoo);

        sm.exec(bar, Mi.Action.pure(() -> Mi.Action.effect(b -> {
            b.bar("b");
            try {
                barrier.await();
            }
            catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
        })));
        barrier.await();
        assertEquals("b", bar.lastBar);
    }

    @Test(timeout = 1000)
    public void stop_in_the_middle_of_an_async_action()
    throws BrokenBarrierException, InterruptedException {
        Fst<Integer> sm = new UnconfinedFst<>(0);
        Foo foo = new Foo();
        sm.start(foo);
        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(1);
        sm.exec(foo, Mi.Action.pure(() -> {
            barrier.await();
            return Mi.Action.effect(f -> {
                f.foo("z");
                done.countDown();
            });
        }));
        sm.stop();
        barrier.await();
        assertNull(foo.lastFoo);
        sm.start(foo);
        done.await();
        assertEquals("z", foo.lastFoo);
    }
}