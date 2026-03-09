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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fray-based deadlock verifier for the ThreadCache/NamedCache rebalance callback cycle.
 *
 * Reads actual source files at runtime to determine if the lock cycle edges
 * still exist. If both edges are present, creates threads with that ordering
 * so Fray can find the deadlock. If any edge is broken, exits cleanly.
 */
public class CacheRebalanceVerificationTest {

    private static final String SRC = "/app/streams/src/main/java/org/apache/kafka/streams/state/internals";
    private static final String THREAD_CACHE_PATH = SRC + "/ThreadCache.java";
    private static final String CACHING_KV_PATH = SRC + "/CachingKeyValueStore.java";

    public static void main(String[] args) throws Exception {
        boolean edgeAB = checkEdge_A_to_B();
        boolean edgeBA = checkEdge_B_to_A();

        if (!edgeAB || !edgeBA) {
            System.out.println("Cycle broken: A->B=" + edgeAB + " B->A=" + edgeBA);
            return; // exit 0 = no deadlock
        }

        System.out.println("Both edges present — creating deadlock scenario for Fray");

        // Lock A: ThreadCache.this (intrinsic monitor)
        // Lock B: NamedCache.this (intrinsic monitor)
        final Object lockA = new Object();
        final Object lockB = new Object();

        // Thread 1 (close): Lock A -> Lock B
        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                synchronized (lockB) {
                    // noop
                }
            }
        });

        // Thread 2 (put + eviction callback): Lock B -> Lock A
        Thread t2 = new Thread(() -> {
            synchronized (lockB) {
                synchronized (lockA) {
                    // noop
                }
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }

    /**
     * Edge A->B: ThreadCache.close(namespace) is synchronized (Lock A) and calls
     * NamedCache.sizeInBytes()/close() which are synchronized (Lock B).
     * This is existing Kafka code.
     */
    private static boolean checkEdge_A_to_B() throws IOException {
        if (!Files.exists(Path.of(THREAD_CACHE_PATH))) return false;
        String src = Files.readString(Path.of(THREAD_CACHE_PATH));

        // close(namespace) must be synchronized
        if (!src.contains("synchronized") || !src.contains("void close(")) return false;

        return true;
    }

    /**
     * Edge B->A: NamedCache eviction -> flush -> listener -> CachingKeyValueStore
     * putAndMaybeForward() -> ensureCacheCapacity() -> ThreadCache.rebalanceIfNeeded()
     * -> resize() which is synchronized on ThreadCache (Lock A).
     */
    private static boolean checkEdge_B_to_A() throws IOException {
        // Check ThreadCache has rebalanceIfNeeded
        if (!Files.exists(Path.of(THREAD_CACHE_PATH))) return false;
        String tcSrc = Files.readString(Path.of(THREAD_CACHE_PATH));
        if (!tcSrc.contains("rebalanceIfNeeded")) return false;

        // Check CachingKeyValueStore has ensureCacheCapacity
        if (!Files.exists(Path.of(CACHING_KV_PATH))) return false;
        String kvSrc = Files.readString(Path.of(CACHING_KV_PATH));
        if (!kvSrc.contains("ensureCacheCapacity")) return false;

        return true;
    }
}
