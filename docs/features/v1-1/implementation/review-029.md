# Review 029 — Section B4 (Diagnostic codes)

**Branch:** v1-1
**Reviewed:** B4 implementation (validator code paths for the twelve new diagnostic codes, LSP wiring, the `workspace/symbol` ranking carry-over, docs update)
**Verdict:** **Not done.** Tests are green and 91/91 + 29/29 pass, but at least two of the new diagnostics are functionally broken on real input (the test files don't exercise the path that runs in production), several required deliverables are missing, and the B3 architectural regressions that were supposed to be cleaned up here have been re-entrenched in a new copy of the code.

The headline numbers (semantics 91/91, integration 29/29, typecheck clean) hide that:
- `ttr/file-ordering` fires **on every canonical, no-imports file** — confirmed by direct repro.
- The `workspace/symbol` "fix" hardcodes the kind `'relation'`; queries like `"tab"`, `"col"`, `"view"`, `"attr"` get no boost.
- `samples/broken/v1.1/` is empty; the "Tests-first" deliverable (twelve fixtures) was not produced.
- `enclosingQnameOf` exists in **three** locations with **three different shapes** now (validator.ts, reference-index.ts, server.ts); the validator's copy is the v1 shape that B3 was meant to retire.

Reviewed against `docs/v1-1/plan/tasks/B4-diagnostics.md` and contracts §§1.4, 3.1, 6.

---

## A. Critical correctness bugs

### A1 — `validateFileOrdering` fires bogus warnings on canonical files (B4.6)

**Repro** (run on the built dist; output captured below):

```
schema er namespace entity
def entity artikl { attributes: [def attribute id { type: int }] }
```

Produces two `ttr/file-ordering` Warnings:
1. `"import declarations must appear before schema directive"` — on a file with **no imports at all**.
2. `"schema directive must appear before definitions"` — on a file in **canonical order** (schema before defs).

**Root cause** (`packages/semantics/src/validator.ts:295–349`):

- For "imports after schema": the guard is `firstImportLine > schemaLine && schemaLine !== Infinity`. When there are no imports, `firstImportLine === Infinity` and `Infinity > 1` is `true`. The guard should be `firstImportLine !== Infinity && firstImportLine > schemaLine`.
- For "schema must appear before definitions": the comparison is **inverted** — `schemaLine < firstDefLine` is the canonical order; the diagnostic should fire when `schemaLine > firstDefLine`. Same inversion for the graph/defs check.

**Why the test didn't catch it.** The test at `diagnostics-v1.1.test.ts:341–353` uses an out-of-order source where the grammar rejects `import` (the grammar is order-strict: `document: packageDecl? importDecl* (schemaDirective | graphBlock)? definition* EOF`). The parser recovers but swallows the `import` and the `def`. So at test time `ast.imports.length === 0` and `ast.definitions.length === 0`; the test passes only because the Infinity-vs-number bug fires. The intended scenario (imports lexically after schema) was never actually exercised.

This is the most serious defect in B4: in production every well-formed v1 file (one schema directive, definitions, no imports) will get two unsolicited Warnings in the Problems panel.

**Confirmed by direct repro** against built `packages/semantics/dist/validator.js`.

### A2 — `validateFileOrdering` test passes for the wrong reason (B4.6 test)

Same root cause as A1. The fixture string contains `import pkg_b.*` after `schema er namespace entity`, but the order-strict grammar produces a parse error on the `import` token. The result: 0 imports in the AST. The test asserts a `FileOrdering` diagnostic is present — and one is, but because of A1, not because of the intended ordering check.

The test does not verify which message it found, which is also a smell.

### A3 — Contracts §1.4 vs grammar conflict left unresolved

`B4-diagnostics.md` explicitly flagged this in the B4.6 note:

> If B4 implements the permissive variant (where the grammar accepts any order and `ttr/file-ordering` is the only warning), that is a deliberate redesign requiring a contract amendment. Do not silently leave the doc out of sync with the grammar.

The grammar still requires the canonical order (verified at `packages/grammar/src/TTR.g4:19–21`). Out-of-order files produce `ttr/parse-error`, not `ttr/file-ordering`. The diagnostic in its current form is therefore effectively dead for the case the contract describes — only the spurious cases from A1 ever fire.

Either:
- amend contracts §1.4 to say "the grammar is order-strict; `ttr/file-ordering` exists only as an aspirational pre-v2 placeholder, and is **not** currently emittable on real input" — **or**
- relax the grammar to `(packageDecl | importDecl | schemaDirective | graphBlock | definition)* EOF` and gate ordering checks in the validator.

Pick one and document it. Right now contracts §1.4 and the implementation disagree, and the disagreement was called out a week ago.

### A4 — `validateCircularDependencies` produces a diagnostic with `source.file: ''`

`validator.ts:419`:

```ts
source: { file: '', line: 1, column: 0, endLine: 1, endColumn: 0, offsetStart: 0, offsetEnd: 0 },
```

The new LSP wiring (`server.ts:225–228`) emits these into `diagnostics` for the *currently-edited* document. Every edit to any file in a cyclic project will surface the same cycle warning attached to the wrong file. Spec (B4.5) said: "emit one Warning per participating package (**at line 1 of an arbitrary file in that package**)" — pick a real `documentUri` from `packageToUris.get(cyclePkg)` and attach the source to that file's `source.file`, then filter against the current `uri` in `publishDiagnostics` (the same way the existing project-level diagnostics are filtered at `server.ts:233`).

---

## B. Missing deliverables (deviations from the task list)

### B1 — `samples/broken/v1.1/` is empty (Tests-first §2)

The task list required **twelve fixtures** under `samples/broken/v1.1/<code>.ttr`, one per code. The directory was created but is empty. `ls samples/broken/v1.1/` returns nothing.

This is also called out in "DONE when": "Twelve fixtures in `samples/broken/v1.1/` exist and produce exactly the expected diagnostic." None exist.

### B2 — `packages/semantics/src/package-inference.ts` was not created (B4.2)

The task said explicitly: "The path-inference helper goes in `packages/semantics/src/package-inference.ts`." Instead the logic was inlined into `Validator.validatePackageDeclarations` at `validator.ts:425–462`.

This isn't merely a placement issue: the helper is the natural home for the URI-stripping / normalisation logic that B4.2 + the future `ttr/wrong-package-name` quick-fix (I3) need. Inlining it means I3 will either duplicate it or refactor the validator.

### B3 — All checkboxes in `B4-diagnostics.md` still `[ ]`

The developer marked B4 `[x]` in `STATUS.md` but did not tick any of the per-task checkboxes in `docs/v1-1/plan/tasks/B4-diagnostics.md` (Tests-first, B4.1–B4.9, DONE when). Process slip — please reflect actual completion state.

---

## C. The `workspace/symbol` carry-over (B4.9) — wrong fix

`packages/lsp/src/server.ts:600–635` has **two** ranking branches:

1. **Branch 1 (lines 601–616):** "exact-kind match" — `entry.obj.kind.toLowerCase() === queryLower`. This matches the recommended approach in B4.9 only when the user types the kind name in full (e.g. `"entity"`, `"relation"`).
2. **Branch 2 (lines 618–635):** a **hardcoded special case for `kind === 'relation'`**. It filters `kindMatches` to only those whose `kind === 'relation'`, then boosts only those. This is the path that makes the failing integration test pass (query `"rel"` → hardcoded relation boost).

Issues with branch 2:
- It is hardcoded to one kind. `"tab"` won't boost `table`, `"col"` won't boost `column`, `"view"` won't boost `view` (would actually be matched by branch 1 if typed in full), `"attr"` won't boost `attribute`. The fix only works for the exact test that prompted the carry-over.
- It also walks `allSymbols` a second time (`O(N)`), then double-filters — wasteful and confusing.
- Two branches with overlapping intent means future maintainers will read both and not understand which fires when.

**The correct surgical fix** (the recommended option in the task): make branch 1 do a **prefix or substring match on kind**, and delete branch 2. E.g.:

```ts
const queryLower = query.toLowerCase();
const matchesKind = (k: string) => k.toLowerCase() === queryLower || k.toLowerCase().startsWith(queryLower);
const exactKindMatches = scored.filter((e) => matchesKind(e.obj.kind));
if (exactKindMatches.length > 0) {
  const others = scored.filter((e) => !matchesKind(e.obj.kind));
  return [...exactKindMatches, ...others].slice(0, 100).map(toSymbolInfo);
}
return scored.map(toSymbolInfo);
```

That handles `"rel"` (prefix of `"relation"`), `"tab"`, `"col"`, etc., uniformly, and removes the duplicated branch. The integration test passes for the right reason, and the behaviour generalises.

---

## D. Architectural / cleanliness issues

### D1 — `enclosingQnameOf` now lives in three places with three shapes

| Location | Shape |
|---|---|
| `reference-index.ts:5–21` | **Correct** v1.1 shape: `[package?, schemaCode, namespace‖kind, def.name]`, covers 10 kinds (entity, table, view, procedure, relation, query, role, er2db*, er2cncRole). |
| `validator.ts:10–15` | **v1 shape**: `[schemaCode, namespace, def.name]`. No kind-fallback, no packageName. Only covers entity / table / view / procedure — i.e. lexical resolution from inside `relation`/`role`/`er2db*` defs is silently disabled in the *validator's* call to the resolver. |
| `server.ts:156–169` | **Yet another shape**: `[schemaCode, namespace‖kind, def.name]`. Includes kind-fallback but no packageName; covers 10 kinds. |

This is the same bug class B3 review-028 supposedly cleaned up — it just got copied into another file. The B3 fix was meant to make `reference-index.ts:enclosingQnameOf` canonical and have everyone call into it.

Action: **export `enclosingQnameOf` from `reference-index.ts`** (or move it to a new `packages/semantics/src/qname-context.ts`), import in both `validator.ts:135` and `server.ts:156`, delete the local copies.

### D2 — `Validator.validateCircularDependencies` ignores its own argument

```ts
validateCircularDependencies(
    _packageGraph: PackageGraph,
    _documents?: Map<string, Document>
  ): ValidationDiagnostic[] {
    const diagnostics: ValidationDiagnostic[] = [];
    const docs = _documents ?? new Map();
    const builder = new PackageGraphBuilder(this.symbols, docs);
    const cycles = builder.findCycles();
```

The caller (`server.ts:225`) passes `pkgGraph` (already built by the same `PackageGraphBuilder`), then this method ignores it and rebuilds the graph again. Either:
- take only `documents: Map<string, Document>` and build internally — drop the `_packageGraph` parameter, or
- take only `packageGraph: PackageGraph` and call `findCycles()` on it (which means `PackageGraph` needs `findCycles` exposed, or we make `findCycles` a free function on a graph).

Either is fine. The current API misleads the caller into thinking the prebuilt graph is being reused.

### D3 — LSP rebuilds the package graph on every keystroke

`server.ts:218–224` (in `publishDiagnostics`):

```ts
const docs = new Map<string, Document>();
for (const uri of documents.keys()) {
  const doc = parseDocument(documents.get(uri)?.getText() ?? '', uri);
  if (doc) docs.set(uri, doc);
}
const pkgGraph = new PackageGraphBuilder(projectSymbols, docs).build();
```

`publishDiagnostics` runs on every text change. This re-parses **every open document** and rebuilds the entire package graph for every keystroke. There is already a `getPackageGraph()` lazy cache at `server.ts:194–204` with invalidation via `packageGraph = null` in `rebuildValidator`. Use it.

Even better: cache the parsed AST per document (the existing `parseDocument` helper isn't memoised), since `publishDiagnostics` already parses the *current* document above on line 209 and then re-parses *all* documents on line 220. For a project with 50 open files, every keystroke does 50 reparses.

### D4 — `manifest.projectRoot` is never set from the LSP initialize handshake

Line 96: `let manifest: ResolvedManifest = resolveManifest(undefined, '');` — projectRoot is `''`.

`rebuildValidator(projectRoot?)` accepts a projectRoot but the call sites at lines 276 and 307 pass no argument, so it stays `''` forever. There is no place in `onInitialize` that calls `resolveManifest` or `rebuildValidator` with the workspace folder.

Consequence: in the *running* LSP, `validatePackageDeclarations` computes `relativePath` against an empty projectRoot. Every file `uri` becomes its own relative path, and the inferred package is some nonsense built from the leading URL segments (e.g. `file://Users/bora/project/pkg_a/file.ttr` → segments include `file:` and `Users` and `bora`). Expect torrents of `ttr/package-declaration-mismatch` Errors when the extension is loaded on a real workspace.

The tests set `manifest.projectRoot` manually, so they didn't catch this.

Action: in `onInitialize` (or the `initialized` handler), capture the first `workspaceFolders[0].uri`, convert URI → path, and call `manifest = resolveManifest(undefined, projectPath)` (or call the new `rebuildValidator(projectPath)`).

### D5 — `validatePackageDeclarations` URI handling is fragile

`validator.ts:430–443`:

```ts
const relativePath = uri.startsWith(projectRoot)
  ? uri.slice(projectRoot.length)
  : uri;
const segments = relativePath.split('/').filter(Boolean);
const isRootFile = segments.length === 1 || (segments.length === 2 && segments[1].startsWith('.'));
const fileName = segments[segments.length - 1] ?? '';
```

- `isRootFile` and `fileName` are computed but never used past their declaration (`fileName` *is* declared but not used; `isRootFile` is used only in the next `if`). Dead-ish.
- `uri.startsWith(projectRoot)` fails when `uri` is `file:///Users/...` and `projectRoot` is `/Users/...` (the scheme prefix breaks the match). Real LSP clients send `file://` URIs. Test never exercises this because tests pass plain paths.
- `withoutExt.map((s) => s.replace(/\.(ttr|ttrg)$/, ''))` is applied to *directory* segments, which never have `.ttr`/`.ttrg` extensions. Harmless but misleading — the extension stripping only matters for the (unused) `fileName`.

Action: extract the helper into `packages/semantics/src/package-inference.ts` per B4.2, handle the `file://` scheme there (`new URL(uri).pathname`), and unit-test it.

### D6 — `validateImports` quietly emits `DuplicateImport` for the shadowing case

`validator.ts:237–250` emits `ttr/duplicate-import` when a **named import shadows a wildcard** of the same package. The task list (B4.4) said: "Same target imported twice → Warning on the second import." Shadowing is a different scenario — `import pkg_b.*` followed by `import pkg_b.some_def` are not "the same target." Either:
- emit a distinct code (`ttr/import-shadows-wildcard`?) and add it to contracts §6 + diagnostics.md, or
- drop the shadowing emission and stick to the spec's literal duplicate-target case.

The docs.md entry **does** describe the shadowing case under `ttr/duplicate-import` — but that's a doc-stretches-to-fit-impl pattern, not a designed behaviour. Decide and document explicitly.

### D7 — `validateReferences` constructs `importedPkgs` set inline; duplicates resolver logic

`validator.ts:160–169` recomputes the import-target-to-package mapping with the same `parts.slice(0, -1).join('.')` logic that lives in `resolver.ts` and `package-graph.ts:resolvePackageOfImport`. Three copies, all equivalent, all liable to drift. Extract to a single helper exported from `references.ts` or a new `imports.ts`.

### D8 — `ttr/wrong-file-kind` "verified" by a parser-side test, not the validator path

The test at line 252–263 confirms `parseString` emits the code in `result.errors`. That's correct — `wrong-file-kind` is parser-emitted. But the task line said "verify here too" and the implication is to confirm the diagnostic reaches the LSP's `publishDiagnostics` flow alongside the new semantics codes. Add an LSP-level integration test (in `tests/integration/`) that opens a `.ttr` fixture containing a `graph` block and asserts the Problems-panel-bound diagnostics include `ttr/wrong-file-kind`. Otherwise B4.8 ("Wire the validator into the LSP's publishDiagnostics flow ... verify") is unproven for this code.

### D9 — `validateTtrgGraph` runs unconditionally on every document, not only `.ttrg` files

`server.ts:223`: `const graphDiags = validator.validateTtrgGraph(uri, result.ast);`

The function only returns diagnostics if `ast.graph` is present, so functionally it's a no-op for non-graph files — but it walks the AST and does string `replace(/\.ttrg$/)` on every `.ttr` URI. Trivially cheap, but: if a graph block ever lands in a `.ttr` file (post-parser-recovery), this will emit `ttr/graph-name-mismatch` against a `.ttr` filename. Gate the call on `uri.endsWith('.ttrg')` in the LSP wiring, or early-return inside the function based on URI.

---

## E. Test gaps (the 14 passing semantics tests aren't enough)

The acceptance-criteria text reads as if exhaustive coverage exists, but several scenarios are uncovered:

- E1 — **A canonical file produces NO `ttr/file-ordering` diagnostic.** (Currently fails — see A1.) Add a negative test.
- E2 — **`ttr/file-ordering` actually fires for out-of-order content.** Since the grammar rejects out-of-order content, this test should either (a) skip until contracts §1.4 is resolved, or (b) rely on a synthetic AST that bypasses the parser.
- E3 — **`ttr/package-declaration-mismatch` with a `file://` URI.** Currently all tests use raw paths; the production code path with VS Code URIs is uncovered.
- E4 — **`ttr/missing-package-declaration` is NOT emitted for a root-level file.** The implementation has an `isRootFile` branch that's never exercised.
- E5 — **`ttr/unimported-reference` is suppressed when the resolver returns `viaStep: 'auto-import'` (stock cnc).** The auto-import path should not produce the unimported-reference warning. Add a test that imports a stock cnc symbol without an explicit import and asserts no `UnimportedReference` is emitted.
- E6 — **`ttr/circular-package-dependency` is attached to a real file, not `source.file: ''`.** Currently the assertion only checks `severity === 'warning'`; the source.file is wrong (see A4) and the test misses it.
- E7 — **Lexical (step-1) resolution from inside a `relation`/`role`/`er2db*` def works.** With `validator.ts:enclosingQnameOf` returning `undefined` for those kinds, step-1 silently fails for refs inside them. Add a regression test in `validator.test.ts` or `resolver.test.ts`.
- E8 — **`workspace/symbol` with query `"tab"` floats tables.** Integration test currently exists only for `"rel"`. Add coverage for `"tab"`, `"col"`, `"view"`, `"attr"` so the C-section fix is regression-proof.

---

## F. Minor / cosmetic

- F1 — `validator.ts:296–349` has 5 nearly-identical `diagnostics.push` blocks. Extract a small helper `pushOrdering(message, source)` and inline the comparisons.
- F2 — `validateImports` walks `collectAllReferences` and resolves each ref a second time (lines 258–273), independently of `validateReferences`. Two resolve passes over the same references per document. Fold "used import" tracking into the existing resolver pass to amortise cost.
- F3 — Lint: pre-existing `'GraphPropertyContext' is defined but never used` error in `packages/parser/src/walker.ts:55` blocks `pnpm -r lint`. Unrelated to B4 but the verify-by-running checklist requires clean lint. Either fix or carry over to the next phase.
- F4 — Diagnostics doc (`docs/v1/design/diagnostics.md`) inserts the new entries above the "Adding a new diagnostic code" section's first paragraph, which now appears mid-content with no heading. Re-add the heading or move the v1.1 entries below the "Adding a new diagnostic code" section.

---

## G. What's good

To balance the list above:

- `DiagnosticCode` union extension at `parser/src/diagnostics.ts:12–24` is clean — codes in code-order, no `any`, no churn elsewhere.
- The thirteen-case test file is well-structured; each describe block sets up its own `ProjectSymbolTable` so suites are independent.
- The `UnimportedReference` path (validator.ts:158–179) correctly checks both `resolvedPackage !== packageName` and "is the package imported," matching contracts §6 verbatim.
- `validateImports` Wildcard-with-no-matches branch (lines 213–223) reads cleanly.
- `findCycles()` consumption in `validateCircularDependencies` correctly uses Tarjan and yields one diagnostic per cycle.
- `docs/v1/design/diagnostics.md` got a full pass — every code has trigger/severity/fix sections in the same template style as the v1 entries.

The bones are right. Most of the issues above are *finishing*: cover the runtime path, deduplicate the qname helpers, wire `projectRoot`, replace the `relation` kludge, and produce the fixtures.

---

## Verification commands run

```
pnpm -r build                        # green
pnpm --filter @modeler/semantics test            # 91/91 green
pnpm --filter @modeler/integration-tests test    # 29/29 green
pnpm -r typecheck                    # green
pnpm -r lint                         # FAIL — pre-existing walker.ts warning
node repro of validateFileOrdering on canonical file → emits 2 spurious diagnostics
node repro of parser on `schema\nimport\n` → import is swallowed (parse error)
```

See `tasks-review-029.md` for the actionable, ordered fix list.
