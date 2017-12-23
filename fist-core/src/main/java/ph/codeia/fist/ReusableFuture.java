package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ReusableFuture<T> implements Future<T> {
    private enum State {WAITING, READY, FAILED, CANCELLED}

    private final Object lock = new Object();
    private T value;
    private Throwable error;
    private State state = State.WAITING;

    public void ok(T t) {
        if (state != State.WAITING) return;
        synchronized (lock) {
            value = t;
            state = State.READY;
            lock.notifyAll();
        }
    }

    public void err(Throwable e) {
        if (state != State.WAITING) return;
        synchronized (lock) {
            error = e;
            state = State.FAILED;
            lock.notifyAll();
        }
    }

    public T take() throws InterruptedException, ExecutionException {
        try {
            return take(0, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e) {
            throw new IllegalStateException("impossible.");
        }
    }

    public T take(long timeout, TimeUnit unit)
    throws InterruptedException, ExecutionException, TimeoutException {
        synchronized (lock) {
            try {
                return get(timeout, unit);
            }
            finally {
                state = State.WAITING;
                value = null;
                error = null;
            }
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (state != State.WAITING) return false;
        synchronized (lock) {
            state = State.CANCELLED;
            lock.notifyAll();
            return true;
        }
    }

    @Override
    public boolean isCancelled() {
        return state == State.CANCELLED;
    }

    @Override
    public boolean isDone() {
        return state != State.WAITING;
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
    throws InterruptedException, ExecutionException, TimeoutException {
        boolean timed = timeout > 0;
        long remaining = unit.toNanos(timeout);
        while (true) synchronized (lock) {
            switch (state) {
                case READY:
                    return value;
                case CANCELLED:
                    throw new CancellationException();
                case FAILED:
                    throw new ExecutionException(error);
                case WAITING:
                    if (timed && remaining <= 0) {
                        throw new TimeoutException();
                    }
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
