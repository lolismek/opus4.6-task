# Deadlock in Kafka Streams

A Kafka Streams application intermittently hangs under concurrent operations.
Thread dumps show threads permanently blocked, each waiting for a lock held by
another. The hang is non-deterministic and depends on thread scheduling.

## Symptom

- Under sustained concurrent workload, threads form a circular wait and never
  make progress.
- The hang is intermittent — it depends on timing between concurrent operations.
- `ThreadMXBean.findDeadlockedThreads()` confirms deadlocked threads when the
  hang occurs.

## Environment

- Source: `/app/streams/src/main/java/org/apache/kafka/streams/`
- Build: `./gradlew :streams:compileJava :streams:compileTestJava -x test --no-daemon`

## Using Fray

The `fray` tool systematically explores thread interleavings to find
concurrency bugs. To use it with this project:

1. Write a Java class with a `main()` method that exercises the suspected
   deadlock scenario.
2. Place it in the test source tree and compile.
3. Run it through Fray:
   ```
   fray-gradle your.package.YourTestClass -- --iter 1000
   ```
   Exit code 0 means no deadlock found; non-zero means Fray detected a bug.

## Your Task

Find and fix the root cause of the cyclic lock dependency so that the deadlock
cannot occur under any thread interleaving.
