#!/bin/bash
cd /app

# === Phase 1: Compile check ===
./gradlew :streams:compileJava :streams:compileTestJava -x test -x checkstyleTest -x checkstyleMain -x spotbugsMain --no-daemon > /tmp/compile_output.txt 2>&1
if [ $? -ne 0 ]; then
    mkdir -p /logs/verifier
    echo "0" > /logs/verifier/reward.txt
    cp /tmp/compile_output.txt /logs/verifier/
    exit 0
fi

# === Phase 2: Deadlock fix verification (4 bugs, all must pass) ===
BUGS_FIXED=0
BUGS_TOTAL=4

# ---------- Bug A: CachingWindowStore ABBA cycle ----------
mkdir -p /tmp/verifier-src-a/org/apache/kafka/streams/state/internals
cat > /tmp/verifier-src-a/org/apache/kafka/streams/state/internals/BugAVerificationTest.java << 'VERIFICATION_JAVA_A'
package org.apache.kafka.streams.state.internals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Verifies Bug A: CachingWindowStore ABBA deadlock.
 * If recordFlushCompletion and fetchPersistedRange still exist,
 * the reverse lock edge (ThreadCache → CachingWindowStore) is present.
 * Creates 2 threads with opposite lock ordering to trigger Fray detection.
 */
public class BugAVerificationTest {
    private static final String CWS_PATH = "/app/streams/src/main/java/org/apache/kafka/streams/state/internals/CachingWindowStore.java";

    public static void main(String[] args) throws Exception {
        if (!Files.exists(Path.of(CWS_PATH))) {
            System.out.println("CachingWindowStore.java not found");
            return;
        }
        String src = Files.readString(Path.of(CWS_PATH));

        // Check if the injected methods still exist
        boolean hasRecordFlush = src.contains("recordFlushCompletion");
        boolean hasFetchPersisted = src.contains("fetchPersistedRange");

        if (!hasRecordFlush || !hasFetchPersisted) {
            System.out.println("Bug A fixed: injected callback chain removed");
            return;
        }

        System.out.println("Bug A present — creating ABBA deadlock scenario for Fray");

        // Lock A = CachingWindowStore monitor (forward: close → flush)
        // Lock B = ThreadCache monitor (reverse: resize → evict → recordFlushCompletion → fetch)
        final Object cachingStoreLock = new Object();
        final Object threadCacheLock = new Object();

        // Thread 1 (close path): cachingStore → threadCache
        Thread t1 = new Thread(() -> {
            synchronized (cachingStoreLock) {
                synchronized (threadCacheLock) {
                    // simulates close → flush → ThreadCache.close
                }
            }
        });

        // Thread 2 (eviction path): threadCache → cachingStore
        Thread t2 = new Thread(() -> {
            synchronized (threadCacheLock) {
                synchronized (cachingStoreLock) {
                    // simulates resize → evict → recordFlushCompletion → fetch
                }
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }
}
VERIFICATION_JAVA_A

mkdir -p /tmp/verifier-classes-a
javac -d /tmp/verifier-classes-a /tmp/verifier-src-a/org/apache/kafka/streams/state/internals/BugAVerificationTest.java > /tmp/verifier_compile_a.txt 2>&1
if [ $? -ne 0 ]; then
    mkdir -p /logs/verifier
    echo "0" > /logs/verifier/reward.txt
    echo "Bug A verification test failed to compile" >> /logs/verifier/details.txt
    cp /tmp/verifier_compile_a.txt /logs/verifier/
    exit 0
fi

fray -cp /tmp/verifier-classes-a org.apache.kafka.streams.state.internals.BugAVerificationTest -- --iter 1000 > /tmp/fray_output_a.txt 2>&1
if [ $? -eq 0 ]; then
    echo "Bug A: PASS (no deadlock)"
    BUGS_FIXED=$((BUGS_FIXED + 1))
else
    echo "Bug A: FAIL (deadlock detected)"
fi

# ---------- Bug B: KafkaStreams setState listener-under-lock ----------
mkdir -p /tmp/verifier-src-b/org/apache/kafka/streams
cat > /tmp/verifier-src-b/org/apache/kafka/streams/BugBVerificationTest.java << 'VERIFICATION_JAVA_B'
package org.apache.kafka.streams;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Verifies Bug B: stateListener.onChange() called inside synchronized(stateLock).
 * If onChange is inside the synchronized block, creates ABBA deadlock.
 */
public class BugBVerificationTest {
    private static final String KS_PATH = "/app/streams/src/main/java/org/apache/kafka/streams/KafkaStreams.java";

    public static void main(String[] args) throws Exception {
        if (!Files.exists(Path.of(KS_PATH))) {
            System.out.println("KafkaStreams.java not found");
            return;
        }
        String src = Files.readString(Path.of(KS_PATH));

        // Find setState method and check if onChange is inside synchronized(stateLock)
        int setStateStart = src.indexOf("private boolean setState(final State newState)");
        if (setStateStart < 0) {
            System.out.println("setState method not found");
            return;
        }

        String methodBody = extractMethodBody(src, setStateStart);
        if (methodBody == null) {
            System.out.println("Could not parse setState method");
            return;
        }

        // Find the synchronized(stateLock) block
        int syncStart = methodBody.indexOf("synchronized (stateLock)");
        if (syncStart < 0) {
            System.out.println("No synchronized(stateLock) block found");
            return;
        }

        // Extract the synchronized block body
        String syncBody = extractBlock(methodBody, syncStart);
        if (syncBody == null) {
            System.out.println("Could not parse synchronized block");
            return;
        }

        // Check if onChange is called inside the synchronized block
        boolean onChangeInsideLock = syncBody.contains("stateListener.onChange") ||
                                     syncBody.contains("onChange(newState, oldState)");

        if (!onChangeInsideLock) {
            System.out.println("Bug B fixed: onChange moved outside synchronized block");
            return;
        }

        System.out.println("Bug B present — creating listener-under-lock deadlock for Fray");

        // Lock A = KafkaStreams intrinsic monitor
        // Lock B = stateLock
        final Object ksLock = new Object();
        final Object stateLock = new Object();

        // Thread 1 (close path): ksLock → stateLock
        Thread t1 = new Thread(() -> {
            synchronized (ksLock) {
                synchronized (stateLock) {
                    // simulates KafkaStreams.close → setState → synchronized(stateLock)
                }
            }
        });

        // Thread 2 (callback path): stateLock → ksLock
        Thread t2 = new Thread(() -> {
            synchronized (stateLock) {
                synchronized (ksLock) {
                    // simulates setState → onChange(listener) → callback needs KafkaStreams lock
                }
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();
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

    private static String extractBlock(String src, int start) {
        int braceStart = src.indexOf('{', start);
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
VERIFICATION_JAVA_B

mkdir -p /tmp/verifier-classes-b
javac -d /tmp/verifier-classes-b /tmp/verifier-src-b/org/apache/kafka/streams/BugBVerificationTest.java > /tmp/verifier_compile_b.txt 2>&1
if [ $? -ne 0 ]; then
    mkdir -p /logs/verifier
    echo "0" > /logs/verifier/reward.txt
    echo "Bug B verification test failed to compile" >> /logs/verifier/details.txt
    cp /tmp/verifier_compile_b.txt /logs/verifier/
    exit 0
fi

fray -cp /tmp/verifier-classes-b org.apache.kafka.streams.BugBVerificationTest -- --iter 1000 > /tmp/fray_output_b.txt 2>&1
if [ $? -eq 0 ]; then
    echo "Bug B: PASS (no deadlock)"
    BUGS_FIXED=$((BUGS_FIXED + 1))
else
    echo "Bug B: FAIL (deadlock detected)"
fi

# ---------- Bug C: TopologyMetadata ↔ DefaultTaskManager observer cycle ----------
mkdir -p /tmp/verifier-src-c/org/apache/kafka/streams/processor/internals
cat > /tmp/verifier-src-c/org/apache/kafka/streams/processor/internals/BugCVerificationTest.java << 'VERIFICATION_JAVA_C'
package org.apache.kafka.streams.processor.internals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Verifies Bug C: TopologyMetadata ↔ DefaultTaskManager observer cycle.
 * Forward: topologyLock → tasksLock (via notifyLifecycleObservers → validateTopologyConsistency)
 * Reverse: tasksLock → topologyLock (via assignNextTask → isTaskTopologyStale)
 */
public class BugCVerificationTest {
    private static final String TM_PATH = "/app/streams/src/main/java/org/apache/kafka/streams/processor/internals/TopologyMetadata.java";
    private static final String DTM_PATH = "/app/streams/src/main/java/org/apache/kafka/streams/processor/internals/tasks/DefaultTaskManager.java";

    public static void main(String[] args) throws Exception {
        // Check forward edge: TopologyMetadata has observer pattern
        if (!Files.exists(Path.of(TM_PATH))) {
            System.out.println("TopologyMetadata.java not found");
            return;
        }
        String tmSrc = Files.readString(Path.of(TM_PATH));

        boolean hasObserverInterface = tmSrc.contains("interface TopologyLifecycleObserver");
        boolean hasNotify = tmSrc.contains("notifyLifecycleObservers()");

        if (!hasObserverInterface || !hasNotify) {
            System.out.println("Bug C fixed: observer pattern removed from TopologyMetadata");
            return;
        }

        // Check reverse edge: DefaultTaskManager has TaskTopologyObserver
        if (!Files.exists(Path.of(DTM_PATH))) {
            System.out.println("DefaultTaskManager.java not found");
            return;
        }
        String dtmSrc = Files.readString(Path.of(DTM_PATH));

        boolean hasTaskObserver = dtmSrc.contains("TaskTopologyObserver");
        boolean hasValidate = dtmSrc.contains("validateTopologyConsistency");

        if (!hasTaskObserver || !hasValidate) {
            System.out.println("Bug C fixed: observer removed from DefaultTaskManager");
            return;
        }

        System.out.println("Bug C present — creating observer cycle deadlock for Fray");

        // Lock A = topologyLock (ReentrantLock)
        // Lock B = tasksLock (ReentrantLock)
        final ReentrantLock topologyLock = new ReentrantLock();
        final ReentrantLock tasksLock = new ReentrantLock();

        // Thread 1 (register topology): topologyLock → tasksLock
        Thread t1 = new Thread(() -> {
            topologyLock.lock();
            try {
                tasksLock.lock();
                try {
                    // simulates registerAndBuildNewTopology → notifyLifecycleObservers → validateTopologyConsistency
                } finally {
                    tasksLock.unlock();
                }
            } finally {
                topologyLock.unlock();
            }
        });

        // Thread 2 (assign task): tasksLock → topologyLock
        Thread t2 = new Thread(() -> {
            tasksLock.lock();
            try {
                topologyLock.lock();
                try {
                    // simulates assignNextTask → isTaskTopologyStale → acquires topologyLock
                } finally {
                    topologyLock.unlock();
                }
            } finally {
                tasksLock.unlock();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }
}
VERIFICATION_JAVA_C

mkdir -p /tmp/verifier-classes-c
javac -d /tmp/verifier-classes-c /tmp/verifier-src-c/org/apache/kafka/streams/processor/internals/BugCVerificationTest.java > /tmp/verifier_compile_c.txt 2>&1
if [ $? -ne 0 ]; then
    mkdir -p /logs/verifier
    echo "0" > /logs/verifier/reward.txt
    echo "Bug C verification test failed to compile" >> /logs/verifier/details.txt
    cp /tmp/verifier_compile_c.txt /logs/verifier/
    exit 0
fi

fray -cp /tmp/verifier-classes-c org.apache.kafka.streams.processor.internals.BugCVerificationTest -- --iter 1000 > /tmp/fray_output_c.txt 2>&1
if [ $? -eq 0 ]; then
    echo "Bug C: PASS (no deadlock)"
    BUGS_FIXED=$((BUGS_FIXED + 1))
else
    echo "Bug C: FAIL (deadlock detected)"
fi

# ---------- Bug D: DefaultStateUpdater missed signal ----------
mkdir -p /tmp/verifier-src-d/org/apache/kafka/streams/processor/internals
cat > /tmp/verifier-src-d/org/apache/kafka/streams/processor/internals/BugDVerificationTest.java << 'VERIFICATION_JAVA_D'
package org.apache.kafka.streams.processor.internals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Verifies Bug D: missed signal in DefaultStateUpdater.
 * requeueTaskForRestoration signals tasksAndActionsCondition but NOT
 * restoredActiveTasksCondition. A waiter on the latter will hang.
 *
 * We check:
 * 1. requeueTaskForRestoration method exists
 * 2. It does NOT contain restoredActiveTasksCondition.signalAll()
 * 3. The topology version check exists (topologyVersion() > restorationStartTopologyVersion)
 *
 * If all conditions hold, the missed signal bug is present.
 */
public class BugDVerificationTest {
    private static final String DSU_PATH = "/app/streams/src/main/java/org/apache/kafka/streams/processor/internals/DefaultStateUpdater.java";

    public static void main(String[] args) throws Exception {
        if (!Files.exists(Path.of(DSU_PATH))) {
            System.out.println("DefaultStateUpdater.java not found");
            return;
        }
        String src = Files.readString(Path.of(DSU_PATH));

        // Check if the topology version check exists
        boolean hasVersionCheck = src.contains("topologyVersion() > restorationStartTopologyVersion");

        // Check if requeueTaskForRestoration method exists
        boolean hasRequeue = src.contains("requeueTaskForRestoration");

        if (!hasVersionCheck || !hasRequeue) {
            System.out.println("Bug D fixed: topology version check or requeue removed");
            return;
        }

        // Check if requeueTaskForRestoration signals the right condition
        int requeueStart = src.indexOf("void requeueTaskForRestoration");
        if (requeueStart < 0) {
            System.out.println("Bug D fixed: requeueTaskForRestoration method removed");
            return;
        }

        String requeueBody = extractMethodBody(src, requeueStart);
        if (requeueBody != null && requeueBody.contains("restoredActiveTasksCondition.signalAll()")) {
            System.out.println("Bug D fixed: restoredActiveTasksCondition is now signaled");
            return;
        }

        System.out.println("Bug D present — creating missed signal scenario for Fray");

        // Simulate: waiter awaits on conditionA, signaler only signals conditionB
        final ReentrantLock lock = new ReentrantLock();
        final Condition restoredCondition = lock.newCondition();
        final Condition tasksCondition = lock.newCondition();
        final AtomicBoolean taskRestored = new AtomicBoolean(false);

        // Thread 1 (main thread): waits on restoredCondition for task completion
        Thread waiter = new Thread(() -> {
            lock.lock();
            try {
                while (!taskRestored.get()) {
                    restoredCondition.await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });

        // Thread 2 (state updater): re-queues task, signals WRONG condition
        Thread signaler = new Thread(() -> {
            lock.lock();
            try {
                // Bug: signals tasksCondition instead of restoredCondition
                taskRestored.set(true);
                tasksCondition.signalAll();
                // Missing: restoredCondition.signalAll();
            } finally {
                lock.unlock();
            }
        });

        waiter.start();
        signaler.start();
        waiter.join();
        signaler.join();
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
VERIFICATION_JAVA_D

mkdir -p /tmp/verifier-classes-d
javac -d /tmp/verifier-classes-d /tmp/verifier-src-d/org/apache/kafka/streams/processor/internals/BugDVerificationTest.java > /tmp/verifier_compile_d.txt 2>&1
if [ $? -ne 0 ]; then
    mkdir -p /logs/verifier
    echo "0" > /logs/verifier/reward.txt
    echo "Bug D verification test failed to compile" >> /logs/verifier/details.txt
    cp /tmp/verifier_compile_d.txt /logs/verifier/
    exit 0
fi

fray -cp /tmp/verifier-classes-d org.apache.kafka.streams.processor.internals.BugDVerificationTest -- --iter 1000 > /tmp/fray_output_d.txt 2>&1
if [ $? -eq 0 ]; then
    echo "Bug D: PASS (no hang)"
    BUGS_FIXED=$((BUGS_FIXED + 1))
else
    echo "Bug D: FAIL (hang detected)"
fi

# === Final reward ===
mkdir -p /logs/verifier
cp /tmp/fray_output_*.txt /logs/verifier/ 2>/dev/null

if [ $BUGS_FIXED -eq $BUGS_TOTAL ]; then
    echo "All $BUGS_TOTAL bugs fixed!"
    echo "1" > /logs/verifier/reward.txt
else
    echo "Only $BUGS_FIXED/$BUGS_TOTAL bugs fixed"
    echo "0" > /logs/verifier/reward.txt
fi
