# Deadlock Injection Notes

## Pattern Used

DBCP-270 (Two-Object ABBA Cycle): "hold lock on resource manager, call into individual resource" vs "hold lock on individual resource, call back into manager."

Adapted as: **Leader** (manager of learner/observer connections) vs **LearnerHandler** (individual observer connection handler).

## Files Modified

1. **`zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/LearnerMaster.java`**
   - Added `getProposalLag(long followerZxid)` helper method that calls `getLastProposed()`.
   - Purpose: Adds an indirection layer so the lock acquisition on `Leader.this` is buried 2 call layers deep from the trigger site.

2. **`zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/LearnerHandler.java`**
   - Modified `getLearnerHandlerInfo()` (already `synchronized(this)`) to include a `proposal_lag` field by calling `learnerMaster.getProposalLag(getLastZxid())`.
   - This creates the lock ordering edge: **LearnerHandler.this → Leader.this** (through `getProposalLag` → `getLastProposed`, which is `synchronized` on the Leader instance).

3. **`zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/Leader.java`**
   - Modified `sendObserverPacket()` to check observer lag by calling `f.getLastZxid()` (which is `synchronized` on the LearnerHandler instance).
   - `sendObserverPacket()` is called from `inform()` and `informAndActivate()`, both called from `tryToCommit()` which is `synchronized(this)` on the Leader.
   - This creates the lock ordering edge: **Leader.this → LearnerHandler.this** (through `tryToCommit` → `inform` → `sendObserverPacket` → `f.getLastZxid()`).

## Lock Cycle

```
Lock A = Leader.this  (the Leader instance's intrinsic monitor)
Lock B = LearnerHandler.this  (an observer's LearnerHandler instance monitor)

Thread 1 (commit processing):
  Leader.tryToCommit()          → acquires Leader.this
    → inform()
      → sendObserverPacket()
        → f.getLastZxid()       → tries to acquire LearnerHandler.this  ← BLOCKED

Thread 2 (admin query / four-letter-word):
  Leader.getObservingLearnersInfo()  → acquires observingLearners lock
    → lh.getLearnerHandlerInfo()     → acquires LearnerHandler.this
      → learnerMaster.getProposalLag()
        → getLastProposed()          → tries to acquire Leader.this  ← BLOCKED
```

## Triggering Interleaving

The deadlock requires this specific interleaving:

1. **Thread 2** (admin query) enters `getObservingLearnersInfo()`, acquires `observingLearners` lock, then starts iterating observers.
2. **Thread 2** calls `lh.getLearnerHandlerInfo()` on an observer handler, acquires **LearnerHandler.this**.
3. **Thread 1** (commit processing) enters `tryToCommit()`, acquires **Leader.this**.
4. **Thread 1** proceeds to `inform()` → `sendObserverPacket()` → calls `f.getLastZxid()` on the *same* observer handler → **BLOCKED** (LearnerHandler.this held by Thread 2).
5. **Thread 2** inside `getLearnerHandlerInfo()` calls `learnerMaster.getProposalLag()` → `getLastProposed()` → **BLOCKED** (Leader.this held by Thread 1).

**DEADLOCK**: Thread 1 holds Leader.this, waits for LearnerHandler.this. Thread 2 holds LearnerHandler.this, waits for Leader.this.

## Why It's Non-Deterministic

- The deadlock only manifests when an admin query (e.g., `mntr`, `stat`, or JMX) overlaps with a transaction commit that involves observer notification.
- Both operations are fast, so the timing window is narrow.
- The deadlock does NOT occur during:
  - Normal proposal processing (only forwarding followers, no observer packet path)
  - Admin queries when no commits are in progress
  - Commits when no admin queries are running
- Observer handlers must be present (the cluster must have observers configured).

## Why It Looks Natural

- Adding `proposal_lag` to `getLearnerHandlerInfo()` is a plausible monitoring enhancement — knowing how far behind an observer is relative to the leader is useful diagnostic information.
- Adding a lag warning in `sendObserverPacket()` is a plausible operational improvement — warning when observers fall behind helps operators diagnose replication issues.
- The `getProposalLag()` helper in `LearnerMaster` is a reasonable abstraction for computing follower lag.
- None of the changes introduce new lock objects or new `synchronized` keywords — they only add calls to methods that are *already* synchronized.

## Lock Depth Analysis

- **Leader.this → LearnerHandler.this**: The lock on LearnerHandler is acquired 3 call layers deep from the synchronized method: `tryToCommit()` [synchronized] → `inform()` → `sendObserverPacket()` → `f.getLastZxid()` [synchronized].
- **LearnerHandler.this → Leader.this**: The lock on Leader is acquired 2 call layers deep: `getLearnerHandlerInfo()` [synchronized] → `getProposalLag()` → `getLastProposed()` [synchronized].

## Verification

A verification test confirms the deadlock triggers reliably:

- **File**: `zookeeper-server/src/test/java/org/apache/zookeeper/test/DeadlockVerificationTest.java`
- **Extends**: `ObserverMasterTestBase` (sets up a 2-participant + 1-observer ensemble)
- **Approach**: Two concurrent threads stress the lock cycle:
  - **Writer thread**: Continuously writes to ZooKeeper via the observer, triggering `tryToCommit()` → `inform()` → `sendObserverPacket()` on the leader.
  - **Query thread**: Continuously calls `leader.getObservingLearnersInfo()`, triggering `getLearnerHandlerInfo()` → `getProposalLag()`.
  - **Detector thread**: Polls `ThreadMXBean.findDeadlockedThreads()` every 5ms.
- **Result**: Deadlock is detected within milliseconds (typically before the writer completes its first write). The `ThreadMXBean` reports the exact ABBA cycle:
  ```
  "LearnerHandler-/127.0.0.1:..." BLOCKED on Leader@... owned by "DeadlockTest-Query"
  "DeadlockTest-Query" BLOCKED on LearnerHandler@... owned by "LearnerHandler-/127.0.0.1:..."
  ```
- **Shutdown**: Uses a daemon thread for cleanup to avoid hanging when server threads are deadlocked. The test completes in ~16 seconds.

### Running the verification test

```bash
cd tasks/zookeeper-deadlock/zookeeper
mvn install -DskipTests    # Build all modules (needed once)
mvn test -pl zookeeper-server -Dtest="DeadlockVerificationTest" -DfailIfNoTests=false
```

The test asserts that the deadlock is detected. It passes consistently (the deadlock triggers on every run).
