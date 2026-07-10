# PL-P4 (⑤) — Argos + the validator SPI (stages S1–S2)

> Pre-flight: PL-P3 review done; kantheon donor commit re-pinned; the PL-P0 security-block grammar spec frozen (implemented in S3). DoD: [`../plan.md`](../plan.md) §PL-P4 — checked at S5's last task. ⚠ Pre-generated 2026-07-09 — re-validate against the PL-P3 review before starting. **Argos arrives working**: the HOCON policy store transplants VERBATIM (H-7 α — RLS/DENY/MASK v1 by construction); resist every temptation to "clean it up onto bundles" here (that is the scheduled H-7-β arc).

## S1 · Argos transplant + hall wiring {#s1}

Verify: `./gradlew :services:argos:test` green (transplanted suites + RLS component tests); the hero run on the local deploy produces **principal-dependent row counts**; dependency-rule test green.

- [ ] **T1.** `git filter-repo` move from the pinned kantheon commit: `services/argos` → `services/argos` + proto slices `org/tatrman/{argos,security}` → `proto/hall-proto`. `TRANSPLANTS.md` updated; kantheon-side delete PR opened as DRAFT and parked until ⑥ (same discipline as PL-P2.S5.T7).
- [ ] **T2.** DQ-2 sweep (`cz.tatrman.argos.*` etc.); version-catalog wiring; transplanted suites green behavior-unchanged — **including the HOCON store tests verbatim** (the store's file format, precedence, and admin-bypass semantics are frozen contract until H-7-β).
- [ ] **T3 (tests first).** `HallValidationTest.kt` (component, radegast + real Argos, FakeKyklop): `"every island's plan passes Argos before dispatch — no bypass path exists"` (assert FakeKyklop never sees an unvalidated plan) · `"RLS predicate injection: the same fixture plan under principal A vs B carries different predicates"` · `"column DENY blocks the plan with a structured verdict; MASK rewrites the projection"` · `"TopN + coercion behaviors unchanged from donor goldens"`.
- [ ] **T4.** Replace Radegast's `PlanValidation` pass-through seam (PL-P2.S4.T4 marker) with the Argos client; the S4 executor component suite re-runs unchanged except the validation assertions now bite.
- [ ] **T5 (test first).** `ValidationAuditTest.kt`: every Argos verdict (pass/deny/mask) emits an AuditEvent through the run-store outbox with `pep: "argos"` and the run's policy context — S7-spine discipline, no side channel (bundle hash stays `"dev-allow-all"` until S5 swaps the stub).
- [ ] **T6.** Helm chart + `tatry clusters/local` roster + OTel; run the hero with two fixture principals and record the differing row counts in the PR (the phase's headline proof).
- [ ] **T7.** Run Verify, check tracker boxes, commit `PL-P4.S1: Argos transplant + hall validation wiring`.

## S2 · Validator SPI + the LLM-Guard proving plugin {#s2}

Verify: `./gradlew :packages:kotlin:ttr-validator-spi:test` (tatrman) + `:services:argos:test` (platform) green; Argos with no plugin installed behaves byte-identically to S1 (deterministic default).

- [ ] **T1 (tests first).** *(tatrman)* `ValidatorSpiContractTest.kt` in a new `packages/kotlin/ttr-validator-spi` module: the contracts §9 surface verbatim — `"Verdict is Pass | Deny(code, reason) | Advise(code, warning) — no plan-rewrite surface exists"` (runtime-guaranteed: `ValidationContext.plan` is a `ByteArray`, so the HOST must pass each plugin a defensive copy — assert a mutating fake plugin cannot affect the plan Argos dispatches; the host-side copy is implemented in S2.T4) · `"ValidationContext carries plan bytes, PrincipalInfo, worldFingerprint, door"` · a `RecordingFakePlugin` test fixture published via `java-test-fixtures`.
- [ ] **T2.** *(tatrman)* Implement `org.tatrman:ttr-validator-spi` (contracts §9 interfaces verbatim, `spiVersion = 1`, ServiceLoader discovery contract documented in KDoc); publish with the toolchain tag set; contracts.md changelog entry (an MIT contract shipped — D-3).
- [ ] **T3 (tests first).** *(platform)* `PluginHostTest.kt` in Argos: `"plugins load via ServiceLoader from a configured plugin dir, isolated classloader"` · `"plugin Deny blocks after Argos's own deterministic pipeline passes"` · `"plugin Advise logs + AuditEvents but never blocks"` · `"plugin throwing → Deny with EXECUTION_ERROR-style code, hall stays up"` (safe-wrapper pattern — kantheon EXAMPLES §3c shape) · `"no plugin installed = deterministic default (identical verdicts to S1 goldens)"`.
- [ ] **T4.** *(platform)* Implement the plugin host in Argos after its deterministic pipeline (RLS/DENY/MASK/TopN/coercion stay Argos-internal, NOT SPI-visible — C-5-i boundary); the host hands every plugin a **defensive copy** of the plan bytes per invocation (T1's runtime guarantee).
- [ ] **T5.** *(kantheon)* Repackage the LLM Guard (DF-V04 tendril) as `llm-guard`, a plugin on `org.tatrman:ttr-validator-spi` — kantheon depending on an MIT SPI artifact is the P2 clarification made real; it declares `door == QUERY` relevance and returns Pass for PROGRAM contexts. Prove it loads in Argos's host with a `door = QUERY` harness context (the live query door arrives in ⑥ — leave `// PL-P6: live proof at adoption` marker).
- [ ] **T6.** Run Verify, check tracker boxes, commit(s) `PL-P4.S2: validator SPI + LLM-Guard plugin` per repo (cross-referenced).
