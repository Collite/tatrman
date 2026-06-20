# Contracts — Python parser

**Companion to:** [`INDEX.md`](INDEX.md), [`architecture.md`](architecture.md),
[`plan.md`](plan.md). The Kotlin equivalent is
[`docs/grammar-master/contracts.md`](../../grammar-master/contracts.md) — the
shapes below are the Python projection of the same AST, so that doc is the
authority on field meaning; this doc states the Python signatures and the
Python-specific conventions.

"Public" = anything a consumer can `import` from `ttr_parser`. The generated
ANTLR classes under `ttr_parser._generated` and the walker's internals are
**not** public and may change between minor versions. While `< 1.0.0`, minor
bumps may break (documented in `CHANGELOG.md`); from `1.0.0`, semver is strict
and the conformance JSON dump schema (§5) is part of the contract.

All model types are `@dataclass(frozen=True, slots=True)`. Collection fields are
**tuples** (immutable, hashable, parity with frozen dataclasses); the public
constructors accept any iterable and normalise to tuple. Conventions:
`from __future__ import annotations` everywhere; PEP 604 unions; CPython 3.13+
(matches the project's toolchain; bumped from 3.10 in P1.1).

---

## 1. Distribution coordinates

| Index | Name | Import | Depends on |
|---|---|---|---|
| **public PyPI** | `ttr-parser` | `ttr_parser` (parser) + `ttr_parser.semantics` (resolution) | `antlr4-python3-runtime==4.13.2` |

**One distribution, two layers (D8).** `ttr-parser` ships both the parser/walker
(`ttr_parser`) and the semantics layer (`ttr_parser.semantics`) — diverging from
Kotlin's two artifacts so a single `pip install ttr-parser` gives consumers
resolution out of the box. The generated ANTLR parser and the stock `cnc-roles.ttr`
are bundled in the wheel; no JVM needed at install.

Source: `packages/python/ttr-parser/`. Reads `packages/grammar/src/TTR.g4` (and
copies `packages/semantics/src/stock/cnc-roles.ttr`) at build time. Tag →
publish: `python/v<x.y.z>` (see §6).

---

## 2. Public API

### 2.1 Entry point: `ttr_parser`

```python
from pathlib import Path

def parse_string(content: str, file_label: str = "<inline>") -> ParseResult: ...
def parse_file(path: str | Path) -> ParseResult: ...
def parse_directory(root: str | Path, recursive: bool = True) -> list[ParseResult]: ...
```

Behaviour (matches `TtrLoader`):

- Syntax errors **never raise**; they accumulate on `ParseResult.errors`.
- `parse_directory` filters to `*.ttr` and **excludes** `*.ttrg` (graphical
  files — out of scope).
- Skips directories named `.modeler`, `node_modules`, `.git`.
- On any parser error, `ParseResult.definitions` is **empty** — no partial
  trees.

### 2.2 `ParseResult`

```python
@dataclass(frozen=True, slots=True)
class ParseResult:
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
```

`ok` is gated on `errors` only; warnings do not block ingestion.

### 2.3 `ParseError` / `ParseWarning`

```python
@dataclass(frozen=True, slots=True)
class ParseError:
    file: str
    line: int           # 1-indexed (human display)
    column: int         # 1-indexed (ANTLR charPositionInLine + 1)
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
```

Position convention matches `ParseError.toString()` in the Kotlin contract:
`line`/`column` **both 1-indexed for human display**. Distinct from
`SourceLocation` on AST nodes (§2.4).

### 2.4 `SourceLocation` (ANTLR-style, modeler superset)

```python
@dataclass(frozen=True, slots=True)
class SourceLocation:
    file: str
    line: int            # 1-indexed   (ANTLR token.line)
    column: int          # 0-indexed   (ANTLR token.charPositionInLine)
    end_line: int        # 1-indexed
    end_column: int      # 0-indexed; one past the last character
    offset_start: int    # 0-indexed character offset, inclusive
    offset_end: int      # 0-indexed character offset, exclusive

    def __str__(self) -> str:
        return f"{self.file}:{self.line}:{self.column}"

    UNKNOWN: ClassVar[SourceLocation]   # SourceLocation("<unknown>", -1, -1, -1, -1, -1, -1)
```

**Multi-token span invariant:** `end_column = stop_token.column + len(stop_token.text)`
— **not** `start_column + span_length`. Mirrors `CLAUDE.md`'s
`makeSourceLocation` note. LSP-style consumers subtract 1 from `line`/`end_line`.

**Offset note:** `offset_start`/`offset_end` are raw ANTLR **character** offsets
(`token.start` / `token.stop + 1`), matching the TS/Kotlin walkers — **not** UTF-8
byte offsets. On non-ASCII sources they differ from a `str.encode()` byte index, so
slice the source **string** (`source[offset_start:offset_end]`), not its bytes.
Derive them from the ANTLR token stream. (JVM ANTLR counts UTF-16 code units, so
astral-plane characters can differ from CPython codepoints — irrelevant for the BMP
text TTR uses.)

### 2.5 `Definition` hierarchy

```python
class Definition(Protocol):
    kind: ClassVar[str]          # lowercased TTR keyword: "model", "table", "er2db_entity", "drill_map", …
    name: str
    source: SourceLocation
    description: str | None
    tags: tuple[str, ...]
```

Concrete subtypes — one per TTR v2.2 `def <kind>`, names mirroring Kotlin,
fields snake_case. Field meaning per grammar-master `contracts.md` §2.5; the
v2.0.0/v2.2 corrections apply identically:

| Subtype | `kind` | Key fields (snake_case) |
|---|---|---|
| `ModelDef` | `model` | `version` |
| `TableDef` | `table` | `primary_key`, `columns`, `indices`, `constraints`, `search` |
| `ViewDef` | `view` | `columns`, `definition_sql`, `search` |
| `ColumnDef` | `column` | `type`, `optional`, `is_key`, `indexed` — **no** top-level `searchable` (lives in `search`) |
| `IndexDef` | `index` | `index_type`, `columns` |
| `ConstraintDef` | `constraint` | `constraint_type`, `columns` |
| `FkDef` | `fk` | `from_`, `to` (`from_` — `from` is a keyword) |
| `ProcedureDef` | `procedure` | `parameters`, `result_columns` |
| `EntityDef` | `entity` | `label_plural`, `name_attribute`, `code_attribute`, `aliases`, `attributes`, `roles`, `display_label`, `search`, `mapping` |
| `AttributeDef` | `attribute` | `type`, `is_key`, `optional`, `display_label`, `value_labels`, `search`, `mapping` — **no** top-level `searchable` |
| `RelationDef` | `relation` | `from_`, `to`, `cardinality`, `join`, `search`, `mapping` |
| `Er2DbEntityDef` | `er2db_entity` | `entity`, `target`, `where_filter` |
| `Er2DbAttributeDef` | `er2db_attribute` | `attribute`, `target` |
| `Er2DbRelationDef` | `er2db_relation` | `relation`, `fk` |
| `QueryDef` | `query` | `language`, `parameters`, `source_text`, `search` |
| `RoleDef` | `role` | `label`, `search` |
| `Er2CncRoleDef` | `er2cnc_role` | `entity`, `role` |
| `DrillMapDef` | `drill_map` | `from_`, `to`, `args`, `display`, `override_auto` |

The `kind` class-var is the discriminator the conformance dumper (§5) keys off,
so it must equal the lowercased TTR keyword exactly. Consumers dispatch with
`isinstance` or `match`:

```python
match d:
    case TableDef():       ...
    case Er2DbEntityDef():  ...
```

Full TS↔Kotlin↔Python field/type rename map: the Python column of
[`AST-NAMING.md`](../../grammar-master/AST-NAMING.md).

### 2.6 `PropertyValue`

```python
class PropertyValue(Protocol):
    source: SourceLocation

@dataclass(frozen=True, slots=True)
class StringValue(PropertyValue):       raw: str;  source: SourceLocation
@dataclass(frozen=True, slots=True)
class TripleStringValue(PropertyValue):  raw: str;  source: SourceLocation
@dataclass(frozen=True, slots=True)
class NumberValue(PropertyValue):        raw: float; source: SourceLocation
@dataclass(frozen=True, slots=True)
class BoolValue(PropertyValue):          raw: bool; source: SourceLocation
@dataclass(frozen=True, slots=True)
class NullValue(PropertyValue):          source: SourceLocation
@dataclass(frozen=True, slots=True)
class IdValue(PropertyValue):
    ref: Reference
    parts: tuple[str, ...]               # split on "."; matches TS IdValue.parts
    source: SourceLocation
@dataclass(frozen=True, slots=True)
class ListValue(PropertyValue):
    items: tuple[PropertyValue, ...];    source: SourceLocation
@dataclass(frozen=True, slots=True)
class ObjectValue(PropertyValue):
    entries: Mapping[str, PropertyValue]; source: SourceLocation
@dataclass(frozen=True, slots=True)
class FunctionCall(PropertyValue):
    name: str
    args: tuple[PropertyValue, ...];     source: SourceLocation

# Tagged embedded-language block ("""<tag>\n…""") — carrier type
@dataclass(frozen=True, slots=True)
class TaggedBlockValue(PropertyValue):
    tag: str                  # e.g. "sql", "postgres"
    language: LanguageKind    # resolved from tag
    dialect: str | None       # e.g. "postgres" or None
    value: str                # fence-stripped, dedented
    tag_source: SourceLocation
    value_source: SourceLocation
    indent_width: int
    source: SourceLocation
```

`raw` on `NumberValue` is a Python `float` (matches Kotlin `Double` / the JSON
dump's number rule). Every variant carries `source`.

**Helper:**

```python
def extract_reference(value: PropertyValue) -> Reference | None: ...   # ttr_parser.extract_reference
```

Returns `value.ref` when `value` is an `IdValue`; `None` for every other
`PropertyValue` variant. Public mirror of `walker.ts` `extractReference`; the
argument is always a `PropertyValue` (scalar properties such as `description`
are unwrapped to `str` and are **not** passed here).

### 2.7 Other model types

```python
@dataclass(frozen=True, slots=True)
class Reference:
    path: str                 # raw dotted name, e.g. "db.dbo.customers"
    parts: tuple[str, ...]    # path.split(".")
    source: SourceLocation

    @classmethod
    def of(cls, path: str) -> Reference:   # convenience; parts derived, source UNKNOWN
        return cls(path, tuple(path.split(".")), SourceLocation.UNKNOWN)

    def __str__(self) -> str: return self.path

@dataclass(frozen=True, slots=True)
class SchemaDirective:  schema_code: str; namespace: str | None; source: SourceLocation
@dataclass(frozen=True, slots=True)
class ImportStatement:  target: str; wildcard: bool; source: SourceLocation
@dataclass(frozen=True, slots=True)
class PackageDeclaration: name: str; source: SourceLocation

@dataclass(frozen=True, slots=True)
class LocalizedStringValue:      by_language: Mapping[str, str] = MappingProxyType({})
@dataclass(frozen=True, slots=True)
class LocalizedStringListValue:  by_language: Mapping[str, tuple[str, ...]] = MappingProxyType({})

@dataclass(frozen=True, slots=True)
class SearchHintsValue:
    keywords: LocalizedStringListValue = LocalizedStringListValue()
    patterns: tuple[str, ...] = ()
    descriptions: LocalizedStringListValue = LocalizedStringListValue()
    examples: tuple[str, ...] = ()
    aliases: tuple[str, ...] = ()
    searchable: bool = False
    fuzzy: bool = False

@dataclass(frozen=True, slots=True)
class DataType:  name: str; length: int | None = None; precision: int | None = None
```

Mapping types (v2.1) mirror the Kotlin sealed hierarchy
(`MappingProperty` → `MappingPropertyBareId` / `MappingPropertyBlock`;
`TargetValue` → `TargetObjectValue` / `TargetReferenceValue`;
`MappingColumnEntry`, `MappingColumnValue` → `MappingColumnBareId` /
`MappingColumnObject`) as frozen dataclasses with a `source` field — full
signatures in grammar-master `contracts.md` §2.7. `GraphBlock` /
`GraphLayoutEntry` are **out of scope** (no graphs).

### 2.8 Diagnostics

```python
class DiagnosticCode(enum.Enum):
    PARSE_ERROR = "ttr/parse-error"
    PARSE_RECOVERY_INFO = "ttr/parse-recovery-info"
    UNKNOWN_PROPERTY = "ttr/unknown-property"
    # … full set mirrors grammar-master contracts §2.8 …
    def __str__(self) -> str: return self.value

class DiagnosticSeverity(enum.Enum):
    ERROR = "Error"; WARNING = "Warning"; INFORMATION = "Information"; HINT = "Hint"
```

The parser emits the parse-level codes (`PARSE_ERROR`, `PARSE_RECOVERY_INFO`);
the **semantics layer** (§3) emits the resolution/validation codes
(`UNRESOLVED_REFERENCE`, `AMBIGUOUS_REFERENCE`, `UNIMPORTED_REFERENCE`,
`DUPLICATE_DEFINITION`, …). The full enum lives in `ttr_parser.diagnostics` as
the single canonical set shared by both layers. `.value` strings are identical to
the Kotlin `DiagnosticCode.id`, so the conformance diagnostic dump (§5.1)
matches.

### 2.9 Triple-string dedent

```python
def dedent(raw: str) -> str: ...        # ttr_parser.dedent
```

Semantics (identical to `Dedent.kt` and the TS dedent):

1. Drop the leading newline immediately after `"""`.
2. Compute the longest common whitespace prefix across all non-blank lines.
3. Strip that prefix; normalise blank lines to empty.

Pinned to the CPython reference cases (`test_dedent.py`, mirroring `DedentSpec`).
Note this is **not** a bare call to `textwrap.dedent` — steps 1 and 3 are added.

---

## 3. Semantics public API (`ttr_parser.semantics`)

A faithful port of the canonical TS layer (`packages/semantics/src/`), pinned to
it by the §5.1 conformance dump. The authority on behaviour is `resolver.ts`;
this states the Python signatures. Field meaning per grammar-master
`contracts.md` §4.

### 3.0 Convenience entry point

```python
from ttr_parser.semantics import Project

def load_project(root: str | Path, *, with_stock: bool = True) -> Project: ...

class Project:
    symbols: SymbolTable
    results: tuple[ParseResult, ...]
    def resolve(self, reference: str, context: ResolutionContext) -> ResolutionResult: ...
    def validate(self) -> tuple[ValidationDiagnostic, ...]: ...
    def diagnostics(self) -> tuple[ValidationDiagnostic, ...]: ...   # parse + resolution + validation
```

`load_project` runs `parse_directory`, upserts every document plus the stock CNC
vocab (under `stock://`) into one `SymbolTable`, and is the common consumer flow.

### 3.1 `Qname`

```python
@dataclass(frozen=True, slots=True)
class Qname:
    value: str
    @property
    def segments(self) -> tuple[str, ...]: return tuple(self.value.split("."))
    @property
    def last(self) -> str: return self.segments[-1]
    @property
    def parent(self) -> Qname | None: ...
    def __str__(self) -> str: return self.value
```

### 3.2 `SymbolTable` / `SymbolEntry`

Project-level table (TS `ProjectSymbolTable`):

```python
class SymbolTable:
    def upsert_document(self, uri: str, result: ParseResult, *, package_name: str = "") -> None: ...
    def get(self, qname: Qname | str) -> SymbolEntry | None: ...
    def get_all(self) -> tuple[SymbolEntry, ...]: ...
    def get_by_package(self, pkg: Qname | str) -> tuple[SymbolEntry, ...]: ...   # same-package step
    def get_by_suffix(self, last: str) -> tuple[SymbolEntry, ...]: ...           # wildcard matching
    def duplicates(self) -> tuple[tuple[SymbolEntry, ...], ...]: ...

@dataclass(frozen=True, slots=True)
class SymbolEntry:
    qname: Qname
    kind: str               # the def kind ("table", "entity", …)
    name: str
    package_name: str
    schema_code: str
    definition: Definition
    source_file: str
    mapping_source: SourceLocation | None = None
```

### 3.3 `Resolver` — six-step chain

```python
@dataclass(frozen=True, slots=True)
class ResolutionContext:
    schema_code: str
    namespace: str
    imports: tuple[ImportStatement, ...] = ()
    package_name: str = ""
    enclosing_qname: str | None = None     # e.g. "er.entity.artikl" for bare-id child lookup

ResolutionStep = Literal[
    "lexical", "same-package", "named-import",
    "wildcard-import", "auto-import", "fully-qualified",
]

@dataclass(frozen=True, slots=True)
class Resolved:
    symbol: SymbolEntry
    via_step: ResolutionStep
@dataclass(frozen=True, slots=True)
class Unresolved:
    reason: Literal["not-found", "ambiguous"]
    tried: tuple[ResolutionAttempt, ...]
    candidates: tuple[SymbolEntry, ...] = ()

ResolutionResult = Resolved | Unresolved

class Resolver:
    def __init__(self, symbols: SymbolTable) -> None: ...
    def resolve_reference(self, ref: str, context: ResolutionContext) -> ResolutionResult: ...
    def resolve_bare_id(self, name: str, scope: LexicalScope) -> ResolutionResult: ...
```

Algorithm (identical order to `resolver.ts`): **lexical scope → same-package
siblings → named imports (full-suffix match) → wildcard imports (non-recursive,
exactly one extra segment) → `cnc.*` auto-imports → fully-qualified name.** Stock
roles resolve to the **doubled** `cnc.cnc.role.<name>` qname form.

### 3.4 `PackageInference`

```python
def infer_package(file_path: str | Path, project_root: str | Path) -> Qname: ...
```

`<root>/foo/bar/baz.ttr` → `foo.bar`; root file → empty package. Advisory
(qnames are declaration-driven, matching modeler).

### 3.5 `PackageGraph`

```python
class PackageGraph:
    def add_edge(self, frm: Qname, to: Qname) -> None: ...
    def detect_cycles(self) -> tuple[tuple[Qname, ...], ...]: ...
```

### 3.6 `Validator`

```python
class Validator:
    def validate(self, results: Sequence[ParseResult], symbols: SymbolTable) -> tuple[ValidationDiagnostic, ...]: ...

@dataclass(frozen=True, slots=True)
class ValidationDiagnostic:
    code: DiagnosticCode
    severity: DiagnosticSeverity
    source: SourceLocation
    message: str
```

**Portable subset only** (per §5.1 rule 3): `validate_document` +
`validate_references` + `validate_project` + `validate_imports` — cardinality
strings, target shapes, type aliases, search-block sub-properties, drill_map
args. The TS-only validators (file-ordering, `.ttrg` graph, package-declaration,
duplicate-search-property) are excluded.

### 3.7 `StockLoader`

```python
class StockLoader:
    @staticmethod
    def load() -> tuple[Definition, ...]: ...     # parses the bundled cnc-roles.ttr
    @staticmethod
    def stock_qnames() -> frozenset[Qname]: ...   # doubled cnc.cnc.role.<name> form
```

The bundled `cnc-roles.ttr` is package data, copied at build time from the
canonical `packages/semantics/src/stock/cnc-roles.ttr` (no committed duplicate).
Loaded via `importlib.resources`. Stock is resolved by upserting it into the
`SymbolTable` under a `stock://` URI (not a separate constructor arg), matching
the Kotlin shape.

## 4. `ttr-writer` (Python) — deferred

A Python `TtrRenderer` (model → text) is **not** part of this feature (OQ2
resolved — read-only). If a consumer later needs round-tripping, it mirrors
grammar-master `contracts.md` §3 (deterministic property order, triple-string
form for multi-line values, round-trip property). Tracked in
[`plan.md`](plan.md) "Deferred".

---

## 5. Conformance dump schema

The Python parser emits the **same** per-fixture JSON as TS and Kotlin, with the
**same** normalisation, defined once in grammar-master `contracts.md` §5. Summary
of the rules the Python dumper (`tests/conformance/dump.py`) must apply:

1. **No `SourceLocation`** anywhere — structure, not positions.
2. **Object keys sorted** alphabetically.
3. **Kind discriminator** = lowercased TTR keyword (`Definition.kind`) — so the
   Python class names never appear in the diff.
4. **Property keys = the TTR surface name** (`primaryKey`, `valueLabels`), not
   the snake_case field — mapped via the Python column of `AST-NAMING.md`.
5. `by_language` maps serialise as plain objects.
6. Numbers/bools/null native.

```json
{
  "schemaDirective": { "code": "db", "namespace": "dbo" },
  "package": "cnc.role",
  "imports": [ { "target": "er.entity", "wildcard": true } ],
  "definitions": [
    { "kind": "table", "name": "QSUBJEKT", "description": "...", "tags": ["audit"],
      "properties": { "primaryKey": ["IDSUBJEKT"],
        "columns": [ { "kind": "column", "name": "IDSUBJEKT",
          "properties": { "type": { "name": "int" }, "isKey": true } } ] } }
  ]
}
```

- **Reference:** the committed TS golden `tests/conformance/out-ts/`. Python
  writes `tests/conformance/out-py/` and the diff job asserts byte-equality
  per fixture after normalisation. Any difference fails the build.
- The §5 schema is part of the public contract once `1.0.0` (a schema change is
  a major bump).

### 5.1 Semantics dump

A second, parallel dump exercises `ttr_parser.semantics` (D8). For every fixture,
the Python layer loads the stock CNC vocab, builds the symbol table, resolves
every reference and runs the portable validator subset, then emits a normalised
`{ diagnostics, resolved }` object that must be byte-identical to the TS golden
`tests/conformance/out-ts-sem/`.

```json
{
  "diagnostics": [ "ttr/unresolved-reference" ],
  "resolved": [ "fact => cnc.cnc.role.fact" ]
}
```

Normalisation (identical to grammar-master `contracts.md` §5.1):

1. **`resolved`** — one sorted `"<refPath> => <resolvedQname>"` string per
   reference that resolves. No source positions.
2. **`diagnostics`** — sorted diagnostic-**code** strings (`DiagnosticCode.value`).
   Severity and positions are not compared.
3. **Validator subset** — exactly `validate_document` + `validate_references` +
   `validate_project` + `validate_imports` (§3.6). The TS-only validators are
   excluded.
4. **Stock always loaded** — both sides upsert the bundled `cnc-roles` vocab under
   a `stock://` URI before resolving, so `cnc.*` auto-imports resolve.

**Multi-document scenarios.** A fixture may be a **subdirectory** of `fixtures/`
(e.g. `32-same-package/`, `33-named-import/`, `34-wildcard-import/`) bundling
several `.ttr` files loaded into **one** symbol table before resolving; the
combined dump is written to `<dir>.json`. Each includes a same-named **decoy**
def in a different package so the targeted resolution step is load-bearing. The
parser dump (§5) ignores subdirectories. Python writes `out-py-sem/` and the diff
asserts byte-equality vs `out-ts-sem/`.

---

## 6. CI / publish workflow contract

### 6.1 `conformance.yml` (updated)

Add jobs alongside the existing `ts-dump` / `kt-dump` / diff jobs:

- `py-dump` — `actions/setup-python@v5` (3.13+) + `actions/setup-java@v4`
  (for the ANTLR generate step) → install runtime → run generate → run the
  pytest dumpers (AST **and** semantics) → upload `out-py/` + `out-py-sem/`.
- `py-vs-ts` — download `ts-dumps` + `py-dumps`, diff byte-for-byte. Green = no
  TS↔Python AST drift.
- `py-sem-vs-ts` — download `ts-sem-dumps` + `py-sem-dumps`, diff byte-for-byte.
  Green = no TS↔Python resolution drift.

The workflow runs on every PR (no paths filter — same rationale as today).

### 6.2 `publish-python.yml` (new)

```yaml
on:
  push:
    tags: ['python/v*']
permissions: { contents: read }       # + index credentials via secrets
jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: '3.12' }
      - uses: actions/setup-java@v4           # ANTLR generate step
        with: { distribution: 'temurin', java-version: '21' }
      - run: pipx run build --wheel --sdist packages/python/ttr-parser
      - name: Publish to PyPI
        run: pipx run twine upload dist/*
        env: { TWINE_USERNAME: __token__, TWINE_PASSWORD: ${{ secrets.PYPI_TOKEN }} }
```

Publishes to **public PyPI** (D3). Version is derived from the tag
(`python/v0.1.0` → `0.1.0`). The build step runs the ANTLR generate (Java in CI)
and bundles the generated parser + stock vocab into the wheel. No SNAPSHOTs.

---

## 7. Backwards compatibility

- **Baseline:** `ttr-parser` (Python) `0.1.0`, published alongside the existing
  Kotlin `0.x`. While `< 1.0.0`, minor bumps may break; documented in
  `CHANGELOG.md`.
- **Post-1.0:** strict semver. Patch = bugfix; minor = additive (new defs,
  properties, diagnostic codes); major = removing/renaming a public type or
  changing the §5 dump schema.
- **Version-bump triggers** mirror Kotlin: grammar version bump → minor;
  walker/AST shape change → minor; bugfix → patch; AST API break → major.
