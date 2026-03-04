"""Layer 3: Cross-class edge resolution — resolve calls-under-lock to lock ordering edges."""

from __future__ import annotations

from .data_types import ClassLockProfile, LockOrderEdge, MethodCallUnderLock


def resolve_cross_class_edges(
    classes: dict[str, ClassLockProfile],
    existing_edges: list[LockOrderEdge],
) -> list[LockOrderEdge]:
    """Resolve method calls under lock into lock ordering edges.

    For each call-under-lock, check if the callee method acquires a lock.
    If so, create a LockOrderEdge from the held lock to the callee's lock.
    """
    # Build lookup: (class_name, method_name) → list of lock identities acquired
    method_locks: dict[tuple[str, str], list[str]] = {}
    for cls in classes.values():
        for acq in cls.lock_acquisitions:
            key = (cls.class_name, acq.method_name)
            method_locks.setdefault(key, []).append(acq.lock_identity)

    new_edges: list[LockOrderEdge] = []

    for cls in classes.values():
        for call in cls.calls_under_lock:
            # Try to resolve which class the callee belongs to
            callee_classes = _resolve_callee_class(call, classes)

            for callee_class in callee_classes:
                key = (callee_class, call.callee_method)
                callee_locks = method_locks.get(key, [])

                for callee_lock in callee_locks:
                    if callee_lock == call.holding_lock:
                        continue  # Reentrant, not a new edge

                    edge = LockOrderEdge(
                        from_lock=call.holding_lock,
                        to_lock=callee_lock,
                        from_class=cls.class_name,
                        from_method=call.method_name,
                        mechanism="call_to_locking_method",
                        call_chain=[f"{cls.class_name}.{call.method_name}",
                                    f"{callee_class}.{call.callee_method}"],
                        file_path=call.file_path,
                        line_number=call.line_number,
                        source="treesitter",
                    )

                    # Deduplicate against existing
                    if not _edge_exists(edge, existing_edges) and not _edge_exists(edge, new_edges):
                        new_edges.append(edge)

    return new_edges


def _resolve_callee_class(
    call: MethodCallUnderLock,
    classes: dict[str, ClassLockProfile],
) -> list[str]:
    """Resolve the callee class from the call-under-lock info."""
    candidates = []

    # 1. Direct class hint from field type resolution
    if call.callee_class_hint and call.callee_class_hint in classes:
        candidates.append(call.callee_class_hint)

    # 2. If callee_object is "this", it's the same class
    if call.callee_object == "this":
        if call.class_name in classes:
            candidates.append(call.class_name)

    # 3. Search all classes for a method with that name
    if not candidates:
        for cls_name, cls_profile in classes.items():
            method_names = {a.method_name for a in cls_profile.lock_acquisitions}
            if call.callee_method in method_names:
                candidates.append(cls_name)

    return candidates


def _edge_exists(edge: LockOrderEdge, edges: list[LockOrderEdge]) -> bool:
    for e in edges:
        if (e.from_lock == edge.from_lock and e.to_lock == edge.to_lock
                and e.from_class == edge.from_class and e.from_method == edge.from_method):
            return True
    return False
