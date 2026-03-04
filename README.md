# Concurrency Bug Benchmark Pipeline

A research pipeline for **cataloging Java concurrency deadlock patterns**, **extracting lock graphs** from real-world repositories, **injecting realistic deadlocks** into target codebases, and **benchmarking AI agents** on fixing them — all in a single automated workflow.

## Motivation

Concurrency bugs are notoriously hard to detect and fix. Can LLM-based coding agents reliably fix deadlocks, race conditions, and missed-signal bugs? To answer this, we need:

1. A **catalog of real deadlock patterns** from known bugs
2. A **tool to analyze lock ordering** in any Java codebase
3. A method to **inject realistic bugs** at natural insertion points
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
                    (23 deadlocks, 5 pattern types)
                                │
                                ▼
  Target Repo ──► Lock Graph Pipeline ──► lock_graph.json
  (e.g. ZooKeeper)   (tree-sitter +      (lock orderings,
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
                    (Claude, GPT, etc. via Harbor)
```

## Repository Structure

```
.
├── deadlock_catalog.md        # Human-readable catalog of 23 deadlock bugs
├── deadlock_patterns.json     # Machine-readable patterns with lock graphs
├── lock_graph_pipeline/       # Python tool: extract lock graphs from Java repos
├── harbor_tasks/              # Harbor benchmark tasks for AI agent evaluation
│   ├── PORTING_GUIDE.md       # How to port more SCTBench tasks
│   └── deadlock01bad/         # Example task: 2-lock cyclic deadlock
├── tasks/                     # Injection targets
│   ├── INJECTION_NOTES.md     # Deadlock injection documentation
│   └── zookeeper/             # ZooKeeper with injected ABBA deadlock
├── output/                    # Generated lock graph artifacts
├── JaConTeBe_TSVD/            # Cloned JaConTeBe benchmark (47 bug kernels)
└── jobs/                      # Harbor evaluation run outputs
```

## Components

### 1. Deadlock Pattern Catalog

Derived from the [JaConTeBe benchmark](https://mir.cs.illinois.edu/marinov/publications/LinETAL15JaConTeBe.pdf) (ASE 2015) — 47 concurrency bug kernels from 8 open-source projects (DBCP, Derby, Groovy, JDK, Log4j, Lucene, Commons Pool).

We cataloged **23 deadlock bugs** into **5 recurring patterns**:

| Pattern | Count | Example |
|---------|-------|---------|
| Two-Object ABBA Cycle | 9 | Thread 1 locks A→B, Thread 2 locks B→A |
| Callback-Induced Cycle | 4 | Lock held while calling back into locking code |
| All-Waiters / Missed Notify | 4 | `notify()` instead of `notifyAll()`, or signal before wait |
| Serialization Graph Cycle | 3 | Lock acquired during `readObject()`/`writeObject()` |
| Infrastructure-vs-Application Lock | 3 | Framework lock conflicts with application lock |

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

Uses lock graph output + pattern catalog to inject realistic deadlocks into target repos at natural insertion points. Documented in [`tasks/INJECTION_NOTES.md`](tasks/INJECTION_NOTES.md).

**Example**: DBCP-270 pattern (Two-Object ABBA Cycle) injected into ZooKeeper's leader election code:
- `Leader.sendObserverPacket()` acquires `Leader.this` → `LearnerHandler.this`
- `LearnerHandler.getLearnerHandlerInfo()` acquires `LearnerHandler.this` → `Leader.this`
- Verified with `ThreadMXBean.findDeadlockedThreads()` under concurrent load

### 4. Harbor Benchmark Tasks

Standalone [Harbor](https://github.com/harbor-framework/harbor) tasks that package injected bugs for AI agent evaluation using [Fray](https://github.com/cmu-pasta/fray) for systematic concurrency testing.

```bash
# Run a task with Claude Code agent
ANTHROPIC_API_KEY=<key> harbor run \
  --path harbor_tasks/deadlock01bad \
  --agent claude-code \
  --model claude-opus-4-6 \
  --n-concurrent 1 \
  --no-delete
```

Each task includes:
- **Dockerfile**: Builds Fray from source, pre-compiles buggy Java code
- **instruction.md**: Bug description shown to the agent (no spoilers)
- **test.sh**: Recompiles agent's fix, runs Fray, writes reward (0 or 1)
- **solve.sh**: Gold patch for oracle baseline

See [`harbor_tasks/PORTING_GUIDE.md`](harbor_tasks/PORTING_GUIDE.md) for porting more tasks from [SCTBench](https://github.com/cmu-pasta/spaghetti-bench) (28 tasks available).

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
