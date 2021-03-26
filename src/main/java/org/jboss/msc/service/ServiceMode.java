package org.jboss.msc.service;

import org.jboss.msc.service.ServiceController.State;

/**
 * The controller mode for a service.
 */
public enum ServiceMode {

    /**
     * Remove this service and all of its dependents.
     */
    REMOVE,
    /**
     * Do not start; in addition, ignore demands from dependents.
     */
    NEVER,
    /**
     * Only come up if all dependencies are satisfied <b>and</b> at least one dependent demands to start.
     */
    ON_DEMAND,
    /**
     * Only come up if all dependencies are satisfied <b>and</b> at least one dependent demands to start. Once in the
     * {@link State#UP UP} state, it will remain that way regardless of demands from dependents.
     */
    LAZY,
    /**
     * Come up automatically as soon as all dependencies are satisfied.
     */
    PASSIVE,
    /**
     * Demand to start, recursively demanding dependencies.  This is the default mode.
     */
    ACTIVE,
    ;

    /**
     * Determine if this mode is one of the given modes.
     *
     * @param modes the modes to check
     * @return {@code true} if this mode is in the set; {@code false} otherwise
     */
    public boolean in(ServiceMode... modes) {
        for (ServiceMode test : modes) {
            if (this == test) {
                return true;
            }
        }
        return false;
    }
}
