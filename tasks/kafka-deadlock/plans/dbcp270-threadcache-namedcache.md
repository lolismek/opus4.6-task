# Plan: Inject DBCP-270 Deadlock into Kafka Streams (ThreadCache + NamedCache)

## Context

Inject a Two-Object ABBA Cycle deadlock (DBCP-270 pattern) into Kafka Streams. The lock graph shows an existing forward edge `ThreadCache.this -> NamedCache.this` via `ThreadCache.resize()`. The reverse edge is missing: NamedCache never calls back into a ThreadCache synchronized method. We add it as a "stale namespace validation" feature.

## Lock Cycle

```
Lock A = ThreadCache.this     (intrinsic monitor)
Lock B = NamedCache.this      (intrinsic monitor)

Thread 1 (resize): ThreadCache.resize()       [holds A]
  -> synchronized(cache)                       [tries B]  <- BLOCKED

Thread 2 (put):    ThreadCache.put()           [does NOT hold A]
  -> getOrCreateCache()                        [acquires+releases A]
  -> synchronized(cache)                       [holds B]
    -> cache.put() -> maybeEvict() -> evict()
      -> flush(eldest)                         [still holds B]
        -> validateNamespaceActive()
          -> checkNamespaceRegistration()
            -> parentCache.isNamespaceRegistered()
              -> getCache()                    [tries A]  <- BLOCKED
```

## Files to Modify

### 1. `NamedCache.java` (4 changes)
**Path**: `tasks/kafka-deadlock/dbcp270-graph/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/NamedCache.java`

**1a.** Add field after line 49 (`private long currentSizeBytes;`):
```java
private final ThreadCache parentCache;
```

**1b.** Convert existing constructor (lines 61-77) to delegate to new 3-arg constructor:
```java
NamedCache(final String name, final StreamsMetricsImpl streamsMetrics) {
    this(name, streamsMetrics, null);
}
```

**1c.** Add new 3-arg constructor after the existing one (duplicates the current body, stores parentCache):
```java
NamedCache(final String name, final StreamsMetricsImpl streamsMetrics, final ThreadCache parentCache) {
    this.name = name;
    this.streamsMetrics = streamsMetrics;
    this.parentCache = parentCache;
    // ... same sensor init as current constructor
}
```

**1d.** Add 2 private helper methods (between `setListener()` and `flush()`):
```java
private void validateNamespaceActive() {
    if (parentCache == null) { return; }
    if (!checkNamespaceRegistration()) {
        log.warn("NamedCache {} may be operating on a stale namespace", name);
    }
}

private boolean checkNamespaceRegistration() {
    return parentCache.isNamespaceRegistered(name);
}
```

**1e.** Insert call in `flush(LRUNode)` after the trace log block (line 126), before listener null-check (line 128):
```java
validateNamespaceActive();
```

### 2. `ThreadCache.java` (2 changes)
**Path**: `tasks/kafka-deadlock/dbcp270-graph/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/ThreadCache.java`

**2a.** Modify line 320 in `getOrCreateCache()`:
```java
// FROM:
cache = new NamedCache(name, this.metrics);
// TO:
cache = new NamedCache(name, this.metrics, this);
```

**2b.** Add bridge method after `getOrCreateCache()` (after line 324):
```java
boolean isNamespaceRegistered(final String namespace) {
    return getCache(namespace) != null;  // getCache() is EXISTING private synchronized
}
```
This is NOT synchronized itself but calls `getCache()` which IS synchronized -> acquires ThreadCache.this.

### 3. `ThreadCacheDeadlockTest.java` (new file)
**Path**: `tasks/kafka-deadlock/dbcp270-graph/kafka/streams/src/test/java/org/apache/kafka/streams/state/internals/ThreadCacheDeadlockTest.java`

- Thread A: continuously calls `cache.resize(small/large)` (acquires ThreadCache.this, then tries NamedCache.this)
- Thread B: continuously calls `cache.put()` with dirty entries (acquires NamedCache.this via `synchronized(cache)`, eviction triggers flush -> callback -> tries ThreadCache.this)
- Detector: polls `ThreadMXBean.findDeadlockedThreads()` every 5ms
- All threads daemon, 30s timeout, assert deadlock detected
- Uses `MockStreamsMetrics`, `LRUCacheEntry(value, headers, isDirty=true, ...)` (matches existing ThreadCacheTest patterns)

### 4. `INJECTION_NOTES.md` (new file)
**Path**: `tasks/kafka-deadlock/dbcp270-graph/INJECTION_NOTES.md`

Documents: pattern, files modified, lock cycle with exact call chains, triggering interleaving, why it's non-deterministic, why it looks natural, lock depth analysis, verification instructions.

## Call Depth Analysis

- **Forward (existing)**: `resize()` [sync] -> `synchronized(cache)` = 1 layer
- **Reverse (injected)**: `flush(LRUNode)` -> `validateNamespaceActive()` -> `checkNamespaceRegistration()` -> `isNamespaceRegistered()` -> `getCache()` [sync] = **3 layers** across 2 files

## Red Herrings for Investigators

- NamedCache has 20+ synchronized methods (get, put, delete, close, clear, keyRange, etc.) - most are NOT part of the cycle
- ThreadCache has `close()` and `clear()` synchronized methods that also call NamedCache methods - forward direction only, not the cycle
- The `DirtyEntryFlushListener` callback in flush() is already a cross-object call - could distract
- `NamedCache.close()` calls `streamsMetrics.removeAllCacheLevelSensors()` - looks like a cross-lock call but isn't

## Verification

```bash
cd tasks/kafka-deadlock/dbcp270-graph/kafka
./gradlew :streams:test --tests "org.apache.kafka.streams.state.internals.ThreadCacheDeadlockTest"
```
