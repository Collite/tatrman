# Stage 3B — Cubelet binding: shapes, columns, journaling

Goal: validate a single `md2db_cubelet` def — grain coverage, shape↔measure-form agreement,
map-mediated attribute bindings, and journaling shapes.

Prereq: Stage 3A merged & green. TDD: 3B1 before 3B2–3B4.

References (verified):
- Rules: [`../../contracts.md`](../../contracts.md) §4.1 (`md2db_cubelet`), §6.6 (binding
  completeness). AST: contracts §2 (`Md2DbCubeletDef`, `ShapeSpec`, `AttrColumnBinding`,
  `MeasureColumnBinding`, `JournalingSpec`).
- Single-def scope here; cross-def rules (multi-source) are 3C.
- Codes: contracts §7 (`md/shape-measure-mismatch`, plus grain-coverage reuse of
  `md/grain-ref-unknown`).

---

- [x] **3B1 — Tests first (red).**
  - **wide**: `shape: wide`, each measure bound to a column → clean; a measure bound with a long
    `{code}` form under `wide` → `md/shape-measure-mismatch`.
  - **long**: `shape: { long: { codeColumn, valueColumn } }`, measures bound by `{code}` → clean; a
    measure bound to a bare column under `long` → `md/shape-measure-mismatch`.
  - **map-mediated attribute**: `attributes: { CostCenter.code: { via: md.…, from: { table, column } } }`
    resolves the `via` map and the source table/column → clean; unresolved `via` →
    `md/unknown-ref` (Phase 2 code reused).
  - grain coverage: `attributes` keys must cover the cubelet's `grain`; a missing grain attribute →
    `md/grain-ref-unknown` (or a dedicated coverage diagnostic if contracts is amended).
  - journaling: `overwrite`/`diff`/`{ invalidate: { validColumn } }` parse and validate; an
    `invalidate` without `validColumn` → error.
  - Confirm red.

- [x] **3B2 — Shape ↔ measure-form check.** Validate every `MeasureColumnBinding` matches `ShapeSpec`:
  wide ⇒ `{column}`; long ⇒ `{code}` with the `codeColumn`/`valueColumn` declared on the shape.
  Emit `md/shape-measure-mismatch`.

- [x] **3B3 — Attribute bindings + grain coverage.** Resolve each attribute binding (plain
  `{column}` or map-mediated `{via, from}`); assert the bound attribute set covers the cubelet's
  `grain` (using the Phase-2 cubelet grain). Resolve `via` maps and `from {table, column}`.

- [x] **3B4 — Journaling validator.** Validate the three modes; `invalidate` requires `validColumn`.
  Record whether this binding implies **writeback** (any journaling present) for the 3C completeness
  check.

- [x] **3B5 — Verify.**
  - 3B1 tests pass. `pnpm --filter @modeler/semantics test`
  - `pnpm -r typecheck && pnpm -r lint && pnpm -r build`

- [x] **3B6 — Commit.** `Section MD-3B: md2db_cubelet shape/column/journaling validators`.
