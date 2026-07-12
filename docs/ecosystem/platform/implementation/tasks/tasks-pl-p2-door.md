# PL-P2 (③) — The door surface + Designer run views (stages S7–S8)

> **[superseded 2026-07-10 · STRAT-3]** license is Apache-2.0 across the open surface; MIT mentions below are historical.

> Pre-flight: S4–S6 done (executor + hall + secrets live locally). DoD: [`../plan.md`](../plan.md) §PL-P2 — S8's last task runs the phase DoD checklist. S7 spans `tatrman-platform` (door API) + one `tatrman` task (the `ttr deploy` client verb — an ordinary API client, MIT-legal, clearly marked). S8 spans platform (extensions) + `tatry`.

## S7 · Door frontend API + `ttr deploy` {#s7}

Verify: `./gradlew :services:radegast:test` green; an httpie/curl transcript of the full §15 surface against the local deploy recorded in the PR; `ttr deploy` round-trips from a laptop checkout.

- [ ] **T1 (tests first).** `DoorApiTest.kt` (Ktor test host): every §15 route — `"POST /v1/runs → 202 {runId}"` · `"GET /v1/runs/{id} returns wave/island/attempt tree with worker + logRef"` · `"SSE /v1/runs/{id}/events streams §14 envelopes live (subscribe mid-run picks up from now)"` · `"cancel → 202, terminal CANCELLED"` · `"resume degraded → 409 PLT-RUN-005 with the P3 reason"` · `"operational list filters by envelope/state"` · `"all routes bearer-authenticated; anonymous → 401"`.
- [ ] **T2 (tests first).** `EventSpineLiveTest.kt` (component, radegast + veles test hosts): a manually fired hero run produces the full transition sequence in Veles's `GET /v1/runs/{runId}` (catalog view) within ingest latency; `LineageEvent.manifest_lineage_ref` resolves against the stored bundle manifest; run + audit events carry `policy_bundle_hash` (the S4 stub's `"dev-allow-all"` until PL-P4).
- [ ] **T3.** Implement the door routes per contracts §15 over S2–S4's machinery (SSE via Ktor `respondTextWriter`/flow bridge from the outbox tail — reuse the forwarder's ordering guarantees, don't re-read raw tables in the handler).
- [ ] **T4.** Implement structured operational queries (`GET /v1/runs?…` executor-owned "what is running now" — distinct from Veles's history view; document the split in the route KDoc, F-6-β).
- [ ] **T5.** *(tatrman)* `ttr deploy` verb in `ttrp-cli`: assembles the envelope YAML (interactive-less: flags/file), uploads bundle + compile-record sidecar + envelope to `POST /v1/envelopes`, prints the validation verdicts verbatim; `--dry-run` = validation only. It is an API **client** — no envelope logic beyond the documented schema (platform owns §13; the CLI vendors nothing).
- [ ] **T6.** Wire Radegast's deploy/run authz decisions to emit AuditEvents through the same outbox (S7 spine discipline — one spine, no side channel).
- [ ] **T7.** Run Verify, check tracker boxes, commit `PL-P2.S7: door API + ttr deploy` (two repos, cross-referenced messages).

## S8 · Designer run + lineage panels · deploy roster · phase DoD {#s8}

Verify: `pnpm --filter @tatrman/designer test` + platform Gradle suites green; on the local `tatry` deploy, the browser Designer shows a live hero run and its column lineage; the PL-P2 DoD checklist ([`../plan.md`](../plan.md) §PL-P2) passes end-to-end.

- [ ] **T1 (tests first).** Vitest for the **runs panel** extension (platform repo, `designer-extensions/runs`): renders the run tree from `GET /v1/runs` fixtures; live updates over SSE; state → visual mapping covers every §14 transition incl. PARKED_RESUMABLE and DEGRADED_RESTART_ONLY.
- [ ] **T2 (tests first).** Vitest for the **column-lineage panel** (`designer-extensions/lineage`): renders `GET /v1/lineage/column` fixture graphs; a lineage edge deep-links to the manifest section it cites (CQ-5 — cite, never re-derive); depth control caps traversal.
- [ ] **T3.** Implement both panels as **Designer Extensions** (contracts §10): ESM bundles built in `tatrman-platform/designer-extensions/`, served by Veles's `GET /v1/designer/extensions`; they use only the §10 `ExtensionContext` surface (no imports from `@tatrman/designer` internals — that's the P1 boundary; add a lint rule).
- [ ] **T4.** Veles serves the extension bundles (static route + the manifest list endpoint gains the two entries); MIT shell loads them only on the `veles` backend (PL-P1.S8 loader test extended with the two real entries).
- [ ] **T5.** Helm: radegast chart + config wiring (Veles URL, IdP, secret-store namespace, SZ-2/SZ-3 knobs); `tatry clusters/local` deploys the full ③ roster (veles, radegast, kyklop, arges, brontes, steropes, postgres).
- [ ] **T6.** Ops runbook page `docs/runbook/radegast.md`: run lifecycle, resume/degrade semantics, outbox monitoring, the F-5 quota knobs — and the **Charon-outside-quota accepted hole** with its recorded revisit trigger (F-3-γ growth or first observed contention).
- [ ] **T7.** Execute the **PL-P2 DoD checklist** on the local deploy (manual fire → end-to-end run on Arges+Steropes with direct/staged transfers, park/resume/degrade, canary green, Designer views live, external `POST /v1/runs` round-trip); attach the transcript; check ALL PL-P2 tracker boxes; commit `PL-P2.S8: run/lineage Designer extensions + ③ roster deploy`; request the PL-P2 `/review`.
