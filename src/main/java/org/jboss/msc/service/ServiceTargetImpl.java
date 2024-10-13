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

import static java.util.Collections.synchronizedSet;

import java.util.Set;

/**
 * Abstract base class used for ServiceTargets.
 *
 * @author John Bailey
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class ServiceTargetImpl implements ServiceTarget {

    private final ServiceTargetImpl parent;
    private final Set<LifecycleListener> lifecycleListeners = synchronizedSet(new IdentityHashSet<>());
    private final Set<StabilityMonitor> monitors = synchronizedSet(new IdentityHashSet<>());

    ServiceTargetImpl(final ServiceTargetImpl parent) {
        if (parent == null) {
            throw new IllegalStateException("parent is null");
        }
        this.parent = parent;
    }

    ServiceTargetImpl() {
        this.parent = null;
    }

    @Override
    public ServiceBuilder addService() {
        return new ServiceBuilderImpl(null, this, null);
    }

    public ServiceTarget addListener(final LifecycleListener listener) {
        if (listener != null) {
            lifecycleListeners.add(listener);
        }
        return this;
    }

    @Override
    public ServiceTarget addMonitor(final StabilityMonitor monitor) {
        if (monitor != null) {
            monitors.add(monitor);
        }
        return this;
    }

    @Override
    public ServiceTarget removeMonitor(final StabilityMonitor monitor) {
        if (monitor != null) {
            monitors.remove(monitor);
        }
        return this;
    }

    @Override
    public ServiceTarget removeListener(final LifecycleListener listener) {
        if (listener != null) lifecycleListeners.remove(listener);
        return this;
    }

    /**
     * Apply monitors & listeners to {@code serviceBuilder}.
     * 
     * @param serviceBuilder serviceBuilder which listeners and dependencies will be added to.
     */
    void apply(ServiceBuilderImpl serviceBuilder) {
        synchronized (monitors) {
            serviceBuilder.addMonitorsNoCheck(monitors);
        }
        synchronized (lifecycleListeners) {
            serviceBuilder.addLifecycleListenersNoCheck(lifecycleListeners);
        }
    }

    /**
     * Install {@code serviceBuilder} in this target.
     *
     * @param serviceBuilder a serviceBuilder created by this ServiceTarget
     *
     * @return the installed service controller
     *
     * @throws ServiceRegistryException if a service registry issue occurred during installation
     */
    ServiceController install(ServiceBuilderImpl serviceBuilder) throws ServiceRegistryException {
        apply(serviceBuilder);
        return parent.install(serviceBuilder);
    }

    ServiceRegistrationImpl getOrCreateRegistration(final ServiceName name) {
        return parent.getOrCreateRegistration(name);
    }

    @Override
    public ServiceTarget subTarget() {
        return new ServiceTargetImpl(this);
    }
}
