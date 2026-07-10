# Phase 3.H — Index `relation`, `query`, `role`, `er2db*`, `er2cnc*` as SymbolEntry rows

**Goal:** the seven `Definition.kind` values that Phase 2 did *not* index now produce `SymbolEntry` rows in `ProjectSymbolTable`. Resolver and `ReferenceIndex` pick them up automatically because they read from the table. The inspector's "Related symbols" list and workspace symbols search become complete.

**Reads:** `docs/plan/progress-phase-02.md` → "Deferred to later phases" row that lists this carryover. [contracts §1 (SchemaCode)](../../design/phase-03-contracts.md#1-schema-and-namespace-constants), Phase 2 `packages/semantics/src/symbol-table.ts`.
**Blocked by:** Pre-flight only — this is parallel-safe with §A.
**Blocks:** §E (`getSymbolDetail` for these kinds returns useful data only after H lands).

## Tests-first

- [ ] `packages/semantics/src/__tests__/symbol-table-extended-kinds.test.ts` — unit-level. One test per kind:
  - `relation` def in a `schema er namespace entity` document emits a `SymbolEntry` with qname `er.entity.<name>`, `kind: 'relation'`.
  - `query` def in `schema query namespace q1` document emits `query.q1.<name>` with `kind: 'query'`.
  - `role` def in `schema cnc namespace role` document emits `cnc.role.<name>` with `kind: 'role'`. (User-defined roles, distinct from the stock `cnc.role.*`.)
  - `er2dbEntity` def in `schema map namespace er2db` emits `map.er2db.<name>` with `kind: 'er2dbEntity'`.
  - `er2dbAttribute`, `er2dbRelation`, `er2cncRole` — one test each, same pattern.

- [ ] `tests/integration/src/symbol-indexing-extended.test.ts` — component-scope.
  - Load `samples/v1-metadata/`. Assert `workspaceSymbols('rel')` includes at least one `kind: 'relation'` row. Pre-H, this query returned zero relations.
  - Assert `getSymbolDetail` for a known relation qname returns non-null with `perKindData.kind === 'relation'`.
  - Assert the inspector's `Related symbols` for `er.entity.artikl` includes both the map-side `er2dbEntity` row AND any `relation` whose `from` or `to` points at `er.entity.artikl`.

## Library reference

None. This is pure refactoring inside `@modeler/semantics`.

## Implementation tasks

- [ ] **H.1 — Identify the existing per-kind emitters.** Read `packages/semantics/src/symbol-table.ts`. Note the function/branch that emits entries for `entity` + nested attributes, `table` + nested columns, `view` + nested columns, `procedure` + nested resultColumns. The new kinds follow the same shape (top-level emit only; no nested children).
- [ ] **H.2 — Add emitters for the seven kinds.** For each of `relation`, `query`, `role`, `er2dbEntity`, `er2dbAttribute`, `er2dbRelation`, `er2cncRole`, emit one `SymbolEntry` with the document's schema/namespace and the def's `name` as the qname's local part. The `kind` field uses the def's literal kind string. Make the unit tests green.
- [ ] **H.3 — Verify resolver and ReferenceIndex pick them up.** No code change expected; they read from `ProjectSymbolTable`. Run `pnpm --filter @modeler/semantics test` and confirm no regressions; the existing 40 tests must stay green.
- [ ] **H.4 — Integration assertions.** Make `symbol-indexing-extended.test.ts` green. If a workspace-symbols query change is needed (e.g. the fuzzy matcher in `@modeler/lsp` filters by some `kind` set), update that filter to include the new kinds.
- [ ] **H.5 — Update Phase 2 progress doc.** Tick the line in `docs/plan/progress-phase-02.md` "Deferred to later phases" → "Indexing relations/queries/roles/er2db_* as separate symbol-table entries" with `Completed in Phase 3.H` + date.

## Verify by running

```bash
pnpm --filter @modeler/semantics test
pnpm --filter @modeler/lsp test
pnpm --filter @modeler/integration-tests test
```

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] Seven new per-kind unit tests green.
- [ ] Integration test asserts non-empty Related-symbols for `er.entity.artikl` including a relation.
- [ ] Phase 2 progress doc line moved from open to closed.
