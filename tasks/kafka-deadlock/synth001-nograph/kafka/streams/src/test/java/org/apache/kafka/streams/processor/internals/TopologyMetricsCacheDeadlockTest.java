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

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the 3-node conditional callback cycle deadlock can be
 * triggered when rebalancing mode is active:
 *
 *   Thread 1: synchronized(TopologyMetricsCache) -> writeLock(AbstractMetricsTransport)
 *   Thread 2: writeLock(AbstractMetricsTransport) -> sessionLock(MetricsSessionManager)
 *   Thread 3: sessionLock(MetricsSessionManager) -> synchronized(TopologyMetricsCache)
 *
 * The closing edge (C->A) only fires when newSession.shardId != oldSession.shardId.
 */
public class TopologyMetricsCacheDeadlockTest {

    @Test
    public void shouldDetectDeadlockUnderRebalancing() throws Exception {
        // 1. Create the object graph
        final MetricsSessionManager sessionManager = new MetricsSessionManager(1L); // 1ms TTL -> always expired
        final StreamsMetricsConnector connector = new StreamsMetricsConnector(sessionManager);
        final TopologyMetricsCache cache = new TopologyMetricsCache(connector, 0L); // 0ms TTL -> always stale

        // 2. Register the invalidation listener
        sessionManager.registerListener(cache.createInvalidationListener());

        // 3. Enable rebalancing mode so shard IDs change on renewal
        sessionManager.setRebalancingMode(true);

        // 4. Mark transport as invalid so every fetchLatestMetrics triggers refreshTransport -> writeLock
        connector.markTransportInvalid();

        // 5. Synchronization primitives
        final CyclicBarrier barrier = new CyclicBarrier(3);
        final AtomicBoolean running = new AtomicBoolean(true);

        // Thread 1: metrics scrape path (A->B edge)
        // synchronized(cache) -> connector.fetchLatestMetrics -> acquireExclusive
        final Thread thread1 = new Thread(() -> {
            try {
                barrier.await();
                while (running.get()) {
                    cache.getSnapshot("test-topology");
                    connector.markTransportInvalid(); // re-invalidate for next iteration
                }
            } catch (final Exception ignored) {
            }
        }, "metrics-scrape-thread");

        // Thread 2: transport maintenance path (B->C edge)
        // acquireExclusive -> sessionManager.refreshSessionIfExpired -> sessionLock
        final Thread thread2 = new Thread(() -> {
            try {
                barrier.await();
                while (running.get()) {
                    connector.performMaintenance();
                }
            } catch (final Exception ignored) {
            }
        }, "transport-maintenance-thread");

        // Thread 3: session renewal path (C->A edge, conditional)
        // sessionLock -> notifyListeners -> onSessionChanged -> cache.invalidateAll -> synchronized(cache)
        final Thread thread3 = new Thread(() -> {
            try {
                barrier.await();
                while (running.get()) {
                    sessionManager.renewSession();
                }
            } catch (final Exception ignored) {
            }
        }, "session-renewal-thread");

        thread1.setDaemon(true);
        thread2.setDaemon(true);
        thread3.setDaemon(true);

        thread1.start();
        thread2.start();
        thread3.start();

        // 6. Poll for deadlock detection
        final ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
        boolean deadlockDetected = false;
        final long deadline = System.currentTimeMillis() + 10_000L;

        while (System.currentTimeMillis() < deadline) {
            final long[] deadlockedThreads = mxBean.findDeadlockedThreads();
            if (deadlockedThreads != null && deadlockedThreads.length >= 2) {
                deadlockDetected = true;
                break;
            }
            Thread.sleep(50);
        }

        // 7. Cleanup
        running.set(false);
        thread1.interrupt();
        thread2.interrupt();
        thread3.interrupt();

        assertTrue(deadlockDetected, "Expected deadlock from 3-node conditional callback cycle but none was detected");
    }
}
