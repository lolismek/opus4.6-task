# Deadlock Bug in Kafka Streams Metrics Infrastructure

A deadlock has been reported in the Kafka Streams processing pipeline. Under
certain conditions (specifically during consumer group rebalancing), multiple
threads become permanently stuck, causing the streams application to hang.

## Symptom

The test `TopologyMetricsCacheDeadlockTest.shouldDetectDeadlockUnderRebalancing`
reliably detects a deadlock using `ThreadMXBean.findDeadlockedThreads()`. The
deadlock involves multiple threads acquiring locks in conflicting orders across
the topology metrics caching infrastructure.

The deadlock is triggered when three concurrent activities overlap:
- A stream thread refreshes cached metric snapshots
- A background thread performs transport maintenance
- A session renewal occurs during rebalancing (when shard assignments change)

## Your Task

**Find and fix the deadlock** in the streams module so that the verification
test no longer detects any deadlocked threads. Your fix must preserve the
functional correctness of the metrics caching system (sessions should still
be renewed, caches should still be invalidated on shard changes, etc.).

## Relevant Source

The streams module source is at:
```
/app/streams/src/main/java/org/apache/kafka/streams/
```

The test that detects the deadlock is at:
```
/app/streams/src/test/java/org/apache/kafka/streams/processor/internals/TopologyMetricsCacheDeadlockTest.java
```

## Build & Test Commands

Recompile after making changes:
```bash
./gradlew :streams:compileJava :streams:compileTestJava -x test --no-daemon
```

Run the verification test:
```bash
./gradlew :streams:test --tests "org.apache.kafka.streams.processor.internals.TopologyMetricsCacheDeadlockTest" --no-daemon
```

The `fray` tool is also available for systematic thread interleaving exploration
if you need it.

## Hints

- The deadlock involves mixed lock primitives (not just one type)
- Look at lock acquisition ordering across thread boundaries
- Consider what happens when callbacks are invoked while holding locks
- The deadlock is conditional: it only manifests when shard IDs change during session renewal
