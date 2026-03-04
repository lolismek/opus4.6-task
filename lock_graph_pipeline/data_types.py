"""Shared dataclasses for the lock graph pipeline."""

from __future__ import annotations
from dataclasses import dataclass, field


@dataclass
class LockAcquisition:
    file_path: str
    class_name: str
    method_name: str
    lock_type: str  # synchronized_method|synchronized_block|reentrant_lock|read_lock|write_lock
    lock_identity: str
    line_number: int
    is_static: bool


@dataclass
class WaitNotifySite:
    file_path: str
    class_name: str
    method_name: str
    call_type: str  # wait|notify|notifyAll|await|signal|signalAll
    object_expr: str
    line_number: int
    inside_lock: str | None


@dataclass
class MethodCallUnderLock:
    file_path: str
    class_name: str
    method_name: str
    holding_lock: str
    callee_object: str
    callee_method: str
    callee_class_hint: str
    line_number: int


@dataclass
class LockOrderEdge:
    from_lock: str
    to_lock: str
    from_class: str
    from_method: str
    mechanism: str  # nested_synchronized|lock_under_lock|call_to_locking_method|infer_starvation
    call_chain: list[str]
    file_path: str
    line_number: int
    source: str  # "treesitter" | "infer"


@dataclass
class ClassLockProfile:
    class_name: str
    file_path: str
    lock_acquisitions: list[LockAcquisition] = field(default_factory=list)
    wait_notify_sites: list[WaitNotifySite] = field(default_factory=list)
    calls_under_lock: list[MethodCallUnderLock] = field(default_factory=list)
    field_types: dict[str, str] = field(default_factory=dict)


@dataclass
class LockGraph:
    repo_path: str
    classes: dict[str, ClassLockProfile] = field(default_factory=dict)
    lock_order_edges: list[LockOrderEdge] = field(default_factory=list)
    candidate_matches: list[dict] = field(default_factory=list)
