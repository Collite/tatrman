# Stage 3A — Bound-domain & table-map bindings

Goal: validate `md2db_domain` (where a `kind: bound` domain's members come from) and `md2db_map`
(the case-table backing a table-backed map), and close the Phase-2 `kind: bound` → source hook.

Prereq: Phase 2 DONE. TDD: 3A1 before 3A2–3A4.

References (verified):
- Binding validator placement: extend the MD validator module from Phase 2 (`md-validators.ts`) or
  add `md-binding-validators.ts` invoked under `schema binding`. The `er2db_*` validators are the
  analogue.
- Rules: [`../../contracts.md`](../../contracts.md) §4.2 (`md2db_domain`), §4.3 (`md2db_map`), §3.1
  (domain `kind: bound` ⇒ source required).
- Codes: contracts §7 (`md/source-on-unbound-domain`, `md/bound-domain-no-source`,
  `md/binding-on-calc-map`, `md/map-columns-incomplete`).

---

- [ ] **3A1 — Tests first (red).**
  - `md2db_domain` targeting a `kind: bound` domain with `{table, column}` → clean; targeting a
    non-`bound` (scalar/calc) domain → `md/source-on-unbound-domain`.
  - A `kind: bound` domain **with** a matching `md2db_domain` → clean; **without** →
    `md/bound-domain-no-source` (the Phase-2 hook now fires).
  - `md2db_map` targeting a table-backed map with `columns` covering all `from`/`to` domains →
    clean; targeting a **calc** map → `md/binding-on-calc-map`; missing a from/to column →
    `md/map-columns-incomplete`.
  - Confirm red.

- [ ] **3A2 — `md2db_domain` validator.** Resolve `domain` ref; assert it is `kind: bound`; require
  `source {table, column}`. Register that this domain now has a source (feeds 3A3).

- [ ] **3A3 — Close the bound-domain loop.** Wire the Phase-2 hook: after collecting all
  `md2db_domain` defs, any `kind: bound` domain without a source → `md/bound-domain-no-source`.

- [ ] **3A4 — `md2db_map` validator.** Resolve `map` ref; assert it is **table-backed** (no
  `calc:`) → else `md/binding-on-calc-map`; assert `columns` covers every `from`/`to` domain per the
  map's cardinality → else `md/map-columns-incomplete`. Escalate the Phase-2
  `md/table-map-no-binding` warning to satisfied/cleared when a matching `md2db_map` exists.

- [ ] **3A5 — Verify.**
  - 3A1 tests pass. `pnpm --filter @modeler/semantics test`
  - `pnpm -r typecheck && pnpm -r lint && pnpm -r build`

- [ ] **3A6 — Commit.** `Section MD-3A: md2db_domain + md2db_map validators`.
