# Tasks — review-036 (Section C2 re-review)

Findings in [`review-036.md`](review-036.md). **Part A (C2-read) is done — do not redo F6/F7/F8/F9/F12/F13/D3.** The remaining work is all on the write side, plus the `.ttrl` decision. The two High items (G1, G2) currently **corrupt the user's `.ttrg`** — fix those first. Do not mark C2 done until every box is ticked.

> Baseline (green): edit 32, parser 82, semantics 107, lsp 53, designer 61, vscode-ext 7, integration 64 | 1 skipped. **Substring assertions are not enough** — the new tests below must apply the edit and re-parse.

---

## G1 [High] — Fix `buildSetLayoutEdit` (it currently emits malformed TTR)

In `packages/edit/src/graph-edits.ts`:

- [ ] **G1.1 — Replace-existing-`layout` path.** `findExistingLayoutBlock` must return the span that covers the **`layout:` keyword through its closing `}`** (not just the brace span). Then the replacement `newText` is the full `serializeLayoutBlock(...)` (which already starts with `layout: {`). Result must be a single `layout: { … }`, not `layout: { layout: { … } }`.
- [ ] **G1.2 — No-existing-`layout` path.** Insert the new block **before the graph's true closing brace**, not by replacing `indexOf('}')`. Find the graph block's matching closing `}` (depth-scan from the `graph <name> {` opening brace, like `findObjectsBrackets` does for `[`/`]`), and insert `,\n    layout: { … }\n` (with a leading comma + newline) immediately before it. The result must parse as one well-formed graph block.
- [ ] **G1.3 — Unit test (this is mandatory; its absence is why G1 shipped).** In `graph-edits.test.ts`, add cases for `buildSetLayoutEdit` that **apply** the returned `TextEdit` to the input string and assert the result:
  - graph with no `layout` → applying the edit yields a string that `parseString(result, 'x.ttrg')` parses with **zero errors**, and the parsed `graph.layout.nodes` contains the set keys (unquoted).
  - graph with an existing `layout` → applying the edit replaces it (no doubled `layout:`), parses clean, new positions present.
  - Assert the emitted node keys are unquoted dotted ids (D1).

## G2 [High] — Fix `autoImport` (it currently emits `import er`)

The package cannot be derived from the qname string in the pure builder. Move package resolution to the LSP layer.

- [ ] **G2.1** In the `modeler/addObjectToGraph` handler (`server.ts`), determine the object's package using the resolver / symbol table (the handler has `projectSymbols`/`resolver`). For an **unpackaged** object (qname is `<schema>.<nsOrKind>.<def>` with no package), the package is `null` → **emit no import**.
- [ ] **G2.2** Change `buildAddObjectEdit`'s signature so the caller passes the resolved package name (e.g. `packageToImport: string | null`) instead of `autoImport: boolean` + internal `extractPackageFromQname`. Delete `extractPackageFromQname`. Only emit an `import` edit when `packageToImport` is non-null and not already imported.
- [ ] **G2.3** Fix the tests that assert `import er`:
  - `graph-edits.test.ts:43` — change to pass a real package (e.g. `packageToImport: 'billing'` → asserts `import billing`) and a `null` case → asserts **no** import edit (documentChanges length 1).
  - `lsp-v1.1-graph-methods.test.ts` autoImport case — use a packaged fixture so a real `import <pkg>` is expected; assert no `import er` is ever emitted for an unpackaged `er.entity.*` object.

## G3 [High] — Implement the `.ttrl` removal (D4) or revise the decision

D4 is recorded as "remove `.ttrl`", but the code still has it. Pick one and make doc + code agree:

- [ ] **G3.1 (if removing — matches the recorded decision)** Delete the `.ttrl` read/write branches from `getLayout`/`setLayout`/`exportLayout` in `server.ts` and the now-dead `layoutStore` option/plumbing. Confirm no v1 test depends on it (`pnpm -r test`).
- [ ] **G3.2 (CC2)** Update the "Text is canonical; layout is a sidecar `.modeler/layout.ttrl`" invariant in `CLAUDE.md` and `docs/v1/design/architecture.md` to say layout now lives in the `.ttrg` `layout` block.
- [ ] **G3.3 (CC3)** If removing `.ttrl` (or the union-return change in G6) alters the §8 method surface, amend `docs/v1-1/design/v1-1-contracts.md` §8 and bump the version (header + §12 changelog entry).
- [ ] **G3.1-ALT (if keeping `.ttrl` for now)** Update `section-C-plan.md` D4 and `tasks-review-035.md` to say `.ttrl` is retained as a fallback for v1 compatibility, with the reason, so the decision and code no longer contradict. (Then CC2/CC3 are not needed yet.)

## G4 [Med] — Make write-side tests apply-and-reparse

- [ ] **G4.1** Add a `setLayout` → apply → `getLayout` round-trip integration test in `lsp-v1.1-graph-methods.test.ts`: send `setLayout`, apply the returned `WorkspaceEdit` to the in-memory doc (update via `didChange`/store), then `getLayout(graphUri)` returns the same node positions and the document still parses with no errors.
- [ ] **G4.2** Upgrade the `addObjectToGraph` / `removeObjectFromGraph` integration tests to **apply** the edit and assert the next `getGraph`/parse reflects the change (object present/absent), instead of only `newText` substring checks.

## G5 [Med] — Clean up the `createGraph` handler

- [ ] **G5.1** Remove the dead `const hasWorkspaceFolder = false; if (!hasWorkspaceFolder)` block. Implement the parent-dir check straightforwardly (known document prefix, or accept any `.ttrg` uri if you can't verify the dir in-memory — pick one and comment why).
- [ ] **G5.2** Don't return `{ documentChanges: [], error }`. Decide the error contract (see G6) and use it consistently.

## G6 [Med] — One return shape per method

- [ ] **G6.1** Make `modeler/setLayout` (graphUri path) and `modeler/createGraph` return a single shape — a `WorkspaceEdit` (the host applies it via `workspace/applyEdit`). If you need to signal "can't build an edit," decide how (empty `documentChanges`, or an LSP error response) and document it in contract §8. Update the client wrapper types to the single shape.

## G7 [Low] — Token-boundary match in `buildRemoveObjectText`

- [ ] **G7.1** Match the target entry on comma/bracket boundaries rather than `inner.indexOf(qname)`, so removing `er.entity.a` can't hit a substring of `er.entity.ab` or the wrong duplicate. Add a unit test with a near-prefix sibling in the list.

---

## Done when

- [ ] `buildSetLayoutEdit` output **parses** (both no-layout and replace paths), proven by an apply-and-reparse unit test (G1).
- [ ] `autoImport` emits a correct `import <package>` only for packaged objects and **nothing** for unpackaged ones; no test asserts `import er` (G2).
- [ ] `.ttrl` decision and code agree; CC2/CC3 done if removing (G3).
- [ ] Write-side tests apply edits and re-parse (G4).
- [ ] `createGraph` clean; single return shape per method (G5/G6).
- [ ] `pnpm -r build && pnpm -r typecheck && pnpm -r lint && pnpm -r test` green — with tests exercising **validity**, not substrings.
