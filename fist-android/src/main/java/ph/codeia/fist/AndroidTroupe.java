package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import android.arch.lifecycle.GenericLifecycleObserver;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;

import ph.codeia.fist.mealy.Mi;
import ph.codeia.fist.moore.Mu;

public class AndroidTroupe {

    public static AndroidTroupe of(LifecycleOwner owner) {
        return new AndroidTroupe(owner.getLifecycle());
    }

    private final Lifecycle lifecycle;

    public AndroidTroupe(Lifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    public <S, E extends Effects<S>> Fst.Actor<S, E> bind(Fst<S> fst, E effects) {
        return wrap(fst.bind(effects));
    }

    public <S, E extends Effects<S>> Fst.Actor<S, E> wrap(Fst.Actor<S, E> inner) {
        LifecycleBoundActor<S, E> decorator = new LifecycleBoundActor<>(inner);
        lifecycle.addObserver(decorator);
        return decorator;
    }

    private static class LifecycleBoundActor<S, E extends Effects<S>>
            implements GenericLifecycleObserver, Fst.Actor<S, E>
    {
        private final Fst.Actor<S, E> delegate;

        LifecycleBoundActor(Fst.Actor<S, E> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
            switch (event) {
            case ON_RESUME:
                start();
                break;
            case ON_PAUSE:
                stop();
                break;
            case ON_DESTROY:
                source.getLifecycle().removeObserver(this);
                break;
            default:
                break;
            }
        }

        @Override
        public void start() {
            delegate.start();
        }

        @Override
        public void stop() {
            delegate.stop();
        }

        @Override
        public void exec(Mu.Action<S> action) {
            delegate.exec(action);
        }

        @Override
        public void exec(Mi.Action<S, E> action) {
            delegate.exec(action);
        }
    }
}
