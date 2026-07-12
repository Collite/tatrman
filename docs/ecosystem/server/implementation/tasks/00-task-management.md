# Tatrman Server — Task Management (SV-P0 ✅ · SV-P1 ✅ · SV-P2)

> SV-P0 lists generated 2026-07-10; **SV-P1 + SV-P2 lists generated 2026-07-11 at the phase-review session** (per the plan's rule: lists per phase, at phase start — SV-P2 generated alongside by Bora's explicit call: all its pre-flights are resolved and it is execution-only). Inputs: [`../plan.md`](../plan.md) §SV-P1/§SV-P2 · [`../../design/contracts.md`](../../design/contracts.md) · naming ledger [`../../../platform/design/naming-260710.md`](../../../platform/design/naming-260710.md) · [`../../../open-source-plan.md`](../../../open-source-plan.md) §1 (G1) · `tatrman/PUBLISHING.md` · [`sv-p0-review-input.md`](./sv-p0-review-input.md) (dispositions 2026-07-11, RO-28) · control room §7 RO-15..28.

## Rules for the executing agent (read before every stage)

1. **Check each checkbox immediately after its task is done.** Never batch checkbox updates.
2. Run the stage's **verify command block** at the end of every stage; a stage is DONE only when it passes.
3. **Rename-before-publish AND version-before-publish are the invariants (RO-24):** anything that reaches a public registry never renames and never re-versions. Before any publish task: run the stage's grep gate, confirm the version, then publish. If a task seems to publish something still carrying a persona string or a pre-freeze proto name, stop and flag ⚑.
4. **Publishes are irreversible.** Every task that pushes a tag, publishes to GH Packages/GHCR/Maven Central/PyPI, or syncs the pilot carries a **[GATE]** marker — re-read its listed preconditions immediately before executing, and stop ⚑ if any is unmet.
5. TDD where checks exist: write/extend the named CI check or test FIRST, watch it fail (where applicable), then change code/files until green.
6. History discipline: moves are `git mv`; doc history is never rewritten — superseded design text gets markers, not edits.
7. Anything unexpected (missing file, extra importer, failing suite unrelated to your change, a published version that already exists): stop, record in the stage's "findings" section, flag ⚑ — do not improvise.
8. Work lands on a feature branch per repo (`sv-p1-…` / `sv-p2-…`); Bora folds to `master`. Exception: tags are cut ONLY from `master` after the fold (see S1·T1).

## SV-P0 · Repo & naming — ✅ PHASE DONE (2026-07-11)

All six stages done (S1–S6, lists in this folder); phase-DONE checklist 5/5 — Bora's ⚑ review executed 2026-07-11, dispositions recorded in [`sv-p0-review-input.md`](./sv-p0-review-input.md) §Dispositions + control room **RO-28**. Headlines: pilot PINNED (olymp `master@12796ac` + 17 digests) · Python Dockerfiles fixed + grep gate enforcing (2026-07-11 follow-up) · image path = **flat `ghcr.io/collite/<name>`** (RO-28) · default-branch flip = Bora's checklist · kantheon `reviews.md` stays.

## SV-P1 · Publish gates — stage sequence & status

| Stage | List | Repo(s) | Depends on | Status |
|---|---|---|---|---|
| S0 · Pre-flight (RO-13 review · Central namespace · calendar) | [`tasks-sv-p1-s0-preflight.md`](./tasks-sv-p1-s0-preflight.md) | tatrman (+ Bora external) | — | ✅ **DONE**. RO-13 reviewed; Central namespace verified (`tatrman.org` DNS); signing key `097C71…EA63`; SV-P0 folds done |
| S1 · Gates 1+2: tatrman 0.9.x line | [`tasks-sv-p1-s1-tatrman-gates.md`](./tasks-sv-p1-s1-tatrman-gates.md) | tatrman → kantheon, tatrman-server | S0·T1 (RO-13) | ✅ **DONE**. `translate.v1` public (`kotlin-translator/v0.9.0` + `python-plan/v0.9.0`); persona grep-gate added |
| S2 · Gate 3a: server library artifacts | [`tasks-sv-p1-s2-server-artifacts.md`](./tasks-sv-p1-s2-server-artifacts.md) | tatrman-server → kantheon | S1 | ✅ **DONE** (T1–T7). `server-libs/v0.9.0` = 11 `org.tatrman:*` libs on GH Packages (capabilities-client trimmed); kantheon registry-only, mavenLocal retired, clean-machine proof green (⚑5 retired). Branches `sv-p1-server-artifacts` |
| S3 · Gate 3b: images + olymp repoint | [`tasks-sv-p1-s3-images-repoint.md`](./tasks-sv-p1-s3-images-repoint.md) | tatrman-server, olymp, kantheon | S2 | ✅ **DONE**. 17 images `ghcr.io/collite/*:0.9.0`; olymp repointed; namespace split (spine → `ttr-server`); SV-P0 pin retired; T6 prose sweep + persona-gate hardened |
| S4 · Maven Central (public coordinates) | [`tasks-sv-p1-s4-central.md`](./tasks-sv-p1-s4-central.md) | tatrman, tatrman-server | S1, S2, S0·T2/T5 | ✅ **DONE**. vanniktech wiring both repos; CI Central lanes; public debut **`0.9.4`** via the Portal; anonymous-resolution proof project |

S1→S2→S3 is strict order (each publishes what the next consumes). S4 runs as soon as the Central namespace verification (S0·T2) lands — it can overlap S2/S3. SV-P2 stages may run in parallel with all of SV-P1 **except** SV-P2·S1·T2 (SPDX headers), which should land before S4's Central publishes so the public jars carry headered sources.

**Phase DONE (from plan §SV-P1) — 4/5, awaiting Bora's ⚑ review:**

- [x] ai-platform (or any consumer) can resolve every spine artifact from **public** coordinates (Maven Central for `org.tatrman:*` jars at `0.9.4`; GHCR for images) — the S4 scratch-project test (`verify-public-resolution`) is the standing proof (run once Central sync settles)
- [x] Nothing published carries a persona string or a pre-freeze proto name (S1/S2/S3 artifact gates + the S3 hardened repo gate incl. `*.tpl`/`logback.xml`; `\barges`/`argos` un-anchored so `TOKEN_`-style env prefixes match)
- [x] The pilot runs on renamed images from `ghcr.io/collite/*` — the SV-P0 pin retired (S3·T5)
- [ ] Findings of all five lists reviewed by Bora; ⚑ items dispositioned → [`sv-p1-review-input.md`](./sv-p1-review-input.md) (compiled 2026-07-12; **awaiting Bora**)

## SV-P2 · Apache-2.0 swap + public-repo hygiene — stage sequence & status

| Stage | List | Repo(s) | Depends on | Status |
|---|---|---|---|---|
| S1 · License mechanics (NOTICE · SPDX · MIT sweep) | [`tasks-sv-p2-s1-license-mechanics.md`](./tasks-sv-p2-s1-license-mechanics.md) | tatrman, tatrman-server | — | ☐ |
| S2 · Governance & community files | [`tasks-sv-p2-s2-governance-files.md`](./tasks-sv-p2-s2-governance-files.md) | tatrman, tatrman-server | — | ☐ |
| S3 · External-reader verification | [`tasks-sv-p2-s3-external-reader.md`](./tasks-sv-p2-s3-external-reader.md) | both + Bora (trademark) | S1, S2 | ☐ |

**Phase DONE (from plan §SV-P2):**

- [ ] Both open repos are legally coherent for an external reader: LICENSE, SPDX headers (CI-enforced), NOTICE, contribution terms (DCO), no stale MIT claims outside decision-log history
- [ ] SECURITY/GOVERNANCE/trademark-policy/CoC/templates exist (G1 checklist walked — [`../../../open-source-plan.md`](../../../open-source-plan.md) §1)
- [ ] Trademark sanity check executed (OQ-6, Bora) — before Aricoma diligence reads the repo
- [ ] Findings reviewed by Bora → `sv-p2-review-input.md`

## The calendar (carried item 2 — PROPOSAL, Bora ratifies in S0·T4)

Anchors: **Nov 2026 = HARD** (ai-platform engagement, SV-P5) · Aricoma window = aspirational (debut before it concludes) · today = 2026-07-11.

| Window | Phase | Note |
|---|---|---|
| Jul w2–w4 (11.–31.7.) | SV-P1 + SV-P2 (parallel) | S4 paced by Central namespace verification lead time — start S0·T2 immediately |
| Aug w1–Sep w2 (1.8.–11.9.) | SV-P3 | resolver convergence = first planning item (ai-platform repo connected, RQ-1..5); time-boxed — the plan's own risk line |
| Sep w3–Oct w4 (14.9.–30.10.) | SV-P4 | docs scaffold + quickstart in the FIRST week (RO-27) |
| Aug→Oct, rides gates | SV-P5 | repoint ai-platform incrementally as gates open; **buffer: done by 1.11.** |
| Nov | SV-P6 debut | acceptance run by an outsider; before Aricoma close if at all possible |

Collision rule (plan §risks, restated): **if Aricoma timing and November collide, SV-P5 wins.**

## Standing facts the tasks rely on (verified 2026-07-11)

- Published today (GH Packages `Collite/tatrman`): `ttr-{parser,writer,semantics}` ≤ 0.9.1 (`kotlin/v0.9.1`), `ttr-metadata(-git)` 0.8.6+ (bundle rides `kotlin/v*` — Gotcha 6), `ttr-{plan-proto,translator}` **0.8.5 = pre-rename, still `proteus.v1`** (`kotlin-translator/v0.8.5`); PyPI: `python-plan/v0.8.4` (pre-rename). The `translate.v1` rename exists ONLY at `0.0.1-LOCAL` — publishing it = S1's core.
- kantheon pins (libs.versions.toml): modeler 0.8.6 · ttr-metadata 0.8.6 · translator/plan-proto **0.8.5** · tatrman-server libs **0.0.1-LOCAL via mavenLocal** — clean-machine builds need tatrman-server `publishToMavenLocal` first (review-input ⚑5). S1/S2 retire both.
- tatrman-server pins: plan-proto + translator **0.0.1-LOCAL** (translate.v1); metadata 0.8.6; **no vendored `query-translator`** — the contracts-§7 temporary module never materialized because `ttr-translator` already lives in tatrman with the service consuming the artifact. Gate 2 is therefore a **publish + repoint**, not a code move.
- Branches to fold before tagging: tatrman `sv-p0-server-fork` · tatrman-server `sv-p0-move` · kantheon `sv-p0-kantheon-close` (Bora folds; tags only from `master`).
- Registry roles (RO-17): **Maven Central = public** · GH Packages = staging (anonymous pulls fail) · GHCR = images (+ chart at SV-P4). Image path = **flat `ghcr.io/collite/<name>`** (RO-28).
- License state: both repos already Apache-2.0 LICENSE; tatrman-server has NOTICE, tatrman does NOT; **zero SPDX headers anywhere**; no SECURITY/GOVERNANCE/CoC in either repo; tatrman CONTRIBUTING.md exists, no DCO.
