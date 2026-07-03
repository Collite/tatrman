# Stage 4C — RAE end-to-end fixtures

Goal: build a handful of realistic end-to-end MD models from the RAE examples and run them through
parse → resolve → validate (logical + binding) as conformance + integration targets. These prove
the design against the real cost-allocation cases it was derived from.

Prereq: Phases 1–3 DONE. May run in parallel with 4B. TDD: the fixtures **are** the tests.

References (verified):
- RAE source material: `docs/features/md/RAE/` — `priklady DS RO - mapovane.txt`,
  `priklady DS RO - trivial.txt`, `priklady DS operace.txt`, `gscript examples - CC Transactions.txt`,
  `gscript examples - CCDrivers.txt`, `gscript examples - AllocRule StdPrice.txt`,
  `gscript examples - XTab.txt`, and [`../../RAE/What are Maps.md`](../../RAE/What%20are%20Maps.md).
- Design targets called out in [`../../design.md`](../../design.md) §12.4.
- Integration harness: `tests/integration/src/`; conformance fixtures: `tests/conformance/fixtures/`.

---

- [x] **4C1 — `costCenterTransactions` (wide).** Model a cost-center transactions cubelet (grain =
  Account × CostCenter × Time; measures = amount) with a **wide** `md2db_cubelet` binding. Assert
  zero diagnostics; assert leaf/grain matches the intended grain.

- [x] **4C2 — `otherDrivers` (long).** Model the cost-center drivers cubelet bound to a **long**
  table (`codeColumn`/`valueColumn`, measures by `{code}` — e.g. FTEs, m²). Assert clean validation
  and correct shape handling.

- [x] **4C3 — `costCenterM2` (map-mediated store).** Model the building→cost-center case: an
  attribute reached **through a table-backed map** (`{ via, from: { table, column } }`) with a
  matching `md2db_map` case-table, and `invalidate` journaling. Assert the map-mediated binding and
  writeback completeness validate.

- [x] **4C4 — Calendar (calc maps).** A Time dimension + `calendar` hierarchy
  (`[Day, Month, Quarter, Year]`) realised by catalog calc maps (`truncToDay`, `monthOfDate`,
  `quarterOfMonth`, `yearOfDate`). Assert hierarchy inference picks the right calc maps with no
  ambiguity and the catalog type-checks pass.

- [x] **4C5 — Wire as targets.** Add the four as integration fixtures
  (`tests/integration/src/md-rae.test.ts`) and, where deterministic, as conformance fixtures. Each
  clean model → zero diagnostics; add one seeded-error variant per fixture asserting the expected
  `md/*` code.

- [x] **4C6 — Verify.**
  - `pnpm --filter @modeler/integration-tests test` green incl. the RAE fixtures.
  - `pnpm -r test` green.

- [x] **4C7 — Commit.** `Section MD-4C: RAE end-to-end MD fixtures`.
