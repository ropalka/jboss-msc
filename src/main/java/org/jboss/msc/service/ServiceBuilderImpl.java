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
    private Mode mode;
    private Set<ServiceListener> listeners;
    private boolean installed;

    ServiceBuilderImpl(final ServiceContainerImpl container) {
        this.container = container;
    }

    @Override
    public ServiceBuilder requires(final String... valueNames) {
        // preconditions
        assertNotInstalled();
        assertNotNull(valueNames);
        assertThreadSafety();
        for (final String valueName : valueNames) {
            assertNotNull(valueName);
            assertNotProvided(valueName);
        }
        // implementation
        for (final String valueName : valueNames) {
            addRequiresInternal(valueName);
        }
        return this;
    }

    @Override
    public ServiceBuilder provides(final String... valueNames) {
        // preconditions
        assertNotInstalled();
        assertNotNull(valueNames);
        assertThreadSafety();
        for (final String valueName : valueNames) {
            assertNotNull(valueName);
            assertNotRequired(valueName);
        }
        // implementation
        for (final String valueName : valueNames) {
            addProvidesInternal(valueName);
        }
        return this;
    }

    @Override
    public ServiceBuilder instance(final Service service) {
        // preconditions
        assertNotInstalled();
        assertNotNull(service);
        assertThreadSafety();
        assertServiceInstanceNotConfigured();
        // implementation
        this.service = service;
        return this;
    }

    @Override
    public ServiceBuilder mode(final Mode mode) {
        // preconditions
        assertNotInstalled();
        assertNotNull(mode);
        assertModeNotConfigured();
        assertThreadSafety();
        // implementation
        this.mode = mode;
        return this;
    }

    @Override
    public ServiceBuilder addListener(final ServiceListener listener) {
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
        assertServiceUsability();
        assertServiceInstanceConfigured();
        // implementation
        installed = true;
        if (mode == null) mode = Mode.ACTIVE;
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

    private void addListenerInternal(final ServiceListener listener) {
        if (listeners == null) listeners = new IdentityHashSet<>();
        listeners.add(listener);
    }

    Map<String, ServiceRegistrationImpl> getProvides() {
        return provides == null ? Collections.emptyMap() : provides;
    }

    Map<String, ServiceRegistrationImpl> getRequires() {
        return requires == null ? Collections.emptyMap() : requires;
    }

    Set<ServiceListener> getListeners() {
        return listeners == null ? Collections.emptySet() : listeners;
    }

    Mode getMode() {
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

    private void assertNotRequired(final String valueName) {
        if (requires != null && requires.containsKey(valueName)) {
            throw new IllegalArgumentException("Cannot both require and provide value name: " + valueName);
        }
    }

    private void assertNotProvided(final String valueName) {
        if (provides != null && provides.containsKey(valueName)) {
            throw new IllegalArgumentException("Cannot both require and provide value name: " + valueName);
        }
    }

    private void assertServiceInstanceNotConfigured() {
        if (service != null) {
            throw new IllegalStateException("instance() method called twice");
        }
    }

    private void assertServiceInstanceConfigured() {
        if (service == null) {
            throw new IllegalStateException("instance() method have not been called");
        }
    }

    private void assertServiceUsability() {
        if (requires == null && provides == null) {
            throw new IllegalStateException("either requires() or provides() method must be called");
        }
    }
    private void assertModeNotConfigured() {
        if (mode != null) {
            throw new IllegalStateException("mode() method called twice");
        }
    }

    private static void assertNotNull(final Object parameter) {
        if (parameter == null) {
            throw new NullPointerException("Method parameter cannot be null");
        }
    }

}
