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

## Stage 3.2 — Polars emit (straight-line scripts, prelude, transfers) — **code-complete**

### What shipped
- `polars/PolarsExprRenderer` — TTR-P `Expression` IR → Polars expression strings (`pl.col`, native operators, `.is_null()`, `pl.when().then()`, `.cast(...)`, catalogue functions). 3VL needs no wrapping (Polars propagates nulls SQL-style natively — documented).
- `polars/PolarsIslandEmitter` — straight-line script: `import polars as pl` + generated prelude + `# --- island ---` + one statement per node (E-c γ). Node roster: Load (CSV declared-schema / staged Arrow), Filter, Project, Aggregate (`group_by().agg()`, distinct → `n_unique`), Sort (`nulls_last=True` always, Q9-3), Union (`pl.concat`), **Join incl. native semi/anti** (equi-key extraction from the ON conjunction), Limit, Store/Display sinks.
- `polars/PreludeGenerator` — needs-analysis emits **only** the helpers referenced (decimal / UTC-µs), sorted, dependency-free (Q9 items 4–6); empty when unneeded.
- `transfer/TransferScriptEmitter` — generated ADBC transfer scripts, Arrow-IPC staging (F-c-i β): pg→staging (`read_database_uri(..., engine="adbc")`) and staging→pg (`write_database(..., engine="adbc")`); creds only via `TTR_CONN_*` env (F-c-ii α). Diagnostics: `TTRP-MOV-001/002`, and the **Q8 egress tripwire `TTRP-RLS-001`** (warn/error per `[ttrp] rls-egress`).
- `polars/PolarsGraphEmitter` — walks a normalized Polars container → step list (topo order, IN ports → staged reads, member Loads → world-schema CSV, OUT ports → Display/Store sinks).
- **Golden corpus**: 7 Polars island goldens + `hero_crunch.py` (the A4 crunch, emitted from the real pipeline) + 2 transfer goldens. `PolarsScriptHygieneTest` runs `python3 -m py_compile` on every golden (all valid Python) + a determinism check.

### Deviations & deferrals
1. **Hero rejects flow deferred**: the crunch's `rejects` OUT port (`j#1.rejects` → store, C3-f erroneous rows) is **not** emitted — the erroneous-rows *producer* semantics are an open v1.x design item (plan.md cross-cutting register: "Erroneous-rows-in-SQL producer semantics"). `PolarsGraphEmitter` skips rejects-mapped OUT ports; `HeroPolarsEmitTest` asserts the omission is conscious. All other hero outputs (result→display, low→store) emit.
2. **CSV Load path** rendered as `<storage>/<object>.csv` (derived); the storage's absolute `path` (world manifest `PropertyValue`) is resolved at bundle time (Stage 3.3). Schema (dtypes) *is* resolved from the world (`sales_csv` → `pl.Decimal`, etc.) — schema-on-read, no inference (T7).
3. **Prelude helpers coexist with inline casts**: expression `Cast`s render inline (`.cast(pl.Decimal(...))`); the prelude helper is emitted as the E-c boundary utility when a decimal/datetime cast is present. For v1 Polars islands the prelude is typically empty (read_csv schema + Arrow-preserved types enforce inline).

## Stage 3.3 — Bundle + executor — _not started_
## Stage 3.4 — Conformance — _not started_

## Verification (run by the coder; reviewer re-runs)
- `./gradlew :packages:kotlin:ttrp-emit:test` — green (8 SQL goldens + hero fragment + boundary + diagnostics + SsaNames property + Calcite-boundary hygiene).
- `./gradlew :packages:kotlin:ttrp-emit:ktlintCheck` — clean.
- `./gradlew :packages:kotlin:ttrp-graph:test :packages:kotlin:ttrp-cli:test :packages:kotlin:ttrp-frontend:test` — green (TtrpPipeline `plan()` refactor; explain goldens byte-stable).
- External gate: `./gradlew :packages:kotlin:ttrp-emit:dependencies --configuration compileClasspath | grep ttr-translator` → resolved project dep.
