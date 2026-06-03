# Tasks — review-037 (Section C2 third re-review)

Findings in [`review-037.md`](review-037.md). **G1 (setLayout) is fixed — leave it.** Two High items remain (G2, G3) plus a build-discipline issue and two Med cleanups. Don't claim done until `pnpm -r build && pnpm -r typecheck && pnpm -r lint && pnpm -r test` is green **with a fresh build**.

> Note: as delivered, `pnpm -r test` was RED (stale `dist/`). Always `pnpm -r build` first — the integration suite runs against compiled output, not source.

---

## G2 [High] — Stop emitting `import er`; emit imports only for real packages

The bug is unchanged from review-036 — the handler still does `qname.slice(0, dotIdx)`, which returns the schema code `er` for `er.entity.artikl`.

- [ ] **G2.1 — Fix the handler** (`server.ts`, `modeler/addObjectToGraph`). Compute the package correctly:
  - **Preferred:** look the object up in the symbol table / resolver (the handler has `projectSymbols`/`resolver`) and use the symbol's declaring package; `null` if it has none.
  - **Minimum acceptable:** treat the qname's first segment as a package **only if it is not one of the schema codes** `db|er|map|query|cnc`. If the first segment is a schema code, the object is unpackaged → `packageToImport = null`. (This handles single-segment packages; note in a comment that multi-segment packages need the resolver.)
  Pass the resulting `string | null` to `buildAddObjectEdit`; when `null`, no import edit is produced.
- [ ] **G2.2 — Fix the unit test** `packages/edit/src/__tests__/graph-edits.test.ts:83-90`: pass an actual package (e.g. `packageToImport: 'billing'`) and assert `import billing\n`; add a `packageToImport: null` case asserting **one** documentChange (no import).
- [ ] **G2.3 — Fix the integration tests** `tests/integration/src/lsp-v1.1-graph-methods.test.ts`:
  - The `autoImport` case (line ~226): use a **packaged** object fixture (a `.ttr` with a `package` declaration) so a real `import <pkg>` is expected; assert it, and assert **no** `import er` is ever emitted for an unpackaged `er.entity.*` object.
  - The `createGraph` canonical-body case (line ~271) currently asserts `import er`; update it to a real package or drop the import expectation if `packages: []`.
- [ ] **G2.4 — Apply-and-reparse:** after building the add edit for an unpackaged object, applying it must yield a document that `parseString(...)` parses with **zero errors** (no stray `import er`).

## G3 [High] — Resolve the `.ttrl` contradiction for real

Pick ONE and make `section-C-plan.md` D4 and the code agree.

- [ ] **G3-A (remove — matches the current D4 text):**
  - [ ] **G3-A.1** Delete the `.ttrl` read/write branches in `getLayout`/`setLayout`/`exportLayout` and the `layoutStore` option/plumbing in `server.ts`. (`grep -c ttrl packages/lsp/src/server.ts` → 0.)
  - [ ] **G3-A.2 (CC2)** Update the "Text is canonical; layout is a sidecar `.modeler/layout.ttrl`" invariant in `CLAUDE.md` and `docs/v1/design/architecture.md` to say layout lives in the `.ttrg` `layout` block.
  - [ ] **G3-A.3 (CC3)** Amend contracts §8 for the changed layout-method surface (no `projectRoot`/`.ttrl`) and bump the version (header + §12 changelog entry).
  - [ ] **G3-A.4** Full suite green after removal (confirm no v1 path depended on `.ttrl`).
- [ ] **G3-B (retain — if you have a reason):** rewrite `section-C-plan.md` D4 to "retain `.ttrl` as a v1 fallback because <reason>"; then CC2/CC3 are not needed. Do **not** leave D4 saying "removed" while the code keeps it.

## G5 / G6 [Med] — One return shape per method

- [ ] **G6.1** Make `modeler/setLayout` (graphUri path) and `modeler/createGraph` each return a single shape — a `WorkspaceEdit`. Signal "can't build an edit" via empty `documentChanges` (or a proper LSP error response), **not** a `{ ok }` object or an `error` field on a `WorkspaceEdit`.
- [ ] **G5.1** Remove the `{ documentChanges: [], error: 'uri must end with .ttrg' }` return in `createGraph`; use the chosen convention from G6.1.
- [ ] **G6.2** Update the client wrapper types in `packages/designer/src/lsp-client.ts` (`setLayout`, `createGraph`) to the single shape.
- [ ] **G6.3** Reflect the final shapes in contracts §8 (tie into CC3 if you're already bumping).

## G7 [Low] — Token-boundary match in `buildRemoveObjectText` (optional)

- [ ] **G7.1** Replace `inner.indexOf(qname)` with a comma/bracket-boundary match so `er.entity.a` can't hit a substring of `er.entity.ab`. Add a unit test with a near-prefix sibling. (Fine to defer if time-boxed.)

---

## Done when

- [ ] No `import er` (or any schema-code import) is ever emitted; unpackaged objects produce no import; tests assert the corrected behaviour and apply-and-reparse cleanly (G2).
- [ ] `section-C-plan.md` D4 and the code agree; if removed, `.ttrl` is gone and CC2/CC3 are done (G3).
- [ ] `setLayout`/`createGraph` each return one shape; client types match (G5/G6).
- [ ] `pnpm -r build && pnpm -r typecheck && pnpm -r lint && pnpm -r test` green **from a clean build** — verified before claiming done.
