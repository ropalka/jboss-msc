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
 * The start lifecycle context.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface StartContext extends LifecycleContext {

    /**
     * Call within the service lifecycle start method to trigger an <em>asynchronous</em> lifecycle start action.
     * This action will not be considered complete until indicated so by calling 
     * either {@link #complete()} or {@link #fail(Throwable)} method on this interface.
     */
    void asynchronous();

    /**
     * Call when either <em>synchronous</em> or <em>asynchronous</em> lifecycle start action is complete.
     *
     * @throws IllegalStateException if called after {@link #fail(Throwable)} was called or if called twice in a row
     */
    void complete();

    /**
     * Call when either <em>synchronous</em> or <em>asynchronous</em> lifecycle start action has failed for some reason.
     *
     * @param reason the reason for the failure
     * @throws IllegalStateException if called after {@link #complete()} was called or if called twice in a row
     */
    void fail(Throwable reason);

    /**
     * Gets required value. Returns dependency value if and only if required value was configured via
     * {@link ServiceBuilder#requires(String...)} method and this context wasn't invalidated, null otherwise.
     *
     * @param name required value or null
     * @return value associated with given name
     * @param <V> value type
     */
    <V> V getValue(String name);

    /**
     * Sets provided value. Injects provided value if and only if provided value was configured via
     * {@link ServiceBuilder#provides(String...)} method and this context wasn't invalidated.
     * Provided values are automatically invalidated when either service start will fail or service will stop.
     *
     * @param name provided value name
     * @param value provided value associated with given name
     * @param <V> value type
     */
    <V> void setValue(String name, V value);

}
