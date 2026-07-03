"""Portable validator subset (← `Validator.kt` / `validator.ts`, contracts §3.6).

`Validator().validate(results, symbols)` runs the portable validators over every
parsed document and returns the aggregated diagnostics:

  - validate_document   — per-kind required-property / attribute / pk / fuzzy
  - validate_references  — resolve every cross-reference; unresolved/ambiguous
  - validate_project     — duplicate definitions (and inline duplicate bindings)
  - validate_imports     — wildcard-no-match / duplicate / unused imports

**Scope (verified against the canon):** this mirrors `Validator.kt` exactly — the
codes it emits and nothing more. The TS-only validators (file-ordering, `.ttrg`
graph, package-declaration, duplicate-search-property) are excluded, and there is
no cardinality / target-shape / type-alias / drill_map validator in the portable
subset. Unresolved references are a **Warning** (the §3.6 API exposes no
lint-strict toggle, matching the Kotlin default).
"""

from __future__ import annotations

from dataclasses import dataclass

from ..diagnostics import DiagnosticCode, DiagnosticSeverity
from ..model import (
    AttributeDef,
    ColumnDef,
    Definition,
    EntityDef,
    ImportStatement,
    ParseResult,
    QueryDef,
    RelationDef,
    RoleDef,
    SearchHintsValue,
    SourceLocation,
    TableDef,
    ViewDef,
)
from .default_schema import default_schema_for_kind
from .references import (
    collect_all_references,
    enclosing_qname_of,
    package_of_import,
)
from .resolver import ResolutionContext, Resolved, Resolver, Unresolved
from .symbol_table import SymbolTable

_ER2DB_KINDS = frozenset({"er2db_entity", "er2db_attribute", "er2db_relation"})


@dataclass(frozen=True, slots=True)
class ValidationDiagnostic:
    code: DiagnosticCode
    severity: DiagnosticSeverity
    source: SourceLocation
    message: str


@dataclass(frozen=True, slots=True)
class _Doc:
    definitions: tuple[Definition, ...]
    schema_code: str
    namespace: str
    package_name: str
    imports: tuple[ImportStatement, ...]


def _error(code: DiagnosticCode, message: str, source: SourceLocation) -> ValidationDiagnostic:
    return ValidationDiagnostic(code, DiagnosticSeverity.ERROR, source, message)


def _warn(code: DiagnosticCode, message: str, source: SourceLocation) -> ValidationDiagnostic:
    return ValidationDiagnostic(code, DiagnosticSeverity.WARNING, source, message)


def _info(code: DiagnosticCode, message: str, source: SourceLocation) -> ValidationDiagnostic:
    return ValidationDiagnostic(code, DiagnosticSeverity.INFORMATION, source, message)


class Validator:
    def validate(
        self, results: tuple[ParseResult, ...] | list[ParseResult], symbols: SymbolTable
    ) -> tuple[ValidationDiagnostic, ...]:
        resolver = Resolver(symbols)
        docs = [self._doc(r) for r in results]
        out: list[ValidationDiagnostic] = []
        for doc in docs:
            out.extend(self._validate_document(doc))
            out.extend(self._validate_references(doc, resolver))
            out.extend(self._validate_imports(doc, resolver, symbols))
        out.extend(self._validate_project(symbols))
        return tuple(out)

    @staticmethod
    def _doc(result: ParseResult) -> _Doc:
        directive = result.model_directive
        schema_code = directive.model_code if directive else ""
        namespace = (directive.schema if directive else "") or ""
        return _Doc(
            definitions=result.definitions,
            schema_code=schema_code,
            namespace=namespace,
            package_name=result.package_name or "",
            imports=result.imports,
        )

    # -- per-document -------------------------------------------------------

    def _validate_document(self, doc: _Doc) -> list[ValidationDiagnostic]:
        out: list[ValidationDiagnostic] = []
        for definition in doc.definitions:
            if isinstance(definition, EntityDef):
                if not definition.attributes:
                    out.append(
                        _error(
                            DiagnosticCode.REQUIRED_PROPERTY_MISSING,
                            "Entity must have at least one attribute",
                            definition.source,
                        )
                    )
                self._check_entity_attr(out, definition, definition.name_attribute, "nameAttribute")
                self._check_entity_attr(out, definition, definition.code_attribute, "codeAttribute")
            elif isinstance(definition, TableDef):
                if not definition.columns:
                    out.append(
                        _error(
                            DiagnosticCode.REQUIRED_PROPERTY_MISSING,
                            "Table must have at least one column",
                            definition.source,
                        )
                    )
                for pk in definition.primary_key:
                    if not any(c.name == pk for c in definition.columns):
                        out.append(
                            _error(
                                DiagnosticCode.PRIMARY_KEY_COLUMN_NOT_FOUND,
                                f"Primary key column '{pk}' not found on table '{definition.name}'",
                                definition.source,
                            )
                        )
            elif isinstance(definition, ColumnDef):
                if definition.type is None:
                    out.append(
                        _error(
                            DiagnosticCode.REQUIRED_PROPERTY_MISSING,
                            "Column must have a type",
                            definition.source,
                        )
                    )
            elif isinstance(definition, AttributeDef):
                if definition.type is None:
                    out.append(
                        _error(
                            DiagnosticCode.REQUIRED_PROPERTY_MISSING,
                            "Attribute must have a type",
                            definition.source,
                        )
                    )

            for search, source in self._search_blocks_of(definition):
                if search.fuzzy and not search.searchable:
                    out.append(
                        _warn(
                            DiagnosticCode.FUZZY_WITHOUT_SEARCHABLE,
                            "fuzzy search is enabled but the element is not marked "
                            "searchable; set searchable: true",
                            source,
                        )
                    )
        return out

    @staticmethod
    def _check_entity_attr(
        out: list[ValidationDiagnostic],
        entity: EntityDef,
        ref: object,
        label: str,
    ) -> None:
        if ref is None:
            return
        path = getattr(ref, "path", "")
        last = path.rsplit(".", 1)[-1]
        if not any(a.name == last for a in entity.attributes):
            out.append(
                _error(
                    DiagnosticCode.ENTITY_ATTRIBUTE_NOT_FOUND,
                    f"{label} '{path}' not found on entity '{entity.name}'",
                    entity.source,
                )
            )

    @staticmethod
    def _search_blocks_of(
        definition: Definition,
    ) -> list[tuple[SearchHintsValue, SourceLocation]]:
        out: list[tuple[SearchHintsValue, SourceLocation]] = []
        if isinstance(definition, EntityDef):
            out.append((definition.search, definition.source))
            out.extend((a.search, a.source) for a in definition.attributes)
        elif isinstance(definition, (TableDef, ViewDef)):
            out.append((definition.search, definition.source))
            out.extend((c.search, c.source) for c in definition.columns)
        elif isinstance(definition, (RelationDef, QueryDef, RoleDef)):
            out.append((definition.search, definition.source))
        return out

    # -- references ---------------------------------------------------------

    def _validate_references(
        self, doc: _Doc, resolver: Resolver
    ) -> list[ValidationDiagnostic]:
        out: list[ValidationDiagnostic] = []
        for collected in collect_all_references(doc.definitions):
            schema_code = doc.schema_code or default_schema_for_kind(
                collected.owner_def.kind
            )
            enclosing = enclosing_qname_of(
                collected.owner_def, schema_code, doc.namespace, doc.package_name
            )
            res = resolver.resolve_reference(
                collected.path,
                ResolutionContext(
                    schema_code=schema_code,
                    namespace=doc.namespace,
                    imports=doc.imports,
                    package_name=doc.package_name,
                    enclosing_qname=enclosing,
                ),
            )

            if isinstance(res, Unresolved):
                if res.reason == "ambiguous":
                    source = (
                        res.candidates[0].definition.source
                        if res.candidates
                        else collected.source
                    )
                    out.append(
                        _error(
                            DiagnosticCode.AMBIGUOUS_REFERENCE,
                            f"Ambiguous reference: '{collected.path}' matches "
                            f"{len(res.candidates)} definitions via wildcard imports",
                            source,
                        )
                    )
                else:
                    tried = ", ".join(a.candidate for a in res.tried)
                    out.append(
                        _warn(
                            DiagnosticCode.UNRESOLVED_REFERENCE,
                            f"Unresolved reference: '{collected.path}' (tried {tried})",
                            collected.source,
                        )
                    )
            elif (
                isinstance(res, Resolved)
                and res.via_step == "fully-qualified"
                and doc.package_name
            ):
                resolved_pkg = res.symbol.package_name
                if resolved_pkg and resolved_pkg != doc.package_name:
                    imported = {package_of_import(i) for i in doc.imports}
                    if resolved_pkg not in imported:
                        out.append(
                            _info(
                                DiagnosticCode.UNIMPORTED_REFERENCE,
                                f"Reference to '{res.symbol.qname}' resolves via "
                                "package search; consider adding an import",
                                collected.source,
                            )
                        )
        return out

    # -- project ------------------------------------------------------------

    def _validate_project(self, symbols: SymbolTable) -> list[ValidationDiagnostic]:
        out: list[ValidationDiagnostic] = []
        for group in symbols.duplicates():
            has_inline = any(e.mapping_source is not None for e in group)
            if has_inline and group[0].kind in _ER2DB_KINDS:
                continue
            for entry in group:
                others = ", ".join(
                    f"{e.source_file}:{e.definition.source.line}"
                    for e in group
                    if not (
                        e.source_file == entry.source_file
                        and e.definition.source.line == entry.definition.source.line
                    )
                )
                out.append(
                    _error(
                        DiagnosticCode.DUPLICATE_DEFINITION,
                        f"Duplicate definition of '{entry.qname}' (also at {others})",
                        entry.definition.source,
                    )
                )

        out.extend(self._validate_duplicate_bindings(symbols))
        return out

    @staticmethod
    def _validate_duplicate_bindings(
        symbols: SymbolTable,
    ) -> list[ValidationDiagnostic]:
        out: list[ValidationDiagnostic] = []
        for group in symbols.duplicates():
            if group[0].kind not in _ER2DB_KINDS:
                continue
            if not any(e.mapping_source is not None for e in group):
                continue
            for entry in group:
                others = ", ".join(
                    f"{e.source_file}:{e.definition.source.line}"
                    for e in group
                    if not (
                        e.source_file == entry.source_file
                        and e.definition.source.line == entry.definition.source.line
                    )
                )
                out.append(
                    _error(
                        DiagnosticCode.DUPLICATE_BINDING,
                        f'Duplicate binding for "{entry.qname}" — declared in '
                        f"{len(group)} places: {others}",
                        entry.definition.source,
                    )
                )
        return out

    # -- imports ------------------------------------------------------------

    def _validate_imports(
        self, doc: _Doc, resolver: Resolver, symbols: SymbolTable
    ) -> list[ValidationDiagnostic]:
        out: list[ValidationDiagnostic] = []
        seen: set[str] = set()
        for imp in doc.imports:
            if imp.wildcard and not symbols.get_by_package(imp.target):
                out.append(
                    _warn(
                        DiagnosticCode.WILDCARD_WITH_NO_MATCHES,
                        f"Wildcard import '{imp.target}.*' has no matching definitions",
                        imp.source,
                    )
                )
            if imp.target in seen:
                out.append(
                    _warn(
                        DiagnosticCode.DUPLICATE_IMPORT,
                        f"Duplicate import of '{imp.target}'",
                        imp.source,
                    )
                )
            else:
                seen.add(imp.target)

        used: set[str] = set()
        for collected in collect_all_references(doc.definitions):
            schema_code = doc.schema_code or default_schema_for_kind(
                collected.owner_def.kind
            )
            res = resolver.resolve_reference(
                collected.path,
                ResolutionContext(
                    schema_code=schema_code,
                    namespace=doc.namespace,
                    imports=doc.imports,
                    package_name=doc.package_name,
                ),
            )
            if isinstance(res, Resolved) and res.via_step in (
                "named-import",
                "wildcard-import",
            ):
                used.add(res.symbol.qname.value.rsplit(".", 1)[0])

        for imp in doc.imports:
            if not imp.wildcard:
                pkg = package_of_import(imp)
                if pkg and pkg not in used:
                    out.append(
                        _warn(
                            DiagnosticCode.UNUSED_IMPORT,
                            f"Import '{imp.target}' is not referenced",
                            imp.source,
                        )
                    )
        return out
