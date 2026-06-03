# Review 051 — Section H1 (Reference completion + auto-import)

**Date:** 2026-05-22
**Scope:** review of Section 1.1.H.1 against [`H1-reference-completion.md`](../plan/tasks/H1-reference-completion.md). Verified against runtime: `pnpm --filter @modeler/lsp test` (56 pass), `integration-tests` (82/1-skip), `pnpm -r typecheck` (clean), plus a full read of `completion-reference.ts` / `import-edits.ts` / the server wiring / both test files, and an end-to-end probe of the auto-import path. Companion: [`tasks-review-051.md`](tasks-review-051.md).
**Verdict:** **Not done.** Reference completion fires in `from:`/`to:` and returns bucketed candidates — but the **headline feature, auto-import, corrupts the file** (verified: it glues the `import` onto the `package` line), and it's **completely untested**, which is why the corruption was reported "ready." Several spec requirements are also unmet: wrong `CompletionItemKind`, sort order not actually honored, the query is the trigger char, and the `autoImport` setting isn't read from client config.

> The 5 completion tests are green, but none asserts `additionalTextEdits`, the sort order, or the correct item kind — exactly the behaviors that are broken.

---

## High — blockers

### F1 [High] — Auto-import produces corrupted output (verified)

`buildImportTextEdit` (`import-edits.ts`) computes a **string offset** via `insertionPointToOffset` (e.g. `content.indexOf('schema ')`) and then emits it as an **LSP line/character position**:

```ts
range: { start: { line: 0, character: insertOffset }, end: { line: 0, character: insertOffset } }
```

Offset ≠ `(line, character)`. End-to-end probe — file in `billing.invoicing`, completion offers the unimported `billing.products.er.entity.produkt`, apply its `additionalTextEdits`:

```
EDIT RANGE: {"start":{"line":0,"character":27}} newText: "import billing.products\n"

APPLIED RESULT:
package billing.invoicingimport billing.products      ← corrupted (no newline, glued to package line)
schema er namespace entity
...
```

`character: 27` is the offset of `schema `, dropped onto line 0 (clamped to end) → the import is welded onto the package declaration, yielding invalid syntax. The whole `insertionPointToOffset`/range computation conflates byte offsets with positions. Auto-import — the defining feature of H1 ("selecting a suggestion from an unimported package inserts both the reference and the import") — does not work. Fix: produce the edit as a real `Position` (convert offset→{line,character}, or compute the insertion line directly and insert a full line), and add a newline so the import lands on its own line in the import block.

### F2 [High] — Auto-import and sort order are untested (this is what hid F1)

The Tests-first explicitly requires: *unit* — "for an unimported suggestion, `additionalTextEdits` contains a `TextEdit` that inserts an `import` line"; *integration* — "assert … at least one auto-import item." **Neither test asserts `additionalTextEdits` at all.** Worse, the fixtures open only one file, so there's no second-package symbol to be "unimported" — auto-import literally cannot fire in the tests. The sort-order case ("same-package first, then named-import, then wildcard, then unimported") is also untested. A test that opened a second package and asserted the import edit would have caught F1 immediately.

---

## Medium

### F3 [Med] — Wrong `CompletionItemKind`

`formatCandidate` returns `kind: 12` (`CompletionItemKind.Value`). The spec (and Tests-first) require **`Reference` (18)**. It's a magic number, and both tests assert `kind === 12`, enshrining the wrong value. Use `CompletionItemKind.Reference` from `vscode-languageserver`.

### F4 [Med] — Sort order is built but never honored

`buildCandidates` returns candidates in bucket order (same-package → named-import → wildcard → unimported), but:
- `formatCandidate` sets **no `sortText`**, so LSP clients ignore array order and sort by `label` — the bucket grouping never reaches the user.
- When a `query` is present, `fuzzysort.go(...)` re-sorts by fuzzy score, destroying the buckets entirely.

The Tests-first sort requirement is effectively unmet. Emit `sortText` with a bucket-rank prefix (e.g. `` `0${name}` `` … `` `3${name}` ``) so the order survives client-side sorting, and apply the query as a tiebreak within that, not as a replacement.

### F5 [Med] — `query` is the trigger character, not the typed prefix

`server.ts:733` sets `query = (triggerKind===2 && triggerCharacter) ? triggerCharacter : ''`. So a `.`-triggered completion runs `fuzzysort.go('.', …)` — matching a literal dot against qnames, which is meaningless filtering/ordering (and is what the tests exercise). It should extract the partial identifier immediately before the cursor (or pass no query and let the client filter the returned list).

### F6 [Med] — `modeler.completion.autoImport` not read from client config

H1.6 / DONE require the setting be honored via `workspace/configuration`. It exists only as a static server-construction option (`opts.completionAutoImport`), with no `workspace/configuration` / `onDidChangeConfiguration` read. A user toggling `modeler.completion.autoImport` in VS Code has no effect.

---

## Low

- **F7** — `matchDefProperty` (`:182`) and `matchReferencePropertyPosition` (`:106`) are byte-identical; `detectReferenceProperty` runs both over the defs redundantly. Collapse to one.
- **F8** — `schemaCode` is guessed from `def.kind`, threaded through `ContextInfo`, and **never used** (filtering is purely property→kinds). Result: `from:` in an `er` file also offers `table`/`view` candidates (cross-schema over-suggestion). Either scope `allowedKinds` by the file's `schemaDirective.schemaCode`, or drop the dead field.
- **F9** — Position detection requires the cursor to be inside an already-parsed value node (`isInRange(pv.source)`). At an empty `from: ` (cursor after the colon, nothing typed — the most common Ctrl-Space case), there's likely no value node → no completions. Verify and handle the empty-value position.
- **F10** — Required test file is `completion-reference.test.ts`; delivered `completion.test.ts`. `completionProvider.resolveProvider: true` is advertised but there's no `onCompletionResolve` handler.

---

## What's good

- The bucket model (same-package / named-import / wildcard-import / unimported) is the right design, and `buildCandidates` dedupes via a `seen` set and walks imports correctly.
- Position detection covers `from:`/`to:` in id/list/object value shapes, plus the other reference properties, and correctly returns `null` outside reference positions (the "empty when not in a reference position" tests pass).
- `getByPackage` / `packageOfImport` are reused from semantics rather than re-derived.
- `isIncomplete` + `limit` (H1.7) are wired.

---

## Recommendation

The shape is right but the load-bearing feature is broken and unguarded. Fix order: (1) make `buildImportTextEdit` emit a correct position (offset→line/character) so auto-import inserts a valid `import` line — F1; (2) write the tests the spec asked for — a second-package fixture asserting `additionalTextEdits` inserts the right import at the right place, and a sort-order assertion — F2 (these pin F1/F4); (3) fix the item kind to `Reference` — F3 (update the tests off `12`); (4) honor sort order via `sortText` — F4; (5) use the real prefix as the query — F5; (6) read `modeler.completion.autoImport` from client config — F6. Then the Low cleanups. `tasks-review-051.md` has the steps.
