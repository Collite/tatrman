# Review 031 — Section B4 third pass (after tasks-review-030)

**Branch:** v1-1
**Reviewed:** the developer's claim that tasks-review-030 is done.
**Verdict:** **Almost.** Both **critical** items are genuinely fixed. The fixtures went from mostly-broken (review-030: 7 broken / 3 noisy / 3 clean) to mostly-working (now: 9 clean / 2 noisy / 1 broken / 2 documented-N/A). But 4 small, specific fixture issues remain and the B7 guardrail covers only 1 of 13 fixtures — so the DONE-when bar ("every fixture emits *exactly* its code", "no `ttr/duplicate-definition` when loading the dir as a project") is not fully met.

This is close. The remaining work is a short, mechanical punch list in `tasks-review-031.md`.

---

## Criticals — both DONE ✅

### A — `enclosingQnameOf` regression: FIXED

`enclosingQnameOf` is now imported from `@modeler/semantics` (`server.ts:36`) and used at all three resolver call sites (`server.ts:451, 488, 539`) with the full `(found.from, schemaCode, namespace, packageName)` signature plus `packageName`. `grep "enclosingQname: qnameOf"` → no matches. The kind-fallback + package-prefix behaviour is restored. ✅

### C — `@modeler/semantics` lint: FIXED

`packages/semantics/package.json:21` now has `"lint": "eslint src"`; `pnpm --filter @modeler/semantics lint` runs clean, and the dead `PackageGraphBuilder` import in `validator.ts` is gone. ✅

*(Note: `pnpm -r lint` still prints "Scope: 8 of 9" — the remaining unlinted member is `@modeler/integration-tests`, not semantics. Out of scope for B4; leave it.)*

---

## B — Fixtures: 9 clean, 2 noisy, 1 broken, 2 N/A

I ran the full validator (parser errors + all `validate*` passes) over every fixture. Per-fixture result, evaluated in the scope each fixture is meant to be opened in:

| Fixture | Expected | Actual | Status |
|---|---|---|---|
| `wrong-file-kind.ttr` | `wrong-file-kind` | `wrong-file-kind` | ✅ |
| `unused-import.ttr` | `unused-import` | `unused-import` | ✅ |
| `wildcard-with-no-matches.ttr` | `wildcard-with-no-matches` | `wildcard-with-no-matches` | ✅ |
| `graph_object_not_found.ttrg` | `graph-object-not-found` | `graph-object-not-found` | ✅ |
| `graph_objects_empty.ttrg` | `graph-objects-empty` | `graph-objects-empty` | ✅ |
| `graph_name_mismatch.ttrg` | `graph-name-mismatch` | `graph-name-mismatch` | ✅ |
| `pkg_a/package-declaration-mismatch.ttr` | `package-declaration-mismatch` | `package-declaration-mismatch` | ✅ |
| `pkg_a/sub/missing-package-declaration.ttr` | `missing-package-declaration` | `missing-package-declaration` | ✅ |
| `circular/pkg_*/…` (isolated project) | `circular-package-dependency` | `circular-package-dependency` | ✅ |
| `duplicate-import.ttr` | `duplicate-import` | `duplicate-import` **+ `unused-import`** | ⚠️ noisy |
| `ambiguous-reference.ttr` | `ambiguous-reference` | `ambiguous-reference` **+ `entity-attribute-not-found`** | ⚠️ noisy |
| `unimported-reference.ttr` | `unimported-reference` | **`unresolved-reference`** | ❌ broken |
| `graph-layout-stale-node.ttrg` | (N/A) | parse-error — documented | 📝 N/A |
| `file-ordering.ttr` | (N/A) | parse-error — documented | 📝 N/A |

Big improvement, and the rename/reorder/isolation fixes (B1.2–B1.4, B4, B5.2–B5.3) all landed correctly. The layout + file-ordering N/A notes in the README are honest and acceptable (the layout one correctly identifies a real C1 grammar gap — qname-keyed `layout.nodes`). Remaining issues:

### B-1 ❌ `unimported-reference.ttr` still has the import — the one explicit instruction was skipped

The fixture still contains `import pkg_b.*`. Task B1.1 warned exactly this: with the import present, the ref `pkg_b.er.entity.some_rel` either resolves via wildcard-import (so `viaStep !== 'fully-qualified'` → no `unimported-reference`) or, as actually happens here, fails to resolve and emits `ttr/unresolved-reference`. **Proof:** removing the `import pkg_b.*` line and pairing it with a clean `pkg_b` makes the validator emit exactly `['ttr/unimported-reference']`. Delete the import line.

### B-2 ⚠️ `duplicate-import.ttr` also emits `unused-import`

The two identical `import pkg_b.some_entity` lines are never referenced, so `unused-import` fires alongside `duplicate-import`. To get exactly one code, make the import actually used (add `def er2db_entity x { entity: pkg_b.er.entity.some_entity }` or similar), or — if both codes are considered acceptable for this fixture — change the README's expectation and the B7 assertion to allow the set `{duplicate-import, unused-import}`. Pick one and make the test match.

### B-3 ⚠️ `ambiguous-reference.ttr` also emits `entity-attribute-not-found`

Using `nameAttribute: shared_name` as the ambiguous trigger means the entity-attribute-existence check *also* fires (`shared_name` isn't a local attribute). Use a trigger site that's genuinely ambiguous but not subject to the attribute-existence rule — e.g. a relation's `from:`/`to:` or an `er2db_relation`'s `relation:` pointing at a bare `shared_name`.

### B-4 ❌ B6 cross-contamination not cleaned

`tasks-review-030 §B6` and the DONE-when ("loading `samples/broken/v1.1/` as a project yields no `ttr/duplicate-definition`") are not met:
- `pkg_b/shared_name1.ttr` is **still present** (declares `package pkg_b1`, lives under `pkg_b/`) — it should have been *moved* to `pkg_b1/`, but instead a new `pkg_b1/shared_name.ttr` was created and the old file left behind. Delete `pkg_b/shared_name1.ttr`.
- `pkg_b/` still has three files (`pkg_b.ttr`, `some_rel.ttr`, `wildcard.ttr`) all defining `def entity some_rel` in `package pkg_b` → `ttr/duplicate-definition`. Keep one, delete the rest.
- Net effect: loading the directory as a project today emits `ttr/duplicate-definition` on ~6 files and a stray `ttr/package-declaration-mismatch`. Clean these so the only diagnostics present are the intended per-fixture ones.

### B-5 ❌ B7 guardrail covers 1 of 13 fixtures

The `describe('v1.1 broken fixture diagnostics')` block in `integration.test.ts` contains a single test (`wrong-file-kind.ttr emits only ttr/wrong-file-kind`). The data-driven table over *all* fixtures asserting the exact emitted code set — the whole point of B7, and the guardrail that would have caught B-1 through B-4 — was not written. Without it, the next change can silently re-break any of the other 12 fixtures. Add the full table.

---

## Test/build status (all green)

```
pnpm -r build                                   # green
pnpm --filter @modeler/semantics test           # 100/100
pnpm --filter @modeler/integration-tests test   # 33/33
pnpm -r typecheck                               # green
pnpm -r lint                                    # green (semantics now linted)
```

The green suites are real, but they do **not** exercise 12 of the 13 fixtures — which is why the per-fixture harness above, not the test count, is the source of truth for §B.

---

## Bottom line

Criticals A and C are done and verified. The fixtures are 70% of the way there (9/13 clean). To close B4, finish the 5 items in `tasks-review-031.md` — 4 of them are one-or-two-line fixture edits, and the fifth (the B7 table) is the test that makes "done" actually checkable. Once B7 covers all fixtures and is green, B4 is genuinely complete.
