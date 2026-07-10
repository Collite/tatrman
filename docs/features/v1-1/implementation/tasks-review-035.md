# Tasks — review-035 (Section C2)

Findings in [`review-035.md`](review-035.md). C2 is **not done**: the read methods work, but the write side is stubbed/buggy/untested and the D3/D4 decisions and half the client wrappers are missing. Do this in two slices — **Part A (C2-read)** closes quickly; **Part B (C2-write)** is the real remaining work. Each task names the exact file and how to verify. Don't mark C2 done until every box here is ticked.

> Baseline (green, keep it): parser 82, semantics 107, lsp 53, designer 61, vscode-ext 7, integration 59 passed | 1 skipped. **Green is not enough** — the new tests below must actually exercise behaviour, not just shape.

---

## Decisions to settle first (blockers)

- [x] **D3 — `getModelGraph` vs `getGraph`.** Pick one and write the decision into `docs/v1-1/plan/section-C-plan.md` (D3) **and** the task file. Two options:
  - **(Recommended)** `getGraph` is the `.ttrg`-scoped render method; `getModelGraph` stays as the whole-schema render used by v1 paths. Keep both, but they **must share** the node/edge builders (tasks A1 + nothing duplicated). Document that they are intentionally distinct.
  - Or: `getGraph` supersedes `getModelGraph` → deprecate/remove `getModelGraph` and update its one caller. If you remove a method from contract §8, do **CC3** (version bump).
  - **→ DECIDED (2026-05-21): keep both.** See section-C-plan.md D3 for rationale.
- [x] **D4 — `.ttrl` removal scope.** Grep every `.ttrl` / `layout.ttrl` / `layoutStore` reference in `packages/lsp/src/server.ts`, `model-graph.ts`, and the designer. Confirm whether any v1 path still depends on the sidecar. Decide: remove it now (preferred, per C2.7) or keep it for v1 compatibility and **descope** the "layout-in-`.ttrg`" write path explicitly. Write the decision down before doing B5/B6.
  - **→ DECIDED (2026-05-21): remove `.ttrl`.** Layout canonical location is the `.ttrg`'s `layout` block. See section-C-plan.md D4.

---

## Part A — C2-read (close these first)

- [ ] **A1 — Share the per-kind node/row builder (F6).** In `packages/lsp/src/model-graph.ts`, extract the table/view/entity row+node construction currently inlined in `buildProjectModelGraph` into a helper, e.g. `buildNodeForDef(def, schemaCode, namespace, schema, preferredLang): ModelGraphNode | null`. Make **both** `buildProjectModelGraph` and `graph-methods.ts`'s `getGraph` call it. Delete the duplicated table/view/entity blocks in `getGraph`. (Mirror the C1 `buildEdgeForDef` refactor.)
- [ ] **A2 — Compute `missingObjectCount` honestly (F7).** In `listGraphs` (`graph-methods.ts`), resolve each `objects` entry the same way `getGraph` does (`qnameToDef.has(qname)`) and set `missingObjectCount` to the real count. If you'd rather not, remove the field from `GraphMetadata` and from contract §8.1 (then note it in the changelog). Do **not** ship a permanent `0`.
- [ ] **A3 — Read the cached `PackageGraph`, drop the mock (F8).** In `getPackageGraph`, stop constructing `mockProjectSymbols as unknown as ProjectSymbolTable` and re-parsing every doc. Use the `PackageGraph` the server already maintains (it's imported in `server.ts`); pass it (or its already-built nodes/edges) into the response builder. No `as unknown as`.
- [ ] **A4 — Unify the default `schemaCode` (F9).** `buildQnameToDef` defaults to `'db'`, `computeGraphEdges` to `'er'`. Pick one shared default (or thread the real schema through) so node and edge qnames are computed identically.
- [ ] **A5 — Real `getGraph` integration test.** In `tests/integration/src/lsp-v1.1-graph-methods.test.ts`, add a case that writes a `.ttrg` whose `objects` resolve against companion `.ttr` docs (open them via `didOpen`), then asserts `getGraph` returns the expected **nodes** (with rows) **and** at least one computed **edge**. The current tests only use `objects: []` / unresolvable — they never build a node.
- [ ] **A6 — Verify Part A:**
  ```bash
  pnpm --filter @modeler/lsp test
  pnpm --filter @modeler/integration-tests test
  pnpm -r typecheck && pnpm -r lint
  ```
  Green, and the new `getGraph` test asserts non-empty nodes+edges.

---

## Part B — C2-write (the real remaining work)

### B1 — Rewrite the edit builders to be CST/offset-based (F1, F2, F3)

- [ ] **B1.1** In `packages/edit/src/graph-edits.ts`, rewrite `buildAddObjectEdit` so it inserts the new qname **inside** the existing `objects: [...]` array as a surgical `TextEdit`, preserving the `[`/`]`, existing indentation, and the prevailing trailing-comma style. Do not reconstruct the whole `objects:` clause as a single line. Use the parser's CST/source-location info (the parser exposes a CST view with trivia — see `@modeler/parser`) rather than `[^\]]*` regex. Fix the dropped-`[` bug for the non-empty case.
- [ ] **B1.2** Rewrite `buildRemoveObjectEdit` to delete just the one entry + its adjacent comma as a surgical `TextEdit` (not a whole-document replace). Fix the `[,s]` bug. Handle all positions: first element, middle, last, and sole element (sole → `objects: []`).
- [ ] **B1.3** Remove the `version: null as unknown as number` casts (F5) — use `version: null` (the type allows it) via the existing `buildTextEdit` helper.

### B2 — Implement `autoImport` / `pruneUnusedImport` (F4)

- [ ] **B2.1** In `buildAddObjectEdit`, when `autoImport` is true and the qname's package isn't already imported, add the appropriate `import` statement as part of the same `WorkspaceEdit` (insert after the last existing import / before the `graph` block). Use the package-resolution rules from semantics; don't hand-roll qname→package parsing if a helper exists.
- [ ] **B2.2** In `buildRemoveObjectEdit`, when `pruneUnusedImport` is true and no remaining object needs that import, also remove the `import` line.

### B3 — Builder unit tests (F14) — write these alongside B1/B2, not after

- [ ] **B3.1** Create `packages/edit/src/__tests__/graph-builders.test.ts` (new `__tests__` dir). Cover, with **content assertions** on the produced `TextEdit`/`newText`:
  - add to empty list → `objects: [er.entity.x]`.
  - add to a single-element list → both elements present, `[`/`]` intact, comma correct.
  - add to a multi-line list → new entry on its own line with matching indentation, original entries untouched.
  - `autoImport: true` for a not-yet-imported package → edit also contains the `import` line.
  - remove middle / first / last / sole element → correct comma handling; sole → `objects: []`.
  - `pruneUnusedImport: true` when the import becomes unused → import line removed; when still used → kept.
  - `buildCreateGraphContent(params)` → matches the canonical body in contracts §7.1 (schema, objects, unquoted keys).

### B4 — `createGraph` handler validation (F-I / C2.4)

- [ ] **B4.1** In the `modeler/createGraph` handler (`server.ts`), validate that `uri` ends in `.ttrg` and that the parent directory is known (in-memory check via the document/workspace state). Return a clear error shape if not, instead of unconditionally building the edit.

### B5 — `setLayout` into the `.ttrg` (F10, D1) — the missing core of C2.7

- [ ] **B5.1** Implement a layout-writing builder (in `@modeler/edit`, e.g. `buildSetLayoutEdit(graphContent, graphUri, layout)`) that replaces/inserts the `layout { ... }` block in the `.ttrg`, emitting `layout.nodes` keys as **unquoted dotted ids** (D1), e.g. `nodes: { billing.invoicing.er.entity.artikl: { x, y } }`.
- [ ] **B5.2** Wire `modeler/setLayout` (graphUri branch) to return that `WorkspaceEdit` instead of the `{ ok: false, reason: 'C2.6 pending' }` stub. The host applies it via `workspace/applyEdit`.
- [ ] **B5.3** Fix `getLayout` (graphUri branch) to return the **actual** parsed `graph.layout.viewport`, not the hardcoded `db`/`er` viewports (F12). Match the single-schema-per-`.ttrg` model (no per-schema viewport map).
- [ ] **B5.4** Unit + integration test: round-trip `setLayout(graphUri, layout)` → apply the edit → `getLayout(graphUri)` returns the same node positions, and the on-disk keys are unquoted.

### B6 — `.ttrl` removal + docs (D4, F11, CC2, CC3) — only if D4 decided "remove"

- [ ] **B6.1** Remove the `.ttrl` read/write paths from `getLayout`/`setLayout`/`exportLayout` in `server.ts` and any `layoutStore` plumbing that's now dead. Confirm no v1 test depends on it (run the full suite).
- [ ] **B6.2 (CC2)** Update the "Text is canonical; layout is a sidecar `.modeler/layout.ttrl`" invariant in `CLAUDE.md` and `docs/v1/design/architecture.md` to say layout now lives inside the `.ttrg` `layout` block.
- [ ] **B6.3 (CC3)** If D3/D4 changed the §8 method surface (e.g. removed `getModelGraph` or the `projectRoot` layout params), amend contracts §8 and bump the version in `docs/v1-1/design/v1-1-contracts.md` (header + §12 changelog entry).

### B7 — Designer client wrappers (F13, C2.8)

- [ ] **B7.1** In `packages/designer/src/lsp-client.ts`, add typed wrappers for `addObjectToGraph`, `removeObjectFromGraph`, `createGraph` (return `WorkspaceEdit`).
- [ ] **B7.2** Update the `getLayout` / `setLayout` / `exportLayout` / `getModelGraph` wrapper signatures per the D3/D4 outcome (e.g. `getLayout(graphUri)` / `setLayout(graphUri, layout)`). Re-export any new request/response types from `@modeler/lsp` so the Designer doesn't duplicate them.
- [ ] **B7.3** Update `useLayoutSync` consistently with the chosen layout source (graphUri vs projectRoot). If the graphUri path depends on section-E state (`currentGraphUri`) that doesn't exist yet, that's fine — but say so explicitly in a comment and keep the projectRoot path working until E.

### B8 — Write-side integration tests (F15)

- [ ] **B8.1** Extend `lsp-v1.1-graph-methods.test.ts`: for `addObjectToGraph`, **apply** the returned edit to the in-memory document and assert the next `getGraph`/parse shows the new object; add an `autoImport: true` case asserting the `import` appears.
- [ ] **B8.2** Add a `removeObjectFromGraph` round-trip test (apply edit → object gone; `pruneUnusedImport` case).
- [ ] **B8.3** Add a `createGraph` test asserting `documentChanges` contains a `CreateFile` **and** a `TextEdit` whose text is the canonical body.
- [ ] **B8.4** Add the `setLayout`/`getLayout` round-trip from B5.4.

---

## Done when

- [ ] D3 and D4 are decided and written into `section-C-plan.md` + the task file.
- [ ] Part A boxes ticked; `getGraph` integration test asserts real nodes **and** edges.
- [ ] Edit builders are CST/offset-based, the F2/F3 bugs are gone, and `autoImport`/`pruneUnusedImport` work — all covered by `graph-builders.test.ts` with content assertions.
- [ ] `setLayout(graphUri)` writes an unquoted-key `layout` block (no stub); `getLayout(graphUri)` returns the real viewport.
- [ ] `.ttrl` removed (or its retention explicitly decided and documented); CC2/CC3 done if applicable.
- [ ] All six new methods + the three write methods have Designer wrappers; updated layout signatures match D3/D4.
- [ ] `pnpm -r build && pnpm -r typecheck && pnpm -r lint && pnpm -r test` green — with the new tests exercising **behaviour**, not just shape.
- [ ] No `as unknown as` casts introduced in C2 code.
