# Review 063 — Section G (ai-platform mirror: Kotlin parser + synthesizer + validator)

**Date:** 2026-05-29
**Release:** v2.1 (inline mappings)
**Scope:** review of Section 2.1.G against [`tasks/section-G-ai-platform.md`](../plan/tasks/section-G-ai-platform.md), the design rule in [`v2.1-inline-mappings.md`](../design/v2.1-inline-mappings.md), and the contract in [`grammar-v2-1-changes.md`](../design/grammar-v2-1-changes.md). Branch under review: ai-platform `grammar-2-1` (uncommitted at time of review).

Verified against runtime (not just by reading the diff):

- `./gradlew build` on the full ai-platform tree green — 646 tasks. No regressions in any module.
- `./gradlew :shared:libs:kotlin:ttr-parser:test` green — 7 new `InlineMappingsSpec` tests + existing tests, 0 failures.
- `./gradlew :shared:libs:kotlin:ttr-writer:test` green — existing tests pass after `TargetObjectValue` wrapping was threaded through `TtrRendererSpec`.
- `./gradlew :infra:metadata:test` green — 8 new `InlineMappingSynthesisSpec` tests (entity/attribute/relation synthesis + duplicate-mapping for entity-level + attribute-level + a clean control + a negative).
- `packages/grammar/scripts/check-sync.sh ~/Dev/ai-platform` reports a "mismatch" that is **only** the 2-line vendoring header (`diff` confirms zero diff in grammar content). Tracked in [modeler#5](https://github.com/BoraPerusic/modeler/issues/5); not a Section G defect.
- Re-probed the synthesizer end-to-end via the new `InMemoryStorage` harness in `InlineMappingSynthesisSpec`: an inline `mapping:` on `def entity X` + an explicit `def er2db_entity X` in the same project produces exactly **2 `ttr/duplicate-mapping`** diagnostics (one per side) on top of a `Model` that holds both `Er2DbEntityMapping` records (one `Inline("entity")`, one `Explicit`).

Companion: [`tasks-review-063.md`](tasks-review-063.md).

**Verdict: SHIP after fixing B1 and C1.** The grammar/parser/walker/AST surface is faithful to the modeler side; the synth-in-`Source.kt` architectural decision (per the answer to my question before starting G.7) fits cleanly into the existing materialisation path; the validator integrates with the existing `ModelReconciler` pipeline without new wiring. Two real items block clean commit: a wrong-namespace bug in the relation synthesiser (B1) and a dead `when` branch (C1). Everything else is either a test gap I'd close in the same commit (T1, T2) or a known limitation worth a follow-up issue, not a v2.1 blocker.

---

## What's good (verified)

### G.2 — Grammar sync

- `shared/libs/kotlin/ttr-parser/src/main/antlr/shared/ttr/parser/generated/TTR.g4` carries `@grammar-version: 2.1`, the new `MAPPING` token, the new `mappingProperty` / `mappingValue` / `mappingBlock` / `mappingBlockProperty` / `mappingColumnsProperty` / `mappingColumnMap` / `mappingColumnEntry` / `mappingColumnValue` / `mappingTargetValue` rules, and the relaxed `targetProperty : TARGET propSep? ( object_ | id )`. The `entityProperty`, `attributeProperty`, and `relationProperty` rule alternatives include `mappingProperty`. Byte-identical to `packages/grammar/src/TTR.g4` modulo the vendoring header.

### G.3 — Parser regeneration

- `./gradlew :shared:libs:kotlin:ttr-parser:generateGrammarSource` runs clean. The generated `TTRParser.java` exposes `MappingValueContext`, `MappingBlockContext`, `MappingBlockPropertyContext`, `MappingColumnsPropertyContext`, `MappingColumnMapContext`, `MappingColumnEntryContext`, `MappingColumnValueContext`, `MappingTargetValueContext` accessors — all the entry points the Kotlin walker needs.

### G.4 — Kotlin AST (`Definition.kt`)

- New types follow the modeler-side TS shape faithfully:
  - `MappingProperty` sealed interface with `MappingPropertyBareId(id: Reference, source)` and `MappingPropertyBlock(target: TargetValue?, columns: List<MappingColumnEntry>, fk: Reference?, source)`.
  - `TargetValue` sealed interface with `TargetObjectValue(obj: PropertyValue.ObjectValue, source)` and `TargetReferenceValue(ref: Reference, source)`.
  - `MappingColumnEntry(name, value, source)` and `MappingColumnValue` sealed with `MappingColumnBareId` and `MappingColumnObject`.
- `EntityDef`, `AttributeDef`, `RelationDef` gain `val mapping: MappingProperty? = null` — default-null preserves binary/source compatibility for every existing construction site.
- `Er2DbEntityDef.target` / `Er2DbAttributeDef.target` widened from `PropertyValue.ObjectValue?` to `TargetValue?` — required so the explicit `target: <bareId>` relaxation parses without data loss.
- Kdoc on the widened fields explicitly tells consumers to pattern-match on `TargetObjectValue` for the entries-map case, which is exactly the affordance the downstream patches need.

### G.5 — Kotlin walker (`TtrWalker.kt`)

- `visitMappingProperty` mirrors the modeler `walkMappingProperty` shape: `mappingValue.id()` → `MappingPropertyBareId`; otherwise iterate `mappingBlockProperty()` and dispatch on `targetProperty()` / `mappingColumnsProperty()` / `fkProperty_()`. Source location uses `location(blockCtx)` for blocks and `location(valueCtx)` for bare-id, matching modeler.
- `visitTargetValue` is the right shared helper — used by `visitMappingProperty` AND by the explicit `visitEr2dbEntity` / `visitEr2dbAttribute` walkers (replacing the previous `visitObject(t.object_())` which silently dropped the bare-ref form).
- `visitMappingColumnMap` correctly emits **synthetic `{ target: <inner> }` ObjectValue wrappers** for column form (b) `{ target: KOD_ZBOZI }` and `{ target: { column: NAZEV_ZBOZI } }`, matching the modeler walker bit-for-bit. The plain `object_` alternative (form c, unusual) falls back to `MappingColumnObject(visitObject(v.object_()))`.

### G.7 — `MappingSource` on the model layer (`Model.kt`)

- `MappingSource` sealed interface with `data object Explicit` and `data class Inline(val hostKind: String)` — idiomatic Kotlin, easy to pattern-match on, future-proof for inline-from-attribute vs inline-from-entity-columns distinction.
- Promoted to an abstract `mappingSource: MappingSource` member on the `Mapping` sealed interface; all four implementers (`Er2DbEntityMapping`, `Er2DbAttributeMapping`, `Er2DbRelationMapping`, `Er2CncRoleMapping`) override with `default = MappingSource.Explicit`. Binary-compatible for every existing construction site.

### G.8 — Synthesiser (`Source.kt`)

- `synthesiseInlineEntityMappings(def, entityQn, sourceFile, mappings)` is invoked from the existing `EntityDef` branch in `processDef`, immediately after the `roles: [...]` desugaring. Tags the entity-level mapping with `MappingSource.Inline("entity")` and each per-column mapping with `MappingSource.Inline("entity")` — the `hostKind` reflects where the mapping was **authored**, not the kind being mapped, which is the right call for diagnostic clarity.
- `synthesiseInlineAttributeMappings` correctly uses `entityQn.schemaCode.name.lowercase()` and `entityQn.namespace` for `attrEntityQn`, so the host file's actual schema/namespace flows through (no hardcoding).
- `inlineColumnToAttributeTarget` correctly handles all three column-value shapes via the wrap-as-`{target: ...}` invariant the walker establishes.
- `synthesiseInlineRelationMapping` mirrors the bare-id and `{ fk: ... }` cases. **But see B1 for a wrong-namespace bug in the synthesised `relation` field.**

### G.9 — Conflict validator (`ModelReconciler.kt`)

- `detectDuplicateMappings(mappings, errors)` correctly groups by qname, filters to groups where at least one entry is `MappingSource.Inline`, and emits one `LoadWarning` per side of the collision. The message prefix `ttr/duplicate-mapping:` matches the existing ai-platform diagnostic-code-in-message convention (`ttr/package-declaration-mismatch:` etc.) and is grep-able.
- The "at least one Inline" guard correctly keeps **pure-explicit** collisions out of `ttr/duplicate-mapping` — those would be the concern of a separate `ttr/duplicate-definition` validator on the ai-platform side (not in scope for v2.1; pre-existing gap not introduced by this work).
- Wired in at the obvious spot in `reconcile()` — immediately after `val mappings = sorted.flatMap { it.mappings }`. No new validator-collection plumbing needed.

### G.10 — Tests

- `InlineMappingsSpec.kt` (parser-side, 7 tests): entity full block + 3 column forms; attribute bare-id; attribute block with `{ target: { column: ... } }`; relation bare-fk; relation `{ fk: ... }`; explicit `def er2db_attribute` with bare-ref target; mapping source-location on line 2.
- `InlineMappingSynthesisSpec.kt` (metadata-side, 8 tests): entity-level synth (entity mapping + 2 per-column mappings, all `Inline("entity")`); attribute-level synth (`Inline("attribute")`); relation-level bare-fk and wrapped-fk synth; plain entity contributes nothing; duplicate-mapping fires for entity-level collision; duplicate-mapping fires for attribute-level collision; no duplicate-mapping for explicit-only project (pre-v2.1 baseline preserved).
- `InMemoryStorage` test helper is local to the spec (small + obvious) — avoids adding a public test utility while still letting the spec drive `FileBasedSource` without disk I/O.

### Cross-cutting downstream patches

- `TtrRenderer.kt` gains a 4-line `renderTargetValue` helper used at the two `Er2Db*Def.target` render sites; renders `TargetReferenceValue` as the bare ref path (lossless round-trip).
- `ModelToDefinitions.kt` wraps the existing `mappingTargetToPropertyValue(...)` output in `TargetObjectValue(obj=..., source=LOC)` — same content, new wrapper.
- `Source.kt` consumption sites in the `Er2DbEntityDef` / `Er2DbAttributeDef` branches were correctly updated to pattern-match on `TargetValue` (with `TargetReferenceValue` mapped to implicit `{ table }` / `{ column }` per the convention spelled out in the AST kdoc).

---

## Findings — fix before commit

### B1 — `synthesiseInlineRelationMapping` hardcodes `er` / `entity` for the synthesised `relation` qname (correctness bug)

**Location:** `infra/metadata/src/main/kotlin/infra/metadata/source/Source.kt:824`

```kotlin
relation = qname("er", "entity", def.name), // best-effort; reconciler resolves
```

The host file's actual `schemaCode` and `namespace` are available in the enclosing `processDef` (function parameters) but aren't threaded through to this helper. For the production case — `schema er namespace entity` — the qname happens to be right by coincidence. For any project that uses `schema er namespace <other>` (which the rest of Section G's machinery supports), the synthesised mapping will reference a non-existent relation qname.

The entity-level and attribute-level synthesisers do **not** have this bug — they correctly use `entityQn.schemaCode.name.lowercase()` and `entityQn.namespace`. The fix mirrors that pattern: pass `schemaCode` and `namespace` into `synthesiseInlineRelationMapping` and construct `qname(schemaCode, namespace, def.name)` for the `relation` field.

**Suggested fix:** extend the helper signature and call site:

```kotlin
private fun synthesiseInlineRelationMapping(
    def: RelationDef,
    schemaCode: String,
    namespace: String,
    sourceFile: String,
    mappings: MutableList<infra.metadata.model.Mapping>,
) {
    ...
    relation = qname(schemaCode, namespace, def.name),
```

and at the call site in `processDef`:

```kotlin
synthesiseInlineRelationMapping(def, schemaCode, namespace, sourceFile, mappings)
```

Add a test fixture using `schema er namespace foo` to lock the fix in.

### C1 — Dead `when` branch in `inlineColumnToAttributeTarget`

**Location:** `infra/metadata/src/main/kotlin/infra/metadata/source/Source.kt:858–862`

```kotlin
// Bare reference inside { target: <bareId> } — convention: column.
entries["target"] is PropertyValue.IdValue ->
    AttributeMappingTarget.Column(
        refToQname(entries["target"], "db", "dbo"),
    )
```

Unreachable. The block just above (lines 845–849) replaces `inner` with `mapOf("column" to it)` when `entries["target"]` is `PropertyValue.IdValue`, so `inner.containsKey("column")` is true and the `inner.containsKey("column")` branch (line 852) catches it before this case is evaluated.

**Suggested fix:** delete the branch (5 lines).

### T1 — No test for relation-level duplicate-mapping

`InlineMappingSynthesisSpec.kt` covers entity-level and attribute-level collisions but not relation-level. Quick to add — same shape as the existing entity-level test with an inline `mapping: db.dbo.fk_a_b` on a relation + an explicit `def er2db_relation` pointing at the same name.

### T2 — No round-trip test against modeler's `samples/2.1` corpus

The cross-loader contract is the whole point of shipping both repos together. The `InMemoryStorage` harness makes it trivial to feed modeler's `samples/2.1/{db,er,map}.ttr` directly through ai-platform's `FileBasedSource` and assert (a) zero parse errors, (b) the expected synthesised mappings appear, (c) zero `ttr/duplicate-mapping` diagnostics on the canonical (non-broken) fixtures.

A modest version of this — read the three files from the modeler checkout (or copy them into ai-platform's test resources) — would be ~20 lines and catches divergence between the two grammars/walkers/synthesisers faster than anything else.

---

## Findings — note and defer (not blockers)

### L1 — Synthesised `Er2Db*Mapping` records carry only `sourceFile`, no line/column

Consequence: `ttr/duplicate-mapping` diagnostics carry `line = -1, column = -1`. Users can identify the offending **file** but not the exact `mapping:` line. The modeler-side validator pinpoints line+column. Closing this gap requires adding `sourceLine` / `sourceColumn` (or a `SourceLocation` value type) to the `Mapping` sealed interface — a pre-existing ai-platform shape question, not in scope for v2.1 to fix.

### L2 — `Er2Db*Mapping.attribute` / `.entity` reference qname shape may diverge between explicit and synth paths

`Reference.toQname` (`Source.kt:974`) interprets a 4-part dotted path as `package.schema.namespace.name`, so an explicit `attribute: er.entity.produkt.id_produktu` ends up with `pkg="er", schema="entity", namespace="produkt", name="id_produktu"`. My synth path constructs `qname("er", "entity", "produkt.id_produktu")` → `pkg="", schema=ER, namespace="entity", name="produkt.id_produktu"`. **The mapping qname itself (the duplicate-detection key) matches** because both paths use `qname("map", "er2db_attribute", "${entity}.${attr}")`, so the validator works correctly. But downstream consumers of `Er2DbAttributeMapping.attribute` that key by the resolved QualifiedName will see different shapes.

This is a pre-existing quirk of `Reference.toQname`'s 4-part interpretation; my synth picks a sensibly different shape that happens to diverge. The right long-term fix is a single canonical interpretation of dotted reference paths across the file. Out of scope for v2.1.

### L3 — Empty `mapping: { }` on entity/attribute/relation synthesises nothing, silently

Entity: needs a `target` to synthesise the entity-level mapping (correct — can't materialise without one). Columns are still synthesised. So `mapping: { columns: { x: Y } }` (no target) produces per-column synth but no entity-level synth — fine.

Attribute: needs a `target` to produce an `AttributeMappingTarget`. Empty block → silent skip.

Relation: needs `fk`. Empty block or `mapping: {}` → silent skip.

Modeler-side may emit a warning for these. Worth a follow-up to align both sides; not a blocker.

### L4 — Cross-loader snake_case vs camelCase in synthesised qnames

Already filed as [modeler#6](https://github.com/BoraPerusic/modeler/issues/6). My ai-platform synth uses `qname("map", "er2db_entity", ...)` (snake_case) to match ai-platform's existing explicit-form convention — so internal duplicate detection works. Modeler synth uses `er2dbEntity` (camelCase). Cross-loader round-trip parity for these qnames is the pre-existing gap; this v2.1 work doesn't widen it.

### L5 — `check-sync.sh` always reports mismatch after a successful sync

Already filed as [modeler#5](https://github.com/BoraPerusic/modeler/issues/5). Pre-existing limitation of the script; not introduced by Section G.

### L6 — No `ttr/duplicate-definition` on the ai-platform side for pure-explicit mapping collisions

Two `def er2db_entity X` declarations in the same project silently merge into the `mappings` list (with the first-seen winning per `mergeMaps` precedence) and produce no diagnostic. Pre-existing ai-platform gap; not v2.1's job to fix. The modeler side does catch this via a separate `ttr/duplicate-definition` validator. Worth a separate ai-platform-side issue tracked at the same level as the modeler issues — but flagging here for the record.

---

## Recommendation

Fix **B1** (real correctness bug, ~10 lines including the test fixture) and **C1** (dead code, ~5 lines deleted) before commit. **T1** (relation duplicate-mapping test) is ~15 lines and closes a coverage gap that the matching modeler-side suite does cover. **T2** is the highest-value follow-up — the cross-loader integration test is what catches Sections G.4/G.5 drift faster than anything else, but it can land in a follow-up commit if the goal is to ship Section G as-is now and iterate. L1–L6 are all defer-with-an-issue material, none block the merge.

`tasks-review-063.md` has the concrete, ordered steps.
