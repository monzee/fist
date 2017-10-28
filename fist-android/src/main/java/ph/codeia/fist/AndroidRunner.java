package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import ph.codeia.fist.moore.AsyncRunner;
import ph.codeia.fist.moore.Cmd;

public class AndroidRunner<S> extends AsyncRunner<S> {

    public static Cmd.ErrorHandler log(String tag) {
        return logAnd(tag, Cmd.ErrorHandler.IGNORE);
    }

    public static Cmd.ErrorHandler logAnd(String tag, Cmd.ErrorHandler delegate) {
        return e -> {
            if (Log.isLoggable(tag, Log.ERROR)) {
                Log.e(tag, "Error", e);
            }
            delegate.handle(e);
        };
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Cmd.ErrorHandler errors;

    public AndroidRunner(S state) {
        this(state, Cmd.ErrorHandler.RETHROW);
    }

    public AndroidRunner(S state, Cmd.ErrorHandler errors) {
        super(state);
        this.errors = errors;
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
            if (context != null && context instanceof Cmd.ErrorHandler) {
                ((Cmd.ErrorHandler) context).handle(e);
            }
            else {
                errors.handle(e);
            }
        });
    }
}
