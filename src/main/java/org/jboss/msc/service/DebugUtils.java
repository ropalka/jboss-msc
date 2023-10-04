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

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicLong;

public final class DebugUtils {

    private DebugUtils() {
        // forbidden instantiation
    }

    private static volatile PrintStream logFile;
    private static final String DEBUG_FILE = "/home/opalka/msc.debug" + System.currentTimeMillis();
    private static final AtomicLong logCounter = new AtomicLong();

    static {
        try {
            logFile = new PrintStream(DEBUG_FILE);
        } catch (Exception ignored) {
            System.err.println("Couldn't create debug file: " + DEBUG_FILE);
        }
    }

    public static void debug(final String msg) {
        final long msgId = logCounter.incrementAndGet();
        logFile.println("[msg-id-" + msgId + "][" + Thread.currentThread().getName() + "] " + msg);
        logFile.flush();
    }

    public static void debug(final Throwable e, final String msg) {
        final long msgId = logCounter.incrementAndGet();
        logFile.println("[msg-id-" + msgId + "][" + Thread.currentThread().getName() + "] " + msg);
        e.printStackTrace(logFile);
        logFile.flush();
    }

}
