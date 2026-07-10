# PL-P5 (E track) — Emit SPI · determinism kit · Kestra (stages S1–S3)

> Pre-flight: PL-P0 review done (SPI + T6-ownership contracts frozen). These MIT stages run **parallel to PL-P2–P4**; only tasks marked *(needs door)* wait for PL-P2. DoD: [`../plan.md`](../plan.md) §PL-P5. ⚠ Pre-generated 2026-07-09 — re-validate against the PL-P0/P1 reviews. All stages here are `tatrman` (MIT). **The SPI is proven by extraction, not invented** — if the bash plugin can't be byte-identical to the pre-extraction emitter, the SPI surface is wrong; fix the surface, not the goldens.

## S1 · Emit SPI + the bash extraction {#s1}

Verify: `./gradlew :packages:kotlin:ttrp-emit-spi:test :packages:kotlin:ttr-emit-bash:test :packages:kotlin:ttrp-emit:test` green; **extraction-parity suite green over the full conformance corpus** (every fixture bundle byte-identical pre/post extraction); `ttrp conform mode-drift` still green.

- [ ] **T1 (tests first).** `EmitSpiContractTest.kt` in new `packages/kotlin/ttrp-emit-spi`: contracts §8 surface verbatim — `"EmitRequest carries graph, VERBATIM island payloads (sha256-checked), resolved type+instance manifests, finished manifestJson"` · `"EmitResult.files is a SortedMap — iteration order is deterministic by construction"` · `"a plugin returning a file colliding with core-owned paths (manifest.json, islands/*, compile-record sidecar name) → structured error"`.
- [ ] **T2 (tests first).** `ExtractionParityTest.kt`: for every conformance-corpus program, `old ttrp-emit output == core emit + ttr-emit-bash plugin output`, byte-for-byte across the whole `.bundle/` tree. THE gate of this stage — write it against the not-yet-split modules.
- [ ] **T3.** Create `org.tatrman:ttrp-emit-spi` (interfaces per contracts §8, `spiVersion = 1`) + carve `ttrp-emit` into core (graph derivation, island payload emit, manifest/record writing) and orchestration-layer emission behind the SPI.
- [ ] **T4.** Extract `org.tatrman:ttr-emit-bash`: the existing `run.sh` renderer moves verbatim behind `TtrEmitPlugin` (`targetId = "bash"`); it ships the bash executor-type manifest (contracts §7 subset — the T6-ownership amendment made real). T2 goes green.
- [ ] **T5.** Plugin loading in the toolchain: isolated classloader per plugin; identity resolved against `ttr.lock [plugins]` (coordinates + version + artifact sha256 — mismatch = hard error `TTRP-LCK`-family); built-in fallback when a target has no lock entry but ships in-tree (bash).
- [ ] **T6 (test).** Isolation tests: `"plugin classpath does not leak into the compiler core (loading a plugin with a conflicting dependency version changes nothing in core behavior)"` · `"plugin cannot read env/filesystem during emit"` (SecurityManager-less JVM: enforce by API shape — EmitRequest is the only input; document + test that the SPI passes no ambient handles).
- [ ] **T7.** Run Verify, check tracker boxes, commit `PL-P5.S1: emit SPI + bash extraction (parity-proven)`.

## S2 · Determinism kit + plugin trust {#s2}

Verify: `./gradlew :packages:kotlin:ttrp-conform:test` green; `ttrp conform emit-determinism --plugin org.tatrman:ttr-emit-bash` passes on two OS runners; a tampered-artifact fixture fails verification.

- [ ] **T1 (tests first).** `EmitDeterminismKitTest.kt`: the kit compiles every corpus program **twice** (fresh JVM state between runs) through the plugin under test and byte-compares `EmitResult.files`; `"a deliberately timestamping fixture plugin FAILS with a diff report naming file + first differing offset"` · `"the bash plugin passes"`.
- [ ] **T2.** Implement `ttrp conform emit-determinism --plugin <coords>` (H-6; the third-party certification requirement — say so in `--help`); wire into tatrman CI for all first-party plugins.
- [ ] **T3 (tests first).** `SignatureVerifyTest.kt`: `"a PGP-signed plugin artifact verifies against its publisher key (BouncyCastle, detached .asc beside the jar — Maven convention)"` · `"unsigned artifact + verify-if-signed → loads with a recorded warning"` · `"unsigned + require-signed → refused, names the knob"` · `"tampered jar → refused regardless of policy"`.
- [ ] **T4.** Implement verify-if-signed loading in the S1.T5 plugin loader; `require-signed` = `[ttrp] require-signed-plugins` project knob (deployment policy per H-6); trusted-key ring location documented.
- [ ] **T5.** Document the certification flow in `docs/ttr-p/plugin-certification.md` (EQ-2): publish signed → pass `emit-determinism` → recorded in the plugin's README; two trust roots restated (IdP vs publisher keys).
- [ ] **T6.** Run Verify, check tracker boxes, commit `PL-P5.S2: determinism kit + plugin signature verification`.

## S3 · `ttr-emit-kestra` — the second consumer {#s3}

Verify: `./gradlew :packages:kotlin:ttr-emit-kestra:test` green; **`ttrp conform emit-determinism --plugin org.tatrman:ttr-emit-kestra` passes** (a Q-6 clause); conformance-lane dockerized Kestra executes the hero's native flow green.

- [ ] **T1 (tests first).** `KestraEmitTest.kt` goldens: hero program → Kestra flow YAML — `"flow {id: <program>, namespace: <configured>} with one task per island, wave order via task dependencies"` · `"pg islands render io.kestra.plugin.scripts.shell.Script invoking psql -v ON_ERROR_STOP=1 (the F-lite invocation binding verbatim)"` · `"polars islands render python script tasks"` · `"transfer edges render as tasks between waves"` · `"connections surface as Kestra-side env placeholders TTR_CONN_* — never material"` (tracker Library card: verified flow shape).
- [ ] **T2 (tests first).** Golden for the **standalone/native semantics** (E-3-β): the emitted flow runs islands directly (no platform anywhere in it), credential-bounded per H-8 — assert no door URL appears in native output for a standalone world.
- [ ] **T3.** Implement `org.tatrman:ttr-emit-kestra` (`targetId = "kestra"`, data-defined target = pure data generation — the EQ-3 structural point; snakeyaml-engine with a deterministic dumper: sorted maps where order isn't semantic, fixed line width).
- [ ] **T4.** Ship the Kestra executor-type manifest in the plugin (control: [fs, ss] via dependencies; invocation {psql, python3} via script tasks; events [cron] via `io.kestra.plugin.core.trigger.Schedule`).
- [ ] **T5.** Conformance-lane job: dockerized Kestra (pinned tag), deploy the hero's emitted flow via Kestra's API, execute (`POST /api/v1/executions/{namespace}/{flow}`), assert island outputs equal the bash-bundle baseline under the seven-point comparison.
- [ ] **T6.** Determinism-kit pass wired into CI for the plugin (the Q-6 clause, permanently guarded).
- [ ] **T7.** Run Verify, check tracker boxes, commit `PL-P5.S3: ttr-emit-kestra (determinism-certified)`.
