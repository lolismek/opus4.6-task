# Lock Graph Pipeline

Extracts a machine-readable lock graph from Java source code, identifying synchronization primitives, lock ordering edges, and potential deadlock patterns. Designed to produce a "lock map" that an LLM can use to find natural insertion points for deadlock pattern injection.

Two analysis modes produce the same output schema:

- **Light mode** (tree-sitter): Fast, no build required. Parses Java AST to find locks, wait/notify, calls under lock, and nested lock orderings. Qualifies all lock identities with their owning class (e.g., `Leader.this`, `DataTree.node`) to avoid cross-class conflation. Cross-class edges are resolved only when the callee type is positively known from field declarations.

- **Infer mode**: Deep interprocedural analysis via Facebook Infer's starvation checker. Finds lock orderings across arbitrary call depths, resolves virtual dispatch, and handles lambdas/anonymous classes. Requires the target repo to compile. Extracts the internal lock graph from Infer's debug summaries (not just reported bugs).

## Installation

```bash
pip install -r lock_graph_pipeline/requirements.txt
```

For Infer mode, install Infer separately:
```bash
# Download prebuilt binary (macOS ARM64 example)
curl -sL https://github.com/facebook/infer/releases/download/v1.2.0/infer-osx-arm64-v1.2.0.tar.xz | tar xJ -C /opt
export PATH="/opt/infer-osx-arm64-v1.2.0/bin:$PATH"
```

## Usage

### Mode 1: Light (tree-sitter only, no build needed)

```bash
python -m lock_graph_pipeline /path/to/java/source \
    --mode light \
    -o output/lock_graph.json \
    -m output/lock_graph.md
```

### Mode 2: Infer (deep analysis, requires repo to compile)

```bash
# Step A: Run Infer on the target repo (Maven example)
cd /path/to/repo && infer --starvation-only -- mvn compile -DskipTests

# Step B: Feed Infer's output to the pipeline
python -m lock_graph_pipeline /path/to/repo/src \
    --mode infer \
    --infer-out /path/to/repo/infer-out \
    -o output/lock_graph.json \
    -m output/lock_graph.md
```

If `infer` is not on PATH, use `--infer-bin /path/to/infer`.

### Mode 3: Both (merge for maximum coverage)

```bash
python -m lock_graph_pipeline /path/to/repo/src \
    --mode both \
    --infer-out /path/to/repo/infer-out \
    -o output/lock_graph.json \
    -m output/lock_graph.md
```

### Options

| Flag | Description |
|------|-------------|
| `--mode light\|infer\|both` | Analysis mode (default: `light`) |
| `--infer-out DIR` | Path to Infer output directory (required for `infer`/`both`) |
| `--infer-bin PATH` | Path to `infer` binary (default: `infer` on PATH) |
| `-o FILE` | Output JSON path (default: `lock_graph.json`) |
| `-m FILE` | Output Markdown summary path |
| `--patterns FILE` | Path to `deadlock_patterns.json` (auto-detected by default) |
| `--include-tests` | Include test directories in scan |

## Pipeline Architecture

```
layer1_scanner.py       Regex file filter (~5-15% of repo passes)
        |
layer2_treesitter.py    Tree-sitter AST extraction (locks, wait/notify, calls under lock)
        |
layer3_resolver.py      Cross-class edge resolution (type-safe only)
        |
infer_adapter.py        Parse Infer debug summaries for deep interprocedural edges
        |
template_matcher.py     Match lock graph against deadlock_patterns.json
        |
output_formatter.py     JSON + Markdown output
```

### How Infer integration works

Infer's `--starvation` analysis builds an internal lock ordering graph stored in per-procedure summaries. Even on clean repos with no bugs, this graph contains every lock acquisition and its context (what locks are already held, the full call chain).

The adapter extracts this by running:
```
infer debug --procedures --procedures-summary --procedures-summary-skip-empty --select all
```

Each procedure summary contains `critical_pairs` with:
- `acquisitions`: locks already held when this event occurs
- `event`: `LockAcquire(lock_expression)` — the lock being acquired
- `trace`: the interprocedural call chain leading to the acquisition

The adapter parses these to produce `LockOrderEdge` entries with `source="infer"`.

## Output Schema

```json
{
  "metadata": {
    "repo_path": "...",
    "mode": "light|infer|both",
    "total_java_files": 70,
    "files_with_locks": 67,
    "total_lock_acquisitions": 469,
    "total_lock_order_edges": 63
  },
  "classes": {
    "ClassName": {
      "file_path": "...",
      "lock_acquisitions": [...],
      "wait_notify_sites": [...],
      "calls_under_lock": [...],
      "field_types": {"fieldName": "TypeName"}
    }
  },
  "lock_order_edges": [
    {
      "from_lock": "Leader.this",
      "to_lock": "Leader.forwardingFollowers",
      "from_class": "Leader",
      "from_method": "propose",
      "mechanism": "call_to_locking_method",
      "call_chain": ["Leader.propose", "Leader.sendPacket"],
      "file_path": "...",
      "line_number": 1331,
      "source": "treesitter|infer"
    }
  ],
  "wait_notify_summary": {
    "classes_with_wait": [...],
    "classes_with_notify": [...],
    "classes_with_wait_but_no_notify": [...]
  },
  "candidate_matches": [...]
}
```

## Tested On

| Target | Mode | Edges | Cycles | Time |
|--------|------|-------|--------|------|
| JaConTeBe benchmark (27 files) | light | 27 | 2 | 0.1s |
| ZooKeeper server (396 files) | light | 63 | 3 | 0.8s |
| ZooKeeper server | infer | 64 | 0 | 0.3s* |
| ZooKeeper server | both | 112 | 3 | 1.0s |

*Excludes Infer's own analysis time (~2 min for ZooKeeper).

## Limitations

**Tree-sitter mode:**
- No inheritance resolution (doesn't know B extends A's synchronized methods)
- Lock-unlock pairing for ReentrantLock is a line-range heuristic, not control-flow based
- Cross-class edge resolution requires field type declarations (won't resolve through interfaces or generics)
- Single-level call resolution only (A calls B, not A calls B calls C)

**Infer mode:**
- Requires the target repo to compile with a supported build system (Maven, Gradle, javac)
- Infer must be installed separately
- Does not provide wait/notify site detection (only lock ordering)
- Analysis time scales with repo size (minutes for medium repos)

**Both modes combined** give the best coverage: tree-sitter provides breadth (all lock sites, wait/notify, shallow edges) while Infer provides depth (multi-hop call chains, virtual dispatch resolution).
