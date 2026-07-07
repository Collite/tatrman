# Progress — Phase 3 (Emit, bundle, run → `ttrp build` + `ttrp run`)

> **Status:** in progress. `[x]` = intent — the reviewer verifies against runtime (CLAUDE.md cadence). Branch `feature/ttr-p-v1-phase3`. External gate held throughout: `org.tatrman:ttr-translator` consumed as the in-repo `:packages:kotlin:ttr-translator` project dep (published line `kotlin-translator/v0.9.1`); **zero vendored translator code**.

## Stage 3.1 — SQL emit (ttr-translator integration, CTE-per-node) — **code-complete**

### What shipped
- **`TtrpPipeline.plan(source, fileName): PlanResult`** — a new structured Phase-2 entry (normalized graph + `ExecutionGraph` + `BoundWorld` + rewrite log). `explain` now delegates to it; the byte-stable hero/er-hero explain goldens are unchanged (verified green).
- **`ttrp-emit` module** (`org.tatrman.ttrp.emit`):
  - `EmitDiagnostics` — `TTRP-EMT-001..006` + `TTRP-WLD-002`, and `TtrpEmitException` (structured id + node provenance + suggested-alternative). **`EMT` is a new contracts §8 area** (changelog entry queued below).
  - `sql/TranslatorFacade` — the ONLY class touching `org.tatrman.translator.*` / `plan.v1`. Wraps `Translator.unparseFromRelNode(PlanNode, Language.SQL, dialect)`; maps translator failure `code`s → `TTRP-EMT-00[1-4]`.
  - `sql/DialectRegistry` — engine type+version → proto `SqlDialect`; unknown → `TTRP-WLD-002`.
  - `sql/IslandModelHandle` — a `ModelHandle` over the island's base + CTE pseudo-tables (ER side empty; er→db already done by TTR-P).
  - `sql/PlanNodeBuilder` — mechanical TTR-P `Node`/`Expression` IR → `plan.v1` `PlanNode`/`Expression` (twin IRs, T5). Sugar → `TTRP-EMT-005`.
  - `core/SsaNames` — SSA-label → identifier mangling, **shared with Stage 3.2** (injectivity property-tested).
  - `sql/CtePlanner` — CTE-per-node assembly: transform nodes become CTEs, the terminal node is the outer query (so terminal Sort/Limit keep ordering), flat-trivial (≤1 transform node) → no `WITH` (E-b). Loads are base-table scans, not CTEs. CTE refs render bare via a sentinel-namespace strip.
  - `sql/SqlIslandEmitter` — public entry; fragment islands emit their interior **verbatim** (C2-f), decomposed relational islands route through `CtePlanner`.
- **Golden SQL corpus** (`src/test/golden/sql/postgres/`, 8 cases green): `trivial_island_flat_select`, `cte_chain_ssa_names`, `ssa_reassignment_mangling`, `sort_nulls_last`, `aggregate_group_by`, `aggregate_having`, `union_all`, `limit_over_sort`, plus `hero_accounts_prep` (fragment verbatim). Emitted by the real ttr-translator; clean, deterministic, double-quoted Postgres. Golden-update workflow wired (`-DupdateGolden=true` + `GoldenSupport` + README).
- Tests: `SqlGoldenTest`, `HeroSqlEmitTest` (real front-half→graph→emit pipeline), `TranslatorBoundaryTest`, `SsaNamesTest`, `EmitDiagnosticsTest`, `NoCalciteOutsideFacadeTest`. `./gradlew :packages:kotlin:ttrp-emit:test :packages:kotlin:ttrp-emit:ktlintCheck` → green.

### Deviations & deferrals (conscious, recorded — read before review)
1. **The translator hides Calcite entirely** (`Translator.unparseFromRelNode(PlanNode)`), so ttrp-emit builds `plan.v1` `PlanNode` trees and **never imports Calcite** — the task-list T3.1.1 "drive Calcite directly" rules (fresh planner, dialect `Context`, `RelBuilder.project(force)`, catch four Calcite exceptions) live *inside* the published translator, not in ttrp-emit. This is the overview **R9** path ("whichever granularity the API offers"). `NoCalciteOutsideFacadeTest` asserts the total boundary; the four Calcite exceptions arrive pre-mapped as translator failure `code`s. `TTRP-EMT-003` (SqlValidatorException) folds into `TTRP-EMT-002` because the translator collapses both validation exceptions to `validation_failed`.
2. **CTE-per-node realization**: Loads are base-table scans (not their own CTE, unlike the task's *illustrative* golden), the terminal node is the outer query (correctness: terminal Sort/Limit ordering), and flat-trivial is ≤1 transform node. This is a cleaner, idiomatic E-b than the illustrative shape; goldens pin the real translator output.
3. **The hero's `acc_prep` SQL island is an opaque `"""sql` fragment** (Phase-2 deviation #2 — fragments stay undecomposed until P6). So its SQL emit is the **verbatim interior** (C2-f), not a decomposed CTE chain. `hero_accounts_prep.sql` is the fragment text. The CtePlanner machinery is exercised by the synthetic corpus (real graph nodes + real expressions).
4. **SQL Join emit DEFERRED** (INNER/LEFT/RIGHT/FULL and SEMI/ANTI): (a) SEMI/ANTI have **no `plan.v1` wire representation** (`JoinType` stops at FULL) — and T8 lowers Intersect/Except to semi/anti, so those set-ops are deferred too; (b) even INNER join needs input-qualified column resolution the `plan.v1` `ColumnRef` → `RelBuilder.field` path does not yet thread. **No v1 hero SQL island contains a join** (the hero join is Polars-side), so this is off the A4 critical path. Joins are covered on the Polars side in Stage 3.2. `PlanNodeBuilder` raises `TTRP-EMT-006` for SEMI/ANTI/CROSS with a pointer here.
5. **NULLS LAST for ASC keys**: Calcite omits the explicit `NULLS LAST` on ascending keys because it is the Postgres ASC default (the emitted SQL *is* nulls-last). DESC keys show explicit `DESC NULLS LAST`. Conformance (Q9-3, Stage 3.4) verifies actual null placement regardless of annotation.

### Contracts changelog (queued — to land with the phase)
- **§8 area addition: `EMT`** (`TTRP-EMT-001..006`) — SQL/emit diagnostics. `TTRP-WLD-002` reused for DialectRegistry unknown-engine.

## Stage 3.2 — Polars emit — _not started_
## Stage 3.3 — Bundle + executor — _not started_
## Stage 3.4 — Conformance — _not started_

## Verification (run by the coder; reviewer re-runs)
- `./gradlew :packages:kotlin:ttrp-emit:test` — green (8 SQL goldens + hero fragment + boundary + diagnostics + SsaNames property + Calcite-boundary hygiene).
- `./gradlew :packages:kotlin:ttrp-emit:ktlintCheck` — clean.
- `./gradlew :packages:kotlin:ttrp-graph:test :packages:kotlin:ttrp-cli:test :packages:kotlin:ttrp-frontend:test` — green (TtrpPipeline `plan()` refactor; explain goldens byte-stable).
- External gate: `./gradlew :packages:kotlin:ttrp-emit:dependencies --configuration compileClasspath | grep ttr-translator` → resolved project dep.
