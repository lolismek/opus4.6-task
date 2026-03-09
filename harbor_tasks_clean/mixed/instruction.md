# Concurrency Bugs in Kafka Streams

A Kafka Streams application intermittently hangs or deadlocks under concurrent
operations. There may be one or more concurrency bugs — including deadlocks
(cyclic lock ordering), hangs (missed signals / lost wakeups), or both.

## Symptoms

- Under sustained concurrent workload, threads may form circular waits and
  never make progress.
- Some operations may hang indefinitely even without a classic deadlock, due to
  missed condition signals or lost wakeups.
- The bugs are non-deterministic and depend on thread scheduling.

## Environment

- Source: `/app/streams/src/main/java/org/apache/kafka/streams/`
- Build: `./gradlew :streams:compileJava :streams:compileTestJava -x test --no-daemon`

## Using Fray

The `fray` tool systematically explores thread interleavings to find
concurrency bugs. To use it with this project:

1. Write a Java class with a `main()` method that exercises the suspected
   concurrency scenario (deadlock, hang, etc.).
2. Place it in the test source tree and compile.
3. Run it through Fray:
   ```
   fray-gradle your.package.YourTestClass -- --iter 1000
   ```
   Exit code 0 means no bug found; non-zero means Fray detected a problem.

## Your Task

Find and fix all concurrency bugs so that no deadlock or hang can occur under
any thread interleaving.
