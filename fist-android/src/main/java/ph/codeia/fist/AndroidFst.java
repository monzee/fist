package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import android.os.Handler;
import android.os.Looper;

public class AndroidFst<S> extends AsyncFst<S> {

    public static class Builder extends AsyncFst.Builder {
        @Override
        public <S> Fst<S> build(S state) {
            return new AndroidFst<>(state, this);
        }
    }

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private AndroidFst(S state, Builder builder) {
        super(state, builder);
    }

    public AndroidFst(S state) {
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
