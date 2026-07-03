# Stage 2D — Map + calc-catalog validation

Goal: validate `def map` — calc reference resolution, argument checking, and `from`/`to`
type-checking against the catalog signature — plus cardinality consistency and the table-backed
"needs a binding" flag.

Prereq: Stages 2A (catalog preload) + 2B (refs resolved) merged & green. TDD: 2D1 before 2D2–2D4.

References (verified):
- Catalog: `@modeler/md-catalog` (`MD_CALC_CATALOG`, `CatalogEntry`). Algorithm:
  [`../../contracts.md`](../../contracts.md) §6.4. Catalog semantics: [`../../map-catalog.md`](../../map-catalog.md).
- Validator module from 2C (`md-validators.ts`).
- Codes: contracts §7 (`md/unknown-calc-map`, `md/bad-calc-args`, `md/calc-type-mismatch`,
  `md/calc-cardinality-conflict`, `md/table-map-no-binding`).

---

- [x] **2D1 — Tests first (red).** Table-driven:
  - unknown `calc:` name → `md/unknown-calc-map`;
  - unknown/missing/out-of-range arg (e.g. `fiscalYearStartMonth: 13`) → `md/bad-calc-args`;
  - `from`/`to` whose domain types don't satisfy the entry (e.g. `truncToDay` with a `to` of type
    `int`) → `md/calc-type-mismatch`; the correct calendar maps (`truncToDay`, `monthOfDate`,
    `quarterOfMonth`) validate clean;
  - explicit `cardinality: 1:1` on a calc map → `md/calc-cardinality-conflict`;
  - a table-backed map (no `calc:`) with no binding context → `md/table-map-no-binding` (warning).
  - Confirm red.

- [x] **2D2 — Calc resolution + args.** Look the `CalcRef.name` up in `MD_CALC_CATALOG`
  (`md/unknown-calc-map`). Validate each `CalcArg` against `entry.params`: known name, required
  present, value within the param's `int{lo..hi}` or enum `values` (`md/bad-calc-args`).

- [x] **2D3 — Type-check `from`/`to`.** Resolve the `from` (single) and `to` domains; check their
  `type` (+`restrict` range where the entry constrains output) satisfy `entry.input`/`entry.output`
  per the shape vocabulary in [`../../map-catalog.md`](../../map-catalog.md) §1
  (`instant`/`date`/`int{lo..hi}`). Emit `md/calc-type-mismatch` with the offending domain's range.

- [x] **2D4 — Cardinality + table-backed flag.** A calc map is implicitly `N:1`; an explicit `1:1`
  conflicts (`md/calc-cardinality-conflict`). A map with no `calc:` and (within a project that has
  binding files) no `md2db_map` → `md/table-map-no-binding`. (In model-only files emit a warning;
  Phase 3 escalates to error where a binding context exists.)

- [x] **2D5 — Verify.**
  - 2D1 tests pass. `pnpm --filter @modeler/semantics test`
  - `pnpm -r typecheck && pnpm -r lint && pnpm -r build`

- [x] **2D6 — Commit.** `Section MD-2D: map + calc-catalog validation`.
