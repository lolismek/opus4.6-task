# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Purpose

This repository is a research project for analyzing and cataloging **Java concurrency deadlock patterns** from the [JaConTeBe benchmark](https://mir.cs.illinois.edu/marinov/publications/LinETAL15JaConTeBe.pdf) (ASE 2015), building tooling to **inject equivalent patterns into larger Java repositories** for testing/research, and **benchmarking AI agents** (Claude Opus 4.6) on finding and fixing these injected bugs using the [Harbor](https://github.com/harbor-framework/harbor) evaluation framework with [Fray](https://github.com/cmu-pasta/fray) verification.

## Repository Structure

- `JaConTeBe_TSVD/` — Cloned benchmark repo ([ChopinLi-cp/JaConTeBe_TSVD](https://github.com/ChopinLi-cp/JaConTeBe_TSVD)). Contains 47 Java concurrency bug kernel test cases from 8 open-source projects (DBCP, Derby, Groovy, JDK6, JDK7, Log4j, Lucene, Commons Pool). Each bug has source in `src/`, HTML descriptions in `description/`, and run scripts in `scripts/`.
- `deadlock_catalog.md` — Human-readable catalog of all 23 deadlock bugs with lock graphs, descriptions, and pattern classification.
- `deadlock_patterns.json` — Machine-readable structured catalog with lock graphs, abstract templates, and metadata for each deadlock bug. Designed for programmatic pattern injection.
- `lock_graph_pipeline/` — Python pipeline for extracting lock graphs from Java repos. See `lock_graph_pipeline/README.md` for full documentation.
- `output/` — Generated lock graph outputs (JSON + Markdown) from pipeline runs.
- `tasks/` — Injection targets: real open-source repos with deadlocks injected. Each injection gets a wrapper directory (e.g., `tasks/zookeeper-deadlock/`) containing `INJECTION_NOTES.md` and the repo clone. See `tasks/README.md` for the injection workflow.
- `harbor_tasks_clean/` — **Active** [Harbor](https://github.com/harbor-framework/harbor) benchmark tasks for evaluating AI agents on concurrency bug fixing in Kafka Streams. 7 tasks across 3 injection patterns (synth001, dbcp270, derby5560) with graph/nograph variants plus a multi-bug mixed task. Each task is a standalone Docker environment with Fray-based verification. See `harbor_tasks_clean/README.md` for task descriptions.
- `harbor_tasks/` — **Deprecated**. Early Harbor tasks (SCTBench ports). Kept for reference only. Use `harbor_tasks_clean/` instead.
- `results2/` — **Evaluation results** from Claude Opus 4.6 agent runs (March 2026). 15 valid trials across synth001-graph, synth001-nograph, and mixed tasks. See `results2/README.md` for the full report.
- `results/` — **Deprecated**. Earlier results snapshot that included suspicious runs. Kept for backup only. Use `results2/` instead.
- `jobs/` — Raw Harbor job outputs from local Mac runs.

## Key Data

- **23 deadlock bugs** from JaConTeBe: 16 resource deadlocks (cyclic lock ordering), 7 wait-notify deadlocks (communication/missed signals)
- **1 synthetic injection pattern** (SYNTH-001): 3-node conditional callback cycle, adapted from a Go concurrency bug to idiomatic Java
- **6 recurring patterns**: Two-Object Cycle, Callback-Induced Cycle, All-Waiters/Missed Notify, Serialization Graph Cycle, Infrastructure-vs-Application Lock, 3-Node Conditional Callback Cycle
- Bug kernel source files are Java, located under `JaConTeBe_TSVD/jacontebe/{project}/src/`

## Evaluation Results Summary

Claude Opus 4.6 evaluated on Kafka Streams deadlock tasks (March 9–10, 2026):

| Task | Valid Runs | Pass Rate |
|------|-----------|-----------|
| synth001-graph | 1 | 100% (n=1) |
| synth001-nograph | 9 | **22.2%** |
| mixed (4 bugs) | 5 | **0%** |

See `results2/README.md` for detailed per-trial results, failure analysis, and methodology.

## Lock Graph Pipeline

The `lock_graph_pipeline/` directory contains a Python tool that extracts a machine-readable lock graph from any Java codebase. It has two modes:

- **Light mode** (`--mode light`): Uses tree-sitter for fast AST-based extraction. No build required. Finds synchronized blocks/methods, ReentrantLock usage, wait/notify sites, calls under lock, and nested lock orderings. ~1 second for a 400-file module.
- **Infer mode** (`--mode infer`): Parses Facebook Infer's starvation analysis debug summaries for deep interprocedural lock ordering edges. Requires the target repo to compile and Infer to be installed.
- **Both mode** (`--mode both`): Merges tree-sitter (breadth) and Infer (depth) for maximum coverage.

### Running the pipeline

```bash
# Install dependencies
pip install -r lock_graph_pipeline/requirements.txt

# Light mode (most common)
python -m lock_graph_pipeline /path/to/java/source -o output/lock_graph.json -m output/lock_graph.md

# Infer mode (after running: infer --starvation-only -- mvn compile -DskipTests)
python -m lock_graph_pipeline /path/to/java/source --mode infer --infer-out /path/to/infer-out -o output/lock_graph.json
```

### Pipeline modules

| Module | Role |
|--------|------|
| `data_types.py` | Shared dataclasses (`LockAcquisition`, `LockOrderEdge`, `ClassLockProfile`, `LockGraph`) |
| `layer1_scanner.py` | Fast regex filter for `.java` files with lock patterns |
| `layer2_treesitter.py` | Tree-sitter AST extraction; qualifies lock identities with class names |
| `layer3_resolver.py` | Cross-class edge resolution using field type declarations (strict, no method-name guessing) |
| `infer_adapter.py` | Parses Infer `debug --procedures-summary` output for lock ordering edges |
| `template_matcher.py` | Matches extracted graph against `deadlock_patterns.json` |
| `output_formatter.py` | JSON + Markdown output |
| `extract_lock_graph.py` | CLI entry point |

## Harbor Tasks (harbor_tasks_clean/)

### Running a task

```bash
ANTHROPIC_API_KEY=<key> harbor run \
  --path harbor_tasks_clean/synth001-nograph \
  --agent claude-code \
  --model claude-opus-4-6 \
  --n-concurrent 1 \
  --no-delete
```

### Available tasks

| Task | Difficulty | Bug Type | Injected Into |
|------|-----------|----------|---------------|
| `synth001-graph` | hard | 3-node deadlock cycle (recognizable names) | Kafka Streams |
| `synth001-nograph` | hard | 3-node deadlock cycle (obfuscated names) | Kafka Streams |
| `dbcp270-graph` | hard | Two-object ABBA cycle (recognizable) | Kafka Streams |
| `dbcp270-nograph` | hard | Two-object ABBA cycle (obfuscated) | Kafka Streams |
| `derby5560-graph` | hard | Serialization graph cycle (recognizable) | Kafka Streams |
| `derby5560-nograph` | hard | Serialization graph cycle (obfuscated) | Kafka Streams |
| `mixed` | very_hard | 4 bugs (2 deadlocks + 1 observer cycle + 1 missed signal) | Kafka Streams |

## Working with JaConTeBe Test Cases

Each bug kernel is a standalone Java file that reproduces the concurrency bug. The `jacontebe` helper project (under `JaConTeBe_TSVD/jacontebe/jacontebe/`) provides `DeadlockMonitor`, `WaitingMonitor`, `Reporter`, and `Helpers` classes used by all tests.

To understand a specific bug: read the Java source file, its `description/*.html` file, and the corresponding entry in `deadlock_patterns.json`.

Run individual tests via shell scripts: `JaConTeBe_TSVD/jacontebe/{project}/scripts/{bug_id}.sh` (requires JDK 6 or 7, plus project-specific JARs in `lib/`).
