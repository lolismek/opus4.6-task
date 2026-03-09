/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.streams.processor.internals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Fray-based deadlock verifier for the 3-node metrics cycle (A->B->C->A).
 *
 * Reads actual source files at runtime to determine if each edge of the
 * lock cycle still has nested lock ordering. If all 3 edges exist, creates
 * threads with that ordering so Fray can find the deadlock. If any edge is
 * broken, exits cleanly.
 */
public class MetricsCycleVerificationTest {

    private static final String SRC = "/app/streams/src/main/java/org/apache/kafka/streams/processor/internals";
    private static final String CACHE_PATH = SRC + "/TopologyMetricsCache.java";
    private static final String CONNECTOR_PATH = SRC + "/StreamsMetricsConnector.java";
    private static final String SESSION_PATH = SRC + "/MetricsSessionManager.java";

    public static void main(String[] args) throws Exception {
        boolean edgeAB = checkEdge_A_to_B();
        boolean edgeBC = checkEdge_B_to_C();
        boolean edgeCA = checkEdge_C_to_A();

        if (!edgeAB || !edgeBC || !edgeCA) {
            System.out.println("Cycle broken: A->B=" + edgeAB + " B->C=" + edgeBC + " C->A=" + edgeCA);
            return; // exit 0 = no deadlock
        }

        System.out.println("All 3 edges present — creating deadlock scenario for Fray");

        // Lock A: TopologyMetricsCache (synchronized intrinsic monitor)
        final Object lockA = new Object();
        // Lock B: AbstractMetricsTransport (ReentrantReadWriteLock)
        final ReentrantReadWriteLock lockB = new ReentrantReadWriteLock();
        // Lock C: MetricsSessionManager (ReentrantLock)
        final ReentrantLock lockC = new ReentrantLock();

        // Thread 1 (metrics scrape): Lock A -> Lock B
        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                lockB.writeLock().lock();
                lockB.writeLock().unlock();
            }
        });

        // Thread 2 (transport maintenance): Lock B -> Lock C
        Thread t2 = new Thread(() -> {
            lockB.writeLock().lock();
            try {
                lockC.lock();
                lockC.unlock();
            } finally {
                lockB.writeLock().unlock();
            }
        });

        // Thread 3 (session renewal): Lock C -> Lock A
        Thread t3 = new Thread(() -> {
            lockC.lock();
            try {
                synchronized (lockA) {
                    // noop
                }
            } finally {
                lockC.unlock();
            }
        });

        t1.start();
        t2.start();
        t3.start();
        t1.join();
        t2.join();
        t3.join();
    }

    /**
     * Edge A->B: TopologyMetricsCache.getSnapshot() is synchronized (Lock A) and
     * calls connector.fetchLatestMetrics() which may call refreshTransport() ->
     * acquireExclusive() (Lock B writeLock).
     */
    private static boolean checkEdge_A_to_B() throws IOException {
        if (!Files.exists(Path.of(CACHE_PATH))) return false;
        String src = Files.readString(Path.of(CACHE_PATH));

        // getSnapshot must be synchronized
        if (!src.contains("synchronized") || !src.contains("getSnapshot")) return false;
        // Must call fetchLatestMetrics (which acquires transport lock)
        if (!src.contains("fetchLatestMetrics")) return false;

        return true;
    }

    /**
     * Edge B->C: StreamsMetricsConnector.performMaintenance() acquires exclusive
     * lock (Lock B) and calls validateSession() -> sessionManager.refreshSessionIfExpired()
     * which acquires sessionLock (Lock C).
     *
     * The key check: validateSession() must be called BETWEEN acquireExclusive
     * and releaseExclusive (i.e., nested under the write lock).
     */
    private static boolean checkEdge_B_to_C() throws IOException {
        if (!Files.exists(Path.of(CONNECTOR_PATH))) return false;
        String src = Files.readString(Path.of(CONNECTOR_PATH));

        // Find performMaintenance method
        int methodStart = src.indexOf("void performMaintenance()");
        if (methodStart < 0) return false;

        String methodBody = extractMethodBody(src, methodStart);
        if (methodBody == null) return false;

        int acquirePos = methodBody.indexOf("acquireExclusive()");
        int releasePos = methodBody.indexOf("releaseExclusive()");
        int validatePos = methodBody.indexOf("validateSession()");

        if (acquirePos < 0 || releasePos < 0 || validatePos < 0) return false;

        // validateSession must be between acquire and release (nested under lock)
        return acquirePos < validatePos && validatePos < releasePos;
    }

    /**
     * Edge C->A: MetricsSessionManager.renewSession() holds sessionLock (Lock C)
     * and calls notifyListeners() -> cache.invalidateAll() which is synchronized
     * on TopologyMetricsCache (Lock A).
     */
    private static boolean checkEdge_C_to_A() throws IOException {
        if (!Files.exists(Path.of(SESSION_PATH))) return false;
        String src = Files.readString(Path.of(SESSION_PATH));

        // Must have notifyListeners (which calls invalidateAll on the cache)
        if (!src.contains("notifyListeners")) return false;

        if (!Files.exists(Path.of(CACHE_PATH))) return false;
        String cacheSrc = Files.readString(Path.of(CACHE_PATH));

        // Cache must have invalidateAll (the callback target)
        if (!cacheSrc.contains("invalidateAll")) return false;

        return true;
    }

    private static String extractMethodBody(String src, int methodStart) {
        int braceStart = src.indexOf('{', methodStart);
        if (braceStart < 0) return null;
        int depth = 1;
        int i = braceStart + 1;
        while (i < src.length() && depth > 0) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            i++;
        }
        if (depth != 0) return null;
        return src.substring(braceStart, i);
    }
}
