# Review 041 — Section E2 (Create-new-graph wizard)

**Date:** 2026-05-22
**Scope:** E2 (`docs/v1-1/plan/tasks/E2-create-wizard.md`) — the 5-step `<CreateGraphWizard>`, its reducer, package/object selection, schema/name steps, and the `createGraph` save path. Verified against runtime (designer tests + typecheck + code trace). Companion: [`tasks-review-041.md`](tasks-review-041.md).
**Verdict:** **Changes requested — the wizard doesn't actually create a usable graph.** The UI shell, the 5 steps, transitions, transitive-package logic, and the RTL tests are well done. But the save path is broken end-to-end: the `WorkspaceEdit` returned by `createGraph` is **never applied**, so no `.ttrg` is created and the post-save "jump into the graph" lands on an empty/null canvas; and the graph **name is not validated as an identifier**, so a name with spaces emits invalid TTR. Two of the DONE-when criteria fail. There are also two spec deviations (Step 2's dep-graph, Step 3's grouping/bulk-select).

> Suite is green (designer **95** tests, +19; typecheck clean). As before, green ≠ done: the RTL tests mock `createGraph` and never assert that a graph is actually created/loadable — see F1/F2.
>
> Caveat: I did not run the Designer in a browser; F1's "empty canvas after create" is a code-trace conclusion (`getGraph(uri)` → `documents.get(uri)` is `undefined` for a never-opened doc → returns `null`). Worth a manual click-through to confirm.

---

## Done well (verified)

- **E2.1 — wizard shell.** `<CreateGraphWizard>` is a 5-step `useReducer` machine with `NEXT`/`BACK`, progress dots, `canNext()` gating (≥1 package on step 1, ≥1 object on step 3), Back/Cancel/Next/Save controls. Clean.
- **E2.2 — Step 1 (packages).** Fetches `getPackageGraph()` on mount; checkbox list with file counts; Next gated on ≥1 selected.
- **Transitive logic.** `ADD_ALL_TRANSITIVE` does a correct BFS over `dependencies` (both directions) — tested.
- **E2.5 — Step 4 (schema).** `er`/`db` radios, `er` default.
- **E2.7/E2.8 — wiring.** `App.tsx` now renders a `creatingGraph` branch (this also closes N1 from review-040 — the picker's "Create New Graph" no longer blanks the screen). `startCreateWizard`/`cancelCreateWizard` work; Cancel returns to the picker.
- **Tests.** `create-graph-wizard.test.tsx` (19 cases) covers every step's UI behaviour, transitions, transitive add, save-calls-createGraph, onComplete, cancel, dots.

---

## Findings

### F1 [High] — the `createGraph` `WorkspaceEdit` is never applied; nothing is created

`createGraph` returns a `WorkspaceEdit` (`CreateFile` + `TextEdit` with the canonical body — from C2). The wizard's `handleSave` calls it but ignores the result, and `App.tsx`'s `onComplete` just does:
```ts
onComplete={(graphUri) => {
  dispatch({ type: 'cancelCreateWizard' });
  handleSelectGraph(graphUri);   // → openGraph + getGraph(graphUri)
}}
```
Nobody applies the edit. The new `.ttrg` is never opened in the LSP document store (and in the browser there is no host filesystem to write to). So `handleSelectGraph` → `client.getGraph(graphUri)` → `documents.get(uri)` is `undefined` → returns `null` → `storeGraph` never fires → the canvas renders `null`. **The wizard "creates" a graph that doesn't exist and then jumps into an empty view.**

This fails two DONE-when criteria: *"Creating a graph through the wizard produces a syntactically valid `.ttrg` (parses cleanly when re-read)"* and *"After save, the Designer jumps into the just-created graph."*

This is the same pattern flagged as N3 in review-040 (the `setLayout` `WorkspaceEdit` is also discarded). The Designer is treating edit-returning LSP methods as fire-and-forget. **Fix:** apply the returned `documentChanges` — for the browser, extract the `CreateFile` + `TextEdit` content and `client.openDocument(newUri, content)` so the doc exists before `getGraph`; where the File System Access API is available, also write the file. Then `handleSelectGraph` will load it.

### F2 [High] — graph name is not validated as an identifier

`handleSave` passes the raw `state.graphName` straight to `createGraph` as `name`, and `buildCreateGraphContent` emits `graph ${name} {`. The Save button is enabled on any non-empty `graphName.trim()`. So a name like `"billing overview"` produces `graph billing overview {` — **invalid TTR** (the grammar's `graph id` requires a single IDENT). The `slugify` helper is applied only to the *filename*, never to the graph name. Even once F1 is fixed, a spaced/special-char name yields an unparseable `.ttrg`.

**Fix:** validate the name against the IDENT rule (`[A-Za-z_][A-Za-z0-9_]*`) and block Save (with a visible message) on invalid input, or derive a valid identifier and show it. The RTL test only ever uses space-free names (`TestGraph`, `NewGraph`), so this is untested — add a negative case.

### F3 [Med] — Step 2 is a checkbox list, not the dep-mini-graph (E2.3 deviation)

E2.3 and the tests-first specify *"Embed a small Cytoscape canvas showing `PackageGraph` with selected packages highlighted."* Step 2 instead renders a second plain checkbox list plus "Add all transitive" / "Continue". The transitive *logic* is present and correct, but the **visual dependency graph is absent**, and the test was written to match the checkbox-list implementation (it never asserts a graph render), so the spec was quietly downgraded. Either implement the embedded Cytoscape panel (the task points at `Canvas.tsx` for the pattern) or, if you're deliberately descoping it, get that agreed and update the task doc.

### F4 [Med] — Step 3 lacks grouping + bulk-select (E2.4 deviation)

E2.4 asks for objects *"grouped by package and by schema kind … bulk-select-package / bulk-select-schema controls."* Step 3 is a flat list from `listSymbols({ limit: 1000 })` filtered client-side by package prefix, with only per-row checkboxes (the "N of M" counter is present). No grouping, no bulk controls. Functional for small projects, but misses the spec and won't scale to large package sets.

### F5 [Low] — `handleSave` swallows `createGraph` errors and navigates anyway

```ts
try { await lspClient.createGraph({...}); } catch { /* ignore create errors, still navigate */ }
onComplete(suggestedUri);
```
Combined with F1, a failed (or no-op) create silently lands the user on an empty canvas with no error. Surface failures (dispatch `setError`) and only navigate on success.

### F6 [Low] — minor

- Step 4 doesn't show the "future multi-schema (disabled)" affordance E2.5 mentioned. Cosmetic.
- No directory picker (E2.6) — defaults to `<projectRoot>/graphs/<slug>.ttrg`. The default is reasonable; fine to defer, but note it's not implemented.

---

## Recommendation

The wizard's interaction design is genuinely good and well-tested — but E2's whole purpose is to *create a working graph*, and right now it creates nothing (F1) and would accept invalid names (F2). Fix F1 and F2 first, with a test that **applies the returned edit and asserts the new `.ttrg` parses and loads** (not just that `createGraph` was called). Then decide on F3/F4 (implement or formally descope). `tasks-review-041.md` has the steps.
