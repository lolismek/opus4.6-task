"""Template matcher: match extracted lock graph against known deadlock patterns."""

from __future__ import annotations

import json
from pathlib import Path

from .data_types import LockGraph, LockOrderEdge


def match_patterns(lock_graph: LockGraph, patterns_file: str | None = None) -> list[dict]:
    """Match the lock graph against known deadlock patterns.

    Returns candidate matches with similarity scores.
    """
    if patterns_file is None:
        # Default to deadlock_patterns.json in the project root
        patterns_file = str(Path(__file__).parent.parent / "deadlock_patterns.json")

    patterns_path = Path(patterns_file)
    if not patterns_path.exists():
        return []

    with open(patterns_path, "r") as f:
        patterns = json.load(f)

    candidates = []

    # 1. Find 2-cycles in lock order edges (resource deadlock candidates)
    cycles = _find_two_cycles(lock_graph.lock_order_edges)
    for cycle in cycles:
        match = _match_cycle_to_patterns(cycle, patterns, lock_graph)
        if match:
            candidates.append(match)

    # 2. Find wait-without-notify patterns (wait-notify deadlock candidates)
    wait_notify_matches = _find_wait_notify_risks(lock_graph, patterns)
    candidates.extend(wait_notify_matches)

    return candidates


def _find_two_cycles(edges: list[LockOrderEdge]) -> list[tuple[LockOrderEdge, LockOrderEdge]]:
    """Find pairs of edges forming A→B and B→A cycles."""
    cycles = []
    seen = set()

    # Build adjacency
    edge_map: dict[tuple[str, str], LockOrderEdge] = {}
    for e in edges:
        edge_map[(e.from_lock, e.to_lock)] = e

    for (a, b), edge_ab in edge_map.items():
        if (b, a) in edge_map:
            key = tuple(sorted([a, b]))
            if key not in seen:
                seen.add(key)
                cycles.append((edge_ab, edge_map[(b, a)]))

    return cycles


def _match_cycle_to_patterns(
    cycle: tuple[LockOrderEdge, LockOrderEdge],
    patterns: list[dict],
    lock_graph: LockGraph,
) -> dict | None:
    """Match a detected 2-cycle against known deadlock patterns."""
    edge_a, edge_b = cycle

    best_score = 0.0
    best_pattern = None

    resource_patterns = [p for p in patterns if p.get("type") == "resource_deadlock"]

    for pattern in resource_patterns:
        score = _compute_similarity(edge_a, edge_b, pattern, lock_graph)
        if score > best_score:
            best_score = score
            best_pattern = pattern

    return {
        "type": "resource_deadlock_cycle",
        "lock_a": edge_a.from_lock,
        "lock_b": edge_a.to_lock,
        "edge_a": {
            "from_class": edge_a.from_class,
            "from_method": edge_a.from_method,
            "mechanism": edge_a.mechanism,
            "file": edge_a.file_path,
            "line": edge_a.line_number,
            "source": edge_a.source,
        },
        "edge_b": {
            "from_class": edge_b.from_class,
            "from_method": edge_b.from_method,
            "mechanism": edge_b.mechanism,
            "file": edge_b.file_path,
            "line": edge_b.line_number,
            "source": edge_b.source,
        },
        "best_matching_pattern": best_pattern["id"] if best_pattern else None,
        "similarity_score": best_score,
    }


def _compute_similarity(
    edge_a: LockOrderEdge,
    edge_b: LockOrderEdge,
    pattern: dict,
    lock_graph: LockGraph,
) -> float:
    """Score similarity between a detected cycle and a known pattern."""
    score = 0.0

    # Pattern keyword overlap
    pattern_keywords = set()
    desc = pattern.get("description", "").lower()
    for word in desc.split():
        if len(word) > 3:
            pattern_keywords.add(word)

    context_words = set()
    for cls_name in [edge_a.from_class, edge_b.from_class]:
        # Split camelCase
        import re
        parts = re.findall(r'[A-Z][a-z]+|[a-z]+', cls_name)
        context_words.update(w.lower() for w in parts)
    for m in [edge_a.from_method, edge_b.from_method]:
        parts = re.findall(r'[A-Z][a-z]+|[a-z]+', m)
        context_words.update(w.lower() for w in parts)

    overlap = pattern_keywords & context_words
    if pattern_keywords:
        score += len(overlap) / len(pattern_keywords) * 0.5

    # Structural similarity: same pattern type
    pat_pattern = pattern.get("pattern", "")
    if pat_pattern == "two_object_cycle":
        score += 0.3  # Most common, small bonus
    elif pat_pattern == "callback_induced_cycle" and edge_a.mechanism == "call_to_locking_method":
        score += 0.5

    # Number of locks match
    pat_locks = pattern.get("locks", [])
    if len(pat_locks) == 2:
        score += 0.2

    return min(score, 1.0)


def _find_wait_notify_risks(lock_graph: LockGraph, patterns: list[dict]) -> list[dict]:
    """Find classes that use wait() but have no corresponding notify()."""
    classes_with_wait = set()
    classes_with_notify = set()

    for cls_name, cls_profile in lock_graph.classes.items():
        for site in cls_profile.wait_notify_sites:
            if site.call_type in ("wait", "await"):
                classes_with_wait.add(cls_name)
            elif site.call_type in ("notify", "notifyAll", "signal", "signalAll"):
                classes_with_notify.add(cls_name)

    wait_only = classes_with_wait - classes_with_notify

    results = []
    wait_patterns = [p for p in patterns if p.get("type") == "wait_notify_deadlock"]

    for cls_name in wait_only:
        best_pattern = None
        best_score = 0.0

        for pattern in wait_patterns:
            # Simple keyword scoring
            desc = pattern.get("description", "").lower()
            import re
            parts = re.findall(r'[A-Z][a-z]+|[a-z]+', cls_name)
            cls_words = {w.lower() for w in parts}
            desc_words = {w for w in desc.split() if len(w) > 3}
            overlap = cls_words & desc_words
            score = len(overlap) / max(len(desc_words), 1) * 0.5 + 0.2
            if score > best_score:
                best_score = score
                best_pattern = pattern

        results.append({
            "type": "wait_without_notify",
            "class": cls_name,
            "file": lock_graph.classes[cls_name].file_path,
            "best_matching_pattern": best_pattern["id"] if best_pattern else None,
            "similarity_score": best_score,
        })

    return results
