package ph.codeia.fist;

/*
 * This file is a part of the fist project.
 */

/**
 * A finite state transducer that accepts both {@link Mu Moore} and {@link Mi
 * Mealy} actions.
 *
 * @param <S> The state type
 */
@SuppressWarnings("NewApi")
public interface Fst<S> {

    /**
     * A machine with a bound receiver. See {@link Fst#bind(Effects)}.
     *
     * @param <S> The state type
     * @param <E> The receiver type
     */
    interface Binding<S, E extends Effects<S>> {
        /**
         * @see Fst#start(Effects)
         */
        void start();

        /**
         * @see Fst#stop()
         */
        void stop();

        /**
         * @param action The action to execute
         * @see Fst#exec(Effects, Mu.Action)
         */
        void exec(Mu.Action<S> action);

        /**
         * @param action The action to execute
         * @see Fst#exec(Effects, Mi.Action)
         */
        void exec(Mi.Action<S, E> action);

        /**
         * @param projection The transform function
         * @see Fst#project(Fn.Func)
         */
        default <T> T project(Fn.Func<S, T> projection) {
            return new Mu.Action<S>() {
                T result;
                { exec(this); }

                @Override
                public Mu<S> apply(S state) {
                    result = projection.apply(state);
                    return Mu.noop();
                }
            }.result;
        }

        /**
         * @see Fst#inspect(Fn.Proc)
         * @param proc The procedure to run
         */
        default void inspect(Fn.Proc<S> proc) {
            project(state -> {
                proc.receive(state);
                return null;
            });
        }
    }

    /**
     * Builds state machines.
     */
    interface Builder {
        /**
         * @param state The initial state
         * @param <S> The state type
         * @return a state machine.
         */
        <S> Fst<S> build(S state);
    }

    /**
     * Starts the machine.
     * <p>
     * Any actions enqueued while the machine is stopped will be run.
     * <p>
     * This should be called in the main thread of the platform.
     *
     * @param effects The state receiver
     */
    void start(Effects<S> effects);

    /**
     * Stops the machine.
     * <p>
     * Any concurrent action being awaited will continue to run but the await
     * itself will be cancelled and enqueued for the next time {@link
     * #start(Effects)} is called.
     * <p>
     * Any action {@link #exec(Effects, Mu.Action) executed} while the machine
     * is stopped will likewise be enqueued.
     */
    void stop();

    /**
     * Modifies the state and notifies a receiver when needed.
     *
     * @param effects The state receiver
     * @param action The state transition function
     */
    void exec(Effects<S> effects, Mu.Action<S> action);

    /**
     * Same as {@link #exec(Effects, Mu.Action)} but with a specific receiver
     * type that the transition function may call.
     *
     * @param effects The state receiver
     * @param action The state transition function
     * @param <E> The receiver type
     */
    <E extends Effects<S>> void exec(E effects, Mi.Action<S, E> action);

    /**
     * Computes a value from the current state.
     * <p>
     * <strong>The function must not mutate the state!</strong> This method
     * is for querying only and should be avoided in general. Any state change
     * should happen in {@link Mu.Action} and {@link Mi.Action} objects only.
     *
     * @param projection The transform function
     * @param <T> The transformed type
     * @return some value derived from the current state of the machine.
     */
    <T> T project(Fn.Func<S, T> projection);

    /**
     * Calls a procedure with the current state.
     * <p>
     * Same as {@link #project(Fn.Func)} but does not return a value. This is
     * mainly for testing and should rarely be used in user code.
     *
     * @param proc The procedure to call with the current state
     */
    default void inspect(Fn.Proc<S> proc) {
        project(state -> {
            proc.receive(state);
            return null;
        });
    }

    /**
     * Associates a receiver with a state machine.
     * <p>
     * This allows the user to start the machine and execute actions without
     * explicitly passing a receiver every time. This may cause memory leaks
     * so the user must be careful not to make this object outlive the receiver.
     *
     * @param effects The state receiver
     * @param <E> The receiver type
     * @return a state machine with an associated receiver
     */
    default <E extends Effects<S>> Binding<S, E> bind(E effects) {
        return new Binding<S, E>() {
            @Override
            public void start() {
                Fst.this.start(effects);
            }

            @Override
            public void stop() {
                Fst.this.stop();
            }

            @Override
            public void exec(Mu.Action<S> action) {
                Fst.this.exec(effects, action);
            }

            @Override
            public void exec(Mi.Action<S, E> action) {
                Fst.this.exec(effects, action);
            }

            @Override
            public <T> T project(Fn.Func<S, T> projection) {
                return Fst.this.project(projection);
            }
        };
    }
}
