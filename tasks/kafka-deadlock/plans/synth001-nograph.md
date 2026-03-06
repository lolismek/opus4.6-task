# SYNTH-001 Deadlock Injection Plan: Kafka Streams

## Context

Inject a 3-Node Conditional Callback Cycle (SYNTH-001) deadlock into the Kafka Streams module. The bug should be genuinely hard to diagnose: mixed lock primitives, inheritance-hidden locks, a conditional closing edge, and 6-9 call hops spanning 3 files. The injection must look like a plausible "topology-aware metrics caching" feature.

## The Cycle

```
Thread 1 (metrics scrape):       synchronized(TopologyMetricsCache) → writeLock(AbstractMetricsTransport)
Thread 2 (transport maintenance): writeLock(AbstractMetricsTransport) → sessionLock(MetricsSessionManager)
Thread 3 (session renewal):       sessionLock(MetricsSessionManager) → synchronized(TopologyMetricsCache) [CONDITIONAL]
```

The closing edge (C→A) only fires when `newSession.shardId != oldSession.shardId`, which happens only during Kafka Streams rebalancing.

## New Files (5)

All in `streams/src/main/java/org/apache/kafka/streams/processor/internals/`:

### 1. `MetricsSessionListener.java` (interface)
- Callback: `void onSessionChanged(MetricsSessionManager.MetricsSession old, MetricsSessionManager.MetricsSession new)`

### 2. `AbstractMetricsTransport.java` (hides the RWLock)
- `private final ReentrantReadWriteLock transportLock = new ReentrantReadWriteLock()`
- `protected void acquireExclusive()` → `transportLock.writeLock().lock()` (THE hidden lock)
- `protected void releaseExclusive()` → `transportLock.writeLock().unlock()`
- `protected void acquireShared()` / `releaseShared()` → readLock (red herring — reads never deadlock)
- Abstract methods: `doConnect()`, `doDisconnect()`, `isTransportValid()`
- Red herring: `volatile long lastConnectTimeMs` read under lock unnecessarily

### 3. `StreamsMetricsConnector.java` extends `AbstractMetricsTransport`
- Field: `MetricsSessionManager sessionManager`
- `fetchLatestMetrics(topologyName)`:
  - Normal path: `acquireShared()` (read lock, no deadlock)
  - Stale transport: calls `refreshTransport()` → `super.acquireExclusive()` (DEADLOCK PATH, edge A→B target)
- `performMaintenance()` (Thread 2 entry):
  - `super.acquireExclusive()` → `validateSession()` → `sessionManager.refreshSessionIfExpired()` (edge B→C)
- Red herring: `ConcurrentHashMap.computeIfAbsent()` that looks racy but is safe

### 4. `MetricsSessionManager.java` (ReentrantLock + Condition)
- `private final ReentrantLock sessionLock = new ReentrantLock()`
- `private final Condition sessionReady = sessionLock.newCondition()`
- `private volatile MetricsSession currentSession` (volatile under lock — red herring)
- `private boolean rebalancingMode = false` (controls conditional edge)
- `refreshSessionIfExpired()`: locks sessionLock, if expired → renews, notifyListeners
- `renewSession()` (Thread 3 entry): locks sessionLock, renews, `notifyListeners(old, new)`, signals condition
- `notifyListeners(old, new)`: iterates listeners, calls `onSessionChanged()` — THIS bridges to edge C→A
- Inner class `MetricsSession`: `String token`, `int shardId`, `long expiryMs`
- When `rebalancingMode=true`: renewal assigns random new shardId (triggers listener)
- When `rebalancingMode=false`: renewal keeps same shardId (listener is no-op)

### 5. `TopologyMetricsCache.java` (synchronized intrinsic monitor)
- `private final Map<String, CachedMetricsSnapshot> cache`
- `private final StreamsMetricsConnector metricsConnector`
- `public synchronized CachedMetricsSnapshot getSnapshot(topologyName)` (Thread 1 entry):
  - Cache miss/stale → `refreshFromBackend()` → `metricsConnector.fetchLatestMetrics()` (edge A→B)
- `public synchronized void invalidateAll()` — TARGET of edge C→A
- `public synchronized void invalidateTopology(name)` — red herring (same monitor, no cycle)
- `public synchronized int size()` — red herring
- Inner class `TopologyMetricsCacheInvalidator implements MetricsSessionListener`:
  - `onSessionChanged(old, new)`: **if shardId changed** → `cache.invalidateAll()` (CONDITIONAL edge C→A)
  - if shardId same → no-op (log only)
- `createInvalidationListener()` — returns the invalidator, called during init (far from deadlock site)

## Modified Files (3)

### 6. `TopologyMetadata.java`
- Add field: `private TopologyMetricsCache metricsCache`
- Add setter: `void setMetricsCache(TopologyMetricsCache cache)`
- Add accessor: `TopologyMetricsCache metricsCache()`

### 7. `DefaultStateUpdater.java`
- Add field: `private StreamsMetricsConnector metricsConnector` (with null-safe setter)
- In `StateUpdaterThread.runOnce()` (line ~201, after `maybeGetClientInstanceIds()`):
  - Add call to `maybePerformTransportMaintenance()` — checks timer, calls `metricsConnector.performMaintenance()`
- This is Thread 2's entry into the cycle (B→C edge)

### 8. `StreamThread.java`
- In `create()` factory (line ~396): instantiate all 5 new objects, wire them, register listener
  - `MetricsSessionManager sessionManager = new MetricsSessionManager(...)`
  - `StreamsMetricsConnector connector = new StreamsMetricsConnector(sessionManager)`
  - `TopologyMetricsCache metricsCache = new TopologyMetricsCache(connector, cacheTtlMs)`
  - `sessionManager.registerListener(metricsCache.createInvalidationListener())` ← listener registration (far from deadlock)
  - `topologyMetadata.setMetricsCache(metricsCache)`
  - `stateUpdater.setMetricsConnector(connector)` (if stateUpdater not null)
- In `runOnceWithoutProcessingThreads()` (line ~1169): add `maybeRefreshMetricsSnapshot()` call
  - This is Thread 1's entry into the cycle (A→B edge)

## Verification Test

**File**: `streams/src/test/java/org/apache/kafka/streams/processor/internals/TopologyMetricsCacheDeadlockTest.java`

```
1. Create MetricsSessionManager, StreamsMetricsConnector, TopologyMetricsCache
2. Register invalidation listener on session manager
3. Enable rebalancing mode: sessionManager.setRebalancingMode(true)
4. Set cacheTtlMs=0 so every getSnapshot() is a miss
5. Mark transport as invalid so every fetch triggers refreshTransport() → writeLock path
6. CyclicBarrier(3) to sync thread starts
7. Thread 1: loop cache.getSnapshot("test") — edge A→B
8. Thread 2: loop connector.performMaintenance() — edge B→C
9. Thread 3: loop sessionManager.renewSession() — edge C→A (conditional, enabled)
10. Poll ThreadMXBean.findDeadlockedThreads() for 10s
11. Assert deadlock detected (≥2 deadlocked threads)
```

Uses `findDeadlockedThreads()` (not `findMonitorDeadlockedThreads()`) because the cycle mixes intrinsic monitors and j.u.c. locks.

## Edge Call Depth Analysis

- **Edge A→B** (3 hops): `getSnapshot()` [sync] → `refreshFromBackend()` → `fetchLatestMetrics()` → `refreshTransport()` → `acquireExclusive()` [writeLock]
- **Edge B→C** (3 hops): `performMaintenance()` [writeLock] → `validateSession()` → `refreshSessionIfExpired()` [sessionLock]
- **Edge C→A** (3 hops): `renewSession()` [sessionLock] → `notifyListeners()` → `onSessionChanged()` → `invalidateAll()` [sync] (conditional)

Total: 9 call hops across 3 files.

## Red Herrings

1. `TopologyMetricsCache` has 4 synchronized methods — only 2 participate in cycle
2. `StreamsMetricsConnector` uses `ConcurrentHashMap.computeIfAbsent()` — looks racy, isn't
3. `AbstractMetricsTransport` has `acquireShared()`/`releaseShared()` — read locks never block reads
4. `MetricsSessionManager` has `volatile currentSession` read under lock — looks like a bug, is harmless
5. `DefaultStateUpdater` has 3 ReentrantLocks — only the interaction with metricsConnector matters

## Why It Looks Natural

- "Topology metrics caching" is plausible: Kafka Streams applications commonly need to export metrics per-topology
- "Session management for metrics backends" is plausible: authenticated endpoints need token refresh
- "Transport maintenance" in the state updater thread is plausible: background threads often do housekeeping
- The RWLock in `AbstractMetricsTransport` justifies itself as "connections are read-heavy, only maintenance needs exclusive access"
- The conditional edge tied to rebalancing is realistic: shard assignments change during rebalancing

## Implementation Order

1. Create `MetricsSessionListener.java`
2. Create `AbstractMetricsTransport.java`
3. Create `MetricsSessionManager.java`
4. Create `StreamsMetricsConnector.java`
5. Create `TopologyMetricsCache.java`
6. Modify `TopologyMetadata.java` (add field + accessor)
7. Modify `DefaultStateUpdater.java` (add connector + maintenance call)
8. Modify `StreamThread.java` (wire everything in create(), add scrape call)
9. Create verification test `TopologyMetricsCacheDeadlockTest.java`
10. Write `INJECTION_NOTES.md`

## Verification

1. Compile: `cd tasks/kafka-deadlock/synth001-nograph/kafka && ./gradlew :streams:compileJava`
2. Run test: `./gradlew :streams:test --tests TopologyMetricsCacheDeadlockTest`
3. The test should detect a deadlock within seconds via `ThreadMXBean.findDeadlockedThreads()`
