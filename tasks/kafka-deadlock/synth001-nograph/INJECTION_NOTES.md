# SYNTH-001 Injection: Kafka Streams — 3-Node Conditional Callback Cycle

## Pattern

SYNTH-001: 3-Node Conditional Callback Cycle with mixed lock primitives.

## Deadlock Cycle

```
Thread 1 (metrics scrape):       synchronized(TopologyMetricsCache) -> writeLock(AbstractMetricsTransport)
Thread 2 (transport maintenance): writeLock(AbstractMetricsTransport) -> sessionLock(MetricsSessionManager)
Thread 3 (session renewal):       sessionLock(MetricsSessionManager) -> synchronized(TopologyMetricsCache) [CONDITIONAL]
```

The closing edge (C->A) only fires when `newSession.shardId != oldSession.shardId`, which happens only during Kafka Streams rebalancing (`rebalancingMode = true`).

## New Files

All in `streams/src/main/java/org/apache/kafka/streams/processor/internals/`:

| File | Lock Primitive | Role in Cycle |
|------|---------------|---------------|
| `MetricsSessionListener.java` | — | Callback interface |
| `AbstractMetricsTransport.java` | `ReentrantReadWriteLock` (hidden) | Node B |
| `MetricsSessionManager.java` | `ReentrantLock` + `Condition` | Node C |
| `StreamsMetricsConnector.java` | inherits RWLock from parent | Edge A->B, B->C |
| `TopologyMetricsCache.java` | `synchronized` (intrinsic) | Node A, Edge C->A |

## Modified Files

| File | Change |
|------|--------|
| `TopologyMetadata.java` | Added `metricsCache` field + getter/setter |
| `DefaultStateUpdater.java` | Added `metricsConnector` field + `maybePerformTransportMaintenance()` call in `runOnce()` |
| `StreamThread.java` | Wires all 5 objects in `create()`, adds `maybeRefreshMetricsSnapshot()` in `runOnceWithoutProcessingThreads()` |

## Edge Call Depth

- **A->B** (4 hops): `getSnapshot()` [sync] -> `refreshFromBackend()` -> `fetchLatestMetrics()` -> `refreshTransport()` -> `acquireExclusive()` [writeLock]
- **B->C** (3 hops): `performMaintenance()` [writeLock] -> `validateSession()` -> `refreshSessionIfExpired()` [sessionLock]
- **C->A** (3 hops): `renewSession()` [sessionLock] -> `notifyListeners()` -> `onSessionChanged()` -> `invalidateAll()` [sync]

Total: ~10 call hops across 3 files.

## Red Herrings

1. `TopologyMetricsCache` has 4 synchronized methods — only `getSnapshot` and `invalidateAll` participate in the cycle
2. `StreamsMetricsConnector` uses `ConcurrentHashMap.computeIfAbsent()` — looks racy but is safe
3. `AbstractMetricsTransport` has `acquireShared()`/`releaseShared()` — read locks never block read locks
4. `MetricsSessionManager` has `volatile currentSession` read under lock — looks like a bug, is harmless
5. `DefaultStateUpdater` already has 3 `ReentrantLock`s — only the interaction with `metricsConnector` matters

## Trigger Condition

The deadlock requires `rebalancingMode = true` in `MetricsSessionManager`. When false, session renewals keep the same shardId, so the `onSessionChanged` listener is a no-op (doesn't call `invalidateAll`), breaking the C->A edge.

## Verification

```bash
cd tasks/kafka-deadlock/synth001-nograph/kafka
./gradlew :streams:test --tests "org.apache.kafka.streams.processor.internals.TopologyMetricsCacheDeadlockTest"
```

Test uses `ThreadMXBean.findDeadlockedThreads()` (not `findMonitorDeadlockedThreads()`) because the cycle mixes intrinsic monitors and `j.u.c.` locks.
