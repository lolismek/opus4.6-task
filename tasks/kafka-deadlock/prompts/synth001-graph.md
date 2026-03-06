Inject a deadlock bug into the Kafka Streams module following the SYNTH-001 pattern (3-Node Conditional Callback Cycle).

**Working directory**: `tasks/kafka-deadlock/synth001-graph/kafka/` — this is your copy of the Kafka repo. All source modifications go here.

**Pattern**: 3 threads, 3 locks, 3 classes forming a cycle: A→B→C→A. Each edge uses a different lock primitive. The closing edge (C→A) is conditional — it only fires under a specific runtime condition (e.g., rebalancing, leader election), making the deadlock intermittent and hard to reproduce.

Key difficulty features:
- Mixed lock primitives: `synchronized` (intrinsic monitor), `ReentrantReadWriteLock` (write lock hidden in superclass), `ReentrantLock` + `Condition`
- The write lock acquisition is buried 2 class levels deep via inheritance (e.g., `AbstractPool.acquireExclusive()`)
- The closing C→A edge fires via a listener/callback pattern only under a specific condition
- Read lock (shared) does NOT create the cycle — only write lock (exclusive) does

**Target**: `tasks/kafka-deadlock/synth001-graph/kafka/streams/src/main/java/org/apache/kafka/streams/` — only modify files in the streams module. You need to find 3 classes that form a natural interaction triangle.

**Lock graph**: Read `tasks/kafka-deadlock/lock_graph/kafka_streams_both_lock_graph.md` for existing lock edges. Look for chains of 3 classes where A→B and B→C edges already exist or are plausible, so you mainly need to add the conditional C→A callback.

**Reference**: See `tasks/zookeeper-deadlock/INJECTION_NOTES.md` for style reference (a simpler 2-node injection into ZooKeeper). Your injection is harder: 3 nodes, mixed primitives, conditional edge.

**Quality criteria**:
- Reuse existing lock fields and synchronized methods as much as possible. You may add ONE new lock field if needed for the ReentrantReadWriteLock, but justify it as a plausible cache/read-heavy optimization.
- Bury lock acquisitions 2-3 call layers deep.
- The conditional edge must depend on real Kafka Streams state (e.g., `State.REBALANCING`, topology version change, task migration).
- The added code must look like plausible features (caching, session management, metrics).
- Write a verification test in `tasks/kafka-deadlock/synth001-graph/kafka/streams/src/test/java/` that triggers the deadlock by forcing the conditional edge.

**Difficulty guidance** (the injected bug should be genuinely hard for a coding agent to diagnose and fix):
- **Condition specificity**: The closing C→A edge should only fire under a narrow runtime condition. An agent must understand the Kafka Streams lifecycle to realize when the condition is true. The deadlock should be impossible to trigger in simple unit tests without carefully setting up that condition.
- **Causal distance**: The 3-node cycle spans 3 files. Each edge should traverse 2+ intermediate calls, so the full cycle is 6-9 call hops long. An agent must trace the entire chain to understand the cycle.
- **Invisibility to tooling**: The mixed lock primitives (synchronized, ReentrantReadWriteLock, ReentrantLock) mean that simple grep for `synchronized` won't reveal the full cycle. The write lock hidden behind inheritance is especially hard for static analysis.
- **Plausible-looking red herrings**: The classes involved should have many other lock acquisitions that look relevant but aren't part of the cycle. For example, DefaultStateUpdater has 3 ReentrantLocks — only one should participate in the cycle.
- **Fix is non-local**: Breaking the cycle should require changing the design of the callback/listener interaction, not just reordering two lines in one file.
- Keep it fair — each individual edge should be traceable, and the cycle should be logically coherent.

**Deliverables**:
1. Modified source files in this kafka copy.
2. A verification test.
3. Write `tasks/kafka-deadlock/synth001-graph/INJECTION_NOTES.md` documenting: pattern used, files modified, full 3-node lock cycle, conditional edge trigger, why it looks natural.
