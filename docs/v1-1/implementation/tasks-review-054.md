# Tasks — review-054 (Section H2 re-review)

> **STATUS (2026-05-25): all addressed.** G1, G2, G4, G5b done and verified; G5a left as-is (defensible); G3 skipped by choice. See the "Resolution" section of [`review-054.md`](review-054.md). Note: G1 required rewriting the broken `extract-property-map.ts` generator, not just switching to `tsx`.

Findings in [`review-054.md`](review-054.md). The H2 completion behaviour is **done and verified** (F1–F4 fixed, F6 collapsed on the completion side). What's left is **one build blocker (G1)** and **a filing fix (G2)**; G3–G5 are optional.

**G1 is the only thing standing between this and "done."** Do it first, and verify with the fresh-checkout test — do **not** just run `prebuild` with the stale local `.js` present.

---

## G1 [High] — Make `prebuild` run the TypeScript source, not a phantom `.js`

The compiled `scripts/extract-property-map.js` is gitignored and not committed, but `prebuild` still runs `node scripts/extract-property-map.js`. A clean clone has no `.js`, so the build dies with `MODULE_NOT_FOUND`. Run the `.ts` directly with `tsx` (already a workspace devDep).

- [ ] **G1.1** In `packages/grammar/package.json`, change the `prebuild` script from:
  ```json
  "prebuild": "node scripts/extract-property-map.js",
  ```
  to:
  ```json
  "prebuild": "tsx scripts/extract-property-map.ts",
  ```
- [ ] **G1.2** Add `tsx` to `packages/grammar` devDependencies (it's already used by `@modeler/designer` at `^4.19.0`):
  ```json
  "devDependencies": {
    "@types/node": "^22.0.0",
    "tsx": "^4.19.0",
    "typescript": "^5.7.0"
  }
  ```
  Then run `pnpm install` from the repo root.
- [ ] **G1.3** Delete the stray compiled file so it can't mask the fix locally:
  ```bash
  rm -f packages/grammar/scripts/extract-property-map.js
  ```
  Keep the `.gitignore:9` entry (`packages/grammar/scripts/extract-property-map.js`) — with `tsx` no `.js` is produced, and the ignore is harmless insurance.
- [ ] **G1.4** **Verify it works with no `.js` present** (this is the fresh-checkout case the old script failed):
  ```bash
  ls packages/grammar/scripts/extract-property-map.js   # must print "No such file"
  pnpm --filter @modeler/grammar prebuild                # must print "Generated property-map.ts with 17 kinds"
  git status --short packages/grammar/src/generated/property-map.ts   # must be empty (no drift)
  ```

> Alternative if you prefer the compile-then-run pattern used by `vscode-ext` (`build-generator` + `node …js`): make `prebuild` = `tsc scripts/extract-property-map.ts --outDir scripts --module nodenext --target es2022 --moduleResolution nodenext --isolatedModules && node scripts/extract-property-map.js`. The `tsx` form (G1.1) is simpler and preferred — pick one, not both.

---

## G2 [Med] — Return the 053 review docs to their directory

`review-053.md` and `tasks-review-053.md` are currently staged as moved to the **repo root**. Review artifacts live under `docs/v1-1/implementation/`.

- [ ] **G2.1** Move them back:
  ```bash
  git mv review-053.md docs/v1-1/implementation/review-053.md
  git mv tasks-review-053.md docs/v1-1/implementation/tasks-review-053.md
  ```
- [ ] **G2.2** Confirm `git status --short` shows them under `docs/v1-1/implementation/` and the repo root no longer contains `review-053.md` / `tasks-review-053.md`.

---

## Low (optional)

- [ ] **G3** — Finish F6's "one implementation": replace `@modeler/migrate`'s local `inferPackage` (`packages/migrate/src/index.ts:33`) with `inferPackageFromUri` from `@modeler/semantics` (use its `.inferred` field), and keep `packages/migrate/src/__tests__/infer-package.test.ts` green (adjust if the import path changes). Skip if you'd rather keep migrate self-contained — note the decision in the PR.
- [ ] **G4** — In `getPackageNameCompletions` (`completion-property.ts`), the `import` branch must drop the empty root package. Change the filter to also exclude `''`:
  ```ts
  items = packages
    .filter((pkg) => pkg.length > 0 && (!prefix || pkg.startsWith(prefix)))
    .map(...)
  ```
  Add a test: open a package-less root file (`file:///root.ttr` with a top-level def, no `package`), then `import ⟨cursor⟩` must **not** include an item with an empty label.
- [ ] **G5** — Nits: (a) decide whether the import `detail` count should be top-level defs only; if so, count `getByPackage(pkg).filter((e) => e.parent == null).length`. (b) In `detectCompletionContext`, the comment says "all six contexts" — there are five; reword to match.

---

## Done when

- [ ] `rm -f packages/grammar/scripts/extract-property-map.js && pnpm --filter @modeler/grammar prebuild` succeeds (proves a clean clone builds) and `property-map.ts` shows no git drift.
- [ ] `review-053.md` / `tasks-review-053.md` are back under `docs/v1-1/implementation/` and absent from the repo root.
- [ ] `pnpm --filter @modeler/lsp test && pnpm --filter @modeler/integration-tests test && pnpm -r typecheck` all green.
- [ ] (If G4 done) `import ⟨cursor⟩` never lists a blank-label package.
