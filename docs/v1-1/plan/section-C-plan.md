# Section C — execution plan (`.ttrg` parsing + LSP methods)

Planning note for C1 (`docs/v1-1/plan/tasks/C1-ttrg-parsing.md`) and C2
(`docs/v1-1/plan/tasks/C2-ttrg-lsp-methods.md`). Captures what's **already done**
(B4 overlap), the **decisions to make before coding**, the **sequence**, and a
few **carry-over tasks** (incl. review-032 task 4).

---

## 0. What's already done (B4 overlap — verify, don't rebuild)

C1's task list predates B4. B4 already shipped much of C1.2–C1.4:

- `DiagnosticCode` already has `GraphObjectsEmpty`, `GraphNameMismatch`,
  `GraphObjectNotFound`, `GraphLayoutStaleNode` (C1.2 ✅).
- `Validator.validateTtrgGraph` already emits **four** of C1.3's five rules
  (objects-empty, name-mismatch, object-not-found, layout-stale) and is wired
  into `publishDiagnostics`, gated on `.ttrg` (C1.3/C1.4 mostly ✅).
- The walker already populates `graph.objects` and `graph.layout.nodes`.

**Implication:** C1.3/C1.4 become *"verify + add the one missing rule"*, not
*"build graph-validator.ts from scratch"*. Do **not** extract a new
`graph-validator.ts` just to match the old plan text — extend the existing
`validateTtrgGraph` (it's already tested by the B7 fixtures). The genuinely-new
C1 work is **C1.5 `computeGraphEdges`** and **C1.6 fixture**, plus the layout-key
decision below.

---

## 1. Decisions to make before coding

### D1 — layout node-key syntax: **DECIDED → unquoted (Option B)**

Contract §7.1 showed `layout.nodes` keyed by **quoted** qnames, but the grammar
is `key : id` (`id : idPart (DOT idPart)*`) — it accepts only **unquoted** dotted
ids, and the current walker reads `key().getText()`. The quoted form does not
parse; this is why `graph-layout-stale-node.ttrg` was marked N/A in
review-030/031.

**Decision (user, 2026-05-20): use unquoted dotted-id keys.** No grammar change,
no antlr/TextMate regen, no ai-platform sync. Since "edges are computed" (§7.2),
`layout.edges` is always empty, so only node keys matter and qnames are valid
unquoted ids.

```
nodes: { billing.invoicing.er.entity.artikl: { x: 320, y: 180 } }
```

Concrete follow-through (folded into the sequence below):
- Amend contracts §7.1 to drop the quotes around node keys; bump the contract
  version (see CC3).
- Update `samples/broken/v1.1/graph-layout-stale-node.ttrg` to unquoted keys and
  **un-skip** its row in the B7 guardrail table (`integration.test.ts`).
- `setLayout` (C2.7) **emits** unquoted dotted-id keys.

> Feeds C1.3 (layout-stale reads node keys), C1.6 (fixture), C2.7 (setLayout
> emission).

### D2 — object resolution: exact `symbols.get` vs the resolver

`validateTtrgGraph` currently does `symbols.get(qname)` (exact, fully-qualified).
Confirm whether `.ttrg` `objects` are always fully-qualified (then exact lookup
is fine) or may be bare/wildcard-imported (then they must go through the
six-step resolver, as references do). Contract §7.1 uses fully-qualified objects
→ exact lookup is probably right, but **verify** and document. Affects C1.5 too
(edge endpoints must resolve the same way).

### D3 — `getModelGraph` vs `getGraph` overlap — **DECIDED**

**Decision (2026-05-21): keep both, they serve different purposes.**

- `getModelGraph` — whole-schema render (all defs of a given `schema`). Used by v1
  paths that need a complete schema view.
- `getGraph` — `.ttrg`-scoped render (only `objects` entries declared in one graph
  file). Used when rendering a specific graph.
- They **must share** the per-kind node/row builder — no duplication. Task A1 extracts
  `buildNodeForDef` that both call.

### D4 — `.ttrl` removal blast radius (C2.7) — **DECIDED**

**Decision (2026-05-21): remove the `.ttrl` sidecar.**

- Layout canonical location is now the `.ttrg`'s `layout` block.
- The project-wide `<root>/.modeler/layout.ttrl` path is deleted.
- The `getLayout`/`setLayout`/`exportLayout` `.ttrl` branches are removed.
- The architecture invariant "text is canonical / layout is sidecar" is updated to
  reflect that layout is now inside the `.ttrg` (CC2).
- If D/E need layout persistence between sessions they will read/write it via
  `getLayout`/`setLayout` using the `.ttrg` file — not a separate sidecar.

---

## 2. Execution sequence

**C1 (do first; ~1 day given B4 overlap):**

1. **D1 decision** (above) — resolve before touching layout.
2. C1.1 — confirm `parseString`/`parseFile` distinguish `.ttr`/`.ttrg` by
   extension; add the `kind` hint only if a real caller needs it (YAGNI-check —
   `Document.graph` already disambiguates).
3. C1.3 — add the **only missing rule**: `schema` required + valid enum
   (`db|er|map|query|cnc`) → reuse/extend `validateTtrgGraph`. Verify the other
   four rules against fully-qualified qnames (D2).
4. C1.5 — **`computeGraphEdges(graph, projectSymbols)`** (the real new logic):
   for each relation/fk in `objects`, include the edge iff both endpoints are
   also in `objects`. Reuse v1 edge-derivation from `model-graph.ts`; add unit
   tests per C1's `graph-resolve.test.ts` (2 nodes+1 edge / omit relation→0
   edges / omit endpoint→0 edges).
5. C1.6 — hand-author `samples/.../graphs/*.ttrg` + companion `.ttr` (with
   `package` decls) so objects resolve; add to the B7 guardrail table.

**C2 (after C1; the bulk — ~3 days, 8 sub-tasks):**

6. C2.6 — `WorkspaceEdit` builders in `packages/edit/src/graph-edits.ts`
   (`buildAddObjectEdit`, `buildRemoveObjectEdit`, `buildCreateGraphContent`).
   First real user of `@modeler/edit`; CST-aware (indentation/trailing comma).
7. C2.1–C2.5 — the five read/edit methods (`listGraphs`, `getGraph`,
   `addObjectToGraph`, `removeObjectFromGraph`, `createGraph`,
   `getPackageGraph`) in a new `graph-methods.ts`; register in **both**
   server entry points.
8. C2.7 — update `getModelGraph`/`getLayout`/`setLayout`/`exportLayout` to take
   `graphUri`; remove `.ttrl` paths (per D3/D4). `setLayout` emits node keys in
   the D1-chosen syntax.
9. C2.8 — Designer `LspClient` typed wrappers; re-export request/response types
   from `@modeler/lsp` (no duplication).
10. Integration tests (`lsp-v1.1-graph-methods.test.ts`) + unit
    (`graph-builders.test.ts`) per C2's tests-first list.

---

## 3. Carry-over / cross-cutting tasks (tracked here explicitly)

- [ ] **CC1 — (review-032 task 4) Fix the TextMate property-keyword
  highlighting gap.** The generator (`packages/vscode-ext/scripts/generate-tm-grammar.ts`)
  emits a `keywords` repository entry that `include`s `#keyword_other_property_ttr`
  (and `#keyword_control_def_ttr`, etc.), but **never emits those repository
  blocks** — the includes are dangling, so *no* property keyword is highlighted
  (`search`, `fuzzy`, `searchable`, and the graph keywords `graph`, `objects`,
  `layout`, `nodes`, `schema`). Pre-existing; surfaced by the search-block
  feature's smoke-test and directly relevant to Section C/D (the `.ttrg`
  keywords won't highlight either). Fix the generator to emit the referenced
  repository blocks, regenerate `ttr.tmLanguage.json` + the `.js` artifact, and
  verify `graph`/`objects`/`layout`/`search`/`fuzzy` all scope in the Extension
  Dev Host. *(Naturally belongs to Section D "vscode ext", but tracked here at
  the user's request so it isn't lost.)*

- [ ] **CC2 — Architecture-doc invariant update (paired with C2.7/D4).** The
  "Text is canonical; layout is sidecar `.modeler/layout.ttrl`" invariant in
  `CLAUDE.md` / `docs/v1/design/architecture.md` is being reversed (layout now
  lives in the `.ttrg`). Update both when C2.7 lands.

- [ ] **CC3 — Contract version bump.** Whichever way D1 goes, amend contracts §7
  (and §8 if D3 changes method surface) and bump the contract version per the
  amendment discipline.

---

## 4. Open risk

C2 is the largest single task in v1.1 (3 days, first real `@modeler/edit` usage,
8 sub-tasks, touches both server entry points + Designer). Suggest splitting the
review into **C1**, **C2-read** (listGraphs/getGraph/getPackageGraph + the
updated read methods), and **C2-write** (the three `WorkspaceEdit` builders +
add/remove/create) so each lands and is reviewed independently rather than as
one giant changeset.
