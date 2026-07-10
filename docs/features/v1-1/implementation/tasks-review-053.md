# Tasks — review-053 (Section H2: other completion)

Findings in [`review-053.md`](review-053.md). **Property-name, schema-kind, and def-kind completion are done — leave them.** What's left is the package-name context: the inferred-from-path suggestion is wrong (**F1**) and its tests don't catch it (**F2**), plus minor gaps. H2 is done when F1–F3 are closed and the package-name tests assert real values.

Do exactly what's written. **Verify the inferred package value, not just `items.length`.**

---

## F1 [High] — Fix the package "inferred from path" suggestion

Verified broken: for `/proj/billing/invoicing/test.ttr` it suggests `proj.billing` instead of `billing.invoicing`.

- [ ] **F1.1** In `server.ts` `onCompletion`, replace `const projectRoot = opts.loadManifest ? '' : '';` (both branches are `''`) with the real root: `const projectRoot = manifest.projectRoot;` (it's already set at `server.ts:192`).
- [ ] **F1.2** Replace `inferPackageFromPath` in `completion-property.ts` with the shared `inferPackageFromUri` from `@modeler/semantics` (export it if not already). Use its `inferred` field. Delete the local `inferPackageFromPath` (it both fails to strip the root and wrongly `pop()`s the leaf directory).
- [ ] **F1.3** Verify: in a file at `<root>/billing/invoicing/x.ttr`, `package ⟨cursor⟩` lists `billing.invoicing` as the top suggestion (detail "(inferred from path)"), and it equals what the validator's `inferPackageFromUri` produces (so accepting it yields no `package-declaration-mismatch`).

## F2 [High] — Make the package-name tests assert real behavior

- [ ] **F2.1** In `completion-package-name.test.ts`, the "package statement" test must assert the **top suggestion's label** equals the inferred package (e.g. `billing.invoicing` for a file under `billing/invoicing/`), not just `items.length > 0`. Initialize the connection with a `rootUri`/`workspaceFolders` so `manifest.projectRoot` is set.
- [ ] **F2.2** The "filter import by prefix" test must register **real** project packages first (open files in `com.foo`, `com.bar`, `org.baz`), then assert `import com.⟨cursor⟩` returns only the `com.*` packages — the current test passes vacuously with zero packages.
- [ ] **F2.3** Add a test that `import ⟨cursor⟩` (no prefix) lists all distinct project packages.

## F3 [Med] — `import` completion detail = child-symbol counts

- [ ] **F3.1** Per the Tests-first, set the `import` items' `detail` to the package's child-symbol count (e.g. `"12 symbols"`), computed from `projectSymbols`, instead of the literal `"package <pkg>"`. Assert it in a test.

---

## Low

- [ ] **F4** — Align `detectCompletionContext` ordering with H2.6 (reference → property → schema/def-kind → package-name), or add a comment documenting why the current order (schema/def/package regex first) is equivalent.
- [ ] **F5** — Don't commit the compiled `packages/grammar/scripts/extract-property-map.js`: either run the `.ts` via `tsx` in the prebuild, or add the `.js` to `.gitignore`.
- [ ] **F6** — (covered by F1.2) one package-inference implementation across the repo — reuse `@modeler/semantics` `inferPackageFromUri` in completion and migrate, not three hand-rolled copies.

---

## Done when

- [ ] `package ⟨cursor⟩` suggests the correct directory-inferred package as the top item (verified by a test asserting the value), matching the validator's inference.
- [ ] `import ⟨cursor⟩` lists all project packages; a partial prefix filters them; `detail` shows child-symbol counts — all asserted with real registered packages.
- [ ] No duplicate/buggy package-inference; no stray committed `.js`.
- [ ] `pnpm --filter @modeler/grammar prebuild && pnpm --filter @modeler/lsp test && pnpm --filter @modeler/integration-tests test && pnpm -r typecheck` all green.
