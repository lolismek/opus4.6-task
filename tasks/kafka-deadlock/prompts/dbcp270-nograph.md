Inject a deadlock bug into the Kafka Streams module following the DBCP-270 pattern (Two-Object ABBA Cycle).

**Working directory**: `tasks/kafka-deadlock/dbcp270-nograph/kafka/` — this is your copy of the Kafka repo. All source modifications go here.

**Pattern**: Two threads acquire two locks in opposite orders. Thread 1: lock(A) → lock(B). Thread 2: lock(B) → lock(A). The abstract template is "manager holds its lock and calls into a managed object" vs "managed object holds its lock and calls back into the manager."

**Target**: `tasks/kafka-deadlock/dbcp270-nograph/kafka/streams/src/main/java/org/apache/kafka/streams/` — only modify files in the streams module. You will need to read the source code to understand which classes use synchronization and how they interact.

**Reference**: See `tasks/zookeeper-deadlock/INJECTION_NOTES.md` for a worked example of this exact pattern injected into ZooKeeper (Leader ↔ LearnerHandler).

**Quality criteria**:
- Do NOT add new `synchronized` blocks or lock fields. Only add calls to methods that are ALREADY synchronized.
- Bury the lock acquisition 2-3 call layers deep (add helper/indirection methods).
- The added code must look like a plausible feature (monitoring, validation, diagnostics).
- The deadlock must be non-deterministic (requires specific thread interleaving).
- Write a verification test in `tasks/kafka-deadlock/dbcp270-nograph/kafka/streams/src/test/java/` that reliably triggers the deadlock using concurrent threads + `ThreadMXBean.findDeadlockedThreads()`.

**Difficulty guidance** (the injected bug should be genuinely hard for a coding agent to diagnose and fix):
- **Causal distance**: The two lock acquisitions that form the cycle should be far apart in the call graph — not in the same method or even the same file. Route through 2-3 intermediate method calls so that tracing the lock ordering requires following a chain across files.
- **Reasoning requires global state**: The reverse edge should go through a code path that involves reading or reacting to shared state (e.g., checking task status, rebalance state), so that understanding *why* the call happens requires understanding the broader system.
- **Plausible-looking red herrings**: The files you modify should already have other synchronized methods that are NOT part of the cycle. An agent investigating the deadlock should encounter several lock acquisitions that look suspicious but are actually safe.
- Keep it fair — the bug should be findable by carefully tracing lock orderings, just not obvious at a glance.

**Deliverables**:
1. Modified source files in this kafka copy.
2. A verification test.
3. Write `tasks/kafka-deadlock/dbcp270-nograph/INJECTION_NOTES.md` documenting: pattern used, files modified, lock cycle (with exact call chains), triggering interleaving, why it looks natural.
