# Tasks · P3 · Stage 3.5 — SQL crunch emit + live A4 variant-B proof

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Why this stage exists

Stage 3.4 landed the conformance harness but the phase DONE bar — **A4 core: one program, two engines, identical results** — was gated on "SQL Join emit." That emit landed as a fast-follow (2026-07-07: `$L`/`$R` join-condition resolution + `coalesce` in the translator; `join_inner.sql`/`join_left_outer.sql` goldens green). Investigating the actual PG-heavy variant-B build then showed the join was **necessary but not sufficient** — the hero's `crunch` container, retargeted whole to `erp_pg`, transitively needs six more things. This stage delivers them. The corrected gating analysis lives in `progress-phase-03.md` §"Deferrals — the full A4 two-engine proof is GATED".

**Decision already taken (Bora, 2026-07-07):** the opaque `accounts` boundary schema is resolved by **declaring a staging-boundary schema in the shared `dev` world** (the deferred D "declared staging schema" mechanism), NOT by pulling P6 fragment-interior inference forward, and NOT by editing the hero source.

## Stage deliverable

`ttrp conform hero.ttrp` produces **two genuinely-different engine placements** — variant A (authored: `accounts`@PG fragment, `sales`+`crunch`@Polars) and variant B (`crunch` retargeted to `erp_pg`) — that pass the Q9 seven-point comparison on the dockerized-PG CI job. **DONE bar:** the `HeroConformLiveTest` (gated `TTRP_CONFORM_PG=1`) is green in CI across both variants; `tasks-overview.md` Phase-3 DONE checkbox flips.

## Pre-flight (all must pass before T3.5.1)

- [ ] `./gradlew :packages:kotlin:ttrp-emit:test :packages:kotlin:ttr-translator:test` → green (the join-emit + `coalesce` fast-follow baseline).
- [ ] `git grep -n 'JoinType.INNER -> PbJoinType.INNER' packages/kotlin/ttrp-emit` → present (join lowering is in place).
- [ ] Confirm the shared world fixture is `packages/kotlin/ttr-metadata/src/testFixtures/resources/fixtures/erp-project/models/acme/worlds/world.ttrm` and identify every golden that fingerprints the world (`WorldFingerprint`) — those regenerate in T3.5.1.

## Tasks

### T3.5.1 · Declare the `accounts` staging-boundary schema in the `dev` world — ✅ DONE 2026-07-07

The PG crunch reads its `accounts` IN port; SQL needs static columns. Declared them in the world's staging storage; resolved by matching the container IN-port name (explicit, P2-legal — the world author names the boundary; no content-sniffing).

- [x] Added to `erp-project/.../world.ttrm` `def storage stage { … }`: `def schema accounts { account_id: string, branch_code: string, region: string }`. Types chosen for join validity (equi-keys compare against `sales_csv` strings; `region` — the surviving group key — is string). Reasoning documented inline.
- [x] Resolution implemented in `SqlGraphEmitter.stagingSchema`/`schemaCols` (world-declared schema matched by container IN-port name; mirrors `PolarsGraphEmitter`'s storage-schema lookup). A missing boundary schema raises a structured emit diagnostic.
- [x] No world-fingerprint golden broke — the fingerprint tests are shape/sensitivity-only and `HeroBundleTest` checks only the `sha256:` prefix; no test asserts a literal fingerprint. `ttr-metadata`/`ttrp-graph`/`ttrp-cli`/`ttrp-emit` suites green.
  - **Verified:** the resolver is exercised end-to-end by `HeroCrunchSqlEmitTest` (the `accounts` IN port types the join's left side).

### T3.5.2 · `SqlGraphEmitter` — decomposed relational island walk + schema propagation — ✅ DONE 2026-07-07

- [x] `org.tatrman.ttrp.emit.sql.SqlGraphEmitter(graph, world)` implemented: topo-orders transform members; resolves inputs (Load → CSV temp table via world storage schema; container IN port → declared staging schema; upstream → CTE); propagates output columns (Load/staged declared, Filter passthrough, Join `right_on` dedup, Aggregate keys+aggs, Project). Produces one `CtePlanner` `EmitNode` plan per (non-`rejects`) OUT port. Session-local relations use the CTE-namespace sentinel so they render bare (`FROM sales_2026`).
- [x] Verified against the **real** hero (stronger than the planned synthetic fixture): `HeroCrunchSqlEmitTest` retargets `crunch`→`erp_pg` via the T3.5.5 override, walks it, and goldens the per-output SQL (`hero_crunch_result.sql`, `hero_crunch_low.sql`). Exercises Branch→Filter (T8), `coalesce` complement, dedup, staging schema, aggregate propagation — all through the published translator.
- [x] `SqlIslandEmitter.emitOutputs(island, graph)` — the bundle-facing seam: fragment islands emit verbatim; decomposed islands route each (non-`rejects`) OUT port through `SqlGraphEmitter`→`CtePlanner`. The old "not emittable in v1" throw is gone. Verified by `HeroCrunchSqlEmitTest` ("…routes the decomposed island…").
- [ ] **Remaining (→ T3.5.4):** `BundleAssembler` must consume `emitOutputs` and write per-output island files + the CSV→PG preamble + terminal Arrow export.
  - **Verified:** `./gradlew :packages:kotlin:ttrp-emit:test` → green (incl. `HeroCrunchSqlEmitTest`).

### T3.5.3 · Post-join column dedup matching Polars `right_on` semantics — ✅ DONE 2026-07-07

- [x] Implemented in `PlanNodeBuilder.join` + shared `JoinDedup` (so emit + schema propagation can't drift). An equi-join emits a positional Project over the join dropping the right-side keys; Calcite uniquifies duplicates so ordinal refs are unambiguous and RelToSql re-qualifies (`accounts.region`). Falls back to a bare join for non-equi/cross/null conditions.
- [x] Cross-checked against `hero_crunch.py`: the SQL join yields `account_id, branch_code, region(=accounts), amount` — identical surviving columns + group key. The standalone `join_inner.sql`/`join_left_outer.sql` goldens regenerated to show the dedup.
  - **Verified:** `./gradlew :packages:kotlin:ttrp-emit:test` → green.

### T3.5.4 · CSV→PG load delivery + multi-output PG island + terminal Arrow export — **REMAINING**

- [ ] CSV→PG: the crunch PG island's `sales` Load delivers via `\copy` into a `CREATE TEMP TABLE sales(…)` built from the declared `sales_csv` schema; the island SQL references `sales`. Emit the DDL+`\copy` as a per-island preamble in the bundle (recorded execution-model decision — document in `progress-phase-03.md`). `run.sh` provisions the CSV path (Stage 3.3 CSV-path deviation).
- [ ] Multi-output: the crunch emits `result` (b.true filter) and `low` (b.false filter) as **two terminal SELECTs** over the shared CTEs. Extend the island→file model to N `.sql` outputs per PG island (e.g. `islands/crunch__result.sql`, `islands/crunch__low.sql`); `rejects` stays skipped (open producer semantics).
- [ ] Terminal Arrow export: each PG island output is ADBC-read to its sink (`out/main_result.arrow`, `staging/low.arrow`) — mirror the existing `TransferScriptEmitter` PG→Arrow path, one per output.
  - **Verify:** `ttrp build` on a crunch@PG fixture assembles the bundle; `bash -n run.sh` clean; the SQL files `psql -f`-parse (offline `EXPLAIN`-only check where possible).

### T3.5.5 · Target-override build API + `PlacementVariants` variant B

- [x] **DONE 2026-07-07:** `TtrpPipeline.plan(source, fileName, targetOverrides)` — a `Map<containerLabel, engineInstance>` applied to the built graph **before** normalize, so T8 auto-lowers Branch→Filter and movement re-synthesizes. Verified by `HeroCrunchSqlEmitTest` (overriding `crunch`→`erp_pg` yields a relational container, no `Branch`, SQL-emittable).
- [ ] **Remaining:** wire `PlacementVariants` variant B to retarget `crunch`→`erp_pg` via the override (NOT by editing hero source) and build a genuinely-different bundle (islands/waves/transfers differ from variant A; results must not). Depends on T3.5.4 (the bundle must emit the multi-output PG island).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-cli:test` → green; the two variant bundles differ in island engines but share the world fingerprint.

### T3.5.6 · Live A4 proof + phase DONE

- [ ] Un-skip `HeroConformLiveTest` (gated `TTRP_CONFORM_PG=1`): build both variants, run under the seven-point comparator against the dockerized PG, assert identical results.
- [ ] CI: the `ttrp-conform` job runs the live variant-A-vs-variant-B comparison (dockerized PG service container).
- [ ] Flip `tasks-overview.md` Phase-3 DONE checkbox; record the A4-core claim + exact commands + CI link in `progress-phase-03.md` for `/review`.
  - **Verify:** `TTRP_CONFORM_PG=1 ./gradlew :packages:kotlin:ttrp-conform:test` (locally or CI) → green across both variants.

## Definition of DONE (stage)

- [ ] `accounts` boundary schema declared in the `dev` world; container IN-port schema resolution implemented; world-fingerprint goldens regenerated + reviewed.
- [ ] `SqlGraphEmitter` walks a decomposed relational island → CTE-per-node SQL with propagated schemas; `SqlIslandEmitter` no longer throws for decomposed islands.
- [ ] Post-join dedup matches Polars `right_on`; the crunch's SQL and Polars emits agree on output schema.
- [ ] CSV→PG delivery + multi-output PG island + terminal Arrow export in the bundle; execution-model decisions documented.
- [ ] Target-override API + `PlacementVariants` variant B build a genuinely-different PG-heavy placement.
- [ ] `HeroConformLiveTest` green in CI across both variants; **A4 core holds — one program, two engines, identical results**.

## Blockers

_(empty)_

## References

- `progress-phase-03.md` §"Deferrals — the full A4 two-engine proof is GATED" (the corrected gating list — items 2–7 map to T3.5.1–T3.5.6).
- `tasks-p3-s3.4-conformance.md` T3.4.4 (`PlacementVariants` variant B intent), §9 invoker contract.
- `packages/kotlin/ttrp-emit/.../polars/PolarsGraphEmitter.kt` (the walker to mirror), `.../polars/PolarsIslandEmitter.kt` (equi-key extraction to reuse), `hero_crunch.py` golden (the parity target).
- Decisions: D (declared staging), B-T5 (3VL / NULL), E-b (CTE-per-node), Q9 (seven points), A4/A5 (hero, identical results).
