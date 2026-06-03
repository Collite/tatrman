# Tasks — review-058 (Sections I.2 / I.3 / I.4)

> **STATUS (2026-05-25): all closed.** M1 (`'comma'` forces one line + test), M2 (extract documented same-package-only), and L1–L6 (spec DONE reconciled, alignKeys test hardened) are done. See the "Resolution" section of [`review-058.md`](review-058.md). Gate: lsp 130 · integration 92(+1 skip) · typecheck 8/8 · lint clean.

Findings in [`review-058.md`](review-058.md). I.2–I.4 are implemented, wired, and green across the whole repo. No correctness blockers — these are behaviour decisions (M) and tracking/cleanup (L). Do M1/M2 (they change shipped behaviour); the L items are documentation/test hardening + tickets.

---

## Medium

- [ ] **M1 — Define `'comma'` separator semantics.** In `formatter/format.ts` `formatDef`, `'comma'` currently maps to a width-decided group (a too-wide def still breaks). Choose one:
  - (a) Make `'comma'` force a single line regardless of width (render the property list flat), or
  - (b) Keep width-sensitivity and reword the setting description in `vscode-ext/package.json` + the I2 spec to "inline when it fits the width."
  Add a test asserting the chosen behaviour at a narrow `width`.
- [ ] **M2 — Decide `refactor.extract` package scope.** `code-actions.ts` `refactorExtractDefToNewFile` always extracts into the same package directory and never adds an import. Either:
  - (a) Document that extract is same-package-only (update I3.6 / the action title), or
  - (b) Implement the cross-package case: when extracting into a *different* package, add `import <pkg>.<schema>.<ns>.<name>` to the source file if a reference remains (I3.6's last bullet).

---

## Low (tracking / hardening)

- [ ] **L1 — Comment preservation.** File a follow-up: the formatter cannot preserve comments until `@modeler/parser` exposes them (lexer currently `-> skip`s `//` and `/* */`). Update I2's DONE checklist to mark comment-preservation as deferred-pending-CST, with the reason.
- [ ] **L2 — Golden fixtures (optional).** If the `samples/format/*.in.ttr`/`.out.ttr` golden pairs are wanted per I2.7, add a few (entity+attributes, table+columns+indices, relation+cardinality+join, a "messy" input) and a test asserting `format(in) === out`. Otherwise note in the I2 spec that inline + sample-idempotency tests replace them.
- [ ] **L3 — Document first-format reordering.** In G's formatting docs, note that the formatter canonicalises property order (so the first format of an existing file may produce a large reordering diff).
- [ ] **L4 — Note the indexed-docs limitation.** Document that "N references" / "N files in package" (and find-references / workspace symbols generally) reflect *opened* documents in this LSP; a project-wide eager index is a separate enhancement.
- [ ] **L5 — Strengthen the `alignKeys` test.** In `formatter.test.ts`, assert the padded alignment (e.g. that `description:` and `nameAttribute:` values start at the same column), not just key presence.
- [ ] **L6 — `listPackageFiles` (optional).** No automated test is feasible without an Extension Host; verify manually via F5 (click the "N files in package" lens → quick-pick → open).

---

## Done when

- [ ] M1 and M2 are resolved (behaviour fixed or documented, with a test for M1).
- [ ] I2's and I3.6's DONE checklists are reconciled with what shipped (L1, L2, M2).
- [ ] `pnpm -r test && pnpm -r typecheck && pnpm -r lint` remain green.
