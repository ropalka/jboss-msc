package org.jboss.msc.service;

import static java.security.AccessController.doPrivileged;

import org.jboss.threads.EnhancedQueueExecutor;

import java.security.PrivilegedAction;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

final class JDKSpecific {

    private static final AtomicInteger executorSeq = new AtomicInteger(1);
    private static final Thread.UncaughtExceptionHandler HANDLER = new Thread.UncaughtExceptionHandler() {
        public void uncaughtException(final Thread t, final Throwable e) {
            ServiceLogger.ROOT.uncaughtException(e, t);
        }
    };
    private static final ThreadPoolExecutor.CallerRunsPolicy POLICY = new ThreadPoolExecutor.CallerRunsPolicy();

    private static final class ThreadAction implements PrivilegedAction<Thread> {
        private final Runnable r;
        private final int id;
        private final AtomicInteger threadSeq;

        ThreadAction(final Runnable r, final int id, final AtomicInteger threadSeq) {
            this.r = r;
            this.id = id;
            this.threadSeq = threadSeq;
        }

        public Thread run() {
            Thread thread = new Thread(r);
            if (thread.isDaemon()) thread.setDaemon(false);
            if (thread.getPriority() != Thread.NORM_PRIORITY) thread.setPriority(Thread.NORM_PRIORITY);
            thread.setName(String.format("MSC platform thread %d-%d", Integer.valueOf(id), Integer.valueOf(threadSeq.getAndIncrement())));
            thread.setUncaughtExceptionHandler(HANDLER);
            return thread;
        }
    }

    private static ThreadFactory getThreadFactory() {
        final ThreadFactory threadFactory = new ThreadFactory() {
            private final int id = executorSeq.getAndIncrement();
            private final AtomicInteger threadSeq = new AtomicInteger(1);

            public Thread newThread(final Runnable r) {
                return doPrivileged(new ThreadAction(r, id, threadSeq));
            }
        };
        return threadFactory;
    }

    static ExecutorService getExecutorService(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final ServiceContainerImpl container) {
        System.out.println("JDK11 executor");
        final ThreadFactory threadFactory = getThreadFactory();
        if (EnhancedQueueExecutor.DISABLE_HINT) {
            return new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<>(), threadFactory, POLICY) {
                protected void afterExecute(final Runnable r, final Throwable t) {
                    super.afterExecute(r, t);
                    if (t != null) {
                        HANDLER.uncaughtException(Thread.currentThread(), t);
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
