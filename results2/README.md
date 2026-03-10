# Harbor Benchmark Results: Claude Opus 4.6 on Kafka Streams Deadlock Tasks

## Overview

We evaluated **Claude Opus 4.6** (via Claude Code agent) on concurrency bug-finding tasks built on top of **Apache Kafka Streams**. The tasks require the agent to locate and fix injected deadlock/hang bugs in a real-world Java codebase (~400 files in the `streams/` module), verified by [Fray](https://github.com/cmu-pasta/fray) v0.7.3 — a systematic concurrency testing tool that exhaustively explores thread interleavings.

All tasks use the [Harbor](https://github.com/harbor-framework/harbor) benchmark framework for reproducible, containerized evaluation.

**Date**: March 9–10, 2026

## Tasks

### synth001-graph (difficulty: hard)

A single injected 3-node deadlock cycle (SYNTH-001) across `KafkaStreams`, `TopologyMetadata`, and `DefaultStateUpdater`. The bug creates a cyclic lock dependency: `KafkaStreams(monitor) → TopologyMetadata(topologyLock) → DefaultStateUpdater(stateLock) → KafkaStreams(monitor)`. The injected pattern uses an `AbstractStateUpdater` superclass and `TopologyChangeHandler` callback to create the cycle.

The "graph" variant uses the **original SYNTH-001 class names** (e.g., `AbstractStateUpdater`), making the injected code slightly more recognizable.

**Gold fix**: Remove all 4 injection sites (inner class, observer registration, snapshot invalidation, superclass extension) and delete the injected `AbstractStateUpdater.java`.

### synth001-nograph (difficulty: hard)

The same 3-node deadlock cycle but with **obfuscated class names** that blend into Kafka's existing code (e.g., `AbstractMetricsTransport`, `MetricsSessionManager`, `TopologyMetricsCache`, `StreamsMetricsConnector`). The injected classes look like natural parts of the Kafka Streams metrics subsystem, making the bug significantly harder to distinguish from real code.

**Gold fix**: Break the cycle by moving `validateSession()` (which acquires `sessionLock`) outside the `writeLock` in `StreamsMetricsConnector.performMaintenance()`.

### mixed (difficulty: very_hard)

**Four** concurrency bugs injected simultaneously into the same Kafka Streams codebase. All four must be fixed for reward=1:

| Bug | Type | Description |
|-----|------|-------------|
| A | ABBA deadlock | `CachingWindowStore` ↔ `ThreadCache` reverse lock ordering via `recordFlushCompletion` callback |
| B | Listener-under-lock | `KafkaStreams.setState()` calls `stateListener.onChange()` inside `synchronized(stateLock)`, creating a lock-ordering inversion |
| C | Observer cycle | `TopologyMetadata` ↔ `DefaultTaskManager` observer pattern creates `topologyLock → tasksLock → topologyLock` cycle |
| D | Missed signal | `DefaultStateUpdater.requeueTaskForRestoration()` signals `tasksAndActionsCondition` but not `restoredActiveTasksCondition`, causing waiters to hang |

## Infrastructure

| | Local Mac | AWS VM |
|---|-----------|--------|
| **Hardware** | Apple M3 Max, 36GB RAM | 8 vCPU x86_64, 30GB RAM |
| **OS** | macOS 14.4 (Darwin 23.4.0) | Ubuntu 22.04 |
| **Docker** | Docker Desktop (QEMU emulation for x86_64) | Native Docker (no emulation) |
| **Fray** | v0.7.3 | v0.7.3 |
| **Agent model** | Claude Opus 4.6 (via Claude Code CLI) | Claude Opus 4.6 (via Claude Code CLI) |

## Run Conditions

From `task.toml`:

| Setting | synth001-graph / synth001-nograph | mixed |
|---------|----------------------------------|-------|
| CPUs | 3 | 3 |
| Memory | 4096 MB | 4096 MB |
| Storage | 20480 MB | 20480 MB |
| Build timeout | 900s | 900s |
| **Agent timeout** | **1200s (20 min)** | **1800s (30 min)** |
| Verifier timeout | 600s | 900s |
| Internet access | Yes | Yes |

Base image: Ubuntu 22.04 build stage + Amazon Corretto 25 runtime. The Kafka Streams module is pre-compiled in the Docker image; the agent can recompile with `./gradlew :streams:compileJava`.

## Results

### All 15 Valid Trials

| # | Task | Trial ID | Env | Reward | Duration | Agent Status | Flagged |
|---|------|----------|-----|--------|----------|-------------|---------|
| 1 | synth001-graph | RGnXYrX | Mac | **1.0** | ~17 min | AgentTimeout | |
| 2 | synth001-nograph | HWukYCV | Mac | **1.0** | ~20 min | AgentTimeout | |
| 3 | synth001-nograph | u4ZcDsH | Mac | 0.0 | ~22 min | AgentTimeout | |
| 4 | synth001-nograph | nxWvjYQ | VM | 0.0 | ~26 min | AgentTimeout | * |
| 5 | synth001-nograph | VpQWGCa | VM | 0.0 | ~20 min | Completed | * |
| 6 | synth001-nograph | jHwLtEx | VM | **1.0** | ~23 min | AgentTimeout | * |
| 7 | synth001-nograph | 9PL3ZYj | VM | 0.0 | ~19 min | Completed | |
| 8 | synth001-nograph | nP8nKYp | VM | 0.0 | ~21 min | AgentTimeout | |
| 9 | synth001-nograph | to7aVS2 | VM | 0.0 | ~21 min | AgentTimeout | |
| 10 | synth001-nograph | z9LwFrf | VM | 0.0 | ~21 min | AgentTimeout | |
| 11 | mixed | P6jStYp | Mac | 0.0 | ~31 min | AgentTimeout | |
| 12 | mixed | 4VnSfEo | VM | 0.0 | — | AgentTimeout | |
| 13 | mixed | NEtvT2o | VM | 0.0 | — | AgentTimeout | |
| 14 | mixed | fBkoKXy | VM | 0.0 | — | AgentTimeout | |
| 15 | mixed | gZHdgAL | VM | 0.0 | — | AgentTimeout | |

### Pass Rates

| Task | Valid Runs | Successes | Pass Rate | 95% CI |
|------|-----------|-----------|-----------|--------|
| synth001-graph | 1 | 1 | 100% | (insufficient sample) |
| synth001-nograph | 9 | 2 | **22.2%** | [2.8%, 60.0%] |
| mixed | 5 | 0 | **0%** | [0%, 52.2%] |
| **Overall** | **15** | **3** | **20.0%** | |

Note: Confidence intervals are wide due to small sample sizes. The synth001-graph result (1/1) is not statistically meaningful.

## Agent Failure Analysis

Common failure modes observed across the 12 unsuccessful trials:

### 1. Wrong Bug Identified (most common)

The agent identifies a real-looking lock pattern in existing Kafka code rather than the injected bug. Common false targets:

- **InMemoryKeyValueStore lock ordering** — The agent finds `synchronized` blocks in the key-value store implementation and believes it found the deadlock.
- **ThreadCache resize vs put** — The agent identifies ThreadCache's internal locking as suspicious and attempts to fix it.
- **StreamThread state transitions** — The agent focuses on the StreamThread lifecycle locks instead of the injected cycle.

This failure mode is especially prevalent in `synth001-nograph` where injected classes use realistic names that blend into the codebase.

### 2. Right Bug, No Fix Applied

The agent correctly identifies the injected deadlock cycle but runs out of time before applying the fix. Typically the agent spends too long:
- Writing and running verification tests through Fray
- Exploring the full codebase before narrowing down
- Iterating on compilation errors in the fix

### 3. Compilation Error in Fix

The agent's fix introduces compilation errors (e.g., missing imports, incorrect method signatures). The verifier's first phase (compilation + Kafka tests) catches this and assigns reward=0.

Example: Trial u4ZcDsH on Mac.

### 4. No Bugs Fixed in Multi-Bug Task

For the `mixed` task (4 bugs), no agent fixed even a single bug. The agents typically:
- Spent the entire timeout on codebase exploration without narrowing down any specific bug
- Found one or two bugs but couldn't fix all four within the 30-minute timeout
- Mixed up which patterns were injected vs. pre-existing

## Suspicious Runs (Flagged)

Three runs were originally suspected of being affected by a `fray-gradle` wrapper classpath bug (cached paths using `/build/kafka/` instead of `/app/`). Investigation showed that **both `test.sh` scripts use the `fray` binary directly, NOT `fray-gradle`** — the wrapper classpath bug could never affect verification scoring.

| Trial ID | Job Batch | Why Flagged | Investigation Result |
|----------|-----------|-------------|---------------------|
| nxWvjYQ | 23-35-50 | Ran before fray-gradle fix | Verifier unaffected (uses `fray` directly) |
| VpQWGCa | 23-38-15 | Ran before fray-gradle fix | Verifier unaffected (uses `fray` directly) |
| jHwLtEx | 23-39-20 | Ran before fray-gradle fix | SUCCESS — verifier confirmed cycle broken |

**Conclusion**: The `fray-gradle` wrapper fix improved agent UX (agents use it during exploration) but never affected verification scoring. All 3 flagged runs are valid.

## Excluded Runs (17 Invalid)

| Job Timestamp | Task | Trial ID | Reason |
|---------------|------|----------|--------|
| 2026-03-09__07-05-59 | synth001-nograph | umS2ahg | CancelledError, no trial results |
| 2026-03-09__07-18-37 | synth001-nograph | aW6jqDw | CancelledError, no trial results |
| 2026-03-09__23-34-15 | synth001-nograph | J3ALT2m | EnvironmentSetupError |
| 2026-03-10__00-17-53 | mixed | fGywata | Agent execution failure |
| 2026-03-10__00-30-09 | mixed | ZXbeyAC | Agent execution failure |
| 2026-03-10__01-32-25 | synth001-nograph | JzfpEga | Agent setup timeout |
| 2026-03-10__01-32-25 | synth001-nograph | LaZeFqq | Agent setup timeout |
| 2026-03-10__01-32-25 | synth001-nograph | aNTFuXx | Agent setup timeout |
| 2026-03-10__01-32-25 | synth001-nograph | qSusFnf | Agent setup timeout |
| 2026-03-10__01-38-47 | synth001-nograph | BAoiCdr | Agent setup timeout |
| 2026-03-10__01-38-47 | synth001-nograph | T5XBzxN | Env setup error |
| 2026-03-10__01-38-47 | synth001-nograph | U9kFm99 | Env setup error |
| 2026-03-10__01-38-47 | synth001-nograph | pipsa33 | Env setup error |
| 2026-03-10__01-39-30 | synth001-nograph | 8AKwdYX | Suspected fray-gradle classpath contamination |
| 2026-03-10__01-39-30 | synth001-nograph | J8HZoTi | Suspected fray-gradle classpath contamination |
| 2026-03-10__01-39-30 | synth001-nograph | cHfXGey | Suspected fray-gradle classpath contamination |
| 2026-03-10__01-39-30 | synth001-nograph | yT3MRVd | Suspected fray-gradle classpath contamination |

Runs 1–13 were excluded because the agent never ran or the verifier never completed (infrastructure issues). Runs 14–17 (the 01-39-30 batch) were excluded due to suspected `fray-gradle` classpath contamination during the agent's exploration phase, which may have affected agent behavior even though it did not affect verifier scoring.

## Reproducibility

### Running a Single Task

```bash
# Prerequisites: harbor CLI, Docker, ANTHROPIC_API_KEY
export ANTHROPIC_API_KEY=<your-key>

# Single trial
harbor run \
  --path harbor_tasks_clean/synth001-nograph \
  --agent claude-code \
  --model claude-opus-4-6 \
  --n-concurrent 1 \
  --no-delete

# Multiple concurrent trials
harbor run \
  --path harbor_tasks_clean/synth001-nograph \
  --agent claude-code \
  --model claude-opus-4-6 \
  --n-concurrent 4 \
  --no-delete
```

### Building the Docker Image

The task Dockerfile uses a two-stage build:
1. **Build stage** (Ubuntu 22.04): Clones Kafka, installs Gradle, compiles `streams` module, installs Fray v0.7.3
2. **Runtime stage** (Amazon Corretto 25): Copies compiled artifacts, installs Claude Code CLI dependencies

The pre-compiled Kafka module and Fray installation are cached in the Docker image to avoid rebuilding on every trial.

### Directory Structure

Each trial directory contains:
- `result.json` — Trial result with reward, timestamps, config, exception info
- `job_config.json` — Parent job configuration
- `job_result.json` — Parent job result
- `config.json` — Trial-level configuration
- `agent/` — Agent workspace (modified source files, logs, Claude Code session transcripts)
- `verifier/` — Verifier output (`reward.txt`, `fray_output.txt`, compilation logs)
- `trial.log` — Harbor trial execution log
- `exception.txt` — Exception details (if agent timed out or errored)
- `artifacts/` — Any artifacts produced during the trial
