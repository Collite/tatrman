# Tasks · P2 · Stage 2.1 — Graph construction

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

The one internal graph (B-T4: one document = one program = one acyclic graph), built from the Phase-1 resolved AST in module `packages/kotlin/ttrp-graph`. Plan stage line: *"node set (T10 roster), typed ports + err/rejects, SSA variable desugar (Q7-γ), containers with port mapping, control edges FS/SS (FF = capability error), acyclicity + single-in checks, Display semantics (Q11)."* At the end of this stage the hero program and its er-variant build into a `TtrpGraph` whose node/edge inventory is pinned by a component test, and every structural violation produces a named `TTRP-CTL-*` diagnostic with a suggested alternative. No manifests, no rewrites yet — those are Stages 2.2/2.3. (Phase-2 DONE bar, for orientation: `ttrp explain` on the hero shows the exact island/wave/movement structure F-lite promised.)

## Pre-flight (all must pass before T2.1.1)

- [ ] **T2.1.0 done** (review-001 1.3-A carry-over) — the join-based er-hero + join-condition synthesis land FIRST (see the task below). T2.1.1's `hero-er.ttrp` and T2.1.7's Join-node provenance assertion both depend on it.
- [ ] `./gradlew :packages:kotlin:ttrp-frontend:test` — green (Phase-1 DONE bar: `ttrp check` passes the hero + er-variant).
- [ ] `./gradlew :packages:kotlin:ttrp-graph:build` — green (Stage-0.1 scaffold module exists, wired into `settings.gradle.kts` and the version catalog, `libs.bundles.kotest` on `testImplementation`).
- [ ] `find packages/kotlin/ttrp-frontend/src/test/resources -name '*.ttrp' | head -20` — locates the Phase-1 hero fixture(s). Note the exact path; T2.1.1 reuses (does not fork) the hero text.
- [ ] `grep -rn "TTRP-" packages/kotlin/ttrp-frontend/src/main | head -30` — locate the Phase-1 diagnostic framework (code class + registry). Record the class FQNs; this stage registers new `TTRP-CTL-*` ids in the same registry. If the registry already uses any of the NNN values proposed below for something else, keep the AREA and take the next free NNN — update this file's ids in place (that is a renumber, not a blocker). `TTRP-CTL-001` is contracts-pinned (contracts.md §3) and MUST NOT be renumbered.

## Tasks

### T2.1.0 · Carry-over from review-001 (1.3-A): restore the join-based er-hero + implement join-condition synthesis — DO THIS FIRST

> **Why here:** Phase-1 review-001 accepted a *reduced* `hero_er` (single-entity `sales_txn` arm) and left the `on: relation` → join-condition **Expression synthesis as a placeholder string** (`TtrpChecker.resolveRelation` emits `dbSpelling = "join-condition($name)"`), because the shared erp-project fixture deliberately under-binds the er tier and **nothing in Phase 1 consumes the join condition**. Stage 2.1 is the first consumer (the `Join` node's `on` condition + T2.1.7's Join-node provenance assertion), so the synthesis lands here, built against the real Join node rather than ahead of it. This is a **cross-repo** task: the ttr-metadata testFixture must be bound first (its `Relation` model already carries `joinPairs: List<AttributeJoinPair>` — no metadata *model* change is needed, only fixture data + a spec fix). Decision recorded 2026-07-06 (review-001 §"one decision", Option B).

- [ ] **Upstream (ttr-metadata testFixtures)** — bind the er tier the design-doc er-hero needs, in `packages/kotlin/ttr-metadata/src/testFixtures/resources/fixtures/erp-project/models/erp/er.ttrm` (+ its `schema binding` / er2db defs), following ttr-metadata's own fixture conventions (contracts §8 — this fixture has its single home there; propose the change upstream, do not fork it into ttrp-frontend):
  - `customer` entity **and its join attribute** (e.g. `customer.id`) er2db-bound to their db columns.
  - `customer_sales` relation given **`joinPairs`** (e.g. `customer.id ↔ sales_txn.customer`), with both endpoint attributes er2db-bound so `MetadataQuery.erToDb` resolves each `AttributeJoinPair` side to a db column.
  - the remaining `sales_txn` attributes used by the hero (`region`, `branch`, `customer`) er2db-bound.
  - **Preserve the `RES-005` seed:** `customer.customerType` is currently ttr-metadata's own unbound-attribute seed (`ErBindingChainSpec`). Binding `customer` must **relocate that seed to a fresh unbound attribute** so ttr-metadata's `RES-005`/`ErBindingChainSpec` stays meaningful — update that spec accordingly.
  - **Gate:** `./gradlew :packages:kotlin:ttr-metadata:test` green after the fixture + spec change (the shared fixture feeds several suites — no regressions).
- [ ] **ttrp-frontend — implement the synthesis** replacing the placeholder in `TtrpChecker.resolveRelation` (`packages/kotlin/ttrp-frontend/.../resolve/TtrpChecker.kt`, the `DEFERRED (review-001 1.3-A)` block): for the matched relation, walk `relation.joinPairs`; resolve each pair's `fromAttr`/`toAttr` to its db column via `erToDb`; build a **port-qualified equality tree** — `op.eq(left.<fromCol>, right.<toCol>)` per pair, AND-ed together — reusing the Stage-1.2 `Expression` IR (`op.and`/`op.eq` catalogue ids). `left`/`right` port assignment follows the join's `left:`/`right:` args mapped to the relation's `fromEntity`/`toEntity`. Attach **mandatory provenance** (E-d) so the synthesized condition renders er-first (`customer.id`/`sales_txn.customer`, not the bare db columns).
  - Placement is the coder's call, constrained by T2.1.7: E-d says the er rewrite is *early*, so the natural home is the frontend `ErRewriter` producing the `Expression` into the resolved report, with the GraphBuilder reading it onto the `Join` node — but building it at graph-construction time is also acceptable **provided** T2.1.7's Join-node provenance assertion holds and `ttrp check` still passes.
  - A binding miss on any joinPair endpoint stays `TTRP-RES-005` (an unbound key column is exactly the E-d "bind it or reference the db object" case).
- [ ] **ttrp-frontend — restore the full er-hero fixture** `programs/hero_er.ttrp`: replace the reduced single-`sales_txn`-arm variant with the design-doc `customer ⋈ sales_txn on relation customer_sales` join (`06-model-binding-options.md` §"The er-flavored hero variant"). `ttrp check` on it → exit 0, zero ERRORs. Update the fixture header comment (the "adapted to the bound `sales_txn` arm" note is no longer true).
- [ ] **ttrp-frontend — goldens + the currently-uncovered positive path:** add the join-condition **golden** (the synthesized `Expression` tree, snapshot per the Stage-1.2 dump mechanism); add a **positive `TTRP-RES-004` happy-path test** — review-001 noted the *success* branch of `resolveRelation` (endpoints match → rewrite recorded) is currently exercised by no test (the `res-004-*` fixtures only hit the mismatch branch). Assert the rewrite + provenance are recorded for a correct 2-entity join.
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test :packages:kotlin:ttrp-frontend:test :packages:kotlin:ttrp-cli:test` all green; `ttrp check` on the restored `hero_er.ttrp` → exit 0; the join-condition golden matches; the positive RES-004 test is green. Then remove the review-001 §Blockers linkage below and the `DEFERRED` comment in `TtrpChecker.kt`.

### T2.1.1 · Test corpus + spec skeletons (TEST-FIRST)

- [ ] Create fixture tree `packages/kotlin/ttrp-graph/src/test/resources/fixtures/graph/`. Copy the hero verbatim from the Phase-1 fixture (canonical rendering = `05-canonical-dsl-options.md` §"The converged hero rendering": `uses world` pin, `container acc_prep target erp_pg """sql…"""`, `container crunch(in accounts, out result, out low, err rejects) target polars {…}` with load/filter/join/aggregate-block/branch, program-level wiring `acc_prep -> crunch.accounts`, `crunch.result -> display(main_result)`, `crunch.low -> store(files.low_regions)`, `crunch.rejects -> store(files.join_errors)`) as `hero.ttrp`, plus the er-variant (`06-model-binding-options.md` §"er-flavored hero variant") as `hero-er.ttrp`.
- [ ] Create the negative fixtures, one file each, with expected diagnostic ids in a sibling `expected.txt`-style Kotest data table (fixture → id → substring of the suggested alternative):
  - `neg/ff-control.ttrp` → `TTRP-CTL-001` (suggested alternative: "FF is not available in v1; use `after` (FS) or restructure"). Content: two trivial pg fragment containers `a`, `b` + top-level `a finishes with b` (C3-e keyword is grammatically reserved, so this parses; F-b: use = capability error).
  - `neg/cycle.ttrp` → `TTRP-CTL-002` ("the graph must be acyclic (B-T2); break the cycle"). Content: two polars containers `c1(in a, out o)` / `c2(in a, out o)` each `o = filter(a, x > 0)`, wired `c1 -> c2.a` and `c2 -> c1.a`.
  - `neg/control-cycle.ttrp` → `TTRP-CTL-002`. Content: two containers + `control { b after a  a after b }`.
  - `neg/multi-in.ttrp` → `TTRP-CTL-003` ("a data in-port takes exactly one edge (B-T2, no implicit union); use `union(a, b)`"). Content: containers `a`, `b`, `c(in x, out o)`; wiring `a -> c.x` and `b -> c.x`.
  - `neg/err-cross-container.ttrp` → `TTRP-CTL-004` ("cross-container `err` (signal) is not supported in v1 (F-d-i); consume `err` inside the island or rely on fail-fast; `rejects` (data) may cross"). Content: `a.err -> b.in` between two containers.
  - `neg/display-as-source.ttrp` → `TTRP-CTL-005` ("`display` is a sink-only leaf (Q11); read from the node feeding it instead"). Content: `crunch.result -> display(main)` then `display(main) -> store(files.copy)`. If `TTRP.g4` (Stage 1.1) already rejects display-in-source syntactically, keep the fixture, assert the Phase-1 parse diagnostic instead, and note which id fired in a fixture comment.
  - `neg/reserved-port.ttrp` → `TTRP-CTL-006` ("`err` is a reserved port name (S10); rename the declared port"). Content: `container c(in err, out o) …`.
- [ ] Write failing spec skeletons in `packages/kotlin/ttrp-graph/src/test/kotlin/org/tatrman/ttrp/graph/` (Kotest `StringSpec`, repo style — see `ttr-parser`'s `DedentSpec.kt`): `model/PortModelSpec.kt`, `build/GraphBuilderSpec.kt`, `build/SsaDesugarSpec.kt`, `build/ContainerMappingSpec.kt`, `build/ControlEdgeSpec.kt`, `validate/StructureValidatorSpec.kt`, `HeroGraphSpec.kt`. Each spec lists its test-case names as strings now (bodies `TODO()` or `fail(...)`); the case rosters are given in T2.1.2–T2.1.7 below.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test` — compiles, runs, and FAILS with the skeleton cases red (test-first baseline). Fixture files load from the classpath (add a `Fixtures.kt` loader mirroring `ttr-semantics/src/test/kotlin/.../Fixtures.kt`).

### T2.1.2 · Core model types: `TtrpGraph`, `Node`, `Port`, `Edge`

- [ ] In `org.tatrman.ttrp.graph.model` create:
  - `sealed interface Node` with the T10/T9/B-T2 roster as data classes: `Project`, `Filter`, `Branch`, `Switch`, `Join` (join `type` incl. `semi`/`anti` per B-T10 sweep), `Aggregate` (carries `AggregateCall` list + optional `having` expression pre-expansion), `Sort`, `Union`, `Intersect`, `Except`, `Values`, `Limit`, `Pivot` (static declared values, T7/T10), `Load`, `Store`, `Transfer`, `Index`, `Container`, `Display` — **plus** the authoring-sugar forms `Select`, `Calc`, `Distinct` under a `sealed interface SugarNode : Node` marker (they must be representable at construction; Stage 2.3a's sugar stratum expands them; `Materialize` is NOT a surface/graph node — S13, it exists only as a 2.3 rewrite output pattern). Every node carries: stable id (document-order), SSA label (Q7-γ: the surviving variable name, or `~n` anonymous per C1-c-i), source location, provenance slot (E-d er origin).
  - `Port` — `name: String`, `kind: PortKind` (`DATA` | `CONTROL`), `direction` (`IN`/`OUT`), optional static schema (T7; nullable ONLY for `Display` inputs, Q11 dynamic-schema exception is modelled as "sink accepts any"). Reserved names as constants: `in, out, err, rejects, true, false, else` (S10, lowercase). Every node kind declares its port signature incl. the two reserved error ports `err` (control-shaped signal) + `rejects` (data-shaped rows) per C3-f; `Branch` outs = `true`/`false`; `Switch` outs = named + optional `else`; `Union` ins = `in1..inN` (S11); every node has a **default port** (B-T2).
  - `Edge` — out-port ref → in-port ref, `EdgeKind` `DATA` | `CONTROL(FS|SS)`. No FF arm in the type (FF is rejected at build with `TTRP-CTL-001`; keeping it un-representable is the B-T2-as-amended v1 scope).
  - `TtrpGraph` — nodes, edges, containers (id → member node ids + port mapping), insertion-ordered collections only (`LinkedHashMap`/`List` — determinism groundwork for 2.3).
- [ ] Implement `PortModelSpec` cases: "every node kind exposes err and rejects", "branch out ports are true and false", "union in ports are in1..inN", "default port resolution per node kind", "reserved names are exactly S10's seven".
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*PortModelSpec'` — green. `ktlintCheck` passes.

### T2.1.3 · Graph builder + SSA variable desugar (Q7-γ)

- [ ] `org.tatrman.ttrp.graph.build.GraphBuilder`: consumes the Phase-1 resolved AST (ttrp-frontend output — depend on `:packages:kotlin:ttrp-frontend` via the version catalog) and emits `TtrpGraph`. Statements are incremental graph construction (B-T4): chains (`a -> filter(…) -> sort(…)`) create a run of nodes/edges; assignments name the out-edge; both freely mixed (C3-a γ); chains legal in source position (C3-a-iv-2); precedence `=` < `->` < call is the parser's job — builder just walks.
- [ ] SSA desugar per Q7-γ: a variable names an edge/anonymous instance; **reassignment mints a fresh instance** (`sales = filter(sales, …)` reads instance `sales#1`, writes `sales#2`); the *name* survives as the node label on every instance (`sales#1`, `sales#2` — ζ-key form per C1-c-i, feeding E-b CTE names later). Variables are data-only, never containers (B-T4). Multicast = same variable read in ≥2 statements ⇒ ≥2 edges off one out-port (B-T2, legal).
- [ ] Multi-in ops from named args only (C3-c): `join(left: …, right: …)` binds ports by arg name; `union(a, b, c)` list form → `in1..inN` (S11). Column qualification `left.x` is expression-land (Phase 1) — builder only wires ports.
- [ ] Implement `SsaDesugarSpec` cases: "reassignment creates a fresh instance with same label", "chain of three ops creates three nodes and two edges", "multicast variable produces two edges from one out-port", "variable labels survive on nodes" ; `GraphBuilderSpec` cases: "named join args bind left and right ports", "union list form binds in1..in3", "chain in source position builds inline nodes".
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*SsaDesugarSpec' --tests '*GraphBuilderSpec'` — green.

### T2.1.4 · Containers: closed functions, port mapping, targets

- [ ] Build `Container` nodes per B-T9/C3-d-iii: closed — bodies reference only their own declared ports (violations were already Phase-1 resolution errors; assert we never see them); container ports **map onto internal node ports** (declared `(in accounts, out result, out low, err rejects)` ↔ body bindings `result = b.true`, `rejects = j.rejects`); container carries the author-assigned execution target (engine-instance qname string at this stage — resolved against the world in 2.2; B-T9/T8 v1 placement is author-assigned). Fragment containers (`"""sql` bodies) arrive from Phase 1 as pre-decomposed node lists (C2-a full decomposition) — the builder treats them identically; they expose single default-out + `err` only (C2-c-i, C2-e).
- [ ] Program-level wiring: `acc_prep -> crunch.accounts` = edge from `acc_prep`'s default out to the container in-port, with default-port elision (`a -> b` ⇒ `a.out -> b.in`). Program-level leaves (`store(...)`, `display(...)`, source `load(...)`) become `Store`/`Display`/`Load` nodes at program level (S14: source load / terminal store authored; engine-crossing movement is NOT built here — Stage 2.3b synthesizes it).
- [ ] Implement `ContainerMappingSpec` cases: "container ports map to internal node ports", "program wiring binds default ports", "fragment container exposes single default out and err", "container target string is carried", "nested container recurses" (C3-d-v).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*ContainerMappingSpec'` — green.

### T2.1.5 · Control edges: FS/SS build, FF rejection, err rules

- [ ] `b after a` → CONTROL(FS) edge; `a with b` → CONTROL(SS); both top-level and inside `control {}` (C3-e). Control constraints are hard on their effect (B-T2) — represent as ordinary edges; the 2.3 rewriter must preserve them.
- [ ] `a finishes with b` (FF) → diagnostic `TTRP-CTL-001`, error, with the suggested alternative from T2.1.1 (F-b: FF dropped from v1; keyword stays reserved in the grammar, use = capability error).
- [ ] `err` port rules: unconnected `err`/`rejects` ⇒ fail-fast default, no diagnostic (C3-f elision is P2-legal). A cross-container edge whose source is an `err` port → `TTRP-CTL-004` (F-d-i α). `rejects` crosses containers freely (data-shaped — the hero's error path).
- [ ] Implement `ControlEdgeSpec` cases: "after builds FS edge", "with builds SS edge", "control block accepted", "finishes with yields TTRP-CTL-001", "cross-container err yields TTRP-CTL-004", "cross-container rejects is legal", "unconnected err is silent".
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*ControlEdgeSpec'` — green, incl. the `neg/ff-control.ttrp` and `neg/err-cross-container.ttrp` fixtures.

### T2.1.6 · Structural validation: acyclicity, single-in, Display, reserved ports

- [ ] `org.tatrman.ttrp.graph.validate.StructureValidator`, run after build:
  - **Acyclicity** over data ∪ control edges (B-T2: v1 graph is acyclic; T2-f) — Kahn or DFS; on cycle emit `TTRP-CTL-002` naming the cycle's node labels in document order (deterministic message, P2).
  - **Single-in:** every DATA in-port has ≤ 1 incoming edge (B-T2 no-implicit-union); violation `TTRP-CTL-003` with the `union(...)` suggestion. Multicast out stays unlimited.
  - **Display (Q11):** sink-only leaf — any out-edge from `Display` = `TTRP-CTL-005`; dynamic (absent) schema is legal ONLY on `Display` inputs (B-T7 sweep: authored program outputs only) — an absent schema anywhere else is an internal error (Phase-1 typing owes it).
  - **Reserved ports (S10):** author-declared container port named from the reserved seven → `TTRP-CTL-006`.
- [ ] Implement `StructureValidatorSpec` cases (one per negative fixture from T2.1.1, asserting id + suggested-alternative substring + source range points at the offending statement): "cycle via data wiring", "cycle via control block", "multi-edge into data in-port", "display as source", "reserved port name".
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*StructureValidatorSpec'` — green; every negative fixture produces exactly its expected diagnostic and no others.

### T2.1.7 · Hero component test — pinned graph inventory

- [ ] `HeroGraphSpec`: build `hero.ttrp` end-to-end (frontend parse+resolve → GraphBuilder → StructureValidator) and pin the inventory with exact assertions (not snapshots — write them out so review reads them): containers `acc_prep` (target `erp_pg`, fragment-decomposed: Load/Project/Filter per C2-a clause table), `crunch` (target `polars`; nodes: Load(files.sales_2026)+declared schema, Filter, Filter (SSA `sales#2`), Join(inner), Aggregate(group region; sum, avg), Branch, port bindings result/low/rejects); program level: 1 Display (`main_result`), 2 Store leaves; cross-container data edge `acc_prep→crunch.accounts` present as a plain data edge (movement synthesis is 2.3b); zero control edges; zero diagnostics.
- [ ] Same for `hero-er.ttrp`: er refs already rewritten to db refs by Phase 1 (E-d early rewrite), so assert node-level **provenance** is populated (`customerType ← erp.er.customer.customerType`-shaped origin on the Filter's expression carrier and on the relation-join's Join node).
- [ ] Register all new `TTRP-CTL-*` ids + suggested alternatives in the Phase-1 diagnostics registry/catalogue table (contracts §8: the tables are versioned fixtures + the assist repair vocabulary).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test` — entire module green. `./gradlew :packages:kotlin:ttrp-frontend:test` still green (no regressions from registry additions).

## Definition of DONE (stage)

- [ ] `./gradlew :packages:kotlin:ttrp-graph:test` green; all specs from T2.1.1 implemented, none skipped.
- [ ] Hero + er-variant build to validated graphs with pinned inventories (T2.1.7 assertions, not TODOs).
- [ ] All seven negative fixtures produce their named `TTRP-CTL-*` diagnostics with suggested alternatives; ids registered in the diagnostics catalogue.
- [ ] FF is unrepresentable in the edge type and rejected with contracts-pinned `TTRP-CTL-001`.
- [ ] No manifest/world/rewrite code leaked into this stage (grep: no reference to capability or staging in `ttrp-graph` main source yet).
- [ ] `./gradlew build` green repo-wide; ktlint clean.

## Blockers

- **CLEARED (2026-07-06):** the review-001 1.3-A carry-over (join-based er-hero + join-condition synthesis) is done in T2.1.0. Resolution required a ttr-metadata loader change (it never read the relation `join:` property into `joinPairs`) — Bora-approved "do it fully via loader change." See `tasks-overview.md` §Blockers for the full note.

## Deviations from the task text (conscious, recorded)

- **T2.1.0 scope grew into ttr-metadata (Bora-approved):** the task assumed "no metadata *model* change… only fixture data + a spec fix." The *model* (data class) is unchanged, but the *loader* (`Source.kt`) had to learn `join: [{from,to}]` → `joinPairs`; the RES-005 seed relocated `customer.customerType`→`customer.segment` (+ unbound `product` entity). `on: relation` synthesis rides on a new `ErRewrite.joinCondition` field.
- **Fragment containers are NOT decomposed in Phase 2 (T2.1.4/T2.1.7):** Phase 1 keeps fragment interiors verbatim (C2-f); SQL→Load/Project/Filter decomposition is Phase 6 (the dialect grammars). So the hero's `acc_prep` builds as an **opaque fragment island** (`Container.fragment` carries the raw SQL; `memberIds` empty), not the "fragment-decomposed Load/Project/Filter" the T2.1.7 text anticipated. Assertions pin the fragment shape instead.
- **Hero `crunch` inventory = 5 nodes (Load/Filter/Join/Aggregate/Branch):** the T2.1.7 text listed "Filter, Filter"; the verbatim hero has one `filter`. Asserted the real inventory (`[x]` = intent, review verifies).
- **FF (`TTRP-CTL-001`) is a Phase-1 reject:** the front-half already emits it; the GraphBuilder skips FF silently (FF is unrepresentable in `EdgeKind`) to avoid double-reporting. The negative corpus asserts the combined front-half+graph error set.

## References

- **Decisions:** B-T2 (ports, multicast, no-implicit-union, FS/SS hard-on-effect, acyclic v1), B-T4/Q7-γ (variables = SSA edge sugar, data-only, one doc = one graph), B-T9 (Load/Store/Transfer/Index distinct; Container = function with mapped ports, bears target), B-T10 (+ sweep: node roster, Distinct→Aggregate sugar, Intersect/Except real, semi/anti = Join types), B-T7 sweep (static schemas; dynamic = outputs only), Q11 (Display sink-only, dynamic schema, named/multiple), C3-a/c/e/f (γ hybrid, named-only multi-in, control keywords, err/rejects), C3-d-iii/iv (closed containers; movement synthesized — *later stage*), F-b (FF dropped v1), F-d-i (cross-container err = error), S10/S11/S12/S13/S14, E-d (er provenance), C1-c-i (ζ SSA name keys — label discipline), C2-a/c/e (fragment decomposition, single out, err-only).
- **Docs:** `docs/ttr-p/architecture/architecture.md` §3–4 · `docs/ttr-p/architecture/contracts.md` §3, §8 · `docs/ttr-p/design/02-internal-model-options.md` T2/T4/T9/T10 · `docs/ttr-p/design/05-canonical-dsl-options.md` (hero rendering) · `docs/ttr-p/design/06-model-binding-options.md` (er hero).
- **Repo conventions:** Kotest `StringSpec` (`packages/kotlin/ttr-parser/src/test/kotlin/.../DedentSpec.kt`), fixture loader pattern (`ttr-semantics/.../Fixtures.kt`), build style (`packages/kotlin/ttr-parser/build.gradle.kts`), ktlint excludes for generated code.
