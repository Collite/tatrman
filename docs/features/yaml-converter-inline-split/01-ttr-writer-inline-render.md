# Stage 1 — ttr-writer: render inline mappings + publish (modeler)

**Goal:** teach `org.tatrman:ttr-writer`'s `TtrRenderer` to emit the inline
`mapping:` property on `EntityDef`, `AttributeDef`, and `RelationDef`. The
parser model + walker already populate these fields (v2.1); only rendering is
missing. **Do not touch the grammar.** Keep the existing standalone
`def er2db_*` renderers untouched — they're still valid for explicit mappings.

**Files:**
- `packages/kotlin/ttr-writer/src/main/kotlin/org/tatrman/ttr/writer/TtrRenderer.kt`
  — `renderEntity` (line 179), `renderAttribute` (240), `renderRelation` (362),
  and the existing helper `renderTargetValue` (639) to reuse.
- Model: `packages/kotlin/ttr-parser/.../model/Definition.kt` — `MappingProperty`
  (`MappingPropertyBareId{ id }`, `MappingPropertyBlock{ target, columns, fk }`),
  `MappingColumnEntry{ name, value }`, `MappingColumnValue`
  (`MappingColumnBareId` / `MappingColumnObject`).
- Tests: `packages/kotlin/ttr-writer/src/test/kotlin/...` (Kotest), matching the
  existing round-trip spec from Phase 1.5.

**Golden output shapes:** `samples/2.1/er.ttr` (artikl = entity-level block;
produkt = per-attribute short forms) and `samples/2.1/db.ttr`.

---

- [x] **1.1 — Tests first: attribute short form.** Add a Kotest case: an
  `AttributeDef` whose `mapping = MappingPropertyBareId(Reference("IDSKUPZBOZI"))`
  renders `mapping: IDSKUPZBOZI` inside the attribute body. Assert exact string.

- [x] **1.2 — Tests first: attribute block form.** An `AttributeDef` whose
  `mapping = MappingPropertyBlock(target = …)` (e.g. `{ target: { column: NAZEV } }`)
  renders the block form. Assert exact string.

- [x] **1.3 — Tests first: entity-level block with columns map.** An `EntityDef`
  with `mapping = MappingPropertyBlock(target = { table: db.dbo.QZBOZI_DF },
  columns = [ id_artiklu→bareId IDZBOZI, kód_artiklu→object {target: KOD_ZBOZI},
  … ])` renders the full `mapping: { target: { table: … }, columns: { … } }`
  block, short bare-id entries where the value is `MappingColumnBareId` and the
  object form where it's `MappingColumnObject`. Mirror artikl in `samples/2.1/er.ttr`.

- [x] **1.4 — Tests first: relation FK short form.** A `RelationDef` with
  `mapping = MappingPropertyBareId(Reference("db.dbo.fk_artikl_produkt"))`
  renders `mapping: db.dbo.fk_artikl_produkt`. Also a round-trip test (below).

- [x] **1.5 — Tests first: round-trip.** Parse `samples/2.1/er.ttr` with
  `ttr-parser`, render with `TtrRenderer`, re-parse, and assert the two ASTs are
  structurally equal (use the existing round-trip helper from the Phase 1.5
  spec). This is the regression guard. Confirm all of 1.1–1.5 currently FAIL.

- [x] **1.6 — Implement.** Add a `renderMapping(m: MappingProperty): String`
  helper and a `renderMappingColumns(entries)` helper; call them from
  `renderEntity`/`renderAttribute`/`renderRelation` when `def.mapping != null`.
  Reuse `renderTargetValue` (line 639) for the `target:` slot. Match the brace/
  comma/indent style of the surrounding renderers exactly so round-trip and
  golden-file comparisons pass. Make 1.1–1.5 green.

- [x] **1.7 — Full gate.** `./gradlew :packages:kotlin:ttr-writer:test` green;
  no regression in existing writer specs. Add a `CHANGELOG.md` note: "ttr-writer
  renders inline `mapping:` on entity/attribute/relation defs (additive)."

- [~] **1.8 — Publish.** **Version: `0.4.0`** (additive minor; `kotlin/v0.3.0`
  is already tagged at the `SymbolEntry.namespace` commit, so this is the next
  bundle — it also first ships the kind-derived defaults to consumers).
  - **Done (local):** `./gradlew -Pversion=0.4.0 :…ttr-parser:publishToMavenLocal
    :…ttr-writer:publishToMavenLocal :…ttr-semantics:publishToMavenLocal` →
    `~/.m2/.../org/tatrman/{ttr-parser,ttr-writer,ttr-semantics}/0.4.0/`. Stages
    2 & 3 develop/test against this.
  - **Pending (user):** the remote GitHub Packages publish is tag-driven CI —
    push `kotlin/v0.4.0` (bundle) before the ai-platform PR merges. Not triggered
    autonomously (outward-facing/irreversible).

### Stage 1 DoD
- [x] Both inline variants render for entity/attribute/relation; round-trip on
      `samples/2.1/er.ttr` is byte-stable through parse→render→parse; new
      ttr-writer version published.
