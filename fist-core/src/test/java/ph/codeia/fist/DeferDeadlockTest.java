package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import org.junit.AfterClass;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

public class DeferDeadlockTest {
    private static ExecutorService E = Executors.newSingleThreadExecutor();

    @AfterClass
    public static void tearDown() {
        E.shutdown();
    }

    @Test(timeout = 1000)
    public void mealy_defer_does_not_deadlock_BlockingFst() {
        Fst.Binding<Integer, ?> f = new BlockingFst<>(110).bind(_n -> {});
        f.exec((n, _e) -> Mi.defer(then -> E.execute(() -> then.ok(13 + n))));
        f.inspect(n -> assertEquals(123, n.intValue()));
    }

    @Test(timeout = 1000)
    public void resuming_a_mealy_defer_in_the_same_thread_does_not_deadlock_BlockingFst() {
        Fst.Binding<Integer, ?> f = new BlockingFst<>(100).bind(_n -> {});
        f.exec((n, _e) -> Mi.defer(block -> block.ok(23 + n)));
        f.inspect(n -> assertEquals(123, n.intValue()));
    }

    @Test(timeout = 1000)
    public void moore_defer_does_not_deadlock_BlockingFst() {
        Fst<Integer> f = new BlockingFst<>(24);
        f.exec(_n -> {}, n -> Mu.defer(then -> E.execute(() -> then.ok(62 + n))));
        f.inspect(n -> assertEquals(86, n.intValue()));
    }

    @Test(timeout = 1000)
    public void resuming_a_moore_defer_in_the_same_thread_does_not_deadlock_BlockingFst() {
        Fst<Integer> f = new BlockingFst<>(123);
        f.exec(_n -> {}, n -> Mu.defer(then -> then.ok(241 + n)));
        f.inspect(n -> assertEquals(364, n.intValue()));
    }
}

