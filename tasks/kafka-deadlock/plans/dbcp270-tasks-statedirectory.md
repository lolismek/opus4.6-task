# Plan: Inject DBCP-270 Deadlock into Kafka Streams

## Context

We need to inject a Two-Object ABBA Cycle deadlock (DBCP-270 pattern) into the Kafka Streams module. The pattern: two threads acquire two locks in opposite orders. The injected bug must look like a plausible feature, be buried 2-3 call layers deep, and be genuinely hard for a coding agent to diagnose.

## Target Pair: `Tasks` ↔ `StateDirectory`

Both classes use `synchronized` on `this` (intrinsic monitor) and operate on the same task lifecycle domain:

- **Tasks.java** — 14 synchronized methods, manages the task registry (active/standby tasks)
- **StateDirectory.java** — 5 synchronized methods, manages task state directories and file locks

Neither currently references the other. Both are used by `TaskManager` and `StreamThread`.

## Lock Cycle Design

```
Lock A = Tasks.this          (intrinsic monitor of the Tasks instance)
Lock B = StateDirectory.this (intrinsic monitor of the StateDirectory instance)

Thread 1 (task lifecycle — removing a task):
  Tasks.removeTask()                     → acquires Lock A (Tasks.this)
    → handleTaskRemovalCleanup()
      → clearResidualStartupState()
        → stateDirectory.removeStartupState()  → tries Lock B (StateDirectory.this) ← BLOCKED

Thread 2 (directory cleanup — periodic cleaner):
  StateDirectory.cleanRemovedTasks()     → acquires Lock B (StateDirectory.this)
    → cleanRemovedTasksCalledByCleanerThread()
      → computeRetainedTaskIds()
        → collectRegisteredTaskIds()
          → tasksRegistry.allInitializedTaskIds()  → tries Lock A (Tasks.this) ← BLOCKED
```

**DEADLOCK**: Thread 1 holds Tasks.this, waits for StateDirectory.this. Thread 2 holds StateDirectory.this, waits for Tasks.this.

## Plausible Feature Framing

- **Edge 1** (Tasks → StateDirectory): "When removing a task from the registry, proactively clear any residual startup state from the state directory to prevent stale state leaks." This is a reasonable lifecycle cleanup.
- **Edge 2** (StateDirectory → Tasks): "Before cleaning removed task directories, check which tasks are still initialized in the registry to avoid accidentally deleting state for active tasks." This is a reasonable safety guard.

## Files to Modify

### 1. `Tasks.java` — Add Edge 1 (Tasks.this → StateDirectory.this)
**Path**: `streams/src/main/java/org/apache/kafka/streams/processor/internals/Tasks.java`

Add:
- Field: `private StateDirectory stateDirectory;` (nullable)
- Setter: `void setStateDirectory(final StateDirectory stateDirectory)`
- In `removeTask()` (line 238, already synchronized): append call to `handleTaskRemovalCleanup(taskToRemove)` after existing logic
- New private method `handleTaskRemovalCleanup(Task task)` — checks `stateDirectory != null && task.isActive()`, calls `clearResidualStartupState(task.id())`
- New private method `clearResidualStartupState(TaskId taskId)` — calls `stateDirectory.removeStartupState(taskId)`

**Lock depth**: `removeTask()` [sync] → `handleTaskRemovalCleanup()` → `clearResidualStartupState()` → `removeStartupState()` [sync] = **3 layers**

### 2. `StateDirectory.java` — Add Edge 2 (StateDirectory.this → Tasks.this)
**Path**: `streams/src/main/java/org/apache/kafka/streams/processor/internals/StateDirectory.java`

Add:
- Field: `private TasksRegistry tasksRegistry;` (nullable)
- Setter: `void setTasksRegistry(final TasksRegistry tasksRegistry)`
- New private method `computeRetainedTaskIds()` — creates a set from `lockedTasksToOwner.keySet()` (red herring: same data as existing check) then calls `collectRegisteredTaskIds(retained)`
- New private method `collectRegisteredTaskIds(Set<TaskId> retained)` — if `tasksRegistry != null`, adds `tasksRegistry.allInitializedTaskIds()` to the set
- In `cleanRemovedTasksCalledByCleanerThread()` (line 617, called while holding StateDirectory.this): at the top, call `computeRetainedTaskIds()` and use result to skip task dirs that are still registered

**Lock depth**: `cleanRemovedTasks()` [sync] → `cleanRemovedTasksCalledByCleanerThread()` → `computeRetainedTaskIds()` → `collectRegisteredTaskIds()` → `allInitializedTaskIds()` [sync] = **4 layers**

### 3. `StreamThread.java` — Wire the cross-references
**Path**: `streams/src/main/java/org/apache/kafka/streams/processor/internals/StreamThread.java`

After line 466 (`final Tasks tasks = new Tasks(logContext);`), add:
```java
tasks.setStateDirectory(stateDirectory);
stateDirectory.setTasksRegistry(tasks);
```

### 4. Verification Test (new file)
**Path**: `streams/src/test/java/org/apache/kafka/streams/processor/internals/TaskStateDirectoryDeadlockTest.java`

Test design:
- Create `Tasks` and `StateDirectory` instances, wire cross-references
- Create a mock `StreamTask` (Mockito) with state=CLOSED, isActive()=true
- **Thread 1**: Loop `tasks.addActiveTask(mockTask)` then `tasks.removeTask(mockTask)` (triggers Edge 1)
- **Thread 2**: Loop `stateDirectory.cleanRemovedTasks(0)` (triggers Edge 2)
- **Detector thread**: Poll `ThreadMXBean.findDeadlockedThreads()` every 5ms
- Assert deadlock is detected within 30 seconds
- All threads are daemon to prevent test hangs

## Difficulty / Red Herring Analysis

**Red herrings for an investigating agent:**
- Tasks has 14 synchronized methods; only `removeTask()` is in the cycle
- StateDirectory has 5 synchronized methods; only `cleanRemovedTasks()` entry point is in the cycle
- StateDirectory also uses `taskDirCreationLock` (separate Object lock) — looks suspicious but is unrelated
- `computeRetainedTaskIds()` collects IDs from `lockedTasksToOwner` AND `tasksRegistry` — the `lockedTasksToOwner` source looks like the "real" data, masking the synchronized call
- `cleanRemovedTasksCalledByCleanerThread()` already calls `lock()`, `removeStartupState()`, `unlock()` internally — all reentrant on StateDirectory.this, not cross-object. Distracting.

**Causal distance:**
- Edge 1 crosses 3 method boundaries before the lock acquisition
- Edge 2 crosses 4 method boundaries before the lock acquisition
- The two edges are in different files

**Global state reasoning required:**
- Understanding Edge 2 requires knowing that `tasksRegistry` was set (wired in StreamThread.create) and that `allInitializedTaskIds()` is synchronized
- Understanding Edge 1 requires knowing that `stateDirectory` was set and that `removeStartupState()` is synchronized
- Both require tracing through helper methods with innocuous names

## Verification

```bash
cd tasks/kafka-deadlock/dbcp270-nograph/kafka
./gradlew :streams:test --tests "org.apache.kafka.streams.processor.internals.TaskStateDirectoryDeadlockTest" --no-daemon
```

The test should reliably detect the deadlock via `ThreadMXBean.findDeadlockedThreads()`.
