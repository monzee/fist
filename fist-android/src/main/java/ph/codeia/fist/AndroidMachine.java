package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;

public class AndroidMachine<S, O extends Fst.ErrorHandler, A extends Fst.Action<S, O, A>>
        extends AsyncMachine<S, O, A>
{
    private final Handler handler = new Handler(Looper.getMainLooper());

    public AndroidMachine(A enter, S state, ExecutorService joiner, ExecutorService workers) {
        super(enter, state, joiner, workers);
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
