# B — Internal Model: Option Catalogue (DIVERGENCE)

> **Mode: divergence.** We enumerate alternatives and trade-offs. **No decisions in this doc** — those go to the control-room decision log only after the option space is walked.
> Control surface: [`00-control-room.md`](./00-control-room.md). Branch context: [`01-design-space-map.md`](./01-design-space-map.md) §B.
> Governing principle: **P1 — small core, rich edges.** Governing scope: **FI-2 — multi-engine v1.**

**The question B must answer.** What are the nodes and edges of the single execution graph that all five surfaces compile to, and how does it relate to Calcite RelNode and Kantheon's `PlanNode`?

**Thread plan** (we'll take these a few per turn, not all at once):

| Thread | Topic | Maps to | Status |
|---|---|---|---|
| **T1** | Node granularity & the core operation set | B1 | 🔵 framing settled; node set TBD |
| **T2** | Edge & **port** model — data / control / error flow | B2 | 🟢 closed for v1 |
| **T3** | Relationship to Kantheon `PlanNode`; serialization / canonical form | B3, B7 | 🔵 options captured; β-vs-γ live |
| **T4** | Multi-statement unit, variables, reuse | B4 | ⚪ |
| **T5** | Expression sublanguage | B6 | 🟡 options captured (leans noted; T5-b-fork open; decide IR with T3) |
| **T6** | Engine affinity / capability attachment | B5 | ⚪ |
| **T7** | Typing / schema determinacy (static vs dynamic columns) | new (↔ D4) | 🔵 lean captured |
| **T8** | Node-equivalence rewriting & capability-driven normalization | new (↔ T6, E, Z) | 🔵 mechanism introduced |
| **T9** | Movement / materialization / **container** nodes (FI-2 / Charon) | B1, brief | 🟢 model settled (Load/Store/Transfer/Index/Materialize-macro/Container) |
| **T10** | Transform node set & primitive-vs-macro classification (fills T1's node set) | B1 | 🔵 open |

---

## T1 — Node granularity & the core operation set

**The question.** At what *granularity* do internal-model nodes live, and what is the minimal set?

**The central tension.** Two granularities pull against each other:
- **Algebra-granular** (few, composable, RelNode-style): `Project`, `Filter`, `Join`, `Aggregate`, … A user "tool" may desugar into several. Transpiler- and optimizer-friendly.
- **Tool-granular** (recognizable user operations): `Filter`, `Summarize`, `Formula`, `Join`, `Sort`, … One box in a Knime/Alteryx canvas = one node. Surface- and Designer-friendly.
The graphical surface *wants* tool-granular nodes (one visible box per operation); the transpiler-via-RelNode *wants* algebra-granular nodes. P1 ("small core") and the brief ("nodes ≈ Calcite RelNodes for v1") both lean algebra-granular. **How we resolve this shapes everything in C and E.**

### Candidate operation sets

- **B-T1-α · Pure Calcite RelNode core.** Scan/TableScan, Project, Filter, Join, Aggregate, Sort, SetOp (Union/Intersect/Minus), Values, Limit/Offset, (Window?). ~8–10 logical nodes = exactly Kantheon's `PlanNode`.
  - *Buys:* free SQL transpile via Proteus; Calcite optimizer-ready; proven; smallest core (P1✓); zero new IR if we adopt `PlanNode`.
  - *Costs:* relational-only — no dataframe-native ops (pivot/reshape/explode), no MD-native ops; control/error flow can't be nodes here (must be edges/wrapper); "Formula"/"Summarize" from the surfaces must desugar.
- **B-T1-β · RelNode + physical/orchestration nodes.** α plus the brief's special nodes: `Input`/`Output` (IO), `DataTransfer` (Charon move), `Materialize`. ~12 nodes.
  - *Buys:* multi-engine made explicit (FI-2✓ — transfers are first-class); matches the brief's "data-transfer & materialization nodes"; still small.
  - *Costs:* mixes logical (Project) and physical (Transfer) in one set — need a layering story; where do transfer/materialize get *inserted* (authored vs optimizer-added)?
- **B-T1-γ · Dataframe-algebra core (Rel superset).** α plus dataframe-isms: `Assign`/WithColumn, `Reshape` (pivot/melt), `Explode`, `Window` first-class.
  - *Buys:* pandas-/Polars-like surfaces map almost 1:1; natural for analyst/scientist; richer expressiveness.
  - *Costs:* bigger core (P1✗); pivot/reshape are awkward-to-impossible in plain SQL → transpile asymmetry; Calcite doesn't cover all → custom optimizer sooner.
- **B-T1-δ · Tool/verb set (the PoC lineage).** Input, Select, Filter, Formula, Join, Summarize, Sort, Sample, Union, Output/Browse, + control `Branch`. What Kyx/Tatrman/RAE/Alteryx actually used.
  - *Buys:* 1:1 with graphical + flow surfaces; user-recognizable; Designer-natural; the PoCs prove it works.
  - *Costs:* "tool" ≠ algebra (Summarize=Aggregate, Formula=Project+expr) → still must lower to RelNode for SQL; granularity drifts as we add tools (anti-P1 pressure).
- **B-T1-ε · Two-level model (verbs over algebra).** Explicitly separate **surface verbs** (rich, tool-granular, what users author) from **core nodes** (small, algebra-granular, what we optimize/transpile). Verbs desugar to core nodes at a defined boundary. Not an operation set — a *structural stance*.
  - *Buys:* directly realizes P1 (rich surfaces, tiny core); resolves the central tension instead of choosing a side; graphical surface groups core nodes into visual tools; transpiler sees clean algebra.
  - *Costs:* two layers to design + keep in sync; "where is the boundary" becomes the new hard question; round-trip (graph→text→graph) must agree on the level; more upfront machinery.

### Emerging direction — Bora (2026-06-30)
**Not "elementary vs macro" but "node set + rewrite rules + per-engine capability."** (Supersedes the earlier elementary/macro framing; that is now a *special case*.) The model is:
1. **A set of nodes** — some relational, some richer; the brief's "≈ RelNodes" is a *revisitable starting prior*, not a commitment (confirmed: free to revisit now and later).
2. **Rewrite rules / equivalences** expressing some nodes in terms of others (analogy: "how to express SQL `HAVING` without `HAVING`"). User-defined **macro nodes** are one kind of rewrite; engine-driven lowering is another. Mechanism is general — see **T8**.
3. **Per-engine capability** — different engines support different node subsets. Transpile step 1 = *normalize the graph into an equivalent graph using only nodes the target engine supports.*

So "small core" (P1) is reinterpreted: not a fixed tiny set, but **whatever subset a given engine supports, reached by rewriting.** The relational starting set is targeted to be supported everywhere; pivot-class ops are the known-hard cases that may have no equivalent on some engines (→ T6/T8). The "what is the elementary set (α vs β)" question becomes "what's in the node set, and which nodes have rewrite rules into which others."

### Resolved in T1 (2026-06-30)
- "nodes ≈ RelNodes" → **revisitable starting prior**, not a commitment.
- Granularity tension → resolved by the **node-set + rewrite-rules** framing (T8); macro nodes are real boxes, lowered by rewriting. The graphical "box" = any node (macro or not).
- "are control/error *nodes or edges*?" → **neither — ports** (T2). `Branch`/`Switch` *are* nodes; flow type is a port property.

### Still open in T1
- **The node set itself** — what nodes exist beyond the relational starting set, and which carry rewrite rules into which others (T8). Partially seeded by the routing-node catalogue below.
- **`DataTransfer` / `Materialize` nodes** — authored vs optimizer-inserted; FI-2 makes placement pressing. → **deferred to next round (Bora), still within B.**

### Cross-links
T1 → T2 (if control/error are nodes vs edges), T1 → T3 (α ⇒ adopt PlanNode directly), T1 → C0/C1 (granularity decides what a graphical "box" is), T1 → E (γ/δ widen the transpile gap), T1 → P1.

---

## T2 — Edge & anchor model

**The question.** How are connections between nodes represented, and where do the three flow classes (data / control / error) live?

### Foundational stance — ports (Bora, 2026-06-30)
**Terminology: PORTS** (Bora leans ports over "anchors", matching the Tatrman repo). Semantics live in the **port**, not the edge.

A node exposes typed **ports** (attachment points). Each port is either **data-bearing** (a dataset with a definite shape/schema flows) or **control-bearing** (a control signal flows). An **edge** merely connects an out-port to an in-port; what flows (which data + shape, or which control) is defined by the port. A node may expose many data ports and many control ports.

- Ports are **named**, and a node has a **default port** (Kyx-style) — so textual surfaces can omit the port name in the common case and name it only when routing to a non-default port. **Port names are irrelevant in the graphical representation but important in the textual surfaces.**
- The "are control/error *nodes or edges*?" question dissolves: flow *type* is a **port property**.
- **Error-flow** is a port role (data- or control-bearing failure port; matches brief's "error can be control or data", Kyx's `.False`).
- **Control-flow dependency semantics** (start-to-start, finish-to-start, …) ride **control ports/edges**.
- Prior-art-aligned: Kyx `.True/.False` + default port, Alteryx/Knime named ports, Tatrman ports.

### Resolved in T2 (2026-06-30)
- **Ports are named + a default port exists.** (T2-a partially: naming = named, not positional.)
- **Multicasting: YES** — one out-port may feed many edges; multiple downstream nodes subscribe to a single output.
- **No implicit Union** — a data-in port does **not** merge multiple incoming edges; combining inputs is always an explicit `Union` node. (⇒ a data-in port takes exactly one edge; multi-input nodes like `Join`/`Union` expose multiple named in-ports.)

### Routing-node catalogue (Bora's worked example, 2026-06-30)
Distinct nodes, deliberately separated to prove the port model carries the semantics:

| Node | Outputs / ports | Semantics |
|---|---|---|
| **Filter** | **one** data-out | True SQL-style filter — only rows passing the predicate flow through. (Not the Alteryx "filter".) |
| **Branch** | two data-out: `true`, `false` | = Alteryx Filter — splits input into passing/failing partitions. |
| **Switch** | many data-out, each with its own condition; optional `else` | Kotlin-style switch. Two modes: **non-overlapping** (each row to exactly one branch, optional ELSE) or **overlapping** (a row flows to *every* branch whose condition it satisfies). |
| **Join** | (≥2 named data-in) | Explicit multi-input; named in-ports (cf. Kyx `Join(a,b)`). |
| **Union** | (≥2 named data-in) → one out | The *only* way to merge inputs (no implicit union). |

### Resolved in T2 (2026-06-30, cont.)
- **T2-a · Port schemas are fully static** (author-time-checked) for now. (Reinforces T7.)
- **T2-b · Control-dependency vocabulary — settled set:** **FS** (finish-to-start, the workhorse, *hard*), **SS** (start-to-start, kept as a *positive* "when starting this, also start that" — distinct from mere parallel-ok, which is just the absence of FS), **FF** (finish-to-finish, for atomic/transactional co-materialization — "publish both or neither"). **SF dropped.** Mutex/concurrency parked for F. FS+condition is **not** a new type (it's FS wired to a success/failure port).
- **T2-c · Error-flow is node-level, with BOTH modes available:** signal-only *and* erroneous-rows (the failure port can carry the bad rows). ⚠️ Generating SQL from the erroneous-rows form will need substantial manipulation (→ E / T8 transpile difficulty).
- **T2-e · The optimizer/normalizer SEES control & error edges but may ignore and rewrite them — they are HINTS, not hard barriers.** ⚠️ Open nuance: FS guarding a *side effect* (e.g. truncate-before-load) reads like a hard correctness constraint, not a hint — so we likely need a **hard vs soft** distinction (or: the optimizer may rewrite only when it can prove the constraint's effect is preserved). Flagged for resolution.

### Emerging concept — control edges carry EVENTS (Bora, 2026-06-30)
The things flowing on control edges are **events**. Consequences Bora drew:
- **Events have a known schema.** So control is not just an untyped signal — an event carries a (known) payload shape.
- **Multiple events can wire to the same edge/port → a node can be started several times** (re-invocation / multiplicity over time).
- **A control (event) output may feed a data input**, *because the event schema is known* — events can be consumed as data.

Reframing this gives a clean unification: nodes emit **lifecycle events** (started / finished / failed, possibly with payload) on control ports; the FS/SS/FF "dependencies" are just **event wirings** — FS = "on A.*finished* → enable B.*start*", SS = "on A.*started* → trigger B.*start*", FF = "B may *finish* only after A.*finished*". The dependency "type" is *which event drives which lifecycle transition*.

**This is orchestration-event flow, NOT data streaming** (A3-bis ruled out streaming/CDC). Events are batch-orchestration signals with payloads, not a CDC/stream.

**Scope resolution (Bora, 2026-06-30): events are v2+.** The conceptual model *accommodates* events (we design so they fit), but **v1 implements only static FS/SS/FF batch ordering.** Consequences:
- **T2-i** → events deferred to **v2+** (we know they're coming).
- **T2-f** → **v1 graph is acyclic** (data DAG); loops / re-invocation are **v2+** (arrive with events).
- **T2-g / T2-h** (symmetry data→control, event lifecycle catalogue) → parked with the v2+ event work.

### Resolved in T2 (2026-06-30, cont.)
- **Mutex / concurrency is NOT in the internal model.** No data use case; scheduling conflicts belong to the **orchestration + scheduling layer (F)**, not control edges.

### Resolved in T2 (2026-06-30, final)
- **T2-e · Control constraints are HARD on their effect.** FS *means* A finishes before B starts — that can't be dropped. The optimizer may **rewrite the surrounding graph freely as long as the constraint's effect is preserved**, not ignore the constraint. "Hint"/reorderable applies only to *parallelism* (absence of FS), not to an explicit FS guarding a side effect. **→ T2 is fully closed for v1.**
- **T2-c (rest)** · retry/compensation semantics on the error path → deferred to **F**.

### Cross-links
T2 → T1/T8 (ports are how nodes expose structure for rewriting), T2 → F (control/error → runtime scheduling), T2 → T7 (port schemas), T2 → C (textual surfaces name ports; graphical draws them).

---

## T7 — Typing / schema determinacy

**The question.** Are column sets / data shapes known statically at authoring time, or can they be runtime-only?

### Emerging direction — Bora's lean (2026-06-30, not yet a decision)
**Static by default.** Every node's output schema (columns + types) is known at authoring time and checkable statically — *except* the final step / output, where runtime-only shape is tolerated. No runtime-only intermediate columns in the initial versions.

Implications & still-open branches:
- *Buys:* author-time validation everywhere; clean anchor schemas (T2-a); simpler transpile (each node has a known schema); aligns with TTR's static model + Kantheon's schema fingerprint contract.
- *Costs / open:* operations whose output schema is *data-dependent* (pivot on distinct values, schema-on-read of arbitrary CSV, `SELECT *`-style passthrough, dynamic column generation) must be either disallowed, pushed to the final/output step, or given a declared-schema escape hatch.
- **T7-a** · What exactly counts as "final step"? Only literal output/materialize nodes, or any leaf?
- **T7-b** · How is an input's schema obtained at author time — from the TTR model (for modeled objects) vs declared-up-front (for ad-hoc CSV)? (↔ D4, D5.)
- **T7-c** · Is `pivot` therefore a "final-step-only" macro in v1, or excluded? (↔ T1 γ, T6.)

### Cross-links
T7 → T2-a (port schemas), T7 → D4/D5 (schema sourcing), T7 → E (static schema eases codegen), T7 → T1 (pivot-class ops).

---

## T8 — Node-equivalence rewriting & capability-driven normalization

**The mechanism (Bora, 2026-06-30).** The internal model is a **node set + a body of rewrite rules** that express some nodes in terms of others (e.g. "express `HAVING` without `HAVING`" = rewrite an aggregate-then-filter macro into Aggregate + Filter). Each engine declares the **node subset it supports**. Transpilation step 1 is **normalization**: rewrite the authored graph into an *equivalent* graph that uses only nodes the target engine supports; then emit. User-defined **macro nodes** are just one source of rewrite rules; engine-lowering is another.

This is the general frame that subsumes "elementary vs macro" and reinterprets P1 ("small core" = whatever subset an engine supports, reached by rewriting).

**Sharpened by T9 (Bora, 2026-06-30).** There is **no logical/physical graph split** — ops sit on a **physicality spectrum**, and normalization is an **iterative fixpoint**: transform less-physical ops into more-physical ones until *"can this engine process this graph now?"* is satisfied. Engine **targets live on Containers** (T9), not on the logical ops, so the transform logic stays engine-agnostic. Materialize and the engine-crossing Store+Transfer+Load pattern are **rewrite outputs**, not authored primitives.

**Sharpened by T10 (Bora, 2026-06-30) — primitive/macro is ENGINE-RELATIVE.** A node is "primitive" for an engine that runs it natively, "macro" for one that must rewrite it (Branch = primitive in streaming, macro in SQL). So there are **two rewrite kinds**: (1) **authoring sugar** (engine-independent, expanded early — Select, Calc, HAVING); (2) **capability lowering** (engine-relative — Branch→Filter for SQL, Pivot→CASE per dialect). The "supported everywhere" set = the *intersection* of engine capabilities, not a privileged tier. This makes T6 (per-engine capability manifest) the input that drives which rewrites fire.

### Branches to explore
- **T8-a · Rule engine.** Hand-written lowering passes vs a declarative rule system vs **Calcite's `RelOptRule`/`HepPlanner`** (already in the stack via Proteus). Reuse Calcite's rewriter for the relational subset?
- **T8-b · Rule direction & confluence.** Rules are generally *lowering* (macro → primitives), but some are *choices* (a node expressible 2 ways, engine picks). Do we need cost to choose? (That edges into the optimizer Z — keep v1 rewriting cost-free / deterministic?)
- **T8-c · Capability declaration.** How does an engine declare its supported node set + supported expression functions? Static manifest per engine (cf. Tatrman's cost JSON, Kantheon worker capability advertisement)?
- **T8-d · "No equivalent" handling.** When a node can't be rewritten into an engine's subset (e.g. `pivot` on a SQL engine with no pivot + dynamic columns blocked by T7), what happens — hard error at author/transpile time, or split the graph across engines (→ FI-2 multi-engine, Charon transfer)? This is where T8 meets the multi-engine placement problem.
- **T8-e · Relationship to the optimizer (Z).** Normalization (correctness: reach a runnable subset) vs optimization (cost: pick a *good* equivalent) are both graph rewriting. Are they one engine or two phases? v1 needs normalization only; Z is deferred — but they may share machinery.

### Cross-links
T8 → T1 (defines what "the node set" must carry), T8 → T6 (capability = which nodes an engine supports), T8 → E (normalization is transpile step 1), T8 → Z (optimization is cost-aware rewriting), T8 → FI-2/F (no-equivalent ⇒ multi-engine split + Charon).

---

## T9 — Materialization & data-transfer nodes

**Corrected model (Bora, 2026-06-30) — my earlier Charon assumptions were wrong.**

**Charon MOVES only.** Charon does *not* persist and does *not* load into memory; it takes physically-existing data at location A and moves it to physical location B (physical→physical), and can **convert formats** en route (e.g. arrow→csv). Persistence / "write" is a **runtime (engine) operation**, not Charon.

### Movement / IO operation set — all DISTINCT nodes (Fork 2 = distinct)
| Node | Direction | Notes |
|---|---|---|
| **Load** | physical → engine memory | bring existing physical data into an engine's working memory (input) |
| **Store** | engine memory → physical | write in-memory data out to a physical location (this is the engine's "write") |
| **Transfer** | physical → physical | **a call to Charon**; may convert formats |
| **Index** | on stored data | build an index (optimization) |
| **Materialize** | pass-through (Fork 1 = β) | **optimization-only; temporary** (e.g. SQL temp table). A **macro** (T8) that rewrites into **Store + (Index) + Load** joined by **FS** control; downstream reads the stored copy. Usually "store and index"; indexing optional in v1 but the model must be *ready* for it. |

**Engine-crossing pattern:** move data engine A → engine B = **Store** (A memory→physical) + **Transfer** (Charon, physical→physical, maybe convert) + **Load** (physical→B memory).

**Resolved:** Fork 1 = **β (pass-through)**. Fork 2 = **distinct ops** (Materialize is a macro over Store/Index/Load, not a primitive).

### Container node (Bora, from Tatrman)
- A **Container** groups operations into a unit. It **has ports**, which **map onto the ports of its internal nodes**.
- Containers carry **no processing value** — pure grouping/encapsulation. Via port-mapping they act as **functions** (hide internal structure).
- **In v1 a Container bears the execution target** (this container runs in SQL, that one in Python).
- **Tatrman's optimizer creates containers to separate runtime targets; transfers are defined between containers.** Copy this. **Collapse the containers into nodes ⇒ the orchestrator graph** (who runs where + transfers between). The orchestration layer (**F**) *emerges* from container-collapse — it is **not** a separate program.

### Fork 3 & 4 — RESOLVED: no logical/physical split of programs
Bora tried a logical→physical *program* split → too many graph layers → abandoned. Instead:
- **One graph of operations.** Ops are not inherently logical or physical — they sit on a **physicality spectrum.** "Load this CSV" is physical w.r.t. the file yet logical, because the downstream (and thus the Load) could be pandas *or* PostgreSQL — a different physical op each way.
- **Before execution, iteratively transform less-physical ops into more-physical ops** (this *is* T8) **until the target engine can process the graph.** No named graph levels. The only questions: *"Can this engine process this graph now?"* and if not *"How do we transform it to be more processable?"* (a normalization fixpoint).
- **Placement lives on containers, not on the logical ops.** The container bears the target; the ops inside stay engine-agnostic. This dissolves Fork-3's "who names engines" — no engine names in the transform logic.

### Open questions in T9
- Do Load/Store appear in the authored graph, or are they (like Transfer) inserted by lowering at container boundaries? (Likely: source Load / final Store authored; engine-crossing Store+Transfer+Load inserted.)
- Container ports: how does port-mapping handle a container spanning multiple internal in/out ports? (Look at Tatrman's implementation.)
- Terminology: **Load/Store LOCKED** (over Read/Write) — names the memory boundary, frees "write". Accepted risk: ETL's "load"=write-to-target collision.

### Cross-links
T9 → T8 (the iterative lowering loop; Materialize + engine-crossing are rewrites), T9 → F (collapse containers ⇒ orchestrator graph), T9 → T6 (engine capability drives "can this engine process it?"), T9 → FI-2 (Store+Transfer+Load is the multi-engine seam), T9 → Charon (Transfer + Convert only), T9 → C (surfaces author engine-agnostic ops; containers carry targets).

---

## T10 — Transform node set & primitive-vs-macro classification

**The question.** What are the actual data-transform nodes (the relational/dataframe operations), and which are **primitives** vs **macros** (rewrite targets for T8)? This finally fills in "the node set" that T8 rewrites over and T6 capability-checks. Governing: **P1** (small primitive core) + the node-set/rewrite frame (T8).

**Working method:** propose a *small primitive core* (targeted to be supported by every engine) + a *macro layer* (user-friendly ops that rewrite into primitives). The routing nodes (T2) and movement/container nodes (T9) are already fixed; this thread is the *value-transform* nodes.

### The node set is uniform; **primitive-vs-macro is ENGINE-RELATIVE** (Bora, 2026-06-30)
There is **no absolute** primitive/macro split. There is **one node set**; each target engine natively supports a **subset**. A node is a "primitive" *for an engine that executes it natively* and a "macro" *for an engine that must rewrite it into supported nodes.* Canonical example: **Branch** is a *macro for SQL* (rewrite to Filter) but a *primitive for a streaming engine* (Alteryx-like) that executes it directly. So "primitive/macro" is a property of a **(node, engine)** pair, decided by capability (T6) + rewrite rules (T8) — not an intrinsic node attribute.

**Two distinct KINDS of rewrite:**
1. **Authoring sugar** — engine-*independent*, expanded early. `Select` (select+rename), `Calc` (=formula), `HAVING` (→ Aggregate+Filter), etc. The user may write the sugar *or* the canonical node; identical result.
2. **Capability lowering** — engine-*relative*, applied only when the target lacks native support. `Branch`/`Switch` → Filter for SQL; `Pivot` → CASE for dialects without native PIVOT.

### The node set (value transforms) — with native-support notes
| Node | Notes / native support |
|---|---|
| **Project** | **one primitive** (choose/rename/compute/drop, carrying expressions). Sugar: **Select** (select+rename) and **Calc** (=formula) — author may use Project or the sugar. |
| **Filter** | row predicate, 1 out (T2). The lowering target for Branch/Switch. |
| **Branch** / **Switch** | **real nodes.** Native in a streaming engine (Alteryx-like); **rewritten to Filter for SQL** (and likely pandas). Engine-relative. |
| **Join** | multi-in named ports; join-types (inner/left/right/full/semi/anti/cross). |
| **Aggregate** | group-by + aggregates. `HAVING` = authoring sugar → Aggregate+Filter. |
| **Sort** | order-by keys. |
| **Union** | explicit multi-in (T2); UNION ALL vs distinct. |
| **Values** | inline/literal relation. |
| **Limit** | row limit/offset. |
| **Pivot** / Unpivot | **in v1, with STATIC typing** (declared pivot values ⇒ static schema, satisfies T7). Native in some SQL dialects; **rewritten to CASE expressions** for dialects without PIVOT. Engine-relative. |
| **Distinct**, **Intersect**, **Except** | set ops; sugar vs per-engine primitive — borderline, revisit. |
| **Window** | **parked to v2.** |

### The "supported everywhere" set
Still useful, but it's the **intersection** of all engines' native capabilities (≈ the relational core: Project / Filter / Join / Aggregate / Sort / Union / Values / Limit), **not** a privileged tier. Everything else is bridged by capability-lowering.

### Resolved in T10 (2026-06-30)
- **Project** = one primitive; sugar = **Select** (select+rename) + **Calc** (formula).
- **Branch/Switch** = real nodes; **engine-relative** — native in streaming (Alteryx), rewritten to Filter for SQL (pandas TBD). Established "primitive/macro is per (node, engine)".
- **Pivot** = in v1, **static typing** (declared values); native or CASE-rewrite per dialect.
- **Window** = **parked to v2.**

### Still open in T10
- **Distinct / Intersect / Except** — authoring sugar vs per-engine primitive.
- **Join semi/anti** — native vs rewrite per engine.
- **Explode / Unnest** — in v1 scope at all?
- **pandas & Branch** — does pandas rewrite Branch to Filter, or execute via boolean masks (near-native)?

### Cross-links
T10 → T8 (macros are rewrite rules; this defines them), T10 → T6 (which primitives each engine supports), T10 → T7 (pivot/dynamic-schema), T10 → C (surface verbs map to macros/primitives), T10 → E (per-engine emit of each primitive).

---

## T5 — Expression sublanguage (DIVERGENCE, 2026-07-01)

**The question.** What is the language *inside* the nodes — the predicates in `Filter`, the computed columns in `Project`/`Calc`, the conditions in `Join`/`Branch`/`Switch`, the calls in `Aggregate`? What is its IR, its function catalogue, its type/NULL rules, and how does it surface in the five languages? Foundational: workstream C cannot proceed without it.

### The organizing insight — T5 is workstream B, one level down (fractal)
Every load-bearing question T5 raises is a question we already answered (or framed) for **nodes**, recapitulated at expression granularity:

| Node-level thread | Expression-level echo |
|---|---|
| T3 — adopt/adapt/fresh `PlanNode` IR | **T5-a** — adopt/adapt/fresh `plan.v1.Expression` IR |
| T10 — primitive-vs-macro is **engine-relative**; two rewrite kinds | **T5-b** — function support is engine-relative; same two rewrite kinds |
| T6 — per-engine capability manifest (which nodes) | **T5-b/f** — capability manifest also lists supported **functions** |
| T7 — static schema/typing | **T5-d** — static expression typing; coercion; NULL |
| T8-d — "no equivalent" ⇒ hard error or multi-engine split | **T5-b** — missing *function*: hard error, or split? (weaker?) |
| G — "text is canonical" | **T5-e** — structured tree is canonical; strings are surface sugar |

So the cheap move is to **make expressions inherit the node machinery** wherever the echo holds, and only diverge where the analogy breaks. The threads below flag exactly where it breaks.

### Prior-art grounding (what already exists)
- **Kantheon `plan.v1.Expression`** — `oneof { Literal | ColumnRef | FunctionCall{operation:string, operands:[Expression]} | ParameterRef | CastExpression | SubqueryExpression }`, plus a `result_type` string tag (text|int|float|bool|datetime; may carry a physical type post-lowering). `FunctionCall.operation` is a **bare string over a standardised operator enum**. Lowers to Calcite `RexNode`. **DFDSL reuses this Expression verbatim** (`AssignExpression.expression`, `filter.condition`, `join.on`) — Kantheon already runs *one shared structured Expression IR* across its DSLs.
- **MD calc catalog** (`@modeler/md-catalog`) — not a bare function list but 11 **typed, semantic** entries (`truncToMonth`, `monthOfDate`, `quarterOfMonth`, …): each has typed params with defaults (`weekStart ∈ {mon,sun}`), an **input domain type** and **output domain type** (incl. refinements like `int{lo:1,hi:12}`), a **cardinality** (`N:1`), and prose semantics. Cross-repo-owned, versioned via `MD_CATALOG_VERSION`, vendored to ai-platform. This is a *modeling-layer* catalog, not a general expression library.
- **The PoCs split on the string-vs-tree axis** — Kyx: opaque strings (`Expression = "amount > 0"`, col refs `[customer]`); Byx: NL-inline (`where amount > 0`, `formula amount * 1.21 as Amt_With_VAT`); **RAE offered both in one call**: `filter(x, {Fteeq.eq(10).or(Fteeq.lt(5))})` (tree) *and* `filter(x, "Fteeq = 10 OR Fteeq < 5")` (string), plus value exprs `createValue(d,"Sum",{CP_Code + 5})`. TransDSL/DFDSL: structured trees only.

---

### T5-a · The expression IR — adopt / adapt / fresh
- **T5-a-α · Adopt `plan.v1.Expression` verbatim.** PL's expression IR *is* the Kantheon proto.
  - *Buys:* zero new IR; direct Calcite `RexNode` lowering through Proteus; DFDSL already proves *DSL → this → RelNode* works; `result_type` tag satisfies T7 static typing for free; the E-boundary is a no-op.
  - *Costs:* SQL/Calcite-shaped — `SubqueryExpression` and `ParameterRef` (a `RexDynamicParam` bridge) are relational baggage a pandas/NL surface never needs; `operation` is stringly-typed; **drags a hard dependency on Kantheon's `plan` proto package into PL's core** (couples PL versioning to Kantheon's wire format).
- **T5-a-β · Adapt — a PL-owned structural twin.** Same tree shape (call / field-ref / literal / cast) but PL owns it: shed the SQL-only arms at first (`SubqueryExpression`, `ParameterRef`), keep room for non-relational operand kinds, with a **defined lowering to `plan.v1.Expression`** at the E boundary.
  - *Buys:* realizes **P1** (core stays clean; the lowering carries the complexity); PL evolves its function/operand set without a cross-repo proto bump; still trivially lowerable because the shape matches.
  - *Costs:* two Expression types kept in sync; the lowering is real (and must survive round-trip for G).
- **T5-a-γ · Design fresh.** Only if PL expressions diverge hard from a RelNode-expressible shape — no evidence yet they do. Held as the "weird" option; likely rejected.

**Coupling flag:** T5-a should be decided **together with T3** (node IR). Adopting `PlanNode` but adapting `Expression` (or vice-versa) is incoherent — pick the same stance at both levels. The β "own-a-twin-that-lowers" option is the expression-level mirror of the "sibling that lowers to PlanNode" node option.

### T5-b · Expressions are engine-relative too — reuse the T8/T10 machinery?
A function may be absent on an engine (`truncToMonth` native-ish on Polars `.dt`, `DATE_TRUNC` on Postgres, `DATEFROMPARTS`-gymnastics on MSSQL; `regexp_*` present on some engines, absent on others). This is **T10's `(node, engine)` primitive/macro split, one level down: `(function, engine)`.**
- **T5-b-α · Unify with T8.** Expression functions carry the same **capability (T6) + rewrite-rule (T8)** machinery. A function is "primitive for engine X, macro for engine Y (rewrite to a supported expansion)". Both rewrite kinds carry down: **authoring sugar** engine-independent (`x BETWEEN a AND b` → `x>=a AND x<=b`; `COALESCE` → `CASE`), **capability lowering** engine-relative (`truncToMonth(d)` → per-dialect expansion).
  - *Buys:* one mental model top-to-bottom; the expression normalizer *is* the node normalizer's fixpoint; the T6 manifest covers node-set **and** function-set with one schema.
  - *Costs:* expression rewriting nested inside node rewriting raises an **ordering/confluence** question (T8-b) that now spans two levels.
- **T5-b-β · Separate mechanism.** Expression lowering lives entirely in per-engine codegen (E) — dialect-specific expression printers — not as graph-level rewrite.
  - *Buys:* keeps the graph normalizer node-only and simpler; matches how real transpilers emit expressions.
  - *Costs:* loses the "one manifest drives everything" story; a **missing function can't trigger a multi-engine split** the way a missing node can.
- **The real fork (T5-b-fork):** does a *missing expression function* get the **T8-d escape** — split the graph across engines + Charon transfer — or is expression-level capability **strictly weaker** (must resolve within the already-chosen engine/container, else hard author-time error)? Intuition: expression capability is weaker than node capability, because splitting mid-expression is far nastier than splitting between nodes. Leaning: **hard error at expression level; splitting is a node-level privilege.** But this is a genuine open decision.

### T5-c · Function catalogue — one, two, or MD-as-surface-sugar
Tension: TTR's **MD calc catalog** (typed, semantic, cross-repo-owned, versioned, vendored) vs the **general expression library** PL needs (arithmetic, comparison, logical, string, conditional/`CASE`, aggregates).
- **T5-c-α · One unified catalogue.** PL's function catalogue is a superset that *includes* the MD calc functions. `monthOfDate` is just a function.
  - *Buys:* single typed registry; MD sugar (D) becomes ordinary expression functions.
  - *Costs:* entangles PL's function versioning with the MD cross-repo sync/vendoring contract; MD entries carry semantic metadata (cardinality, domain refinement types) general functions may lack — one schema must cover both.
- **T5-c-β · Two catalogues, one interface.** MD calc catalog stays its own owned/versioned artifact; PL defines a general function catalogue; **both implement a common `CatalogEntry`-style signature/typing interface** so the type-checker and rewriter treat them uniformly.
  - *Buys:* respects the existing MD ownership/version boundary; PL functions evolve independently; MD functions are *referenced*, not absorbed.
  - *Costs:* two registries to resolve against; a name-collision/precedence policy needed.
- **T5-c-γ · MD functions are surface sugar only (workstream D).** PL *core* has general functions; MD calcs desugar (at the D/model-binding layer) into core expressions + the calc-map.
  - *Buys:* keeps the PL expression core minimal (P1); MD stays a modeling concern.
  - *Costs:* `truncToMonth`'s core expansion is itself engine-relative → back to T5-b.
- **Entanglement flag:** T5-c overlaps workstream **D** (MD-sugar has its own session). Resolve *here* only the **interface** (a typed catalog-entry contract the checker/rewriter consume); defer the merge/ownership policy to D. Lean: **β interface, D decides absorption.**

### T5-d · Typing, coercion, NULL
T7 says static. Consequences per sub-axis:
- **Typing.** Every expression node has a statically-known result type (plan.v1 already tags `result_type`; MD entries are fully domain-typed). Type-checker walks the tree bottom-up. Richness fork: **(i)** coarse DSL surface types only (`text|int|float|bool|datetime`, as plan.v1 uses) vs **(ii)** MD-style refinement types (`int{1..12}`, domain-typed) for stronger checks (`quarterOfMonth` demands `int{1..12}`). *Lean:* start coarse (i), design so refinements (ii) can layer later — same "model must be ready for it" discipline as indexing/events.
- **Coercion.** plan.v1 has an explicit `CastExpression`. Fork: implicit widening (int→float) vs Cast-everywhere. **Engine-relative wrinkle:** implicit-cast *rules differ per engine* (SQL ≠ Polars ≠ pandas) — so allowing implicit coercion makes the rules engine-relative (T5-b again). *Lean:* **explicit `Cast` in the IR** (authoring sugar may insert it), so the core is engine-agnostic and unambiguous; each engine's codegen knows only its own cast syntax.
- **NULL — the sharpest cross-engine gap.** SQL three-valued logic vs pandas `NaN`/`None` vs Polars null. Options: **(α)** pin **SQL three-valued semantics as PL-canonical**; dataframe-engine codegen inserts null-handling to match. **(β)** engine-defined NULL — **rejected: breaks A4 "identical results."** **(γ)** canonical + a declared strictness knob. **Strong lean α, and worth flagging loudly: A4 (identical results across ≥2 engines) *requires* a canonical NULL semantics — it cannot be engine-defined.** This is a hard correctness constraint on E, not a preference.

### T5-e · Surface representation — opaque string vs structured tree
Prior art: Kyx/Byx opaque strings; TransDSL/DFDSL structured; RAE *both*.
- **T5-e-α · Structured everywhere.** The IR always holds the parsed tree; surfaces differ only in concrete syntax, all parsing to one `Expression` tree.
  - *Buys:* type-checking (T7), rewriting (T8), round-trip + "text is canonical" (G), and structural editing in the graphical surface all work uniformly.
  - *Costs:* every surface needs an expression parser (even NL/graphical).
- **T5-e-β · Opaque strings at rest, parsed lazily in codegen.**
  - *Buys:* surfaces stay trivial.
  - *Costs:* **no author-time typing (breaks T7/T2-a), no rewriting, no round-trip** — effectively incompatible with the static decisions already made. Likely rejected.
- **T5-e-γ · RAE's dual.** Structured is canonical, but a surface *may* accept an opaque string that the parser immediately **lifts into the tree** (paste-in convenience; a graphical node's "raw expression" field).
  - *Buys:* string ergonomics + tree guarantees.
  - *Costs:* the string must be **one canonical PL-expression grammar**, not per-engine SQL.
- **Strong lean α/γ**, with a pin worth stating now: **there is exactly one PL expression grammar, surface-independent; an "opaque string" is unparsed *PL-expression* text, never pass-through target-dialect SQL.** This kills the tempting-but-wrong "let users inline raw SQL" escape — raw dialect SQL can't be type-checked, rewritten, or lowered to another engine, so it violates T5-b/d and A4.

### Resolved in T5 (2026-07-01)
- **NULL = canonical SQL three-valued** (`NULL = NULL` → NULL). Forced by A4; hard constraint on E. → decision log.
- **T5-b-fork CLOSED — expression capability misses resolve at NODE granularity, never mid-expression.** No-rewrite function ⇒ the whole node re-places to a capable engine (T8-d node-level split), policy = split-with-warning, configurable (auto vs refuse). Rewrite order: sugar → function-lowering → node re-placement. → decision log. Consequence: the expression normalizer only answers "can function F be rewritten into engine E's set?"; it never handles cross-engine seams.

### Provisional leans (still open — for convergence with T3)
1. IR: **β (own a structural twin that lowers to `plan.v1.Expression`)** — *iff* T3 picks the sibling stance for nodes; else **α**. **Decide with T3.**
2. Catalogue: **two catalogues behind one typed interface (β)**; absorption policy deferred to D.
3. Typing: coarse surface types now, refinement-ready; **explicit Cast**.
4. Surface: **structured is canonical (α), string is a lift-to-tree convenience (γ); one PL-expression grammar; never pass-through SQL.**

### Still open in T5
- **Aggregate expressions** — plan.v1 models these as a *separate* `AggregateCall{function, args, distinct, alias}`, not as `FunctionCall`. Does PL keep aggregate calls a distinct expression arm (matches Calcite's scalar/agg split) or unify? (↔ T10 Aggregate node.)
- **Window/analytic expressions** — parked with Window nodes (T10 → v2).
- **Subquery-valued expressions** (`x IN (SELECT …)`, scalar subquery, `EXISTS`) — in v1 expression scope, or node-level only? plan.v1 supports them (`SubqueryExpression`); do the surfaces?
- **Parameters / bind values** — plan.v1 has `ParameterRef`. Does PL v1 expose parameterized programs, or is that workstream F (orchestration) / v2?
- **Confluence/ordering (T8-b at two levels)** — when expression rewrites and node rewrites interleave, what fires first?

### Cross-links
T5 → T3 (**decide the IR stance jointly**), T5 → T8/T10 (function lowering reuses the two-rewrite-kinds machinery), T5 → T6 (manifest lists supported functions, not just nodes), T5 → T7 (static expression typing + NULL), T5 → D (MD-catalog absorption), T5 → E (per-dialect expression emit; canonical NULL is a hard E constraint), T5 → C (each surface's expression concrete-syntax + the single canonical grammar), T5 → A4 (identical-results forces canonical NULL/coercion).

---

## T3 — Relationship to Kantheon `PlanNode`; serialization / canonical form (DIVERGENCE, 2026-07-01)

**The question.** Is PL's internal model literally Kantheon's `PlanNode`, a superset of it, or a sibling that *lowers to* it? Do we reuse Calcite's `RelOptRule`/`HepPlanner` to implement T8's rewriting for the relational subset? And what is PL's canonical serialized form, given G's "text is canonical"?

### Grounding — what `plan.v1.PlanNode` actually is (read from the proto, not the brief's gloss)
- **~11 node arms, relational-only:** `scan` / `table_scan` / `project` / `filter` / `join` / `aggregate` / `sort` / `limit_offset` / `values` / `subquery` / `workspace_ref`. "The canonical wire form of the v1 RelOp subset… RelNode is the lingua franca of the v1 query pipeline." Lowers to/from Calcite `RelNode` via **Proteus**.
- **It has NONE of PL's non-relational apparatus:** no control-flow, no error-flow, no `Branch`/`Switch`, no `Pivot`, no movement nodes (`Load`/`Store`/`Transfer`/`Index`), no `Container`, no `Materialize`. It is exactly the **relational island**.
- **It carries its own mini logical→physical step internally:** `ScanNode` (pre-physical; "a tree containing any ScanNode is, by contract, pre-physical") → `MAP_TO_PHYSICAL` → `TableScanNode`. Note this is *scan-level* lowering — not the *program-level* logical/physical split PL rejected in T9.
- **`WorkspaceRef`** — a session-scoped DataFrame reference (stateful Polars worker); how Kantheon threads multi-step state through its single-query model. (↔ T4/F.)
- **`SchemaCode`:** DB, ER, CNC, WS, OBJ active; **MD, DF reserved.** PL's dataframe ops would live in the reserved `DF` territory.
- **Shares PL's leaf types already:** `Expression`, `ColumnRef`, `Literal`, `QualifiedName`, `AggregateCall` — the T5 IR.

### The central finding — **PL ⊋ PlanNode** (strict superset), proven by prior decisions
PL's node set (T2/T9/T10) = PlanNode's relational nodes **plus** control/error ports (T2), `Branch`/`Switch`/`Pivot` (T10), movement `Load`/`Store`/`Transfer`/`Index` + `Container` + `Materialize` (T9). The hero scenario A5 *requires* the extras (control, error, cross-engine transfer). So **PL cannot *be* PlanNode.** `PlanNode` is naturally the shape of PL's **relational island after normalization** — i.e. a **lowering target** for SQL-family engines, not PL's IR.

### The stances
- **T3-α · Adopt `PlanNode` as PL's IR.** *Rejected on arrival* — relational-only; can't carry control/error/movement/container. Only viable if PL were relational-only (contradicts A5/T2/T9/T10).
- **T3-β · Superset `PlanNode`.** PL's model = PlanNode's relational arms (byte-identical) + PL's extra arms, sharing the T5 leaf types.
  - *Buys:* the relational subset is wire-compatible with PlanNode ⇒ free Calcite/SQL emit via Proteus for that subset; reuses `Expression` (settles T5-a = adopt); one proto family.
  - *Costs:* PL owns a bigger proto that *embeds* Kantheon's `plan` package ⇒ **version coupling** to Kantheon's wire format for the shared arms; blurs "PL core is engine-agnostic" (T9) by baking a specific relational proto into the core.
- **T3-γ · Sibling that lowers TO `PlanNode`.** PL has its own node IR (own text + optional proto); the **relational island lowers to `PlanNode`** at the E boundary for SQL-family engines (via Proteus → Calcite → SQL). Non-relational engines (Polars/pandas) get their own emit and may bypass PlanNode entirely.
  - *Buys:* **P1** — PL owns a clean engine-agnostic core; PlanNode becomes *one E-target among several*, matching T9 ("targets on containers, ops engine-agnostic"); clean story for dataframe engines that don't want RelNode; no core-level version coupling.
  - *Costs:* a real lowering `PL-rel-node → PlanNode` to build + maintain; we don't get Calcite "for free" *as the IR* (but can still call it as a target).
- **T3-δ · Normalize-to-islands (γ made explicit).** The authored PL graph (rich superset) runs the T8 normalization fixpoint; the output per container is, for a SQL-family container, a **relational island whose canonical form *is* `PlanNode`** (→ Proteus → SQL); for a Polars container, a dataframe plan. δ = γ + "the T8 fixpoint is the bridge, and a SQL island's canonical form is literally PlanNode."

### Sub-fork T3-a · Calcite reuse (`RelOptRule` / `HepPlanner`) — two-tier rewriting?
T8 rewriting splits naturally by what Calcite knows:
- **PL's own rewriter** handles the non-relational lowerings Calcite can't see: `Branch`/`Switch` → `Filter`, control/error handling, movement insertion (`Store`+`Transfer`+`Load`), `Materialize` macro, `Pivot` → `CASE`.
- **Calcite (`HepPlanner`/`RelOptRule`, already in the stack via Proteus)** handles the *relational-island* rewriting + SQL emit once PL has normalized down to a PlanNode-shaped island.
- ⇒ **two-tier:** PL rewriter lowers to a relational island, Calcite optimizes/emits it. This is exactly today's Proteus (lang↔RelNode↔SQL). *Lean: reuse it for the SQL-family islands; don't reimplement relational rewriting.*

### Sub-fork T3-b · WHERE does rewriting run? (the sleeper — likely the most consequential)
The modeler tooling stack is **TypeScript** (grammar→parser→semantics→lsp, "text is canonical"). Calcite/Proteus are **JVM**, and live in **Kantheon's execution path**, not the editor. So:
- **Author-time (TS, modeler):** parse, type-check (T5-d), author-time sugar expansion + capability *checking* (can this run? warn on splits) — needs the capability manifest (T6) but **not** Calcite.
- **Execution-time (JVM, Kantheon/Proteus):** the actual capability-lowering + relational optimization + SQL emit.
- **Fork:** is normalization (T8) an **author-time** concern (reimplemented in TS, so the editor can show the lowered/placed graph and warnings live), an **execution-time** concern (editor stays declarative; Kantheon lowers), or **split** (author-time checking + preview; execution-time authoritative lowering)? This decides how much of T8 we build twice. *Lean: author-time does checking + placement preview; execution-time (Proteus/Calcite) is authoritative for relational emit.* (↔ E, G.)

### Sub-fork T3-c · Serialization / canonical form vs G "text is canonical"
`PlanNode` is **protobuf**. G's modeler invariant says **text is canonical** for PL (a `.ttrp`-ish source; protobuf is derived). These don't conflict once ordered: **PL text (canonical) → parse → PL graph → normalize → per container → `PlanNode` protobuf (SQL island) *or* dataframe plan → engine.** PlanNode is an **interchange/wire form at the E boundary**, never PL's source of truth — exactly how modeler already treats "text canonical, protobuf derived." *Lean: text canonical; PlanNode is a derived E-boundary artifact.*

### Provisional leans (NOT decisions)
1. **T3-γ/δ — sibling that normalizes to PlanNode islands.** PL owns an engine-agnostic superset core; PlanNode is the SQL-family lowering target, one E-target among several. (Rejects α; prefers γ/δ over β to avoid core-level version coupling and honor T9.)
2. This **retroactively settles T5-a = adapt/β** — PL owns its `Expression` twin, lowering to `plan.v1.Expression` for SQL islands.
3. Calcite reuse: **two-tier** — PL rewriter for non-relational lowering, Calcite/Proteus for the relational island + SQL emit.
4. Rewriting locus: **author-time checking + preview; execution-time authoritative.**
5. Canonical form: **text (G); PlanNode is a derived E-boundary interchange form.**

### Still open in T3
- **β vs γ is the live one.** β (embed PlanNode arms) buys wire-compatibility but couples PL's core to Kantheon's `plan` proto version; γ decouples at the cost of a lowering. Which matters more — zero-friction reuse, or an independent core (P1/T9)?
- **`WorkspaceRef` / stateful-session model** — does PL's multi-step (T4) reuse Kantheon's workspace-ref mechanism, or its own? (↔ T4, F.)
- **`Scan` vs `TableScan` two-step** — does PL's `Load` normalization mirror PlanNode's pre-physical→physical scan step, or is `Load` already the physical leaf? (↔ T9.)
- **`DF` schema code** — activate the reserved dataframe schema for PL's Polars/pandas islands, or model dataframe plans outside PlanNode entirely (⇒ γ)?
- **Proto ownership** — if γ, does PL's optional proto live in modeler, in Kantheon's `shared/proto`, or a new shared package? (↔ G tooling.)

### Cross-links
T3 → T5-a (**IR stance decided jointly**), T3 → T8 (two-tier rewriting; who owns which rules), T3 → T6 (capability manifest feeds author-time checking), T3 → E (PlanNode = SQL-family emit target via Proteus; dataframe islands separate), T3 → G (text canonical; proto derived; where the PL proto lives), T3 → T4/F (`WorkspaceRef` multi-step), T3 → T9 (islands = normalized containers; Scan/TableScan vs Load), T3 → P1 (γ keeps the core small + engine-agnostic).
