package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import org.junit.AfterClass;
import org.junit.Test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

public class AsyncDeadlockTest {
    private static final ExecutorService MAIN = Executors.newSingleThreadExecutor();

    private static <T> Effects<T> NOOP() {
        return o -> {};
    }

    private static <T> Effects<T> hit(CyclicBarrier barrier) {
        return o -> {
            try {
                barrier.await();
            }
            catch (InterruptedException|BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @AfterClass
    public static void tearDown() {
        MAIN.shutdown();
    }

    @Test
    public void sanity_test() {
        AsyncFst<Integer> fst = new UnconfinedFst<>(0);
        Effects<Integer> println = System.out::println;
        fst.start(println);
        fst.exec(println, i -> Mu.enter(i + 123));
        fst.inspect(i -> assertEquals(123, i.intValue()));
    }

    @Test
    public void bind_machine_to_effects() {
        Fst.Binding<Integer, ?> fst = new UnconfinedFst<>(0).bind(NOOP());
        fst.start();
        fst.exec(n -> Mu.enter(n + 100));
        fst.inspect(n -> assertEquals(100, n.intValue()));
    }

    @Test(timeout = 1000)
    public void async_mealy_action() throws BrokenBarrierException, InterruptedException {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Fst.Binding<Integer, Effects<Integer>> fst = new UnconfinedFst<>(0).bind(hit(barrier));
        MAIN.execute(fst::start);
        barrier.await();
        fst.exec((n, _e) -> Mi.async(() -> Mi.Action.pure(n + 1)));
        barrier.await();
        fst.inspect(n -> assertEquals(1, n.intValue()));
    }

    @Test(timeout = 1000)
    public void async_moore_action() throws BrokenBarrierException, InterruptedException {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Fst.Binding<Integer, ?> fst = new UnconfinedFst<>(0).bind(hit(barrier));
        MAIN.execute(fst::start);
        barrier.await();
        fst.exec(n -> Mu.async(() -> Mu.Action.pure(n + 1)));
        barrier.await();
        fst.inspect(n -> assertEquals(1, n.intValue()));
    }

    @Test(timeout = 1000)
    public void late_start() throws BrokenBarrierException, InterruptedException {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Fst.Binding<Integer, ?> fst = new UnconfinedFst<>(0).bind(hit(barrier));
        fst.exec(n -> Mu.enter(n + 1));
        MAIN.execute(fst::start);
        barrier.await();
        barrier.await();
        fst.inspect(n -> assertEquals(1, n.intValue()));
    }

    @Test(timeout = 1000)
    public void async_late_start() throws BrokenBarrierException, InterruptedException {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Fst.Binding<Integer, ?> fst = new UnconfinedFst<>(0).bind(hit(barrier));
        fst.exec(n -> Mu.async(() -> Mu.Action.pure(n + 1)));
        MAIN.execute(fst::start);
        barrier.await();
        barrier.await();
        fst.inspect(n -> assertEquals(1, n.intValue()));
    }
}
