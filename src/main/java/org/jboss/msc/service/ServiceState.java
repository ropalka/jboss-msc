package org.jboss.msc.service;

/**
 * A possible state for a service controller.
 */
public enum ServiceState {

    /**
     * Down.  All dependents are down.
     */
    DOWN,
    /**
     * Service is starting.  Dependencies may not enter the {@code DOWN} state.  This state may not be left until the
     * {@code start} method has finished or failed.
     */
    STARTING,
    /**
     * Start failed, or was cancelled.  From this state, the start may be retried or the service may enter the {@code
     * DOWN} state.
     */
    START_FAILED,
    /**
     * Up.
     */
    UP,
    /**
     * Service is stopping.  Dependents may not enter the {@code STARTING} state.  This state may not be left until all
     * dependents are {@code DOWN} and the {@code stop} method has finished.
     */
    STOPPING,
    /**
     * Service was removed from the container.
     */
    REMOVED,
    ;

    /**
     * Determine if this state is one of the given states.
     *
     * @param states the states to check
     * @return {@code true} if this state is in the set; {@code false} otherwise
     */
    public boolean in(ServiceState... states) {
        for (ServiceState test : states) {
            if (this == test) {
                return true;
            }
        }
        return false;
    }
}
