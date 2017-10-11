package ph.codeia.fist;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CancellationException;
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

    private volatile Throwable mainThreadError;

    static Reference<?> gc(ReferenceQueue<?> q) throws InterruptedException {
        assertNull(q.poll());
        Reference<?> ref = null;
        for (; ref == null; ref = q.remove(100)) {
            System.gc();
        }
        return ref;
    }

    Fst.Machine<Integer, Printer, Action> start(int s0, Printer p, Action g) {
        Fst.Machine<Integer, Printer, Action> sm = new ExecutorMachine<>(g, s0, MAIN, JOIN, WORK);
        sm.start(p);
        return sm;
    }

    @Before
    public void handleMainThreadExns() throws ExecutionException, InterruptedException {
        MAIN.submit(() -> {
            Thread t = Thread.currentThread();
            t.setUncaughtExceptionHandler((_t, e) -> mainThreadError = e);
            t.setPriority(Thread.MIN_PRIORITY);
        }).get();
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

    @Test(timeout = 1000)
    public void gc_works() throws InterruptedException {
        Printer p = new Printer();
        ReferenceQueue<Printer> q = new ReferenceQueue<>();
        PhantomReference<Printer> ref = new PhantomReference<>(p, q);

        p = null;
        assertNotNull(gc(q));
    }

    @Test(timeout = 1000)
    public void async_does_not_leak_the_actor() throws InterruptedException {
        Printer p = new Printer();
        ReferenceQueue<Printer> q = new ReferenceQueue<>();
        PhantomReference<Printer> rp = new PhantomReference<>(p, q);

        Fst.Machine<Integer, Printer, Action> sm = new ExecutorMachine<>(NOOP, 0, MAIN, JOIN, WORK);
        sm.start(p);
        sm.exec(p, (_n, _a) -> Fst.async(() -> {
            Thread.sleep(10_000);
            return NOOP;
        }));

        p = null;
        // i don't even need to stop the machine
        assertNotNull(gc(q));
    }

    @Test(timeout = 1000)
    public void stream_keeps_running_in_the_background() throws InterruptedException {
        CountDownLatch settingUp = new CountDownLatch(10);
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
            assertEquals(10, n.intValue());
            return Fst.async(() -> {
                Thread.sleep(32);
                return (fn, _b) -> {
                    try {
                        assertTrue("stream seems to have stopped", fn > 10);
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

    @Test(timeout = 1000)
    public void interrupts_stream_when_machine_is_stopped()
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

    @Test(timeout = 1000)
    public void cancels_stream_when_actor_is_GCed() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Printer p = new Printer();
        ReferenceQueue<Printer> q = new ReferenceQueue<>();
        PhantomReference<Printer> r = new PhantomReference<>(p, q);

        Fst.Machine<Integer, Printer, Action> sm = start(0, p, NOOP);
        sm.exec(p, (_a, _b) -> Fst.stream(tx -> {
            try {
                while (true) {
                    tx.send(n -> n);
                    Thread.sleep(16);
                }
            }
            catch (CancellationException e) {
                done.countDown();
            }
        }, NOOP));

        p = null;
        assertNotNull(gc(q));
        done.await();
    }

    static final Action NOOP = (a, b) -> Fst.noop();
    static final Action REENTER = (a, b) -> Fst.reenter();
    static final Action MOVE = (a, b) -> Fst.move(a);
    static final Action PRINT = (n, t) -> {
        t.say("<- %d", n);
        return Fst.noop();
    };
}
