# Phase P2 / Stage 2.2 — Model: frozen dataclasses (AST + PropertyValue)

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1 day.

**Pre-flight:**
- Stage 2.1 merged (failing parser suites exist).
- Read [`../contracts.md`](../contracts.md) §2.4–§2.8 (the exact signatures) and
  [`../architecture.md`](../architecture.md) §6 (AST mapping).
- Open the canon: TS `packages/parser/src/ast.ts`, Kotlin
  `packages/kotlin/ttr-parser/src/main/kotlin/.../model/Definition.kt`.

**Conventions (apply throughout):** `from __future__ import annotations`;
`@dataclass(frozen=True, slots=True)`; collection fields are **tuples**; PEP 604
unions; no logic in the model (pure data). Class names mirror Kotlin
(`ModelDef`, `Er2DbEntityDef`, …); fields snake_case (D5).

**Tasks** (check each immediately after completion):

- [ ] **2.2.1 — `model.py` core types.** `SourceLocation` (6 fields + `UNKNOWN`
      classvar + `__str__`), `ParseError`, `ParseWarning`, `DiagnosticCode` /
      `DiagnosticSeverity` enums (move enums to `diagnostics.py` and re-export).
      `DiagnosticCode.value` strings must equal the Kotlin `DiagnosticCode.id`
      exactly (contracts §2.8) — copy the full list from grammar-master
      `contracts.md` §2.8.

- [ ] **2.2.2 — `PropertyValue` hierarchy** (contracts §2.6): a `PropertyValue`
      base (use a common base class, not `Protocol`, so isinstance works for the
      union) and the variants `StringValue`, `TripleStringValue`, `NumberValue`
      (`raw: float`), `BoolValue`, `NullValue`, `IdValue` (with `parts`),
      `ListValue`, `ObjectValue`, `FunctionCall`, `TaggedBlockValue`. Every variant
      carries `source`.

- [ ] **2.2.3 — Support types** (contracts §2.7): `Reference` (with `of()`
      classmethod), `SchemaDirective`, `ImportStatement`, `PackageDeclaration`,
      `LocalizedStringValue`, `LocalizedStringListValue`, `SearchHintsValue`
      (with `searchable`/`fuzzy` — NOT on column/attribute), `DataType`, and the
      mapping types (`MappingProperty` + `MappingPropertyBareId`/
      `MappingPropertyBlock`; `TargetValue` + `TargetObjectValue`/
      `TargetReferenceValue`; `MappingColumnEntry`, `MappingColumnValue` +
      `MappingColumnBareId`/`MappingColumnObject`). **No** `GraphBlock` (out of
      scope).

- [ ] **2.2.4 — `Definition` base + the 16 subtypes** (contracts §2.5). Give each a
      `kind: ClassVar[str]` equal to the lowercased TTR keyword (`"table"`,
      `"er2db_entity"`, `"drill_map"`, …) plus the common `name`/`source`/
      `description`/`tags` and the per-kind fields from the §2.5 table. **v2.0.0
      fix:** `ColumnDef`/`AttributeDef` have **no** top-level `searchable`
      (it lives in `search: SearchHintsValue | None`); `ColumnDef.indexed` stays
      top-level. Use `from_` for the `from` field (Python keyword) on
      `FkDef`/`RelationDef`/`Er2*`/`DrillMapDef`.
      **`isinstance` note (review-065 F4):** the 2.1 parser suites call
      `isinstance(d, Definition)`. A bare `Protocol` raises `TypeError` under
      `isinstance`. So make `Definition` a **concrete common base class** (same
      decision as `PropertyValue` in 2.2.2 — "base class, not `Protocol`"); only if
      you keep it a `Protocol` must you decorate it `@runtime_checkable`. Contracts
      §2.5 writes it as `Protocol` for signature brevity — the runtime form must be
      isinstance-able either way.

- [ ] **2.2.5 — `LanguageKind`.** Define the enum/literal used by
      `TaggedBlockValue.language` (mirror the TS `tag-registry` language set).

- [ ] **2.2.6 — Public re-exports.** From `ttr_parser/__init__.py` export the model
      types, `ParseResult`, `parse_string`/`parse_file`/`parse_directory`
      (the loader names land in stage 2.3 — add the import then), and `dedent`.
      Keep `__all__` explicit.

- [ ] **2.2.7 — `mypy --strict` clean** on `model.py`. No `Any`. Resolve forward
      refs via `from __future__ import annotations`.

- [ ] **2.2.8 — Update `AST-NAMING.md` (Python column).** In
      [`../../../grammar-master/AST-NAMING.md`](../../../grammar-master/AST-NAMING.md)
      add a Python column mapping every TS type → Kotlin type → Python class, and
      every field whose snake_case differs from the TTR **surface** name
      (`primary_key`→`primaryKey`, `value_labels`→`valueLabels`,
      `override_auto`→`override`, `from_`→`from`, …). The conformance dumper
      (stage 3.1) reads this map.

**Verification commands:**
```bash
cd packages/python/ttr-parser && . .venv/bin/activate
mypy src/ttr_parser/model.py src/ttr_parser/diagnostics.py
python -c "from ttr_parser import TableDef, IdValue, SourceLocation; print(TableDef.kind)"  # -> table
```

**Stage DoD:**
- All eight tasks checked.
- All 16 `Definition` kinds, 10 `PropertyValue` variants, and the support/mapping
  types exist with the §2.4–§2.8 signatures; `mypy --strict` clean.
- `AST-NAMING.md` has a complete Python column.
- Parser suites from 2.1 now fail only on the **walker/loader** (model imports
  resolve).
