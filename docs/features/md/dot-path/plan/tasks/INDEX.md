# MD dot-path sugar — task management

**Companion to** [`../implementation-plan.md`](../implementation-plan.md) (phases, pre-flight,
DONE) · [`../../contracts.md`](../../contracts.md) (rules R1–R23, cited everywhere below) ·
[`../../architecture.md`](../../architecture.md) (MDS1–MDS6).

## Rules for the coder (read before every session)

1. **Check every checkbox the moment its task is done** — here in the INDEX for finished lists,
   and inside the list for finished tasks. Never batch checkbox updates.
2. **TDD is not optional.** Every list is ordered: spec/fixture tasks (red) come before
   implementation (green). Confirm red before implementing; confirm green before moving on.
3. Every list ends with the repo gates. TS domain: `pnpm -r typecheck && pnpm -r lint &&
   pnpm -r build && pnpm -r test`. Kotlin domain: `./gradlew build`. Grammar-touching lists also
   run the conformance harness.
4. Record surprises in the list's **Coder notes** section — reviews read them.
5. Do one list per PR/commit-series; commit style `md-sugar S<n><letter>: <description>`.

## Structure & order

Serial S0→S5→S5C; S6 may start any time after S2 (parallel to S3–S5C); S7 needs S2 and S6;
S8 last. S5C additionally requires the **semantics-block feature** (grammar 4.2) merged &
published (arc pre-flight).

### Phase S0 — grammar version
- [ ] [S0-A — NUMBER audit & guard fixtures](S0-A-number-audit-fixtures.md)
- [ ] [S0-B — grammar change, regeneration, version cut](S0-B-grammar-change.md)

### Phase S1 — Kotlin MD semantics port
- [ ] [S1-A — MdModel & grain lattice](S1-A-md-model-lattice.md)
- [ ] [S1-B — defaults, calc catalog, TS parity](S1-B-defaults-catalog-parity.md)

### Phase S2 — resolver core
- [ ] [S2-A — module scaffold, classification, qualified pairs](S2-A-module-classification.md)
- [ ] [S2-B — constraint search, ambiguity, defaults](S2-B-search-ambiguity.md)
- [ ] [S2-C — canonical form, calc/asof, disconnected, context, explanation](S2-C-canonical-context-disconnected.md)

### Phase S3 — TTR-P read integration
- [ ] [S3-A — recognition, precedence, asof plumbing](S3-A-recognition-precedence.md)
- [ ] [S3-B — shape typing, broadcast, diagnostics roster](S3-B-typing-diagnostics.md)

### Phase S4 — read lowering
- [ ] [S4-A — canonical path → plan.v1](S4-A-read-lowering.md)
- [ ] [S4-B — end-to-end engine conformance](S4-B-conformance.md)

### Phase S5 — writeback (slices)
- [ ] [S5-A — strict LHS, context overlay, grain reconciliation](S5-A-strict-lhs-context.md)
- [ ] [S5-B — write lowering & journaling](S5-B-write-lowering.md)

### Phase S5C — cubelet statements, materialization & journaling roles
- [ ] [S5C-A — statements: dispatch, virtual cubelets, merge/delete](S5C-A-cubelet-statements.md)
- [ ] [S5C-B — materialization, generated model, journal roles](S5C-B-materialize-journal-roles.md)

### Phase S6 — member catalog (parallel-capable after S2)
- [ ] [S6-A — MemberCatalog library in ttr-metadata](S6-A-catalog-library.md)
- [ ] [S6-B — ttrm/* protocol & compile-path client](S6-B-protocol-client.md)

### Phase S7 — agent resolver service
- [ ] [S7-A — ttr-md-agent MCP server](S7-A-mcp-service.md)

### Phase S8 — wrap-up
- [ ] [S8-A — docs sync, publish, arc review](S8-A-wrap-up.md)
