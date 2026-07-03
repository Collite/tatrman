# 1.1.H.3 — Document outline, workspace symbols, completion settings

**Goal:** `textDocument/documentSymbol` returns the hierarchical outline (package → schema → defs → properties); `workspace/symbol` returns package-prefixed qnames; the user-facing settings for completion are wired through `workspace/configuration`.

**Reads:** `packages/lsp/src/server.ts`, existing v1 `workspaceSymbols` implementation.
**Blocked by:** 1.1.H.2.
**Blocks:** I (rename uses workspace symbols).
**Estimated time:** 2 days.

## Tests-first

- [ ] `packages/lsp/src/__tests__/document-symbol.test.ts` — unit. Cases:
  - A file with `package A.B`, `schema er namespace entity`, two entities each with attributes returns a `DocumentSymbol` tree:
    - Root: `A.B` (kind: Package)
      - Child: `er.entity` (kind: Namespace)
        - Two entity children (kind: Class) each with attribute children (kind: Field)
  - A file with no `package` declaration: root is the schema directive.
  - A `.ttrg` file: root is the graph block; children are the listed objects.
- [ ] `tests/integration/src/workspace-symbol-v1.1.test.ts` — integration. Open the v1.1-mini samples; `workspace/symbol` query `artikl` returns hits with full qnames like `billing.invoicing.er.entity.artikl`. Query `billing.` returns every symbol in the billing package and its sub-packages.
- [ ] `packages/lsp/src/__tests__/config-completion.test.ts` — unit. Cases:
  - `getCompletionConfig` returns the merged config with defaults when `workspace/configuration` returns null.
  - When the user sets `modeler.completion.autoImport: false`, subsequent completion results have no `additionalTextEdits`.

## Library reference

```
mcp__context7__resolve-library-id { libraryName: "vscode-languageserver", query: "DocumentSymbol, SymbolKind, hierarchical outline, workspaceSymbol" }
mcp__context7__query-docs         { libraryId: "<id>", query: "DocumentSymbol tree children range vs selectionRange" }
```

The `SymbolKind` enum maps our concepts: `Package`, `Namespace`, `Class` (entities/tables), `Field` (attributes/columns), `Interface` (relations), `Constant` (roles), etc. Pick the closest match per def kind.

## Implementation tasks

- [ ] **H3.1 — Implement `buildDocumentSymbols(document): DocumentSymbol[]`.** New file `packages/lsp/src/document-symbol.ts`. Walks the AST; produces a hierarchical tree per the test cases above. Each `DocumentSymbol` has accurate `range` (full def span) and `selectionRange` (just the name).
- [ ] **H3.2 — Wire `connection.onDocumentSymbol`.** Register in `server.ts`; capability `documentSymbolProvider: true` in `initialize`.
- [ ] **H3.3 — Verify `workspace/symbol` with package qnames.** The v1 implementation (`fuzzysort` over the project symbol table) should "just work" because the qname shape changed in B2; verify the integration test, fix any regressions.
- [ ] **H3.4 — Add per-package query mode to `workspace/symbol`.** Recognise queries that start with `<package>.` as a package-scoped filter; restrict the candidate set to that package's symbols (plus children if the query has further dotted segments).
- [ ] **H3.5 — Implement `getCompletionConfig` helper.** New file `packages/lsp/src/config-completion.ts`. Calls `connection.workspace.getConfiguration('modeler.completion')`; merges with defaults `{ autoImport: true, preselectFullyQualified: false }`. Cached for the session; invalidated on `workspace/didChangeConfiguration`.
- [ ] **H3.6 — Wire the config into H1's `additionalTextEdits` logic.** Read the config at completion-request time (cheap due to caching); honour `autoImport`.
- [ ] **H3.7 — Document the new settings.** Add `modeler.completion.autoImport` and `modeler.completion.preselectFullyQualified` to `packages/vscode-ext/package.json`'s `contributes.configuration` with descriptions and defaults.

## Verify by running

```bash
pnpm --filter @modeler/lsp test
pnpm --filter @modeler/vscode-ext test
pnpm --filter @modeler/integration-tests test
pnpm -r typecheck
```

All tests green. Manual smoke in VS Code: Cmd-T (workspace symbols) fuzzy-finds `artikl`; outline view shows the hierarchical tree.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] Outline view in VS Code shows the new tree shape for every sample file.
- [ ] Workspace symbol search returns full qnames; package-scoped queries work.
- [ ] `modeler.completion.autoImport` is honoured and documented. `modeler.completion.preselectFullyQualified` is documented and loaded into config but **deferred (not yet honoured)** — the v1.1 reference completion emits a single item per symbol (bare for imported, FQN for unimported, deduped by qname), so there is no FQN/bare pair to disambiguate; honouring this setting requires a dual-item completion model, deferred to a later phase (review-056 F1.5).
- [ ] H sub-phase as a whole satisfies [`implementation-plan-v1.1.md`](../implementation-plan-v1.1.md) §1.1.H acceptance.
