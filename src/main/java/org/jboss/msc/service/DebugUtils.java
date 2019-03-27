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

import java.io.File;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class DebugUtils {

    private DebugUtils() {
        // forbidden instantiation
    }

    private static volatile PrintStream logFile;
    private static volatile PrintStream logFile1;
    private static volatile PrintStream logFile2;
    private static volatile PrintStream logFile3;
    private static volatile PrintStream logFile4;
    private static volatile PrintStream logFile5;
    private static final long ID = System.currentTimeMillis();
    private static final String DEBUG_DIR = "/home/opalka/.msc/";
    private static final String DEBUG_FILE = DEBUG_DIR + "/" + ID + "-msc-0.debug";
    private static final String ID_FILE = DEBUG_DIR + "/" + ID + "-msc-1.gv";
    private static final String VALUE_FILE = DEBUG_DIR + "/" + ID + "-msc-2.gv";
    private static final String PARENT_D_FILE = DEBUG_DIR + "/" + ID + "-msc-3.gv";
    private static final String VALUE_D_FILE = DEBUG_DIR + "/" + ID + "-msc-4.gv";
    private static final String DEPENDENCY_D_FILE = DEBUG_DIR + "/" + ID + "-msc-5.gv";
    private static final Lock lock = new ReentrantLock(true);

    static {
        try { logFile = new PrintStream(new File(DEBUG_FILE)); } catch (Exception ignored) { System.err.println("Couldn't create debug file: " + DEBUG_FILE); }
        try { logFile1 = new PrintStream(new File(ID_FILE)); } catch (Exception ignored) { System.err.println("Couldn't create debug file: " + ID_FILE); }
        try { logFile2 = new PrintStream(new File(VALUE_FILE)); } catch (Exception ignored) { System.err.println("Couldn't create debug file: " + VALUE_FILE); }
        try { logFile3 = new PrintStream(new File(PARENT_D_FILE)); } catch (Exception ignored) { System.err.println("Couldn't create debug file: " + PARENT_D_FILE); }
        try { logFile4 = new PrintStream(new File(VALUE_D_FILE)); } catch (Exception ignored) { System.err.println("Couldn't create debug file: " + VALUE_D_FILE); }
        try { logFile5 = new PrintStream(new File(DEPENDENCY_D_FILE)); } catch (Exception ignored) { System.err.println("Couldn't create debug file: " + DEPENDENCY_D_FILE); }
    }

    public static void debug(final String msg) {
        lock.lock();
        try {
            logFile.println("[" + Thread.currentThread().getName() + "] " + msg);
            logFile.flush();
        } finally {
            lock.unlock();
        }
    }

    public static void debug(final Throwable e, final String msg) {
        lock.lock();
        try {
            logFile.println("[" + Thread.currentThread().getName() + "] " + msg);
            e.printStackTrace(logFile);
            logFile.flush();
        } finally {
            lock.unlock();
        }
    }

    static void debug(final ServiceName id, final Set<ServiceRegistrationImpl> provides, final ServiceControllerImpl parent, final Set<Dependency> requires) {
        lock.lock();
        try {
            // CREATE (A:id {name:"A"})
            logFile1.println("CREATE (`" + hc(id) + "`:id { name:\"" + escape(id) + "\"})");
            ServiceName valueName = null;
            for (Iterator<ServiceRegistrationImpl> i = provides.iterator(); i.hasNext();) {
                valueName = i.next().getName();
                if (valueName.equals(id)) continue;
                // CREATE (B:value {name:"B"})
                logFile2.println("CREATE (`" + hc(valueName) + "`:value {name:\"" + escape(valueName) + "\"})");
                // CREATE (A)-[:v]->(B)
                logFile4.println("CREATE (`" + hc(id) + "`)-[:v]->(`" + hc(valueName) + "`)");
            }
            if (parent != null) {
                // CREATE (A)-[:p]->(P)
                logFile3.println("CREATE (`" + hc(id) + "`)-[:p]->(`" + hc(parent.getName()) + "`)");
            }
            for (final Dependency dep : requires) {
                // CREATE (A)-[:d]->(D)
                logFile5.println("CREATE (`" + hc(id) + "`)-[:d]->(`" + hc(dep.getName()) + "`)");
            }
            logFile1.flush();
            logFile2.flush();
            logFile3.flush();
            logFile4.flush();
            logFile5.flush();
        } finally {
            lock.unlock();
        }
    }

    private static String hc(final ServiceName sn) {
        return "" + System.identityHashCode(sn);
    }

    private static String escape(final ServiceName sn) {
        return sn.getCanonicalName().replace("\"", "\\\"");
    }

}
