"""Layer 2: Tree-sitter AST extraction of lock acquisitions, wait/notify, and calls under lock."""

from __future__ import annotations

from pathlib import Path
import tree_sitter_java as tsjava
from tree_sitter import Language, Parser, Node

from .data_types import (
    LockAcquisition,
    WaitNotifySite,
    MethodCallUnderLock,
    LockOrderEdge,
    ClassLockProfile,
)

JAVA_LANGUAGE = Language(tsjava.language())


def _node_text(node: Node) -> str:
    return node.text.decode("utf-8") if node else ""


def _line(node: Node) -> int:
    return node.start_point[0] + 1


def _end_line(node: Node) -> int:
    return node.end_point[0] + 1


def _find_enclosing(node: Node, type_name: str) -> Node | None:
    cur = node.parent
    while cur:
        if cur.type == type_name:
            return cur
        cur = cur.parent
    return None


def _find_enclosing_class(node: Node) -> str:
    cls = _find_enclosing(node, "class_declaration")
    if cls is None:
        cls = _find_enclosing(node, "enum_declaration")
    if cls is None:
        cls = _find_enclosing(node, "interface_declaration")
    if cls:
        name_node = cls.child_by_field_name("name")
        if name_node:
            return _node_text(name_node)
    return "<unknown>"


def _find_enclosing_method(node: Node) -> str:
    m = _find_enclosing(node, "method_declaration")
    if m is None:
        m = _find_enclosing(node, "constructor_declaration")
    if m:
        name_node = m.child_by_field_name("name")
        if name_node:
            return _node_text(name_node)
    return "<init>"


def _has_modifier(node: Node, mod: str) -> bool:
    """Check if a declaration node has a specific modifier."""
    if node is None:
        return False
    mods = node.child_by_field_name("modifiers")
    if mods is None:
        # Also check direct children for modifiers node
        for child in node.children:
            if child.type == "modifiers":
                mods = child
                break
    if mods:
        for child in mods.children:
            if _node_text(child) == mod:
                return True
    return False


def _collect_method_invocations(body: Node) -> list[Node]:
    """Recursively collect all method_invocation nodes in a subtree."""
    results = []
    if body is None:
        return results
    if body.type == "method_invocation":
        results.append(body)
    for child in body.children:
        results.extend(_collect_method_invocations(child))
    return results


def _collect_nodes_of_type(root: Node, node_type: str) -> list[Node]:
    results = []
    if root.type == node_type:
        results.append(root)
    for child in root.children:
        results.extend(_collect_nodes_of_type(child, node_type))
    return results


def _collect_synchronized_statements(root: Node) -> list[Node]:
    return _collect_nodes_of_type(root, "synchronized_statement")


def _qualify_lock(lock_expr: str, class_name: str) -> str:
    """Qualify a lock identity with the class name to avoid cross-class conflation.

    'this' → 'Leader.this'
    'ClassName.class' → kept as-is (already qualified)
    'fieldName' → 'Leader.fieldName'
    'obj.field' → kept as-is (already has context)
    """
    if not lock_expr or lock_expr == "<unknown>":
        return lock_expr
    # Already qualified with a class name or contains a dot
    if "." in lock_expr or lock_expr.endswith(".class"):
        return lock_expr
    # Qualify bare identifiers: this, fieldName, etc.
    return f"{class_name}.{lock_expr}"


class TreeSitterExtractor:
    def __init__(self):
        self.parser = Parser(JAVA_LANGUAGE)

    def extract_file(self, file_path: str) -> ClassLockProfile | list[ClassLockProfile]:
        """Extract lock information from a single Java file. Returns list of profiles (one per class)."""
        path = Path(file_path)
        source = path.read_bytes()
        tree = self.parser.parse(source)
        root = tree.root_node

        # Find all class declarations
        classes = _collect_nodes_of_type(root, "class_declaration")
        if not classes:
            # Might be an enum or interface
            classes = _collect_nodes_of_type(root, "enum_declaration")
            classes += _collect_nodes_of_type(root, "interface_declaration")

        profiles = []
        for cls_node in classes:
            name_node = cls_node.child_by_field_name("name")
            class_name = _node_text(name_node) if name_node else "<unknown>"
            profile = ClassLockProfile(class_name=class_name, file_path=file_path)

            # Extract field types
            self._extract_fields(cls_node, profile)

            # Extract from methods
            methods = _collect_nodes_of_type(cls_node, "method_declaration")
            methods += _collect_nodes_of_type(cls_node, "constructor_declaration")

            for method in methods:
                self._process_method(method, file_path, class_name, profile)

            profiles.append(profile)

        # If no class found, create a single profile from root
        if not profiles:
            profile = ClassLockProfile(class_name=path.stem, file_path=file_path)
            methods = _collect_nodes_of_type(root, "method_declaration")
            methods += _collect_nodes_of_type(root, "constructor_declaration")
            for method in methods:
                self._process_method(method, file_path, path.stem, profile)
            self._extract_fields(root, profile)
            profiles.append(profile)

        return profiles

    def _extract_fields(self, cls_node: Node, profile: ClassLockProfile):
        """Extract field name → type mappings."""
        for field_decl in _collect_nodes_of_type(cls_node, "field_declaration"):
            type_node = field_decl.child_by_field_name("type")
            if type_node is None:
                continue
            type_name = _node_text(type_node)
            declarator = field_decl.child_by_field_name("declarator")
            if declarator is None:
                # Try variable_declarator children
                for child in field_decl.children:
                    if child.type == "variable_declarator":
                        name_node = child.child_by_field_name("name")
                        if name_node:
                            profile.field_types[_node_text(name_node)] = type_name
            else:
                name_node = declarator.child_by_field_name("name")
                if name_node:
                    profile.field_types[_node_text(name_node)] = type_name

    def _process_method(self, method: Node, file_path: str, class_name: str, profile: ClassLockProfile):
        method_name_node = method.child_by_field_name("name")
        method_name = _node_text(method_name_node) if method_name_node else "<unknown>"
        is_static = _has_modifier(method, "static")

        # 1. Synchronized methods
        if _has_modifier(method, "synchronized"):
            lock_id = f"{class_name}.class" if is_static else f"{class_name}.this"
            profile.lock_acquisitions.append(LockAcquisition(
                file_path=file_path,
                class_name=class_name,
                method_name=method_name,
                lock_type="synchronized_method",
                lock_identity=lock_id,
                line_number=_line(method),
                is_static=is_static,
            ))
            # The entire method body is a lock region
            body = method.child_by_field_name("body")
            if body:
                self._extract_calls_under_lock(body, file_path, class_name, method_name, lock_id, profile)
                self._extract_wait_notify_in_region(body, file_path, class_name, method_name, lock_id, profile)
                # Check for nested synchronized inside
                for sync_stmt in _collect_synchronized_statements(body):
                    self._process_synchronized_block(sync_stmt, file_path, class_name, method_name, is_static, profile, outer_lock=lock_id)

        # 2. Synchronized blocks (not nested inside synchronized methods handled above)
        body = method.child_by_field_name("body")
        if body and not _has_modifier(method, "synchronized"):
            for sync_stmt in _collect_synchronized_statements(body):
                self._process_synchronized_block(sync_stmt, file_path, class_name, method_name, is_static, profile, outer_lock=None)
            # Also scan for ReentrantLock usage
            self._extract_reentrant_locks(body, file_path, class_name, method_name, is_static, profile)
            # Wait/notify outside synchronized blocks
            self._extract_wait_notify_in_region(body, file_path, class_name, method_name, None, profile, exclude_synchronized=True)

        elif body and _has_modifier(method, "synchronized"):
            # Already handled synchronized body above, but also scan for ReentrantLock
            self._extract_reentrant_locks(body, file_path, class_name, method_name, is_static, profile)

    def _process_synchronized_block(self, sync_node: Node, file_path: str, class_name: str,
                                     method_name: str, is_static: bool, profile: ClassLockProfile,
                                     outer_lock: str | None):
        """Process a synchronized(expr) { body } statement."""
        # Get the lock expression
        lock_expr = None
        sync_body = None
        for child in sync_node.children:
            if child.type == "parenthesized_expression":
                # The expression inside parens
                inner = child.children[1] if len(child.children) >= 2 else child
                lock_expr = _node_text(inner)
            elif child.type == "block":
                sync_body = child

        if lock_expr is None:
            lock_expr = "<unknown>"
        lock_expr = _qualify_lock(lock_expr, class_name)

        profile.lock_acquisitions.append(LockAcquisition(
            file_path=file_path,
            class_name=class_name,
            method_name=method_name,
            lock_type="synchronized_block",
            lock_identity=lock_expr,
            line_number=_line(sync_node),
            is_static=is_static,
        ))

        # If this is nested inside an outer lock, record lock ordering edge
        if outer_lock and outer_lock != lock_expr:
            profile.lock_acquisitions  # edge goes on the graph, not profile
            # We'll collect edges separately; store via return or side channel
            self._nested_edges.append(LockOrderEdge(
                from_lock=outer_lock,
                to_lock=lock_expr,
                from_class=class_name,
                from_method=method_name,
                mechanism="nested_synchronized",
                call_chain=[],
                file_path=file_path,
                line_number=_line(sync_node),
                source="treesitter",
            ))

        if sync_body:
            self._extract_calls_under_lock(sync_body, file_path, class_name, method_name, lock_expr, profile)
            self._extract_wait_notify_in_region(sync_body, file_path, class_name, method_name, lock_expr, profile)
            # Check for nested synchronized inside this block
            for nested_sync in _collect_synchronized_statements(sync_body):
                if nested_sync is not sync_node:
                    self._process_synchronized_block(nested_sync, file_path, class_name, method_name, is_static, profile, outer_lock=lock_expr)

    def _extract_reentrant_locks(self, body: Node, file_path: str, class_name: str,
                                  method_name: str, is_static: bool, profile: ClassLockProfile):
        """Extract ReentrantLock .lock()/.readLock().lock()/.writeLock().lock() calls."""
        invocations = _collect_method_invocations(body)

        # Track lock regions by line range (lock_call_line, lock_identity)
        lock_regions: list[tuple[int, int, str, str]] = []  # (start_line, end_line, lock_id, lock_type)

        for inv in invocations:
            name_node = inv.child_by_field_name("name")
            if name_node is None:
                continue
            name = _node_text(name_node)

            if name not in ("lock", "tryLock"):
                continue

            obj_node = inv.child_by_field_name("object")
            if obj_node is None:
                continue
            obj_text = _node_text(obj_node)

            # Determine lock type
            if ".readLock()" in obj_text:
                lock_type = "read_lock"
                lock_id = _qualify_lock(obj_text.replace(".readLock()", ""), class_name)
            elif ".writeLock()" in obj_text:
                lock_type = "write_lock"
                lock_id = _qualify_lock(obj_text.replace(".writeLock()", ""), class_name)
            else:
                lock_type = "reentrant_lock"
                lock_id = _qualify_lock(obj_text, class_name)

            profile.lock_acquisitions.append(LockAcquisition(
                file_path=file_path,
                class_name=class_name,
                method_name=method_name,
                lock_type=lock_type,
                lock_identity=lock_id,
                line_number=_line(inv),
                is_static=is_static,
            ))

            # Find matching unlock to define lock region
            unlock_line = self._find_unlock_line(invocations, lock_id, _line(inv))
            lock_regions.append((_line(inv), unlock_line, lock_id, lock_type))

        # For each lock region, extract calls under lock
        for start, end, lock_id, lt in lock_regions:
            for inv in invocations:
                inv_line = _line(inv)
                if start < inv_line < end:
                    name_node = inv.child_by_field_name("name")
                    if name_node is None:
                        continue
                    callee_method = _node_text(name_node)
                    if callee_method in ("lock", "tryLock", "unlock"):
                        continue
                    obj_node = inv.child_by_field_name("object")
                    callee_obj = _node_text(obj_node) if obj_node else "this"
                    profile.calls_under_lock.append(MethodCallUnderLock(
                        file_path=file_path,
                        class_name=class_name,
                        method_name=method_name,
                        holding_lock=lock_id,
                        callee_object=callee_obj,
                        callee_method=callee_method,
                        callee_class_hint=self._guess_class(callee_obj, profile.field_types),
                        line_number=inv_line,
                    ))

            # Check for nested lock acquisitions within this region
            for start2, end2, lock_id2, lt2 in lock_regions:
                if start < start2 < end and lock_id != lock_id2:
                    self._nested_edges.append(LockOrderEdge(
                        from_lock=lock_id,
                        to_lock=lock_id2,
                        from_class=class_name,
                        from_method=method_name,
                        mechanism="lock_under_lock",
                        call_chain=[],
                        file_path=file_path,
                        line_number=start2,
                        source="treesitter",
                    ))

    def _find_unlock_line(self, invocations: list[Node], lock_id: str, after_line: int) -> int:
        """Find the line of the matching unlock() call."""
        for inv in invocations:
            name_node = inv.child_by_field_name("name")
            if name_node is None:
                continue
            if _node_text(name_node) != "unlock":
                continue
            obj_node = inv.child_by_field_name("object")
            if obj_node is None:
                continue
            obj_text = _node_text(obj_node)
            # Normalize: strip .readLock()/.writeLock()
            normalized = obj_text.replace(".readLock()", "").replace(".writeLock()", "")
            if normalized == lock_id and _line(inv) > after_line:
                return _line(inv)
        # Fallback: end of method
        return after_line + 1000

    def _extract_calls_under_lock(self, body: Node, file_path: str, class_name: str,
                                   method_name: str, lock_id: str, profile: ClassLockProfile):
        """Extract all method calls within a lock region."""
        for inv in _collect_method_invocations(body):
            name_node = inv.child_by_field_name("name")
            if name_node is None:
                continue
            callee_method = _node_text(name_node)
            # Skip lock/wait/notify calls themselves
            if callee_method in ("lock", "tryLock", "unlock", "wait", "notify", "notifyAll",
                                  "await", "signal", "signalAll"):
                continue
            obj_node = inv.child_by_field_name("object")
            callee_obj = _node_text(obj_node) if obj_node else "this"
            profile.calls_under_lock.append(MethodCallUnderLock(
                file_path=file_path,
                class_name=class_name,
                method_name=method_name,
                holding_lock=lock_id,
                callee_object=callee_obj,
                callee_method=callee_method,
                callee_class_hint=self._guess_class(callee_obj, profile.field_types),
                line_number=_line(inv),
            ))

    def _extract_wait_notify_in_region(self, body: Node, file_path: str, class_name: str,
                                        method_name: str, lock_id: str | None,
                                        profile: ClassLockProfile, exclude_synchronized: bool = False):
        """Extract wait/notify/await/signal calls."""
        for inv in _collect_method_invocations(body):
            # If excluding synchronized, skip invocations inside synchronized blocks
            if exclude_synchronized:
                if _find_enclosing(inv, "synchronized_statement"):
                    continue

            name_node = inv.child_by_field_name("name")
            if name_node is None:
                continue
            call_name = _node_text(name_node)
            if call_name not in ("wait", "notify", "notifyAll", "await", "signal", "signalAll"):
                continue

            obj_node = inv.child_by_field_name("object")
            obj_expr = _node_text(obj_node) if obj_node else "this"

            # Determine the enclosing lock if not provided
            actual_lock = lock_id
            if actual_lock is None:
                sync = _find_enclosing(inv, "synchronized_statement")
                if sync:
                    for child in sync.children:
                        if child.type == "parenthesized_expression":
                            inner = child.children[1] if len(child.children) >= 2 else child
                            actual_lock = _qualify_lock(_node_text(inner), class_name)
                            break

            profile.wait_notify_sites.append(WaitNotifySite(
                file_path=file_path,
                class_name=class_name,
                method_name=method_name,
                call_type=call_name,
                object_expr=obj_expr,
                line_number=_line(inv),
                inside_lock=actual_lock,
            ))

    def _guess_class(self, obj_expr: str, field_types: dict[str, str]) -> str:
        """Guess the class of a callee object from field declarations."""
        if obj_expr == "this":
            return ""
        # Direct field lookup
        if obj_expr in field_types:
            return field_types[obj_expr]
        # Strip this. prefix
        stripped = obj_expr.replace("this.", "")
        if stripped in field_types:
            return field_types[stripped]
        # Use the expression itself as hint
        return obj_expr

    def extract_file_with_edges(self, file_path: str) -> tuple[list[ClassLockProfile], list[LockOrderEdge]]:
        """Extract profiles and lock order edges from a file."""
        self._nested_edges: list[LockOrderEdge] = []
        profiles = self.extract_file(file_path)
        if isinstance(profiles, ClassLockProfile):
            profiles = [profiles]
        edges = self._nested_edges
        self._nested_edges = []
        return profiles, edges
