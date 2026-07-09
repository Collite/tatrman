# PL-P2 (③) — Radegast: run store · envelopes · the executor (stages S2–S4)

> Pre-flight: S1 done (manifests carry params/on-failure); PL-P1 platform stack deploys locally. DoD: [`../plan.md`](../plan.md) §PL-P2. Check each box the moment its task is done. All stages here are `tatrman-platform`. **Run state is externalized from day one (F-3-α)** — no in-memory run truth, ever; every state change writes the store and its outbox row in ONE transaction.

## S2 · Run store + event outbox {#s2}

Verify: `./gradlew :services:radegast:test` green against Testcontainers PostgreSQL; kill-and-recover component test proves no lost/duplicated events.

- [ ] **T1 (tests first).** `RunIdTest.kt`: codec for contracts §14 — `"hero-nightly@7/nightly/20260710T020000Z"` round-trips `{name, version, triggerId, fireTime}`; attempt ids `…#summarize.2` parse; ordering is lexicographic-by-fireTime within an envelope; `"manual"` triggerId formats. Golden strings pinned.
- [ ] **T2 (tests first).** `RunStoreTest.kt`: schema + invariants — `"run row transitions follow the §14 RunEvent state machine (illegal transition → rejected)"` · `"attempt rows are append-only under their run"` · `"every state-changing write inserts its outbox row in the SAME transaction"` (assert via injected failure between the two = rollback of both) · `"'what is running now' query returns only live runs"`.
- [ ] **T3 (tests first).** `OutboxForwarderTest.kt`: batches ≤SZ-6, at-least-once (redelivery on failed POST), rows marked forwarded only on 2xx from the ingest endpoint, ordering preserved per run.
- [ ] **T4.** Service skeleton `services/radegast` (ktor-configurator pattern, ingress module, `/health|/ready`) + Flyway migrations for `runs`, `attempts`, `outbox` (PostgreSQL; HikariCP; same conventions as Veles's stats store).
- [ ] **T5.** Implement the run store repository (state machine enforced in SQL constraints + repository layer) + run-id codec.
- [ ] **T6.** Implement the outbox forwarder (coroutine loop → `POST /v1/ingest/events` on Veles; closes Veles's `// PL-P2: Radegast feeds this` markers on the read endpoints — verify the PL-P1 fixture-fed tests now pass against live-fed data).
- [ ] **T7.** Run Verify, check tracker boxes, commit `PL-P2.S2: run store + transactional event outbox`.

## S3 · Envelope store + deploy validation {#s3}

Verify: `./gradlew :services:radegast:test` green; every `PLT-ENV-00x` in contracts §21 fixture-backed; deploy of the hero envelope fixture round-trips.

- [ ] **T1 (tests first).** `EnvelopeCodecTest.kt`: the contracts §13 YAML fixture round-trips (kaml or snakeyaml + kotlinx — pick in T4 and record); `artifact:`/`source:` mutual exclusion; unknown fields rejected (P3); envelope identity `name@version` immutable once accepted (re-deploy same version with different content → error).
- [ ] **T2 (tests first).** `DeployValidationTest.kt` — one case per `PLT-ENV-00x` (contracts §13 list): unresolvable bundle hash · manifest `schemaVersion` outside the SZ-3 window · unbound required param · param type mismatch · unmapped manifest connection · missing principal · deployer without use-grant (stub PEP: config-listed grants until PL-P4 — `// PL-P4: Perun bundles here`) · **T6 fingerprint mismatch against Veles's served world** (fixture Veles). Plus the happy path.
- [ ] **T3 (tests first).** `BundleStoreTest.kt`: content-addressed bundle upload (tar of `.bundle/`), sha256 verified on ingest, immutable by hash, manifest extracted + parsed on accept; compile-record **sidecar** stored beside it and hash-checked against `provenance.compileRecord` (contracts §5/§13).
- [ ] **T4.** Implement envelope codec + envelope/bundle stores (envelope rows in PostgreSQL; bundle + sidecar blobs on the same blob-store abstraction Veles uses — extract it to `shared/blobstore` if copy #2 appears).
- [ ] **T5.** Implement deploy endpoints (`POST /v1/envelopes`, `GET /v1/envelopes/{name}/{version}` per contracts §15) with the full validation chain from T2; deploy/run authz PEP stub seam named.
- [ ] **T6.** Wire T6-fingerprint verification: fetch `GET /v1/worlds/current` from Veles, compare with the manifest's `world.fingerprint`; unreachable Veles at deploy = explicit retryable error (deploy is not dispatch — fail loud, not closed).
- [ ] **T7.** Run Verify, check tracker boxes, commit `PL-P2.S3: envelope + bundle stores, deploy validation`.

## S4 · The executor — wave walker with F-4 semantics {#s4}

Verify: `./gradlew :services:radegast:test` green; the component suite runs the hero manifest against a **FakeHall** (scripted dispatch results) through every F-4 behavior; every transition lands as an outbox event.

- [ ] **T1 (tests first).** `WaveWalkerTest.kt` (component, FakeHall): `"waves execute in manifest order; islands within a wave dispatch concurrently"` · `"first island failure aborts the wave's remaining dispatches but running ones finish (no preemption)"` · `"the manifest graph is authoritative — run.sh is never read"` (FakeHall asserts it only ever receives island payload refs, never the script).
- [ ] **T2 (tests first).** `RetryResumeTest.kt`: `"per-island retries honor manifest counts; attempt-scoped staging wiped between attempts (FakeCharon Evict recorded)"` · `"transient failure retries; permanent fails immediately"` · `"failed run parks PARKED_RESUMABLE; resume skips completed islands IFF envelope snapshot fingerprint unchanged"` · `"fingerprint changed → resume refused, restart offered (P3-explicit message)"` · `"past SZ-2 retention → DEGRADED_RESTART_ONLY + PLT-RUN-005"`.
- [ ] **T3 (tests first).** `ParamsOnFailureTest.kt`: `"trigger-time param binding validates types; run-date builtin injected"` · `"params reach islands via the env-binding idiom beside TTR_CONN_*"` · `"on-failure island runs iff its source failed; handled failure still ends the run non-success"` · `"cancel: SCHEDULED islands never dispatch, running ones finish, run ends CANCELLED"`.
- [ ] **T4.** Implement the walker: coroutine-per-island within a wave (`coroutineScope`/`async` — resolver-graph precedent, kantheon EXAMPLES §4), all state via the S2 run store, every transition = store write + outbox row. Validation seam per island: `PlanValidation` interface, v1 binding = pass-through with decision-logged AuditEvent (`// PL-P4: Argos + Perun replace this`).
- [ ] **T5.** Implement retry/resume/staging bookkeeping: attempt-scoped vs run-scoped staging registry rows (Charon executes the actual Evict — until PL-P3, a `StagingCleaner` interface with a logging fake, `// PL-P3: Charon Evict here`); retention clock + degrade job (SZ-2).
- [ ] **T6.** Implement param binding + injection plumbing (values flow to dispatch as env additions next to `TTR_CONN_*` — S6 owns the secret half), and on-failure edge execution.
- [ ] **T7.** `POST /v1/runs` + manual trigger path end-to-end against FakeHall (real Kyklop lands in S5; the door's public surface completes in S7).
- [ ] **T8.** Run Verify, check tracker boxes, commit `PL-P2.S4: executor wave walker (F-4 semantics)`.
