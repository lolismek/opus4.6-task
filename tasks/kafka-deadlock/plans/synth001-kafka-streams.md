# SYNTH-001 Deadlock Injection Plan: Kafka Streams

## Context

Inject a 3-Node Conditional Callback Cycle (SYNTH-001) deadlock into the Kafka Streams module. The deadlock uses 3 threads, 3 locks, 3 classes forming a cycle A→B→C→A. The closing edge C→A is conditional on `REBALANCING` state, making the deadlock intermittent. Mixed lock primitives make it hard for static analysis to detect.

## 3-Node Cycle Summary

```
Thread 1 (Admin/Telemetry):   Lock A (KafkaStreams.this)     → waits for Lock B (RRWL.writeLock)
Thread 2 (StateUpdater):      Lock B (RRWL.writeLock)        → waits for Lock C (topologyLock)
Thread 3 (Topology Update):   Lock C (topologyLock)          → waits for Lock A (KafkaStreams.this)  [CONDITIONAL: only during REBALANCING]
```

| Node | Class | Lock Type | Lock Field |
|------|-------|-----------|------------|
| A | `KafkaStreams` | `synchronized(this)` (intrinsic monitor) | EXISTING |
| B | `DefaultStateUpdater` (via `AbstractStateUpdater`) | `ReentrantReadWriteLock` | NEW (hidden in superclass) |
| C | `TopologyMetadata` | `ReentrantLock` + `Condition` | EXISTING (`topologyLock` in `TopologyVersion`) |

## Files to Create/Modify

### 1. NEW: `AbstractStateUpdater.java`
**Path**: `streams/src/main/java/org/apache/kafka/streams/processor/internals/AbstractStateUpdater.java`

Abstract superclass for DefaultStateUpdater. Introduces a `ReentrantReadWriteLock` for "caching restoration task snapshots" (plausible read-heavy optimization for monitoring).

```java
package org.apache.kafka.streams.processor.internals;

import org.apache.kafka.streams.processor.TaskId;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

abstract class AbstractStateUpdater implements StateUpdater {
    private final ReadWriteLock snapshotLock = new ReentrantReadWriteLock();
    private volatile Set<TaskId> restoringTaskSnapshot = Collections.emptySet();
    private final AtomicLong snapshotVersion = new AtomicLong(0L);

    protected void acquireSnapshotShared() {          // RED HERRING: read lock doesn't deadlock
        snapshotLock.readLock().lock();
    }
    protected void releaseSnapshotShared() {
        snapshotLock.readLock().unlock();
    }
    protected void acquireSnapshotExclusive() {       // THE DEADLOCK LOCK
        snapshotLock.writeLock().lock();
    }
    protected void releaseSnapshotExclusive() {
        snapshotLock.writeLock().unlock();
    }
    protected Set<TaskId> cachedRestoringTasks() {
        return restoringTaskSnapshot;
    }
    protected void updateRestorationSnapshot(final Set<TaskId> taskIds) {
        restoringTaskSnapshot = taskIds;
        snapshotVersion.incrementAndGet();
    }
    protected long snapshotVersion() {
        return snapshotVersion.get();
    }
}
```

**Why it looks natural**: Monitoring and admin APIs frequently query restoration state. Caching this behind a read-write lock avoids contention with the hot restoration path.

### 2. MODIFY: `DefaultStateUpdater.java`
**Path**: `streams/src/main/java/org/apache/kafka/streams/processor/internals/DefaultStateUpdater.java`

**Change A** — Class declaration (line 77):
```java
// FROM:
public class DefaultStateUpdater implements StateUpdater {
// TO:
public class DefaultStateUpdater extends AbstractStateUpdater {
```

**Change B** — Add `invalidateRestorationSnapshot()` public method (after `shutdown()`, ~line 883):
```java
public void invalidateRestorationSnapshot() {
    acquireSnapshotExclusive();
    try {
        if (stateUpdaterThread != null) {
            final Set<TaskId> currentTasks = stateUpdaterThread.updatingTasks.keySet()
                .stream()
                .collect(Collectors.toSet());
            updateRestorationSnapshot(currentTasks);
        } else {
            updateRestorationSnapshot(Collections.emptySet());
        }
    } finally {
        releaseSnapshotExclusive();
    }
}
```

**Change C** — Add `maybeRefreshRestorationSnapshot()` to `StateUpdaterThread` inner class, called from `runOnce()` after `restoreTasks()`:

In `runOnce()` add call: `maybeRefreshRestorationSnapshot();` after `restoreTasks(totalStartTimeMs);`

New private method in StateUpdaterThread:
```java
private void maybeRefreshRestorationSnapshot() {
    acquireSnapshotExclusive();   // Lock B acquired (via DefaultStateUpdater.this → AbstractStateUpdater)
    try {
        final Set<TaskId> activeRestoringIds = updatingTasks.keySet()
            .stream()
            .collect(Collectors.toSet());
        updateRestorationSnapshot(activeRestoringIds);
        topologyMetadata.maybeNotifyTopologyVersionListeners();  // → acquires Lock C (topologyLock)
    } finally {
        releaseSnapshotExclusive();
    }
}
```

**Edge B→C**: `acquireSnapshotExclusive()` [Lock B] → `topologyMetadata.maybeNotifyTopologyVersionListeners()` → `lock()` [Lock C]

### 3. MODIFY: `TopologyMetadata.java`
**Path**: `streams/src/main/java/org/apache/kafka/streams/processor/internals/TopologyMetadata.java`

**Change A** — Add `TopologyLifecycleObserver` interface (after `TopologyVersionListener` class, ~line 100):
```java
public interface TopologyLifecycleObserver {
    void onTopologyChange(long newVersion, Set<String> activeTopologies);
}
```

**Change B** — Add field (after `threadVersions`, ~line 83):
```java
private final List<TopologyLifecycleObserver> lifecycleObservers = new ArrayList<>();
```

**Change C** — Add `registerLifecycleObserver()` method (after `unregisterThread()`, ~line 170):
```java
public void registerLifecycleObserver(final TopologyLifecycleObserver observer) {
    lock();
    try {
        lifecycleObservers.add(observer);
    } finally {
        unlock();
    }
}
```

**Change D** — Add `notifyLifecycleObservers()` private method:
```java
private void notifyLifecycleObservers() {
    final long currentVersion = version.topologyVersion.get();
    final Set<String> activeTopologies = namedTopologiesView();
    for (final TopologyLifecycleObserver observer : lifecycleObservers) {
        try {
            observer.onTopologyChange(currentVersion, activeTopologies);
        } catch (final Exception e) {
            log.warn("Topology lifecycle observer threw exception", e);
        }
    }
}
```

**Change E** — Call `notifyLifecycleObservers()` inside `registerAndBuildNewTopology()` (after `wakeupThreads()`):
```java
wakeupThreads();
notifyLifecycleObservers();  // ← NEW: fires under topologyLock
```

**Change F** — Call `notifyLifecycleObservers()` inside `unregisterTopology()` (after the log.info):
```java
log.info("Finished removing NamedTopology ...");
notifyLifecycleObservers();  // ← NEW: fires under topologyLock
```

**Edge C→A**: `registerAndBuildNewTopology()` → `lock()` [Lock C] → `notifyLifecycleObservers()` → observer → `KafkaStreams.refreshRestorationState()` → `synchronized(this)` [Lock A]

### 4. MODIFY: `StreamThread.java`
**Path**: `streams/src/main/java/org/apache/kafka/streams/processor/internals/StreamThread.java`

**Change A** — Add public method (near `resizeCache()`):
```java
public void invalidateRestorationSnapshot() {
    if (stateUpdater instanceof DefaultStateUpdater) {
        ((DefaultStateUpdater) stateUpdater).invalidateRestorationSnapshot();
    }
}
```

This bridges KafkaStreams → StreamThread → DefaultStateUpdater, adding call depth.

### 5. MODIFY: `KafkaStreams.java`
**Path**: `streams/src/main/java/org/apache/kafka/streams/KafkaStreams.java`

**Change A** — Add `TopologyChangeHandler` inner class (after `StreamStateListener`, ~line 681):
```java
private final class TopologyChangeHandler implements TopologyMetadata.TopologyLifecycleObserver {
    @Override
    public void onTopologyChange(final long newVersion, final Set<String> activeTopologies) {
        if (state == State.REBALANCING) {    // ← CONDITIONAL: only during rebalancing
            refreshRestorationState();
        }
    }
}
```

**Change B** — Add `refreshRestorationState()` private synchronized method:
```java
private synchronized void refreshRestorationState() {
    processStreamThread(StreamThread::invalidateRestorationSnapshot);
}
```

This is `synchronized` on `this` (Lock A), then calls into StreamThread → DefaultStateUpdater → `acquireSnapshotExclusive()` (Lock B).

**Change C** — Register observer in constructor (after `topologyMetadata.setLog(logContext)`, line 968):
```java
topologyMetadata.registerLifecycleObserver(new TopologyChangeHandler());
```

**Change D** — Add snapshot invalidation in `clientInstanceIds()` (after the `synchronized(changeThreadCount)` block, ~line 1872, still inside the `synchronized(this)` method):
```java
// Refresh restoration snapshots for consistent telemetry
processStreamThread(StreamThread::invalidateRestorationSnapshot);
```

**Edge A→B**: `clientInstanceIds()` → `synchronized(this)` [Lock A] → `processStreamThread(...)` → `StreamThread.invalidateRestorationSnapshot()` → `DefaultStateUpdater.invalidateRestorationSnapshot()` → `acquireSnapshotExclusive()` [Lock B]

### 6. NEW: Verification Test
**Path**: `streams/src/test/java/org/apache/kafka/streams/processor/internals/Synth001DeadlockVerificationTest.java`

Directly exercises the 3-lock cycle with minimal mocking using 3 threads, each holding one lock and trying the next. Detection via `ThreadMXBean.findDeadlockedThreads()` polled every 5ms.

## Full Cycle Trace (9 call hops)

```
Thread 1: KafkaStreams.clientInstanceIds()              [synchronized(this) = Lock A]
  → processStreamThread(StreamThread::invalidateRestorationSnapshot)
    → StreamThread.invalidateRestorationSnapshot()
      → DefaultStateUpdater.invalidateRestorationSnapshot()
        → AbstractStateUpdater.acquireSnapshotExclusive()    [TRIES Lock B] ← BLOCKED

Thread 2: StateUpdaterThread.runOnce()
  → maybeRefreshRestorationSnapshot()
    → AbstractStateUpdater.acquireSnapshotExclusive()        [Lock B acquired]
      → topologyMetadata.maybeNotifyTopologyVersionListeners()
        → TopologyMetadata.lock()                            [TRIES Lock C] ← BLOCKED

Thread 3: TopologyMetadata.registerAndBuildNewTopology()
  → TopologyMetadata.lock()                                  [Lock C acquired]
    → notifyLifecycleObservers()
      → TopologyChangeHandler.onTopologyChange()
        → if (state == REBALANCING)                          [CONDITIONAL CHECK]
          → KafkaStreams.refreshRestorationState()
            → synchronized(this)                             [TRIES Lock A] ← BLOCKED
```

## Why It's Hard to Diagnose

1. **Mixed primitives**: `synchronized` + `ReentrantReadWriteLock` + `ReentrantLock` — no single grep pattern finds all
2. **Hidden lock**: RRWL is in `AbstractStateUpdater` (superclass), access is via `protected` methods — 2 levels deep
3. **Red herrings**: DefaultStateUpdater has 3 other ReentrantLocks that look relevant but aren't in the cycle
4. **Red herring 2**: `acquireSnapshotShared()` (read lock) exists but doesn't deadlock — only `acquireSnapshotExclusive()` does
5. **Conditional edge**: C→A only fires when `state == REBALANCING` — requires understanding Kafka Streams lifecycle
6. **Causal distance**: 9 call hops across 5 files to trace the full cycle
7. **Non-local fix**: Breaking the cycle requires redesigning either the observer notification (don't call under topologyLock), the snapshot update (don't call `maybeNotifyTopologyVersionListeners` under RRWL), or the admin method (don't call `invalidateRestorationSnapshot` under `synchronized`)

## Verification

```bash
cd tasks/kafka-deadlock/synth001-graph/kafka
# Build streams module
./gradlew :streams:compileJava :streams:compileTestJava
# Run the verification test
./gradlew :streams:test --tests "org.apache.kafka.streams.processor.internals.Synth001DeadlockVerificationTest"
```

The test should detect the deadlock via `ThreadMXBean.findDeadlockedThreads()` within seconds.
