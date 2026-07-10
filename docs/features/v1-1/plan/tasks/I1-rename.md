# 1.1.I.1 — Rename: cross-package + `.ttrg` propagation + rename-package

**Goal:** `textDocument/rename` produces a `WorkspaceEdit` that updates every reference to the renamed symbol across the project, including qnames listed inside `.ttrg` `objects` blocks. Additionally, renaming a package (via right-click on the `package` declaration) updates every `import` referencing it.

**Reads:** [contracts §4 / §5 / §8](../../design/v1-1-contracts.md), `packages/edit/src/` (placeholder), `packages/semantics/src/reference-index.ts`.
**Blocked by:** 1.1.H.3 (uses workspace symbols + resolver).
**Blocks:** I2, I3, I4.
**Estimated time:** 2.5 days.

## Tests-first

- [ ] `packages/edit/src/__tests__/rename-symbol.test.ts` — unit. Cases:
  - Rename `billing.invoicing.er.entity.artikl` → `artikl_v2`: the produced `WorkspaceEdit` has `TextEdit`s for (a) the def site, (b) every cross-reference, (c) every `.ttrg`'s `objects` entry. Verify counts against a fixture project.
  - Rename a reference site (not the def): same result; the LSP resolves to the def first.
  - Rename across schema kinds: an `er` entity referenced from `map.<...>` updates both files.
  - Idempotent: applying the `WorkspaceEdit`, re-running rename with the same new name, the second `WorkspaceEdit` is empty.
- [ ] `packages/edit/src/__tests__/rename-package.test.ts` — unit. Cases:
  - Rename package `billing.invoicing` → `billing.invoicing_v2`: the `WorkspaceEdit` updates the `package` declaration in every file currently in that package, and every `import billing.invoicing.*` / `import billing.invoicing.<x>` in any other file across the project.
  - References that used bare names (resolved via same-package) keep working — they don't need rewriting because they were never qualified.
- [ ] `tests/integration/src/rename.test.ts` — integration. End-to-end via the LSP: rename via `textDocument/rename`, apply via `workspace/applyEdit`, re-parse, assert no diagnostics regressed.

## Library reference

```
mcp__context7__resolve-library-id { libraryName: "vscode-languageserver-types", query: "WorkspaceEdit, TextDocumentEdit, applyEdit semantics, version" }
mcp__context7__query-docs         { libraryId: "<id>", query: "WorkspaceEdit documentChanges versioning ordering" }
```

The CST view from `packages/parser/src/` is the basis for surgical text patches. Re-use the `getHiddenTokensToLeft/Right` pattern already exercised by C2.6's add-object/remove-object builders.

## Implementation tasks

- [ ] **I1.1 — Implement `buildRenameSymbolEdit(qname, newName, projectSymbols, referenceIndex, documents): WorkspaceEdit`.** New file `packages/edit/src/rename-symbol.ts`. Calls `referenceIndex.getReferences(qname)` (from v1's reference index); for each reference site, produces a `TextEdit` replacing the bare or qualified mention. Also updates the def site. Also iterates every `.ttrg`'s `Graph.objects` for matching qnames and produces edits there.
- [ ] **I1.2 — Implement `buildRenamePackageEdit(oldName, newName, projectSymbols, documents): WorkspaceEdit`.** New file `packages/edit/src/rename-package.ts`. Updates every `package` declaration whose `name === oldName`; updates every `import` declaration whose `target` starts with `oldName + '.'` or equals `oldName + '.*'`; updates every `.ttrg`'s `objects` entries that have qnames starting with `oldName + '.'`.
- [ ] **I1.3 — Register `connection.onRenameRequest` in `server.ts`.** Validate the new name (legal identifier + non-conflict check); call into `buildRenameSymbolEdit` or `buildRenamePackageEdit` based on whether the cursor is on a def/reference vs. a `package` declaration. Capability `renameProvider: { prepareProvider: true }` in `initialize`.
- [ ] **I1.4 — Implement `connection.onPrepareRename`.** Returns the editable range (the bare name span) plus the current placeholder. Refuses rename for unsupported positions (e.g. inside a string literal, on a keyword) by returning `null`.
- [ ] **I1.5 — Validation: refuse new name that would collide.** Check the symbol table for an existing symbol that the rename would shadow. Return an LSP `ResponseError` with code `InvalidParams` and a clear message; VS Code surfaces this as a refusal dialog.
- [ ] **I1.6 — Update `referenceIndex` to track `.ttrg` mentions.** The v1 reference index only knows about `.ttr` cross-refs. Extend it to scan `.ttrg` `objects` lists too, so rename's reverse-lookup picks them up.

## Verify by running

```bash
pnpm --filter @modeler/edit test
pnpm --filter @modeler/lsp test
pnpm --filter @modeler/integration-tests test
pnpm -r typecheck
```

All tests green. Manual smoke in VS Code: F2 on an entity name, type a new name, see the rename propagate through every reference + every `.ttrg`.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] Rename works for any def or reference site, propagating through `.ttr` and `.ttrg`.
- [ ] Rename-package works from any `package` declaration.
- [ ] Pre-flight validation refuses colliding renames cleanly.
- [ ] No formatting / code actions / code lens / semantic tokens yet — I2–I4.
