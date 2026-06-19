# Contracts — Default (root) package name

## 1. `modeler.toml` schema addition

```toml
[project]
name = "df-ai-models"
version = "0.1.0"
package = "df"          # NEW — name of the default (root) package. Optional.
```

- **Key:** `project.package`
- **Type:** string
- **Optional:** yes. Omitted ⇒ `rootPackage = ""` ⇒ behaviour identical to today.
- **Allowed value:** a TTR qualified name — `segment ('.' segment)*` where each `segment` matches the
  lexer `IDENT` rule `[a-zA-ZÀ-ɏ_][a-zA-Z0-9_À-ɏ]*` (`grammar/TTR.g4:628`). Keyword-only segments
  (e.g. `db`, `table`) are accepted by the grammar's `idPart` but are **not** required to be
  supported as a manifest value; validation MAY restrict to `IDENT`-shaped segments.
- **Reference regex** (starting point — must accept the diacritics the model uses, e.g. `účetnictví`):

  ```
  ^[A-Za-zÀ-ɏ_][A-Za-z0-9_À-ɏ]*(\.[A-Za-zÀ-ɏ_][A-Za-z0-9_À-ɏ]*)*$
  ```

- **Invalid value:** emit a manifest-level diagnostic and fall back to `rootPackage = ""` (OQ3 —
  non-fatal). Do not throw / do not block project load.

## 2. TypeScript interface changes (`semantics/manifest.ts`)

```ts
export interface ProjectManifest {
  project?: { name?: string; version?: string; package?: string };   // + package
  // ...unchanged...
}

export interface ResolvedManifest {
  name: string;
  projectRoot: string;
  rootPackage: string;          // NEW — "" when unset/invalid
  preferredLanguage: string;
  declaredSchemas: string[];
  namespaces: Record<string, string>;
  stockVocabularies: string[];
  lint: { strict: boolean; requireDescriptions: boolean };
}
```

`resolveManifest()` gains:

```ts
rootPackage: isValidPackageName(m?.project?.package) ? m!.project!.package! : '',
```

(`isValidPackageName` — new exported predicate; returns `false` for `undefined`/`""`/non-matching.)

## 3. New helper (`semantics`)

```ts
/**
 * The effective package name for a document, honouring (in order):
 *   1. stock:// URIs        → "" (stock cnc must stay nameless)
 *   2. explicit packageDecl → its name (absolute override — decision D2)
 *   3. root-level file      → rootPackage (decision D1)
 *   4. otherwise            → "" (sub-dir / no decl — unchanged behaviour)
 */
export function effectivePackageName(
  ast: { packageDecl?: { name: string } | null },
  uri: string,
  projectRoot: string,
  rootPackage: string,
): string;
```

- Pure, no I/O. Reuses `inferPackageFromUri(uri, projectRoot).isRootFile`.
- When `rootPackage === ""`, returns exactly what `ast.packageDecl?.name ?? ''` returns today (for
  non-stock files), guaranteeing a no-op default.

Exported from `semantics/index.ts` alongside `inferPackageFromUri`.

## 4. Activating the dormant `upsertDocument` parameter

`semantics/project-symbols.ts:9` and `semantics/symbol-table.ts:45`:

```ts
// project-symbols.ts — before
upsertDocument(uri, ast, schemaCode, namespace, _packageName = ''): void

// after — parameter is now USED and forwarded to DocumentSymbolTable
upsertDocument(uri, ast, schemaCode, namespace, packageName = ''): void
```

`DocumentSymbolTable` (symbol-table.ts) takes `packageName` from the constructor argument instead of
re-deriving `ast.packageDecl?.name ?? ''`. **Caller contract:** callers MUST pass
`effectivePackageName(ast, uri, projectRoot, manifest.rootPackage)`. A caller that passes nothing
(default `''`) preserves a degenerate but safe behaviour (empty package) — acceptable only for tests
that don't exercise root-package naming.

> ⚠️ Conformance note: keep the `isStockCnc` gate working. With stock URIs short-circuited to `""`
> by the helper, `isStockCnc = schemaCode==='cnc' && !packageName && uri.startsWith('stock://')`
> stays true. Add a regression test that a configured `rootPackage` does **not** leak onto stock
> roles (`cnc.cnc.role.fact` stays `cnc.cnc.role.fact`).

## 5. Resolution context (`semantics/resolver.ts`)

No signature change. The **caller** that builds `ResolutionContext` for references inside a root file
must set `packageName` to the effective name (so step 2 *same-package* and `getByPackage()` work):

```ts
const pkg = effectivePackageName(ast, uri, projectRoot, manifest.rootPackage);
resolver.resolveReference(ref, { schemaCode, namespace, imports, packageName: pkg });
```

Resulting resolution behaviour for a root package named `df`:

| Reference site | Resolves via |
|---|---|
| Bare ref to a sibling def in the same root file/package | step 2 *same-package* (`df.er.entity.X`) |
| Cross-package ref **to** a root-package def, with `import df.er.entity.X` | step 3 *named-import* |
| Cross-package ref **to** a root-package def, with `import df.*` | step 4 *wildcard-import* (now possible — was impossible with the nameless default) |
| Bare-but-unique FQN | step 6 (now `df.er.entity.X`, no longer an ambiguous suffix) |

## 6. LSP qname-builder call sites (must all use the effective name)

| File:line | Today | After |
|---|---|---|
| `lsp/graph-methods.ts:56` | `ast.packageDecl?.name ?? ''` | `effectivePackageName(ast, uri, root, rootPackage)` |
| `lsp/model-graph.ts:541` | `ast.packageDecl?.name ?? ''` | same |
| `lsp/completion-reference.ts:102/107/114/309` | `doc.packageDecl?.name ?? ''` | same |
| `lsp/code-lens.ts:17/31` | `ast.packageDecl?.name ?? ''` | same (display) |
| `lsp/document-symbol.ts` | shows `doc.packageDecl.name` only | optionally synthesise a root-package symbol when none declared |

`ResolvedManifest` is already available where the model graph is built (`lsp/model-graph.ts:421`
takes `manifest: ResolvedManifest`); thread `manifest.rootPackage` + document URI + `projectRoot`
into the helpers that currently take only the AST.

## 7. Completion contract (`lsp/completion-property.ts`)

`package`/`import` completion (`completion-property.ts:330-331` detect these contexts) SHOULD offer
`manifest.rootPackage` as a candidate when it is set, labelled e.g. `df (project default package)`,
so authors can declare it explicitly or import from it.

## 8. Test contracts (TDD — author before impl)

Unit (`semantics`):
- `effectivePackageName`: stock→`""`; explicit decl wins over `rootPackage`; root file + `rootPackage`
  →`rootPackage`; sub-dir no-decl →`""`; `rootPackage===""` reproduces `ast.packageDecl?.name ?? ''`.
- `resolveManifest`: valid `package` → set; invalid → `""`; missing → `""`.
- `isValidPackageName`: ascii, dotted, diacritics (`účetnictví`), reject leading digit / spaces / empty / trailing dot.
- symbol-table: root file with `rootPackage="df"` registers `df.er.entity.X`; stock roles stay `cnc.cnc.role.*`.

Component (`semantics` resolver):
- root-package def reachable via `import df.*` (wildcard) and `import df.er.entity.X` (named) from
  another package; bare-but-unique FQN `df.er.entity.X` resolves via step 6 without ambiguity.

Integration (`tests/integration`):
- boot server with a `modeler.toml` carrying `[project] package = "df"`, open a root `.ttr` with no
  declaration + a second file in package `app` importing `df.*`; assert the cross-package reference
  resolves (no diagnostic) and `modeler/getModelGraph` emits node id `df.er.entity.X`.

## 9. Lint behaviour contract (`@modeler/lint`)

Path inference drives lint, not qnames (architecture §2b). With a configured `rootPackage`:

| Rule | Today | After |
|---|---|---|
| `missing-package-declaration` (info) | fires for **non-root** files with no decl; roots skipped via `isRootFile` | unchanged for sub-dir; for a **root** file with no decl + `rootPackage` set, do **not** emit (the file *is* in `df` by config) — or emit an info worded as "(project default)" |
| `package-declaration-mismatch` (error) | declared ≠ path-inferred → error (inferred is `""` for roots, so roots never fire) | unchanged for sub-dir; roots stay non-erroring (D2). Optional **info** when a root decl ≠ `rootPackage` (OQ3, default-off) |

The lint `ctx.manifest` is the `ResolvedManifest` — `rootPackage` flows in for free once Phase 1 lands.
No new rule is required; the change is making the existing two rootPackage-aware for root files.

## 10. Kotlin contract (`ttr-semantics`)

Mirror of §1–§4, kept byte-conformant by the harness.

```kotlin
// Manifest.kt — new
data class ProjectManifest(val rootPackage: String = "")          // parsed from [project].package
fun parseManifest(toml: String): ProjectManifest                  // tolerant; invalid package → ""
fun isValidPackageName(s: String?): Boolean

// effective package — mirror of TS effectivePackageName (§4)
fun effectivePackageName(
    packageDecl: String?,      // ast.packageDecl?.name
    uri: String,
    projectRoot: String,
    rootPackage: String,
): String                       // reuses PackageInference.inferFromUri(uri, projectRoot).isRootFile
```

- Same precedence: `stock://` → `""`; declaration → absolute; root file → `rootPackage`; else `""`.
- `SymbolTable` keeps taking a `packageName` (declared today); the **caller** supplies the effective
  name. Do not change the table's qname assembly.
- Conformance: extend `SemanticsConformanceDump` with a `modeler.toml` + root-file fixture; TS and
  Kotlin must emit identical qnames (`df.er.entity.X`).
- TOML dependency (OQ2): proposed in `ttr-semantics` (e.g. a small Kotlin TOML lib); alternatively
  ai-platform parses and passes `rootPackage` (then `Manifest.kt` is unnecessary).

## 11. ai-platform loader contract (`infra/metadata`)

Effective package per file at load time:

```
effectivePackage(file):
    if file.declaredPackage != null:                 return file.declaredPackage   # D2
    if file.computedPackage (from path) is empty:    return rootPackage            # root file → configured name
    return file.computedPackage                                                    # sub-dir (unchanged)
```

- `rootPackage` source: `<modelDir>/modeler.toml` `[project].package`, where `modelDir` is the loaded
  git subdir (`METADATA_GIT_SUBDIR`, e.g. `model-ttr`). Missing manifest ⇒ `rootPackage = ""` ⇒
  today's behaviour (root files exempt — `ReferenceResolutionPass.kt:45`).
- Apply on the **go-forward** resolution path (OQ1): `PublishedResolverAdapter.kt:43`
  (`packageName = effectivePackage(f)`) and/or `ReferenceResolutionPass.kt:37`.
- Keep `ttr/package-declaration-mismatch` (`Source.kt:249`) for **sub-dir** files; root files with a
  declaration follow D2 (declaration wins, no mismatch against `rootPackage`).
- Conformance acceptance: the same `modeler.toml` + root-file fixture resolves identically through the
  modeler LSP (`modeler/getModelGraph` node id) and the ai-platform loader (resolved `packageName`).
