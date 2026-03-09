# Mixed Deadlock Injection — Kafka Streams

## Overview

Four concurrency bugs injected simultaneously into a clean Kafka Streams module.
The difficulty comes from multiple potential deadlock cycles, different activation
windows, and one bug that is a missed signal (not a lock cycle).

## Bug A: State Store ABBA Cycle (CachingWindowStore ↔ ThreadCache)

**Pattern:** DERBY-5560 — wrapper/physical resource close cycle
**File:** `streams/.../state/internals/CachingWindowStore.java`

**Forward edge:** `CachingWindowStore.close()` [synchronized] → `cache.flush()` → `ThreadCache.close()` [synchronized]
**Reverse edge:** `ThreadCache.resize()` [synchronized] → evict → flush listener → `putAndMaybeForward()` → `recordFlushCompletion()` → `verifyPersistedWindow()` → `fetchPersistedRange()` → `fetch(key, ts, ts)` [synchronized on CachingWindowStore]

**Changes:** Added 3 private methods (`recordFlushCompletion`, `verifyPersistedWindow`, `fetchPersistedRange`) and 1 call site in `putAndMaybeForward()`.

**Fix:** Remove the `recordFlushCompletion` call (or make it not call back into `this.fetch()`).

## Bug B: Listener-Under-Lock (KafkaStreams setState)

**Pattern:** Moving an intentionally-external callback inside a synchronized block
**File:** `streams/.../KafkaStreams.java`

**Change:** Moved `stateListener.onChange(newState, oldState)` inside `synchronized(stateLock)` block. Deleted the safety comment that explained why it was outside.

**Forward:** `KafkaStreams.close()` [synchronized(this)] → thread shutdown → `StreamThread.setState(PENDING_SHUTDOWN)` → `synchronized(stateLock)`
**Reverse:** `StreamThread.setState()` → `synchronized(stateLock)` → listener → KafkaStreams callback → needs `synchronized(this)`

**Fix:** Move `stateListener.onChange()` back outside the `synchronized(stateLock)` block.

## Bug C: Observer Callback Cycle (TopologyMetadata ↔ DefaultTaskManager)

**Pattern:** DERBY-5447 — observer registration creates bidirectional lock dependency
**Files:**
- `streams/.../processor/internals/TopologyMetadata.java`
- `streams/.../processor/internals/tasks/DefaultTaskManager.java`
- `streams/.../processor/internals/StreamThread.java` (wiring)

**Forward (topologyLock → tasksLock):** `TopologyMetadata.registerAndBuildNewTopology()` [holds topologyLock] → `notifyLifecycleObservers()` → `DefaultTaskManager.TaskTopologyObserver.onTopologyChange()` → `validateTopologyConsistency()` → `executeWithTasksLocked()` [acquires tasksLock]

**Reverse (tasksLock → topologyLock):** `DefaultTaskManager.assignNextTask()` [holds tasksLock] → `topologyMetadata.isTaskTopologyStale(task.id())` → acquires topologyLock

**Fix:** Either remove the observer pattern, or don't call `isTaskTopologyStale()` under `tasksLock`, or don't notify observers under `topologyLock`.

## Bug D: Missed Condition Signal (DefaultStateUpdater)

**Pattern:** POOL-149 — condition signal lost on alternate code path
**File:** `streams/.../processor/internals/DefaultStateUpdater.java`

**Mechanism:** In `maybeCompleteRestoration()`, if topology version changed during restoration, task is re-queued via `requeueTaskForRestoration()`. This method signals `tasksAndActionsCondition` (work queue) but does NOT signal `restoredActiveTasksCondition`. The main thread waiting in `drainRestoredActiveTasks()` on `restoredActiveTasksCondition` will hang if all tasks get re-queued.

**Fix:** Add `restoredActiveTasksCondition.signalAll()` in `requeueTaskForRestoration()`, or remove the topology version check entirely.

## Interactions

- Bugs B and C both involve KafkaStreams state transitions — fixing B by restructuring state listeners may mask C's activation window
- Bug C adds a TopologyMetadata observer; Bug D adds a topology version check in DefaultStateUpdater — both touch topology versioning, making them seem related when they're independent
- Bug A is fully isolated in the state store layer (no interaction, acts as constant noise)

## Verification

- Bugs A, B, C: Lock-cycle deadlocks detectable by Fray's shadow locking
- Bug D: Missed signal → Fray explores the schedule where topology version changes mid-restoration, causing `drainRestoredActiveTasks()` to hang (detected as timeout)
