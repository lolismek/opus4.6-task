# DERBY-5560 Pattern Injection: Wrapper/Physical Resource Cycle in Kafka Streams

## Pattern

**Wrapper/Physical Resource Cycle** (DERBY-5560) — a lock-ordering cycle between a wrapper/manager object and the physical resource it manages, triggered when a callback from the physical resource re-enters the wrapper.

## Target Pair

| Role | Class | Lock |
|------|-------|------|
| Wrapper | `ThreadCache` | `synchronized` methods → `ThreadCache.this` |
| Physical | `NamedCache` | `synchronized` methods → `NamedCache.this` |
| Bridge | `CachingKeyValueStore` | Flush listener callback connecting NamedCache back to ThreadCache |

## Lock Cycle

### Edge 1: ThreadCache.this → NamedCache.this (already exists)

```
ThreadCache.close(namespace)     [synchronized on ThreadCache.this]
  → removed.sizeInBytes()        [synchronized on NamedCache.this]
  → removed.close()              [synchronized on NamedCache.this]
```

### Edge 2: NamedCache.this → ThreadCache.this (injected via callback)

```
ThreadCache.put()                                   [NOT synchronized on ThreadCache.this]
  → synchronized(cache)                             [acquires NamedCache.this]
    → cache.put() → maybeEvict() → cache.evict()
      → flush(eldest) → listener.apply()
        → CachingKeyValueStore.putAndMaybeForward()
          → ensureCacheCapacity()                   [INJECTED]
            → ThreadCache.rebalanceIfNeeded()       [INJECTED]
              → ThreadCache.resize()                [synchronized on ThreadCache.this → BLOCKED]
```

## Deadlock Interleaving

1. **Thread A** (stream thread): `ThreadCache.put()` → `synchronized(cache)` acquires **NamedCache.this**
2. **Thread B** (cleanup thread): `ThreadCache.close(ns)` acquires **ThreadCache.this**
3. **Thread B**: calls `removed.sizeInBytes()` → tries **NamedCache.this** → **BLOCKED**
4. **Thread A**: eviction callback → `ensureCacheCapacity()` → `rebalanceIfNeeded()` → `resize()` → tries **ThreadCache.this** → **BLOCKED**
5. **DEADLOCK**

## Files Modified

### `streams/.../ThreadCache.java`
Added `rebalanceIfNeeded()` method (package-private, NOT synchronized) after `resize()`:
```java
void rebalanceIfNeeded() {
    if (sizeInBytes.get() > maxCacheSizeBytes) {
        resize(maxCacheSizeBytes);
    }
}
```

### `streams/.../CachingKeyValueStore.java`
Added `ensureCacheCapacity()` helper and call at end of `putAndMaybeForward()`:
```java
private void ensureCacheCapacity(final InternalProcessorContext<?, ?> context) {
    if (context.cache() != null) {
        context.cache().rebalanceIfNeeded();
    }
}
```

### `streams/.../CacheRebalanceDeadlockTest.java` (new)
Verification test that reproduces the deadlock using two threads and `ThreadMXBean.findDeadlockedThreads()`.

## Why It Looks Natural

- `rebalanceIfNeeded()` is a plausible cache management method — after flushing entries, check if cache still exceeds its size limit
- `ensureCacheCapacity()` blends in with the many other ThreadCache calls CachingKeyValueStore already makes
- No new `synchronized` blocks or lock fields — only calls to existing synchronized methods

## Why It's Hard to Diagnose

- **Misleading symptoms**: manifests as shutdown hang or stuck put — looks like timeout/resource leak
- **Causal distance**: reverse edge traverses 3 files and 6+ method calls
- **Non-local fix**: correct fix requires understanding callback chain across ThreadCache, NamedCache, and CachingKeyValueStore

## Verification

```bash
cd tasks/kafka-deadlock/derby5560-nograph/kafka
./gradlew :streams:test --tests "org.apache.kafka.streams.state.internals.CacheRebalanceDeadlockTest"
```
