"""Typed model produced by the TTR parser (P2).

Pure data — no logic. Every `def <kind> <name> { ... }` block in a `.ttr`
file becomes one `Definition` subtype. Cross-references are kept as raw
`Reference`s; semantic resolution is the consumer's job (P4).

Mirrors the Kotlin `packages/kotlin/ttr-parser/src/main/kotlin/.../model/Definition.kt`
(PINNED.md: "Python classes mirror the Kotlin names") and the TS
`packages/parser/src/ast.ts`. Class names match Kotlin (`ModelDef`,
`Er2DbEntityDef`, `Er2CncRoleDef`). Field names are **snake_case** (D5).
The `kind` class-var is the lowercased TTR keyword — used by the
conformance dumper (§5) and by `isinstance`/`match` dispatch in consumers.

No `GraphBlock` here: the Python port is read-only + models-only per
INDEX.md scope; graphs (`graph { … }`, `.ttrg`) are out of scope.

Conventions (contracts §0):
- `@dataclass(frozen=True, slots=True)` for every concrete type.
- Collection fields are **tuples** (immutable, hashable, parity with frozen).
- PEP 604 unions (`X | Y`).
- Every PropertyValue variant carries a `source: SourceLocation`.
"""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass, field
from types import MappingProxyType
from typing import ClassVar

from .diagnostics import DiagnosticCode

# ============================================================================
# SourceLocation (D4 superset; ANTLR-style)
# ============================================================================


@dataclass(frozen=True, slots=True)
class SourceLocation:
    """ANTLR-style source span (contracts §2.4).

    - `line` / `end_line` are 1-indexed (match ANTLR `token.line`).
    - `column` / `end_column` are 0-indexed (match ANTLR `charPositionInLine`);
      `end_column` is one past the last character of the last token in the span.
    - `offset_start` / `offset_end` are 0-indexed byte offsets; `offset_end`
      is **exclusive** — `source[offset_start:offset_end]` slices the span.

    The multi-token-span invariant: `end_column = stop_token.column + len(stop_token.text)`
    (NOT `start_column + span_length`). The walker enforces this.
    """

    file: str
    line: int
    column: int
    end_line: int
    end_column: int
    offset_start: int
    offset_end: int

    def __str__(self) -> str:
        return f"{self.file}:{self.line}:{self.column}"

    UNKNOWN: ClassVar[SourceLocation]


SourceLocation.UNKNOWN = SourceLocation("<unknown>", -1, -1, -1, -1, -1, -1)


# ============================================================================
# ParseError / ParseWarning
# ============================================================================


@dataclass(frozen=True, slots=True)
class ParseError:
    """Parser-level error (contracts §2.3).

    Note: `line` / `column` are **1-indexed for human display** (per contracts
    §2.3 + `CLAUDE.md` "LSP-style consumers subtract 1"). This is **distinct**
    from `SourceLocation` on AST nodes, which uses 0-indexed columns.
    """

    file: str
    line: int
    column: int
    message: str
    code: DiagnosticCode = DiagnosticCode.PARSE_ERROR

    def __str__(self) -> str:
        return f"{self.file}:{self.line}:{self.column}: {self.message}"


@dataclass(frozen=True, slots=True)
class ParseWarning:
    file: str
    line: int
    column: int
    message: str
    code: DiagnosticCode


# ============================================================================
# Reference (carries the id token's own span, distinct from the def's source)
# ============================================================================


@dataclass(frozen=True, slots=True)
class Reference:
    """Cross-reference identifier.

    Carries `parts` (the path split on `.`) and `source` — the **id token's**
    span, not the enclosing def's. Mirrors the canonical TS `Reference`
    (`{ path, parts, source }`) and Kotlin `Reference` shape.

    Use `Reference.of(path)` when constructing outside the parser (tests,
    synthesizers) where no token span exists; it derives `parts` and uses
    `SourceLocation.UNKNOWN`. The walker always supplies a real span.
    """

    path: str
    parts: tuple[str, ...]
    source: SourceLocation

    @classmethod
    def of(cls, path: str) -> Reference:
        return cls(path, tuple(path.split(".")), SourceLocation.UNKNOWN)

    def __str__(self) -> str:
        return self.path


# ============================================================================
# Localized strings & search hints
# ============================================================================


@dataclass(frozen=True, slots=True)
class LocalizedStringValue:
    """`{ cs: "...", en: "..." }` carrier."""

    by_language: Mapping[str, str] = field(default_factory=lambda: MappingProxyType({}))


@dataclass(frozen=True, slots=True)
class LocalizedStringListValue:
    """`{ cs: ["...", "..."], en: ["..."] }` carrier."""

    by_language: Mapping[str, tuple[str, ...]] = field(
        default_factory=lambda: MappingProxyType({})
    )


@dataclass(frozen=True, slots=True)
class SearchHintsValue:
    """`search { keywords {...} patterns [...] descriptions {...} examples [...]
    aliases [...] searchable: bool fuzzy: bool }` carrier.

    v2.0.0: `searchable` lives **only** here, not on `ColumnDef`/`AttributeDef`.
    """

    keywords: LocalizedStringListValue = field(default_factory=LocalizedStringListValue)
    patterns: tuple[str, ...] = ()
    descriptions: LocalizedStringListValue = field(default_factory=LocalizedStringListValue)
    examples: tuple[str, ...] = ()
    aliases: tuple[str, ...] = ()
    searchable: bool = False
    fuzzy: bool = False


# ============================================================================
# DataType
# ============================================================================


@dataclass(frozen=True, slots=True)
class DataType:
    """Surface or physical type. `name` is the canonical token (`text`, `int`,
    `varchar`, `decimal`, …). `length`/`precision` are present on physical
    types only (e.g. `decimal(19, 5)`)."""

    name: str
    length: int | None = None
    precision: int | None = None


# ============================================================================
# PropertyValue (sealed-style hierarchy via common base + isinstance dispatch)
# ============================================================================


@dataclass(frozen=True, slots=True)
class PropertyValue:
    """Common base for every property value variant.

    Contracts §2.5 requires `isinstance(value, Definition)` to work; same for
    `PropertyValue` — so this is a concrete common base class (NOT a
    `Protocol`). Subclasses set `kind: ClassVar[str]` to the TTR-friendly
    discriminator (`"string"`, `"tripleString"`, `"id"`, …) used by the
    conformance dumper (§5).
    """

    source: SourceLocation
    kind: ClassVar[str] = "propertyValue"


@dataclass(frozen=True, slots=True)
class StringValue(PropertyValue):
    raw: str = ""
    kind: ClassVar[str] = "string"


@dataclass(frozen=True, slots=True)
class TripleStringValue(PropertyValue):
    raw: str = ""
    kind: ClassVar[str] = "tripleString"


@dataclass(frozen=True, slots=True)
class NumberValue(PropertyValue):
    raw: float = 0.0
    kind: ClassVar[str] = "number"


@dataclass(frozen=True, slots=True)
class BoolValue(PropertyValue):
    raw: bool = False
    kind: ClassVar[str] = "bool"


@dataclass(frozen=True, slots=True)
class NullValue(PropertyValue):
    kind: ClassVar[str] = "null"


@dataclass(frozen=True, slots=True)
class IdValue(PropertyValue):
    """An identifier reference inside a property value (e.g. `from: db.dbo.X`).

    Carries `ref` (a `Reference` with the id token's own span) **and** `parts`
    (the dotted-name split) — matches both TS `IdValue.parts` and Kotlin
    `IdValue.parts` for cross-language parity.
    """

    ref: Reference | None = None
    parts: tuple[str, ...] = ()
    kind: ClassVar[str] = "id"


@dataclass(frozen=True, slots=True)
class ListValue(PropertyValue):
    items: tuple[PropertyValue, ...] = ()
    kind: ClassVar[str] = "list"


@dataclass(frozen=True, slots=True)
class ObjectValue(PropertyValue):
    """Object literal `{ key: value, … }`.

    Per contracts §2.7 (Kotlin shape): entries are a **Mapping** (last-write-wins),
    not an ordered list. This diverges from the TS `entries: ObjectEntry[]`
    (ordered, with per-entry source). Consumers that need entry order read the
    raw parse tree; consumers that need a key→value lookup use this map.
    """

    entries: Mapping[str, PropertyValue] = field(default_factory=lambda: MappingProxyType({}))
    kind: ClassVar[str] = "object"


@dataclass(frozen=True, slots=True)
class FunctionCall(PropertyValue):
    """A function call expression `name(arg, arg, …)`."""

    name: str = ""
    args: tuple[PropertyValue, ...] = ()
    kind: ClassVar[str] = "functionCall"


# ============================================================================
# LanguageKind — `Literal` over the four query-language tags
# ============================================================================

LanguageKind = str  # see contracts §2.6: 'SQL' | 'TRANSFORMATION_DSL' | 'DATAFRAME_DSL' | 'REL_NODE'


# ============================================================================
# TaggedBlockValue — top-level PropertyValue variant for embedded SQL blocks
# ============================================================================


@dataclass(frozen=True, slots=True)
class TaggedBlockValue(PropertyValue):
    """A triple-quoted block with a tag prefix carrying embedded foreign-language source.

    Produced only by `sourceText` / `definitionSql` via the `embeddedBlock`
    grammar rule. The tag is peeled before `value`, so it never reaches the
    executed SQL.
    """

    tag: str = ""
    language: LanguageKind = "SQL"
    dialect: str | None = None
    value: str = ""
    tag_source: SourceLocation = field(default_factory=lambda: SourceLocation.UNKNOWN)
    value_source: SourceLocation = field(default_factory=lambda: SourceLocation.UNKNOWN)
    indent_width: int = 0
    kind: ClassVar[str] = "taggedBlock"


# ============================================================================
# Mapping types (v2.1)
# ============================================================================


@dataclass(frozen=True, slots=True)
class MappingProperty:
    """Common base for inline `mapping:` property variants.

    Subclasses set `kind` to `"bareId"` or `"block"`.
    """

    source: SourceLocation = field(default_factory=lambda: SourceLocation.UNKNOWN)
    kind: ClassVar[str] = "mapping"


@dataclass(frozen=True, slots=True)
class MappingPropertyBareId(MappingProperty):
    """`mapping: db.dbo.fk_a_b` — relation FK shorthand or attribute-level mapping."""

    id: Reference | None = None
    kind: ClassVar[str] = "bareId"


@dataclass(frozen=True, slots=True)
class MappingPropertyBlock(MappingProperty):
    """`mapping: { target: …, columns: { … }, fk: … }`."""

    target: TargetValue | None = None
    columns: tuple[MappingColumnEntry, ...] = ()
    fk: Reference | None = None
    kind: ClassVar[str] = "block"


@dataclass(frozen=True, slots=True)
class TargetValue:
    """Common base for `target:` value variants.

    Both inline `mapping: { target: … }` blocks and explicit `def er2db_*`
    `target:` slots accept either an object (e.g. `{ table: db.dbo.T }`) or
    a bare reference (e.g. `db.dbo.T`).
    """

    source: SourceLocation = field(default_factory=lambda: SourceLocation.UNKNOWN)
    kind: ClassVar[str] = "target"


@dataclass(frozen=True, slots=True)
class TargetObjectValue(TargetValue):
    obj: ObjectValue | None = None
    kind: ClassVar[str] = "object"


@dataclass(frozen=True, slots=True)
class TargetReferenceValue(TargetValue):
    ref: Reference | None = None
    kind: ClassVar[str] = "reference"


@dataclass(frozen=True, slots=True)
class MappingColumnEntry:
    """One entry in a `mapping: { columns: { id_artiklu: IDZBOZI, … } }` map."""

    name: str = ""
    value: MappingColumnValue | None = None
    source: SourceLocation = field(default_factory=lambda: SourceLocation.UNKNOWN)


@dataclass(frozen=True, slots=True)
class MappingColumnValue:
    """Common base for inline `columns:` value variants."""

    source: SourceLocation = field(default_factory=lambda: SourceLocation.UNKNOWN)
    kind: ClassVar[str] = "mappingColumn"


@dataclass(frozen=True, slots=True)
class MappingColumnBareId(MappingColumnValue):
    """`id_artiklu: IDZBOZI` (bare-id form)."""

    id: Reference | None = None
    kind: ClassVar[str] = "bareId"


@dataclass(frozen=True, slots=True)
class MappingColumnObject(MappingColumnValue):
    """`kód_artiklu: { target: KOD_ZBOZI }` (object form)."""

    obj: ObjectValue | None = None
    kind: ClassVar[str] = "object"


# ============================================================================
# File-level constructs
# ============================================================================


@dataclass(frozen=True, slots=True)
class SchemaDirective:
    """File-level `schema <code> [namespace <id>]`."""

    schema_code: str
    namespace: str | None
    source: SourceLocation


@dataclass(frozen=True, slots=True)
class ImportStatement:
    """File-level `import <qualifiedName> [.*]`."""

    target: str
    wildcard: bool
    source: SourceLocation


@dataclass(frozen=True, slots=True)
class PackageDeclaration:
    """File-level `package <qualifiedName>`."""

    name: str
    source: SourceLocation


# ============================================================================
# Definition base + the 18 kinds (D5; mirrors Kotlin)
# ============================================================================


@dataclass(frozen=True, slots=True)
class Definition:
    """Common base for every `def <kind>` AST node.

    The `kind` class-var is the **lowercased TTR keyword** (`"table"`,
    `"er2db_entity"`, `"drill_map"`, …) — used as the conformance-dump
    discriminator (§5) and by consumer `isinstance`/`match` dispatch.
    """

    name: str
    source: SourceLocation
    description: str | None
    tags: tuple[str, ...]
    kind: ClassVar[str] = "definition"


@dataclass(frozen=True, slots=True)
class ModelDef(Definition):
    kind: ClassVar[str] = "model"
    version: str | None = None


@dataclass(frozen=True, slots=True)
class TableDef(Definition):
    primary_key: tuple[str, ...] = ()
    columns: tuple[ColumnDef, ...] = ()
    indices: tuple[IndexDef, ...] = ()
    constraints: tuple[ConstraintDef, ...] = ()
    search: SearchHintsValue = field(default_factory=SearchHintsValue)
    kind: ClassVar[str] = "table"


@dataclass(frozen=True, slots=True)
class ViewDef(Definition):
    columns: tuple[ColumnDef, ...] = ()
    # The `definitionSql` property's `PropertyValue` carrier — a `StringValue`,
    # `TripleStringValue`, or `TaggedBlockValue` (embedded SQL block).
    definition_sql: PropertyValue | None = None
    search: SearchHintsValue = field(default_factory=SearchHintsValue)
    kind: ClassVar[str] = "view"


@dataclass(frozen=True, slots=True)
class ColumnDef(Definition):
    """v2.0.0: NO top-level `searchable`; it lives in `search` (TS parity).

    `indexed` stays top-level — it is a grammar-level column property, not a
    search hint.
    """

    type: DataType | None = None
    optional: bool = False
    is_key: bool = False
    indexed: bool = False
    search: SearchHintsValue = field(default_factory=SearchHintsValue)
    kind: ClassVar[str] = "column"


@dataclass(frozen=True, slots=True)
class IndexDef(Definition):
    index_type: str | None = None
    columns: tuple[str, ...] = ()
    kind: ClassVar[str] = "index"


@dataclass(frozen=True, slots=True)
class ConstraintDef(Definition):
    constraint_type: str | None = None
    columns: tuple[str, ...] = ()
    kind: ClassVar[str] = "constraint"


@dataclass(frozen=True, slots=True)
class FkDef(Definition):
    """`from` / `to` are typed as `PropertyValue` to allow either an `IdValue`
    (the common case) or an `ObjectValue` (rare)."""

    from_: PropertyValue | None = field(default=None, metadata={"surface": "from"})
    to: PropertyValue | None = None
    kind: ClassVar[str] = "fk"


@dataclass(frozen=True, slots=True)
class ProcedureDef(Definition):
    """Each `parameters` entry is an `ObjectValue` with keys `name`/`type`/`label`/`direction`."""

    parameters: tuple[PropertyValue, ...] = ()
    result_columns: tuple[ColumnDef, ...] = ()
    kind: ClassVar[str] = "procedure"


@dataclass(frozen=True, slots=True)
class EntityDef(Definition):
    """`roles: [fact, dimension]` shorthand — refs resolved by the consumer."""

    label_plural: str | None = None
    name_attribute: Reference | None = None
    code_attribute: Reference | None = None
    aliases: tuple[str, ...] = ()
    attributes: tuple[AttributeDef, ...] = ()
    roles: tuple[Reference, ...] = ()
    display_label: LocalizedStringValue | None = None
    search: SearchHintsValue = field(default_factory=SearchHintsValue)
    mapping: MappingProperty | None = None
    kind: ClassVar[str] = "entity"


@dataclass(frozen=True, slots=True)
class AttributeDef(Definition):
    """v2.0.0: NO top-level `searchable`; it lives in `search`."""

    type: DataType | None = None
    is_key: bool = False
    optional: bool = False
    display_label: LocalizedStringValue | None = None
    value_labels: Mapping[str, LocalizedStringValue] = field(
        default_factory=lambda: MappingProxyType({})
    )
    search: SearchHintsValue = field(default_factory=SearchHintsValue)
    mapping: MappingProperty | None = None
    kind: ClassVar[str] = "attribute"


@dataclass(frozen=True, slots=True)
class RelationDef(Definition):
    from_: PropertyValue | None = field(default=None, metadata={"surface": "from"})
    to: PropertyValue | None = None
    cardinality: ObjectValue | None = None
    join: tuple[PropertyValue, ...] = ()
    search: SearchHintsValue = field(default_factory=SearchHintsValue)
    mapping: MappingProperty | None = None
    kind: ClassVar[str] = "relation"


@dataclass(frozen=True, slots=True)
class Er2DbEntityDef(Definition):
    """TTR keyword `er2db_entity` — kind discriminator `"er2db_entity"`.

    The TS type uses lowercase-`db` (`Er2dbEntityDef`); Kotlin/TS shape uses
    `Db` (`Er2DbEntityDef`). Python follows Kotlin (D5).
    """

    entity: Reference | None = None
    target: TargetValue | None = None
    where_filter: ObjectValue | None = None
    kind: ClassVar[str] = "er2db_entity"


@dataclass(frozen=True, slots=True)
class Er2DbAttributeDef(Definition):
    attribute: Reference | None = None
    target: TargetValue | None = None
    kind: ClassVar[str] = "er2db_attribute"


@dataclass(frozen=True, slots=True)
class Er2DbRelationDef(Definition):
    relation: Reference | None = None
    fk: Reference | None = None
    kind: ClassVar[str] = "er2db_relation"


@dataclass(frozen=True, slots=True)
class QueryDef(Definition):
    """`source_text` is the property's `PropertyValue` carrier — a `StringValue`,
    `TripleStringValue`, or `TaggedBlockValue` (embedded SQL/DSL block).

    `parameters` matches TS `ParameterDef[]` — named dicts, not `ObjectValue`s
    (Kotlin uses `ObjectValue`; the Python port goes with TS for the public
    surface to keep `from ttr_parser import QueryDef` ergonomics).
    """

    language: LanguageKind | None = None
    parameters: tuple[PropertyValue, ...] = ()
    source_text: PropertyValue | None = None
    search: SearchHintsValue = field(default_factory=SearchHintsValue)
    kind: ClassVar[str] = "query"


@dataclass(frozen=True, slots=True)
class RoleDef(Definition):
    label: LocalizedStringValue | None = None
    search: SearchHintsValue = field(default_factory=SearchHintsValue)
    kind: ClassVar[str] = "role"


@dataclass(frozen=True, slots=True)
class Er2CncRoleDef(Definition):
    """TTR keyword `er2cnc_role` — kind discriminator `"er2cnc_role"`.

    TS uses lowercase `cnc` (`Er2cncRoleDef`); Kotlin/TS shape uses `Cnc`
    (`Er2CncRoleDef`). Python follows Kotlin (D5).
    """

    entity: Reference | None = None
    role: Reference | None = None
    kind: ClassVar[str] = "er2cnc_role"


@dataclass(frozen=True, slots=True)
class DrillMapDef(Definition):
    """v2.2 — `def drill_map <id> { from, to, args, display?, override? }`.

    `args` is `Mapping[str, str]` (Kotlin shape); the walker flattens
    `StringValue`/`TripleStringValue` to plain strings, and column-vs-literal
    validation is deferred to the semantics layer.
    """

    from_: Reference | None = field(default=None, metadata={"surface": "from"})
    to: Reference | None = None
    args: Mapping[str, str] = field(default_factory=lambda: MappingProxyType({}))
    display: LocalizedStringValue | None = None
    override_auto: bool = False
    kind: ClassVar[str] = "drill_map"


# ============================================================================
# Top-level ParseResult
# ============================================================================


@dataclass(frozen=True, slots=True)
class ParseResult:
    """Outcome of `parse_string` / `parse_file` / `parse_directory`."""

    definitions: tuple[Definition, ...]
    schema_directive: SchemaDirective | None
    errors: tuple[ParseError, ...]
    source_file: str
    warnings: tuple[ParseWarning, ...] = ()
    package_name: str | None = None
    imports: tuple[ImportStatement, ...] = ()

    @property
    def ok(self) -> bool:
        return not self.errors