# Tasks — review-042 (Section E2 re-review)

Findings in [`review-042.md`](review-042.md). **F1, F2, F5 are fixed — leave them.** What's left is a decision on F3/F4 (the doc and code currently disagree) plus an optional test strengthening. E2 is done once F3/F4 are reconciled.

---

## F3 / F4 — Decide: implement, or descope in the task doc

For **each** of these, do exactly one of (a) implement or (b) descope-in-doc. Don't leave `E2-create-wizard.md` describing behavior the code doesn't have.

### F3 — Step 2 dependency mini-graph
- [ ] **(a) Implement:** embed a small Cytoscape `PackageGraph` panel in Step 2 with the selected packages highlighted (pattern in `Canvas.tsx`). Keep the working "Add all transitive" / "Continue" actions. Add a test that the panel renders the selected packages.
- [ ] **(b) Descope:** edit `E2-create-wizard.md` — rewrite E2.3 and the Step-2 Tests-first bullet to describe the checkbox-list + "Add all transitive" UX that shipped, with a one-line note on why the Cytoscape view was deferred (and where it'll land, if anywhere).

### F4 — Step 3 grouping + bulk-select
- [ ] **(a) Implement:** group the object list by package and schema kind; add bulk-select-package / bulk-select-schema controls (keep the "N of M" counter). Add a test for a bulk-select.
- [ ] **(b) Descope:** edit `E2-create-wizard.md` E2.4 to describe the flat checkbox list + counter that shipped, with the reason.

## F1.3 [Low] — Strengthen the create round-trip test (optional but recommended)

- [ ] **F1.3.1** In `create-graph-wizard.test.tsx`, assert that on Save `client.openDocument` is called with `createOp.uri` and the `newText` from the returned edit (so the test fails if the apply-edit wiring regresses).
- [ ] **F1.3.2** Optionally assert the generated body parses: `expect(parseString(newText, uri).errors).toHaveLength(0)`.

---

## Done when

- [ ] F3 and F4 are each either implemented (with a test) or descoped in `E2-create-wizard.md` so the doc matches the code.
- [ ] (Optional) the create test asserts `openDocument` is called with the created content.
- [ ] `pnpm --filter @modeler/designer test && pnpm --filter @modeler/designer typecheck && pnpm --filter @modeler/designer build` green.
