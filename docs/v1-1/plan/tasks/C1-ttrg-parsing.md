# 1.1.C.1 — `.ttrg` parsing, validation, edge inclusion

**Goal:** `.ttrg` files parse via the same parser as `.ttr`; the validator enforces graph-block invariants; the resolver computes the edge set from the explicit object list using the "edges-are-computed" rule.

**Reads:** [contracts §7 (.ttrg shape)](../../design/v1-1-contracts.md#7-ttrg-graph-file-shape), [contracts §2 (GraphBlock AST)](../../design/v1-1-contracts.md#2-ast-additions), `packages/parser/src/walker.ts`, `packages/semantics/src/validator.ts`, `packages/lsp/src/model-graph.ts`. Planning context: [`docs/v1-1/plan/section-C-plan.md`](../section-C-plan.md).
**Blocked by:** 1.1.B.4.
**Blocks:** C2 (LSP methods consume the parsed Graph), E (Designer renders from C2).
**Estimated time:** ~1 day (was 2; B4 already shipped most of the validation — see Status).

---

## Status & decisions (2026-05-20) — READ FIRST

This task list predates B4. **B4 already shipped much of the original C1 plan**, so several steps below are now *verify*, not *build*:

- ✅ **Diagnostic codes already exist** in `packages/parser/src/diagnostics.ts`: `GraphObjectsEmpty`, `GraphNameMismatch`, `GraphObjectNotFound`, `GraphLayoutStaleNode` (original C1.2 — done).
- ✅ **`Validator.validateTtrgGraph(uri, ast)` already exists** and emits **4 of the 5** §7.3 rules (objects-empty, name-mismatch, object-not-found, layout-stale). It's wired into the LSP `publishDiagnostics` flow (gated on `.ttrg`) and covered by the B7 fixtures in `tests/integration/src/integration.test.ts`.
- ⛔ **Do NOT create a new `graph-validator.ts`.** The old C1.3/C1.4 said to — that's superseded. **Extend the existing `validateTtrgGraph`** instead; it's already tested and wired. Adding a parallel validator would duplicate and drift.
- ❗ **The only missing §7.3 rule is `schema` required.** Add it (see C1.3).

**Decision D1 — layout node keys are UNQUOTED dotted ids.** Contract §7.1 originally showed quoted qname keys (`"a.b.er.entity.X": {...}`), but the grammar's `key : id` accepts only unquoted dotted ids, and the walker reads `key().getText()`. We are **keeping the grammar as-is** (no `.g4` change, no antlr/TextMate regen, no ai-platform sync) and **amending the contract** to unquoted keys:

```
layout: { nodes: { billing.invoicing.er.entity.artikl: { x: 320, y: 180 } } }
```

This is why `samples/broken/v1.1/graph-layout-stale-node.ttrg` was marked N/A — it used quoted keys. Part of this task is to fix that fixture and un-skip its guardrail row (C1.6).

**Decision D2 (verify during C1.3) — object resolution.** `validateTtrgGraph` currently resolves objects via `symbols.get(qname)` (exact, fully-qualified). Contract §7.1 objects are fully-qualified, so exact lookup is expected to be correct — **confirm** that fully-qualified package qnames (e.g. `billing.invoicing.er.entity.artikl`) actually resolve via `symbols.get`, and document the finding. If `.ttrg` objects may ever be bare/wildcard-imported, route them through the six-step `Resolver` instead. The same resolution path must be used by `computeGraphEdges` (C1.5).

---

## Tests-first

- [ ] `packages/parser/src/__tests__/ttrg-parse.test.ts` — new file. Cases:
  - Hand-authored `.ttrg` fixture (mini, 2 entities, 1 relation) parses to a `Document` with `graph` populated and `definitions === []`.
  - `.ttrg` file with no `graph` block → `ttr/wrong-file-kind` (Error). *(Verify; B-section behaviour.)*
  - `.ttr` file with a `graph` block → `ttr/wrong-file-kind` (Error). *(Already verified in `integration.test.ts`; assert at parser level here too.)*
  - A `.ttrg` `layout` with **unquoted** dotted-id node keys parses and populates `graph.layout.nodes` keyed by the dotted qname (regression guard for D1).
- [ ] **Extend** `packages/semantics/src/__tests__/diagnostics-v1.1.test.ts` (the existing graph cases live here — do **not** start a new `graph-validator.test.ts`). Add the one missing case:
  - `graph X { objects: [...] }` with **no `schema`** → the schema-required diagnostic (C1.3).
  - (The other four rules — objects-empty, name-mismatch, object-not-found, layout-stale — already have cases; confirm they still pass, and that layout-stale uses **unquoted** keys.)
- [ ] `packages/lsp/src/__tests__/graph-resolve.test.ts` — new file. Cases for the edge-inclusion rule (C1.5):
  - `objects: [A, B, R]` where `R` is a relation with `from: A, to: B` → 2 nodes, 1 edge.
  - `objects: [A, B]` (relation `R` omitted) → 2 nodes, **0 edges** ([contracts §7.2](../../design/v1-1-contracts.md#72-edge-inclusion-semantics-open-question-6-resolution)).
  - `objects: [A, R]` (B omitted) → 1 node, 0 edges (edge needs both endpoints).

## Library reference

`.ttrg` shares the parser with `.ttr` — no new parsing library. Cytoscape rendering happens later (E1–E4); this task stops at producing the model graph JSON. For edge derivation, reuse the v1 relation/fk-edge logic already in `packages/lsp/src/model-graph.ts` (don't reinvent it).

## Implementation tasks

- [ ] **C1.1 — Confirm `.ttr`/`.ttrg` dispatch; add a `kind` hint only if a caller needs it (YAGNI).** `parseString(content, fileLabel)` already parses both; `Document.graph` vs `Document.definitions` already disambiguates file kind. **Do not add a `kind` parameter unless a concrete caller requires it** — the original C1.1 over-specified this. If you find such a caller (e.g. C2 needs to reject a `.ttr` opened as a graph), add it then, minimally.
- [ ] **C1.2 — DONE (verify only).** The four graph codes are already in `diagnostics.ts`. No new codes needed unless C1.3's schema rule warrants one (see below).
- [ ] **C1.3 — Add the missing `schema`-required rule to `validateTtrgGraph`.** When `graph.schema` is absent, emit an Error. Reuse `DiagnosticCode.RequiredPropertyMissing` (message e.g. `"graph requires a 'schema' property"`) unless you prefer a dedicated `ttr/graph-schema-missing` code — if you add one, amend contracts §6 and bump the version (CC3). **Invalid** schema values (`schema: xyz`) are already a *parse* error because `schemaCode` is a fixed token set — **verify** that token set matches contract §7.3's enum `db|er|map|query|cnc` (note `query`), and if it doesn't, that's a grammar fix to flag separately. Also verify the existing four rules against fully-qualified qnames (D2).
- [ ] **C1.4 — DONE (verify only).** `validateTtrgGraph` is already called from the LSP `publishDiagnostics` path (gated on `.ttrg`). Confirm the new schema rule flows through. No new wiring.
- [ ] **C1.5 — Implement edge-inclusion computation (the real new work).** New helper `computeGraphEdges(graph: GraphBlock, projectSymbols: ProjectSymbolTable): ModelGraphEdge[]` (place in `packages/lsp/src/model-graph.ts` next to the v1 edge logic, or a sibling module it imports). For each qname in `objects` that is a relation/fk, include the edge **iff both endpoint qnames are also in `objects`**; otherwise skip. Reuse the v1 endpoint-extraction from `model-graph.ts`. Used by `modeler/getGraph` (C2.2). Cover with the `graph-resolve.test.ts` cases above.
- [ ] **C1.6 — Fixtures + un-skip the layout guardrail.**
  - Hand-author a positive `.ttrg` fixture plus companion `.ttr` files (with `package` declarations so objects resolve). Suggested home: `samples/v1.1-mini/graphs/artikl_overview.ttrg` (+ the `.ttr`s it references). Header comment: `// hand-authored fixture for 1.1.C; eventual home is the migrated samples in 1.1.G`.
  - **Fix `samples/broken/v1.1/graph-layout-stale-node.ttrg` to use unquoted dotted-id node keys** (per D1) so it parses and actually emits `ttr/graph-layout-stale-node`, then **un-skip** its row in the B7 table (`tests/integration/src/integration.test.ts` → `describe('v1.1 broken fixture diagnostics')`; it's currently an `it.skip`). Update `samples/broken/v1.1/README.md` to drop the N/A note for this fixture.
- [ ] **C1.7 — Amend contracts §7.1 (CC3).** Change the `layout.nodes` example from quoted to unquoted dotted-id keys; add a changelog entry and bump the contract version per the amendment discipline.

## Verify by running

```bash
pnpm --filter @modeler/parser test
pnpm --filter @modeler/semantics test
pnpm --filter @modeler/lsp test
pnpm --filter @modeler/integration-tests test   # incl. the un-skipped layout-stale row
pnpm -r typecheck
```

All new/extended test files green; existing tests still pass; the B7 guardrail now covers `graph-layout-stale-node` (no longer skipped).

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] `.ttrg` files parse cleanly; `Document.graph` is populated; unquoted dotted-id layout keys parse into `graph.layout.nodes`.
- [ ] `validateTtrgGraph` enforces **all five** §7.3 rules (the four from B4 + the new `schema`-required).
- [ ] `computeGraphEdges` implements the edges-are-computed-from-objects rule, covered by `graph-resolve.test.ts`.
- [ ] `graph-layout-stale-node.ttrg` is unquoted, parses, emits its code, and its B7 row is un-skipped and green.
- [ ] Contracts §7.1 amended to unquoted keys and version-bumped (C1.7 / CC3).
- [ ] No LSP method changes yet — C2 wraps the parsed/validated graph in custom LSP methods.

---

## Cross-cutting carry-over (tracked here per request — can be done any time in C/D)

- [ ] **CC1 — (review-032 task 4) Fix TextMate property-keyword highlighting.** `packages/vscode-ext/scripts/generate-tm-grammar.ts` emits a `keywords` repository entry that `include`s `#keyword_other_property_ttr` (and `#keyword_control_def_ttr`, etc.) but **never emits those repository blocks** — the includes are dangling, so **no property keyword is highlighted**, including the graph keywords this section introduces (`graph`, `objects`, `layout`, `nodes`, `schema`) and the search keywords (`search`, `fuzzy`, `searchable`). Fix the generator to emit the referenced repository blocks, run `pnpm --filter @modeler/vscode-ext run regen-tmgrammar`, and verify in the Extension Dev Host (F5) that those keywords scope. *(Strictly a Section D concern, but flagged here so it isn't lost; see `docs/v1-1/implementation/review-032.md` task 4 / `tasks-review-032.md` §4.)*
