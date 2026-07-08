# S4-A — canonical path → plan.v1 (ttr-translator)

Goal: lower resolved read paths to `plan.v1` relational nodes per the contracts §8 table, via the
model's `md2db_*` bindings. No new node kinds (MDS5).

Prereq: S3-B; MD Layer A phase 3 (bindings) merged; TTR-P translator (Phase 3) merged.
TDD: S4-A1–A2 (red) before S4-A3–A6.

## Tasks

- [ ] **S4-A1 — binding fixtures.** Extend the sales-model testFixtures with `md2db_*` bindings:
  `sales` → **wide** fact table (`f_sales`: customer_name, sale_date, net, gross), `plan` →
  **long** table (`f_plan`: customer_name, month, measure_code, value); dimension backing tables
  for hops (`d_customer` with name/code/region, `customer_address` with zip + valid_from) — reuse
  the MD feature's binding fixture shapes if phase-3 fixtures exist (check
  `docs/features/md/plan/phase-3-binding/` task docs; do not fork, extend).
- [ ] **S4-A2 — red lowering goldens.** Translator spec pattern (existing plan.v1 golden
  mechanism in ttr-translator): one golden per contracts §8 row — pinned → Filter EQ;
  set → IN; range → BETWEEN; star → group-by key; `viaCalc` (month from date) → the catalog
  entry's SQL expression pre-applied; measure+agg → Aggregate; **long-shape** → measure-code
  pre-Filter before Aggregate; hop (`Kaufland.zip`) → Join `f_sales ⋈ d_customer ⋈
  customer_address` along the map-backing tables; scalar shape → aggregate-all (no group-by);
  vector shape → group-by on the free dim's bound column.
- [ ] **S4-A3 — implement the lowering visitor.** `org.tatrman.ttr.translator.md.MdPathLowering`:
  `CanonicalPath + shape + bindings → RelNode` via the existing translation core's `RelBuilder`
  usage (scan → filter → project → aggregate ordering; see the Calcite RelBuilder patterns
  already in ttr-translator, and `~/Dev/view-only/calcite` + its `graphify-out/` for
  `RelBuilder.aggregate(groupKey, aggCall)` / `filter` / `join` specifics).
- [ ] **S4-A4 — wide/long shape handling** per the binding's declared shape (brief §Mapping);
  long: measure-code filter + value column as the measure operand; multi-source cubelets (same
  attributes, several tables) → Union before Aggregate (only if phase-3 bindings support it —
  else record as deferred here and in the plan changelog).
- [ ] **S4-A4b — journaling read view (R31).** Extend the fixtures with journal-mode variants of
  the `plan` binding (invalidate with a valid column; diff) and add red goldens first: invalidate
  → Load wrapped in Filter on the valid role (flag; and the temporal `valid_from ≤ asof <
  valid_to` variant); diff → Aggregate SUM per grain key over the journal rows; overwrite →
  plain Load (golden unchanged). Then implement the wrap at the single point where cubelet Loads
  are emitted — every other §8 row must compose on top of the wrapped Load unchanged (the
  existing goldens prove it). Note: technical-column *roles* land in S5C; until then the fixture
  declares the valid column directly on the binding (as the brief's `valid` column) — S5C
  migrates the fixture, goldens must not change.
- [ ] **S4-A5 — asof substitution** (compile-time literal into the calc expressions that need it)
  + deferred-member coordinates (R13) lower to Filters over the **literal** with a bind-time
  existence guard node/flag per the translator's existing parameter mechanism — document the
  chosen representation here.
- [ ] **S4-A6 — green + gates.** Goldens green;
  `./gradlew :packages:kotlin:ttr-translator:test` + Kotlin gate; plan.v1 proto round-trip test
  (paths survive serialize/deserialize). Commit `md-sugar S4A: read lowering to plan.v1`.

## Coder notes

_(empty — A4 multi-source status + A5 representation land here)_

## References

- Contracts §8 (normative table) · MD feature `contracts.md` §7 (binding shapes) ·
  `docs/ttr-translator/` (translation core, plan.v1 ownership TR-3/S25, lockstep tags).
- Calcite: prefer `RelBuilder`; no direct RelNode construction (matches existing core style).
