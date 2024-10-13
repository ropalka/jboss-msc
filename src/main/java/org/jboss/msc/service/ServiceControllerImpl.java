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

import static java.lang.Thread.holdsLock;
import static org.jboss.msc.service.SecurityUtils.getCL;
import static org.jboss.msc.service.SecurityUtils.setTCCL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The service controller implementation.
 *
 * @param <S> the service type
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class ServiceControllerImpl<S> implements ServiceController<S>, Dependent {

    private static final String ILLEGAL_CONTROLLER_STATE = "Illegal controller state";

    private static final int DEPENDENCY_AVAILABLE_TASK = 1;
    private static final int DEPENDENCY_UNAVAILABLE_TASK = 1 << 1;
    private static final int DEPENDENCY_STARTED_TASK = 1 << 2;
    private static final int DEPENDENCY_STOPPED_TASK = 1 << 3;
    private static final int DEPENDENCY_FAILED_TASK = 1 << 4;
    private static final int DEPENDENCY_RETRYING_TASK = 1 << 5;

    /**
     * The service container.
     */
    private final ServiceContainerImpl container;
    /**
     * The service identifier.
     */
    private final ServiceName serviceId;
    /**
     * The service itself.
     */
    final org.jboss.msc.Service service;
    /**
     * Lifecycle listeners.
     */
    private final Set<LifecycleListener> lifecycleListeners;
    /**
     * Container shutdown listener.
     */
    private ContainerShutdownListener shutdownListener;
    /**
     * The set of registered stability monitors.
     */
    private final Set<StabilityMonitor> monitors;
    /**
     * Required dependencies by this service.
     */
    private final Set<Dependency> requires;
    /**
     * Provided dependencies by this service.
     */
    private final Map<ServiceRegistrationImpl, WritableValueImpl> provides;
    /**
     * The parent of this service.
     */
    private final ServiceControllerImpl<?> parent;
    /**
     * The children of this service (only valid during {@link State#UP}).
     */
    private final Set<ServiceControllerImpl<?>> children;

    private final Set<ServiceName> requiredValues;
    private final Set<ServiceName> providedValues;
    /**
     * The start exception.
     */
    private StartException startException;
    /**
     * The controller mode.
     */
    private ServiceController.Mode mode = ServiceController.Mode.NEVER;
    /**
     * The controller state.
     */
    private Substate state = Substate.NEW;
    /**
     * Tracks which dependent tasks have completed its execution.
     * First 16 bits track if dependent task have been scheduled.
     * Second 16 bits track whether scheduled dependent task finished its execution.
     */
    private int execFlags;
    /**
     * The number of registrations which place a demand-to-start on this
     * instance. If this value is >0, propagate a demand up to all parent
     * dependents. If this value is >0 and mode is ON_DEMAND, we should start.
     */
    private int demandedByCount;
    /**
     * Count for dependencies that are trying to stop.  If this count is greater than zero then
     * dependents will be notified that a stop is necessary.
     */
    private int stoppingDependencies;
    /**
     * Count of unavailable dependencies of this service.
     */
    private int unavailableDependencies;
    /**
     * The number of dependents that are currently running. The deployment will
     * not execute the {@code stop()} method (and subsequently leave the
     * {@link org.jboss.msc.service.ServiceController.State#STOPPING} state)
     * until all running dependents (and listeners) are stopped.
     */
    private int runningDependents;
    /**
     * Count for failure notification. It indicates how many services have
     * failed to start and are not recovered so far. This count monitors
     * failures that happen when starting this service, and dependency related
     * failures as well. When incremented from 0 to 1, it is time to notify
     * dependents and listeners that a failure occurred. When decremented from 1
     * to 0, the dependents and listeners are notified that the affected
     * services are retrying to start. Values larger than 1 are ignored to avoid
     * multiple notifications.
     */
    private int failCount;
    /**
     * Indicates whether dependencies have been demanded.
     */
    private boolean dependenciesDemanded = false;
    /**
     * The number of asynchronous tasks that are currently running. This
     * includes listeners, start/stop methods, outstanding asynchronous
     * start/stops, and internal tasks.
     */
    private int asyncTasks;
    /**
     * Tasks executed last on transition outside the lock.
     */
    private final List<Runnable> listenerTransitionTasks = new ArrayList<>();
    /**
     * The service target for adding child services (can be {@code null} if none
     * were added).
     */
    private volatile ChildServiceTarget childTarget;
    /**
     * The system nanotime of the moment in which the last lifecycle change was
     * initiated.
     */
    @SuppressWarnings("VolatileLongOrDoubleField")
    private volatile long lifecycleTime;

    private static final String[] NO_STRINGS = new String[0];

    static final int MAX_DEPENDENCIES = (1 << 14) - 1;

    ServiceControllerImpl(final ServiceContainerImpl container, final ServiceName serviceId, final org.jboss.msc.Service service, final Set<Dependency> requires, final Map<ServiceRegistrationImpl, WritableValueImpl> provides, final Set<StabilityMonitor> monitors, final Set<LifecycleListener> lifecycleListeners, final ServiceControllerImpl<?> parent) {
        assert requires.size() <= MAX_DEPENDENCIES;
        this.container = container;
        this.serviceId = serviceId;
        this.service = service;
        this.requires = requires;
        this.requiredValues = unmodifiableSetOf(requires);
        this.provides = provides;
        this.providedValues = unmodifiableSetOf(provides.keySet());
        this.lifecycleListeners = new IdentityHashSet<>(lifecycleListeners);
        this.monitors = new IdentityHashSet<>(monitors);
        // We also need to register this controller with monitors explicitly.
        // This allows inherited monitors to have registered all child controllers
        // and later to remove them when inherited stability monitor is cleared.
        for (final StabilityMonitor monitor : monitors) {
            monitor.addControllerNoCallback(this);
        }
        this.parent = parent;
        int depCount = requires.size();
        stoppingDependencies = parent == null ? depCount : depCount + 1;
        children = new IdentityHashSet<>();
    }

    private static Set<ServiceName> unmodifiableSetOf(final Set<? extends Dependency> set) {
        if (set.isEmpty()) return Collections.EMPTY_SET;
        final Set<ServiceName> temp = new HashSet<>(set.size());
        for (Dependency dependency : set) {
            temp.add(dependency.getName());
        }
        return Collections.unmodifiableSet(temp);
    }

    /**
     * Set this instance into serviceName registration.
     * <p></p>
     * All notifications from registrations will be ignored until the
     * installation is {@link #commitInstallation(org.jboss.msc.service.ServiceController.Mode) committed}.
     */
    void startInstallation() {
        ServiceRegistrationImpl registration;
        WritableValueImpl injector;
        Lockable lock;
        for (Entry<ServiceRegistrationImpl, WritableValueImpl> provided : provides.entrySet()) {
            registration = provided.getKey();
            injector = provided.getValue();
            lock = registration.getLock();
            synchronized (lock) {
                lock.acquireWrite();
                try {
                    registration.set(this, injector);
                    if (injector != null) {
                        injector.setInstance(this);
                    }
                } finally {
                    lock.releaseWrite();
                }
            }
        }
    }

    /**
     * Start this service configuration connecting it to its parent and dependencies.
     * <p></p>
     * All notifications from dependencies and parents will be ignored until the
     * installation is {@link #commitInstallation(org.jboss.msc.service.ServiceController.Mode) committed}.
     */
    void startConfiguration() {
        Lockable lock;
        for (Dependency dependency : requires) {
            lock = dependency.getLock();
            synchronized (lock) {
                lock.acquireWrite();
                try {
                    dependency.addDependent(this);
                } finally {
                    lock.releaseWrite();
                }
            }
        }
        if (parent != null) parent.addChild(this);
    }

    /**
     * Commit the service install, kicking off the mode set and listener execution.
     *
     * @param initialMode the initial service mode
     */
    void commitInstallation(Mode initialMode) {
        assert (state == Substate.NEW);
        assert initialMode != null;
        assert !holdsLock(this);

        final List<Runnable> tasks;
        synchronized (this) {
            if (container.isShutdown()) {
                throw new IllegalStateException ("Container is down");
            }
            final boolean leavingRestState = isStableRestState();
            internalSetMode(initialMode);
            tasks = transition();
            addAsyncTasks(tasks.size());
            updateStabilityState(leavingRestState);
        }
        doExecute(tasks);
    }

    /**
     * Roll back the service install.
     */
    void rollbackInstallation() {
        final Runnable removeTask;
        synchronized (this) {
            final boolean leavingRestState = isStableRestState();
            mode = Mode.REMOVE;
            state = Substate.REMOVING;
            removeTask = new RemoveTask();
            incrementAsyncTasks();
            updateStabilityState(leavingRestState);
        }
        removeTask.run();
    }

    /**
     * Return {@code true} only if this service controller installation is committed.
     *
     * @return true if this service controller installation is committed
     */
    boolean isInstallationCommitted() {
        assert holdsLock(this);
        return state.compareTo(Substate.NEW) > 0;
    }

    /**
     * Controller notifications are ignored (we do not create new tasks on notification) if
     * controller didn't finish its installation process.
     *
     * @return true if notification must be ignored, false otherwise
     */
    private boolean ignoreNotification() {
        assert holdsLock(this);
        return state == Substate.NEW;
    }

    /**
     * Determine whether a stopped controller should start.
     *
     * @return {@code true} if so
     */
    private boolean shouldStart() {
        assert holdsLock(this);
        return mode == Mode.ACTIVE || mode == Mode.PASSIVE && stoppingDependencies == 0 || demandedByCount > 0 && (mode == Mode.ON_DEMAND || mode == Mode.LAZY);
    }

    /**
     * Determine whether a running controller should stop.
     *
     * @return {@code true} if so
     */
    private boolean shouldStop() {
        assert holdsLock(this);
        return mode == Mode.REMOVE || mode == Mode.NEVER || demandedByCount == 0 && mode == Mode.ON_DEMAND;
    }

    /**
     * Returns true if controller is in rest state and no async tasks are running, false otherwise.
     * @return true if stable rest state, false otherwise
     */
    private boolean isStableRestState() {
        assert holdsLock(this);
        return asyncTasks == 0 && state.isRestState();
    }

    private void updateStabilityState(final boolean leavingStableRestState) {
        assert holdsLock(this);
        final boolean enteringStableRestState = state.isRestState() && asyncTasks == 0;
        if (leavingStableRestState) {
            if (!enteringStableRestState) {
                container.incrementUnstableServices();
                for (StabilityMonitor monitor : monitors) {
                    monitor.incrementUnstableServices();
                }
            }
        } else {
            if (enteringStableRestState) {
                container.decrementUnstableServices();
                for (StabilityMonitor monitor : monitors) {
                    monitor.decrementUnstableServices();
                }
                if (state == Substate.REMOVED) {
                    for (StabilityMonitor monitor : monitors) {
                        monitor.removeControllerNoCallback(this);
                    }
                    if (shutdownListener != null) {
                        shutdownListener.controllerDied();
                        shutdownListener = null;
                    }
                }
            }
        }
    }

    /**
     * Identify the transition to take.  Call under lock.
     *
     * @return the transition or {@code null} if none is needed at this time
     */
    private Transition getTransition() {
        assert holdsLock(this);
        switch (state) {
            case NEW: {
                if (!container.isShutdown()) {
                    return Transition.NEW_to_DOWN;
                }
                break;
            }
            case DOWN: {
                if (mode == ServiceController.Mode.REMOVE) {
                    return Transition.DOWN_to_REMOVING;
                } else if (shouldStart()) {
                    if (unavailableDependencies > 0 || failCount > 0) {
                        return Transition.DOWN_to_PROBLEM;
                    }
                    if (stoppingDependencies == 0) {
                        return Transition.DOWN_to_START_REQUESTED;
                    }
                }
                break;
            }
            case PROBLEM: {
                if (!shouldStart() || (unavailableDependencies == 0 && failCount == 0)) {
                    return Transition.PROBLEM_to_DOWN;
                }
                break;
            }
            case START_REQUESTED: {
                if (shouldStart() && stoppingDependencies == 0) {
                    return Transition.START_REQUESTED_to_STARTING;
                } else {
                    return Transition.START_REQUESTED_to_DOWN;
                }
            }
            case STARTING: {
                if (startException == null) {
                    return Transition.STARTING_to_UP;
                } else {
                    return Transition.STARTING_to_START_FAILED;
                }
            }
            case START_FAILED: {
                if (children.isEmpty()) {
                    if (shouldStart() && stoppingDependencies == 0) {
                        if (startException == null) {
                            return Transition.START_FAILED_to_STARTING;
                        }
                    } else {
                        return Transition.START_FAILED_to_DOWN;
                    }
                }
                break;
            }
            case UP: {
                if (shouldStop() || stoppingDependencies > 0) {
                    return Transition.UP_to_STOP_REQUESTED;
                }
                break;
            }
            case STOP_REQUESTED: {
                if (shouldStart() && stoppingDependencies == 0) {
                    return Transition.STOP_REQUESTED_to_UP;
                } else if (runningDependents == 0) {
                    return Transition.STOP_REQUESTED_to_STOPPING;
                }
                break;
            }
            case STOPPING: {
                if (children.isEmpty()) {
                    return Transition.STOPPING_to_DOWN;
                }
                break;
            }
            case REMOVING: {
                return Transition.REMOVING_to_REMOVED;
            }
            case REMOVED: {
                // no possible actions
                break;
            }
        }
        return null;
    }

    private boolean postTransitionTasks(final List<Runnable> tasks) {
        assert holdsLock(this);
        // Listener transition tasks are executed last for ongoing transition and outside of intrinsic lock
        if (listenerTransitionTasks.size() > 0) {
            tasks.addAll(listenerTransitionTasks);
            listenerTransitionTasks.clear();
            return true;
        }
        return false;
    }

    /**
     * Run the locked portion of a transition. Call under the lock.
     *
     * @return returns list of async tasks to execute
     */
    private List<Runnable> transition() {
        assert holdsLock(this);
        if (asyncTasks != 0) {
            // no movement possible
            return Collections.EMPTY_LIST;
        }
        final List<Runnable> tasks = new ArrayList<>();
        if (postTransitionTasks(tasks)) {
            // no movement possible
            return tasks;
        }
        // clean up tasks execution flags
        execFlags = 0;

        Transition transition;
        do {
            // first of all, check if dependencies & parent should be un/demanded
            switch (mode) {
                case NEVER:
                case REMOVE:
                    if (dependenciesDemanded) {
                        tasks.add(new UndemandDependenciesTask());
                        dependenciesDemanded = false;
                    }
                    break;
                case LAZY: {
                    if (state == Substate.UP) {
                        if (!dependenciesDemanded) {
                            tasks.add(new DemandDependenciesTask());
                            dependenciesDemanded = true;
                        }
                        break;
                    }
                    // fall thru!
                }
                case ON_DEMAND:
                case PASSIVE: {
                    if (demandedByCount > 0 && !dependenciesDemanded) {
                        tasks.add(new DemandDependenciesTask());
                        dependenciesDemanded = true;
                    } else if (demandedByCount == 0 && dependenciesDemanded) {
                        tasks.add(new UndemandDependenciesTask());
                        dependenciesDemanded = false;
                    }
                    break;
                }
                case ACTIVE: {
                    if (!dependenciesDemanded) {
                        tasks.add(new DemandDependenciesTask());
                        dependenciesDemanded = true;
                    }
                    break;
                }
            }
            transition = getTransition();
            if (transition == null) {
                return tasks;
            }
            switch (transition) {
                case NEW_to_DOWN: {
                    getListenerTasks(LifecycleEvent.DOWN, listenerTransitionTasks);
                    break;
                }
                case DOWN_to_PROBLEM: {
                    container.addProblem(this);
                    for (StabilityMonitor monitor : monitors) {
                        monitor.addProblem(this);
                    }
                    break;
                }
                case PROBLEM_to_DOWN: {
                    container.removeProblem(this);
                    for (StabilityMonitor monitor : monitors) {
                        monitor.removeProblem(this);
                    }
                    break;
                }
                case DOWN_to_START_REQUESTED: {
                    lifecycleTime = System.nanoTime();
                    tasks.add(new DependencyAvailableTask());
                    tasks.add(new DependentStartedTask());
                    break;
                }
                case START_REQUESTED_to_STARTING: {
                    tasks.add(new StartTask());
                    break;
                }
                case STARTING_to_UP: {
                    getListenerTasks(LifecycleEvent.UP, listenerTransitionTasks);
                    tasks.add(new DependencyStartedTask());
                    break;
                }
                case UP_to_STOP_REQUESTED: {
                    lifecycleTime = System.nanoTime();
                    if (mode == Mode.LAZY && demandedByCount == 0) {
                        assert dependenciesDemanded;
                        tasks.add(new UndemandDependenciesTask());
                        dependenciesDemanded = false;
                    }
                    tasks.add(new DependencyStoppedTask());
                    break;
                }
                case STOP_REQUESTED_to_STOPPING: {
                    ChildServiceTarget childTarget = this.childTarget;
                    if (childTarget != null) {
                        childTarget.valid = false;
                        this.childTarget = null;
                    }
                    tasks.add(new StopTask());
                    tasks.add(new RemoveChildrenTask());
                    break;
                }
                case STOPPING_to_DOWN: {
                    getListenerTasks(LifecycleEvent.DOWN, listenerTransitionTasks);
                    tasks.add(new DependencyUnavailableTask());
                    tasks.add(new DependentStoppedTask());
                    break;
                }
                case DOWN_to_REMOVING: {
                    tasks.add(new RemoveTask());
                    break;
                }
                case REMOVING_to_REMOVED: {
                    getListenerTasks(LifecycleEvent.REMOVED, listenerTransitionTasks);
                    lifecycleListeners.clear();
                    break;
                }
                case START_REQUESTED_to_DOWN: {
                    tasks.add(new DependencyUnavailableTask());
                    tasks.add(new DependentStoppedTask());
                    break;
                }
                case STOP_REQUESTED_to_UP: {
                    tasks.add(new DependencyStartedTask());
                    break;
                }
                case STARTING_to_START_FAILED: {
                    getListenerTasks(LifecycleEvent.FAILED, listenerTransitionTasks);
                    container.addFailed(this);
                    for (StabilityMonitor monitor : monitors) {
                        monitor.addFailed(this);
                    }
                    ChildServiceTarget childTarget = this.childTarget;
                    if (childTarget != null) {
                        childTarget.valid = false;
                        this.childTarget = null;
                    }
                    tasks.add(new DependencyFailedTask());
                    tasks.add(new RemoveChildrenTask());
                    break;
                }
                case START_FAILED_to_DOWN: {
                    getListenerTasks(LifecycleEvent.DOWN, listenerTransitionTasks);
                    container.removeFailed(this);
                    for (StabilityMonitor monitor : monitors) {
                        monitor.removeFailed(this);
                    }
                    startException = null;
                    tasks.add(new DependencyUnavailableTask());
                    tasks.add(new DependencyRetryingTask());
                    tasks.add(new DependentStoppedTask());
                    break;
                }
                case START_FAILED_to_STARTING: {
                    container.removeFailed(this);
                    for (StabilityMonitor monitor : monitors) {
                        monitor.removeFailed(this);
                    }
                    tasks.add(new DependencyRetryingTask());
                    tasks.add(new StartTask());
                    break;
                }
                default: {
                    throw new IllegalStateException();
                }
            }
            state = transition.getAfter();
        } while (tasks.isEmpty() && listenerTransitionTasks.isEmpty());
        // Notify waiters that a transition occurred
        notifyAll();
        if (tasks.size() > 0) {
            // Postponing listener transition tasks
        } else {
            postTransitionTasks(tasks);
        }
        return tasks;
    }

    private void getListenerTasks(final LifecycleEvent event, final List<Runnable> tasks) {
        for (LifecycleListener listener : lifecycleListeners) {
            tasks.add(new LifecycleListenerTask(listener, event));
        }
    }

    void doExecute(final List<Runnable> tasks) {
        assert !holdsLock(this);
        if (tasks.isEmpty()) return;
        final Executor executor = container.getExecutor();
        for (Runnable task : tasks) {
            try {
                executor.execute(task);
            } catch (RejectedExecutionException e) {
                task.run();
            }
        }
    }

    public void setMode(final ServiceController.Mode newMode) {
        internalSetMode(null, newMode);
    }

    private boolean internalSetMode(final ServiceController.Mode expectedMode, final ServiceController.Mode newMode) {
        assert !holdsLock(this);
        if (newMode == null) {
            throw new IllegalArgumentException("newMode is null");
        }
        if (newMode != Mode.REMOVE && container.isShutdown()) {
            throw new IllegalArgumentException("Container is shutting down");
        }
        final List<Runnable> tasks;
        synchronized (this) {
            final boolean leavingRestState = isStableRestState();
            final Mode oldMode = mode;
            if (expectedMode != null && expectedMode != oldMode) {
                return false;
            }
            if (oldMode == newMode) {
                return true;
            }
            internalSetMode(newMode);
            tasks = transition();
            addAsyncTasks(tasks.size());
            updateStabilityState(leavingRestState);
        }
        doExecute(tasks);
        return true;
    }

    private void internalSetMode(final Mode newMode) {
        assert holdsLock(this);
        final ServiceController.Mode oldMode = mode;
        if (oldMode == Mode.REMOVE) {
            if (state.compareTo(Substate.REMOVING) >= 0) {
                throw new IllegalStateException("Service already removed");
            }
        }
        mode = newMode;
    }

    @Override
    public void dependencyAvailable() {
        final List<Runnable> tasks;
        synchronized (this) {
            final boolean leavingRestState = isStableRestState();
            assert unavailableDependencies > 0;
            --unavailableDependencies;
            if (ignoreNotification() || unavailableDependencies != 0) return;
            // we dropped it to 0
            tasks = transition();
            addAsyncTasks(tasks.size());
            updateStabilityState(leavingRestState);
        }
        doExecute(tasks);
    }

    @Override
    public void dependencyUnavailable() {
        final List<Runnable> tasks;
        synchronized (this) {
            final boolean leavingRestState = isStableRestState();
            ++unavailableDependencies;
            if (ignoreNotification() || unavailableDependencies != 1) return;
            // we raised it to 1
            tasks = transition();
            addAsyncTasks(tasks.size());
            updateStabilityState(leavingRestState);
        }
        doExecute(tasks);
    }

    /** {@inheritDoc} */
    public ServiceControllerImpl<?> getDependentController() {
        return this;
    }

    @Override
    public void dependencyUp() {
        final List<Runnable> tasks;
        synchronized (this) {
            final boolean leavingRestState = isStableRestState();
            assert stoppingDependencies > 0;
            --stoppingDependencies;
            if (ignoreNotification() || stoppingDependencies != 0) return;
            // we dropped it to 0
            tasks = transition();
            addAsyncTasks(tasks.size());
            updateStabilityState(leavingRestState);
        }
        doExecute(tasks);
    }

    @Override
    public void dependencyDown() {
        final List<Runnable> tasks;
        synchronized (this) {
            final boolean leavingRestState = isStableRestState();
            ++stoppingDependencies;
            if (ignoreNotification() || stoppingDependencies != 1) return;
            // we raised it to 1
            tasks = transition();
            addAsyncTasks(tasks.size());
            updateStabilityState(leavingRestState);
        }
        doExecute(tasks);
    }

    @Override
    public void dependencyFailed() {
        final List<Runnable> tasks;
        synchronized (this) {
            final boolean leavingRestState = isStableRestState();
            ++failCount;
            if (ignoreNotification() || failCount != 1) return;
            // we raised it to 1
            tasks = transition();
            addAsyncTasks(tasks.size());
            updateStabilityState(leavingRestState);
        }
        doExecute(tasks);
    }

    @Override
    public void dependencySucceeded() {
        final List<Runnable> tasks;
        synchronized (this) {
            final boolean leavingRestState = isStableRestState();
            assert failCount > 0;
            --failCount;
            if (ignoreNotification() || failCount != 0) return;
            // we dropped it to 0
            tasks = transition();
            addAsyncTasks(tasks.size());
            updateStabilityState(leavingRestState);
        }
        doExecute(tasks);
    }

    void dependentStarted() {
        dependentsStarted(1);
    }

    void dependentsStarted(final int count) {
        assert !holdsLock(this);
        synchronized (this) {
            runningDependents += count;
        }
    }

    void dependentStopped() {
        assert !holdsLock(this);
        final List<Runnable> tasks;
        synchronized (this) {
            final boolean leavingRestState = isStableRestState();
            assert runningDependents > 0;
            --runningDependents;
            if (ignoreNotification() || runningDependents != 0) return;
            // we dropped it to 0
            tasks = transition();
            addAsyncTasks(tasks.size());
            updateStabilityState(leavingRestState);
        }
        doExecute(tasks);
    }

    void newDependent(final Dependent dependent) {
        assert holdsLock(this);
        if (isFailed()) dependent.dependencyFailed();
        if (isUnavailable()) dependent.dependencyUnavailable();
        if (isUp()) dependent.dependencyUp();
    }

    private boolean isFailed() {
        assert holdsLock(this);
        if (state == Substate.START_FAILED && finishedTask(DEPENDENCY_FAILED_TASK)) return true;
        if (state == Substate.STARTING && unfinishedTask(DEPENDENCY_RETRYING_TASK)) return true;
        if (state == Substate.DOWN && unfinishedTask(DEPENDENCY_RETRYING_TASK)) return true;
        return false;
    }

    private boolean isUnavailable() {
        assert holdsLock(this);
        if (state == Substate.NEW || state == Substate.PROBLEM || state == Substate.REMOVING || state == Substate.REMOVED) return true;
        if (state == Substate.DOWN && finishedTask(DEPENDENCY_UNAVAILABLE_TASK)) return true;
        if (state == Substate.START_REQUESTED && unfinishedTask(DEPENDENCY_AVAILABLE_TASK)) return true;
        return false;
    }

    private boolean isUp() {
        assert holdsLock(this);
        if (state == Substate.UP && finishedTask(DEPENDENCY_STARTED_TASK)) return true;
        if (state == Substate.STOP_REQUESTED && unfinishedTask(DEPENDENCY_STOPPED_TASK)) return true;
        return false;
    }

    private boolean unfinishedTask(final int taskFlag) {
        assert holdsLock(this);
        final boolean taskScheduled = (execFlags & (taskFlag << 16)) != 0;
        final boolean taskRunning = (execFlags & taskFlag) == 0;
        return taskScheduled && taskRunning;
    }

    private boolean finishedTask(final int taskFlag) {
        assert holdsLock(this);
        final boolean taskUnscheduled = (execFlags & (taskFlag << 16)) == 0;
        final boolean taskFinished = (execFlags & taskFlag) != 0;
        return taskUnscheduled || taskFinished;
    }

    void addDemand() {
        addDemands(1);
    }

    void addDemands(final int demandedByCount) {
        assert !holdsLock(this);
        final boolean propagate;
        final List<Runnable> tasks;
        synchronized (this) {
            final boolean leavingRestState = isStableRestState();
            final int cnt = this.demandedByCount;
            this.demandedByCount += demandedByCount;
            if (ignoreNotification()) return;
            boolean notStartedLazy = mode == Mode.LAZY && state != Substate.UP;
            propagate = cnt == 0 && (mode == Mode.ON_DEMAND || notStartedLazy || mode == Mode.PASSIVE);
            if (!propagate) return;
            tasks = transition();
            addAsyncTasks(tasks.size());
            updateStabilityState(leavingRestState);
        }
        doExecute(tasks);
    }

    void removeDemand() {
        assert !holdsLock(this);
        final boolean propagate;
        final List<Runnable> tasks;
        synchronized (this) {
            final boolean leavingRestState = isStableRestState();
            assert demandedByCount > 0;
            final int cnt = --demandedByCount;
            if (ignoreNotification()) return;
            boolean notStartedLazy = mode == Mode.LAZY && state != Substate.UP;
            propagate = cnt == 0 && (mode == Mode.ON_DEMAND || notStartedLazy || mode == Mode.PASSIVE);
            if (!propagate) return;
            tasks = transition();
            addAsyncTasks(tasks.size());
            updateStabilityState(leavingRestState);
        }
        doExecute(tasks);
    }

    void addChild(final ServiceControllerImpl<?> child) {
        assert !holdsLock(this);
        synchronized (this) {
            if (state.getState() != State.STARTING && state.getState() != State.UP) {
                throw new IllegalStateException("Children cannot be added in state " + state.getState());
            }
            children.add(child);
            newDependent(child);
        }
    }

    void removeChild(final ServiceControllerImpl<?> child) {
        assert !holdsLock(this);
        final List<Runnable> tasks;
        synchronized (this) {
            final boolean leavingRestState = isStableRestState();
            if (!children.remove(child)) return; // may happen if child installation process failed
            if (ignoreNotification() || children.size() > 0) return;
            // we dropped it to 0
            tasks = transition();
            addAsyncTasks(tasks.size());
            updateStabilityState(leavingRestState);
        }
        doExecute(tasks);
    }

    Set<ServiceControllerImpl<?>> getChildren() {
        assert holdsLock(this);
        return children;
    }

    public ServiceControllerImpl<?> getParent() {
        return parent;
    }

    public ServiceContainerImpl getServiceContainer() {
        return container;
    }

    public ServiceController.State getState() {
        synchronized (this) {
            return state.getState();
        }
    }

    public Service<S> getService() throws IllegalStateException {
        if (!(service instanceof Service)) {
            throw new UnsupportedOperationException();
        }
        return (Service<S>) service;
    }

    public ServiceName getName() {
        return serviceId;
    }

    public Set<ServiceName> requires() {
        return requiredValues;
    }

    public Set<ServiceName> provides() {
        return providedValues;
    }

    public Set<ServiceName> missing() {
        return getUnavailableDependencies();
    }

    void addListener(final ContainerShutdownListener listener) {
        assert !holdsLock(this);
        synchronized (this) {
            if (state == Substate.REMOVED && asyncTasks == 0) {
                return; // controller is dead
            }
            if (shutdownListener != null) {
                return; // register listener only once
            }
            shutdownListener = listener;
            shutdownListener.controllerAlive();
        }
    }

    public void addListener(final LifecycleListener listener) {
        if (listener == null) return;
        final List<Runnable> tasks;
        synchronized (this) {
            final boolean leavingRestState = isStableRestState();
            if (lifecycleListeners.contains(listener)) return;
            lifecycleListeners.add(listener);
            if (state == Substate.NEW) {
                return;
            } else if (state == Substate.UP) {
                listenerTransitionTasks.add(new LifecycleListenerTask(listener, LifecycleEvent.UP));
            } else if (state == Substate.DOWN) {
                listenerTransitionTasks.add(new LifecycleListenerTask(listener, LifecycleEvent.DOWN));
            } else if (state == Substate.START_FAILED) {
                listenerTransitionTasks.add(new LifecycleListenerTask(listener, LifecycleEvent.FAILED));
            } else if (state == Substate.REMOVED) {
                listenerTransitionTasks.add(new LifecycleListenerTask(listener, LifecycleEvent.REMOVED));
            }
            tasks = transition();
            addAsyncTasks(tasks.size());
            updateStabilityState(leavingRestState);
        }
        doExecute(tasks);
    }

    public void removeListener(final LifecycleListener listener) {
        synchronized (this) {
            lifecycleListeners.remove(listener);
        }
    }

    @Override
    public StartException getStartException() {
        synchronized (this) {
            return startException;
        }
    }

    @Override
    public void retry() {
        assert !holdsLock(this);
        final List<Runnable> tasks;
        synchronized (this) {
            final boolean leavingRestState = isStableRestState();
            if (failCount > 0 || state.getState() != ServiceController.State.START_FAILED) return;
            startException = null;
            tasks = transition();
            addAsyncTasks(tasks.size());
            updateStabilityState(leavingRestState);
        }
        doExecute(tasks);
    }

    @Override
    public Set<ServiceName> getUnavailableDependencies() {
        final Set<ServiceName> retVal = new IdentityHashSet<>();
        for (Dependency dependency : requires) {
            synchronized (dependency.getLock()) {
                if (isUnavailable(dependency)) {
                    retVal.add(dependency.getName());
                }
            }
        }
        return Collections.unmodifiableSet(retVal);
    }

    private static boolean isUnavailable(final Dependency dependency) {
        final ServiceControllerImpl controller = dependency.getDependencyController();
        if (controller == null) return true;
        synchronized (controller) {
            return controller.isUnavailable();
        }
    }

    public ServiceController.Mode getMode() {
        synchronized (this) {
            return mode;
        }
    }

    public boolean compareAndSetMode(final Mode expectedMode, final Mode newMode) {
        if (expectedMode == null) {
            throw new IllegalArgumentException("expectedMode is null");
        }
        return internalSetMode(expectedMode, newMode);
    }

    void addMonitor(final StabilityMonitor monitor) {
        assert !holdsLock(this);
        synchronized (this) {
            if (!monitors.add(monitor)) return;
            if (!isStableRestState()) {
                monitor.incrementUnstableServices();
            }
            if (state == Substate.START_FAILED) {
                monitor.addFailed(this);
            } else if (state == Substate.PROBLEM) {
                monitor.addProblem(this);
            }
        }
    }

    void removeMonitor(final StabilityMonitor monitor) {
        assert !holdsLock(this);
        synchronized (this) {
            if (!monitors.remove(monitor)) return;
            if (!isStableRestState()) {
                monitor.decrementUnstableServices();
            }
            monitor.removeProblem(this);
            monitor.removeFailed(this);
        }
    }

    void removeMonitorNoCallback(final StabilityMonitor monitor) {
        assert !holdsLock(this);
        synchronized (this) {
            monitors.remove(monitor);
        }
    }

    Set<StabilityMonitor> getMonitors() {
        assert holdsLock(this);
        return monitors;
    }

    private Substate getSubstate() {
        synchronized (this) {
            return state;
        }
    }

    Collection<ServiceRegistrationImpl> getRegistrations() {
        return provides.keySet();
    }

    private void checkProvidedValues() {
        WritableValueImpl injector;
        for (Entry<ServiceRegistrationImpl, WritableValueImpl> entry : provides.entrySet()) {
            injector = entry.getValue();
            if (injector != null && injector.value == null) {
                throw new IllegalStateException("Injector for " + entry.getKey().getName() + " was not initialized");
            }
        }
    }

    private void uninjectProvides(final Collection<WritableValueImpl> injectors) {
        for (WritableValueImpl injector : injectors) {
            if (injector != null) injector.uninject();
        }
    }

    @Override
    public String toString() {
        return String.format("Controller for %s@%x", getName(), Integer.valueOf(hashCode()));
    }

    private abstract class ControllerTask implements Runnable {
        private ControllerTask() {
            assert holdsLock(ServiceControllerImpl.this);
        }

        public final void run() {
            assert !holdsLock(ServiceControllerImpl.this);
            try {
                beforeExecute();
                if (!execute()) return;
                final List<Runnable> tasks;
                synchronized (ServiceControllerImpl.this) {
                    final boolean leavingRestState = isStableRestState();
                    // Subtract one for this task
                    decrementAsyncTasks();
                    tasks = transition();
                    addAsyncTasks(tasks.size());
                    updateStabilityState(leavingRestState);
                }
                doExecute(tasks);
            } catch (Throwable t) {
                ServiceLogger.SERVICE.internalServiceError(t, getName());
            } finally {
                afterExecute();
            }
        }

        void afterExecute() {}
        void beforeExecute() {}
        abstract boolean execute();
    }

    private abstract class DependenciesControllerTask extends ControllerTask {
        final boolean execute() {
            Lockable lock;
            for (Dependency dependency : requires) {
                lock = dependency.getLock();
                synchronized (lock) {
                    lock.acquireWrite();
                    try {
                        inform(dependency);
                    } finally {
                        lock.releaseWrite();
                    }
                }
            }
            if (parent != null) inform(parent);
            return true;
        }

        abstract void inform(Dependency dependency);
        abstract void inform(ServiceControllerImpl parent);
    }

    private abstract class DependentsControllerTask extends ControllerTask {
        private final int execFlag;

        private DependentsControllerTask(final int execFlag) {
            this.execFlag = execFlag;
            execFlags |= (execFlag << 16);
        }

        final boolean execute() {
            for (ServiceRegistrationImpl registration : provides.keySet()) {
                for (Dependent dependent : registration.getDependents()) {
                    inform(dependent);
                }
            }
            synchronized (ServiceControllerImpl.this) {
                for (Dependent child : children) {
                    inform(child);
                }
                execFlags |= execFlag;
            }
            return true;
        }

        void inform(final Dependent dependent) {}

        void beforeExecute() {
            Lockable lock;
            for (ServiceRegistrationImpl registration : provides.keySet()) {
                lock = registration.getLock();
                synchronized (lock) { lock.acquireRead(); }
            }
        }

        void afterExecute() {
            Lockable lock;
            for (ServiceRegistrationImpl registration : provides.keySet()) {
                lock = registration.getLock();
                synchronized (lock) { lock.releaseRead(); }
            }
        }
    }

    private final class DemandDependenciesTask extends DependenciesControllerTask {
        void inform(final Dependency dependency) { dependency.addDemand(); }
        void inform(final ServiceControllerImpl parent) { parent.addDemand(); }
    }

    private final class UndemandDependenciesTask extends DependenciesControllerTask {
        void inform(final Dependency dependency) { dependency.removeDemand(); }
        void inform(final ServiceControllerImpl parent) { parent.removeDemand(); }
    }

    private final class DependentStartedTask extends DependenciesControllerTask {
        void inform(final Dependency dependency) { dependency.dependentStarted(); }
        void inform(final ServiceControllerImpl parent) { parent.dependentStarted(); }
    }

    private final class DependentStoppedTask extends DependenciesControllerTask {
        void inform(final Dependency dependency) { dependency.dependentStopped(); }
        void inform(final ServiceControllerImpl parent) { parent.dependentStopped(); }
    }

    private final class DependencyAvailableTask extends DependentsControllerTask {
        DependencyAvailableTask() { super(DEPENDENCY_AVAILABLE_TASK); }
        void inform(final Dependent dependent) { dependent.dependencyAvailable(); }
    }

    private final class DependencyUnavailableTask extends DependentsControllerTask {
        DependencyUnavailableTask() { super(DEPENDENCY_UNAVAILABLE_TASK); }
        void inform(final Dependent dependent) { dependent.dependencyUnavailable(); }
    }

    private final class DependencyStartedTask extends DependentsControllerTask {
        private DependencyStartedTask() { super(DEPENDENCY_STARTED_TASK); }
        void inform(final Dependent dependent) { dependent.dependencyUp(); }
    }

    private final class DependencyStoppedTask extends DependentsControllerTask {
        private DependencyStoppedTask() { super(DEPENDENCY_STOPPED_TASK); }
        void inform(final Dependent dependent) { dependent.dependencyDown(); }
    }

    private final class DependencyFailedTask extends DependentsControllerTask {
        private DependencyFailedTask() { super(DEPENDENCY_FAILED_TASK); }
        void inform(final Dependent dependent) { dependent.dependencyFailed(); }
    }

    private final class DependencyRetryingTask extends DependentsControllerTask {
        private DependencyRetryingTask() { super(DEPENDENCY_RETRYING_TASK); }
        void inform(final Dependent dependent) { dependent.dependencySucceeded(); }
    }

    private final class StartTask extends ControllerTask {
        boolean execute() {
            final StartContextImpl context = new StartContextImpl();
            try {
                startService(service, context);
                boolean startFailed;
                synchronized (context.lock) {
                    context.state |= AbstractContext.CLOSED;
                    if ((context.state & AbstractContext.ASYNC) != 0) {
                        // asynchronous() was called
                        if ((context.state & (AbstractContext.COMPLETED | AbstractContext.FAILED)) == 0) {
                            // Neither complete() nor failed() have been called yet
                            return false;
                        }
                    } else {
                        // asynchronous() was not called
                        if ((context.state & (AbstractContext.COMPLETED | AbstractContext.FAILED)) == 0) {
                            // Neither complete() nor failed() have been called yet
                            context.state |= AbstractContext.COMPLETED;
                        }
                    }
                    startFailed = (context.state & AbstractContext.FAILED) != 0;
                }
                if (startFailed) {
                    uninjectProvides(provides.values());
                } else {
                    checkProvidedValues();
                }
            } catch (StartException e) {
                e.setServiceName(getName());
                startFailed(e, context);
            } catch (Throwable t) {
                startFailed(new StartException("Failed to start service", t, getName()), context);
            }
            return true;
        }

        private void startService(org.jboss.msc.Service service, StartContext context) throws StartException {
            final ClassLoader contextClassLoader = setTCCL(getCL(service.getClass()));
            try {
                service.start(context);
            } finally {
                setTCCL(contextClassLoader);
            }
        }
    }

    private void startFailed(final StartException e, final StartContextImpl context) {
        ServiceLogger.FAIL.startFailed(e, getName());
        synchronized (context.lock) {
            context.state |= (AbstractContext.FAILED | AbstractContext.CLOSED);
            synchronized (ServiceControllerImpl.this) {
                startException = e;
            }
        }
        uninjectProvides(provides.values());
    }

    private final class StopTask extends ControllerTask {
        boolean execute() {
            final StopContextImpl context = new StopContextImpl();
            boolean ok = false;
            try {
                stopService(service, context);
                ok = true;
            } catch (Throwable t) {
                ServiceLogger.FAIL.stopFailed(t, getName());
            } finally {
                synchronized (context.lock) {
                    context.state |= AbstractContext.CLOSED;
                    if (ok & (context.state & AbstractContext.ASYNC) != 0) {
                        // no exception thrown and asynchronous() was called
                        if ((context.state & AbstractContext.COMPLETED) == 0) {
                            // complete() have not been called yet
                            return false;
                        }
                    } else {
                        // exception thrown or asynchronous() was not called
                        if ((context.state & AbstractContext.COMPLETED) == 0) {
                            // complete() have not been called yet
                            context.state |= AbstractContext.COMPLETED;
                        }
                    }
                }
                uninjectProvides(provides.values());
            }
            return true;
        }

        private void stopService(org.jboss.msc.Service service, StopContext context) {
            final ClassLoader contextClassLoader = setTCCL(getCL(service.getClass()));
            try {
                service.stop(context);
            } finally {
                setTCCL(contextClassLoader);
            }
        }
    }

    private final class LifecycleListenerTask extends ControllerTask {
        private final LifecycleListener listener;
        private final LifecycleEvent event;

        LifecycleListenerTask(final LifecycleListener listener, final LifecycleEvent event) {
            this.listener = listener;
            this.event = event;
        }

        boolean execute() {
            final ClassLoader oldCL = setTCCL(getCL(listener.getClass()));
            try {
                listener.handleEvent(ServiceControllerImpl.this, event);
            } catch (Throwable t) {
                ServiceLogger.SERVICE.listenerFailed(t, listener);
            } finally {
                setTCCL(oldCL);
            }
            return true;
        }
    }

    private final class RemoveChildrenTask extends ControllerTask {
        boolean execute() {
            synchronized (ServiceControllerImpl.this) {
                for (ServiceControllerImpl<?> child : children) child.setMode(Mode.REMOVE);
            }
            return true;
        }
    }

    private final class RemoveTask extends ControllerTask {
        boolean execute() {
            assert getMode() == ServiceController.Mode.REMOVE;
            assert getSubstate() == Substate.REMOVING;
            ServiceRegistrationImpl registration;
            WritableValueImpl injector;
            Lockable lock;
            boolean removeRegistration;
            for (Entry<ServiceRegistrationImpl, WritableValueImpl> provided : provides.entrySet()) {
                registration = provided.getKey();
                injector = provided.getValue();
                lock = registration.getLock();
                synchronized (lock) {
                    lock.acquireWrite();
                    try {
                        if (injector != null) {
                            injector.setInstance(null);
                        }
                        removeRegistration = registration.clear(ServiceControllerImpl.this);
                        if (removeRegistration) {
                            container.removeRegistration(registration.getName());
                        }
                    } finally {
                        lock.releaseWrite();
                    }
                }
            }
            for (Dependency dependency : requires) {
                lock = dependency.getLock();
                synchronized (lock) {
                    lock.acquireWrite();
                    try {
                        removeRegistration = dependency.removeDependent(ServiceControllerImpl.this);
                        if (removeRegistration) {
                            container.removeRegistration(dependency.getName());
                        }
                    } finally {
                        lock.releaseWrite();
                    }
                }
            }
            if (parent != null) parent.removeChild(ServiceControllerImpl.this);
            return true;
        }
    }

    private abstract class AbstractContext implements LifecycleContext {
        static final int ASYNC = 1;
        static final int CLOSED = 1 << 1;
        static final int COMPLETED = 1 << 2;
        static final int FAILED = 1 << 3;

        int state;
        final Object lock = new Object();

        abstract void onComplete();

        final int setState(final int newState) {
            synchronized (lock) {
                if (((newState & ASYNC) != 0 && ((state & ASYNC) != 0 || (state & CLOSED) != 0)) ||
                    ((newState & (COMPLETED | FAILED)) != 0 && (state & (COMPLETED | FAILED)) != 0) ||
                    ((newState & (COMPLETED | FAILED)) != 0 && (state & CLOSED) != 0 && (state & ASYNC) == 0)) {
                    throw new IllegalStateException(ILLEGAL_CONTROLLER_STATE);
                }
                return state |= newState;
            }
        }

        final void taskCompleted() {
            final List<Runnable> tasks;
            synchronized (ServiceControllerImpl.this) {
                final boolean leavingRestState = isStableRestState();
                // Subtract one for this task
                decrementAsyncTasks();
                tasks = transition();
                addAsyncTasks(tasks.size());
                updateStabilityState(leavingRestState);
            }
            doExecute(tasks);
        }

        public final void complete() {
            final int state = setState(COMPLETED);
            if ((state & CLOSED) != 0) {
                onComplete();
                taskCompleted();
            }
        }

        public final void asynchronous() {
            setState(ASYNC);
        }

        public final long getElapsedTime() {
            return System.nanoTime() - lifecycleTime;
        }

        public final ServiceController<?> getController() {
            return ServiceControllerImpl.this;
        }

        public final void execute(final Runnable command) {
            if (command == null) return;
            synchronized (lock) {
                if ((state & (COMPLETED | FAILED)) != 0) {
                    throw new IllegalStateException("Lifecycle context is no longer valid");
                }
                doExecute(Collections.singletonList(new Runnable() {
                    public void run() {
                        final ClassLoader contextClassLoader = setTCCL(getCL(command.getClass()));
                        try {
                            command.run();
                        } finally {
                            setTCCL(contextClassLoader);
                        }
                    }
                }));
            }
        }
    }

    private final class StartContextImpl extends AbstractContext implements StartContext {
        public void failed(StartException reason) throws IllegalStateException {
            if (reason == null) {
                reason = new StartException("Start failed, and additionally, a null cause was supplied");
            }
            final ServiceName serviceName = getName();
            reason.setServiceName(serviceName);
            ServiceLogger.FAIL.startFailed(reason, serviceName);
            final int state;
            synchronized (lock) {
                state = setState(FAILED);
                synchronized (ServiceControllerImpl.this) {
                    startException = reason;
                }
            }
            if ((state & CLOSED) != 0) {
                uninjectProvides(provides.values());
                taskCompleted();
            }
        }

        public ServiceTarget getChildTarget() {
            synchronized (lock) {
                if ((state & (COMPLETED | FAILED)) != 0) {
                    throw new IllegalStateException("Lifecycle context is no longer valid");
                }
                synchronized (ServiceControllerImpl.this) {
                    if (childTarget == null) {
                        childTarget = new ChildServiceTarget(container);
                    }
                    return childTarget;
                }
            }
        }

        void onComplete() {
            try {
                checkProvidedValues();
            } catch (Throwable t) {
                startFailed(new StartException("Failed to start service", t, getName()), this);
            }
        }
    }

    private final class StopContextImpl extends AbstractContext implements StopContext {
        void onComplete() {
            uninjectProvides(provides.values());
        }
    }

    private final class ChildServiceTarget extends ServiceTargetImpl {
        private volatile boolean valid = true;

        private ChildServiceTarget(final ServiceTargetImpl parentTarget) {
            super(parentTarget);
        }

        <T> ServiceController<T> install(final ServiceBuilderImpl<T> serviceBuilder) throws ServiceRegistryException {
            if (! valid) {
                throw new IllegalStateException("Service target is no longer valid");
            }
            return super.install(serviceBuilder);
        }

        @Override
        public ServiceTarget subTarget() {
            return new ChildServiceTarget(this);
        }
    }

    private void addAsyncTasks(final int size) {
        assert holdsLock(this);
        assert size >= 0;
        if (size > 0) asyncTasks += size;
    }

    private void incrementAsyncTasks() {
        assert holdsLock(this);
        asyncTasks++;
    }

    private void decrementAsyncTasks() {
        assert holdsLock(this);
        assert asyncTasks > 0;
        asyncTasks--;
    }

}
