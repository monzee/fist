package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import android.os.Handler;
import android.os.Looper;

import ph.codeia.fist.mealy.AsyncMealy;
import ph.codeia.fist.mealy.Mi;

public class AndroidMealy<S, C extends Mi.Effects<S>> extends AsyncMealy<S, C> {

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    public AndroidMealy(S state) {
        super(state);
    }

    @Override
    protected void runOnMainThread(Runnable proc) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            proc.run();
        }
        else {
            HANDLER.post(proc);
        }
    }

}
