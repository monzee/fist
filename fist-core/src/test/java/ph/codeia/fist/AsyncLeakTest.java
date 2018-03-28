package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import org.junit.Test;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

public class AsyncLeakTest {

    static class Noop<T> implements Effects<T> {
        @Override
        public void onEnter(T t) {
        }
    }

    static Reference<?> gc(ReferenceQueue<?> q) throws InterruptedException {
        assertNull(q.poll());
        Reference<?> ref = null;
        for (; ref == null; ref = q.remove(100)) {
            System.gc();
        }
        return ref;
    }

    @Test(timeout = 1000)
    public void gc_works() throws InterruptedException {
        Noop<Integer> p = new Noop<>();
        ReferenceQueue<Noop<Integer>> q = new ReferenceQueue<>();
        PhantomReference<Noop<Integer>> ref = new PhantomReference<>(p, q);

        p = null;
        assertNotNull(gc(q));
    }

    @Test(timeout = 1000)
    public void async_moore_action_does_not_leak_the_effects_object() throws InterruptedException {
        Noop<Integer> p = new Noop<>();
        ReferenceQueue<Noop<Integer>> q = new ReferenceQueue<>();
        PhantomReference<Noop<Integer>> ref = new PhantomReference<>(p, q);

        Fst<Integer> fst = new UnconfinedFst<>(0);
        fst.start(p);
        CountDownLatch done = new CountDownLatch(1);
        fst.exec(p, Mu.Action.pure(() -> {
            done.await();
            return Mu.Action.pure(0);
        }));

        p = null;
        assertNotNull(gc(q));
        done.countDown();
    }

    @Test(timeout = 1000)
    public void async_mealy_action_does_not_leak_the_effects_object() throws InterruptedException {
        Noop<Integer> p = new Noop<>();
        ReferenceQueue<Noop<Integer>> q = new ReferenceQueue<>();
        PhantomReference<Noop<Integer>> ref = new PhantomReference<>(p, q);

        Fst<Integer> fst = new UnconfinedFst<>(0);
        fst.start(p);
        CountDownLatch done = new CountDownLatch(1);
        fst.exec(p, Mi.Action.pure(() -> {
            done.await();
            return Mi.Action.pure(0);
        }));

        p = null;
        assertNotNull(gc(q));
        done.countDown();
    }
}
