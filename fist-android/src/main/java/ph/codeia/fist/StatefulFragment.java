package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

import ph.codeia.fist.mealy.Mi;
import ph.codeia.fist.moore.Mu;

public abstract class StatefulFragment<S, E extends Effects<S>> extends Fragment {
    private Fst.Actor<S, E> view;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        view = initialState();
    }

    @Override
    public void onResume() {
        super.onResume();
        view.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        view.stop();
    }

    protected abstract Fst.Actor<S, E> initialState();

    protected void exec(Mu.Action<S> action) {
        view.exec(action);
    }

    protected void exec(Mi.Action<S, E> action) {
        view.exec(action);
    }
}
