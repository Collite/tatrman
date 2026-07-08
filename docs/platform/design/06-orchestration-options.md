# Tatrman Platform — Orchestration-Engine Integration Options (workstream E)

> Divergence catalogue for **E — orchestrators appearing twice** (GI-1): as compiler **emit targets** and as **platform-registered engines**. One mechanism or two? What is a "plugin"? What is a "registered engine"? Session opened 2026-07-08 (after F converged).
> Companions: [Control Room](./00-control-room.md) · [Design-Space Map](./01-design-space-map.md) · [`05-scheduler-options.md`](./05-scheduler-options.md) · TTR-P [`07-emit-options.md`](../../ttr-p/design/07-emit-options.md).
>
> **Scope guard:** E designs the *mechanisms* — emit-plugin SPI, engine registration, delegation shape, harvest scope. Scheduling semantics = F (converged). Inbound metadata harvest at large + megaproviders = I (E pins only the connector *frame* it shares). Individual plugin placement (MIT vs platform) = already decided per A (mechanism MIT; plugins either side).

## Inherited constraints

- **GI-1:** orchestrators as emit targets (Dagster / Airflow / … / bash) **and** as platform-hosted/registered engines with live-ish metadata, statistics, contents.
- **LF-4 (last unresolved LF but LF-7/LF-8):** emit mechanism = data-driven manifests + core codegen · JVM SPI plugins · out-of-process — *and* is platform registration the same mechanism or a second one?
- **TTR-P B-T6 (the designed substrate):** execution engines carry **capability manifests** — parameterized declarative entries; **two layers**: engine-**type** manifest (ships with the toolchain) + environment-**instance** overlay in the **world doc** (`extends` type + deltas); manifests are facts, separate from compiler rewrite knowledge; the execution-layer graph is derived (container-collapse); invocation bindings choose per-container delivery.
- **TTR-P E-a/E-g:** artifacts carry **concrete payloads**; PlanNode emission is world-driven; Transfer is an abstract node bound per world. The **bash emitter is the prototype emit target** (F-lite's `run.sh` = the orchestration-layer emit for the bash executor).
- **A-1:** the emit-plugin *mechanism* = MIT; individual plugins may live on either side of the license line. **D-2:** license boundary = repo boundary — so *in-tree* emit targets can never be commercial. **D-3:** toolchain-touched contracts ⇒ tatrman-owned (MIT).
- **B:** **BQ-4** — emit-plugin versions are **canon, pinned in `ttr.lock`**, part of the recorded snapshot; **B-3-α** — compile is a pure function of the snapshot, so **plugins must be deterministic and identity-pinned**; **B-4** — the seam admits *registries* (data) — a registered-engine registry is legal seam content.
- **C-2-γ:** the registered-engine registry lives in the metadata server; export connectors exist (outbound). **F (converged):** F-1-γ — external orchestrators are *alternative frontends to the program door* (E owes this the pressure-test); F-2-β — the bundle **manifest's graph is authoritative**; F-4-v-γ — external events ride E's connectors; F-6-β — run/lineage events ingest into the metadata server.
- **P1/P2/P3**; hero **life 3**: Dagster registered, program emitted as a Dagster DAG, scheduled there, lineage flowing to OpenMetadata.

---

## E-1 · The emit-target mechanism (LF-4's emit half)

**Question:** adding an orchestrator emit target (Dagster, Airflow, …) — by what mechanism? Note the scope line first: **emit plugins own the *orchestration layer*** (what F-lite's bash emitter does: waves → `run.sh`) — island payloads (SQL, Polars) are core-compiler emit (T5/E machinery) and **no plugin touches them**. Adding a *data engine* is core toolchain work (manifest + rewrites + codegen); adding an *executor* is what E-1 mechanizes.

- **E-1-α · In-tree: manifests + core codegen (the status quo).** Each executor = a type manifest + an emit module inside the compiler; adding one = a compiler PR (bash's F-lite pattern repeated).
  - *Buys:* no SPI to design or version; conformance harness covers every target natively; BQ-4 pinning is trivial (the compiler version *is* the plugin version).
  - *Costs:* every target rides the MIT compiler's release train; third parties must fork to add targets; **a commercial/platform-side emit target is structurally impossible** (D-2: in-tree = MIT) — contradicts A-1's "plugins may live either side."
- **E-1-β · JVM SPI plugin artifacts.** The compiler defines an emit SPI (MIT, tatrman-owned per D-3); `ttr-emit-dagster` etc. are separate versioned artifacts; the project declares plugins, `ttr.lock` pins them (BQ-4 verbatim); the SPI hands the plugin the **derived orchestration graph + finished island payloads + manifests** and receives bundle content.
  - *Buys:* BQ-4 was *written for this shape* — plugin identity is already canon in the lock; third parties and the platform ship targets without forking; the bash emitter refactors into the first in-tree SPI consumer (the SPI is proven by extraction, not invented — the MD/TR arc discipline applied to the compiler's own edge).
  - *Costs:* an SPI is a standing public contract (versioning discipline, compatibility guarantees); determinism must be a *stated SPI obligation* (B-3-α: same snapshot ⇒ same bytes — a plugin that timestamps its output breaks parity); JVM-only plugin authorship.
- **E-1-γ · Template/data-driven emit.** Orchestrator descriptions as data (templates + manifest); one generic DAG-to-template emitter in the core.
  - *Buys:* cheapest per target; no code execution from third parties (templates are inert — a real trust win).
  - *Costs:* weakest idiomatic output (real Dagster wants resources, partitions, retry policies — templating fights every step); the template language becomes an accidental programming language (the usual fate); capability manifests already carry the *facts* — γ makes them carry *behavior* too, which B-T6 deliberately rejected (manifests ≠ rewrite rules).
- **E-1-δ · Weird: neutral DAG + external adapters.** The compiler emits no orchestrator formats at all; the **bundle manifest's graph** (authoritative per F-2-β) *is* the neutral DAG; adapters *outside the compiler* (deploy-time, platform-side or standalone CLIs) read the manifest and build the Dagster/Airflow representation.
  - *Buys:* the compiler stays small forever; the manifest-graph contract does double duty; adapters can run where deployment context lives (an adapter can inject platform URLs, secrets refs — things a pure compile *must not* know, B-4).
  - *Costs:* the emitted DAG is **not part of the reviewed compile artifact** — what actually runs on Dagster was never in the reviewable bundle (weakens the artifact-as-reviewable-text ethos); pinning/parity discipline (BQ-4/B-3-α) doesn't reach deploy-time adapters — a second, weaker reproducibility regime needs stating.

*Interlock:* β and δ **compose along the B-4 line** rather than compete: compile-time plugin (β) for everything reviewable and world-derivable; deploy-time adapter (δ) for what only the platform knows (endpoints, principals, schedule wiring — exactly the envelope's cargo, F-2-β). The fork is not "β or δ" but *where the line between them sits*.

**RESOLVED 2026-07-08 → E-1 = β (JVM SPI plugin artifacts), with the bash emitter extracted as the SPI's proving plugin, and δ acknowledged as the platform's deploy-time half** (the door-calling wrapper of E-3 is an adapter, not an emit plugin; the β/δ line = the B-4 line). SPI = tatrman-owned MIT (D-3); plugin identity pinned per BQ-4; **determinism is a stated SPI obligation** (B-3-α); plugins own the orchestration layer only. Rejected: α in-tree-only (D-2 makes commercial targets structurally impossible); γ templates (manifests would carry behavior — B-T6 rejected that shape already).

## E-2 · What is a *registered engine*? (the registry hunch, pressure-tested)

**Question:** GI-1b says the Platform "hosts and registers orchestrators like it registers databases." Is a registered database and a registered orchestrator **one registry concept**?

- **E-2-α · A dedicated registry service/table.** Platform-side config: rows of engines (kind, endpoint, credentials, harvest settings) managed by admin API; worlds are unaware.
  - *Buys:* simple ops story; nothing touches the language.
  - *Costs:* **duplicates T6** — the world doc already declares engines with instance manifests; two sources of engine truth (world says Postgres-15, registry says Postgres-16) is a P3 catastrophe waiting; the compiler can't see the registry (B-4 would have to smuggle it).
- **E-2-β · Two registries by kind.** Data engines stay world-declared (T6); orchestrators get their own platform registry (they're "operational," not "modeled").
  - *Buys:* matches the intuition that orchestrators are ops-config.
  - *Costs:* the intuition is wrong by our own design — **executors are world citizens in TTR-P** (B-T6: bash is an executor with a type manifest *in the world*); an orchestrator-only registry re-splits what T6 unified; delegated compiles (E-3) need the orchestrator's capability manifest *at compile time* — which is exactly what worlds are for.
- **E-2-γ · Registration = world content, served by the metadata server.** Registering an engine (database *or* orchestrator) = the platform-managed world gains an **environment-instance entry** (B-T6's second layer: `extends` the type manifest + deltas + connection ref); the metadata server *is* the registry because it *serves the world* (C-2-γ); the compiler sees registered engines through the ordinary seam (B-1: a source binding — B-4: registries are legal seam data); harvest connectors (E-4) *fill* the entries.
  - *Buys:* **the hunch confirmed with zero new concepts** — one registry concept because both were already the same concept (world-declared engines with manifests, differing in manifest *kind*); T6's two layers do the whole job (type manifest ships with the emit plugin; instance entry = the registration); standalone parity is automatic (a standalone world can declare a Dagster instance by hand — the platform just *manages* the entries).
  - *Costs:* "registration UX" becomes world-editing (admin actions must produce world/lock changes — fine under G-γ-flavored writes-through-git, but the UX must hide the plumbing); secrets can't live in world docs (connection *refs* only; resolution is platform-side — H).
- **E-2-δ · Weird: the connector is the registration.** No declarative record; installing/configuring a harvest connector *is* registering; the registry is emergent from running connectors.
  - *Buys:* zero registry schema. *Costs:* P3 head-on — engine existence becomes an invisible runtime fact derived from plugin state; the compiler and the world never learn what's registered; unreviewable.

**RESOLVED 2026-07-08 → E-2 = γ: registration = world content (B-T6's instance layer), served by the metadata server, which thereby *is* the registry.** The hunch confirmed with zero new concepts; secrets enter as connection *refs* only (resolution platform-side, H). LF-4's second question answers itself: registration is *not* a mechanism — it's world content; the *mechanisms* are the emit SPI (compile-time) and the connector SPI (platform-side), meeting in the world entry. Rejected: α dedicated registry (second source of engine truth — P3 catastrophe); β two registries (re-splits what T6 unified); δ connector-is-registration (engine existence as invisible runtime fact).

## E-3 · Delegation shape — how a program actually runs *on* Dagster (F-1-γ's pressure-test)

**Question:** F said external orchestrators are "alternative frontends to the program door." What does the emitted/deployed thing actually look like?

- **E-3-α · Door-calling DAG.** Emit/adapt a Dagster DAG whose ops call the **program door** (platform executes; Dagster owns calendar + operator visibility). Granularity sub-fork: **α-1** one op = one program run (Dagster sees a black box); **α-2** per-island ops mirroring the wave graph (Dagster sees structure — but now *two* executors walk the same graph, and drift between the Dagster mirror and the door's authoritative walk is a standing risk).
  - *Buys:* F-1-γ's "frontend" made literal; policy hall intact (everything still passes Argos/Kyklop); run store stays authoritative.
  - *Costs:* Dagster's value shrinks to scheduling + a status light (α-1); α-2 buys visibility at the price of double-orchestration.
- **E-3-β · Self-contained native DAG.** The emit plugin translates the bundle into *native* Dagster ops that run islands directly against the engines — the platform is **not on the runtime path**. This is F-lite's pattern with Dagster instead of bash: an MIT-legal, standalone-flavored target (A-1: "compile" side).
  - *Buys:* works with zero platform (a pure OSS user with Dagster gets real value — P1 fuel); Dagster-native retries/observability apply for real.
  - *Costs:* **not a frontend at all** — the policy hall is bypassed exactly like F-3-δ's runner (no Argos, no run store) — *acceptable in standalone worlds, disqualifying as the platform's delegation story*; F-4 semantics only as good as the target's manifest honestly declares.
- **E-3-γ · World-driven: both shapes as invocation bindings.** Which shape a program gets is declared by the world (T6 fractal, E-g precedent): a **standalone Dagster world** binds β (native DAG, no platform); a **platform world with Dagster registered** binds α (door-calling adapter, hall intact). The same program compiles to either by changing worlds — the two-mode story (FI-1) applied to delegation.
  - *Buys:* names the real situation — GI-1's two halves are *different products* (β = an emit target; α = a platform feature) sharing the manifest concept; no shape is smuggled in as the other.
  - *Costs:* both shapes must exist and be conformance-tested; the docs must teach the difference (the α/β confusion is *the* predictable user error).
- **E-3-δ · Trigger-only registration.** A registered orchestrator only *fires* door runs (a sensor/cron op calling "run program X") — no DAG structure exported at all.
  - *Buys:* thinnest possible integration; already legal under F-1-γ (a trigger source). *Costs:* it's α-1 minus the deploy convenience; Dagster shows one opaque op. Catalogued as the degenerate case α-1 collapses into.

**Pressure-test verdict to record:** F-1-γ **holds** — for α/δ. β is *not* a frontend and never was; it's a compile-side emit target that competes with the platform rather than fronting it. The frontend contract the door must expose for α: `{start program run(envelope ref, params), poll/subscribe run state, cancel}` — small, and F-6-β's operational-query API already carries most of it.

**RESOLVED 2026-07-08 → E-3 = γ, in force from day one** — γ is the *rule* (delegation shape is a world-declared invocation binding, never hard-coded), and α-1/β are its two day-one contents: **α-1 = the platform-world binding** (door-calling whole-program op; hall intact), **β = the standalone-world binding** (native DAG via the emit plugin — MIT-legal, *not a frontend*). α-2 (per-island mirroring) recorded as a later visibility upgrade *within* the α binding, carrying a stated drift cost. Pressure-test verdict logged: F-1-γ holds for door-calling shapes; the door's frontend contract = `{start(envelope ref, params), poll/subscribe, cancel}`. Rejected: any single shape smuggled in as the other; δ noted as α-1's degenerate case.

## E-4 · Harvest scope (GI-1b's "live-ish metadata, statistics, contents")

**Question:** what flows *back* from a registered engine, through what?

- **E-4-α · Nothing in v1.** Registered = dispatchable/delegable, period.
  - *Buys:* zero build. *Costs:* hero life 3's "lineage appears in OpenMetadata" dies — a delegated run reports nothing; F-6's lineage graph goes blind for delegated work.
- **E-4-β · Run-results harvest.** A per-orchestrator connector maps the engine's run events (Dagster run records) into the platform's run/lineage event contract (F-6-β's ingest path — delegated runs land in the same lineage graph as native runs); F-4-v-γ's *external event triggers* ride the same connector (a sensor-ish subscription).
  - *Buys:* life 3 works end to end; one ingest contract for native and delegated runs; the connector frame is exactly what I needs inbound (shared SPI, built once).
  - *Costs:* per-engine mapping code (run-model impedance: Dagster partitions/retries vs our run/attempt ids — FQ-2's schema must leave room); eventual consistency for delegated lineage.
- **E-4-γ · Full harvest.** Schemas, datasets, statistics from registered *data* engines too — the whole inbound half.
  - *Buys:* worlds and the stats store (BQ-2) get fed automatically. *Costs:* this is **I's workstream entire** — doing it here front-runs I's truth-direction fork (LF-7). E pins the connector SPI *shape*; I decides what flows.
- **E-4-δ · Weird: bidirectional.** The platform also *pushes* (schedules, run requests, materialization state) into the orchestrator's own metadata so Dagster is a first-class mirror.
  - *Buys:* native-feeling Dagster ops console. *Costs:* two sources of run truth; sync-conflict machinery for operational state; LF-7's hardest option imported into E for the least reason.

**RESOLVED 2026-07-08 → E-4 = β (run-results harvest only in v1)**, with the connector SPI explicitly shared with I (one frame: connector = {engine kind, auth ref, ingest mapping, optional event subscription}) and γ/δ left for I/LF-7. Rejected for v1: α (life 3's lineage dies); γ (front-runs LF-7); δ (two sources of run truth).

## E-5 · The manifest-graph contract (E-1-δ's residue)

**Question:** F-2-β made the bundle manifest's graph authoritative; E-1-δ/E-3 adapters and third-party tools want to read it. What is its contract status?

- **E-5-α · Internal.** Versioned implicitly by the compiler; adapters we ship parse it, others at their own risk. *Cheap, and a lie — F-2-β already made it the thing the door executes.*
- **E-5-β · Documented-stable, tatrman-owned (MIT).** The manifest schema (graph section + fingerprints + checksums) becomes a versioned public contract per D-3 (toolchain-touched ⇒ tatrman-owned) — the F-lite JSON manifest graduates to a spec'd format with a schema version field.
- **E-5-γ · Standardize outward.** Embed/map to an external standard (OpenLineage-flavored graph). *Buys interop; costs fidelity (our waves/FS/SS/transfers don't map 1:1) — the mapping belongs in C-2-γ's export connectors, not in the artifact.*

**RESOLVED 2026-07-08 → E-5 = β (documented-stable, versioned, tatrman-owned MIT manifest schema)**, with γ's external-standard mapping living platform-side in the export connectors. The manifest is already load-bearing three ways (door execution, F-7 provenance, adapters) — pinning it is overdue hygiene, not new surface. Rejected: α internal (a lie after F-2-β); γ in-artifact (fidelity loss belongs in connectors).

---

## Synthesis: the orchestrator support package (LF-4 answered in shape)

Under the leans, "supporting Dagster" =

1. an **emit plugin** `ttr-emit-dagster` (E-1-β; MIT or commercial per A-1) — compiles bundles to native DAGs for standalone Dagster worlds (E-3-β), carrying the **executor-type manifest** (B-T6 layer 1);
2. a **registration** = a world instance entry in the platform world (E-2-γ; B-T6 layer 2) — created by admin UX, served by the metadata server;
3. a **platform adapter + harvest connector** (E-1-δ/E-4-β; platform-side) — deploys door-calling DAGs (E-3-α-1) and ingests run/lineage events (F-6-β), doubling as the external-event trigger source (F-4-v-γ).

One *concept* (world-declared engine with a manifest), two *mechanisms* (compile-time SPI, platform-side connector), three deliverables per orchestrator. **LF-4 resolves as: JVM SPI for emit; registration is not a mechanism but world content; the platform half is connector-shaped.**

## Hero rendering ("one program, three lives")

- **Life 1:** bash = just another executor with an in-tree/first-party emit plugin after the E-1-β extraction; nothing observable changes.
- **Life 2:** untouched — no orchestrator in the loop; E adds nothing to the native path.
- **Life 3, standalone variant:** OSS user, no platform: Dagster world + `ttr-emit-dagster` → native DAG (E-3-β), scheduled and observed in Dagster. MIT end to end.
- **Life 3, platform variant:** Dagster registered (world entry, E-2-γ); the deploy pipeline's adapter emits a door-calling DAG (E-3-α-1); Dagster fires nightly → program door → hall (policy intact); run events harvest back (E-4-β) into lineage; C-2-γ export connectors push OpenLineage-shaped lineage to OpenMetadata.

## Cross-links out

- **→ F:** the door's frontend contract (start/poll/cancel) formalizes F-6-β's operational API; E-4-β lands F-4-v-γ's external events; delegated runs enter the run store as harvested, not executor-walked.
- **→ I:** the connector SPI frame is shared property; E-4-γ/δ explicitly deferred to LF-7; anchor-system choice (EQ-3 ↔ I's "pick one anchor").
- **→ B:** SPI determinism is a stated plugin obligation (B-3-α); plugin identity pinned per BQ-4; registered engines reach the compiler only as world content through the seam (B-1/B-4).
- **→ A/D:** SPI interface = tatrman-owned MIT (D-3); plugin artifacts placeable either side (A-1); nothing in-tree may be commercial (D-2).
- **→ H:** registration entries carry connection *refs*, never secrets; harvest connectors authenticate platform-side; delegated-run principal = FQ-5's sibling question.
- **→ G:** registration UX = world-editing with plumbing hidden (G's writes story constrains the admin flow).
- **→ J:** names — the SPI, the support-package convention, `ttr-emit-*` coordinates.

## EQ-3 dive · The anchor-orchestrator field (researched 2026-07-08)

> The anchor = the engine the emit SPI, door-calling adapter, and harvest connector are built and conformance-tested against **first**. Bora asked for the long list. Each contender is scored on E's three integration surfaces — **emit** (E-3-β: how idiomatically waves/FS/SS/retries render, and whether the definition format is *data* or *code*), **frontend** (E-3-α-1: trigger/poll/cancel API quality), **harvest** (E-4-β: run events + lineage, OpenLineage affinity) — plus **manifest honesty** (what its B-T6 type manifest could truthfully declare) and **base/audience fit**. Web-researched July 2026; sources at the end.

**A structural finding first.** The field splits on *definition format*, and it matters more than popularity: **data-defined** engines (YAML/JSON workflow definitions — Kestra, Argo, Step Functions, ADF, Control-M, even SQL Agent's T-SQL) take emit as *pure data generation* — deterministic, diffable, reviewable text in the bundle, our artifact ethos extended to the orchestrator layer. **Code-defined** engines (Python DAGs — Airflow, Dagster, Prefect, Flyte, Mage) take emit as *codegen* — heavier to make deterministic (B-3-α obligation), but idiomatic for their users. A second finding: **the enterprise-WLA tier is precisely A-1's commercial-plugin market** — nobody gives away a Control-M emitter; that's a platform-side revenue surface, not an anchor candidate.

### Tier 1 · Modern data orchestrators (the anchor candidates proper)

1. **Apache Airflow 3** (3.0 GA April 2025) — *emit:* Python codegen (TaskFlow), workable, not beautiful; DAG-versioning in 3.x helps the deploy story. *frontend:* mature REST API (much improved in 3.x). *harvest:* **OpenLineage support is first-class/built-in** — the strongest lineage story in the field. *manifest:* retries, pools, event-driven scheduling (AIP-82) — honest, rich. *base:* the largest by far; the managed trio (MWAA, Cloud Composer, Astronomer) = a free showcase of B-T6's type-vs-instance split (one type manifest, three instance overlays).
2. **Dagster** — *emit:* Python codegen; its **asset-centric model is an impedance** — our unit is program/islands, theirs is assets; job-based Dagster fits better but is the less-loved half. *frontend:* GraphQL API, well-trodden trigger/poll patterns. *harvest:* OpenLineage via community package (weaker than Airflow's built-in); native lineage excellent *inside* Dagster. *base:* the dbt-native analytics crowd. Hero life 3 names it — as an *example*, not a commitment.
3. **Prefect** — *emit:* Python codegen, dynamic-flavored (our static waves under-use it). *frontend:* clean deployment/flow-run API. *harvest:* thinnest lineage story of the big three. *base:* Python DX loyalists.
4. **Kestra** — *emit:* **declarative YAML flows — the best emit fit in the entire field** (a generated flow is reviewable data in the bundle); polyglot tasks; native event triggers. *frontend:* REST API; its own plugin ecosystem proves the trigger/poll pattern both directions. *harvest:* OpenLineage integration (present, younger than Airflow's). *bonus:* JVM-based — ecosystem kinship with our toolchain. *base:* growing fast 2025–26, but the youngest bet of the four.
5. **Mage** — notebook-flavored DX; contributor-momentum warnings in 2025–26 surveys; weak enterprise story. Not anchor material.

### Tier 2 · General-purpose / infra engines

6. **Temporal** — durable-execution engine, not a batch-DAG scheduler; emit target awkward (workflows are SDK code with app-level semantics). Better read as **prior art for F-3's internals** than as a target. Not an anchor.
7. **Argo Workflows** — K8s-native CRDs; emit = clean YAML (data-defined ✓); but requires K8s in the customer's world, data-lineage story thin, contributor-momentum warnings. Fine later target, not the anchor.
8. **Flyte** — typed ML pipelines, K8s; same momentum warnings; ML-shaped, not our center.
9. **Apache DolphinScheduler** — visual DAG scheduler, strong APAC-enterprise base; API-defined workflows; momentum warnings in western surveys. Later target.
10. **Windmill** — scripts→workflows; interesting, niche.
11. *(Boundary marker:* **NiFi** — flow-based *streaming* dataflow; wrong paradigm; catalogued only to mark where "orchestrator" ends.)

### Tier 3 · Cloud-managed (mostly instance overlays or I's territory)

12. **AWS Step Functions** — ASL JSON: purest data-defined emit imaginable; per-cloud lock-in; no OpenLineage affinity; harvest via EventBridge. A clean later plugin.
13. **Managed Airflow (MWAA / Cloud Composer / Astronomer)** — not separate targets: **the Airflow type manifest + three instance overlays** (B-T6 layer 2 doing its job).
14. **Azure Data Factory / Fabric pipelines** — JSON-defined (emit ✓); but this is **GI-3 megaprovider territory — I's workstream**, entangled with the PowerBI/Fabric metadata story; deciding it here would front-run I.
15. **Google Cloud Workflows** — thin; skip.
16. **Databricks Lakeflow Jobs** — orchestrator fused to a data engine we don't run as a worker; *engine-attached orchestration* — a registration question for I-era worlds, not an anchor.
17. **Snowflake Tasks** — DAGs *inside* a data engine: the boundary case where the orchestrator manifest is an organ of a data-engine manifest. Catalogued for the manifest model's sake; no anchor relevance.

### Tier 4 · Enterprise WLA (the commercial-plugin market, not the anchor)

18. **BMC Control-M** — declining mindshare (≈14% from ≈25% year-over-year) but deeply entrenched where our MSSQL/ERP audience lives; jobs-as-JSON Automation API makes emit *feasible*; harvest thin. **Prime commercial platform-side plugin.**
19. **Broadcom AutoSys/Automic** — declining; JIL is text (emit feasible); grim APIs. Commercial-plugin candidate on customer demand only.
20. **Stonebranch UAC** — *rising* hybrid-IT WLA (50% of enterprises prioritizing WLA investment in 2026); modern API. Watch.
21. **Redwood RunMyJobs** — rising SaaS WLA. Watch.
22. **IBM Workload Scheduler** — mainframe adjacency; demand-driven only.
23. **SQL Server Agent** — the weird-but-real one: ubiquitous in the Brontes audience; emit = generated T-SQL (`sp_add_job…`) — pure reviewable text; zero harvest. A cheap, oddly credible OSS-side target for MSSQL shops.
24. **pg_cron** — same shape for PG, even smaller.

### Tier 5 · Weird/adjacent (spectrum markers)

25. **cron/systemd timers** — the degenerate baseline; **bash + cron is already shipped** (F-lite). Nothing to build.
26. **GitHub Actions / GitLab CI** — CI-as-scheduler is a real-world pattern (teams do run nightly data jobs there); YAML emit trivial; no data locality, quota chaos. Catalogued honestly; not endorsed.
27. **Jenkins / Rundeck / K8s CronJob** — legacy CI / runbooks / infra substrate; not integration targets.

### The lean

**Anchor = Airflow 3; fast-follow = Kestra; Dagster third.** Reasoning: the anchor must exercise all three surfaces *hard*, and harvest is the surface we can least afford to prove against a weak partner — Airflow's built-in OpenLineage is the field's best, its 3.x event-scheduling maps directly onto F-4-v-γ, its REST API carries the door-calling adapter, and its installed base means the first plugin lands where the most users are; the managed trio showcases the type/instance manifest split for free. Kestra follows because it is the **emit-fitness champion** (data-defined flows = our artifact ethos) and JVM kin — and building the *second* target early is what proves the SPI is an SPI, not an Airflow-shaped hole. Dagster demotes to third with no hard feelings: hero life 3's mention was exemplary; its asset-model impedance and community-grade OpenLineage make it a worse *anchor* while remaining an obvious early target. The counter-case for a Kestra anchor (younger base, but every integration surface is cheapest there) is real — recorded so the rejection, if Bora ratifies the lean, is conscious.

*Sources:* [Airflow 3 GA announcement](https://airflow.apache.org/blog/airflow-three-point-oh-is-here/) · [AWS on Airflow 3 event-driven/AIP-82](https://builder.aws.com/content/3EVroFClVTDy6Y2oSi6N3b59Bty/whats-new-in-apache-airflow-30-event-driven-orchestration-asset-aware-workflows-and-scalable-architecture) · [State of workflow orchestration ecosystem (pracdata)](https://www.pracdata.io/p/state-of-workflow-orchestration-ecosystem-2025) · [Orchestration landscape quadrant, March 2026](http://npow.github.io/posts/workflow-orchestration-market-quadrant-2026/) · [Dagster OpenLineage docs](https://docs.dagster.io/integrations/libraries/openlineage) · [openlineage-dagster (community pkg)](https://pypi.org/project/openlineage-dagster/) · [Kestra↔Dagster trigger/poll plugin](https://github.com/kestra-io/plugin-dagster) · [Top data-orchestration comparisons 2026 (lakeFS)](https://lakefs.io/blog/data-orchestration-tools/) · [WLA mindshare 2026 (PeerSpot)](https://www.peerspot.com/categories/workload-automation) · [Stonebranch 2026 State of IT Automation](https://www.stonebranch.com/resources/analyst-reports/global-state-of-it-automation)

## Open questions (E-local)

- **EQ-1 · SPI surface pin-down** — exact inputs an emit plugin receives (derived orchestration graph + island payloads + type/instance manifests + project defaults?) and outputs (bundle overlay?). Planning-stage; the *boundary* (orchestration layer only, never island payloads) is design content, recorded in E-1.
- **EQ-2 · Plugin distribution & trust** — coordinates in `ttr.lock` (BQ-4 implies Maven-style identity), signature/verification story for third-party plugins. Planning-stage; H-adjacent.
- ~~**EQ-3 · v1 anchor orchestrator**~~ **RESOLVED 2026-07-08 (Bora): BOTH — v1 executor-target set = {bash, Airflow 3, Kestra}.** Consciously widens the pick-one-anchor discipline: the SPI-proving argument (a second target is what proves the SPI isn't an Airflow-shaped hole) is promoted from fast-follow to v1 scope; Airflow 3 = the harvest/base anchor, Kestra = the emit-fitness/data-defined anchor, bash = the already-shipped F-lite floor. **Dagster = first post-v1 target.** WLA tier remains the commercial-plugin market (demand-driven).
- **EQ-4 · Do *data-engine* instances harvest through the same connector frame** (schemas/stats into worlds/BQ-2 store)? Shape says yes; substance is I's (LF-7).

## Convergence status

**🟢 E IS CONVERGED (2026-07-08)** — E-1 β JVM SPI (bash extracted as proof) · E-2 γ registration-is-world-content · E-3 γ world-driven delegation from day one (α-1 platform binding, β standalone binding; F-1-γ pressure-test passed for door-calling shapes) · E-4 β run-results harvest (connector SPI shared with I) · E-5 β documented-stable manifest contract · **EQ-3: v1 executor targets = {bash, Airflow 3, Kestra}; Dagster first post-v1**. **LF-4 resolved** via the support-package synthesis (one concept, two mechanisms, three deliverables). **Riders out:** connector SPI frame + EQ-4 → I; secrets/trust (registration refs, plugin signing) → H; registration UX = world-editing → G; EQ-1/EQ-2 → planning work items. Per-orchestrator v1 build: two full support packages (Airflow 3, Kestra) + the bash emit plugin extraction.
