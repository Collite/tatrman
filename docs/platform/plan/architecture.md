# Tatrman Platform (PL) — Architecture

> **❄ FROZEN 2026-07-10 — OPERATE-TIER REFERENCE (RO-2).** The 2026-07-10 ecosystem redraw (control room §7 → "Ecosystem redraw amendment batch") parked the operate tier this document targets: it wakes with **satellite (c)** — the first operated estate. Until then this document is a *reference*, amended only by tier-disposition banners. Known dispositions: STRAT-6 re-sequences the strangler (③'s hall content is done in kantheon as open-spine work; ④⑦ park; ⑤'s validate half is open and done; ⑥ moved to the core critical path); "MIT" reads Apache-2.0 (STRAT-3); pre-J-v2 persona names (Radegast, Zorya, Theseus, Argos, Kyklop, Arges/Brontes/Steropes) read per the rename map in `../design/naming-260710.md`; contracts ⚑ flags split per RO-13. **Live near-term docs: `docs/server/architecture.md` + `docs/server/plan.md`.**
>
> **Status:** consolidated 2026-07-09 from the converged platform design ([`../design.md`](../design.md), ground truth = decision log in [`../design/00-control-room.md`](../design/00-control-room.md) §7). This document is the implementation-facing statement of the solution architecture for **Platform v1** as bounded by the Q-6 acceptance statement. Wherever this document and the decision log could be read to differ, the log wins.
> Companions: [`contracts.md`](./contracts.md), [`plan.md`](./plan.md).

---

## 1. What the Platform is (and is not)

**The Platform is the connected mode of the Tatrman toolchain (FI-1/FI-3).** Two first-class modes exist: *standalone* (repo-only, static, offline — MIT) and *connected* (the Tatrman Platform — Tatrman Platform License). Connected adds capability (live worlds, statistics, execution, scheduling, security, lineage, collaboration); it never fixes standalone (P1). No language feature gates on the Platform.

**The Platform is not a compiler (A-1).** The edition rule is "compile vs operate": everything that turns source into artifacts — parse, check, optimize, emit, format, lint, view — is MIT and lives in the `tatrman` repo; everything that runs, stores, schedules, secures, or serves shared state is Platform and lives in the new `tatrman-platform` repo. The optimizer, including its statistics-driven algorithms, is fully MIT — the Platform's edge is *data*, not secret algorithms.

**The Platform executes manifests, not compilers (F-2-β).** The deployed unit is the verbatim standalone bundle (byte-identical under hard parity B-3) wrapped in a deployment envelope. The bundle manifest's wave graph is authoritative; `run.sh` is a rendering of it. Compatibility keys on the E-5 manifest schema version (current + N−1 minimum, contracts §4).

**Scale contract.** v1 is a single-organization anchor (multi-tenant SaaS parked). One platform instance serves: one platform world, N project repos (server config), the two doors, one worker hall.

## 2. Repo & artifact topology (D-1/D-2/D-4/J-5)

```
tatrman (MIT, pnpm+Gradle)        tatrman-platform (TPL, Gradle-only)     tatry (infra)
  org.tatrman:*                      cz.tatrman:*                            cluster defs =
  ttr-parser / ttr-writer            veles/ radegast/ zorya/ theseus/        "deployment
  ttr-metadata (+ -git)              argos/ kyklop/ charon/ perun/           instance #1"
  ttr-plan-proto / ttr-translator    workers: arges/ brontes/ steropes/      (helm charts live
  ttrp-* (cli, emit, conform, …)     designer-extensions/ connectors/         in tatrman-platform
  Designer frontend (React, MIT)     helm/ (product packaging)                beside services)
        │  publishes                       │  publishes
        ▼                                  ▼
        └── build-time deps: tatrman ──► tatrman-platform ──► kantheon (P2, one-way,
            must hold at EVERY intermediate state of the strangler)
```

- **License boundary = repo boundary (D-2).** `tatrman` = 100% MIT; `tatrman-platform` = Tatrman Platform License; the Maven group id makes it physical: `org.tatrman:*` MIT · `cz.tatrman:*` Platform. No per-directory carve-outs.
- **Naming register (J):** platform-native services get Slavic names (Veles, Radegast, Zorya, Perun); transplants keep Greek (Theseus, Argos, Kyklop, Charon, Arges, Brontes, Steropes). Plugin coordinates: `ttr-emit-<target>` / `ttr-connect-<system>`.
- **Transplant sources** (kantheon repo, history-preserving `git filter-repo`, proto/package roots swept on arrival — DQ-2): `services/{theseus,argos,kyklop,charon}`, `workers/{arges,brontes,steropes}`, `infra/whois` (→ Perun). `services/proteus` dissolves (compile-time `ttr-translator` replaces it); `services/pinakes` = verify-then-place during bootstrap; `services/metis` stays kantheon, flagged. `tatrman-semantics` repo archived (DQ-3).
- **Contract ownership (D-3):** toolchain-touched ⇒ tatrman-owned (MIT); service-internal ⇒ platform-owned; kantheon owns nothing shared. The full inventory with owners is contracts §1.

## 3. Component architecture — two doors, one hall (C-3)

```
            triggers                    external orchestrators          agents / Designer
        ┌────────────┐                (Airflow 3 door-calling DAG)        previews/debug
        │   Zorya    │ cron·manual·          │                                │
        │ (triggers) │ upstream-run          │ door frontend contract         │
        └─────┬──────┘                       │ {start, poll/subscribe,        │
              ▼                              ▼  cancel}                       ▼
      ╔═══════════════════════════════════════════╗            ╔═════════════════════╗
      ║ Radegast — PROGRAM DOOR (executor)        ║            ║ Theseus — QUERY DOOR║
      ║ walks the manifest wave graph; run state  ║            ║ one validated plan, ║
      ║ externalized to executor-owned run store  ║            ║ synchronous         ║
      ╚═══════╤═══════════════════════════════════╝            ╚══════════╤══════════╝
              │ per island                                                │
              ▼                     THE HALL (one path to data)           ▼
        ┌───────────┐  validated  ┌────────────┐  dispatch  ┌───────────────────────┐
        │   Argos   │────────────►│   Kyklop   │───────────►│ Cyclopes workers      │
        │ validator │             │ dispatcher │  priority+ │ Arges(PG)·Brontes(MS) │
        │ +val. SPI │             │            │  admission │ ·Steropes(Polars)     │
        └───────────┘             └────────────┘  (F-5-γ)   └───────────────────────┘
              │ per transfer edge: Radegast calls ┌────────┐
              │  (accepted v1 hole: outside quota)│ Charon │ Materialize/Stage/Copy/Evict
              ▼                                   └────────┘
   ┌─────────────────────┐   events (runs·lineage·audit)  ┌──────────────────────────┐
   │ Perun — PDP         │◄──bundle pulls──┐              │ Veles — metadata server  │
   │ directory + builds/ │      PEPs at doors,hall,       │ B contract·Designer     │
   │ signs/serves Rego   │      Veles, Designer bck.      │ reads·lineage organ·    │
   │ policy bundles      │      evaluate locally          │ export connectors·      │
   └─────────────────────┘      (fail-closed at expiry)   │ harvest scheduling      │
                                                          └──────────────────────────┘
```

**Per-service responsibilities and one-line contracts are fixed in [`../design.md`](../design.md) §5** and are not restated here. Key structural facts for implementers:

- **Radegast** is a central executor with run state externalized to a durable, executor-owned run store *from day one* (F-3-α); the reconciler/workers-pull architecture is the recorded growth direction, not v1. Radegast owns run writes and "what is running now"; Veles ingests events and serves all catalog/Designer/history reads — catalog down ≠ execution down.
- **Veles** is built on `org.tatrman:ttr-metadata` (world resolver, fingerprints) + `ttr-metadata-git` (git-backed storage). It *is* the engine registry because registration is world content (E-2-γ). Four consumer classes: compiler seam (snapshot archives + stats), Designer (model graph serving), runtime (world verification, run history), harvest (connector scheduling via the refresh organ).
- **Both doors converge on the same hall**; everything passes Argos. The LLM Guard leaves Argos as a validator-SPI plugin (C-5-i): Platform defines the hook, kantheon ships the plugin, no plugin = deterministic default.
- **Perun evaluates nothing at runtime** — PEPs pull content-hashed signed bundles and evaluate locally; every run record cites the bundle hash in force (H-4).
- **Ariadne stays in kantheon** as the agents' facade, thinning on its own schedule; kantheon keeps its mini-spine until strangler ⑥ (accepted, time-boxed, P2-legal duplication).

## 4. The mode seam (B) — what the Platform serves the toolchain

The compiler is mode-blind behind the `ttr-metadata` source SPI; connected is a *binding* (`MetadataServerSource`), never a behavior. The Platform's half of the seam is Veles serving the **B contract**:

```
 tatrman toolchain (MIT)                         Veles
 ┌──────────────────────────┐  fetch (explicit/policy)  ┌───────────────────────────┐
 │ ttr fetch ──────────────────────────────────────────►│ snapshot endpoint:        │
 │   ttr.lock (committed,   │◄─────────────────────────│  content-addressed archive │
 │   pins canon by hash)    │   archive by hash         │  of the RESOLVED composed │
 │ stats source (floats,    │◄─────────────────────────│  world (K)                │
 │   max-age, per-object)   │   stats endpoint          │ per-object stats endpoint │
 │ compile = pure fn of     │                           │  {qname, schema-hash,     │
 │   recorded snapshot(B-3) │   NEVER mid-compile (B-5) │   observed-at, values}    │
 └──────────────────────────┘                           └───────────────────────────┘
```

Seam-legality (B-4, standing): the seam admits **data and diagnostics; never identity, never side effects**. Everything identity-bound or effectful (policy, deployment, secrets, scheduling) happens in platform phases after compile — the deployment envelope is exactly the container for what the seam bans. Canon (models, worlds, manifests, emit-plugin versions) pins in `ttr.lock`; observation (stats) floats, keyed per object, embedded verbatim in the compile record; the world fingerprint stays stats-free forever (BQ-2). Wire formats: contracts §2–§3.

**World composition (K):** project worlds `extends`-declare the platform world; Veles's resolver composes behind the declaration; contradiction = compile error (platform entries authoritative); the platform world lives in its own git repo (admin edits = commits; edit rights = an H-3 policy object); the lock pins the referenced platform world by content hash; project roster = server configuration.

## 5. Runtime flows

**Deploy (life 2, step 1).** `ttr` CLI / Designer → Radegast deploy endpoint: envelope `{name@version, bundle hash, triggers, typed param bindings, connection refs, policy/principal refs, {lock hash, compile record}}` + the content-addressed bundle. PEP checks deploy grant + grant-to-use-principal (H-2). Bundle T6 fingerprint verified against Veles's served resolved world.

**Nightly run (life 2, step 2).** Zorya cron trigger → door `start(envelope ref, params)`; Radegast opens a run (run-id per contracts §6/FQ-2), executes the manifest wave graph: per island Argos-validate (RLS predicate injection, deny/mask) → Kyklop-dispatch (priority+admission; interactive > batch, batch yields at the dispatch slot, running work finishes) → worker executes; per transfer edge Radegast calls Charon. Secrets resolve at dispatch through the secret-store SPI and inject as `TTR_CONN_*` env (H-5; never at rest, canary-tested). Retries per island (attempt-scoped staging wiped); wave-level resume gated on unchanged snapshot fingerprint; on-failure islands run iff source failed. Radegast emits run/lineage/audit events → Veles ingests → Designer shows runs + column lineage (static, cited from the manifest's lineage section — CQ-5).

**Delegation (life 3).** Airflow 3 runs an emitted door-calling DAG (E-3-α-1): task = `start` + `poll` against Radegast — a scheduling frontend; the hall is intact, policy holds. The per-orchestrator harvest connector maps Airflow run events into the F-6-β ingest contract; Veles's export connectors project lineage/catalog onward to OpenMetadata (OpenLineage column-lineage facet). Standalone delegation = native DAG emit (MIT emit target, credential-bounded not policy-bounded, H-8) — not a frontend.

**Designer read path.** Browser Designer (MIT React, backend-selectable) → Veles read APIs: catalog (coarse-visibility PEP), model graphs, TTR-P program graphs (rendered from the E-5 manifest), runs, column lineage. Edit mode (writes-through-git, session = branch, save = commit, publish = repo policy; G-1-γ) is designed now, built behind a flag, graduates at ⑦.

## 6. Security architecture (H)

- **Identity:** one shared ingress module at every door — bearer-only JWT from the IdP, enrichment-never-authority, fail-closed. Machine callers = client-credentials service principals. Scheduled runs execute as an explicit, envelope-named service principal (required, no ambient default); deployers need a grant to *use* a principal.
- **Policy:** Rego + structured data bound to metadata qnames, built/signed/served by Perun as content-hashed bundles; PEPs (doors, hall PEP = Argos data plane, Veles catalog visibility, Designer backend) evaluate locally, fail-closed at bundle expiry. The TTR `security` block (MIT sugar, tatrman-side generator) deterministically generates standard fragments; composition = deny-overrides; generated fragments never hand-edited; fingerprint-neutral.
- **Argos arrives working:** HOCON store verbatim at ⑤ (RLS v1 by construction); the HOCON→bundle rekey is a follow-up arc (H-7 β-step, HQ-5).
- **Secrets:** secret-store SPI (K8s Secrets default binding; Vault/cloud later), dispatch-time env injection (`TTR_CONN_*` verbatim — parity with bash-land), manifest-scoped per island/transfer, never-at-rest invariant CI-canary-tested, no endpoint returns secret material, store-unreachable = island pre-flight failure, secret-zero = deployment config.
- **Trust roots:** IdP (identity) · publisher keys (artifacts: emit plugins verify-if-signed v1, require-signed = policy knob; policy bundles PDP-signed) · the `ttrp-conform` determinism kit (double-compile byte-compare) closes stated-vs-verified for plugins.
- **Compile-time policy is advisory-only** (hard parity forces it); the block is at deploy and run time.

## 7. Tech stack

| Layer | Choice | Provenance |
|---|---|---|
| Platform services | Kotlin · JDK 21 · Ktor (CIO/Netty) · kotlinx.serialization · Gradle version catalog | kantheon house patterns (see kantheon `EXAMPLES.md` §1–§2: `installKtorServerBase`, `buildJsonObject`, sealed-interface DTOs) |
| Wire formats | JSON (kotlinx) for REST/door/event surfaces · protobuf for plan/worker protos (`ttr-plan-proto` precedent) | D-3, TR-3 |
| Run store (Radegast) & event outbox | PostgreSQL | ⚑ planning call — see contracts §14 |
| Veles storage | git repos (canon; via `ttr-metadata-git`) + content-addressed archive/blob store on the filesystem (S3-able later) | B-1/BQ-1, I-3 |
| Policy engine | OPA distributed as sidecar per PEP, pulling Perun's bundle endpoint (standard OPA bundle API) | ⚑ planning call — see contracts §19 |
| Observability | OpenTelemetry (shared otel-config pattern) → Alloy/Tempo/Prometheus/Loki | kantheon EXAMPLES §8 |
| Designer | React (existing MIT frontend in tatrman) + Designer Extensions surface | G, Q-4-a |
| Deployment | K8s; helm charts in `tatrman-platform/helm/`, instance = `tatry` repo | D-4 |
| Toolchain-side (seam client, emit SPI, security-block generator) | Kotlin modules in `tatrman/packages/kotlin/*` (`org.tatrman:*`) | A-1 |

## 8. Testing strategy

- **Unit:** per class/function; Kotest (platform, house style) · Vitest (Designer). Written FIRST per the TDD task discipline.
- **Component:** inter-class within a service — e.g. envelope validation → run-store write → event emit inside Radegast against a fake hall; Veles resolver + snapshot assembly against fixture git repos; Perun bundle build/sign/serve against a fake PEP puller.
- **Contract/conformance:** `ttrp-conform` gains the **mode-drift suite** (B-3: same program + same resolved inputs ⇒ byte-identical artifacts across bindings) and the **emit-determinism kit** (H-6: double-compile byte-compare). Event/envelope/door schemas get fixture-backed round-trip tests in the owning repo (D-3).
- **Security invariant tests:** CI plants canary credentials and asserts their bytes appear in no envelope, run store row, log line, lineage event, or artifact (H-5); fail-closed tests at bundle expiry (H-4).
- **No full E2E in task lists** — integration testing has a separate flow; the Q-6 hero acceptance run is the plan-level DONE gate (plan §PL-P7), not a per-phase test.

## 9. Version roadmap (trigger-driven, from the parked ledger)

| Version | Adds | Trigger | Constrains now |
|---|---|---|---|
| v1.x | Dagster support package · DataHub connector | first post-v1 demand | connector frame stays two-output-kind (EQ-4) |
| v1.x | Argos HOCON→Perun-bundle rekey (H-7 β) | follow-up arc after ⑤ | run records already cite bundle hash |
| v2 | FQ-1 backfill · FF atomic publish (staging+swap) | first real demand | FF grammar stays reserved; params designed trigger-bound |
| v2 | F-3-γ reconciler/workers-pull · Charon-as-worker-kind | growth/contention | run state externalized day one; Charon quota hole documented |
| v2 | multi-tenant · F-5-δ identity-priced quotas · live co-editing · SLSA attestation | demand | single-org anchor; git write model |

## 10. Invariants (cite by ID; enforcement named)

1. **P1 — standalone is not a demo** — no language feature gates on the Platform; life 1 stays green in CI throughout the strangler.
2. **P2 — one-way arrow** `tatrman → platform → kantheon`, at every intermediate state — enforced by Gradle dependency rules + CI check in both repos.
3. **P3 — no miracles** — explicit or deterministically derived from declared defaults; otherwise an error.
4. **B-3 hard parity** — compile = pure function of the recorded snapshot; `ttrp-conform` mode-drift suite enforces it.
5. **B-4 seam legality** — data + diagnostics only; never identity, never side effects — review gate on every seam change.
6. **B-5 fetch-then-compile** — the compiler never talks to a server mid-compile; unreachable server = stale snapshot only.
7. **D-3 ownership** — toolchain-touched ⇒ `org.tatrman` (MIT); service-internal ⇒ `cz.tatrman` — checked at contract creation (contracts §1).
8. **H-5 secrets** — never at rest (CI canary) · no endpoint returns secret material.
9. **H-1 deny-overrides** — sugar grants, hand Rego takes away; generated fragments never hand-edited.
10. **Determinism obligations** — emit plugins (E-1, H-6 kit) and harvest connectors (I-3: same external state ⇒ same proposal bytes).
11. **"Robots write through git"** — every canon writer (humans, Designer, harvest connectors) produces reviewable commits; stats bypass by temperament only.
12. **The manifest's graph is authoritative; `run.sh` is a rendering** — drift = emit bug caught by conformance.
13. **Bearer-only · enrichment-never-authority · fail-closed** at every ingress.
