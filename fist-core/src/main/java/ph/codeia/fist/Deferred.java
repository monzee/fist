package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import java.util.concurrent.Callable;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Deferred<T> implements Callable<T> {
    private T value;
    private final Lock lock = new ReentrantLock();
    private final Condition hasValue = lock.newCondition();

    public void offer(T t) {
        lock.lock();
        try {
            value = t;
            hasValue.signal();
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public T call() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (value == null) {
                hasValue.await();
            }
            T t = value;
            value = null;
            return t;
        }
        finally {
            lock.unlock();
        }
    }
}
