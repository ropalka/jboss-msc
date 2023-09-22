package org.jboss.msc.service;

import org.jboss.threads.EnhancedQueueExecutor;

import java.util.concurrent.*;

final class JDKSpecific {
    static ExecutorService getExecutorService(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final ThreadFactory threadFactory, final ServiceContainerImpl container) {
        System.out.println("JDK11 executor");
        if (EnhancedQueueExecutor.DISABLE_HINT) {
            return new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<>(), threadFactory, container.POLICY) {
                protected void afterExecute(final Runnable r, final Throwable t) {
                    super.afterExecute(r, t);
                    if (t != null) {
                        container.HANDLER.uncaughtException(Thread.currentThread(), t);
                    }
                }

                protected void terminated() {
                    container.shutdownComplete(container.shutdownInitiated);
                }
            };
        } else {
            return new EnhancedQueueExecutor.Builder()
                    .setCorePoolSize(corePoolSize)
                    .setMaximumPoolSize(maximumPoolSize)
                    .setKeepAliveTime(keepAliveTime, unit)
                    .setTerminationTask(new Runnable() {
                        public void run() {
                            container.shutdownComplete(container.shutdownInitiated);
                        }
                    })
                    .setThreadFactory(threadFactory)
                    .build();
        }
    }
}
