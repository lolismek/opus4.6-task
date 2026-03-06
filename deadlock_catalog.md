# JaConTeBe Deadlock Bug Catalog

**Source**: [JaConTeBe: A Benchmark Suite of Real-World Java Concurrency Bugs (ASE 2015)](https://mir.cs.illinois.edu/marinov/publications/LinETAL15JaConTeBe.pdf)
**Repository**: [ChopinLi-cp/JaConTeBe_TSVD](https://github.com/ChopinLi-cp/JaConTeBe_TSVD)
**Local clone**: `JaConTeBe_TSVD/`

## Overview

- **Total concurrency bugs in JaConTeBe**: 47
- **Total deadlock bugs**: 23 (from JaConTeBe) + 1 synthetic injection pattern
  - **Resource deadlocks** (cyclic lock ordering): 16 + 1 synthetic
  - **Wait-notify deadlocks** (missed signals / communication): 7
- **Projects**: DBCP, Derby, Groovy, Log4j, Lucene, Commons Pool, JDK6, JDK7
- **Synthetic patterns**: 1 (designed for injection into large codebases)

---

## Deadlock Classification Taxonomy

### Type A: Resource Deadlock (Cyclic Lock Ordering)
Two or more threads acquire locks in different orders, creating a cycle in the wait-for graph.

### Type B: Wait-Notify Deadlock (Communication Deadlock)
Threads wait on condition variables / wait-notify for signals that never arrive (missed notify, interrupted notifier, all threads waiting).

### Type C: Native Deadlock (subtype of Resource)
Resource deadlock involving native (JNI/C++) locks mixed with Java-level locks.

---

## Bug Catalog

### 1. DBCP-270 — Resource Deadlock
- **Source**: `jacontebe/dbcp/src/Dbcp270.java`
- **Project**: Apache Commons DBCP 1.2
- **Bug Report**: https://issues.apache.org/jira/browse/DBCP-270

**Pattern**: Pool return vs. pool eviction
```
Thread1: close(connection) → Lock[Pool] → Lock[Connection]
Thread2: evict()           → Lock[Connection] → Lock[Pool]
Cycle: Pool ↔ Connection
```

**Description**: Thread1 calls `poolableConnection.close()` which internally returns the object to the pool (acquires pool lock, then connection lock for validation). Thread2 calls `pool.evict()` which iterates idle objects (acquires connection lock during testing, then pool lock). Different lock acquisition orders create a cycle.

**Graph**:
```
   Pool ──→ Connection
    ↑           │
    └───────────┘
```

---

### 2. DBCP-65 — Resource Deadlock
- **Source**: `jacontebe/dbcp/src/Dbcp65.java`
- **Project**: Apache Commons DBCP 1.2
- **Bug Report**: https://issues.apache.org/jira/browse/DBCP-65

**Pattern**: Pool eviction vs. statement preparation
```
Thread1: evict()             → Lock[KeyedPool] → Lock[PoolingConnection]
Thread2: prepareStatement()  → Lock[PoolingConnection] → Lock[KeyedPool]
Cycle: KeyedPool ↔ PoolingConnection
```

**Description**: Thread1 evicts idle keyed objects (acquires pool lock, validates via PoolingConnection). Thread2 prepares a SQL statement through PoolingConnection (acquires connection lock, borrows from keyed pool). Inverse lock ordering.

**Graph**:
```
   KeyedPool ──→ PoolingConnection
      ↑               │
      └────────────────┘
```

---

### 3. DERBY-4129 — Resource Deadlock
- **Source**: `jacontebe/derby/src/Derby4129.java`
- **Project**: Apache Derby 10.5.1.1
- **Bug Report**: https://issues.apache.org/jira/browse/DERBY-4129

**Pattern**: Shared JDBC connection with concurrent BLOB reads
```
Thread1: executeQuery() → Lock[Connection] → Lock[BlobContainer]
Thread2: executeQuery() → Lock[BlobContainer] → Lock[Connection]
Cycle: Connection ↔ BlobContainer
```

**Description**: Two threads share one JDBC Connection and repeatedly execute queries reading 50KB BLOBs. Lock contention arises between connection-level and container-level locks during concurrent read/close operations.

---

### 4. DERBY-5447 — Resource Deadlock
- **Source**: `jacontebe/derby/src/org/apache/derby/impl/store/raw/data/Derby5447.java`
- **Project**: Apache Derby 10.5.1.1
- **Bug Report**: https://issues.apache.org/jira/browse/DERBY-5447

**Pattern**: Container close vs. page release (Observer pattern)
```
Thread1: releaseExclusive(page)    → Lock[Page] → Lock[Container]
Thread2: close(containerHandle)    → Lock[Container] → Lock[Page]  (via observer notification)
Cycle: Container ↔ Page
```

**Description**: A StoredPage is registered as an observer of a BaseContainerHandle. Thread2 closes the container (notifies observers, needs page lock). Thread1 releases the exclusive page lock (needs container lock). Observer pattern creates bidirectional dependency.

**Graph**:
```
   Container ──observes──→ Page
      ↑                     │
      └──releaseExclusive───┘
```

---

### 5. DERBY-5560 — Resource Deadlock
- **Source**: `jacontebe/derby/src/Derby5560.java`
- **Project**: Apache Derby 10.5.1.1
- **Bug Report**: https://issues.apache.org/jira/browse/DERBY-5560

**Pattern**: Logical connection close vs. pooled connection close
```
Thread1: logicalConnection.close()       → Lock[LogicalConn] → Lock[PooledConn]
Thread2: clientPooledConnection.close()  → Lock[PooledConn] → Lock[LogicalConn]
Cycle: LogicalConnection ↔ ClientXAConnection
```

**Description**: Thread1 closes the logical connection (needs to notify pooled connection). Thread2 closes the pooled connection (needs to invalidate logical connection). CountDownLatch ensures both start close operations near-simultaneously.

---

### 6. DERBY-764 — Resource Deadlock
- **Source**: `jacontebe/derby/src/org/apache/derby/impl/services/reflect/Derby764.java`
- **Project**: Apache Derby 10.5.1.1
- **Bug Report**: https://issues.apache.org/jira/browse/DERBY-764

**Pattern**: ClassLoader lock modification vs. lock release
```
Thread1: modifyJar()  → Lock[ClassLoader] → Lock[LockFactory]
Thread2: unlock()     → Lock[LockFactory] → Lock[ClassLoader]
Cycle: ClassLoader ↔ LockFactory
```

**Description**: Setup acquires EXCLUSIVE lock on the classloader. Thread1 calls `updateLoader.modifyJar()` which needs classloader lock and lock factory. Thread2 calls `operator.unlock()` which needs lock factory and classloader.

---

### 7. GROOVY-4736 — Resource Deadlock (Native)
- **Source**: `jacontebe/groovy/src/Groovy4736.java`
- **Project**: Apache Groovy 1.7.9
- **Bug Report**: http://jira.codehaus.org/browse/GROOVY-4736

**Pattern**: File write (synchronized) + cache clear vs. class loading
```
WriterThread:   Lock[Groovy4736.this] → Lock[GroovyClassLoader.cache]
CompilerThread: Lock[GroovyClassLoader.cache] → Lock[Groovy4736.this] (via resource loader callback)
Cycle: Instance ↔ ClassLoader
```

**Description**: Multiple writer threads write Groovy source files (synchronized method) then clear the classloader cache. Multiple compiler threads load classes via the classloader, which callbacks into the synchronized resource loader. Native method involvement in class loading makes this a native deadlock variant.

---

### 8. LOG4J-38137 — Wait-Notify Deadlock
- **Source**: `jacontebe/log4j/src/org/apache/log4j/Test38137.java`
- **Project**: Apache Log4j 1.2.13
- **Bug Report**: https://issues.apache.org/bugzilla/show_bug.cgi?id=38137

**Pattern**: AsyncAppender buffer saturation — all producers wait, no consumer can drain
```
10 AppendThreads: append(event) → buffer.wait() when full
Dispatcher:       cannot drain because all threads are blocked
All threads: waiting for notify() that never comes
```

**Description**: 10 threads each append 50 events to an AsyncAppender with a bounded buffer. When all threads fill the buffer simultaneously, they all call `wait()`. The dispatcher thread cannot drain because notification logic is broken — all producers are stuck waiting.

**Graph**:
```
   AppendThread₁ ─┐
   AppendThread₂ ─┤
   ...            ├──→ buffer.wait() ←── no notify()
   AppendThread₁₀ ┘         ↑
                    Dispatcher blocked
```

---

### 9. LOG4J-41214 — Resource Deadlock
- **Source**: `jacontebe/log4j/src/com/main/Test41214.java`
- **Project**: Apache Log4j 1.2.13
- **Bug Report**: https://issues.apache.org/bugzilla/show_bug.cgi?id=41214

**Pattern**: Logger formatting calls toString()/getMessage() on objects that also log
```
AnObjectThread:    Lock[AnObject] → Log → Lock[LogManager]
AnExceptionThread: Lock[AnException] → Log → Lock[LogManager]
RootLoggerThread:  Lock[LogManager] → format → Lock[AnObject] or Lock[AnException]
Cycle: LogManager ↔ AnObject/AnException
```

**Description**: Three threads interact: two threads hold locks on domain objects while logging, and one thread holds the LogManager lock while trying to format those domain objects (calling `toString()` / `getMessage()`). Classic callback-induced resource deadlock.

---

### 10. LUCENE-1544 — Wait-Notify Deadlock
- **Source**: `jacontebe/lucene/src/org/apache/lucene/Test1544.java`
- **Project**: Apache Lucene 2.4.0
- **Bug Report**: https://issues.apache.org/jira/browse/LUCENE-1544

**Pattern**: IndexWriter addIndexes holds write lock, waits for merge; merge needs read lock
```
MainThread:  Lock[WriteLock] → wait(runningMerges empty)
MergeThread: Lock[ReadLock]  ← blocked by WriteLock
Cycle: WriteLock holder waits for MergeThread; MergeThread waits for WriteLock release
```

**Description**: Main thread calls `addIndexes(readers)` which acquires the write lock and triggers optimization. The ConcurrentMergeScheduler spawns a merge thread that needs a read lock (blocked by write lock). Main thread waits for merges to complete — but merges can't start.

---

### 11. LUCENE-2783 — Wait-Notify Deadlock
- **Source**: `jacontebe/lucene/src/org/apache/lucene/index/Test2783.java`
- **Project**: Apache Lucene 2.9.3
- **Bug Report**: https://issues.apache.org/jira/browse/LUCENE-2783

**Pattern**: Concurrent index writers + readers with merge contention
```
2 IndexerThreads:  updateDocument() → Lock[SegmentWrite] → wait(merge)
2 SearcherThreads: open(reader)     → Lock[SegmentRead] → wait(write)
MergeScheduler:    merge()          → needs both locks
```

**Description**: Stress test with 2 indexer threads and 2 searcher threads on the same directory. Deadlock involves both resource locks and wait-notify between the merge scheduler, document update, and index reader operations. This deadlock mixes resource and communication patterns.

---

### 12. POOL-146 — Wait-Notify Deadlock
- **Source**: `jacontebe/pool/src/Test146.java`
- **Project**: Apache Commons Pool 1.5
- **Bug Report**: https://issues.apache.org/jira/browse/POOL-146

**Pattern**: Keyed pool — borrow on key "one" blocks, then borrow on key "two" also blocks
```
MainThread:    borrowObject("one") ✓ → sleep → borrowObject("two") BLOCKS
BorrowThread:  borrowObject("one") BLOCKS (maxActive=1)
Bug: Global exhaustion check incorrectly blocks key "two" borrow
```

**Description**: With `maxActive=1` per key, borrowing from key "one" succeeds. A second thread tries to borrow "one" and blocks (expected). But when the main thread tries to borrow from a *different* key "two", it also blocks due to a bug in the global pool exhaustion logic. Neither thread can proceed.

---

### 13. POOL-149 — Wait-Notify Deadlock
- **Source**: `jacontebe/pool/src/Test149.java`
- **Project**: Apache Commons Pool 1.5
- **Bug Report**: https://issues.apache.org/jira/browse/POOL-149

**Pattern**: borrowObject + invalidateObject race — missed notification
```
Thread1: borrowObject() → invalidateObject() → destroys object (may not notify)
Thread2: borrowObject() → wait() for available object → never notified
```

**Description**: With `maxActive=1`, Thread1 borrows then invalidates an object. Thread2 is waiting to borrow. If `invalidateObject()` doesn't properly notify waiting threads (e.g., exception during destruction), Thread2 waits forever.

---

### 14. POOL-162 — Wait-Notify Deadlock
- **Source**: `jacontebe/pool/src/Test162.java`
- **Project**: Apache Commons Pool 1.5
- **Bug Report**: https://issues.apache.org/jira/browse/POOL-162

**Pattern**: Interrupted thread was supposed to be the notifier
```
MainThread:      borrow("one") ✓ → start WaitingThread → interrupt WaitingThread → return("one") → borrow("two") BLOCKS
WaitingThread:   borrow("one") → wait() → interrupted → dies without notifying
```

**Description**: Thread interruption kills a thread that was in a `wait()` state. That thread was supposed to eventually `notify()` other waiters. When it dies, the notification chain breaks, and subsequent `borrowObject()` calls block forever. Root cause: improper handling of `InterruptedException` in wait loops.

---

### 15. JDK-6492872 — Resource Deadlock
- **Source**: `jacontebe/jdk6/src/Test6492872.java`
- **Project**: OpenJDK 6
- **Bug Report**: https://bugs.openjdk.java.net/browse/JDK-6492872

**Pattern**: SSLEngine wrap/unwrap vs. delegated task execution
```
MainThread:     SSLEngine.wrap()/unwrap() → Lock[SSLEngine.state]
TaskDispatcher: SSLEngine.getDelegatedTask().run() → Lock[SSLEngine.state]
Cycle: Internal SSLEngine state contention
```

**Description**: Main thread does SSL handshake operations (wrap/unwrap). A separate task dispatcher thread executes delegated tasks from the SSLEngine. Both contend for the SSLEngine's internal state locks.

---

### 16. JDK-6582568 — Resource Deadlock
- **Source**: `jacontebe/jdk6/src/Test6582568.java`
- **Project**: OpenJDK 6
- **Bug Report**: https://bugs.openjdk.java.net/browse/JDK-6582568

**Pattern**: Classic — Hashtable.equals() with opposite ordering
```
Thread1: p1.equals(p2) → Lock[p1] → Lock[p2]
Thread2: p2.equals(p1) → Lock[p2] → Lock[p1]
Cycle: p1 ↔ p2
```

**Description**: Simplest possible resource deadlock. Two `Hashtable` instances; two threads call `equals()` in opposite directions. Since `Hashtable` is synchronized, `equals()` holds `this` lock and then calls `equals()` on the other table (acquiring its lock). Clean A-B / B-A deadlock.

**Graph**:
```
   p1 ──equals──→ p2
   ↑               │
   └───equals───────┘
```

---

### 17. JDK-6588239 — Resource Deadlock
- **Source**: `jacontebe/jdk6/src/Test6588239.java`
- **Project**: OpenJDK 6
- **Bug Report**: https://bugs.openjdk.java.net/browse/JDK-6588239

**Pattern**: Annotation parsing — getAnnotations() vs. AnnotationType.getInstance()
```
Thread1: Class.getAnnotations()        → Lock[Class.annotationData] → Lock[AnnotationType.cache]
Thread2: AnnotationType.getInstance()  → Lock[AnnotationType.cache] → Lock[Class.annotationData]
Cycle: annotationData ↔ AnnotationType.cache
```

---

### 18. JDK-6927486 — Resource Deadlock
- **Source**: `jacontebe/jdk6/src/Test6927486.java`
- **Project**: OpenJDK 6
- **Bug Report**: https://bugs.openjdk.java.net/browse/JDK-6927486

**Pattern**: Bidirectional Hashtable serialization
```
ht1 contains → mo1 (references ht2)
ht2 contains → mo2 (references ht1)

Thread1: serialize(ht1) → Lock[ht1] → serialize(mo1) → Lock[ht2]
Thread2: serialize(ht2) → Lock[ht2] → serialize(mo2) → Lock[ht1]
Cycle: ht1 ↔ ht2
```

**Description**: Two Hashtables contain custom objects that reference the other Hashtable. Serialization locks the Hashtable being serialized, then encounters the other Hashtable in the object graph. Classic cross-reference deadlock.

---

### 19. JDK-6934356 — Resource Deadlock
- **Source**: `jacontebe/jdk6/src/Test6934356.java`
- **Project**: OpenJDK 6
- **Bug Report**: https://bugs.openjdk.java.net/browse/JDK-6934356

**Pattern**: Bidirectional Vector serialization with barrier synchronization
```
v1 contains [barrier, v2]
v2 contains [barrier, v1]

Thread1: serialize(v1) → Lock[v1] → barrier.await() → serialize(v2) → needs Lock[v2]
Thread2: serialize(v2) → Lock[v2] → barrier.await() → serialize(v1) → needs Lock[v1]
Cycle: v1 ↔ v2  (barrier ensures simultaneous execution)
```

---

### 20. JDK-6977738 — Resource Deadlock
- **Source**: `jacontebe/jdk6/src/Test6977738.java`
- **Project**: OpenJDK 6
- **Bug Report**: https://bugs.openjdk.java.net/browse/JDK-6977738

**Pattern**: System.Properties lock vs. ClassLoader lock
```
Thread1: Lock[System.Properties] → store() → needs ClassLoader
Thread2: ClassLoader.getResource() → needs Lock[System.Properties]
Cycle: Properties ↔ ClassLoader
```

---

### 21. JDK-6648001 — Wait-Notify Deadlock
- **Source**: `jacontebe/jdk6/src/Test6648001.java`
- **Project**: OpenJDK 6
- **Bug Report**: https://bugs.openjdk.java.net/browse/JDK-6648001

**Pattern**: HTTP authentication serialized requests — missed notify
```
Thread1: HTTP request → auth fails (returns null) → should notify next waiter
Thread2: HTTP request → wait for auth response → never notified
```

**Description**: With `http.auth.serializeRequests=true`, authentication requests are serialized. When the first request's authenticator returns null, the notification to wake the second waiter may be lost, causing permanent blocking.

---

### 22. JDK-7122142 — Resource Deadlock
- **Source**: `jacontebe/jdk7/src/Test7122142.java`
- **Project**: OpenJDK 7
- **Bug Report**: https://bugs.openjdk.java.net/browse/JDK-7122142

**Pattern**: Mutually-referencing annotation types
```
@AnnA annotated with @AnnB
@AnnB annotated with @AnnA

Thread1: AnnA.getDeclaredAnnotations() → Lock[AnnA.parsing] → parse @AnnB → Lock[AnnB.parsing]
Thread2: AnnB.getDeclaredAnnotations() → Lock[AnnB.parsing] → parse @AnnA → Lock[AnnA.parsing]
Cycle: AnnA ↔ AnnB
```

**Description**: Two annotation types reference each other. When two threads simultaneously parse them, each thread holds the parsing lock for one annotation and needs the lock for the other. Identical pattern to Hashtable.equals() but with annotation metadata.

---

### 23. JDK-8012019 — Wait-Notify Deadlock
- **Source**: `jacontebe/jdk7/src/Test8012019.java`
- **Project**: OpenJDK 7
- **Bug Report**: https://bugs.openjdk.java.net/browse/JDK-8012019

**Pattern**: FileChannel concurrent reads with thread interruption
```
Thread1: fc.read(bb, pos) → Lock[FileChannel] → interrupted → stuck
Thread2: fc.read(bb, pos) → Lock[FileChannel] → blocked
Main:    interrupt(Thread1) → join() → waits forever
```

---

### 24. JDK-8010939 — Resource Deadlock
- **Source**: `jacontebe/jdk7/src/Test8010939.java`
- **Project**: OpenJDK 7
- **Bug Report**: https://bugs.openjdk.java.net/browse/JDK-8010939

**Pattern**: LogManager configuration vs. Logger creation
```
Thread1: Logger.getLogger()          → Lock[LoggerHierarchy] → Lock[LogManager.config]
Thread2: LogManager.readConfiguration() → Lock[LogManager.config] → Lock[LoggerHierarchy]
Cycle: LoggerHierarchy ↔ LogManager.config
```

---

## Abstract Pattern Summary

| # | Bug ID | Type | Pattern | Lock Cycle | Threads |
|---|--------|------|---------|-----------|---------|
| 1 | DBCP-270 | Resource | Pool return vs evict | Pool ↔ Connection | 2 |
| 2 | DBCP-65 | Resource | Pool evict vs prepare | KeyedPool ↔ PoolingConn | 2 |
| 3 | DERBY-4129 | Resource | Concurrent BLOB reads | Connection ↔ Container | 2 |
| 4 | DERBY-5447 | Resource | Container close vs page release | Container ↔ Page | 2 |
| 5 | DERBY-5560 | Resource | Logical vs pooled conn close | LogicalConn ↔ PooledConn | 2 |
| 6 | DERBY-764 | Resource | ClassLoader mod vs unlock | ClassLoader ↔ LockFactory | 2 |
| 7 | GROOVY-4736 | Resource/Native | File write vs class load | Instance ↔ ClassLoader | 3+ |
| 8 | LOG4J-38137 | Wait-Notify | Buffer saturation | All producers wait | 10 |
| 9 | LOG4J-41214 | Resource | Logger vs object locks | LogManager ↔ DomainObj | 3 |
| 10 | LUCENE-1544 | Wait-Notify | Write lock vs merge | WriteLock holder ← MergeThread | 2 |
| 11 | LUCENE-2783 | Wait-Notify | Index write vs read vs merge | Multi-resource | 5 |
| 12 | POOL-146 | Wait-Notify | Keyed pool global exhaustion | Global pool state | 2 |
| 13 | POOL-149 | Wait-Notify | Invalidate without notify | Pool condition var | 2 |
| 14 | POOL-162 | Wait-Notify | Interrupted notifier | Pool condition var | 2 |
| 15 | JDK-6492872 | Resource | SSLEngine ops vs tasks | SSLEngine.state | 2 |
| 16 | JDK-6582568 | Resource | Hashtable.equals() | ht1 ↔ ht2 | 2 |
| 17 | JDK-6588239 | Resource | Annotation parsing | annotData ↔ AnnotType | 2 |
| 18 | JDK-6927486 | Resource | Hashtable serialization | ht1 ↔ ht2 | 2 |
| 19 | JDK-6934356 | Resource | Vector serialization | v1 ↔ v2 | 2 |
| 20 | JDK-6977738 | Resource | Properties vs ClassLoader | Props ↔ ClassLoader | 2 |
| 21 | JDK-6648001 | Wait-Notify | HTTP auth missed notify | AuthInfo monitor | 2 |
| 22 | JDK-7122142 | Resource | Mutual annotation refs | AnnA ↔ AnnB | 2 |
| 23 | JDK-8010939 | Resource | LogManager config vs create | LogHierarchy ↔ Config | 2 |
| 25 | SYNTH-001 | Resource | 3-node conditional callback cycle | CacheMgr → ConnPool → SessionStore → CacheMgr | 3 |

---

### 25. SYNTH-001 — Resource Deadlock (Synthetic Injection Pattern)
- **Source**: Designed for injection into large Java codebases; adapted from cross-language concurrency research
- **Type**: Resource deadlock with callback-induced closing edge
- **Target**: Any Java project with caching + connection pooling + session management

**Pattern**: 3-Node Conditional Callback Cycle
```
Thread1 (cache read):          synchronized(CacheManager) → ConnPool.writeLock
Thread2 (background refresh):  ConnPool.writeLock → SessionStore.lock
Thread3 (session listener):    SessionStore.lock → listener callback → synchronized(CacheManager)
Cycle: CacheManager → ConnPool → SessionStore → CacheManager
```

**Description**: Three classes each manage their own lock. CacheManager uses `synchronized` methods (intrinsic monitor). ConnPool uses a `ReentrantReadWriteLock`, with the write lock acquisition hidden in a superclass (`AbstractPool.acquireExclusive()`). SessionStore uses a `ReentrantLock` with a `Condition` variable.

Thread1 calls `CacheManager.get()` (acquires intrinsic lock on cache miss), which calls `connPool.fetchFromBackend()` (tries to acquire write lock via `super.acquireExclusive()`). Thread2 is a background refresh task that already holds the write lock and calls `sessionStore.refreshSession()` when the session is expired (tries to acquire SessionStore.lock). Thread3 is inside `SessionStore.refreshSession()` (holds SessionStore.lock), which fires a registered `SessionListener`; the listener calls `cacheManager.invalidate()` (tries to acquire intrinsic lock).

**Conditional closing edge**: The listener callback (SessionStore → CacheManager) only fires when the refreshed session maps to a different shard or host than the previous one (e.g., during shard rebalancing or leader election). Under normal session renewal (same shard), the listener is a no-op. The deadlock is invisible during regular operation and unit testing.

**Layers of hiddenness**:
1. **Inheritance**: ConnPool's write lock acquisition is in `AbstractPool.acquireExclusive()`, called via `super.acquireExclusive()`. The agent must trace the class hierarchy (2 levels) to find the lock.
2. **Mixed lock primitives**: `synchronized` (CacheManager) + `ReentrantReadWriteLock` (ConnPool) + `ReentrantLock` + `Condition` (SessionStore). Syntactically disjoint — no single tool signature or grep pattern finds all three.
3. **Callback closing edge**: The closing edge goes through a `SessionListener` interface. The listener was registered during initialization (e.g., in a Spring `@PostConstruct` or constructor), far from the deadlock site. The dependency is data-flow (who registered this listener?) not control-flow.
4. **ReadWriteLock asymmetry**: The ConnPool edge only blocks when the *write* lock is held (exclusive). Read locks are shared and don't block. The deadlock only manifests during write-heavy paths (pool maintenance, connection refresh), not during normal read-heavy cache lookups.

**Secondary deadlock vector (Condition.await())**: If a thread holds ConnPool.writeLock and calls `sessionStore.awaitReady()`, the `Condition.await()` releases SessionStore.lock but the thread still holds the write lock. Another thread needing the write lock to complete the work that would signal the condition is blocked — a hold-and-wait deadlock orthogonal to the main 3-node cycle. This also confuses static analysis: tools see "lock released" at the `await()` point and may stop tracing.

**Red herrings**:
- 2–3 `synchronized` methods on CacheManager that look suspicious but are all protected by the same intrinsic monitor (no cycle possible)
- A `ConcurrentHashMap` in ConnPool with correct `computeIfAbsent()` usage that looks racy but isn't
- A `volatile` field in SessionStore read under lock unnecessarily — looks like a concurrency bug but is harmless

**Graph**:
```
   CacheManager ──synchronized──→ ConnPool
   (intrinsic)                    (RWLock.writeLock,
        ↑                         hidden in AbstractPool)
        │                             │
        │                             │ [only on session expiry]
        │                             ↓
   ←──callback──── SessionStore
   [only on shard  (ReentrantLock + Condition)
    rebalance]
```

---

## Recurring Structural Patterns

### Pattern 1: Two-Object Cycle (A ↔ B)
Most common. Two objects with bidirectional references or operations that touch both.
**Bugs**: DBCP-270, DBCP-65, DERBY-5447, DERBY-5560, DERBY-764, JDK-6582568, JDK-6588239, JDK-6927486, JDK-6934356, JDK-6977738, JDK-7122142, JDK-8010939

### Pattern 2: Callback-Induced Cycle
Object A calls into Object B while holding lock; B calls back into A.
**Bugs**: LOG4J-41214, GROOVY-4736, DERBY-5447 (observer), LUCENE-1544

### Pattern 3: All-Waiters / Missed Notify
All threads enter wait state; no thread remains to call notify.
**Bugs**: LOG4J-38137, POOL-146, POOL-149, POOL-162, JDK-6648001

### Pattern 4: Serialization Graph Cycle
Object graph has bidirectional references; serialization locks objects as it traverses.
**Bugs**: JDK-6927486, JDK-6934356

### Pattern 5: Infrastructure Lock vs Application Lock
System-level lock (ClassLoader, LogManager, System.Properties) vs. application-level lock.
**Bugs**: JDK-6977738, JDK-8010939, GROOVY-4736

### Pattern 6: 3-Node Conditional Callback Cycle
Three locks across three classes form an A→B→C→A cycle. The closing edge goes through a callback/listener interface and only triggers under a specific runtime condition. Mixed lock primitives (intrinsic + explicit) and inheritance-hidden acquisition make the cycle invisible to single-primitive analysis and direct grep.
**Bugs**: SYNTH-001
