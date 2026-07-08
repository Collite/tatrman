# Tatrman Platform — Design-Space Map

> The option tree. One section per workstream: **Question → Branches → Cross-links → Open**.
> Deliberately divergent: options are added, never silently removed. Companion: [`00-control-room.md`](./00-control-room.md).

---

## A · Product split, scope & licensing

**Question:** given FI-1..4, what is the *rule* that decides which capability lands MIT-side vs Platform-side — and what happens to the grey-zone items?

**Branches:**
- **A-α · Capability rule: "compile vs operate".** Anything that turns source into artifacts (parse, check, optimize, emit, format, lint, view) = MIT; anything that *runs, stores, schedules, secures, or serves* = Platform. Crisp; the optimizer stays fully OSS.
- **A-β · Capability rule: "single-player vs multi-player".** MIT = everything one engineer does alone against a repo; Platform = everything requiring shared state (live metadata, multi-user Designer, security, run history). Puts e.g. a *local* run-helper on the OSS side.
- **A-γ · Feature-tier rule (open-core).** A curated feature matrix, decided case-by-case (classic open-core). Maximal business flexibility, minimal principle — drift risk P1 explicitly fights.
- **A-δ · Weird: everything OSS, Platform sells operations.** The Platform code itself is source-available/OSS; the product is the hosted/supported deployment. (Grafana/Airbyte-adjacent.)

**Grey-zone items to place explicitly:** optimizer depth (statistics-*driven* optimization needs Platform data — is the *algorithm* still OSS?); `ttr-designer-server` (repo-attached, loopback — single-player, but a server); the conformance harness; the browser Designer frontend (Q-4); emit plugins for commercial orchestrators.

**Cross-links:** LF-3 (repo boundary ≠ license boundary), LF-8 (security reach), B (whatever the seam is, the OSS side must be whole — P1).

**Open:** who is the standalone persona — OSS adopter without the Platform forever, or on-ramp to the Platform?

---

## B · The mode seam (compiler/optimizer contract) — 🟢 converged 2026-07-08

**Question:** what exactly does "connected" change for compilation/optimization, through what interface, with what parity and degradation guarantees?

**Branches:** → [`02-mode-seam-options.md`](./02-mode-seam-options.md) (B-1 seam object · B-2 statistics/optimizer · B-3 parity · B-4 connected-only additions · B-5 degradation · B-6 where compile runs).

**Cross-links:** everything. LF-1/LF-5; TTR-P T6/D-g ground it; F (deploy needs the artifact question Q-3); H (compile-time policy).

---

## C · Platform service architecture — 🟢 converged 2026-07-08 (→ `03-service-architecture-options.md`)

**Question:** the service roster and their boundaries — what moves from kantheon, what is new, what merges?

**Branches (roster philosophy):**
- **C-α · Transplant.** Move Theseus/Kyklop/workers/Charon/Argos/Ariadne essentially as-are into the Platform repo; rename later. Fast, carries kantheon's shapes (incl. its single-query gap).
- **C-β · Re-found.** Define the Platform's services from the TTR-P design (worlds, manifests, invocation bindings, F-proper) and *fill* them with extracted kantheon code (the ttr-metadata pattern, applied to execution). Slower, cleaner ownership.
- **C-γ · Minimal platform.** v1 Platform = metadata server + workers + dispatcher only; scheduler/security/Designer-write arrive in later platform versions. (A platform F-lite.)
- **C-δ · Weird: no services — the Platform is a control plane over engines the customer already runs** (it registers databases + orchestrators and only coordinates; workers optional add-on).

**Sub-forks:** metadata server identity (LF-2: Ariadne-comes-home / grow designer-server / new-on-ttr-metadata / OpenMetadata-as-backend); dispatcher (Kyklop) vs scheduler (F) boundary; does Charon become the Platform's Transfer *binding* service (E-g generalization made concrete)?

**Cross-links:** D (repo topology constrains transplant vs re-found), F, H, G.

**Open:** Q-2 (Argos/OPA verification); which kantheon services are *shared* (needed by both halves post-split — e.g. Charon for agent data access?).

---

## D · Kantheon split & repo/infra topology

**Question:** which repos exist after the split, who owns what, and how do the extraction arcs sequence?

**Branches:**
- **D-α · Two repos + two infra repos.** `tatrman` (OSS, this repo) · `tatrman-platform` (services) · `olymp` (kantheon infra, exists) · a new platform infra repo. Kantheon repo shrinks to intelligence.
- **D-β · Platform inside this monorepo.** `services/` beside `packages/`; per-directory licensing carries the MIT boundary. One-repo ergonomics; license and OSS-community hygiene get harder.
- **D-γ · Repo-per-service** (metadata, scheduler, security, designer-backend…). Kantheon-style; maximal independence, maximal coordination cost.
- **D-δ · Weird: the Platform is a kantheon *rename*.** Kantheon repo splits by moving the *intelligence* out instead — the deterministic majority stays put and the repo is renamed `tatrman-platform`; agents move to a new repo in Olymp. Least code motion, most git-history preservation for the bigger half.

**Sequencing sub-fork:** extraction arcs first (ttr-metadata, ttr-translator — already planned), then service moves; or big-bang split.

**Cross-links:** LF-3, A (license boundary), C (roster), P2 (one-way arrow must hold at every intermediate state).

**Open:** where do the *published contract* artifacts (plan proto, world schemas, manifests) live so both halves consume them without cycles? (Precedent: TR-3 — tatrman owns.)

---

## E · Orchestration-engine integration

**Question:** orchestrators appear twice — as compiler **emit targets** (GI-1a) and as **platform-registered engines** with live metadata (GI-1b). One mechanism or two, and what is a "plugin"?

**Branches (emit side):**
- **E-α · Manifests + core codegen.** No plugin API: each orchestrator = an executor-type manifest + an emit module in the compiler (bash's F-lite pattern repeated). Adding one = a compiler PR.
- **E-β · JVM SPI plugins.** `ttr-emit-dagster` etc. as separate artifacts implementing a compiler SPI; third parties can ship emit targets without forking.
- **E-γ · Template/data-driven emit.** Orchestrator descriptions as data (templates + manifest); the compiler has one generic DAG-to-template emitter. Weakest for idiomatic output, cheapest per target.
- **E-δ · Weird: emit TTR's own neutral DAG format; adapters live *outside* the compiler** (a Dagster adapter reads the bundle manifest and builds the DAG at deploy time — the F-lite JSON manifest is already half of this).

**Branches (platform side):** registration modeled like databases (world/manifest instances) with a connector per orchestrator harvesting live-ish metadata/stats/contents; vs orchestrators as *dumb execution targets* only (no harvest); vs full adoption (Platform schedules *through* them, F delegation).

**Cross-links:** TTR-P B-T6 (executor manifests, invocation bindings) is the designed substrate; F (LF-6); I (harvesting = metadata connectivity, same connector shape?).

**Open:** is a registered *database* and a registered *orchestrator* the same registry concept ("registered engine") with different manifest kinds? (Lean-shaped hunch worth pressure-testing.)

---

## F · Scheduler & job execution

**Question:** the Platform server that schedules and runs jobs — its relationship to workers (dispatch), to registered orchestrators (delegation), and to TTR-P F-proper's deferred list.

**Branches:**
- **F-α · Own scheduler service** (cron + event triggers + the F-proper orchestrator semantics: FF, retries, resume, on-failure islands, runtime params). The Platform is self-sufficient; biggest build.
- **F-β · Delegate-only.** The Platform never schedules; it deploys to registered orchestrators (Dagster et al.) and harvests results. Thin, but "platform can run the hero nightly" then *requires* a third-party orchestrator — even for worker-only jobs.
- **F-γ · Thin trigger layer + F-proper executor.** Scheduling (time/event triggers) is a small service; *execution semantics* (waves→orchestrator graph, FF, retries) live in an executor that runs on workers; external orchestrators are alternative *frontends* to the same executor.
- **F-δ · Weird: the metadata server schedules.** Jobs are metadata (deployed bundles with schedule properties); a reconciler loop in the metadata server fires them. No separate scheduler service at all.

**Cross-links:** F-lite artifacts (does the platform *run bundles* or a richer deployed unit — Q-3); B-T6 execution-engine manifests (Kantheon-the-executor gets one — now it's Tatrman-the-executor); E (delegation), C (dispatcher boundary).

**Open:** run identity & history schema (metadata server owns run lineage?); backfill semantics (out of v1?).

---

## G · Designer evolution

**Question:** the browser Designer as a Platform product — writes, multi-user, presence — versus the standalone IDE view-only story; one frontend or two?

**Branches:**
- **G-α · One frontend, two backends.** `packages/designer` keeps the MD6 adapter seam: browser-worker LSP / loopback designer-server (standalone) and Platform metadata server (connected). Write capability = backend capability, not a fork of the app.
- **G-β · Platform Designer is a new app** (multi-user concerns — presence, locking/CRDT, review flows — warp the architecture enough that sharing a codebase hurts).
- **G-γ · Designer writes go through git anyway.** Even Platform-side, edits become commits/PRs to the model repo (text is canonical, TTR invariant); multi-user = git semantics + advisory presence, not live co-editing.
- **G-δ · Weird: the IDE is the only editor forever; the browser Designer never writes** — it grows dashboards/lineage/catalog views instead (a *reader* that gets richer, not an editor).

**Cross-links:** text-is-canonical invariant (CLAUDE.md); S24 (loopback v1 → auth story is H); C1-f arc (TTR-M editing convergence, `.ttrl` migration); Q-4 (license side of the frontend).

**Open:** does "view-only over `.ttrl` inside IDEs" (FI-2) mean the existing VS Code webview path, and does *it* stay when the Platform Designer matures?

---

## H · Security & governance

**Question:** the OPA-based security server bound to the metadata: what identities, what enforcement points, what policy model — and any standalone meaning?

**Branches (enforcement points, combinable):** metadata reads (who sees which models/worlds) · deploy/run (who executes what, where) · data-level (RLS at workers — Argos's current job) · Designer writes · compile-time advisory (policy lint).

**Branches (shape):**
- **H-α · Standalone has zero security concept.** Security is purely a Platform service. Cleanest split; policy is unrepresentable in OSS artifacts.
- **H-β · Policy is a TTR-family document** (`schema policy`?) — authored, versioned, repo-native like worlds; the Platform's OPA server *enforces* it; standalone can at least *lint against* it. (T6's declare/verify split, applied to security.)
- **H-γ · Adopt-external:** policy lives in OPA/Rego files (or the provider's IAM — I); the Platform binds them to metadata objects; TTR never models policy.

**Cross-links:** LF-8, Q-1, Q-2 (Argos verification), I (megaprovider IAM), TTR-P Q8 (trusted principal + tripwire — the standalone stance already decided for artifacts).

**Open:** is RLS (data-level) in scope for Platform v1 or does it stay a worker/kantheon concern until later?

---

## I · External metadata & megaproviders

**Question:** the Platform meets other metadata systems (OpenMetadata, Collibra, Amundsen) and megaprovider ecosystems (Google; Azure incl. PowerBI-for-MD, Fabric-for-metadata). Who is source of truth, and what's the connector shape?

**Branches (truth direction):**
- **I-α · Import only.** External systems are *population sources* for worlds/models (TTR-P D-g's stance, generalized): harvest → propose TTR model docs → human ratifies into the repo. Text stays canonical.
- **I-β · Bidirectional sync.** Tatrman pushes its models/lineage out (be a *good citizen* in OpenMetadata) and pulls schemas in; conflict rules needed.
- **I-γ · Federation.** The Platform metadata server answers queries by delegating live to external servers for non-TTR objects; TTR objects stay native. No copies, live coupling.
- **I-δ · Weird: OpenMetadata *is* the metadata server** (LF-2's fourth option) — the Platform ships as an OpenMetadata app/extension; Tatrman-native metadata becomes custom types there.

**Branches (megaproviders):** same connector SPI as metadata servers vs dedicated per-ecosystem integrations (PowerBI semantic-model ↔ TTR-M `md` mapping is a *modeling* feature, not just plumbing).

**Cross-links:** E (registered-engine registry — same connector frame?), C (LF-2), md-catalog (`MD_CALC_CATALOG` sync key — PowerBI MD work lands near it).

**Open:** lineage export format (OpenLineage?); which single external system anchors v1 (pick one, like FI-2 picked two engines)?

---

## J · Naming & conventions

**Question:** platform name ("Tatrman Platform" final?), service names (does mythology naming continue on the Tatrman side, or is mythology now Kantheon's namespace?), repo names, artifact groups, edition names (standalone/connected? community/platform?).

**Branches:** deferred until shapes exist (anti-bikeshed guardrail). Collect candidates opportunistically here.

**Cross-links:** D (repo names), A (edition names).

**Open:** —
