# Review 053 — Section H2 (property / schema-kind / def-kind / package-name completion)

**Date:** 2026-05-23
**Scope:** review of Section 1.1.H.2 against [`H2-other-completion.md`](../plan/tasks/H2-other-completion.md). Verified against runtime: `pnpm --filter @modeler/lsp test` (79), `integration-tests` (82/1-skip), `pnpm -r typecheck` (clean), grammar `prebuild` (deterministic regen, 17 kinds), plus probes of the package-name and property completions. Companion: [`tasks-review-053.md`](tasks-review-053.md).
**Verdict:** **Not done.** Three of the four contexts — property-name, schema-kind, def-kind — are implemented correctly and the property-map is generated from the grammar (matches the spec, regenerates deterministically). But the **fourth context, package-name, is broken**: the "inferred-from-path" suggestion is wrong (verified: it returns `proj.billing` where it should return `billing.invoicing`), and the package-name tests are vacuous, so the bug ships green. DONE requires "all four … work with the right candidate sets."

---

## High — blockers

### F1 [High] — Package-name "inferred from path" suggestion is wrong (verified)

Probe: open `/proj/billing/invoicing/test.ttr` (workspace root `/proj`), `package ⟨cursor⟩`:

```
PKG ITEMS: [{"label":"proj.billing","detail":"(inferred from path)"}]
EXPECTED top inferred: billing.invoicing
```

Two compounding bugs:

1. **The project root is never passed.** In `server.ts` the dispatch does `const projectRoot = opts.loadManifest ? '' : '';` — both ternary branches are `''`, a leftover stub. `manifest.projectRoot` is right there in scope (set at `server.ts:192`) but unused, so `getPackageNameCompletions` always gets `''` and never strips the root.
2. **`inferPackageFromPath` drops the leaf directory.** It filters out the filename (`!p.endsWith('.ttr')`) **and then** also `parts.pop()` — removing the immediate package directory. So even with the right root it would under-compute by one level.

Net: the suggestion is garbage (`proj.billing`). Worse, it disagrees with the validator's own `inferPackageFromUri` (which yields `billing.invoicing`), so a user who accepts the suggestion immediately gets a `ttr/package-declaration-mismatch` error. This is the **third** hand-rolled package-inference (alongside `@modeler/semantics` `inferPackageFromUri` and `@modeler/migrate` `inferPackage`); it should reuse the semantics one so completion and validation agree.

### F2 [High] — Package-name tests are vacuous; they don't catch F1

`completion-package-name.test.ts` (4 tests):
- "returns package suggestion inside package statement" asserts only `items.length > 0` — never the suggested **value**. The spec's Tests-first explicitly requires "returns the inferred package name (from path) as the **top suggestion**." Asserting the value would have caught F1.
- "filters import suggestions by partial prefix" opens a file with **no registered project packages**, so `packages = []`, `labels = []`, and `labels.every(startsWith('com.'))` is **vacuously true**. It doesn't test filtering at all.
- The other two only assert `items` is defined / a negative case.

So the only context that's broken is the only one whose tests assert nothing meaningful.

---

## Medium

### F3 [Med] — `import` completion `detail` lacks child-symbol counts

Tests-first: "Inside `import ⟨cursor⟩`: returns all distinct project packages **with their child symbol counts in `detail`**." The implementation sets `detail = `package ${pkg}`` — no counts. (The `import` prefix-filter logic itself is correct, just untested per F2.)

---

## Low

- **F4** — Dispatch order differs from the spec. H2.6 says "reference → property-name → schema/def-kind → package-name"; `detectCompletionContext` checks schema/def/package (line-prefix regex) **first**, then reference, then property. The contexts are mutually exclusive in practice so it's benign, but it contradicts the documented order — align it or note the deviation.
- **F5** — A compiled `packages/grammar/scripts/extract-property-map.js` is committed alongside the `.ts` (the prebuild runs the `.js`). Either run the `.ts` directly (tsx) or gitignore the `.js`; don't commit a build artifact.
- **F6** — (root of F1) collapse the three package-inference implementations to one — export and reuse `inferPackageFromUri` from `@modeler/semantics`.

---

## What's good (verified)

- **Property-name completion (H2.2) works and excludes present properties.** Probe on an entity with `description:` already set returned `[tags, labelPlural, nameAttribute, codeAttribute, aliases, attributes, roles, displayLabel, search]` — `description` correctly omitted, nothing over-excluded. `kind = Property`, `detail` = value type. Search sub-properties are wired (`SEARCH_SUB_PROPERTIES`).
- **`property-map.ts` is generated from the grammar** (`extract-property-map`), regenerates **deterministically**, covers 17 kinds, and matches the spec exactly (entity = 10 props incl. `search`; column = 7 props).
- **Schema-kind (H2.3)** returns `db/er/map/query/cnc` as `Keyword`, gated on a trailing `schema `.
- **Def-kind (H2.4)** returns kinds filtered by the file's schema (`er` → entity/attribute/relation/…; `db` → table/column/…), as `Keyword`.
- LSP 79 / integration 82 / typecheck clean; grammar prebuild wired.

---

## Recommendation

Three of four contexts are done well. To finish: (1) pass `manifest.projectRoot` (not the `''` stub) and fix/replace `inferPackageFromPath` with semantics' `inferPackageFromUri` so the inferred package is correct and matches the validator — F1/F6; (2) make the package-name tests assert the actual inferred value and exercise prefix filtering with real registered packages — F2 (this pins F1); (3) add child-symbol counts to `import` detail — F3. Then the Low cleanups (dispatch order, stray `.js`). `tasks-review-053.md` has the steps.
