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

    public static Mu.ErrorHandler log(String tag) {
        return logAnd(tag, Mu.ErrorHandler.IGNORE);
    }

    public static Mu.ErrorHandler logAnd(String tag, Mu.ErrorHandler delegate) {
        return e -> {
            if (Log.isLoggable(tag, Log.ERROR)) {
                Log.e(tag, "Error", e);
            }
            delegate.handle(e);
        };
    }

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private final Mu.ErrorHandler errors;

    public AndroidRunner(S state) {
        this(state, Mu.ErrorHandler.RETHROW);
    }

    public AndroidRunner(S state, Mu.ErrorHandler errors) {
        super(state);
        this.errors = errors;
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

    @Override
    protected void handle(Throwable e, Mu.Context<S> context) {
        runOnMainThread(() -> {
            if (context != null && context instanceof Mu.ErrorHandler) {
                ((Mu.ErrorHandler) context).handle(e);
            }
            else {
                errors.handle(e);
            }
        });
    }
}
