# Review 042 — Section E2 re-review (after `tasks-review-041`)

**Date:** 2026-05-22
**Scope:** re-review of the create-graph wizard after the developer reported the `tasks-review-041` fixes done. Verified against runtime (designer tests + typecheck + code read). Companion: [`tasks-review-042.md`](tasks-review-042.md).
**Verdict:** **Core fixed — sign-off pending a F3/F4 decision.** Both High blockers are resolved and verified: the wizard now actually creates a graph (the `createGraph` `WorkspaceEdit` is applied) and the name is validated as an identifier. The two Medium deviations (F3 Step-2 dependency graph, F4 Step-3 grouping/bulk-select) are **still neither implemented nor descoped** — the task doc `E2-create-wizard.md` continues to specify them while the code ships plain lists. Decide implement-or-descope on those and E2 is done.

> Suite green: designer **97** tests (+2), typecheck clean.

---

## Fixed and verified

- **F1 [High] — the `createGraph` edit is now applied.** `handleSave` extracts the `CreateFile` op + the `TextEdit` `newText` from `documentChanges` and calls `lspClient.openDocument(createOp.uri, newText)` before `onComplete` → `handleSelectGraph`. So the new `.ttrg` exists in the LSP store and `getGraph` can load it. The create flow now produces a real, loadable graph.
- **F2 [High] — name is validated as an identifier.** `IDENT_REGEX = /^[A-Za-z_][A-Za-z0-9_]*$/`; `canNext()` gates step 5 on it; the Save button is `disabled={state.step === 5 ? !canNext() : …}`; an inline message ("Name must be a valid identifier: letters, digits, underscore; no spaces") shows on invalid input. Two new tests cover invalid→error+disabled and valid→enabled.
- **F5 [Low] — errors surfaced.** `handleSave` now routes `createGraph` failure, empty `documentChanges`, and `openDocument` failure through an `onError` callback and returns without navigating. No more silent jump-to-empty.

---

## Still open

### F3 [Med] — Step 2 dependency mini-graph not implemented, and not descoped

Step 2 is still a checkbox list; there is no embedded Cytoscape `PackageGraph` view. That would be acceptable *if it were a documented decision* — but `E2-create-wizard.md` was not touched: E2.3 and the Tests-first still say *"Embed a small Cytoscape canvas showing `PackageGraph` with selected packages highlighted."* So the spec and the code disagree. (The transitive-add logic itself is correct and tested.)

### F4 [Med] — Step 3 grouping + bulk-select not implemented, and not descoped

Step 3 is still a flat list with per-row checkboxes; no grouping by package/schema kind, no bulk-select controls. Again `E2-create-wizard.md` E2.4 still specifies them. Spec vs code mismatch persists.

> This is the same doc-vs-code drift pattern flagged earlier in the project (e.g. the D4/`.ttrl` decision): a task list says X, the code does Y, and neither is reconciled. `tasks-review-041` explicitly asked to *"implement **or** formally descope in `E2-create-wizard.md`"* — please do one.

### F1.3 [Low] — the round-trip test is weaker than requested

The new tests assert `createGraph` is called and `onComplete` fires, and the mock returns realistic `documentChanges`. But no test asserts `openDocument` was called with the created content, nor that the generated body parses cleanly. The F1 wiring is correct by inspection, but the test would still pass if `openDocument` were dropped. Strengthen it: assert `openDocument` is called with `createOp.uri` + the `newText`, and (ideally) that `parseString(newText, uri)` has zero errors.

---

## Recommendation

The functional blockers are genuinely closed — E2 now creates a valid, loadable graph with a validated name, which is the point of the section. What remains is a **decision**, not a bug: implement F3 (Cytoscape dep view) and F4 (grouping/bulk-select), or descope them by editing `E2-create-wizard.md` so the spec matches the shipped checkbox-list UX. Either is fine; leaving the doc claiming features the code doesn't have is not. Optionally strengthen the F1 test (F1.3). `tasks-review-042.md` lists these.
