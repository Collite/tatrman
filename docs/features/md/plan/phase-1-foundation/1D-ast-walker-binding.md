# Stage 1D — AST + walker: binding objects

Goal: shape the four binding `def` kinds (`md2db_cubelet`, `md2db_domain`, `md2db_map`,
`md2er_cubelet`) into typed AST nodes with source locations. References opaque; no validation.

Prereq: Stage 1C merged & green. TDD: 1D1 before 1D2–1D3.

References (verified):
- AST/walker: `packages/parser/src/{ast.ts,walker.ts}`. The existing `er2db_*` walker code is the
  closest analogue — mirror its handling of `target`, nested `object_` maps, and column bindings.
- Target node shapes: [`../../contracts.md`](../../contracts.md) §2 (`// ---- Binding ----`):
  `Md2DbCubeletDef`, `ShapeSpec`, `AttrColumnBinding`, `MeasureColumnBinding`, `JournalingSpec`,
  `Md2DbDomainDef`, `Md2DbMapDef`, `Md2ErCubeletDef`.
- Property tables: [`../../contracts.md`](../../contracts.md) §4 (what each body may contain).
- Grammar: the binding bodies use generic `object_` maps for `attributes`/`measures`/`source`/
  `columns`; the walker turns those into the typed records below.

---

- [ ] **1D1 — Walker tests first (red).** Assert the AST of the 1B binding fixtures:
  - `md2db_cubelet` **wide**: `shape {shape:'wide'}`, `attributes` record of `{column}` (plus one
    map-mediated `{via, from:{table,column}}` entry), `measures` record of `{column}`,
    `journaling {mode:'overwrite'}`.
  - `md2db_cubelet` **long**: `shape {shape:'long', codeColumn, valueColumn}`, `measures` of
    `{code}`, and an `invalidate` journaling `{mode:'invalidate', validColumn}`.
  - `md2db_domain`: `domainRef`, `source_ {table, column}`.
  - `md2db_map`: `mapRef`, `table`, `columns` record (domain → column).
  - `md2er_cubelet`: `cubeletRef`, `entity`, `attributes` record (attr → ER attr). Assert it has
    **no** shape/measures/journaling fields populated.
  - Confirm red.

- [ ] **1D2 — AST node types.** Add the contracts §2 binding interfaces to `ast.ts`; extend the
  AST/definition union. Keep all refs as strings; keep the `ShapeSpec`/`JournalingSpec`/
  `AttrColumnBinding`/`MeasureColumnBinding` discriminated unions exactly as contracts §2.

- [ ] **1D3 — Walker.**
  - `Md2DbCubeletDef`: parse `shape` (`id` `wide` vs object `{long:{…}}`) → `ShapeSpec`; walk
    `attributes`/`measures` generic maps into the typed records (detect the map-mediated
    `{via, from}` attribute form and the long `{code}` measure form); parse `journaling`
    (`id` vs `{invalidate:{validColumn}}`) → `JournalingSpec`.
  - `Md2DbDomainDef`, `Md2DbMapDef`, `Md2ErCubeletDef`: straightforward record walks per contracts.
  - Source locations on every node + nested column binding (edit-synthesizer invariant).

- [ ] **1D4 — Verify.**
  - 1D1 tests pass. `pnpm --filter @modeler/parser test`
  - `pnpm -r typecheck && pnpm -r lint && pnpm -r build && pnpm -r test`
  - **Phase 1 DONE check:** every construct in [`../../design.md`](../../design.md) §5–§6 parses to
    the contracts §2 AST; `@modeler/md-catalog` (1A) green; all gates green.

- [ ] **1D5 — Commit.** `Section MD-1D: AST + walker for MD binding objects`.
