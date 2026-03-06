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
package org.apache.kafka.streams.state.internals;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.common.utils.LogContext;
import org.apache.kafka.streams.processor.internals.MockStreamsMetrics;

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reproduces a deadlock between ThreadCache (wrapper) and NamedCache (physical cache)
 * when cache eviction triggers a flush listener callback that re-enters ThreadCache.
 *
 * Lock cycle:
 *   Thread A: NamedCache.this (via ThreadCache.put -> synchronized(cache))
 *             -> ThreadCache.this (via rebalanceIfNeeded -> resize)
 *   Thread B: ThreadCache.this (via close)
 *             -> NamedCache.this (via removed.sizeInBytes / removed.close)
 */
public class CacheRebalanceDeadlockTest {

    private static final String NAMESPACE = "0.0-test-store";
    private static final byte[] RAW_KEY = new byte[]{0};
    private static final byte[] RAW_VALUE = new byte[]{0};
    private static final long TIMEOUT_MS = 30_000;
    private static final int NUM_PUTTER_THREADS = 4;

    private final AtomicBoolean deadlockDetected = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(true);

    @Test
    public void shouldDetectDeadlockOnEvictionRebalance() throws InterruptedException {
        final ThreadCache cache = createSmallCache();
        registerRebalanceListener(cache);

        final CountDownLatch startLatch = new CountDownLatch(NUM_PUTTER_THREADS + 1);
        final List<Thread> putters = createPutterThreads(cache, startLatch);
        final Thread closer = createCloserThread(cache, startLatch);
        final Thread detector = createDetectorThread(putters, closer);

        startAllThreads(putters, closer, detector);
        waitForDeadlockOrTimeout(putters, closer);
        stopAllThreads(putters, closer, detector);

        if (!deadlockDetected.get()) {
            fail("Expected deadlock between ThreadCache and NamedCache was not detected within timeout");
        }
    }

    private ThreadCache createSmallCache() {
        final LogContext logContext = new LogContext("test ");
        final MockStreamsMetrics metrics = new MockStreamsMetrics(new Metrics());
        return new ThreadCache(logContext, 200L, metrics);
    }

    private void registerRebalanceListener(final ThreadCache cache) {
        cache.addDirtyEntryFlushListener(NAMESPACE, entries -> cache.rebalanceIfNeeded());
    }

    private List<Thread> createPutterThreads(final ThreadCache cache, final CountDownLatch startLatch) {
        final List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < NUM_PUTTER_THREADS; t++) {
            final int threadId = t;
            threads.add(new Thread(() -> runPutter(cache, startLatch, threadId), "putter-" + t));
        }
        return threads;
    }

    private void runPutter(final ThreadCache cache, final CountDownLatch startLatch, final int threadId) {
        awaitLatch(startLatch);
        int i = 0;
        while (running.get() && !deadlockDetected.get()) {
            try {
                final Bytes key = Bytes.wrap(new byte[]{(byte) ((threadId * 16 + i) % 128)});
                cache.put(NAMESPACE, key, new LRUCacheEntry(
                    new byte[64], new RecordHeaders(), true, 1L, 1L, 1, "topic", RAW_KEY, RAW_VALUE));
                i++;
            } catch (final Exception ignored) {
                // Continue on exceptions from close/listener race
            }
        }
    }

    private Thread createCloserThread(final ThreadCache cache, final CountDownLatch startLatch) {
        return new Thread(() -> {
            awaitLatch(startLatch);
            while (running.get() && !deadlockDetected.get()) {
                try {
                    cache.close(NAMESPACE);
                    registerRebalanceListener(cache);
                } catch (final Exception ignored) {
                    // Expected during close/re-register race
                }
            }
        }, "closer-thread");
    }

    private Thread createDetectorThread(final List<Thread> putters, final Thread closer) {
        return new Thread(() -> {
            final ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
            while (running.get()) {
                final long[] deadlockedIds = mxBean.findDeadlockedThreads();
                if (deadlockedIds != null && deadlockedIds.length > 0) {
                    deadlockDetected.set(true);
                    interruptAll(putters);
                    closer.interrupt();
                    return;
                }
                sleepQuietly(5);
            }
        }, "deadlock-detector");
    }

    private void startAllThreads(final List<Thread> putters, final Thread closer, final Thread detector) {
        putters.forEach(Thread::start);
        closer.start();
        detector.start();
    }

    private void waitForDeadlockOrTimeout(final List<Thread> putters, final Thread closer) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (!deadlockDetected.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            if (allDead(putters) && !closer.isAlive()) {
                break;
            }
        }
    }

    private void stopAllThreads(final List<Thread> putters, final Thread closer, final Thread detector)
        throws InterruptedException {
        running.set(false);
        interruptAll(putters);
        closer.interrupt();
        detector.interrupt();
        for (final Thread t : putters) {
            t.join(5000);
        }
        closer.join(5000);
        detector.join(5000);
    }

    private boolean allDead(final List<Thread> threads) {
        for (final Thread t : threads) {
            if (t.isAlive()) {
                return false;
            }
        }
        return true;
    }

    private void interruptAll(final List<Thread> threads) {
        threads.forEach(Thread::interrupt);
    }

    private void awaitLatch(final CountDownLatch latch) {
        latch.countDown();
        try {
            latch.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sleepQuietly(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
