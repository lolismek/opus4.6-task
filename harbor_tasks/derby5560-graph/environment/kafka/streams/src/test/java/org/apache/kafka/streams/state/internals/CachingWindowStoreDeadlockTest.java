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
import org.apache.kafka.streams.processor.internals.ProcessorRecordContext;
import org.apache.kafka.test.InternalMockProcessorContext;
import org.apache.kafka.test.TestUtils;

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Stress test for concurrent close and cache eviction on CachingWindowStore.
 * Validates that closing a store while the cache is evicting entries does not deadlock.
 */
public class CachingWindowStoreDeadlockTest {

    private static final long WINDOW_SIZE = 10L;
    private static final long SEGMENT_INTERVAL = 100L;
    private static final String TOPIC = "topic";
    private static final int CACHE_SIZE_BYTES = 600;
    private static final long TIMESTAMP_SPREAD = WINDOW_SIZE + 5;

    @Test
    public void shouldNotDeadlockOnConcurrentCloseAndEviction() throws Exception {
        final AtomicBoolean deadlockDetected = new AtomicBoolean(false);
        final AtomicReference<String> deadlockInfo = new AtomicReference<>();
        final AtomicBoolean stop = new AtomicBoolean(false);
        final CountDownLatch ready = new CountDownLatch(2);

        final ThreadCache cache = new ThreadCache(
            new LogContext("testCache "),
            CACHE_SIZE_BYTES,
            new MockStreamsMetrics(new Metrics())
        );

        final InMemoryWindowStore underlying =
            new InMemoryWindowStore("test", 100L, WINDOW_SIZE, false, "metrics-scope");
        final CachingWindowStore cachingStore = new CachingWindowStore(underlying, WINDOW_SIZE, SEGMENT_INTERVAL);

        final InternalMockProcessorContext<?, ?> context =
            new InternalMockProcessorContext<>(TestUtils.tempDirectory(), null, null, null, cache);
        context.setRecordContext(new ProcessorRecordContext(0L, 0, 0, TOPIC, new RecordHeaders()));
        cachingStore.init(context, cachingStore);

        fillCache(cachingStore, context, 50);

        final Thread storeCloseThread = createStoreCloseThread(
            cachingStore, context, ready, stop, deadlockDetected);
        final Thread cacheResizeThread = createCacheResizeThread(
            cache, ready, stop, deadlockDetected);
        final Thread detectorThread = createDetectorThread(
            stop, deadlockDetected, deadlockInfo);

        startAndAwaitDeadlock(storeCloseThread, cacheResizeThread, detectorThread,
            stop, deadlockDetected, deadlockInfo);
    }

    private void fillCache(final CachingWindowStore store,
                           final InternalMockProcessorContext<?, ?> context,
                           final int count) {
        for (int i = 0; i < count; i++) {
            final long timestamp = i * TIMESTAMP_SPREAD;
            context.setRecordContext(new ProcessorRecordContext(timestamp, 0, 0, TOPIC, new RecordHeaders()));
            store.put(Bytes.wrap(("key-" + i).getBytes()), ("value-" + i).getBytes(), timestamp);
        }
    }

    private Thread createStoreCloseThread(final CachingWindowStore cachingStore,
                                          final InternalMockProcessorContext<?, ?> context,
                                          final CountDownLatch ready,
                                          final AtomicBoolean stop,
                                          final AtomicBoolean deadlockDetected) {
        return new Thread(() -> {
            ready.countDown();
            try {
                ready.await();
            } catch (final InterruptedException e) {
                return;
            }
            while (!stop.get() && !deadlockDetected.get()) {
                try {
                    cachingStore.close();
                    context.setRecordContext(
                        new ProcessorRecordContext(0L, 0, 0, TOPIC, new RecordHeaders()));
                    cachingStore.init(context, cachingStore);
                    fillCache(cachingStore, context, 20);
                } catch (final Exception e) {
                    // Store may be in inconsistent state during concurrent access
                }
            }
        }, "StoreClose");
    }

    private Thread createCacheResizeThread(final ThreadCache cache,
                                           final CountDownLatch ready,
                                           final AtomicBoolean stop,
                                           final AtomicBoolean deadlockDetected) {
        return new Thread(() -> {
            ready.countDown();
            try {
                ready.await();
            } catch (final InterruptedException e) {
                return;
            }
            while (!stop.get() && !deadlockDetected.get()) {
                try {
                    cache.resize(200);
                    cache.resize(CACHE_SIZE_BYTES);
                } catch (final Exception e) {
                    // Continue on transient errors
                }
            }
        }, "CacheResize");
    }

    private Thread createDetectorThread(final AtomicBoolean stop,
                                        final AtomicBoolean deadlockDetected,
                                        final AtomicReference<String> deadlockInfo) {
        return new Thread(() -> {
            final ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
            while (!stop.get()) {
                checkForDeadlock(mxBean, deadlockDetected, deadlockInfo);
                if (deadlockDetected.get()) {
                    return;
                }
                try {
                    Thread.sleep(5);
                } catch (final InterruptedException e) {
                    return;
                }
            }
        }, "DeadlockDetector");
    }

    private void checkForDeadlock(final ThreadMXBean mxBean,
                                  final AtomicBoolean deadlockDetected,
                                  final AtomicReference<String> deadlockInfo) {
        final long[] deadlockedThreads = mxBean.findDeadlockedThreads();
        if (deadlockedThreads != null) {
            final StringBuilder sb = new StringBuilder("DEADLOCK DETECTED!\n");
            for (final ThreadInfo info : mxBean.getThreadInfo(deadlockedThreads, true, true)) {
                sb.append(info.toString());
            }
            deadlockInfo.set(sb.toString());
            deadlockDetected.set(true);
        }
    }

    private void startAndAwaitDeadlock(final Thread storeCloseThread,
                                       final Thread cacheResizeThread,
                                       final Thread detectorThread,
                                       final AtomicBoolean stop,
                                       final AtomicBoolean deadlockDetected,
                                       final AtomicReference<String> deadlockInfo)
                                       throws InterruptedException {
        storeCloseThread.setDaemon(true);
        cacheResizeThread.setDaemon(true);
        detectorThread.setDaemon(true);

        detectorThread.start();
        storeCloseThread.start();
        cacheResizeThread.start();

        final long deadline = System.currentTimeMillis() + 30_000;
        while (!deadlockDetected.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }

        stop.set(true);
        storeCloseThread.join(5000);
        cacheResizeThread.join(5000);
        detectorThread.join(5000);

        if (deadlockDetected.get()) {
            fail("Deadlock detected between CachingWindowStore and ThreadCache:\n" + deadlockInfo.get());
        }
    }
}
