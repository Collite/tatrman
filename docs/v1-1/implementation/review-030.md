# Review 030 — Section B4 re-review (after tasks-review-029)

**Branch:** v1-1
**Reviewed:** the developer's claim that every item in `tasks-review-029.md` is done.
**Verdict:** **Not done.** Real, measurable progress — the file-ordering bug is genuinely fixed, the circular-dependency source attribution is correct, `package-inference.ts` exists with `file://` handling and tests, the qname helpers are partly deduplicated, lint is clean, and the test counts are up (semantics 100/100, integration 32/32). But two **critical** items are wrong:

1. **D1.3 was done incorrectly — it's a regression, not a fix.** The server's resolver call sites now use `qnameOf` (a v1-shape helper with no kind-fallback and no package prefix) instead of the canonical `enclosingQnameOf`. The canonical helper is exported but never imported into the LSP. This *removes* the kind-fallback that the old code had.
2. **B1 fixtures mostly don't work.** I ran the full validator over every fixture: of the 13 real fixtures (file-ordering excluded by design), **7 emit only `ttr/parse-error` or the wrong code**, and 3 more emit their code plus spurious noise. Only 3 are clean. The DONE-when requirement "produce *exactly* the expected diagnostic" is not met — and there is no automated test that would have caught this (the broken-fixtures parse test now *excludes* `v1.1`).

Details below, then `tasks-review-030.md` has the ordered fix list.

---

## A. CRITICAL — D1.3 regression in `packages/lsp/src/server.ts`

The task asked: delete the local `enclosingQnameOf`, import the canonical one from `@modeler/semantics`, and call it with `(found.from, schemaCode, namespace, packageName)`.

What actually happened:
- The local `enclosingQnameOf` was deleted (good).
- `enclosingQnameOf` was added to `@modeler/semantics`'s exports (`packages/semantics/src/index.ts:15`) — good.
- But it is **not imported** into `server.ts` (confirmed: the `@modeler/semantics` import block at `server.ts:26–39` does not include it).
- Instead, the three resolver call sites (`server.ts:448, 485, 536`) were rewired to **`qnameOf(found.from, ast)`**.

`qnameOf` (`server.ts:145`) returns `[schemaCode, namespace, def.name]` for **all** kinds — no kind-fallback, no package prefix. Compare the three relevant shapes:

| Helper | Package prefix | Kind-fallback (empty namespace → kind) | Restricted to container kinds |
|---|---|---|---|
| `reference-index.ts:enclosingQnameOf` (canonical) | yes | yes | yes |
| **deleted** local `server.ts:enclosingQnameOf` | no | **yes** | yes |
| **`qnameOf` (now wired in)** | no | **no** | no |

So this is a behavioural **regression even against the code that existed before review-029**: the old local helper applied kind-fallback (`const nsOrKind = namespace || def.kind`); `qnameOf` does not. For a reference inside a def whose enclosing scope has an empty namespace, the enclosing qname now resolves differently, and in a multi-package project the package prefix is missing entirely — exactly the v1.1 scenarios this work exists to support.

Why the tests still pass: the only multi-def sample exercised by the LSP integration tests is `v1-metadata` (`er.ttr` / `map.ttr`), which uses `namespace entity` (non-empty) and is effectively single-package. `qnameOf` and `enclosingQnameOf` coincide there, so nothing fails. This is the same class of blind spot review-029 §D1 was meant to close.

**Fix:** import `enclosingQnameOf` from `@modeler/semantics`, and at each of the three call sites pass `(found.from, schemaCode, namespace, ast.packageDecl?.name ?? '')`. Note `enclosingQnameOf` returns `undefined` for non-container kinds — that's fine, `resolveReference`'s `enclosingQname` is optional. Do **not** use `qnameOf` for the enclosing-scope argument. (`qnameOf` is still legitimately used for *naming the found def itself* at `server.ts:458, 489, 541` — leave those.)

---

## B. CRITICAL — B1 fixtures do not produce the expected diagnostics

I loaded every file under `samples/broken/v1.1/` as one project (projectRoot = that dir) and ran the complete validator + parser over each. Results:

| Fixture | Expected | Actually emitted | Status |
|---|---|---|---|
| `unimported-reference.ttr` | `ttr/unimported-reference` | `ttr/parse-error` only | ❌ |
| `unused-import.ttr` | `ttr/unused-import` | `ttr/parse-error` only | ❌ |
| `wildcard-with-no-matches.ttr` | `ttr/wildcard-with-no-matches` | `ttr/parse-error` only | ❌ |
| `duplicate-import.ttr` | `ttr/duplicate-import` | `ttr/parse-error` only | ❌ |
| `wrong-file-kind.ttr` | `ttr/wrong-file-kind` | `ttr/parse-error` only | ❌ |
| `graph-layout-stale-node.ttrg` | `ttr/graph-layout-stale-node` | `ttr/parse-error` + `ttr/graph-name-mismatch` | ❌ |
| `pkg_a/circular-package-dependency.ttr` (+ `pkg_b/…`) | `ttr/circular-package-dependency` | (none on the fixture file) | ❌ |
| `ambiguous-reference.ttr` | `ttr/ambiguous-reference` | `ttr/ambiguous-reference` **+ `ttr/required-property-missing`** | ⚠️ noisy |
| `graph-object-not-found.ttrg` | `ttr/graph-object-not-found` | `ttr/graph-object-not-found` **+ `ttr/graph-name-mismatch`** | ⚠️ noisy |
| `graph-objects-empty.ttrg` | `ttr/graph-objects-empty` | `ttr/graph-objects-empty` **+ `ttr/graph-name-mismatch`** | ⚠️ noisy |
| `graph-name-mismatch.ttrg` | `ttr/graph-name-mismatch` | `ttr/graph-name-mismatch` | ✅ |
| `pkg_a/package-declaration-mismatch.ttr` | `ttr/package-declaration-mismatch` | `ttr/package-declaration-mismatch` | ✅ |
| `pkg_a/sub/missing-package-declaration.ttr` | `ttr/missing-package-declaration` | `ttr/missing-package-declaration` | ✅ |
| `file-ordering.ttr` | (none — documented N/A) | `ttr/parse-error` | ✅ (acknowledged) |

**3 of 13 clean. 7 broken. 3 noisy.** Root causes, each verified empirically:

### B1 — the four import fixtures have `import` *after* `schema`

`unimported-reference.ttr`, `unused-import.ttr`, `wildcard-with-no-matches.ttr`, `duplicate-import.ttr` all order the file `package → schema → import → def`. The grammar is order-strict (`importDecl*` precedes `schemaDirective`), so the `import` line is a parse error and the import is dropped — the intended diagnostic can never fire.

Proof: reordering `unused-import.ttr` to `package → import → schema → def` makes the validator emit exactly `['ttr/unused-import']`. The developer already knows the correct order — `pkg_a/circular-package-dependency.ttr` uses it. The four top-level fixtures are just mis-ordered.

### B2 — `wrong-file-kind.ttr` also contains a `schema` directive

The fixture is `schema er namespace entity` + `graph my_graph {…}` + `def …`. Grammar allows `(schemaDirective | graphBlock)?` — at most one — so schema-then-graph is a parse error, and the file never reaches the `wrong-file-kind` check. Proof: the *same developer's* new LSP test (`integration.test.ts`, "wrong-file-kind") uses `graph my_graph { schema: er }` + `def …` (no top-level schema directive) and correctly gets `ttr/wrong-file-kind`. The fixture should match that shape — drop the `schema er namespace entity` line.

### B3 — `graph-layout-stale-node.ttrg` layout syntax doesn't parse

The fixture uses `layout: { nodes: { "er.entity.nonexistent": { x: 100, y: 100 } } }`. The parser rejects the quoted-string node key: `mismatched input '"er.entity.nonexistent"' expecting … IDENT`. The `layout.nodes` map ends up empty, so the stale-node check never runs. This is the most concerning fixture because it suggests the `.ttrg` layout grammar **cannot represent qname-keyed layout nodes at all** — which is a C1 concern, not just a fixture typo. Either the grammar needs a string/qname key production for `nodes`, or the layout format uses a different shape than the fixture assumes. Flag for C1; for B4, the fixture must use whatever syntax actually parses (verify against the grammar before re-committing it).

### B4 — circular-dependency fixture shows nothing on the fixture file

`validateCircularDependencies` attaches each cycle warning to `packageToUris.get(cycle[0])?.[0]` — the first-loaded file of the package. `pkg_a/` contains three files; the warning lands on whichever is first (e.g. `artikl.ttr`), and `publishDiagnostics` filters by `source.file === uri`, so opening `pkg_a/circular-package-dependency.ttr` shows nothing. The *implementation* is acceptable (one warning per package, arbitrary file) and the unit/integration cycle tests pass because they don't filter by uri — but the *fixture* can't demonstrate the diagnostic. Fix by giving the cycle its own isolated 2-file project (one file per package, no siblings).

### B5 — the three "noisy" fixtures

- `ambiguous-reference.ttr`: `def entity use_shared { nameAttribute: shared_name }` has no `attributes`, so it also trips `ttr/required-property-missing`. Give it a minimal attribute list.
- `graph-object-not-found.ttrg` / `graph-objects-empty.ttrg`: inner graph is `graph artikl`/`graph my_graph` while the filename differs, so each also emits `ttr/graph-name-mismatch`. Name the inner graph to match the filename stem (use underscore filenames since graph names must be valid identifiers — e.g. file `graph_object_not_found.ttrg` with `graph graph_object_not_found {…}`).

### B6 — fixture project layout is cross-contaminated

`samples/broken/v1.1/pkg_b/` contains **three** files (`pkg_b.ttr`, `some_rel.ttr`, `wildcard.ttr`) that each declare `def entity some_rel` in `package pkg_b` → duplicate-definition when the directory is loaded as a project. `pkg_b/shared_name1.ttr` declares `package pkg_b1` but lives under `pkg_b/`, which (a) trips `ttr/package-declaration-mismatch` and (b) contradicts the README, which says `ambiguous-reference` needs a `pkg_b1/` directory. The shared `pkg_a/`/`pkg_b/` dirs mix unrelated fixtures. Each multi-file fixture should be a self-contained minimal project (its own subtree), so opening it doesn't drag in unrelated defs.

### B7 — no test guards the fixtures

`tests/integration/src/integration.test.ts:56` now calls `getAllTtrFiles(brokenDir, ['v1.1'])` — i.e. the broken-fixtures parse sweep **excludes** the new v1.1 fixtures. So nothing asserts they emit the right codes. That exclusion is *why* the 7 broken fixtures passed review unnoticed. Add a data-driven test that loads each fixture (or its minimal project) and asserts the emitted code set equals the expected one — this is the guardrail that should have existed before claiming B1 done.

---

## C. MEDIUM — `@modeler/semantics` has no `lint` script

`pnpm -r lint` reports "Scope: 8 of 9 workspace projects" and never lints `@modeler/semantics`. Consequently the unused `import { PackageGraphBuilder }` at `validator.ts:9` (now dead — `validateCircularDependencies` uses `findCyclesOn`) was not flagged, and the repo's `no-explicit-any` / `no-unused-vars` rules never run on the package that holds the most logic. Add a `"lint": "eslint src"` script to `packages/semantics/package.json` and fix whatever it surfaces (at minimum the dead import). This is pre-existing, but it directly caused a miss in this very change set.

---

## D. What was done correctly (verified)

- **A1 (file-ordering):** guards now require `… !== Infinity` on the relevant operands, and the schema/graph-vs-defs comparisons are inverted to `>`. Repro on a canonical no-imports file now returns `[]`. `pushOrdering` helper (F1) extracted. ✅
- **A3 (contracts vs grammar):** contracts §1.4 amended and the fixture README documents that `ttr/file-ordering` is not emittable under the order-strict grammar. Honest and correct. ✅
- **A4 / D2 (circular dep):** `validateCircularDependencies(packageGraph)` now takes only the graph, calls the new `findCyclesOn(graph)` free function in `package-graph.ts`, and attaches each diagnostic to a real `documentUri`. The LSP filters by `source.file === uri`. Implementation is correct (the fixture, not the code, is the B4 problem). ✅
- **C1/C2 (workspace/symbol):** kind-prefix boost over the full index; `"rel"`/`"ent"`/`"attr"` integration tests pass (32/32). ✅
- **D3 (package-graph cache):** `publishDiagnostics` now calls `getPackageGraph()` instead of rebuilding inline every keystroke. ✅
- **D4 (projectRoot wiring):** `onInitialize` reads `params.workspaceFolders?.[0]?.uri`, converts `file://` → path, and calls `resolveManifest` + `rebuildValidator(projectRoot)`. ✅ *(Caveat — see below.)*
- **D6:** the named-import-shadows-wildcard branch was removed (Option α). ✅
- **D7:** `packageOfImport` extracted into `references.ts` and used in `validator.ts` (both `validateReferences` and `validateImports`). The third copy in `package-graph.ts:resolvePackageOfImport` still exists but is internal; acceptable, though folding it in would finish the job. ✅ (mostly)
- **D8:** LSP integration test for `ttr/wrong-file-kind` added and passing. ✅
- **D9:** `validateTtrgGraph` gated on `uri.endsWith('.ttrg')` in `publishDiagnostics`. ✅
- **B2 (package-inference):** `packages/semantics/src/package-inference.ts` created, handles `file://` via `new URL().pathname`, with 5 unit tests. ✅
- **F3:** `walker.ts` unused-var lint error fixed; `pnpm -r lint` clean (for the 8 packages it covers). ✅
- **diagnostics-v1.1.test.ts:** grew to 18 cases, all green. ✅

### Minor caveats (not blockers)

- **D4.3 not done:** no integration test opens a file under a project subdirectory and asserts no spurious `ttr/package-declaration-mismatch`. Given B6's mislaid `shared_name1.ttr` already proves package-mismatch can fire unexpectedly, this test is worth adding.
- **D4 robustness:** `onInitialize` only reads `workspaceFolders`; clients that send the (deprecated but still common) `rootUri`/`rootPath` instead get `projectRoot = ''`. Consider falling back to `params.rootUri`.

---

## Verification commands run

```
pnpm -r build                                   # green
pnpm --filter @modeler/semantics test           # 100/100 green
pnpm --filter @modeler/integration-tests test   # 32/32 green
pnpm -r typecheck                               # green
pnpm -r lint                                    # green (8 of 9 — semantics has no lint script; see §C)
node harness over samples/broken/v1.1/*         # 3 clean / 7 broken / 3 noisy (see §B table)
node repro: unused-import reordered             # -> ['ttr/unused-import']  (proves §B1 fix)
node repro: graph layout string-key             # -> ttr/parse-error        (proves §B3)
node repro: wrong-file-kind w/o schema          # -> ['ttr/wrong-file-kind'] (proves §B2)
```

See `tasks-review-030.md` for the ordered, specific fix list.
