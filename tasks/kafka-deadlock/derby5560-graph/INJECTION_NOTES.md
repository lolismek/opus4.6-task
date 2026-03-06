# DERBY-5560 Deadlock Injection: Kafka Streams CachingWindowStore

## Pattern

**Wrapper/Physical Resource Cycle** (DERBY-5560): Two threads close related resources in opposite directions. Thread 1 closes the wrapper (holds wrapper lock, needs physical lock), Thread 2 manages the physical resource (holds physical lock, needs wrapper lock).

## Files Modified

1. **CachingWindowStore.java** — Added `recordFlushCompletion()`, `verifyPersistedWindow()`, `fetchPersistedRange()` helper methods after `putAndMaybeForward()`. Added call to `recordFlushCompletion()` at end of `putAndMaybeForward()`.
2. **NamedCache.java** — Added flush-before-clear step in `close()` method (red herring path).
3. **CachingWindowStoreDeadlockTest.java** — New verification test.

## Lock Cycle

```
Thread 1 (store close):
  CachingWindowStore.close()         -> acquires CachingWindowStore.this  [synchronized method]
    -> ThreadCache.flush(namespace)  -> acquires NamedCache (via synchronized block)
    -> ThreadCache.close(namespace)  -> acquires ThreadCache.this  [synchronized method]
                                                                    <- BLOCKED if Thread 2 holds it

Thread 2 (cache resize):
  ThreadCache.resize()               -> acquires ThreadCache.this  [synchronized method]
    -> synchronized(cache)           -> acquires NamedCache
      -> cache.evict()              -> (re-entrant NamedCache, already held)
        -> flush(eldest)            -> listener.apply(entries)
          -> putAndMaybeForward()
            -> recordFlushCompletion()
              -> verifyPersistedWindow()
                -> fetchPersistedRange()
                  -> this.fetch(key, ts, ts)  -> tries to acquire CachingWindowStore.this
                                                                    <- BLOCKED if Thread 1 holds it
```

**Cycle: CachingWindowStore.this <-> ThreadCache.this** (classic ABBA deadlock)

## Triggering Interleaving

1. Thread 1 calls `cachingStore.close()`, acquires `CachingWindowStore.this` monitor
2. Thread 2 calls `cache.resize(smallerSize)`, acquires `ThreadCache.this` monitor
3. Thread 1 proceeds to `ThreadCache.close(namespace)` — tries to acquire `ThreadCache.this` — **BLOCKED**
4. Thread 2 proceeds through eviction chain to `fetchPersistedRange()` — tries to acquire `CachingWindowStore.this` — **BLOCKED**
5. **Deadlock**

## Why It Looks Natural

- **Consistency check on flush**: Verifying flushed entries persist correctly is a legitimate concern during eviction under memory pressure
- **Flush-before-close in NamedCache**: "Don't lose dirty data on cache teardown" is standard robustness practice
- **No new locks**: Zero new `synchronized` blocks or lock fields; all acquisitions go through existing synchronized methods
- **Conditional trigger**: The `maxObservedTimestamp > timestamp + windowSize` condition means checks only fire for expired windows

## Why It's Hard to Diagnose

- **Causal distance**: The reverse edge traverses 6 method calls across 3 files
- **Red herring**: NamedCache.close() flush creates a misleading second path (re-entrant on same thread, no deadlock)
- **Blends in**: `recordFlushCompletion()` sits among existing delegation calls in `putAndMaybeForward()`
- **Indirection**: `fetchPersistedRange()` -> `fetch()` hides that it's calling a synchronized method on `this`

## Verification

```bash
./gradlew :streams:test --tests "*CachingWindowStoreDeadlockTest*"
```

Expected: test detects deadlock via `ThreadMXBean.findDeadlockedThreads()` within seconds and fails with descriptive message.
