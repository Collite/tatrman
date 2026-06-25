# Stage 1C — AST + walker: logical objects

Goal: shape the parse tree of the six logical MD `def` kinds into typed AST nodes with accurate
source locations. Cross-references stay **opaque strings** (resolution is Phase 2). No validation.

Prereq: Stage 1B merged & green (generated parser exists). TDD: 1C1 before 1C2–1C4.

References (verified):
- AST: `packages/parser/src/ast.ts` (existing `*Def` node conventions — copy the style).
- Walker: `packages/parser/src/walker.ts` — `makeSourceLocation` is here. **Footgun (CLAUDE.md):**
  for multi-token spans `endColumn = stopToken.column + stopTokenLength`, **not**
  `startColumn + spanLength`. Re-verify on every new multi-token body.
- Target node shapes: [`../../contracts.md`](../../contracts.md) §2 (the `// ---- Logical ----`
  block) — `MdDomainDef`, `RestrictClause`, `RangeLiteral`, `DomainMember`, `DimensionDef`,
  extended `AttributeDef`, `MdMapDef`, `CalcRef`/`CalcArg`, `HierarchyDef`/`HierarchyLevel`,
  `MeasureDef`/`AggregationSpec`, `CubeletDef`.
- Reused types: `DataType`, `LocalizedString`, `ValueLabels`, `LiteralValue`, `SourceLocation`
  (already in `ast.ts`).

---

- [ ] **1C1 — Walker tests first (red).** In `packages/parser/src/__tests__/`, assert the AST of
  the 1B fixtures:
  - domain: `domainKind`, `restrict[]` clauses, a `RangeLiteral {lo,hi}`, member labels;
  - dimension: `key`, inline `attributes[]` (each an `AttributeDef` with `domainRef`), `hierarchies[]`;
  - attribute: MD form populates `domainRef` (and optional `aggregation`), ER form still populates
    `type` (shared node — assert both shapes round-trip);
  - map: `from[]`/`to[]` arrays, `cardinality`, `calc` (`CalcRef` with `args[]` for the
    parameterised case; `calc` absent for table-backed);
  - hierarchy: `levels[]` in **leaf→root** order with optional `via`;
  - measure: `domainRef`, `measureClass`, `aggregation` (both the bare-`sum` and the per-dimension
    object form → `AggregationSpec`), `validBy`;
  - cubelet: `grain[]` dotted refs, `measures[]` (string refs and inline `MeasureDef`).
  - Assert `source` spans on a sampling of nested nodes (esp. multi-token: `restrict` block, a
    `levels` entry with `via`). Confirm red.

- [ ] **1C2 — AST node types.** Add the contracts §2 logical interfaces to `ast.ts`. Add the new
  node kinds to the `Definition`/AST union type. Keep cross-references as `string` / `string[]`.

- [ ] **1C3 — Walker: domain / dimension / attribute.**
  - Build `MdDomainDef` (type, kind, restrict clauses incl. `RangeLiteral` and `DomainMember`
    labels via the reused localized-string handling).
  - Build `DimensionDef`; reuse the existing inline-attribute walk for `attributes[]`.
  - Extend the shared `AttributeDef` walk to populate `domainRef` from `domain:` and `aggregation`
    from the MD agg form, leaving ER fields intact. **Do not** branch on schema here — both shapes
    are accepted; Phase 2 enforces per-schema validity.

- [ ] **1C4 — Walker: map / hierarchy / measure / cubelet.**
  - `MdMapDef`: normalise `from`/`to` single-or-list into arrays; parse `cardinality` object →
    `'1:1' | 'N:1'`; build `CalcRef` (name + `CalcArg[]`) from `id`/`functionCall`.
  - `HierarchyDef`: preserve level order; capture `via` per level.
  - `MeasureDef`: build `AggregationSpec` from the bare-id or object form; capture `measureClass`,
    `validBy`.
  - `CubeletDef`: dotted `grain` refs as strings; `measures` as refs **or** inline `MeasureDef`.
  - Verify `makeSourceLocation` spans on each new multi-token body (the footgun).

- [ ] **1C5 — Verify.**
  - 1C1 tests pass. `pnpm --filter @modeler/parser test`
  - `pnpm -r typecheck && pnpm -r lint && pnpm -r build`
  - Existing ER attribute walker tests still green (shared-node change is additive).

- [ ] **1C6 — Commit.** `Section MD-1C: AST + walker for MD logical objects`.
