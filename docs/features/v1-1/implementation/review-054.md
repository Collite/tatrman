# Review 054 — Section H2 re-review (fixes for review-053)

**Date:** 2026-05-25
**Scope:** re-review of Section 1.1.H.2 after the developer addressed [`tasks-review-053.md`](tasks-review-053.md). Verified against runtime:

- `pnpm --filter @modeler/grammar prebuild` → "Generated property-map.ts with 17 kinds", **deterministic** (no git drift in `property-map.ts`).
- `pnpm --filter @modeler/lsp test` → **80 passed** (incl. `completion-package-name.test.ts` **5/5**).
- `pnpm --filter @modeler/integration-tests test` → **85 passed, 1 skipped**.
- `pnpm -r typecheck` → **clean** (all 8 packages).
- Fresh-checkout simulation of the prebuild (see G1).

Companion: [`tasks-review-054.md`](tasks-review-054.md).

**Verdict:** **Almost done.** The four substantive review-053 blockers — F1 (wrong inferred package), F2 (vacuous tests), F3 (import detail counts), F4 (dispatch order) — are **fixed and verified at runtime**, and the completion-side package-inference duplication (F6) is collapsed onto `@modeler/semantics`. **One blocker remains:** the F5 fix is wrong — it gitignored the compiled `extract-property-map.js` but left `prebuild` invoking `node …js`, so a **fresh checkout's `pnpm -r build` fails** (proven below). Plus a filing error (the 053 review docs were moved to the repo root) and two Low nits.

---

## Verified fixed (runtime evidence)

### F1 — package "inferred from path" is now correct ✓
- `server.ts:820` now passes `const projectRoot = manifest.projectRoot ?? ''` into `getPackageNameCompletions` (the `'' : ''` stub is gone).
- `completion-property.ts` deleted the local `inferPackageFromPath` and uses the shared `inferPackageFromUri` from `@modeler/semantics` (`completion-property.ts:12,281`). Since completion and the validator now call the **same** function, an accepted suggestion can't produce a `package-declaration-mismatch`.
- **Test asserts the value:** `completion-package-name.test.ts` "returns billing.invoicing as top suggestion…" asserts `top.label === 'billing.invoicing'` **and** `top.detail === '(inferred from path)'` for a file at `billing/invoicing/`, with `rootUri` set so `manifest.projectRoot` is real. Green.

### F2 — package-name tests assert real behaviour ✓
- Prefix-filter test now opens **real** packages (`com.foo`, `com.bar`, `org.baz`) then asserts `import com.⟨cursor⟩` labels all `startsWith('com.')` **and** `not.toContain('org.baz')` — no longer vacuous.
- Added "returns all project packages on import with no prefix" (F2.3).
- 5 tests total, all green.

### F3 — import detail = child-symbol counts ✓
- `completion-property.ts:300-306` sets `detail = `${count} symbol${count !== 1 ? 's' : ''}`` from `projectSymbols.getByPackage(pkg).length`. Test asserts `detail` matches `/symbol/`. Green.

### F4 — dispatch order ✓ (documented)
- `detectCompletionContext` keeps the line-prefix checks (schema/def/package) as a fast-path and adds a comment explaining the contexts are mutually exclusive so order is non-functional. Acceptable per the F4 "…or add a comment documenting why."

### F6 — package-inference duplication (partial, acceptable) ◑
- The **completion** copy (`inferPackageFromPath`) that caused F1 is gone; completion now reuses `@modeler/semantics`. The blocking duplication is resolved.
- `@modeler/migrate` still has its own `inferPackage` (`packages/migrate/src/index.ts:33`). F6 was Low ("covered by F1.2"); unifying migrate too is a nice-to-have, not a blocker. Noted as G3.

---

## Blocker

### G1 [High] — `prebuild` breaks on a fresh checkout (the F5 fix is wrong)
F5 asked to stop committing the compiled `extract-property-map.js`. The developer added it to `.gitignore` (`.gitignore:9`) and removed it from git — **but left `prebuild` running `node scripts/extract-property-map.js`** (`packages/grammar/package.json:17`), and nothing generates that `.js`. It only still works locally because the old compiled `.js` is physically present (untracked).

Proven — moving the `.js` aside (simulating a clean clone) and running prebuild:

```
$ mv scripts/extract-property-map.js /tmp && node scripts/extract-property-map.js
Error: Cannot find module '…/packages/grammar/scripts/extract-property-map.js'
  code: 'MODULE_NOT_FOUND'   (exit 1)
```

So `pnpm -r build` (whose `@modeler/grammar` build runs `prebuild` first) fails on every fresh clone / CI runner. The fix must run the **`.ts`** (the source of truth), not a phantom `.js`. See G1 in the task list.

---

## Low

- **G2 [Med] — 053 review docs were moved to the repo root.** `git status` shows staged renames `docs/v1-1/implementation/review-053.md -> review-053.md` and `…/tasks-review-053.md -> tasks-review-053.md`. Review artifacts must live under `docs/v1-1/implementation/` (the cadence convention). Move them back.
- **G3 [Low] — migrate still hand-rolls package inference** (`packages/migrate/src/index.ts:33` `inferPackage`). Optional: migrate it onto `@modeler/semantics` `inferPackageFromUri` to finish F6's "one implementation" intent.
- **G4 [Low] — `import ⟨cursor⟩` can list a blank item.** `listPackages()` includes the empty `''` package (root/package-less files). The `package` branch filters `p.length > 0`, but the **`import` branch does not**, so a project with any package-less file yields an item with an empty label. Filter `''` out of the import candidates.
- **G5 [nit] — import "N symbols" counts nested symbols.** `getByPackage` returns every entry in the package via `all()`, including attributes/columns — so the count is total symbols, not importable top-level defs. Fine if intended; if "child symbols" was meant to mean top-level defs, filter to `entry.parent == null`. Also the F4 comment says "all six contexts" — there are five (reference, property, schemaCode, defKind, packageName); reword.

---

## Recommendation

H2's behaviour is complete and correct — all four completion contexts return the right candidate sets, verified at runtime. Ship it **after** G1 (make `prebuild` run the `.ts`, so clean clones build) and G2 (return the 053 docs to `docs/v1-1/implementation/`). G3–G5 are optional cleanups. `tasks-review-054.md` has exact steps.

---

## Resolution (2026-05-25)

Fixed directly. **G1 was worse than described:** switching `prebuild` to `tsx scripts/extract-property-map.ts` surfaced that the `.ts` was a **broken, divergent stub** — it resolved the grammar path from `process.argv[1]` (yielding `scripts/src/TTR.g4`), used `process.argv[2]` (undefined) for the output path, its regex couldn't parse the spaced `*Property` rules, and it **never emitted `SEARCH_SUB_PROPERTIES`**. The real generator was the committed `.js` (a build artifact, since gitignored), which the `.ts` did not match. So the `.ts` was rewritten into a correct generator: paths via `import.meta.url`; parses each `<kind>Property` rule (anchored at line start) and maps sub-rules to property names (`searchBlock→search`, `columnNamesList→columnNames`, trailing `_` stripped); sources `SEARCH_SUB_PROPERTIES` from the `searchSubProperty` rule. Output is **byte-identical** to the previous `property-map.ts` except the header attribution line (`.js`→`.ts`). Verified: with no `.js` present, `pnpm --filter @modeler/grammar prebuild` → "Generated property-map.ts with 17 kinds".

- **G1** ✅ rewrote the generator; `prebuild` = `tsx …extract-property-map.ts`; `tsx` added to grammar devDeps; stray `.js` removed.
- **G2** ✅ `review-053.md` / `tasks-review-053.md` returned to `docs/v1-1/implementation/`.
- **G4** ✅ `import` completion filters the empty root package; regression test added.
- **G5b** ✅ comment reworded ("five contexts"). **G5a** left as-is — counting all package symbols is a defensible reading of the spec's "child symbol counts."
- **G3** ⏭️ skipped by choice — `@modeler/migrate` keeps its own `inferPackage` (unifying it onto semantics is a future cleanup; the F1-causing duplication in completion is already gone).

Gate: grammar prebuild deterministic (17 kinds) · **lsp 81** · **integration 85 (+1 skip)** · `pnpm -r typecheck` clean (8/8).
