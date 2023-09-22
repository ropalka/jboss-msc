package org.jboss.msc.service;

import java.util.concurrent.*;

final class JDKSpecific {
    static ExecutorService getExecutorService(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final ThreadFactory threadFactory, final ServiceContainerImpl container) {
        System.out.println("JDK21 executor");
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
