#!/bin/bash
cd /app

# === Phase 1: Functional correctness ===
# Recompile the agent's changes
./gradlew :streams:compileJava :streams:compileTestJava -x test -x checkstyleTest -x checkstyleMain -x spotbugsMain --no-daemon > /tmp/compile_output.txt 2>&1
if [ $? -ne 0 ]; then
    mkdir -p /logs/verifier
    echo "0" > /logs/verifier/reward.txt
    cp /tmp/compile_output.txt /logs/verifier/
    exit 0
fi

# Run existing streams unit tests covering the modified subsystem
./gradlew :streams:test --tests "org.apache.kafka.streams.state.internals.ThreadCacheTest" --tests "org.apache.kafka.streams.state.internals.NamedCacheTest" -x checkstyleTest --no-daemon > /tmp/test_output.txt 2>&1
if [ $? -ne 0 ]; then
    mkdir -p /logs/verifier
    echo "0" > /logs/verifier/reward.txt
    cp /tmp/test_output.txt /logs/verifier/
    exit 0
fi

# === Phase 2: Deadlock fix verification ===
# Generate verification test at verification time (not present during agent run)
mkdir -p /tmp/verifier-src/org/apache/kafka/streams/state/internals
cat > /tmp/verifier-src/org/apache/kafka/streams/state/internals/Dbcp270DeadlockVerificationTest.java << 'VERIFICATION_JAVA'
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
 * Fray-based deadlock verifier for the ThreadCache/NamedCache ABBA cycle.
 *
 * Reads actual source files at runtime to determine if the lock cycle edges
 * still exist. If both edges are present, creates threads with that ordering
 * so Fray can find the deadlock. If any edge is broken, exits cleanly.
 *
 * Run via: fray -cp <classpath> org.apache.kafka.streams.state.internals.Dbcp270DeadlockVerificationTest -- --iter 1000
 */
public class Dbcp270DeadlockVerificationTest {

    private static final String SRC = "/app/streams/src/main/java/org/apache/kafka/streams/state/internals";
    private static final String NAMED_CACHE_PATH = SRC + "/NamedCache.java";
    private static final String THREAD_CACHE_PATH = SRC + "/ThreadCache.java";

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

        // Thread 1 (resize): Lock A -> Lock B
        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                synchronized (lockB) {
                    // noop
                }
            }
        });

        // Thread 2 (put with eviction): Lock B -> Lock A
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
     * Edge A->B: ThreadCache.resize() is synchronized (Lock A) and iterates
     * over caches with synchronized(cache) (Lock B). This is existing Kafka code.
     */
    private static boolean checkEdge_A_to_B() throws IOException {
        if (!Files.exists(Path.of(THREAD_CACHE_PATH))) return false;
        String src = Files.readString(Path.of(THREAD_CACHE_PATH));

        // resize must be synchronized
        if (!src.contains("synchronized") || !src.contains("void resize(")) return false;
        // Must iterate over caches with synchronized(cache)
        if (!src.contains("synchronized (cache)")) return false;

        return true;
    }

    /**
     * Edge B->A: NamedCache.flush() calls validateNamespaceActive() which
     * eventually calls parentCache.isNamespaceRegistered() -> getCache()
     * which is synchronized on ThreadCache (Lock A).
     */
    private static boolean checkEdge_B_to_A() throws IOException {
        if (!Files.exists(Path.of(NAMED_CACHE_PATH))) return false;
        String src = Files.readString(Path.of(NAMED_CACHE_PATH));

        // Must have parentCache field (back-reference to ThreadCache)
        if (!src.contains("parentCache")) return false;
        // Must have validateNamespaceActive (the injected validation call)
        if (!src.contains("validateNamespaceActive")) return false;

        return true;
    }
}
VERIFICATION_JAVA

mkdir -p /tmp/verifier-classes
javac -d /tmp/verifier-classes /tmp/verifier-src/org/apache/kafka/streams/state/internals/Dbcp270DeadlockVerificationTest.java > /tmp/verifier_compile.txt 2>&1
if [ $? -ne 0 ]; then
    mkdir -p /logs/verifier
    echo "0" > /logs/verifier/reward.txt
    cp /tmp/verifier_compile.txt /logs/verifier/
    exit 0
fi

# Run through Fray
fray -cp /tmp/verifier-classes org.apache.kafka.streams.state.internals.Dbcp270DeadlockVerificationTest -- --iter 1000 > /tmp/fray_output.txt 2>&1
RESULT=$?

mkdir -p /logs/verifier
cp /tmp/fray_output.txt /logs/verifier/

if [ $RESULT -eq 0 ]; then
    echo "1" > /logs/verifier/reward.txt
else
    echo "0" > /logs/verifier/reward.txt
fi
