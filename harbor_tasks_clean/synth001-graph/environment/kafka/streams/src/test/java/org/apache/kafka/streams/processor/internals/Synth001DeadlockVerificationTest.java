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
 * Fray-based deadlock verifier for SYNTH-001 (3-node cycle: A→B→C→A).
 *
 * Reads the actual source files at runtime to determine if each edge of the
 * lock cycle still has nested lock ordering. If all 3 edges exist, creates
 * threads with that ordering so Fray can find the deadlock. If any edge is
 * broken (method removed, locks reordered), exits cleanly (no deadlock).
 *
 * Run via: fray-gradle org.apache.kafka.streams.processor.internals.Synth001DeadlockVerificationTest -- --iter 1000
 */
public class Synth001DeadlockVerificationTest {

    private static final String STREAMS_SRC = "/app/streams/src/main/java/org/apache/kafka/streams";
    private static final String KAFKA_STREAMS_PATH = STREAMS_SRC + "/KafkaStreams.java";
    private static final String DSU_PATH = STREAMS_SRC + "/processor/internals/DefaultStateUpdater.java";
    private static final String ASU_PATH = STREAMS_SRC + "/processor/internals/AbstractStateUpdater.java";
    private static final String TM_PATH = STREAMS_SRC + "/processor/internals/TopologyMetadata.java";

    public static void main(String[] args) throws Exception {
        // Check each edge of the 3-node cycle
        boolean edgeAB = checkEdge_A_to_B();
        boolean edgeBC = checkEdge_B_to_C();
        boolean edgeCA = checkEdge_C_to_A();

        if (!edgeAB || !edgeBC || !edgeCA) {
            // At least one edge is broken — cycle cannot form
            System.out.println("Cycle broken: A->B=" + edgeAB + " B->C=" + edgeBC + " C->A=" + edgeCA);
            return; // exit 0 = no deadlock
        }

        System.out.println("All 3 edges present — creating deadlock scenario for Fray");

        // Lock A: intrinsic monitor (KafkaStreams.this)
        final Object lockA = new Object();
        // Lock B: ReentrantReadWriteLock (AbstractStateUpdater.snapshotLock)
        final ReentrantReadWriteLock lockB = new ReentrantReadWriteLock();
        // Lock C: ReentrantLock (TopologyMetadata.topologyLock)
        final ReentrantLock lockC = new ReentrantLock();

        // Thread 1 (Admin): Lock A → Lock B
        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                lockB.writeLock().lock();
                lockB.writeLock().unlock();
            }
        });

        // Thread 2 (StateUpdater): Lock B → Lock C
        Thread t2 = new Thread(() -> {
            lockB.writeLock().lock();
            try {
                lockC.lock();
                lockC.unlock();
            } finally {
                lockB.writeLock().unlock();
            }
        });

        // Thread 3 (Topology Update): Lock C → Lock A
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
     * Edge A→B: KafkaStreams.refreshRestorationState() is synchronized (Lock A)
     * and calls invalidateRestorationSnapshot which acquires snapshotLock (Lock B).
     */
    private static boolean checkEdge_A_to_B() throws IOException {
        if (!Files.exists(Path.of(KAFKA_STREAMS_PATH))) return false;
        String src = Files.readString(Path.of(KAFKA_STREAMS_PATH));

        // refreshRestorationState must exist and be synchronized
        if (!src.contains("synchronized void refreshRestorationState()")) return false;
        // It must call invalidateRestorationSnapshot (which acquires snapshotLock)
        if (!src.contains("invalidateRestorationSnapshot")) return false;

        return true;
    }

    /**
     * Edge B→C: DefaultStateUpdater.maybeRefreshRestorationSnapshot() acquires
     * snapshotLock (Lock B) and then, while still holding it, calls
     * topologyMetadata.maybeNotifyTopologyVersionListeners() which acquires
     * topologyLock (Lock C).
     *
     * The key check: the notify call must be BETWEEN acquireSnapshotExclusive
     * and releaseSnapshotExclusive (i.e., nested).
     */
    private static boolean checkEdge_B_to_C() throws IOException {
        if (!Files.exists(Path.of(ASU_PATH))) return false;
        if (!Files.exists(Path.of(DSU_PATH))) return false;
        String src = Files.readString(Path.of(DSU_PATH));

        // Find maybeRefreshRestorationSnapshot method
        int methodStart = src.indexOf("void maybeRefreshRestorationSnapshot()");
        if (methodStart < 0) return false;

        // Extract method body by counting braces
        String methodBody = extractMethodBody(src, methodStart);
        if (methodBody == null) return false;

        int acquirePos = methodBody.indexOf("acquireSnapshotExclusive()");
        int releasePos = methodBody.indexOf("releaseSnapshotExclusive()");
        int notifyPos = methodBody.indexOf("maybeNotifyTopologyVersionListeners()");

        if (acquirePos < 0 || releasePos < 0 || notifyPos < 0) return false;

        // Notify must be between acquire and release (nested under the lock)
        return acquirePos < notifyPos && notifyPos < releasePos;
    }

    /**
     * Edge C→A: TopologyMetadata holds topologyLock (Lock C) and calls
     * notifyLifecycleObservers() which invokes TopologyChangeHandler.onTopologyChange()
     * in KafkaStreams, which calls refreshRestorationState() (synchronized = Lock A).
     */
    private static boolean checkEdge_C_to_A() throws IOException {
        if (!Files.exists(Path.of(TM_PATH))) return false;
        String tmSrc = Files.readString(Path.of(TM_PATH));

        // TopologyLifecycleObserver interface and notifyLifecycleObservers must exist
        if (!tmSrc.contains("interface TopologyLifecycleObserver")) return false;
        if (!tmSrc.contains("notifyLifecycleObservers()")) return false;

        if (!Files.exists(Path.of(KAFKA_STREAMS_PATH))) return false;
        String ksSrc = Files.readString(Path.of(KAFKA_STREAMS_PATH));

        // TopologyChangeHandler must exist and call refreshRestorationState
        if (!ksSrc.contains("TopologyChangeHandler")) return false;
        if (!ksSrc.contains("refreshRestorationState()")) return false;

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
