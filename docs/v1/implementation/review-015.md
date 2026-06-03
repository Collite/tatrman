# Review 015 — Phase 3 Section E (Inspector panel)

**Scope:** verify the developer's claim that Section E (Inspector panel) is ready, per `docs/plan/phase-03/E-inspector.md`.
**Verdict:** **Not done.** The LSP-side work (E.1, E.2) is solid and already verified end-to-end by integration test 4.4. The Designer-side work (E.3, E.4, E.5) is partial: the panel renders most of the spec, but the three interactive behaviours the plan explicitly required — *clickable source link*, *clickable "Related symbols" list*, *edge + background tap* — are missing. The plan-mandated **tests-first** artefacts for §E (LSP unit test, RTL component test) **do not exist**; the only thing keeping the suite green is unrelated coverage.
**Suite state:** designer 42, lsp 38, integration 22 — all green, but none of these tests exercise §E. Progress doc still shows E.1–E.5 as `[ ]`.

---

## What's resolved

### E.1 — `buildSymbolDetail` in the LSP ✅ (implementation only)

`packages/lsp/src/model-graph.ts:240-328` implements `buildSymbolDetailForDef`:

- Localizes `label` via `getDisplayLabel(def, preferredLang)` with fallback to `def.name`.
- Discriminates `perKindData` for **all** plan-listed kinds: `table` / `view` / `entity` / `fk` / `relation` / `role` / `other`.
- Sets `isNameAttribute` / `isCodeAttribute` on entity attribute rows (`model-graph.ts:289-290`).
- `referencedBy` is populated from `refIndex.findByQname(qname)`.
- `findDefByQname` only handles top-level defs (documented at `model-graph.ts:357-363`) — this v1 limitation is consciously accepted and covered by integration test 4.6.

### E.2 — Handler registration ✅

`packages/lsp/src/server.ts:381` registers `modeler/getSymbolDetail`, threading the manifest, `projectSymbols`, `resolver`, `refIndex`, and a document getter/parser. End-to-end coverage:

- `tests/integration/src/lsp-phase-03-custom-methods.test.ts:121-152` (4.4) — `er.entity.artikl` returns non-null with Czech label, non-null description, `perKindData.kind === 'entity'`, populated `attributes`, and non-empty `referencedBy` (the `er2db_entity` mapping from `map.ttr`).
- Test 4.6 locks in the deliberate column-qname → null behaviour.

This is the single piece of §E that's verifiably done.

### Reducer + client wiring ✅ (data layer of E.3)

- `designer-reducer.ts:13,68-75` handles `storeSymbolDetail`.
- `lsp-client.ts:70-72` exposes `getSymbolDetail(qname)`.

---

## What's broken or missing

### F1 — Detail-fetch is in the click handler, not an effect (deviates from E.3)

The plan (`E-inspector.md:36`) is explicit:

> In `App.tsx`, an **effect on `state.selectedSymbol`** calls `getSymbolDetail` (only if not cached in `state.symbolDetails`), then dispatches `storeSymbolDetail`.

The implementation (`App.tsx:57-64`) inlines this in `handleNodeSelect`:

```ts
const handleNodeSelect = async (qname: string) => {
  dispatch({ type: 'selectSymbol', qname });
  const client = clientRef.current;
  if (!client) return;
  if (state.symbolDetails[qname]) return;
  const detail = await client.getSymbolDetail(qname);
  if (detail) dispatch({ type: 'storeSymbolDetail', detail });
};
```

Problems:
1. **`state.symbolDetails[qname]` is read from the closure**, captured at render-time. After fast successive selections the closure can be stale — and the dispatched `selectSymbol` for the *next* qname has already changed `selectedSymbol`, so the late-arriving `storeSymbolDetail` may be irrelevant (or, with the same qname, no-op).
2. **Only fires from the Canvas tap path.** Once F3 below lands and the "Related symbols" buttons start dispatching `selectSymbol` directly, *those* selections will never trigger a fetch — the user clicks an item, the qname changes, but no detail loads.

Move the fetch into an effect keyed on `state.selectedSymbol?.qname`. Read `state.symbolDetails` fresh inside the effect body (or pass it via deps and accept the re-fire — the inner `if cached` guard makes the cost zero).

### F2 — Source `file:line` is text, not a clickable copy-to-clipboard control (E.4 gap)

Plan (`E-inspector.md:37`):

> source `file:line` (clickable; on click, `navigator.clipboard.writeText(`<file>:<line>`)` plus a toast "Copied").

Implementation (`InspectorPanel.tsx:175-180`) renders the source as a plain `<p>`:

```tsx
<Section label="Source">
  <p className="text-xs text-gray-500 font-mono">
    {detail.sourceUri.split('/').pop()}
    <span className="text-gray-400 ml-1">:{detail.sourceLine}</span>
  </p>
</Section>
```

No `<button>`, no `onClick`, no `navigator.clipboard.writeText`, no toast. This is one of the visible-behaviour pieces of §E and it's just not there.

### F3 — "Related symbols" is read-only text, not clickable (E.4 gap)

Plan (`E-inspector.md:37`):

> "Related symbols" list at the bottom; each item is a **button** that dispatches `selectSymbol`.

Implementation (`InspectorPanel.tsx:126-138`) renders each item as a `<span>`:

```tsx
<li key={item.qname} className="text-xs">
  <span className="font-mono text-sky-600">{item.qname}</span>
  <span className="text-gray-400 ml-1">:{item.sourceLine}</span>
</li>
```

No buttons, no `onClick`, no dispatcher prop wired through. The plan's RTL test case "clicking a row in 'Related symbols' dispatches `selectSymbol` with that qname" cannot pass against this DOM, and the manual demo-path check "click a 'Related symbols' entry; selection shifts" cannot pass either.

This is also the case that **F1** silently breaks: even if we wire up the buttons, the detail won't auto-fetch because the fetch lives in the Canvas tap handler.

### F4 — Canvas selection only fires on nodes; no edge tap, no background tap (E.5 gap)

Plan (`E-inspector.md:38`):

> Cytoscape's `cy.on('tap', 'node, edge', evt => dispatch({ type: 'selectSymbol', qname: evt.target.data('qname') }))`. Background tap clears selection (`'selectSymbol', qname: null`).

Implementation (`Canvas.tsx:127-130`) handles `'tap', 'node'` only:

```ts
cy.on('tap', 'node', (evt: CytoscapeInstance) => {
  const data = evt.target.data();
  onNodeSelectRef.current(data['qname'] as string);
});
```

- **Tapping a relation edge does nothing.** Yet `perKindData` already supports `kind: 'relation'` and `kind: 'fk'`, and the inspector knows how to render them — they're just unreachable from the UI.
- **Tapping the empty background doesn't clear selection.** The inspector keeps showing the previously-selected node's details until something else is tapped.

The signature `(qname: string) => void` in `CanvasProps` can't carry `null`, so the prop type also needs to widen to `(qname: string | null) => void` (and `handleNodeSelect` accepts the same).

### F5 — Plan-mandated tests do not exist (tests-first discipline broken)

Plan (`E-inspector.md:11-26`) specified three test files, with explicit assertion lists. None exist:

| Plan file | Status |
|---|---|
| `packages/lsp/src/__tests__/symbol-detail.test.ts` | ✗ missing |
| `tests/integration/src/symbol-detail.test.ts` | ✗ missing (the assertions partially live in `lsp-phase-03-custom-methods.test.ts:121` — acceptable substitution, see N1 below) |
| `packages/designer/src/components/__tests__/InspectorPanel.test.tsx` | ✗ missing |

The seven LSP unit-test cases the plan demanded (entity 2 attrs, table with PK and 3 cols, cs/de localization, null-description fallback, unknown qname → null, refIndex with 2 refs) are not covered anywhere — the integration test only covers one happy path.

Given that the developer claims §E is done, the lack of these tests is itself the strongest signal that §E is not done.

### F6 — Progress doc is wrong

`docs/plan/progress-phase-03.md:50-54` shows **all five E.* boxes as `[ ]`**, but the claim is "ready". This is the same `[x]` ⇄ `[ ]` ⇄ reality drift that auto-memory's `feedback-progress-doc-skepticism` warned about — verify, don't trust.

---

## Notes (lower priority)

### N1 — Integration test 4.4 substitutes for `tests/integration/src/symbol-detail.test.ts`

The existing test 4.4 covers the spirit of the plan's integration assertion (entity 4.4 against `samples/v1-metadata/`). If you're happy keeping integration cases in `lsp-phase-03-custom-methods.test.ts` rather than a per-feature file, no need to split — but document that decision in `phase-03-contracts.md`'s changelog or in the §E plan, so the next reviewer doesn't flag it again.

### N2 — `findDefByQname` re-parses on every `getSymbolDetail` call

`model-graph.ts:364-386` invokes `parseDocument(content, uri)` afresh on every request. Plan E.2: "Cache the latest parsed AST per document in the existing document cache so the handler doesn't reparse." With v1-metadata's `er.ttr` (~1400 lines) every inspector click triggers a parse on the worker thread. Not blocking for v1 but worth noting — when the user clicks several entities in a row you'll see CPU spike on the worker. Either implement the AST cache, or call this out as a deferred optimization.

### N3 — Relation cardinality is shown using internal enum names

`InspectorPanel.tsx:111` renders `[${perKindData.fromCardinality}–${perKindData.toCardinality}]`. The values are `'one' | 'zero-or-one' | 'many' | 'one-or-many'` — readable, but inconsistent with the on-canvas glyph (which is a visual). Consider mapping back to the `.ttr`-source form (`"1"`, `"0..*"`, etc.) for the inspector text, or rendering a mini-glyph next to the cardinality.

### N4 — Two slight wording differences vs the plan

- Empty state: plan said "Nothing selected"; code says "Select a node to see its details." Cosmetic — keep the longer one, it's more actionable.
- Section header: plan said "Related symbols"; code says "Referenced By". The semantics are the same (refIndex pointing in); pick one and use it consistently in tests and docs.

---

## Severity summary

| # | Finding | Severity | Blocks §E? |
|---|---|---|---|
| F1 | Detail-fetch in click handler, not effect | **High** | Yes — blocks F3 |
| F2 | Source `file:line` not clickable, no toast | **High** | Yes — visible E.4 gap |
| F3 | "Related symbols" entries not clickable | **High** | Yes — visible E.4 gap |
| F4 | Canvas: no edge tap, no background tap | **High** | Yes — E.5 partially done |
| F5 | Plan-mandated tests do not exist | **High** | Yes — tests-first discipline |
| F6 | Progress doc unchecked but claimed done | Medium | No, but signals process drift |
| N1 | Integration test split deferred | Low | No |
| N2 | `findDefByQname` re-parses on every call | Low | No — v1 optimization |
| N3 | Cardinality enum displayed verbatim | Low | No |
| N4 | Empty-state / section-header wording | Trivial | No |

See `tasks-review-015.md` for the actionable checklist.
