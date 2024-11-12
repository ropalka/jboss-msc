/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.msc.service;

/**
 * A fine-grained substate of the more general basic controller {@link ServiceState}s.
 */
enum Substate {
    /**
     * New controller being installed.
     */
    NEW(ServiceState.DOWN, true),
    /**
     * Controller is down.
     */
    DOWN(ServiceState.DOWN, true),
    /**
     * Controller cannot start due to a problem with a dependency or transitive dependency.
     */
    PROBLEM(ServiceState.DOWN, true),
    /**
     * First phase of start processing.
     */
    START_REQUESTED(ServiceState.DOWN, false),
    /**
     * Second phase of start processing ({@link Service#start(StartContext) start()} method invoked).
     */
    STARTING(ServiceState.STARTING, false),
    /**
     * Start failed.
     */
    START_FAILED(ServiceState.FAILED, true),
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
     * Service has been removed.
     */
    REMOVING(ServiceState.REMOVED, false),
    /**
     * Service has been terminated.
     */
    REMOVED(ServiceState.REMOVED, true),
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
}
