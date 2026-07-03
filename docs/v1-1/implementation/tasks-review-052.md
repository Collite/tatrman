# Tasks — review-052 (Section H1 re-review)

Findings in [`review-052.md`](review-052.md). **H1 is done** — all High/Medium/Low findings from `tasks-review-051` are fixed and verified. Nothing blocking remains.

---

## Verified fixed (no action needed)

- [x] **F1** — auto-import inserts a valid `import` line (whole-line edit; verified the applied edit re-parses with 0 errors).
- [x] **F2** — auto-import + sort order covered: `import-edits` unit tests (apply→parse), integration `additionalTextEdits` test, `sortText` bucket test.
- [x] **F3** — `CompletionItemKind.Reference` (18); tests assert 18.
- [x] **F4** — `sortText` bucket ranks preserve same-package → named-import → wildcard → unimported.
- [x] **F5** — query is the typed prefix (`extractQueryPrefix`), not the trigger char.
- [x] **F6** — `modeler.completion.autoImport` read via `workspace/configuration`; verified `true`→edit, `false`→none.
- [x] **F7** — duplicate match functions consolidated.
- [x] **F8** — detection scopes by `schemaDirective.schemaCode`.
- [x] **F9** — empty-value position handled (`detectEmptyReferencePosition`).
- [x] **F10** — `completion-reference.test.ts` added.

## Optional tidy-ups (non-blocking)

- [ ] Fold or drop the redundant cases in the original `completion.test.ts` now that `completion-reference.test.ts` exists.
- [ ] Either drop `completionProvider.resolveProvider: true` or add a no-op `onCompletionResolve` handler.

## Gate

- [x] `pnpm --filter @modeler/lsp test` (64) · `integration-tests` (82/1-skip) · `pnpm -r typecheck` · lsp `lint` · lsp `build` — all green.

**H1 signed off. Proceed to H2.**
