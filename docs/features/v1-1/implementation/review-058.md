# Review 058 — Section I.2 / I.3 / I.4 (formatting, code actions, code lens + semantic tokens)

**Date:** 2026-05-25
**Scope:** review of Sections 1.1.I.2, I.3, I.4 against [`I2-formatting.md`](../plan/tasks/I2-formatting.md), [`I3-code-actions.md`](../plan/tasks/I3-code-actions.md), [`I4-codelens-semantic.md`](../plan/tasks/I4-codelens-semantic.md). (These were implemented in one pass; this is an independent verification.) Verified against runtime:

- Full repo suite: parser 109 · semantics 108 · edit 60 · migrate 23 · **lsp 130** · vscode-ext 24 · designer 129 · integration 92 (+1 skip).
- `pnpm -r typecheck` 8/8 · lint clean (lsp, semantics, vscode-ext).
- Formatter: idempotent + parse-clean on **all 25 v1.1 sample files**. Probed `alignKeys` (pads correctly) and `'comma'` (width-sensitive).

Companion: [`tasks-review-058.md`](tasks-review-058.md).

**Verdict:** **Done, with tracked gaps.** All three sub-phases are implemented, wired, and tested, and the whole repo is green. The architecture is clean (formatter IR/renderer/AST-walk are well-separated; code-actions and code-lens are small pure modules; semantic-tokens reuse the reference index). No correctness blockers. What's left are honest deviations from the task DONE lists — none block "code complete," but several should be ticketed rather than silently closed.

---

## What's good (verified)

- **Formatter (I2.1–I2.4, I2.6, I2.7)** — clean three-layer design (`ir.ts` builders, `render.ts` width-based renderer with a `verbatim` node for multi-line literals, `format.ts` AST→IR). The idempotency strategy — reformat structure, slice every value verbatim from source via offsets — is sound and proven idempotent on every sample, including the large metadata `db`/`er`/`query` files and `.ttrg` graphs. Triple-strings preserved exactly. Settings wired; `indentSpaces`/`width`/`alignKeys` work (alignKeys pads keys as specified).
- **Code actions (I3)** — all four quick-fixes + `refactor.extract` are correct and linked to their diagnostics (`diagnostics: [...]`, `isPreferred` where specified). Reuses `buildImportTextEdit` and the I2 formatter (extract builds the new file via `formatDocument` on a synthetic single-def document — neat). 5 end-to-end tests drive the real diagnostic→fix path.
- **Code lens + semantic tokens (I4)** — lenses on every def + package decl with the right command/args; the legend is extended *append-only* (existing indices stable), and references are correctly classified local/imported/unimported (the per-reference named/wildcard-import check is precise). Tokens collected, sorted, and pushed in order.

---

## Medium

### M1 — `'comma'` separator is width-sensitive, not a hard one-line
`I2.4`/Tests-first say `separator: 'comma'` "puts them on one line." The implementation maps `'comma'` to `broken=false`, which renders the property list as a width-decided `group` — so a definition wider than `width` still breaks. Probe: `'comma'` + `width: 30` on a 65-char def breaks onto multiple lines. Either force-inline for `'comma'` (ignore width) or document that `'comma'` means "inline when it fits."

### M2 — `refactor.extract` is same-package only; never adds an import
`I3.6` calls for "a `TextEdit` adding `import …` to the current file if any reference remains." The implementation always creates the new file in the **same package directory**, so same-package references keep resolving and no import is needed — which is correct *for that case*, but the cross-package/import branch of I3.6 is simply not implemented. Fine if extract is intentionally same-package-only; say so, or implement the import-adding path.

---

## Low

- **L1 — Comment preservation (I2 DONE) is unmet, by architecture.** The parser lexer-skips comments (`-> skip` in `TTR.g4`), so the AST carries no comment data and a reprint formatter cannot restore them. Samples have none, so idempotency holds, but I2's "comments preserved verbatim" DONE box cannot be ticked without a parser CST change. Track as a follow-up; update the I2 DONE note meanwhile.
- **L2 — Golden fixtures done inline.** I2.7 asked for `samples/format/<name>.in.ttr`/`.out.ttr` pairs; coverage is instead inline assertions in `formatter.test.ts` + the 25-file sample idempotency suite. Equivalent coverage, but the `samples/format/` fixtures don't exist.
- **L3 — Formatter canonicalises property order.** Formatting reorders each def's properties into a fixed canonical order (needed for determinism). Correct and idempotent, but the *first* format of an existing file can produce a large reordering diff — confirm that's the intended UX, and mention it in G's docs.
- **L4 — Code-lens / semantic-token counts reflect only indexed (opened) documents.** "N references" and "N files in package" come from `refIndex`/`projectSymbols`, which this LSP populates from *opened* documents (a pre-existing trait shared by find-references and workspace symbols). In a fresh VS Code session they undercount until the relevant files are opened. Inherited limitation, not introduced here — note it; a project-wide eager index is the real fix (out of I4 scope).
- **L5 — Weak `alignKeys` unit test.** `formatter.test.ts`'s alignKeys case only asserts the keys are present, not that values are padded/aligned (the behaviour *is* correct — verified by probe). Strengthen the assertion so the feature can't silently regress.
- **L6 — `listPackageFiles` only manually verifiable.** The vscode-ext command handler (quick-pick over `**/*.ttr`) is real but can only be exercised in the Extension Host (F5); no automated coverage. The LSP-side lens data is tested.

---

## Recommendation

Ship it as the code-complete I sub-phase. Address M1 (define `'comma'` semantics) and M2 (decide extract's package scope) since they're behaviour questions; the rest are Low/tracking items — most importantly L1 (comment preservation needs a parser change) and updating the I2/I3 DONE notes to match what shipped. `tasks-review-058.md` lists the concrete follow-ups. The remaining v1.1 release gates (G's docs pass, marketplace publish) are non-code.

---

## Resolution (2026-05-25)

All items closed.

- **M1** ✅ `'comma'` now forces a single line regardless of width (`formatDef` no longer wraps the inline case in a width-decided group). Test asserts it at `width: 20`.
- **M2** ✅ Extract documented as same-package-only (code comment on `refactorExtractDefToNewFile` + I3 spec note); no import needed since the package is unchanged.
- **L1** ✅ I2 DONE updated: comment-preservation marked deferred (needs a parser CST/trivia view); reason recorded.
- **L2** ✅ I2 spec notes inline assertions + the 25-file sample suite replace `samples/format/` golden pairs.
- **L3** ✅ I2 spec notes the first-format property-reordering behaviour.
- **L4** ✅ I4 spec notes the indexed-(opened)-documents limitation on the counts.
- **L5** ✅ `alignKeys` test strengthened — asserts values share a column and the shorter key is padded.
- **L6** ✅ I4 spec notes `listPackageFiles` is Extension-Host-verifiable only.

Gate after fixes: **lsp 130** · integration 92 (+1 skip) · `pnpm -r typecheck` 8/8 · lint clean. (Full repo earlier: parser 109 · semantics 108 · edit 60 · migrate 23 · vscode-ext 24 · designer 129.)
