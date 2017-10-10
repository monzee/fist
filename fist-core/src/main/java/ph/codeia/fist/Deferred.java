package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Deferred<T> implements Future<T> {
    private enum Status { WAITING, READY, FAILED, CANCELLED }

    private final Object lock = new Object();
    private T value;
    private Throwable error;
    private Status status = Status.WAITING;

    public void ok(T t) {
        if (status != Status.WAITING) return;
        synchronized (lock) {
            value = t;
            status = Status.READY;
            lock.notifyAll();
        }
    }

    public void err(Throwable e) {
        if (status != Status.WAITING) return;
        synchronized (lock) {
            error = e;
            status = Status.FAILED;
            lock.notifyAll();
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (status != Status.WAITING) return false;
        synchronized (lock) {
            status = Status.CANCELLED;
            lock.notifyAll();
            return true;
        }
    }

    @Override
    public boolean isCancelled() {
        return status == Status.CANCELLED;
    }

    @Override
    public boolean isDone() {
        return status != Status.WAITING;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        try {
            return get(0, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e) {
            throw new IllegalStateException("impossible.");
        }
    }

    @Override
    public T get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException,
            TimeoutException, CancellationException
    {
        boolean timed = timeout > 0;
        long remaining = unit.toNanos(timeout);
        while (true) {
            switch (status) {
                case READY: return value;
                case CANCELLED: throw new CancellationException();
                case FAILED: throw new ExecutionException(error);
                case WAITING:
                    if (timed && remaining <= 0) {
                        throw new TimeoutException();
                    }
                    synchronized (lock) {
                        if (!timed) lock.wait();
                        else {
                            long start = System.nanoTime();
                            TimeUnit.NANOSECONDS.timedWait(lock, remaining);
                            // account for spurious wakeup
                            long elapsed = System.nanoTime() - start;
                            remaining -= elapsed;
                        }
                    }
            }
        }
    }
}
