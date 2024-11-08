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

import java.util.function.Consumer;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class WritableValueImpl implements Consumer<Object> {

    private static final Object UNDEFINED = new Object();
    private volatile ServiceController controller;
    volatile Object value = UNDEFINED;

    Object getValue() {
        final Object value = this.value;
        if (UNDEFINED != value) return value;
        throw new IllegalStateException("Service unavailable");
    }

    @Override
    public void accept(final Object newValue) {
        final ServiceController controller = this.controller;
        if (controller != null) synchronized (controller) {
            final ServiceState state = controller.state();
            if (state == ServiceState.STARTING) {
                value = newValue;
                return;
            } else if (state == ServiceState.STOPPING) {
                if (newValue != null) {
                    throw new IllegalArgumentException("Null parameter expected");
                }
                value = UNDEFINED;
                return;
            }
        }
        throw new IllegalStateException("Outside of Service lifecycle method");
    }

    void uninject() {
        final ServiceController controller = this.controller;
        if (controller != null) synchronized (controller) {
            final ServiceState state = controller.state();
            if (state == ServiceState.STARTING || state == ServiceState.STOPPING) {
                value = UNDEFINED;
                return;
            }
        }
        throw new IllegalStateException("Outside of Service lifecycle method");
    }

    void setInstance(final ServiceController controller) {
        this.controller = controller;
    }

}
