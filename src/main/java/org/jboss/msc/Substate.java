package org.jboss.msc;

/**
 * A fine-grained substate of the more general basic controller {@link ServiceState}s.  The list of possible substates may
 * change over time, so users should not rely on its permanence.
 *
 * @deprecated this class will be removed in a future release
 */
@Deprecated
enum Substate {
    /**
     * New controller being installed.
     */
    NEW(ServiceState.DOWN, true),
    /**
     * Cancelled controller installation due to duplicate or other problem.
     */
    CANCELLED(ServiceState.REMOVED, true),
    /**
     * Controller is down.
     */
    DOWN(ServiceState.DOWN, false),
    /**
     * Controller is waiting for an external condition to start, such as a dependent demand.
     */
    WAITING(ServiceState.DOWN, true),
    /**
     * Controller is configured not to start.
     */
    WONT_START(ServiceState.DOWN, true),
    /**
     * Controller cannot start due to a problem with a dependency or transitive dependency.
     */
    PROBLEM(ServiceState.DOWN, true),
    /**
     * A stopped controller has been requested to start.
     */
    START_REQUESTED(ServiceState.DOWN, false),
    /**
     * First phase of start processing.
     */
    START_INITIATING(ServiceState.STARTING, false),
    /**
     * Second phase of start processing ({@link Service#start(StartContext) start()} method invoked).
     */
    STARTING(ServiceState.STARTING, false),
    /**
     * Start failed.
     */
    START_FAILED(ServiceState.START_FAILED, true),
    /**
     * Service is up.
     */
    UP(ServiceState.UP, true),
    /**
     * Service is up but has been requested to stop.
     */
    STOP_REQUESTED(ServiceState.UP, false),
    /**
     * Service is stopping.
     */
    STOPPING(ServiceState.STOPPING, false),
    /**
     * Service is being removed.
     */
    REMOVING(ServiceState.DOWN, false),
    /**
     * Service has been removed.
     */
    REMOVED(ServiceState.REMOVED, false),
    /**
     * Service has been terminated.
     */
    TERMINATED(ServiceState.REMOVED, true),
    ;
    private final ServiceState state;
    private final boolean restState;

    Substate(final ServiceState state, final boolean restState) {
        this.state = state;
        this.restState = restState;
    }

    /**
     * Determine whether this is a "rest" state.
     *
     * @return {@code true} if it is a rest state, {@code false} otherwise
     */
    public boolean isRestState() {
        return restState;
    }

    /**
     * Get the state corresponding to this sub-state.
     *
     * @return the state
     */
    public ServiceState getState() {
        return state;
    }

    /**
     * Determine if this substate is one of the given substates.
     *
     * @param substates the substates to check
     * @return {@code true} if this substate is in the set; {@code false} otherwise
     */
    public boolean in(Substate... substates) {
        for (Substate test : substates) {
            if (this == test) {
                return true;
            }
        }
        return false;
    }
}
