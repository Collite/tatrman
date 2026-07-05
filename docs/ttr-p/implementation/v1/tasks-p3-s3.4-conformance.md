# Tasks · P3 · Stage 3.4 — Conformance (`ttrp-conform`, seven-point comparison, CI gate)

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

`ttrp-conform` (S3) implements the contracts §9 invoker contract — **reads `manifest.json` → provisions `TTR_CONN_*` → runs `run.sh` per engine placement variant → collects `out/` + staged Arrow → compares under the Q9 seven-point procedure** — and proves A4's core: the hero, compiled in PG-heavy and Polars-heavy placement variants, produces **identical results**. Wired into CI as the standing **emit regression gate** with a dockerized Postgres service container. **This stage owns the ONLY live-engine execution in Phase 3** — comparator and invoker logic are still unit-tested offline against Arrow fixtures; the dockerized-PG run lives in the CI job (locally reproducible behind an env-var gate). **DONE bar:** `ttrp conform hero.ttrp` exits 0 across both placement variants; the CI job `ttrp-conform` is green on PR; phase DONE bar (A4 core: one program, two engines, identical results) holds.

## Pre-flight (all must pass before T3.4.1)

- [ ] Stage 3.3 DONE: `./gradlew :packages:kotlin:ttrp-cli:test` → BUILD SUCCESSFUL, and `ttrp build hero.ttrp` assembles the hero bundle offline (T3.3.6 green).
- [ ] `./gradlew :packages:kotlin:ttrp-conform:build` → BUILD SUCCESSFUL (Phase 0 skeleton).
- [ ] Local runtime roster for the gated live test: `docker --version` OK; `python3 -c "import polars, adbc_driver_postgresql"` OK (matches the executor manifest's package list). Record versions in the progress doc. If docker is unavailable locally, the live path is CI-only — that is acceptable, note it.
- [ ] Re-read `../../architecture/contracts.md` §9 and `../../design/07-emit-options.md` §E-e (Q9 items 1–7) — normative for T3.4.2.

## Tasks

### T3.4.1 · Arrow IPC reading in Kotlin (TEST-FIRST)

- [ ] Add Apache Arrow Java to the version catalog (`gradle/libs.versions.toml`): `arrow-vector = { module = "org.apache.arrow:arrow-vector", version.ref = "arrow-java" }`, `arrow-memory-netty = { module = "org.apache.arrow:arrow-memory-netty", version.ref = "arrow-java" }` (current 18.x line; netty allocator needed at runtime); `implementation` both in `ttrp-conform`. JVM flag note: Arrow Java needs `--add-opens=java.base/java.nio=ALL-UNNAMED` on JDK 17+ — add to `tasks.test { jvmArgs(...) }` and the conform CLI launcher.
- [ ] Write `ArrowIoTest.kt` FIRST against tiny committed fixture files under `packages/kotlin/ttrp-conform/src/test/resources/arrow/` (generate them once with a throwaway polars script; commit the .arrow files): read schema + rows of a 2-column file; null handling; decimal + `timestamp[us, UTC]` columns.
- [ ] Implement `org.tatrman.ttrp.conform.ArrowIo`: `readTable(path: Path): ConformTable` (schema + row-major cell access sufficient for comparison — no need to expose vectors upstream). Core pattern (Arrow Java `ArrowFileReader`, IPC random-access format — what `polars.write_ipc` produces):
  ```kotlin
  RootAllocator().use { allocator ->
      FileInputStream(file).use { fis ->
          ArrowFileReader(fis.channel, allocator).use { reader ->
              val schema = reader.vectorSchemaRoot.schema
              for (block in reader.recordBlocks) {
                  reader.loadRecordBatch(block)
                  val root: VectorSchemaRoot = reader.vectorSchemaRoot
                  // copy cells out per field vector; root is reused per batch
              }
          }
      }
  }
  ```
  - **Verify:** `./gradlew :packages:kotlin:ttrp-conform:test --tests "org.tatrman.ttrp.conform.ArrowIoTest"` → green.

### T3.4.2 · Seven-point comparator (Q9 items 1–7) — table-driven unit tests, offline

- [ ] Implement `org.tatrman.ttrp.conform.SevenPointComparator` with one method/verdict per point, each with positive + negative unit tests over hand-built `ConformTable` fixtures (no engines involved):
  1. **Schema fingerprint (Q9-1):** canonical serialization of the Arrow schema (field name, type, nullability; nested order preserved) → sha256; must equal on both sides AND equal the bundle's `schemas/*.json` fingerprint for staged boundaries.
  2. **Row multiset (Q9-2):** order-insensitive by default — canonical-sort both sides by ALL columns (codepoint order for strings, point 6) and compare streams; **order-sensitive on the sorted-column prefix only when the island's terminal node is Sort** (terminal-Sort flag comes from the manifest/explain data, parameter of the comparator, not sniffed).
  3. **NULLS LAST (Q9-3):** under terminal Sort, verify nulls actually sort last in both outputs (a runtime check that the emitted `NULLS LAST`/`nulls_last=True` held).
  4. **Numerics (Q9-4):** decimal = exact (scale-aware `BigDecimal.compareTo`); float64 = within **declared** tolerance from the per-program conformance annotation; **no declared tolerance ⇒ exact, and exact-failure fails the run** (P2: no silent epsilon). Diagnostic on failure names column, row-sample, delta.
  5. **Datetime (Q9-5):** both sides `timestamp[us]` UTC; any non-µs or non-UTC arrival = schema-point failure (enforcement was emit's job — conform only verifies).
  6. **Collation (Q9-6):** string compare/sort = binary UTF-8 codepoint; no locale anywhere (assert no `Collator` usage; comparator sorts by `String.compareTo` on codepoints / byte arrays).
  7. **Delivery (Q9-7):** both sides read as Arrow IPC via T3.4.1 — the comparator ONLY accepts Arrow inputs (types make this structural).
- [ ] Verdict type `ConformReport`: per-point pass/fail + human summary + machine JSON (for CI artifact upload).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-conform:test --tests "*SevenPointComparatorTest"` → green (≥ 2 tests per point).

### T3.4.3 · Invoker — manifest-driven bundle execution + collection

- [ ] Implement per contracts §9, offline-testable with a **stub bundle** (fixture bundle whose run.sh writes canned Arrow files — no engines):
  - `org.tatrman.ttrp.conform.ManifestReader`: strict-parse `manifest.json` (reuse `RunManifest` from Stage 3.3 — depend on the module, don't duplicate); **verify the world fingerprint** against the invoker's own resolution of the world doc (F-f-ii: the artifact records, the capable invoker VERIFIES — `ttrp-conform` is a capable invoker); mismatch ⇒ report + exit 2 semantics.
  - `org.tatrman.ttrp.conform.BundleInvoker`: provisions `TTR_CONN_*` env (from the conform run's own config — CLI flags/env passthrough, never from the bundle), executes `bash run.sh` via `ProcessBuilder` in the bundle dir, captures exit code + `logs/`; collects `out/*.arrow` + `staging/*.arrow` paths afterward.
  - `org.tatrman.ttrp.conform.ConformRunner`: orchestrates N placement variants → invoke each → pair up `out/` displays by name (+ staged boundaries where schemas declare them) → `SevenPointComparator` → aggregate `ConformReport`; exit 0 all-pass / 1 comparison failure / 2 invocation-or-preflight failure (mirrors the bundle exit contract).
- [ ] Unit tests with the stub bundle: happy path; run.sh exit 1 propagates as invocation failure; missing `TTR_CONN_*` (exit 2 from run.sh) reported distinctly; fingerprint-mismatch path.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-conform:test --tests "*ConformRunnerTest"` → green — still zero live engines.

### T3.4.4 · Hero placement variants (PG↔Polars) — the live test, gated

- [ ] Placement-variant production: `org.tatrman.ttrp.conform.PlacementVariants` — from the hero source, produce variant A (authored: accounts@PG, sales+crunch@Polars) and variant B (crunch container retargeted to the PG engine — legal per the PG capability manifest; retarget via a programmatic world/target override in the build API, NOT by editing the hero source). Each variant builds its own bundle through `ttrp build` machinery; islands/waves/transfers differ, results must not.
- [ ] Live test `HeroConformLiveTest` (Kotest, `enabled = System.getenv("TTRP_CONFORM_PG") == "1"` with visible skip reason): requires `TTR_CONN_ERP_PG` pointing at a Postgres with the hero seed data (seed SQL fixture: `packages/kotlin/ttrp-conform/src/test/resources/seed/hero_seed.sql` — accounts + sales rows incl. NULL keys, negative amounts for the error path, decimal money, UTC-µs timestamps); runs `ConformRunner` over variants A + B; asserts all seven points pass AND the hero's rejects output is identical across variants (the error path is part of A4).
  - **Verify (local, optional):** `docker run -d --name ttrp-pg -e POSTGRES_PASSWORD=ttrp -p 5432:5432 postgres:16` + seed + `TTRP_CONFORM_PG=1 TTR_CONN_ERP_PG=postgresql://postgres:ttrp@localhost:5432/postgres ./gradlew :packages:kotlin:ttrp-conform:test --tests "*HeroConformLiveTest"` → green. **This is the phase's only sanctioned live execution; if docker is unavailable locally, defer proof to T3.4.5's CI run.**

### T3.4.5 · CI wiring — dockerized PG service container (the emit regression gate)

- [ ] Add `.github/workflows/conformance-ttrp.yml` (skeleton — adapt runner/versions to the repo's existing CI conventions in `.github/workflows/`):
  ```yaml
  name: ttrp-conform
  on:
    pull_request:
      paths: ["packages/kotlin/**", "packages/grammar/**", "docs/ttr-p/architecture/contracts.md"]
  jobs:
    conform:
      runs-on: ubuntu-latest
      services:
        postgres:
          image: postgres:16
          env: { POSTGRES_PASSWORD: ttrp, POSTGRES_DB: ttrp }
          ports: ["5432:5432"]
          options: >-
            --health-cmd "pg_isready -U postgres"
            --health-interval 5s --health-timeout 5s --health-retries 10
      steps:
        - uses: actions/checkout@v4
        - uses: actions/setup-java@v4
          with: { distribution: temurin, java-version: "21" }
        - uses: actions/setup-python@v5
          with: { python-version: "3.13" }
        - run: pip install "polars>=1.0" adbc-driver-postgresql pyarrow
        - run: psql "postgresql://postgres:ttrp@localhost:5432/ttrp" -f packages/kotlin/ttrp-conform/src/test/resources/seed/hero_seed.sql
        - run: ./gradlew :packages:kotlin:ttrp-conform:test
          env:
            TTRP_CONFORM_PG: "1"
            TTR_CONN_ERP_PG: postgresql://postgres:ttrp@localhost:5432/ttrp
        - uses: actions/upload-artifact@v4
          if: failure()
          with: { name: conform-report, path: "**/build/conform-report*.json" }
  ```
  Python package list must mirror the executor-type manifest (F-c: interpreter + packages are manifest-declared — keep the two in sync, add a comment cross-referencing the manifest file).
- [ ] Emit-regression role: the job runs the FULL `ttrp-conform` suite (offline comparator tests + live hero), making it the standing gate for any emit change (Q9-7: doubles as the emit regression suite and the standalone-vs-Kantheon drift guard once plan.v1 consumers exist).
  - **Verify:** push a branch touching `packages/kotlin/ttrp-emit/**`; the `ttrp-conform` workflow triggers and is green on the PR.

### T3.4.6 · `ttrp conform` CLI + phase close-out

- [ ] Replace Stage 3.3's `ConformCommand` stub: `ttrp conform <file>.ttrp [--variant <engine-placement>...] [--tolerance <col>=<eps>...]` → builds variants (default: the registered placement-variant set), runs `ConformRunner`, prints the per-point report, exits 0/1/2. `--tolerance` feeds Q9-4's declared float64 tolerance (per-program annotation surface until the language-level annotation lands).
- [ ] CLI test with the stub bundle path (offline); `--help` documents the exit contract.
- [ ] Phase DONE check against plan.md: `ttrp build` + `ttrp run` + `ttrp conform` all exercised on the hero; record the A4-core claim ("one program, two engines, identical results — canonical authoring") in `progress-phase-03.md` with the exact commands + CI run link for the `/review` cycle to verify.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-conform:test :packages:kotlin:ttrp-cli:test` → BUILD SUCCESSFUL; CI conformance job green on the phase-closing PR.

## Definition of DONE (stage)

- [ ] Invoker contract implemented per contracts §9: manifest read (strict) → fingerprint VERIFIED (capable-invoker half of F-f-ii) → `TTR_CONN_*` provisioned → run.sh per variant → `out/` + staged Arrow collected → seven-point comparison.
- [ ] All seven Q9 points unit-tested offline (≥2 tests each) — schema fingerprint, multiset/canonical-sort with terminal-Sort mode, NULLS LAST, decimal-exact/declared-float-tolerance (no silent epsilon), UTC-µs, binary collation, Arrow-IPC-only delivery.
- [ ] Hero PG↔Polars placement variants produce identical results including the rejects path — live-verified in CI with dockerized PG (the phase's only live execution).
- [ ] `.github/workflows/conformance-ttrp.yml` green and wired as the emit regression gate.
- [ ] `ttrp conform` subcommand functional with exit contract 0/1/2.
- [ ] Phase 3 DONE bar recorded in `progress-phase-03.md` (claims; `/review` verifies).

## Blockers

_(empty)_

## References

- `../../architecture/contracts.md` §9 (conformance — NORMATIVE), §5 (bundle/manifest/exit contract).
- `../../design/07-emit-options.md` §E-e — Q9 seven-point procedure (items 1–7, verbatim source).
- `../../design/08-orchestration-options.md` — F-c (env creds), F-f-ii (record/verify fingerprint split).
- `../../design/00-control-room.md` — S3 (`ttrp-conform`), Q9 entry, F-f entry.
- Arrow Java (verified 2026-07): `org.apache.arrow:arrow-vector` + `arrow-memory-netty`; `ArrowFileReader(channel, allocator)` → `recordBlocks` / `loadRecordBatch` / `vectorSchemaRoot`; `--add-opens=java.base/java.nio=ALL-UNNAMED` on JDK 17+.
- CLAUDE.md — Kotest conventions, CI/publishing conventions, `/review` cadence.
