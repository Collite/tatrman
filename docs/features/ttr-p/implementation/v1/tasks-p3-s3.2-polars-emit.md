# Tasks · P3 · Stage 3.2 — Polars emit (straight-line scripts, inline prelude, transfers)

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

`ttrp-emit` emits Polars islands as **straight-line Python scripts** — one statement per node, SSA names carried as variable names, mirroring the canonical text (E-c γ) — with a **generated inline prelude containing ONLY the enforcement helpers the program actually needs** (3VL NULL, decimal exactness, UTC-µs datetimes — Q9 items 4–6); scripts are **dependency-free** beyond `polars` itself (F-c: "dependency-free" = no TTR harness lib, not no polars). Cross-engine movement emits as **generated ADBC/connectorx transfer scripts staging through Arrow IPC** (F-c-i β). The Q8 egress tripwire fires here (`TTRP-RLS-001`). **DONE bar:** the hero's Polars island (sales prep + join/aggregate/branch crunch) and both hero transfers (pg→staging, staging→pg or →out) match committed goldens under `packages/kotlin/ttrp-emit/src/test/golden/{polars,transfers}/`; `./gradlew :packages:kotlin:ttrp-emit:test` green. No live Python/PG execution in this stage — goldens are text; execution proof is Stage 3.4's job.

## Pre-flight (all must pass before T3.2.1)

- [x] Stage 3.1 DONE bar: `./gradlew :packages:kotlin:ttrp-emit:test` → BUILD SUCCESSFUL (golden harness `GoldenSupport.kt`, `SsaNames`, `updateGolden` wiring all exist — this stage reuses them).
- [x] `./gradlew :packages:kotlin:ttrp-cli:run --args="explain <hero.ttrp>"` → island map shows the Polars island(s) and the synthesized Store+Transfer+Load movement pair around the PG↔Polars boundary (Stage 2.3 movement synthesis, C3-d-iv).
- [x] The bash executor-type manifest fixture (Stage 2.2) declares `python 3.13` + `polars` package for the polars×bash binding (F-c) — `grep -r "polars" packages/kotlin/ttrp-graph/src/test/resources/manifests/` (or the actual manifest home from Stage 2.2) → hit found.

## Tasks

### T3.2.1 · Golden Python corpus — cases committed FIRST (TEST-FIRST)

- [x] Create `packages/kotlin/ttrp-emit/src/test/kotlin/org/tatrman/ttrp/emit/polars/PolarsGoldenTest.kt` (Kotest `FunSpec`, reusing `GoldenSupport`) with these cases, all red initially. Goldens under `src/test/golden/polars/`:
  - `straight_line_ssa.py` — Load→Filter→Project chain ⇒ one statement per node, SSA variable names via the shared `SsaNames` mangling (identical names to what the same island would get as CTE names — one naming story).
  - `no_prelude_when_unneeded.py` — island with no decimal/datetime columns and no helpers required ⇒ **zero prelude lines**, script starts at `import polars as pl` (prelude minimality is an asserted property, not an accident).
  - `prelude_decimal_only.py` — decimal columns present ⇒ ONLY the decimal helper emitted.
  - `prelude_datetime_utc_us.py` — datetime columns ⇒ ONLY the UTC-µs helper emitted.
  - `sort_nulls_last.py` — Sort ⇒ `nulls_last=True` on every sort call (Q9-3 parity with SQL emit).
  - `join_semi_anti.py` — inner/semi/anti joins.
  - `aggregate_group_by.py` — Aggregate with distinct arm.
  - `hero_sales_prep.py` + `hero_crunch.py` — the hero's Polars islands. Expected SHAPE (illustrative):
    ```python
    import polars as pl
    # --- ttrp prelude (generated; only helpers this program needs) ---
    def _ttrp_dt_utc_us(df, cols):
        return df.with_columns([pl.col(c).cast(pl.Datetime("us", "UTC")) for c in cols])
    # --- island: crunch ---
    accounts = pl.read_ipc("staging/accounts.arrow")
    sales = pl.read_ipc("staging/sales.arrow")
    sales_2 = sales.filter((pl.col("amount") > 0) & pl.col("customer_id").is_not_null())
    j = accounts.join(sales_2, on="customer_id", how="inner")
    sums = j.group_by("region").agg(pl.col("amount").sum().alias("total"))
    sums_2 = sums.sort("total", descending=True, nulls_last=True)
    sums_2.write_ipc("out/main_result.arrow")
    ```
  - Transfer goldens under `src/test/golden/transfers/`: `pg_to_staging.py`, `staging_to_pg.py` (T3.2.4).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-emit:test --tests "org.tatrman.ttrp.emit.polars.PolarsGoldenTest"` → all red for the right reason (emitter absent). Commit (`Section 3.2: golden Polars corpus (red)`).

### T3.2.2 · `PolarsIslandEmitter` — straight-line node emit

- [x] Implement `org.tatrman.ttrp.emit.polars.PolarsIslandEmitter`: topo walk; one assignment statement per node; variable names from the shared `org.tatrman.ttrp.emit.core.SsaNames` (Stage 3.1). Node→Polars patterns (current Polars 1.x Python API — verified via docs; re-verify against the executor manifest's pinned `polars` version before changing):
  - Load (staged input) → `pl.read_ipc("staging/<edge>.arrow")`; Load (declared-schema CSV, D-c) → `pl.read_csv(path, schema={...})` with the declared schema rendered as Polars dtypes — never schema inference (T7 schema-on-read ban).
  - Filter → `df.filter(<expr>)`; Project/Calc → `df.select(...)` / `df.with_columns(...)`; Join → `df.join(other, on=/left_on=/right_on=, how="inner"|"semi"|"anti"|"left"|"full")`; Aggregate → `df.group_by(keys).agg([...])` (distinct arm via `.n_unique()`/`pl.col(x).unique().count()` — pick one, golden-pin it); Sort → `df.sort(by, descending=..., nulls_last=True)` **always** (Q9-3); Union → `pl.concat([a, b, c])`; Intersect/Except → capability-lowered join patterns (already rewritten by T8 — assert unreachable, same `TTRP-EMT-005` guard as SQL); Limit → `df.head(n)` (ordered-input already enforced upstream, S15); Store (staged output) → `df.write_ipc("staging/<edge>.arrow")`; Display → `df.write_ipc("out/<name>.arrow")` + `print(f"display <name>: out/<name>.arrow")` (F-c display binding).
  - Branch → two derived frames via complementary `.filter(...)` (T8 lowering may already have done this — follow the normalized graph, don't re-derive).
- [x] Expression rendering: the T5 expression IR → Polars expressions (`pl.col`, operators, catalogue function map). 3VL note: Polars comparison/boolean ops propagate nulls SQL-style natively; document in `PolarsExprRenderer` KDoc which operators are trusted native vs which get enforcement wrapping (the wrap list feeds T3.2.3's needs-analysis).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-emit:test --tests "org.tatrman.ttrp.emit.polars.PolarsGoldenTest"` → `straight_line_ssa`, `sort_nulls_last`, `join_semi_anti`, `aggregate_group_by` green.

### T3.2.3 · `PreludeGenerator` — needs-analysis + only-needed helpers (Q9 items 4–6)

- [x] Implement `org.tatrman.ttrp.emit.polars.PreludeGenerator`: scans the island's schemas + expression IR and emits **only** the helpers actually referenced. Helper roster (each a small pure-Python function, no imports beyond `polars`; dependency-free per E-c):
  - `_ttrp_decimal(df, col, precision, scale)` — cast to `pl.Decimal(precision, scale)`; **decimal stays exact end-to-end** (Q9-4); float64 never silently substituted.
  - `_ttrp_dt_utc_us(df, cols)` — cast to `pl.Datetime("us", "UTC")`; sub-µs truncation happens here at Load/Store boundaries, emitted not assumed (Q9-5).
  - `_ttrp_3vl_*` — only for the operators T3.2.2's audit flagged as diverging from SQL 3VL (expected: near-empty for Polars; the mechanism must exist because pandas-engine v1.x will need it — E-c).
- [x] Emitter contract: prelude block appears between `import polars as pl` and the `# --- island:` marker, with the fixed banner comment `# --- ttrp prelude (generated; only helpers this program needs) ---`; deterministic helper order (sorted by name).
- [x] Unit test `PreludeGeneratorTest`: needs-analysis table-driven — (no special types → empty), (decimal only → decimal helper only), (datetime only → dt helper only), (both → both, sorted).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-emit:test --tests "*PreludeGeneratorTest" --tests "*PolarsGoldenTest"` → prelude cases (`no_prelude_when_unneeded`, `prelude_decimal_only`, `prelude_datetime_utc_us`) green.

### T3.2.4 · `TransferScriptEmitter` — ADBC/connectorx movement, Arrow IPC staging (F-c-i β)

- [x] Implement `org.tatrman.ttrp.emit.transfer.TransferScriptEmitter` → one script per synthesized Transfer under `transfers/<edge>.py`. Patterns (current polars 1.x API, verified):
  - **pg → staging:** connection from env, query = a plain `SELECT` of the staged edge's columns from the Store target:
    ```python
    import os
    import polars as pl
    uri = os.environ["TTR_CONN_ERP_PG"]
    df = pl.read_database_uri("SELECT account_id, customer_id, region, balance FROM ttrp_staging.accounts", uri, engine="adbc")
    df = df.with_columns(pl.col("event_ts").cast(pl.Datetime("us", "UTC")))  # boundary enforcement, only when needed
    df.write_ipc("staging/accounts.arrow")
    ```
    `engine="adbc"` is the default choice (exact typed Arrow path); `engine="connectorx"` is the documented fallback knob if the ADBC PG driver proves unavailable in the executor environment — pick ONE for v1 goldens (lean: adbc) and record the choice in the emitter KDoc + executor-manifest package list (`adbc-driver-postgresql`).
  - **staging → pg:** `df = pl.read_ipc("staging/<edge>.arrow")` then `df.write_database(table_name="...", connection=os.environ["TTR_CONN_ERP_PG"], engine="adbc", if_table_exists="replace")`.
  - Boundary enforcement (Q9-4/5) is emitted **at the transfer boundary** where types cross engines — reuse `PreludeGenerator`'s needs-analysis.
- [x] Diagnostics: `TTRP-MOV-001` — no ADBC/connectorx binding derivable for the (storage, executor) pair; `TTRP-MOV-002` — transfer endpoint's storage has no named connection in the world (suggested alternative: name the connection in the world doc). Unit tests for both.
- [x] **Q8 egress tripwire:** emitting a Store or Transfer whose *source* storage declares `rls: true` raises `TTRP-RLS-001` ("data leaves an RLS-governed engine under the executing principal") — severity from `[ttrp] rls-egress = warn|error` (default warn, contracts §2). Test both severities + the non-RLS silent case.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-emit:test --tests "org.tatrman.ttrp.emit.transfer.*"` → green incl. `pg_to_staging.py` / `staging_to_pg.py` goldens.

### T3.2.5 · Hero islands + transfers green

- [x] `hero_sales_prep.py`, `hero_crunch.py`, and the hero's actual transfer set emitted from the real pipeline (front-half → graph → normalize → emit), matching goldens byte-for-byte. The hero's error path (`rejects` flow, F-d-i: data-shaped, crosses engines as normal synthesized movement) must appear in the emitted scripts/transfers — assert the rejects edge produces its own staged Arrow file.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-emit:test --tests "*PolarsGoldenTest"` → all green.

### T3.2.6 · Script hygiene + static sanity (no live execution)

- [x] Every emitted script is syntactically valid Python: add `PythonSyntaxTest` compiling each golden via the JVM-side check `python3 -m py_compile <file>` **if** `python3` is on PATH, else `ast`-less fallback = skip with a logged warning (CI has python3; the test must not silently pass locally — use Kotest `!enabled` semantics with a visible skip reason). No live PG, no live data — py_compile only.
- [x] Determinism: emitting the hero twice yields byte-identical scripts (property: emit is a pure function of graph + world). One repeat-emit test.
- [x] `./gradlew :packages:kotlin:ttrp-emit:ktlintCheck` clean.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-emit:test` → BUILD SUCCESSFUL (full module, Stages 3.1 + 3.2 suites together).

## Definition of DONE (stage)

- [x] All Polars + transfer goldens green; hero islands byte-identical to committed goldens.
- [x] Prelude minimality asserted by test (empty when unneeded; per-helper granularity) — Q9 items 4–6 enforced in generated code, dependency-free (E-c γ).
- [x] Every Sort emits `nulls_last=True`; SSA variable names identical to Stage 3.1's CTE names via shared `SsaNames` (Q7-γ/E-b naming story).
- [x] Transfers are generated ADBC scripts staging Arrow IPC only (F-c-i β); credentials only via `TTR_CONN_*` env reads (F-c-ii α); `TTRP-MOV-001/002` + `TTRP-RLS-001` tested.
- [x] No live engine touched anywhere in this stage's tests.
- [x] Progress recorded in `progress-phase-03.md`.

## Blockers

_(empty)_

## References

- `../../architecture/contracts.md` §6 (Polars emit, invocation bindings), §2 (`rls-egress`), §8 (diagnostics).
- `../../design/07-emit-options.md` — E-c γ (prelude), Q9 items 4–6, E-f/Q8 (tripwire).
- `../../design/08-orchestration-options.md` — F-c (binding table, F-c-i β Arrow staging, F-c-ii α env creds).
- `../../design/00-control-room.md` — E-c, F-c, Q8, Q9, S14, S17 entries.
- Polars Python API (verified 2026-07): `pl.read_ipc` / `write_ipc`, `pl.read_database_uri(query, uri, engine="adbc"|"connectorx")`, `DataFrame.write_database(..., engine="adbc")`, `pl.Datetime("us", "UTC")`, `pl.Decimal(precision, scale)`, `sort(..., nulls_last=True)`.
