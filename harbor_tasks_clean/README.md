# Harbor Benchmark Tasks (Kafka Streams)

[Harbor](https://github.com/harbor-framework/harbor) tasks for evaluating AI agents on concurrency bug fixing. Each task injects one or more deadlock patterns from `deadlock_patterns.json` into **Apache Kafka Streams** (~400 Java files in the `streams/` module), verified by [Fray](https://github.com/cmu-pasta/fray) v0.7.3.

## Tasks

| Task | Difficulty | Bug Pattern | Description |
|------|-----------|-------------|-------------|
| `synth001-graph` | hard | SYNTH-001 (3-node cycle) | Injected via `AbstractStateUpdater`, recognizable names |
| `synth001-nograph` | hard | SYNTH-001 (3-node cycle) | Obfuscated as metrics classes (`AbstractMetricsTransport`, etc.) |
| `dbcp270-graph` | hard | DBCP-270 (ABBA cycle) | Two-object reverse lock ordering, recognizable |
| `dbcp270-nograph` | hard | DBCP-270 (ABBA cycle) | Two-object reverse lock ordering, obfuscated |
| `derby5560-graph` | hard | DERBY-5560 (serialization cycle) | Serialization graph cycle, recognizable |
| `derby5560-nograph` | hard | DERBY-5560 (serialization cycle) | Serialization graph cycle, obfuscated |
| `mixed` | very_hard | 4 bugs combined | 2 deadlocks + 1 observer cycle + 1 missed signal |

### Graph vs. Nograph Variants

Each injection has two variants:
- **graph**: Injected classes use recognizable names (e.g., `AbstractStateUpdater`) that hint at the pattern origin
- **nograph**: Injected classes use obfuscated names that blend into Kafka's codebase (e.g., `AbstractMetricsTransport`, `StreamsMetricsConnector`), making the bug significantly harder to find

### Mixed Task (4 Bugs)

The `mixed` task injects 4 concurrency bugs simultaneously. All must be fixed for reward=1:

| Bug | Type | Classes Involved |
|-----|------|-----------------|
| A | ABBA deadlock | `CachingWindowStore` ↔ `ThreadCache` |
| B | Listener-under-lock | `KafkaStreams.setState()` + `stateListener` |
| C | Observer cycle | `TopologyMetadata` ↔ `DefaultTaskManager` |
| D | Missed signal | `DefaultStateUpdater.requeueTaskForRestoration()` |

## Directory Layout

```
harbor_tasks_clean/
├── README.md                  # This file
├── <task_name>/
│   ├── task.toml              # Harbor metadata (difficulty, resources, timeouts)
│   ├── instruction.md         # What the agent sees (no spoilers)
│   ├── environment/
│   │   ├── Dockerfile         # Builds Fray + Kafka Streams with injected bugs
│   │   └── kafka/             # Kafka source with injected changes
│   ├── tests/
│   │   └── test.sh            # Verifier: recompile, run Fray, write reward
│   └── solution/
│       └── solve.sh           # Gold patch (reference fix)
```

## Running a Task

```bash
ANTHROPIC_API_KEY=<key> harbor run \
  --path harbor_tasks_clean/<task_name> \
  --agent claude-code \
  --model claude-opus-4-6 \
  --n-concurrent 1 \
  --no-delete
```

### Resource Configuration

From `task.toml`:

| Setting | Single-bug tasks | mixed |
|---------|-----------------|-------|
| CPUs | 3 | 3 |
| Memory | 4096 MB | 4096 MB |
| Storage | 20480 MB | 20480 MB |
| Build timeout | 900s | 900s |
| Agent timeout | 1200s (20 min) | 1800s (30 min) |
| Verifier timeout | 600s | 900s |

### Platform Notes

- Fray JVMTI is **x86_64-only**. On Apple Silicon, everything runs under QEMU emulation (~5-10x slower).
- Always use `--platform=linux/amd64` in Dockerfiles.
- Use `--no-delete` to keep Docker images between runs (avoids costly rebuilds).
- `allow_internet = true` is required (agent installs Claude Code CLI + calls API).

### Docker Image

Two-stage build:
1. **Build stage** (Ubuntu 22.04): Clones Kafka, installs Gradle, compiles `streams` module, builds Fray v0.7.3 from source
2. **Runtime stage** (Amazon Corretto 25): Copies compiled artifacts, pre-compiled Kafka module

See `../harbor_tasks/PORTING_GUIDE.md` for Fray build gotchas (JDK versions, linker errors, etc.).

## Evaluation Results

See `../results2/README.md` for Claude Opus 4.6 evaluation results on synth001 and mixed tasks.
