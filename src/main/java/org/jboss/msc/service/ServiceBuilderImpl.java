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
import java.util.function.Consumer;

/**
 * Multi-value services {@link ServiceBuilder} implementation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class ServiceBuilderImpl implements ServiceBuilder {

    private final ServiceContainerImpl container;
    private final Thread thread = currentThread();
    private final Map<String, WritableValueImpl> provides = new HashMap<>();
    private Service service;
    private ServiceMode initialMode;
    private Map<String, Dependency> requires;
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
    @SuppressWarnings("unchecked")
    public <V> Consumer<V> provides(final String... values) {
        // preconditions
        assertNotInstalled();
        assertNotNull(values);
        assertThreadSafety();
        for (final String value : values) {
            assertNotNull(value);
            assertNotRequired(value, false);
        }
        // implementation
        final WritableValueImpl retVal = new WritableValueImpl();
        for (final String value : values) {
            addProvidesInternal(value, retVal);
        }
        return (Consumer<V>)retVal;
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
        this.initialMode = mode;
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
        if (initialMode == null) initialMode = ServiceMode.ACTIVE;
        return container.install(this);
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
            requires.put(name, container.getOrCreateRegistration(name));
        }
    }

    void addProvidesInternal(final String name, final WritableValueImpl dependency) {
        if (dependency != null) {
            provides.put(name, dependency);
        } else if (!provides.containsKey(name)) {
            provides.put(name, null);
        }
    }

    void addListenerInternal(final LifecycleListener listener) {
        if (lifecycleListeners == null) lifecycleListeners = new IdentityHashSet<>();
        lifecycleListeners.add(listener);
    }

    Map<String, WritableValueImpl> getProvides() {
        return provides;
    }

    Map<String, Dependency> getRequires() {
        return requires == null ? Collections.emptyMap() : requires;
    }

    Set<LifecycleListener> getLifecycleListeners() {
        return lifecycleListeners == null ? Collections.emptySet() : lifecycleListeners;
    }

    ServiceMode getInitialMode() {
        return initialMode;
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
        if (initialMode != null) {
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
