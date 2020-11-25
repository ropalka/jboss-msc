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
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Multi-value services {@link ServiceBuilder} implementation.
 *
 * @param <T> the type of service being built
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class ServiceBuilderImpl<T> implements ServiceBuilder<T> {

    final ServiceName serviceId;
    final ServiceControllerImpl<?> parent;
    private final ServiceTargetImpl serviceTarget;
    private final Thread thread = currentThread();
    private final Map<ServiceName, WritableValueImpl> provides = new HashMap<>();
    private Service service;
    private ServiceController.Mode initialMode;
    private Map<ServiceName, Dependency> requires;
    private Set<LifecycleListener> lifecycleListeners;
    private boolean installed;

    ServiceBuilderImpl(final ServiceName serviceId, final ServiceTargetImpl serviceTarget, final ServiceControllerImpl<?> parent) {
        this.serviceId = serviceId;
        this.serviceTarget = serviceTarget;
        this.parent = parent;
        addProvidesInternal(serviceId, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V> Supplier<V> requires(final ServiceName dependency) {
        // preconditions
        assertNotInstalled();
        assertNotNull(dependency);
        assertThreadSafety();
        assertNotInstanceId(dependency);
        assertNotProvided(dependency, true);
        // implementation
        return (Supplier<V>) ((ServiceRegistrationImpl)addRequiresInternal(dependency)).getReadableValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V> Consumer<V> provides(final ServiceName... dependencies) {
        // preconditions
        assertNotInstalled();
        assertNotNull(dependencies);
        assertThreadSafety();
        for (final ServiceName dependency : dependencies) {
            assertNotNull(dependency);
            assertNotRequired(dependency, false);
            assertNotProvided(dependency, false);
        }
        // implementation
        final WritableValueImpl retVal = new WritableValueImpl();
        for (final ServiceName dependency : dependencies) {
            addProvidesInternal(dependency, retVal);
        }
        return (Consumer<V>)retVal;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ServiceBuilder<T> setInstance(final Service service) {
        // preconditions
        assertNotInstalled();
        assertThreadSafety();
        assertServiceNotConfigured();
        // implementation
        this.service = service != null ? service : NullService.INSTANCE;
        return this;
    }

    @Override
    public ServiceBuilder<T> setInitialMode(final ServiceController.Mode mode) {
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
    public ServiceBuilder<T> addListener(final LifecycleListener listener) {
        // preconditions
        assertNotInstalled();
        assertNotNull(listener);
        assertThreadSafety();
        // implementation
        addListenerInternal(listener);
        return this;
    }

    @Override
    public ServiceController<T> install() throws ServiceRegistryException {
        // preconditions
        assertNotInstalled();
        assertThreadSafety();
        // implementation
        installed = true;
        if (service == null) service = NullService.INSTANCE;
        if (initialMode == null) initialMode = ServiceController.Mode.ACTIVE;
        return serviceTarget.install(this);
    }

    // implementation internals

    void addLifecycleListenersNoCheck(final Set<LifecycleListener> listeners) {
        if (listeners == null || listeners.isEmpty()) return;
        for (final LifecycleListener listener : listeners) {
            if (listener != null) addListenerInternal(listener);
        }
    }

    Service getService() {
        return service;
    }

    private Dependency addRequiresInternal(final ServiceName name) {
        if (requires == null) requires = new HashMap<>();
        if (requires.size() == ServiceControllerImpl.MAX_DEPENDENCIES) {
            throw new IllegalArgumentException("Too many dependencies specified (max is " + ServiceControllerImpl.MAX_DEPENDENCIES + ")");
        }
        final Dependency existing = requires.get(name);
        if (existing != null) {
            return existing;
        }
        final Dependency dependency = serviceTarget.getOrCreateRegistration(name);
        requires.put(name, dependency);
        return dependency;
    }

    void addProvidesInternal(final ServiceName name, final WritableValueImpl dependency) {
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

    Map<ServiceName, WritableValueImpl> getProvides() {
        return provides;
    }

    Map<ServiceName, Dependency> getDependencies() {
        return requires == null ? Collections.emptyMap() : requires;
    }

    Set<LifecycleListener> getLifecycleListeners() {
        return lifecycleListeners == null ? Collections.emptySet() : lifecycleListeners;
    }

    ServiceController.Mode getInitialMode() {
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

    private void assertNotInstanceId(final ServiceName dependency) {
        if (serviceId.equals(dependency)) {
            throw new IllegalArgumentException("Cannot both require and provide same dependency:" + dependency);
        }
    }

    private void assertNotRequired(final ServiceName dependency, final boolean processingRequires) {
        if (requires != null && requires.keySet().contains(dependency)) {
            if (processingRequires) {
                throw new IllegalArgumentException("Cannot require dependency more than once:" + dependency);
            } else {
                throw new IllegalArgumentException("Cannot both require and provide same dependency:" + dependency);
            }
        }
    }

    private void assertNotProvided(final ServiceName dependency, final boolean processingRequires) {
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
        if (initialMode != null) {
            throw new IllegalStateException("setInitialMode() method called twice");
        }
    }

    private static void assertNotNull(final Object parameter) {
        if (parameter == null) {
            throw new NullPointerException("Method parameter cannot be null");
        }
    }

    private static void assertNotRemove(final ServiceController.Mode mode) {
        if (mode == ServiceController.Mode.REMOVE) {
            throw new IllegalArgumentException("Initial service mode cannot be REMOVE");
        }
    }

}
