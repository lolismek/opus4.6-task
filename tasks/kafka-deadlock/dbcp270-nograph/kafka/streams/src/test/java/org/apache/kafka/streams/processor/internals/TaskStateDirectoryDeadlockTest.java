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

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.utils.LogContext;
import org.apache.kafka.common.utils.MockTime;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.processor.TaskId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TaskStateDirectoryDeadlockTest {

    private static final long DEADLOCK_TIMEOUT_MS = 30_000;

    @TempDir
    File tempDir;

    @Test
    public void shouldDetectDeadlockBetweenTasksAndStateDirectory() throws Exception {
        final TaskId taskId = new TaskId(0, 0);
        final StateDirectory stateDirectory = createStateDirectory();
        stateDirectory.getOrCreateDirectoryForTask(taskId);

        final Tasks tasks = new Tasks(new LogContext("test "));
        tasks.setStateDirectory(stateDirectory);
        stateDirectory.setTasksRegistry(tasks);

        final StreamTask mockTask = createMockTask(taskId);
        final AtomicBoolean deadlockDetected = new AtomicBoolean(false);
        final CountDownLatch startLatch = new CountDownLatch(2);

        final Thread thread1 = createTaskLifecycleThread(tasks, mockTask, deadlockDetected, startLatch);
        final Thread thread2 = createDirectoryCleanerThread(stateDirectory, deadlockDetected, startLatch);
        final Thread detector = createDeadlockDetectorThread(deadlockDetected);

        thread1.start();
        thread2.start();
        detector.start();

        detector.join(DEADLOCK_TIMEOUT_MS);

        thread1.interrupt();
        thread2.interrupt();

        assertTrue(deadlockDetected.get(),
            "Expected deadlock between Tasks and StateDirectory but none was detected");
    }

    private StateDirectory createStateDirectory() {
        final Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.STATE_DIR_CONFIG, tempDir.getAbsolutePath());
        return new StateDirectory(new StreamsConfig(props), new MockTime(), true, false);
    }

    private StreamTask createMockTask(final TaskId taskId) {
        final StreamTask mockTask = mock(StreamTask.class);
        when(mockTask.id()).thenReturn(taskId);
        when(mockTask.isActive()).thenReturn(true);
        doReturn(Task.State.CLOSED).when(mockTask).state();
        when(mockTask.inputPartitions()).thenReturn(
            Collections.singleton(new TopicPartition("topic", 0))
        );
        return mockTask;
    }

    private Thread createTaskLifecycleThread(final Tasks tasks,
                                             final StreamTask mockTask,
                                             final AtomicBoolean deadlockDetected,
                                             final CountDownLatch startLatch) {
        final Thread thread = new Thread(() -> {
            startLatch.countDown();
            awaitLatch(startLatch);
            while (!deadlockDetected.get() && !Thread.currentThread().isInterrupted()) {
                tasks.addActiveTask(mockTask);
                tasks.removeTask(mockTask);
            }
        }, "task-lifecycle-thread");
        thread.setDaemon(true);
        return thread;
    }

    private Thread createDirectoryCleanerThread(final StateDirectory stateDirectory,
                                                final AtomicBoolean deadlockDetected,
                                                final CountDownLatch startLatch) {
        final Thread thread = new Thread(() -> {
            startLatch.countDown();
            awaitLatch(startLatch);
            while (!deadlockDetected.get() && !Thread.currentThread().isInterrupted()) {
                stateDirectory.cleanRemovedTasks(0);
            }
        }, "directory-cleaner-thread");
        thread.setDaemon(true);
        return thread;
    }

    private Thread createDeadlockDetectorThread(final AtomicBoolean deadlockDetected) {
        final Thread thread = new Thread(() -> {
            final ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
            while (!deadlockDetected.get() && !Thread.currentThread().isInterrupted()) {
                final long[] ids = mxBean.findDeadlockedThreads();
                if (ids != null && ids.length > 0) {
                    deadlockDetected.set(true);
                    return;
                }
                awaitMs(5);
            }
        }, "deadlock-detector");
        thread.setDaemon(true);
        return thread;
    }

    private static void awaitLatch(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitMs(final long ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
