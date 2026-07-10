# 1.1.H.1 — Reference completion + auto-import

**Goal:** `textDocument/completion` returns useful reference suggestions in value positions that accept references. Auto-import: selecting a suggestion from an unimported package inserts both the reference and the appropriate `import` statement.

**Reads:** [contracts §8.3 / §4 (resolver chain)](../../design/v1-1-contracts.md#83-modeleraddobjecttograph-new), `packages/lsp/src/server.ts`.
**Blocked by:** 1.1.B.3 (uses resolver + symbol table).
**Blocks:** H2 (other completion types build on top), I3 (auto-import is also a code-action).
**Estimated time:** 2.5 days.

## Tests-first

- [ ] `packages/lsp/src/__tests__/completion-reference.test.ts` — unit. Cases:
  - At a position inside a `from:` value, `getCompletions(...)` returns a list of `CompletionItem`s scoped to compatible kinds (entities for er, tables for db).
  - For each suggestion: `label` is the bare symbol name; `detail` is the full qname; `documentation` is the symbol's `description` (Markdown).
  - Suggestions are sorted: same-package first, then named-import targets, then wildcard-import-visible, then unimported (FQN form).
  - For an unimported suggestion: the `CompletionItem.additionalTextEdits` contains a `TextEdit` that inserts an `import` line into the file's import block.
  - `CompletionItem.kind` is `Reference` (per LSP spec).

- [ ] `tests/integration/src/completion.test.ts` — extend or new. Case: open a file in package A, position cursor inside `from:` for a relation, request completion via `textDocument/completion`; assert the response shape and at least one auto-import item.

## Library reference

```
mcp__context7__resolve-library-id { libraryName: "vscode-languageserver", query: "completion, CompletionItem, additionalTextEdits, CompletionItemKind" }
mcp__context7__query-docs         { libraryId: "<id>", query: "completion request response, resolveCompletionItem" }
```

Existing Phase-2 hover-formatting helpers in `packages/lsp/src/` are useful for `documentation` field formatting. Reuse them.

## Implementation tasks

- [ ] **H1.1 — Implement `getReferenceCompletions(position, document, projectSymbols, manifest)`.** New file `packages/lsp/src/completion-reference.ts`. Determines whether the cursor is in a "reference-accepting value position" by walking up the parse tree (e.g. inside `from:`, `to:`, `nameAttribute:`, `joinProperty` entries). Returns `CompletionItem[]`.
- [ ] **H1.2 — Compute candidate symbols.** From `projectSymbols`, filter to kinds compatible with the parent property (e.g. `from:` on a `def relation` accepts entity qnames). Group by resolution-step bucket: same-package, named-import, wildcard-import, unimported.
- [ ] **H1.3 — Format each candidate as a `CompletionItem`.** `label` = bare name; `detail` = full qname + bucket badge ("same package" / "import: pkg.*" / "unimported"); `documentation` = Markdown with description + source location. `insertText` = the canonical form for that bucket (bare name for in-scope; full qname for unimported).
- [ ] **H1.4 — Build `additionalTextEdits` for auto-import.** When the candidate is from an unimported package, compute the insertion point for a new `import` line (alphabetical order in the file's existing import block; new block if none) and produce a single `TextEdit`. Helper: `buildImportTextEdit(document, importTarget): TextEdit` in `packages/lsp/src/import-edits.ts`.
- [ ] **H1.5 — Wire `connection.onCompletion`.** In `server.ts`, register a completion handler that dispatches to `getReferenceCompletions` when the cursor is in a reference position; falls through to H2's handlers otherwise. Add a `CompletionTriggerKind` config (registered in `initialize` response capabilities) listing `.` as a trigger character.
- [ ] **H1.6 — Add a `Setting` for `modeler.completion.autoImport`.** Default `true`. When `false`, suppress the `additionalTextEdits` field; the user can still pick unimported items, but the import won't be auto-added. Read via `workspace/configuration` per the existing v1 pattern.
- [ ] **H1.7 — Performance pass.** For large projects (>1000 symbols), the candidate list must be limited to the top N (default 50) by relevance score. Add `CompletionList.isIncomplete = true` and rely on the client re-querying as the user types.

## Verify by running

```bash
pnpm --filter @modeler/lsp test
pnpm --filter @modeler/integration-tests test
pnpm -r typecheck
```

The unit + integration tests pass. Manual smoke in VS Code: in a `def relation { from: <CURSOR> }`, hit Ctrl-Space, see candidates with the right grouping.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] Reference completion works in `from:`, `to:`, `nameAttribute:`, `codeAttribute:`, `join:` value positions (extend the position detection to all reference-accepting properties).
- [ ] Auto-import works for unimported candidates; insertion preserves alphabetical order.
- [ ] `modeler.completion.autoImport` setting honoured.
- [ ] No other completion kinds yet — H2 covers property-name / schema-kind / def-kind / package-name.
