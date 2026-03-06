# DBCP-270 Deadlock Injection: ThreadCache + NamedCache

## Pattern

**Two-Object ABBA Cycle** (DBCP-270 from JaConTeBe catalog)

## Files Modified

1. `kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/NamedCache.java`
2. `kafka/streams/src/main/java/org/apache/kafka/streams/state/internals/ThreadCache.java`

## File Created

3. `kafka/streams/src/test/java/org/apache/kafka/streams/state/internals/ThreadCacheDeadlockTest.java`

## Lock Cycle

```
Lock A = ThreadCache.this     (intrinsic monitor)
Lock B = NamedCache.this      (intrinsic monitor)
```

### Thread 1 (resize): A -> B

```
ThreadCache.resize()                          [synchronized -> holds A]
  -> CircularIterator over caches
    -> synchronized(cache)                    [tries B]  <- BLOCKED
```

### Thread 2 (put): B -> A

```
ThreadCache.put()
  -> getOrCreateCache()                       [synchronized -> acquires+releases A]
  -> synchronized(cache)                      [holds B]
    -> cache.put() -> maybeEvict() -> evict()
      -> flush(eldest)                        [still holds B]
        -> validateNamespaceActive()
          -> checkNamespaceRegistration()
            -> parentCache.isNamespaceRegistered(name)
              -> getCache()                   [synchronized -> tries A]  <- BLOCKED
```

## Triggering Interleaving

1. Thread 1 enters `resize()`, acquires `ThreadCache.this` (Lock A)
2. Thread 2 enters `put()`, calls `getOrCreateCache()` (acquires+releases Lock A), then enters `synchronized(cache)` acquiring Lock B
3. Thread 1 reaches `synchronized(cache)` inside resize loop — blocked on Lock B
4. Thread 2's put triggers eviction -> flush -> validateNamespaceActive -> isNamespaceRegistered -> getCache — blocked on Lock A
5. **Deadlock**: Thread 1 holds A, waits for B. Thread 2 holds B, waits for A.

## Why It's Non-Deterministic

- The deadlock requires Thread 2 to be inside `synchronized(cache)` (holding B) when Thread 1 holds `ThreadCache.this` (A) and tries to acquire B
- Thread 2 must also trigger eviction (cache must be full enough) and the evicted entry must be dirty (triggering flush)
- The reverse callback (`validateNamespaceActive`) only fires during `flush(LRUNode)`, which only happens on dirty entry eviction
- Cache size, entry count, and thread scheduling all affect whether the interleaving occurs

## Why It Looks Natural

- The `parentCache` field is a reasonable back-reference for a child object to validate its parent's state
- "Stale namespace validation" is a plausible defensive check — ensuring the cache hasn't been deregistered while still in use
- The method names (`validateNamespaceActive`, `checkNamespaceRegistration`, `isNamespaceRegistered`) follow Kafka's naming conventions
- The validation is placed in `flush()`, a natural point to check invariants before writing data
- `isNamespaceRegistered()` is not itself synchronized — it delegates to the existing `getCache()` which is synchronized, hiding the lock acquisition 3 layers deep

## Call Depth Analysis

- **Forward edge (existing)**: `resize()` [sync] -> `synchronized(cache)` = 1 layer, same file
- **Reverse edge (injected)**: `flush(LRUNode)` -> `validateNamespaceActive()` -> `checkNamespaceRegistration()` -> `isNamespaceRegistered()` -> `getCache()` [sync] = **3 layers** across 2 files

## Changes Summary

### NamedCache.java
- Added `parentCache` field (reference back to owning ThreadCache)
- Converted 2-arg constructor to delegate to new 3-arg constructor
- Added 3-arg constructor accepting `ThreadCache parentCache`
- Added `validateNamespaceActive()` — checks if namespace is still registered with parent
- Added `checkNamespaceRegistration()` — calls `parentCache.isNamespaceRegistered(name)`
- Inserted `validateNamespaceActive()` call in `flush(LRUNode)` after trace logging

### ThreadCache.java
- Changed `getOrCreateCache()` to pass `this` to NamedCache 3-arg constructor
- Added `isNamespaceRegistered(namespace)` — non-synchronized bridge that calls `getCache()` (which IS synchronized)

## Verification

```bash
cd tasks/kafka-deadlock/dbcp270-graph/kafka
./gradlew :streams:test --tests "org.apache.kafka.streams.state.internals.ThreadCacheDeadlockTest"
```

The test spawns two threads (resizer + putter) and a deadlock detector polling `ThreadMXBean.findDeadlockedThreads()` every 5ms. It asserts that a deadlock is detected within 30 seconds.
