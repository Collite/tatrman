# Tasks — review-056 (Section H3, second re-review)

> **STATUS (2026-05-25): all closed — H3 done.** F3 e2e config test added (`completion-config.test.ts`); F1.5 `preselectFullyQualified` deferred + documented. See the "Resolution" section of [`review-056.md`](review-056.md). Gate green: lsp 91 · integration 88(+1) · vscode-ext 24 · lint clean · typecheck 8/8.

Findings in [`review-056.md`](review-056.md). The 055 High blockers are **fixed and verified** (autoImport honoured end-to-end; lint clean; F4/F5 done). Two items remain, both from 055.

---

## F1.5 [Med] — Honour `preselectFullyQualified`, or defer it explicitly

Choose ONE:

- [ ] **Option A — wire it.** In the reference-completion path (`completion-reference.ts` / `getReferenceCompletions`), read `getCompletionConfig().preselectFullyQualified`; when `true`, set `preselect: true` on the fully-qualified-name `CompletionItem` (and leave it off the bare-name item) so the FQN is the default selection. Add an assertion to the test from F3 (or a sibling) that the FQN item has `preselect === true` only when the setting is on.
- [ ] **Option B — defer it.** If preselect is out of scope for v1.1, say so in the PR description and edit `docs/v1-1/plan/tasks/H3-symbols-settings.md` DONE: change "Both completion settings are honoured" to note `preselectFullyQualified` is documented-but-deferred, with a one-line reason. Leaving it silently dead is the only unacceptable outcome.

## F3 [Med] — Add the end-to-end config test

- [ ] **F3.1** Add a server-level test (in `completion.test.ts`, or rewrite the misleading case in `config-completion.test.ts`) that:
  - boots `createServerConnection`, registers a client `workspace/configuration` responder,
  - `initialize` with `capabilities.workspace.configuration = true`, sends `initialized`, waits for the config to load,
  - opens the two-file auto-import scenario (package A defines a symbol; file B references it unimported),
  - requests `textDocument/completion` at the reference,
  - asserts: with the responder returning `autoImport: false`, **no** returned item has `additionalTextEdits`; with `true`, the import edit **is** present.
  - This is exactly the behaviour proven by hand in review-056; codify it so it can't regress again.
- [ ] **F3.2** Fix or delete the old `config-completion.test.ts` case titled "subsequent completion results have no additionalTextEdits" — it tests no such thing. Keep the pure-helper cases (defaults / cache / invalidate) if you like, but the behaviour claim must be backed by F3.1.

---

## Done when

- [ ] `preselectFullyQualified` is honoured (with a test) or explicitly deferred in `H3-symbols-settings.md`.
- [ ] A server-level test proves `autoImport` true⇒import-edit / false⇒no-edit through a real completion.
- [ ] `pnpm --filter @modeler/lsp lint && pnpm --filter @modeler/lsp test && pnpm --filter @modeler/integration-tests test && pnpm --filter @modeler/vscode-ext test && pnpm -r typecheck` all green.
