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
        value = t;
        hasValue.signal();
        lock.unlock();
    }

    @Override
    public T call() throws InterruptedException {
        if (value == null) {
            lock.lockInterruptibly();
            try {
                while (value == null) {
                    hasValue.await();
                }
            }
            finally {
                lock.unlock();
            }
        }
        T t = value;
        value = null;
        return t;
    }
}
