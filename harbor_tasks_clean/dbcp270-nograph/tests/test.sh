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
./gradlew :streams:test --tests "org.apache.kafka.streams.processor.internals.TasksTest" -x checkstyleTest --no-daemon > /tmp/test_output.txt 2>&1
if [ $? -ne 0 ]; then
    mkdir -p /logs/verifier
    echo "0" > /logs/verifier/reward.txt
    cp /tmp/test_output.txt /logs/verifier/
    exit 0
fi

# === Phase 2: Deadlock fix verification ===
# Generate verification test at verification time (not present during agent run)
mkdir -p /tmp/verifier-src/org/apache/kafka/streams/processor/internals
cat > /tmp/verifier-src/org/apache/kafka/streams/processor/internals/TasksStateDirectoryVerificationTest.java << 'VERIFICATION_JAVA'
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

/**
 * Fray-based deadlock verifier for the Tasks/StateDirectory ABBA cycle.
 *
 * Reads actual source files at runtime to determine if the bidirectional
 * wiring between Tasks and StateDirectory still creates a lock cycle.
 * If both edges are present, creates threads with that ordering so Fray
 * can find the deadlock. If any edge is broken, exits cleanly.
 */
public class TasksStateDirectoryVerificationTest {

    private static final String SRC = "/app/streams/src/main/java/org/apache/kafka/streams/processor/internals";
    private static final String TASKS_PATH = SRC + "/Tasks.java";
    private static final String STATE_DIR_PATH = SRC + "/StateDirectory.java";

    public static void main(String[] args) throws Exception {
        boolean edgeAB = checkEdge_A_to_B();
        boolean edgeBA = checkEdge_B_to_A();

        if (!edgeAB || !edgeBA) {
            System.out.println("Cycle broken: A->B=" + edgeAB + " B->A=" + edgeBA);
            return; // exit 0 = no deadlock
        }

        System.out.println("Both edges present — creating deadlock scenario for Fray");

        // Lock A: Tasks.this (intrinsic monitor)
        // Lock B: StateDirectory.this (intrinsic monitor)
        final Object lockA = new Object();
        final Object lockB = new Object();

        // Thread 1 (task lifecycle): Lock A -> Lock B
        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                synchronized (lockB) {
                    // noop
                }
            }
        });

        // Thread 2 (directory cleaner): Lock B -> Lock A
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
     * Edge A->B: Tasks.removeTask() is synchronized (Lock A) and calls
     * handleTaskRemovalCleanup() which calls stateDirectory methods (Lock B).
     */
    private static boolean checkEdge_A_to_B() throws IOException {
        if (!Files.exists(Path.of(TASKS_PATH))) return false;
        String src = Files.readString(Path.of(TASKS_PATH));

        // Must have stateDirectory field (bidirectional wiring)
        if (!src.contains("stateDirectory")) return false;
        // Must have handleTaskRemovalCleanup (the injected callback)
        if (!src.contains("handleTaskRemovalCleanup")) return false;

        return true;
    }

    /**
     * Edge B->A: StateDirectory.cleanRemovedTasksCalledByCleanerThread() holds
     * its lock (Lock B) and calls computeRetainedTaskIds() which calls
     * tasksRegistry methods (Lock A).
     */
    private static boolean checkEdge_B_to_A() throws IOException {
        if (!Files.exists(Path.of(STATE_DIR_PATH))) return false;
        String src = Files.readString(Path.of(STATE_DIR_PATH));

        // Must have tasksRegistry field (back-reference to Tasks)
        if (!src.contains("tasksRegistry")) return false;
        // Must have computeRetainedTaskIds (the injected method)
        if (!src.contains("computeRetainedTaskIds")) return false;

        return true;
    }
}
VERIFICATION_JAVA

mkdir -p /tmp/verifier-classes
javac -d /tmp/verifier-classes /tmp/verifier-src/org/apache/kafka/streams/processor/internals/TasksStateDirectoryVerificationTest.java > /tmp/verifier_compile.txt 2>&1
if [ $? -ne 0 ]; then
    mkdir -p /logs/verifier
    echo "0" > /logs/verifier/reward.txt
    cp /tmp/verifier_compile.txt /logs/verifier/
    exit 0
fi

# Run through Fray
fray -cp /tmp/verifier-classes org.apache.kafka.streams.processor.internals.TasksStateDirectoryVerificationTest -- --iter 1000 > /tmp/fray_output.txt 2>&1
RESULT=$?

mkdir -p /logs/verifier
cp /tmp/fray_output.txt /logs/verifier/

if [ $RESULT -eq 0 ]; then
    echo "1" > /logs/verifier/reward.txt
else
    echo "0" > /logs/verifier/reward.txt
fi
