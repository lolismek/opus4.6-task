"""Infer adapter: parse Facebook Infer's starvation debug summaries into our schema.

Infer's --starvation analysis builds an internal lock ordering graph stored in
per-procedure summaries. To extract it:

    infer debug --procedures --procedures-summary --procedures-summary-skip-empty \
        --select all --results-dir infer-out > summaries.txt

This module parses that text output, extracting LockAcquire events and their
held-lock contexts (acquisitions) to produce LockOrderEdge entries.
"""

from __future__ import annotations

import json
import re
import subprocess
from pathlib import Path

from .data_types import LockOrderEdge, LockAcquisition, ClassLockProfile, LockGraph


def parse_infer_output(infer_out_dir: str, infer_bin: str | None = None) -> list[LockOrderEdge]:
    """Extract lock ordering edges from Infer's starvation analysis.

    First tries to dump debug summaries by running `infer debug`. If a pre-generated
    summaries file exists at infer_out_dir/summaries.txt, uses that instead.
    """
    infer_out = Path(infer_out_dir)
    edges: list[LockOrderEdge] = []

    # Try pre-generated summaries file first
    summaries_file = infer_out / "summaries.txt"
    if not summaries_file.exists():
        summaries_file = _generate_summaries(infer_out, infer_bin)

    if summaries_file and summaries_file.exists():
        text = summaries_file.read_text(errors="replace")
        edges = _parse_summaries_text(text)

    # Also parse report.json for any explicit STARVATION/DEADLOCK bugs
    report_file = infer_out / "report.json"
    if report_file.exists():
        report_edges = _parse_report_json(report_file)
        # Merge, deduplicating
        seen = {(e.from_lock, e.to_lock, e.from_class, e.from_method) for e in edges}
        for e in report_edges:
            key = (e.from_lock, e.to_lock, e.from_class, e.from_method)
            if key not in seen:
                seen.add(key)
                edges.append(e)

    return edges


def extract_infer_lock_profiles(infer_out_dir: str, infer_bin: str | None = None) -> dict[str, ClassLockProfile]:
    """Extract per-class lock profiles from Infer summaries.

    Returns class profiles with lock acquisitions (no wait/notify or calls-under-lock,
    since Infer doesn't expose those separately).
    """
    infer_out = Path(infer_out_dir)
    summaries_file = infer_out / "summaries.txt"
    if not summaries_file.exists():
        summaries_file = _generate_summaries(infer_out, infer_bin)

    if not summaries_file or not summaries_file.exists():
        return {}

    text = summaries_file.read_text(errors="replace")
    return _extract_profiles_from_summaries(text)


def _generate_summaries(infer_out: Path, infer_bin: str | None) -> Path | None:
    """Run infer debug to generate summaries.txt."""
    if infer_bin is None:
        # Try to find infer on PATH
        infer_bin = "infer"

    summaries_file = infer_out / "summaries.txt"
    try:
        result = subprocess.run(
            [infer_bin, "debug", "--procedures", "--procedures-summary",
             "--procedures-summary-skip-empty", "--select", "all",
             "--results-dir", str(infer_out)],
            capture_output=True, text=True, timeout=300,
        )
        if result.returncode == 0 and result.stdout:
            summaries_file.write_text(result.stdout)
            return summaries_file
    except (FileNotFoundError, subprocess.TimeoutExpired):
        pass

    return None


def _parse_summaries_text(text: str) -> list[LockOrderEdge]:
    """Parse the text output of infer debug --procedures-summary.

    Extracts critical_pairs where acquisitions is non-empty (meaning a lock
    is acquired while holding another lock).
    """
    edges: list[LockOrderEdge] = []
    seen: set[tuple[str, str, str, str]] = set()

    # Split into per-procedure blocks on "ERRORS:" which marks block boundaries
    # Each block: signature_line\nERRORS:\nWARNINGS:\n...\nStarvation: {...}\nsummary_loads=...
    proc_blocks = re.split(r'\n(?=\S[^\n]*\nERRORS:)', text)

    for block in proc_blocks:
        # First line is the procedure signature
        first_line = block.split('\n')[0].strip()
        if not first_line or 'ERRORS:' in first_line:
            continue

        class_name, method_name = _extract_class_method(first_line)

        # Find all critical pairs with non-empty acquisitions
        # Pattern: acquisitions= { {elem= <lock=LOCK_EXPR; ...> ...} }; event= LockAcquire(LOCK_EXPR)
        # We look for pairs where acquisitions has content (nested lock acquisition)

        pairs = _extract_critical_pairs(block)
        for held_locks, acquired_lock, line_num, call_chain in pairs:
            for held_lock in held_locks:
                held_id = _simplify_lock_id(held_lock)
                acq_id = _simplify_lock_id(acquired_lock)

                if held_id == acq_id:
                    continue  # Reentrant

                key = (held_id, acq_id, class_name, method_name)
                if key in seen:
                    continue
                seen.add(key)

                edges.append(LockOrderEdge(
                    from_lock=held_id,
                    to_lock=acq_id,
                    from_class=class_name,
                    from_method=method_name,
                    mechanism="infer_starvation",
                    call_chain=call_chain,
                    file_path="",
                    line_number=line_num,
                    source="infer",
                ))

    return edges


def _extract_class_method(sig_line: str) -> tuple[str, str]:
    """Extract class name and method name from Infer procedure signature.

    Formats:
      "ReturnType ClassName.methodName(params)(actual_params)"
      "void void Leader.propose(Request)(...)"
      "void DataTree.createNode(String,byte[],List,long,int,long,long,Stat)(...)"
      "CircularBlockingQueue.<init>(int)"
    """
    # Find pattern: Word(possibly.qualified$inner).methodName(
    # Take the LAST match before the first ( to avoid matching return types
    matches = list(re.finditer(r'([\w$]+)\.([\w$<>]+)\(', sig_line))
    if matches:
        # Use the first match that looks like a real class (capitalized)
        for m in matches:
            cls = m.group(1)
            method = m.group(2)
            if cls[0].isupper():
                # Simplify inner classes: Class$Inner -> Class$Inner
                return cls, method
        # Fallback to first match
        return matches[0].group(1), matches[0].group(2)

    return "<unknown>", "<unknown>"


def _extract_critical_pairs(block: str) -> list[tuple[list[str], str, int, list[str]]]:
    """Extract critical pairs from a procedure summary block.

    Returns list of (held_locks, acquired_lock, line_number, call_chain).
    """
    results = []

    # Split block into critical pair elements
    # Each starts with {elem= {acquisitions=
    pair_chunks = re.split(r'\{elem=\s*\{acquisitions=', block)

    for i, chunk in enumerate(pair_chunks):
        if i == 0:
            continue  # Before first pair

        # Extract held locks from acquisitions
        held_locks = []
        acq_section_match = re.match(r'(.*?);\s*event=', chunk, re.DOTALL)
        if acq_section_match:
            acq_text = acq_section_match.group(1)
            # Find lock= expressions in acquisitions
            # The lock expr looks like: lock=P<0>{(this:Type*)->field};
            # We need to capture everything up to the matching ; but handle nested {}
            for lock_start in re.finditer(r'lock=', acq_text):
                start_pos = lock_start.end()
                # Find the end: scan for ; at depth 0
                depth = 0
                end_pos = start_pos
                while end_pos < len(acq_text):
                    ch = acq_text[end_pos]
                    if ch == '{':
                        depth += 1
                    elif ch == '}':
                        depth -= 1
                    elif ch == ';' and depth <= 0:
                        break
                    end_pos += 1
                lock_expr = acq_text[start_pos:end_pos].strip()
                if lock_expr:
                    held_locks.append(lock_expr)

        # Extract the acquired lock from LockAcquire({ LOCK_EXPR }, ...)
        # The lock expr can contain nested braces, so we need to match balanced braces
        lock_acq_match = re.search(r'event=\s*LockAcquire\(\{', chunk)
        if not lock_acq_match:
            continue
        start = lock_acq_match.end()
        acquired_lock = _extract_balanced_braces(chunk, start)
        if not acquired_lock:
            continue

        # Extract line number (from the pair's loc=, not from inside acquisitions)
        # The pair's loc= comes after the event= section
        # Find loc= line N after the LockAcquire
        after_event = chunk[lock_acq_match.start():]
        line_match = re.search(r'loc=\s*line\s+(\d+)', after_event)
        line_num = int(line_match.group(1)) if line_match else 0

        # Extract call chain from trace
        call_chain = []
        trace_match = re.search(r'trace=\s*\{([^}]*(?:\{[^}]*\}[^}]*)*)\}', after_event)
        if trace_match:
            trace_text = trace_match.group(1)
            for call_match in re.finditer(r'(\w[\w$.]+\.\w+)\(', trace_text):
                call_chain.append(call_match.group(1))

        # Only include pairs where we're acquiring under a held lock
        if held_locks:
            results.append((held_locks, acquired_lock, line_num, call_chain))

    return results


def _extract_balanced_braces(text: str, start: int) -> str:
    """Extract content between balanced braces starting at position start (after opening {)."""
    # We're already past the opening {, so we start with depth=1
    # But actually we need to find the content up to the matching }
    # The content is like: " P<0>{(this:...)->field} "
    # We need everything until the }, but { } can be nested
    depth = 1
    pos = start
    while pos < len(text) and depth > 0:
        if text[pos] == '{':
            depth += 1
        elif text[pos] == '}':
            depth -= 1
        pos += 1
    if depth == 0:
        return text[start:pos - 1].strip()
    return text[start:min(start + 200, len(text))].strip()


def _simplify_lock_id(raw: str) -> str:
    """Simplify Infer's lock expression to a readable identifier.

    Input examples:
      P<0>{(this:org.apache.zookeeper.server.quorum.Leader*)->forwardingFollowers}
      P<0>{(this:org.apache.zookeeper.server.quorum.Leader*)}
      P<0>{(this:org.apache.zookeeper.server.DataTree*)->pTrie->readLock}
      G{(Rcc:org.apache.jute.compiler.generated.Rcc).recTab}
      ($bcvar0:org.apache.zookeeper.ZooKeeper*)->cnxn->eventThread->waitingEvents
      (self:org.apache.zookeeper.server.quorum.Leader*)->forwardingFollowers
      C{org.apache.zookeeper.server.ZooTrace}
    Output:
      Leader.forwardingFollowers
      Leader.this
      DataTree.pTrie.readLock
      Rcc.recTab
      ZooKeeper.cnxn.eventThread.waitingEvents
      Leader.forwardingFollowers
      ZooTrace.class
    """
    # Pattern 1: P<N>{(var:type*)->field->field} or P<N>{(var:type*)}
    m = re.search(r'[PG]<?\d*>?\{?\((?:\w+:)?([^)]+?)\*?\)(?:\)->([\w>-]+))?', raw)
    if m:
        full_type = m.group(1)
        class_name = full_type.rsplit('.', 1)[-1]
        # Remove $ inner class prefix for cleaner names
        if '$' in class_name:
            class_name = class_name.split('$')[0]
        field_path = m.group(2)
        if field_path:
            field_path = field_path.replace('->', '.')
            return f"{class_name}.{field_path}"
        return f"{class_name}.this"

    # Pattern 2: (var:type*)->field->field (no P<N> wrapper)
    m = re.search(r'\((?:\w+):([^)]+?)\*?\)->([\w>-]+)', raw)
    if m:
        full_type = m.group(1)
        class_name = full_type.rsplit('.', 1)[-1]
        if '$' in class_name:
            class_name = class_name.split('$')[0]
        field_path = m.group(2).replace('->', '.')
        return f"{class_name}.{field_path}"

    # Pattern 3: G{(var:type).field} — global/static lock
    m = re.search(r'G\{?\((?:\w+:)?([^)]+)\)\.(\w+)', raw)
    if m:
        full_type = m.group(1)
        class_name = full_type.rsplit('.', 1)[-1]
        return f"{class_name}.{m.group(2)}"

    # Pattern 4: C{type} — class-level lock
    m = re.search(r'C\{([^}]+)\}', raw)
    if m:
        class_name = m.group(1).rsplit('.', 1)[-1]
        return f"{class_name}.class"

    # Fallback: clean up any remaining Infer syntax
    cleaned = re.sub(r'[PG]<\d+>\{|\}', '', raw).strip()
    return cleaned if cleaned else raw


def _extract_profiles_from_summaries(text: str) -> dict[str, ClassLockProfile]:
    """Extract per-class lock profiles from summaries."""
    profiles: dict[str, ClassLockProfile] = {}

    # Find all LockAcquire events (both with and without held locks)
    lock_pattern = re.compile(
        r'event=\s*LockAcquire\(\{\s*([^}]+)\}.*?loc=\s*line\s+(\d+)',
        re.DOTALL,
    )

    proc_blocks = re.split(r'\n(?=\S+\s+\S+\()', text)
    for block in proc_blocks:
        full_sig = block.split('\n')[0]
        class_name, method_name = _extract_class_method(full_sig)
        if class_name == "<unknown>":
            continue

        if class_name not in profiles:
            profiles[class_name] = ClassLockProfile(
                class_name=class_name, file_path="",
            )

        for m in lock_pattern.finditer(block):
            lock_expr = _simplify_lock_id(m.group(1).strip())
            line_num = int(m.group(2))

            # Determine lock type
            if ".readLock" in lock_expr:
                lock_type = "read_lock"
            elif ".writeLock" in lock_expr:
                lock_type = "write_lock"
            elif ".this" in lock_expr:
                lock_type = "synchronized_method"
            else:
                lock_type = "reentrant_lock"

            acq = LockAcquisition(
                file_path="",
                class_name=class_name,
                method_name=method_name,
                lock_type=lock_type,
                lock_identity=lock_expr,
                line_number=line_num,
                is_static=False,
            )
            profiles[class_name].lock_acquisitions.append(acq)

    return profiles


def _parse_report_json(report_file: Path) -> list[LockOrderEdge]:
    """Parse report.json for STARVATION and DEADLOCK entries (if any)."""
    with open(report_file, "r") as f:
        reports = json.load(f)

    edges: list[LockOrderEdge] = []
    seen: set[tuple[str, str, str, str]] = set()

    for report in reports:
        bug_type = report.get("bug_type", "")
        if bug_type not in ("STARVATION", "DEADLOCK"):
            continue

        trace = report.get("bug_trace", [])
        file_path = report.get("file", "")

        lock_sites = []
        for entry in trace:
            desc = entry.get("description", "").lower()
            if any(kw in desc for kw in ("locks", "acquires", "lock ", "locking")):
                lock_id = _extract_lock_from_report_desc(entry.get("description", ""))
                lock_sites.append({
                    "lock": lock_id,
                    "file": entry.get("filename", ""),
                    "line": entry.get("line_number", 0),
                    "class": Path(entry.get("filename", "")).stem,
                    "method": "",
                })

        for i in range(len(lock_sites) - 1):
            from_site = lock_sites[i]
            to_site = lock_sites[i + 1]
            key = (from_site["lock"], to_site["lock"],
                   from_site["class"], from_site["method"])
            if key in seen:
                continue
            seen.add(key)
            edges.append(LockOrderEdge(
                from_lock=from_site["lock"],
                to_lock=to_site["lock"],
                from_class=from_site["class"],
                from_method=from_site["method"],
                mechanism="infer_starvation",
                call_chain=[],
                file_path=to_site.get("file", file_path),
                line_number=to_site.get("line", 0),
                source="infer",
            ))

    return edges


def _extract_lock_from_report_desc(desc: str) -> str:
    """Extract lock identity from an Infer report description."""
    m = re.search(r'`([^`]+)`', desc)
    if m:
        return m.group(1)
    m = re.search(r'"([^"]+)"', desc)
    if m:
        return m.group(1)
    m = re.search(r'locks?\s+(\S+)', desc, re.IGNORECASE)
    if m:
        return m.group(1)
    return desc.strip()
