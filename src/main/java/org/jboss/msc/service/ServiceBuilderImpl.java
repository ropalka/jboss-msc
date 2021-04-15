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

import static java.lang.Thread.currentThread;

import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class ServiceBuilderImpl implements ServiceBuilder {

    private final ServiceContainerImpl serviceContainer;
    private final Thread thread = currentThread();
    private final Map<String, ServiceRegistrationImpl> provides = new HashMap<>();
    private Map<String, Dependency> requires;
    private Service service;
    private ServiceMode mode;
    private boolean installed;

    ServiceBuilderImpl(final ServiceContainerImpl serviceContainer) {
        this.serviceContainer = serviceContainer;
    }

    @Override
    public ServiceBuilder requires(final String... dependencies) {
        // preconditions
        assertNotInstalled();
        assertNotNull(dependencies);
        assertThreadSafety();
        for (String dependency : dependencies) {
            assertNotNull(dependency);
            assertNotProvided(dependency, true);
        }
        // implementation
        for (String dependency : dependencies) {
            addRequiresInternal(dependency);
        }
        return this;
    }

    @Override
    public ServiceBuilder provides(final String... dependencies) {
        // preconditions
        assertNotInstalled();
        assertNotNull(dependencies);
        assertThreadSafety();
        for (String dependency : dependencies) {
            assertNotNull(dependency);
            assertNotRequired(dependency, false);
            assertNotProvided(dependency, false);
        }
        // implementation
        for (String dependency : dependencies) {
            addProvidesInternal(dependency);
        }
        return this;
    }

    @Override
    public ServiceBuilder instance(final Service service) {
        // preconditions
        assertNotInstalled();
        assertThreadSafety();
        assertServiceNotConfigured();
        // implementation
        this.service = service != null ? service : NullService.INSTANCE;
        return this;
    }

    @Override
    public ServiceBuilder mode(final ServiceMode mode) {
        // preconditions
        assertNotInstalled();
        assertNotNull(mode);
        assertNotRemove(mode);
        assertModeNotConfigured();
        assertThreadSafety();
        // implementation
        this.mode = mode;
        return this;
    }

    @Override
    public ServiceController install() {
        // preconditions
        assertNotInstalled();
        assertThreadSafety();
        // implementation
        installed = true;
        // TODO: ensure at least one provides exists
        // TODO: ensure service is not null
        if (mode == null) mode = ServiceMode.ACTIVE;
        return serviceContainer.install(this);
    }

    // implementation internals

    Service getService() {
        return service;
    }

    private void addRequiresInternal(final String name) {
        if (requires == null) requires = new HashMap<>();
        if (requires.size() == ServiceControllerImpl.MAX_DEPENDENCIES) {
            throw new IllegalArgumentException("Too many dependencies specified (max is " + ServiceControllerImpl.MAX_DEPENDENCIES + ")");
        }
        final Dependency existing = requires.get(name);
        if (existing == null) {
            requires.put(name, serviceContainer.getOrCreateRegistration(name));
        }
    }

    void addProvidesInternal(final String name) {
        provides.put(name, serviceContainer.getOrCreateRegistration(name));
    }

    Map<String, ServiceRegistrationImpl> getProvides() {
        return provides;
    }

    Map<String, Dependency> getDependencies() {
        return requires == null ? Collections.emptyMap() : requires;
    }

    ServiceMode getMode() {
        return mode;
    }

    // implementation assertions

    private void assertNotInstalled() {
        if (installed) {
            throw new IllegalStateException("ServiceBuilder already installed");
        }
    }

    private void assertThreadSafety() {
        if (thread != currentThread()) {
            throw new ConcurrentModificationException("ServiceBuilder used by multiple threads");
        }
    }

    private void assertNotRequired(final String dependency, final boolean processingRequires) {
        if (requires != null && requires.keySet().contains(dependency)) {
            if (processingRequires) {
                throw new IllegalArgumentException("Cannot require dependency more than once:" + dependency);
            } else {
                throw new IllegalArgumentException("Cannot both require and provide same dependency:" + dependency);
            }
        }
    }

    private void assertNotProvided(final String dependency, final boolean processingRequires) {
        if (processingRequires) {
            if (provides.containsKey(dependency)) {
                throw new IllegalArgumentException("Cannot both require and provide same dependency:" + dependency);
            }
        } else {
            if (provides.get(dependency) != null) {
                throw new IllegalArgumentException("Cannot provide dependency more than once: " + dependency);
            }
        }
    }

    private void assertServiceNotConfigured() {
        if (service != null) {
            throw new IllegalStateException("Detected addAliases(), requires(), provides() or setInstance() call after setInstance() method call");
        }
    }

    private void assertModeNotConfigured() {
        if (mode != null) {
            throw new IllegalStateException("setInitialMode() method called twice");
        }
    }

    private static void assertNotNull(final Object parameter) {
        if (parameter == null) {
            throw new NullPointerException("Method parameter cannot be null");
        }
    }

    private static void assertNotRemove(final ServiceMode mode) {
        if (mode == ServiceMode.REMOVE) {
            throw new IllegalArgumentException("Initial service mode cannot be REMOVE");
        }
    }
}
