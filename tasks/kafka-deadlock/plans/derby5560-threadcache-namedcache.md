# Deadlock Injection Plan: DERBY-5560 Pattern in Kafka Streams

## Context

Inject a Wrapper/Physical Resource Cycle deadlock (DERBY-5560 pattern) into the Kafka Streams cache layer. The deadlock creates a lock-ordering cycle between `ThreadCache` (wrapper/manager) and `NamedCache` (physical/individual cache) that manifests during cache eviction + concurrent close — appearing as a shutdown hang rather than an obvious lock cycle.

## Target Pair

- **Wrapper**: `ThreadCache` — manages multiple `NamedCache` instances, uses `synchronized` methods for map access
- **Physical**: `NamedCache` — individual LRU cache, nearly all methods `synchronized`
- **Bridge**: `CachingKeyValueStore` — registers a flush listener callback that connects NamedCache back to ThreadCache

## Lock Cycle

### Edge 1: ThreadCache.this → NamedCache.this (already exists)

```
ThreadCache.close(namespace) [synchronized on ThreadCache.this]
  → removed.sizeInBytes()   [synchronized on NamedCache.this]
  → removed.close()         [synchronized on NamedCache.this]
```

### Edge 2: NamedCache.this → ThreadCache.this (injected via callback)

```
ThreadCache.put()                              [NOT synchronized on ThreadCache.this]
  → synchronized(cache)                        [acquires NamedCache.this]
    → cache.put() → maybeEvict() → cache.evict()
      → flush(eldest) → listener.apply()
        → CachingKeyValueStore.putAndMaybeForward()
          → ensureCacheCapacity()              [INJECTED - CachingKeyValueStore.java]
            → ThreadCache.rebalanceIfNeeded()  [INJECTED - ThreadCache.java]
              → ThreadCache.resize()           [ALREADY synchronized on ThreadCache.this → BLOCKED]
```

### Deadlock Interleaving

1. **Thread A** (stream thread): `ThreadCache.put()` → `synchronized(cache)` acquires **NamedCache.this**
2. **Thread B** (cleanup thread): `ThreadCache.close(ns)` acquires **ThreadCache.this**
3. **Thread B**: calls `removed.sizeInBytes()` → tries **NamedCache.this** → **BLOCKED**
4. **Thread A**: eviction callback → `ensureCacheCapacity()` → `rebalanceIfNeeded()` → `resize()` → tries **ThreadCache.this** → **BLOCKED**
5. **DEADLOCK**

## Files to Modify

### 1. `streams/src/main/java/org/apache/kafka/streams/state/internals/ThreadCache.java`

Add one non-synchronized method after `resize()` (~line 101):

```java
void rebalanceIfNeeded() {
    if (sizeInBytes.get() > maxCacheSizeBytes) {
        resize(maxCacheSizeBytes);
    }
}
```

- Package-private, NOT synchronized (lock acquisition deferred to existing `resize()`)
- Reads `sizeInBytes` (AtomicLong) and `maxCacheSizeBytes` (volatile) — both thread-safe
- `resize(maxCacheSizeBytes)` is effectively a no-op (shrink=false), but the deadlock occurs at the `synchronized` barrier, not inside the method body

### 2. `streams/src/main/java/org/apache/kafka/streams/state/internals/CachingKeyValueStore.java`

Add helper method (~line 250) and call from `putAndMaybeForward()`:

```java
private void ensureCacheCapacity(final InternalProcessorContext<?, ?> context) {
    if (context.cache() != null) {
        context.cache().rebalanceIfNeeded();
    }
}
```

Add `ensureCacheCapacity(context);` at end of `putAndMaybeForward()` (before final `}`).

### 3. `streams/src/test/java/org/apache/kafka/streams/state/internals/CacheRebalanceDeadlockTest.java` (new)

Verification test:
- Create ThreadCache with small max size (200 bytes — forces frequent evictions)
- Register flush listener that calls `cache.rebalanceIfNeeded()` (simulates injected callback)
- 4 putter threads: continuously put dirty entries → triggers eviction → flush → listener → rebalanceIfNeeded → resize
- 1 closer thread: continuously close namespace then re-register listener → triggers ThreadCache.close → NamedCache.sizeInBytes/close
- Detector thread: polls `ThreadMXBean.findDeadlockedThreads()` every 5ms
- Assert deadlock detected within 30s
- Test utilities: `MockStreamsMetrics(new Metrics())`, `LogContext`, `LRUCacheEntry(..., isDirty=true, ...)`, `RecordHeaders`
- Must pass Kafka's checkstyle rules (CyclomaticComplexity ≤ 16, NPathComplexity ≤ 500) — extract helper methods for thread creation, latch awaiting, etc.

### 4. `tasks/kafka-deadlock/derby5560-nograph/INJECTION_NOTES.md` (new)

Document pattern, files, lock cycle, triggering interleaving, and why it looks natural.

## Why It Looks Natural

- `rebalanceIfNeeded()` is a plausible cache management method — after flushing entries to the store, check if the cache still exceeds its size limit and trigger eviction
- `ensureCacheCapacity()` blends in with the many other ThreadCache calls CachingKeyValueStore already makes (flush, close, put, get, clear)
- The callback from NamedCache.flush() → listener → CachingKeyValueStore is an existing pattern; adding one more ThreadCache call in that path is unremarkable
- No new `synchronized` blocks, no new lock fields — only calls to already-synchronized methods

## Why It's Hard to Diagnose

- **Misleading symptoms**: manifests as shutdown hang or stuck put operation — looks like timeout/resource leak
- **Causal distance**: reverse edge traverses 3 files (NamedCache → CachingKeyValueStore → ThreadCache) and 6+ method calls
- **Fix is non-local**: correct fix requires understanding the callback chain across ThreadCache, NamedCache, and CachingKeyValueStore
- **Blends in**: CachingKeyValueStore makes ~10 different ThreadCache calls; this is just another one

## Implementation Notes

- The timing window for the deadlock is tight: putter must hold NamedCache.this (via `synchronized(cache)` in `ThreadCache.put`) during eviction while the closer holds ThreadCache.this (via `synchronized` on `close()`). Using 4 putter threads significantly increases contention and makes the deadlock form reliably.
- During the eviction callback, `ThreadCache.sizeInBytes` (AtomicLong) has NOT yet been decremented for the evicted entry (it's updated after `cache.evict()` returns). This means `rebalanceIfNeeded()`'s check `sizeInBytes.get() > maxCacheSizeBytes` evaluates to TRUE, ensuring `resize()` is always called.
- The closer thread's `close()` removes the NamedCache from the map and nulls its listener. Putter threads that still hold a reference to the old NamedCache may hit `IllegalArgumentException` from `flush()` when the listener is null — the test catches these and continues.

## Verification

```bash
cd tasks/kafka-deadlock/derby5560-nograph/kafka
./gradlew :streams:test --tests "org.apache.kafka.streams.state.internals.CacheRebalanceDeadlockTest"
```
