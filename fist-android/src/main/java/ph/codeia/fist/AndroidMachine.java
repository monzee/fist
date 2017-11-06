package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import ph.codeia.fist.deprecated.AsyncMachine;
import ph.codeia.fist.deprecated.Fst;

public class AndroidMachine<S, O extends Fst.ErrorHandler, A extends Fst.Action<S, O, A>>
        extends AsyncMachine<S, O, A>
{
    private final Handler handler = new Handler(Looper.getMainLooper());

    public AndroidMachine(
            A enter, S state, long timeout,
            ExecutorService joiner, ExecutorService workers
    ) {
        super(enter, state, timeout, joiner, workers);
    }

    public AndroidMachine(
            A enter, S state, long timeout, TimeUnit unit,
            ExecutorService joiner, ExecutorService workers
    ) {
        super(enter, state, timeout, unit, joiner, workers);
    }

    public AndroidMachine(A enter, S state, long timeout, TimeUnit unit) {
        super(enter, state, timeout, unit);
    }

    public AndroidMachine(A enter, S state, long timeout) {
        super(enter, state, timeout);
    }

    public AndroidMachine(A enter, S state) {
        super(enter, state);
    }

    @Override
    public void runOnMainThread(Runnable r) {
        if (Thread.currentThread() == handler.getLooper().getThread()) {
            r.run();
        } else {
            handler.post(r);
        }
    }
}
