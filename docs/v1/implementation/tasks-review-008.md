# Tasks — Review 008 (Phase 3, Section B)

Companion to `review-008.md`. Tasks 1–4 are showstoppers; tasks 5–8 are should-fix; task 9 is final acceptance. Do them in order — each verification command must pass before you tick the box.

## 1. Fix `getModelGraph` so it actually returns data when called from the Designer (F1)

The Designer dispatches `state.projectUri = file:///<rootName>` (a synthetic root, never a registered document). The server looks up `documents.get(params.textDocument.uri)`, finds nothing, and returns an empty graph. End-to-end break.

Pick **one** path; do not implement both.

### Path A (recommended) — server-side: build graph from all open documents

- [ ] **1.1A** In `packages/lsp/src/server.ts`, change the `modeler/getModelGraph` handler to iterate every document in `documents.all()`, parse each, filter to those whose `ast.schemaDirective?.schemaCode === params.schema`, and merge the per-document `buildModelGraph` results into one `ModelGraph`. The `params.textDocument.uri` becomes a *hint* (used to derive the project root if needed) — the handler must not require it to be a registered document.
- [ ] **1.2A** In `packages/lsp/src/model-graph.ts`, add a sibling export `buildProjectModelGraph(asts: Document[], schema: RenderableSchemaCode): ModelGraph`. Move the per-AST node-collection and FK/relation-resolution loops into it; have the existing `buildModelGraph(ast, schema)` delegate to `buildProjectModelGraph([ast], schema)` so the unit tests stay green. Ensure the `knownQnames` set spans every contributing AST, so an FK in `db/orders.ttr` whose `to:` points at a table defined in `db/items.ttr` resolves.
- [ ] **1.3A** Drop the `if (ast.schemaDirective?.schemaCode && ast.schemaDirective.schemaCode !== schema) return empty;` early-exit at `model-graph.ts:363-365`. In multi-document mode the per-document filter is now applied at the project level by the handler.
- [ ] **1.4A** Add a unit test in `packages/lsp/src/__tests__/model-graph.test.ts`: `buildProjectModelGraph` over **two** ASTs (one entity each, both `schema er namespace entity`) returns 2 nodes; an FK whose `from`/`to` cross documents resolves to one edge.
- [ ] **1.5A** Verify by running: `pnpm --filter @modeler/lsp test`. Expect green (37 + new tests).

### Path B (alternative) — client-side: send a real per-file URI

Only pick this if you have a concrete reason to keep the server per-document.

- [ ] **1.1B** In `packages/designer/src/state/designer-state.ts`, add `primaryDocumentUri: string | null` to `DesignerState` (and update `initialDesignerState`).
- [ ] **1.2B** In `packages/designer/src/App.tsx`'s `handleFileLoad`, choose the first `.ttr` file as primary (or, more deliberately, the file matching the `activeSchema`'s schema directive). Dispatch a new action `setPrimaryDocumentUri` after `setProjectUri`.
- [ ] **1.3B** Update the `useEffect` at `App.tsx:58-68` to call `client.getModelGraph(state.primaryDocumentUri, ...)` instead of `state.projectUri`. Bail out early if `primaryDocumentUri` is null.
- [ ] **1.4B** Document this in `docs/design/phase-03-contracts.md` §2 (add the field) and §7.2 (clarify the URI contract). Bump the changelog.
- [ ] **1.5B** Note: Path B regresses the multi-file project UX the parent plan called out as a §B-5 mitigation — confirm with the owning eng before picking this.

### After 1.A or 1.B

- [ ] **1.6** Add (or update) the integration test in `tests/integration/src/lsp-phase-03-custom-methods.test.ts` (see task 4) that proves: open every `.ttr` file under `samples/v1-metadata/`, call `getModelGraph` with `schema: 'db'`, expect ≥5 edges and every edge's `fromNode`/`toNode` resolving inside the response.

## 2. Make `getSymbolDetail` return real per-kind data (F2)

`buildSymbolDetail` currently fakes a `Definition` from four `SymbolEntry` fields and loses `columns` / `attributes` / `displayLabel` / `description` / `cardinality` / `tags` for every kind. Fix it by re-deriving the real `Definition` from the cached document text.

- [ ] **2.1** In `packages/lsp/src/server.ts`, give `createServerConnection` a way for `buildSymbolDetail` to look up the real `Definition` for a qname:
  - Either pass `documents` (the `TextDocuments<TextDocument>` instance) into `buildSymbolDetail` and have it re-parse the source containing the symbol; **or**
  - Maintain a `Map<documentUri, Document>` that's updated alongside the symbol-table in `updateSymbolTable` (cheaper — re-parsing in the request handler is wasteful), and pass that map.
- [ ] **2.2** In `packages/lsp/src/model-graph.ts`, change `buildSymbolDetail`'s signature to accept the real `Definition` (or the document map / a re-parse callback). Walk the AST starting from the symbol's `documentUri` to find the def whose qname matches; pass *that* `Definition` to `buildSymbolDetailForDef`. Keep returning `null` when the symbol isn't in the table.
- [ ] **2.3** Update the `connection.onRequest('modeler/getSymbolDetail', ...)` registration at `server.ts:385-387` to plumb the new dependency through.
- [ ] **2.4** Remove the unused `resolver` parameter from `buildSymbolDetail` (or keep and use it where the per-kind data needs reference resolution — `nameAttributeQname` / `codeAttributeQname` should resolve via the resolver, not via a `def.name + attr.path` string concat).
- [ ] **2.5** Add a unit test in `packages/lsp/src/__tests__/model-graph.test.ts`: build a `SymbolDetail` for an entity with `displayLabel: { cs: "Artikl" }` and a `description`; expect `label === "Artikl"` when `manifest.preferredLanguage === 'cs'`, `description !== null`, `perKindData.kind === 'entity'`, and `perKindData.attributes.length > 0`.
- [ ] **2.6** Verify by running: `pnpm --filter @modeler/lsp test`. Expect green.

## 3. Add the race-condition guard comment in `App.tsx` (F3)

- [ ] **3.1** Above the `for (const ... of files.files)` loop at `App.tsx:46`, add:

  ```ts
  // RACE-CONDITION GUARD: every openDocument MUST settle before getModelGraph
  // fires, or browser-mode cross-file resolution sees an incomplete project
  // and returns a graph missing edges. We intentionally await each open in
  // sequence; do not parallelise without preserving the "all settled before
  // setProjectUri" invariant.
  ```

- [ ] **3.2** (Optional) If you'd rather use `Promise.all` per the mini-list, switch to `await Promise.all(Array.from(files.files, ([path, content]) => client.openDocument(\`file:///${files.rootName}/${path}\`, content)))`. The guard comment still applies — the dispatch must be after the `await`, never before.
- [ ] **3.3** Verify by running: `pnpm --filter @modeler/designer typecheck && pnpm --filter @modeler/designer test`. Expect 16 passing.

## 4. Write the four missing integration tests (F4)

Create `tests/integration/src/lsp-phase-03-custom-methods.test.ts` (a separate file, per the mini-list). Use the same `PassThrough`-paired-connection harness as `integration.test.ts` — copy the helper if needed, or extract it to `tests/integration/src/_helpers/server.ts` and import from both files.

- [ ] **4.1** Test for `modeler/getLayout` with no `.modeler/` directory:
  - Boot a server with `loadManifest` resolving for a temp dir that has no `.modeler/`.
  - Send `modeler/getLayout` with `projectRoot = <tempDir>`.
  - Expect the response to deep-equal `emptyLayout()`.
- [ ] **4.2** Test for `modeler/setLayout` then `modeler/getLayout` round-trip:
  - In a temp dir, send `modeler/setLayout` with a non-empty `LayoutFile` (one node entry, one edge with one bend point).
  - Send `modeler/getLayout` immediately after.
  - Expect the returned `LayoutFile` to deep-equal the one sent. Clean up the temp dir in `afterAll`.
- [ ] **4.3** Test for `modeler/applyGraphEdit`:
  - Send `modeler/applyGraphEdit` with arbitrary params.
  - Expect `{ ok: false, reason: 'edit-mode-not-available-in-v1' }`.
- [ ] **4.4** Test for `modeler/getSymbolDetail` for `er.entity.artikl` against `samples/v1-metadata/`:
  - Open every `.ttr` file under `samples/v1-metadata/` (use `getAllTtrFiles` from `integration.test.ts`).
  - Send `modeler/getSymbolDetail` with `qname: 'er.entity.artikl'`.
  - Expect: result is non-null; `result.label === 'Artikl'` (the Czech display label, given the manifest); `result.description !== null`; `result.perKindData.kind === 'entity'`; `result.perKindData.attributes.length > 0`; `result.referencedBy.length > 0`.
  - **This is the test that catches F2 — write it before fixing F2 so the fix has a red test to drive it.**
- [ ] **4.5** Update the existing `getModelGraph` test (or add a new case): pass `schema: 'db'` against a multi-file project (open all `.ttr` files), expect ≥5 edges and every edge resolves. (Required by the mini-list; currently only `'er'` is covered.)
- [ ] **4.6** Verify by running: `pnpm --filter @modeler/integration-tests test`. Expect at least 19 passing (15 existing + 4 new).

## 5. Remove the duplicate `setProjectUri` action; restore `loadProject` semantics (F5)

- [ ] **5.1** In `packages/designer/src/state/designer-reducer.ts`, delete the `'setProjectUri'` case (line 80-81) and remove the action variant from `DesignerAction` (line 15).
- [ ] **5.2** In `packages/designer/src/App.tsx:50`, change `dispatch({ type: 'setProjectUri', uri: ... })` to `dispatch({ type: 'loadProject', projectUri: \`file:///${files.rootName}\` })`. The reducer's existing `loadProject` case already resets `symbolDetails`.
- [ ] **5.3** Verify the existing reducer test for `'loadProject' resets symbolDetails cache` still passes; it now exercises the live code path.
- [ ] **5.4** Verify by running: `pnpm --filter @modeler/designer test`. Expect 16 passing.

## 6. Decide what to do with `setGraph` / `graph` field (F6)

The reducer state should not carry the rendered `ModelGraph` until Section C decides where it lives. Either delete now or amend the contract.

- [ ] **6.1** Pick **one**:
  - **(a)** Delete `graph: ModelGraph | null` from `DesignerState`, drop the `setGraph` action and case, drop the `useEffect` at `App.tsx:58-68` (or replace its `dispatch({ type: 'setGraph', graph })` body with a `void graph;` and a `// Section C will wire this` comment). Section C re-introduces it with the right shape.
  - **(b)** Open `docs/design/phase-03-contracts.md`, add the field and action to §2, bump v1 → v2, add a one-line changelog entry, then leave the code in place. Update the reducer test file to cover `setGraph`.
- [ ] **6.2** Verify by running: `pnpm --filter @modeler/designer typecheck && pnpm --filter @modeler/designer test`.

## 7. Fix the file-input flow for directory uploads (F7 + F8)

- [ ] **7.1** In `packages/designer/src/components/Header.tsx`, change `accept=".ttr"` (line 78) to `accept=".ttr,.ttrl,.toml"` so the picker matches the shim's filter.
- [ ] **7.2** Add the `webkitdirectory` attribute to the same `<input>` so `webkitRelativePath` populates and `loadProjectViaUpload` derives a real `rootName`. (React renders this as `webkitdirectory=""`; TypeScript may need `// @ts-expect-error` on the JSX attribute or a property added to the JSX `IntrinsicAttributes` declaration.)
- [ ] **7.3** Update the visible button label from "Load .ttr files" to "Load Project Folder" (or similar) so users understand the picker now expects a directory.
- [ ] **7.4** Add a Header test in `Header.test.tsx`: render Header, trigger the file input, assert it has the `webkitdirectory` attribute.
- [ ] **7.5** Verify by running: `pnpm --filter @modeler/designer test && pnpm --filter @modeler/designer dev`. Manually pick a directory containing `samples/v1-metadata/` and confirm the synthetic `rootName` is `v1-metadata`, every URI sent to `openDocument` is `file:///v1-metadata/<relpath>`, and the network panel shows the right requests.

## 8. Clean up duplicate layout tests; remove dead exports

- [ ] **8.1** Delete the four `validateLayout` cases at the bottom of `packages/lsp/src/__tests__/model-graph.test.ts` (`emptyLayout returns valid LayoutFile`, `validateLayout rejects version !== 1`, `rejects missing db/er viewports`, `round-trip`). They duplicate `model-graph-layout.test.ts`.
- [ ] **8.2** Add a one-line comment in `packages/lsp/src/model-graph.ts:489-498` (`extractFkRef`) explaining "FK edges are table-to-table; we pick the first column to derive the source table — multi-column FKs collapse to one edge."
- [ ] **8.3** Verify by running: `pnpm --filter @modeler/lsp test`. Expect 33 passing (37 − 4 dupes).

## 9. Final acceptance

- [ ] **9.1** From the repo root: `pnpm -r build && pnpm -r test && pnpm -r lint && pnpm -r typecheck`. All exit 0.
- [ ] **9.2** `pnpm --filter @modeler/integration-tests test` passes the new four-test file with ≥4 cases green.
- [ ] **9.3** Open the dev server (`pnpm --filter @modeler/designer dev`), pick `samples/v1-metadata/` via "Load Project Folder", confirm:
  - The console shows N `openDocument` notifications followed by exactly one `getModelGraph` request.
  - The `getModelGraph` response payload (visible in devtools or via a one-off `console.log` in `App.tsx`) has `nodes.length > 0` and `edges.length >= 5` for `schema: 'db'`.
  - Switching the schema toggle to `er` triggers a second `getModelGraph` request whose response is also non-empty.
- [ ] **9.4** Tick B.1–B.7 in `docs/plan/progress-phase-03.md` only after 9.1–9.3 pass. Do not bulk-tick.
- [ ] **9.5** If contract amendments were needed (task 6 path b, or any addition under §2 / §5 / §7 driven by the F2 fix), confirm `docs/design/phase-03-contracts.md` is bumped from v1 to v2 with a one-line changelog naming each shape change.
