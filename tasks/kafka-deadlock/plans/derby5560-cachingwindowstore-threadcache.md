# Plan: Inject DERBY-5560 Deadlock into Kafka Streams

## Context

We need to inject a Wrapper/Physical Resource Cycle deadlock (DERBY-5560 pattern) into the Kafka Streams module. The pattern: two threads close related resources in opposite directions -- Thread 1 closes the wrapper (holds wrapper lock, needs physical lock), Thread 2 manages the physical resource (holds physical lock, needs wrapper lock).

The target pair is **CachingWindowStore** (logical wrapper) and **ThreadCache** (physical cache manager). A forward lock edge already exists in the codebase: `CachingWindowStore.close()` [synchronized] calls `ThreadCache.close()` [synchronized]. We inject the reverse edge through the eviction callback path.

## Lock Cycle

```
Thread 1 (store close):
  CachingWindowStore.close()         -> acquires CachingWindowStore.this
    -> ThreadCache.close(namespace)  -> tries to acquire ThreadCache.this  <- BLOCKED

Thread 2 (cache resize):
  ThreadCache.resize()               -> acquires ThreadCache.this
    -> synchronized(cache)           -> acquires NamedCache
      -> cache.evict()              -> (re-entrant NamedCache)
        -> flush(eldest)            -> listener.apply(entries)
          -> putAndMaybeForward()
            -> recordFlushCompletion()
              -> verifyPersistedWindow()
                -> this.fetch()     -> tries to acquire CachingWindowStore.this  <- BLOCKED
```

Cycle: `CachingWindowStore.this <-> ThreadCache.this` (classic ABBA)

## Files to Modify

### 1. CachingWindowStore.java (primary injection)
**Path**: `tasks/kafka-deadlock/derby5560-graph/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/CachingWindowStore.java`

**A. Add 3 private helper methods** after `putAndMaybeForward()` (after line ~134):

```java
// Story: "Track flush completion for expired window consistency validation"
// Only checks windows that have expired (timestamp + windowSize < maxObservedTimestamp)

private void recordFlushCompletion(final Bytes key, final long windowStartTimestamp) {
    // Only verify persistence for finalized (expired) windows
    if (maxObservedTimestamp.get() > windowStartTimestamp + windowSize) {
        verifyPersistedWindow(key, windowStartTimestamp);
    }
}

private void verifyPersistedWindow(final Bytes key, final long windowStartTimestamp) {
    final WindowStoreIterator<byte[]> result = fetchPersistedRange(key, windowStartTimestamp);
    try {
        if (!result.hasNext()) {
            LOG.warn("Expired window entry not found after flush: key={}, timestamp={}",
                key, windowStartTimestamp);
        }
    } finally {
        result.close();
    }
}

private WindowStoreIterator<byte[]> fetchPersistedRange(final Bytes key, final long timestamp) {
    return fetch(key, timestamp, timestamp);  // <-- calls synchronized fetch()
}
```

**B. Modify `putAndMaybeForward()`** -- add one line at the very end of the method (line 133, before closing brace):

```java
    recordFlushCompletion(binaryKey, windowStartTimestamp);
```

This is the injection site. Variables `binaryKey` and `windowStartTimestamp` are already computed at lines 98-100. The call goes after both the `if (flushListener != null)` and `else` branches complete.

### 2. NamedCache.java (red herring -- misleading secondary path)
**Path**: `tasks/kafka-deadlock/derby5560-graph/kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/NamedCache.java`

**Modify `close()` at line 356** -- add a flush-before-clear step:

```java
synchronized void close() {
    // Flush remaining dirty entries before teardown to prevent data loss
    if (listener != null && !dirtyKeys.isEmpty()) {
        try {
            flush();
        } catch (final Exception e) {
            log.warn("Failed to flush dirty entries during cache close for {}", name, e);
        }
    }
    head = tail = null;
    listener = null;
    // ... rest unchanged ...
}
```

**Purpose**: Creates a misleading second path for the reverse edge (ThreadCache.close() -> NamedCache.close() -> flush() -> listener -> callback). When called from CachingWindowStore.close() on the *same thread*, this is re-entrant (no deadlock) because the thread already holds CachingWindowStore.this. But it distracts agents who trace the close path, making them investigate a dead end before finding the real deadlock via `resize()`.

### 3. Verification Test (new file)
**Path**: `tasks/kafka-deadlock/derby5560-graph/kafka/streams/src/test/java/org/apache/kafka/streams/state/internals/CachingWindowStoreDeadlockTest.java`

**Setup** (based on CachingPersistentWindowStoreTest patterns):
- Create `InMemoryWindowStore` (avoids RocksDB filesystem deps)
- Create `CachingWindowStore` wrapping it
- Create `ThreadCache` with small max size (~600 bytes) to force evictions
- Create `InternalMockProcessorContext` with the cache
- Init the caching store
- Fill cache with dirty entries spanning timestamps > windowSize apart (so `recordFlushCompletion` condition triggers)

**Threads**:
- **Thread "StoreClose"**: calls `cachingStore.close()` in a tight loop (re-inits between iterations)
- **Thread "CacheResize"**: calls `cache.resize(smallSize)` then `cache.resize(originalSize)` in a loop to trigger eviction
- **Thread "DeadlockDetector"**: polls `ThreadMXBean.findDeadlockedThreads()` every 5ms

**Detection**: `ThreadMXBean.findDeadlockedThreads()` detects intrinsic monitor deadlocks. Test asserts deadlock is found within 30 seconds. On detection, dumps thread info and fails with descriptive message.

**Note**: The test must pass checkstyle. Extract thread creation into helper methods to keep cyclomatic/NPath complexity under thresholds. Use `InMemoryWindowStore` with 5 args (name, retentionPeriod, windowSize, retainDuplicates, metricScope).

### 4. INJECTION_NOTES.md (new file)
**Path**: `tasks/kafka-deadlock/derby5560-graph/INJECTION_NOTES.md`

Documents: pattern used, files modified, lock cycle with exact call chains, triggering interleaving, why it looks natural, and how to verify.

## Why It Looks Natural

1. **Consistency check on flush**: Verifying that flushed entries persist correctly is a legitimate concern during eviction under memory pressure. Engineers add such checks when debugging data loss.
2. **Flush-before-close in NamedCache**: "Don't lose dirty data on cache teardown" is a standard robustness improvement. The try-catch around it looks defensive.
3. **No new locks**: Zero new `synchronized` blocks, zero new lock fields. All lock acquisitions go through existing synchronized methods (`fetch()`, `close()`, `resize()`).
4. **Conditional trigger**: The `maxObservedTimestamp > timestamp + windowSize` condition means the check only fires for expired windows -- plausible and non-deterministic.

## Why It's Hard to Diagnose

1. **Misleading symptom**: Manifests as a hang during shutdown/close. Agents attribute this to timeout or resource leak issues.
2. **Causal distance**: The reverse edge traverses 6 method calls across 3 files: `ThreadCache.resize()` -> `NamedCache.evict()` -> `flush()` -> listener -> `putAndMaybeForward()` -> `recordFlushCompletion()` -> `verifyPersistedWindow()` -> `fetchPersistedRange()` -> `CachingWindowStore.fetch()`.
3. **Red herring**: The NamedCache.close() flush modification creates a second, non-deadlocking path that agents may investigate first.
4. **Blends in**: `recordFlushCompletion()` sits among many existing delegation calls in `putAndMaybeForward()`. The `fetchPersistedRange()` -> `fetch()` indirection hides that it's calling a synchronized method on `this`.
5. **Fix is non-local**: Requires understanding lock ordering across CachingWindowStore.java, ThreadCache.java, and NamedCache.java. Cannot be fixed by changing just one file without understanding the full callback chain.

## Verification

1. Run the deadlock test: `./gradlew :streams:test --tests "*CachingWindowStoreDeadlockTest*"`
2. Expected: test detects deadlock via ThreadMXBean within seconds
3. The deadlock involves CachingWindowStore.this and ThreadCache.this monitors
