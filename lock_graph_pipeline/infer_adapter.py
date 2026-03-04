"""Infer adapter: parse Facebook Infer's starvation/deadlock output into our schema."""

from __future__ import annotations

import json
from pathlib import Path

from .data_types import LockOrderEdge


def parse_infer_output(infer_out_dir: str) -> list[LockOrderEdge]:
    """Parse Infer's report.json to extract lock ordering edges.

    Looks for STARVATION and DEADLOCK bug types in infer-out/report.json.
    Each report's bug_trace contains the lock acquisition chain.
    """
    infer_out = Path(infer_out_dir)
    edges: list[LockOrderEdge] = []

    # Primary source: report.json
    report_file = infer_out / "report.json"
    if report_file.exists():
        edges.extend(_parse_report_json(report_file))

    return edges


def _parse_report_json(report_file: Path) -> list[LockOrderEdge]:
    """Parse report.json for STARVATION and DEADLOCK entries."""
    with open(report_file, "r") as f:
        reports = json.load(f)

    edges: list[LockOrderEdge] = []
    seen = set()

    for report in reports:
        bug_type = report.get("bug_type", "")
        if bug_type not in ("STARVATION", "DEADLOCK"):
            continue

        # Extract lock acquisitions from the bug trace
        trace = report.get("bug_trace", [])
        file_path = report.get("file", "")
        qualifier = report.get("qualifier", "")

        lock_sites = _extract_lock_sites_from_trace(trace)

        # Create edges between consecutive lock acquisitions
        for i in range(len(lock_sites) - 1):
            from_site = lock_sites[i]
            to_site = lock_sites[i + 1]

            dedup_key = (from_site["lock"], to_site["lock"],
                         from_site.get("class", ""), from_site.get("method", ""))
            if dedup_key in seen:
                continue
            seen.add(dedup_key)

            # Build call chain from trace
            call_chain = []
            for site in lock_sites[: i + 2]:
                if site.get("class") and site.get("method"):
                    call_chain.append(f"{site['class']}.{site['method']}")

            edges.append(LockOrderEdge(
                from_lock=from_site["lock"],
                to_lock=to_site["lock"],
                from_class=from_site.get("class", ""),
                from_method=from_site.get("method", ""),
                mechanism="infer_starvation",
                call_chain=call_chain,
                file_path=to_site.get("file", file_path),
                line_number=to_site.get("line", 0),
                source="infer",
            ))

    return edges


def _extract_lock_sites_from_trace(trace: list[dict]) -> list[dict]:
    """Extract lock acquisition sites from an Infer bug trace.

    Infer trace entries have level, description, filename, line_number.
    Lock acquisitions show up as descriptions containing "locks" or "acquires".
    """
    lock_sites = []

    for entry in trace:
        desc = entry.get("description", "")
        desc_lower = desc.lower()

        # Look for lock acquisition descriptions
        if any(kw in desc_lower for kw in ("locks", "acquires", "lock ", "locking")):
            site: dict = {
                "file": entry.get("filename", ""),
                "line": entry.get("line_number", 0),
                "description": desc,
            }

            # Try to extract lock identity from description
            # Common patterns: "locks `this.lockField`", "acquires lock on `obj`"
            lock_id = _extract_lock_from_description(desc)
            site["lock"] = lock_id

            # Extract class/method from the procedure name or filename
            proc = entry.get("node_tags", [])
            for tag in proc:
                if tag.get("tag") == "procedure":
                    qualified = tag.get("value", "")
                    parts = qualified.rsplit(".", 1)
                    if len(parts) == 2:
                        site["class"] = parts[0].split(".")[-1]
                        site["method"] = parts[1]

            # Fallback: extract from filename
            if "class" not in site:
                fname = Path(entry.get("filename", "")).stem
                site["class"] = fname
                site["method"] = ""

            lock_sites.append(site)

    return lock_sites


def _extract_lock_from_description(desc: str) -> str:
    """Extract lock identity from an Infer trace description."""
    import re

    # Pattern: `lockName` or "lockName"
    m = re.search(r'`([^`]+)`', desc)
    if m:
        return m.group(1)

    m = re.search(r'"([^"]+)"', desc)
    if m:
        return m.group(1)

    # Pattern: "locks this" or "locks <object>"
    m = re.search(r'locks?\s+(\S+)', desc, re.IGNORECASE)
    if m:
        return m.group(1)

    return desc.strip()
