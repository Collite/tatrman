# AST-NAMING — TS↔Kotlin type mapping

**Status:** Source-of-truth for the rename map between modeler's TS AST
(`packages/parser/src/ast.ts`) and the Kotlin AST published as
`org.tatrman:ttr-parser`. Decision D3 keeps both sides using their own
idiomatic naming; this doc is what makes the conformance harness possible.

**Maintenance rule:** any AST shape change (new def, renamed property, new
sealed variant) must update this table in the same PR.

## Definition types

| TTR kind keyword | TS type (`ast.ts`) | Kotlin type (`model.Definition.kt`) | Notes |
|---|---|---|---|
| `model` | `ModelDef` | `ModelDef` | identical |
| `table` | `TableDef` | `TableDef` | identical |
| `view` | `ViewDef` | `ViewDef` | identical |
| `column` | `ColumnDef` | `ColumnDef` | **v2.0.0:** drop top-level `searchable` on the Kotlin side to match TS. `indexed` STAYS top-level on both sides (it is a column-level grammar property, not part of `SearchHintsValue`). |
| `index` | `IndexDef` | `IndexDef` | identical |
| `constraint` | `ConstraintDef` | `ConstraintDef` | identical |
| `fk` | `FkDef` | `FkDef` | identical |
| `procedure` | `ProcedureDef` | `ProcedureDef` | identical |
| `entity` | `EntityDef` | `EntityDef` | identical |
| `attribute` | `AttributeDef` | `AttributeDef` | **v2.0.0:** drop top-level `searchable` (same fix as `ColumnDef`). |
| `relation` | `RelationDef` | `RelationDef` | identical |
| `er2db_entity` | `Er2dbEntityDef` | `Er2DbEntityDef` | **D3 rename:** TS keeps lowercase-d "db"; Kotlin uses camelcase `Db`. |
| `er2db_attribute` | `Er2dbAttributeDef` | `Er2DbAttributeDef` | same as above |
| `er2db_relation` | `Er2dbRelationDef` | `Er2DbRelationDef` | same as above |
| `query` | `QueryDef` | `QueryDef` | identical |
| `role` | `RoleDef` | `RoleDef` | v2.2 — identical |
| `er2cnc_role` | `Er2cncRoleDef` | `Er2CncRoleDef` | **D3 rename:** TS lowercase `cnc`; Kotlin `Cnc`. |
| `drill_map` | `DrillMapDef` | `DrillMapDef` | v2.2 — identical |

## Value types

| TS (`ast.ts`) | Kotlin (`model.Definition.kt`) | Notes |
|---|---|---|
| `PropertyValue` (union) | `PropertyValue` (sealed interface) | **D4:** Kotlin gains `source` field on every variant. |
| `StringValue` | `PropertyValue.StringValue` | TS `value` vs Kotlin `raw` — JSON dump normalises to `value`. |
| `TripleStringValue` | `PropertyValue.TripleStringValue` | NEW on Kotlin side — currently merged into `StringValue` in ai-platform; split out per TS shape (P1-3). |
| `NumberValue` | `PropertyValue.NumberValue` | TS `value: number` vs Kotlin `raw: Double`. |
| `BoolValue` | `PropertyValue.BoolValue` | TS `value: boolean` vs Kotlin `raw: Boolean`. |
| `NullValue` | `PropertyValue.NullValue` | Was data object in ai-platform; becomes data class with `source`. |
| `IdValue` | `PropertyValue.IdValue` | TS has `path` + `parts: string[]`. Kotlin gains `parts: List<String>` (P1-3). |
| `ListValue` | `PropertyValue.ListValue` | identical |
| `ObjectValue` | `PropertyValue.ObjectValue` | identical |
| `ObjectEntry` | (entries are `Map<String, PropertyValue>` in Kotlin) | TS uses entry objects with `source`; Kotlin uses Map for now — revisit if entry-level source is needed. |
| `FunctionCallValue` | `PropertyValue.FunctionCall` | **D3 rename:** TS suffix `Value`; Kotlin drops it. |
| `Reference` | `Reference` (value class) | identical surface (`path: String`). |
| `LocalizedString` | `LocalizedStringValue` | **D3 rename:** TS no suffix; Kotlin keeps `Value` suffix matching ai-platform convention. |
| `LocalizedStringList` | `LocalizedStringListValue` | same |
| `SearchBlock` | `SearchHintsValue` | **D3 rename.** Same field set. |
| `DataType` | `DataType` | identical |
| `SimpleDataType` / `StructuredDataType` | (folded into `DataType`) | TS splits; Kotlin uses one type with optional `length`/`precision`. |
| `MappingProperty` (union) | `MappingProperty` (sealed) | identical structure. |
| `MappingPropertyBareId` | `MappingPropertyBareId` | identical |
| `MappingPropertyBlock` | `MappingPropertyBlock` | identical |
| `MappingColumnEntry` | `MappingColumnEntry` | identical |
| `MappingColumnValue` | `MappingColumnValue` (sealed) | TS uses tagged union; Kotlin uses sealed interface. |
| `TargetValue` | `TargetValue` (sealed) | identical |
| `TargetObjectValue` | `TargetObjectValue` | identical |
| `TargetReferenceValue` | `TargetReferenceValue` | identical |
| `SchemaDirective` | `SchemaDirective` | identical |
| `ImportDecl` | `ImportStatement` | **D3 rename:** TS `Decl` suffix; Kotlin keeps `Statement`. |
| `PackageDecl` | `PackageDeclaration` | **D3 rename:** TS abbreviates; Kotlin spells out. |
| `GraphBlock` | `GraphBlock` | identical |
| `GraphLayout` | `GraphLayoutEntry` | TS uses one type; Kotlin uses `GraphBlock.layout: List<GraphLayoutEntry>`. |
| `SourceLocation` | `SourceLocation` | **D4:** Kotlin adopts TS superset (`endLine`, `endColumn`, `offsetStart`, `offsetEnd`). |
| `ParseError` | `ParseError` | TS: line 1-indexed, column 1-indexed (for display). Kotlin matches this for the error type only (distinct from `SourceLocation` on AST nodes). |
| `ParseResult` | `ParseResult` | identical |

## Field-name divergences (within otherwise-matching types)

| TS field | Kotlin field | Notes |
|---|---|---|
| `StringValue.value` | `StringValue.raw` | JSON dump normalises to `value`. |
| `NumberValue.value` | `NumberValue.raw` | same |
| `BoolValue.value` | `BoolValue.raw` | same |
| `DrillMapDef.override` | `DrillMapDef.overrideAuto` | `override` is a TS-reserved word in some contexts; Kotlin avoids the soft keyword. |
| `EntityDef.attributes[].valueLabels` | `AttributeDef.valueLabels: Map<String, LocalizedStringValue>` | identical shape, identical name. |

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
