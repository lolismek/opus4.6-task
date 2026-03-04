"""Main CLI entry point for the lock graph extraction pipeline."""

from __future__ import annotations

import argparse
import sys
import time

from .data_types import LockGraph
from .layer1_scanner import scan_repo
from .layer2_treesitter import TreeSitterExtractor
from .layer3_resolver import resolve_cross_class_edges
from .infer_adapter import parse_infer_output
from .template_matcher import match_patterns
from .output_formatter import write_json, write_markdown


def run_light_mode(repo_path: str, include_tests: bool = False) -> LockGraph:
    """Run the lightweight tree-sitter-based extraction."""
    print(f"[layer1] Scanning {repo_path} for lock-related files...")
    flagged = scan_repo(repo_path, include_tests=include_tests)
    print(f"[layer1] Found {len(flagged)} files with lock patterns")

    extractor = TreeSitterExtractor()
    graph = LockGraph(repo_path=repo_path)
    all_edges = []

    print("[layer2] Extracting lock graph with tree-sitter...")
    for fpath, _patterns in flagged:
        try:
            profiles, edges = extractor.extract_file_with_edges(fpath)
            all_edges.extend(edges)
            for profile in profiles:
                # Merge if class already seen (e.g., inner classes across files)
                if profile.class_name in graph.classes:
                    existing = graph.classes[profile.class_name]
                    existing.lock_acquisitions.extend(profile.lock_acquisitions)
                    existing.wait_notify_sites.extend(profile.wait_notify_sites)
                    existing.calls_under_lock.extend(profile.calls_under_lock)
                    existing.field_types.update(profile.field_types)
                else:
                    graph.classes[profile.class_name] = profile
        except Exception as e:
            print(f"  [warn] Failed to parse {fpath}: {e}", file=sys.stderr)

    print(f"[layer2] Extracted {len(graph.classes)} classes, {len(all_edges)} direct edges")

    # Layer 3: cross-class resolution
    print("[layer3] Resolving cross-class lock ordering edges...")
    cross_edges = resolve_cross_class_edges(graph.classes, all_edges)
    all_edges.extend(cross_edges)
    print(f"[layer3] Found {len(cross_edges)} additional cross-class edges")

    graph.lock_order_edges = all_edges
    return graph


def run_infer_mode(repo_path: str, infer_out: str) -> LockGraph:
    """Run Infer-based extraction."""
    print(f"[infer] Parsing Infer output from {infer_out}...")
    edges = parse_infer_output(infer_out)
    print(f"[infer] Extracted {len(edges)} lock ordering edges from Infer")

    graph = LockGraph(repo_path=repo_path)
    graph.lock_order_edges = edges
    return graph


def run_both_mode(repo_path: str, infer_out: str, include_tests: bool = False) -> LockGraph:
    """Run both modes and merge results."""
    light_graph = run_light_mode(repo_path, include_tests=include_tests)
    infer_edges = parse_infer_output(infer_out)

    print(f"[merge] Merging {len(light_graph.lock_order_edges)} tree-sitter edges "
          f"with {len(infer_edges)} Infer edges...")

    # Deduplicate
    seen = set()
    merged = []
    for e in light_graph.lock_order_edges:
        key = (e.from_lock, e.to_lock, e.from_class, e.from_method)
        if key not in seen:
            seen.add(key)
            merged.append(e)
    for e in infer_edges:
        key = (e.from_lock, e.to_lock, e.from_class, e.from_method)
        if key not in seen:
            seen.add(key)
            merged.append(e)

    light_graph.lock_order_edges = merged
    print(f"[merge] Total unique edges: {len(merged)}")
    return light_graph


def main():
    parser = argparse.ArgumentParser(
        description="Extract Java lock graph for deadlock pattern injection analysis",
    )
    parser.add_argument("repo_path", help="Path to the Java source tree to analyze")
    parser.add_argument("--mode", choices=["light", "infer", "both"], default="light",
                        help="Analysis mode (default: light)")
    parser.add_argument("--infer-out", help="Path to Infer output directory (required for infer/both modes)")
    parser.add_argument("-o", "--output", default="lock_graph.json",
                        help="Output JSON file path")
    parser.add_argument("-m", "--markdown", help="Output Markdown summary file path")
    parser.add_argument("--patterns", help="Path to deadlock_patterns.json (default: auto-detect)")
    parser.add_argument("--include-tests", action="store_true",
                        help="Include test directories in scan")

    args = parser.parse_args()

    if args.mode in ("infer", "both") and not args.infer_out:
        parser.error("--infer-out is required for infer/both modes")

    start = time.time()

    if args.mode == "light":
        graph = run_light_mode(args.repo_path, include_tests=args.include_tests)
    elif args.mode == "infer":
        graph = run_infer_mode(args.repo_path, args.infer_out)
    elif args.mode == "both":
        graph = run_both_mode(args.repo_path, args.infer_out, include_tests=args.include_tests)

    # Template matching
    print("[match] Matching against deadlock patterns...")
    graph.candidate_matches = match_patterns(graph, args.patterns)
    print(f"[match] Found {len(graph.candidate_matches)} candidate matches")

    # Output
    write_json(graph, args.mode, args.output)
    print(f"[output] Wrote JSON to {args.output}")

    if args.markdown:
        write_markdown(graph, args.mode, args.markdown)
        print(f"[output] Wrote Markdown to {args.markdown}")

    elapsed = time.time() - start
    print(f"[done] Completed in {elapsed:.1f}s")


if __name__ == "__main__":
    main()
