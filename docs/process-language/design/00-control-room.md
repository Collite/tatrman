# PL Design — Control Room

> The single dashboard for the **TTR Processing Language (PL)** design effort.
> Open this first every session. It tells us where we are, what's open, and what's decided.
> Companion docs: [`01-design-space-map.md`](./01-design-space-map.md) (the branches to explore), the brief [`../pl-brief.md`](../pl-brief.md).
>
> **Status:** Framing (Phase 0). Started 2026-06-30.

---

## 0. How we run this

This is a **multi-session, exploration-first design effort.** The explicit rule (Bora's): **diverge before we converge.** We enumerate alternatives broadly and resist committing to a design until the option space has been walked. Claude's job is to *hold the structure*, surface branches we haven't explored, and push back when we narrow too early.

**The cadence — three gears:**

| Gear | What happens | Output |
|---|---|---|
| **Framing** | Set scope, personas, success criteria, the load-bearing forks. | This doc + design-space map populated. |
| **Divergence (deep-dive)** | Pick one workstream. Enumerate *all* reasonable alternatives, with trade-offs, examples from the PoCs/prior art. **No decision.** | A `NN-<workstream>-options.md` doc: the option catalogue. |
| **Convergence** | Only once a workstream's options are well understood: weigh, decide, record. | A decision in the log + (later) an `architecture.md` / `contracts.md`. |

**Deep-dive protocol** (for each Divergence session):
1. State the question the workstream must answer.
2. Enumerate alternatives — aim for ≥3, including the "weird" one.
3. For each: what it buys, what it costs, which prior art/PoC shows it, which other forks it constrains.
4. Capture **open questions** and **cross-links** to other workstreams.
5. End with: are we done diverging here? What would we need to converge later?

**Anti-rush guardrails** (Claude enforces these):
- No internal-model lock-in until ≥2 surface languages have been sketched against candidate models.
- No surface-language syntax bikeshedding until that language's *target persona* and *coverage tier* are named.
- Every "let's just use X" gets a recorded alternative before it becomes a decision.
- "Deferred" is a valid, tracked outcome — see the Parking Lot.

---

## 1. Workstream dashboard

Status legend: ⚪ not started · 🔵 diverging · 🟡 options captured · 🟢 converged/decided · ⏸ parked

| # | Workstream | Status | Core question | Notes |
|---|---|---|---|---|
| **A** | Vision, scope & personas | 🟢 | Who is each surface language *for*, and what does "v1 done" mean? | **Converged 2026-06-30.** See decision log + principles. |
| **B** | Internal model (execution graph) | 🔵 | One graph with data/control/error flow — what are nodes & edges, and how does it relate to Calcite RelNode / Kantheon PlanNode? | **In progress — structural heart done** (T2/T9/T10/T8); **T5 expr options captured** (🟡, leans noted, decide IR with T3). Remaining: T3 Calcite → T4 → T6. See `next-steps-260701.md`. |
| **C** | Surface languages (×5) | ⚪ | Are the 5 languages isomorphic to one graph, or tiered subsets? One deep-dive each, after a cross-cutting "relationship" session. | Graphical · NL-like (Byx) · flow-DSL (Kyx) · SQL-like · pandas-like (RAE). |
| **D** | Data objects / models | ⚪ | How do PL programs reference TTR model objects (table/entity/cube), and which ops apply to which model? | Includes MD syntactic-sugar (own session). |
| **E** | Transpilation & targets | ⚪ | RelNode-via-Proteus vs direct emit; engine affinity; pandas/Polars/SQL/Kotlin-DF. | Reuses Kantheon Proteus/Kyklop/Charon. |
| **F** | Orchestration / scripting layer | ⚪ | The multi-step DAG gap. New service? Extend Theseus? Reuse Pythia's DAG executor? | Kantheon has single-query only today. |
| **G** | Tooling & delivery | ⚪ | Does PL ride the modeler stack (grammar→parser→semantics→lsp→hosts, "text is canonical")? | Reuse vs new repo/packages. |
| **H** | Naming & conventions | ⚪ | Family name; rename Byx; where docs live; versioning. | Low stakes, do opportunistically. |
| **Z** | Optimizer | ⏸ | Calcite vs OptaPlanner vs custom; learn from Tatrman's slow brute-force. | **v2.** Parked by brief. Roadmap only. |

---

### Framing inputs (Bora, 2026-06-30)

These are scope commitments stated up front. Not yet "decisions" (no design converged), but they constrain the deep-dives:

- **FI-1 · Start with workstream A** (Vision & scope) before touching the internal model.
- **FI-2 · Multi-engine from v1.** v1 must demonstrate one program executing across ≥2 engines (e.g. SQL + Polars). **Cascade:** this pulls the **orchestration/scripting layer (F)** and **Charon data-movement** *into* v1 — they leave the parking lot. Single-engine-only is rejected as the v1 anchor.

## 2. What we already have (asset inventory)

The landscape is unusually rich — much of the target already exists as prior art. Mapped in detail in the design-space map; summary here so we don't reinvent:

- **Three historical surface-language PoCs** (in `../examples/`):
  - **RAE** (Groovy) — pandas/functional, variable-binding dataflow: `X = filter(Y, {expr})`, `aggr`, `cut`, `createValue`, `materialize`, `append`.
  - **Kyx** (Kotlin DSL) — Alteryx tool-flow: `Input{} + Filter{} + Join(a,b){}`, anchors `.True/.False`, targets Alteryx `.yxmd`.
  - **Byx** (NL-like) — sentence commands: `Load file "...". Select cols a,b. Summarize sum(amt) group by branch.`
- **TTR modeling language** (this repo) — declarative, multi-schema (`db`/`er`/`md`/`binding`/`cnc`), `def <kind> <id> { props }`. Extension hooks: `def procedure`/`def query` (already carry `language` + `sourceText`), `calc` maps + MD catalog. **Big open fork:** is PL a new model *code* or a separate language family?
- **Tatrman repo** — already implements the **exact execution-graph internal model the brief describes**: JGraphT DAG with dataflow + control-flow + error + lineage edge types, multi-engine *affinity* via static cost JSON, transpile to Calcite RelNode → SQL. Its optimizer is a brute-force state-space search → **slow** (combinatorial, no pruning/A*/cardinality). Closest existing prototype to workstream B.
- **Kantheon platform** — the execution substrate:
  - Unit of work = **Calcite PlanNode** (RelNode tree, protobuf); results stream as **Arrow IPC** with a cross-engine schema fingerprint.
  - Pipeline: **Theseus** (orchestrate) → **Proteus** (lang↔RelNode↔SQL) → **Argos** (validate/RLS) → **Kyklop** (dispatch) → **Workers** (Brontes/MSSQL, Steropes/Polars, Arges/PG).
  - **Charon** moves data (Materialize/Stage/Copy/Evict, Arrow, named connections).
  - **Two existing DSL protos** already in Kantheon: **TransDSL** (declarative query) and **DFDSL** ("Pandish", imperative op-chain) — both lower to RelNode. Candidate ancestors for workstreams C/B.
  - **Known gap:** no multi-step orchestration DAG — single-query only. Pythia has a custom DAG-executor PoC. This is workstream F.

---

## 3. Load-bearing forks (decide consciously, not by drift)

These are the questions whose answers cascade. Listed here so they stay visible; explored in the design-space map.

1. ~~**PL-as-TTR-model-code vs PL-as-separate-language-family.**~~ **RESOLVED 2026-06-30 → separate family that references TTR models (option b).** See decision log.
2. **Unified graph vs layered graph.** One graph carrying data + control + error flow together (Tatrman's approach), or a RelNode dataflow core wrapped by a separate orchestration/control layer (closer to Kantheon's split)?
3. **Reuse Kantheon's PlanNode/TransDSL/DFDSL as the internal model, or design fresh.** Adopt vs adapt vs greenfield.
4. **Surface coverage: isomorphic vs tiered.** Do all 5 languages express the full graph (round-trippable), or do some (NL, SQL) cover only a subset?
5. **"Text is canonical" for the graphical language too?** The modeler invariant says text is the source of truth and the designer issues structured edits. Does PL's graphical surface round-trip through text, or can the graph be primary?
6. **Build the orchestrator new vs extend Theseus vs adopt Pythia's DAG executor.**

---

## 3a. Design principles

Stable values that constrain every workstream. Cite by ID in decisions.

- **P1 · Small core, rich edges. (2026-06-30)** Prefer a *minimal* operation/node set; push expressiveness and ergonomics into the surface languages and the transpilers, not into the core operation count. The internal model and transpilers are what we pressure-test; the language stays simple. *(Origin: Bora, A1-bis.)* Constrains B1 (keep node taxonomy lean, RelNode-aligned), C (surfaces are sugar over the small core), E (transpilers carry the complexity).

## 4. Decision log

> Append-only. Format: `YYYY-MM-DD · [workstream] · Decision · Why · Alternatives rejected`.

- **2026-06-30 · [A2] · PL is a separate language family *within* the Tatrman (TTR) family, distinct from the modeling language, that references TTR models.** · Processing is imperative dataflow with control/error edges — a different beast from TTR's declarative `def {props}` metadata; but it stays in the family and resolves data objects against TTR semantics (mirrors how Proteus↔Ariadne work). · Rejected: (a) processing as a sixth `model` code inside TTR; (c) consuming only resolved-semantics with no family ties.
- **2026-06-30 · [A5] · Hero scenario = the cross-engine sales+accounts case.** Read accounts (SQL DB) + read sales (CSV/file) → join → summarize → branch on filter, with an error path; DB-side work runs in SQL, CSV-side in Polars, Charon moves data between. One program, two engines, one graph. Carried verbatim through every workstream. · Forces FI-2 (multi-engine) into the canonical example.
- **2026-06-30 · [A3] · No writes beyond materialization.** The only "write" primitive is *materialize + call Charon*; no arbitrary sink/DML in v1. · Keeps PL a transform language, not an ETL-sink language.
- **2026-06-30 · [A1] · v1 personas = data engineer + data analyst. Data scientist added later.** · Anchors scope on two users; defers the data-scientist surface affinity.
- **2026-06-30 · [A1-bis] · All 5 surface languages ship in v1 (brief wins over persona-tiering).** · Keeps the internal-model isomorphism test honest — more surfaces = stronger pressure on a surface-agnostic core. Pandas-like and NL-like are forward-investments. · Rejected: tiering surfaces to persona timing (3-surface v1). · **Couples to principle P1 below.**
- **2026-06-30 · [A3-bis] · Also out of v1 scope: streaming/CDC, incremental/refresh, user-defined functions.** · (with A3: no writes beyond materialize+Charon.)
- **2026-06-30 · [A4] · Success criteria for v1: the hero scenario, authored in ≥2 surface languages → compiles to one graph → executes across ≥2 engines on Kantheon workers → produces identical results.** · Bakes FI-2 into "done."

**Workstream A is converged (🟢).** Next: pick the first technical divergence (B internal model vs C0 surface relationship).

### B / internal model — converging (in progress; full options in `02-internal-model-options.md`)

- **2026-06-30 · [B-T1] · Internal model = a NODE SET + REWRITE RULES + per-engine CAPABILITY, not a fixed "elementary vs macro" split.** Transpile step 1 = normalize the graph to an engine's supported node subset by rewriting (T8). "≈ RelNodes" is a revisitable prior. Reinterprets P1: "small core" = whatever subset an engine supports, reached by rewriting. · Rejected: fixed two-tier elementary/macro as the primary frame (it's a special case).
- **2026-06-30 · [B-T2] · Connections use PORTS (term chosen over "anchors").** Semantics live in the port, not the edge. Ports are typed (data-bearing | control-bearing), **named, with a default port** (names matter in text, not in the graphical view). **Multicast yes** (one out-port → many edges); **no implicit union** (a data-in takes one edge; merging is an explicit `Union`). · Aligns with Tatrman ports / Kyx `.True/.False`+default.
- **2026-06-30 · [B-T2] · Routing nodes pinned:** `Filter` (1 out, SQL-style pass-through) · `Branch` (true/false split = Alteryx filter) · `Switch` (multi-out, per-output conditions; non-overlapping+else OR overlapping) · `Join`/`Union` (explicit named multi-in).
- **2026-06-30 · [B-T2] · Control vocabulary = FS (hard, workhorse) + SS (positive co-start) + FF (atomic co-finish). SF dropped. Mutex excluded (→ F).** Parallel-ok = absence of FS, not an edge.
- **2026-06-30 · [B-T2] · Error-flow is node-level, both modes:** signal-only and erroneous-rows. (Erroneous-rows → SQL will be hard; flagged for E/T8.)
- **2026-06-30 · [B-T2] · Port schemas fully static for now** (author-time-checked; reinforces T7).
- **2026-06-30 · [B-T2] · Control edges carry EVENTS — conceptual model only; v1 is static FS/SS/FF, v1 graph is ACYCLIC. Events + loops/iteration are v2+.** Model is designed to accommodate events; machinery deferred.
- **2026-06-30 · [B-T2] · Control constraints are HARD on their effect; the optimizer may rewrite the surrounding graph only while preserving that effect (never drop it).** "Reorderable hint" = parallelism (absence of FS) only. **→ T2 fully closed.**
- **2026-06-30 · [B-T9] · Charon MOVES only** (physical→physical) **and converts formats** (arrow→csv). It does **not** persist and does **not** load into memory. Persistence/"write" is a runtime (engine) op. *(Corrects an earlier wrong assumption that Charon persists.)*
- **2026-06-30 · [B-T9] · Movement/IO ops are DISTINCT nodes:** **Load** (physical→engine memory), **Store** (engine memory→physical; this is the engine "write"), **Transfer** (a Charon call; physical→physical; may convert), **Index**. **Engine-crossing = Store + Transfer + Load.** **Terminology locked: Load/Store** (over Read/Write) — names the memory boundary, frees "write" as the generic engine concept; accepted risk = ETL's "load = write-to-target" collision, held by convention (Load=input, Store=output).
- **2026-06-30 · [B-T9] · Materialize = optimization-only, temporary (temp-table), pass-through; it is a MACRO** that rewrites (T8) into **Store + (Index) + Load joined by FS.** Indexing optional in v1 but the model must be ready for it. · Fork 2 = distinct ops (Materialize not a primitive); Fork 1 = pass-through.
- **2026-06-30 · [B-T9] · Container node** (from Tatrman): groups ops, no processing value; has **ports mapped to internal node ports** (acts as a *function*/encapsulation); **in v1 bears the execution target** (SQL vs Python). **Collapse containers into nodes ⇒ the orchestrator graph** (F); transfers are defined between containers.
- **2026-06-30 · [B-T9 / closes old B2 "unified vs layered"] · No logical/physical split of programs.** One op graph; ops sit on a **physicality spectrum**; before execution, **iteratively rewrite less-physical → more-physical ops until "can this engine process this graph?" holds** (T8 fixpoint). Engine targets live on **containers**, not on the (engine-agnostic) transform ops. · Rejected: a separate logical→physical program lowering (Bora tried it — too many graph layers).
- **2026-06-30 · [B-T10] · PRIMITIVE-vs-MACRO is ENGINE-RELATIVE, not absolute.** One node set; each engine natively supports a subset; a node is "primitive" for engines that run it natively and "macro" for engines that must rewrite it (Branch: primitive in streaming/Alteryx, macro→Filter in SQL). Two rewrite kinds: **authoring sugar** (engine-independent — Select, Calc, HAVING) and **capability lowering** (engine-relative — Branch→Filter, Pivot→CASE). "Supported everywhere" = intersection of engine capabilities, not a privileged tier.
- **2026-07-01 · [B-T5] · NULL semantics = canonical SQL three-valued logic.** `NULL = NULL` → NULL; unknown propagates (SQL-style). Engines that don't natively match (pandas/Polars) get codegen that *enforces* it. · **Forced by A4** — "identical results across ≥2 engines" cannot hold under engine-defined NULL. This is a hard constraint handed to E, not a preference. · Rejected: engine-defined NULL (breaks A4); a strictness knob (no v1 use case).
- **2026-07-01 · [B-T5] · Expression-level capability misses resolve at NODE granularity — never mid-expression.** When a function inside a value node (Project/Calc/Filter/…) has no rewrite into the container engine's supported function set, the **whole node** is the unit of re-placement: it moves to a capable engine (container reassignment + Store/Transfer/Load, per T9) — the same T8-d node-level split, triggered by an expression miss. The expression normalizer only decides *"can this function be rewritten into engine E's set?"*; a "no" escalates to the node-placement fixpoint (T8). Rewrite order: authoring-sugar expansion → function capability-lowering (where a rewrite exists) → whole-node re-placement (none exists). **Policy: split-with-warning by default, configurable as a project/user preference** (auto-split vs refuse). · Closes **T5-b-fork.** · Rejected: hard-error-only (too rigid); mid-expression engine split (nasty seam). Consequence: the expression layer never reasons about cross-engine seams.
- **2026-06-30 · [B-T10] · Transform node set (v1):** **Project** (primitive; sugar **Select**=select+rename, **Calc**=formula), **Filter**, **Branch**/**Switch** (real nodes, engine-relative), **Join**, **Aggregate** (HAVING=sugar→Aggregate+Filter), **Sort**, **Union**, **Values**, **Limit**, **Pivot** (v1, **static/declared-value typing**; native or CASE per dialect). **Window → v2.** Open: Distinct/Intersect/Except, semi/anti joins, Explode.

---

## 5. Parking lot / deferred

| Item | Why parked | Revisit when |
|---|---|---|
| Optimizer (workstream Z) | Brief defers to v2; separate design effort. | After internal model + transpilation are stable. |
| ~~Orchestration layer (F)~~ | **Un-parked by FI-2** — multi-engine v1 forces it in. | Now in v1 scope. |
| Window functions | Deferred by Bora (B-T10). | v2. |
| Own execution engine (Kotlin+Arrow / Rust+Arrow) | Brief: "later, not now." v1 transpiles only. | Post-v1, if engine-affinity forces it. |
| MD syntactic-sugar deep design | Brief: "special session." | During workstream D. |
| Error-flow semantics depth | Needs internal model first. | During/after workstream B. |

---

## 6. Open questions (rolling)

Cross-session scratchpad of unresolved threads. Promote to a workstream deep-dive or the decision log as they mature.

- Q1 (A): Is the AI agent (Pythia/Golem) a *first-class user* of one of the 5 surfaces, or only a consumer of the compiled graph?
- Q2 (B/E): Is the internal model literally Kantheon's `PlanNode`, a superset of it, or a sibling that lowers *to* it?
- Q3 (C): Which single surface language do we prototype first to pressure-test the internal model?
- Q4 (A): What's the v1 "hero scenario" — one end-to-end example we can carry through every workstream as the running case study?

---

## 7. Session index

| Date | Phase | Focus | Output doc |
|---|---|---|---|
| 2026-06-30 | Framing | Kickoff: process, dashboard, design-space map. | This doc + `01-design-space-map.md` |
| 2026-06-30 | Framing | **Workstream A converged** — A2 (separate TTR-family lang), A5 (cross-engine hero scenario), A3/A3-bis (scope-out), A1/A1-bis (personas + all 5 surfaces v1), A4 (success criteria), principle P1. | Decision log |
| 2026-06-30 | Divergence | **Workstream B — structural heart** — ports/edges/control/error (T2), events-v2, movement+materialize+container (T9), transform node set + engine-relative primitive/macro (T10), node-set/rewrite/normalization framing (T1/T8), Load/Store terminology. **Agreed resume order: T5 expressions → T3 Calcite/PlanNode → T4 → T6.** | `02-internal-model-options.md`, `next-steps-260701.md` |
| 2026-07-01 | Divergence | **Workstream B — T5 expression sublanguage** — option catalogue captured (🟡). Grounded in `plan.v1.Expression` (DFDSL reuses it verbatim), the typed MD calc catalog, and PoC string-vs-tree prior art. Threads: T5-a IR (adopt/adapt/fresh — **decide with T3**), T5-b engine-relative functions (unify with T8; missing-fn = hard error?), T5-c catalogue (two behind one interface; absorption → D), T5-d typing/coercion/**NULL canonical, forced by A4**, T5-e structured-canonical + one PL-expr grammar, never pass-through SQL. Provisional leans noted; **no decisions**. Open: T5-b-fork. | `02-internal-model-options.md` §T5 |
