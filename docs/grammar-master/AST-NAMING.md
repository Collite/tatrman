# AST-NAMING — TS↔Kotlin↔Python type mapping

**Status:** Source-of-truth for the rename map between modeler's TS AST
(`packages/parser/src/ast.ts`), the Kotlin AST published as
`org.tatrman:ttr-parser`, and the Python AST published as
`ttr-parser` (`packages/python/ttr-parser/`). Decision D3 keeps each side
using its own idiomatic naming; this doc is what makes the conformance harness
possible.

**Maintenance rule:** any AST shape change (new def, renamed property, new
sealed variant) must update this table in the same PR.

## Definition types

| TTR kind keyword | TS type (`ast.ts`) | Kotlin type (`model.Definition.kt`) | Python type (`ttr_parser.model`) | Notes |
|---|---|---|---|---|
| `project` | `ProjectDef` | `ProjectDef` | `ProjectDef` | **v4.0:** was `model`/`ModelDef` (the whole-artifact `def project` header). |
| `table` | `TableDef` | `TableDef` | `TableDef` | identical |
| `view` | `ViewDef` | `ViewDef` | `ViewDef` | identical |
| `column` | `ColumnDef` | `ColumnDef` | `ColumnDef` | **v2.0.0:** drop top-level `searchable` on the Kotlin side to match TS. `indexed` STAYS top-level on all sides (it is a column-level grammar property, not part of `SearchHintsValue`). |
| `index` | `IndexDef` | `IndexDef` | `IndexDef` | identical |
| `constraint` | `ConstraintDef` | `ConstraintDef` | `ConstraintDef` | identical |
| `fk` | `FkDef` | `FkDef` | `FkDef` | identical; Python uses `from_` for the `from` field. |
| `procedure` | `ProcedureDef` | `ProcedureDef` | `ProcedureDef` | identical |
| `entity` | `EntityDef` | `EntityDef` | `EntityDef` | identical |
| `attribute` | `AttributeDef` | `AttributeDef` | `AttributeDef` | **v2.0.0:** drop top-level `searchable` on all sides. |
| `relation` | `RelationDef` | `RelationDef` | `RelationDef` | identical; Python uses `from_`. |
| `er2db_entity` | `Er2dbEntityDef` | `Er2DbEntityDef` | `Er2DbEntityDef` | **D3 rename:** TS keeps lowercase-d "db"; Kotlin/Python use camelcase `Db`. |
| `er2db_attribute` | `Er2dbAttributeDef` | `Er2DbAttributeDef` | `Er2DbAttributeDef` | same as above |
| `er2db_relation` | `Er2dbRelationDef` | `Er2DbRelationDef` | `Er2DbRelationDef` | same as above |
| `query` | `QueryDef` | `QueryDef` | `QueryDef` | identical |
| `role` | `RoleDef` | `RoleDef` | `RoleDef` | v2.2 — identical |
| `er2cnc_role` | `Er2cncRoleDef` | `Er2CncRoleDef` | `Er2CncRoleDef` | **D3 rename:** TS lowercase `cnc`; Kotlin/Python `Cnc`. |
| `drill_map` | `DrillMapDef` | `DrillMapDef` | `DrillMapDef` | v2.2 — identical; Python uses `from_`. |

## Value types

| TS (`ast.ts`) | Kotlin (`model.Definition.kt`) | Python (`ttr_parser.model`) | Notes |
|---|---|---|---|
| `PropertyValue` (union) | `PropertyValue` (sealed interface) | `PropertyValue` (concrete base) | **D4:** Kotlin/Python gain `source` on every variant. **Python:** base is a concrete class so `isinstance` works. |
| `StringValue` | `PropertyValue.StringValue` | `StringValue` | TS `value` vs Kotlin `raw` — JSON dump normalises to `value`. Python uses `raw` (Kotlin shape). |
| `TripleStringValue` | `PropertyValue.TripleStringValue` | `TripleStringValue` | NEW on Kotlin/Python — currently merged into `StringValue` in ai-platform; split out per TS shape (P1-3). |
| `NumberValue` | `PropertyValue.NumberValue` | `NumberValue` | TS `value: number` vs Kotlin/Python `raw: float`. |
| `BoolValue` | `PropertyValue.BoolValue` | `BoolValue` | TS `value: boolean` vs Kotlin/Python `raw: bool`. |
| `NullValue` | `PropertyValue.NullValue` | `NullValue` | Was data object in ai-platform; becomes data class with `source`. |
| `IdValue` | `PropertyValue.IdValue` | `IdValue` | TS has `path` + `parts: string[]`. Kotlin/Python have `parts` + nested `ref: Reference` (no top-level `path` — read `id.ref.path`). |
| `ListValue` | `PropertyValue.ListValue` | `ListValue` | identical; Python uses tuple instead of list. |
| `ObjectValue` | `PropertyValue.ObjectValue` | `ObjectValue` | identical; Kotlin/Python use `Mapping[str, PropertyValue]`. |
| `ObjectEntry` | (entries are `Map<String, PropertyValue>` in Kotlin) | (same as Kotlin) | TS uses entry objects with `source`; Kotlin/Python use Mapping for now — revisit if entry-level source is needed. |
| `FunctionCallValue` | `PropertyValue.FunctionCall` | `FunctionCall` | **D3 rename:** TS suffix `Value`; Kotlin/Python drop it. |
| `Reference` | `Reference` (data class) | `Reference` | identical surface: `path`, `parts`, `source`. Python: `parts` is tuple. |
| `LocalizedString` | `LocalizedStringValue` | `LocalizedStringValue` | **D3 rename:** TS no suffix; Kotlin/Python keep `Value` suffix. Python: `by_language: Mapping[str, str]`. |
| `LocalizedStringList` | `LocalizedStringListValue` | `LocalizedStringListValue` | same; Python: `by_language: Mapping[str, tuple[str, ...]]`. |
| `SearchBlock` | `SearchHintsValue` | `SearchHintsValue` | **D3 rename.** Same field set; Python: collections are tuples. |
| `DataType` | `DataType` | `DataType` | identical |
| `SimpleDataType` / `StructuredDataType` | (folded into `DataType`) | (folded into `DataType`) | TS splits; Kotlin/Python use one type with optional `length`/`precision`. |
| `MappingProperty` (union) | `MappingProperty` (sealed) | `MappingProperty` (concrete base) | identical structure. |
| `MappingPropertyBareId` | `MappingPropertyBareId` | `MappingPropertyBareId` | identical |
| `MappingPropertyBlock` | `MappingPropertyBlock` | `MappingPropertyBlock` | identical |
| `MappingColumnEntry` | `MappingColumnEntry` | `MappingColumnEntry` | identical |
| `MappingColumnValue` | `MappingColumnValue` (sealed) | `MappingColumnValue` (concrete base) | TS uses tagged union; Kotlin/Python use sealed/base-class. |
| `TargetValue` | `TargetValue` (sealed) | `TargetValue` (concrete base) | identical |
| `TargetObjectValue` | `TargetObjectValue` | `TargetObjectValue` | identical |
| `TargetReferenceValue` | `TargetReferenceValue` | `TargetReferenceValue` | identical |
| `ModelDirective` | `ModelDirective` | `SchemaDirective` | **v4.0:** was `SchemaDirective` (the `model <code> [schema <id>]` directive). TS fields `modelCode`/`schema`; Kotlin still `schemaCode`/`namespace`, Python still `schema_code`/`namespace` — field-name alignment to TS is deferred to the Kotlin/Python semantics rewrite (qname-redesign Phase 5). The conformance dump output keys are unchanged (`code`/`namespace`), so cross-language parity holds. |
| `ImportDecl` | `ImportStatement` | `ImportStatement` | **D3 rename:** TS `Decl` suffix; Kotlin/Python keep `Statement`. |
| `PackageDecl` | `PackageDeclaration` | `PackageDeclaration` | **D3 rename:** TS abbreviates; Kotlin/Python spell out. |
| `GraphBlock` | `GraphBlock` | (out of scope — not ported) | Python port is read-only + models-only per INDEX.md scope. |
| `GraphLayout` | `GraphLayoutEntry` | (out of scope) | n/a |
| `SourceLocation` | `SourceLocation` | `SourceLocation` | **D4:** all sides use ANTLR superset (`end_line`, `end_column`, `offset_start`, `offset_end`). Python: `UNKNOWN` is a classvar instance. |
| `ParseError` | `ParseError` | `ParseError` | All sides: `line` 1-indexed, `column` 1-indexed (for display — distinct from `SourceLocation`). |
| `ParseResult` | `ParseResult` | `ParseResult` | Python: collections are tuples. `ok` is a property. |
| `TaggedBlockValue` | `TaggedBlockValue` (in `PropertyValue` family) | `TaggedBlockValue` (in `PropertyValue` family) | identical surface; Python: `tag_source`, `value_source`, `indent_width`. |

## Field-name divergences (within otherwise-matching types)

| TS field | Kotlin field | Python field | Notes |
|---|---|---|---|
| `StringValue.value` | `StringValue.raw` | `StringValue.raw` | JSON dump normalises to `value`. |
| `NumberValue.value` | `NumberValue.raw` | `NumberValue.raw` | same |
| `BoolValue.value` | `BoolValue.raw` | `BoolValue.raw` | same |
| `DrillMapDef.override` | `DrillMapDef.overrideAuto` | `DrillMapDef.override_auto` | `override` is a TS-reserved word in some contexts; Kotlin avoids the soft keyword; Python renames to snake_case. JSON dump normalises back to `override`. |
| `FkDef.from` | `FkDef.from` | `FkDef.from_` | Python: `from` is a reserved keyword; trailing underscore. |
| `RelationDef.from` | `RelationDef.from` | `RelationDef.from_` | same |
| `DrillMapDef.from` | `DrillMapDef.from` | `DrillMapDef.from_` | same |
| `EntityDef.attributes[].valueLabels` | `AttributeDef.valueLabels: Map<String, LocalizedStringValue>` | `AttributeDef.value_labels: Mapping[str, LocalizedStringValue]` | identical shape; Python renames to snake_case. |
| `QueryDef.parameters` (TS: `ParameterDef[]`) | `QueryDef.parameters` (Kotlin: `ObjectValue[]`) | `QueryDef.parameters` (Python: `PropertyValue[]`) | TS uses a named dataclass; Kotlin/Python use `ObjectValue` (and consumers iterate entries). |
| `ModelDirective.modelCode` | `ModelDirective.schemaCode` | `SchemaDirective.schema_code` | **v4.0:** TS renamed `schemaCode`→`modelCode` and `namespace`→`schema`; Kotlin/Python field-name alignment deferred to Phase 5. |
| `ViewDef.definitionSql` | `ViewDef.definitionSql` + `definitionSqlBlock` | `ViewDef.definition_sql` + `definition_sql_block` | Kotlin/Python carry both flattened text + structured block; TS only has the union type. |
| `QueryDef.sourceText` | `QueryDef.sourceText` + `sourceTextBlock` | `QueryDef.source_text` + `source_text_block` | same |
| `EntityDef.labelPlural` | `EntityDef.labelPlural` | `EntityDef.label_plural` | snake_case |
| `IndexDef.indexType` | `IndexDef.indexType` | `IndexDef.index_type` | snake_case |
| `ConstraintDef.constraintType` | `ConstraintDef.constraintType` | `ConstraintDef.constraint_type` | snake_case |
| `ProcedureDef.resultColumns` | `ProcedureDef.resultColumns` | `ProcedureDef.result_columns` | snake_case |
| `Er2DbEntityDef.whereFilter` | `Er2DbEntityDef.whereFilter` | `Er2DbEntityDef.where_filter` | snake_case |

## Diagnostic codes

Identical enum values on both sides — see `contracts.md` §2.8. The
`DiagnosticCode` enum is part of the contract; no rename map needed.

## How the conformance harness uses this

1. Both runtimes emit JSON with `kind` = TTR keyword (e.g. `er2db_entity`, NOT
   either `Er2dbEntityDef` or `Er2DbEntityDef`).
2. Field names in the `properties` block use the **TTR surface name**
   (`primaryKey`, `valueLabels`, `searchable`), not the host-language field
   name. The rename map above tells the dumper which Kotlin/TS field to read
   for each TTR property.
3. `PropertyValue` discriminator uses TTR-friendly tags (`string`,
   `tripleString`, `id`, `object`, etc.).

When this table changes, update both `normalize.ts` and `Normalize.kt` (in
`tests/conformance/`) to keep the JSON dump shape stable.
