# PL-P7 (⑦) — Designer close-out · fresh instance · Q-6 acceptance (stages S3–S5)

> **[superseded 2026-07-10 · STRAT-3]** license is Apache-2.0 across the open surface; MIT mentions below are historical.

> Pre-flight: S1–S2 done; every earlier phase reviewed. DoD: [`../plan.md`](../plan.md) §PL-P7 = **Q-6 verbatim** — S5 executes it. ⚠ Pre-generated 2026-07-09 — re-validate against preceding reviews. S3 spans `tatrman` (MIT Designer) + platform; S4 is `tatry` + platform; S5 is the acceptance arc.

## S3 · Designer close-out: deploy views · the graduation gate {#s3}

Verify: `pnpm --filter @tatrman/designer test` + platform suites green; the graduation decision is WRITTEN (either way) in `docs/decisions/edit-mode-graduation.md`; GQ-4 quotas enforced if edit mode is on.

- [ ] **T1 (tests first).** Vitest for the **deploy/operations panel** (platform extension): envelope list with trigger status (next fire from Zorya), run-now action (door call), park/degrade states surfaced with their P3 reasons; fixtures cover every §14 terminal state.
- [ ] **T2.** Implement the deploy panel as a Designer Extension; Veles proxies the Radegast/Zorya operational reads it needs (one backend for the browser — no direct multi-service fan-out from the frontend).
- [ ] **T3.** *(decision task, with Bora)* **The graduation gate (G-4/G-1, design §8 ⑦):** assess the workspace machinery — branch workspaces (G-3), `WorkspaceEdit` path (G-2), git write path (G-1) — against what PL-P1–P6 actually built. Verdict A: proven → flip the flag ON for edit mode + the registration wizard this stage. Verdict B: not proven → **explicit post-v1 slip recorded with reasons** (Q-6 requires neither — the slip is legal). Write the decision doc; the next three tasks execute Verdict A and are checked as `[x] n/a — verdict B` otherwise.
- [ ] **T4 (tests first, if A).** Wizard tests: one wizard, kind-parameterized (engine/orchestrator/connection are the same world-entry concept — GQ-3); output = a commit/PR to the platform-world repo (K pin 2 — git plumbing from S2.T5 reused); `"secrets enter as refs only — the wizard has no secret field that round-trips material"`; fail-closed validation of the entry against the type manifest before commit.
- [ ] **T5 (if A).** Enable edit sessions behind the flag: branch workspace materialization on Veles (G-3 hybrid: stateless reads stay), previews ride the **query door at interactive priority** under the session's principal; save = commit via the structured-edit path.
- [ ] **T6 (if A).** GQ-4 quotas (SZ-7): max concurrent edit sessions/user + idle reap; `"the 4th session is refused with a P3-explicit message naming the knob"`.
- [ ] **T7.** Run Verify, check tracker boxes, commit `PL-P7.S3: deploy views + graduation verdict (+ wizard/edit if A)`.

## S4 · Fresh-instance rehearsal (`tatry` umbrella) {#s4}

Verify: **from an empty cluster**, `just bootstrap && just deploy <cluster>` brings up the complete roster green, with zero manual kubectl surgery; the rehearsal transcript is committed.

- [ ] **T1.** Umbrella chart in `tatrman-platform/helm`: the full roster (veles, radegast, zorya, theseus, argos, kyklop, arges, brontes, steropes, charon, perun, postgres instances, OPA sidecars) with one values file per instance; `tatry` clusters consume the umbrella (D-4's graduation direction exercised early).
- [ ] **T2.** Secret-zero + IdP bootstrap documented AND scripted in `tatry`: K8s service accounts for the secret-store SPI, Keycloak realm fixture (or org IdP values), Perun's signing key provisioning (via the secret store — never in values files), platform-world repo seed + project-roster config.
- [ ] **T3 (test).** Cold-start ordering test: `/ready` gating chains (Veles needs its repos, Radegast needs Veles, PEPs need first bundles) — kill-and-recover each service on the running instance; nothing wedges, fail-closed where policy is involved.
- [ ] **T4.** Provision the hero's world: fixture Postgres + files storage registered (world entries — through the wizard if S3 verdict A, by commit if B); service principal `svc-analytics-nightly` + grants in the fixture bundle sources.
- [ ] **T5.** The **fresh-instance rehearsal**: tear down completely, redeploy from tag, deploy + run the hero manually end-to-end. Fix every papercut found; re-run until boring. Commit the transcript as `tatry/docs/rehearsal-<date>.md`.
- [ ] **T6.** Run Verify, check tracker boxes, commit `PL-P7.S4: umbrella deploy + fresh-instance rehearsal`.

## S5 · The Q-6 acceptance run {#s5}

Verify: `docs/ecosystem/platform/implementation/acceptance-1.0.0.md` exists, every clause checked with evidence links; all tatrman suites + `ttrp conform` (mode-drift + emit-determinism) green at the closing commit.

- [ ] **T1.** Stand up the acceptance instance **fresh from `tatry`** (S4's rehearsal, for real — Q-6's opening clause) + the external pair: Airflow 3 and OpenMetadata (pinned versions, the conformance-lane configs promoted).
- [ ] **T2. Life 2:** deploy the hero via envelope (`ttr deploy`); the **nightly cron fires it under `svc-analytics-nightly`** on Arges+Steropes with Charon transfers; **RLS enforced** (two-principal row-count proof); **runs and column lineage visible in the Designer**. Evidence: transcripts + screenshots per clause.
- [ ] **T3. Life 3:** the same program **delegates through Airflow 3** (door-calling DAG fired by Airflow's scheduler), **lineage harvested back** into the same graph, **exported to OpenMetadata** (entities + column lineage verified via OM's API); **the Kestra emit plugin passes conformance** (determinism kit + execution suite, links to the green CI runs).
- [ ] **T4. Life 1:** full tatrman verification at the closing commit — `pnpm -r test`, all Gradle suites, `ttrp conform` incl. mode-drift: **the MIT toolchain never regressed** (diff the conformance fingerprints against the PL-P0-exit baseline captured in `docs/ecosystem/platform/implementation/baseline-p0.json` — PL-P0.S2.T6 records it).
- [ ] **T5.** Write `docs/ecosystem/platform/implementation/acceptance-1.0.0.md`: Q-6 quoted verbatim, clause-by-clause evidence table, deviations/waivers (none, or listed with Bora's sign-off), the parked-ledger snapshot (what v1 consciously does not do, from design §11).
- [ ] **T6.** Close out: check ALL remaining tracker boxes, update the tracker header to "v1 ACCEPTED <date>", commit `PL-P7.S5: Q-6 acceptance — platform v1`, request the final `/review`, and hand the parked ledger + H-7-β/Dagster/DataHub arcs to their own future planning sessions.
