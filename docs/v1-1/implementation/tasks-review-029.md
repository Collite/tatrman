# Tasks — review-029 (Section B4)

> Findings live in [`review-029.md`](review-029.md). Section letters here mirror the review (A = correctness bugs, B = missing deliverables, C = workspace/symbol fix, D = architecture, E = test gaps, F = cosmetic).
>
> **Order matters.** Do A → B → D before E (some E tasks depend on D fixes). Tick each `[ ]` as you complete it. Run the verification commands at the bottom after every Section.

---

## Section A — Correctness bugs (do first)

### A1. Fix `validateFileOrdering` (`packages/semantics/src/validator.ts:295–349`)

- [ ] **A1.1.** Replace the "imports after schema" guard.
  Current:
  ```ts
  if (firstImportLine > schemaLine && schemaLine !== Infinity) { ... }
  ```
  Change to:
  ```ts
  if (firstImportLine !== Infinity && schemaLine !== Infinity && firstImportLine > schemaLine) { ... }
  ```
  Without the `firstImportLine !== Infinity` term, the warning fires on every file with a schema and no imports.

- [ ] **A1.2.** Same fix for the "imports after graph" guard:
  ```ts
  if (firstImportLine !== Infinity && graphLine !== Infinity && firstImportLine > graphLine) { ... }
  ```

- [ ] **A1.3.** Same fix for the "package after imports" guard (already partially correct but make symmetric):
  ```ts
  if (pkgLine !== Infinity && firstImportLine !== Infinity && pkgLine > firstImportLine) { ... }
  ```

- [ ] **A1.4.** **Invert** the schema-vs-definitions comparison. Current code emits "schema directive must appear before definitions" when `schemaLine < firstDefLine` — that *is* the canonical order. The diagnostic should fire when schema is AFTER defs. Change:
  ```ts
  if (schemaLine !== Infinity && firstDefLine !== Infinity && schemaLine > firstDefLine) { ... }
  ```

- [ ] **A1.5.** Same inversion for graph-vs-definitions:
  ```ts
  if (graphLine !== Infinity && firstDefLine !== Infinity && graphLine > firstDefLine) { ... }
  ```

- [ ] **A1.6.** **Self-check:** after the edits, run the repro that produced two spurious warnings — confirm it now produces zero:
  ```bash
  cat <<'EOF' | node --input-type=module -
  import { parseString } from './packages/parser/dist/index.js';
  import { Validator } from './packages/semantics/dist/validator.js';
  import { Resolver } from './packages/semantics/dist/resolver.js';
  import { ProjectSymbolTable } from './packages/semantics/dist/project-symbols.js';
  import { resolveManifest } from './packages/semantics/dist/manifest.js';
  const ast = parseString(`schema er namespace entity
  def entity artikl { attributes: [def attribute id { type: int }] }`, 'test.ttr').ast;
  const symbols = new ProjectSymbolTable();
  symbols.upsertDocument('test.ttr', ast, 'er', 'entity', '');
  const v = new Validator(symbols, new Resolver(symbols), resolveManifest({lint:{strict:false}}, '/'));
  console.log(v.validateFileOrdering('test.ttr', ast));
  EOF
  ```
  Expected output: `[]`.

### A2. Replace the `validateFileOrdering` test so it tests the real path (`packages/semantics/src/__tests__/diagnostics-v1.1.test.ts:341–353`)

- [ ] **A2.1.** The current fixture exercises out-of-order tokens that the grammar rejects (parser drops the import and the def — see "Why the test didn't catch it" in review-029 §A1). Since the grammar is order-strict (see A3), there is no way to construct a real string that triggers `ttr/file-ordering` via the parser.

  Replace the test with one that builds an AST **manually with the wrong line numbers** and feeds it directly to `validateFileOrdering`. Concretely:
  ```ts
  it('emits Warning when imports appear after schema directive', () => {
    const { validator } = setupValidator(`schema er namespace entity`, 'test.ttr');
    const ast: Document = {
      // ... shape per AST type
      packageDecl: undefined,
      imports: [{ target: 'pkg_b', wildcard: true, source: { file: 'test.ttr', line: 2, column: 0, endLine: 2, endColumn: 12, offsetStart: 0, offsetEnd: 12 } }],
      schemaDirective: { schemaCode: 'er', namespace: 'entity', source: { file: 'test.ttr', line: 1, column: 0, endLine: 1, endColumn: 26, offsetStart: 0, offsetEnd: 26 } },
      definitions: [],
      source: ...,
    };
    const diags = validator.validateFileOrdering('test.ttr', ast);
    const ordering = diags.find((d) => d.code === DiagnosticCode.FileOrdering);
    expect(ordering).toBeDefined();
    expect(ordering!.message).toContain('import declarations must appear before schema directive');
  });
  ```
  Use the existing `Document` type from `@modeler/parser` — don't invent fields.

- [ ] **A2.2.** Add a positive test: build a canonical-order AST (package line 1, imports line 2, schema line 3, defs line 4) and assert `validateFileOrdering` returns `[]`.

- [ ] **A2.3.** Add the inversion regression test: build an AST where `schemaLine > firstDefLine` (e.g. schema line 5, def line 2) and assert a "schema directive must appear before definitions" warning IS emitted.

### A3. Resolve the contracts §1.4 vs grammar conflict

Pick exactly one option and document the decision. The current state — grammar is order-strict, contracts say grammar is permissive — has been flagged for two reviews now.

- [ ] **A3.1.** Decide which path:
  - **Option α (recommended):** keep the grammar order-strict (matches Kotlin parser in ai-platform), and amend contracts §1.4 to read approximately: *"The grammar is order-strict (`packageDecl? importDecl* (schemaDirective | graphBlock)? definition* EOF`). Out-of-order tokens produce `ttr/parse-error`, not `ttr/file-ordering`. `ttr/file-ordering` exists for tooling — e.g. a future formatter — that operates on a permissive AST builder, and is not currently emittable from regular parsing."* Update the changelog in §11.
  - **Option β:** relax the grammar to `(packageDecl | importDecl | schemaDirective | graphBlock | definition)* EOF`, regenerate the parser, update Kotlin parser sync, then `ttr/file-ordering` becomes the canonical signal.

- [ ] **A3.2.** Whichever option is chosen, commit the docs change alongside the code. If α, the per-code entry in `docs/v1/design/diagnostics.md` for `ttr/file-ordering` should also note "not currently emittable in v1.1 — placeholder for v2 formatter."

### A4. Fix `validateCircularDependencies` source attribution (`validator.ts:406–423`)

- [ ] **A4.1.** Pick a real file for the diagnostic's source. Build a `packageToUris` map (same logic as in `PackageGraphBuilder.build`) and use the first URI of the cycle's first package:
  ```ts
  const packageToUris = new Map<string, string[]>();
  for (const entry of this.symbols.all()) {
    const arr = packageToUris.get(entry.packageName) ?? [];
    arr.push(entry.documentUri);
    packageToUris.set(entry.packageName, arr);
  }
  for (const cycle of cycles) {
    const uri = packageToUris.get(cycle[0])?.[0] ?? '';
    diagnostics.push({
      code: DiagnosticCode.CircularPackageDependency,
      severity: 'warning',
      message: `Package '${cycle[0]}' is part of a cycle: ${cycle.join(' → ')} → ${cycle[0]}. ...`,
      source: { file: uri, line: 1, column: 0, endLine: 1, endColumn: 0, offsetStart: 0, offsetEnd: 0 },
    });
  }
  ```

- [ ] **A4.2.** In `server.ts:225` (where `circularDiags` is collected), **filter by current uri** the same way `validateProject().filter((d) => d.source.file === uri)` does on line 233. Concretely:
  ```ts
  const circularDiags = validator
    .validateCircularDependencies(pkgGraph, docs)
    .filter((d) => d.source.file === uri);
  ```
  Otherwise every file in the cycle (and every file outside it) gets the cycle warning attached.

---

## Section B — Missing deliverables

### B1. Create twelve broken-sample fixtures under `samples/broken/v1.1/`

The directory exists but is empty. One `.ttr` file per code, minimal content that triggers exactly that code. Use this exact filename mapping:

- [ ] `samples/broken/v1.1/unimported-reference.ttr`
- [ ] `samples/broken/v1.1/unused-import.ttr`
- [ ] `samples/broken/v1.1/wildcard-with-no-matches.ttr`
- [ ] `samples/broken/v1.1/duplicate-import.ttr`
- [ ] `samples/broken/v1.1/circular-package-dependency.ttr`  *(this one needs a 2-file fixture — also create `samples/broken/v1.1/circular-package-dependency-b.ttr` and document the pairing in a `samples/broken/v1.1/README.md`)*
- [ ] `samples/broken/v1.1/package-declaration-mismatch.ttr`  *(needs to be placed under a subdirectory like `samples/broken/v1.1/pkg_a/package-declaration-mismatch.ttr`)*
- [ ] `samples/broken/v1.1/missing-package-declaration.ttr`  *(also under a subdirectory)*
- [ ] `samples/broken/v1.1/ambiguous-reference.ttr`  *(plus the two referenced-source files containing `shared_name` in two different packages)*
- [ ] `samples/broken/v1.1/wrong-file-kind.ttr`  *(a `.ttr` containing a `graph` block)*
- [ ] `samples/broken/v1.1/graph-object-not-found.ttrg`
- [ ] `samples/broken/v1.1/graph-layout-stale-node.ttrg`
- [ ] `samples/broken/v1.1/file-ordering.ttr`  *(deferred until A3 lands — if Option α, this fixture is impossible; document in the README that it has no fixture under the current grammar)*

For each, **verify by opening in VS Code** (F5 from `packages/vscode-ext`) that the Problems panel shows the expected code at the expected severity, and only that diagnostic (no unrelated noise from a malformed example).

- [ ] **B1.13.** Add `samples/broken/v1.1/README.md` with one paragraph per fixture: which code it triggers, why it's the minimal example, any cross-file dependencies.

### B2. Extract `packages/semantics/src/package-inference.ts` (B4.2)

- [ ] **B2.1.** Create `packages/semantics/src/package-inference.ts` exporting:
  ```ts
  export function inferPackageFromUri(uri: string, projectRoot: string): { inferred: string; isRootFile: boolean };
  ```
  Move the path-stripping logic out of `validator.ts:430–443`. Handle `file://` URIs explicitly (`new URL(uri).pathname`).

- [ ] **B2.2.** Update `Validator.validatePackageDeclarations` to call the helper.

- [ ] **B2.3.** Add unit tests in `packages/semantics/src/__tests__/package-inference.test.ts`:
  - plain path: `/proj/pkg_a/sub/file.ttr`, projectRoot `/proj/` → inferred `pkg_a.sub`
  - `file://` URI: `file:///proj/pkg_a/file.ttr`, projectRoot `/proj/` → inferred `pkg_a`
  - root file: `/proj/main.ttr`, projectRoot `/proj/` → `isRootFile: true`, inferred `''`
  - `.ttrg` file: `/proj/pkg_a/graphs/main.ttrg`, projectRoot `/proj/` → inferred `pkg_a.graphs`

### B3. Tick the checkboxes in `docs/v1-1/plan/tasks/B4-diagnostics.md`

- [ ] Walk through every `[ ]` in the task list and tick the ones that are actually done. Leave the contracts §1.4 note alone until A3 lands. **Don't** tick B4.9 until C-section below is done.

---

## Section C — `workspace/symbol` ranking (replace the carry-over fix) — ✅ DONE (reviewer-applied)

> **Resolved by the reviewer.** Implementation in `packages/lsp/src/server.ts` (the `connection.onWorkspaceSymbol` handler) and tests in `tests/integration/src/symbol-indexing-extended.test.ts`. Integration suite: **31/31**. The developer does NOT need to redo this section — it's documented here so the approach is clear.

**Why the developer's prefix-match-over-`scored` fix failed (the "fragile test" was right to fail).** The 111 `relation`-kind defs live in `er.ttr` under `namespace entity`, so their qnames are `er.entity.<name>` — **no "rel" substring**. Meanwhile the 111 `er2dbRelation` defs in `map.ttr` have qnames `map.er2dbRelation.<name>` — those *do* contain "rel". `fuzzysort.go('rel', …, { limit: 100 })` therefore returns 100 `er2dbRelation` hits and **zero** `relation` hits. Filtering `scored` by kind can never surface relations because they were never in `scored`. The boost has to draw from the **full symbol index**, not the fuzzy-scored subset.

### C1. ✅ Kind boost now draws from the full index (`server.ts`, `onWorkspaceSymbol`)

- [x] **C1.1.** Deleted both the hardcoded `relation` branch and the `scored`-only kind filter.
- [x] **C1.2.** Replaced with a prefix-or-exact kind match computed over `allSymbols` (the full `projectSymbols.all()` list), floated above the de-duplicated fuzzy results:
  ```ts
  const queryLower = query.toLowerCase();
  const isKindQuery = (kind: string): boolean => {
    const k = kind.toLowerCase();
    return k === queryLower || k.startsWith(queryLower);
  };
  const kindMatched =
    query.length >= 3 ? allSymbols.filter((s) => isKindQuery(s.kind)) : [];
  const seen = new Set(kindMatched.map((s) => s.qname));
  const results =
    kindMatched.length > 0
      ? [...kindMatched, ...scored.map((e) => e.obj).filter((s) => !seen.has(s.qname))].slice(0, 100)
      : scored.map((e) => e.obj).slice(0, 100);
  ```
  The `query.length >= 3` gate prevents a one/two-char name-fragment query (e.g. `"e"`) from being hijacked into "float every entity"; all kind names are ≥4 chars, so a 3-char prefix is discriminating. Generalises to `"ent"`, `"attr"`, `"rel"`, etc. — nothing hardcoded.

### C2. ✅ Integration coverage added (`symbol-indexing-extended.test.ts`)

> **Note on test data:** the H.4 fixture loads only `er.ttr` + `map.ttr`, whose kinds are `entity / attribute / relation / er2dbEntity / er2dbAttribute / er2dbRelation / er2cncRole`. There are **no** `table`/`column`/`view` defs here, so the originally-suggested `"tab"`/`"col"` queries had no data to match — replaced with `"ent"` and `"attr"`.

- [x] **C2.1.** `workspace/symbol query="ent"` → asserts an `entity`-kind result in the top 5 (verified via `getSymbolDetail`, since entities are top-level defs).
- [x] **C2.2.** `workspace/symbol query="attr"` → asserts a `SymbolKind.Field` result in the top 5. Attributes are *nested* defs, and `getSymbolDetail` returns null for nested qnames (the same v1 limitation covered by test 4.6), so the test checks the LSP `SymbolKind` (attribute → `Field`, unique among this fixture's kinds) rather than `getSymbolDetail`.
- [x] **C2.3.** Existing `"rel"` test passes for the right reason now (relations float from the full index, not a hardcoded branch).
- [x] **C2.4.** Full integration suite: **31/31** (was 29; +2 new tests). `pnpm --filter @modeler/lsp test` 45/45, `pnpm --filter @modeler/lsp lint` clean, `pnpm --filter @modeler/lsp typecheck` clean.

---

## Section D — Architecture / cleanliness

### D1. Deduplicate `enclosingQnameOf`

There are now **three** copies (validator.ts, reference-index.ts, server.ts) with **three different shapes**.

- [ ] **D1.1.** In `packages/semantics/src/reference-index.ts`, change `function enclosingQnameOf` to `export function enclosingQnameOf` (it's already the canonical v1.1 shape with kind-fallback and packageName).

- [ ] **D1.2.** In `validator.ts`, delete the local `enclosingQnameOf` (lines 10–15). Import from `reference-index.ts`:
  ```ts
  import { enclosingQnameOf } from './reference-index.js';
  ```
  Update the call site at line 135:
  ```ts
  const enclosingQname = enclosingQnameOf(ownerDef, schemaCode, namespace, packageName);
  ```

- [ ] **D1.3.** In `packages/lsp/src/server.ts`, delete the local `enclosingQnameOf` (lines 156–169). Import from `@modeler/semantics`. Make sure `@modeler/semantics/src/index.ts` re-exports `enclosingQnameOf`:
  ```ts
  export { enclosingQnameOf } from './reference-index.js';
  ```
  Update the server.ts call sites accordingly (also pass `packageName` from the AST's `packageDecl?.name` if available).

- [ ] **D1.4.** Re-run the resolver tests to confirm step-1 lexical resolution now works from inside `relation`/`role`/`er2db*` defs in the validator path (review-029 §E7).

### D2. Fix the `validateCircularDependencies` API

- [ ] **D2.1.** Change the signature from `(packageGraph, documents?)` to either:
  - **(recommended)** `(packageGraph: PackageGraph)` — and add a free function `findCyclesOf(graph: PackageGraph): string[][]` in `package-graph.ts` that operates purely on the graph, no rebuild. Then `validateCircularDependencies` is `findCyclesOf(packageGraph).map(cycle => ...)`. Drop the `documents` parameter entirely.
  - or `(documents: Map<string, Document>)` — build internally, drop the `packageGraph` parameter.

- [ ] **D2.2.** Update the call site in `server.ts:225` to match the new signature. **Use the existing `getPackageGraph()` cache** (see D3 below).

### D3. Use the `getPackageGraph()` cache instead of rebuilding every keystroke

- [ ] **D3.1.** In `server.ts:206–235` (`publishDiagnostics`), replace lines 218–224:
  ```ts
  const docs = new Map<string, Document>();
  for (const uri of documents.keys()) {
    const doc = parseDocument(documents.get(uri)?.getText() ?? '', uri);
    if (doc) docs.set(uri, doc);
  }
  const pkgGraph = new PackageGraphBuilder(projectSymbols, docs).build();
  ```
  With:
  ```ts
  const pkgGraph = getPackageGraph();
  ```

- [ ] **D3.2.** If the new `validateCircularDependencies(packageGraph)` signature from D2.1 is used, drop the `docs` map from this scope entirely.

- [ ] **D3.3.** Verify cache invalidation: `rebuildValidator()` already does `packageGraph = null`. Confirm that adding/removing/changing a file calls `rebuildValidator()` (see lines 276 and 307).

### D4. Wire `manifest.projectRoot` from the LSP initialize handshake

- [ ] **D4.1.** In `server.ts`, find `connection.onInitialize(...)`. After the handshake, capture `params.workspaceFolders?.[0]?.uri`, convert to a filesystem path:
  ```ts
  function uriToFsPath(uri: string): string {
    return uri.startsWith('file://') ? new URL(uri).pathname : uri;
  }
  ```
  Then assign:
  ```ts
  const projectRoot = params.workspaceFolders?.[0]?.uri
    ? uriToFsPath(params.workspaceFolders[0].uri)
    : '';
  manifest = resolveManifest(undefined, projectRoot);
  rebuildValidator(projectRoot);
  ```

- [ ] **D4.2.** When `workspace/didChangeWorkspaceFolders` arrives (if not already handled), re-resolve the manifest. (Out of scope if not handled today — leave a TODO if so.)

- [ ] **D4.3.** Add an integration test that opens a fixture under a project subdirectory and asserts no spurious `ttr/package-declaration-mismatch` Errors are published.

### D5. (Already handled by B2.) `validatePackageDeclarations` → `package-inference.ts`

- [ ] B2.1–B2.3 above cover this. After B2, `validateImports` should NOT also re-do this string manipulation — delete any duplicate logic.

### D6. Decide: shadowing case under `ttr/duplicate-import` or its own code

- [ ] **D6.1.** Pick one:
  - **Option α:** drop the shadowing emission (lines 237–250 of validator.ts). The spec was literal: "Same target imported twice." Document at the call site why shadowing isn't separately flagged in v1.1.
  - **Option β:** add a new code `ttr/import-shadows-wildcard` (Warning). Add it to `DiagnosticCode` enum, contracts §6, and `docs/v1/design/diagnostics.md`. Update the emission to use the new code.

- [ ] **D6.2.** Adjust the corresponding test in `diagnostics-v1.1.test.ts` (the `ttr/duplicate-import` test currently exercises *only* the shadowing case at line 109–139 — replace its fixture with two literal `import pkg_b.something` lines if you take Option α).

### D7. Deduplicate import-target-to-package logic

- [ ] **D7.1.** Add to `packages/semantics/src/references.ts` (or a new `imports.ts`):
  ```ts
  export function packageOfImport(imp: ImportDecl): string {
    if (imp.wildcard) return imp.target;
    const parts = imp.target.split('.');
    return parts.length >= 2 ? parts.slice(0, -1).join('.') : '';
  }
  ```
- [ ] **D7.2.** Replace the three inline copies:
  - `validator.ts:162–168` (in `validateReferences`)
  - `resolver.ts` import resolution (find and replace)
  - `package-graph.ts:184–192` (`resolvePackageOfImport`)

### D8. LSP integration test for `ttr/wrong-file-kind` (B4.8 verification)

- [ ] **D8.1.** Add to `tests/integration/`: open a fixture `.ttr` containing a `graph` block, send `didOpen`, wait for `publishDiagnostics`, assert the diagnostics list contains a `ttr/wrong-file-kind` entry with severity Error.

### D9. Gate `validateTtrgGraph` on `.ttrg` extension

- [ ] **D9.1.** Either in `server.ts:223`:
  ```ts
  const graphDiags = uri.endsWith('.ttrg')
    ? validator.validateTtrgGraph(uri, result.ast)
    : [];
  ```
  Or, at the top of `Validator.validateTtrgGraph`:
  ```ts
  if (!_uri.endsWith('.ttrg')) return [];
  ```
  Pick one; the server-side gate is preferred (the validator should be URI-agnostic where possible).

---

## Section E — Test gaps (add these after D1 lands)

- [ ] **E1.** Negative `validateFileOrdering` test: canonical-order AST → expects `[]`. (Added in A2.2.)
- [ ] **E2.** Positive `validateFileOrdering` test via hand-built AST. (Added in A2.1, A2.3.)
- [ ] **E3.** `validatePackageDeclarations` with `file://` URI: file `file:///proj/pkg_a/x.ttr`, projectRoot `/proj/` → no false mismatch.
- [ ] **E4.** `validatePackageDeclarations` on a root-level file (e.g. `/proj/main.ttr`) → emits NO `MissingPackageDeclaration`.
- [ ] **E5.** `validateReferences` does NOT emit `UnimportedReference` when the resolver returns `viaStep: 'auto-import'` (stock cnc auto-import path). Build a fixture that references `cnc.cnc.role.fact` without importing — expect no diagnostic.
- [ ] **E6.** `validateCircularDependencies` attaches the diagnostic to a real `documentUri` from the cycle (not `''`). Update the existing cycle test to assert `diag.source.file !== ''`.
- [ ] **E7.** Lexical step-1 resolution works for `relation`/`role`/`er2db*` defs. Add a resolver-level test: a ref inside a `relation` resolves via step 1 when the target is a sibling in the same enclosing context. (Requires D1 to land first.)
- [ ] **E8.** Integration tests for `workspace/symbol` queries `"tab"`, `"col"`, `"attr"`. (Added in C2.)

---

## Section F — Cosmetic / low priority (can ship after the rest)

- [ ] **F1.** Extract `pushOrdering(message, source)` helper in `validateFileOrdering` to reduce the five near-identical `diagnostics.push` blocks.
- [ ] **F2.** Fold "used import" tracking into the existing resolver pass in `validateReferences` so `validateImports` doesn't re-resolve every ref.
- [ ] **F3.** Fix `packages/parser/src/walker.ts:55` — `'GraphPropertyContext' is defined but never used`. Either use it or delete the import. (Pre-existing, but `pnpm -r lint` is in the verify-by-running list.)
- [ ] **F4.** Restore the "Adding a new diagnostic code" heading in `docs/v1/design/diagnostics.md` (the v1.1 entries were inserted above its first paragraph and broke the structure).

---

## Verify by running

After each Section, run:

```bash
pnpm -r build
pnpm --filter @modeler/semantics test          # expect 91/91 → grows by ~5 once E1/E2/E5/E6 land
pnpm --filter @modeler/integration-tests test  # expect 29/29 → grows by ~5 once C2/D4.3/D8 land
pnpm -r typecheck
pnpm -r lint                                   # must be clean after F3
```

Plus the manual smoke check from `B4-diagnostics.md`:

> Open one of the broken fixtures in VS Code (manual smoke check) — Problems panel shows the expected code and severity.

Do this for **each** of the twelve fixtures from B1. Confirm none of them produce spurious extras (no surprise `ttr/file-ordering` warnings, no `ttr/package-declaration-mismatch` from a misconfigured projectRoot).

---

## DONE-when checklist (cross-reference against `B4-diagnostics.md`)

- [ ] All checkboxes above are ticked.
- [ ] `samples/broken/v1.1/` contains the twelve fixtures (or eleven + a documented exception for `file-ordering` if A3 lands as Option α).
- [ ] `validateFileOrdering` returns `[]` on canonical files (A1.6 repro).
- [ ] `workspace/symbol` test coverage exists for `"rel"`, `"tab"`, `"col"`, `"attr"`.
- [ ] `enclosingQnameOf` exists in **one** place only.
- [ ] `manifest.projectRoot` is set from the workspace folder, not `''`.
- [ ] Contracts §1.4 and the grammar agree.
- [ ] `STATUS.md` line `[x] B4 diagnostic` reflects reality (and `B4-diagnostics.md` checkboxes are ticked).
