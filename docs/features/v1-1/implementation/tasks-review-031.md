# Tasks — review-031 (Section B4, final punch list)

> **STATUS: ALL DONE (reviewer-applied 2026-05-20).** Verified: integration suite 46 passed + 2 skipped (N/A fixtures), semantics 100/100, typecheck + lint clean. Every non-N/A fixture emits exactly its intended code, loading `samples/broken/v1.1/` yields no `ttr/duplicate-definition`, and `package-declaration-mismatch` fires only on its intended fixture. See the per-item notes below.
>
> Findings in [`review-031.md`](review-031.md). Criticals A (enclosingQnameOf) and C (semantics lint) were already done by the developer. This list covered the 5 remaining fixture items.

---

## 1. `unimported-reference.ttr` — delete the import line ✅ DONE

> Done. Removed `import pkg_b.*`, gave it unique package `pkg_unimported`. Emits exactly `ttr/unimported-reference`.

- [x] **1.1.** Edit `samples/broken/v1.1/unimported-reference.ttr`, remove the `import pkg_b.*` line. Final content:
  ```
  package pkg_a
  schema er namespace entity
  def entity artikl { attributes: [def attribute id { type: int }] }
  def er2db_relation r { relation: pkg_b.er.entity.some_rel }
  ```
  With no import, the fully-qualified ref resolves via step 6 and `pkg_b` isn't imported → `ttr/unimported-reference`. (Verified: this exact shape, paired with a clean `pkg_b` providing `some_rel`, emits exactly `['ttr/unimported-reference']`.)
- [ ] **1.2.** Ensure exactly one clean `pkg_b` def of `some_rel` exists in the project scope this fixture loads (see task 4 — after dedup, `pkg_b/b.ttr` or `pkg_b/some_rel.ttr`, not three copies).

## 2. `duplicate-import.ttr` — remove the `unused-import` co-diagnostic ✅ DONE

> Done via a cleaner third option: **duplicate a wildcard import** (`import pkg_b.*` × 2). Wildcard imports are exempt from the unused-import check, so this emits exactly `ttr/duplicate-import`. (Note: the originally-suggested 2.a — making a named import "used" — does not work because the resolver resolves bare refs via the fully-qualified step, not the named-import step, so named imports are never marked used. That's a separate B3 resolver-semantics matter, out of scope here; flagged for follow-up.) Unique package `pkg_dupimport`.

Pick ONE:
- [ ] **2.a.** Make the duplicated import actually referenced, so only `duplicate-import` fires. E.g. add a def that uses `pkg_b.some_entity`:
  ```
  package pkg_a
  import pkg_b.some_entity
  import pkg_b.some_entity
  schema er namespace entity
  def entity artikl { attributes: [def attribute id { type: int }] }
  def er2db_entity bridge { entity: pkg_b.er.entity.some_entity }
  ```
  (Verify the `er2db_entity` ref resolves to the imported symbol so `unused-import` does not fire.)
- [ ] **2.b.** *Or* accept both codes as intended for this fixture: update `README.md` to list expected = `duplicate-import` + `unused-import`, and make the B5 test assert that two-element set.

## 3. `ambiguous-reference.ttr` — use a trigger not subject to attribute-existence checks ✅ DONE

> Done. Replaced `nameAttribute: shared_name` with `def er2db_relation r { relation: shared_name }` — a bare ref not subject to the entity attribute-existence check. Emits exactly `ttr/ambiguous-reference`. Unique package `pkg_ambiguous`; `pkg_b1`/`pkg_b2` each define `shared_name`.

- [x] **3.1.** Replace the `nameAttribute: shared_name` trigger (which also fires `entity-attribute-not-found`) with an ambiguous bare ref at a site that isn't attribute-validated. Example using an `er2db_relation`'s `relation:` field:
  ```
  package pkg_a
  import pkg_b1.*
  import pkg_b2.*
  schema er namespace entity
  def entity artikl { attributes: [def attribute id { type: int }] }
  def er2db_relation r { relation: shared_name }
  ```
  where `pkg_b1` and `pkg_b2` each define `def relation shared_name` (or `def entity shared_name`, matching whatever `relation:` expects). Confirm the only emitted code is `ttr/ambiguous-reference`.
- [ ] **3.2.** Make sure `pkg_b1/` and `pkg_b2/` each contain exactly the `shared_name` def needed, with package declarations matching their directory (`package pkg_b1` in `pkg_b1/…`, `package pkg_b2` in `pkg_b2/…`).

## 4. Clean the cross-contaminated `pkg_b/` layout (B6 from review-030) ✅ DONE

> Done. Deleted `pkg_b/shared_name1.ttr`, `pkg_b/pkg_b.ttr`, `pkg_b/some_rel.ttr`, `pkg_b/wildcard.ttr`. `pkg_b/b.ttr` now holds the single `some_entity` + `some_rel` defs. Verified: loading the whole `v1.1/` dir produces no `ttr/duplicate-definition` and `package-declaration-mismatch` fires only on its intended fixture (both asserted by the B7 test).

- [x] **4.1.** Delete `samples/broken/v1.1/pkg_b/shared_name1.ttr` (it's the leftover from the move to `pkg_b1/` — the `pkg_b1/shared_name.ttr` replacement already exists).
- [ ] **4.2.** In `pkg_b/`, keep exactly one definition of `some_rel` and one of `some_entity`. Today `pkg_b.ttr`, `some_rel.ttr`, and `wildcard.ttr` all define `def entity some_rel` in `package pkg_b` → `ttr/duplicate-definition`. Decide which single file each fixture needs and delete the redundant ones. (`unimported-reference` needs `some_rel`; `unused-import`/`duplicate-import` need `some_entity`; `wildcard-with-no-matches` targets a *nonexistent* package so needs nothing in `pkg_b`.)
- [ ] **4.3.** Verify: loading `samples/broken/v1.1/` as a single project emits **no** `ttr/duplicate-definition` and **no** stray `ttr/package-declaration-mismatch` (only the intended per-fixture codes remain). Use the harness pattern from task 5.
- [ ] **4.4.** Update `README.md` cross-file-dependency section to match the final layout.

## 5. B7 guardrail — the data-driven test over ALL fixtures ✅ DONE

> Done. `tests/integration/src/integration.test.ts` now has a `collectFixtureCodes(rootDir, excludeDirs)` helper that loads a project, runs the full validator pipeline + parser, and returns codes per file. The `describe('v1.1 broken fixture diagnostics')` block table-tests all 11 non-N/A fixtures with exact-set assertions (`toEqual(new Set(...))`), plus: no-duplicate-definition, package-mismatch-only-on-intended-fixture, circular-as-own-project, and two `it.skip` rows for the N/A fixtures with reasons. All green (46 passed, 2 skipped).

- [x] **5.1.** Replace the single-case `describe('v1.1 broken fixture diagnostics')` block in `tests/integration/src/integration.test.ts` with a table-driven test. For each fixture, load the correct project scope, run the full validator + parser, collect the unique diagnostic codes attributed to the fixture's own URI, and assert the **exact set**:
  ```ts
  const cases = [
    { file: 'unimported-reference.ttr',                 expect: ['ttr/unimported-reference'] },
    { file: 'unused-import.ttr',                        expect: ['ttr/unused-import'] },
    { file: 'wildcard-with-no-matches.ttr',             expect: ['ttr/wildcard-with-no-matches'] },
    { file: 'duplicate-import.ttr',                     expect: [/* per task 2 decision */] },
    { file: 'wrong-file-kind.ttr',                      expect: ['ttr/wrong-file-kind'] },
    { file: 'ambiguous-reference.ttr',                  expect: ['ttr/ambiguous-reference'] },
    { file: 'pkg_a/package-declaration-mismatch.ttr',   expect: ['ttr/package-declaration-mismatch'] },
    { file: 'pkg_a/sub/missing-package-declaration.ttr',expect: ['ttr/missing-package-declaration'] },
    { file: 'graph_object_not_found.ttrg',              expect: ['ttr/graph-object-not-found'] },
    { file: 'graph_objects_empty.ttrg',                 expect: ['ttr/graph-objects-empty'] },
    { file: 'graph_name_mismatch.ttrg',                 expect: ['ttr/graph-name-mismatch'] },
    { file: 'circular/pkg_b/b.ttr',                     expect: ['ttr/circular-package-dependency'], project: 'circular' },
  ];
  ```
- [ ] **5.2.** Assert with `expect(new Set(codes)).toEqual(new Set(expect))` — exact match, so a stray `ttr/parse-error` or extra code fails the test. This is what makes tasks 1–4 verifiable instead of trust-me.
- [ ] **5.3.** For the two N/A fixtures (`graph-layout-stale-node.ttrg`, `file-ordering.ttr`), either omit them from the table or add an `it.skip(...)` with a one-line reason pointing at the README note — do **not** silently leave them untested without explanation.
- [ ] **5.4.** Keep the comment at `integration.test.ts:56` (the `getAllTtrFiles(brokenDir, ['v1.1'])` exclusion) pointing readers at this new table test.

---

## Verify

```bash
pnpm -r build
pnpm --filter @modeler/integration-tests test     # the new B7 table must be green
pnpm -r typecheck
pnpm -r lint
```

## DONE-when (final)

- [ ] Every non-N/A fixture emits **exactly** its intended code set (B7 table green).
- [ ] Loading `samples/broken/v1.1/` as a project produces no `ttr/duplicate-definition` and no unintended `ttr/package-declaration-mismatch`.
- [ ] The two N/A fixtures are documented in the README and either skipped-with-reason or omitted from the table.
- [ ] `STATUS.md` `[x] B4 diagnostic` is honest — i.e. all of the above is true.
