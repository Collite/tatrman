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

### T3.5.4 · CSV→PG load delivery + multi-output PG island + terminal Arrow export — **PARTIAL**

**Design settled (adbc, not psql):** correct Arrow export from PG needs the query and its session-local temp tables on **one ADBC connection** — `psql -f` can't. So a decomposed PG island is a **`python3` + `adbc_driver_postgresql` script** (run.sh already dispatches python3 islands); compute still runs server-side in PG (a genuine second engine). Same-engine `accounts` handoff = acc_prep's fragment SQL inlined as `CREATE TEMP TABLE accounts AS <sql>` (no cross-session staging). CSV → typed `pyarrow.csv.read_csv` + `adbc_ingest` (COPY). Each OUT port = one `execute`+`fetch_arrow_table`+Arrow-IPC write.

- [x] `PgAdbcIslandEmitter` — emits the runtime script (SQL temps + CSV temps + per-output Arrow export). **Verified offline**: `PgAdbcIslandEmitterTest` feeds the *real* crunch per-output SQL (via `emitOutputs`) through it, goldens `pg_adbc/hero_crunch.py`, and **`py_compile`s it** (python3 3.13 present locally + CI).
- [x] **`BundleAssembler` integration** (`PgIslandScript` + the island-shape routing in `BundleAssembler`): a decomposed PG island → the `PgAdbcIslandEmitter` script as `islands/crunch.py` (invocation `python3`, authoritative over the resolver's `psql` default); acc_prep's fragment SQL inlined as the `accounts` temp (same-engine, no transfer); CSV Loads → typed `adbc_ingest` temps; OUT ports → `out/`/`staging/` Arrow sinks. Verified live.
- [x] **`sales_2026.csv` fixture** provided (`ttrp-conform`/`ttrp-cli` test resources); `HeroConformLiveTest` provisions it into each bundle's `files/`.

### ⚠ Live-run findings — ALL RESOLVED 2026-07-07 (each was a real bug the always-skipped live test had hidden)

1. **Join-key type mismatch — FIXED.** Seed `accounts.account_id` was `integer` vs CSV `customer` `string` → `account_id = customer` failed both engines. Seeded `account_id` as `text` (matches the T3.5.1 declared string schema); the emitted SQL is type-agnostic (column refs only) so it runs int-or-text at runtime.
2. **Empty join — FIXED.** Re-seeded so `branch_code` shares the region domain (`north`/`south`); the join now yields matching rows (`north` total 150000.5, `south` 50000). A meaningful, non-vacuous proof.
3. **Result-schema fidelity — FIXED.** Two sub-bugs: (a) the ADBC PG driver **corrupts `decimal128` on ingest** (30000.50 → 35000.0) and returns PG `numeric` as an opaque Arrow extension — resolved by making `sales_csv.amount` `float` (Arrow-native `double`, cross-engine-consistent; Q9-4 float-with-tolerance); (b) Polars writes `string_view` (default) / `large_string` (oldest), Postgres/pyarrow write `utf8` — resolved by emitting Polars sinks at `compat_level=oldest()` (Arrow-Java-readable) **and** normalizing all UTF-8 string encodings to one logical type in `ArrowIo`/the fingerprint.
   Plus a fourth latent bug: the transfer wrote `staging/stage.arrow` but the consumer read `staging/<port>.arrow` — fixed by naming the staged file after the destination boundary port.

### T3.5.5 · Target-override build API + `PlacementVariants` variant B

- [x] **DONE 2026-07-07:** `TtrpPipeline.plan(source, fileName, targetOverrides)` — a `Map<containerLabel, engineInstance>` applied to the built graph **before** normalize, so T8 auto-lowers Branch→Filter and movement re-synthesizes. Verified by `HeroCrunchSqlEmitTest` (overriding `crunch`→`erp_pg` yields a relational container, no `Branch`, SQL-emittable).
- [x] **DONE 2026-07-07:** `PlacementVariants` builds `authored` (empty override) + `crunch-pg` (`crunch`→`erp_pg` via `targetOverrides`) — genuinely-different bundles (variant A: PG fragment + Arrow transfer + Polars island; variant B: single PG adbc island, no transfer). Not by editing the hero source.
  - **Verified:** `./gradlew :packages:kotlin:ttrp-cli:test` green; the live proof runs both.

### T3.5.6 · Live A4 proof + phase DONE — ✅ DONE 2026-07-07

- [x] `HeroConformLiveTest` (moved to **ttrp-cli** — it needs `PlacementVariants` there + `ConformRunner` from ttrp-conform; ttrp-conform can't depend on ttrp-cli): builds both variants, provisions the CSV, runs `ConformRunner` against live PG, asserts `exitCode == 0` (all seven points agree). Gated `TTRP_CONFORM_PG=1`, else a visible skip.
- [x] CI (`conformance-ttrp.yml`) runs `:ttrp-conform:test :ttrp-cli:test` with the docker PG service + seed + `polars`/`adbc-driver-postgresql`/`pyarrow`.
- [x] Flipped `tasks-overview.md` Phase-3 DONE; A4-core claim recorded in `progress-phase-03.md`.
  - **Verified locally** against a Rancher-Desktop `postgres:16` on `localhost:55432`: `TTRP_CONFORM_PG=1 TTR_CONN_ERP_PG=… ./gradlew :packages:kotlin:ttrp-cli:test --tests "*HeroConformLiveTest"` → `tests=1 skipped=0 failures=0`. Result row both engines: `region=north, total=150000.5, avg_amt=75000.25`.

## Definition of DONE (stage) — ✅ ALL MET 2026-07-07

- [x] `accounts` boundary schema declared in the `dev` world; container IN-port schema resolution implemented; no fingerprint golden broke.
- [x] `SqlGraphEmitter` walks a decomposed relational island → CTE-per-node SQL with propagated schemas; `SqlIslandEmitter.emitOutputs` no longer throws for decomposed islands.
- [x] Post-join dedup matches Polars `right_on`; the crunch's SQL and Polars emits agree on output schema.
- [x] CSV→PG delivery (adbc `python3` island) + multi-output PG island + terminal Arrow export in the bundle; execution-model decisions documented.
- [x] Target-override API + `PlacementVariants` variant B build a genuinely-different PG-heavy placement.
- [x] `HeroConformLiveTest` green across both variants (live PG); **A4 core holds — one program, two engines, identical results**.

## Blockers

_(none — stage complete)_

## References

- `progress-phase-03.md` §"Deferrals — the full A4 two-engine proof is GATED" (the corrected gating list — items 2–7 map to T3.5.1–T3.5.6).
- `tasks-p3-s3.4-conformance.md` T3.4.4 (`PlacementVariants` variant B intent), §9 invoker contract.
- `packages/kotlin/ttrp-emit/.../polars/PolarsGraphEmitter.kt` (the walker to mirror), `.../polars/PolarsIslandEmitter.kt` (equi-key extraction to reuse), `hero_crunch.py` golden (the parity target).
- Decisions: D (declared staging), B-T5 (3VL / NULL), E-b (CTE-per-node), Q9 (seven points), A4/A5 (hero, identical results).
