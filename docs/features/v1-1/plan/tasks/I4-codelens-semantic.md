# 1.1.I.4 — Code lens + semantic tokens enrichment

**Goal:** `textDocument/codeLens` surfaces "N references" on every def header and "N files in package" on every `package` declaration. `textDocument/semanticTokens/full` adds four new token types so the editor can colour package names, imported symbols, local symbols, and unimported references differently.

**Reads:** `packages/lsp/src/server.ts`, the v1 semantic-tokens implementation (Phase 3.G), `packages/semantics/src/reference-index.ts`.
**Blocked by:** 1.1.I.3.
**Blocks:** v1.1 release acceptance.
**Estimated time:** 2 days.

## Tests-first

- [ ] `packages/lsp/src/__tests__/code-lens.test.ts` — unit. Cases:
  - On a def with 0 references: lens reads "0 references" (still shown — clicking does nothing useful but visibility matters).
  - On a def with 3 references: lens reads "3 references"; the command's `arguments` include the def's qname for follow-up "find references" lookup.
  - On a `package billing.invoicing` line: lens reads "N files in package"; clicking opens a virtual document listing the files.
- [ ] `packages/lsp/src/__tests__/semantic-tokens-v1.1.test.ts` — unit. Cases:
  - A `package billing.invoicing` declaration: the qname tokens are tagged with the new `packageName` type.
  - A reference to a same-package symbol: tagged `localSymbol`.
  - A reference to an imported symbol: tagged `importedSymbol`.
  - A reference resolved via step-6 (fully-qualified-but-unique): tagged `unimportedReference`.

## Library reference

```
mcp__context7__resolve-library-id { libraryName: "vscode-languageserver", query: "CodeLens, semantic tokens, SemanticTokensLegend, token types modifiers" }
mcp__context7__query-docs         { libraryId: "<id>", query: "semantic tokens delta, legend, encoding scheme" }
```

The v1 semantic-tokens implementation already handles the existing token types — extend the legend with the new four, then update the visitor to tag them.

## Implementation tasks

- [ ] **I4.1 — Implement `getCodeLenses(document, projectContext): CodeLens[]`.** New file `packages/lsp/src/code-lens.ts`. Walks the AST; produces a lens for every top-level def header (uses `referenceIndex.getReferences(qname).length`) and the `package` declaration (uses `projectSymbols.getByPackage(packageName).length`).
- [ ] **I4.2 — Implement `resolveCodeLens(lens): CodeLens`.** Optional: lenses can be returned without the `command` field for performance, then resolved on hover. v1.1 returns them fully populated — simpler.
- [ ] **I4.3 — Wire `connection.onCodeLens` and `connection.onCodeLensResolve`.** Capability `codeLensProvider: { resolveProvider: false }` in `initialize`.
- [ ] **I4.4 — Implement the "files in package" virtual document.** Register a custom command `modeler.listPackageFiles` that VS Code can invoke. The command receives the package name and produces a quick-pick / output channel listing every file.
- [ ] **I4.5 — Extend the semantic-tokens legend.** Add `packageName`, `importedSymbol`, `localSymbol`, `unimportedReference` to the `SemanticTokensLegend.tokenTypes`. Keep existing types.
- [ ] **I4.6 — Update the semantic-tokens visitor.** In the v1 implementation, when emitting a reference token, look up the reference's resolution result (cached from the validator pass); tag with the appropriate new type. When emitting a `package` declaration's qname parts, tag as `packageName`.
- [ ] **I4.7 — Update the TextMate generator to fall through to semantic tokens for these cases.** The base TextMate scope can stay generic (`variable.other`); the LSP's semantic tokens override the colour for tagged ranges.

## Verify by running

```bash
pnpm --filter @modeler/lsp test
pnpm --filter @modeler/vscode-ext test
pnpm -r typecheck
```

All code-lens + semantic-tokens tests green. Manual smoke in VS Code: open a sample, see "N references" lenses on def headers and "M files in package" on package declarations; cross-package references render in a distinct colour from local-package references (with default themes, this is visible as a subtle italicisation or tint).

## DONE when

- [x] Every checkbox above is ticked.
- [x] Code lenses appear on every def + every package declaration. (`code-lens.test.ts`.)
- [x] The four new semantic-token types are emitted correctly. (`semantic-tokens-v1.1.test.ts`.)

### Notes (review-058)

- **Counts reflect indexed (opened) documents.** "N references" and "N files in package" are computed from `refIndex` / `projectSymbols`, which this LSP populates from *opened* documents (a pre-existing trait shared by find-references and workspace symbols). In a fresh editor session they undercount until the relevant files are opened; a project-wide eager index would be a separate enhancement.
- **`modeler.listPackageFiles`** is registered in the extension (a quick-pick over `**/*.ttr`) but is only verifiable in the Extension Host (F5); the LSP-side lens data is unit-tested.
- [ ] I sub-phase as a whole satisfies [`implementation-plan-v1.1.md`](../implementation-plan-v1.1.md) §1.1.I acceptance.
- [ ] v1.1 release acceptance per [`implementation-plan-v1.1.md`](../implementation-plan-v1.1.md) §"Acceptance summary" is now reachable; G's docs pass + a v1.1 marketplace publish finishes the release.
