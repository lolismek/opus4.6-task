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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ThreadCacheDeadlockTest {

    private static final String NAMESPACE = "0.0-store";
    private static final LogContext LOG_CONTEXT = new LogContext("deadlockTest ");
    private static final byte[] RAW_KEY = new byte[]{0};
    private static final byte[] RAW_VALUE = new byte[]{0};

    @Test
    public void shouldDetectDeadlockBetweenResizeAndPut() throws InterruptedException {
        // Use a tiny cache so that puts trigger eviction (and thus flush) immediately.
        // Each entry is roughly 100+ bytes, so 200 bytes means eviction after ~1-2 entries.
        final long smallCacheSize = 200L;
        final long largeCacheSize = 500L;
        final ThreadCache cache = new ThreadCache(LOG_CONTEXT, smallCacheSize, new MockStreamsMetrics(new Metrics()));

        // Register a flush listener (required by NamedCache.flush)
        cache.addDirtyEntryFlushListener(NAMESPACE, dirty -> { });

        // Seed the cache with one dirty entry so eviction has something to flush
        cache.put(NAMESPACE, Bytes.wrap(new byte[]{42}),
            new LRUCacheEntry(new byte[]{1, 2, 3}, new RecordHeaders(), true, 1L, 1L, 1, "", RAW_KEY, RAW_VALUE));

        final AtomicBoolean stop = new AtomicBoolean(false);
        final AtomicReference<long[]> deadlockedThreads = new AtomicReference<>();
        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        // Thread A: continuously resize (acquires ThreadCache.this, then tries NamedCache.this)
        final Thread resizer = new Thread(() -> {
            while (!stop.get()) {
                cache.resize(smallCacheSize);
                cache.resize(largeCacheSize);
            }
        }, "resizer-thread");

        // Thread B: continuously put dirty entries (acquires NamedCache.this via synchronized(cache),
        // eviction triggers flush -> validateNamespaceActive -> isNamespaceRegistered -> getCache
        // which tries to acquire ThreadCache.this)
        final Thread putter = new Thread(() -> {
            int counter = 0;
            while (!stop.get()) {
                final byte[] key = new byte[]{(byte) (counter % 256), (byte) (counter / 256)};
                cache.put(NAMESPACE, Bytes.wrap(key),
                    new LRUCacheEntry(new byte[]{1, 2, 3, 4, 5}, new RecordHeaders(), true, 1L, 1L, 1, "", RAW_KEY, RAW_VALUE));
                counter++;
            }
        }, "putter-thread");

        // Deadlock detector: polls ThreadMXBean every 5ms
        final Thread detector = new Thread(() -> {
            while (!stop.get()) {
                final long[] ids = threadMXBean.findDeadlockedThreads();
                if (ids != null) {
                    deadlockedThreads.set(ids);
                    stop.set(true);
                    return;
                }
                try {
                    Thread.sleep(5);
                } catch (final InterruptedException e) {
                    return;
                }
            }
        }, "deadlock-detector");

        resizer.setDaemon(true);
        putter.setDaemon(true);
        detector.setDaemon(true);

        resizer.start();
        putter.start();
        detector.start();

        // Wait up to 30 seconds for deadlock detection
        detector.join(30_000);
        stop.set(true);

        // Give threads a moment to stop
        resizer.join(1_000);
        putter.join(1_000);

        assertNotNull(deadlockedThreads.get(),
            "Expected a deadlock between resize (ThreadCache.this -> NamedCache.this) " +
            "and put/evict/flush (NamedCache.this -> ThreadCache.this via validateNamespaceActive)");
    }
}
