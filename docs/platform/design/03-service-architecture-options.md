# Tatrman Platform — Service Architecture Options (workstream C)

> Divergence catalogue for **C — the Platform's service roster and boundaries**: what moves from kantheon, what is new, what merges, what dies. Session opened 2026-07-08 (after Q-2 verification in the kantheon repo).
> Companions: [Control Room](./00-control-room.md) · [Design-Space Map](./01-design-space-map.md) · [`02-mode-seam-options.md`](./02-mode-seam-options.md).
>
> **Scope guard:** C draws the *service map* and each service's one-line contract. Scheduler semantics = F; security policy model = H; Designer product = G; repo topology = D. Where a fork depends on one of those, C names the dependency and leans lightly.

## Inherited constraints

- **FI-3/FI-4:** the Platform owns the workers; roster ≥ {workers, browser Designer, metadata server, security server bound to metadata}; "the whole deterministic part of Kantheon."
- **B (converged):** the metadata server's compiler-facing contract = **content-addressed snapshot archives + per-object stats endpoint** (+ later delta-push). B's IOU: C must confirm this is buildable-cheap (→ C-6).
- **P2:** one-way arrow — Platform consumes published `org.tatrman:*` artifacts; kantheon consumes the Platform; no cycles at any intermediate state.
- **MD arc:** `ttr-metadata` lib owns model/graph/search/world resolution; Ariadne is already a thin gRPC facade; `ttr-designer-server` exists (repo-attached, loopback, read-only v1).
- **TTR-P F-lite/F-proper:** the deployed unit today = the bundle (`run.sh` + islands + manifest + world fingerprint); F-proper semantics (events, FF, retries, resume) are *designed to land platform-side*.
- **Hero, life 2:** deploy → schedule nightly → run on workers (Arges+Steropes) → Charon moves → policy authorizes → runs/lineage in metadata.

---

## C-0 · Verified kantheon inventory (2026-07-08, kantheon repo @ HEAD)

Placement verdicts are **provisional** (they converge with C-1); ✂ marks internal splits needed.

| Component | What it is (verified) | Provisional placement |
|---|---|---|
| `services/theseus` | Query orchestrator, the agent's single entry: Proteus → Argos → Kyklop chain | **moves** (the execution spine) — but see C-3 fork |
| `services/proteus` | Translator; already a thin wrapper over `query-translator` → becoming `org.tatrman:ttr-translator` (TR arc) | **moves** (or dissolves into the spine — C-3) |
| `services/argos` | PlanNode validator: RLS predicate injection, column DENY/MASK, TopN, strict coercion, admin bypass; **in-process HOCON policy engine (OPA dropped at Stage 3.2)**; ✂ **LLM Guard DF-V04** = intelligence tendril | **moves** ✂ (LLM Guard seam → C-5) |
| `services/kyklop` | Worker dispatcher (validated physical PlanNode → capable worker) | **moves** |
| `workers/` arges·brontes·steropes | PG / MSSQL / Polars workers | **move** (FI-3 verbatim) |
| `services/charon` | Data movement (Materialize/Stage/Copy/Evict, Arrow, named connections) | **moves** (E-g's Kantheon-world Transfer binding) |
| `services/ariadne` | Thin gRPC facade over `ttr-metadata` (+ QueryParseWorker, RefreshScheduler skin) | **moves-or-dissolves** → C-2 (LF-2) |
| `infra/whois` | User/role directory + **OPA bundle server** (Keycloak/ERP sync, own PG); optional fail-closed role enrichment for Argos | **moves** — the seed of FI-4's security server (H shapes it) |
| theseus-mcp edge (`IdentityResolver`) | Identity resolution: Keycloak JWT → `PipelineContext` | ✂ **splits**: the *edge* is caller-side (Kantheon keeps its MCP edge); the Platform needs its own ingress identity (H) |
| `services/prometheus` | LLM gateway | **stays** (intelligence) |
| `services/kadmos`, `services/echo` | NLP foundation; fuzzy catalog matcher (serves agents/Themis) | **stay** (intelligence-serving; echo is algorithmic but its consumers are agents) |
| `services/kallimachos`, `pinakes`, `report-renderer`, `metis` | Doc-wiki corpus; catalog; rendering; stats/forecast models | **grey zone** — placement sweep with D (metis computes deterministically but serves agent workflows) |
| `agents/` (pythia, themis, golem, hebe, midas, kleio, iris-bff, sysifos-bff), `frontends/` | The intelligence + product surfaces | **stay** (Kantheon) |

**Two structural findings** (already logged at Q-2): the deterministic/intelligence line cuts *through* Argos (LLM Guard), and identity is resolved at an agent-side edge — the Platform can't inherit its ingress identity from theseus-mcp.

---

## C-1 · Roster philosophy — how the Platform gets its services

**Question:** transplant kantheon's shapes, or re-found from the TTR-P design?

- **C-1-α · Transplant.** Move the C-0 "moves" rows as-are; rename later; evolve in place.
  - *Buys:* fastest to a running Platform; the services already work together; git history preserved.
  - *Costs:* imports kantheon's **single-query shape** (Theseus chains one PlanNode; the known no-multi-step-DAG gap) into a platform whose *reason to exist* is running compiled multi-island programs; naming/proto roots (`org.tatrman.kantheon.*`) need sweeping anyway.
- **C-1-β · Re-found.** Define services from the TTR-P vocabulary (worlds, manifests, invocation bindings, bundles, F-proper) and *fill* them with extracted kantheon code — the ttr-metadata pattern applied to execution: libraries extracted, services re-founded around them.
  - *Buys:* the Platform's unit of work is the **compiled program** (bundle/plan set) from day one, not the single query; clean ownership; the extraction-arc muscle already exists (MD, TR precedents).
  - *Costs:* slower; every service transition needs a kantheon adoption arc; risk of second-system effect.
- **C-1-γ · Minimal platform first.** v1 roster = metadata server + workers + dispatcher only; scheduler, security server, Designer-write arrive in platform v1.x — "a platform F-lite."
  - *Buys:* shippable earliest; each later service designs against a running core.
  - *Costs:* FI-4 names security and Designer as roster members — γ defers framing commitments; "run the hero nightly" (life 2) needs *some* scheduler anyway.
- **C-1-δ · Weird: control plane only.** The Platform owns no execution services — it registers customer-run engines/orchestrators and only coordinates (metadata, compile, deploy, observe); workers become an optional add-on pack.
  - *Buys:* radically light; maximally BYO-infrastructure; sidesteps the whole carve-out.
  - *Costs:* contradicts FI-3 ("takes ownership of the workers"); kantheon still needs the deterministic spine *somewhere* — δ just refuses to own it. Catalogued to sharpen why ownership matters: **kantheon (intelligence) must become a *client* of the Platform**, and it can only be a client of something that exists.

*Compound worth naming:* **β-spine + α-leaves** — re-found the *spine* (execution/orchestration around the bundle as unit of work, C-3) because that's where kantheon's shape is wrong for us; transplant the *leaves* (workers, Charon, whois, Kyklop) because their shapes are right.

**Lean: the β-spine + α-leaves compound**, sequenced γ-ish (metadata + spine + workers first). Pure α imports the single-query shape; pure β re-designs services whose shape is already correct.

## C-2 · The metadata server (LF-2)

**Question:** FI-4's metadata server — who is it? It must serve: the **B contract** (snapshot archives, stats, registries), the **Designer** (model graph, later edits), the **runtime** (world verification, run/lineage recording), and **harvesting** (E/I connectors populating worlds/stats).

- **C-2-α · Ariadne comes home.** Move the service into the Platform; extend its facade with the B contract + registries + runs.
  - *Buys:* exists, tested, already thin over `ttr-metadata`.
  - *Costs:* Ariadne's contract is *kantheon's runtime view* (14 RPCs shaped for agents/query pipeline); the B contract, registries, run history, and harvest are all new organs — "comes home" is really "grows a second body"; kantheon then consumes a service it used to own (adoption arc either way).
- **C-2-β · Grow `ttr-designer-server`.** The repo-attached Designer server graduates: multi-repo, persistent, networked, plus the B contract.
  - *Buys:* one server story from laptop to platform (S24's loopback server becomes the platform server's small mode — a satisfying two-mode echo).
  - *Costs:* S24/G-b deliberately scoped it as **editor infrastructure, not platform runtime** — graduating it betrays that boundary and drags editing concerns into the platform's core service; the "small mode = big mode" symmetry is aesthetic, not load-bearing.
- **C-2-γ · New service on the `ttr-metadata` library.** A platform-native metadata server designed around its four consumer classes (compiler-seam, Designer, runtime, harvest), embedding `ttr-metadata` (+`-git`); Ariadne stays kantheon-side as the *agents'* facade, now consuming the Platform server (or its published snapshots) instead of raw git.
  - *Buys:* contract designed for *this* platform's consumers; the library (where the hard logic lives) is reused 100%; Ariadne's fate decouples (it can thin further or dissolve on kantheon's own schedule); the B contract is native, not bolted on.
  - *Costs:* a new service to build (though the MD arc shows the service layer is the thin part: Ariadne ≈ 1,300 LOC of skin); temporary duplication while Ariadne persists.
- **C-2-δ · Weird: OpenMetadata is the backend.** Ship the Platform as an OpenMetadata deployment + a TTR extension (custom entity types for models/worlds/runs); our "metadata server" is a shim.
  - *Buys:* enormous surface for free (UI, connectors, lineage, governance); I's integration question dissolves.
  - *Costs:* TTR's model semantics (worlds, manifests, fingerprints, snapshot archives) become second-class custom types in someone else's schema; the B contract (content-addressed archives, per-object stats keyed by schema-hash) would fight the host; core platform identity outsourced. Belongs in I as a *connector*, not here as the spine.

**Lean: γ**, with α's useful organs (refresh scheduling, QueryParseWorker pattern) absorbed as library/service modules, and the C-2-β *symmetry* preserved only as: both servers embed the same `ttr-metadata` library — sharing the lib, not the service.

## C-3 · The execution spine — unit of work

**Question:** kantheon's spine (Theseus → Proteus → Argos → Kyklop → worker) runs **one query**. The Platform's unit is the **compiled program** (bundle: islands + transfers + control waves). What's the spine's new shape?

- **C-3-α · Transplant + wrap.** Keep the single-plan spine intact; add a **program executor** service on top that walks the bundle's waves and feeds islands/plans into the spine one at a time (and calls Charon for transfers).
  - *Buys:* spine untouched (lowest risk); the program executor is exactly F-proper's new code, cleanly separated; kantheon's ad-hoc single-query path keeps working through the same spine.
  - *Costs:* two orchestration layers (executor over Theseus) — Theseus's "orchestrator" name becomes a lie (it chains translate/validate/dispatch, it doesn't orchestrate programs); per-island trips through the full chain re-validate/re-translate things the compiler already settled.
- **C-3-β · Re-found the spine around the bundle.** One **program executor** natively speaks bundle (waves, FS/SS, transfers, F-proper semantics); translation is gone from the runtime path (**the compiler already emitted concrete payloads** — E-a; Proteus-the-service dissolves into the compile-time `ttr-translator` lib); validation (Argos) and dispatch (Kyklop) become per-island steps the executor calls.
  - *Buys:* honest to the TTR-P design — compile-time and run-time stop duplicating work (T6: the runtime *verifies*, it doesn't re-derive); one orchestration concept; Proteus's dissolution is already half-done (TR arc).
  - *Costs:* kantheon's **ad-hoc query path** (agents compose a PlanNode *at runtime* — no compile, no bundle) still needs translate-validate-dispatch: β must keep a thin **single-plan door** anyway, or kantheon keeps its own mini-spine (duplication P2 arrow tension).
- **C-3-γ · Two doors, one hall.** Name the two entry shapes explicitly: the **program door** (deployed bundles — the Platform's native unit, F-proper semantics) and the **query door** (one validated plan, synchronous — kantheon's agents, Designer previews, debugging). Both converge on the same Argos-validate → Kyklop-dispatch → worker hall; the program door adds waves/transfers/run-state on top.
  - *Buys:* names reality instead of hiding it (the query door is load-bearing for the intelligence half *and* for interactive Designer/debug use); no duplicated hall; each door's contract stays small.
  - *Costs:* two public contracts to version; scheduling/quota interplay between doors (a nightly program vs a burst of agent queries) becomes an explicit design problem (F).
- **C-3-δ · Weird: no spine — workers pull.** The Platform is a queue + state store; workers (and Charon) pull work items; the "executor" is a reconciler writing desired-state (very Kubernetes). Maximal decoupling, minimal services.
  - *Buys:* resilience/scale story for free; F-proper's retries/resume become queue semantics.
  - *Costs:* a paradigm shift from everything extracted; synchronous query-door latency suffers; debugging a declarative reconciler is its own art. Catalogued as the architecture the scheduler (F) may *grow toward*, not start at.

**Lean: γ, with β's Proteus dissolution** — the program door is new (F-proper's home); the query door is the transplanted, slimmed Theseus chain (minus runtime translation for pre-compiled work); Argos/Kyklop/workers = the shared hall, transplanted per C-1's α-leaves.

## C-4 · Movement — Charon's place

**Question:** Charon today is a kantheon service (Materialize/Stage/Copy/Evict over named connections). In TTR-P, Transfer is an *abstract node* whose binding is world-driven (E-g: Charon in Kantheon-worlds, native tools in bash-land).

- **C-4-α · Transplant as the Platform's movement service.** Charon moves in, unchanged contract; the platform-world Transfer binding targets it (E-g realized literally).
- **C-4-β · Charon becomes a worker kind.** Movement = just another capability a worker advertises; Kyklop dispatches Transfer work items like any island; no dedicated movement service.
  - *Buys:* one dispatch path, one scaling story. *Costs:* movement's connection-pair topology (source×target) fits the capable-worker model awkwardly; Charon's staging/evict lifecycle is stateful in ways islands aren't.
- **C-4-γ · Weird: no movement service — transfers always emit as generated code** (the F-lite pattern universalized: ADBC/psql scripts run *as islands* on workers). Charon retires.
  - *Buys:* fewer services; artifact-visible movement everywhere. *Costs:* loses streaming/zero-copy Arrow paths and central connection governance; regresses the platform to bash-land's mechanism — the platform world *should* have a richer binding (that's what E-g's generalization was *for*).

**Lean: α** — transplant; E-g already designed the seam so both worlds coexist. β re-examined when F's scheduler exists (it may make Kyklop-dispatched movement natural); γ rejected-shaped but catalogued.

## C-5 · Security placement (interface to H)

**Question:** C only places the boxes; H designs the policy model. Placement forks:

- **C-5-i · Argos** moves with the hall (per C-3) — RLS enforcement stays glued to dispatch (the only placement that keeps "no path to data around the validator"). Its **LLM Guard** (DF-V04): (a) **drops** platform-side (a deterministic platform shouldn't call LLM-as-judge), (b) becomes a **callback to Kantheon** (the intelligence half judges when configured — inverts the arrow: Platform *optionally calls* Kantheon; P2 says the *build* arrow, is a runtime callback legal?), or (c) becomes a **pluggable validator SPI** (Platform defines the hook; Kantheon ships a plugin; arrow preserved — the plugin is Kantheon-owned code conforming to a Platform contract). *Lean: (c), which subsumes (a) as "no plugin installed."*
- **C-5-ii · whois-descendant** = FI-4's "security server bound to the metadata": directory + policy bundles, now keyed to metadata objects (models/worlds/programs), OPA machinery revived per H's design. *Lean-shaped, but H owns it.*
- **C-5-iii · Ingress identity.** The Platform needs its own edge (JWT → principal) — it cannot inherit theseus-mcp's. Every door (C-3-γ), the Designer, and the metadata server share one ingress identity discipline. New design work; land it in H with Kantheon's fork-§6 invariants (bearer-only identity, enrichment-never-authority, fail-closed) carried over as prior art.

## C-6 · B's IOU — is the compiler-facing contract buildable-cheap?

**Confirmed cheap, with one design note.** Evidence: `ttr-metadata` already has snapshot semantics (`SourceSnapshot`, `fetchVersion()`, storage SPI) and `GitArchiveStorage` proves the archive path; a content-addressed archive endpoint over a resolved snapshot is skin, not organs (the MD arc's 1,300-LOC-skin precedent). Per-object stats = a new small store keyed `{qname, object-schema-hash, observed-at}` (BQ-2) — a table + an endpoint; harvesting *fills* it (E/I's job, later). Delta-push explicitly deferred (BQ-3). **Design note:** the snapshot archive must be assembled from the *resolved world* (post-`hosts:` resolution), not raw files — so the archive builder sits behind the same resolver the Designer uses, in the C-2-γ server. **B's confirmation: granted.**

---

## Cross-links out

- **→ F:** C-3-γ hands F the two-door scheduling/quota problem + the program door's run-state model; C-3-δ recorded as F's possible growth direction; C-4-β re-opens with F.
- **→ H:** C-5's three placements; ingress identity; whois-descendant shape; LLM-Guard-as-SPI legality (runtime plugin vs P2's build-time arrow).
- **→ D:** the grey-zone services (kallimachos, pinakes, report-renderer, metis) need a placement sweep when D sequences the split; C-1's compound implies extraction arcs for the spine, transplant moves for the leaves.
- **→ E/I:** harvest connectors write into the C-2-γ server (worlds population + stats store); registered-engine registry lives there too (B-4 item 1).
- **→ G:** the Designer's platform backend = the C-2-γ server (its Designer-facing organ), not a grown designer-server (C-2-β rejected-shaped).
- **→ A:** the C-2-γ/C-3 line means the *entire runtime* is Platform-side; nothing in the OSS repo grows a service dependency — P1/P2 hold.

## Open questions (C)

- **CQ-1:** Does kantheon's ad-hoc query path call the Platform's query door from day one, or keep its own mini-spine during transition? (Sequencing — D.)
- **CQ-2:** Run/lineage records — do they live in the C-2-γ metadata server or in a separate run-store the scheduler owns? (F decides; metadata server serves *reads* either way.)
- **CQ-3:** Is a runtime callback/plugin from Platform to Kantheon-owned code (C-5-i-c) compatible with P2, which was stated as a *dependency* arrow? Propose: P2 constrains *build-time* dependencies; runtime plugins conforming to Platform-defined SPIs are legal. Needs ratification.
- **CQ-4:** Worker capability manifests (B-T6) vs Kyklop's current capability routing — same vocabulary or a mapping layer?

## Divergence exit check

C-1..C-4 walked with weird options and leans; C-5 is placement-only (H owns the substance); C-6 discharged B's IOU. To converge: Bora's read on **C-1's compound (β-spine + α-leaves)**, **C-2-γ**, and **C-3-γ** — these three lock the service map; C-4/C-5 follow quickly behind.
