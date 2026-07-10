# The Tatrman Platform — Detailed Design

> The exhaustive write-up of the Tatrman Platform design (converged 2026-07-09). This document is a narrative for a reader who was not in the design sessions: it explains what the Platform is, how the two-mode architecture works end to end, and *why* each of the big calls went the way it did — including the alternatives that were considered and rejected. The compact, planning-facing statement of the same design is [`design.md`](./design.md); the append-only decision log in [`00-control-room.md`](./00-control-room.md) is ground truth; the full option catalogues live in `02`–`09`.


> **⚠ SUPERSEDED-IN-PART — ecosystem redraw, 2026-07-10.** The design recorded here stands; its *tiering* changed (control room §7 → "Ecosystem redraw amendment batch", STRAT-1..9 + RO-1..14). Read with these markers: the open spine ships as **Tatrman Server** (Apache-2.0, new **`tatrman-server`** repo — RO-1); the operate tier described here (ttr-run/Radegast, ttr-schedule/Zorya, Charon, Perun, envelopes, event spine, continuous harvest) is **parked by sequence** as Tatrman Platform (STRAT-1); every "MIT" reads **Apache-2.0** (STRAT-3); A-1's "compile vs operate" → **"interoperate vs administrate"** (STRAT-2); Q-6 is the *operate-tier* bar — the near-term bar is the **Tatrman Server v1** statement (RO-3); service names per **J-v2** (`design/naming-260710.md`); the dependency chain is now `tatrman → tatrman-server → {tatrman-platform, kantheon}` (RO-6). **Live core docs: `docs/ecosystem/server/` + `docs/ecosystem/ecosystem.md`.**

---

## 1. What is being built, and why

Tatrman is a data processing language family (TTR-M for models, TTR-P for programs, TTR-B for business surfaces) with a compiler that turns source into runnable artifacts. Until this effort, the runtime that executed those artifacts lived inside **Kantheon** — a platform that mixes two very different natures: a *deterministic* half (workers that run SQL and Polars, a dispatcher, a data mover, a validator, a metadata facade) and an *intelligence* half (agents, LLM gateways, NLP services).

This design carves that boundary permanently. The framing, fixed by Bora at the effort's opening and never a fork:

**Two modes are first-class.** The compiler and optimizer work in *standalone mode* — reading only repositories, static, offline — and in *connected mode* — talking to a Tatrman metadata server. Neither is a demo of the other.

**Standalone is MIT open source.** The languages, textual representations, IDE extensions, and the compiler itself — which emits scripts for different engines and is not an execution platform. Repositories are the only backing store.

**Connected is the Tatrman Platform.** The Platform takes ownership of the deterministic half of Kantheon — the workers, movement, dispatch, validation, metadata, security machinery — as a product in its own right. Kantheon keeps the intelligence and remains in its own infrastructure; it becomes a *client* of the Platform.

The editions are named simply **Tatrman** (MIT) and **Tatrman Platform**. There is deliberately no "community edition" or "lite" vocabulary: the design's first principle (P1, "standalone is not a demo") is that the open-source toolchain must be complete, correct, and useful entirely on its own, with connected mode *adding* capability — live worlds, statistics, execution, scheduling, security, collaboration — and never *fixing* standalone.

Three principles govern everything below and are cited by ID throughout the corpus:

- **P1 — Standalone is not a demo.** No feature of the language is gated on the Platform.
- **P2 — One-way arrow.** Build-time dependencies flow only `tatrman → platform → kantheon`. The open-source repo never depends on the Platform; the Platform consumes published `org.tatrman:*` artifacts; Kantheon consumes the Platform. Runtime plugins that conform to SPIs defined lower in the chain are legal — the arrow constrains *build-time* coupling.
- **P3 — No miracles.** Everything is explicit or deterministically derived from declared defaults; otherwise it is an error. Connected mode must not introduce invisible compile inputs.

## 2. The hero: one program, three lives

Every workstream in the design was tested against a single concrete scenario, carried from the TTR-P effort: a program that reads accounts from SQL and sales from CSV, joins, summarizes, and branches across two engines. The same program lives three lives:

**Life 1 — standalone.** A developer compiles the program offline against a world described by repository files. The compiler produces a `.bundle/` directory — the F-lite artifact: a `run.sh` that executes islands in dependency waves, the islands' concrete payloads (SQL, Polars code), a JSON manifest describing the graph, and a semantic world fingerprint. The developer runs it by hand, supplying database credentials through `TTR_CONN_*` environment variables. MIT tools only, no services anywhere.

**Life 2 — platform.** The *same program, byte-identical artifact*, is deployed to a Tatrman Platform instance. The world now comes from the Platform's metadata server, complete with live schemas and fresh statistics. The deployment wraps the bundle in an *envelope* naming a version, a nightly schedule, a service principal, and connection bindings. Each night a trigger fires, the executor walks the manifest's wave graph, islands are validated (row-level security applied), dispatched to Postgres and Polars workers, data moves between engines through the movement service, and the run history and column-level lineage appear in the browser Designer. Policy decides who may deploy, run, and see what.

**Life 3 — federated.** The Platform has Airflow registered as an orchestration engine and OpenMetadata connected as an external catalog. The program is delegated through Airflow — whose DAG simply calls the Platform's program door — scheduled there, with run results harvested back into the same lineage graph and exported onward to OpenMetadata. Alternatively, a standalone user with no Platform at all emits a *native* DAG for their own orchestrator and runs it themselves.

The ratified v1 acceptance statement is written directly in these terms — it is quoted verbatim in `design.md` §1 and defines "done": life 2 end-to-end on a fresh deployment, life 3 through Airflow 3 with lineage out to OpenMetadata and the Kestra emit plugin passing conformance, and life 1 unchanged and green throughout.

---

## 3. The mode seam: a mode-blind compiler

The deepest question in the effort was what "connected" actually *changes* for compilation. The answer, and the design's most load-bearing structure, is: **nothing about compiling — only where the inputs come from.**

### 3.1 The seam is a source SPI

The compiler always compiles against four kinds of input — models, worlds, manifests, statistics — obtained through a source SPI (the `ModelSource`/`ModelStorage` line that already exists in the `ttr-metadata` library). Standalone binds a local-filesystem storage; connected binds a `MetadataServerSource`. Compilation itself has no notion of mode; "connected" is a *binding chosen in project configuration*, not a behavior.

This extends two commitments the TTR-P design had already made: the world is a *compile target* that the runtime later verifies (never a live source of compile-time truth), and the compiler is *offline* — it embeds its metadata component and reads from paths, never from a service mid-compile. Connected mode changes *where the world comes from*, never what compiling means.

The alternatives were walked and rejected deliberately. A **mode flag** (`--connected <url>`) would have smeared mode-awareness across every compiler phase — each new connected capability another `if (connected)`, doubling the test surface and eroding P1 by a thousand cuts. **Two compilers** (an untouched OSS core wrapped by a platform compile service that adds connected phases) is the drift disease the conformance harness exists to fight: two compile behaviors to explain, and the language experience forks. A **sync-only** shape (connectedness as a fetch tool, the compiler only ever reading files) was the interesting weird option — its hermetic, reviewable-inputs discipline was too good to lose, and indeed it *wasn't* lost: it survives inside the chosen design as the lockfile and the fetch-then-compile rule.

### 3.2 Canon and observation: the two temperaments

The snapshot a compile consumes has two kinds of content with different natures, and the design refuses to give them one policy.

**Canon** — models, worlds, manifests, and emit-plugin versions — is stable, review-worthy content. It is pinned by a committed **`ttr.lock`** file: content hashes into a local, content-addressed archive cache served by the metadata server. Fetching is an explicit, reviewable event — a lock diff that says *"the platform's view of the world changed"* and gets code review like anything else. Same commit ⇒ same canon, team-wide, by construction. CI runs `--frozen` by default.

**Observation** — statistics (row counts, sizes, freshness) — is volatile and advisory. Stats float under a max-age auto-refresh policy, never enter the lock (hourly diff noise would make the lockfile unlivable), and are keyed **per object** as `{qname, object-schema-hash, observed-at, values}`. Validity is per-object: an entry whose schema hash no longer matches is discarded *for that object only*, which degrades that object to the static cost model — granular, explicit, and diagnosable — while the rest of the program keeps its statistics. Crucially, the semantic world fingerprint used for runtime verification stays stats-free forever: row counts drifting hourly must never read as "the world changed."

Absent statistics are a *defined degradation* (the optimizer's static cost model), never an error — which is also exactly the standalone experience, so the zero-stats path is permanently exercised.

### 3.3 Hard parity and fetch-then-compile

Two guarantees give the two-mode story teeth:

**Hard parity.** *A compile is a pure function of its recorded snapshot; mode only chooses where the snapshot comes from.* Same program plus same resolved inputs yields byte-identical artifacts in either mode. Every input — models, world, manifests, statistics, plugin versions — is part of the recorded snapshot; the statistics values actually used are embedded verbatim in the compile record, so the artifact *shows the numbers the optimizer saw* and replay is trivial. The conformance harness gains a mode-drift suite. The rejected alternative — "connected mode is allowed to be better" — is how two modes become two products by drift.

**Fetch-then-compile.** The compiler *never* talks to the server during a compile. A fetch step — explicit or policy-driven — refreshes the local snapshot; every compile is then local. An unreachable server means a stale snapshot and nothing else: failure semantics collapse into freshness policy (`--frozen`, `--offline`, max-age knobs, staleness recorded in the artifact). The offline nature of the compiler is preserved verbatim in both modes; the laptop-on-a-plane case is not an error state but the ordinary state.

A standing rule bounds all future connected features — the **seam-legality line**: *the seam admits data (sources, registries, overlays) and diagnostics; never identity, never side effects.* Anything identity-bound or effectful — policy enforcement, deployment, secret resolution, scheduling — happens in platform phases *after* compile, which is precisely what keeps parity intact. This one line later decided where the deployment envelope lives, why compile-time policy is advisory-only, and why registries may flow through the seam as world content.

One compiler core serves both modes: the CLI and IDE bind it locally; the Platform's compile/deploy service binds the same version server-side. Under hard parity, *where* a compile runs is a deployment convenience, not a semantic question.

### 3.4 The lock's scope

The lock stays one-per-project-root. When deployment needed program-scoped provenance, the answer was not per-program locks (which would allow two programs in one commit to see different worlds — a canon-skew smell) but the observation that the machinery already existed: the compile record lists exactly which objects and statistics each program consumed, so the deployment envelope cites `{lock hash, compile record}` — a program-scoped provenance slice with no new file kind.

---

## 4. The product split: compile vs operate

The edition boundary needed a *rule*, not a feature matrix. The ratified rule is **"compile vs operate"**: anything that turns source into artifacts — parse, check, optimize, emit, format, lint, view — is MIT; anything that runs, stores, schedules, secures, or serves shared state is Platform.

The rule's power shows in the grey zones, which all fall out rather than being negotiated case by case. The **optimizer is fully MIT, including the statistics-driven algorithms** — the Platform's edge is *data* (fresh statistics, live worlds), not secret algorithms; this is the honest open-source story. The repo-attached `ttr-designer-server` is MIT (it is editor infrastructure — "view"). The conformance harness is MIT. The **Designer frontend is MIT** and stays in the tatrman repo — one React application, backend-selectable, with platform-only panels arriving as platform-shipped extensions on an MIT-defined extension surface ("Designer Extensions"). The emit-plugin *mechanism* is MIT while individual plugins may live on either side — which is what makes a commercial Control-M emitter possible without forking the compiler.

Rejected: a "single-player vs multi-player" rule (fuzzier at the run-helper edge), classic open-core feature matrices (maximal flexibility, minimal principle — drift is P1's enemy), and everything-OSS-sell-operations (parked with the monetization question, which is a business call, not a design blocker).

## 5. Repos, contracts, and the shape of the split

**The Platform gets a new repo, `tatrman-platform`, and the license boundary is the repo boundary.** The tatrman repo is 100% MIT; the platform repo carries the Tatrman Platform License; there are no per-directory carve-outs anywhere. The boundary is made physical in Maven coordinates: **`org.tatrman:*` is MIT, `cz.tatrman:*` is Platform** — a clone or a dependency declaration tells you the license without reading a file. The platform repo is Gradle-only (all TypeScript stays in tatrman with the Designer). Re-founded services are born in the new repo; transplanted services arrive by history-preserving `git filter-repo` moves, with proto and package roots swept on arrival.

**Contract ownership follows one line: "toolchain-touched ⇒ tatrman-owned (MIT); service-internal ⇒ platform-owned."** If the open-source compiler or toolchain reads or writes a format — plan protos, world and manifest schemas, the snapshot archive and lock formats, the stats-entry schema, the bundle manifest, the SPI interfaces — tatrman owns it, and a standalone user can read every format their artifacts touch. If only platform services speak it — door protos, worker protos, the envelope, run events — the platform owns it. Kantheon owns nothing shared. The dependency chain is `tatrman → platform → kantheon`, one-way, three tiers, and it must hold at *every intermediate state* of the split, not just at the end.

**Infrastructure** mirrors the proven Olymp pattern: a new infra repo, **`tatry`**, holds cluster definitions and is explicitly "deployment instance #1" — while product packaging (helm charts) lives in the platform repo beside the services, and the declared direction is to graduate to ship-as-umbrella-chart/operator, so that every deployment, ours included, becomes an instance of the packaged product.

**The split sequences as a strangler**, with each move a mini-arc (move → adopt → delete): ① the metadata and translator extraction arcs complete; ② the platform repo bootstraps with the metadata server; ③ the program door and the workers/dispatcher transplant; ④ the data mover; ⑤ the validator and the security server; ⑥ kantheon adopts the query door (until then it keeps its own mini-spine — an accepted, time-boxed, P2-legal duplication); ⑦ the scheduler layer. A big-bang split was rejected because the one-way arrow is unverifiable mid-bang; greenfield-forever was rejected because it inverts the arrow at steady state.

A placement sweep settled the grey-zone services: the document-wiki engine, report renderer, fuzzy matcher, and NLP foundation stay with Kantheon's intelligence; **metis** (statistical models over Arrow series) stays but is flagged as "a worker in disguise" — the test case for engine-by-manifest when the worker SPI lands; **pinakes** gets verified and placed during the repo bootstrap.

---

## 6. The service architecture: two doors, one hall

Kantheon's execution spine runs *one query at a time* — its orchestrator chains translate → validate → dispatch for a single plan. The Platform's unit of work is the **compiled program**: a bundle of islands, transfers, and control waves. That mismatch shaped the roster philosophy: **re-found the spine, transplant the leaves.** Services whose shape is wrong for the program-as-unit (the spine) are designed fresh from the TTR-P vocabulary and *filled* with extracted code; services whose shape is right (workers, dispatcher, mover, directory) transplant as they are.

The spine's new shape is **two doors, one hall**:

- The **program door** is new — the home of full orchestration semantics (see §7). It accepts deployed programs and executes them.
- The **query door** is the transplanted, slimmed Theseus chain — one validated plan, synchronous. It exists because the ad-hoc path is load-bearing: Kantheon's agents compose plans at runtime, the Designer needs previews, and debugging wants single-shot execution.
- Both doors converge on the same **hall**: Argos (validation) → Kyklop (dispatch) → workers, with Charon handling movement. There is exactly one path to data, and everything passes the validator.

Two services *disappear*: Proteus (runtime translation) dissolves into the compile-time translator library — the compiler already emits concrete payloads, so translating at runtime re-derives settled work; and Ariadne stays behind in Kantheon as the agents' facade, thinning on its own schedule.

The naming convention encodes the archaeology (**new services get Slavic names; transplants keep their Greek ones**), so the roster reads:

**Veles**, the metadata server, is new — built on the `org.tatrman:ttr-metadata` library and designed around its four consumer classes: the compiler seam (content-addressed snapshot archives assembled from the *resolved* composed world, plus the per-object stats endpoint), the Designer (model graph serving), the runtime (world verification, ingested run history), and harvest (connector scheduling, via the refresh organ inherited from Ariadne). Two organs were added by explicit decision: **lineage** — served at *column* grain in v1 — and **export connectors** (the outbound half of the external-metadata story). Veles is also the engine registry, for the elegant reason described in §8: registration *is* world content, and Veles serves the world. Rejected shapes for this service: transplanting Ariadne ("comes home" would really mean "grows a second body" — its contract is Kantheon's runtime view), growing the repo-attached designer-server (deliberately scoped as editor infrastructure), and shipping on top of OpenMetadata (TTR semantics would become second-class custom types in a host schema; OpenMetadata was demoted to a connector).

**Radegast**, the program-door executor, is new — described in §7.

**Zorya**, the trigger layer, is new and deliberately thin — time, event, manual, and API triggers that fire the program door. Scheduling was consciously *split* from execution semantics: triggers are the easy tenth of a scheduler and worth owning; the hard 90% (calendars, sensors, backfill) is exactly what mature orchestrators do well, and they participate as alternative frontends calling the same door.

**Theseus** keeps its name and becomes the query door. **Argos** transplants with the hall as the plan validator — row-level-security predicate injection, column deny/mask, TopN, strict coercion — with one cut: its LLM Guard (an intelligence tendril inside a deterministic validator) becomes a **pluggable validator SPI**. The Platform defines the hook; Kantheon ships the plugin; with no plugin installed, the deterministic default applies. This decision also ratified the P2 clarification that runtime plugins conforming to platform-defined SPIs are legal. **Kyklop** transplants as the dispatcher and carries the two-door quota discipline. The **Cyclopes workers** — Arges (Postgres), Brontes (MSSQL), Steropes (Polars) — transplant verbatim. **Charon** transplants as the movement service; the platform world's Transfer binding targets it literally.

**Perun**, the policy decision point, grows from the whois transplant — described in §9. The historical Perun-and-Veles oath pairing names the architecture: Veles guards what is *known*, Perun what is *allowed*.

---

## 7. Deployment, scheduling, and execution

### 7.1 The envelope: the platform's half of the artifact

The deployed unit is the **verbatim standalone bundle wrapped in a deployment envelope**. The bundle — byte-identical to what standalone produces, per hard parity — is content-addressed and untouched. The envelope is the platform-side record around it: `name@version`, the bundle hash, triggers, typed runtime-parameter bindings, connection bindings (references, never material), policy references, the **required service principal** the program runs as, and `{lock hash, compile record}` provenance.

This is the seam-legality rule made structural: everything the compile seam *bans* — identity, schedules, side effects — is exactly what the envelope *carries*. Compile stays pure; operation gets a reviewable home. The executor runs the **manifest's wave graph, which is authoritative; `run.sh` is a rendering of it** — the bash script remains the standalone and rescue path, and drift between the two is an emit bug caught by conformance.

Rejected: the bare bundle (no home for platform state — schedules and identity would become invisible API state), and a new richer platform artifact (two artifact grammars evolving independently is the same drift disease as two compilers; and the platform would run something the standalone user can never inspect). A *pointer* deploy — `{repo ref, commit, provenance}` with the platform compiling server-side — is recorded as a later, legal envelope variant, with one correction the independent review caught: the pointer must cite `{lock hash + compile record}` and recompile *from the recorded statistics*, never from the lock alone — otherwise the server compile would float fresh stats and deploy a plan the author never reviewed.

### 7.2 The executor and its semantics

Execution is a **central executor walking waves** — per island Argos-validate → Kyklop-dispatch → worker, Charon per transfer edge — with **run state externalized to a durable, executor-owned store from day one**. That externalization is what makes the two more exotic execution models cheap later: moving the walker into a work item is a deployment refactor, and the reconciler/workers-pull architecture remains the recorded *growth direction*, honored but not started (starting there would rebuild the hall instead of moving it). The disqualified extreme — literally executing `run.sh` on a sandboxed runner — was catalogued to mark the parity endpoint: it bypasses the hall, so validation and policy see nothing, which no security design can accept.

The v1 semantics package repays the TTR-P "F-proper" debt item by item, and its content *is* the capability manifest of Tatrman-the-executor (worlds that target the platform declare it; programs using vocabulary the manifest lacks get an ordinary compile error — the manifest machinery doing the both-worlds job it was designed for):

- **Runtime parameters** — declared, typed, defaulted-or-required, bound at trigger time, run-date built in; injected per island in the same idiom as connections.
- **Retries** — per-island counts, manifest-declared, with attempt-scoped staging wiped before each attempt.
- **Resume** — wave-level: completed islands skip if and only if the envelope's snapshot fingerprint is unchanged. Run-scoped staging (completed islands' outputs) is retained while a failed run is parked-resumable, under an envelope-declared retention policy; at expiry the run *degrades explicitly* from resumable to restart-only, and Charon's Evict cleans up on success, abandonment, or expiry.
- **On-failure islands** — an error-consuming container runs iff its source failed; a handled failure is still not a success. This is the first vocabulary where platform and bash manifests genuinely diverge.
- **Events** — cron, manual, and upstream-run (program B on success of program A); external events arrive later through the connector frame.
- **FF (the atomic-publish vocabulary) stays reserved** — staging-and-swap is the designed mechanism, shipped when a program actually demands it; until then, using FF remains an honest compile error.

The door executes *manifests, not compilers*: compatibility keys on the bundle-manifest schema version (supporting at least current and previous), so toolchain upgrades never touch deployed envelopes, which are pinned by hash.

### 7.3 Sharing the hall: quotas

Nightly program waves and interactive agent bursts share one hall. The v1 arbitration is **priority plus admission at dispatch**: one worker pool, interactive work outranks batch, batch yields at the dispatch slot (a running SQL statement is never preempted), with a weighted-fair floor available so batch never fully starves. Static pool labels remain available as deployment configuration. Doing *nothing* (FIFO) was rejected by name — it would have been decision-by-drift on an explicitly flagged problem. One hole is recorded and accepted for v1: Charon transfers are executor-called, not Kyklop-dispatched, so they sit outside this arbitration.

### 7.4 Run records, lineage, and the event spine

The executor owns the run store — the hot, transactional record of what is running now. The metadata server **ingests events** — run completions, lineage, authority decisions — and serves all catalog and Designer reads. This split keeps the catalog off the execution hot path (catalog down ≠ execution down) and gives the platform exactly one event spine: audit and observability *ride the same discipline* (enforcement points and the executor emit events; Veles ingests and answers queries; bulky log payloads stay in the run store, referenced from events).

**Column-level lineage is a v1 promise, and it is static**: derived *at compile time* — the compiler already resolves every column reference — and emitted into the bundle manifest's lineage section and the compile record. That makes lineage MIT-side, reviewable artifact content; delegated runs get lineage for free (their manifests carry it); run events attach runtime context and *cite* the manifest section rather than re-deriving anything. Runtime-*observed* lineage is explicitly not promised in v1. Exports map it to OpenLineage's column-lineage facet.

---

## 8. Orchestration engines: the support package

Orchestrators appear twice in the framing — as compiler *emit targets* (produce a Dagster DAG, an Airflow DAG, a bash script) and as *platform-registered engines* with live metadata. The design's answer is one concept, two mechanisms, three deliverables.

**The emit mechanism is a JVM SPI.** The compiler defines an emit SPI (MIT, toolchain-owned); `ttr-emit-airflow3`, `ttr-emit-kestra` and friends are separate, versioned plugin artifacts whose identities are pinned in `ttr.lock` as canon — the lock discipline was written for exactly this shape. A plugin owns the *orchestration layer only*: it receives the derived orchestration graph, the finished island payloads, and the manifests, and renders the target's workflow format. Island payloads (the SQL, the Polars code) are core-compiler emit; no plugin touches them. **Determinism is a stated SPI obligation** — same snapshot, same bytes — and, because a stated obligation is not an enforced one, the conformance harness ships a double-compile byte-compare kit that any plugin consumer can run, and passing it is a certification requirement for third parties. The existing bash emitter is *extracted* to become the SPI's first, proving consumer — the SPI is proven by extraction, not invented. Rejected: in-tree-only targets (the license boundary would make commercial targets structurally impossible), and template-driven emit (manifests would start carrying behavior, a shape the manifest design explicitly rejected; and every template language becomes an accidental programming language).

**Registration is not a mechanism at all — it is world content.** The TTR-P manifest design already has two layers: an engine-*type* manifest (shipped, now, with the emit plugin — a recorded amendment to the original "ships with the compiler" wording) and an environment-*instance* entry in the world (`extends` the type, adds deltas and a connection *reference*). Registering an orchestrator or a database on the Platform simply *is* creating that instance entry in the platform-managed world — and the metadata server is the registry because it serves the world. The compiler sees registered engines through the ordinary seam, as data. Standalone parity is automatic: a standalone world can declare the same instance entry by hand. The rejected alternatives — a dedicated registry service, or one registry for databases and another for orchestrators — would have created a second source of engine truth beside the world ("world says PG-15, registry says PG-16"), the canonical P3 catastrophe.

**Delegation shape is world-driven.** How a program runs *on* an external orchestrator is an invocation binding declared by the world, never hard-coded, with two day-one contents. In a *platform* world, the binding is a **door-calling DAG**: the emitted Airflow DAG's task simply starts a program-door run and polls — the orchestrator is a scheduling *frontend*, and everything still passes the hall, so policy holds. In a *standalone* world, the binding is a **native DAG**: the emit plugin translates the bundle into real orchestrator tasks that run islands directly — an MIT emit product with no platform on the runtime path at all. The design refuses to blur these: the native DAG is *not* a frontend, and the predictable user confusion between the two shapes is met by naming them. The door's frontend contract is small and pinned: `{start(envelope ref, params), poll/subscribe, cancel}`.

**Harvest, v1, is run results only.** A per-orchestrator connector maps the engine's run events into the platform's ingest contract, so delegated runs land in the same lineage graph as native ones; the same connector doubles as the external-event trigger source. Full metadata harvest deliberately waited for the external-metadata workstream (§11).

**The v1 target set is {bash, Airflow 3, Kestra}, with Dagster the first post-v1 target.** The choice followed a researched field scan of ~27 engines in five tiers. Airflow 3 is the harvest-and-base anchor: the largest installed base and the field's best built-in OpenLineage support — harvest being the surface least affordable to prove against a weak partner. Kestra is the emit-fitness anchor: declarative YAML flows make emitted workflows reviewable *data* (the artifact ethos extended to the orchestrator layer), and it is JVM kin; building the second target in v1 is what proves the SPI is an SPI and not an Airflow-shaped hole. Dagster's hero-scenario mention was exemplary, not contractual: its asset-model impedance and community-grade OpenLineage made it a worse anchor. The enterprise workload-automation tier (Control-M, AutoSys, Stonebranch…) was identified as the *commercial plugin market* — demand-driven, platform-side revenue surface, never anchor material.

## 9. Security: Perun and the policy machinery

The security design fills boxes the service architecture placed, under one verified fact: in Kantheon, "the OPA thing" is **whois** — a user/role directory and OPA bundle server — while Argos validates plans against an in-process policy store, and identity is resolved at an agent-side edge the Platform cannot inherit.

**Policy is Rego, bound to metadata.** Policies live as Rego (plus structured data for row predicates and masks) keyed by metadata qnames — models, worlds, programs, engine instances — and are served as **content-hashed, signed bundles**. Policy is *canon*: reviewable, versioned, pinned. On top of this sits the design's one piece of new language surface: a **TTR `security` block** — MIT sugar, precedented by the E-R→DB shortcut and the `semantics` block — in which a data owner declares the common cases ("accounting reads this entity", "this column is PII: mask") and a deterministic MIT generator turns them into standard policy fragments. Hand-written Rego covers everything else, and composition is **deny-overrides**: sugar grants; hand Rego can always take away. The block never alters emitted plans and is fingerprint-neutral — access changes must not churn world verification. The workflow line is explicit: the security block is the *data owner's* declaration; hand Rego is the *organization's* policy.

**Identity comes from the IdP, and only the IdP.** Every door verifies bearer JWTs through one shared ingress module — bearer-only, enrichment-never-authority, fail-closed, carried verbatim from Kantheon's hard-won invariants. Machine callers are client-credentials service principals from the same IdP; there is no API-key side channel and no platform-native account store (a second identity truth was rejected on arrival). **A scheduled run executes as an explicit, envelope-named service principal — required, with no ambient default**: the deploying-user alternative was rejected for its offboarding ghosts (a person leaves; the nightly keeps running as them), and deployers need an explicit grant to *use* a principal.

**Enforcement is a minimal spine.** Deploy and run authorization at the doors; the transplanted Argos data plane (row-level security ships in v1 *by construction* — Argos arrives working, HOCON store verbatim, and a follow-up arc rekeys that store onto Perun's bundles so data-plane decisions also cite a bundle hash); coarse catalog visibility; Designer writes ride git permissions. **Compile-time policy is advisory only** — hard parity *forces* this, since compile output may never depend on identity; a policy violation at compile is a diagnostic, and the block is at deploy and run time.

**Perun evaluates nothing at runtime — the PEPs do.** Perun (the whois descendant) is the directory plus the policy decision point: it builds, signs, and serves bundles; enforcement points — doors, hall, Veles, the Designer backend — pull bundles and **evaluate locally**, with fail-closed semantics at bundle expiry. The security server being down does not stop the hall (within the expiry window), and every run record cites the bundle hash that was in force — audit joins provenance.

**Secrets are never at rest in the platform.** Connection references (`secret://<store>/<path>`) resolve **at dispatch time** through a secret-store SPI — Kubernetes Secrets as the default binding, Vault and cloud managers as others — and the material is injected into the executing process environment (`TTR_CONN_*`, the standalone contract verbatim, so platform-run and hand-run islands receive credentials identically). Only the connections an island's manifest declares are injected. The invariants are stated and mechanically tested: material never appears in the envelope, run store, logs, lineage events, or any artifact (CI plants canary credentials and asserts their bytes appear nowhere downstream), and **no platform endpoint returns secret material** — resolution is a side effect of dispatch, so there is nothing for a curious client to call. A store unreachable at dispatch fails the island pre-flight, the same failure shape as an unset environment variable in bash-land. Building a native secret store was rejected as the classic build-a-vault trap with rotation drift; the "secret zero" bootstrap is the deployment's configuration, documented rather than solved.

**Trust has two roots**: the IdP for identity, publisher keys for artifacts — emit plugins are signature-verified (verify-if-signed in v1, require-signed as a policy knob), policy bundles are PDP-signed, and the determinism kit closes the gap between stated and verified plugin behavior. Full supply-chain attestation is post-v1, demand-driven.

**Standalone security is integrity, not enforcement.** Standalone artifacts run as the invoking principal with the credentials that principal already holds; checksums and fingerprints verify integrity; optionally, the MIT toolchain lints a program against an exported policy bundle. Enforcement is inherently "operate" and therefore the Platform's. The delegation bypass is bounded by the same logic: *credential-bounded, not policy-bounded* — platform-governed credentials resolve platform-side only, so going around the hall is possible exactly when it was already possible without us.

## 10. The Designer and the write model

The browser Designer is the same MIT React frontend as standalone, pointed at a platform backend, with platform-only panels shipped as extensions. Its v1 is deliberately **reader-first**: catalog, model graph views, run history, **column lineage**, a registration wizard, and **read-only TTR-P program graphs** rendered from the documented bundle manifest — most of life 2's visible value. Edit mode is *designed now and built second*, graduating from behind a feature flag.

The write model is the design's quiet keystone, because three earlier decisions presumed it: **writes go through git.** An edit session works on a branch; save is a commit (through the existing structured-edit path, so comments and layout survive); publish is whatever the repository's own policy says — direct commit or pull request, git's own governance rather than an invented one. Multi-user editing is git semantics plus advisory presence; conflicts surface as review flows, not editor fights. The IDE user and the browser user become the same kind of writer. CRDT-style live co-editing was rejected as the *write model* — a CRDT over a graph that is itself a projection of text with preserved trivia is a research project, and it would have killed the text-is-canonical invariant in spirit — but it is recorded as the within-session future (two users sharing one branch workspace) that composes later without touching the model. Pessimistic locking was rejected as advisory theater in a git-backed world.

Edits split honestly into two idioms with one write path: graph operations for structure (nodes, edges, containers), an embedded text panel for content (expressions, `semantics`/`security` blocks), both producing workspace edits into the session branch. Sessions are hybrid: reads are stateless against Veles; *entering edit mode* materializes a server-side branch workspace on demand; previews ride the query door under the session's principal — at interactive priority, per the quota design.

The registration wizard is the write model's first product: one wizard, kind-parameterized (engines, orchestrators, connections are all the same world-entry concept), producing — under the hood — a commit to the platform-world repo. The standalone story is stated as decisions, not gaps: the IDE view-only path is *permanent*, and standalone has *no data preview*, because preview is execution and execution is "operate."

## 11. World composition and external metadata

**World composition** was the independent review's biggest catch: every converged workstream leaned on "the platform-managed world," which no workstream had designed. The resolution: a project world **declares** the platform world it extends — visible, reviewable text in the project repo (a small grammar surface queued as a TTR-P amendment) — and Veles's resolver composes the two behind the declaration; the lock pins the referenced platform world by content hash, so "the platform's view of the world changed" is literally a reviewable lock diff. Platform entries are authoritative for the facts they state: projects may *add* private instances and *extend* with scoped deltas, but a contradiction is a compile error — one truth per engine instance, by rule rather than inference. The platform world lives in its own git repo (admin edits are commits; edit rights are a policy object), standalone parity comes free through file-exported copies, and the roster of which project repos a server serves is server *configuration*, not world content. Rejected: invisible merge-at-assembly alone (composition nobody can see), the platform world as *the* world (structurally different documents per mode — a parity killer), and per-project copies (N truths — the registry catastrophe, distributed).

**External metadata** resolves the last load-bearing fork with a slogan: **"proposals in, projections out — never sync, never federate."** Inbound canon — schemas harvested from engines, entries from OpenMetadata — arrives as **PR-shaped proposals**: deterministically generated TTR documents on a branch, ratified by a human merging the diff. Robots write through git too. Inbound *statistics* bypass review by temperament and flow directly into the per-object stats store. Outbound, Veles's export connectors emit regenerable projections (OpenLineage-shaped lineage, catalog entries). No object ever has two writers, so conflict machinery — where sync projects go to die — never exists; and live federation was banned from the compile path from the start (a federated answer is unpinnable). Drift *detection* is automatic and advisory; *ratification* is human.

One connector frame serves everything — orchestrator run harvest, data-engine introspection, external catalogs — differing only in mapping, driven by Veles's refresh organ, authenticated with service principals and secret refs. The MIT/platform line is drawn once more with the same pattern: a **one-shot `ttr import-schema` CLI** (introspect a database, generate draft world documents, review, commit) is generator-shaped and MIT — standalone users bootstrap worlds without hand-typing schemas; *continuous scheduled* harvest runs, stores, and schedules, and is Platform. The anchor external system is **OpenMetadata** (its import and export connector pair is v1); DataHub is the first follow-on, its inclusion in v1 consciously declined. Megaproviders are connector families, with one carve-out: the PowerBI semantic-model mapping is a *modeling* problem (it maps meaning, not catalog entries) and is routed to a future TTR-M arc.

## 12. What was deliberately not decided

The design records its deferrals as tracked outcomes with revisit conditions, not as omissions. The monetization model waits for the license-file moment in the repo bootstrap. The standalone-persona question (OSS-forever vs on-ramp) waits for a first external adopter. Multi-tenancy waits behind the single-org v1 anchor, and with it identity-priced quotas. Backfill semantics wait for first real demand. FF (atomic publish) stays reserved with staging-and-swap as its designed mechanism. The reconciler/workers-pull execution architecture is the recorded growth direction, at which point Charon-as-worker-kind reopens. Dagster and DataHub are the named first follow-on targets on their respective tracks. Charon's exemption from quota arbitration is a named, accepted v1 hole. Live co-editing is the within-session future of the git write model.

Two work batches leave the effort: a **TTR-P/TTR-M amendment sweep** (runtime-parameter grammar, error-flow vocabulary, the executor capability manifest, the manifest-contract graduation with its lineage section, the type-manifest ownership amendment, the `extends`-platform-world grammar, and the `security`-block grammar — recorded in one batch under the family's amendment discipline), and two **kantheon-repo arcs** (query-door adoption; the post-split documentation sweep — which includes this repo's own identity statement, since the MIT toolchain is now a client of the platform's published snapshot contract).

## 13. How the design was made

The effort ran 2026-07-08 → 2026-07-09 under the diverge-then-converge protocol: eleven workstreams (A–K), eight load-bearing forks named up front and resolved consciously, at least three alternatives per fork including the deliberately weird one, every decision recorded append-only with its rejected alternatives. An independent review between the sixth and seventh workstreams re-read the whole corpus against ground truth, confirmed every examined decision, and surfaced the three concentrated exposures — world composition, the security rider pile-up, and scope-without-an-acceptance-bar — that the final sessions then closed. A consolidation sweep batch-ratified the accumulated micro-decisions so nothing was decided by drift.

The two organizing ideas the review singled out are worth restating as the design's signature: the **mode-blind compiler behind a source SPI with the canon/observation temperament split**, and **registration as world content**. Both are simple, both were reached by walking the option space rather than by defaulting, and each makes the other cheaper — which is what a converged design is supposed to feel like.

