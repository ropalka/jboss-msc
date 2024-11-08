package org.jboss.msc.service;

/**
 * The service mode.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public enum ServiceMode {
    /**
     * Remove this service and all of its dependents.
     */
    REMOVE,
    /**
     * Only come up if all dependencies are satisfied <b>and</b> at least one dependent demands to start.
     */
    ON_DEMAND,
    /**
     * Only come up if all dependencies are satisfied <b>and</b> at least one dependent demands to start.
     * Once in the {@link ServiceController.State#UP UP} state, it will remain that way regardless of demands from dependents.
     */
    LAZY,
    /**
     * Come up automatically as soon as all dependencies are satisfied.
     */
    PASSIVE,
    /**
     * Demand to start, recursively demanding dependencies.  This is the default mode.
     */
    ACTIVE;
}
