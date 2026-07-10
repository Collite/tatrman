# Tatrman Platform (PL) — Phased Implementation Plan

> **❄ FROZEN 2026-07-10 — OPERATE-TIER REFERENCE (RO-2).** The 2026-07-10 ecosystem redraw (control room §7 → "Ecosystem redraw amendment batch") parked the operate tier this document targets: it wakes with **satellite (c)** — the first operated estate. Until then this document is a *reference*, amended only by tier-disposition banners. Known dispositions: STRAT-6 re-sequences the strangler (③'s hall content is done in kantheon as open-spine work; ④⑦ park; ⑤'s validate half is open and done; ⑥ moved to the core critical path); "MIT" reads Apache-2.0 (STRAT-3); pre-J-v2 persona names (Radegast, Zorya, Theseus, Argos, Kyklop, Arges/Brontes/Steropes) read per the rename map in `../design/naming-260710.md`; contracts ⚑ flags split per RO-13. **Live near-term docs: `docs/server/architecture.md` + `docs/server/plan.md`.**
>
> **Status:** consolidated 2026-07-09 from [`../design.md`](../design.md) §8 (strangler ①–⑦) with the S1 amendment batch pulled in as Phase 0. This document = overall plan + phases with deliverables, pre-flight conditions, and definitions of DONE. **Per-phase task lists (6–8 tasks each, TDD-ordered, with an overall task-management document) are generated separately, phase by phase, when implementation starts — PL-P0 and PL-P1 lists exist ([`tasks/00-task-management.md`](./tasks/00-task-management.md)); later phases get theirs after the preceding phase's review.**
> Companions: [`architecture.md`](./architecture.md), [`contracts.md`](./contracts.md).

---

## 0. Overall plan

The spine follows the strangler ①–⑦ (D-5, per-move mini-arc: **move → adopt → delete**), with two planning additions: **contracts first** (PL-P0 records the S1 amendment batch and ratifies `contracts.md`, because ②'s seam client and ⑤'s security block compile against those contracts), and **the E track split out** (PL-P5: emit SPI + support packages — mostly MIT work that parallelizes against the platform track once the SPI contract is pinned). P2 (one-way arrow) must hold at **every intermediate state**; life 1 stays green in CI throughout (Q-6's third clause).

```
PL-P0 contracts ─► PL-P1 (②) ─► PL-P2 (③) ─► PL-P3 (④) ─► PL-P4 (⑤) ─► PL-P6 (⑥) ─► PL-P7 (⑦, acceptance)
 (S1 batch,        (seam client,   (Radegast,      (Charon)       (Argos+SPI,                (Zorya, deploy views,
  ratify ⚑s)        Veles v1,       workers,                       Perun)                     harvest scheduling,
                    reader          event spine)                                              Q-6 hero run)
                    Designer)          │
                                       └──► PL-P5 (E: emit SPI + bash/Kestra/Airflow3 packages) — starts after P0;
                                            MIT halves parallel to P2–P4; platform adapter half needs P2's door
```

**Repos touched:** `tatrman` (MIT halves: seam client, emit SPI, security-block grammar/generator, Designer, conformance), **`tatrman-platform` (new, born in PL-P1)**, **`tatry` (new, born in PL-P1)**, `kantheon` (donor via `git filter-repo`; adoption arcs in PL-P6 and per-move deletes).

**Global pre-flight**

1. Strangler ① complete: ttr-metadata arc (M0–M4; `org.tatrman:ttr-metadata` + `-git` published, world resolver + fingerprint live) and ttr-translator arc (A1–A3; `ttr-plan-proto` + `ttr-translator` published at ≥0.8.0).
2. TTR-P toolchain emits F-lite bundles with `manifest.json` v1 and passes `ttrp-conform` (Phase 3 of the TTR-P v1 plan) — the E-5 graduation in PL-P0 amends a *working* contract.
3. Kantheon donor services healthy at a pinned commit (transplant baseline; kantheon `f2e2efb0…` is the recorded translator pin — re-pin at each move).
4. `docs/platform/design.md` + decision log frozen (design effort closed 2026-07-09). ✅

**Global definition of DONE (Q-6, verbatim — the scope bar)**

> *"Platform v1 is done when, on a fresh `tatry`-deployed instance: (life 2) the hero program deploys via envelope, runs nightly under a service principal on Arges+Steropes with Charon transfers, RLS enforced, runs and column lineage visible in the Designer; (life 3) the same program delegates through Airflow 3 with lineage harvested back and exported to OpenMetadata, and the Kestra emit plugin passes conformance; (life 1) unchanged and green throughout — the MIT toolchain never regresses."*

---

## PL-P0 · Contract pinning + the S1 amendment batch (tatrman repo, docs + grammar surface)

**Pre-flight:** global pre-flight 1–4; the ⚑ flags in `contracts.md` reviewed by Bora (a `/review`-cadence pass over this planning set counts).

**Deliverables:** the S1 batch recorded as ONE batch per the family amendment discipline (TTR-P/TTR-M contracts + changelog entries): F-4-i runtime-params grammar surface; F-4-iv on-failure vocabulary; FQ-4 Tatrman-executor capability manifest (contracts §7 artifact recorded in TTR-P docs); E-5 manifest-contract graduation to v2 incl. lineage + provenance sections (contracts §6); T6 type-manifest-ownership amendment (emit plugins ship executor-type manifests); K `extends`-platform-world grammar surface (T4-adjacent); H-1 `security`-block grammar reservation (contracts §11 shape); plus the S6 CLAUDE.md line ("the MIT toolchain is a client of the platform's published snapshot contract") and ratification of `contracts.md` v1 (⚑ flags resolved or amended, changelog v2 cut).

**DONE when:** TTR-P `architecture/contracts.md` changelog carries the batch entry; `TTR.g4` grammar work items for params/on-failure/extends-world/security-block are cut through the grammar-master process (specs recorded; implementation lands in the phases that consume them — extends-world in PL-P1, params/on-failure in PL-P2, security block in PL-P4); `contracts.md` here is at v2 with zero unresolved ⚑; `pnpm -r test` + Gradle suites green (no behavior change).

## PL-P1 · Strangler ② — seam client · `tatrman-platform` bootstrap · Veles v1 · reader Designer (task lists exist)

**Pre-flight:** PL-P0 DONE.

**Deliverables:** *(tatrman, MIT)* snapshot-archive reader/writer + deterministic packing (contracts §2); `ttr.lock` + `ttr fetch` + `--frozen`/`--offline` (§3); `StatisticsSource` + stats entries (§4); compile record (§5); manifest v2 emission behind the E-5 graduation (§6); `MetadataServerSource` binding; `ttrp-conform` mode-drift suite (B-3); K `extends`-platform-world grammar implemented in TTR-M/ttr-metadata resolver; `ttr import-schema` CLI (§12). *(tatrman-platform, new repo)* Gradle-only bootstrap with `cz.tatrman` group, Tatrman Platform License file (legal text = the D-5 ② task), P2 dependency-rule CI, helm skeleton; **Veles v1** (contracts §16: snapshot resolve/serve from the resolved composed world, per-object stats store + endpoints, `ttrm/*` read surface with the shared H-2 ingress module, event-ingest skeleton, platform-world git repo + project-roster config per K); pinakes verify-then-place; **reader Designer** (MD6 `VelesDataSource` adapter + catalog/model-graph/TTR-P-program-graph panels; Designer Extensions surface §10); **OpenMetadata export organ + `ttr-connect-openmetadata` export half** (§18; import/harvest scheduling = PL-P7). *(tatry, new repo)* olymp-shaped bootstrap deploying Veles.

**DONE when:** on a `tatry`-deployed instance, `ttr fetch && ttr compile --frozen` against Veles produces a bundle **byte-identical** to the standalone compile of the same pinned inputs (mode-drift suite green in CI); the Designer (browser, Veles backend) renders catalog + model graph + the hero program's graph from its manifest; a stats entry served by Veles demonstrably changes an optimizer decision and appears verbatim in the compile record; OpenMetadata (CI instance) receives exported catalog entities; life 1 suites untouched and green.

## PL-P2 · Strangler ③ — program door: Radegast + workers/Kyklop transplant + event spine

**Pre-flight:** PL-P1 DONE; the PL-P0 grammar-master work items for params/on-failure ready to implement (spec text frozen); kantheon transplant commit re-pinned.

**Deliverables:** **Radegast** (envelope store + validation per contracts §13; wave-walking executor with F-4 semantics: params, retries, wave-resume, on-failure islands; run store with externalized state + outbox; door API §15; run ids §14); **Kyklop + Arges/Brontes/Steropes transplanted** (filter-repo, package sweep, F-5-γ priority+admission at dispatch, **CQ-4 mapping layer**: a named table translating T6 worker capability manifests into Kyklop's routing vocabulary — mapping first, vocabulary convergence later, contracts §1 row 22); params/on-failure grammar implemented (the PL-P0 specs) so manifests v2 carry them; **minimal secret-store SPI + k8s binding** (§17 — dispatch injection is needed the day the platform runs an island; full hardening rides PL-P4); **event spine v1** (§14 proto, outbox forwarder, Veles ingest + run/lineage reads; Designer runs + column-lineage panels as Designer Extensions); helm charts for all of it; kantheon mini-spine untouched (P2-legal duplication).

**DONE when:** the hero envelope deploys (`artifact:` variant) and a **manually fired** run executes end-to-end on Arges+Steropes (transfers still direct/staged — Charon lands next), parks-resumable on injected failure, resumes correctly iff fingerprint unchanged, degrades restart-only past retention (SZ-2); run events + cited column lineage visible in the Designer; canary-credential suite green (H-5); `POST /v1/runs` round-trips for an external caller (the E-3 adapter's future seam).

## PL-P3 · Strangler ④ — Charon

**Pre-flight:** PL-P2 DONE.

**Deliverables:** Charon transplanted (Materialize/Stage/Copy/Evict); Radegast's transfer edges routed through it with per-transfer secret resolution (source×target pair); FQ-6 staging lifecycle wired to Evict (success/abandonment/expiry); the accepted quota hole documented in ops runbook (revisit trigger recorded).

**DONE when:** the hero run's cross-engine edge moves through Charon with Arrow-IPC staging; Evict provably cleans all three lifecycle ends; conformance outputs unchanged vs PL-P2 (movement is transparent to results).

## PL-P4 · Strangler ⑤ — Argos + validator SPI · Perun

**Pre-flight:** PL-P3 DONE; security-block grammar spec (PL-P0) ready for implementation.

**Deliverables:** Argos transplanted **with its HOCON store verbatim** (RLS/DENY/MASK v1 by construction) into the hall on both doors; **validator SPI** (§9) with kantheon's LLM Guard repackaged as the proving plugin (query door); **Perun** grown from whois (directory + bundle build/sign/serve per §19); shared-ingress hardening sweep (all doors on the one H-2 module); security-block **grammar + MIT generator** in tatrman (§11) feeding Perun's bundle pipeline (deny-overrides, fail-closed role validation); PEP wiring (doors deploy/run authz, Veles coarse catalog visibility, OPA sidecars, bundle-hash citation in run/audit events); H-7-β rekey arc **scheduled, not executed** (HQ-5 decided inside it).

**DONE when:** an unauthorized principal cannot deploy/run/see the hero program (fail-closed proven, incl. bundle-expiry behavior per SZ-4); the hero run executes RLS-filtered on Arges (row counts differ by principal, by construction); a `security` block change flows sugar→fragments→signed bundle→PEP without hand-editing; audit events with bundle hashes land in Veles; the LLM-Guard plugin loads on the query door and its absence yields the deterministic default.

## PL-P5 · Orchestration support packages (E) — parallel track

**Pre-flight:** PL-P0 DONE (SPI + T6-ownership contracts). MIT halves (SPI, bash, Kestra emit) run parallel to PL-P2–P4; the Airflow adapter + harvest halves need PL-P2's door + event spine.

**Deliverables:** `ttrp-emit-spi` module + **bash emitter extracted** as the proving plugin (§8 — SPI proven by extraction); H-6 determinism kit in `ttrp-conform` (`emit-determinism` verb) + PGP verify-if-signed loading; **`ttr-emit-kestra`** (data-defined YAML target — the SPI's second consumer, proving it's not a bash-shaped hole); **`ttr-emit-airflow3`** (door-calling DAG for platform worlds per E-3-α-1; native DAG for standalone worlds per E-3-β); executor-type manifests shipped in each plugin; **`ttr-connect-airflow3`** run-harvest connector (§18) mapping Airflow run events into the spine; registration = world instance entries (no new mechanism — E-2).

**DONE when:** bash plugin emits byte-identically to the pre-extraction emitter over the conformance corpus; Kestra plugin **passes the determinism kit** (a Q-6 clause); an Airflow 3 (CI instance) DAG emitted for the platform world fires the door and its run + lineage appear in Veles indistinguishably from native runs; a standalone-world Kestra/Airflow native DAG runs the hero islands with user-held credentials only (H-8 line).

## PL-P6 · Strangler ⑥ — kantheon query-door adoption

**Pre-flight:** PL-P4 DONE (the hall is whole: Argos+Perun live). This is a **kantheon-repo arc** with platform-side support.

**Deliverables:** Theseus slimmed + package-swept as the platform query door; kantheon agents (via Ariadne) call it with client-credentials principals; **HQ-4 decided here**: agent Keycloak-role pass-through vs mapping into platform grants; kantheon mini-spine deleted (the "delete" leg of the mini-arc); CQ-6 ad-hoc validation against Veles's served world.

**DONE when:** kantheon agent queries flow through the platform hall under Argos+Perun policy; the duplicated spine is gone from kantheon; kantheon's own suites green against the adopted door.

## PL-P7 · Strangler ⑦ — Zorya, scheduler surface, closing the loop + Q-6 acceptance

**Pre-flight:** PL-P2 DONE (door API); PL-P5 Airflow package DONE for the delegation clause; PL-P4 DONE for principal-bound triggers.

**Deliverables:** **Zorya** (cron/manual/upstream-run triggers firing the door under envelope principals — F-4-v); harvest **scheduling** turned on (Veles refresh organ driving connectors — the post-② rider) + OpenMetadata **import** half (PR-shaped proposals, I-3); Designer deploy/run operational views; **edit mode + the kind-parameterized registration wizard graduate here iff the workspace machinery is proven** (G-4/G-1 condition, design §8 ⑦ — the wizard's writes are commits to the K platform-world repo; if not proven by acceptance time they slip post-v1 as an explicit planning call, since no Q-6 clause requires them) with GQ-4 edit-session quota enforcement (SZ-7); tatry umbrella deployment of the full roster; **the Q-6 acceptance run** executed and recorded as `docs/platform/implementation/acceptance-v1.md`.

**DONE when:** Q-6 holds verbatim on a fresh `tatry`-deployed instance — nightly cron fires the hero under its service principal (life 2 complete: RLS + Charon + Designer-visible runs/lineage); Airflow delegation + harvest-back + OpenMetadata export green (life 3); `pnpm -r test`, Gradle suites, `ttrp-conform` (incl. mode-drift + determinism kits) green across tatrman at the closing commit (life 1).

---

## Risks & watch items

- **Byte-determinism of archives/emit (PL-P1/P5):** zstd version drift or tar metadata leaks break content addressing — pin the compressor version in the toolchain, test archives against golden hashes on two OSes. Tripwire: any golden-hash churn without a content change.
- **K composition semantics (PL-P1):** `extends`-platform-world is new grammar feeding the fingerprint — property-test that composition is order-insensitive and that contradiction detection is exact (`TTRP-LCK-004`), or parity dies quietly.
- **Transplant drift (PL-P2–P4):** kantheon moves while we strangle — re-pin the donor commit per move; each mini-arc ends with the kantheon-side delete, never leaving two active copies past its phase review.
- **Secret delivery via env (/proc caveat, PL-P2):** accepted for v1 parity; the file-mount hardening knob is recorded — revisit if a pen-test flags it.
- **OPA sidecar operational surface (PL-P4):** fail-closed at expiry means a broken Perun eventually stops the hall (by design, SZ-4 window) — alert on bundle staleness at half-expiry.
- **Airflow/OpenMetadata CI instances (PL-P5/P7):** heavyweight test dependencies — containerized, version-pinned, and exercised only in the conformance lane, not unit lanes.
- **Scope magnetism at ⑦:** Zorya is deliberately thin (F-1-γ); calendars/sensors/backfill belong to registered orchestrators — FQ-1 stays parked.

## Task lists

Generated 2026-07-09 for **all phases PL-P0 → PL-P7**: see [`tasks/00-task-management.md`](./tasks/00-task-management.md) — 24 stage-level mini task lists of 6–8 checkboxed tasks, TDD-ordered, with verify commands per stage. Everything after PL-P1 was pre-generated at Bora's request, ahead of the phase reviews this plan originally gated them on — **before starting any phase, re-validate its list against all preceding `/review` outcomes**; review outcomes win over pre-generated lists, and the later the phase, the more amendment its list should be expected to need.
