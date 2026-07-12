# PL-P1 (②) — Seam client (tatrman repo, stages S1–S4)

> **[superseded 2026-07-10 · STRAT-3]** license is Apache-2.0 across the open surface; MIT mentions below are historical.

> Pre-flight: PL-P0 review done. DoD: [`../plan.md`](../plan.md) §PL-P1. Check each box the moment its task is done. All stages here are MIT toolchain work in `tatrman`. **Byte-determinism is the contract of S1–S3** — golden hashes are law (tracker rule 7).

## S1 · Snapshot archive + cache {#s1}

Verify: `./gradlew :packages:kotlin:ttr-snapshot:test` green; the golden-hash test passes on Linux AND macOS CI runners; `pnpm -r test` untouched.

- [ ] **T1 (tests first).** New module dir + `ArchiveDeterminismTest.kt` (FunSpec), cases: `"packing the fixture docs twice yields identical bytes"` · `"entry order is bytewise path order, mtime=0, uid=gid=0"` (assert by re-reading tar headers) · `"archive id equals sha256 of compressed bytes, spelled sha256:<hex>"` · `"golden: hero fixture archive hash == <compute by hand once, then pin>"`. Use the erp/world fixtures from `testFixtures(project(":packages:kotlin:ttr-metadata"))`. Write against not-yet-existing `SnapshotWriter` — watch it fail.
- [ ] **T2 (tests first).** `SnapshotArchiveStorageTest.kt`: `"listFiles/read over an archive behave like LocalFsStorage over the same docs"` (property: same fixture tree → identical `MetadataLoader.load()` result) · `"snapshot.json round-trips {formatVersion, kind, qnames, producedBy, resolvedFrom}"` · `"corrupt archive → structured failure, no throw"`.
- [ ] **T3.** Create `packages/kotlin/ttr-snapshot` (`org.tatrman:ttr-snapshot`, JVM 21, deps: `api` ttr-metadata, `implementation` commons-compress 1.27.x + zstd-jni — pin exact versions in the version catalog; zstd version pinned per plan.md risk #1). Wire into `settings.gradle.kts` + the `kotlin-metadata/v*` publish tag set (lockstep — record in PUBLISHING.md).
- [ ] **T4.** Implement `SnapshotWriter` per contracts §2 (deterministic tar rules verbatim; see tracker Library card for the exact commons-compress/zstd calls) + `SnapshotId.of(bytes): String`.
- [ ] **T5.** Implement `SnapshotReader` + `SnapshotArchiveStorage : ModelStorage` + the `snapshot.json` codec (kotlinx.serialization).
- [ ] **T6.** Implement `SnapshotCache` (contracts §2/SZ-8 layout `sha256/<first2>/<hex>`, immutable-by-id, `gc(keep: Set<SnapshotId>)`); unit tests for hit/miss/no-partial-writes (write to temp + atomic move).
- [ ] **T7.** Run Verify, check tracker boxes, commit `PL-P1.S1: deterministic snapshot archives + cache (ttr-snapshot)`.

## S2 · `ttr.lock` + `ttr fetch` + `MetadataServerSource` {#s2}

Verify: `./gradlew :packages:kotlin:ttrp-frontend:test :packages:kotlin:ttrp-cli:test` green; lock fixture bytes round-trip byte-identically; `TTRP-LCK-001..004` all fixture-backed.

- [ ] **T1 (tests first).** `LockCodecTest.kt`: `"parses the contracts §3 fixture verbatim"` (commit that TOML as a test resource) · `"writer emits byte-stable output (sorted sections/keys, fixed formatting)"` · `"unknown keys → TTRP-LCK-001 with the offending path"`. Plus `FetchPlanTest.kt`: given a fixture `/v1/snapshots/resolve` response and a current lock, the computed diff lists exactly {changed world archive, added model pin} — nothing else.
- [ ] **T2 (tests first).** `FrozenOfflineTest.kt`: `"--frozen with incomplete cache → TTRP-LCK-002 naming the missing id"` · `"--offline with cache → compiles, warns TTRP-LCK-003, staleness recorded"` (assert via the compile-record hook stubbed until S3 — leave `// PL-P1.S3: assert via compile record` marker).
- [ ] **T3.** Implement the lock model + codec in `ttrp-frontend` (parse: `org.tomlj:tomlj`; write: hand-rolled deterministic writer — tracker Library card). Config: `[ttrp] metadata-server`, `stats-max-age`, `cache-dir` keys (server URL lives in config, NEVER in the lock — contracts §3).
- [ ] **T4.** Implement `MetadataServerSource : ModelSource` — reads **only** the cache per the lock pins (no network in `load()`, ever — B-5); missing pin → the S1 storage's structured failure → `TTRP-LCK-002`.
- [ ] **T5.** Implement `ttr fetch` in `ttrp-cli`: POST `/v1/snapshots/resolve` (JDK `java.net.http`, bearer token from config) → download archives by id into the cache → rewrite `ttr.lock` → print the lock diff. Wire `--frozen` (CI default via config) / `--offline` into the compile entry point.
- [ ] **T6.** Fixture-back `TTRP-LCK-001..004` in the diagnostics table test (004 = contradiction, asserted properly in S4.T2 — stub the fixture now with `// PL-P1.S4` marker).
- [ ] **T7.** Run Verify, check tracker boxes, commit `PL-P1.S2: ttr.lock + fetch + MetadataServerSource`.

## S3 · Stats source · compile record · manifest v2 · mode-drift suite {#s3}

Verify: `./gradlew :packages:kotlin:ttrp-emit:test :packages:kotlin:ttrp-conform:test` green; `BundleManifestV2SchemaTest` (PL-P0) now validates a **generated** manifest; mode-drift suite green.

- [ ] **T1 (tests first).** `StatsSourceTest.kt`: `"entry with matching objectSchemaHash is served to the optimizer"` · `"mismatched hash → entry discarded, TTRP-STA-001 names the object, object degrades to static cost model"` · `"absent stats == stats-less compile (no error, no diagnostic)"` (contracts §4).
- [ ] **T2 (tests first).** `CompileRecordTest.kt`: from a fixture connected compile — `"record carries lock hash, snapshot ids, worldFingerprint, plugins, statsUsed VERBATIM, objectsRead"` · `"record is a SIDECAR: <program>.compile-record.json beside .bundle/, absent from the bundle and from manifest files{}"` (the B-3 guard — verification finding #1) · `"--offline sets staleness.offline"` (closes the S2.T2 marker) (contracts §5).
- [ ] **T3 (tests first).** `ManifestV2EmitTest.kt`: hero fixture emits `schemaVersion: 2` with per-island `connections`, `lineage.columns` (assert the `aggregate:SUM` hero column maps output←inputs exactly), **no provenance block, and `params` absent** (params/on-failure *emission* arrives with the PL-P2 grammar — the schema already permits them) — and the PL-P0 JSON Schema validates the generated document.
- [ ] **T4.** Implement `StatisticsSource` (cache-backed like S2.T4; max-age policy SZ-1 evaluated at *fetch*, not load) + optimizer wiring (Z reads stats through this source only; static model on absence — TTR-P optimizer contracts hold).
- [ ] **T5.** Implement compile-record emission (sidecar per contracts §5 — outside `.bundle/`, outside `files{}`) + manifest v2 in `ttrp-emit`: lineage derived from the resolver's column-provenance (er→db chains already carry it — see `ErBindingResult.chain`).
- [ ] **T6.** Add the **mode-drift suite** to `ttrp-conform` (`ttrp conform mode-drift`): compile hero via `LocalFsStorage` and via `SnapshotArchiveStorage` over an archive of the same content ⇒ **byte-identical `.bundle/`** (walk `files{}` + manifest bytes). This is B-3 made executable.
- [ ] **T7.** Run Verify, check tracker boxes, commit `PL-P1.S3: stats source + compile record + manifest v2 + mode-drift`.

## S4 · K `extends`-world grammar · composition · `ttr import-schema` {#s4}

Verify: grammar regeneration procedure from CLAUDE.md run (parser prebuild + tm-grammar); `pnpm -r test` AND `./gradlew build` green (cross-target conformance corpus updated); composition property tests green; import-schema replay test green.

- [ ] **T1 (tests first).** Conformance-corpus entries (TS + Kotlin parsers) for the PL-P0 `extends` spec: `def world dev extends "tatrman.platform.world" { … }` parses; negative fixture (extends on non-world def) errors. Watch both parsers fail.
- [ ] **T2 (tests first).** `WorldCompositionTest.kt` in ttr-metadata: `"project adds a private storage — legal"` · `"project extends a platform engine with a scoped delta — legal, overlay per RM6 (instance wins, lists wholesale)"` · `"project contradicts a platform-governed engine fact → StagingConflict-style structured failure surfaced as TTRP-LCK-004"` (closes S2.T6 marker) · property: `"composition is declaration-order-insensitive and idempotent"` · `"fingerprint of the composed world changes iff a semantic field changes"`.
- [ ] **T3.** Amend `TTR.g4` per the PL-P0 spec; run the full regeneration procedure (CLAUDE.md: antlr-ng prebuild, tm-grammar script); commit the grammar + corpus together.
- [ ] **T4.** Implement composition in `WorldResolver`: `resolve(projectWorld, platformWorldSnapshot)` — platform entries authoritative; add/extend merge per the RM6 overlay rules; contradiction = structured failure. The platform-world snapshot arrives as an ordinary §2 archive (the lock's `platformWorld.pin`).
- [ ] **T5.** Implement `ttr import-schema <connection>` in `ttrp-cli` (pg first): `INFORMATION_SCHEMA` introspection → draft world + model docs per contracts §12 (mangling rules 1–4, `source-name:` property, bytewise-sorted emission, `--package` required, collisions → `TTRP-IMP-001`).
- [ ] **T6 (test).** Import-schema determinism replay: two runs over the same fixture dump (use a checked-in `information_schema` CSV fixture, not a live DB) ⇒ byte-identical docs; collision fixture errors.
- [ ] **T7.** Run Verify, check tracker boxes, commit `PL-P1.S4: K extends-world composition + import-schema` (grammar commit separate per CLAUDE.md convention).
