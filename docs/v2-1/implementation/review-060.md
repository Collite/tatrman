# Review 060 — Section C (parser AST + walker for inline mappings)

**Date:** 2026-05-27
**Release:** v2.1 (inline mappings)
**Scope:** review of Section 2.1.C against [`tasks/section-C-parser-ast.md`](../plan/tasks/section-C-parser-ast.md) and the design forms in [`v2.1-inline-mappings.md`](../design/v2.1-inline-mappings.md) §3. Commit under review: `df56393` "Section C: parser — AST nodes and walker for inline mappings".

Verified against runtime (not just by reading the diff):

- `pnpm --filter @modeler/parser test` green — **122** tests (10 files); `inline-mappings.test.ts` 7/7.
- `pnpm -r typecheck` green (8/8) — the widened `Er2dbEntityDef.target` / `Er2dbAttributeDef.target` (`ObjectValue | Reference`) broke nothing downstream.
- `pnpm -r test` green — parser 122 · semantics 108 · edit 60 · migrate 23 · lsp 130 · vscode-ext 24 · designer 129 · integration 92 (+1 skip). No regressions.
- **Probed the actual AST for all three column-entry forms** (the check the test skips) — this surfaced C1 below.

Companion: [`tasks-review-060.md`](tasks-review-060.md).

**Verdict: substantially done — one correctness fix (C1) plus its missing test (C2) required before Section D.** The AST types, exports, and walker wiring all match the task spec and the suite is green. The walker also makes a **necessary and correct undocumented deviation** to cope with the Section B / review-059 grammar change (see "Good deviation" below). But the two `columns:` entry forms (b) and (c) — documented as interchangeable — are walked into **structurally inconsistent** AST shapes, and the test never checks them, so it slipped through. This is a latent defect that will complicate Section D; fix it in C.

---

## What's good (verified)

- **AST types match C.1 exactly.** `MappingPropertyBareId` / `MappingPropertyBlock` / `MappingProperty`, `MappingColumnEntry`, `MappingColumnValue` (discriminated `bareId` | `object`). `mapping?: MappingProperty` added to `EntityDef`, `AttributeDef`, `RelationDef`. `Er2dbEntityDef.target` / `Er2dbAttributeDef.target` widened to `ObjectValue | Reference`. The synthesized `IdValue` used in the walker is a valid `PropertyValue` variant — type-clean.
- **Exports match C.2** — all five new types re-exported from `index.ts`.
- **Walker wiring is complete, including the easy-to-miss spot.** `walkMappingProperty` is hooked into `walkEntityDef`, `walkAttributeDef`, `walkRelationDef`, **and** `walkAttributeDefList` (the inline `attributes: [ def attribute … ]` walker — the task's explicit "if you forget one, the entity-with-inline-attributes case fails" gotcha). Verified: the entity-full fixture, whose attributes are inline, parses and populates `mapping` correctly.
- **Explicit `er2db_*` target handling updated.** `walkEr2dbEntityDef` / `walkEr2dbAttributeDef` now call `walkTargetValue`, so the bare-id `target: <ref>` relaxation works in explicit declarations too — covered by the "targetProperty bare-id relaxation" test.
- **Source location points at the value, not the keyword.** The C.0 source-location test passes: `attr.mapping.source.offsetStart` slices to `IDX`, not to `mapping`. Confirmed at runtime.
- **All five surface forms parse with zero errors** (entity full, attribute bare-id, attribute full, relation bare-fk, relation full) and the bare-id discriminator (`kind: 'bareId'`, `id.path`) is correct.

### Good deviation (correct, should be noted in the task file)

The task spec's `walkMappingColumnMap` (C.2) assumed `mappingColumnValue : id | object_` and did `if (v.id()) … else walkObject(v.object_()!)`. But review-059 (B1) changed the grammar to `mappingColumnValue : id | LBRACE TARGET … mappingTargetValue RBRACE | object_`, so forms (b)/(c) no longer produce an `object_` child — `v.object_()` is **null** for them and the spec's walker would have thrown. The developer correctly added a `v.mappingTargetValue()` branch. This is the right adaptation and the kind of grammar/walker coupling that must track the B1 change — good catch. (The task file should be updated to document it; see task C3.)

---

## Medium

### C1 — `columns:` forms (b) and (c) produce inconsistent AST shapes

Design §3.1 states forms (a), (b), (c) "are interchangeable per entry — the synthesizer treats them identically." At the AST level they are **not** consistent. Probed:

```
kód_artiklu:   { target: KOD_ZBOZI }              → object.entries = [{ key: 'target', value: id(KOD_ZBOZI) }]      // wrapper PRESERVED
název_artiklu: { target: { column: NAZEV_ZBOZI } } → object.entries = [{ key: 'column', value: id(NAZEV_ZBOZI) }]    // wrapper STRIPPED
```

Form (b) keeps the `target` key; form (c) drops it and exposes the *inner* `{ column: … }`. Root cause: in `walkMappingColumnMap` (`walker.ts` ~lines 1602–1608), the form-(c) `else` branch does `object: walkObject(mtv.object_()!, file)` — returning the inner object — whereas the form-(b) branch (lines 1594–1601) explicitly rebuilds a `{ target: … }` wrapper. The two branches disagree on whether the `target` wrapper survives.

**Why it matters.** `MappingColumnValue` of `kind: 'object'` has exactly one sensible meaning: the object literal as authored, which for both (b) and (c) has a top-level `target` key. Section D's synthesizer will read these and, as written, must branch on whether it finds `target` (form b) or `column`/`table` (form c) — and a bare `{ column: X }` is ambiguous/misleading. This contradicts the design's equivalence guarantee and pushes avoidable special-casing into D. It is not a parse error and nothing consumes it yet (Section D is unwritten), so the build is green — but it should be fixed in C, before D is built on the inconsistency.

**Fix (verified).** Wrap the form-(c) object in a synthetic `target` entry, mirroring the form-(b) branch:

```ts
} else {
  const inner = walkObject(mtv.object_()!, file);
  value = {
    kind: 'object',
    object: { kind: 'object', entries: [{ key: 'target', value: inner, source: makeSourceLocation(mtv, file) }], source: makeSourceLocation(v, file) },
    source: makeSourceLocation(v, file),
  };
}
```

I applied exactly this, rebuilt, and re-probed: form (c) then yields `entries = [{ key: 'target', value: object{column} }]` — consistent with form (b). Reverted afterward.

### C2 — Forms (b) and (c) are untested (this is why C1 slipped through)

`inline-mappings.test.ts`'s entity-full case asserts only `columns[0].value.kind === 'bareId'` (form a). It never asserts the shape of `columns[1]` (form b) or `columns[2]` (form c). The task spec's example test had the same gap, and the developer copied it. Add assertions that **both** forms (b) and (c) produce `value.kind === 'object'` with a top-level `entries[0].key === 'target'`. These assertions fail on the current code (catching C1) and pass after the C1 fix — they are the guard for the fix.

---

## Low / observational

- **L1 — Synthetic-entry source fidelity.** After the C1 fix, the rebuilt `{ target: … }` wrapper carries a synthetic `target` key with no backing token, and the inner entry's `source` is the value span (`mtv`), not the `{ target: … }` span. This is fine for navigation — `MappingColumnValue.source` (the outer one, `makeSourceLocation(v)`) correctly covers the whole `{ … }` and is what Cmd-click uses. Just don't let a later refactor move the column value's authored-text source off `v`.
- **L2 — `model-graph.ts:365/372` is a pre-existing simplification, not a Section C issue.** `e2.target.table` casts the def to a local `{ target?: { table?: string } }`, but the real AST `target` is an `ObjectValue` with `entries` — so `.table` is silently `undefined` at runtime regardless. The widening (`ObjectValue | Reference`) is insulated by the cast (typecheck 8/8), so C introduces no new breakage here. Flag for whoever wires real er2db target rendering; out of C scope. (Satisfies the C verification "downstream `.target.`" check.)
- **L3 — `pnpm -r lint` not part of C's verification list.** It was green during the review-059 pass and C touched only parser source; not re-flagged here, but worth running in Section H's final gate.

---

## Recommendation

Section C is one walker branch and one test away from clean. Do **C1** (wrap the form-(c) object so it matches form (b)) and **C2** (assert forms (b) and (c) shapes — these catch C1 and guard the fix). Then update the Section C task file to record the `mappingTargetValue` walker branch (the necessary deviation from the spec, forced by review-059's B1 grammar change). The Low items are tracking notes. Once C1/C2 land, Section D (semantic synthesizer) can consume a single, consistent column-value shape. `tasks-review-060.md` has the concrete, ordered steps.

---

## Resolution (2026-05-27, commit `05b0747`)

**Verdict now: DONE.** Re-verified against runtime — both fixes landed exactly as specified, full workspace green.

- **C1 ✅** `walkMappingColumnMap`'s form-(c) branch now wraps the inner object in a `{ target: <inner> }` ObjectValue (`walker.ts:1603–1606`). Probed at runtime: form (b) → `entries:[{key:'target', value:id(KOD_ZBOZI)}]` and form (c) → `entries:[{key:'target', value:object{column}}]` — both now carry a consistent top-level `target` key. The design's "interchangeable forms" guarantee now holds at the AST level.
- **C2 ✅** `inline-mappings.test.ts` asserts forms (b) and (c) both produce `value.kind === 'object'` with `entries[0].key === 'target'`. These assertions fail on the pre-fix code (which produced `'column'` for form c), so they genuinely guard C1. Suite green (7 tests).
- **C3 ✅** The Section C task file now carries the full `mappingTargetValue` walker branch (both sub-cases) in the C.2 code block, and the bottom gotcha was rewritten to describe the three-alternative `mappingColumnValue` and the `{ target: <inner> }` rebuild.
- **L1/L2/L3** — tracking notes only; L2 (model-graph's pre-existing `target.table` simplification) carries forward to whoever wires real er2db target rendering.

**Gate after fix:** parser 122 · semantics 108 · edit 60 · migrate 23 · lsp 130 · vscode-ext 24 · designer 129 · integration 92 (+1 skip) · `pnpm -r typecheck` 8/8. Section C is ready; Section D (semantic synthesizer) can proceed on a single, consistent column-value shape.
