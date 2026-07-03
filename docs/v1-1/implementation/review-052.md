# Review 052 — Section H1 re-review (after `tasks-review-051`)

**Date:** 2026-05-22
**Scope:** re-review of Section 1.1.H.1 after the developer reported the `tasks-review-051` fixes done. Verified against runtime: `pnpm --filter @modeler/lsp test` (64), `integration-tests` (82/1-skip), `typecheck`/`lint`/`build` clean, plus an end-to-end probe of auto-import (both `autoImport` true and false) applying the returned edit and re-parsing. Companion: [`tasks-review-052.md`](tasks-review-052.md).
**Verdict:** **Done.** Every High and Medium finding from review-051 is fixed and verified; the Lows are addressed too. Auto-import now inserts a valid `import` line (verified: applied edit re-parses with 0 errors), the setting is honored, the item kind and sort order match the spec, and the gaps that hid the original corruption are now covered by tests.

---

## Fixed and verified

- **F1 [was High] — auto-import no longer corrupts the file.** `import-edits.ts` now emits whole-line edits at `{ line, character: 0 }` with proper line computation (`findInsertionLineForFirstImport` / alphabetical insertion among existing imports). End-to-end probe (file in `billing.invoicing`, completing the unimported `billing.products…produkt`): edit `{line:1,char:0}` + `"import billing.products\n"`, applied →
  ```
  package billing.invoicing
  import billing.products
  schema er namespace entity
  ```
  **re-parses with 0 errors** (was `package billing.invoicingimport billing.products`). Fixed.
- **F2 [was High] — auto-import + sort order are now tested.** `import-edits` unit tests apply the edit and assert it parses (and the no-imports + existing-block + already-present cases); the integration `auto-import` test asserts an unimported candidate carries `additionalTextEdits` inserting `import billing.products` on its own line (`range.start.character === 0`, `line > 0`); a `sortText` test asserts same-package items rank `0_`.
- **F3 [was Med] — `CompletionItemKind.Reference` (18).** `formatCandidate` uses the enum; both test files now assert `18`.
- **F4 [was Med] — sort order honored.** `sortText = `${BUCKET_RANK[bucket]}_${name}`` so the client preserves same-package → named-import → wildcard → unimported regardless of `fuzzysort`'s internal array order.
- **F5 [was Med] — real prefix as query.** `onCompletion` uses `extractQueryPrefix(content, position)` instead of the trigger character.
- **F6 [was Med] — setting honored.** `onCompletion` reads `modeler.completion.autoImport` via `workspace/configuration` (falling back to the static default). Probe confirms: `true` → `additionalTextEdits` present; `false` → `null`.
- **F7 [was Low] — duplication removed.** The identical `matchDefProperty`/`matchReferencePropertyPosition` pair is consolidated to one.
- **F8 [was Low] — schema-aware.** Detection now reads `doc.schemaDirective?.schemaCode`.
- **F9 [was Low] — empty-value position.** `detectEmptyReferencePosition` handles the cursor sitting after `from:`/etc. with no parsed value yet.
- **F10 [was Low] — `completion-reference.test.ts` added.**

> Suites: lsp 64 (+8), integration 82 (+1) — and the new tests assert the behaviors (auto-import edit, kind, sort) that were previously unguarded.

---

## Minor (non-blocking, optional)

- The original `completion.test.ts` was kept alongside the new `completion-reference.test.ts` (rather than renamed), so there's a little duplicated test scaffolding. Harmless — fold or drop the redundant cases whenever convenient.
- `completionProvider.resolveProvider: true` is still advertised without an `onCompletionResolve` handler. No functional impact (the items are already fully populated); drop the flag or add a no-op resolve if you want to be tidy.

---

## Recommendation

H1 is complete and correct. The headline auto-import works end-to-end with valid output, the `modeler.completion.autoImport` setting is wired through `workspace/configuration`, item kind and sort order match the spec, and the test suite now covers exactly the behaviors that were broken before. Sign off and proceed to H2. The two minor items above are tidy-ups, not blockers.
