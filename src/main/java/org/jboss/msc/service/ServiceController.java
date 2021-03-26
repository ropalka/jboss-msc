/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021, Red Hat, Inc., and individual contributors
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

import java.util.Collection;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A controller for a single service instance.
 *
 * @param <S> the service type
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface ServiceController<S> {

    /**
     * Get the service controller's current mode.
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
     * Get the current service controller state.
     *
     * @return the current state
     */
    ServiceState state();

    /**
     * Get the name of this service, if any.
     *
     * @return the name, or {@code null} if none was specified.
     */
    String getName();

    /**
     * Get the reason why the last start failed.
     *
     * @return the last start exception, or {@code null} if the last start succeeded or the service has not yet started
     */
    Throwable reason();

    /**
     * Get the complete list of dependencies that are unavailable.
     *
     * @return a set containing the names of all unavailable dependencies
     */
    Collection<String> missing();

}
