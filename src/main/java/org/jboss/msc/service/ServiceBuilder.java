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

import java.util.ConcurrentModificationException;

/**
 * The service builder. It is used to configure the service before installing it into the container.
 * <p>
 * Service may require multiple values provided by other services in order to be able to start.
 * Every named value service will require must be declared via {@link #requires(String...)} method.
 * Once required values are configured and service is installed into the container
 * then declared values will be accessible via:
 * <ul>
 *     <li>{@link StartContext#getValue(String)} method during execution of {@link Service#start(StartContext)} method</li>
 *     <li>{@link StopContext#getValue(String)} method during execution of {@link Service#stop(StopContext)} method</li>
 * </ul>
 * <p>
 * Service can provide multiple values which may be consumed by other services.
 * Every named value service will provide must be declared via {@link #provides(String...)} method.
 * Once provided values are configured and service is installed into the container
 * then declared values must be provided to the container via:
 * <ul>
 *     <li>{@link StartContext#setValue(String, Object)} method during execution of {@link Service#start(StartContext)} method</li>
 * </ul>
 * It is not necessary to clean up the provided values. The container will do it automatically when either service
 * start will fail or service will stop. However, it is required that installed service will provide all
 * declared provided values to the container otherwise service start process will fail.
 * <p>
 * The implementation of this interface is thread safe because it relies on thread confinement
 * i.e. the builder instance can be used only by thread that created it.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface ServiceBuilder {

    /**
     * Specifies value names required by service.
     *
     * @param values required value names
     * @return this builder
     * @throws ConcurrentModificationException if builder is shared between threads.
     * Only thread that created the builder can manipulate it.
     * @throws IllegalArgumentException if value <code>name</code> was before used as parameter
     * in {@link #provides(String...)} method call. Value can be either required or provided but not both.
     * @throws IllegalStateException if this method have been called after {@link #install()} method.
     * @throws NullPointerException if <code>names</code> parameter is <code>null</code> or any value of the vararg
     * array is <code>null</code>.
     */
    ServiceBuilder requires(String... values);

    /**
     * Specifies value names provided by service.
     *
     * @param values provided value names
     * @return this builder
     * @throws ConcurrentModificationException if builder is shared between threads.
     * Only thread that created the builder can manipulate it.
     * @throws IllegalArgumentException if value <code>name</code> was before used as parameter
     * in {@link #requires(String...)} method call. Value can be either required or provided but not both.
     * @throws IllegalStateException if this method have been called after {@link #install()} method.
     * @throws NullPointerException if <code>names</code> parameter is <code>null</code> or any value of the vararg
     * array is <code>null</code>.
     */
    ServiceBuilder provides(String... values);

    /**
     * Sets service mode.
     *
     * @param mode the service mode
     * @return this builder
     * @throws ConcurrentModificationException if builder is shared between threads.
     * Only thread that created the builder can manipulate it.
     * @throws IllegalStateException if this method have been either called twice or it was called after
     * {@link #install()} method.
     * @throws NullPointerException if <code>mode</code> parameter is <code>null</code>.
     */
    ServiceBuilder mode(Mode mode);

    /**
     * Sets service instance. If {@link #install()} method call is issued
     * without this method being called then <code>NULL</code> service will be
     * installed into the container.
     * <p>
     * Once this method have been called then all subsequent
     * calls of {@link #requires(String...)}, and {@link #provides(String...)}
     * methods will fail because their return values should be provided to service instance.
     *
     * @param service the service instance
     * @return this builder
     * @throws ConcurrentModificationException if builder is shared between threads.
     * Only thread that created the builder can manipulate it.
     * @throws IllegalStateException if this method have been either called twice or it was called after
     * {@link #install()} method.
     */
    ServiceBuilder instance(Service service);

    /**
     * Adds a service lifecycle listener.
     *
     * @param listener the listener to listen for service lifecycle events
     * @return this builder
     * @throws ConcurrentModificationException if builder is shared between threads.
     * Only thread that created the builder can manipulate it.
     * @throws IllegalStateException if this method have been called after {@link #install()} method.
     * @throws NullPointerException if <code>listener</code> parameter is <code>null</code>.
     */
    ServiceBuilder addListener(ServiceListener listener);

    /**
     * Installs configured service into the container.
     *
     * @return installed service controller
     * @throws ConcurrentModificationException if builder is shared between threads.
     * Only thread that created the builder can manipulate it.
     * @throws IllegalStateException if this method have been called twice or if instance was not provided
     * or if service is useless i.e. it neither requires any value nor provide any value.
     * @throws CycleDetectedException if installation process failed because there was
     * a dependencies cycle detected on attempt to install this service to the container.
     * @throws DuplicateValueException if installation process failed because there was
     * an existing service that is already providing configured provided value detected
     * on attempt to install this service to the container.
     */
    ServiceController install();

}
