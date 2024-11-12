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

import java.util.Collection;

/**
 * Indicates service installation process failed because there was a dependencies
 * cycle detected on attempt to install a new service to the container.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class CycleDetectedException extends RuntimeException {
    private static final long serialVersionUID = -4826336558749601678L;
    private final Collection<String> cycle;

    CycleDetectedException(final String msg, final Collection<String> cycle) {
        super(msg);
        this.cycle = cycle;
    }

    /**
     * Returns a dependencies cycle description identified on service installation attempt.
     * 
     * @return a collection of services description involved in the cycle, in dependency order.
     */
    public Collection<String> getCycle() {
        return cycle;
    }
}
