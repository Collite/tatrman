# S1-B — defaults, calc catalog, TS parity

Goal: complete the Kotlin MD semantics subset — default measure/agg accessors and the MD calc
catalog at its reserved seat (D-h / T5-c-β) — and prove Kotlin ≡ TS with golden parity fixtures.

Prereq: S1-A. TDD: S1-B1–B2 (red) before S1-B3–B4.

## Tasks

- [ ] **S1-B1 — red DefaultsSpec.** `…/semantics/md/DefaultsSpec.kt` over the S1-A1 fixture:
  `sales.defaultMeasure == net`; `net.defaultAgg == SUM`; a measure without an explicit default
  agg falls back per the MD feature contracts (check `docs/features/md/contracts.md` §measures —
  cite the rule id in the test name); `zip`'s time-based "latest valid" default = MAX (brief
  §Usage example).
- [ ] **S1-B2 — red CalcCatalogSpec.** Lookup by token: `month`, `year`, `lastMonth` resolve to
  catalog entries with their domain signatures (`date → month` etc.); unknown token → null;
  `MD_CATALOG_VERSION` exposed and equal to the TS `@tatrman/md-catalog` version (the cross-repo
  sync key) — this is the **drift-guard test**: it reads the version out of the vendored source
  of truth (S1-B4) and fails loudly on mismatch instructions ("regenerate via S1-B4 script").
- [ ] **S1-B3 — implement defaults.** Accessors on `MdModel`/`MdCubelet`/`MdMeasure` per B1.
- [ ] **S1-B4 — vendor the calc catalog.** Generation script
  `packages/kotlin/ttr-semantics/scripts/generate-md-catalog.main.kts` reading
  `packages/md-catalog/src/` (the TS data-only source of truth) and emitting
  `org/tatrman/ttr/semantics/md/MdCalcCatalog.kt` (entries + `MD_CATALOG_VERSION`). Committed
  generated file + regeneration documented in the script header (same pattern as the ai-platform
  vendoring, MD feature plan Phase 4). Wire the entries as `CatalogEntry`s at the ttrp-frontend
  `CompositeCatalog` seat — **registration only in S3**, here just implement the `CatalogEntry`
  adapter so the seat's KDoc note can point at it.
- [ ] **S1-B5 — TS golden export + parity spec.** Add a small export test to
  `packages/semantics` (TS) dumping, for the shared S1-A1 fixture: lattice edges, leaves,
  defaults, map-sugar resolutions as sorted canonical JSON →
  `…/testFixtures/resources/fixtures/md/sales-model/goldens/semantics-parity.json` (committed).
  Kotlin `ParitySpec.kt` computes the same JSON from `MdModel` and byte-compares.
- [ ] **S1-B6 — green + both gates** (`pnpm -r test` includes the TS export test;
  `./gradlew build`). Commit `md-sugar S1B: defaults + calc catalog + TS parity`.

## Coder notes

_(empty)_

## References

- `packages/md-catalog/` (TS source of truth; `MD_CALC_CATALOG`, `MD_CATALOG_VERSION`) ·
  `docs/features/md/map-catalog.md` (entry semantics, timezone/asof caveats §"Timezone-aware").
- Reserved seat: `docs/ttr-p/implementation/v1/tasks-p1-s1.2-expressions.md` T1.2.5
  (`CompositeCatalog`, T5-c-β KDoc note).
- R12 consumers arrive in S2-C; only lookup + adapter here.
