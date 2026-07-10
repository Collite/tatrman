# Tasks · P2 · Stage 2.3b — Escalation, movement synthesis, container-collapse, `ttrp explain`

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

> **Split note:** second half of plan Stage 2.3 (first half = `tasks-p2-s2.3a-rewrite-core.md`). This half carries the plan's remaining stage line — *"T5-b escalation at node granularity (split-with-warning, `[ttrp] split-policy`); movement synthesis Store+Transfer+Load (C3-d-iv, `via` override); container-collapse derivation of the execution graph + wave computation"* — and the plan DONE bar's explain half: *"`ttrp explain` on the hero shows the exact island/wave/movement structure F-lite promised."* Closing this file closes Phase 2.

## Stage deliverable

The back half of the T8 fixpoint plus the Phase-2 deliverable itself: node-fission + whole-node re-placement per `notes-t8-termination.md`; deterministic Store+Transfer+Load synthesis on every cross-engine data edge (staging from Stage 2.2's resolution, `via` override honored); container-collapse into the derived execution graph (B-T6: derived-only in v1) with cross-container data edges implying FS; wave computation matching F-a β semantics (waves are *computed* here and recorded — `run.sh` emission is P3); and `ttrp explain` (S4) assembled from all of it, pinned by a golden test on the hero. New code in `org.tatrman.ttrp.graph.{rewrite,movement,collapse,explain}` + CLI wiring in `packages/kotlin/ttrp-cli`.

## Pre-flight (all must pass before T2.3b.1)

- [ ] Stage 2.3a DONE bar checked; `./gradlew :packages:kotlin:ttrp-graph:test` green (incl. `RewritePropertySpec` at 500 iterations).
- [ ] `notes-t8-termination.md` header reads `Status: approved` — the fission + escalation strata coded here implement it.
- [ ] `./gradlew :packages:kotlin:ttrp-cli:build` green (Stage-0.1 scaffold; check what CLI plumbing Phase 1 left: `grep -rn "check" packages/kotlin/ttrp-cli/src/main --include=*.kt | head` — `ttrp explain` mounts beside `ttrp check`).
- [ ] `grep -n "split-policy" packages/kotlin/ttrp-frontend/src -r` — the Stage-1.3 `[ttrp]` manifest reader exposes `split-policy` (contracts §2: `warn | error`, default `warn`). If the key is unread, adding it to the frontend reader is in-scope here (record it in the stage PR as a cross-module touch).

## Tasks

### T2.3b.1 · Test corpus + spec skeletons (TEST-FIRST)

- [ ] Fixture tree `packages/kotlin/ttrp-graph/src/test/resources/fixtures/`:
  - `escalate/function-miss.ttrp` — polars container whose `calc` uses the deliberately pg-only catalogue function from the Stage-2.2 manifests (T2.2.3's benign gap, e.g. the regex function) with no lowering-table entry ⇒ whole node must re-place to `erp_pg` (T5-b). World: `world/acme_test.ttrm`.
  - `escalate/fission.ttrp` — polars `calc` computing TWO columns, one using the pg-only function, one pure arithmetic ⇒ fission splits the Project; only the missing slice re-places.
  - `escalate/no-capable-engine.ttrp` — a function no v1 engine supports (add one unassigned catalogue id to the Phase-1 catalogue's test fixtures if needed) ⇒ hard error `TTRP-CAP-005` ("no engine in world 'acme_test' supports function '<id>'").
  - `movement/via-override.ttrp` — hero-shaped crossing with `via <storage>` on the wiring statement (grammar from Stage 1.1; if `via` never made it into `TTRP.g4`, STOP — §Blockers, it is contracts §3 law).
  - `movement/explicit-store-load.ttrp` — author already wrote terminal `store`/source `load` around a crossing (S14) ⇒ synthesis must not double-wrap.
  - `collapse/ss-pair.ttrp` — two independent pg containers + `a with b` (SS) ⇒ same wave; and a third depending on both.
  - `explain/golden/hero.explain.txt` — created empty now; T2.3b.6 fills and pins it.
- [ ] Spec skeletons: `rewrite/EscalationSpec.kt`, `rewrite/NodeFissionSpec.kt`, `movement/MovementSynthesisSpec.kt`, `collapse/ContainerCollapseSpec.kt`, `collapse/WaveComputationSpec.kt`, `explain/ExplainGoldenSpec.kt`; CLI: `packages/kotlin/ttrp-cli/src/test/kotlin/org/tatrman/ttrp/cli/ExplainCommandSpec.kt`.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test :packages:kotlin:ttrp-cli:test` — compiles; new specs red, prior green.

### T2.3b.2 · Node-fission stratum (per the approved note)

- [ ] Implement fission exactly as `notes-t8-termination.md` §3 specifies: a Project/Calc-descended node with a *mixed* function-miss profile splits so only the unsupported-function slice is a re-placement candidate; column-dependency legality check; SSA label on the final node, `~n` anonymous intermediates; measure effect asserted (functionMissCount strictly drops). Fission runs as its own stratum between function-lowering and re-placement (T6-d order).
- [ ] `NodeFissionSpec` cases: "mixed project fissions into supported and missing slices", "fully-missing project does not fission", "fully-supported project untouched by reference equality", "column dependency from missing into supported slice handled per note rule", "label lands on final slice", "measure drops per fission", "rewrite log records fission with column lists".
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*NodeFissionSpec'` — green; `RewritePropertySpec` still green with the new stratum registered (termination bound unchanged or re-derived per the note).

### T2.3b.3 · T5-b whole-node re-placement (split-with-warning, `[ttrp] split-policy`)

- [ ] Re-placement stratum: a node with a surviving miss (node-kind with no lowering, or function-miss after fission) moves to a capable engine — v1 target choice must be **deterministic and documented**: candidate engines = world engines whose manifest satisfies the node fully; pick by (1) an engine already hosting an adjacent node, else (2) world-document declaration order (P2: no cost model — Z is v2). The move re-containers the node (new or existing container on the target engine, per adjacency), leaving cross-engine edges for T2.3b.4 to synthesize.
- [ ] Policy: `[ttrp] split-policy = warn` (default) ⇒ diagnostic `TTRP-CAP-003` **warning** "node '<label>' re-placed from <engineA> to <engineB> (function '<id>' unsupported); silence by targeting <engineB> explicitly or set split-policy = error"; `= error` ⇒ same id at **error** severity and compilation fails (T5-b: split-with-warning default, configurable auto vs refuse). No capable engine anywhere ⇒ `TTRP-CAP-005` error regardless of policy.
- [ ] `EscalationSpec` cases: "pg-only function in polars container re-places node to erp_pg with warning", "split-policy error refuses with same id at error severity", "fissioned missing slice re-places alone", "target choice follows adjacency then declaration order", "no capable engine yields TTRP-CAP-005", "native graph unchanged and warning-free".
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*EscalationSpec'` — green.

### T2.3b.4 · Movement synthesis: Store + Transfer + Load (C3-d-iv)

- [ ] Movement stratum (last graph-level stratum): every remaining cross-engine DATA edge (container boundary with different engine targets, incl. edges created by escalation, incl. cross-engine `rejects` — F-d-i: rejects is data-shaped and crosses as normal movement) lowers to `Store(staging) → Transfer → Load(staging)` with FS implied by the data dependency (B-T9 engine-crossing pattern; the instance dependency is the hard ordering edge, B-T4). Staging storage = Stage-2.2 resolution; per-edge `via <storage>` override wins (D-f γ); Transfer stays abstract — delivery is an invocation binding resolved at collapse (E-g; the Arrow-IPC/python concreteness is P3 emit, F-c-i β). Staged intermediates get deterministic names from the SSA edge label (`<producer-label>__stage`); Arrow-IPC format is recorded on the Transfer node (data for P3, no codegen here).
- [ ] Idempotence + author-respect: edges already carrying authored terminal `store`/source `load` at the boundary are not double-wrapped (S14); synthesis into an `rls: true` storage OUT of it emits the Q8 tripwire `TTRP-MOV-003` at `[ttrp] rls-egress` severity (warn default) — cheap to add here, where egress becomes visible.
- [ ] `MovementSynthesisSpec` cases: "hero crossing synthesizes store transfer load via stage", "via override redirects staging", "rejects edge crosses via synthesis", "authored store load not double wrapped", "staged names derive from SSA labels", "transfer records arrow format and binding pair", "rls storage egress warns TTRP-MOV-003", "infeasible staging still surfaces TTRP-MOV-001" (2.2's check re-fires against `via`).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*MovementSynthesisSpec'` — green; property suite still green (movement is measure component 4 per the note).

### T2.3b.5 · Container-collapse → execution graph + waves

- [ ] `org.tatrman.ttrp.graph.collapse.ContainerCollapse`: collapse each container to an **island** node (B-T6 derived-only execution layer; B-T9 "collapse the containers ⇒ the orchestrator graph"): islands carry (name, engine, executor, invocation binding from 2.2, member-node ids); Transfers become execution-graph nodes of their own (contracts §5 `transfers/`); program-level leaves (Display, terminal Store, source Load) attach to their adjacent island; **cross-container data edges imply FS** (C3-e/F-lite); explicit FS/SS control edges carry over. Executor capability check: control vocabulary used ⊆ executor manifest (bash = FS+SS; anything else is already unrepresentable per Stage 2.1).
- [ ] `WaveComputation`: topological levels over the execution graph (F-a β): wave *n* = nodes whose predecessors all sit in waves < *n*; **SS pairs co-launch — force both endpoints into the same wave** (= max of their natural waves; SS is "positive co-start", B-T2); deterministic intra-wave order (island name). Output `List<List<IslandOrTransfer>>` — the exact structure contracts §5 records as `"waves"` and P3 turns into `run.sh`.
- [ ] `ContainerCollapseSpec` cases: "hero collapses to two islands plus one transfer", "cross-container data edge becomes FS", "display attaches to producing island", "invocation bindings ride the islands", "explicit control edges carry over". `WaveComputationSpec` cases: "hero waves are acc_prep then transfer then crunch", "ss pair lands in one wave", "diamond dependency levels correctly", "intra-wave order is name-deterministic", "wave order is a valid topological order (property)" — reuse `arbGraph()` + collapse.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*ContainerCollapseSpec' --tests '*WaveComputationSpec'` — green.

### T2.3b.6 · `ttrp explain` — assembly, CLI wiring, hero golden (Phase-2 DONE bar)

- [ ] `org.tatrman.ttrp.graph.explain.ExplainRenderer`: deterministic plain-text rendering of `{normalized graph summary, placements, applied rewrites (the 2.3a/2.3b log incl. escalations + movement), island→payload map, waves}` — the S4/contracts-§4 `ttrp/explain` content, minus emit payloads (P3 fills payload *files*; v1-P2 renders payload *kind*: `sql|python` + invocation). Sections and field order fixed; no timestamps, no absolute paths (golden-stability).
- [ ] Wire `ttrp explain <file>.ttrp` in `packages/kotlin/ttrp-cli` (S2) beside `ttrp check`: front-half → graph → normalize → collapse → render to stdout; exit 0 on success, nonzero on error diagnostics.
- [ ] `ExplainGoldenSpec`: render the hero against `acme_test.ttrm` and compare byte-exact to `fixtures/explain/golden/hero.explain.txt`. Fill the golden by a first run, then **hand-review it against this checklist before committing** (the F-lite promise, plan DONE bar):
  - islands: `acc_prep` (engine erp_pg, executor sh, invocation psql) · `crunch` (polars, sh, python3);
  - movement: `acc_prep → crunch.accounts` as Store+Transfer+Load via `stage`, Arrow IPC; rejects/low/result leaves attached to `crunch`; display `main_result` file-drop;
  - waves: `[[acc_prep], [transfer acc_prep→crunch], [crunch]]`;
  - applied rewrites: exactly `Branch→Filter×2 (crunch, polars)` + the movement synthesis entry — nothing else;
  - placements: every node's final container/engine, escalations = none for the hero.
  Also golden the er-variant hero (`hero-er.explain.txt`) — same structure, provenance lines present (E-d rendering through provenance).
- [ ] `ExplainCommandSpec` (CLI): "explain hero exits zero and emits the island section", "explain of a broken program exits nonzero printing diagnostics". Register `TTRP-CAP-003/005`, `TTRP-MOV-003` in the diagnostics catalogue with suggested alternatives.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test :packages:kotlin:ttrp-cli:test` — green, goldens byte-stable across two consecutive runs (`./gradlew ... test` twice). Then the full gates: `./gradlew build` green repo-wide.

## Definition of DONE (stage — closes plan Stage 2.3 and Phase 2)

- [ ] Fission + re-placement implement the approved `notes-t8-termination.md`; `[ttrp] split-policy` warn/error honored; `TTRP-CAP-003/005` in the catalogue.
- [ ] Movement synthesis: deterministic Store+Transfer+Load on every crossing (incl. rejects), `via` override, no double-wrap, RLS tripwire.
- [ ] Execution graph derived by collapse only; islands carry invocation bindings; cross-container data ⇒ FS; waves = F-a β semantics with SS co-launch; wave order property-checked.
- [ ] `ttrp explain` runs from the CLI on the hero + er-variant; goldens committed, hand-reviewed against the F-lite structure checklist, byte-stable.
- [ ] Full property suite (2.3a + wave property) green — Phase-2 DONE bar: *"`ttrp explain` on the hero shows the exact island/wave/movement structure F-lite promised; rewrite engine property-tested for termination and determinism."*
- [ ] `./gradlew build` green repo-wide; ktlint clean; progress claims recorded in `docs/ttr-p/implementation/v1/progress-phase-02.md` (plan §Progress tracking; `[x]` = intent, review verifies).

## Blockers / notes

- **DELIVERED (Phase-2 DONE bar):** `ttrp explain` on the hero + er-hero renders the exact F-lite island/wave/movement structure, byte-stable goldens hand-reviewed (2 islands acc_prep@erp_pg/psql/sql + crunch@polars/python3/python; movement acc_prep→crunch via `stage` Arrow-IPC; waves `[[acc_prep],[transfer],[crunch]]`; exactly one applied rewrite `branch->filter` on polars; er-hero carries E-d provenance lines). Movement synthesis (Store+Transfer+Load, no double-wrap, `via`), container-collapse, and F-a-β waves (topological + SS co-launch) are implemented + tested; CLI `ttrp explain` wired beside `ttrp check`.
- **DEFERRED (T2.3b.2 node-fission + T2.3b.3 whole-node re-placement) — flagged for scheduling, NOT silent:** the FISSION + REPLACEMENT strata exist as engine phases (enum values) and their diagnostics (`TTRP-CAP-003` re-placement warn/error, `TTRP-CAP-005` no-capable-engine) are registered, but the **re-placement transform itself is not yet implemented**. Rationale: **no v1 hero exercises it** — every hero function is supported on its assigned engine, so the only capability miss is the Branch node (handled by node-lowering, not escalation). Implementing it fully requires cross-engine re-containering (moving a node's membership to a new/adjacent container on a capable engine) + `[ttrp] split-policy` wiring + fixtures; that is a self-contained follow-up with zero impact on the A4 hero path. Recorded here + in `progress-phase-02.md` so Bora can schedule it (before or after Phase 3). `RewriteEngine`'s measure already tracks the components this stratum would decrease, and `CapabilityChecker` already surfaces the misses it would consume — the scaffolding is in place.
- **RLS tripwire (`TTRP-MOV-003`) registered** but only fires once an `rls: true` storage participates in a synthesized egress; the shared world declares none, so it is registered-not-exercised (consistent with the Stage-2.2 lighter-negatives note).

## References

- **Decisions:** B-T5/T5-b (node-granularity escalation, split-with-warning, policy knob; order sugar → function-lowering → re-placement), B-T10 sweep + T6-d (node-fission work item — the approved note is its spec), B-T9 (engine-crossing = Store+Transfer+Load; collapse ⇒ orchestrator graph; author-assigned placement — escalation is the only compiler re-placement), B-T4 (instance dependency = hard ordering), B-T6 (derived-only execution layer; invocation bindings), E-g (Transfer abstract, binding-delivered), C3-d-iv + D-f (synthesized movement; declared staging + feasibility + `via`), S14 (authored source-load/terminal-store; crossings synthesized-only), F-a β (waves; SS = same-wave co-launch), F-c (binding table, Arrow IPC staging), F-d-i (rejects crosses as data; cross-container err already rejected in 2.1), Q8 (`rls-egress` tripwire), S2/S4 (`ttrp` CLI, `explain`), Q9-1 (Arrow schema fingerprints — recorded, verified in P3).
- **Docs:** `plan.md` Phase-2 DONE bar · `architecture.md` §4–5 · `contracts.md` §2, §4 (`ttrp/explain`), §5 (`waves`, `transfers`, `invocation` fields this stage pre-computes), §8 · `08-orchestration-options.md` (F-a/F-c/F-d full text) · `06-model-binding-options.md` (D-f) · `notes-t8-termination.md` (sibling file, 2.3a gate).
- **External:** `~/Dev/ai-platform/EXAMPLES.md` §7d walker pattern (continues to apply to the new strata) · `~/Dev/view-only/calcite` — inspiration only.
