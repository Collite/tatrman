# Phase 1.1 — Progress

**Started:** 2026-05-18
**Branch:** `feat/v1.1-packages-and-graphs` (merged)
**Status:** Completed (Section G)

## Sub-phases

| Sub-phase | Goal | Status |
|---|---|---|
| 1.1.A | Grammar additions (`package`, `import`, `graph`) | Completed |
| 1.1.B | Package-aware symbol table, resolver, diagnostics | Completed |
| 1.1.C | `.ttrg` parsing, validation, LSP methods | Completed |
| 1.1.D | VS Code: `.ttrg` language registration, `.ttrl` removal | Completed |
| 1.1.E | Designer rework: graph picker, wizard, add/remove affordances | Completed |
| 1.1.F | Migration CLI (`modeler migrate-to-packages`) | Completed |
| 1.1.G | Migrate samples + docs cleanup | **Completed** (this document) |
| 1.1.H | Productivity-tier LSP (completion, package/import awareness) | Pending |
| 1.1.I | Polish-tier LSP (rename, format, code actions) | Pending |

---

## 1.1.A — Grammar additions

**Acceptance:** v1 samples parse cleanly; new fixtures parse cleanly.

- [x] New lexer tokens: `PACKAGE`, `IMPORT`, `GRAPH`, `STAR`
- [x] New parser rules: `packageDecl`, `importDecl`, `graphBlock`, `graphProperty`
- [x] Updated `document` rule: `packageDecl? importDecl* (schemaDirective | graphBlock)? definition* EOF`
- [x] Updated `idPart` rule: include the three new keywords
- [x] Regenerated TS parser via `pnpm --filter @modeler/parser run prebuild`
- [x] Updated TextMate grammar
- [x] Grammar version bump to `2.0.0`
- [x] All existing v1 samples parse without errors

---

## 1.1.B — Semantics: package-aware symbol table + resolver

**Acceptance:** existing v1 integration tests pass; new diagnostic fixtures cover each code.

- [x] AST extension: `Document` gains `packageDecl?` and `imports[]`
- [x] `DocumentSymbolTable` gains `packageName` field
- [x] `ProjectSymbolTable` keys by full package-prefixed qname
- [x] Six-step resolution chain implemented
- [x] New diagnostic codes: `ttr/unimported-reference`, `ttr/ambiguous-reference`, `ttr/package-declaration-mismatch`, `ttr/wrong-file-kind`, `ttr/unused-import`, `ttr/wildcard-with-no-matches`, `ttr/duplicate-import`, `ttr/circular-package-dependency`, `ttr/missing-package-declaration`, `ttr/graph-object-not-found`, `ttr/graph-layout-stale-node`
- [x] cnc stock-vocab qname: **option (b)** — `cnc.role.*` with auto-import of `cnc.*`; internal qname `cnc.cnc.role.*`
- [x] All integration tests pass with v1.1-shaped qnames

---

## 1.1.C — `.ttrg` end-to-end

**Acceptance:** integration tests cover all new LSP methods and graph validation.

- [x] Parser dispatch: `.ttrg` without graph block → `ttr/wrong-file-kind`
- [x] `Graph` AST node + validator: `schema` required; `objects` required; `layout.nodes` qnames must match `objects`
- [x] `modeler/listGraphs(projectRoot)` → `{ graphs: GraphMetadata[] }`
- [x] `modeler/getGraph(uri)` → `{ schema, objects, edges, layout, missingObjects }`
- [x] `modeler/addObjectToGraph(uri, qname)` → `WorkspaceEdit`
- [x] `modeler/removeObjectFromGraph(uri, qname)` → `WorkspaceEdit`
- [x] `modeler/createGraph({ uri, name, schema, packages, objects })` → `WorkspaceEdit`
- [x] `modeler/getPackageGraph(projectRoot)` → `{ packages, dependencies }`
- [x] `modeler/getLayout` / `modeler/setLayout` gain `graphUri` parameter
- [x] `modeler/getProjectInfo` gains `packages: PackageInfo[]`
- [x] Edge-inclusion: both endpoints + the edge itself must be in `objects`

---

## 1.1.D — VS Code updates

- [x] Register `.ttrg` as language id `ttrg` with file-extension binding
- [x] `.ttrg`-specific TextMate scopes for `graph`, `objects`, `layout` blocks
- [x] File icon for `.ttrg`
- [x] Remove `.ttrl` language registration and icon

---

## 1.1.E — Designer rework

- [x] Three entry modes: open existing graph, browse project graphs, create new graph wizard
- [x] "Add object" button → `modeler/addObjectToGraph`
- [x] Context menu "Remove from graph" → `modeler/removeObjectFromGraph`
- [x] "Missing objects" badge when `getGraph` returns `missingObjects.length > 0`
- [x] `state.currentGraphUri` replaces project-wide schema toggle
- [x] Per-graph layout persistence via `modeler/setLayout(graphUri, ...)`

---

## 1.1.F — Migration CLI

- [x] New package `@modeler/migrate` under `packages/migrate/`
- [x] CLI: `pnpm exec modeler-migrate <project-root>`
- [x] `--dry-run` mode
- [x] `--commit-ttrl-removal` flag
- [x] `--wildcard-threshold N` flag
- [x] `insertPackageDecl` — adds `package <name>` at top (idempotent; skips for default/empty package)
- [x] `scanCrossReferences` — wildcard vs named import decision per threshold
- [x] `convertTtrlToTtrg` — produces one `.ttrg` per schema with layout preserved
- [x] Ambiguous-reference exit code 1
- [x] Unit tests: `infer-package.test.ts`, `insert-package.test.ts`, `insert-imports.test.ts`, `ttrl-to-ttrg.test.ts`, `scan-cross-references.test.ts`
- [x] Integration test on copy of `samples/v1-mini/`

**Key bug fixed during implementation:** `scanCrossReferences` had two bugs:
1. `byPackage` skipped entries where `entry.packageName === fromPackage` — wrong because it filtered *before* grouping, removing all entries when `fromPackage === ''`
2. `fromPackage` was passed as `''` but every file in a single-package project also has `packageName ''`, so no cross-package refs could ever be found

**Fix:** remove the `if (entry.packageName === fromPackage) continue` filter from `byPackage` construction; add `if (pkg === fromPackage) continue` after match resolution instead.

---

## 1.1.G — Migrate samples + docs

- [x] **G.1** `samples/v1-mini/` → `samples/v1.1-mini/` (hand-authored)
  - Package structure: `billing.invoicing` (entities + relations) and `billing.products` (produkt, podprodukt); one file per def, all directly in the package leaf directory so each declared `package` matches its directory (verified: 0 resolver diagnostics)
  - Cross-package relations (`artikl_produkt`, `artikl_podprodukt`, …) live in their own files under `billing.invoicing/`, referencing `billing.products` defs fully-qualified; `er.ttr` carries the `import billing.products.*` decls
  - `graphs/all_er.ttrg` and `graphs/all_db.ttrg` created; additional focused `graphs/artikl_overview.ttrg`
  - Single `modeler.toml` at the sample root (project-root marker)

- [x] **G.2** `samples/v1-metadata/` → `samples/v1.1-metadata/` (hand-authored)
  - All files in `package billing`
  - `map.ttr` includes `import cnc.*` for cnc.role references
  - `graphs/all_er.ttrg` (52 entities) and `graphs/all_db.ttrg` (44 tables)

- [x] **G.3** Additional `.ttrg` fixtures hand-authored under migrated samples
  - `samples/v1.1-mini/graphs/artikl_overview.ttrg`
  - `samples/v1.1-mini/graphs/all_er.ttrg`, `all_db.ttrg`

- [x] **G.4** `samples/builtin/cnc-stock-roles.ttr` — `package cnc` prepended with TODO comment about v2.x revisit

- [x] **G.5** `docs/v1-1/design/grammar-v1-1-changes.md` §8 open items resolved:
  - §4.3 stock-vocab qname → option (b): `cnc.role.*` with auto-import
  - §3.2 grammar vs semantic enforcement → semantic-level; `ttr/wrong-file-kind` emitted
  - §4.5 diagnostic emission → guidance given in table

- [x] **G.6** `docs/v1/design/architecture.md` updated:
  - §4.3 stock vocabulary: updated to reflect auto-imported `cnc.*` and doubled `cnc.cnc.role.*` internal form
  - §4.4 edit synthesizer: now load-bearing (no longer a placeholder); rename + graph mutations documented
  - §5 project model: package model added
  - §6 layout sidecar: replaced with pointer to v1.1 and note that `.ttrl` is removed
  - §10 open questions: items 6, 7, 8 resolved
  - §11 Designer ↔ LSP: graph-centric flow added; `.ttrg`-scoped layout documented

- [x] **G.7** `docs/v1-1/plan/progress-phase-v1.1.md` written (this document)

- [x] **G.8** `CLAUDE.md` references reviewed (layout sidecar note already present)

---

## Test totals

```
pnpm --filter @modeler/parser test    → 109 passed
pnpm --filter @modeler/semantics test → 107 passed
pnpm --filter @modeler/edit test      → 42 passed
pnpm --filter @modeler/lsp test       → 53 passed
pnpm --filter @modeler/migrate test   → 23 passed
pnpm --filter @modeler/vscode-ext test → 24 passed
pnpm --filter @modeler/designer test  → 128 passed
pnpm --filter @modeler/integration-tests test → 79 passed | 1 skipped
pnpm -r typecheck                    → clean
pnpm -r lint                         → clean
pnpm -r build                        → all green
```

---

## Notes for 1.1.H onwards

- **Rename interaction with `.ttrg`:** renaming a def rewrites every `.ttrg` that lists its qname. The synthesizer's CST patching must be correct on the first try — broken rename silently corrupts user diagrams. Extensive rename fixtures recommended.
- **Migration CLI on real-world projects:** tested on `samples/` but ai-platform projects may have edge cases (deep paths, mixed schemas). `--dry-run` shipped from day one; iterate before committing.
- **Designer wizard UX:** ship workable version and iterate in v1.1.x patches.