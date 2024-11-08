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

import java.util.Set;

/**
 * A controller for a single service instance.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface ServiceController {

    /**
     * Get the service controller's mode.
     *
     * @return the controller mode
     */
    ServiceMode mode();

    /**
     * Change the service controller's current mode.  Might result in the service starting or stopping.  The mode
     * may only be changed if it was not already set to {@link ServiceMode#REMOVE}.  Calling this method with the controller's
     * current mode has no effect and is always allowed.
     *
     * @param mode the new controller mode
     * @throws IllegalStateException if the mode given is {@code null}, or the caller attempted to change the
     *  service's mode from {@link ServiceMode#REMOVE} to a different mode
     */
    void setMode(ServiceMode mode);

    /**
     * Get the current service state.
     *
     * @return the current service state
     */
    State state();

    /**
     * Get the name of this service, if any.
     *
     * @return the name, or {@code null} if none was specified.
     * @see #provides()
     * @deprecated Use {@code ServiceController#provides()} instead. This method will be removed in a future release.
     */
    @Deprecated
    ServiceName getName();

    /**
     * Get the names of all values this service require.
     *
     * @return names of required values
     */
    Set<ServiceName> requires();

    /**
     * Get the names of all values this service provide.
     *
     * @return names of provided values
     */
    Set<ServiceName> provides();

    /**
     * Get the names of all missing values this service require.
     *
     * @return names of missing values
     */
    Set<ServiceName> missing();
    /**
     * Add a service lifecycle listener.
     *
     * @param listener the lifecycle listener
     */
    void addListener(LifecycleListener listener);

    /**
     * Remove a lifecycle listener.
     *
     * @param listener the lifecycle listener to remove
     */
    void removeListener(LifecycleListener listener);

    /**
     * Get the reason why the start failed.
     *
     * @return the start exception, or {@code null} if the last start succeeded or the service has not yet started
     */
    Throwable reason();

    /**
     * A possible state for a service controller.
     */
    enum State {

        /**
         * Down.  All dependents are down.
         */
        DOWN,
        /**
         * Service is starting.  Dependencies may not enter the {@code DOWN} state.  This state may not be left until
         * the {@code start} method has finished or failed.
         */
        STARTING,
        /**
         * Start failed, or was cancelled.  From this state, the start may be retried or the service may enter the
         * {@code DOWN} state.
         */
        START_FAILED,
        /**
         * Up.
         */
        UP,
        /**
         * Service is stopping.  Dependents may not enter the {@code STARTING} state.  This state may not be left until
         * all dependents are {@code DOWN} and the {@code stop} method has finished.
         */
        STOPPING,
        /**
         * Service was removed from the container.
         */
        REMOVED,
        ;
    }

}
