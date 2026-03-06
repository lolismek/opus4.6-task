# Injection Tasks

Each subdirectory contains a **real open-source Java project** with a concurrency bug injected into it. The injection is designed to look natural — no new lock objects or `synchronized` keywords are introduced; instead, calls are added to methods that are already synchronized, creating a hidden lock-ordering cycle.

## Directory Layout

```
tasks/
├── README.md                          # This file
├── <project>-<bug_type>/             # Wrapper directory per injection
│   ├── INJECTION_NOTES.md            # Documents the injected bug (pattern, lock cycle, trigger, verification)
│   └── <project>/                    # The actual project repo clone with injected changes
│       └── ...
```

## Creating a New Injection Task

1. **Pick a pattern** from `deadlock_patterns.json` (see the `abstract_template` field).
2. **Pick a target repo** and run the lock graph pipeline to find natural injection points:
   ```bash
   python -m lock_graph_pipeline /path/to/java/source -o output/lock_graph.json -m output/lock_graph.md
   ```
3. **Clone the repo** into `tasks/<project>-<bug_type>/<project>/`.
4. **Inject the bug** — add cross-class calls under existing locks to create the cycle. Guidelines:
   - Don't add new `synchronized` blocks or lock fields; call methods that are already synchronized.
   - Bury the lock acquisition 2-3 call layers deep so it isn't obvious.
   - Make the added code look like a plausible feature (monitoring, validation, logging).
5. **Write a verification test** that reliably triggers the deadlock (use `ThreadMXBean.findDeadlockedThreads()` or Fray).
6. **Document the injection** in `INJECTION_NOTES.md` — see `zookeeper-deadlock/INJECTION_NOTES.md` for the expected format (pattern used, files modified, lock cycle, triggering interleaving, why it looks natural).

## Existing Injections

| Directory | Target Project | Pattern | Catalog Bug |
|-----------|---------------|---------|-------------|
| `zookeeper-deadlock/` | Apache ZooKeeper | Two-Object ABBA Cycle | DBCP-270 |

## Turning an Injection into a Harbor Task

Once verified, package the injection as a Harbor benchmark task in `harbor_tasks/`. See `harbor_tasks/README.md` for the Harbor task format.
