# Deadlock Bug in Kafka Streams Cache Layer

A deadlock has been reported in the Kafka Streams thread cache subsystem. Under concurrent workloads, two threads can become permanently stuck waiting for each other's locks, causing the stream processing pipeline to hang indefinitely.

## Symptoms

- Under concurrent `resize()` and `put()` operations on the `ThreadCache`, the JVM reports deadlocked threads via `ThreadMXBean.findDeadlockedThreads()`
- One thread holds an intrinsic monitor on `ThreadCache` and waits for a `NamedCache` monitor
- Another thread holds the `NamedCache` monitor and waits for the `ThreadCache` monitor
- The deadlock is non-deterministic — it depends on thread scheduling and cache occupancy

## Reproducing the Bug

A test has been written that reliably triggers the deadlock:

```bash
cd /app
./gradlew :streams:test --tests "org.apache.kafka.streams.state.internals.ThreadCacheDeadlockTest" --no-daemon
```

The test currently **passes**, meaning it successfully detects the deadlock. Your goal is to fix the source code so that the deadlock can no longer occur (the test should then **fail** because no deadlock is detected).

## Relevant Source Code

The bug is in the Kafka Streams state internals module:

```
streams/src/main/java/org/apache/kafka/streams/state/internals/
```

Key classes to examine: `ThreadCache.java`, `NamedCache.java`, and their synchronized methods.

## Build & Recompile

After making changes, recompile with:

```bash
./gradlew :streams:compileJava :streams:compileTestJava -x test --no-daemon
```

## Tools Available

- `fray` is available at `/opt/fray/bin/fray` for systematic thread interleaving exploration (useful for verifying concurrency fixes)
- The full Kafka source tree is available under `/app/` with Gradle build support
- All dependencies are pre-cached; no internet needed for builds

## Goal

Find and fix the root cause of the lock ordering violation so that the deadlock is eliminated under all possible thread interleavings.
