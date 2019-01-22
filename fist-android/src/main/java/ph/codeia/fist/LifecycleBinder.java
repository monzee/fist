package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

import androidx.lifecycle.GenericLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

public class LifecycleBinder {

    public static LifecycleBinder of(LifecycleOwner owner) {
        return new LifecycleBinder(owner.getLifecycle());
    }

    private final Lifecycle lifecycle;

    public LifecycleBinder(Lifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    public <S, E extends Effects<S>> Fst.Binding<S, E> bind(Fst<S> fst, E effects) {
        return wrap(fst.bind(effects));
    }

    public <S, E extends Effects<S>> Fst.Binding<S, E> bind(Fst<S> fst, Fn.Supplier<E> effects) {
        return wrap(fst.bind(effects));
    }

    public <S, E extends Effects<S>> Fst.Binding<S, E> wrap(Fst.Binding<S, E> inner) {
        LifecycleBinding<S, E> decorator = new LifecycleBinding<>(inner);
        lifecycle.addObserver(decorator);
        return decorator;
    }

    private static class LifecycleBinding<S, E extends Effects<S>>
            implements GenericLifecycleObserver, Fst.Binding<S, E>
    {
        private final Fst.Binding<S, E> delegate;

        LifecycleBinding(Fst.Binding<S, E> delegate) {
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

        @Override
        public <T> T project(Fn.Func<S, T> projection) {
            return delegate.project(projection);
        }

        @Override
        public void inspect(Fn.Proc<S> proc) {
            delegate.inspect(proc);
        }
    }
}
