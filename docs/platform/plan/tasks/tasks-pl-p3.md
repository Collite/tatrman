# PL-P3 (④) — Charon (stages S1–S2)

> Pre-flight: PL-P2 review done; kantheon donor commit re-pinned for the move. DoD: [`../plan.md`](../plan.md) §PL-P3. Check each box the moment its task is done. Both stages `tatrman-platform`. The phase's contract: **movement becomes Charon's without changing a single result byte** — conformance-unchanged is the gate. The quota hole (Charon transfers outside F-5 arbitration) is *accepted* for v1 — do not "fix" it here.

## S1 · Charon transplant {#s1}

Verify: `./gradlew :services:charon:test` green (transplanted suites + new contract tests); dependency-rule test green; `TRANSPLANTS.md` updated.

- [ ] **T1.** `git filter-repo` move from the pinned kantheon commit: `services/charon` → `services/charon` + its proto slice `shared/proto/src/main/proto/org/tatrman/charon` → `proto/hall-proto` (beside kyklop/worker protos). Record in `TRANSPLANTS.md`.
- [ ] **T2.** DQ-2 sweep: proto package `org.tatrman.charon.*` → `cz.tatrman.charon.*`, Kotlin roots likewise; version-catalog wiring; transplanted suites green behavior-unchanged. (Charon's `bench/` moves too — keep it runnable, exclude from CI.)
- [ ] **T3 (tests first).** `CharonContractTest.kt` (component, Testcontainers PG + local files): the four verbs against fixtures — `"Materialize: query result lands as Arrow IPC at the staging ref"` · `"Stage/Copy: byte-faithful transport (sha256 in == out)"` · `"Evict: target gone, idempotent on re-call"` · `"named-connection resolution failure → structured error, no partial writes"`. These pin the transplant's behavior as the platform's contract — write them from the transplanted API's actual shape, then treat them as frozen.
- [ ] **T4 (test first).** `TransferSecretsTest.kt`: per-transfer resolution for the **source×target pair** (contracts §17) — a pg→files transfer resolves exactly two refs, injects both `TTR_CONN_*`, and the canary assertions hold on Charon's logs/artifacts (extend the S6 canary suite to cover Charon paths).
- [ ] **T5.** Wire Charon's connection acquisition to the secret-store SPI (dispatch-time, per transfer — replacing whatever donor-side config lookup it carried; the `TTR_CONN_*` env contract stays verbatim).
- [ ] **T6.** Helm chart + `tatry clusters/local` roster + OTel wiring per house pattern.
- [ ] **T7.** Run Verify, check tracker boxes, commit `PL-P3.S1: Charon transplant + secret-SPI wiring`; open the kantheon-side delete PR as DRAFT and park it (kantheon's mini-spine uses Charon until the ⑥ adoption arc — delete leg executes in PL-P6.S2).

## S2 · Transfer edges through Charon + the FQ-6 staging lifecycle {#s2}

Verify: `./gradlew :services:radegast:test :services:charon:test` green; `ttrp conform` over the hero on the local deploy: **results byte-identical to the PL-P2 baseline** (record both fingerprint sets in the PR); Evict lifecycle test green on all three ends.

- [ ] **T1 (tests first).** `TransferEdgeTest.kt` (component, radegast + real Charon): `"the hero's cross-engine edge routes executor → Charon (Materialize/Stage/Copy per the manifest's via <staging>)"` · `"transfer failure fails the dependent island's pre-flight, retry re-runs the transfer"` · `"Charon calls carry the run/attempt id for log correlation (IslandEvent.worker == \"charon\")"`.
- [ ] **T2 (tests first).** `EvictLifecycleTest.kt` — FQ-6's three ends: `"run SUCCEEDED → run-scoped staging evicted"` · `"run abandoned (explicit cancel of a parked run) → evicted"` · `"retention expiry (SZ-2) → evicted AND run degrades restart-only in the same job"` · `"attempt-scoped staging wiped between retries via Evict (closes S4's StagingCleaner fake)"`.
- [ ] **T3.** Implement the executor's transfer-edge path: per manifest transfer, resolve staging via the world's Transfer binding → Charon verbs; replace the `// PL-P3: Charon Evict here` StagingCleaner fake with the real client (S4's suites re-run unchanged — the interface holds).
- [ ] **T4.** Implement the retention/degrade job's Evict integration (one job owns the clock: degrade + evict atomically with the run-store transition + outbox event).
- [ ] **T5.** Conformance-unchanged gate: run `ttrp conform` (seven-point comparison) on the hero across the PL-P2-baseline artifacts vs post-Charon runs — movement must be invisible in results (Arrow schema fingerprints + multiset equality).
- [ ] **T6.** Runbook page `docs/runbook/charon.md`: verb semantics, staging layout, eviction monitoring, and the **accepted quota hole** restated with its revisit trigger (do not silently arbitrate transfers).
- [ ] **T7.** Run Verify, execute the PL-P3 DoD checklist ([`../plan.md`](../plan.md) §PL-P3), check ALL tracker boxes, commit `PL-P3.S2: transfers via Charon + FQ-6 lifecycle`; request the PL-P3 `/review`.
