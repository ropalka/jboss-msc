package org.jboss.msc;

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
     * {@link ServiceState#UP UP} state, it will remain that way regardless of demands from dependents.
     */
    LAZY,
    /**
     * Demand to start, recursively demanding dependencies.  This is the default mode.
     */
    ACTIVE,
}
