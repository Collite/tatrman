# Tatrman Modeler v1.1 — Implementation Plan

**Status:** Plan v1, 2026-05-18. Covers the v1.1 release: packages + imports + `.ttrg` graph files (language change), productivity-tier LSP, polish-tier LSP. Design rationale in [`docs/v1-1/design/v1.1-packages-and-graphs.md`](../design/v1.1-packages-and-graphs.md). Grammar diff for ai-platform in [`docs/v1-1/design/grammar-v1-1-changes.md`](../design/grammar-v1-1-changes.md).

## Plan shape

v1.1 is organised into nine sub-phases (A–I). The first seven (A–G) deliver the language change and the Designer rework; the last two (H, I) deliver productivity- and polish-tier LSP features that depend on the package model being in place. A–G is roughly 4–6 weeks; H+I add another 3–5 weeks; total wall-clock 8–12 weeks with the parallel tracks called out below.

| Sub-phase | Goal                                                                       | Time      | Dependencies                |
| --------- | -------------------------------------------------------------------------- | --------- | --------------------------- |
| 1.1.A     | Grammar additions (`package`, `import`, `graph` keywords + rules)          | 3–5 days  | v1 (Phase 5) shipped        |
| 1.1.B     | Semantics: package-aware symbol table, new resolver chain, diagnostic codes | 5–7 days  | 1.1.A                       |
| 1.1.C     | `.ttrg` end-to-end: parsing, validation, LSP custom methods                | 5–7 days  | 1.1.B                       |
| 1.1.D     | VS Code: `.ttrg` language registration, TextMate updates, `.ttrl` removal  | 2–3 days  | 1.1.A (parallel with B/C)   |
| 1.1.E     | Designer rework: graph picker, creation wizard, add/remove affordances     | 7–10 days | 1.1.C                       |
| 1.1.F     | Migration CLI (`modeler migrate-to-packages`)                              | 3–5 days  | 1.1.B (parallel with C/D/E) |
| 1.1.G     | Migrate `samples/`, write coordination doc, update `architecture.md`       | 2–3 days  | 1.1.F                       |
| 1.1.H     | Productivity-tier LSP (completion incl. package/import awareness)          | 7–10 days | 1.1.B (parallel with C–G)   |
| 1.1.I     | Polish-tier LSP (rename, format, code actions, code lens, semantic tokens) | 7–10 days | 1.1.H                       |

**Critical path:** A → B → C → E (≈ 20–29 days, 4–6 weeks).
**Parallel tracks once B lands:** F → G; H → I; D (independent after A).

**Estimated wall-clock: 8–12 weeks** for one full-time developer plus Bora reviewing.

## Sub-phase 1.1.A — Grammar additions (3–5 days)

**Goal**: extend `TTR.g4` with the three new top-level constructs (`package`, `import`, `graph`) without breaking any existing samples.

**Deliverables**:
- New lexer tokens: `PACKAGE`, `IMPORT`, `GRAPH`, `STAR`
- New parser rules: `packageDecl`, `importDecl`, `graphBlock`, `graphProperty`
- Updated `document` rule: `packageDecl? importDecl* (schemaDirective | graphBlock)? definition* EOF`
- Updated `idPart` rule: include the three new keywords so they remain usable as identifier components in cross-references (consistent with how schema-code and kind keywords are handled today)
- Regenerated TS parser via `pnpm --filter @modeler/parser run prebuild`
- Updated TextMate grammar via `node scripts/generate-tm-grammar.ts`
- Grammar version bump to `2.0.0` in `packages/grammar/package.json` and the `.g4` header
- All existing v1 samples still parse cleanly (no `package` declaration = "default package", which the grammar accepts)

**Acceptance**: every file in `samples/v1-metadata/` and `samples/v1-mini/` parses without errors against the new grammar; `pnpm -r build && pnpm -r test` green; new fixtures under `samples/v1.1-packages/` (created in 1.1.G) parse cleanly.

**Notes**: this sub-phase is grammar-only — no semantics, no diagnostics, no resolver changes. Those land in 1.1.B. The grammar change is itself the contract with ai-platform; once it merges, the coordination doc in 1.1.G is what they consume.

## Sub-phase 1.1.B — Semantics: package-aware symbol table + resolver (5–7 days)

**Goal**: make the semantics layer understand packages and imports.

**Deliverables**:
- AST extension: `Document` gains `packageDecl?` and `imports[]`; new `PackageDecl` and `ImportDecl` node types
- `DocumentSymbolTable` gains `packageName` field
- `ProjectSymbolTable` keys every symbol by full package-prefixed qname (e.g. `billing.invoicing.er.entity.artikl`)
- Resolver implements the six-step resolution chain per design §4.2: lexical → same-package → named-import → wildcard-import → auto-import → fully-qualified
- `ResolutionResult.tried[]` entries gain a `reason` field (`"not-imported"`, `"wildcard-non-recursive"`, `"unknown-symbol"`, etc.)
- New `PackageGraph` module: builds the directed dep graph between packages from imports; exposes `getDependents(pkg)`, `getDependencies(pkg)`, `findCycles()`
- New diagnostic codes wired through the validator:
  - `ttr/unimported-reference` (Error)
  - `ttr/unused-import` (Warning)
  - `ttr/wildcard-with-no-matches` (Warning)
  - `ttr/duplicate-import` (Warning)
  - `ttr/circular-package-dependency` (Warning)
  - `ttr/package-declaration-mismatch` (Error)
  - `ttr/missing-package-declaration` (Info)
  - `ttr/ambiguous-reference` (Error)
- Resolve open question §13.10 (stock-vocab qname under packages) — implement chosen option; document the choice in `docs/v1/design/diagnostics.md` and `docs/v1/design/architecture.md` §4.3
- Existing v1 features (go-to-def, find-refs, hover) keep working with the new qname shape — verify via the integration-test suite

**Acceptance**: existing v1 integration tests pass with v1.1-shaped qnames; new tests cover each new diagnostic code (one fixture per code under `samples/broken/v1.1/`); the cnc stock-vocab qname choice is exercised by a test asserting the chosen form.

**Notes**: this is the largest sub-phase and the most likely to surface unforeseen issues with the resolver chain. Allocate slack.

## Sub-phase 1.1.C — `.ttrg` end-to-end (5–7 days)

**Goal**: `.ttrg` files load, validate, and serve the Designer.

**Deliverables**:
- Parser dispatch: a `.ttrg` file with no `graph` block, or a `.ttr` file with one, produces `ttr/wrong-file-kind` (Error)
- `Graph` AST node + validator:
  - `schema` required, must be one of the declared schema kinds
  - `objects` required, non-empty; every qname resolves; unresolved → `ttr/graph-object-not-found` (Warning)
  - `layout` optional; node-keys correspond to entries in `objects` (extras → `ttr/graph-layout-stale-node`, Warning)
- New custom LSP methods (all under `modeler/`):
  - `listGraphs(projectRoot)` → `{ graphs: GraphMetadata[] }`
  - `getGraph(uri)` → `{ schema, objects, edges, layout, missingObjects }`
  - `addObjectToGraph(uri, qname)` → `WorkspaceEdit`
  - `removeObjectFromGraph(uri, qname)` → `WorkspaceEdit`
  - `createGraph({ uri, name, schema, packages, objects })` → `WorkspaceEdit` (creates a new file)
  - `getPackageGraph(projectRoot)` → `{ packages, dependencies }`
- Updated `modeler/getLayout` / `modeler/setLayout` signatures: gain `graphUri`; project-wide layout removed
- Updated `modeler/getProjectInfo`: gains `packages: PackageInfo[]` with file counts and dependents
- Updated `modeler/getModelGraph`: takes a `graphUri` parameter; renders that graph's slice
- Edge-inclusion semantics per design §6.4 (both endpoints + the edge itself must be in `objects`)
- LSP integration tests under `tests/integration/` covering each new method, plus migration of existing layout-persistence tests to per-graph layout

**Acceptance**: integration test suite covers (a) opening a hand-written `.ttrg` and verifying the returned graph, (b) `addObjectToGraph` produces a `WorkspaceEdit` that, when applied, makes the new object appear in a subsequent `getGraph` call, (c) `createGraph` produces a syntactically valid `.ttrg` file that round-trips, (d) `getPackageGraph` returns expected nodes/edges for a fixture project with mutual imports.

## Sub-phase 1.1.D — VS Code updates (2–3 days)

**Goal**: VS Code recognises `.ttrg`, removes `.ttrl` support.

**Deliverables**:
- Register `.ttrg` as a separate language id (`ttrg`) with file-extension binding
- Reuse TextMate scopes from `ttr` where possible; add `.ttrg`-specific patterns for `graph`, `objects`, `layout` blocks
- File icon for `.ttrg` (variant of the `.ttr` icon)
- Remove `.ttrl` language registration; remove the icon and grammar file
- VS Code smoke test updated to open a sample `.ttrg` from the migrated `samples/`

**Acceptance**: smoke test asserts syntax highlighting on a `.ttrg` file; opening a `.ttrl` (if one exists in the user's project) no longer registers a language but doesn't error.

**Notes**: this can run in parallel with 1.1.B and 1.1.C once 1.1.A's grammar lands. Only depends on the grammar, not on the resolver or LSP method changes.

## Sub-phase 1.1.E — Designer rework (7–10 days)

**Goal**: Designer entry flow becomes graph-centric. Three entry modes per design §8.5.

**Deliverables**:

*Entry modes:*
- **Open existing graph**: file picker → `.ttrg` selection → render
- **Browse project graphs**: open folder → `modeler/listGraphs` → graph picker panel (search/filter on name/tags/schema) → select → render
- **Create new graph**: multi-step wizard
  1. Pick packages (checkboxes against `modeler/getProjectInfo` output)
  2. Show dependency graph (Cytoscape mini-canvas of `modeler/getPackageGraph` output, with the selected packages highlighted and their transitive deps suggested)
  3. Pick objects from selected packages (filtered list view)
  4. Pick schema kind (radio: `er` / `db` / future)
  5. Name + filename + save location → `modeler/createGraph`

*In-canvas affordances:*
- "Add object" button on the toolbar → picker scoped to imported packages → `modeler/addObjectToGraph`
- Context menu on any node: "Remove from graph" → `modeler/removeObjectFromGraph`
- "Extend imports" affordance: when the user picks an object outside current import scope, offer to add the import automatically (toggle in the picker)
- "Missing objects" badge: surfaced when `getGraph` returns `missingObjects.length > 0`; clicking lists them with "Remove" buttons

*Reducer / state changes:*
- `state.projectUri` joined by `state.currentGraphUri`
- Schema toggle UI from Phase 3 either (a) removed in favour of graph switching, or (b) re-purposed as "Switch to graph rendering the other schema for this scope" — decide during sub-phase
- Per-graph `displayMode` persistence (was per-schema in v1)
- Layout persistence rewires from `.ttrl`-shaped writes to per-`.ttrg` `layout` block; uses updated `modeler/setLayout(graphUri, ...)` signature

*Tests:*
- RTL tests for the three entry modes
- Reducer tests for graph-switch, add-object, remove-object actions
- Playwright test for the create-graph wizard end-to-end (lands as part of 1.1.G's sample migration)

**Acceptance**: open a migrated sample's `_all_er.ttrg`, see it render; create a new graph via the wizard, save it, re-open it, see same content; add an object via the toolbar, see the `.ttrg` file's `objects` list grow; remove via context menu, see it shrink.

**Notes**: this is the second-largest sub-phase. The wizard UX is the most likely thing to require iteration; ship a workable version and refine in v1.1.x patches if needed.

## Sub-phase 1.1.F — Migration CLI (3–5 days)

**Goal**: `modeler migrate-to-packages` ships, works on real projects.

**Deliverables**:
- New package `@modeler/migrate` under `packages/migrate/`
- CLI entry point: `pnpm exec modeler migrate-to-packages <project-root>`
- Walks every `.ttr` under the root, infers package name from path relative to `modeler.toml`
- Inserts `package <name>` at file top (idempotent; skips already-declared files)
- Scans every cross-reference; inserts appropriate `import` statements for cross-package targets
- Converts `<root>/.modeler/layout.ttrl` to `<root>/graphs/_all_db.ttrg` and `<root>/graphs/_all_er.ttrg` (one per schema kind found in the project), preserving layout positions
- `--dry-run` mode prints what would change without writing
- `--commit-ttrl-removal` flag deletes `.ttrl` after successful migration (default: leave it)
- Reports: files touched, packages created, imports inserted, ambiguous references requiring manual fix
- Unit tests on a synthetic small project; integration test on `samples/v1-mini/` (its migrated output becomes `samples/v1.1-mini/`)

**Acceptance**: running the CLI on a copy of `samples/v1-mini/` produces output that re-parses cleanly under v1.1, all cross-references resolve, and the produced `_all_er.ttrg` opens in the Designer with the original layout preserved.

**Notes**: can run in parallel with 1.1.C/D/E once 1.1.B lands. Test fixtures built here feed into 1.1.G's sample migration.

## Sub-phase 1.1.G — Migrate samples + docs (2–3 days)

**Goal**: every sample is on the new model; ai-platform's coordination doc is finalised; the architecture doc is updated.

**Deliverables**:
- Run `modeler migrate-to-packages` on `samples/v1-mini/` and `samples/v1-metadata/`; commit the results as `samples/v1.1-mini/` and `samples/v1.1-metadata/` (keeping the originals as v1 fixtures for the migration tests)
- Hand-author a few extra `.ttrg` files under each migrated sample to exercise non-trivial graphs (subdomain views, focused entity views)
- Update `samples/builtin/cnc-stock-roles.ttr` per the qname-shape decision from 1.1.B (open question §13.10)
- Finalise `docs/v1-1/design/grammar-v1-1-changes.md` based on what actually shipped in 1.1.A
- Update `docs/v1/design/architecture.md`:
  - §4.3 stock vocabulary: reference new package shape
  - §4.4 edit synthesizer: note that rename + add/remove-object are now load-bearing (no longer a v1 placeholder)
  - §5 project model: reference packages
  - §6 layout sidecar (`.ttrl`): replace with pointer to the `.ttrg` design doc
  - §10 open questions: mark resolved ones
  - §11 Designer ↔ LSP: update to reflect graph-centric flow
- Write `docs/v1-1/plan/progress-phase-v1.1.md` with task-completion log
- Update `CLAUDE.md` references where needed

**Acceptance**: `pnpm -r build && pnpm -r test && pnpm -r typecheck && pnpm -r lint` green; both migrated sample bundles open in the Designer; ai-platform's parser maintainer has reviewed `grammar-v1-1-changes.md` (sign-off can be async).

## Sub-phase 1.1.H — Productivity-tier LSP (7–10 days)

**Goal**: ship the completion + symbol features that depend on the package model being in place.

**Deliverables**:
- `textDocument/completion` for:
  - Property names within a `def <kind> { ... }` block (based on per-kind property maps from semantics)
  - Schema kinds in `schema X` directive
  - Def kinds after `def`
  - **Reference completion**: when typing inside a value position that accepts a reference, suggest visible symbols (imports + same-package + auto-imports)
  - **Auto-import completion**: when the user picks a reference from an unimported package, suggest adding the `import` automatically (code action attached to the completion item)
  - **Package-name completion**: in `package` and `import` statements
- `textDocument/documentSymbol` — outline view per file (already partially present; complete the hierarchical structure: package → schema → defs → properties)
- `workspace/symbol` — already shipped in v1.2.J; verify it returns full package-prefixed qnames
- Settings:
  - `modeler.completion.autoImport`: boolean (default `true`) — controls auto-import on reference completion
  - `modeler.completion.preselectFullyQualified`: boolean (default `false`) — preselect FQN over bare name when both match

**Acceptance**: in VS Code, typing `er.` inside a `from:` value brings up reference candidates; selecting one from another package inserts both the reference and the appropriate `import` automatically; typing `def ` inside a `.ttr` brings up kind candidates; typing `package ` brings up directory-based suggestions.

**Notes**: this can start once 1.1.B is done — no need to wait for the Designer rework. Often the most "magical-feeling" feature for users; worth investing in polish.

## Sub-phase 1.1.I — Polish-tier LSP (7–10 days)

**Goal**: rename, format, code actions, code lens, richer semantic tokens.

**Deliverables**:
- `textDocument/rename`:
  - Cross-package rename via the edit synthesizer (now load-bearing per design §12)
  - **Rename propagates into `.ttrg` `objects` lists** — this is the second concrete `.ttrg`/synthesizer interaction (the first being add/remove-object from 1.1.C)
  - Rename-package operation (right-click on a `package` keyword): updates the declaration, updates every `import` referencing it across the project
- `textDocument/formatting`:
  - Pretty-print rules: brace style, comma-vs-newline separator policy, alignment of property keys
  - Settings: `modeler.format.separator` (`comma` / `newline` / `preserve`), `modeler.format.alignKeys` (boolean)
  - Tested against every file in `samples/v1.1-*/`
- `textDocument/codeAction`:
  - Quick-fix for `ttr/unimported-reference` → "Add import for …"
  - Quick-fix for `ttr/unused-import` → "Remove import"
  - Quick-fix for `ttr/missing-package-declaration` → "Add `package <inferred>`"
  - Quick-fix for `ttr/package-declaration-mismatch` → "Update declaration to match directory"
  - Refactor: "Extract def to new file in package …"
- `textDocument/codeLens`:
  - On a `def <kind> name { ... }` header: "N references" (clickable → find-refs)
  - On a `package` declaration: "N files in package" (clickable → file listing)
- `textDocument/semanticTokens/full`:
  - Add token types for `packageName`, `importedSymbol`, `localSymbol`, `unimportedReference`
  - Update TextMate generator's "fallback" patterns to defer richer cases to semantic tokens

**Acceptance**: rename a `def entity` in one file, see updates in every referencing file plus every `.ttrg` that listed it; format any file, see consistent output; trigger each quick-fix from VS Code's lightbulb UI and see the expected change.

**Notes**: rename is the most subtle feature — the edit synthesizer's CST-aware text-patching has to handle every reference site cleanly. Allocate slack for edge cases (refs inside triple-strings, refs inside function calls, refs adjacent to comments).

## Parallelism map

```
1.1.A ──┬── 1.1.B ──┬── 1.1.C ── 1.1.E
        │           │
        │           ├── 1.1.F ── 1.1.G
        │           │
        │           └── 1.1.H ── 1.1.I
        │
        └── 1.1.D
```

Recommended sequencing for one developer + Bora reviewing:
1. Land A in week 1
2. Land B in weeks 1–2 (single-thread for resolver coherence)
3. From week 3 onwards: C+D in parallel; F starts when bandwidth allows; H starts when C is integration-test-green
4. E starts once C lands (week 4 or 5)
5. G is the integration / cleanup week
6. H and I close out

## Acceptance summary

v1.1 ships when:

- `samples/v1.1-metadata/` opens in VS Code, every `.ttr` parses, every cross-reference resolves, every diagnostic in the new taxonomy fires correctly on fixtures
- The Designer can open `.ttrg` files from the migrated samples, create new graphs through the wizard, and add/remove objects via the in-canvas affordances
- Rename a def in VS Code propagates through every reference site *and* through every `.ttrg` that listed the renamed object
- Format-document produces deterministic output on every file in `samples/v1.1-*/`
- The migration CLI converts a fresh copy of `samples/v1-metadata/` to a working v1.1 project without manual intervention
- `docs/v1-1/design/grammar-v1-1-changes.md` is sign-off-ready for ai-platform's parser maintainer
- `docs/v1/design/architecture.md` reflects v1.1's shape
- All four CI checks green (build, test, typecheck, lint)
- VS Code Marketplace + JetBrains Marketplace bumps to v1.1.0

## Risks

- **Resolver-chain edge cases.** The new six-step chain has more failure modes than v1's two-step (lexical → project-symbol). Mitigation: fixture-driven test suite, one fixture per chain step, plus combinatorial fixtures for ordering edge cases (same-package shadowing a wildcard import, etc.).
- **`.ttrg` file's interaction with rename.** Renaming a def should rewrite every `.ttrg` that lists its qname. The synthesizer's CST-aware patching has to be correct here on the first try — broken rename would silently corrupt user diagrams. Mitigation: extensive rename fixtures, including `.ttrg` files in the test set; round-trip every rename through Designer-open + re-render to assert the diagram still loads.
- **ai-platform parser drift.** Per B12, modeler ships v1.1 independently of ai-platform's grammar bump. There's a coordination window during which ai-platform consumes grammar v1 but modeler emits files containing v2-only syntax. Mitigation: the `grammar-v1-1-changes.md` doc is the contract; Bora tracks ai-platform's adoption; once they're on v2.0, the sync CI returns to "block on drift".
- **Designer wizard UX.** Multi-step wizards are easy to get wrong. Mitigation: ship workable, iterate in v1.1.x; don't gate v1.1 on perfection here. The "Open existing graph" entry mode is the always-works fallback if the wizard turns out to need rework.
- **Migration CLI on real-world projects.** The CLI is tested on `samples/`, but ai-platform's actual projects will have edge cases (deep paths, mixed schema kinds, custom layout structures). Mitigation: `--dry-run` mode shipped from day one; ai-platform runs against `--dry-run` first and reports issues; CLI iterates before they commit migration.

## v1.2+ outlook (still post-v1.1)

The order locked in during the v1.1 brainstorm:

- **v1.2** — graphical editor (edit synthesizer becomes fully load-bearing for Designer-side mutations)
- **v1.3** — `cnc` schema in Designer + Chen / UML / other E-R display variants
- **v1.4** — natural-language pane wired to an LLM
- **v1.5+** — live-database integration via ai-platform; team collaboration

v1.1's contribution to v1.2: the synthesizer is partially exercised in v1.1 (rename + add/remove-object), so v1.2 inherits a working, tested synthesizer rather than a placeholder. The `.ttrg` scope mechanic also gives v1.2's editor a precise "edit *this*" boundary.
