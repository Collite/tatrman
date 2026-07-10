# Tasks — Review 015 (Phase 3 Section E)

The LSP-side work is done. The Designer-side panel renders ~80% of the spec but is missing three interactive behaviours and all of the §E test coverage. Work through the tasks in order — F1 unblocks F3; F5 is best done alongside its sibling fix so the assertion is concrete.

---

## F1 — Move the detail fetch from the click handler to an effect

- [ ] Open `packages/designer/src/App.tsx`.
- [ ] Shrink `handleNodeSelect` so it only dispatches selection (no async, no fetch). Pass the qname through unchanged. Allow `null` (background-tap from F4 below):
  ```ts
  const handleNodeSelect = (qname: string | null) => {
    dispatch({ type: 'selectSymbol', qname });
  };
  ```
- [ ] Add a new `useEffect` keyed on `state.selectedSymbol?.qname` that fetches the detail if it isn't already cached:
  ```ts
  useEffect(() => {
    const qname = state.selectedSymbol?.qname;
    if (!qname) return;
    if (state.symbolDetails[qname]) return;
    const client = clientRef.current;
    if (!client) return;
    let cancelled = false;
    client.getSymbolDetail(qname).then((detail) => {
      if (cancelled || !detail) return;
      dispatch({ type: 'storeSymbolDetail', detail });
    }).catch((err) => {
      if (cancelled) return;
      dispatch({ type: 'setError', message: String(err) });
    });
    return () => { cancelled = true; };
  }, [state.selectedSymbol?.qname, state.symbolDetails, clientRef]);
  ```
  Note `clientRef` is a `useRef`; using it in deps is a no-op (ref identity is stable). Including it is harmless and signals intent. If your lint rule complains, omit it.
- [ ] Widen `CanvasProps.onNodeSelect` in `Canvas.tsx` to `(qname: string | null) => void` so F4 can dispatch `null` for background taps.
- [ ] Run `pnpm --filter @modeler/designer test` — should stay green.

## F2 — Source `file:line` becomes a clickable copy-to-clipboard control

- [ ] Open `packages/designer/src/components/InspectorPanel.tsx`.
- [ ] Replace the `<p>` in the `Source` section with a `<button>`:
  ```tsx
  <Section label="Source">
    <button
      type="button"
      onClick={() => {
        const text = `${detail.sourceUri.replace(/^file:\/\//, '')}:${detail.sourceLine}`;
        navigator.clipboard.writeText(text);
        setCopiedToast(true);
        setTimeout(() => setCopiedToast(false), 1200);
      }}
      className="text-xs text-sky-600 hover:text-sky-700 font-mono underline-offset-2 hover:underline"
    >
      {detail.sourceUri.split('/').pop()}
      <span className="text-gray-400 ml-1">:{detail.sourceLine}</span>
    </button>
  </Section>
  ```
- [ ] Add local `useState`:
  ```ts
  const [copiedToast, setCopiedToast] = useState(false);
  ```
  and render the toast inline beneath the source button (or anywhere visible inside the panel):
  ```tsx
  {copiedToast && (
    <p className="text-xs text-emerald-600 mt-1" role="status">Copied</p>
  )}
  ```
- [ ] Import `useState` from React if it isn't already.
- [ ] Verify in the dev server: clicking the source line copies the `<absolute-path>:<line>` string to the clipboard and a "Copied" notice flashes for ~1.2s.

## F3 — "Related symbols" rows become buttons that re-dispatch selection

- [ ] Still in `InspectorPanel.tsx`. Add an `onSelect` prop:
  ```ts
  interface InspectorPanelProps {
    selectedSymbol: { qname: string } | null;
    symbolDetails: Record<string, /* ... */>;
    onSelect: (qname: string) => void;
  }
  ```
- [ ] In `ReferencedBy`, accept the same `onSelect` and turn each row into a button:
  ```tsx
  function ReferencedBy({ items, onSelect }: {
    items: Array<{ qname: string; sourceUri: string; sourceLine: number }>;
    onSelect: (qname: string) => void;
  }) {
    if (items.length === 0) return <p className="text-xs text-gray-400">None</p>;
    return (
      <ul className="space-y-1">
        {items.map((item) => (
          <li key={item.qname}>
            <button
              type="button"
              onClick={() => onSelect(item.qname)}
              className="block w-full text-left text-xs hover:bg-sky-50 px-1 py-0.5 rounded"
            >
              <span className="font-mono text-sky-600">{item.qname}</span>
              <span className="text-gray-400 ml-1">:{item.sourceLine}</span>
            </button>
          </li>
        ))}
      </ul>
    );
  }
  ```
- [ ] In `App.tsx`, pass `handleNodeSelect` as `onSelect` to `<InspectorPanel>`:
  ```tsx
  <InspectorPanel
    selectedSymbol={state.selectedSymbol}
    symbolDetails={state.symbolDetails}
    onSelect={handleNodeSelect}
  />
  ```
- [ ] Manual check: click any node in the canvas, observe the inspector populates with "Referenced By" items; click one item, the selected qname shifts AND (because of F1's effect) the next detail loads automatically.

## F4 — Canvas selection covers edges and background clears

- [ ] Open `packages/designer/src/components/Canvas.tsx`.
- [ ] Replace the existing tap handler (`Canvas.tsx:127-130`):
  ```ts
  cy.on('tap', 'node, edge', (evt: CytoscapeInstance) => {
    const data = evt.target.data();
    onNodeSelectRef.current(data['qname'] as string);
  });
  cy.on('tap', (evt: CytoscapeInstance) => {
    // tap on the background (cy itself, not on a node/edge) → clear selection
    if (evt.target === cy) onNodeSelectRef.current(null);
  });
  ```
- [ ] Confirm `onNodeSelect` signature in `CanvasProps` is `(qname: string | null) => void` (already widened by F1).
- [ ] Manual check:
  - Tap a relation edge: inspector populates with `perKindData.kind === 'relation'`, showing `fromQname [from-card–to-card] toQname`.
  - Tap empty canvas: inspector reverts to the empty state.
  - Tap an FK edge in `db` schema: inspector shows `perKindData.kind === 'fk'`.

## F5 — Plan-mandated tests

### F5.a — LSP unit test (`packages/lsp/src/__tests__/symbol-detail.test.ts`)

- [ ] Create the file. One `describe('buildSymbolDetail', ...)` block; one `it(...)` per case below. Use the same in-memory test pattern as `model-graph.test.ts` — parse a small `.ttr` string with `parseString`, build a `ProjectSymbolTable`, `Resolver`, `ReferenceIndex`, `ResolvedManifest`, then call `buildSymbolDetail`. The plan's exact list (`E-inspector.md:11-18`):
  1. Entity with two attributes → `perKindData.kind === 'entity'`, `perKindData.attributes.length === 2`.
  2. Table with `primaryKey: ['id']` and three columns → `perKindData.kind === 'table'`, `perKindData.primaryKey === ['id']`, `perKindData.columns.length === 3`.
  3. `preferredLanguage = 'cs'` + `displayLabel: { cs: 'Artikl', en: 'Item' }` → `label === 'Artikl'`.
  4. `preferredLanguage = 'de'`, same input → fallback to `'Item'` (en) — **but** note: current code falls back to `name` (per contract amendment v5), not to `en`. **Pick one** before writing the test: either change the test to lock in current behaviour (`label === 'foo'`) and update the plan-doc line to match, or change `getDisplayLabel` to fall back to `en` and then `name`, then write the plan-spec test. Recommend keeping current behaviour and updating the plan.
  5. No `description` → `detail.description === null`.
  6. Unknown qname → returns `null`.
  7. `refIndex` with two refs → `detail.referencedBy.length === 2`.
- [ ] Run `pnpm --filter @modeler/lsp test`. Should add ~7 tests; total 45.

### F5.b — Inspector RTL test (`packages/designer/src/components/__tests__/InspectorPanel.test.tsx`)

- [ ] Create the file. Plan-required cases (`E-inspector.md:23-26`):
  1. `selectedSymbol === null` → panel shows the empty-state text.
  2. With a fixture entity `SymbolDetail` (qname `er.entity.artikl`, 2 attributes, 1 tag, 2 referencedBy entries) → assert `screen.getByText('er.entity.artikl')`, the kind chip is visible, the description and tags render, the source `file:line` is present, and the attribute table has the expected rows.
  3. Clicking a row in "Referenced By" calls the `onSelect` prop with that qname:
     ```ts
     const onSelect = vi.fn();
     render(<InspectorPanel selectedSymbol={{ qname: 'er.entity.artikl' }} symbolDetails={{ 'er.entity.artikl': fixture }} onSelect={onSelect} />);
     fireEvent.click(screen.getByText('er.entity.related_thing'));
     expect(onSelect).toHaveBeenCalledWith('er.entity.related_thing');
     ```
  4. (Bonus, from F2) Clicking the source button writes the expected `<file>:<line>` string to `navigator.clipboard.writeText`:
     ```ts
     const writeText = vi.fn(() => Promise.resolve());
     Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
     ...
     fireEvent.click(sourceButton);
     expect(writeText).toHaveBeenCalledWith('/path/to/file.ttr:42');
     ```
- [ ] Run `pnpm --filter @modeler/designer test`. Should add 3–4 tests; total 45–46.

### F5.c — Decide on the integration-test file split

- [ ] Either create `tests/integration/src/symbol-detail.test.ts` and move test 4.4 (and 4.6) into it, **or** add a one-line entry to `phase-03-contracts.md`'s changelog noting the deliberate consolidation into `lsp-phase-03-custom-methods.test.ts`. (Recommend the second — moving tests for the sake of plan-prose-fidelity is churn.)

## F6 — Progress doc + final verification

- [ ] After all above pass, tick E.1–E.5 in `docs/plan/progress-phase-03.md` and add a one-line note pointing at `review-015.md`.
- [ ] Run the full quartet:
  - [ ] `pnpm --filter @modeler/lsp test` (45)
  - [ ] `pnpm --filter @modeler/designer test` (45–46)
  - [ ] `pnpm --filter @modeler/integration-tests test` (22)
  - [ ] `pnpm -r lint && pnpm -r typecheck && pnpm -r build`
- [ ] Manual demo path (the §E half of the plan's hand-verified demo, `tasks-phase-03-designer.md:117-128`):
  1. Click an `er` entity → inspector shows kind, name, qname, Czech description, tags, source `file:line`, attribute table, and a non-empty Referenced By list.
  2. Click the source `file:line` button → "Copied" toast appears; pasting from the clipboard yields `<absolute-path>:<line>`.
  3. Click a Referenced By entry → selected qname shifts and inspector populates the new detail. (If the new qname is below v1's "top-level def only" floor, the inspector should clear gracefully — verify it doesn't crash.)
  4. Click a relation edge → inspector shows `perKindData.kind === 'relation'` with `fromQname [from–to] toQname`.
  5. Click empty canvas → inspector reverts to empty state.
  6. Switch to `db` → click an FK edge → inspector shows `perKindData.kind === 'fk'`.

---

## Optional / deferred (don't block §E)

- [ ] N2 — Add a per-document parsed-AST cache so `findDefByQname` doesn't reparse on every click. v1-optimisation; defer if the inspector feels snappy enough on `v1-metadata`.
- [ ] N3 — Render cardinality in the relation `perKindData` view using `.ttr`-source strings or mini-glyphs instead of internal enum names.
- [ ] N4 — Pick "Related symbols" vs "Referenced By" wording and use it consistently across plan, contracts, and code. Recommend "Referenced By" (matches the data semantics).
