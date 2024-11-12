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
 * The service stop lifecycle context.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface StopContext {

    /**
     * Call within the service lifecycle stop method to trigger an <em>asynchronous</em> lifecycle stop action.
     * This action will not be considered complete until indicated so by calling {@link #complete()} method on this interface.
     */
    void asynchronous();

    /**
     * Call when either <em>synchronous</em> or <em>asynchronous</em> lifecycle stop action is complete.
     * The user must do maximum effort to completely release all resources acquired in {@link Service#start(StartContext)} method.
     * Calling explicitly {@link StopContext#complete()} method is mandatory in case of <em>asynchronous</em> lifecycle stop action.
     * Calling {@link StopContext#complete()} method is optional in case of successful <em>synchronous</em> lifecycle stop action i.e.
     * when {@link Service#stop(StopContext)} method execution finished successfully and {@link StopContext#asynchronous()} have not been called.
     * In such case the container will detect it as successful <em>synchronous</em> stop operation
     * and will call {@link StopContext#complete()} method implicitly on behalf of the user.
     * <P>
     * Note that {@link Service#stop(StopContext)} method can never fail. Although it is allowed to throw {@link RuntimeException} from
     * {@link Service#stop(StopContext)} method when such use case is detected the container will log
     * that exception and will mark <em>synchronous</em> lifecycle stop method as successful i.e. it will call {@link StopContext#complete()} method
     * implicitly on behalf of the user regardless if it was successful or runtime exception have been thrown.
     * </P>
     * @throws IllegalStateException if called twice in a row.
     */
    void complete();

    /**
     * Gets dependency value. Returns dependency value if and only if dependency value was configured via
     * {@link ServiceBuilder#requires(String...)} method and this context wasn't invalidated, null otherwise.
     *
     * @param name required value or null
     * @return value associated with given name
     * @param <V> value type
     */
    <V> V getValue(String name);

}