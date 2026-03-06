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
import org.junit.jupiter.api.Timeout;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the 3-node conditional callback cycle (SYNTH-001) deadlock pattern:
 *   Lock A (intrinsic monitor) -> Lock B (RRWL writeLock) -> Lock C (ReentrantLock) -> Lock A
 *
 * This test directly exercises the lock acquisition ordering without requiring
 * full Kafka Streams infrastructure.
 */
@Timeout(30)
public class Synth001DeadlockVerificationTest {

    @Test
    public void shouldDetectThreeNodeCyclicDeadlock() throws InterruptedException {
        // Lock A: intrinsic monitor (simulates KafkaStreams.this)
        final Object lockA = new Object();

        // Lock B: ReentrantReadWriteLock (simulates AbstractStateUpdater.snapshotLock)
        final ReentrantReadWriteLock lockB = new ReentrantReadWriteLock();

        // Lock C: ReentrantLock (simulates TopologyMetadata.topologyLock)
        final ReentrantLock lockC = new ReentrantLock();

        // Latches to ensure all threads hold their first lock before trying the second
        final CountDownLatch allReady = new CountDownLatch(3);
        final CountDownLatch go = new CountDownLatch(1);

        // Thread 1 (Admin/Telemetry): Lock A -> Lock B
        final Thread thread1 = new Thread(() -> {
            synchronized (lockA) {
                allReady.countDown();
                try {
                    go.await();
                } catch (final InterruptedException e) {
                    return;
                }
                // Try to acquire Lock B (write lock) -- will block if Thread 2 holds it
                lockB.writeLock().lock();
                try {
                    // would not reach here in deadlock
                } finally {
                    lockB.writeLock().unlock();
                }
            }
        }, "Thread-1-Admin");

        // Thread 2 (StateUpdater): Lock B -> Lock C
        final Thread thread2 = new Thread(() -> {
            lockB.writeLock().lock();
            try {
                allReady.countDown();
                try {
                    go.await();
                } catch (final InterruptedException e) {
                    return;
                }
                // Try to acquire Lock C -- will block if Thread 3 holds it
                lockC.lock();
                try {
                    // would not reach here in deadlock
                } finally {
                    lockC.unlock();
                }
            } finally {
                lockB.writeLock().unlock();
            }
        }, "Thread-2-StateUpdater");

        // Thread 3 (Topology Update): Lock C -> Lock A
        final Thread thread3 = new Thread(() -> {
            lockC.lock();
            try {
                allReady.countDown();
                try {
                    go.await();
                } catch (final InterruptedException e) {
                    return;
                }
                // Try to acquire Lock A -- will block if Thread 1 holds it
                synchronized (lockA) {
                    // would not reach here in deadlock
                }
            } finally {
                lockC.unlock();
            }
        }, "Thread-3-TopologyUpdate");

        thread1.setDaemon(true);
        thread2.setDaemon(true);
        thread3.setDaemon(true);

        thread1.start();
        thread2.start();
        thread3.start();

        // Wait for all threads to acquire their first lock
        allReady.await(5, TimeUnit.SECONDS);

        // Release all threads simultaneously to attempt their second lock
        go.countDown();

        // Poll for deadlock detection
        final ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedThreads = null;
        final long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            deadlockedThreads = mxBean.findDeadlockedThreads();
            if (deadlockedThreads != null) {
                break;
            }
            Thread.sleep(5);
        }

        assertNotNull(deadlockedThreads,
            "Expected deadlock among 3 threads (A->B->C->A cycle) but none was detected");
    }
}
