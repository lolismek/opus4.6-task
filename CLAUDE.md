# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Purpose

This repository is a research project for analyzing and cataloging **Java concurrency deadlock patterns** from the [JaConTeBe benchmark](https://mir.cs.illinois.edu/marinov/publications/LinETAL15JaConTeBe.pdf) (ASE 2015). The goal is to extract abstract deadlock structures from real-world bugs, represent them in reusable form, and later inject equivalent patterns into larger repositories for testing/research.

## Repository Structure

- `JaConTeBe_TSVD/` — Cloned benchmark repo ([ChopinLi-cp/JaConTeBe_TSVD](https://github.com/ChopinLi-cp/JaConTeBe_TSVD)). Contains 47 Java concurrency bug kernel test cases from 8 open-source projects (DBCP, Derby, Groovy, JDK6, JDK7, Log4j, Lucene, Commons Pool). Each bug has source in `src/`, HTML descriptions in `description/`, and run scripts in `scripts/`.
- `deadlock_catalog.md` — Human-readable catalog of all 23 deadlock bugs with lock graphs, descriptions, and pattern classification.
- `deadlock_patterns.json` — Machine-readable structured catalog with lock graphs, abstract templates, and metadata for each deadlock bug. Designed for programmatic pattern injection.

## Key Data

- **23 deadlock bugs** total: 16 resource deadlocks (cyclic lock ordering), 7 wait-notify deadlocks (communication/missed signals)
- **5 recurring patterns**: Two-Object Cycle, Callback-Induced Cycle, All-Waiters/Missed Notify, Serialization Graph Cycle, Infrastructure-vs-Application Lock
- Bug kernel source files are Java, located under `JaConTeBe_TSVD/jacontebe/{project}/src/`

## Working with JaConTeBe Test Cases

Each bug kernel is a standalone Java file that reproduces the concurrency bug. The `jacontebe` helper project (under `JaConTeBe_TSVD/jacontebe/jacontebe/`) provides `DeadlockMonitor`, `WaitingMonitor`, `Reporter`, and `Helpers` classes used by all tests.

To understand a specific bug: read the Java source file, its `description/*.html` file, and the corresponding entry in `deadlock_patterns.json`.

Run individual tests via shell scripts: `JaConTeBe_TSVD/jacontebe/{project}/scripts/{bug_id}.sh` (requires JDK 6 or 7, plus project-specific JARs in `lib/`).
