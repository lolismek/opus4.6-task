# Arx — Engineering Async Assessment

With this submission, I have fulfilled both the creation of one task as required by the problem statement, but also created an entire semi-automated pipeline for generating a benchmark of synthetic deadlock bugs injected into large open-source codebases.

I was inspired by [Spaghetti Bench](https://github.com/cmu-pasta/spaghetti-bench) (CMU PASTA Lab), which benchmarks AI agents on concurrency bugs using [Fray](https://github.com/cmu-pasta/fray) for verification. My project specializes on **deadlocks** specifically — a bug class Spaghetti Bench touches only lightly — and automates the pipeline from pattern cataloging through injection to Harbor task generation.

Please see all my work at [this repo](https://github.com/lolismek/arxlab_problem2). The `README.md` documents the project structure well. There is also a more comprehensive AI-generated writeup answering the assessment's questions in detail in `WRITEUP.md` — you could read that after this human-written overview.

This project took me about **25 hours** and **$100 in Anthropic API credits**.

---

## Why Deadlocks?

Deadlocks cause complete service hangs — no error, no crash, just threads permanently waiting on each other. They are non-deterministic, unreproducible in most test suites, and notoriously painful to diagnose. Every team running concurrent Java services has war stories about them.

Yet SWE-bench has exactly **1 deadlock** out of 2,294 tasks. The gap exists because deadlocks are hard to evaluate automatically — you can't just diff the output. Spaghetti Bench showed that Fray (a systematic concurrency testing tool from CMU) can reliably verify deadlock fixes, but its tasks are single-file synthetic programs. I wanted to bring that verification to real codebases — and automate the process of creating such tasks.

## The Pipeline (briefly)

I built a pipeline that goes from **real deadlock patterns → lock graph analysis → pattern injection → Harbor task**. The repo contains a catalog of 23 deadlock patterns from the JaConTeBe benchmark, a lock graph extraction tool (tree-sitter + Facebook Infer), and 7 Harbor tasks injected into Apache Kafka Streams. More on this at the end — first, the primary task.

## The Primary Task: `synth001-nograph`

The bug is a **3-node deadlock cycle** injected across 4 Java classes in Kafka Streams. Three different lock types are involved — an intrinsic monitor (`synchronized`), a `ReentrantReadWriteLock`, and a `ReentrantLock` — forming a cycle:

```
TopologyMetricsCache (monitor)
    → StreamsMetricsConnector → AbstractMetricsTransport (writeLock)
        → MetricsSessionManager (sessionLock)
            → callback → TopologyMetricsCache (monitor)   ← cycle!
```

The class names are deliberately chosen to blend into Kafka's existing naming conventions. Kafka Streams already has `TopologyMetadata`, various metrics classes, and session-related code — the injected files look like they belong.

**Deliverables:**

| What | Where |
|------|-------|
| Harbor task | `harbor_tasks_clean/synth001-nograph/` |
| Instruction | `harbor_tasks_clean/synth001-nograph/instruction.md` |
| Reference solution | `harbor_tasks_clean/synth001-nograph/solution/solve.sh` |
| Grading suite | `harbor_tasks_clean/synth001-nograph/tests/test.sh` |
| 9 evaluation runs | `results2/synth001-nograph/` |

## What Makes This Hard

The task requires **cross-file lock ordering analysis** in a ~400-file Java module. The agent must:

1. Search hundreds of files to find lock acquisition sites (no hints about which files matter)
2. Distinguish the 4 injected classes from Kafka's ~50 real files with `synchronized` blocks
3. Trace the lock cycle across 3 files and 3 lock types, mentally tracking which locks are held at each call site
4. Apply a fix that breaks exactly one edge without introducing new bugs

This may sound like a needle-in-a-haystack problem — and it is — but the agent is not unarmed. **Fray** is pre-installed in the container and documented in the instruction, giving the agent the same systematic concurrency testing tool a human engineer would use. The agent has full shell access, a pre-compiled codebase, and a `fray-gradle` convenience wrapper for Kafka's complex classpath. No turn limits, no tool restrictions — just a 20-minute timeout. This follows the same philosophy as Spaghetti Bench: give the agent a real debugging tool, then see if it can use it effectively.

Despite having Fray, current models fail primarily because of **signal-to-noise**: Kafka Streams has extensive real synchronization. In 5 out of 7 failures, the agent latched onto a plausible-looking lock pattern in existing Kafka code (`ThreadCache`, `MemoryLRUCache`, `InMemoryKeyValueStore`) and never found the injected bug. The obfuscated names make this worse — `AbstractMetricsTransport` doesn't stand out the way `AbstractStateUpdater` would. The agent has the tools to verify a hypothesis, but it struggles to form the right hypothesis in the first place.

## Grading

The grading suite (`test.sh`) runs in two phases:

1. **Compilation check** — does the agent's fix still compile? (catches broken edits)
2. **Fray verification** — a test generated at verification time reads the agent's modified source files, checks whether each edge of the lock cycle is still present, and if so, creates threads with that lock ordering for Fray to explore. If Fray finds a `DeadlockException`, reward = 0. If any cycle edge is broken, reward = 1.

The **reference solution** (`solve.sh`) simply moves one method call (`validateSession()`) outside a write lock in `StreamsMetricsConnector.performMaintenance()`, breaking one edge of the cycle. It is effectively a revert of the injected change.

Importantly, the verification test is **deleted from the Docker image** during build and regenerated only at verification time. Early iterations had leakage problems — the agent would find the test, read its comments, and extract the fix. Now the `tests/` directory is only mounted after the agent finishes.

## Results

9 valid trials on Claude Opus 4.6. **Pass rate: 2/9 (22%)**.

| # | Trial ID | Reward | Failure Mode |
|---|----------|--------|-------------|
| 1 | HWukYCV | **1** | — |
| 2 | u4ZcDsH | 0 | Wrong bug → compilation error |
| 3 | nxWvjYQ | 0 | Right bug, wrong fix |
| 4 | VpQWGCa | 0 | Wrong bug |
| 5 | jHwLtEx | **1** | — |
| 6 | 9PL3ZYj | 0 | Wrong bug |
| 7 | nP8nKYp | 0 | Wrong bug |
| 8 | to7aVS2 | 0 | Wrong bug |
| 9 | z9LwFrf | 0 | Wrong bug |

Full trial data (agent transcripts, verifier outputs) is in `results2/synth001-nograph/`.

One successful agent (trial 5) demonstrated exactly why giving the agent Fray matters: it broke the wrong cycle edge first, ran Fray to check, saw the deadlock persisted, then fixed the right edge. This is exactly how a human would use the tool — and it's the kind of iterative debugging loop that makes the task fair despite its difficulty.

---

## The Broader Pipeline

Beyond `synth001-nograph`, the repo contains the full infrastructure for generating more tasks:

**Pattern catalog** (`deadlock_catalog.md`, `deadlock_patterns.json`): 23 deadlock patterns from 47 bugs in the JaConTeBe benchmark, classified into 6 types (ABBA cycles, callback-induced cycles, missed signals, serialization graph cycles, etc.).

**Lock graph pipeline** (`lock_graph_pipeline/`): a Python tool that extracts lock orderings from any Java codebase via tree-sitter (fast, no build needed) or Facebook Infer (deep interprocedural analysis). This produces the "map" used to find natural injection points. It was also designed as an experimental agent aid — the `graph` task variants provide the lock graph to the agent, testing whether it helps (insufficient data so far: 1/1 on `synth001-graph`).

**7 tasks total** in `harbor_tasks_clean/`, from 3 different catalog patterns with graph/nograph variants, plus a multi-bug `mixed` task:

| Task | Pattern | Evaluated? |
|------|---------|-----------|
| `synth001-graph` | 3-node callback cycle (recognizable names) | 1 run (1/1) |
| `synth001-nograph` | 3-node callback cycle (obfuscated names) | **9 runs (2/9)** |
| `dbcp270-graph` / `nograph` | Two-object ABBA cycle | Not yet |
| `derby5560-graph` / `nograph` | Serialization graph cycle | Not yet |
| `mixed` | 4 bugs simultaneously | **5 runs (0/5)** |

**The `mixed` task** is an interesting experiment: 4 individually-simple bugs combined into one task. Each is easier than `synth001-nograph` alone (2-node cycles, single missing signals), but together **0/5 agents fixed even one bug**. The combinatorial explosion of locking noise makes it impossible for the agent to isolate any single pattern.

**Infrastructure**: Fray is x86_64-only, so development on my Mac (Apple Silicon) ran under QEMU emulation at ~5-10x slowdown. I deployed evaluation runs to an AWS Lightsail VM for parallel execution. The Docker images use a multi-stage build to handle Fray's specific JDK requirements (Corretto 25 for jmods, JDK 11 for Gradle toolchain — details in `harbor_tasks/PORTING_GUIDE.md`).
