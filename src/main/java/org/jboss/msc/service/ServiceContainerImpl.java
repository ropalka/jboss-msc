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

import static java.security.AccessController.doPrivileged;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.security.PrivilegedAction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.threads.EnhancedQueueExecutor;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class ServiceContainerImpl extends ServiceTargetImpl implements ServiceContainer {

    private static final AtomicInteger SERIAL = new AtomicInteger(1);

    private final ConcurrentMap<ServiceName, ServiceRegistrationImpl> registry = new ConcurrentHashMap<>(512);
    private final long start = System.nanoTime();

    private final Set<ServiceController<?>> problems = new IdentityHashSet<>();
    private final Set<ServiceController<?>> failed = new IdentityHashSet<>();
    private final Object lock = new Object();

    private int unstableServices;
    private long shutdownInitiated;

    private final List<TerminateListener> terminateListeners = new ArrayList<>(1);

    private static final class ShutdownHookThread extends Thread {
        final Reference<ServiceContainer> containerRef;

        private ShutdownHookThread(final ServiceContainer container) {
            setName(container.getName() + " MSC Shutdown Thread");
            setDaemon(false);
            containerRef = new WeakReference<>(container);
        }

        @Override
        public void run() {
            final ServiceContainer container = containerRef.get();
            if (container == null) return;
            container.shutdown();
            try {
                container.awaitTermination();
            } catch (InterruptedException ie) {
                // ignored
            }
        }
    }

    private volatile TerminateListener.Info terminateInfo;
    private volatile boolean down;
    private final ContainerExecutor executor;
    private final String name;
    private final Thread shutdownThread;

    ServiceContainerImpl(String name, int coreSize, long timeOut, TimeUnit timeOutUnit, final boolean autoShutdown) {
        final int serialNo = SERIAL.getAndIncrement();
        if (name == null) {
            name = String.format("anonymous-%d", Integer.valueOf(serialNo));
        }
        this.name = name;
        executor = new ContainerExecutor(coreSize, coreSize, timeOut, timeOutUnit);
        this.shutdownThread = autoShutdown ? new ShutdownHookThread(this) : null;
    }

    void registerShutdownCleaner() {
        if (shutdownThread == null) return;
        try {
            doPrivileged(
                    new PrivilegedAction<Void>() {
                        @Override
                        public Void run() {
                            Runtime.getRuntime().addShutdownHook(shutdownThread);
                            return null;
                        }
                    });
        } catch (IllegalStateException ex) {
            // if the shutdown hook was triggered, then no services can ever come up in any new containers.
            terminateInfo = new TerminateListener.Info(System.nanoTime(), System.nanoTime());
            down = true;
        }
    }

    void removeProblem(ServiceController<?> controller) {
        synchronized (lock) {
            problems.remove(controller);
        }
    }

    void removeFailed(ServiceController<?> controller) {
        synchronized (lock) {
            failed.remove(controller);
        }
    }

    void incrementUnstableServices() {
        synchronized (lock) {
            unstableServices++;
        }
    }

    void addProblem(ServiceController<?> controller) {
        synchronized (lock) {
            problems.add(controller);
        }
    }

    void addFailed(ServiceController<?> controller) {
        synchronized (lock) {
            failed.add(controller);
        }
    }

    void decrementUnstableServices() {
        synchronized (lock) {
            if (--unstableServices == 0) {
                lock.notifyAll();
            }
            assert unstableServices >= 0; 
        }
    }

    public String getName() {
        return name;
    }

    long getStart() {
        return start;
    }

    @Override
    public void addTerminateListener(final TerminateListener listener) {
        if (terminateInfo == null) {
            synchronized (this) {
                if (terminateInfo == null) {
                    terminateListeners.add(listener);
                    return;
                }
            }
        }
        try {
            listener.handleTermination(terminateInfo);
        } catch (final Throwable t) {
            // ignored
        }
    }

    @Override
    public void awaitTermination() throws InterruptedException {
        final LatchListener listener = new LatchListener(1);
        addTerminateListener(listener);
        listener.await();
    }

    @Override
    public void awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        final LatchListener listener = new LatchListener(1);
        addTerminateListener(listener);
        listener.await(timeout, unit);
    }

    @Override
    public void awaitStability() throws InterruptedException {
        awaitStability(null, null);
    }

    @Override
    public boolean awaitStability(final long timeout, final TimeUnit unit) throws InterruptedException {
        return awaitStability(timeout, unit, null, null);
    }

    @Override
    public void awaitStability(Set<? super ServiceController<?>> failed, Set<? super ServiceController<?>> problem) throws InterruptedException {
        synchronized (lock) {
            while (unstableServices != 0) {
                lock.wait();
            }
            if (failed != null) {
                failed.addAll(this.failed);
            }
            if (problem != null) {
                problem.addAll(this.problems);
            }
        }
    }

    @Override
    public boolean awaitStability(final long timeout, final TimeUnit unit, Set<? super ServiceController<?>> failed, Set<? super ServiceController<?>> problem) throws InterruptedException {
        long now = System.nanoTime();
        long remaining = unit.toNanos(timeout);
        synchronized (lock) {
            while (unstableServices != 0) {
                if (remaining <= 0L) {
                    return false;
                }
                lock.wait(remaining / 1000000L, (int) (remaining % 1000000L));
                remaining -= (-now + (now = System.nanoTime()));
            }
            if (failed != null) {
                failed.addAll(this.failed);
            }
            if (problem != null) {
                problem.addAll(this.problems);
            }
            return true;
        }
    }

    public boolean isShutdown() {
        return down;
    }

    public void shutdown() {
        synchronized (this) {
            if (down) return;
            down = true;
            shutdownInitiated = System.nanoTime();
        }
        // unregistering shutdown hook
        if (shutdownThread != null) {
            try {
                doPrivileged(
                        new PrivilegedAction<Void>() {
                            @Override
                            public Void run() {
                                Runtime.getRuntime().removeShutdownHook(shutdownThread);
                                return null;
                            }
                        });
            } catch (IllegalStateException ignored) {
                // shutdown hook was already initiated
            }
        }
        // shutting down all services
        final ContainerShutdownListener shutdownListener = new ContainerShutdownListener(new Runnable() {
            public void run() {
                executor.shutdown();
            }
        });
        ServiceControllerImpl<?> controller;
        for (ServiceRegistrationImpl registration : registry.values()) {
            controller = registration.getDependencyController();
            if (controller != null) {
                controller.addListener(shutdownListener);
                try {
                    controller.setMode(Mode.REMOVE);
                } catch (IllegalArgumentException ignored) {
                    // controller removed in the meantime
                }
            }
        }
        shutdownListener.done();
    }

    public boolean isShutdownComplete() {
        return terminateInfo != null;
    }

    private void shutdownComplete(final long started) {
        synchronized (this) {
            terminateInfo = new TerminateListener.Info(started, System.nanoTime());
        }
        for (TerminateListener terminateListener : terminateListeners) {
            try {
                terminateListener.handleTermination(terminateInfo);
            } catch (Throwable t) {
                // ignore
            }
        }
    }

    static final class LatchListener extends CountDownLatch implements TerminateListener {

        LatchListener(int count) {
            super(count);
        }

        @Override
        public void handleTermination(Info info) {
            countDown();
        }
    }

    Executor getExecutor() {
        return executor;
    }

    /**
     * Atomically get or create a registration.
     *
     * @param name the service name
     * @return the registration
     */
    ServiceRegistrationImpl getOrCreateRegistration(final ServiceName name) {
        final ConcurrentMap<ServiceName, ServiceRegistrationImpl> registry = this.registry;
        ServiceRegistrationImpl registration;
        boolean success;
        do {
            registration = registry.get(name);
            if (registration == null) {
                registration = new ServiceRegistrationImpl(name);
                ServiceRegistrationImpl existing = registry.putIfAbsent(name, registration);
                if (existing != null) {
                    registration = existing;
                }
            }
            synchronized (registration) {
                registration.acquireWrite();
                try {
                    success = registration.addPendingInstallation();
                } finally {
                    registration.releaseWrite();
                }
            }
        } while (!success);
        return registration;
    }

    void removeRegistration(final ServiceName name) {
        registry.remove(name);
    }

    @Override
    public ServiceController<?> getRequiredService(final ServiceName serviceName) throws ServiceNotFoundException {
        final ServiceController<?> controller = getService(serviceName);
        if (controller == null) {
            throw new ServiceNotFoundException(serviceName + " not found");
        }
        return controller;
    }

    @Override
    public ServiceController<?> getService(final ServiceName serviceName) {
        final ServiceRegistrationImpl registration = registry.get(serviceName);
        return registration == null ? null : registration.getDependencyController();
    }

    @Override
    public List<ServiceName> getServiceNames() {
        final List<ServiceName> result = new ArrayList<>(registry.size());
        for (Map.Entry<ServiceName, ServiceRegistrationImpl> registryEntry: registry.entrySet()) {
            if (registryEntry.getValue().getDependencyController() != null) {
                result.add(registryEntry.getKey());
            }
        }
        return result;
    }

    @Override
    <T> ServiceController<T> install(final ServiceBuilderImpl<T> serviceBuilder) throws DuplicateServiceException {
        apply(serviceBuilder);

        // Initialize registrations and injectors map
        final Map<ServiceRegistrationImpl, WritableValueImpl> provides = new LinkedHashMap<>();
        Entry<ServiceName, WritableValueImpl> entry;
        for (Iterator<Entry<ServiceName, WritableValueImpl>> j = serviceBuilder.getProvides().entrySet().iterator(); j.hasNext(); ) {
            entry = j.next();
            provides.put(getOrCreateRegistration(entry.getKey()), entry.getValue());
        }

        // Dependencies
        final Map<ServiceName, ServiceBuilderImpl.Dependency> dependencyMap = serviceBuilder.getDependencies();
        final Set<Dependency> requires = new HashSet<>();
        final List<ValueInjection<?>> valueInjections = new ArrayList<>();
        Dependency dependency;
        for (ServiceBuilderImpl.Dependency dependencyDefinition : dependencyMap.values()) {
            dependency = dependencyDefinition.getRegistration();
            requires.add(dependency);
            for (Injector<Object> injector : dependencyDefinition.getInjectorList()) {
                valueInjections.add(new ValueInjection<>(dependency, injector));
            }
        }
        final ValueInjection<?>[] valueInjectionArray = valueInjections.toArray(new ValueInjection<?>[valueInjections.size()]);

        // Next create the actual controller
        final ServiceControllerImpl<T> instance = new ServiceControllerImpl<>(this, serviceBuilder.serviceId, serviceBuilder.getService(),
                requires, provides, valueInjectionArray,
                serviceBuilder.getMonitors(), serviceBuilder.getLifecycleListeners(), serviceBuilder.parent);
        boolean ok = false;
        try {
            synchronized (this) {
                if (down) {
                    ok = true; // do not rollback installation because we didn't install anything
                    throw new IllegalStateException ("Container is down");
                }
                // It is necessary to call startInstallation() under container intrinsic lock.
                // This is the only point in MSC code where ServiceRegistrationImpl.instance
                // field is being set to non null value. So in order for shutdown() method
                // which iterates 'registry' concurrent hash map outside of intrinsic lock
                // to see up to date installed controllers this synchronized section is necessary.
                // Otherwise it may happen container stability will be seriously broken on shutdown.
                instance.startInstallation();
            }
            instance.startConfiguration();
            // detect circularity before committing
            detectCircularity(instance);
            instance.commitInstallation(serviceBuilder.getInitialMode());
            ok = true;
            return instance;
        } finally {
            if (! ok) {
                instance.rollbackInstallation();
            }
        }
    }

    /**
     * Detects if installation of {@code instance} results in dependency cycles.
     *
     * @param instance                     the service being installed
     * @throws CircularDependencyException if a dependency cycle involving {@code instance} is detected
     */
    private <T> void detectCircularity(ServiceControllerImpl<T> instance) throws CircularDependencyException {
        if (isAggregationService(instance)) {
            // aggregation services cannot introduce a dependency cycle
            return;
        }
        final Set<ServiceControllerImpl<?>> visited = new IdentityHashSet<>();
        final Deque<ServiceControllerImpl> visitStack = new ArrayDeque<>();
        visitStack.push(instance);
        for (ServiceRegistrationImpl registration : instance.getRegistrations()) {
            synchronized (registration) {
                detectCircularity(registration.getDependents(), instance, visited, visitStack);
            }
        }
    }

    private void detectCircularity(Set<? extends Dependent> dependents, ServiceControllerImpl<?> instance, Set<ServiceControllerImpl<?>> visited,  Deque<ServiceControllerImpl> visitStack) {
        for (Dependent dependent: dependents) {
            final ServiceControllerImpl<?> controller = dependent.getDependentController();
            if (controller == instance) {
                // change cycle from dependent order to dependency order
                ServiceName[] cycle = new ServiceName[visitStack.size()];
                int i = cycle.length - 1;
                for (ServiceControllerImpl c : visitStack) {
                    cycle[i--] = c.getName() != null ? c.getName() : (ServiceName)c.provides().iterator().next();
                }
                throw new CircularDependencyException("Container " + name + " has a circular dependency: " + Arrays.asList(cycle), cycle);
            }
            if (visited.add(controller)) {
                if (isRemovedService(controller) || isAggregationService(controller)) continue;
                visitStack.push(controller);
                synchronized(controller) {
                    detectCircularity(controller.getChildren(), instance, visited, visitStack);
                }
                for (ServiceRegistrationImpl registration : controller.getRegistrations()) {
                    if (registration.getDependencyController() == null) continue; // concurrent removal
                    synchronized (registration) {
                        detectCircularity(registration.getDependents(), instance, visited, visitStack);
                    }
                }
                visitStack.poll();
            }
        }
    }

    private static boolean isAggregationService(final ServiceControllerImpl<?> controller) {
        return !(controller.service instanceof org.jboss.msc.service.Service) && controller.provides().isEmpty();
    }

    private static boolean isRemovedService(final ServiceControllerImpl<?> controller) {
        return controller.getState() == ServiceController.State.REMOVED;
    }

    private static final AtomicInteger executorSeq = new AtomicInteger(1);
    private static final Thread.UncaughtExceptionHandler HANDLER = new Thread.UncaughtExceptionHandler() {
        public void uncaughtException(final Thread t, final Throwable e) {
            ServiceLogger.ROOT.uncaughtException(e, t);
        }
    };
    private static final ThreadPoolExecutor.CallerRunsPolicy POLICY = new ThreadPoolExecutor.CallerRunsPolicy();

    static class ServiceThread extends Thread {
        private final ServiceContainerImpl container;

        ServiceThread(final Runnable runnable, final ServiceContainerImpl container) {
            super(runnable);
            this.container = container;
        }

        ServiceContainerImpl getContainer() {
            return container;
        }
    }

    final class ThreadAction implements PrivilegedAction<ServiceThread> {
        private final Runnable r;
        private final int id;
        private final AtomicInteger threadSeq;

        ThreadAction(final Runnable r, final int id, final AtomicInteger threadSeq) {
            this.r = r;
            this.id = id;
            this.threadSeq = threadSeq;
        }

        public ServiceThread run() {
            ServiceThread thread = new ServiceThread(r, ServiceContainerImpl.this);
            if (thread.isDaemon()) thread.setDaemon(false);
            if (thread.getPriority() != Thread.NORM_PRIORITY) thread.setPriority(Thread.NORM_PRIORITY);
            thread.setName(String.format("MSC service thread %d-%d", Integer.valueOf(id), Integer.valueOf(threadSeq.getAndIncrement())));
            thread.setUncaughtExceptionHandler(HANDLER);
            return thread;
        }
    }


    final class ContainerExecutor implements ExecutorService {

        private final ExecutorService delegate;

        ContainerExecutor(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit) {
            final ThreadFactory threadFactory = new ThreadFactory() {
                private final int id = executorSeq.getAndIncrement();
                private final AtomicInteger threadSeq = new AtomicInteger(1);

                public Thread newThread(final Runnable r) {
                    return doPrivileged(new ThreadAction(r, id, threadSeq));
                }
            };
            if (EnhancedQueueExecutor.DISABLE_HINT) {
                delegate = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<>(), threadFactory, POLICY) {
                    protected void afterExecute(final Runnable r, final Throwable t) {
                        super.afterExecute(r, t);
                        if (t != null) {
                            HANDLER.uncaughtException(Thread.currentThread(), t);
                        }
                    }

                    protected void terminated() {
                        shutdownComplete(shutdownInitiated);
                    }
                };
            } else {
                delegate = new EnhancedQueueExecutor.Builder()
                    .setCorePoolSize(corePoolSize)
                    .setMaximumPoolSize(maximumPoolSize)
                    .setKeepAliveTime(keepAliveTime, unit)
                    .setTerminationTask(new Runnable() {
                        public void run() {
                            shutdownComplete(shutdownInitiated);
                        }
                    })
                    .setThreadFactory(threadFactory)
                    .build();
            }
        }

        public void shutdown() {
            delegate.shutdown();
        }

        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        public <T> Future<T> submit(final Callable<T> task) {
            return delegate.submit(task);
        }

        public <T> Future<T> submit(final Runnable task, final T result) {
            return delegate.submit(task, result);
        }

        public Future<?> submit(final Runnable task) {
            return delegate.submit(task);
        }

        public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
            return delegate.invokeAll(tasks);
        }

        public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) throws InterruptedException {
            return delegate.invokeAll(tasks, timeout, unit);
        }

        public <T> T invokeAny(final Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            return delegate.invokeAny(tasks);
        }

        public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.invokeAny(tasks, timeout, unit);
        }

        public void execute(final Runnable command) {
            delegate.execute(command);
        }
    }
}
