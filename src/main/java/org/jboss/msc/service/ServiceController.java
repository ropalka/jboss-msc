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
     * Get the current service state.
     *
     * @return the current service state
     */
    ServiceState state();

    /**
     * Get the names of all values this service require.
     *
     * @return names of required values
     */
    Set<String> requires();

    /**
     * Get the names of all values this service provide.
     *
     * @return names of provided values
     */
    Set<String> provides();

    /**
     * Get the names of all missing values this service require.
     *
     * @return names of missing values
     */
    Set<String> missing();
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

}
