# Tasks — review-039 (Section D)

Findings in [`review-039.md`](review-039.md). **D.2/D.3/D.4/D.5 and CC1 are done — leave them.** Three items remain: the LSP wiring (F1), the double-registration (F2), and the missing tests (F3). F1 is the central deliverable. Don't claim D done until `.ttrg` actually reaches the LSP and the new tests assert it.

> Suite is green but vscode-ext still has only 7 tests — no D tests ran. Add the unit tests below; they catch F1/F2 without booting VS Code.

---

## F1 [High] — Wire `.ttrg` into the LSP client (D.6)

- [ ] **F1.1** In `packages/vscode-ext/src/extension.ts:23`, change the document selector to include `ttrg`:
  ```ts
  documentSelector: [
    { scheme: 'file', language: 'ttr' },
    { scheme: 'file', language: 'ttrg' },
  ],
  ```
- [ ] **F1.2** Confirm any other place that filters by language id (activation events, `onLanguage:`, file watchers, the `Open in Designer` command's enablement) also accounts for `ttrg`. Grep `packages/vscode-ext/` for `'ttr'` / `language === 'ttr'` / `onLanguage:ttr` and add `ttrg` where the LSP needs the document.
- [ ] **F1.3** Verify (smoke or manual F5): open a `.ttrg` in the Extension Dev Host; the server receives `didOpen` and publishes diagnostics for an intentionally-broken `.ttrg` (e.g. `graph_objects_empty.ttrg`). Without F1 this produces nothing.

## F2 [High] — Stop double-registering `.ttrg` (D.1)

- [ ] **F2.1** In `packages/vscode-ext/package.json`, remove `.ttrg` from the **`ttr`** language's `extensions` so it reads `[".ttr"]`. The `ttrg` language keeps `[".ttrg"]`. After this, exactly one language declares `.ttrg`.

## F3 [High] — Add the required tests (D tests-first)

- [ ] **F3.1 — `packages/vscode-ext/scripts/__tests__/tm-grammar-ttrg.test.ts`** (vitest, runs in `pnpm -r test`). Load `syntaxes/ttrg.tmLanguage.json` and assert:
  - `scopeName === 'source.ttrg'`.
  - the grammar matches the `graph`, `objects`, `layout` keywords (assert the relevant patterns/scopes exist, e.g. `keyword.declaration.graph.ttrg`, `keyword.other.property.ttrg`).
- [ ] **F3.2 — Config/registration unit test** (vitest; e.g. `packages/vscode-ext/scripts/__tests__/language-registration.test.ts`). Load `package.json` and assert:
  - exactly **one** `contributes.languages` entry lists `.ttrg` in its `extensions`, and it is the `ttrg` language (guards against F2 regressing).
  - `contributes.grammars` has a `ttrg` → `source.ttrg` entry.
  - no `.ttrl` extension is registered anywhere.
- [ ] **F3.3 — Smoke `.ttrg` case** (`@vscode/test-electron`; `packages/vscode-ext/src/test/suite/ttrg-registration.test.ts`, run via `test:smoke`). Open a `.ttrg` fixture (add one under `packages/vscode-ext/test-fixtures/` until `samples/v1.1-mini` is wired in) and assert `vscode.window.activeTextEditor?.document.languageId === 'ttrg'`. Optionally assert scope inspection includes `keyword.declaration.graph.ttrg`. (This is the acceptance-gate smoke from the task's Tests-first.)

## Verify by running

```bash
pnpm --filter @modeler/vscode-ext build
pnpm --filter @modeler/vscode-ext test          # F3.1/F3.2 (vitest)
pnpm --filter @modeler/vscode-ext test:smoke     # F3.3 (electron)
pnpm -r typecheck
```

## Done when

- [ ] Opening a `.ttrg` in VS Code gives it the `ttrg` language id **and** the LSP client forwards it to the server (diagnostics appear) — F1.
- [ ] Exactly one language declares `.ttrg`; no `.ttrl` registration remains — F2.
- [ ] `tm-grammar-ttrg` + registration unit tests pass under `pnpm -r test`; the `.ttrg` smoke case passes under `test:smoke` — F3.
- [ ] `pnpm -r build && pnpm -r typecheck && pnpm -r test` green from a clean build.
