package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import ph.codeia.fist.moore.AsyncRunner;
import ph.codeia.fist.moore.Mu;

public class AndroidRunner<S> extends AsyncRunner<S> {

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    public AndroidRunner(S state) {
        super(state);
    }

    @Override
    protected void runOnMainThread(Runnable proc) {
        if (Thread.currentThread() == HANDLER.getLooper().getThread()) {
            proc.run();
        }
        else {
            HANDLER.post(proc);
        }
    }
}
