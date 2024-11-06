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
 * A service registry.  Registries can return services by name, or get a collection of service names.
 *
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ServiceRegistry {

    /**
     * Get a controller of service providing given value.
     * This method can return {@code null} if there is no such service.
     * This can happen if value was only declared as required value by some installed service
     * but there is no service installed yet providing given value.
     *
     * @param valueName the name of the value
     * @return either a controller of service providing given value or {@code null}
     */
    ServiceController controllerOfValue(ServiceName valueName);

    /**
     * Get value names that are required or provided by already installed services.
     *
     * @return all value names declared by installed services
     */
    Set<ServiceName> valueNames();

}
