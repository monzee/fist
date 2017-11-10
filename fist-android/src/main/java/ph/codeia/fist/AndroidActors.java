package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.OnLifecycleEvent;

import ph.codeia.fist.mealy.Mi;
import ph.codeia.fist.moore.Mu;

public class AndroidActors {

    public static AndroidActors of(LifecycleOwner owner) {
        return new AndroidActors(owner.getLifecycle());
    }

    private final Lifecycle lifecycle;

    public AndroidActors(Lifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    public <S, E extends Effects<S>> Fst.Actor<S, E> bind(Fst<S> fst, E effects) {
        Decorator<S, E> decorator = new Decorator<>(fst.bind(effects));
        lifecycle.addObserver(decorator);
        return decorator;
    }

    static class Decorator<S, E extends Effects<S>> implements LifecycleObserver, Fst.Actor<S, E> {
        private final Fst.Actor<S, E> delegate;

        Decorator(Fst.Actor<S, E> delegate) {
            this.delegate = delegate;
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
        @Override
        public void start() {
            delegate.start();
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
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

        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        void onDestroy(LifecycleOwner owner) {
            owner.getLifecycle().removeObserver(this);
        }
    }
}
