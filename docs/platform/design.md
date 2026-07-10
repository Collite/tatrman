# Tatrman Platform — Design (v1)

> **The technical result of the Tatrman Platform design effort (2026-07-08 → 2026-07-09).** Compact by design: this document states what was decided and what planning must produce; the *why* and the rejected alternatives live in the decision log ([`design/00-control-room.md`](./design/00-control-room.md) §7 — ground truth) and the option docs (`design/02`–`09`). The exhaustive narrative is [`detailed-design.md`](./detailed-design.md).
>

> **⚠ SUPERSEDED-IN-PART — ecosystem redraw, 2026-07-10.** The design recorded here stands; its *tiering* changed (control room §7 → "Ecosystem redraw amendment batch", STRAT-1..9 + RO-1..14). Read with these markers: the open spine ships as **Tatrman Server** (Apache-2.0, new **`tatrman-server`** repo — RO-1); the operate tier described here (ttr-run/Radegast, ttr-schedule/Zorya, Charon, Perun, envelopes, event spine, continuous harvest) is **parked by sequence** as Tatrman Platform (STRAT-1); every "MIT" reads **Apache-2.0** (STRAT-3); A-1's "compile vs operate" → **"interoperate vs administrate"** (STRAT-2); Q-6 is the *operate-tier* bar — the near-term bar is the **Tatrman Server v1** statement (RO-3); service names per **J-v2** (`design/naming-260710.md`); the dependency chain is now `tatrman → tatrman-server → {tatrman-platform, kantheon}` (RO-6). **Live core docs: `docs/server/` + `docs/ecosystem/ecosystem.md`.**

> **Audience: the `/planning` session.** Everything planning needs is here or one link away: the acceptance bar (§1), the architecture (§2–§6), the contract inventory with owners (§7), the sequencing (§8), the standing rules (§9), the collected planning-stage work items (§10), and the parked/deferred ledger (§11).

---

## 1. The v1 acceptance statement (Q-6, ratified 2026-07-09 — verbatim)

> **2026-07-10:** superseded as the near-term bar (STRAT-5); remains the Tatrman Platform (operate-tier) bar. Near-term bar = the Tatrman Server v1 statement (RO-3, `docs/server/`).

> *"Platform v1 is done when, on a fresh `tatry`-deployed instance: (life 2) the hero program deploys via envelope, runs nightly under a service principal on Arges+Steropes with Charon transfers, RLS enforced, runs and column lineage visible in the Designer; (life 3) the same program delegates through Airflow 3 with lineage harvested back and exported to OpenMetadata, and the Kestra emit plugin passes conformance; (life 1) unchanged and green throughout — the MIT toolchain never regresses."*

This is the scope bar. Planning sequences against it, not against the full decision pile.

---

## 2. Framing (fixed, not designed)

> **2026-07-10:** FI-2's "MIT" → Apache-2.0 (STRAT-3); FI-3's "connected = the (commercial) Tatrman Platform" is reframed — the connected-mode server is open, the mode seam is open↔open (RO-4); editions extended to the four-brand architecture incl. **Tatrman Server** (STRAT-4).

- **FI-1** Two first-class modes: **standalone** (repo-only, static, offline) and **connected** (Tatrman metadata server). Robust, not a bolt-on.
- **FI-2** Standalone = **MIT open source**: languages (TTR-M/TTR-P/TTR-B), textual representations, IDE extensions, the compiler (emits scripts per engine; not an execution platform). Designer standalone = view-only over `.ttrl`/model files in IDEs (permanent, per G-5).
- **FI-3** Connected = **the Tatrman Platform**, which takes the workers and the whole deterministic half of Kantheon; Kantheon keeps the intelligence (agents + LLMs) and stays in Olymp; the Platform gets its own infra repo.
- **FI-4** Minimum platform roster: workers · browser Designer · metadata server · security server bound to the metadata.
- **Editions (J-4):** **Tatrman** (MIT) and **Tatrman Platform** — no "community"/"lite" vocabulary. Modes stay "standalone/connected". License = Tatrman Platform License (text = a D-5 ② task).
- **Hero scenario:** "one program, three lives" (standalone bundle by hand · platform nightly on workers · delegated through a registered orchestrator with lineage flowing out). Rendered per workstream in the option docs; narrated end-to-end in `detailed-design.md`.

---

## 3. The mode seam (workstream B — the OSS/platform contract)

The compiler is **mode-blind** behind a **source SPI**; "connected" is a *binding*, never a behavior:

- **B-1 = β** — the compiler always compiles against `(models, worlds, manifests, statistics)` through the `ttr-metadata` source SPI. Standalone binds `LocalFsStorage`; connected binds `MetadataServerSource`.
- **B-2 = β** — statistics are a **separate, snapshot-pinnable source kind**; absent stats = defined degradation to the static cost model, never an error.
- **B-3 = α** — **hard parity**: a compile is a pure function of its recorded snapshot; mode only chooses where the snapshot comes from. Same program + same resolved inputs ⇒ byte-identical artifacts. `ttrp-conform` gains a mode-drift suite.
- **B-5 = δ** — **fetch-then-compile** is the only connected shape; the compiler never talks to the server mid-compile; unreachable server = stale snapshot, nothing else.
- **B-6 = γ** — **one compiler core**, bound locally (CLI/IDE) and server-side (platform compile/deploy service).
- **BQ-1** — snapshot transport = **content-addressed archives** served by Veles, cached locally by hash.
- **BQ-2 = γ+δ** — stats keyed **per object** `{qname, object-schema-hash, observed-at, values}`; used values embedded verbatim in the compile record; the world fingerprint stays stats-free forever.
- **BQ-3 layered + BQ-4** — **`ttr.lock` (committed) pins canon** (models, world, manifests, **emit-plugin versions**) by content hash; fetch = a reviewable lock diff. **Stats float** under max-age auto-refresh, never in the lock, recorded per-compile. Flags `--frozen` (CI default) / `--offline`.
- **F-7 = γ** — the lock stays **per project root**; program-scoped provenance = the envelope citing `{lock hash, compile record}` (BQ-5 dissolved).
- **B-4 (standing seam-legality rule):** *the seam admits data (sources, registries, overlays) and diagnostics; never identity, never side effects.*

---

## 4. Repos, licensing, ownership (workstreams A, D, J)

> **2026-07-10:** A-1 superseded by "interoperate vs administrate" (STRAT-2); D-1 amended — the spine's public home is the new `tatrman-server` repo, `tatrman-platform` reserved for the operate tier (RO-1); D-3's chain redrawn `tatrman → tatrman-server → {tatrman-platform, kantheon}`, ownership ≠ license tier (RO-6); J superseded by J-v2 (`design/naming-260710.md`).

- **A-1 = A-α** — the edition rule: **"compile vs operate."** Turns-source-into-artifacts (parse, check, optimize, emit, format, lint, view) = MIT; runs/stores/schedules/secures/serves-shared-state = Platform. Grey zones placed: optimizer fully MIT (incl. stats-driven algorithms — the Platform's edge is *data*), `ttr-designer-server` MIT, conformance harness MIT, Designer frontend MIT, emit-plugin *mechanism* MIT (individual plugins either side).
- **D-1 = α** — a **new `tatrman-platform` repo** (Gradle-only, DQ-1); re-founded services born there; transplanted leaves arrive via history-preserving `git filter-repo`; proto/package roots swept on arrival (DQ-2). `tatrman-semantics` archived (DQ-3).
- **D-2 = α + Q-4-a** — **license boundary = repo boundary.** `tatrman` = 100% MIT; the platform repo = Tatrman Platform License. The Designer frontend stays MIT in tatrman, backend-selectable; platform-only panels = platform-shipped **"Designer Extensions"** on an MIT-defined extension surface.
- **D-3 = α** — contract ownership: **"toolchain-touched ⇒ tatrman-owned (MIT); service-internal ⇒ platform-owned."** Kantheon owns nothing shared. Dependency chain **`tatrman → platform → kantheon`**, one-way, three tiers (P2 generalized).
- **D-4 = α-now-δ-direction** — infra = new olymp-shaped repo **`tatry`** = "deployment instance #1"; helm charts live in the platform repo beside services; declared graduation = ship-as-umbrella-chart/operator.
- **D-6** — grey-zone sweep: kallimachos, report-renderer, echo, kadmos stay kantheon; **metis stays-but-flagged** (worker in disguise; the engine-by-manifest test case when the worker SPI lands); **pinakes = verify-then-place in ②**.
- **J (naming):** register rule = **platform-native services get Slavic names, transplants keep Greek**. Coordinates: **`org.tatrman:*` = MIT · `cz.tatrman:*` = Platform** (the group id makes the license boundary physical). Plugin/connector coordinates: `ttr-emit-<target>` / `ttr-connect-<system>`.

---

## 5. The v1 service map (C, F, H, J — names + one-line contracts)

Two doors, one hall (C-3 = γ); β-spine + α-leaves (C-1); Proteus-the-service dissolves into compile-time `ttr-translator`.

| Service | Origin | One-line contract |
|---|---|---|
| **Veles** (metadata server) | new, on `org.tatrman:ttr-metadata` | Serves the **B contract** (content-addressed snapshot archives assembled from the *resolved* composed world + per-object stats endpoint), Designer reads, the **lineage organ** (column-grain v1, ingested from run events, citing the manifest's compiler-derived lineage section), **export connectors** (OpenMetadata/OpenLineage-shaped), and **harvest scheduling** (the refresh organ drives connectors). It *is* the engine registry because it serves the world (E-2-γ). PEP for coarse catalog visibility. |
| **Radegast** (program-door executor) | new (F-proper's home) | Executes deployment envelopes: walks the bundle **manifest's wave graph** (authoritative; `run.sh` is a rendering), per island Argos → Kyklop → worker, Charon per transfer edge; **central executor with run state externalized** to a durable, executor-owned run store from day one (F-3 = α); owns run writes + "what is running now"; **emits run-completion/lineage/audit events** that Veles ingests (F-6 = β, S7). Door frontend contract: `{start(envelope ref, params), poll/subscribe, cancel}`. |
| **Zorya** (trigger layer) | new, thin (F-1 = γ) | Time/event/manual/API triggers fire the program door; external orchestrators are *alternative frontends* calling the same door. v1 events: cron, manual, upstream-run (F-4-v = β). |
| **Theseus** (query door) | slimmed transplant | Synchronous single-validated-plan door — kantheon agents, Designer previews, debug. Ad-hoc plans validate against Veles's currently-served resolved world (CQ-6); deployed bundles verify their T6 fingerprint. |
| **Argos** (validator) | transplant + SPI | RLS predicate injection, column DENY/MASK, TopN, coercion — transplants with its HOCON store **verbatim** (RLS = v1 by construction), then a follow-up arc rekeys onto Perun's bundles (H-7 = α-then-β). LLM Guard = a **pluggable validator SPI** (Platform defines the hook, Kantheon ships the plugin; no plugin = deterministic default, C-5-i = c). |
| **Kyklop** (dispatcher) | transplant | Routes validated physical plans to capable workers; carries the two-door quota discipline: **priority + admission at dispatch** (interactive > batch; batch yields at the dispatch slot; running work always finishes; pool labels = deployment option) (F-5 = γ-minimal). |
| **Cyclopes workers** (Arges/Brontes/Steropes) | transplant | PG / MSSQL / Polars execution engines (FI-3 verbatim). |
| **Charon** (movement) | transplant (C-4 = α) | Materialize/Stage/Copy/Evict; the platform-world Transfer binding targets it; called by Radegast per transfer edge. Known accepted v1 hole: transfers sit outside the F-5 quota. |
| **Perun** (PDP / security server) | whois transplant → γ (H-4) | Directory (IdP/ERP sync) + builds, signs, and serves **content-hashed Rego policy bundles** bound to metadata qnames; **PEPs (doors, hall, Veles, Designer backend) pull bundles and evaluate locally**; fail-closed at bundle expiry; run records cite the bundle hash in force. The `security`-block pipeline homes here (sugar fragments + hand Rego, deny-overrides, sign). |
| **Designer** | MIT frontend (tatrman) + platform backend | One React app, backend-selectable (browser-worker / loopback `ttr-designer-server` / Veles). **v1 = reader-first** (G-4 = α): catalog · model graph views · runs · **column lineage** · registration wizard (one wizard, kind-parameterized) · **read-only TTR-P program graphs** (from the E-5 manifest). Edit mode **designed now, built second** behind a flag: writes-through-git (session = branch, save = commit, publish = repo-policy-decides; G-1 = γ), graph ops + embedded text in one `WorkspaceEdit` path (G-2 = γ), stateless reads / session-ful edits with previews via the query door (G-3 = γ). |

**Execution semantics (F-4, = Tatrman-the-executor's capability manifest content):** FS + SS · typed runtime params bound at trigger (run-date built-in) · manifest-declared per-island retries (attempt-scoped staging wiped per retry) · wave-level snapshot-guarded resume (run-scoped staging retained while parked-resumable under envelope-declared retention; at expiry the run degrades resumable→restart-only, P3-explicit; Charon Evict cleans on success/abandonment/expiry — FQ-6) · on-failure islands without `absorbs` · events {cron, manual, upstream-run}. **FF stays reserved** (staging+swap = the designed mechanism when a program demands it). The door executes **manifests, not compilers**: compatibility keys on the E-5 manifest schema version (current + N−1 minimum; window = planning; FQ-7 dissolved).

**The deployed unit (F-2 = β, resolves Q-3):** the **verbatim F-lite bundle wrapped in a deployment envelope** — `name@version`, bundle hash, triggers, typed param bindings, connection bindings (refs only), policy/principal refs, **required envelope-named service principal** (H-2-iii), `{lock hash, compile record}` provenance. The δ pointer-deploy variant is a later legal envelope shape (`source:` vs `artifact:`) and **must cite `{lock hash + compile record}`** — recompile-from-recorded-stats, never the lock alone (Q-7).

---

## 6. Cross-cutting subsystems

### 6.1 World composition (K — resolves Q-5)

Project worlds **declare** the platform world they extend (visible, reviewable project text; T4-adjacent grammar → amendment sweep); Veles's resolver composes behind the declaration; the lock pins the referenced platform world by content hash. Pins: **contradiction = compile error** (platform entries authoritative; projects add/extend, never contradict) · **the platform world lives in its own git repo** (admin edits = commits; edit rights = an H-3 policy object) · **standalone parity via file-exported copies** (B-1 two-bindings) · **project roster = server config**, not world content.

### 6.2 Orchestration engines (E — resolves LF-4, LF-6)

One *concept* (world-declared engine with a B-T6 manifest), two *mechanisms*, three deliverables per orchestrator (**the support package**):

1. **Emit plugin** (`ttr-emit-<target>`, JVM SPI, E-1 = β) — owns the *orchestration layer only* (island payloads stay core-compiler emit); identity pinned in `ttr.lock` (BQ-4); **determinism is a stated SPI obligation** (verified by the H-6 kit); ships the executor-**type** manifest (a recorded T6 amendment). The bash emitter is extracted as the SPI's proving plugin.
2. **Registration = world content** (E-2 = γ) — an environment-instance entry in the platform world; Veles is the registry; secrets enter as connection *refs* only.
3. **Platform adapter + harvest connector** (E-3-α-1 / E-4 = β) — deploys door-calling DAGs (the platform-world binding; hall intact) and ingests run/lineage events into F-6-β's path; doubles as the external-event trigger source.

**Delegation is world-driven from day one (E-3 = γ):** platform world binds α-1 (door-calling whole-program op — a *frontend*); standalone world binds β (native DAG — an MIT *emit target*, not a frontend, credential-bounded per H-8). **v1 executor targets = {bash, Airflow 3, Kestra}; Dagster first post-v1; WLA tier = the demand-driven commercial-plugin market** (EQ-3).

### 6.3 Security (H)

- **Policy (H-1 = γ + sugar):** Rego/OPA bundles bound to metadata qnames, content-hashed canon, **plus the TTR `security` block** — MIT sugar that deterministically generates standard fragments (read grants, column masks/classifications, ownership; row predicates stay Rego-side). One-way generation; **deny-overrides** composition; never alters plans; T6-fingerprint-neutral; owner-declaration vs org-policy line. HQ-1: classifications are the native grant vocabulary; verbatim roles legal, fail-closed-validated at bundle build.
- **Identity (H-2):** IdP-verified JWT at every door via one shared ingress module (bearer-only · enrichment-never-authority · fail-closed); machine callers = client-credentials service principals; **scheduled runs execute as an explicit, envelope-named service principal — required, v1** (discharges FQ-5); deployers need a grant to *use* a principal.
- **PEP map v1 (H-3 = α):** deploy/run authz at the doors · transplanted Argos data plane · coarse catalog visibility · Designer writes ride git permissions (HQ-3 discharged by G-1-γ) · **compile-time policy = advisory-only** (B's ledger clear).
- **Secrets (H-5 = β+γ):** **secret-store SPI** (K8s Secrets default; Vault/cloud bindings) + **dispatch-time env injection** (`TTR_CONN_*` verbatim — parity both modes); refs resolve at dispatch, manifest-scoped per island/transfer; **never-at-rest invariant** (no material in envelope/run store/logs/artifacts — canary-tripwire-tested in CI); **no-secret-API rule**; store-unreachable = pre-flight island failure; secret-zero = deployment config.
- **Trust (H-6 = β):** checksums + lock pinning + publisher-signature verification for emit plugins (verify-if-signed v1; require-signed = policy knob) + the **`ttrp-conform` determinism kit** (double-compile byte-compare; third-party certification requirement) + PDP-signed bundles. Two trust roots: the IdP (identity) · publisher keys (artifacts).
- **Standalone (H-8, resolves Q-1/LF-8):** integrity (trusted-principal stance, TTR-P Q8) + optional advisory lint against an exported/pinned bundle; **enforcement is inherently "operate" ⇒ the Platform's.** The E-3-β bypass line: *credential-bounded, not policy-bounded.*

### 6.4 External metadata (I — resolves LF-7)

**"Proposals in, projections out — never sync, never federate."** Inbound canon = **PR-shaped proposals** of generated TTR documents (robots write through git too; deterministic generation; humans ratify by merge); inbound stats = the BQ-2 store direct (temperament split); outbound = Veles's export connectors (regenerable projections). No object has two writers ⇒ no conflict machinery. **One connector frame** shared with E (`{engine kind, auth ref, ingest mapping, optional event subscription}`, widened to two output kinds — EQ-4). MIT/platform line: **one-shot `ttr import-schema` CLI = MIT; continuous scheduled harvest = Platform.** **Anchor = OpenMetadata** (v1 = its import+export connector pair); DataHub first follow-on, consciously not v1. Megaproviders = connector families (I-4 = α); the PowerBI semantic-model ↔ TTR-M `md` mapping = a TTR-M modeling arc (IQ-1), not plumbing.

### 6.5 Lineage, audit & observability (CQ-5, S7)

**Column lineage is v1 and STATIC:** derived **at compile** and emitted into the bundle manifest's **lineage section** (E-5) / compile record — MIT, reviewable, free for delegated runs; run events attach context and *cite* the manifest section. Export = OpenLineage column-lineage facet. **Audit + observability ride the F-6-β event spine (S7):** PEPs/executor *emit* events (authority decisions with bundle hash, run transitions, island logs by reference); Veles *ingests and serves queries*; log payloads live in the run store. One event spine, no new architecture.

---

## 7. Contract inventory (D-3 rule applied — planning must pin each)

**MIT, tatrman-owned (`org.tatrman`):** toolchain-touched.

| Contract | Status / notes |
|---|---|
| Plan protos (`plan.v1`) | exists (TR-3) |
| World / manifest schemas (incl. B-T6 type+instance manifests) | exists; gains **K's `extends`-platform-world surface** + F-4 vocabulary (params, on-failure) — amendment sweep |
| Snapshot-archive format · `ttr.lock` format · stats-entry schema (BQ-2) | new; the B contract's wire half |
| **E-5 bundle-manifest schema** (graph + fingerprints + checksums + **lineage section**), versioned | the load-bearing contract: door execution keys on its schema version, F-7 provenance, adapters, lineage |
| Compile record (recorded stats values, provenance slice) | pinned with B-3-α/BQ-2-δ |
| **Emit SPI** (E-1; determinism a stated obligation) | new; EQ-1 pins the surface |
| **Validator SPI** interface (C-5-i) | new |
| **Designer Extensions** surface (Q-4-a) | new |
| **`security` block grammar + generator** (H-1) | new; TTR-M amendment |
| `ttr import-schema` CLI output conventions (I-2) | new; generator-shaped |

**Platform-owned (`cz.tatrman`):** service-internal.

| Contract | Status / notes |
|---|---|
| **Deployment envelope schema** (F-2-β; incl. required service principal, retention policy, `{lock hash, compile record}`, `artifact:`/`source:` variants) | new |
| **Run/lineage/audit event contract** (F-6-β + S7; carries bundle-hash field, cites manifest lineage) | new |
| **Door frontend contract** `{start(envelope ref, params), poll/subscribe, cancel}` (E-3) | new; largely F-6-β's operational API |
| Query-door proto (slimmed Theseus) · internal hall/worker protos (`worker.v1`, …) | transplant + sweep |
| **Secret-store SPI** (H-5) | new |
| **Connector SPI** (E-4/I-2, two output kinds) | new |
| Policy-bundle conventions (qname binding, signing, expiry) (H-1/H-4) | new |

Kantheon owns nothing shared.

---

## 8. Sequencing — strangler ①–⑦ (D-5, with H/G/I placements)

> **2026-07-10:** re-sequenced by STRAT-6 — ①② stand; ③'s hall content done in kantheon (open spine; the program door parks); ④⑦ park with the operate tier; ⑤ splits (ttr-validate = open, done; Perun parked); ⑥ remains, inside the new critical path (`docs/server/plan.md`).

Per-move mini-arc discipline (move → adopt → delete); P2 must hold at every intermediate state.

1. **①** MD + TR arcs complete (already planned; `ttr-metadata`, `ttr-translator`).
2. **②** `tatrman-platform` repo bootstrap: **Veles v1** (B contract + Designer serving). **Reader Designer rides ②** (G); **pinakes verify-then-place here**; license file forced here; **harvest connectors land post-②** (the refresh organ drives them); **the OpenMetadata connector pair lands with Veles's export organ**.
3. **③** Program door (**Radegast**) + workers/Kyklop transplant.
4. **④** **Charon**.
5. **⑤** **Argos + validator SPI · Perun** (whois-descendant, with H); Argos's HOCON→bundle rekey = a follow-up arc (H-7 β-step, HQ-5).
6. **⑥** Kantheon query-door adoption arc (kantheon keeps its mini-spine until here — accepted, time-boxed, P2-legal duplication; **HQ-4** agent-role mapping decided here).
7. **⑦** **Zorya + the scheduler surface** (F) et al.; deploy views join the Designer here; edit mode graduates when the workspace machinery is proven (G, after K's platform-world repo exists).

---

## 9. Standing rules (cite by ID in planning and implementation)

- **P1 · Standalone is not a demo.** Connected adds capability; it never fixes standalone; no language feature gates on the Platform.
- **P2 · One-way arrow, generalized.** Build-time deps flow `tatrman → platform → kantheon` only; runtime plugins on Platform/OSS-defined SPIs are legal.
- **P3 · No miracles.** Explicit, or deterministically derived from declared defaults; otherwise an error.
- **B-4 seam legality:** the seam admits data + diagnostics; never identity, never side effects.
- **D-3 ownership:** toolchain-touched ⇒ tatrman-owned (MIT); service-internal ⇒ platform-owned.
- **H-5 invariants:** secrets **never at rest** (canary-tested) · **no endpoint returns secret material**.
- **H-1 composition:** deny-overrides (sugar grants; hand Rego can always take away); generated fragments never hand-edited.
- **Determinism obligations:** emit plugins (E-1, verified by the H-6 kit) and harvest connectors (I-3: same external state ⇒ same proposal bytes).
- **"Robots write through git"** (G-1-γ universalized by I-3): every canon writer — humans, the Designer, harvest connectors — produces reviewable commits; stats bypass by temperament only.
- **The manifest's graph is authoritative; `run.sh` is a rendering of it** (F-2-β).
- **Bearer-only · enrichment-never-authority · fail-closed** (H-2 ingress, carried from kantheon fork-§6).

---

## 10. Planning-stage work items (collected riders)

1. **FQ-2** — run-id schema (candidate: `{envelope name@version, trigger id, fire-time}` + island attempt ids).
2. **FQ-4** — write **Tatrman-the-executor's capability manifest** as a concrete artifact (content = §5's F-4 package; B-T6 requires it for compiles against platform worlds).
3. **EQ-1** — pin the emit-SPI surface (inputs: derived orchestration graph + island payloads + type/instance manifests; outputs: bundle content).
4. **EQ-2 / H-6** — plugin distribution, signing, and the determinism-kit mechanics (double-compile byte-compare verb; certification flow).
5. **IQ-2** — deterministic external-name → TTR-qname mapping discipline (first connector's mapping spec).
6. **IQ-4** — export-lossiness list (what of TTR semantics maps lossy into OpenMetadata/OpenLineage).
7. **GQ-4** — Designer edit-session quotas/limits (first per-user server resource; ops item).
8. **FQ-6/FQ-7 sizes** — run-scoped staging retention default; the door's manifest-schema-version support window (current + N−1 minimum).
9. **HQ-4** — agent-role mapping at query-door adoption (⑥).
10. **HQ-5** — Argos HOCON→bundle migration path (mechanical translation vs re-authoring; the H-7 β-step).
11. **S7 sizing** — event-spine retention, log-payload sizing, notification channels (audit + observability).
12. **CQ-4** — worker capability manifests vs Kyklop routing vocabulary: mapping layer first (spine-extraction work item).
13. **F-6-β schema** — gains the policy-bundle-hash field (H-4) and the manifest-lineage citation (CQ-5).

---

## 11. Parked, deferred, and handed off

**Parking lot (control room §8) plus decision-log-recorded deferrals — each with a revisit condition:** monetization/licensing model (revisit: ② forces a license file) · standalone persona (first external OSS adopter / GTM) · TTR-B/NL surfaces on the Platform (kantheon-side agent design) · multi-tenant SaaS (single-org is the v1 anchor) · **FQ-1 backfill** (first real demand; interacts with params + external events) · **F-5-δ identity-priced quotas** (multi-tenant/H) · **Charon-outside-quota hole** (accepted v1; revisit at F-3-γ growth or first contention) · Dagster support package (first post-v1 target) · DataHub connector (first follow-on) · SLSA-grade attestation (demand-driven) · live co-editing (the within-session future of G-1) · α-2 per-island delegation mirroring (visibility upgrade with stated drift cost) · F-3-γ reconciler/workers-pull (the recorded growth direction; re-opens C-4-β).

**The S1 amendment batch (TTR-P/TTR-M side — record per the `.ttrl` discipline + contracts changelog, one batch):** F-4-i runtime params grammar · F-4-iv on-failure islands / error-flow vocabulary · FQ-4 Tatrman-the-executor manifest · E-5 manifest-contract graduation + lineage section · **T6 type-manifest ownership** (emit plugins ship executor-type manifests — the silent amendment, now recorded) · **K's `extends`-platform-world grammar** (T4-adjacent) · **H-1 `security`-block grammar** (TTR-M).

**Post-design kantheon-repo arcs:** ⑥ query-door adoption · post-split doc sweep (what "kantheon" means).

**This repo (S6):** CLAUDE.md's "never talks to those services" → "the MIT toolchain is a client of the platform's published snapshot contract" (`ttr fetch`, `MetadataServerSource`).

---

## 12. Next step

Run **`/planning`** on this document → architecture, contracts, phased plan, task lists. The decision log (control room §7) is ground truth wherever this summary and the log could be read to differ.
