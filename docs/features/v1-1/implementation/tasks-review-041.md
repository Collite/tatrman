# Tasks — review-041 (Section E2)

Findings in [`review-041.md`](review-041.md). The wizard UI is solid; the **save path is broken** (F1) and the **name isn't validated** (F2) — fix those first, they're the point of E2. Then decide F3/F4. Don't claim E2 done until creating a graph actually produces a loadable, valid `.ttrg`.

> Suite is green (designer 95) but the RTL tests mock `createGraph` and never create a real graph — the new test in F1.3 must apply the edit and re-parse.

---

## F1 [High] — Actually create the graph (apply the `WorkspaceEdit`)

Right now `createGraph`'s returned `WorkspaceEdit` is discarded, so no `.ttrg` exists and the post-save jump shows an empty canvas.

- [ ] **F1.1** In the save flow (wizard `handleSave` or `App.tsx`'s `onComplete`), **apply** the `WorkspaceEdit` returned by `client.createGraph(...)`. For the browser transport: extract the `CreateFile` target uri + the `TextEdit.newText` (the canonical body) from `documentChanges` and call `client.openDocument(newUri, content)` so the document exists in the LSP store before `getGraph` runs. Where the File System Access API is available, also persist the file to disk.
- [ ] **F1.2** Only after the document is registered, dispatch `openGraph(newUri)` / call `handleSelectGraph(newUri)` so `getGraph` finds it and the canvas renders the new graph.
- [ ] **F1.3** Add a test that proves the round-trip (not a mock-only check): create a graph through the wizard (or call the save handler), apply the returned edit, then assert `getGraph(newUri)` returns a non-null graph **and** the generated content `parseString(content, newUri)` has zero errors. This is the test that would have caught F1.

## F2 [High] — Validate the graph name as an identifier

- [ ] **F2.1** Validate `state.graphName` against the TTR IDENT rule (`/^[A-Za-z_][A-Za-z0-9_]*$/`). Disable Save (with a visible inline message, e.g. "Name must be a valid identifier: letters, digits, underscore; no spaces") when it doesn't match. Do **not** pass a raw spaced/special-char name to `createGraph`.
- [ ] **F2.2** Keep `slugify` for the *filename* only; the graph `name` sent to `createGraph` must be the validated identifier.
- [ ] **F2.3** Add a negative test: a name with a space leaves Save disabled / shows the error; a valid identifier enables it. (Current tests only use space-free names.)

## F3 [Med] — Step 2 dependency mini-graph (E2.3)

- [ ] **F3.1** Implement the embedded Cytoscape panel showing the `PackageGraph` with the selected packages highlighted (the task points at `Canvas.tsx` for the small-embedded-graph pattern). Keep the working "Add all transitive" / "Continue" actions. **Or**, if you intend to ship Step 2 as a checkbox list, get that descope agreed and update `E2-create-wizard.md` (E2.3 + tests-first) so the doc and the code match — don't leave the spec saying "mini-graph" while the code is a list.

## F4 [Med] — Step 3 grouping + bulk-select (E2.4)

- [ ] **F4.1** Group the object list by package and by schema kind, and add bulk-select-package / bulk-select-schema controls (keep the "N of M selected" counter). Or formally descope in the task doc with a reason.

## F5 [Low] — Don't swallow create errors

- [ ] **F5.1** In `handleSave`, on `createGraph` failure dispatch `setError` (or surface a toast) and do **not** navigate. Only call `onComplete` after a successful create+apply (F1).

## F6 [Low] — optional

- [ ] **F6.1** Add the disabled "multi-schema (coming later)" affordance on Step 4 (E2.5), if you want spec parity.
- [ ] **F6.2** Directory picker on Step 5 (E2.6) — optional; the `<projectRoot>/graphs/` default is acceptable if noted.

---

## Done when

- [ ] Creating a graph through the wizard produces a `.ttrg` that exists in the LSP store, parses with zero errors, and loads on the canvas — proven by a non-mock test (F1).
- [ ] Invalid graph names are blocked at Save; only valid identifiers reach `createGraph` (F2).
- [ ] F3/F4 are either implemented or explicitly descoped in `E2-create-wizard.md`.
- [ ] Create failures surface an error instead of silently navigating (F5).
- [ ] `pnpm --filter @modeler/designer test && pnpm --filter @modeler/designer typecheck && pnpm --filter @modeler/designer build` green.
