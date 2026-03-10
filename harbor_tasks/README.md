# Harbor Benchmark Tasks (DEPRECATED)

> **This directory is deprecated.** It contains early SCTBench-ported tasks (single-file Java bugs). The active benchmark tasks are in `../harbor_tasks_clean/` which injects deadlock patterns into Apache Kafka Streams for more realistic evaluation. See `../harbor_tasks_clean/README.md`.

Standalone [Harbor](https://github.com/harbor-framework/harbor) tasks for evaluating AI agents on concurrency bug fixing. Each task is a self-contained Docker environment with a buggy Java program, Fray-based verification, and a gold patch.

## Directory Layout

```
harbor_tasks/
├── README.md                  # This file
├── PORTING_GUIDE.md           # Guide for porting SCTBench synthetic tasks
├── <task_name>/
│   ├── task.toml              # Harbor metadata (difficulty, resources, timeouts)
│   ├── instruction.md         # What the agent sees (bug description + verify command)
│   ├── environment/
│   │   ├── Dockerfile         # Builds JDK + Fray + buggy source
│   │   └── *.java             # Buggy source file(s)
│   ├── tests/
│   │   └── test.sh            # Verifier script (runs Fray, checks exit code)
│   └── solution/
│       └── solve.sh           # Gold patch (reference fix)
```

## Task Sources

Tasks come from two pipelines:

1. **SCTBench (synthetic)** — Single-file concurrency bugs ported from [spaghetti-bench](https://github.com/cmu-pasta/spaghetti-bench). See `PORTING_GUIDE.md`.
2. **Injected (real codebases)** — Bugs injected into real projects via `tasks/`. These are multi-file, harder tasks where the agent must navigate a real codebase.

## Running a Task

```bash
ANTHROPIC_API_KEY=<key> harbor run \
  --path harbor_tasks/<task_name> \
  --agent claude-code \
  --model claude-opus-4-6 \
  --n-concurrent 1 \
  --no-delete
```

## Existing Tasks

| Task | Source | Difficulty | Bug Type |
|------|--------|------------|----------|
| `deadlock01bad` | SCTBench | Easy | 2-lock cyclic deadlock |

## Platform Notes

- Fray JVMTI is **x86_64-only**. On Apple Silicon, everything runs under QEMU emulation (~5-10x slower).
- Always use `--platform=linux/amd64` in Dockerfiles.
- Use `--no-delete` to keep Docker images between runs (avoids costly rebuilds).
- See `PORTING_GUIDE.md` for Fray build gotchas (JDK versions, linker errors, etc.).
