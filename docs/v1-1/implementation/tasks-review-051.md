# Tasks — review-051 (Section H1: reference completion + auto-import)

Findings in [`review-051.md`](review-051.md). Work top-to-bottom: **F1/F2** are the blockers (auto-import corrupts files and is untested), **F3–F6** are spec requirements not met, **F7–F10** are cleanups. H1 is done when F1–F6 are closed and the tests (which must include auto-import + sort order) are green.

Do exactly what's written. **Verify with a two-package fixture and by applying the `additionalTextEdits`** — a single-file fixture cannot exercise auto-import.

---

## F1 [High] — Fix the auto-import edit so it inserts a valid `import` line

The bug: `buildImportTextEdit` computes a string offset (`insertionPointToOffset`) but emits it as `range: { start: { line: 0, character: insertOffset } }`. Offsets are not positions, so the import gets welded onto line 0 (verified: `package billing.invoicingimport billing.products`).

- [ ] **F1.1** In `import-edits.ts`, produce the edit as a real LSP position. Either convert the byte offset to `{ line, character }` (count newlines before the offset), or — simpler and less error-prone — decide the **insertion line** and emit a whole-line insert at `{ line: L, character: 0 }` with `newText` = `` `import ${target}\n` ``.
- [ ] **F1.2** Insertion-point rules: if the file has an import block, insert in alphabetical order **within** it (on its own line); if it has imports but the new one sorts last, insert after the last import line; if there are no imports, insert on the line **after** the `package` declaration (with a blank line if needed), before `schema`/`graph`/`def`. Never on the `package` line itself.
- [ ] **F1.3** Verify by applying: after the edit, the file must contain `import <pkg>` on its own line and still parse with 0 errors. (Cover the no-imports case AND the existing-import-block case.)

## F2 [High] — Test auto-import and sort order (a two-package fixture)

- [ ] **F2.1** In `completion.test.ts` (rename to `completion-reference.test.ts` per F10, or keep — but cover this): open **two** files — package `billing.products` defining `produkt`, and package `billing.invoicing` with a relation whose `from:`/`to:` cursor is in a reference position. Request completion; find the `produkt` item; assert it is bucket "unimported", has `additionalTextEdits` with exactly one edit, and that **applying** that edit to the source yields a file containing `import billing.products` on its own line that parses cleanly. (This fails today — it pins F1.)
- [ ] **F2.2** Assert sort order: with same-package + named-import + wildcard + unimported candidates present, assert the items' `sortText` (or order, once F4 emits `sortText`) groups them same-package → named-import → wildcard → unimported.
- [ ] **F2.3** Add the integration-test auto-import assertion the spec requires (open a project with two packages; assert ≥1 item with `additionalTextEdits`).

## F3 [Med] — Correct `CompletionItemKind`

- [ ] **F3.1** In `formatCandidate`, use `CompletionItemKind.Reference` (18) from `vscode-languageserver`, not the literal `12` (Value).
- [ ] **F3.2** Update the unit + integration tests that assert `kind === 12` to assert `Reference` (18).

## F4 [Med] — Honor sort order via `sortText`

- [ ] **F4.1** In `formatCandidate`, set `sortText` with a bucket-rank prefix so the client preserves the grouping, e.g. `sortText = `${rank}_${entry.name}`` where rank is `0` same-package, `1` named-import, `2` wildcard, `3` unimported.
- [ ] **F4.2** Stop letting `fuzzysort` reorder across buckets: either sort within each bucket and concatenate, or use the query only as a filter/tiebreak while keeping `sortText` authoritative for grouping.

## F5 [Med] — Use the typed prefix as the query, not the trigger character

- [ ] **F5.1** In `server.ts` `onCompletion`, compute the query from the source text immediately before the cursor (the partial identifier being typed), not from `params.context.triggerCharacter`. If you can't cheaply extract a prefix, pass `''` and let the client filter — do **not** pass `'.'`.

## F6 [Med] — Read `modeler.completion.autoImport` from client config

- [ ] **F6.1** Wire `modeler.completion.autoImport` (default `true`) through `workspace/configuration` / `onDidChangeConfiguration`, per the existing v1 settings pattern, and feed it into the `autoImport` arg — instead of relying solely on the static `opts.completionAutoImport`. Add a test that with the setting `false`, unimported items have **no** `additionalTextEdits`.

---

## Low

- [ ] **F7** — Collapse the identical `matchDefProperty` / `matchReferencePropertyPosition` into one helper; remove the redundant second loop in `detectReferenceProperty`.
- [ ] **F8** — Either use the file's `schemaDirective.schemaCode` to scope `allowedKinds` (so `from:` in an `er` file doesn't offer `table`/`view`), or remove the unused `schemaCode`/`namespace` fields from `ContextInfo`.
- [ ] **F9** — Verify completion at an empty value position (`from: ` with nothing typed, Ctrl-Space). If it returns nothing, extend detection to handle the cursor sitting after `from:`/`to:`/etc. with no parsed value node yet.
- [ ] **F10** — Rename `completion.test.ts` → `completion-reference.test.ts` (spec). Either drop `resolveProvider: true` or add an `onCompletionResolve` handler.

---

## Done when

- [ ] Selecting an unimported candidate inserts a valid `import` line (own line, alphabetical, parses) — verified by applying the edit in a test (F1/F2.1).
- [ ] Tests cover auto-import (`additionalTextEdits`) and sort order; the `kind` assertion is `Reference`.
- [ ] Items carry `sortText` so the bucket order survives client sorting; the query is the typed prefix.
- [ ] `modeler.completion.autoImport` is read from client config and honored (tested both ways).
- [ ] `pnpm --filter @modeler/lsp test && pnpm --filter @modeler/integration-tests test && pnpm -r typecheck` all green.
