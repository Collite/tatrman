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

*Lean: β for Z's first cut; γ's seat noted (manifest flag reserved). α recorded as rejected-leaning: it is what makes the problem look impossibly big.*

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

*Lean: β as the target semantics, with α as an admitted v2.0 simplification if scheduling proves heavy (a cost-sum optimum is still a good plan, just not the best one). γ = declared-weights layer later; the objective enum belongs in `[ttrp]` or the world doc either way.*

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

*Lean: **γ** as the v2 machinery — with min-cut as the fast exact path for 2-engine cost-sum worlds, HEFT as seed/fallback (ε), Wayang's enumeration ideas studied first (δ), Calcite untouched at emit (α rejected for Z), and **β named as the acknowledged v3 evolution** if rewrite×placement coupling (Z-f) outgrows what choice variables can encode.*

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

## Z-f · Rewrite × placement coupling

Some rewrites only pay off under placements you haven't chosen yet (push the aggregate below the transfer *iff* the transfer happens); some placements only make sense after a rewrite. How coupled is the search?

- **Z-f-α · Strict two-phase.** Rewrite pass, then placement. *Buys:* simplest. *Costs:* misses cross-phase wins (the aggregate-pushdown example is real, not hypothetical — it's the hero scenario's shape).
- **Z-f-β · Coupled rewrites as choice variables.** The known boundary-relevant rewrites (Z-a-β's short list) are encoded *into* the placement model as alternatives — e.g. each potential cut edge carries optional "pre-filtered/pre-aggregated volume" variants with their conditions. The solver picks rewrite and placement together.
  - *Buys:* exact co-optimization for the enumerated couplings; the list is short *because* Z-a-β made it short.
  - *Costs:* model-builder complexity grows with each coupled rewrite; unforeseen couplings still missed.
- **Z-f-γ · Iterate to fixpoint.** Rewrite → place → re-rewrite under the chosen cuts → re-place, until stable. *Buys:* catches second-order effects cheaply. *Costs:* no optimality claim; oscillation needs a tie-break (T8's termination-measure discipline applies).
- **Z-f-δ · Unified search** = Z-c-β's memo, rules and placement in one space. The full answer, at full price.

*Lean: β (+ γ as a cheap safety pass), consciously bounded: if the coupled-rewrite list stops being short, that is the evidence that promotes Z-c-β from "v3 evolution" to "build it now".*

## Z-g · Runtime, solver dependency & component home

- **JVM vs Rust:** stay **Kotlin**. Z is a compile-time search over graphs of tens-to-hundreds of nodes — nothing needs Rust's profile; every relevant solver is JVM-reachable; Wayang is existence proof that this problem class lives happily on the JVM. Rust re-enters only via the parked "own execution engine" item — a different feature with its own parking-lot row. (If Z ever needs a native solver, that's a JNI dependency, not a language migration.)
- **Solver sub-fork (for Z-c-γ):** **α · OR-Tools CP-SAT** (Java bindings; native lib packaged per-platform; the strongest free solver for assignment+makespan) vs **β · Choco** (pure JVM, no native deps, weaker at scheduling scale) vs **γ · own min-cut + branch-and-bound** (zero deps; exact for the 2-engine case via Stone; hand-rolled beyond that). *Lean: define `PlacementSolver` as a Kotlin interface; ship γ's min-cut fast path + HEFT fallback as the dependency-free floor, α behind an optional module — the interface is the decision, the backends are swappable.*
- **Component home:** `org.tatrman:ttr-optimizer` — its own published artifact per the component pattern (§6 architecture); consumed by the compiler between the placement-check and movement-synthesis stages. CLI surface: `ttrp build --optimize` (or `[ttrp] optimize = on|off|pins-only`), `ttrp explain` renders Z's decisions; `ttrp-conform` already runs placement variants — it doubles, unchanged, as Z's correctness harness (any Z plan must conform against the unoptimized plan).

---

## Open questions (rolling)

- **ZQ1 · Graph scale.** What's the realistic ceiling — tens of nodes (exact solve always wins) or thousands (heuristic tier mandatory)? Sets the solver bar and whether Z-c-ε is a corner case or a co-equal path.
- **ZQ2 · Compile-time budget.** Interactive (`ttrp build` in seconds, Designer preview) vs batch (CI, minutes)? CP-SAT is anytime-capable (best-found-so-far + gap) — budget could be a `[ttrp]` knob.
- **ZQ3 · Objective timing.** When does "$" become real (Snowflake credits are the obvious forcing function)? Z-b-γ's declared-weights design should be sketched before the cost-shape vocabulary in manifests freezes.
- **ZQ4 · Index advice scope.** The Index node exists (B-T9). Does Z choose indexes (a whole discipline — index selection is its own NP-hard problem) or only *use* declared ones v2? Lean: materialize-with-index as a single macro choice var; index *selection* parked.
- **ZQ5 · Re-optimization cadence.** Per build (deterministic from inputs — P2-clean) vs cached plan in the bundle keyed by world fingerprint + stats version? Interaction with F-f's manifest.
- **ZQ6 · Executor concurrency model.** Makespan (Z-b-β) needs "can engine E run two islands concurrently, at what penalty?" — executor/engine manifest content; F-a's waves currently assume yes for distinct engines. Needs a manifest vocabulary.
- **ZQ7 · Stats home & staleness.** Where do Z-d-γ's observed stats live (world instance overlay? a sibling stats doc? the model repo?), and what invalidates them (world fingerprint change? age? declared override)?
- **ZQ8 · Hints vocabulary.** Which of the PoC's soft-hint machinery returns (prefer-engine, avoid-transfer, materialize-here), and in which surface (canonical text attributes? `[ttrp]`? world)?

## Convergence readiness

Diverging. The catalogue is populated across all seven forks; the leans form a coherent candidate (β boundary-scope · β makespan-target/α-simplification · γ two-phase CP-SAT + min-cut fast path + HEFT seed · α+γ declared-plus-observed stats · β node-granularity with pins · β coupled-choice-vars · Kotlin + `PlacementSolver` interface), with Z-c-β (own Cascades memo) as the named evolution path. **Before converging:** answer ZQ1/ZQ2 (they gate the solver choice), sketch the manifest cost-shape vocabulary against a real world doc, and hand-run the hero scenario through the candidate (min-cut on the 2-engine world) to sanity-check that the model produces the placement a human would choose.
