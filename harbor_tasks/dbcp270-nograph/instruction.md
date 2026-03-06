# Deadlock in Kafka Streams Task Lifecycle

## Problem

A deadlock has been detected in the Kafka Streams module during concurrent task lifecycle operations. When a stream thread removes a task while the periodic state directory cleaner runs simultaneously, the application hangs indefinitely.

## Symptoms

- `ThreadMXBean.findDeadlockedThreads()` reports deadlocked threads
- One thread is stuck in a task removal path, another in the directory cleanup path
- Both threads hold one intrinsic monitor (`synchronized`) and wait for the other
- The issue is in the `streams` module under `org.apache.kafka.streams.processor.internals`

## Reproducing

A test has been written that reliably triggers the deadlock:

```bash
cd /app
./gradlew :streams:test --tests "org.apache.kafka.streams.processor.internals.TaskStateDirectoryDeadlockTest" --no-daemon
```

The test currently **passes**, confirming the deadlock exists. Your fix should make this test **fail** (i.e., no deadlock is detected within 30 seconds).

## Recompiling

After making changes, recompile with:

```bash
./gradlew :streams:compileJava :streams:compileTestJava -x test --no-daemon
```

## Tools

- The `fray` tool is available for systematic thread interleaving exploration if needed
- Source code is at `/app/streams/src/main/java/org/apache/kafka/streams/processor/internals/`
- Test code is at `/app/streams/src/test/java/org/apache/kafka/streams/processor/internals/`

## Goal

Fix the deadlock so that the two concurrent operations (task removal and directory cleanup) can run safely without circular lock dependencies. The test should **fail** after your fix (meaning no deadlock is detected).
