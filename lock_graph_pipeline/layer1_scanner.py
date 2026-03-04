"""Layer 1: Fast regex-based file scanner to identify Java files with locking constructs."""

from __future__ import annotations

import os
import re
from pathlib import Path

SKIP_DIRS = {".git", "target", "build", "node_modules", ".gradle", ".idea", ".settings"}

LOCK_PATTERNS = [
    re.compile(r"\bsynchronized\b"),
    re.compile(r"\.lock\s*\("),
    re.compile(r"\.unlock\s*\("),
    re.compile(r"\bReentrantLock\b"),
    re.compile(r"\bReadWriteLock\b"),
    re.compile(r"\bReentrantReadWriteLock\b"),
    re.compile(r"\.readLock\s*\("),
    re.compile(r"\.writeLock\s*\("),
    re.compile(r"\.wait\s*\("),
    re.compile(r"\.notify\s*\("),
    re.compile(r"\.notifyAll\s*\("),
    re.compile(r"\.await\s*\("),
    re.compile(r"\.signal\s*\("),
    re.compile(r"\.signalAll\s*\("),
    re.compile(r"\bCondition\b"),
]


def scan_repo(repo_path: str, include_tests: bool = False) -> list[tuple[str, list[str]]]:
    """Walk all .java files and return those matching lock-related patterns.

    Returns list of (filepath, matched_pattern_names).
    """
    results = []
    repo = Path(repo_path)

    for root, dirs, files in os.walk(repo):
        # Prune skipped directories
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]

        # Skip test directories unless requested
        if not include_tests:
            rel = os.path.relpath(root, repo)
            parts = rel.split(os.sep)
            if any(p in ("test", "tests", "testFixtures") for p in parts):
                dirs.clear()
                continue

        for fname in files:
            if not fname.endswith(".java"):
                continue

            fpath = os.path.join(root, fname)
            try:
                with open(fpath, "r", encoding="utf-8", errors="replace") as f:
                    content = f.read()
            except OSError:
                continue

            matched = []
            for pat in LOCK_PATTERNS:
                if pat.search(content):
                    matched.append(pat.pattern)

            if matched:
                results.append((fpath, matched))

    return results
