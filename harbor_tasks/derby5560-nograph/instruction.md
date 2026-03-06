# Deadlock Bug in Kafka Streams Cache Layer

A deadlock has been reported in the Kafka Streams cache subsystem. During concurrent stream processing and task cleanup, threads occasionally hang indefinitely — the application appears to freeze during shutdown or under sustained write load.

## Symptoms

- Stream processing threads hang and stop making progress
- Task shutdown/cleanup hangs indefinitely
- `ThreadMXBean.findDeadlockedThreads()` reports deadlocked threads involving the cache layer
- The hang involves threads stuck waiting on monitor locks in the `streams` module's `state.internals` package

## Reproducing the Bug

A test has been written that reliably reproduces the deadlock:

```bash
cd /app
./gradlew :streams:test --tests "org.apache.kafka.streams.state.internals.CacheRebalanceDeadlockTest" --no-daemon
```

This test creates concurrent cache writers and a cleanup thread, then uses `ThreadMXBean` to detect when threads become deadlocked. The test **passes** (i.e., the deadlock IS detected) when the bug is present. Your fix should make this test **fail** (i.e., the deadlock should no longer occur).

## Your Task

Find and fix the deadlock in the Kafka Streams cache code. The relevant source is in:

```
/app/streams/src/main/java/org/apache/kafka/streams/state/internals/
```

After making changes, you can recompile with:

```bash
./gradlew :streams:compileJava :streams:compileTestJava -x test --no-daemon
```

Then re-run the test to verify your fix.

## Tools Available

- `fray` is available on the PATH for systematic thread interleaving exploration
- The full Kafka source tree is available under `/app/` with Gradle build support
- JDK 25 (Amazon Corretto) is the runtime
