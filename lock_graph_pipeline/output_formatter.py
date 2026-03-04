"""Output formatter: JSON and Markdown output for the lock graph."""

from __future__ import annotations

import json
from dataclasses import asdict
from pathlib import Path

from .data_types import LockGraph


def to_json(lock_graph: LockGraph, mode: str) -> dict:
    """Convert LockGraph to the output JSON schema."""
    # Compute metadata
    all_files = set()
    files_with_locks = set()
    total_acquisitions = 0

    for cls in lock_graph.classes.values():
        all_files.add(cls.file_path)
        if cls.lock_acquisitions:
            files_with_locks.add(cls.file_path)
        total_acquisitions += len(cls.lock_acquisitions)

    # Wait/notify summary
    classes_with_wait = set()
    classes_with_notify = set()
    for cls_name, cls_profile in lock_graph.classes.items():
        for site in cls_profile.wait_notify_sites:
            if site.call_type in ("wait", "await"):
                classes_with_wait.add(cls_name)
            elif site.call_type in ("notify", "notifyAll", "signal", "signalAll"):
                classes_with_notify.add(cls_name)

    output = {
        "metadata": {
            "repo_path": lock_graph.repo_path,
            "mode": mode,
            "total_java_files": len(all_files),
            "files_with_locks": len(files_with_locks),
            "total_lock_acquisitions": total_acquisitions,
            "total_lock_order_edges": len(lock_graph.lock_order_edges),
        },
        "classes": {},
        "lock_order_edges": [],
        "wait_notify_summary": {
            "classes_with_wait": sorted(classes_with_wait),
            "classes_with_notify": sorted(classes_with_notify),
            "classes_with_wait_but_no_notify": sorted(classes_with_wait - classes_with_notify),
        },
        "candidate_matches": lock_graph.candidate_matches,
    }

    for cls_name, cls_profile in lock_graph.classes.items():
        output["classes"][cls_name] = {
            "file_path": cls_profile.file_path,
            "lock_acquisitions": [asdict(a) for a in cls_profile.lock_acquisitions],
            "wait_notify_sites": [asdict(s) for s in cls_profile.wait_notify_sites],
            "calls_under_lock": [asdict(c) for c in cls_profile.calls_under_lock],
            "field_types": cls_profile.field_types,
        }

    for edge in lock_graph.lock_order_edges:
        output["lock_order_edges"].append(asdict(edge))

    return output


def write_json(lock_graph: LockGraph, mode: str, output_path: str):
    """Write lock graph as JSON."""
    data = to_json(lock_graph, mode)
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w") as f:
        json.dump(data, f, indent=2)


def write_markdown(lock_graph: LockGraph, mode: str, output_path: str):
    """Write a human/LLM-readable markdown summary."""
    data = to_json(lock_graph, mode)
    meta = data["metadata"]
    lines = []

    lines.append(f"# Lock Graph Summary: {Path(lock_graph.repo_path).name}")
    lines.append("")
    lines.append(f"**Mode**: {mode}")
    lines.append(f"**Java files scanned**: {meta['total_java_files']}")
    lines.append(f"**Files with locks**: {meta['files_with_locks']}")
    lines.append(f"**Total lock acquisitions**: {meta['total_lock_acquisitions']}")
    lines.append(f"**Lock order edges**: {meta['total_lock_order_edges']}")
    lines.append("")

    # Lock hotspots
    lines.append("## Lock Hotspots")
    lines.append("")
    hotspots = []
    for cls_name, cls_data in data["classes"].items():
        n_acq = len(cls_data["lock_acquisitions"])
        if n_acq > 0:
            hotspots.append((cls_name, n_acq, cls_data["file_path"]))
    hotspots.sort(key=lambda x: -x[1])

    if hotspots:
        lines.append("| Class | Lock Acquisitions | File |")
        lines.append("|-------|------------------|------|")
        for cls_name, count, fpath in hotspots[:20]:
            lines.append(f"| {cls_name} | {count} | {fpath} |")
    else:
        lines.append("No lock acquisitions found.")
    lines.append("")

    # Lock order edges
    lines.append("## Lock Order Edges")
    lines.append("")
    if data["lock_order_edges"]:
        lines.append("| From Lock | To Lock | Class.Method | Mechanism | Source | File:Line |")
        lines.append("|-----------|---------|-------------|-----------|--------|-----------|")
        for edge in data["lock_order_edges"]:
            loc = f"{edge['file_path']}:{edge['line_number']}"
            lines.append(
                f"| {edge['from_lock']} | {edge['to_lock']} "
                f"| {edge['from_class']}.{edge['from_method']} "
                f"| {edge['mechanism']} | {edge['source']} | {loc} |"
            )
    else:
        lines.append("No lock order edges detected.")
    lines.append("")

    # Cycles
    cycles = _find_cycles_from_edges(data["lock_order_edges"])
    lines.append("## Detected Cycles")
    lines.append("")
    if cycles:
        for i, (lock_a, lock_b) in enumerate(cycles, 1):
            lines.append(f"{i}. **{lock_a}** ↔ **{lock_b}**")
    else:
        lines.append("No lock ordering cycles detected.")
    lines.append("")

    # Wait/notify risks
    wn = data["wait_notify_summary"]
    lines.append("## Wait/Notify Risk")
    lines.append("")
    if wn["classes_with_wait_but_no_notify"]:
        lines.append("Classes with `wait()`/`await()` but no `notify()`/`signal()` in same class:")
        lines.append("")
        for cls_name in wn["classes_with_wait_but_no_notify"]:
            lines.append(f"- {cls_name}")
    else:
        lines.append("No wait-without-notify risks detected.")
    lines.append("")

    # Candidate matches
    lines.append("## Candidate Insertion Points")
    lines.append("")
    if data["candidate_matches"]:
        for match in data["candidate_matches"]:
            match_type = match.get("type", "unknown")
            if match_type == "resource_deadlock_cycle":
                lines.append(
                    f"- **Cycle**: {match['lock_a']} ↔ {match['lock_b']} "
                    f"(pattern: {match.get('best_matching_pattern', 'N/A')}, "
                    f"score: {match.get('similarity_score', 0):.2f})"
                )
            elif match_type == "wait_without_notify":
                lines.append(
                    f"- **Wait risk**: {match['class']} "
                    f"(pattern: {match.get('best_matching_pattern', 'N/A')}, "
                    f"score: {match.get('similarity_score', 0):.2f})"
                )
    else:
        lines.append("No candidate insertion points identified.")
    lines.append("")

    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w") as f:
        f.write("\n".join(lines))


def _find_cycles_from_edges(edges: list[dict]) -> list[tuple[str, str]]:
    """Find 2-cycles in the edge list."""
    edge_set = {(e["from_lock"], e["to_lock"]) for e in edges}
    cycles = []
    seen = set()
    for a, b in edge_set:
        if (b, a) in edge_set:
            key = tuple(sorted([a, b]))
            if key not in seen:
                seen.add(key)
                cycles.append((a, b))
    return cycles
