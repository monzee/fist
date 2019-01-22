package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import android.util.Log;


/**
 * Base class for Android MVC/P views that provides logging methods.
 *
 * <p> The {@link #echo(String)} and {@link #onEnter(Object)} still need to
 * be implemented by the views.
 *
 * @param <S> The state type
 */
public abstract class AndroidConsole<S> implements Console<S> {
    private final String tag;
    private final int logLevel;
    private boolean isMuted;

    /**
     * @param tag The log tag
     * @param logLevel Should be one of the int constants in {@link Log}
     */
    protected AndroidConsole(String tag, int logLevel) {
        this.tag = tag;
        this.logLevel = logLevel;
    }

    /**
     * Sets the log level to {@link Log#INFO}.
     *
     * @param tag The log tag
     */
    protected AndroidConsole(String tag) {
        this(tag, Log.INFO);
    }

    /**
     * Disables logging.
     */
    protected void muteLog() {
        isMuted = true;
    }

    @Override
    public void log(Throwable ex, String text, Object... fmtArgs) {
        if (isMuted || !Log.isLoggable(tag, logLevel)) {
            return;
        }
        String message = String.format(text, fmtArgs);
        switch (logLevel) {
            case Log.VERBOSE:
                Log.v(tag, message, ex);
                break;
            case Log.DEBUG:
                Log.d(tag, message, ex);
                break;
            case Log.WARN:
                Log.w(tag, message, ex);
                break;
            case Log.ERROR:
                Log.e(tag, message, ex);
                break;
            case Log.ASSERT:
                Log.wtf(tag, message, ex);
                break;
            case Log.INFO:  // fallthrough
            default:
                Log.i(tag, message, ex);
                break;
        }
    }
}

