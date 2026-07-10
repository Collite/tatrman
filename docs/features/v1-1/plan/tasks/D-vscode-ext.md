# 1.1.D — VS Code extension: `.ttrg` registration, `.ttrl` removal

**Goal:** VS Code recognises `.ttrg` as a separate language id; the existing `.ttrl` registration is removed; the smoke test exercises opening a `.ttrg` file.

**Reads:** [contracts §9 (VS Code language-registration shape)](../../design/v1-1-contracts.md#9-vs-code-language-registration-shape), `packages/vscode-ext/package.json`, `packages/vscode-ext/syntaxes/`.
**Blocked by:** 1.1.A (grammar/TextMate updated).
**Blocks:** the v1.1 smoke-test acceptance gate.
**Estimated time:** 2–3 days.

## Tests-first

- [ ] `packages/vscode-ext/src/test/suite/ttrg-registration.test.ts` — new smoke case. Boot VS Code via `@vscode/test-electron`, open a `.ttrg` from `samples/v1.1-mini/graphs/` (will exist after 1.1.G; for now use a hand-authored fixture under `packages/vscode-ext/test-fixtures/`), assert:
  - `vscode.window.activeTextEditor?.document.languageId === 'ttrg'`.
  - Syntax-highlight scopes include `keyword.declaration.graph.ttrg`, `keyword.control.package.ttrg`, `keyword.control.import.ttrg` (use the existing scope-inspection helper from the v1 smoke tests).
- [ ] `packages/vscode-ext/scripts/__tests__/tm-grammar-ttrg.test.ts` — new file. Cases:
  - The generated `ttrg.tmGrammar.json` references `source.ttrg` as scopeName.
  - The generated file has patterns for `graph`, `objects`, `layout` keywords.

## Library reference

```
mcp__context7__resolve-library-id { libraryName: "@vscode/test-electron", query: "boot VS Code, open file, scope inspection" }
mcp__context7__query-docs         { libraryId: "<id>", query: "runTests, workspaceFolder, scope inspector API" }
```

The v1 smoke-test harness (`packages/vscode-ext/src/test/suite/*.test.ts`) is the canonical pattern; new smoke cases follow it exactly.

## Implementation tasks

- [ ] **D.1 — Register `.ttrg` as a separate language in `package.json`.** Add `{ "id": "ttrg", "extensions": [".ttrg"], "configuration": "./language-configuration.json" }` to `contributes.languages`. Reuse the existing `language-configuration.json` (brackets/comments are identical to `.ttr`).
- [ ] **D.2 — Generate `ttrg.tmGrammar.json`.** Extend `scripts/generate-tm-grammar.ts` to emit a second grammar file alongside `ttr.tmGrammar.json`. The two share most patterns; `.ttrg`'s adds rules for `graph`, `objects`, `layout` keywords (scopes: `keyword.declaration.graph.ttrg`, `keyword.other.property.ttrg`). Run the generator; commit both files.
- [ ] **D.3 — Wire the `.ttrg` grammar registration.** Add `{ "language": "ttrg", "scopeName": "source.ttrg", "path": "./syntaxes/ttrg.tmGrammar.json" }` to `contributes.grammars`.
- [ ] **D.4 — Add a `.ttrg` file icon.** Create `packages/vscode-ext/icons/ttrg-icon.svg` (a variant of the existing `.ttr` icon — e.g. add a small "G" badge). Reference it under `contributes.iconThemes` if the v1 extension uses an icon theme; otherwise add it as a file icon via `vscode-files`.
- [ ] **D.5 — Remove `.ttrl` registration.** Delete the `.ttrl` entry from `contributes.languages` and `contributes.grammars`. Delete `packages/vscode-ext/syntaxes/ttrl.tmGrammar.json` (or whatever the v1 file is). Delete the `.ttrl` icon if present. Search the extension code for any `.ttrl`-related logic and remove it.
- [ ] **D.6 — Update LSP client document selector.** In `packages/vscode-ext/src/extension.ts`, the LSP client's `documentSelector` should now be `[{ scheme: 'file', language: 'ttr' }, { scheme: 'file', language: 'ttrg' }]` (was `'ttr'` only, with `'ttrl'` excluded). The server will now receive `didOpen` notifications for both kinds.

## Verify by running

```bash
pnpm --filter @modeler/vscode-ext build
pnpm --filter @modeler/vscode-ext test
pnpm --filter @modeler/vscode-ext test:smoke
pnpm -r typecheck
```

The new smoke case passes; existing smoke cases still pass.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] Opening any `.ttrg` file in VS Code shows the right language label in the status bar and the right syntax colours.
- [ ] No `.ttrl` reference remains anywhere in `packages/vscode-ext/`.
- [ ] The LSP client receives `.ttrg` documents and forwards them to the server.
