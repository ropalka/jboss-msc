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

import static java.lang.Thread.currentThread;

import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class ServiceBuilderImpl implements ServiceBuilder {

    private final ServiceContainerImpl container;
    private final Thread thread = currentThread();
    private Map<String, ServiceRegistrationImpl> provides;
    private Map<String, ServiceRegistrationImpl> requires;
    private Service service;
    private ServiceMode mode;
    private Set<LifecycleListener> lifecycleListeners;
    private boolean installed;

    ServiceBuilderImpl(final ServiceContainerImpl container) {
        this.container = container;
    }

    @Override
    public ServiceBuilder requires(final String... values) {
        // preconditions
        assertNotInstalled();
        assertNotNull(values);
        assertThreadSafety();
        for (final String value : values) {
            assertNotNull(value);
            assertNotProvided(value, true);
        }
        // implementation
        for (final String value : values) {
            addRequiresInternal(value);
        }
        return this;
    }

    @Override
    public ServiceBuilder provides(final String... values) {
        // preconditions
        assertNotInstalled();
        assertNotNull(values);
        assertThreadSafety();
        for (final String value : values) {
            assertNotNull(value);
            assertNotRequired(value, false);
        }
        // implementation
        for (final String value : values) {
            addProvidesInternal(value);
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
        this.service = service != null ? service : Service.NULL;
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
    public ServiceBuilder addListener(final LifecycleListener listener) {
        // preconditions
        assertNotInstalled();
        assertNotNull(listener);
        assertThreadSafety();
        // implementation
        addListenerInternal(listener);
        return this;
    }

    @Override
    public ServiceController install() {
        // preconditions
        assertNotInstalled();
        assertThreadSafety();
        // implementation
        installed = true;
        if (service == null) service = Service.NULL;
        if (mode == null) mode = ServiceMode.ACTIVE;
        return container.install(this);
    }

    // implementation internals

    Service getService() {
        return service;
    }

    private void addRequiresInternal(final String name) {
        if (requires == null) requires = new HashMap<>();
        final Dependency existing = requires.get(name);
        if (existing == null) {
            requires.put(name, container.getOrCreateRegistration(name));
        }
    }

    private void addProvidesInternal(final String name) {
        if (provides == null) provides = new HashMap<>();
        final ServiceRegistrationImpl existing = provides.get(name);
        if (existing == null) {
            provides.put(name, container.getOrCreateRegistration(name));
        }
    }

    private void addListenerInternal(final LifecycleListener listener) {
        if (lifecycleListeners == null) lifecycleListeners = new IdentityHashSet<>();
        lifecycleListeners.add(listener);
    }

    Map<String, ServiceRegistrationImpl> getProvides() {
        return provides;
    }

    Map<String, ServiceRegistrationImpl> getRequires() {
        return requires == null ? Collections.emptyMap() : requires;
    }

    Set<LifecycleListener> getLifecycleListeners() {
        return lifecycleListeners == null ? Collections.emptySet() : lifecycleListeners;
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
            throw new IllegalStateException("Detected requires(), provides() or setInstance() call after setInstance() method call");
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
