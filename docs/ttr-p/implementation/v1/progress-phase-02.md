# Progress — Phase 2 (Graph + normalizer → `ttrp explain`)

> **Status:** code-complete 2026-07-06, awaiting `/review`. `[x]` = intent — the reviewer verifies against runtime (CLAUDE.md cadence). Branch `feature/ttr-p-v1-phase2`.

## Deliverable

`ttrp explain <file>.ttrp` — normalized graph, placements, applied rewrites, island→payload map, and waves for the hero + er-hero. The whole new `packages/kotlin/ttrp-graph` module (plus a ttr-metadata loader change, a diagnostics-catalogue growth, and CLI wiring) lands here. `./gradlew build` green repo-wide; ktlint clean.

## What shipped, by stage

### Stage 2.1 — Graph construction
- **T2.1.0 (review-001 1.3-A):** restored the full join-based er-hero; implemented `on: relation` → port-qualified join-condition `Expression` synthesis (`op.eq`/`op.and`, provenance, on `ErRewrite.joinCondition`, read onto the `Join` node). **Required a ttr-metadata loader change** (Bora-approved): `Source.kt` now reads the relation `join:` property into `Relation.joinPairs` (previously dropped). Shared erp fixture binds the er tier; RES-005 seed relocated `customer.customerType`→`customer.segment` (+ unbound `product` entity). Positive RES-004 happy-path test added.
- Node model (T10 roster + T9 movement/IO + Container/Display), typed ports (S10 reserved names, err/rejects per C3-f), `Edge`/`TtrpGraph`.
- `GraphBuilder`: SSA variable desugar (Q7-γ, `name#k` labels), containers with port mapping (B-T9), fragment containers kept **opaque** (P6 decomposes), FS/SS control edges, program-level wiring/leaves.
- `StructureValidator`: acyclicity (CTL-002), single-in (CTL-003), display-sink (CTL-005); build-time CTL-004 (cross-container err), CTL-006 (reserved port); FF is a Phase-1 reject (CTL-001).
- Hero + er-hero build to validated graphs with pinned inventories; **7 negative fixtures** each produce exactly their CTL id.

### Stage 2.2 — Manifests + world binding
- Engine-type capability manifest format (T6 β) as shipped JSON via kotlinx-serialization (**reviewable choice**, behind `ManifestSource`); the three v1 manifests postgres-16 / polars / bash.
- `WorldBinder` (engine→manifest by `type`+major-version / `extendsRef`; WLD-005), `CapabilityChecker` (node + function granular, parameter-aware; CAP-001/002), `InvocationBindingResolver` (psql/python3/file-drop; WLD-007), `StagingResolver` (default + reachability feasibility; WLD-006, MOV-002/004).
- Hero component test: binds clean, capability check reports **exactly {Branch@polars}**, bindings {psql, python3, file-drop}, staging=stage feasible.

### Stage 2.3a — Rewrite engine (T8) — review gate PASSED
- `notes-t8-termination.md` written + **approved by Bora** (measure + node-fission rules).
- `RewriteEngine`: stratified fixpoint, strictly-decreasing 4-tuple lexicographic measure asserted per rewrite (hard-fail otherwise), ordered rewrite log, deterministic (lowest-node-id-first).
- Sugar stratum (Select/Calc→Project, Distinct→Aggregate, HAVING split); capability-lowering stratum (Branch→Filter×2 with 3VL-correct `not(coalesce(pred,false))`; right-Join→left+swap; Intersect/Except→semi/anti Join).
- Property tests (200 iters): termination, determinism, idempotence, sugar-freedom. Hero normalizes with exactly one `branch->filter` lowering.

### Stage 2.3b — Movement + collapse + `ttrp explain`
- `MovementSynthesizer`: cross-engine data edge → Store+Transfer+Load via staging (Arrow-IPC, `via`, no double-wrap).
- `ContainerCollapse`: islands + transfers + F-a-β waves (topological levels + SS co-launch).
- `ExplainRenderer` + `TtrpPipeline` (front-half → build → normalize → staging → movement → collapse → explain); CLI `ttrp explain` beside `ttrp check`.
- Byte-stable hero + er-hero explain goldens, hand-reviewed against the F-lite checklist.

## Deviations & deferrals (conscious, recorded — read before review)

1. **T2.1.0 grew into ttr-metadata (Bora-approved):** loader now parses relation `join:`; the *model* data class is unchanged. See tasks-overview §Blockers.
2. **Fragment containers stay opaque in Phase 2** (T2.1.4/.7): SQL→node decomposition is P6; the hero's `acc_prep` is a fragment island (`Container.fragment`), not decomposed Load/Project/Filter.
3. **`distinct`/`having` not directly authorable** (Phase-1 grammar gap): `TTRP.g4` reserves `distinct` and has no `having` clause. The sugar rules are tested on hand-built graph nodes; authoring them is a Phase-1 grammar follow-up.
4. **T2.3b.2 node-fission + T2.3b.3 whole-node re-placement DEFERRED** (flagged for scheduling): the strata + diagnostics (CAP-003/005) exist; the re-placement transform is not implemented because **no v1 hero exercises it** (all hero functions are supported on their engine; the only miss is the Branch, handled by node-lowering). Self-contained follow-up, zero impact on the A4 hero. See tasks-p2-s2.3b §Blockers.
5. **Switch→Filter-chain, Pivot→CASE, and the function-lowering table** (between/coalesce) are not yet implemented — they fold into the escalation window with (4).
6. **Lighter-tested negative diagnostics:** WLD-006/007, MOV-002/003/004, CAP-003/005 are implemented + registered but not each fixture-driven (some need crafted worlds; RLS/MOV-003 needs an `rls: true` storage the shared world lacks).

## Phase 3 gate (why Phase 2 is the stopping point)

Phase 3 (emit/bundle/run) has a hard **external gate**: `org.tatrman:ttr-translator` (the kantheon-side Proteus-extraction arc) is **not published / not in Maven Local**. Per the coder protocol ("do not stub/vendor/re-implement the translator") and Bora's decision (Phase 2 only), work stops cleanly at the Phase-3 gate.

## Verification (run by the coder; reviewer re-runs)

- `./gradlew build` — BUILD SUCCESSFUL, all modules, ktlint clean.
- `./gradlew :packages:kotlin:ttrp-graph:test` — green (model, builder, validator, capability, rewrite property @200 iters, movement/collapse, explain goldens).
- `./gradlew :packages:kotlin:ttrp-cli:test` — green (`ttrp explain` hero exit 0 + island/wave sections; broken program exit 1).
- `./gradlew :packages:kotlin:ttr-metadata:test :packages:kotlin:ttrp-frontend:test` — green (loader + fixture changes, join-condition synthesis, RES-004 positive path, relocated RES-005 seed).
