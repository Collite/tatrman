# MD dot-path sugar — implementation plan

**Status:** v1 draft, 2026-07-08 · Owner: md-sugar arc · Companion to
[`../architecture.md`](../architecture.md) and [`../contracts.md`](../contracts.md).
Rules **R1–R23**, decisions **D1–D19**, and architecture decisions **MDS1–MDS6** cited from those.

This arc is **scheduled independently** (own arc; not on TTR-P v1's or the MD Layer A plan's
critical path). Phases are serial S0→S5→S5C except where noted; S6 can overlap S3–S5C; S7 needs
S2 + S6. The
per-phase mini-task-lists (6–8 tasks, TDD-ordered) hang off this document via
[`tasks/INDEX.md`](tasks/INDEX.md) — same management pattern as `docs/features/md/plan/` and
`docs/ttr-metadata/implementation/v1/`.

## Arc pre-flight (all must hold before S0)

- [ ] **MD Layer A v1 (TS) complete** through phase 2 (logical semantics) — it is the behavioral
  spec for the Kotlin port (MDS2). Phase 3 (binding) complete before **S4** (lowering needs
  `md2db_*`).
- [ ] **TTR-P v1** through Phase 1 (frontend + expressions) for S2/S3; through Phase 3
  (translator) for S4.
- [ ] **ttr-metadata v1** merged (registry, designer-server, `ttrm/*` protocol) — S6 extends it.
- [ ] **Optimizer-arc coordination review** held: one shared snapshot/fingerprint pattern in
  ttr-metadata (architecture §7 risk); outcome recorded in this file's changelog.
- [ ] **Semantics-block feature (grammar 4.2) merged & published** — required before **S5C**
  (journaling technical-column roles ride it, MDS8); not needed for S0–S5.
- [ ] Design note + contracts reviewed by Bora (D1–D19 sign-off — done 2026-07-08; contracts
  pending).

Every phase ends green on both build domains' gates:
`pnpm -r typecheck && pnpm -r lint && pnpm -r build && pnpm -r test` and
`./gradlew build` — plus the conformance harness where grammar is touched.
TDD within every phase: test-authoring tasks come first (red), implementation second (green).
Reviews follow the repo's `/review` cadence (progress docs record claims; reviews verify).

---

## Phase S0 — grammar version (syntax carrier)

**Deliverables:** one new grammar version per `docs/grammar-master/new-grammar-version-process.md`
carrying: `NUMBER`→`INT` + `floatLiteral` parser rule + `DOTDOT` + `mdPath`/`pathComponent` +
set braces + the statement operators `:=`/`+=`/`-=` with `cubeletStmt`/`withClause`
(contracts §1.1–1.2) in `TTRP.g4`, and the `publish: members` domain clause in `TTR.g4` (§1.4). Conformance fixtures for the §1.3 float/path table and every pre-existing
numeric-literal shape. TextMate grammar + writer/renderer updates for the TTR-M clause.

**Pre-flight:** audit of every current `NUMBER` usage across TTRP.g4 consumers (frontend, emit,
conform) — recorded as the first task.
**DONE when:** all three parser targets regenerate warning-free; conformance green including new
fixtures; **zero behavior change** for existing programs proven by fixture diff (`12.5` etc.);
grammar version tagged & published per grammar-master.

## Phase S1 — Kotlin MD semantics port (`ttr-semantics.md` subpackage)

**Deliverables:** `MdModel` — MD symbol graph (domains, dimensions, attributes, maps, measures,
hierarchies, cubelets + bindings as parsed), grain lattice (leaf = no N:1 targets; partial order =
N:1 closure), attribute→domain-map sugar resolution, cubelet default measure / measure default
agg, calc-catalog entry lookup (md-catalog absorption at the reserved seat, D-h / T5-c-β).
**Explicitly not ported:** Layer A validators/diagnostics (stay TS-side).

**Pre-flight:** TS `@tatrman/semantics` md phases 2A/2B/2E merged (the spec).
**DONE when:** parity fixtures green — same `.ttrm` fixture models in, same lattice / defaults /
map-sugar resolutions out as the TS implementation (goldens exported from the TS test suite);
published inside the existing `ttr-semantics` artifact.

## Phase S2 — resolver core (`ttr-md-resolver`, new module)

**Deliverables:** the §2–§3 contract complete: classification (R5), qualified pairs (R6),
star/set/range binding (R7), constraint search (R8) with the R9 ambiguity policy and the
TTRP-MD-014 bound, defaults fill (R10), repetition rule (R11), calc tokens + `asof` (R12),
disconnected mode (R13), explanations (R14), canonical form + shape (§3, R15), `PathContext`
overlay resolution (R20, as a library function — statement wiring is S5). `MemberCatalog`
*interface* + in-memory fixture implementation only.

**Pre-flight:** S0 (parse shapes), S1 (`MdModel`).
**DONE when:** golden fixture suite green (contracts §10 — every design-note example, all order
permutations, all diagnostics, disconnected variants); search benchmark recorded (≤10-token paths
resolve < 10 ms against the fixture model); `org.tatrman:ttr-md-resolver` publishes locally.

## Phase S3 — TTR-P read integration (ttrp-frontend)

**Deliverables:** mdPath recognition in expression position; column-first precedence + shadowing
warning (R23 / TTRP-MD-012); resolver invocation with snapshot + `asof` (compile-time parameter
plumbing, D17); shape typing + broadcast checks (R15–R18, TTRP-MD-008); diagnostics TTRP-MD-001…
008/011…014 wired into the Stage-1.1 enum pattern; hover-ready `Explanation` exposed through the
frontend API (consumed later by ttrp-lsp — exposure only, no LSP work here).

**Pre-flight:** S2. **DONE when:** frontend Kotest suites green (positive: the design-note read
examples typecheck with correct shapes; negative: one case per diagnostic); C3-a-iv spec cases
still green (no regression on column scoping); `EXP_001` behavior unchanged for non-path chains.

## Phase S4 — read lowering (ttr-translator)

**Deliverables:** canonical path → `plan.v1` per the §8 table (Load/Filter/Join/Aggregate over
`md2db_*`; wide + long shapes; calc-map expressions from the catalog; hops → joins; the
**journaling read view** — R31 — wrapping every cubelet Load per its binding's mode). Conformance:
same fixture programs produce identical results on the PG and Polars engines via `ttrp-conform`.

**Pre-flight:** S3; MD Layer A phase 3 (bindings) done; TTR-P translator (Phase 3) done.
**DONE when:** end-to-end fixture — a `.ttrp` program reading `kaufland.sales.2025.net`-class
paths — compiles, emits, and runs with results matching hand-written SQL goldens on both engines;
translator lockstep tag policy respected (`kotlin-translator/v*`).

## Phase S5 — writeback

**Deliverables:** cubelet-assignment statement (grammar already carries `mdPath = expr` shape from
S0 — S5 adds semantics): strict-LHS validation (R19 / TTRP-MD-009), context overlay wiring (R20),
grain reconciliation — collapse / align / spread (R21 / TTRP-MD-010), lowering of pinned-grain
writes to Store + journaling modes, spread lowering **only** for strategies the binding declares
(R22); `+=` handling.

**Pre-flight:** S4. **DONE when:** write fixtures green end-to-end on PG (overwrite + invalidate
+ diff journaling; one spread case with a declared strategy; TTRP-MD-009/010 negatives);
round-trip test — write then read back through a dot-path returns the written values.

## Phase S5C — cubelet statements, materialization & journaling roles

**Deliverables:** the statement family (contracts §11): dispatch (R24), virtual cubelets +
session namespace in the resolver (R25), `:=` on existing and fresh targets with the `with`
clause and **generated-`.ttrm` emission** (R26/R27, MDS7), `+=` merge on grain keys (R28),
`-=` region delete (R29); journaling declaration + validation via `semantics{}` roles (R30,
MDS8), technical-column filling and the per-mode write/delete lowerings (R31); diagnostics
TTRP-MD-015…023; E2E script fixture (create → materialize → merge → delete → read back).

**Pre-flight:** S5; semantics-block feature merged & published (arc pre-flight); grammar S0
already carries the operators.
**DONE when:** the E2E script runs green on PG under all three journaling modes (minus `-=` on
diff, which must error); generated `.ttrm` emission is deterministic and idempotent (re-run
byte-identical); S5-B's binding-declared `valid` column usage is migrated to the role-based
declaration with no behavior change; virtual-cubelet dot-path reads pass.

## Phase S6 — member catalog, connected mode (can start after S2, parallel to S3–S5C)

**Deliverables:** `MemberCatalog`/`MemberSnapshot`/`MemberIndex` in ttr-metadata (§7.1) with
snapshot fingerprinting (pattern shared with optimizer stats — pre-flight review); serverless
backing = null-catalog (R13 path already tested in S2); `ttrm/getMemberDomains` +
`ttrm/getMembers` + `ttrm/getMaterializationStatus` (the dbt-ish drift report, D22) on
ttr-designer-server (§7.2), sourcing DISTINCT member values through the domain's `md2db`
binding, honoring `publish: members` (S0); client-side catalog in the frontend compile path;
degradation ladder (TTRP-MD-013); snapshot fingerprint + `asof` in the bundle manifest.

**Pre-flight:** S0, S2, ttr-metadata v1, optimizer-coordination review.
**DONE when:** integration spec — designer-server over a fixture repo + PG serves members; a
connected compile resolves bare `Kaufland`, the same compile disconnected demands the pair
(TTRP-MD-007); paging exercised past one page; manifest records fingerprint.

## Phase S7 — agent resolver service (`ttr-md-agent`; can start after S2, needs S6 to finish)

**Deliverables:** MCP server (Kotlin MCP SDK, streamable HTTP — follow the ai-platform example
per the planning-skill `EXAMPLES.md`; SDK source `~/Dev/view-only/kotlin-mcp-sdk`) exposing
`md_resolve`, `md_explain`, `md_list_members` (§9); raw-string splitting (quotes/braces aware);
DTO serialization pinned by spec (public contract).

**Pre-flight:** S2 (functional core), S6 (connected member lookups).
**DONE when:** Kotest integration spec drives the server over HTTP through all three tools incl.
an ambiguous case returning alternatives; a smoke script demonstrates the planning-agent loop
(tokens → alternatives → pick → resolved canonical path).

## Phase S8 — wrap-up

**Deliverables:** docs sync (design.md §11 status, control-room D-h register, CLAUDE.md if module
list changed, `dot-path-sugar.md` marked "implemented — see dot-path/"); full conformance sweep;
publish tags (`ttr-md-resolver`, semantics, translator lockstep as touched); arc review
(`review-NNN.md` + `tasks-review-NNN.md`, serial numbering).

**DONE when:** review findings closed or explicitly deferred with owners; both build domains and
conformance green from clean checkout.

---

## Deferred follow-ups (named seats, post-arc)

TS/LSP + Designer arc (hovers from `Explanation`, path highlighting); `with`-context-blocks
(D16); measure tuples (D12); per-expression `asof` (D17); safe navigation (D13); scientific
notation (R3); MOLAP lowering target; allocation-strategy library beyond declared bindings;
delete-markers for diff journaling (D25); materialized current-state views for diff-journaled
cubelets (read-cost seat, architecture §7).

## Changelog

- **v1 · 2026-07-08** — initial plan from architecture/contracts v1 drafts.
- **v1.1 · 2026-07-08** — cubelet-statements extension (D20–D26 / contracts v1.1): S0 carries
  `:=`/`+=`/`-=` + `cubeletStmt`/`withClause`; S4 gains the journaling read view (R31); new
  **Phase S5C** (statements, materialization, journaling roles) with the semantics-block feature
  as its pre-flight; S6 gains `ttrm/getMaterializationStatus`.
