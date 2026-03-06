# Deadlock Bug in Kafka Streams

A deadlock has been detected in the Kafka Streams module. The verification test `Synth001DeadlockVerificationTest` reproduces a cyclic lock ordering bug involving three locks and three threads. When the deadlock is triggered, threads hang indefinitely and `ThreadMXBean.findDeadlockedThreads()` reports deadlocked threads.

## Symptoms

- Under certain thread interleavings, three threads form a circular wait on three different lock primitives (a mix of intrinsic monitors, `ReentrantReadWriteLock`, and `ReentrantLock`).
- The deadlock is intermittent — it depends on runtime timing and Kafka Streams lifecycle state.
- The verification test directly exercises the lock acquisition ordering pattern extracted from the production code.

## How to verify

Recompile and run the verification test:

```bash
cd /app
./gradlew :streams:compileJava :streams:compileTestJava -x test --no-daemon
./gradlew :streams:test --tests "org.apache.kafka.streams.processor.internals.Synth001DeadlockVerificationTest" --no-daemon
```

The test currently **passes** (meaning it successfully detects the deadlock). Your fix is correct when the test **fails** — i.e., the deadlock can no longer be reproduced.

## Where to look

The streams module source is at `/app/streams/src/main/java/org/apache/kafka/streams/`. The deadlock involves lock ordering across multiple classes in the `processor/internals` package and the top-level `KafkaStreams` class.

The tool `fray` is also available for systematic thread interleaving exploration if needed.

## Your task

Find and fix the root cause of the cyclic lock dependency so that the deadlock cannot occur under any thread interleaving. The fix should break the lock cycle without removing necessary synchronization — ensure thread safety is preserved.
