package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import android.os.Handler;
import android.os.Looper;

import ph.codeia.fist.moore.AsyncRunner;
import ph.codeia.fist.moore.Cmd;

public class AndroidRunner<S> extends AsyncRunner<S> {

    private final Handler handler = new Handler(Looper.getMainLooper());

    public AndroidRunner(S state) {
        super(state);
    }

    @Override
    protected void runOnMainThread(Runnable proc) {
        if (Thread.currentThread() == handler.getLooper().getThread()) {
            proc.run();
        }
        else {
            handler.post(proc);
        }
    }

    @Override
    protected void handle(Throwable e, Cmd.Context<S> context) {
        runOnMainThread(() -> {
            throw new RuntimeException(e);
        });
    }
}
