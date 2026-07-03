# PL Design — Design-Space Map

> The catalogue of **branches to explore** before we design anything. One section per workstream.
> This is deliberately *divergent*: it lists alternatives, not choices. We add options here; we remove nothing without recording why.
> Control surface: [`00-control-room.md`](./00-control-room.md).

How to read each workstream: **Question** (what it must answer) → **Branches** (the alternatives, with trade-offs and prior art) → **Cross-links** (which other forks it constrains) → **Open**.

---

## A. Vision, scope & personas

**Question.** Who is each surface language *for*, what is in v1 vs later, and what single end-to-end scenario do we carry through every workstream?

**Branches to explore:**
- **A1. Persona model.** Candidate users: (a) data engineer building pipelines, (b) analyst doing ad-hoc transforms, (c) business user (NL), (d) AI agent emitting PL, (e) the *Designer* user (graphical). Are all five surfaces first-class in v1, or do we anchor on 1–2 and treat the rest as exploratory?
- **A2. Relationship to TTR modeling.** Is PL (i) a new `model` code in TTR, (ii) a separate but co-resident language family that references TTR models, or (iii) a layer that consumes the *semantics* output of TTR but has its own everything? (Load-bearing fork #1.)
- **A3. v1 boundary.** Brief fixes: no own engine, no optimizer. What else is explicitly out? (Streaming? Incremental/CDC? Writes/materialization beyond intermediate? UDFs?)
- **A4. Success criteria.** What proves v1? E.g. "one program, authored in ≥2 surfaces, compiles to one graph, runs on ≥2 Kantheon workers producing identical results."
- **A5. The hero scenario.** Pick one concrete transformation (the Kyx/Tatrman examples all use a sales+accounts join→summarize→branch-on-error). Reuse it as the canonical case across all docs.

**Resolved 2026-06-30:** A2 → separate TTR-family language referencing models (b). A3 → no writes beyond materialize+Charon. A5 → cross-engine sales/accounts hero scenario. A1 → v1 personas = data engineer + data analyst (data scientist later).

**Resolved 2026-06-30 (cont.):** A1-bis → all 5 surfaces ship in v1 (brief wins; → principle P1 "small core, rich edges"). A3-bis → streaming/CDC, incremental/refresh, UDFs all out of v1. A4 → success criteria signed off (hero scenario, ≥2 surfaces → one graph → ≥2 engines → identical results).

**Workstream A = converged.** Remaining deferred thread: **Q1** — is the AI agent a *producer* of a surface or only a consumer of the compiled graph? (Not a v1 persona; revisit if/when agent authoring matters.)

**Cross-links:** A2 → B, C, G. A1 → C (coverage tiers).

---

## B. Internal model (the execution graph)

**Question.** What are the nodes and edges of the one graph every surface compiles to — and how does it relate to Calcite RelNode and Kantheon's `PlanNode`?

**Branches to explore:**
- **B1. Node taxonomy.** Start = Calcite RelNodes (brief). Plus the brief's special nodes: **data-transfer** (Charon moves) and **materialization** (intermediate/final). Question: is the v1 node set *exactly* RelNode, RelNode + {transfer, materialize, IO}, or a superset from day one?
- **B2. Edge taxonomy — the big one.** Brief wants three connection classes coexisting:
  - *data-flow* (input→output, à la Knime/Alteryx),
  - *control-flow* (start-to-start = parallel-ok, start-to-end, finish-to-start = serial, etc. — project-management-style dependencies),
  - *error-flow* ("if-this-fails-then-this", can be control or data).
  Tatrman already did this with JGraphT edge types (`OP_TABLE_OP`, `OP_CONTROL_OP`, error/lineage edges). Branches: one graph with typed edges (Tatrman) vs **layered** — a RelNode dataflow core + a separate control/error orchestration graph wrapping it (closer to Kantheon's clean PlanNode). (Load-bearing fork #2.)
- **B3. Relationship to Kantheon PlanNode.** (i) PL graph *is* `PlanNode`; (ii) PL graph is a **superset** that lowers to one-or-many `PlanNode`s; (iii) PL graph is a sibling, Proteus translates. RelNode can't express control/error flow or multi-step — so some wrapper is unavoidable. (Load-bearing fork #3; Q2.)
- **B4. Multi-statement / scripting unit.** Is "a PL program" one graph, or a sequence/graph-of-graphs? Where do variables, intermediate names, and reuse live? (RAE uses variable binding; Kyx uses `+` chaining; TransDSL/DFDSL are single-query.)
- **B5. Beyond-Rel nodes & engine affinity.** Brief's endgame: nodes carry *affinity/capability* for engines; "this workflow doesn't run on SQL, needs another engine." How is capability attached to a node — at the node type level, or per-instance? (Tatrman models this in its cost JSON.)
- **B6. Expression sublanguage.** Filters/formulas need an expression language. Reuse Kantheon `plan.v1.Expression` (function-call tree: `call{function, operands}`, `field_ref`, `literal`)? Or TTR's calc-reference style? Or per-surface expression syntax lowering to one IR?
- **B7. Serialization & identity.** Protobuf (Kantheon style) vs a text canonical form (modeler style) vs both. Ties to G and to fork #5.

**Cross-links:** everything. B is the convergence point.
**Open:** Do control/error edges live *in* the same graph the optimizer sees, or only in the orchestration layer above it?

---

## C. Surface languages (×5)

**Question.** Are the five languages isomorphic to one graph (full round-trip), or tiered subsets — and what is each one's persona, philosophy, and coverage?

**Cross-cutting first (before per-language deep-dives): C0. The relationship session.**
- Isomorphic-all vs tiered: maybe graphical + flow-DSL are full-coverage (express any graph), while NL and SQL-like are *ergonomic subsets* that cover the common 80%. Round-trip expectations differ accordingly (fork #4).
- Shared expression sublanguage across surfaces (see B6) vs per-surface.
- Authoring overlap: can one program mix surfaces (NL for the easy parts, flow-DSL for the rest)?

**Per-language branches:**
- **C1. Graphical (Knime/Alteryx).** Brief wants "different skins." Branches: text-canonical with graph as a view (modeler invariant, fork #5) vs graph-primary with text projection. Layout persistence (TTR already solved this for models via `.ttrg` `layout` blocks). What's a "skin"?
- **C2. NL-like (evolve Byx, rename).** Byx today: `Load file "...". Select cols a,b. Summarize sum(amt) group by branch.` Branches: strict controlled-NL grammar (parseable) vs LLM-assisted loose NL → canonical surface. How much ambiguity tolerated? The brief calls Byx the one PoC "maybe" worth keeping.
- **C3. Flow-DSL (evolve Kyx; cf. Kantheon DFDSL & Tatrman DSL).** Dot/`+`-linked operations. Kyx: `Input{} + Filter{} + Join(a,b){}` with `.True/.False` anchors. Branches: host-language-embedded (Kotlin/Groovy internal DSL, like Kyx/RAE/Tatrman) vs standalone external grammar (own parser, like TTR). Anchors/branching model for control & error flow.
- **C4. SQL-like declarative.** Branches: real SQL dialect subset (lean on Proteus's existing SQL→RelNode) vs a TTR-flavored declarative query (cf. Kantheon **TransDSL**: `core/columns/calculations/filter/aggregation`). How does declarative SQL express control/error flow at all? (Maybe it can't — tiered, fork #4.)
- **C5. Pandas-like (much-better RAE; cf. Kantheon DFDSL).** Method/op chain: `from → select → filter → assign → groupby → orderby → limit → join`. RAE's functional binding vs DFDSL's ordered op-chain. Branches: fluent method chain vs functional-assignment vs Polars-expression style.

**Cross-links:** every surface → B (must map to the graph) and → D (must reference model objects). C0 → fork #4.
**Open:** Which surface do we prototype first to pressure-test B? (Q3) — flow-DSL and graphical are the most expressive candidates.

---

## D. Data objects / models

**Question.** How do PL programs name and operate on the data objects from TTR models (tables, entities, cubes), and which operations apply to which model type?

**Branches to explore:**
- **D1. Object sourcing.** Reuse TTR model objects across all model types: relational (`db`), E-R (`er`), multidimensional (`md`), later conceptual (`cnc`). Mirrors the Kantheon translator that "understands SQL using E-R models." How does a PL `from`/`Input` reference resolve — by TTR qname (`db.dbo.table.Orders`), by entity, by cube?
- **D2. Operation availability matrix.** Not every op applies to every model (brief). The *essential relational* set (project/filter/join/aggregate/sort) works everywhere; some ops are model-specific. Define the matrix: op × model-type → available?
- **D3. MD syntactic sugar** (own session, parked detail). Multidimensional gets heavy shorthand for building expressions (cf. TTR's `calc` maps + MD catalog: `monthOfDate`, `fiscalYearOfDate(...)`, additive measures, grain). What does sugar look like in each surface?
- **D4. Schema/type flow.** Arrow schema + Kantheon's cross-engine fingerprint is the runtime contract. How does PL's static type/schema reasoning connect to it? Where do schema errors surface (parse vs semantics vs runtime)?
- **D5. Literals & external data.** CSV/file inputs (`Load file`), inline values, named connections (Charon registry). In-model objects vs ad-hoc external data.

**Cross-links:** D → B6 (expressions over typed fields), D → E (Proteus consumes TTR models via Ariadne).
**Open:** Does PL reference TTR *source* or the *resolved semantics* output? (ties to A2)

---

## E. Transpilation & targets

**Question.** How does one graph become runnable scripts on Python+pandas, Python+Polars, SQL dialects, and (later) Kotlin DataFrames — and who decides what runs where?

**Branches to explore:**
- **E1. Lowering path.** (i) Graph → Calcite RelNode → Proteus `UnparseFromRelNode` → dialect SQL (reuse existing). (ii) Graph → direct emitter per target (own pandas/Polars codegen). (iii) Hybrid: RelNode for SQL targets, direct emit for dataframe targets. Tatrman does RelNode→SQL; Kantheon DFDSL→RelNode exists; pandas/Polars *codegen* does not yet exist anywhere.
- **E2. Per-target codegen.** pandas vs Polars vs SQL-dialects vs Kotlin-DF each need an emitter. Shared IR (RelNode) vs per-target lowering. Which targets are v1?
- **E3. Engine affinity / capability.** Which node runs on which engine, accounting for data-transfer cost (Charon). Tatrman's cost-model approach (static JSON, affinity discounts) is prior art — but slow. v1 likely: *manual/explicit* engine assignment; auto-assignment is optimizer (Z).
- **E4. Kantheon integration.** Map PL graph onto Theseus (orchestrate) / Proteus (translate) / Argos (validate+RLS) / Kyklop (dispatch) / Workers / Charon. Does PL emit `PlanNode`s and hand to the existing pipeline, or a new submission surface?
- **E5. Result/materialization handling.** Charon Materialize/Stage between steps; intermediate vs final outputs (brief's materialization nodes).

**Cross-links:** E → B3 (PlanNode relationship), E → F (multi-step needs orchestration), E → Z (affinity → optimizer).
**Open:** Is the v1 transpiler target-direct (emit a `.py`/`.sql` script a human can read) or platform-native (emit `PlanNode`s the workers run)? Possibly both — different use cases.

---

## F. Orchestration / scripting layer

**Question.** A PL program is multi-step; Kantheon executes single queries today. What runs the DAG, passes data between steps, handles control/error flow at runtime?

**Branches to explore:**
- **F1. Where it lives.** (i) New orchestrator service. (ii) Extend Theseus with a `CompilePlan`/`ExecutePipeline` surface. (iii) Adopt/generalize **Pythia's custom DAG executor** (Postgres-checkpointed, coroutine steps) — the existing PoC.
- **F2. Execution semantics.** How control-flow edges (start-to-start/finish-to-start) and error-flow edges become runtime behavior: scheduling, parallelism, retries, compensation. Maps to B2.
- **F3. Data passing.** Charon as the inter-step shuttle (SeaweedFS/Redis/worker-session DataFrames). Which intermediate stays in a worker session vs materializes.
- **F4. State & checkpointing.** Resumability, idempotency, partial-failure. Pythia's model vs Temporal/Airflow-style vs none-in-v1.
- **F5. v1 minimalism.** Could v1 *avoid* a real orchestrator by compiling to a single script/single PlanNode where possible, and only invoke orchestration for genuinely multi-engine graphs?

**Cross-links:** F → B2/B4, F → E4.
**Open:** Is v1 single-engine-per-program (no orchestration needed) and multi-engine deferred? Big scope lever.

---

## G. Tooling & delivery

**Question.** Does PL ride the existing modeler toolchain, and does the "text is canonical" invariant carry over?

**Branches to explore:**
- **G1. Repo/package placement.** New packages in this monorepo (`@modeler/pl-*`) mirroring `grammar→parser→semantics→lsp→designer`, vs separate repo. Brief frames this as *the same project* (editor tooling). Likely reuse.
- **G2. One model, many grammars.** TTR already proves "one model, many surfaces" (db/er/md). PL extends it to "one graph, many surface grammars." Reuse the parser/semantics/LSP architecture; add N grammars.
- **G3. "Text is canonical" for graphical PL?** Modeler invariant: Designer issues structured edits, LSP synthesizes `WorkspaceEdit`, re-parses. Carry over (fork #5) or let the graph be primary for PL?
- **G4. LSP custom methods.** TTR's `modeler/getModelGraph` etc. PL analogues: `pl/getExecutionGraph`, `pl/transpile`, `pl/getTargets`.
- **G5. Versioning & publishing.** PL grammar as a new canonical `.g4` alongside `TTR.g4`? Cross-target conformance harness (Kantheon-consumed artifacts) analogue?

**Cross-links:** G → C1 (graphical surface), G → B7 (serialization).
**Open:** Is PL its own grammar(s), or sugar that desugars into extended TTR?

---

## H. Naming & conventions

**Question.** What do we call the family and its pieces, and where does it all live?

**Branches:** family name for PL; rename **Byx** (NL surface) and **Kyx** (flow surface) into the new scheme; doc layout under `docs/process-language/`; alignment with Kantheon planning conventions (architecture/contracts/plan) once we reach convergence; vocabulary canon (avoid "named query" etc., per Kantheon §9). Low stakes — settle opportunistically, don't block on it.

---

## Z. Optimizer (parked — v2)

**Question (future).** How to optimize the execution graph: classical (filter pushdown, join reorder), materialization (index/materialize before heavy joins), engine assignment (who runs what, counting transfers).

**Branches (future):** Apache Calcite optimizer vs OptaPlanner/constraint-solver vs custom. Prior art: Tatrman's custom brute-force state-space search **works but is slow** (combinatorial, no pruning/A*, static costs, no cardinality). The problem is genuinely *unlike* a SQL optimizer (multi-engine, transfer-aware). Parked by the brief; roadmap only.
