# Deadlock Benchmark Pipeline

A research pipeline for **cataloging Java deadlock patterns**, **extracting lock graphs** from real-world repositories, **injecting realistic deadlocks** into target codebases, and **benchmarking AI agents** on finding and fixing them — all in a single automated workflow.

## Motivation

Deadlocks are one of the most feared classes of production bugs in multi-threaded systems — they cause complete service hangs, are non-deterministic, and notoriously difficult to diagnose. Can LLM-based coding agents reliably find and fix deadlocks in real codebases? To answer this, we need:

1. A **catalog of real deadlock patterns** from known bugs
2. A **tool to analyze lock ordering** in any Java codebase
3. A method to **inject realistic deadlocks** at natural insertion points
4. A **benchmark harness** to evaluate AI agents on fixing them

This repo implements each stage of that pipeline.

## Pipeline Overview

```
                         deadlock_catalog.md
                         deadlock_patterns.json
  JaConTeBe Benchmark ──────────┐
  (47 real concurrency bugs)    │
                                ▼
                        Pattern Catalog
                    (23 deadlocks + 1 synthetic,
                     6 pattern types)
                                │
                                ▼
  Target Repo ──► Lock Graph Pipeline ──► lock_graph.json
  (e.g. Kafka)      (tree-sitter +      (lock orderings,
                      Facebook Infer)     nested acquisitions,
                                          wait/notify sites)
                                │
                                ▼
                        Deadlock Injection
                    (LLM-guided, pattern-matched)
                                │
                                ▼
                        Harbor Benchmark Tasks
                    (Docker + Fray verification)
                                │
                                ▼
                        AI Agent Evaluation
                    (Claude Opus 4.6 via Harbor)
```

## Repository Structure

```
.
├── deadlock_catalog.md           # Human-readable catalog of 23 deadlock bugs
├── deadlock_patterns.json        # Machine-readable patterns with lock graphs
├── lock_graph_pipeline/          # Python tool: extract lock graphs from Java repos
├── harbor_tasks_clean/           # Harbor benchmark tasks (active, Kafka Streams)
│   ├── README.md                 # Task descriptions and running instructions
│   ├── synth001-graph/           # 3-node cycle, recognizable class names
│   ├── synth001-nograph/         # 3-node cycle, obfuscated class names
│   ├── dbcp270-graph/            # ABBA cycle, recognizable
│   ├── dbcp270-nograph/          # ABBA cycle, obfuscated
│   ├── derby5560-graph/          # Serialization graph cycle, recognizable
│   ├── derby5560-nograph/        # Serialization graph cycle, obfuscated
│   └── mixed/                    # 4 bugs simultaneously (very_hard)
├── results2/                     # Evaluation results (Claude Opus 4.6, March 2026)
│   ├── README.md                 # Full report with per-trial results
│   ├── synth001-graph/           # 1 trial (1 success)
│   ├── synth001-nograph/         # 9 trials (2 successes)
│   └── mixed/                    # 5 trials (0 successes)
├── tasks/                        # Injection targets (real codebases with injected bugs)
│   ├── README.md                 # Guide for creating new injection tasks
│   └── zookeeper-deadlock/       # ZooKeeper with injected ABBA deadlock
├── output/                       # Generated lock graph artifacts
├── JaConTeBe_TSVD/               # Cloned JaConTeBe benchmark (47 bug kernels)
├── jobs/                         # Raw Harbor job outputs (local Mac runs)
├── harbor_tasks/                 # [DEPRECATED] Early Harbor tasks (SCTBench ports)
└── results/                      # [DEPRECATED] Earlier results snapshot (use results2/)
```

## Components

### 1. Deadlock Pattern Catalog

Derived from the [JaConTeBe benchmark](https://mir.cs.illinois.edu/marinov/publications/LinETAL15JaConTeBe.pdf) (ASE 2015) — 47 concurrency bug kernels from 8 open-source projects (DBCP, Derby, Groovy, JDK, Log4j, Lucene, Commons Pool).

We cataloged **23 deadlock bugs** into **5 recurring patterns**, plus **1 synthetic injection pattern** adapted from a Go concurrency bug observed in a separate project:

| Pattern | Count | Example |
|---------|-------|---------|
| Two-Object ABBA Cycle | 9 | Thread 1 locks A→B, Thread 2 locks B→A |
| Callback-Induced Cycle | 4 | Lock held while calling back into locking code |
| All-Waiters / Missed Notify | 4 | `notify()` instead of `notifyAll()`, or signal before wait |
| Serialization Graph Cycle | 3 | Lock acquired during `readObject()`/`writeObject()` |
| Infrastructure-vs-Application Lock | 3 | Framework lock conflicts with application lock |
| 3-Node Conditional Callback Cycle | 1 | 3-lock cycle with conditional closing edge via listener callback (SYNTH-001) |

The synthetic pattern (SYNTH-001) was derived from observing a 3-node mutex+channel deadlock in a Go codebase and carefully adapting it to idiomatic Java concurrency primitives (`synchronized`, `ReentrantReadWriteLock`, `ReentrantLock` + `Condition`). The Go channel dependency was translated to a callback/listener pattern — the closest Java analog that preserves the key difficulty property of data-flow-hidden lock dependencies. See the catalog entry for full details on what translates faithfully and what was adapted.

- **`deadlock_catalog.md`** — Detailed descriptions with lock graphs for each bug
- **`deadlock_patterns.json`** — Structured data for programmatic use (lock graphs, abstract templates, injection metadata)

### 2. Lock Graph Pipeline

Extracts a machine-readable lock graph from any Java codebase. See [`lock_graph_pipeline/README.md`](lock_graph_pipeline/README.md) for full documentation.

```bash
pip install -r lock_graph_pipeline/requirements.txt

# Fast AST-based extraction (no build required)
python -m lock_graph_pipeline /path/to/java/src -o output/lock_graph.json -m output/lock_graph.md

# Deep analysis using Facebook Infer
python -m lock_graph_pipeline /path/to/java/src --mode infer --infer-out /path/to/infer-out

# Both modes merged
python -m lock_graph_pipeline /path/to/java/src --mode both --infer-out /path/to/infer-out
```

**Output includes**: lock acquisitions per class, lock ordering edges (A→B), wait/notify sites, calls made under lock, field type declarations, and candidate pattern matches against the catalog.

**Modes**:
| Mode | Speed | Build Required | Coverage |
|------|-------|---------------|----------|
| `light` | ~1s per 400 files | No | Intra-method lock orderings |
| `infer` | Minutes | Yes (Maven/Gradle) | Deep interprocedural edges |
| `both` | Minutes | Yes | Maximum (merged) |

### 3. Deadlock Injection

Uses lock graph output + pattern catalog to inject realistic deadlocks into target repos at natural insertion points. See [`tasks/README.md`](tasks/README.md) for the general injection workflow and [`tasks/zookeeper-deadlock/INJECTION_NOTES.md`](tasks/zookeeper-deadlock/INJECTION_NOTES.md) for a worked example.

**Example**: DBCP-270 pattern (Two-Object ABBA Cycle) injected into ZooKeeper's leader election code:
- `Leader.sendObserverPacket()` acquires `Leader.this` → `LearnerHandler.this`
- `LearnerHandler.getLearnerHandlerInfo()` acquires `LearnerHandler.this` → `Leader.this`
- Verified with `ThreadMXBean.findDeadlockedThreads()` under concurrent load

### 4. Harbor Benchmark Tasks

Standalone [Harbor](https://github.com/harbor-framework/harbor) tasks that package injected bugs into Apache Kafka Streams for AI agent evaluation using [Fray](https://github.com/cmu-pasta/fray) for systematic concurrency testing.

```bash
# Run a task with Claude Code agent
ANTHROPIC_API_KEY=<key> harbor run \
  --path harbor_tasks_clean/synth001-nograph \
  --agent claude-code \
  --model claude-opus-4-6 \
  --n-concurrent 1 \
  --no-delete
```

**Available tasks** (in `harbor_tasks_clean/`):

| Task | Difficulty | Bug Type | Variant |
|------|-----------|----------|---------|
| `synth001-graph` | hard | 3-node conditional callback cycle | Recognizable class names |
| `synth001-nograph` | hard | 3-node conditional callback cycle | Obfuscated class names |
| `dbcp270-graph` | hard | Two-object ABBA cycle | Recognizable |
| `dbcp270-nograph` | hard | Two-object ABBA cycle | Obfuscated |
| `derby5560-graph` | hard | Serialization graph cycle | Recognizable |
| `derby5560-nograph` | hard | Serialization graph cycle | Obfuscated |
| `mixed` | very_hard | 4 concurrent bugs (deadlocks + missed signal) | — |

Each task includes:
- **Dockerfile**: Builds Fray from source, pre-compiles Kafka Streams with injected bugs
- **instruction.md**: Bug description shown to the agent (no spoilers)
- **test.sh**: Recompiles agent's fix, runs Fray, writes reward (0 or 1)
- **solve.sh**: Gold patch for oracle baseline

### 5. Evaluation Results

Claude Opus 4.6 was evaluated on the synth001 and mixed tasks across two environments (local Mac with QEMU emulation, AWS VM with native x86_64). See [`results2/README.md`](results2/README.md) for the full report.

**Summary** (15 valid trials, March 9–10, 2026):

| Task | Valid Runs | Successes | Pass Rate |
|------|-----------|-----------|-----------|
| synth001-graph | 1 | 1 | 100% (insufficient sample) |
| synth001-nograph | 9 | 2 | **22.2%** |
| mixed | 5 | 0 | **0%** |

Key findings:
- The most common failure mode is the agent identifying the **wrong lock pattern** — a real-looking pattern in existing Kafka code rather than the injected bug
- The `mixed` task (4 simultaneous bugs) appears too hard for a single agent session within the timeout
- Obfuscated class names (nograph variant) significantly increase difficulty

## Dependencies

- **Python 3.10+** with `tree-sitter`, `tree-sitter-java`
- **Docker** (for Harbor tasks — images are x86_64, runs under QEMU on Apple Silicon)
- **[Harbor](https://github.com/harbor-framework/harbor)** (`pip install harbor-bench`) for agent evaluation
- **[Facebook Infer](https://fbinfer.com/)** (optional, for deep lock graph analysis)

## References

- Lin et al., [JaConTeBe: A Benchmark Suite of Real-World Java Concurrency Bugs](https://mir.cs.illinois.edu/marinov/publications/LinETAL15JaConTeBe.pdf), ASE 2015
- [Fray](https://github.com/cmu-pasta/fray) — Systematic JVM concurrency testing (CMU PASTA Lab)
- [Spaghetti Bench](https://github.com/cmu-pasta/spaghetti-bench) — Concurrency bug fixing benchmark (CMU PASTA Lab)
- [Harbor](https://github.com/harbor-framework/harbor) — AI agent evaluation framework
