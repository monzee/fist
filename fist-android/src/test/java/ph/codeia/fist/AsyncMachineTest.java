package ph.codeia.fist;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

public class AsyncMachineTest {

    interface Action extends Fst.Action<Integer, Printer, Action> {}

    static class Printer implements Fst.ErrorHandler {
        @Override
        public void handle(Throwable error) {
            throw new RuntimeException(error);
        }

        void say(String tpl, Object... fmtArgs) {
            System.out.printf(tpl, fmtArgs);
            System.out.println();
        }
    }

    final ExecutorService MAIN = Executors
            .newSingleThreadExecutor(r -> new Thread(r, "Main thread"));
    static final ExecutorService JOIN = Executors.newSingleThreadExecutor();
    static final ExecutorService WORK = Executors.newCachedThreadPool();

    private Throwable mainThreadError;

    Fst.Machine<Integer, Printer, Action> start(int s0, Printer p, Action g) {
        Fst.Machine<Integer, Printer, Action> sm = new ExecutorMachine<>(g, s0, MAIN, JOIN, WORK);
        sm.start(p);
        return sm;
    }

    @Before
    public void handleMainThreadExns() throws ExecutionException, InterruptedException {
        MAIN.submit(() -> Thread
                .currentThread()
                .setUncaughtExceptionHandler((t, e) -> mainThreadError = e))
                .get();
    }

    @After
    public void noMainThreadErrors() throws Throwable {
        if (mainThreadError != null) {
            throw mainThreadError;
        }
        MAIN.shutdown();
    }

    @AfterClass
    public static void tearDown() {
        JOIN.shutdown();
        WORK.shutdown();
    }

    @Test(timeout = 5000)
    public void stream_keeps_running_in_the_background() throws InterruptedException {
        CountDownLatch settingUp = new CountDownLatch(20);
        Printer p = new Printer();
        Fst.Machine<Integer, Printer, Action> sm = start(0, p, NOOP);
        sm.exec(p, (_n, _a) -> Fst.stream(tx -> {
            while (true) {
                tx.send(n -> n + 1);
                Thread.sleep(16);
            }
        }, (Action) (n, a) -> {
            settingUp.countDown();
            return Fst.enter(n);
        }));
        settingUp.await();

        p = new Printer();
        CountDownLatch done = new CountDownLatch(1);
        sm.exec(p, (n, _a) -> {
            assertEquals(20, n.intValue());
            return Fst.async(() -> {
                Thread.sleep(160);
                return (fn, _b) -> {
                    try {
                        assertTrue(fn > 20);
                        return Fst.noop();
                    }
                    finally {
                        done.countDown();
                    }
                };
            });
        });
        done.await();
        sm.stop();
    }

    @Test(timeout = 5000)
    public void cancel_streams_by_stopping_the_machine()
            throws BrokenBarrierException, InterruptedException
    {
        CyclicBarrier b = new CyclicBarrier(2);
        Printer p = new Printer();
        Fst.Machine<Integer, Printer, Action> sm = start(0, p, NOOP);
        sm.exec(p, (_a, _b) -> Fst.stream(tx -> {
            b.await();
            try {
                Thread.sleep(10_000);
            }
            catch (InterruptedException e) {
                b.await();
            }
        }, NOOP));
        b.await();
        sm.stop();
        b.await();
    }

    static final Action NOOP = (a, b) -> Fst.noop();
    static final Action REENTER = (a, b) -> Fst.reenter();
    static final Action MOVE = (a, b) -> Fst.move(a);
    static final Action PRINT = (n, t) -> {
        t.say("<- %d", n);
        return Fst.noop();
    };
}