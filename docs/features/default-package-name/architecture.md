# Architecture — Default (root) package name

## 1. Problem

A `.ttr` file with no `package` declaration belongs to the **default (root) package**, represented by
the *absence* of a name (empty string). Empty-package defs get qnames with no package segment (e.g.
`er.entity.artikl`), which the resolver can only reach via the *fully-qualified* step (an ambiguous
suffix match) or a named import — and which can **never** be wildcard-imported (no name to put in
`import <pkg>.*`). Giving the default package a real name collapses this to ordinary resolution.

## 2. Two notions of "package" (verified against code)

This is the crux, and it spans both repos. **Package** means two different things in two layers, and
they are deliberately decoupled:

### 2a. Semantic / qname layer — *declaration-only* (authoritative for resolution)

| Fact | Evidence |
|---|---|
| Effective package for qnames = `ast.packageDecl?.name ?? ''`, everywhere | `semantics/symbol-table.ts:45`, `lsp/server.ts:747,791`, `lsp/model-graph.ts:541`, `lsp/graph-methods.ts:56`, `lsp/completion-reference.ts:102/107/114/309`, `lsp/code-lens.ts:17/31` |
| `ProjectSymbolTable.upsertDocument(...,_packageName)` **ignores** its 5th arg; `DocumentSymbolTable` re-derives from the AST | `semantics/project-symbols.ts:9` (constructs `new DocumentSymbolTable(uri, ast, schemaCode, namespace)` — no package passed), `semantics/symbol-table.ts:45` |
| Server passes `ast.packageDecl?.name ?? ''` (redundant pass-through, same value) | `lsp/server.ts:747,791`; root-scan pass passes `''` at `:852` |
| Stock cnc gating depends on empty package | `semantics/symbol-table.ts:53` `isStockCnc = schemaCode==='cnc' && !packageName && uri.startsWith('stock://')` |
| Resolver same-package step keys off `context.packageName` + `getByPackage()` | `semantics/resolver.ts`, `semantics/project-symbols.ts:126` |

→ **Path is irrelevant to qnames in modeler.** A declaration-less file resolves in the *empty* package
regardless of its directory.

### 2b. Lint / path-inference layer — *advisory* (drives diagnostics + autofix, NOT qnames)

`inferPackageFromUri(uri, root) → { inferred, isRootFile }` (`semantics/package-inference.ts`) feeds:

| Consumer | Behaviour | Evidence |
|---|---|---|
| `@modeler/lint` `missing-package-declaration` (**info**) | non-root file, no decl → *"File is in package 'X' but has no package declaration"* + **safe autofix** inserting `package <inferred>` | `lint/src/rules/packages.ts` |
| `@modeler/lint` `package-declaration-mismatch` (**error**) | declared ≠ inferred → error + suggestion fix | `lint/src/rules/packages.ts` |
| LSP completion | labels the inferred package `(inferred from path)` | `lsp/completion-property.ts:281-291` |
| `@modeler/migrate` `inferPackage()` | migration CLI writes `package <inferred>` into files | `migrate/src/index.ts:33,293,312` |

→ The lint layer *nudges authors to declare* the path-implied package; it never sets the qname itself.
The VS Code "inferred as 'ucetnictvi'" message is the `missing-package-declaration` **info**.

### 2c. ai-platform runtime — *path-computed* (a third, divergent assignment)

ai-platform (`infra/metadata`) does **not** mirror modeler's declaration-only qname rule:

| Fact | Evidence (ai-platform) |
|---|---|
| Computes package from path and **resolves using it** | `source/Source.kt:248` `computePackageFromPath(...)`; `resolve/ReferenceResolutionPass.kt:37` `packageName = file.computedPackage.ifEmpty { null }` |
| Enforces `ttr/package-declaration-mismatch` as a **load error** | `source/Source.kt:249-257` |
| **Root-level files → empty computedPackage, explicitly exempt** | `resolve/ReferenceResolutionPass.kt:45` |
| Published-resolver adapter path uses *declared* package | `resolve/PublishedResolverAdapter.kt:43` `packageName = f.declaredPackage ?: ""` |
| **Does NOT read `modeler.toml`** (no manifest reading at all) | grep — no hits under `infra/metadata/src/main` |

→ modeler keys off **declared**; ai-platform off **path-computed**. They coincide *only* because the
mismatch rule forces `declared == path`. **Root files are the single place they differ** (both → empty
today) — exactly what this feature changes. This sits on top of the pre-existing declared-vs-computed
model seam (PR #90 / abandoned Phase D).

## 3. Solution shape (TS / modeler)

A single source of truth for the effective qname package, computed once at ingestion and threaded
everywhere a qname is built.

```
                       modeler.toml  [project] package = "df"
                            ▼
   parseManifest ─► ProjectManifest.project.package?  ─► resolveManifest ─► ResolvedManifest.rootPackage: string ("")
                            │
   ┌────────────────────────┴──────────────────────────────────────────────┐
   │   effectivePackageName(ast, uri, projectRoot, rootPackage)  ← NEW       │
   │     1. uri startsWith "stock://" → ""        (never tag stock files)    │
   │     2. ast.packageDecl present   → decl.name (D2: absolute override)    │
   │     3. inferPackageFromUri().isRootFile → rootPackage   (D1)            │
   │     4. otherwise                 → ""        (sub-dir/no-decl: unchanged)│
   └────────────────────────┬──────────────────────────────────────────────┘
        ┌───────────────────┼───────────────────┬──────────────────┬───────────────┐
        ▼                   ▼                   ▼                  ▼               ▼
 ProjectSymbolTable   model-graph /        completion-         code-lens /     @modeler/lint
 .upsertDocument      graph-methods        reference           doc-symbol      package rules
 (activate 5th param) qname builders       context.packageName display         (rootPackage-aware)
```

The helper is the **only** place the fallback logic lives after this change. Every existing
`ast.packageDecl?.name ?? ''` qname site is replaced by a call that passes URI + projectRoot +
`manifest.rootPackage`. When `rootPackage === ""` it is provably a no-op.

## 4. Effective-package algorithm

```
effectivePackageName(ast, uri, projectRoot, rootPackage):
    if uri.startsWith("stock://"):  return ""                  # E1 — keep stock cnc nameless
    if ast.packageDecl != null:     return ast.packageDecl.name # D2 — absolute override
    if inferPackageFromUri(uri, projectRoot).isRootFile:
                                    return rootPackage          # D1 — name the root package
    return ""                                                  # sub-dir, no decl — unchanged
```

Properties: behaviour-preserving when `rootPackage===""`; preserving for any file that declares a
package; only changes root-level declaration-less files; stock cnc untouched (E1 keeps `isStockCnc`).

## 5. Consistency invariant (within modeler)

The symbol table (semantics), the model-graph builders (`lsp/model-graph.ts`, `lsp/graph-methods.ts`)
and the completion/reference layer each build qnames independently. **All three MUST derive the
package from the same `effectivePackageName()` call.** Otherwise the symbol table registers
`df.er.entity.X` while the graph emits `er.entity.X`, breaking node↔symbol matching, resolution and
layout keys. `ResolvedManifest` is already in the model-graph plumbing (`lsp/model-graph.ts:421`);
thread `manifest.rootPackage` + URI + projectRoot into the remaining builders. This is the main work
and the primary review surface.

## 6. Cross-repo conformance (chosen direction — option **b**)

The user's decision: **make ai-platform aware of the project file and root files** so editor and
runtime agree. The root-package name must therefore be honoured by the runtime, not just the editor.

```
   model-ttr/modeler.toml  ([project] package = "df")
        │
        ├── modeler (TS)        : effectivePackageName() in @modeler/semantics  (§3)
        │
        ├── modeler (Kotlin twin): Manifest.kt + effectivePackageName() in ttr-semantics,
        │                          kept byte-conformant via the conformance harness
        │
        └── ai-platform (infra/metadata):
              • loader reads <modelDir>/modeler.toml → rootPackage
              • root-level files (empty computedPackage) adopt rootPackage instead of ""
              • applied on BOTH resolution paths (computedPackage + PublishedResolverAdapter)
```

The model root for ai-platform is the loaded git subdir (`METADATA_GIT_SUBDIR=model-ttr`), which is
exactly where `modeler.toml` lives — so the loader reads `<subdir>/modeler.toml`.

## 7. Component impact

| Repo / package / file | Change |
|---|---|
| modeler `semantics/manifest.ts` | `project.package` in `ProjectManifest`; `rootPackage` in `ResolvedManifest`; validate value. |
| modeler `semantics/effective-package.ts` (new) | `effectivePackageName()`; export from `index.ts`. |
| modeler `semantics/project-symbols.ts` + `symbol-table.ts` | Activate the dormant package param — use the **passed** name, not the AST. |
| modeler `lsp/server.ts` (`:747,791,852`) | Compute effective name; pass it into `upsertDocument` + resolution context. |
| modeler `lsp/model-graph.ts`, `graph-methods.ts`, `completion-reference.ts`, `code-lens.ts`, `document-symbol.ts` | Route qname builders through the effective name. |
| modeler `lint/src/rules/packages.ts` | `missing-package-declaration` / `package-declaration-mismatch` become rootPackage-aware for root files (don't false-nudge a configured root package). |
| modeler `lsp/completion-property.ts` | Offer `manifest.rootPackage` in `package`/`import` completion. |
| modeler Kotlin `ttr-semantics` | `Manifest.kt` + `effectivePackageName` (mirror TS); conformance fixtures; publish. |
| ai-platform `infra/metadata` (`source/Source.kt`, `resolve/ReferenceResolutionPass.kt`, `resolve/PublishedResolverAdapter.kt`) | Read `modeler.toml`; apply rootPackage to root files on both resolution paths. |
| modeler `samples/` + `docs/` | A sample with `[project] package` + a root file; note in architecture §5. |

## 8. Out of scope (explicit)

- **Sub-directory path inference into qnames (modeler).** modeler keeps qnames declaration-only; a
  declaration-less file in `ucetnictvi/` stays in the empty package (the lint still nudges). Not
  changed here. (ai-platform *does* compute sub-dir packages from path — that asymmetry is pre-existing
  and out of scope; this feature only aligns the **root** case.)
- **Base-prefix-all-packages semantics.** Rejected in favour of D1 (root-only).
- **Resolving the declared-vs-computed model divergence** between modeler and ai-platform generally.
  Tracked separately (PR #90 lineage); this feature only aligns root-file naming.

## 9. Open questions / risks

- **OQ1 — declared-vs-computed divergence (precondition, not blocker).** The feature is conformant for
  root files iff both impls agree on the *non-root* model, which they do only via the enforced
  `declared == path` rule. Confirm that invariant still holds in the target ai-platform resolution path
  (published adapter vs `ReferenceResolutionPass`) before wiring rootPackage, so we extend the
  *go-forward* path. (Resolved in Phase 6 discovery.)
- **OQ2 — where JVM-side manifest parsing lives.** Either (a) `ttr-semantics` gains a TOML dep +
  `Manifest.kt` (maximises conformance — the value-validation + defaults are identical to TS), or (b)
  ai-platform parses `modeler.toml` itself and passes `rootPackage` into the semantics API
  (keeps ttr-semantics dependency-light). Proposed: **(a)** for conformance, with a thin loader call in
  ai-platform. Decide in Phase 5.
- **OQ3 — advisory diagnostic** when a root file declares a package ≠ configured rootPackage. D2 says
  the declaration wins (no error); an optional info hint is deferred to Phase 4, off by default.
- **OQ4 — manifest value validation severity.** Invalid `package` value → non-fatal: surface a manifest
  diagnostic and fall back to `""`. Consistent with "all keys optional / defaults on missing".

## 10. Repo-hygiene note (incidental, found during this analysis)

`packages/lsp/src/server.ts` contains a literal **NUL byte** (~offset 14418), so `grep`/`rg` treat it
as binary and silently skip text matches (use `rg -a`). Not caused by this feature, but worth a
cleanup commit so search/tooling behaves on the LSP's largest file.
