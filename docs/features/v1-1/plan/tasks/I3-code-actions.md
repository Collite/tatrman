# 1.1.I.3 — Code actions: quick-fixes + extract-to-file

**Goal:** `textDocument/codeAction` returns quick-fixes for four diagnostics (`ttr/unimported-reference`, `ttr/unused-import`, `ttr/missing-package-declaration`, `ttr/package-declaration-mismatch`) and one refactor (extract-def-to-new-file-in-package).

**Reads:** [contracts §6 (diagnostic codes)](../../design/v1-1-contracts.md#6-diagnostic-codes-v11-additions), [contracts §8 (LSP methods)](../../design/v1-1-contracts.md#8-lsp-custom-method-contracts), `packages/lsp/src/server.ts`, `packages/lsp/src/import-edits.ts` (from H1).
**Blocked by:** 1.1.I.1, 1.1.I.2 (extract uses formatter to write the new file's contents).
**Blocks:** I4.
**Estimated time:** 2.5 days.

## Tests-first

- [ ] `packages/lsp/src/__tests__/code-actions.test.ts` — unit. Cases:
  - For a diagnostic `ttr/unimported-reference` at a reference site: returns a `CodeAction` titled "Add import for `<pkg>.<schema>.<ns>.<name>`" with `kind: 'quickfix'` and an `edit: WorkspaceEdit` that inserts the import line. The action's `diagnostics: [the-original]` field references the diagnostic.
  - For `ttr/unused-import`: action "Remove unused import"; removes the offending `import` line (and the trailing newline).
  - For `ttr/missing-package-declaration`: action "Add `package <inferred>`"; inserts `package <name>` at the top of the file.
  - For `ttr/package-declaration-mismatch`: action "Update declaration to match directory"; replaces the existing `package X.Y` with the inferred `package X.Z` value.
  - For a position over a `def` block: returns a refactor `CodeAction` titled "Extract def to new file in package <X>" with `kind: 'refactor.extract'`. The action creates a new file with the def's contents and removes the def from the current file.

## Library reference

```
mcp__context7__resolve-library-id { libraryName: "vscode-languageserver", query: "CodeAction, CodeActionKind, refactor.extract, diagnostics linkage" }
mcp__context7__query-docs         { libraryId: "<id>", query: "code action providers, resolve, isPreferred" }
```

Existing `import-edits.ts` (H1) and the rename/graph-edit builders are the WorkspaceEdit-building template — reuse aggressively.

## Implementation tasks

- [ ] **I3.1 — Implement `getQuickFixes(diagnostic, document, projectContext): CodeAction[]`.** New file `packages/lsp/src/code-actions.ts`. Dispatches on `diagnostic.code` to one of four helper functions. Each returns 0–N actions (most return exactly one).
- [ ] **I3.2 — Implement `quickFixUnimportedReference(diagnostic, ...): CodeAction`.** Computes the import to add (same logic as H1.4's `buildImportTextEdit`). Returns an action with `isPreferred: true`.
- [ ] **I3.3 — Implement `quickFixUnusedImport(diagnostic, ...): CodeAction`.** The diagnostic's range covers the offending `import` line; the action's edit is a single `TextEdit` deleting that range (plus the trailing newline if present).
- [ ] **I3.4 — Implement `quickFixMissingPackageDeclaration(diagnostic, ...): CodeAction`.** Computes the inferred package name (path-relative); inserts `package <name>\n` at line 1.
- [ ] **I3.5 — Implement `quickFixPackageDeclarationMismatch(diagnostic, ...): CodeAction`.** Computes the inferred name; the action's edit is a single `TextEdit` replacing the existing `package X.Y` line's value with the inferred one.
- [ ] **I3.6 — Implement `refactorExtractDefToNewFile(position, document, projectContext): CodeAction | null`.** Returns non-null when the cursor is on a top-level `def` block. Builds a `WorkspaceEdit` with:
  - A `CreateFile` op for `<projectRoot>/<package-path>/<defName>.ttr`
  - An initial `TextEdit` writing `package <pkg>\nschema <s> namespace <ns>\n\n<def-content>\n` (formatted via the I2 formatter)
  - A `TextEdit` removing the def from the current file
  - A `TextEdit` adding `import <pkg>.<schema>.<ns>.<defName>` to the current file if any reference remains
- [ ] **I3.7 — Wire `connection.onCodeAction`.** Inspects `params.context.diagnostics` for quick-fixes; computes refactor candidates from the cursor position. Capability `codeActionProvider: { codeActionKinds: ['quickfix', 'refactor.extract'] }` in `initialize`.

## Verify by running

```bash
pnpm --filter @modeler/lsp test
pnpm --filter @modeler/integration-tests test
pnpm -r typecheck
```

All code-action tests green. Manual smoke in VS Code: trigger the diagnostic, hover the lightbulb, see the quick-fix; click it; the edit is applied.

## DONE when

- [x] Every checkbox above is ticked.
- [x] All four quick-fixes work (verified end-to-end in `code-actions.test.ts`: trigger the diagnostic → request the fix).
- [x] Extract-to-file produces a valid new file and updates the source file.
- [x] All code actions are correctly linked to their originating diagnostics via the `diagnostics: [...]` field.

### Note (review-058)

`refactorExtractDefToNewFile` extracts into the **same package** (a sibling file in the current directory). The package is unchanged, so same-package references keep resolving and **no `import` is added** — I3.6's import-adding bullet applies only to a cross-package extract, which is out of scope for v1.1.
