# PL-P4 (⑤) — Security block · Perun · PEP wiring (stages S3–S5)

> **[superseded 2026-07-10 · STRAT-3]** license is Apache-2.0 across the open surface; MIT mentions below are historical.

> Pre-flight: S1–S2 done. DoD: [`../plan.md`](../plan.md) §PL-P4 — S5's last task runs the checklist. ⚠ Pre-generated 2026-07-09 — re-validate against preceding reviews. S3 is `tatrman` (MIT sugar + generator); S4–S5 are `tatrman-platform`. Standing rules that bind every task here: **deny-overrides** (H-1), **fail-closed** (H-2/H-4), generated fragments **never hand-edited**, and the security block is **fingerprint-neutral**.

## S3 · `security` block grammar + MIT generator {#s3}

Verify: grammar regeneration procedure run; `pnpm -r test` + `./gradlew build` green; generator determinism replay green; **fingerprint-neutrality property test green** (the load-bearing one).

- [ ] **T1 (tests first).** Conformance-corpus entries for the PL-P0 H-1 spec: the contracts §11 block (`own` / `classify` / `grant read` / `mask`) parses in both parsers; negatives (unknown verb, row-predicate syntax → "Rego-side in v1" error, grant on unknown object) error. Watch both parsers fail.
- [ ] **T2 (tests first).** `FingerprintNeutralityTest.kt` (property, in ttr-metadata's fingerprint suite): adding/editing/removing any `security` block leaves `WorldFingerprint` and the T6 semantic hash **bit-identical** — access changes must never churn world verification (H-1 pin 2).
- [ ] **T3 (tests first).** `SecurityGenTest.kt` in a new `packages/kotlin/ttr-security-gen` module: fixture block → Rego fragments + `data.json` goldens — `"package path = tatrman.generated.<sanitized-qname> (contracts §11 rule — Perun's build COMPOSES these into the §19 query layout, S4.T2)"` · `"every generated file carries the '# GENERATED — do not edit' header"` · `"grants only grant — no generated fragment can contain a deny"` (deny-overrides precondition) · `"classifications land as data, roles referenced verbatim"` (HQ-1) · `"same block ⇒ same bytes"` (determinism replay).
- [ ] **T4.** Amend `TTR.g4` per the frozen spec; full regeneration procedure; grammar + corpus committed separately.
- [ ] **T5.** Implement `org.tatrman:ttr-security-gen`: block model → fragment generation per T3's goldens; CLI verb `ttr security-gen <model-repo> --out <dir>` (Perun's build pipeline invokes exactly this — one generator, two callers, I-2 pattern).
- [ ] **T6.** Semantics-layer validation (ttr-semantics): security blocks resolve their object references (unknown qname = ordinary resolution error); **advisory-only at compile** (H-3 — a violation diagnostic, never a compile block).
- [ ] **T7.** Run Verify, check tracker boxes, commit `PL-P4.S3: security block grammar + deterministic generator`.

## S4 · Perun — directory + PDP {#s4}

Verify: `./gradlew :services:perun:test` green; a full bundle build (sugar + hand Rego → composed, signed, hashed) round-trips against a real OPA container pulling from Perun's endpoint.

- [ ] **T1.** `git filter-repo` move: `infra/whois` → `services/perun` (the one transplant that gets a Slavic *rename* — it grows into a new service; record the lineage in `TRANSPLANTS.md`); DQ-2 sweep to `cz.tatrman.perun.*`; directory suites (IdP/ERP sync → Postgres, `UserRecord` lookups) green behavior-unchanged. Kantheon-side whois delete PR opened as DRAFT and parked until ⑥ (same D-5 discipline as every transplant — executes in PL-P6.S2.T4).
- [ ] **T2 (tests first).** `BundleBuildTest.kt`: the contracts §19 pipeline — `"generated fragments (§11 packages tatrman.generated.*) COMPOSE into the bundle's §19 query layout (tatrman.<kind>.<sanitized-qname>) — a PEP query at the §19 path sees the sugar grants"` (the §11→§19 aggregation rule, contracts changelog v1.1) · `"generated fragments + hand-Rego repo compose; a hand-Rego deny wins over any sugar grant (deny-overrides golden matrix)"` · `"unknown role name in any fragment → build FAILS CLOSED naming role + source file (HQ-1)"` · `"classification→role mapping is org data consumed at build, not baked into fragments"` · `"bundle is content-hashed; same inputs ⇒ same hash"`.
- [ ] **T3 (tests first).** `BundleServeTest.kt`: `"GET /v1/policies/bundles/{scope} serves a standard OPA bundle (.manifest, *.rego, data.json, .signatures.json)"` · `"signature verifies against Perun's publisher key (JWT bundle-signing format)"` · `".manifest carries the SZ-4 expiry"` · `"/hash endpoint returns the content hash the events must cite"`.
- [ ] **T4.** Implement the build pipeline: watch/pull the policy sources (model repos' generated fragments via `ttr-security-gen`, the org hand-Rego git repo — robots-through-git applies to policy too), compose, validate, sign (BouncyCastle; key from the secret-store SPI — never on disk), hash, store, serve.
- [ ] **T5.** Implement the directory endpoints Perun keeps from whois (principal lookups the doors' use-grant checks need) behind the shared ingress module. Leave a `// post-v1: consume Veles rename events (contracts §19) — v1 defers; a renamed qname surfaces as fail-closed denials until policy sources are updated` seam marker + one line in the S5.T5 decision doc (silence would be the defect).
- [ ] **T6.** OPA-against-Perun component test: a real OPA container configured with Perun's bundle endpoint downloads, verifies, activates, and answers a fixture authz query — the exact wiring S5's sidecars replicate.
- [ ] **T7.** Helm chart (+ its Postgres) + roster; run Verify, check tracker boxes, commit `PL-P4.S4: Perun — directory + policy build/sign/serve`.

## S5 · PEP wiring — fail-closed enforcement everywhere {#s5}

Verify: platform Gradle suites green; the **fail-closed drill** (stop Perun, wait past SZ-4 expiry, watch the hall refuse) recorded on the local deploy; PL-P4 DoD checklist passes.

- [ ] **T1 (tests first).** `DoorAuthzTest.kt` (radegast): `"deploy without deploy-grant → 403 with AuditEvent(decision=deny)"` · `"deploy without use-grant on the envelope's principal → 403 naming the principal"` · `"run/cancel/resume authz enforced per envelope"` · `"every decision cites the REAL bundle hash"` (retires `"dev-allow-all"` — closes the PL-P2 stubs).
- [ ] **T2 (tests first).** `FailClosedTest.kt` (component, real OPA sidecar): `"bundle within expiry → decisions flow"` · `"bundle past expiry → EVERY PEP decision denies with PLT-POL-001"` · `"Perun down but bundle fresh → decisions flow (PDP off the hot path — H-4's point)"`.
- [ ] **T3.** Wire OPA sidecars via helm (one per PEP pod: radegast, argos, veles; query door joins at ⑥) pulling Perun's bundles; PEP clients query `localhost` OPA; refresh 60 s, alert at half-expiry (SZ-4 — plan risk item made a Prometheus rule).
- [ ] **T4.** Replace the two remaining stubs: PL-P2.S3's config-grant deploy check → OPA query; PL-P1.S7.T5's Veles allow-all catalog PEP → coarse per-project/all-org visibility from bundle data (H-3), with the Designer's catalog reads now filtered.
- [ ] **T5.** Argos data plane note made real in docs: it keeps HOCON (H-7 α) — write `docs/decisions/h7-rekey-arc.md` scheduling the β-step (scope: rekey HOCON store onto Perun's structured bundle data; **HQ-5 — mechanical translation vs re-authoring — is decided INSIDE that arc**, informed by this phase's transplant experience; trigger: first policy-change request that has to be made twice).
- [ ] **T6.** End-to-end security-block flow drill on the local deploy: edit a fixture model's `security` block → commit → `ttr security-gen` output picked up by Perun's build → new signed bundle → PEPs refresh → behavior change visible at the door — **zero hand-edited policy files** anywhere in the chain (record the transcript).
- [ ] **T7.** Canary suite (PL-P2.S6) re-run green with policy machinery live; ingress-hardening sweep: confirm every platform route sits behind the shared H-2 module (grep for ad-hoc auth: none).
- [ ] **T8.** Execute the PL-P4 DoD checklist ([`../plan.md`](../plan.md) §PL-P4), check ALL PL-P4 tracker boxes, commit `PL-P4.S5: PEP wiring + fail-closed enforcement`, request the PL-P4 `/review`.
