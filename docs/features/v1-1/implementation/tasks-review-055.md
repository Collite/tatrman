# Tasks — review-055 (Section H3)

Findings in [`review-055.md`](review-055.md). The document-outline and workspace-symbol work (H3.1–H3.4, H3.7) is **done** — leave it. What's broken is the **completion-settings wiring** (F1) and **lint** (F2); the config test is misleading (F3). H3 is done when the completion settings actually reach the server, lint is clean, and a real-completion test proves `autoImport: false` suppresses auto-import.

**Verify the behaviour through a completion request — not just the cached flag.**

---

## F1 [High] — Actually load the completion config (and reload on change)

The config is never read from the client (`loadCompletionConfig` is imported but never called; no `onDidChangeConfiguration`), so `modeler.completion.autoImport` is ignored. Wire it.

- [ ] **F1.1** In `server.ts` `onInitialize`, record whether the client supports pull configuration. Near where `params` is read:
  ```ts
  const supportsConfiguration = !!params.capabilities?.workspace?.configuration;
  ```
  Store it in a `let` visible to the handlers below (declare `let supportsConfiguration = false;` in the outer scope and assign it here).
- [ ] **F1.2** In `connection.onInitialized` (currently empty), load the config once:
  ```ts
  connection.onInitialized(async () => {
    if (supportsConfiguration) {
      await loadCompletionConfig(connection);
    }
  });
  ```
- [ ] **F1.3** Register a configuration-change handler (reload when supported, else reset to defaults):
  ```ts
  connection.onDidChangeConfiguration(async () => {
    if (supportsConfiguration) {
      await loadCompletionConfig(connection);
    } else {
      invalidateCompletionConfig();
    }
  });
  ```
  Add `invalidateCompletionConfig` to the import from `./config-completion.js` (line 54).
- [ ] **F1.4** Confirm the completion handler still reads `getCompletionConfig()` (it does, `server.ts:805`). Leave the `opts.completionAutoImport ?? config.autoImport` line — the `opts` override stays useful for unit tests.
- [ ] **F1.5** Decide `preselectFullyQualified`: DONE says "both completion settings are honoured," but nothing consumes it. Either (a) wire it into the reference-completion ordering (preselect the FQN item when true), or (b) if it's deferred, say so in the PR and update `H3-symbols-settings.md` DONE to scope it out. Don't leave it silently dead.
- [ ] **F1.6** Verify (the proof that F1 works): with a client that answers `workspace/configuration` with `false` for `autoImport`, a reference completion at an unimported symbol returns items with **no** `additionalTextEdits`. (See F3 — make this the test.)

## F2 [High] — Make `pnpm --filter @modeler/lsp lint` pass

Remove the unused imports (`@typescript-eslint/no-unused-vars`):

- [ ] **F2.1** `server.ts:54` — `loadCompletionConfig` becomes used after F1; if you also import `invalidateCompletionConfig`, ensure it's used too. No unused symbols left on this line.
- [ ] **F2.2** `document-symbol.ts:5` — remove `Location` from the `vscode-languageserver` import.
- [ ] **F2.3** `document-symbol.ts:7` — remove `PackageDecl` from the `@modeler/parser` import.
- [ ] **F2.4** `document-symbol.test.ts:5` — remove the unused `import { parseString } from '@modeler/parser';`.
- [ ] **F2.5** `config-completion.ts` — if `ConfigurationItem` is no longer referenced after F1, remove it; otherwise leave it.
- [ ] **F2.6** Run `pnpm --filter @modeler/lsp lint` → **0 errors**.

## F3 [Med] — Test the config through a real completion, not the helper

- [ ] **F3.1** Rewrite the `config-completion.test.ts` "autoImport: false" case (or add a server-level test in `completion.test.ts`): boot `createServerConnection`, register a client `workspace/configuration` responder returning `[false]`, `initialize` with `capabilities.workspace.configuration = true`, send `initialized`, open a two-file scenario where file B references an unimported symbol from package A, request `textDocument/completion` at that reference, and assert the matching item has **no** `additionalTextEdits`. Then flip the responder to `[true]` and assert the import edit **is** present. This pins F1 so it can't regress.

---

## Low

- [ ] **F4** — In `document-symbol.test.ts` case 1, also assert the attribute level: the `artikl` symbol has a child named `kod` with `kind === SymbolKind.Field`. (Matches the spec's "attribute children (kind: Field)".)
- [ ] **F5** — In `server.ts` `onWorkspaceSymbol`, scope the package filter precisely: match `qnameLower.startsWith(prefix + '.')` (where `prefix` is the query minus its trailing `.`) so `"billing."` doesn't also match a sibling package such as `billingsystem.*`.

---

## Done when

- [ ] A reference completion honours `modeler.completion.autoImport` end-to-end: `false` ⇒ no `additionalTextEdits`, `true` ⇒ import edit present — asserted by a server-level test (F3).
- [ ] `preselectFullyQualified` is either honoured or explicitly deferred in the docs (F1.5).
- [ ] `pnpm --filter @modeler/lsp lint` is clean.
- [ ] `pnpm --filter @modeler/lsp test && pnpm --filter @modeler/integration-tests test && pnpm --filter @modeler/vscode-ext test && pnpm -r typecheck` all green.
