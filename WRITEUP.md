# Arx — Engineering Async Assessment

**Author:** Alex Jerpelea
**Date:** March 2026
**Time spent:** ~30 hours over 5 days (pattern cataloging, pipeline development, task injection, Docker/Fray infrastructure debugging, evaluation runs, analysis)

---

## Deliverable Locations

| Deliverable | Location |
|-------------|----------|
| **Harbor task** | `harbor_tasks_clean/synth001-nograph/` |
| Task instruction | `harbor_tasks_clean/synth001-nograph/instruction.md` |
| Reference solution | `harbor_tasks_clean/synth001-nograph/solution/solve.sh` |
| Grading suite | `harbor_tasks_clean/synth001-nograph/tests/test.sh` |
| Evaluation results (9 runs) | `results2/synth001-nograph/` + `results2/README.md` |
| This write-up | `WRITEUP.md` |

> **Note:** While this submission focuses on `synth001-nograph` (the primary evaluated task), the repository contains a full pipeline that produced 7 Harbor tasks across 3 deadlock patterns with graph/nograph variants, plus a multi-bug `mixed` task. These are in `harbor_tasks_clean/`. Additionally, 6 runs on the `mixed` task (5 valid) are included in `results2/mixed/`.

---

## 1. What Category of Reasoning the Task Requires

The task requires **concurrency reasoning over a real-world codebase** — specifically, finding and fixing a deadlock in Apache Kafka Streams (~400 Java source files in the `streams/` module).

This combines several reasoning capabilities that are individually tractable for frontier models but compound into genuine difficulty:

- **Codebase navigation at scale.** The agent must search through hundreds of files to find lock acquisition sites, with no hints about which files are relevant. The injected bug spans 4 classes and 3 lock types (intrinsic monitor, `ReentrantReadWriteLock`, `ReentrantLock`).

- **Cross-file lock ordering analysis.** The deadlock is a 3-node cycle: class A acquires lock A then calls into class B (acquiring lock B), class B acquires lock B then calls into class C (acquiring lock C), and class C acquires lock C then calls back into class A (acquiring lock A). Understanding this requires tracing call chains across file boundaries while mentally tracking which locks are held.

- **Distinguishing injected bugs from real patterns.** Kafka Streams has extensive pre-existing synchronization. The agent sees dozens of legitimate `synchronized` blocks, `ReentrantLock` usages, and cross-class locking patterns. It must determine which of these constitutes the actual deadlock — and the injected classes use names like `StreamsMetricsConnector` and `TopologyMetricsCache` that blend into Kafka's real architecture.

- **Generating a correct concurrent fix.** The fix must break exactly one edge of the lock cycle without introducing new bugs. Moving a method call outside a lock scope requires understanding what invariants the lock was protecting.

### Why Deadlocks?

Concurrency bugs are a well-documented pain point in production software engineering, yet they are dramatically underrepresented in coding benchmarks. SWE-bench, the most widely-used LLM coding benchmark, contains exactly **1 deadlock-related issue** out of 2,294 tasks. This gap is not because deadlocks are rare — they are a recurring source of production incidents in any multi-threaded Java system — but because they are hard to reproduce deterministically and hard to evaluate automatically.

This project was inspired by [Spaghetti Bench](https://github.com/cmu-pasta/spaghetti-bench) (CMU PASTA Lab), which evaluates AI agents on synthetic concurrency bugs using the [Fray](https://github.com/cmu-pasta/fray) systematic testing framework. Spaghetti Bench demonstrated that Fray can serve as a reliable automated verifier for concurrency fixes. However, its tasks are single-file synthetic programs — useful for unit-testing concurrency reasoning, but far from the complexity of finding a deadlock buried in a real codebase.

This project bridges that gap: real codebase, injected bugs from cataloged patterns, and Fray-based verification.

---

## 2. What the Agent Has to Do

The agent receives this instruction (see `instruction.md`):

> *A Kafka Streams application intermittently hangs under concurrent operations. Thread dumps show threads permanently blocked, each waiting for a lock held by another. The hang is non-deterministic and depends on thread scheduling.*

It is told:
- The source is at `/app/streams/src/main/java/org/apache/kafka/streams/`
- How to build: `./gradlew :streams:compileJava :streams:compileTestJava -x test --no-daemon`
- How to use Fray: `fray-gradle your.package.YourTestClass -- --iter 1000`
- Its task: *Find and fix the root cause of the cyclic lock dependency so that the deadlock cannot occur under any thread interleaving.*

The agent has full shell access, can read/write files, and has 20 minutes. It is **not** told which files are involved, what lock types are used, or how many nodes are in the cycle.

### What the agent actually needs to do

1. **Explore** the `streams/` module (~400 Java files) to find files with lock acquisitions
2. **Identify** the 4 injected classes (`TopologyMetricsCache`, `StreamsMetricsConnector`, `AbstractMetricsTransport`, `MetricsSessionManager`) among Kafka's real code
3. **Trace** the 3-node lock cycle: `TopologyMetricsCache(monitor)` → `AbstractMetricsTransport(writeLock)` → `MetricsSessionManager(sessionLock)` → `TopologyMetricsCache(monitor)`
4. **Fix** the cycle by breaking one edge — the gold solution moves `validateSession()` outside the write lock in `StreamsMetricsConnector.performMaintenance()`
5. **Verify** the fix compiles and (optionally) passes Fray

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

### Phase 1: Compilation check

```bash
./gradlew :streams:compileJava :streams:compileTestJava -x test --no-daemon
```

If the agent's changes break compilation, reward = 0 immediately. This catches a common failure mode (ill-formed fixes that introduce syntax or type errors).

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

### Summary

9 valid trials of `synth001-nograph` on Claude Opus 4.6 (2 on local Mac with QEMU emulation, 7 on AWS VM with native x86_64):

| # | Trial ID | Env | Reward | Duration |
|---|----------|-----|--------|----------|
| 1 | HWukYCV | Mac | **1** | ~20 min |
| 2 | u4ZcDsH | Mac | 0 | ~22 min |
| 3 | nxWvjYQ | VM | 0 | ~26 min |
| 4 | VpQWGCa | VM | 0 | ~20 min |
| 5 | jHwLtEx | VM | **1** | ~23 min |
| 6 | 9PL3ZYj | VM | 0 | ~19 min |
| 7 | nP8nKYp | VM | 0 | ~21 min |
| 8 | to7aVS2 | VM | 0 | ~21 min |
| 9 | z9LwFrf | VM | 0 | ~21 min |

**Pass rate: 2/9 = 22.2%** (within the preferred 10–50% range).

Full trial data (agent transcripts, verifier outputs, result.json) is preserved in `results2/synth001-nograph/`.

### Additional task: `mixed` (4 simultaneous bugs)

5 valid trials, all reward = 0 (**0% pass rate**). The `mixed` task injects 4 concurrency bugs simultaneously — 2 deadlocks, 1 observer cycle, and 1 missed signal — requiring the agent to find and fix all of them. Results are in `results2/mixed/`.

---

## 5. Why Current Models Fail — Analysis of Runs

### What successful agents did

Both successful trials (HWukYCV, jHwLtEx) followed a similar trajectory:

1. **Broad exploration phase** (~60–80% of time): searched across all files in `processor/internals/` for `synchronized`, `ReentrantLock`, and lock-related patterns using grep
2. **Convergence on injected files**: eventually found `TopologyMetricsCache`, `StreamsMetricsConnector`, `AbstractMetricsTransport`, and `MetricsSessionManager` — files with unfamiliar names that nonetheless had dense lock interactions
3. **Correct cycle identification**: traced the 3-node lock ordering across files and identified it as the deadlock
4. **Minimal fix**: moved `validateSession()` outside the write lock in `performMaintenance()`, breaking one edge without changing the method's functional behavior

Trial jHwLtEx was particularly interesting — it initially broke the *wrong* edge (C→A instead of B→C), used Fray to verify, found the fix was insufficient (a 2-lock sub-cycle remained), then applied a second fix to break B→C as well. This demonstrates the value of providing Fray as a tool: the agent used it for iterative debugging, exactly as a human engineer would.

### What failed agents did

The 7 failures fell into three categories:

**1. Wrong bug identified (5 of 7 failures).**
The most common failure mode — and the most revealing. The agent finds a plausible-looking lock pattern in *existing* Kafka code and believes it has found the deadlock:

- Trials 02, 06, 07, 08: Focused on `ThreadCache`/`NamedCache`, `MemoryLRUCache`, or `InMemoryKeyValueStore` — all real Kafka classes with real `synchronized` blocks that *look* like they could deadlock but don't.
- Trial 09: Focused on `StreamThread` state transitions.

This is the core difficulty of the task: the agent is searching for a needle in a haystack where *many of the hay strands look like needles*. Kafka Streams has extensive legitimate synchronization, and the agent cannot distinguish "synchronized block that exists for correctness" from "synchronized block that is part of an injected deadlock cycle" without actually tracing cross-file lock orderings.

**2. Right bug, wrong fix (1 of 7).**
Trial nxWvjYQ correctly identified the 3-node cycle but broke edge A→B (by modifying `TopologyMetricsCache.getSnapshot()`) instead of edge B→C. The fix was semantically valid — it did break one edge of the cycle — but the verifier's source-level heuristic still detected the edge as present because both `synchronized` and `fetchLatestMetrics` remained in the method (just reordered). This is a limitation of the source-analysis heuristic, though Fray confirmed the deadlock still existed under the modified code.

**3. Compilation error (1 of 7).**
Trial u4ZcDsH attempted to rewrite `ThreadCache.resize()` (wrong file) and introduced 100+ compilation errors.

### Why this is hard for LLMs specifically

1. **Cross-file state tracking.** LLMs process files sequentially. To identify a 3-node lock cycle, the agent must hold in working memory: "class A holds lock X and calls method M in class B, which acquires lock Y, and then calls method N in class C, which acquires lock Z and calls back into class A." This chain of transitive lock dependencies is exactly the kind of multi-hop reasoning that degrades with context window pressure.

2. **Signal-to-noise ratio.** Kafka Streams has ~50 files with `synchronized` blocks. The 4 injected files are designed to blend in — `TopologyMetricsCache` sounds like a real Kafka class (and indeed, Kafka has a `TopologyMetadata` class). The agent must evaluate dozens of candidate deadlock patterns and reject all but one.

3. **The "obfuscated names" design.** The `nograph` variant specifically tests whether the agent can find the bug without name-based hints. In the `graph` variant (1/1 success, insufficient sample), the injected class is called `AbstractStateUpdater` — a name that stands out. In `nograph`, it is called `AbstractMetricsTransport` — a name that fits Kafka's existing naming conventions perfectly.

---

## 6. The Mixed Task: Combinatorial Difficulty

The `mixed` task (`harbor_tasks_clean/mixed/`) represents an exploration of how task difficulty scales with bug count. It injects 4 concurrency bugs simultaneously:

| Bug | Type | Pattern |
|-----|------|---------|
| A | ABBA deadlock | `CachingWindowStore` ↔ `ThreadCache` |
| B | Listener-under-lock | `KafkaStreams.setState()` callback inversion |
| C | Observer cycle | `TopologyMetadata` ↔ `DefaultTaskManager` |
| D | Missed signal | `DefaultStateUpdater` missing `signalAll()` |

Each of these bugs individually is simpler than `synth001-nograph` (2-node cycles or single missing signals). But combined, the task becomes effectively impossible: **0/5 agents fixed even a single bug**.

The motivation is that in production, codebases often have *multiple* concurrency issues. As the number of potential deadlock patterns grows, the search space for Fray and for the agent grows combinatorially — there is too much locking noise for the agent to isolate any single pattern. The agent timeout was extended to 30 minutes (vs. 20 for single-bug tasks), but agents still spent the entire time exploring without converging.

---

## 7. Infrastructure and Deployment

### Choice of Kafka Streams

The target codebase was chosen deliberately:

- **Kafka Streams** is a real, widely-used framework where concurrency is fundamental — it manages thread pools, state stores, and topology changes, all under various locks.
- The `streams/` module is **medium-sized** (~400 files): large enough that the agent cannot read every file, but small enough that Gradle compilation takes seconds rather than minutes.
- Spaghetti Bench uses Kafka Streams for its "real codebase" deadlock tasks, validating this choice.

### Fray on macOS (Apple Silicon)

Fray's JVMTI native library is x86_64-only, requiring QEMU emulation on Apple Silicon. This made local development painful (~5–10x slower builds). We ultimately deployed evaluation runs to an **AWS Lightsail VM** (8 vCPU x86_64, 30GB RAM) for parallel execution. Key adaptations:

- **Docker images are multi-stage** (Ubuntu build → Corretto runtime) to minimize image size while supporting Fray's specific JDK requirements (Corretto 25 for jmods, JDK 11 for Gradle toolchain).
- **`task.toml` resource tuning**: CPUs reduced from 3 to 2 on the VM to fit 4 concurrent containers; memory increased to 8GB for Java compilation headroom.

### Cost

The evaluation consumed approximately **$100 in Anthropic API credits** across all runs (including invalid/cancelled runs and the `mixed` task). Each successful `synth001-nograph` trial uses ~5M input tokens (the agent reads many files during exploration).

---

## 8. The Broader Pipeline

While this submission focuses on `synth001-nograph`, the repository contains the full pipeline used to create it:

### Pattern Catalog

We analyzed 47 concurrency bug kernels from the [JaConTeBe benchmark](https://mir.cs.illinois.edu/marinov/publications/LinETAL15JaConTeBe.pdf) and cataloged 23 deadlocks into 6 recurring patterns (see `deadlock_catalog.md`, `deadlock_patterns.json`). This catalog drives pattern-matched injection: pick a pattern, find a matching lock structure in the target codebase, inject.

### Lock Graph Pipeline

The `lock_graph_pipeline/` extracts a machine-readable lock graph from any Java codebase using tree-sitter (fast, no build required) and optionally Facebook Infer (deep interprocedural analysis). The lock graph maps every lock acquisition, lock ordering edge, and cross-class call-under-lock — providing a "map" for the injection process.

The lock graph was originally designed to also serve as an *agent aid*: by providing the extracted lock graph alongside the task instruction (the `graph` variant), we hypothesized the agent could use it for fuzzy matching against the injected pattern. The `graph` vs. `nograph` variant structure tests this hypothesis, though we need more runs on `graph` variants to draw conclusions (current data: 1/1 on `synth001-graph`, insufficient sample).

### 7 Tasks from 3 Patterns

Using the pipeline, we created tasks from 3 different catalog patterns, each with graph/nograph variants, plus a combined `mixed` task:

| Task | Pattern Source | Difficulty |
|------|---------------|-----------|
| `synth001-*` | SYNTH-001 (3-node callback cycle) | hard |
| `dbcp270-*` | DBCP-270 (two-object ABBA cycle) | hard |
| `derby5560-*` | DERBY-5560 (serialization graph cycle) | hard |
| `mixed` | 4 bugs combined | very_hard |

The `dbcp270` and `derby5560` tasks are fully built and have gold solutions but have not yet been evaluated at scale.

---

## 9. Bonus: Scaling Task Creation

> *How would you approach creating 100 or 1,000 tasks like these in a week?*

### The bottleneck

The current pipeline is semi-automated: pattern selection, injection point identification, and code injection still require human judgment. Of the ~30 hours spent on this project, roughly 10 were on infrastructure (Docker, Fray, Harbor), 10 on pattern cataloging and injection design, and 10 on evaluation. The injection step is the bottleneck — each injection requires understanding the target codebase's lock structure, choosing natural insertion points, writing plausible-looking code, and verifying the bug is triggerable.

### Approach for 100 tasks

1. **More target codebases.** Currently all tasks target Kafka Streams. Expanding to other heavily-concurrent Java projects (ZooKeeper, Cassandra, Netty, gRPC-Java, Elasticsearch) provides fresh code for injection without pattern repetition.

2. **LLM-assisted injection.** The lock graph pipeline already produces machine-readable lock orderings. An LLM could be prompted: *"Here is a lock graph for ZooKeeper. Here is the DBCP-270 deadlock pattern (two-object ABBA cycle). Inject this pattern at a natural point in the codebase, using existing class names and coding conventions."* The human role becomes review and verification rather than creation.

3. **Automated verification.** Every injected bug must be verified as triggerable. Fray automates this: inject the bug, write a Fray test, confirm it finds the deadlock. This step is already scriptable.

4. **Parameterized injection.** Each pattern can be injected at multiple points in the same codebase with different lock types (`synchronized` vs. `ReentrantLock` vs. `ReadWriteLock`), different cycle lengths (2-node, 3-node, 4-node), and different obfuscation levels. This multiplies tasks from a single injection site.

### Approach for 1,000 tasks

At this scale, the key constraint becomes **QA** — ensuring each task is (a) solvable, (b) non-trivial, and (c) has a correct gold solution. This requires:

1. **Template-based generation.** Define parameterized injection templates (lock type, cycle length, obfuscation level, target module) and generate tasks combinatorially. Each template is verified once; instances inherit correctness.

2. **Automated difficulty calibration.** Run each generated task against 3–5 agent trials to estimate pass rate. Tasks that are too easy (>80%) or too hard (<5%) are filtered or adjusted (e.g., adding/removing obfuscation, changing cycle length).

3. **Diverse bug types beyond deadlocks.** Expand to races, atomicity violations, ordering bugs, and missed signals — the JaConTeBe catalog has 24 non-deadlock bugs that could be adapted. Spaghetti Bench's SCTBench suite has 28 additional patterns.

4. **Cross-language extension.** The lock graph pipeline currently targets Java, but the pattern catalog (cyclic lock ordering, callback-under-lock, missed signal) applies to any language with mutex-like primitives. Go (`sync.Mutex`, channels), Rust (`Mutex<T>`, `RwLock<T>`), and C++ (`std::mutex`) are natural targets.

The fundamental scaling insight is that **pattern injection is composable**: once you have a verified pattern template and a lock graph for a target codebase, injection is mechanical. The creative work is in designing new patterns and choosing target codebases — and even that can be partially automated by mining bug databases (JIRA, GitHub Issues) for concurrency-related reports.
