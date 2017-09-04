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

/**
 * An optional dependency. Creates bridge between dependent and dependency.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class OptionalDependencyImpl implements Dependency, Dependent {

    private final Dependency dependency;
    private volatile Dependent dependent;
    private volatile boolean available = true;

    OptionalDependencyImpl(final Dependency dependency) {
        this.dependency = dependency;
    }

    @Override
    public void addDependent(final Dependent dependent) {
        debug("addDependent(" + dependent + ") START");
        assert this.dependent == null;
        this.dependent = dependent;
        dependency.addDependent(this);
        debug("addDependent(" + dependent + ") END");
    }

    @Override
    public boolean removeDependent(final Dependent dependent) {
        debug("removeDependent(" + dependent + ") START");
        assert this.dependent == dependent;
        this.dependent = null;
        debug("removeDependent(" + dependent + ") END");
        return dependency.removeDependent(this);
    }

    @Override
    public void addDemand() {
        debug("addDemand() START");
        dependency.addDemand();
        debug("addDemand() END");
    }

    @Override
    public void removeDemand() {
        debug("removeDemand() START");
        dependency.removeDemand();
        debug("removeDemand() END");
    }

    @Override
    public void dependentStarted() {
        debug("dependentStarted() START");
        dependency.dependentStarted();
        debug("dependentStarted() END");
    }

    @Override
    public void dependentStopped() {
        debug("dependentStopped() START");
        dependency.dependentStopped();
        debug("dependentStopped() END");
    }

    @Override
    public Object getValue() {
        try {
            return available ? dependency.getValue() : null;
        } catch (final IllegalStateException ignored) {
            return null;
        }
    }

    @Override
    public ServiceName getName() {
        return dependency.getName();
    }

    public ServiceControllerImpl<?> getDependencyController() {
        return dependency.getDependencyController();
    }

    @Override
    public void dependencyAvailable() {
        debug("dependencyAvailable() START");
        available = true;
        final Dependent dependent = this.dependent;
        if (dependent != null) dependent.dependencyDown();
        debug("dependencyAvailable() END");
    }

    @Override
    public void dependencyUnavailable() {
        debug("dependencyUnavailable() START");
        available = false;
        final Dependent dependent = this.dependent;
        if (dependent != null) dependent.dependencyUp();
        debug("dependencyUnavailable() END");
    }

    @Override
    public void dependencyUp() {
        debug("dependencyUp() START");
        final Dependent dependent = this.dependent;
        if (dependent != null) dependent.dependencyUp();
        debug("dependencyUp() END");
    }

    @Override
    public void dependencyDown() {
        debug("dependencyDown() START");
        final Dependent dependent = this.dependent;
        if (dependent != null) dependent.dependencyDown();
        debug("dependencyDown() END");
    }

    @Override
    public void dependencyFailed() {
        debug("dependencyFailed() START");
        final Dependent dependent = this.dependent;
        if (dependent != null) dependent.dependencyFailed();
        debug("dependencyFailed() END");
    }

    @Override
    public void dependencySucceeded() {
        debug("dependencySucceeded() START");
        final Dependent dependent = this.dependent;
        if (dependent != null) dependent.dependencySucceeded();
        debug("dependencySucceeded() END");
    }

    @Override
    public ServiceControllerImpl<?> getDependentController() {
        final Dependent dependent = this.dependent;
        return dependent != null ? dependent.getDependentController() : null;
    }

    @Override
    public Lockable getLock() {
        return dependency.getLock();
    }

    @Override
    public String toString() {
        return "OPTIONAL(" + dependency + ")";
    }

    private void debug(final String msg) {
        DebugUtils.debug(this + " " + msg);
    }

}
