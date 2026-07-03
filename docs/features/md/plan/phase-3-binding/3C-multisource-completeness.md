# Stage 3C — Multi-source, writeback completeness, md2er structural-only

Goal: the cross-binding rules — multi-source cubelets (several defs, same `cubelet:`), writeback
completeness, and the deliberately thin `md2er_cubelet` (structural-only). Closes Phase 3 with an
end-to-end integration round-trip.

Prereq: Stage 3B merged & green. TDD: 3C1 before 3C2–3C4.

References (verified):
- Rules: [`../../contracts.md`](../../contracts.md) §4.1 (multi-source), §4.4 (`md2er_cubelet`),
  §6.6 (completeness). Design rationale: [`../../design.md`](../../design.md) §6.5.
- Integration harness: `tests/integration/src/` (the `PassThrough` pattern; `areas-lsp.test.ts` is a
  recent example).
- Codes: contracts §7 (`md/multisource-grain-mismatch`, `md/md2er-physical-prop`; completeness
  reuses grain/measure coverage codes).

---

- [x] **3C1 — Tests first (red).**
  - multi-source: two `md2db_cubelet` defs for the same `cubelet:` whose attribute bindings union to
    the full grain and bind disjoint measure subsets → clean; defs that disagree on grain →
    `md/multisource-grain-mismatch`.
  - writeback completeness: a cubelet binding with `journaling` that leaves a measure or grain
    attribute unbound → completeness error; fully-bound → clean.
  - `md2er_cubelet`: structural `{cubelet, entity, attributes}` → clean; one carrying
    `shape`/`journaling`/`measures` → `md/md2er-physical-prop`.
  - Confirm red.

- [x] **3C2 — Multi-source validator.** Group `md2db_cubelet` defs by `cubelet:`; assert the union
  of attribute bindings covers the grain and the per-def grains agree (`md/multisource-grain-mismatch`);
  detect conflicting measure bindings across defs.

- [x] **3C3 — Writeback completeness.** For any binding implying writeback (journaling present, from
  3B), require every measure and every grain attribute bound (directly or via map). md→er is
  read-oriented — no writeback requirement there.

- [x] **3C4 — `md2er_cubelet` structural-only.** Validate `{cubelet, entity, attributes}`; reject
  any physical prop (`shape`/`journaling`/`measures`) with `md/md2er-physical-prop`. Resolve
  attribute→ER-attribute targets.

- [x] **3C5 — Integration round-trip.** In `tests/integration/src/md-binding.test.ts`: open a
  project with a logical model **and** `schema binding` files (a wide cubelet, a long cubelet, a
  bound domain + source, a table-backed map + case-table); assert zero diagnostics on the clean set
  and the expected `md/*` codes on seeded errors.

- [x] **3C6 — Verify (Phase 3 DONE).**
  - 3C1 unit + 3C5 integration pass; every binding `md/*` code has a triggering + clean fixture.
  - `pnpm --filter @modeler/semantics test && pnpm --filter @modeler/integration-tests test`
  - `pnpm -r typecheck && pnpm -r lint && pnpm -r build && pnpm -r test`

- [x] **3C7 — Commit.** `Section MD-3C: multi-source + completeness + md2er structural binding`.
