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

## Stage 3.3 — Bundle + executor (`<program>.bundle/`, run.sh, `ttrp` CLI) — **code-complete**

Lands in `ttrp-cli` (package `org.tatrman.ttrp.bundle`), which now depends on `ttrp-emit`.

### What shipped
- `bundle/RunManifest` — contracts §5 `@Serializable` data classes; pretty, stable-key-order, **strict** decode (unknown keys rejected); round-trip + shape tests. `manifest.json` excluded from `files{}` (can't self-hash — flagged for a contracts §5 clarification).
- `bundle/WorldFingerprint` — semantic hash (F-f-ii β) of the *resolved* world (mini-spec of record in the KDoc): sorted engines/executors/storages, capability manifests as sorted key→value (`PropertyValue` rendered source-immune), storage staging/hosts/schemas; credentials-free. Determinism + semantic-sensitivity tested.
- `bundle/RunShGenerator` — pure `manifest + bindings → run.sh`: `#!/usr/bin/env bash` + `set -euo pipefail`, bash-version + `TTR_CONN_*` pre-flight (exit 2), wipe-on-restart (F-e), per-wave `&`+pid launch with a `wait -n` early-abort loop (sibling kill, `FAILED island=…`, exit 1), F-c invocations (`psql`/`python3`), display notices, `exit 0`. Content-asserted + `bash -n`.
- `bundle/BundleAssembler` — drives `TtrpPipeline.plan` → emits island payloads (SQL fragment verbatim / Polars via `PolarsGraphEmitter`), transfer scripts, `schemas/*.json`, `manifest.json`, `run.sh` (chmod +x); sha256 per file + `files{}`; deterministic. **Verified: `ttrp build hero.ttrp` produces the full 3-wave hero bundle** (acc_prep psql → transfer → crunch python3), manifest exactly per §5, hashes re-verify, `bash -n` clean.
- `cli/Main.kt` — the **clikt** `ttrp` command: `build` / `run` (propagates the child exit code verbatim) / `explain` / `check` / `conform` (stub → distinct exit 3 until 3.4). `application` launcher (`installDist`) prints the subcommand roster. Legacy `TtrpCli` object retained for the P1/P2 CLI specs.

### Deviations & deferrals
1. **Cross-engine execution model** (recorded for review): a PG→Polars transfer reads the source SQL island's result **directly via ADBC** — the transfer embeds the island SQL as a subquery (`SELECT * FROM (<island-sql>) AS _ttrp_src`). The `islands/*.sql` file is retained for provenance/psql inspection; consequently the psql wave-step for a transfer-sourced fragment island re-materializes rather than persists (functionally correct for A4; a leaner model — psql `CREATE TABLE AS` + table-read transfer — is a follow-up).
2. **`schemas/*.json`** carry a placeholder Arrow-schema (`fields: []` + a zero fingerprint) — the real per-boundary Arrow schema derivation lands with Stage 3.4's Arrow reader (the comparator needs it too). Structure/tree is contract-correct.
3. **`plans/*.pb`** gating: hero world (bash executor) ⇒ no `plans/` (correct); a Kantheon-target `.pb` write is not exercised (no such v1 hero) — gating logic present, `.pb` emission deferred (R9 `.pb`-only scope).

## Stage 3.4 — Conformance (`ttrp-conform`, seven-point, CI gate) — **harness complete; live A4 proof gated**

### What shipped
- `conform/ArrowIo` — reads Arrow IPC *file* format (Arrow Java 18.x, `--add-opens` wired) → `ConformTable` (schema + normalized row-major cells: Long/Double/BigDecimal/String/Boolean/epoch-µs). Tested against **committed pyarrow-generated `.arrow` fixtures**.
- `conform/SevenPointComparator` — all Q9 points 1–7: schema fingerprint, row multiset (canonical-sort; order-sensitive sort-prefix under terminal Sort), NULLS LAST, numerics (decimal exact via `BigDecimal.compareTo`; declared float64 tolerance, **no silent epsilon**), UTC-µs datetime, binary codepoint collation, Arrow-only delivery. ≥2 table-driven tests per point.
- `conform/ConformRunner` + `BundleInvoker` + `ManifestReader` — invoker contract (contracts §9): provision `TTR_CONN_*` (from the conform run, never the bundle) → `bash run.sh` → collect `out/` Arrow → pair displays → seven-point → aggregate; exit 0/1/2. Tested over **stub bundles** (run.sh writes canned Arrow) — happy path, run-exit-1→2, missing-conn→2, comparison-fail→1.
- `bundle/PlacementVariants` + `ttrp conform <file> [--tolerance col=eps]` (real command; exit 0/1/2). `--tolerance` feeds Q9-4.
- `.github/workflows/conformance-ttrp.yml` — dockerized-PG service, executor-manifest-mirrored pip list, seed load; runs the `ttrp-conform` suite as the standing **emit regression gate**.
- `resources/seed/hero_seed.sql` — accounts + sales with the A4 error-path shapes (NULL keys, negative amounts, decimal money, UTC-µs).
- **Architecture:** `RunManifest` moved down to `ttrp-emit` (shared bundle contract) to break the `ttrp-cli ↔ ttrp-conform` dependency cycle; `ttrp-cli` now depends on `ttrp-conform` for the conform command.

### Deferrals — the full A4 two-engine proof is GATED (read before review)
1. **PG↔Polars placement-variant identical-results (A4 core for the hero) is blocked by SQL Join emit** (Stage 3.1 deferral). The hero crunch contains a join, so a PG-heavy variant B is **not emittable** until SQL Join emit lands (`plan.v1` JoinType stops at FULL + equi-join column resolution through the translator). `PlacementVariants` therefore builds deterministic rebuilds of the authored placement — enough to exercise the full invoke→collect→seven-point harness, not two genuinely-different engine placements.
2. **`HeroConformLiveTest`** is gated (`TTRP_CONFORM_PG=1`) and currently a documented **skip** — beyond (1), the authored-variant live run needs runtime CSV-path resolution wiring (Stage 3.3 deviation #1/#2). The offline `ttrp-conform` suite is the real, green regression gate today.

## Phase 3 status

**Emit → bundle → run is complete and green offline for the hero**: `ttrp build hero.ttrp` assembles the full 3-wave bundle (PG fragment island → ADBC/Arrow transfer → Polars crunch), `ttrp run`/`ttrp conform` are wired, and the conformance harness (Arrow IO + seven-point + invoker) is fully tested. **The one open item before the phase DONE bar ("one program, two engines, identical results") can be signed off is SQL Join emit** — it gates the hero's PG↔Polars placement variance and the live A4 comparison. Recommend `/review` here to decide whether SQL Join emit lands in this phase or as a fast-follow (it is self-contained and off every other v1 path).

## Verification (run by the coder; reviewer re-runs)
- `./gradlew build` — **BUILD SUCCESSFUL** repo-wide (all modules compile, all tests pass, ktlint clean).
- `./gradlew :packages:kotlin:ttrp-emit:test :packages:kotlin:ttrp-cli:test :packages:kotlin:ttrp-conform:test` — green.
- `ttrp build`/`run`/`explain`/`check`/`conform` wired (clikt); `installDist` launcher lists the roster; `ttrp build hero.ttrp` produces the verified bundle (manifest §5, hashes re-verify, `bash -n` clean).
- Arrow round-trip against committed pyarrow fixtures; seven-point comparator + invoker suites green.

## Verification (run by the coder; reviewer re-runs)
- `./gradlew :packages:kotlin:ttrp-emit:test` — green (8 SQL goldens + hero fragment + boundary + diagnostics + SsaNames property + Calcite-boundary hygiene).
- `./gradlew :packages:kotlin:ttrp-emit:ktlintCheck` — clean.
- `./gradlew :packages:kotlin:ttrp-graph:test :packages:kotlin:ttrp-cli:test :packages:kotlin:ttrp-frontend:test` — green (TtrpPipeline `plan()` refactor; explain goldens byte-stable).
- External gate: `./gradlew :packages:kotlin:ttrp-emit:dependencies --configuration compileClasspath | grep ttr-translator` → resolved project dep.
