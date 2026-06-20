"""Project-level symbol table (← `project-symbols.ts` + `symbol-table.ts`).

Builds one `SymbolEntry` per definition (and per child attribute/column), keyed
by the dotted qname. Qname construction mirrors the TS `DocumentSymbolTable`
exactly so the §5.1 conformance dump stays byte-identical:

    [package?] [cnc-if-stock] schema [namespace|default-ns|kind] name [child]

`upsert_document` replaces a document's prior entries; `duplicates()` surfaces
qname collisions across documents (the duplicate-definition validator reads it).
The `is_stock_cnc` gate stores the bundled cnc roles under the doubled
`cnc.cnc.role.<name>` form the resolver's auto-import step looks for.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..model import (
    Definition,
    EntityDef,
    ParseResult,
    ProcedureDef,
    SourceLocation,
    TableDef,
    ViewDef,
)
from .default_schema import default_namespace_for_schema, default_schema_for_kind
from .qname import Qname

# The qname namespace-fallback segment uses the def kind when no namespace (and
# no schema default) applies. TS stores `def.kind` in camelCase; the Python model
# uses snake_case. Map the compound kinds back to the TS camelCase form so the
# resulting qname (e.g. `map.er2dbEntity.x`) is byte-identical to the golden.
_KIND_QNAME_SEGMENT: dict[str, str] = {
    "er2db_entity": "er2dbEntity",
    "er2db_attribute": "er2dbAttribute",
    "er2db_relation": "er2dbRelation",
    "er2cnc_role": "er2cncRole",
    "drill_map": "drillMap",
}


def _kind_segment(kind: str) -> str:
    return _KIND_QNAME_SEGMENT.get(kind, kind)


@dataclass(frozen=True, slots=True)
class SymbolEntry:
    qname: Qname
    kind: str
    name: str
    package_name: str
    schema_code: str
    definition: Definition
    source_file: str
    mapping_source: SourceLocation | None = None


class SymbolTable:
    """Mirrors TS `ProjectSymbolTable`: a per-qname multimap plus per-document
    index for replacement."""

    def __init__(self) -> None:
        self._by_document: dict[str, list[SymbolEntry]] = {}
        self._by_qname: dict[str, list[SymbolEntry]] = {}

    # -- mutation -----------------------------------------------------------

    def upsert_document(
        self, uri: str, result: ParseResult, *, package_name: str = ""
    ) -> None:
        if uri in self._by_document:
            self.remove_document(uri)

        directive = result.schema_directive
        schema_code = directive.schema_code if directive else ""
        namespace = (directive.namespace if directive else "") or ""
        package = package_name or (result.package_name or "")
        is_stock_cnc = (
            schema_code == "cnc" and not package and uri.startswith("stock://")
        )

        entries: list[SymbolEntry] = []
        for definition in result.definitions:
            self._add_definition(
                entries, definition, uri, schema_code, namespace, package, is_stock_cnc
            )

        self._by_document[uri] = entries
        for entry in entries:
            self._by_qname.setdefault(entry.qname.value, []).append(entry)

    def remove_document(self, uri: str) -> None:
        entries = self._by_document.pop(uri, None)
        if not entries:
            return
        for entry in entries:
            existing = self._by_qname.get(entry.qname.value)
            if existing is None:
                continue
            remaining = [e for e in existing if e.source_file != uri]
            if remaining:
                self._by_qname[entry.qname.value] = remaining
            else:
                del self._by_qname[entry.qname.value]

    # -- qname construction (mirror of DocumentSymbolTable) -----------------

    @staticmethod
    def _make_qname(
        parts: list[str],
        ns_or_kind: str,
        schema: str,
        package: str,
        is_stock_cnc: bool,
    ) -> str:
        segments: list[str] = []
        if package:
            segments.append(package)
        if is_stock_cnc:
            segments.append("cnc")
        segments.append(schema)
        if ns_or_kind:
            segments.append(ns_or_kind)
        segments.extend(parts)
        return ".".join(segments)

    def _add_definition(
        self,
        out: list[SymbolEntry],
        definition: Definition,
        uri: str,
        schema_code: str,
        namespace: str,
        package: str,
        is_stock_cnc: bool,
    ) -> None:
        schema = schema_code or default_schema_for_kind(definition.kind)
        ns_or_kind = (
            namespace
            or default_namespace_for_schema(schema)
            or _kind_segment(definition.kind)
        )
        qname = self._make_qname(
            [definition.name], ns_or_kind, schema, package, is_stock_cnc
        )
        out.append(
            SymbolEntry(
                qname=Qname(qname),
                kind=definition.kind,
                name=definition.name,
                package_name=package,
                schema_code=schema,
                definition=definition,
                source_file=uri,
            )
        )

        children: tuple[Definition, ...] = ()
        if isinstance(definition, EntityDef):
            children = definition.attributes
        elif isinstance(definition, (TableDef, ViewDef)):
            children = definition.columns
        elif isinstance(definition, ProcedureDef):
            children = definition.result_columns

        if not children:
            return

        # Children inherit the parent's effective schema; namespace → schema
        # default → parent kind, exactly as the TS `makeQnameChild`.
        child_ns = (
            namespace
            or default_namespace_for_schema(schema)
            or _kind_segment(definition.kind)
        )
        for child in children:
            child_qname = self._make_qname(
                [definition.name, child.name], child_ns, schema, package, is_stock_cnc
            )
            out.append(
                SymbolEntry(
                    qname=Qname(child_qname),
                    kind=child.kind,
                    name=child.name,
                    package_name=package,
                    schema_code=schema,
                    definition=child,
                    source_file=uri,
                )
            )

    # -- queries ------------------------------------------------------------

    def get(self, qname: Qname | str) -> SymbolEntry | None:
        key = qname.value if isinstance(qname, Qname) else qname
        entries = self._by_qname.get(key)
        return entries[0] if entries else None

    def get_all(self) -> tuple[SymbolEntry, ...]:
        out: list[SymbolEntry] = []
        seen: set[str] = set()
        for entries in self._by_qname.values():
            for entry in entries:
                if entry.qname.value not in seen:
                    seen.add(entry.qname.value)
                    out.append(entry)
        return tuple(out)

    def get_by_package(self, pkg: Qname | str) -> tuple[SymbolEntry, ...]:
        name = pkg.value if isinstance(pkg, Qname) else pkg
        return tuple(e for e in self.get_all() if e.package_name == name)

    def get_by_suffix(self, last: str) -> tuple[SymbolEntry, ...]:
        out: list[SymbolEntry] = []
        for entries in self._by_qname.values():
            for entry in entries:
                if entry.qname.value.endswith(f".{last}") or entry.qname.value == last:
                    out.append(entry)
        return tuple(out)

    def duplicates(self) -> tuple[tuple[SymbolEntry, ...], ...]:
        return tuple(
            tuple(entries)
            for entries in self._by_qname.values()
            if len(entries) > 1
        )
