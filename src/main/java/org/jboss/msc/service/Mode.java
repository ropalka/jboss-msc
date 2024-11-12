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
 * The service mode.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public enum Mode {
    /**
     * Only come up if all dependencies are satisfied <b>and</b> at least one dependent demands to start.
     */
    ON_DEMAND,
    /**
     * Only come up if all dependencies are satisfied <b>and</b> at least one dependent demands to start.
     * Once in the {@link ServiceState#UP UP} state, it will remain that way regardless of demands from dependents.
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
