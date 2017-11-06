package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import android.os.Handler;
import android.os.Looper;

import ph.codeia.fist.moore.AsyncMoore;

public class AndroidMoore<S> extends AsyncMoore<S> {

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    public AndroidMoore(S state) {
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
