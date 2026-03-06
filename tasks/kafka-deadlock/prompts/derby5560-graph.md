Inject a deadlock bug into the Kafka Streams module following the DERBY-5560 pattern (Wrapper/Physical Resource Cycle).

**Working directory**: `tasks/kafka-deadlock/derby5560-graph/kafka/` — this is your copy of the Kafka repo. All source modifications go here.

**Pattern**: Two threads close related resources in opposite directions. Thread 1 closes a logical wrapper (holds wrapper lock, then needs physical resource lock to detach). Thread 2 closes the physical resource (holds physical lock, then needs wrapper lock to invalidate). The abstract template is "close_wrapper() → lock(wrapper) → detach(physical) → lock(physical)" vs "close_physical() → lock(physical) → invalidate(wrapper) → lock(wrapper)."

**Target**: `tasks/kafka-deadlock/derby5560-graph/kafka/streams/src/main/java/org/apache/kafka/streams/` — only modify files in the streams module. Look for wrapper/delegate patterns (e.g., classes that wrap other locked classes, caching layers around stores, stream thread wrapping partition groups).

**Lock graph**: Read `tasks/kafka-deadlock/lock_graph/kafka_streams_both_lock_graph.md` for the existing lock ordering edges. Look for wrapper→delegate lock edges that you can close with a reverse edge during cleanup/close operations.

**Reference**: See `tasks/zookeeper-deadlock/INJECTION_NOTES.md` for a worked example of a similar two-object cycle injected into ZooKeeper.

**Quality criteria**:
- Do NOT add new `synchronized` blocks or lock fields. Only add calls to methods that are ALREADY synchronized.
- Bury the lock acquisition 2-3 call layers deep (add helper/indirection methods).
- The added code must look like a plausible feature (resource cleanup, state validation on close).
- The deadlock must be non-deterministic (requires specific thread interleaving).
- Write a verification test in `tasks/kafka-deadlock/derby5560-graph/kafka/streams/src/test/java/` that reliably triggers the deadlock using concurrent threads + `ThreadMXBean.findDeadlockedThreads()`.

**Difficulty guidance** (the injected bug should be genuinely hard for a coding agent to diagnose and fix):
- **Misleading symptoms**: The deadlock manifests during shutdown/close, which agents often misattribute to timeout issues or resource leaks rather than lock ordering. Choose a close() path where the symptom (hang on shutdown) doesn't immediately point to a lock cycle.
- **Causal distance**: The wrapper→physical and physical→wrapper lock edges should each traverse 2-3 method calls across files. Don't put both directions in the same class.
- **Fix is non-local**: The correct fix should require reordering operations or breaking a dependency in a way that touches the close/cleanup logic of both classes — not just adding a `tryLock` in one place.
- **Invisibility to simple tooling**: If the wrapper delegates most calls to the physical resource, the one call that creates the reverse edge should blend in with the many safe delegation calls.
- Keep it fair — the bug should be findable by tracing the close() call chains, just not obvious.

**Deliverables**:
1. Modified source files in this kafka copy.
2. A verification test.
3. Write `tasks/kafka-deadlock/derby5560-graph/INJECTION_NOTES.md` documenting: pattern used, files modified, lock cycle (with exact call chains), triggering interleaving, why it looks natural.
