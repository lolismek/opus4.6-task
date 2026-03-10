# Arx — Engineering Async Assessment

**Author:** Alex Jerpelea
**Date:** March 2026
**Time spent:** ~30 hours over 5 days (pattern cataloging, pipeline development, task injection, Docker/Fray infrastructure debugging, evaluation runs, analysis)

---

## Deliverable Locations

| Deliverable | Location |
|-------------|----------|
| **Harbor task** (primary) | `harbor_tasks_clean/synth001-nograph/` |
| Task instruction | `harbor_tasks_clean/synth001-nograph/instruction.md` |
| Reference solution | `harbor_tasks_clean/synth001-nograph/solution/solve.sh` |
| Grading suite | `harbor_tasks_clean/synth001-nograph/tests/test.sh` |
| Evaluation results (9 runs on primary task + 5 on mixed) | `results2/` |
| Full pipeline (catalog, lock graph tool, 7 tasks) | see Section 1 |
| This write-up | `WRITEUP.md` |

---

## 1. Approach: A Pipeline for Generating Deadlock Tasks

Rather than hand-crafting a single task, I built a **semi-automated pipeline** that can produce many tasks from a structured catalog of real-world deadlock patterns. The submitted task (`synth001-nograph`) is one output of this pipeline — but the pipeline itself is the more significant contribution.

### Why Deadlocks?

Deadlocks are one of the most feared classes of production bugs in multi-threaded systems. They cause complete service hangs — no error, no crash, just threads permanently waiting on each other. They are non-deterministic, often unreproducible in testing, and notoriously difficult to diagnose from thread dumps alone. Every team running concurrent Java services (Kafka, Cassandra, Elasticsearch, etc.) has war stories about deadlocks that took days to track down.

Yet deadlocks are dramatically underrepresented in coding benchmarks. SWE-bench, the most widely-used LLM coding benchmark, contains exactly **1 deadlock-related issue** out of 2,294 tasks. This gap is not because deadlocks are rare — they are a recurring source of production incidents — but because they are hard to reproduce deterministically and hard to evaluate automatically.

I was inspired by [Spaghetti Bench](https://github.com/cmu-pasta/spaghetti-bench) (CMU PASTA Lab), which evaluates AI agents on synthetic concurrency bugs using the [Fray](https://github.com/cmu-pasta/fray) systematic testing framework. Spaghetti Bench demonstrated that Fray can serve as a reliable automated verifier for deadlock fixes — but its tasks are single-file synthetic programs (e.g., a 50-line `Deadlock01Bad.java`). Useful for unit-testing deadlock reasoning, but far from the complexity of finding a deadlock buried in a real codebase.

This project bridges that gap: **real codebases, injected deadlocks from cataloged patterns, and Fray-based verification.**

### Pipeline Overview

```
  JaConTeBe Benchmark                     deadlock_catalog.md
  (47 real concurrency bugs) ──────────►  deadlock_patterns.json
                                          (23 deadlocks, 6 pattern types)
                                                    │
                                                    ▼
  Target Repo ──────► Lock Graph Pipeline ──► lock_graph.json
  (e.g. Kafka Streams)   (tree-sitter +       (lock orderings, nested
                           Facebook Infer)      acquisitions, call graphs)
                                                    │
                                                    ▼
                                          Pattern-Matched Injection
                                          (pick pattern, find natural
                                           insertion points, inject)
                                                    │
                                                    ▼
                                          Harbor Benchmark Task
                                          (Docker + Fray verification)
                                                    │
                                                    ▼
                                          Agent Evaluation
                                          (Claude Opus 4.6 via Harbor)
```

### Stage 1: Pattern Catalog (`deadlock_catalog.md`, `deadlock_patterns.json`)

I analyzed 47 concurrency bug kernels from the [JaConTeBe benchmark](https://mir.cs.illinois.edu/marinov/publications/LinETAL15JaConTeBe.pdf) (ASE 2015) — real bugs from DBCP, Derby, Groovy, JDK, Log4j, Lucene, and Commons Pool — and cataloged **23 deadlocks** into **6 recurring patterns**:

| Pattern | Count | Example |
|---------|-------|---------|
| Two-Object ABBA Cycle | 9 | Thread 1 locks A→B, Thread 2 locks B→A |
| Callback-Induced Cycle | 4 | Lock held while calling back into locking code |
| All-Waiters / Missed Notify | 4 | `notify()` instead of `notifyAll()`, or signal before wait |
| Serialization Graph Cycle | 3 | Lock acquired during `readObject()`/`writeObject()` |
| Infrastructure-vs-Application Lock | 3 | Framework lock conflicts with application lock |
| 3-Node Conditional Callback Cycle | 1 | SYNTH-001: 3-lock cycle via listener callback |

Each pattern has a machine-readable entry in `deadlock_patterns.json` with lock graphs, abstract templates, and injection metadata — designed for programmatic pattern matching against target codebases.

### Stage 2: Lock Graph Pipeline (`lock_graph_pipeline/`)

A Python tool that extracts a machine-readable lock graph from any Java codebase:

- **Light mode** (`--mode light`): Uses tree-sitter for fast AST-based extraction. No build required. Finds synchronized blocks/methods, ReentrantLock usage, wait/notify sites, calls under lock, and nested lock orderings. Processes ~400 files in ~1 second.
- **Infer mode** (`--mode infer`): Parses Facebook Infer's starvation analysis for deep interprocedural lock ordering edges. Requires the target repo to compile.
- **Both mode**: Merges tree-sitter (breadth) with Infer (depth) for maximum coverage.

The lock graph serves two purposes:
1. **Injection planning**: it provides a "map" of the target codebase's lock structure, making it possible to identify natural insertion points where a deadlock pattern would look plausible.
2. **Agent aid** (experimental): in the `graph` task variants, the extracted lock graph is provided alongside the task instruction, hypothesizing that the agent could use it for fuzzy matching against the injected pattern. The `graph` vs. `nograph` variant structure tests this hypothesis (more data needed — current: 1/1 on `synth001-graph`, insufficient sample).

### Stage 3: Pattern-Matched Injection

Using the lock graph output + pattern catalog, deadlocks are injected at natural insertion points in the target codebase. The injection follows strict guidelines:
- Don't introduce new `synchronized` blocks or lock fields that would stand out; instead call methods that are already synchronized.
- Bury lock acquisitions 2–3 call layers deep so the dependency is non-obvious.
- Make added code look like a plausible feature (metrics transport, session management, topology caching).
- Use class and method names that match the target codebase's conventions.

### Stage 4: Harbor Task Packaging

Each injection is packaged as a Harbor task with:
- **Dockerfile**: multi-stage build (Fray from source + Kafka Streams compilation)
- **instruction.md**: bug description as the agent would receive it (no hidden context)
- **test.sh**: compilation check + Fray-based deadlock verification
- **solve.sh**: gold patch that achieves reward = 1

### Output: 7 Tasks from 3 Patterns

| Task | Pattern Source | Variant | Difficulty |
|------|---------------|---------|-----------|
| `synth001-graph` | SYNTH-001 (3-node callback cycle) | Recognizable names | hard |
| `synth001-nograph` | SYNTH-001 (3-node callback cycle) | Obfuscated names | hard |
| `dbcp270-graph` | DBCP-270 (two-object ABBA cycle) | Recognizable | hard |
| `dbcp270-nograph` | DBCP-270 (two-object ABBA cycle) | Obfuscated | hard |
| `derby5560-graph` | DERBY-5560 (serialization graph cycle) | Recognizable | hard |
| `derby5560-nograph` | DERBY-5560 (serialization graph cycle) | Obfuscated | hard |
| `mixed` | 4 bugs combined | — | very_hard |

All 7 tasks are in `harbor_tasks_clean/` with working gold solutions. `synth001-nograph` and `mixed` have been evaluated at scale (see Section 4).

### Choice of Target Codebase: Apache Kafka Streams

The target codebase was chosen deliberately:

- **Kafka Streams** is a real, widely-used framework where concurrency is fundamental — it manages thread pools, state stores, and topology changes, all under various locks.
- The `streams/` module is **medium-sized** (~400 Java files): large enough that the agent cannot read every file, but small enough that Gradle compilation takes seconds rather than minutes.
- Kafka Streams is also used by Spaghetti Bench for its "real codebase" deadlock tasks, validating this choice.
- Heavy multi-threading: the module has ~50 files with `synchronized` blocks, providing genuine noise for the agent to sift through.

---

## 2. The Primary Task: `synth001-nograph`

The assessment asks for one task with 5–10 evaluation runs. I'll use `synth001-nograph` — the hardest single-bug task, with a 22% pass rate across 9 runs.

### What the agent sees

The agent receives this instruction (full text in `instruction.md`):

> *A Kafka Streams application intermittently hangs under concurrent operations. Thread dumps show threads permanently blocked, each waiting for a lock held by another. The hang is non-deterministic and depends on thread scheduling.*

It is told:
- The source is at `/app/streams/src/main/java/org/apache/kafka/streams/`
- How to build: `./gradlew :streams:compileJava :streams:compileTestJava -x test --no-daemon`
- How to use Fray: `fray-gradle your.package.YourTestClass -- --iter 1000`
- Its task: *Find and fix the root cause of the cyclic lock dependency so that the deadlock cannot occur under any thread interleaving.*

The agent has full shell access, can read/write files, and has 20 minutes. It is **not** told which files are involved, what lock types are used, or how many nodes are in the cycle.

### What the agent actually needs to do

1. **Explore** the `streams/` module (~400 Java files) to find files with lock acquisitions
2. **Identify** the 4 injected classes (`TopologyMetricsCache`, `StreamsMetricsConnector`, `AbstractMetricsTransport`, `MetricsSessionManager`) among Kafka's real code — these names are designed to blend in
3. **Trace** the 3-node lock cycle across files: `TopologyMetricsCache(monitor)` → `AbstractMetricsTransport(writeLock)` → `MetricsSessionManager(sessionLock)` → `TopologyMetricsCache(monitor)`
4. **Fix** the cycle by breaking one edge — the gold solution moves `validateSession()` outside the write lock in `StreamsMetricsConnector.performMaintenance()`
5. **Verify** the fix compiles and (optionally) passes Fray

### What category of reasoning does this require?

The task combines several capabilities that are individually tractable for frontier models but compound into genuine difficulty:

- **Codebase navigation at scale.** The agent must search hundreds of files to find lock acquisition sites, with no hints about which files are relevant. The bug spans 4 classes and 3 different lock types (intrinsic monitor, `ReentrantReadWriteLock`, `ReentrantLock`).

- **Cross-file lock ordering analysis.** The deadlock is a 3-node cycle: class A acquires lock A then calls into class B (acquiring lock B), class B acquires lock B then calls into class C (acquiring lock C), and class C acquires lock C then calls back into class A (acquiring lock A). Understanding this requires tracing call chains across file boundaries while mentally tracking which locks are held — the kind of multi-hop reasoning that degrades under context window pressure.

- **Distinguishing injected bugs from real patterns.** Kafka Streams has extensive pre-existing synchronization. The agent sees dozens of legitimate `synchronized` blocks and cross-class locking patterns. It must determine which constitutes the actual deadlock — and the injected classes use names like `StreamsMetricsConnector` and `TopologyMetricsCache` that blend into Kafka's real architecture. (The `nograph` variant specifically tests this: the `graph` variant uses recognizable names like `AbstractStateUpdater` that stand out.)

- **Generating a correct concurrent fix.** The fix must break exactly one edge of the lock cycle without introducing new bugs. Moving a method call outside a lock scope requires understanding what invariants the lock was protecting.

### Equipping the agent with the right tools

A key design principle was **fairness**: the agent should have every tool a human engineer would use. To this end:

- **Fray** is pre-installed in the Docker container and documented in the instruction. Fray systematically explores thread interleavings — it is the closest thing to a concurrency bug oracle. A human debugging this would use a tool like Fray; so should the agent.
- **`fray-gradle`** is a convenience wrapper that handles Kafka's complex classpath, so the agent can test hypotheses without fighting build infrastructure.
- **Full build tools** (Gradle, JDK 25) are pre-installed and the module is pre-compiled.
- **No artificial turn or tool limits** — the agent can make as many searches, reads, and builds as it needs within the 20-minute timeout.

Importantly, Fray serves double duty: it is both a **tool available to the agent** during debugging and the **core of our evaluation harness**. This symmetry is intentional — we are not testing with a metric the agent cannot access.

---

## 3. Grading Suite Design

The grading suite (`tests/test.sh`) has two phases:

### Phase 1: Compilation + Kafka tests

```bash
./gradlew :streams:compileJava :streams:compileTestJava --no-daemon
```

The agent's changes must compile and pass Kafka Streams' existing test suite. This ensures the fix doesn't break existing functionality — a deadlock fix that changes locking semantics could easily introduce regressions. If compilation or tests fail, reward = 0 immediately.

### Phase 2: Deadlock verification via Fray

The verifier generates a `MetricsCycleVerificationTest.java` at verification time (not during the agent's run — see anti-leakage below) that:

1. **Reads the agent's modified source files** and performs text-based analysis to check whether each of the 3 lock-cycle edges is still present
2. If any edge is broken → the cycle cannot deadlock → `exit 0` → **reward = 1**
3. If all 3 edges remain → creates 3 threads with the exact lock ordering and runs them through **Fray for 1,000 iterations**. If Fray finds a `DeadlockException` → `exit non-zero` → **reward = 0**

This two-layer approach is robust: the source analysis catches structural fixes (e.g., moving a call outside a lock), and Fray catches cases where the source heuristic is ambiguous. In practice, 1,000 Fray iterations is more than sufficient — across all evaluation runs, Fray found deadlocks within 1–7 iterations when the cycle was intact.

### Anti-leakage: hiding the verification test

Early iterations of this task suffered from **test leakage**: the agent would discover the verification test in the source tree, read it, and extract the exact fix from the test's comments. To prevent this:

1. The verification test is **deleted from the Docker image** during build (lines 85–86 of the Dockerfile):
   ```dockerfile
   RUN rm -f /app/streams/src/test/java/.../MetricsCycleVerificationTest.java \
       && rm -f /app/streams/build/classes/java/test/.../MetricsCycleVerificationTest.class
   ```
2. The test is **regenerated at verification time** by `test.sh` using a heredoc — it exists only in `/tmp/verifier-src/` during the verifier phase, which runs after the agent has completed.
3. The `tests/` directory is only mounted into the container at verification time by Harbor, not during the agent phase.

This ensures the agent must genuinely reason about the deadlock rather than extract answers from test code.

---

## 4. Evaluation Results

### `synth001-nograph`: 9 runs, 22% pass rate

9 valid trials on Claude Opus 4.6 (2 on local Mac with QEMU emulation, 7 on AWS VM with native x86_64):

| # | Trial ID | Env | Reward | Duration | Failure Mode |
|---|----------|-----|--------|----------|-------------|
| 1 | HWukYCV | Mac | **1** | ~20 min | — |
| 2 | u4ZcDsH | Mac | 0 | ~22 min | Wrong bug → compilation error |
| 3 | nxWvjYQ | VM | 0 | ~26 min | Right bug, wrong fix |
| 4 | VpQWGCa | VM | 0 | ~20 min | Wrong bug |
| 5 | jHwLtEx | VM | **1** | ~23 min | — |
| 6 | 9PL3ZYj | VM | 0 | ~19 min | Wrong bug |
| 7 | nP8nKYp | VM | 0 | ~21 min | Wrong bug |
| 8 | to7aVS2 | VM | 0 | ~21 min | Wrong bug |
| 9 | z9LwFrf | VM | 0 | ~21 min | Wrong bug |

**Pass rate: 2/9 = 22.2%** (within the preferred 10–50% range).

Full trial data (agent transcripts, verifier outputs, result.json) is preserved in `results2/synth001-nograph/`.

### `mixed`: 5 runs, 0% pass rate

5 valid trials, all reward = 0 (**0% pass rate**). No agent fixed even a single bug out of 4. Results in `results2/mixed/`.

### Additional runs

1 run on `synth001-graph` (reward = 1, insufficient sample). 6 runs on a deprecated task (`deadlock01bad`, a simpler SCTBench port) are in `jobs/` but from an older task set.

---

## 5. Why Current Models Fail — Analysis of Runs

### What successful agents did

Both successful trials (HWukYCV, jHwLtEx) followed a similar trajectory:

1. **Broad exploration phase** (~60–80% of time): searched across all files in `processor/internals/` for `synchronized`, `ReentrantLock`, and lock-related patterns using grep.
2. **Convergence on injected files**: eventually found `TopologyMetricsCache`, `StreamsMetricsConnector`, `AbstractMetricsTransport`, and `MetricsSessionManager` — files with unfamiliar names that nonetheless had dense lock interactions.
3. **Correct cycle identification**: traced the 3-node lock ordering across files and identified it as the deadlock.
4. **Minimal fix**: moved `validateSession()` outside the write lock in `performMaintenance()`, breaking one edge without changing the method's functional behavior.

Trial jHwLtEx was particularly interesting — it initially broke the *wrong* edge (C→A instead of B→C), used Fray to verify, found the fix was insufficient (a 2-lock sub-cycle remained), then applied a second fix to break B→C as well. This demonstrates the value of providing Fray as a tool: the agent used it for iterative debugging, exactly as a human engineer would.

### What failed agents did

The 7 failures fell into three categories:

**1. Wrong bug identified (5 of 7 failures).**
The most common failure mode — and the most revealing. The agent finds a plausible-looking lock pattern in *existing* Kafka code and believes it has found the deadlock:

- Trials u4ZcDsH, 9PL3ZYj, nP8nKYp, to7aVS2: focused on `ThreadCache`/`NamedCache`, `MemoryLRUCache`, or `InMemoryKeyValueStore` — all real Kafka classes with real `synchronized` blocks that *look* like they could deadlock but don't.
- Trial z9LwFrf: focused on `StreamThread` state transitions.

This is the core difficulty: the agent is searching for a needle in a haystack where *many of the hay strands look like needles*. Kafka Streams has extensive legitimate synchronization, and the agent cannot distinguish "synchronized block that exists for correctness" from "synchronized block that is part of an injected deadlock cycle" without tracing cross-file lock orderings to completion.

**2. Right bug, wrong fix (1 of 7).**
Trial nxWvjYQ correctly identified the 3-node cycle but broke edge A→B (by modifying `TopologyMetricsCache.getSnapshot()`) instead of edge B→C. The fix was semantically plausible — it did restructure the lock ordering — but the verifier's source-level heuristic still detected the edge as present because both `synchronized` and `fetchLatestMetrics` remained in the method body. Fray confirmed the deadlock persisted under the modified code.

**3. Compilation error (1 of 7).**
Trial u4ZcDsH attempted to rewrite `ThreadCache.resize()` (wrong file) and introduced 100+ compilation errors.

### Why this is hard for LLMs specifically

1. **Cross-file state tracking.** To identify a 3-node lock cycle, the agent must hold in working memory: "class A holds lock X and calls method M in class B, which acquires lock Y, and then calls method N in class C, which acquires lock Z and calls back into class A." This chain of transitive lock dependencies is exactly the kind of multi-hop reasoning that degrades with context window pressure — and the agent is reading hundreds of files along the way.

2. **Signal-to-noise ratio.** Kafka Streams has ~50 files with `synchronized` blocks. The 4 injected files are designed to blend in — `TopologyMetricsCache` sounds like a real Kafka class (Kafka has a real `TopologyMetadata` class). The agent must evaluate dozens of candidate deadlock patterns and reject all but one.

3. **Obfuscated naming.** The `nograph` variant specifically tests whether the agent can find the bug without name-based hints. In the `graph` variant, the injected class is called `AbstractStateUpdater` — a name that might stand out as unfamiliar. In `nograph`, it is called `AbstractMetricsTransport` — a name that fits Kafka's existing naming conventions perfectly.

---

## 6. The Mixed Task: Combinatorial Difficulty

The `mixed` task (`harbor_tasks_clean/mixed/`) explores how task difficulty scales with bug count. It injects 4 concurrency bugs simultaneously:

| Bug | Type | Pattern |
|-----|------|---------|
| A | ABBA deadlock | `CachingWindowStore` ↔ `ThreadCache` |
| B | Listener-under-lock | `KafkaStreams.setState()` callback inversion |
| C | Observer cycle | `TopologyMetadata` ↔ `DefaultTaskManager` |
| D | Missed signal | `DefaultStateUpdater` missing `signalAll()` |

Each bug individually is simpler than `synth001-nograph` (2-node cycles or single missing signals). But combined, the task becomes effectively impossible: **0/5 agents fixed even a single bug**.

The motivation: in production, codebases often have *multiple* concurrency issues. As the number of potential deadlock patterns grows, the search space grows combinatorially — there is too much locking noise for the agent to isolate any single pattern. The agent timeout was extended to 30 minutes (40 on the VM), but agents still spent the entire time exploring without converging.

The `mixed` grading suite runs 4 independent Fray checks (one per bug), and the agent needs all 4 to pass for reward = 1. This is intentionally strict — partial credit would dilute the signal.

---

## 7. Infrastructure and Deployment

### Fray: Verification and Agent Tool

[Fray](https://github.com/cmu-pasta/fray) (CMU PASTA Lab, v0.7.3) is a systematic concurrency testing tool that instruments the JVM to exhaustively explore thread interleavings. Unlike stress testing (which hopes to hit the right scheduling), Fray *guarantees* coverage of interleaving space up to its iteration budget.

Fray serves dual roles in this project:
- **Agent tool**: pre-installed in the container, documented in the instruction. The agent can write a test class and run it through Fray to check whether a suspected deadlock is real.
- **Verifier**: the grading suite uses Fray to confirm whether the agent's fix actually eliminates the deadlock under all interleavings.

This symmetry is important for fairness: we evaluate the agent with the same tool it has access to.

### Fray on macOS (Apple Silicon)

Fray's JVMTI native library is x86_64-only, requiring QEMU emulation on Apple Silicon. This made local development painful (~5–10x slower builds and Fray runs). To run evaluations in parallel, I deployed to an **AWS Lightsail VM** (8 vCPU x86_64, 30GB RAM). Key adaptations:

- Docker images are **multi-stage** (Ubuntu 22.04 build → Amazon Corretto 25 runtime) to support Fray's specific JDK requirements (Corretto 25 for jmods needed by jlink, JDK 11 for Gradle toolchain).
- **`task.toml` resource tuning**: CPUs reduced from 3 to 2 on the VM to fit 4 concurrent containers.

### Cost

The evaluation consumed approximately **$100 in Anthropic API credits** across all runs (including invalid/cancelled attempts, infrastructure debugging, and the `mixed` task). Each `synth001-nograph` trial uses ~5M input tokens as the agent reads many files during exploration.

---

## 8. Bonus: Scaling Task Creation

> *How would you approach creating 100 or 1,000 tasks like these in a week?*

The pipeline already uses LLMs for injection — Claude performs the actual code injection guided by structured prompts with the pattern catalog and lock graph as context. The infrastructure (Docker builds, Fray verification, Harbor packaging) is templated. So the core loop works today: pick a pattern, pick a codebase, run the lock graph pipeline, prompt the LLM to inject, package as a Harbor task.

**To scale to 100+ tasks**, the main work is expanding to more target codebases. Currently everything targets Kafka Streams. Adding ZooKeeper, Cassandra, Netty, gRPC-Java, or Elasticsearch is straightforward — the lock graph pipeline already supports any Java project. Each new codebase × 6 patterns × 2 variants (graph/nograph) yields 12 tasks, so ~10 codebases gets to 100+. Parameterized variants (different lock types, cycle lengths, obfuscation levels) multiply further.

**The bottleneck at scale is QA.** Each generated task must be verified: (a) the injected deadlock is actually triggerable by Fray, (b) the gold solution achieves reward = 1, (c) the task is non-trivial (pass rate in the 10–50% range, not 0% or 100%). Fray handles (a) automatically. For (b), the gold solution is just a revert of the injection — this is mechanical. For (c), the only reliable method is running 3–5 agent trials per task and filtering by pass rate, which costs ~$50 per task at current API prices. At 1,000 tasks, that's $50K in calibration runs — significant but tractable if the pipeline is otherwise automated.
