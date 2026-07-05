# Tasks · P3 · Stage 3.1 — SQL emit (ttr-translator integration, CTE-per-node)

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

The `ttrp-emit` module turns any normalized relational island (Phase 2 output) into Postgres-dialect SQL via `org.tatrman:ttr-translator`: **CTE-per-node, SSA names as CTE names, trivial islands flatten to a plain SELECT** (E-b), **every Sort emits explicit `NULLS LAST`** (Q9-3), er provenance untouched (already db-tier per E-d). Errors from the translator surface as structured `TTRP-EMT-*` diagnostics, never raw Calcite exceptions. **DONE bar:** the hero's PG island (`accounts` prep) emits SQL byte-identical to its committed golden; the full per-dialect golden corpus under `packages/kotlin/ttrp-emit/src/test/golden/sql/postgres/` is green via `./gradlew :packages:kotlin:ttrp-emit:test`; no Calcite class is referenced outside the ttr-translator boundary.

## ⛔ EXTERNAL GATE (phase pre-flight — read first)

**`org.tatrman:ttr-translator` MUST be resolvable before any task in this stage.** It is delivered by the kantheon-side **Proteus-extraction arc** (E-a α′; `plan.v1` proto vendored per S25). Maven Local is acceptable for iteration — the publisher runs, in the source repo, the CLAUDE.md pattern:

```bash
./gradlew -Pversion=0.0.1-LOCAL :<translator-module-path>:publishToMavenLocal
```

and `packages/kotlin/ttrp-emit/build.gradle.kts` repositories must include `mavenLocal()` (before the GitHub Packages repo) for `0.0.1-LOCAL` coordinates to resolve. **If the artifact does not resolve, STOP — record it under §Blockers. Do not stub, vendor, or re-implement the translator.**

## Pre-flight (all must pass before T3.1.1)

- [ ] `./gradlew :packages:kotlin:ttrp-emit:dependencies --configuration compileClasspath | grep "org.tatrman:ttr-translator"` → prints a resolved coordinate line (version visible, no `FAILED`). **This is the external gate check.**
- [ ] `./gradlew :packages:kotlin:ttrp-graph:test :packages:kotlin:ttrp-frontend:test` → BUILD SUCCESSFUL (Phase 2 DONE: normalized graph + islands available as library input).
- [ ] `./gradlew :packages:kotlin:ttrp-cli:run --args="explain <hero.ttrp path from the P1 corpus, expected packages/kotlin/ttrp-frontend/src/test/resources/corpus/hero.ttrp>"` → prints island/wave/movement structure incl. exactly one PG-targeted island (Stage 2.3 DONE bar).
- [ ] `./gradlew :packages:kotlin:ttrp-emit:build` → BUILD SUCCESSFUL (module skeleton from Phase 0 still green).

## Tasks

### T3.1.1 · Pin the translator boundary + version-aware dialect registry (TEST-FIRST)

The Calcite engagement rules below are **normative for all translator-boundary code** in this repo (source: the ai-platform reference implementation, `~/Dev/ai-platform` EXAMPLES.md §7, and the cloned Calcite tree at `~/Dev/view-only/calcite` — consult both when the API surface is ambiguous):

1. **A Calcite `Planner` is single-use per query stage** (state machine RESET→PARSED→VALIDATED→CONVERTED). Construct a fresh planner per island — never cache/reuse across islands.
2. **Never use dialect singletons** (`PostgresqlSqlDialect.DEFAULT` etc.) directly — always go through the version-aware registry (below), which constructs dialects from a `SqlDialect.Context` carrying the engine version from the world's engine manifest.
3. **`RelBuilder.project(nodes, aliases, force = true)`** — `force` preserves identity projects; without it RelBuilder silently elides them and CTE-per-node round-trips break (a Project node would vanish from the emitted shape).
4. **SQL emit call shape:** `RelToSqlConverter(dialect).visitRoot(rel).asStatement()`, then `sqlNode.toSqlString { it.withDialect(dialect) }`.
5. **Catch exactly the four exception types** — `SqlParseException`, `ValidationException`, `SqlValidatorException`, `RelConversionException` — and map to structured diagnostics (T3.1.6). Never let a raw Calcite exception escape `ttrp-emit`.
6. **Accept Calcite's NULLS-LAST CASE-expansion noise** where a dialect lacks native `NULLS LAST` (`CASE WHEN x IS NULL THEN 1 ELSE 0 END` sort keys). Postgres has native support, so PG goldens must show literal `NULLS LAST`; the acceptance rule is recorded for future dialects, not applied to PG.

- [ ] Write `packages/kotlin/ttrp-emit/src/test/kotlin/org/tatrman/ttrp/emit/sql/TranslatorBoundaryTest.kt` (Kotest `FunSpec`) FIRST: (a) translator resolves and translates a minimal one-node island fixture to non-empty SQL; (b) `DialectRegistry.forEngine("postgres", version = "16")` returns a dialect whose unparse of a `Sort` contains `NULLS LAST`; (c) `DialectRegistry` throws `TtrpEmitException(TTRP-WLD-002)` for an unknown engine/version pair.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-emit:test --tests "org.tatrman.ttrp.emit.sql.TranslatorBoundaryTest"` → fails (classes missing), for the right reason.
- [ ] Implement `org.tatrman.ttrp.emit.sql.DialectRegistry` (object; `forEngine(engineType: String, version: String): SqlDialect`-shaped, but **returning the translator's dialect handle type if ttr-translator wraps Calcite dialects** — inspect the published API first and record the actual signature in a KDoc note). No singleton constants anywhere.
- [ ] Implement `org.tatrman.ttrp.emit.sql.TranslatorFacade` — the ONLY class importing `org.tatrman.translator.*`; constructor takes the resolved world's engine entry; exposes `translateNode(...)`/`translateIsland(...)` per whatever granularity the published translator API offers. Fresh planner per island inside (rule 1).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-emit:test --tests "org.tatrman.ttrp.emit.sql.TranslatorBoundaryTest"` → BUILD SUCCESSFUL.

### T3.1.2 · Golden-snapshot harness + update workflow (TEST-FIRST)

- [ ] Implement `packages/kotlin/ttrp-emit/src/test/kotlin/org/tatrman/ttrp/emit/GoldenSupport.kt`: `assertMatchesGolden(actual: String, relPath: String)` — compares against `src/test/golden/<relPath>`; on mismatch fails with a unified diff; when JVM system property `updateGolden=true`, rewrites the golden file and fails the test with message `golden updated — re-run without -DupdateGolden` (updated goldens must be reviewed in the diff, never silently green).
- [ ] Wire the property through in `packages/kotlin/ttrp-emit/build.gradle.kts`: `tasks.test { systemProperty("updateGolden", System.getProperty("updateGolden") ?: "false") }`.
- [ ] Document the workflow in a README stub at `packages/kotlin/ttrp-emit/src/test/golden/README.md`: layout is **per-dialect** — `golden/sql/postgres/*.sql`, `golden/polars/*.py` (Stage 3.2), `golden/transfers/*.py` (Stage 3.2); update command: `./gradlew :packages:kotlin:ttrp-emit:test -DupdateGolden=true` then review `git diff`, then re-run clean.
  - **Verify:** a throwaway self-test writing `"x"` against a missing golden fails; with `-DupdateGolden=true` it creates the file; clean re-run passes. Then delete the throwaway. `git status` shows only intended files.

### T3.1.3 · Golden SQL corpus — cases + hero shape committed FIRST (TEST-FIRST)

- [ ] Create `SqlGoldenTest.kt` (Kotest `FunSpec`, one test per case) with these named cases, each building its island fixture from the Phase-2 graph API (not from hand-built RelNodes), all failing initially. Golden files under `src/test/golden/sql/postgres/`:
  - `trivial_island_flat_select.sql` — single Load→Filter island ⇒ **plain `SELECT … WHERE …`, no `WITH`** (E-b's deterministic flat-trivial rule; pin the rule in code as: island with ≤1 non-Load node emits flat).
  - `cte_chain_ssa_names.sql` — Load→Filter→Project chain with named edges ⇒ `WITH <ssa> AS (…), … SELECT`.
  - `ssa_reassignment_mangling.sql` — `accounts` reassigned ⇒ CTEs `accounts`, `accounts_2` (mangling rule in T3.1.4).
  - `sort_nulls_last.sql` — terminal Sort ⇒ literal `ORDER BY … NULLS LAST` (Q9-3; also with authored `desc`).
  - `aggregate_having.sql` — Aggregate + post-Filter (HAVING sugar already expanded by T8) ⇒ two CTEs, GROUP BY + WHERE-over-aggregate shape.
  - `join_semi_anti.sql` — inner + semi + anti Join types (B-T10).
  - `set_ops_union_intersect_except.sql` — `UNION ALL`/`INTERSECT`/`EXCEPT` per node config.
  - `limit_offset_over_sort.sql` — Limit over Sort (S15 ordering already enforced upstream).
  - `hero_accounts_prep.sql` — **the hero's PG island.** Expected SHAPE (illustrative, exact columns per the hero fixture):
    ```sql
    WITH accounts AS (
      SELECT account_id, customer_id, region, balance
      FROM erp.accounts
    ), accounts_2 AS (
      SELECT account_id, customer_id, region, balance
      FROM accounts
      WHERE balance > 0
    )
    SELECT account_id, customer_id, region, balance
    FROM accounts_2
    ```
    CTE-per-node; SSA names as CTE names; no ORDER BY unless the island's terminal node is Sort — and then with explicit `NULLS LAST`.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-emit:test --tests "org.tatrman.ttrp.emit.sql.SqlGoldenTest"` → all cases FAIL (emitter absent). Commit the test + empty/expected golden intent now (`Section 3.1: golden SQL corpus (red)`).

### T3.1.4 · `CtePlanner` — island walk, SSA→identifier mangling, CTE assembly

- [ ] Implement `org.tatrman.ttrp.emit.core.SsaNames`: deterministic SSA-label → SQL/Python identifier mangling, **shared with Stage 3.2** (one naming story: Q7-γ → E-b → ζ). Rule: first binding = the bare name (`accounts`); SSA reassignment *n* = `<name>_<n>` (`accounts_2`); anonymous edges = `_<nodekind>_<topoIndex>` (`_filter_3`); collision with an existing binding = deterministic suffix escalation + unit-tested. Property test (kotest-property): mangling is injective over any island's node set.
- [ ] Implement `org.tatrman.ttrp.emit.sql.CtePlanner`: topological walk of the island; per node, drive `TranslatorFacade` to produce that node's SELECT body **over its input CTE names** (inputs registered as named relations for the translator — per-node scope, rule 1); assemble `WITH a AS (…), b AS (…) SELECT * FROM <terminal>`; apply the flat-trivial rule from T3.1.3. If the published ttr-translator API already offers whole-island preserved-shape CTE unparse, use it and reduce `CtePlanner` to naming + flat-rule + assembly — record which path was taken in the class KDoc.
- [ ] Implement `org.tatrman.ttrp.emit.sql.SqlIslandEmitter` (public entry): `emit(island: Island, world: ResolvedWorld): SqlEmitResult` where `SqlEmitResult` carries text + per-node source-range map (provenance for P4 hover, E-d).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-emit:test --tests "org.tatrman.ttrp.emit.core.SsaNamesTest" --tests "org.tatrman.ttrp.emit.sql.SqlGoldenTest"` → `trivial_island_flat_select`, `cte_chain_ssa_names`, `ssa_reassignment_mangling` green; others may still be red.

### T3.1.5 · Full node coverage for the relational roster

- [ ] Cover every T10 node legal in a PG island post-normalize: Project (with `force = true`, rule 3 — identity projects must survive), Filter, Join (incl. semi/anti), Aggregate (incl. distinct `AggregateCall` arm), Sort (**always** injecting `NULLS LAST` unless authored placement present — implement as a normalization of the Sort node's collation before translation, not string patching), Union/Intersect/Except, Values, Limit, Load (→ table scan via world binding), Store (→ handled at bundle level; emit `INSERT INTO … SELECT` or `CREATE TABLE AS` per the Store node's config — pick one, document, golden-test it). Distinct/Select/Calc/HAVING never reach emit (T8 sugar — assert with a guard throwing `TTRP-EMT-005` internal-invariant diagnostic).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-emit:test --tests "org.tatrman.ttrp.emit.sql.SqlGoldenTest"` → all cases except `hero_accounts_prep` green.

### T3.1.6 · Structured diagnostics for translator failures

- [ ] Define in `org.tatrman.ttrp.emit.EmitDiagnostics`: `TTRP-EMT-001` (SqlParseException), `TTRP-EMT-002` (ValidationException), `TTRP-EMT-003` (SqlValidatorException), `TTRP-EMT-004` (RelConversionException), `TTRP-EMT-005` (internal invariant: sugar node reached emit), `TTRP-WLD-002` (unknown engine/version in DialectRegistry). Each carries: node source range (from the graph), island name, the translator message as `detail`, and a suggested-alternative field where meaningful (contracts §8). **Note:** `EMT` is a new area — append it to the contracts §8 area list with a changelog entry (contracts change discipline).
- [ ] Unit tests: force each of the four Calcite exceptions through `TranslatorFacade` (malformed fixture per type) and assert the mapped ID, that the message contains no Calcite class names, and that the source range points at the offending node.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-emit:test --tests "org.tatrman.ttrp.emit.EmitDiagnosticsTest"` → green.

### T3.1.7 · Hero island green + boundary hygiene

- [ ] `hero_accounts_prep.sql` golden green — emitted from the actual hero fixture through the full front-half + graph + emit pipeline (integration-shaped test living in `ttrp-emit`'s test source, consuming `ttrp-frontend`/`ttrp-graph` as test deps).
- [ ] Boundary check: add an ArchUnit-style or grep-based test (`NoCalciteOutsideFacadeTest`) asserting no file in `ttrp-emit/src/main` except `TranslatorFacade.kt` (and `DialectRegistry.kt` if the translator exposes raw Calcite dialects) imports `org.apache.calcite`.
- [ ] `./gradlew :packages:kotlin:ttrp-emit:ktlintCheck` clean; no `!!` on translator results.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-emit:test` → BUILD SUCCESSFUL, all goldens green, and `git status` shows committed goldens only.

## Definition of DONE (stage)

- [ ] External gate held throughout: `ttr-translator` consumed as a published/Maven-Local artifact — zero vendored translator code in this repo.
- [ ] `./gradlew :packages:kotlin:ttrp-emit:test` green including all 9 golden SQL cases; `hero_accounts_prep.sql` matches byte-for-byte.
- [ ] CTE-per-node + SSA-name + flat-trivial + NULLS LAST rules all golden-pinned (E-b, Q9-3).
- [ ] All translator failures surface as `TTRP-EMT-00[1-4]`; contracts §8 changelog entry for the EMT area landed.
- [ ] `SsaNames` shared module in place for Stage 3.2 reuse.
- [ ] Progress recorded in `docs/ttr-p/implementation/v1/progress-phase-03.md` (claims only — `/review` verifies).

## Blockers

_(empty)_

## References

- `../../architecture/contracts.md` §6 (emit), §8 (diagnostics) — normative.
- `../../architecture/architecture.md` §5 (emit & execution).
- `../../design/07-emit-options.md` — E-a α′ (world-driven PlanNode, concrete payloads), E-b (CTE-per-node), E-d (er provenance), Q9-3 (NULLS LAST).
- `../../design/00-control-room.md` — E-a/E-b/E-d/Q9/S25/H-3 decision entries.
- Calcite engagement reference: ai-platform repo `~/Dev/ai-platform` (EXAMPLES.md §7), cloned Calcite `~/Dev/view-only/calcite`.
- `PUBLISHING.md` + CLAUDE.md §Kotlin artifacts — Maven Local pattern for the external gate.
