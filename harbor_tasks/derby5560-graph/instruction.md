# Deadlock in Kafka Streams Caching Layer

## Problem

A stress test has uncovered a deadlock in the Kafka Streams caching layer. When a `CachingWindowStore` is being closed concurrently with cache eviction/resize operations, the application hangs indefinitely. The deadlock involves intrinsic Java monitors (`synchronized`) and is detected by `ThreadMXBean.findDeadlockedThreads()`.

## Symptom

The test `CachingWindowStoreDeadlockTest` reproduces the issue by running two threads concurrently:
- One thread repeatedly closes and reinitializes a `CachingWindowStore`
- Another thread repeatedly resizes the `ThreadCache`, triggering eviction of dirty entries

Within seconds, the JVM reports deadlocked threads and the test fails.

## How to Run the Test

```bash
cd /app
./gradlew :streams:test --tests "org.apache.kafka.streams.state.internals.CachingWindowStoreDeadlockTest" --no-daemon
```

You can recompile your changes without running tests:

```bash
./gradlew :streams:compileJava :streams:compileTestJava -x test --no-daemon
```

## Relevant Source

The Kafka Streams source code is at `/app/streams/src/`. The key classes involved in the caching layer are in:

```
streams/src/main/java/org/apache/kafka/streams/state/internals/
```

Look at the classes related to window store caching, the thread cache, and the named cache.

## Tools Available

- `fray` is installed and available for systematic thread interleaving exploration
- The full Kafka source tree is available with Gradle for building and testing
- All dependencies are pre-cached; no network access needed for builds

## Your Task

Find and fix the deadlock so that the test passes (no deadlock detected). The fix should maintain correctness — don't simply remove functionality, but ensure that lock ordering is consistent across all code paths.
