# Z · Optimizer — Option Catalogue

> Workstream Z divergence doc, opened 2026-07-07. Companion to [`00-control-room.md`](./00-control-room.md), [`02-internal-model-options.md`](./02-internal-model-options.md) (B — node set, T8 rewrites, manifests), [`06-model-binding-options.md`](./06-model-binding-options.md) (D — world), [`07-emit-options.md`](./07-emit-options.md) (E — preserved shape), [`08-orchestration-options.md`](./08-orchestration-options.md) (F-lite — waves, staging).
>
> **The question Z must answer:** given one TTR-P graph, a declared world (data engines, executors, storages, staging), and per-engine costs, choose the **execution plan** — which engine runs each operation, where movement happens, what gets materialized — such that the plan is (near-)optimal under a declared objective, found in acceptable compile time, and explainable. Z is **v2 design-ahead**; nothing here changes v1.
>
> **Status:** diverging. Option catalogue only — no decisions.

**What's already constrained (not on the table):**

- **v1 placement is author-assigned** (B-T9 · v1 placement): containers carry targets; the compiler re-places nodes only via T5-b escalation (capability miss → whole-node re-placement). Z layers cost on top of *capability* — the manifest format "already has room for cost attributes" (T6).
- **Preserved-shape emit** (E-b): optimization is deliberately *not* done at emit. Z is a distinct pipeline stage; emit stays a faithful printer of Z's output graph.
- **Control constraints are hard on their effect**; the optimizer may rewrite the surrounding graph only while preserving that effect — never drop it (B-T2). "Reorderable" = parallelism (absence of FS) only.
- **Movement is synthesized deterministically** (C3-d-iv, D-f): engine-crossing = Store + Transfer + Load via declared staging; `via` override exists. **Materialize = macro** → Store + (Index) + Load (B-T9, S13). Z's output vocabulary already exists in the model — an optimized plan is just a graph with (re)assigned containers, synthesized movement, and materialize macros. Nothing downstream of Z changes.
- **The world is a compile target** (T6/T4): type manifests + instance overlays; runtime verifies compatibility. Costs and stats, wherever they live, follow the same type/instance split.
- **Arrow IPC at every staging boundary** (F-c-i): every run yields *exact* intermediate row counts and byte sizes for free.
- **P2 · No miracles** — read carefully for Z: the optimizer must be **deterministic given its declared inputs** (same graph + world + stats ⇒ same plan). Cost *estimates* are allowed to be wrong — they affect performance, never results (A4 equivalence holds under any placement, by construction; `ttrp-conform` literally tests placement variants).
- **`ttrp explain`** (S4) exists as the transparency surface; Z must feed it (chosen placement, alternatives, cost breakdown).
- **Kotlin-only toolchain** (G-b). Any solver dependency must be JVM-reachable.

**Grounding inputs (Bora, 2026-07-07 — first batch).** Decision-shaped constraints stated during divergence; cited as GI-n below. Not yet converged decisions, but they narrow the forks:

- **GI-1 · Division of labor confirmed:** intra-island optimization belongs to the engine's optimizer; Z's goal is to **distribute the islands** — never compete with Snowflake's join ordering. (Settles Z-a's direction: β.)
- **GI-2 · Z also SHAPES the islands.** Containers serve several distinct roles: (a) **graphical grouping only** — no engine annotation; (b) **engine boundary** — with engine annotation; (c) the author's engine annotation can be a **hint** ("I'd rather run this in PG") or a **directive** ("this MUST run in PG"). A graph may have **no containers at all** (Z draws them — internally and for `explain`), containers that **may be re-drawn**, or containers that **must not be touched**. ⚠ *Model consequence:* v1's "container bears the execution target" (B-T9) generalizes in v2 — target becomes optional (grouping-only containers exist) and annotated targets carry a **strength** (hint | directive). Flagged as a B-model v2 amendment candidate; surface syntax → ZQ9.
- **GI-3 · Scale ceiling (answers ZQ1):** realistic ceiling **< 100 nodes**; beyond that, telling the user "this is un-optimizable" is acceptable (explicit failure, P2-friendly). Consequence: exact solving is viable across the whole supported range; the heuristic tier is demoted to seed/warm-start, not a fallback path.
- **GI-4 · Compile budget (answers ZQ2):** yes, budgeted — for the Designer, a **user setting**, analogous to choosing a model + thinking level ("fast / balanced / thorough"). Fits an anytime exact solver (best-found + optimality gap at budget exhaustion).
- **GI-5 · Materialize-with-index (answers ZQ4):** confirmed as a single macro choice; **indexing after materialization is precisely the data-engineering trick built-in optimizers don't normally do** — it is Z's kind of win. Index *selection* (which columns) stays parked.
- **GI-6 · Static world now, elastic world later (extends ZQ6):** v2 assumes a **static world** (engines + instances given). Long-term direction: **elastic worlds** — K8s pods, spawning another Python worker for night batches — where scheduling optimization and **$$$ enter for real**. Possibly a separate optimization task; strong preference that the **same solver machinery/interface** serves it. Recorded in the long-term register below; shapes Z-g's interface design (world = input, not constant).
- **GI-7 · Z-g lean ratified:** swappable solver backends behind a `PlacementSolver` interface — enthusiastically confirmed.
- **GI-8 · Meta-approach: "steal the ideas, develop ourselves."** Formally rejects Z-c-α (adapt Calcite as Z) and Z-c-δ (adopt Wayang) as *machinery*; both stay as required reading. Z-c narrows to the γ → β trajectory, own Kotlin code.

**Grounding inputs — second batch (Bora, 2026-07-07, Z-b/Z-d session):**

- **GI-9 · Z-b = β (makespan) for Z 1.0 — agreed.** γ (multi-objective) comes later; the *direction* must be known now: evolution or a different engine? (Answered in Z-b's γ-direction note below: **evolution**, conditional on one decide-now item — dimensioned cost vectors.)
- **GI-10 · Objective PROFILES.** Weights are **world-specific**; the optimizer function is **separate** from the world; expect a few named **profiles** with different optimization requirements (e.g. interactive-fastest vs nightly-cheapest-under-deadline). Maps onto the T6 pattern: profile *shapes* = toolchain vocabulary, profile *instances* (weights, deadlines) = world doc; selection per build (`[ttrp]` default + `--profile`).
- **GI-11 · Statistics live in the WORLD (metadata level).** Stats may be refreshed often, but **the optimizer never calls real instances** — it works only on metadata. The world schema gains **statistical info for physical tables** (row counts, sizes; a `schema world` / storage-level extension). Settles Z-d: α is the mechanism (β probing formally rejected as an optimizer behavior); γ's feedback loop is reframed as *refresh tooling that writes world stats* — outside the optimizer, which stays offline and deterministic.
- **GI-12 · Multi-objective timing (answers ZQ3's when):** not soon — Z versions as 1.0, 1.1, …; multi-objective = **Z 2.0**. But its *inevitability* should influence present decisions — hence the decide-now list in ZQ3.
- **GI-13 · The optimizer runs against a metadata SERVER.** The world is **served, not read**: the (planned) tatrman metadata component, in served form, provides the world *and reads/serves the stats*. Stats in the committed repo = "nonsense" — rejected. Consequence accepted: **no server ⇒ no (refreshed) stats.** ⚠ *Tension to design consciously:* D-g/T6 say compile is offline (compiler embeds the metadata component, reads repo paths, no service). Reconciliation shape: **correctness-compile stays offline; the optimization pass may consume a served world+stats source** — same metadata interface, two backings (embedded-over-repo = declared/stale stats; served = fresh stats). Determinism (P2) is preserved by *recording*: the plan/bundle records the stats snapshot (fingerprint) it optimized against — same snapshot ⇒ same plan. Degradation ladder when the server is absent → ZQ10.
- **GI-14 · Z-f = β now, δ later — agreed** (see Z-f + the version roadmap: δ arrives as generator; ordering resolved multi-objective-first).
- **GI-15 · ZQ10 boundary CONFIRMED (Bora, 2026-07-07):** *"correctness-compile stays offline, the optimization pass may consume served metadata — plus P2 hygiene: the bundle records the stats snapshot fingerprint it optimized against, so 'deterministic' stays true as 'same inputs including stats snapshot ⇒ same plan'."* A conscious, optimize-pass-scoped refinement of D-g's offline stance — to be entered in the control-room decision log at Z's convergence (the compiler-correctness path is untouched). Bundle-manifest consequence: a `stats` fingerprint field beside the world fingerprint (F-f amendment at Z 1.0 implementation).
- **GI-17 · ZQ5 ANSWERED (Bora, 2026-07-07):** **per-build optimization in Z 1.0; plan caching explicitly DEFERRED** (noted in the roadmap; a cached plan would key on graph + world + stats fingerprints — design when needed).
- **GI-18 · ZQ7 CLARIFIED — two metadata modes (Bora, 2026-07-07):** the repo *can* be read directly. **Server-connected mode:** metadata (world + stats) is served. **Serverless mode:** a repo is attached and read (declared stats only). Same metadata interface, two backings — refines GI-13 (which over-stated "served, not read"). **Stats *acquisition* is the server's design, out of Z's scope: the optimizer just asks, if stats are available.** Z's design needs the **CONTRACT** (the stats-query interface: what can be asked, what comes back, snapshot fingerprint semantics), never the mechanism. → contract sketch = a "before converging" work item.
- **GI-19 · ZQ10 degradation ladder ANSWERED (Bora, 2026-07-07):** **no metadata at the start of the optimize pass ⇒ hard error** ("I have no metadata"); **source lost later ⇒ proceed on cached metadata with a stale warning** ("working with cached data which might be stale"). Coheres with GI-18: serverless-mode repo metadata = a valid source (declared stats); the hard error is for *no source at all*.
- **GI-20 · ZQ8 ANSWERED (Bora, 2026-07-07):** **`prefer <engine>` alone is the hint vocabulary for Z 1.0.** No avoid-transfer / materialize-here hints — those intents are covered by existing explicit (directive-strength) syntax: `store`, `via`, `together`. **The question board is empty.**
- **GI-16 · ZQ9 ANSWERED in full (Bora, 2026-07-07):** (a) **keyword pair** `target` (directive) / `prefer` (hint) — bare `target` keeps v1 meaning; (b) **`together`** cohesion keyword — the fourth cell is real; (c) **grouping-only = α, program text**: a target-less `container` is the one grouping mechanism — **no separate `.ttrl` lassos** ("containerization is not ttrl"); `.ttrl` carries view state like *collapsed* (where pure grouping shines), skins decide how containers draw; (d) **containers are the only hint-bearing construct** — no node-level pin syntax; (e) **penalties stay out of program text** — just the `prefer` hint; deviation costs are profile/world content. Consequence of (c): grouping-only containers are closed functions (ports declared) — accepted; no persisted pure-visual boxes exist.

**Prior art:**
- **tatrman-poc** (`~/Dev/tatrman-poc`, ex `~/Dev/tatrman`): `Planner` pre-expands the pipeline per environment (`expand()` + `moveTables()` insert candidate ops and MOVE ops across environments), then `Optimizer` runs a **uniform-cost frontier search**: priority queue of partial `GraphPath`s ordered by cumulative cost; pop cheapest, extend with every applicable op on the current table frontier; stop at the first path satisfying mandatory requirements. Costs = `CostDef(op, opEnv, srcEnv, tarEnv) → CostVal(fixed, variable, discount[])`, JSON-parameterized. Dijkstra-like and therefore *correct* — and exponential: the state space is (partial plan × environment assignment), path deduplication is effectively broken (`similarPathExists` hash logic), no memoization of shared substructure, no branch-and-bound, no admissible heuristic (it's uniform-cost, not A*), discounts stubbed. **Concepts worth keeping regardless of Z's machinery:** the hint/requirement split (mandatory vs soft + `WillingnessToPlease` as a soft-constraint budget), `IMaterializationStrategy` as a pluggable seam, cost definitions as data.
- **Apache Calcite** — VolcanoPlanner; federation via `Convention` traits + `ConverterRule`s (this *was* Calcite's founding use case, as Optiq).
- **Apache Wayang (ex-Rheem)** — JVM cross-platform optimizer: per-operator platform choice, conversion operators, pluggable cost model, plan enumeration with lossless pruning ("RHEEM: Enabling Cross-Platform Data Processing", PVLDB 2018).
- **Musketeer** (EuroSys 2015) — front-end/back-end decoupling with cost-based backend choice. **BigDAWG** polystore — islands + CAST operators.
- **Operations research:** Stone's task-assignment problem (1977 — module→processor assignment with communication costs; **exactly solvable by min-cut for 2 processors**, NP-hard for k≥3 = multiway cut); **HEFT** (heterogeneous earliest-finish-time list scheduling — the classic "DAG onto heterogeneous processors with communication costs" heuristic); **CP-SAT** (Google OR-Tools — assignment *and* makespan scheduling, Java bindings); **Choco** (pure-JVM CP); **Timefold/OptaPlanner** (metaheuristic local search — named in the brief).
- **Cascades** (Graefe) — memo structure, groups, physical properties, enforcers; the lineage of every modern SQL optimizer.

---

## Z-a · Division of labor — what Z optimizes at all

The brief names three optimizations: classical rewrites, materialization, engine placement. They are not equally Z's to solve — **the target engines already have world-class optimizers for whatever lands inside an island** (Postgres reorders joins; Snowflake prunes partitions). Preserved-shape emit (E-b) already leans on this.

- **Z-a-α · Full-stack optimizer.** Z does classical algebraic optimization (join reordering, redundant-join elimination, …) *plus* placement.
  - *Buys:* helps weak engines (a naive Polars script doesn't reorder joins); one engine-independent quality bar.
  - *Costs:* competes with Postgres/Snowflake on their home turf and loses; explodes Z's search space with rewrites whose benefit the engine would have delivered anyway; duplicates per-engine optimizer knowledge Tatrman can't maintain.
- **Z-a-β · Boundary-only optimizer.** Z decides **island boundaries and what crosses them**: placement, movement, materialization + only the rewrites that change *boundary economics* — filter/project pushdown across a prospective transfer (move less data), partial-aggregate-before-transfer, materialize-before-reuse (multicast fan-out), index-before-join-on-stored. Island interiors are the engine's problem.
  - *Buys:* Z's search space shrinks by orders of magnitude; Z does only the job *no engine can do* (no engine sees the cross-engine picture); rewrite set is small, enumerable, and provably boundary-relevant; consistent with E-b.
  - *Costs:* dataframe islands get no algebraic help (Polars does execute lazily with its own optimizer, softening this); "boundary-relevant" needs a crisp definition (a rewrite is in scope iff it changes the cost of some cut edge or enables a different cut).
- **Z-a-γ · β + opt-in island rewrites for weak engines.** Manifest flag `optimizes: false` on an engine type ⇒ Z also runs a classical rewrite pass on islands assigned there.
  - *Buys:* covers the naive-script case without competing with real optimizers.
  - *Costs:* a second rewrite tier; deferrable until an actually-weak engine ships.

*Lean: β for Z's first cut; γ's seat noted (manifest flag reserved). α recorded as rejected-leaning: it is what makes the problem look impossibly big.* **GI-1 confirms β's direction** (distribute the islands, never compete with the engine's optimizer) — pending only formal convergence.

## Z-b · The objective function

The old PoC minimized **cumulative cost** (sum over ops). But F-a's runtime is **wave-parallel**: two islands on different engines run concurrently — elapsed time is the **critical path (makespan)**, not the sum. The choice changes which machinery fits.

- **Z-b-α · Cost-sum.** Minimize Σ op-cost + Σ transfer-cost.
  - *Buys:* simplest models (pure assignment; min-cut applies directly); proxies "$" and total resource burn well.
  - *Costs:* systematically wrong about wall-clock time under parallelism — it will happily serialize everything onto the one fastest engine when spreading across two engines halves elapsed time.
- **Z-b-β · Makespan.** Minimize the critical path under the wave/FS/SS semantics the bundle actually executes.
  - *Buys:* optimizes what the user asked for ("time"); rewards parallel placements; matches F-a exactly — Z's cost model and `run.sh`'s execution model are the same model.
  - *Costs:* placement becomes placement+scheduling (harder class); min-cut no longer exact; needs per-engine concurrency assumptions (can one engine run two islands at once? → executor manifest content).
- **Z-b-γ · Weighted / lexicographic multi-objective** (time + $, later energy…). Declared weights (P2: the weighting is an input, not a guess); Pareto front surfaced via `ttrp explain`.
  - *Buys:* the brief's "$ and maybe something else" seat.
  - *Costs:* premature — no $ model exists for v2's engines; multi-objective search costs enumeration effort.

*Lean: β as the target semantics, with α as an admitted simplification tier if scheduling proves heavy (a cost-sum optimum is still a good plan, just not the best one). γ = declared-weights layer later; the objective enum belongs in `[ttrp]` or the world doc either way.* **GI-9 confirms β for Z 1.0.** GI-4's budget knob may select the tier: "fast" = cost-sum assignment (min-cut exact on 2-engine worlds), "thorough" = full makespan scheduling — α survives as a speed setting, not a design alternative.

**γ-direction note (recorded 2026-07-07, answering GI-9's question): evolution, not a different engine** — *provided one decision is made now.*

1. **Decide now — dimensioned resource vectors, not scalar costs.** Manifest cost shapes must record *dimensioned* quantities (cpu-seconds, bytes-moved, rows-scanned, wall-seconds), with **prices per resource** in the world instance ($/cpu-second on this Snowflake warehouse, $/GB on this egress link). Then *every* objective — time, $, energy — is a function over the same resource vector, and switching objectives never touches manifests, stats, or the model builder. Cost of this decision: near zero (record vectors instead of pre-collapsed scalars). Cost of *not* deciding: re-collecting every manifest when $ arrives. This is exactly what ZQ3 warned about, now concrete.
2. **Solver machinery is objective-agnostic.** Weighted sum = same single solve. Lexicographic ("fastest, then cheapest among ties") = standard sequence of solves with progressive constraints. Pareto front = repeated ε-constraint solves — more compute, same machinery, and GI-4's budget knob already governs compute. Nothing here is a new engine.
3. **The batch profile is the beautiful case:** "finish by 06:00, minimize $" = makespan as a *constraint*, $ as the *objective* — the same CP-SAT model with objective and constraint roles swapped. GI-10's profiles are precisely this: named (objective, constraints, weights) tuples.
4. **The only new-engine trigger:** non-linear/stochastic objectives (risk-aware percentile deadlines, spot-price uncertainty). If that day comes, metaheuristics re-enter *behind the same `PlacementSolver` interface* (GI-7's swappability is the insurance policy). Elastic worlds (GI-6) likely arrive together with this class.

## Z-c · Search machinery — the core fork

- **Z-c-α · Adapt Calcite's VolcanoPlanner.** Engine = `Convention` trait; Transfer = `ConverterRule`; cost = time. Federation is Calcite's founding use case; the mechanism genuinely exists, on the JVM we already ship (ttr-translator).
  - *Buys:* memo + rule engine + cost pruning for free; battle-tested; we already know Calcite.
  - *Costs:* (1) **vocabulary** — RelNode is relational-only; Z must reason about control edges, containers, movement, Display, error ports: the same mismatch that made B-T3 reject PlanNode as the internal model, now at the optimizer layer. Round-tripping the full graph through RelNode loses exactly the apparatus Z exists to optimize. (2) **Trees, not DAGs** — Volcano costs plans as trees; multicast fan-out (shared subresults) gets double-counted; no real spool/materialization reasoning — and materialization is a third of Z's brief. (3) **No makespan** — cost is a scalar sum (Z-b-β unreachable). (4) Opaque, famously hard-to-debug planner internals for a problem that isn't its center of mass.
  - *Verdict-shaped note:* Calcite stays exactly where E put it — emit-time translation of relational islands. Using it *as Z* means fighting it everywhere our problem is distinctive.
- **Z-c-β · Own Cascades-style memo optimizer (Kotlin).** Memo of groups (logically-equivalent sub-graphs) × physical implementations; **engine/location = a physical property**; **Store+Transfer+Load = property enforcers** (precisely how Cascades treats Sort); branch-and-bound with an upper bound from a greedy initial plan (e.g. HEFT). The memo's sharing is the disciplined fix for exactly what made the PoC exponential — brute force enumerated whole assignment states with zero substructure sharing.
  - *Buys:* full generality — rewrites and placement searched *together* (see Z-f); native home for materialization-as-enforcer; our vocabulary, our cost model, no impedance mismatch; the "own optimizer later" seat B-T3 explicitly reserved.
  - *Costs:* a serious framework build (memo, groups, rule application, property derivation, duplicate detection — the parts that take Calcite/ORCA years to harden); Cascades also assumes tree-shaped cost accounting — DAG fan-out and makespan need real extensions, not just elbow grease.
- **Z-c-γ · Two-phase: deterministic rewrites → placement as an explicit combinatorial model.** Phase 1: the boundary-relevant rewrite set (Z-a-β) runs as a T8-style deterministic rule pass. Phase 2: placement/materialization/scheduling posed as a discrete model and handed to an exact solver — **CP-SAT** first candidate: assignment vars x[node, engine], cut edges pay transfer (size-dependent; route deterministic via D-f staging so cost = f(bytes, engine-pair)), materialization and coupled rewrites as boolean choice vars, makespan via interval variables + cumulative constraints (CP-SAT's home game). **Special case: a 2-engine world under cost-sum is Stone's problem — exactly solvable by min-cut in polynomial time.** v2's likely worlds (PG + Polars) are 2–3 engines.
  - *Buys:* *provably optimal* under the declared model — what the brute force was groping for; graphs of hundreds of nodes × a handful of engines solve in ms–s; tiny bespoke-code surface (the model builder, not a search framework); solver is swappable behind an interface; deterministic (fixed seed/params — P2-clean); naturally emits the explain payload (objective breakdown per decision).
  - *Costs:* rewrites the model-builder didn't anticipate can't be discovered (mitigations in Z-f); solver dependency (see Z-g sub-fork); cost model must be *expressible* as linear/interval terms (fine for fixed+variable-on-size shapes, the PoC's own CostVal form).
- **Z-c-δ · Adopt Apache Wayang.** The closest existing system to the problem statement, on the JVM.
  - *Buys:* the whole pipeline exists: operator inflation to platform alternatives, conversion operators, cost interfaces, enumeration with lossless pruning.
  - *Costs:* adopting its operator model couples Z's core to someone else's vocabulary — the B-T3 argument a third time; project health/bus-factor risk; integration ≈ writing translators to and from *another* internal model.
  - *Verdict-shaped note:* **steal, don't adopt** — its enumeration algebra and pruning proofs (Rheem paper §4) are required reading for whichever option wins.
- **Z-c-ε · Metaheuristics** (Timefold/OptaPlanner local search; or plain HEFT list scheduling; simulated annealing over assignments).
  - *Buys:* anytime behavior (good plan under a compile-time budget); handles messy/nonlinear cost models; HEFT is ~a page of code and specifically targets "DAG onto heterogeneous processors with communication costs".
  - *Costs:* no optimality or gap guarantee; Timefold is real infrastructure weight for a compiler pass; for this problem's plausible sizes, an exact solver simply dominates.
  - *Verdict-shaped note:* not the core — but HEFT earns a seat as the **greedy seed / upper bound** (for γ's solver warm-start or β's branch-and-bound) and the fallback for pathological graph sizes.

*Lean: **γ** as the v2 machinery — with min-cut as the fast exact path for 2-engine cost-sum worlds, HEFT as seed/fallback (ε), Wayang's enumeration ideas studied first (δ), Calcite untouched at emit (α rejected for Z), and **β named as the acknowledged v3 evolution** if rewrite×placement coupling (Z-f) outgrows what choice variables can encode.* **GI-8 ("steal the ideas, develop ourselves") formally rejects α and δ as machinery** — both remain required reading; the γ → β own-code trajectory stands. **GI-3 (<100 nodes)** makes exact solving viable across the whole supported range; ε shrinks to seed/warm-start only, and "graph too large" becomes an explicit compile diagnostic, not a silent heuristic downgrade.

## Z-d · Cost model & statistics

Whatever the search, garbage estimates ⇒ garbage plans. Orthogonal to Z-c; the axis with the most P2 texture.

**Where costs live:** engine-**type** manifests carry cost *shapes* (fixed + variable-per-row/byte per node kind — the PoC's `CostVal` form, now per T6's parameterized entries); engine-**instance** overlays in the world doc carry *calibration* (this Postgres is 4 vCPU; this Snowflake is an XS warehouse; this link does ~80 MB/s). Type/instance is T6's existing split — costs are just more manifest content.

**Where cardinalities come from — the real fork:**

- **Z-d-α · Declared stats.** Row counts/sizes declared in the world doc (per storage/model object), defaulted pessimistically when absent.
  - *Buys:* P2-perfect (deterministic, offline, declared); zero infrastructure.
  - *Costs:* stale and coarse; selectivity of intermediate ops still needs textbook heuristics (the part every optimizer gets wrong).
- **Z-d-β · Compile-time probing.** Ask engines (`ANALYZE`, `COUNT(*)`, catalog stats) while compiling.
  - *Buys:* fresh numbers.
  - *Costs:* **breaks the offline-compile stance** (D-g: compiler reads repo + world doc from paths, no services at compile time) and P2's determinism (same source, different day, different plan, no visible cause). If it ever exists it must be a separate explicit step (`ttrp stats pull` writing *declared* stats into the world/instance overlay) — i.e. it collapses into α with tooling.
- **Z-d-γ · Feedback from runs.** Every staging boundary is Arrow IPC (F-c-i) ⇒ every run records **exact** intermediate row counts and byte sizes; per-island wall-clock is in the logs; the run manifest (F-f) is the natural carrier. Feed observed sizes back as instance-overlay stats; re-optimize with reality. Most optimizers dream of this loop; the architecture gets it by construction.
  - *Buys:* self-calibrating where it matters (the boundaries — exactly what Z optimizes); observed, not estimated.
  - *Costs:* needs a recorded-stats home + staleness/fingerprint policy (world fingerprint already exists to key it); first run is uncalibrated (α's defaults cover it).
- **Z-d-δ · Learned cost models** (Wayang-style ML calibration). *v3+ at the earliest; recorded for completeness.*

*Lean: α + γ — declared floor, observation-fed calibration keyed by world fingerprint; β collapses into α-with-tooling if wanted; δ parked. Selectivity estimation stays deliberately dumb v2 (declared or default constants): Z's wins come from boundary economics where sizes are observed, not from guessing filter selectivities.*

**Fork resolved in principle by GI-11 + GI-13:** stats are **world content at the metadata level, served by the metadata server** — never the committed repo (rejected as "nonsense"), never a direct optimizer→engine call. **The optimizer never calls real instances** — β is rejected *as optimizer behavior*, permanently: Z is a client of the metadata interface with **two backings (GI-18)** — server-connected (world + stats served, fresh) and serverless (repo attached and read; declared stats only). No server ⇒ no *refreshed* stats, accepted. γ survives *reframed*: Arrow-staging observations and engine-catalog pulls are **acquisition paths of the server**, upstream of the optimizer. Determinism is preserved by snapshot recording (ZQ10): same graph + world + **stats snapshot** ⇒ same plan. Remaining sub-questions: ZQ7 (server's acquisition + staleness), ZQ10 (degradation ladder, D-g boundary).

## Z-e · Granularity & author interaction

v1 authors draw containers and assign targets. What does Z's arrival do to that authoring model?

- **Z-e-α · Z assigns author-drawn containers only.** Containers stay author-authored; Z picks each container's engine.
  - *Buys:* smallest delta from v1; author's structure respected.
  - *Costs:* the *boundaries themselves* are the biggest lever (splitting a container to push a filter engine-side is where the wins live) and α forecloses it; authors must pre-guess good islands.
- **Z-e-β · Node-granularity placement; containers derived.** Z assigns engines per node and *derives* island/container boundaries (the T9 container-collapse machinery already turns islands into the execution graph). **Author-assigned targets become pins** — hard constraints (the PoC's mandatory requirements); unpinned nodes are Z's to place; `target auto` (or absence) marks consent.
  - *Buys:* full optimization space; v1 programs remain valid (fully-pinned = Z is a no-op — clean migration story); pins double as the escape hatch when Z's cost model is wrong; hint vocabulary (soft preferences + a `WillingnessToPlease`-style budget) has a natural revival here.
  - *Costs:* containers become partially optimizer-output — the graphical surface must render *derived* islands distinctly from authored ones (C1 has the derived-canvas precedent); "my container got split" needs explain-grade justification.
- **Z-e-γ · Containers atomic by default; fission only where T5-b already splits.** Middle road: Z places containers, may split only at capability-forced seams.
  - *Buys:* bounded surprise.
  - *Costs:* arbitrary line — capability misses may split but cost wins may not; the interesting optimizations are exactly cost-driven splits.

*Lean: β with pins. It preserves the v1 contract (pins = author-assigned targets), makes Z opt-in per node, and keeps Z's output inside the existing model (derived containers + synthesized movement + materialize macros), so emit/bundle/conform are untouched. `ttrp explain` shows: pins honored, placements chosen, cuts paid, materializations inserted, and why.*

**GI-2 supersedes the α/β/γ framing with a richer container-role taxonomy** — the fork is no longer "which granularity" but "the model supports the full spectrum":

| Container state | Z's freedom |
|---|---|
| no containers at all | Z places nodes freely and **draws** the islands (internal + `explain`) |
| container, no target | pure graphical grouping — **no placement meaning**; Z may split/ignore for placement |
| container + target as **hint** | soft preference — Z pays a declared penalty to deviate (→ ZQ8 weights) |
| container + target as **directive** | hard pin — contents run there, period (the v1 semantics) |

Consequences to carry: (1) **B-model v2 amendment candidate** — container target becomes *optional* and gains a *strength* attribute (v1 files where every container has a target = all-directive, so v1 programs keep their exact meaning); (2) grouping-only containers reconcile with the C1 orchestration view (container-collapse must now distinguish placement islands from visual groups); (3) surface syntax for hint vs directive and target-less containers → **ZQ9**; (4) hint-deviation penalties are the natural revival of the PoC's soft requirements + `WillingnessToPlease` budget (ZQ8).

## Z-f · Rewrite × placement coupling

Some rewrites only pay off under placements you haven't chosen yet (push the aggregate below the transfer *iff* the transfer happens); some placements only make sense after a rewrite. How coupled is the search?

- **Z-f-α · Strict two-phase.** Rewrite pass, then placement. *Buys:* simplest. *Costs:* misses cross-phase wins (the aggregate-pushdown example is real, not hypothetical — it's the hero scenario's shape).
- **Z-f-β · Coupled rewrites as choice variables.** The known boundary-relevant rewrites (Z-a-β's short list) are encoded *into* the placement model as alternatives — e.g. each potential cut edge carries optional "pre-filtered/pre-aggregated volume" variants with their conditions. The solver picks rewrite and placement together.
  - *Buys:* exact co-optimization for the enumerated couplings; the list is short *because* Z-a-β made it short.
  - *Costs:* model-builder complexity grows with each coupled rewrite; unforeseen couplings still missed.
- **Z-f-γ · Iterate to fixpoint.** Rewrite → place → re-rewrite under the chosen cuts → re-place, until stable. *Buys:* catches second-order effects cheaply. *Costs:* no optimality claim; oscillation needs a tie-break (T8's termination-measure discipline applies).
- **Z-f-δ · Unified search** = Z-c-β's memo, rules and placement in one space. The full answer, at full price.

*Lean: β (+ γ as a cheap safety pass), consciously bounded: if the coupled-rewrite list stops being short, that is the evidence that promotes Z-c-β from "v3 evolution" to "build it now".*

**GI-14 (Bora, 2026-07-07): agreed — β now, δ later.** Ordering question raised: is δ the Z 2.0 and multi-objective Z 3.0, or the reverse? → resolved in the **version roadmap sketch** below (recommendation: multi-objective first — δ's memo, when it comes, arrives as a *generator* feeding the same solver, not a replacement).

## Z-g · Runtime, solver dependency & component home

- **JVM vs Rust:** stay **Kotlin**. Z is a compile-time search over graphs of tens-to-hundreds of nodes — nothing needs Rust's profile; every relevant solver is JVM-reachable; Wayang is existence proof that this problem class lives happily on the JVM. Rust re-enters only via the parked "own execution engine" item — a different feature with its own parking-lot row. (If Z ever needs a native solver, that's a JNI dependency, not a language migration.)
- **Solver sub-fork (for Z-c-γ):** **α · OR-Tools CP-SAT** (Java bindings; native lib packaged per-platform; the strongest free solver for assignment+makespan) vs **β · Choco** (pure JVM, no native deps, weaker at scheduling scale) vs **γ · own min-cut + branch-and-bound** (zero deps; exact for the 2-engine case via Stone; hand-rolled beyond that). *Lean: define `PlacementSolver` as a Kotlin interface; ship γ's min-cut fast path + HEFT fallback as the dependency-free floor, α behind an optional module — the interface is the decision, the backends are swappable.*
- **Component home:** `org.tatrman:ttr-optimizer` — its own published artifact per the component pattern (§6 architecture); consumed by the compiler between the placement-check and movement-synthesis stages. CLI surface: `ttrp build --optimize` (or `[ttrp] optimize = on|off|pins-only`), `ttrp explain` renders Z's decisions; `ttrp-conform` already runs placement variants — it doubles, unchanged, as Z's correctness harness (any Z plan must conform against the unoptimized plan).

---

## Version roadmap sketch (recorded 2026-07-07, answering GI-14's ordering question)

Versions are trigger-driven, not date-driven:

| Version | Content | Trigger |
|---|---|---|
| **Z 1.0** | Makespan objective (single profile) · two-phase machinery: boundary rewrites → exact solve (choice variables per Z-f-β, γ fixpoint safety pass) · min-cut fast path (2-engine, cost-sum tier) + HEFT seed · node-granularity placement, GI-2 pin/hint/directive vocabulary · stats via metadata server, declared fallback · static world · `PlacementSolver` interface with objective-as-data | v2 of TTR-P |
| **Z 2.0** | **Multi-objective**: profile vocabulary live (GI-10), $ via dimensioned resource vectors already collected since 1.0 (ZQ3 decide-now), lexicographic + weighted + deadline-constrained profiles | $ pressure: Snowflake-credit-class engines in worlds; the night-batch profile ("by 06:00, cheapest") |
| **Z 3.0** | **δ unified search — as a GENERATOR, not a replacement**: own memo explores rewrite chains / discovers non-pre-enumerated alternatives, *emits them as choice variables* into the same solver, which stays the decision layer. Objective machinery (2.0) survives untouched. | evidence: the coupled-rewrite list outgrows pre-enumeration (the Z-f tripwire) |
| **Long-term** | Elastic worlds (GI-6): capacity as decision variable, stochastic/risk objectives; possibly a sibling planner calling the same `PlacementSolver` over candidate worlds | K8s/pod-spawning worlds become real |

**Why multi-objective before δ (the ordering recommendation):** (1) *Cost asymmetry* — 2.0 is mostly plumbing on the same solver (CP-SAT handles weighted/lexicographic natively; the decide-now items were bought in 1.0); δ is a framework build. (2) *Business pull* — $ has a forcing function (credits, elastic worlds); rewrite-chain discovery has only an internal quality trigger, which may never fire at <100 nodes. (3) *The technical kicker* — **Cascades-style memo pruning fundamentally assumes scalar, totally-ordered costs**; multi-objective inside a memo means Pareto-set dominance per group — genuinely hard research-grade machinery. Doing δ first and multi-objective second means rebuilding δ's pruning; doing multi-objective first on the solver and then adding δ-as-generator keeps both cheap. The generator framing is what dissolves the "δ replaces the solver" assumption entirely.

## ZQ9 detail · Container-role surface syntax — sub-catalogue (opened 2026-07-07 · **RESOLVED same day, GI-16**)

> **Resolution summary:** (a) `target`/`prefer` keyword pair · (b) `together` cohesion keyword · (c) α — target-less container in program text is the grouping mechanism, no `.ttrl` lassos (`.ttrl` = view state like *collapsed*; skins = how containers draw) · (d) containers only · (e) no weights in text. Sub-sections kept below as the option record.

GI-2's taxonomy needs spellings. Analysis first: the taxonomy looks like one axis (none/grouping/hint/directive) but is really **two**:

- **Target strength:** none · hint ("I'd rather PG") · directive ("MUST be PG").
- **Cohesion:** may Z move *part* of the contents elsewhere (split the island), or is the container atomic?

The matrix, with a finding: **the directive column collapses** — if every node MUST run on PG, the contents form one PG island by definition; "directive + splittable" is meaningless (splitting a same-engine island is just materialization, which Z decides independently, inside or outside containers). So cohesion only matters for *none* and *hint* strengths — and it surfaces a **fourth real cell**: *cohesion without a target* — "keep these together as ONE island, I don't care which engine" (transaction-domain / no-intermediate-materialization intent):

| | no target | `hint` target | `directive` target |
|---|---|---|---|
| **splittable** | pure grouping (Z free) | "prefer PG, feel free" | — (collapses ↓) |
| **atomic** | "keep together, anywhere" | "keep together, preferably PG" | "keep together in PG" (= v1 semantics) |

### ZQ9-a · Strength spelling

```ttrp
# α — keyword pair (target = directive, v1 files meaning-stable):
container crunch target erp_pg  { … }      # directive: MUST run here
container crunch prefer erp_pg  { … }      # hint: Z may deviate, pays penalty
container crunch                { … }      # no placement meaning

# β — punctuation strength:
container crunch target! erp_pg { … }      # directive
container crunch target  erp_pg { … }      # hint (bare form DEMOTED — breaks v1 meaning!)

# γ — attribute form:
container crunch target erp_pg strength hint { … }
```

- **α** *buys:* reads as English; bare `target` keeps its v1 meaning exactly (P2-clean migration — v1 all-directive files change behavior zero); matches the C3-e precedent (keywords over symbols: `after`/`with` won over `-FS->`). *Costs:* one more keyword.
- **β** *buys:* one keyword. *Costs:* silently reinterprets every v1 file's `target` from directive to hint the day Z ships — a P2 violation wearing punctuation.
- **γ** *buys:* extensible (numeric strengths later). *Costs:* verbose for the common case; numeric strengths in program text are probably wrong anyway (ZQ9-e).

*Lean: α, firmly — β is disqualified by the v1-compat rule alone.*

### ZQ9-b · Cohesion spelling

```ttrp
# α — strength implies cohesion (no new syntax):
#     directive ⇒ atomic; hint/none ⇒ splittable. Fourth cell unreachable.

# β — explicit modifier, orthogonal:
container prep together             { … }   # atomic, any engine (the 4th cell)
container prep together prefer pg   { … }   # atomic, preferably PG
container prep prefer pg            { … }   # splittable, preferably PG
```

- **α** *buys:* zero syntax. *Costs:* loses the fourth cell, which has real use cases (co-location without engine opinion).
- **β** *buys:* full matrix; `together` reads naturally beside `prefer`/`target`. *Costs:* one more modifier keyword; the C1 canvas needs a visual for it.

*Lean: β — the fourth cell is worth one word. (Keyword candidate `together`; alternatives `atomic`, `intact` — naming → convergence.)*

### ZQ9-c · Grouping-only containers — program text or view state?

A container with *no* placement meaning is arguably pure presentation — and TTR-P already has a home for presentation: the `.ttrl` sidecar (C3-h). But program-text containers are also **closed functions** (C3-d-iii: ports, scope, reusable shape) — that's structure, not presentation.

- **α · Program text:** target-less `container` = grouping. *Buys:* one construct; GI-2's wording ("containers serve several roles"). *Costs:* a closed function forces port declarations on something meant as a lasso — heavy for "I just want a box around these six nodes".
- **β · View state:** visual groups live in `.ttrl` (a canvas lasso, C1 vocabulary); program-text containers *always* have placement semantics (target or prefer mandatory). *Buys:* clean split (semantics in program, presentation in sidecar — the C3-h philosophy); no port ceremony for boxes. *Costs:* GI-2 said containers; a `.ttrl` group is invisible in the text surface.
- **γ · Both:** target-less containers legal (structural grouping, ports and all) *and* `.ttrl` lassos exist (pure visual). *Buys:* each need served by the right tool. *Costs:* two grouping mechanisms to explain.

*Lean was γ; **RESOLVED α (GI-16):* no separate lassos — containerization is structural, never `.ttrl`. `.ttrl`'s role stays what C1 gave it (collapsed state — where a pure grouping container is exactly what gets collapsed); skins own container rendering. Port ceremony on grouping containers = accepted cost; no persisted pure-visual boxes.*

### ZQ9-d · Node-level pins (no container at all)

```ttrp
# α — containers only (mini-container is the escape hatch):
container just_this target erp_pg { in x, out y   x -> pivot(…) -> y }

# β — inline node annotation:
sales -> filter(amount > 0) -> pivot(…) on erp_pg -> sort(total)   # `on <engine>`?  `@erp_pg`?
```

- **α** *buys:* one placement mechanism; the graph stays clean. *Costs:* ceremony for one-node pins.
- **β** *buys:* lightweight. *Costs:* chain syntax gets noisy; `on` collides with join's `on:`; every reader must scan expressions for placement marks; C1 must render per-node badges.

*Lean: α for Z 1.0 — revisit only on evidence of mini-container fatigue.*

### ZQ9-e · Hint weights

Does `prefer erp_pg` carry a number (`prefer erp_pg 0.8`)? *Lean: no — numbers in program text are false precision (what does 0.8 mean to an analyst?); deviation penalties are profile/world content (GI-10 — world-specific weights), uniform per strength level. The program says* what *the author wants; the profile says* how much it costs to disobey. *Ties to ZQ8.*

### Consequences to carry (all sub-answers)

- The **no-containers-at-all** program: Z places freely, draws derived islands; `explain` + C1 render them as derived containers (dashed, read-only — C1's derived-canvas precedent applies verbatim).
- The B-model amendment (GI-2 flag) becomes concrete: Container gains `targetStrength: none|hint|directive` + `cohesion: bool`; v1 files parse as `directive + atomic`.
- C1 canvas: distinct affordances for directive (solid border?), hint (tinted?), grouping (thin/dashed?), derived (dashed+lock) — skin roster leftover.

## Open questions (rolling)

- ~~**ZQ1 · Graph scale.**~~ **ANSWERED (GI-3):** ceiling **< 100 nodes**; beyond it, an explicit "un-optimizable" diagnostic is acceptable. Exact solving covers the whole range; Z-c-ε = seed/warm-start only.
- ~~**ZQ2 · Compile-time budget.**~~ **ANSWERED (GI-4):** budgeted, and in the Designer it's a **user setting** (fast/balanced/thorough, "model + thinking level" style). Anytime solver + reported optimality gap; knob home (`[ttrp]` default + Designer override) → consolidation.
- **ZQ3 · Objective timing → decide-now list.** *Timing answered (GI-12):* multi-objective = **Z 2.0**, not soon. What its inevitability forces **now**: (1) **dimensioned resource vectors** in manifest cost shapes + per-resource prices in world instances (the Z-b γ-direction note — the one genuinely irreversible-ish choice); (2) profile vocabulary shaped as type/instance (GI-10) so Z 1.0's single "makespan" profile is just the first instance; (3) `PlacementSolver` objective passed as data, not hard-coded. Nothing else.
- ~~**ZQ4 · Index advice scope.**~~ **ANSWERED (GI-5):** materialize-with-index = one macro choice var; index-after-materialization is exactly the data-engineering trick engine optimizers don't do — in scope for Z. Index *selection* (which columns — its own NP-hard discipline) stays parked.
- ~~**ZQ5 · Re-optimization cadence.**~~ **ANSWERED (GI-17):** per build in Z 1.0; **plan caching deferred** (future design would key on graph + world + stats fingerprints).
- **ZQ6 · Executor concurrency model.** Makespan (Z-b-β) needs "can engine E run two islands concurrently, at what penalty?" — executor/engine manifest content; F-a's waves currently assume yes for distinct engines. Needs a manifest vocabulary. **Partially framed by GI-6:** v2 = static world (given engines/instances); the elastic case is registered long-term, not this design.
- ~~**ZQ7 · Stats home & staleness.**~~ **RESOLVED for Z's purposes (GI-11 + GI-13 + GI-18):** stats = world metadata; two backings (served fresh / repo-read declared); **acquisition = server design, out of Z scope — Z needs only the stats-query CONTRACT** (what can be asked, what returns, snapshot fingerprint semantics). Contract sketch = "before converging" work item; acquisition mechanism + staleness policy → the metadata-server design effort.
- ~~**ZQ10 · Optimizer online/offline split & degradation ladder.**~~ **FULLY ANSWERED (GI-15 + GI-19):** boundary confirmed (correctness-compile offline; optimize pass may consume served metadata; bundle records stats snapshot fingerprint). Ladder: **no metadata source at optimize-pass start ⇒ hard error**; **source lost mid-session ⇒ proceed on cached metadata + stale warning**. Serverless repo metadata counts as a source (declared stats).
- ~~**ZQ8 · Hints vocabulary.**~~ **ANSWERED (GI-16-e + GI-20):** hint vocabulary = **`prefer <engine>` alone** (container-level); deviation penalties live in profiles/world, uniform per strength level, never in program text. Avoid-transfer / materialize-here intents = existing explicit syntax (`store`, `via`, `together`).
- ~~**ZQ9 · Surface syntax for container roles (from GI-2).**~~ **ANSWERED in full (GI-16)** — see the ZQ9-detail sub-catalogue: `target`/`prefer` pair · `together` · grouping = target-less container in program text (no `.ttrl` lassos) · containers-only pins · no weights in text. Structural finding stands: strength × cohesion, directive column collapses.

## Long-term register (not this design, keep visible)

- **Elastic worlds (GI-6):** K8s pods, spawn-a-worker-for-the-night-batch; scheduling optimization where capacity is a *decision variable* and $ is native. Possibly a separate optimization task; requirement on Z now: the `PlacementSolver` interface treats the world as an *input* (set of available engines/instances), so an elastic planner can call the same machinery with candidate worlds.

## Convergence readiness

Diverging; two grounding batches recorded (GI-1…GI-8, GI-9…GI-12, 2026-07-07). **Settled by GI:** Z-a scope (β — distribute + shape islands, GI-1/2), machinery meta-approach (own code, steal ideas — α/δ out, GI-8), scale ceiling + explicit un-optimizable diagnostic (GI-3), budget-as-user-setting (GI-4), materialize-with-index in / index-selection parked (GI-5), static world v2 + elastic long-term register (GI-6), swappable `PlacementSolver` (GI-7), **Z-b = β makespan for Z 1.0, γ = evolution of the same solver** (GI-9 + γ-direction note), **objective profiles, world-instanced weights** (GI-10), **Z-d = stats-in-world, optimizer never calls instances** (GI-11), **multi-objective = Z 2.0 with a three-item decide-now list** (GI-12/ZQ3). **Also settled:** Z-f = β now, δ later as generator (GI-14); version roadmap sketched (1.0 makespan → 2.0 multi-objective → 3.0 δ-as-generator → long-term elastic), ordering rationale recorded. **Also settled: ZQ9 in full (GI-16)** — container-role syntax (`target`/`prefer`/`together`, grouping in program text, containers-only pins, no weights); **ZQ5 (GI-17)** per-build, caching deferred; **ZQ7 (GI-18)** two metadata backings, Z needs the stats contract only; **ZQ10 (GI-19)** hard-error at start / stale-warning mid-session. **ZQ8 (GI-20):** `prefer` alone. **THE QUESTION BOARD IS EMPTY** — all ZQ1–ZQ10 answered or resolved-for-Z; every fork has an agreed lean or a GI resolution. Status advances to **🟡 options captured + grounded**; what separates this from 🟢 converged is executing the work items below and writing the decision-log entries into the control room. **Before-converging work items: EXECUTED 2026-07-07 → [`14-optimizer-worksheets.md`](./14-optimizer-worksheets.md)** — W1 cost-shape vocabulary (dimensioned vectors + calibration/prices split) · W2 stats-query contract (one snapshot per pass; absence-is-a-value; ladder diagnostics; Z-d-β rejected by the type system) · W3 hero hand-run (**passed** — min-cut finds the human plan, cuts inside the accounts chain; choice-var version finds eager-aggregation, 20.1 s → 5.3 s) · W4 B-T9 amendment draft (**one flagged sub-decision:** behavior of `prefer`/target-less containers when Z is off) · W5 world-stats schema (lean: sibling `def stats` doc = the serialization of W2's snapshot; inline floor permitted). Remaining to 🟢: review the worksheets, settle the W4 flag, write the decision-log entries. **Before converging:** sketch the manifest cost-shape vocabulary — *as dimensioned resource vectors* (ZQ3 item 1) — against a real world doc; hand-run the hero scenario through the candidate (min-cut on the 2-engine world) and check it produces the placement a human would choose; draft the GI-2 container-role amendment against B-T9's wording; sketch the world-schema stats extension (GI-11) against a real storage def.
