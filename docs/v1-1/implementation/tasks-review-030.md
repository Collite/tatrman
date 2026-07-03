# Tasks — review-030 (Section B4, second pass)

> Findings in [`review-030.md`](review-030.md). Most of tasks-review-029 is genuinely done (see review-030 §D). This list covers only what's still wrong. **Two criticals first (A, B), then the medium (C), then the minor (D).**
>
> Tick each `[ ]` only after you've *run the verification step under it* — not just written the code. The whole reason B1 failed is that fixtures were created but never run through the validator.

---

## Section A — CRITICAL: fix the `enclosingQnameOf` regression in the LSP

The previous pass deleted `server.ts`'s local `enclosingQnameOf` but wired the resolver call sites to `qnameOf`, which has no kind-fallback and no package prefix. That's a regression. Use the canonical helper instead.

### A1. Import the canonical helper

- [ ] **A1.1.** In `packages/lsp/src/server.ts`, add `enclosingQnameOf` to the existing `@modeler/semantics` import block (the one at lines ~26–39 that already imports `ProjectSymbolTable`, `Resolver`, `Validator`, …):
  ```ts
  import {
    ProjectSymbolTable,
    Resolver,
    Validator,
    resolveManifest,
    loadProjectFromOpenDocuments,
    collectReferences,
    nestedDefs,
    ReferenceIndex,
    PackageGraphBuilder,
    enclosingQnameOf,          // <-- add
    type ResolvedManifest,
    type ValidationDiagnostic,
    type PackageGraph,
  } from '@modeler/semantics';
  ```

### A2. Replace the three `enclosingQname:` arguments (NOT the def-naming calls)

There are exactly three resolver calls that pass `enclosingQname: qnameOf(found.from, ast)`. Change **only** the `enclosingQname:` value at each:

- [ ] **A2.1.** `server.ts:448` — change
  ```ts
  { schemaCode, namespace, enclosingQname: qnameOf(found.from, ast) }
  ```
  to
  ```ts
  { schemaCode, namespace, enclosingQname: enclosingQnameOf(found.from, schemaCode, namespace, ast.packageDecl?.name ?? ''), packageName: ast.packageDecl?.name ?? '' }
  ```
- [ ] **A2.2.** `server.ts:485` — same change.
- [ ] **A2.3.** `server.ts:536` — same change.

  > **Do NOT touch** `qnameOf(found.def, ast, found.enclosing)` at lines ~458, 489, 541 — those legitimately name the *found def itself* and are correct as-is.

- [ ] **A2.4.** Confirm `enclosingQnameOf` returning `undefined` for non-container kinds is acceptable here — it is, because `resolveReference`'s `enclosingQname` field is optional. No extra guarding needed.

### A3. Verify

- [ ] **A3.1.** `pnpm --filter @modeler/lsp build && pnpm --filter @modeler/lsp typecheck` — clean.
- [ ] **A3.2.** `pnpm --filter @modeler/integration-tests test` — still 32/32 (the v1-metadata sample won't change behaviour, but confirm no regression).
- [ ] **A3.3.** Grep to confirm `qnameOf` is no longer used for any `enclosingQname:` argument:
  ```bash
  grep -n "enclosingQname: qnameOf" packages/lsp/src/server.ts   # expect: no matches
  ```

---

## Section B — CRITICAL: make every fixture emit exactly its intended diagnostic

The fixtures exist but 7 of 13 don't emit their code (they emit `ttr/parse-error` or the wrong code), and 3 emit extra noise. Fix each, then add a test that *proves* it.

### B1. Reorder the four import fixtures (`import` must precede `schema`)

For each, change the order to `package → import(s) → schema → def`:

- [ ] **B1.1.** `samples/broken/v1.1/unimported-reference.ttr`:
  ```
  package pkg_a
  import pkg_b.*
  schema er namespace entity
  def entity artikl { attributes: [def attribute id { type: int }] }
  def er2db_relation r { relation: pkg_b.er.entity.some_rel }
  ```
  (Keeps the import present-but-unused-for-this-ref so the fully-qualified ref still trips `unimported-reference`; verify in B7 that it does — if the present `import pkg_b.*` suppresses it, remove the import line entirely so the ref resolves only via fully-qualified search.)
- [ ] **B1.2.** `samples/broken/v1.1/unused-import.ttr`: move `import pkg_b.some_entity` above `schema`.
- [ ] **B1.3.** `samples/broken/v1.1/wildcard-with-no-matches.ttr`: move `import pkg_nonexistent.*` above `schema`.
- [ ] **B1.4.** `samples/broken/v1.1/duplicate-import.ttr`: move both `import pkg_b.some_entity` lines above `schema`.

### B2. Fix `wrong-file-kind.ttr` — remove the schema directive

- [ ] **B2.1.** Replace `samples/broken/v1.1/wrong-file-kind.ttr` with the shape your own passing LSP test uses (graph block + def, **no** top-level `schema` directive):
  ```
  graph my_graph { schema: er }
  def entity artikl { attributes: [def attribute id { type: int }] }
  ```

### B3. Fix `graph-layout-stale-node.ttrg` — use layout syntax that actually parses

- [ ] **B3.1.** The current `nodes: { "er.entity.nonexistent": {...} }` fails to parse (the parser rejects the quoted-string key: `expecting … IDENT`). **Before editing the fixture**, determine what layout-node-key syntax the grammar actually accepts:
  ```bash
  grep -n "graphLayoutProperty\|LAYOUT\|nodes\|object_" packages/grammar/src/TTR.g4
  ```
  Then write the fixture using the syntax the grammar supports, such that `layout.nodes` parses to a non-empty map with a key that is **not** in `objects`.
- [ ] **B3.2.** If the grammar genuinely cannot express a qname-keyed layout node (likely — qnames contain dots), this is a **C1 grammar gap, not a fixture bug**. In that case: (a) leave a one-line note in the fixture README that `graph-layout-stale-node` is blocked on C1's layout-key grammar, exactly like the `file-ordering` note; and (b) raise it in the C1 task list. Do **not** ship a fixture that silently parse-errors.

### B4. Give the circular-dependency fixture an isolated project

- [ ] **B4.1.** Create a self-contained subtree so each package has exactly one file and no unrelated siblings, e.g.:
  ```
  samples/broken/v1.1/circular/pkg_a/a.ttr   ->  package pkg_a / import pkg_b.* / schema er namespace entity / def entity a { attributes: [def attribute id { type: int }] }
  samples/broken/v1.1/circular/pkg_b/b.ttr   ->  package pkg_b / import pkg_a.* / schema er namespace entity / def entity b { attributes: [def attribute id { type: int }] }
  ```
- [ ] **B4.2.** Delete the old `pkg_a/circular-package-dependency.ttr` and `pkg_b/circular-package-dependency.ttr`.

### B5. De-noise the three noisy fixtures

- [ ] **B5.1.** `ambiguous-reference.ttr`: give `use_shared` a minimal attribute list so it doesn't trip `ttr/required-property-missing`:
  ```
  def entity use_shared { attributes: [def attribute id { type: int }] nameAttribute: shared_name }
  ```
- [ ] **B5.2.** Rename `graph-object-not-found.ttrg` → `graph_object_not_found.ttrg` and set its inner graph name to match: `graph graph_object_not_found { … }`. (Graph names must be valid identifiers, so the file stem must use underscores.)
- [ ] **B5.3.** Same for `graph-objects-empty.ttrg` → `graph_objects_empty.ttrg`, inner `graph graph_objects_empty { … }`.
- [ ] **B5.4.** For consistency, also rename `graph-name-mismatch.ttrg` → keep the inner name deliberately different from the stem (that's the whole point of *this* fixture) — but make sure the stem is a valid-identifier-ish name so the *only* mismatch is intentional. Leave its inner `graph wrong_name` as-is; just confirm it emits **only** `ttr/graph-name-mismatch` (it currently does).

### B6. Fix the cross-contaminated multi-file layout

- [ ] **B6.1.** Move `pkg_b/shared_name1.ttr` (which declares `package pkg_b1`) into `samples/broken/v1.1/pkg_b1/` so its path matches its declared package. Update the README's "Cross-file Dependencies" section accordingly.
- [ ] **B6.2.** Remove the duplicate `def entity some_rel` definitions: `pkg_b/` currently has `pkg_b.ttr`, `some_rel.ttr`, **and** `wildcard.ttr` all defining `some_rel` in `package pkg_b`. Keep exactly one file per def the fixtures actually need; delete the rest. The goal: loading `samples/broken/v1.1/` as a project produces **no** `ttr/duplicate-definition`.
- [ ] **B6.3.** After restructuring, re-run the harness in B7 and confirm no fixture emits a code that isn't its intended one (except documented N/A cases).

### B7. Add the guardrail test that should have caught all of this

- [ ] **B7.1.** Add `tests/integration/src/broken-fixtures-v1.1.test.ts` (or a `describe` block in an existing integration file). For each fixture, assert the emitted diagnostic-code **set** matches the expected set. Use a table:
  ```ts
  const cases: Array<{ file: string; expect: string[]; project?: string }> = [
    { file: 'unimported-reference.ttr', expect: ['ttr/unimported-reference'] },
    { file: 'unused-import.ttr', expect: ['ttr/unused-import'] },
    { file: 'wildcard-with-no-matches.ttr', expect: ['ttr/wildcard-with-no-matches'] },
    { file: 'duplicate-import.ttr', expect: ['ttr/duplicate-import'] },
    { file: 'wrong-file-kind.ttr', expect: ['ttr/wrong-file-kind'] },
    { file: 'ambiguous-reference.ttr', expect: ['ttr/ambiguous-reference'], project: '.' },
    { file: 'pkg_a/package-declaration-mismatch.ttr', expect: ['ttr/package-declaration-mismatch'] },
    { file: 'pkg_a/sub/missing-package-declaration.ttr', expect: ['ttr/missing-package-declaration'] },
    { file: 'graph_object_not_found.ttrg', expect: ['ttr/graph-object-not-found'] },
    { file: 'graph_objects_empty.ttrg', expect: ['ttr/graph-objects-empty'] },
    { file: 'graph-name-mismatch.ttrg', expect: ['ttr/graph-name-mismatch'] },
    { file: 'circular/pkg_a/a.ttr', expect: ['ttr/circular-package-dependency'], project: 'circular' },
    // graph-layout-stale-node + file-ordering: documented N/A — assert they are NOT in the suite, or xit() with the reason.
  ];
  ```
  For each case, load the appropriate project scope, run the full validator (`validateDocument` + `validateReferences` + `validateImports` + `validateFileOrdering` + `validatePackageDeclarations` + `validateTtrgGraph` for `.ttrg` + `validateCircularDependencies` + parser `errors`), collect the unique codes for the fixture's own URI, and `expect(new Set(codes)).toEqual(new Set(expect))`.
- [ ] **B7.2.** This test must assert the **exact** set — no `ttr/parse-error`, no spurious extras. That is the literal DONE-when wording ("produce exactly the expected diagnostic").
- [ ] **B7.3.** Reconsider the `getAllTtrFiles(brokenDir, ['v1.1'])` exclusion at `integration.test.ts:56`. The v1.1 fixtures are *intentionally* broken so they can't join the "parses without errors" sweep — but they should be covered by B7.1's targeted test instead. Leave a comment at the exclusion pointing to the new test so the gap is obvious to the next reader.

### B8. Update the README table to reflect the final state

- [ ] **B8.1.** After all renames/moves, update `samples/broken/v1.1/README.md`: correct filenames, correct cross-file dependency paths (`pkg_b1/`, `circular/`), and the `graph-layout-stale-node` status (N/A-blocked-on-C1 if B3.2 applies).

---

## Section C — MEDIUM: lint `@modeler/semantics`

- [ ] **C1.** Add a lint script to `packages/semantics/package.json`:
  ```json
  "scripts": { "lint": "eslint src", ... }
  ```
- [ ] **C2.** Run `pnpm --filter @modeler/semantics lint` and fix what it surfaces. At minimum, remove the now-dead `import { PackageGraphBuilder } from './package-graph.js';` at `validator.ts:9` (the class is unused since `validateCircularDependencies` switched to `findCyclesOn`).
- [ ] **C3.** Confirm `pnpm -r lint` now reports "9 of 9 workspace projects" and is clean.

---

## Section D — MINOR (do after A–C)

- [ ] **D1.** (was D4.3) Add an integration test: open a file under a project subdirectory whose `package` declaration matches its path, and assert **no** `ttr/package-declaration-mismatch` is published. This guards the `inferPackageFromUri` + `projectRoot` wiring end-to-end.
- [ ] **D2.** `onInitialize` projectRoot fallback: when `params.workspaceFolders` is absent, fall back to `params.rootUri` (and then `rootPath`) before defaulting to `''`. Some clients still send only `rootUri`.
- [ ] **D3.** (optional) Fold `package-graph.ts:resolvePackageOfImport` into the shared `packageOfImport` from `references.ts` to finish the D7 deduplication (one helper, not two).

---

## Verify by running (after each section)

```bash
pnpm -r build
pnpm --filter @modeler/semantics test
pnpm --filter @modeler/integration-tests test     # must include the new B7 fixture test
pnpm -r typecheck
pnpm -r lint                                       # must now be 9 of 9 (Section C)
```

Plus the fixture harness — for **every** fixture, the emitted code set must equal the expected set (Section B7 automates this; run it and read the output, don't just trust the green checkmark).

## DONE-when (the real bar this time)

- [ ] `grep -n "enclosingQname: qnameOf" packages/lsp/src/server.ts` → no matches; `enclosingQnameOf` imported from `@modeler/semantics`.
- [ ] Every fixture emits **exactly** its intended diagnostic (B7 test green), or is explicitly documented N/A with a C1 follow-up.
- [ ] Loading `samples/broken/v1.1/` as a project yields no `ttr/duplicate-definition` and no stray `ttr/package-declaration-mismatch`.
- [ ] `pnpm -r lint` covers 9 of 9 packages and is clean.
- [ ] Integration suite green and includes the fixture guardrail test.
