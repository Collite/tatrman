# Review 039 — Section D (VS Code extension: `.ttrg` registration, `.ttrl` removal)

**Date:** 2026-05-21
**Scope:** D (`docs/v1-1/plan/tasks/D-vscode-ext.md`) — register `.ttrg` as a language, remove `.ttrl`, wire the LSP client, smoke test. Verified against runtime (clean build, package.json/grammar/extension inspection). Companion: [`tasks-review-039.md`](tasks-review-039.md).
**Verdict:** **Changes requested — D is not complete.** The grammar and `.ttrl` removal are done well, and the carry-over CC1 highlighting bug is fixed. But the **functional core of D is missing**: the LSP client's `documentSelector` was never updated, so VS Code recognises `.ttrg` files but the LSP never receives them — no diagnostics, no graph methods, nothing works for `.ttrg`. `.ttrg` is also double-registered across two languages, and **both** required test files (the ones that would have caught these) were not written.

> Suite is green from a clean build (vscode-ext 7, integration 63 | 1 skipped) — but vscode-ext is still at **7 tests / 1 file**, i.e. no new D tests ran. As before, green ≠ done.

---

## Done correctly (verified)

- **D.2 — `ttrg.tmLanguage.json` generated.** `scopeName: source.ttrg`; repository has `keywords` (no dangling include) with `keyword.declaration.graph.ttrg`, `keyword.other.property.ttrg`, `keyword.other.schema.ttrg`, `keyword.control.ttrg`; the `graph`/`objects`/`layout` keywords are present.
- **D.3 — grammar registered.** `contributes.grammars` has `{ language: 'ttrg', scopeName: 'source.ttrg', path: './syntaxes/ttrg.tmLanguage.json' }`.
- **D.4 — icon.** `packages/vscode-ext/icons/ttrg.svg` exists and is referenced.
- **D.5 — `.ttrl` removed.** `ttrl.svg`, `language-configuration-ttrl.json`, `schemas/ttrl.schema.json`, `syntaxes/ttrl.tmLanguage.json` deleted; no `.ttrl` reference remains in `packages/vscode-ext/`.
- **CC1 (review-032 task 4) — fixed.** `ttr.tmLanguage.json` now emits every `keyword_*` repository block its includes reference (no dangling includes), so property keywords (`schema`, `objects`, `layout`, `search`, …) will actually highlight. Good — this had been outstanding for several reviews.

---

## Findings

### F1 [High] — D.6 not done: the LSP client still ignores `.ttrg`

`packages/vscode-ext/src/extension.ts:23`:
```ts
documentSelector: [{ scheme: 'file', language: 'ttr' }],
```
`ttrg` was never added. D.6 required `[{ scheme: 'file', language: 'ttr' }, { scheme: 'file', language: 'ttrg' }]`, and DONE-when states *"The LSP client receives `.ttrg` documents and forwards them to the server."* As shipped, opening a `.ttrg` in VS Code gives it the `ttrg` language id and TextMate colours, but the language client never sends `didOpen`/`didChange` to the server — so **`.ttrg` files get no diagnostics, no `getGraph`, no graph methods at all** in VS Code. This is the central deliverable of D and it's missing.

### F2 [High] — D.1: `.ttrg` is double-registered across two languages

`contributes.languages` declares:
```jsonc
{ "id": "ttr",  "extensions": [".ttr", ".ttrg"], … }   // <-- .ttrg should not be here
{ "id": "ttrg", "extensions": [".ttrg"], … }
```
Two languages claim `.ttrg`. D.1 said add a **separate** `ttrg` language for `.ttrg`; it did not say add `.ttrg` to the `ttr` language. With both claiming the extension, the language a `.ttrg` file resolves to is ambiguous (VS Code picks one by internal precedence), which can silently give a `.ttrg` file the `ttr` language id and the wrong grammar — and would make the smoke assertion `languageId === 'ttrg'` unreliable. Remove `.ttrg` from the `ttr` language's `extensions`.

### F3 [High] — tests-first not written; smoke test doesn't cover `.ttrg`

Both files the task required are absent:
- `packages/vscode-ext/src/test/suite/ttrg-registration.test.ts` — **missing**. The existing `extension.smoke.test.ts:54` still asserts `doc.languageId === 'ttr'`; there is no `.ttrg` open/scope case.
- `packages/vscode-ext/scripts/__tests__/tm-grammar-ttrg.test.ts` — **missing** (only the v1 `generate-tm-grammar.test.ts` exists).

This is why F1/F2 shipped: a `tm-grammar-ttrg` unit test would assert `scopeName === 'source.ttrg'` and the keyword patterns, and a cheap package.json/extension unit test would catch the double-registration (F2) and the missing `documentSelector` entry (F1) **without** booting VS Code. The smoke (`@vscode/test-electron`) test isn't part of `pnpm -r test`, but the grammar/config unit tests are — write those.

---

## Status summary

| Item | Status |
|------|--------|
| D.1 register `ttrg` language | ◐ present, but `.ttrg` also wrongly on the `ttr` language (F2) |
| D.2 `ttrg.tmLanguage.json` | ✅ |
| D.3 grammar registration | ✅ |
| D.4 `.ttrg` icon | ✅ |
| D.5 remove `.ttrl` | ✅ |
| D.6 LSP `documentSelector` += ttrg | ❌ not done (F1) |
| Tests-first (smoke + tm-grammar-ttrg) | ❌ missing (F3) |
| CC1 TextMate property highlighting | ✅ fixed |

## Recommendation

D is close on the static-registration side but the functional wiring (F1) is the whole point — `.ttrg` must reach the LSP. Fix F1 and F2 (both one-liners), then add the unit tests (F3) that prove `.ttrg` maps to exactly one language, the client selects it, and the grammar scope is `source.ttrg`. The smoke `.ttrg` case is nice-to-have on top. `tasks-review-039.md` has the steps.
