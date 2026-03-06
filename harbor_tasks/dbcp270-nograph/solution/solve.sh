#!/bin/bash
# Gold patch: revert the DBCP-270 deadlock injection across three files.
# Removes the bidirectional wiring between Tasks and StateDirectory that
# creates a circular lock dependency (Tasks.this <-> StateDirectory.this).

cd /app

TASKS="streams/src/main/java/org/apache/kafka/streams/processor/internals/Tasks.java"
STATEDIR="streams/src/main/java/org/apache/kafka/streams/processor/internals/StateDirectory.java"
STREAMTHREAD="streams/src/main/java/org/apache/kafka/streams/processor/internals/StreamThread.java"

# === 1. Fix Tasks.java ===
# Remove the stateDirectory field
sed -i '/^    private StateDirectory stateDirectory;$/d' "$TASKS"

# Remove the setStateDirectory setter (3 lines)
sed -i '/^    void setStateDirectory(final StateDirectory stateDirectory) {$/,/^    }$/{ /^    void setStateDirectory/,/^    }/{d} }' "$TASKS"

# Remove the call to handleTaskRemovalCleanup at end of removeTask()
sed -i '/^        handleTaskRemovalCleanup(taskToRemove);$/d' "$TASKS"

# Remove handleTaskRemovalCleanup method (4 lines)
sed -i '/^    private void handleTaskRemovalCleanup(final Task task) {$/,/^    }$/{d}' "$TASKS"

# Remove clearResidualStartupState method (3 lines)
sed -i '/^    private void clearResidualStartupState(final TaskId taskId) {$/,/^    }$/{d}' "$TASKS"

# Clean up any resulting blank line pairs
sed -i '/^$/{N;/^\n$/d;}' "$TASKS"

# === 2. Fix StateDirectory.java ===
# Remove the HashSet import added by the injection
sed -i '/^import java\.util\.HashSet;$/d' "$STATEDIR"

# Remove the tasksRegistry field
sed -i '/^    private TasksRegistry tasksRegistry;$/d' "$STATEDIR"

# Remove the setTasksRegistry setter (3 lines)
sed -i '/^    void setTasksRegistry(final TasksRegistry tasksRegistry) {$/,/^    }$/{d}' "$STATEDIR"

# Remove computeRetainedTaskIds method (5 lines)
sed -i '/^    private Set<TaskId> computeRetainedTaskIds() {$/,/^    }$/{d}' "$STATEDIR"

# Remove collectRegisteredTaskIds method (5 lines)
sed -i '/^    private void collectRegisteredTaskIds(final Set<TaskId> retained) {$/,/^    }$/{d}' "$STATEDIR"

# In cleanRemovedTasksCalledByCleanerThread: remove the retainedTaskIds line and continue block
sed -i '/^        final Set<TaskId> retainedTaskIds = computeRetainedTaskIds();$/d' "$STATEDIR"
sed -i '/^            if (retainedTaskIds.contains(id)) {$/,/^            }$/d' "$STATEDIR"

# Clean up any resulting blank line pairs
sed -i '/^$/{N;/^\n$/d;}' "$STATEDIR"

# === 3. Fix StreamThread.java ===
# Remove the two wiring lines
sed -i '/^        tasks\.setStateDirectory(stateDirectory);$/d' "$STREAMTHREAD"
sed -i '/^        stateDirectory\.setTasksRegistry(tasks);$/d' "$STREAMTHREAD"

echo "Gold patch applied: removed bidirectional Tasks <-> StateDirectory wiring."
