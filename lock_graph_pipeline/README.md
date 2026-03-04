# Lock Graph Pipeline

Extracts a machine-readable lock graph from Java source code, identifying synchronization primitives, lock ordering edges, and potential deadlock patterns.

## Installation

```bash
pip install -r lock_graph_pipeline/requirements.txt
```

## Usage

### Mode 1: Lightweight (tree-sitter, no build required)

```bash
python -m lock_graph_pipeline /path/to/java/source \
    --mode light \
    -o output/lock_graph.json \
    -m output/lock_graph.md
```

### Mode 2: Infer (deep interprocedural analysis)

```bash
# First run Infer on the target repo
cd /path/to/repo && infer --starvation -- gradle assemble

# Then feed output to pipeline
python -m lock_graph_pipeline /path/to/repo/src \
    --mode infer \
    --infer-out /path/to/repo/infer-out \
    -o output/lock_graph.json
```

### Mode 3: Both (merge for maximum coverage)

```bash
python -m lock_graph_pipeline /path/to/repo/src \
    --mode both \
    --infer-out /path/to/repo/infer-out \
    -o output/lock_graph.json \
    -m output/lock_graph.md
```

### Options

- `--mode light|infer|both` — analysis mode (default: light)
- `--infer-out DIR` — path to Infer output directory (required for infer/both)
- `-o FILE` — output JSON path (default: lock_graph.json)
- `-m FILE` — output Markdown summary path
- `--patterns FILE` — path to deadlock_patterns.json (auto-detected by default)
- `--include-tests` — include test directories in scan

## Pipeline Architecture

1. **Layer 1 (Scanner)**: Fast regex filter to identify Java files with locking constructs (~5-15% of repo)
2. **Layer 2 (Tree-sitter)**: AST extraction of lock acquisitions, wait/notify sites, calls under lock, nested locks
3. **Layer 3 (Resolver)**: Cross-class edge resolution — connects calls-under-lock to callee's lock acquisitions
4. **Infer Adapter**: Parses Facebook Infer's starvation analysis for deep interprocedural edges
5. **Template Matcher**: Matches extracted graph against known deadlock patterns from `deadlock_patterns.json`
6. **Output Formatter**: Produces JSON + Markdown output

## Output Schema

See `data_types.py` for the full schema. The JSON output contains:
- `metadata` — scan statistics
- `classes` — per-class lock profiles (acquisitions, wait/notify, calls under lock, field types)
- `lock_order_edges` — directed edges showing lock ordering relationships
- `wait_notify_summary` — classes with wait/notify and risk indicators
- `candidate_matches` — matches against known deadlock patterns

## Limitations

**Tree-sitter mode**: No inheritance resolution, lock-unlock pairing is line-range heuristic, single-level call resolution only.

**Infer mode**: Requires target repo to compile and Infer to be installed.
