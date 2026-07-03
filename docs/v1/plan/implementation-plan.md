# Tatrman Modeler — Implementation Plan

**Status:** Plan v1, 2026-05-14. Plan covers v1 (Foundation+Core LSP, minimal Designer, IntelliJ stub) plus a forward look at v1.1+ for context.

## Plan shape

The build is organized into six phases, executed mostly sequentially with one parallel track. Phase 0 is the vertical thin slice (decision D6); Phase 1 closes out Foundation tier; Phase 2 adds Core tier; Phase 3 lands the Designer's read-only experience; Phase 4 stands up the IntelliJ plugin; Phase 5 hardens, packages, and ships v1. Phases 1–3 can overlap modestly; phase 4 needs phase 2 done to be useful; phase 5 needs everything else.

| Phase | Goal | Time | Dependencies |
|---|---|---|---|
| 0 | Vertical thin slice end-to-end | 1–2 weeks | None |
| 1 | Foundation tier complete (highlighting, syntax diagnostics, language config, .ttrl support, diagnostic taxonomy, semantic tokens, parser error recovery, ai-platform sync CI, VS Code smoke test) | 1.5–2 weeks | Phase 0 (post review-001 P0 fixes) |
| 2 | Core tier (full AST, project model, symbol table, resolver, validator, go-to-def, find-refs, hover, workspace symbols, semantic tokens, parse-recovery-info, VS Code smoke) | 4–5 weeks | Phase 1 |
| 3 | Designer v1 (read-only, db + er, schema/detail toggles, layout persistence) | 3–4 weeks | Phase 0, Phase 2 (semantics over LSP) |
| 4 | IntelliJ plugin v1 (LSP4IJ wrapper, file-type registration, bundled runtime) | 1–2 weeks | Phase 2 |
| 5 | Hardening, packaging, distribution, docs | 1–2 weeks | Phases 1–4 |

Total wall-clock estimate: **11.5–16.5 weeks** for v1, assuming one full-time developer plus Bora reviewing. Parallelization (e.g. Phase 3 starting once Phase 2's §A–§C are stable) can compress this to 9.5–11.5 weeks.

## Phase 0 — vertical thin slice (1–2 weeks)

**Goal**: end-to-end working pipeline at the lowest possible feature bar. After Phase 0, opening a `.ttr` file in VS Code shows syntax-highlighted text with red squigglies on syntax errors, and opening the Designer shows a minimal read-only graph for one sample file.

**Deliverables**:
- Monorepo scaffold (pnpm workspaces, TypeScript, Vitest, ESLint, Prettier, EditorConfig)
- `@modeler/grammar` package with `TTR.g4` moved into it and a sync script (no ai-platform integration yet — just the local script with documented usage)
- `@modeler/parser` with antlr4ng-generated parser, minimal `parseString` API returning errors + a stub AST (no full Definition hierarchy yet — just the schema directive and definition kinds + names)
- `@modeler/semantics` placeholder (empty exports; package exists for downstream imports)
- `@modeler/edit` placeholder
- `@modeler/lsp` minimal: initialize, document sync, syntax-error diagnostics; one custom method `modeler/getModelGraph` returning a stub graph from the parsed file
- `@modeler/vscode-ext` minimal: LSP client wiring, language registration, generated TextMate grammar
- `@modeler/designer` minimal: forked from Ontology Playground with the cuts described in §4.7 of the architecture; LSP-in-Web-Worker bootstrap; renders one schema (just entity nodes, no edges, no detail panel content) from the stub graph
- CI passing on a green build

Detailed task breakdown in `tasks-phase-00-thin-slice.md`. After Phase 0, the developer can demo end-to-end: open a `.ttr` file, see highlighting, save with a typo to see a diagnostic, switch to the Designer to see the model nodes rendered.

## Phase 1 — Foundation tier complete (1.5–2 weeks)

**Detailed task list:** [`tasks-phase-01-foundation.md`](tasks-phase-01-foundation.md).

**Goal**: take Phase 0's vertical thin slice from "the wire works" to "every file under `samples/` looks and behaves like a real language file in any LSP host." Phase 1 is still pre-semantics; symbol table, references, and navigation land in Phase 2.

**Deliverables** (sectioned in the task list as A–L):
- **A** Carryover cleanup from review-001 P1/P2: `Definition` discriminated union, `.gitignore` fix, parser tsconfig + ESLint exclusions, JSDoc on `SourceLocation`, `grammar/index.ts` path cleanup, unused-import sweep, parser-test path robustness
- **B** TextMate grammar full coverage: rebuild the generator to walk the grammar's lexer rules and emit a categorized scope set (keywords, kinds, properties, primitives, constants, strings, numbers, comments, definition names, qnames, punctuation); CI guard against stale generated output
- **C** Full language configuration: `wordPattern` honoring Czech/Latin Extended identifiers, `indentationRules`, `onEnterRules` for `def { … }` blocks
- **D** `.ttrl` layout sidecar support: separate language registration, JSON-schema validation, LSP gates parse-as-TTR off for `.ttrl` files
- **E** Diagnostic taxonomy: stable codes (`ttr/parse-error`, `ttr/unknown-property`, `ttr/parse-recovery-info`) with `source: 'modeler'`; documented in `docs/design/diagnostics.md`
- **F** Parser error recovery: tune ANTLR's recovery so common-typo broken inputs still yield useful partial ASTs; emit `parse-recovery-info` at recovery boundaries
- **G** Semantic tokens via LSP: cover the cases TextMate can't disambiguate (dotted ids with keyword fragments, definition names with non-IDENT characters)
- **H** File icons: one for `.ttr`, one for `.ttrl`, bound to the language ids
- **I** ai-platform sync CI integration: cross-repo PR check that fails on grammar drift
- **J** VS Code smoke test (`@vscode/test-electron`): boot a real VS Code instance, open a sample, assert language detection and diagnostic flow
- **K** Broken-sample fixtures (`samples/broken/`): one per defect category, consumed by integration tests
- **L** Documentation: progress doc, diagnostics catalog, architecture-doc updates, vscode-ext README

**Acceptance**: every sample in `samples/v1-metadata/` opens and is highlighted correctly across every category; brackets/comments/indentation behave; each `samples/broken/` defect produces the expected diagnostic with the expected code; `.ttrl` files highlight as JSON with schema validation; cross-repo grammar-sync CI passes; VS Code smoke test green.

**Note on scope expansion vs the original 1-week estimate**: the bump to 1.5–2 weeks absorbs (a) the review-001 P1/P2 carryover (sections A and parts of B/C), (b) parser error recovery (Section F — needed for `parse-recovery-info` to be more than a formality), and (c) the cross-repo sync CI (Section I — deferred from Phase 0). The substantive Foundation-tier work itself is roughly the original 1-week estimate; carryover and infrastructure account for the rest.

## Phase 2 — Core tier (4–5 weeks)

**Detailed task list:** [`tasks-phase-02-core.md`](tasks-phase-02-core.md).

**Goal**: take Phase 1's syntax-aware editor to a semantics-aware editor. After Phase 2, the parser produces a fully-populated AST; the semantics layer builds a project-wide symbol table from `.ttr` files plus stock vocabulary; cross-references resolve; a per-kind validator catches structural problems; the LSP exposes go-to-definition, find-references, hover, workspace symbols, and semantic tokens. Real users can author a TTR project in VS Code and feel the editor understands what they're writing.

**Sub-phases** (sectioned in the task list as A–N):
- **A** AST completion — full `PropertyValue` union (string, triple-string, number, bool, null, id, list, object, function-call), full per-kind property maps for all 17 `Definition` subtypes, inline def lists (`columns:`, `attributes:`, `parameters:`, etc.), `LocalizedString` / `LocalizedStringList` / `ValueLabels` / `SearchBlock` / `DataType` (shorthand + structured), `Reference` extraction. ~11 sub-sections; the largest part of Phase 2.
- **B** Project model + `modeler.toml` — manifest types, TOML parsing, project-root resolution (walk-up to `modeler.toml`; convention default = workspace folder), `modeler/getProjectInfo` LSP method, sample manifest at `samples/v1-metadata/modeler.toml`
- **C** Symbol table — qname structure, `DocumentSymbolTable`, `ProjectSymbolTable` (merge across documents, duplicate detection), stock-vocabulary loader (CNC roles), incremental rebuild on document change
- **D** Reference resolver — dotted refs (schema-qualified vs project-relative), bare-id refs (lexical scope: attribute-within-entity, role-within-stock), `ResolutionResult` shape with `tried[]` chain
- **E** Validator — required-property checks, cross-reference checks (FK from/to, mapping refs, primary-key columns, name-attribute), duplicate-definition checks, empty-block warnings
- **F** Diagnostic-code expansion — new codes for unresolved-reference, duplicate-definition, required-property-missing, primary-key-column-not-found, entity-attribute-not-found, empty-localized-string, empty-search-block; severity per code; `docs/design/diagnostics.md` updated
- **G** Go-to-definition — `textDocument/definition`, `findNodeAtPosition` helper
- **H** Find-references — `ReferenceIndex` reverse index, `textDocument/references`
- **I** Hover — `formatHover` markdown with description / kind / type / source link, localized per `[language].preferred`, `textDocument/hover`
- **J** Workspace symbols — fuzzy search over project symbol table via `fuzzysort`, `workspace/symbol`
- **K** Semantic tokens (carryover from Phase 1.G) — `textDocument/semanticTokens/full` for dotted refs and definition names
- **L** `parse-recovery-info` emission (carryover from Phase 1.F) — `DefaultErrorStrategy` subclass that emits info diagnostics at recovery boundaries
- **M** VS Code smoke test (carryover from Phase 1.J) — `@vscode/test-electron` boot, sample-file open, diagnostic flow assertions
- **N** Documentation — progress doc, diagnostics catalog updates, semantics package README, LSP README

**Acceptance**: in `samples/v1-metadata/` opened in VS Code: Cmd-click a reference jumps to its definition; right-click → Find All References lists every use across files; hover shows description + kind + source link in Czech (manifest declares `preferred = "cs"`); Cmd-T fuzzy-finds any symbol; introducing a typo in a qname produces a red squiggly with code `ttr/unresolved-reference` and a useful "tried: …" message.

**Note on scope expansion vs the original 3–4-week estimate**: the bump to 4–5 weeks absorbs (a) Section B project model (was implicit in 2.B; now its own sub-phase because the symbol table can't merge across files without a project root), (b) Section F diagnostic-code expansion (was implicit in 2.D; now explicit because Phase 1's code-taxonomy work raised the bar), and (c) the three Phase 1 carryovers in §K, §L, §M.

## Phase 3 — Designer v1 (3–4 weeks)

**Detailed task list:** [`tasks-phase-03-designer.md`](tasks-phase-03-designer.md).

**Goal**: minimal Designer per D7 — read-only render of `db` and `er`, single display variant, schema/detail toggles, layout persistence.

**Sub-phases**:

**3.A — Designer scaffold cleanup (3–5 days)**. Remove the cuts (Quests, gamification, Ontology School). Keep canvas, detail panel, top menu bar, NL pane (functionally inert), look-and-feel. Replace RDF-specific code paths with TTR-specific stubs.

**3.B — LSP integration (3–5 days)**. LSP-in-Web-Worker bootstrap; LSP client over MessageChannel; `modeler/getModelGraph` consumed; `modeler/getLayout` / `modeler/setLayout` round-trip working; file-system shim (File System Access API + upload fallback).

**3.C — db schema rendering (3–5 days)**. Cytoscape adapter for `db.table` nodes (showing columns inline per displayMode), `db.fk` edges between tables. Schema toggle UI (db / er buttons). Detail-mode toggle UI (just-names / with-types / with-constraints).

**3.D — er schema rendering (3–5 days)**. Cytoscape adapter for `er.entity` nodes (showing attributes inline), `er.relation` edges (with cardinality glyph in Crow's foot style). Reuses the schema/detail-toggle UI from 3.C.

**3.E — Detail panel (3–5 days)**. Right-side panel populated from `modeler/getModelGraph`'s descriptive records (`description`, `tags`, type, source file:line, related symbols).

**3.F — Layout persistence (2–3 days)**. On every viewport pan/zoom and node drag, debounced `modeler/setLayout` calls. On open, `modeler/getLayout` restores positions and viewport state.

**3.G — Static-site deploy (1–2 days)**. GitHub Pages workflow; embeddable `<script>` tag for inline use; demo-mode landing page with the `samples/` bundle pre-loaded.

**Acceptance**: open the `samples/v1-metadata/` bundle in the Designer, see db and er rendered correctly with default layouts, drag nodes to a custom layout, close + reopen, layout restored. Click a node and see its details in the right panel.

## Phase 4 — IntelliJ plugin v1 (1–2 weeks)

**Goal**: deliver the priority-3 IntelliJ plugin with feature parity to VS Code's Phase 1+2 surface.

**Sub-phases**:

**4.A — Gradle scaffold (1–2 days)**. IntelliJ Platform Gradle Plugin, Kotlin, target IntelliJ 2024.x+. File type registrations for `.ttr` and `.ttrl`.

**4.B — Bundled runtime (2–3 days)**. Per-platform Node binary bundled into the plugin JAR. Runtime extraction to a per-user cache directory on first run.

**4.C — LSP4IJ integration (2–3 days)**. Language server descriptor pointing at the bundled LSP. Standard LSP4IJ surface lights up the standard LSP features automatically.

**4.D — Polish (1–2 days)**. Plugin metadata (name, description, screenshots, icon). Install/upgrade smoke tests in IntelliJ Community + Ultimate.

**Acceptance**: install the plugin in a clean IntelliJ; open `samples/v1-metadata/`; same highlighting, navigation, hover, find-references behavior as VS Code.

## Phase 5 — Hardening, packaging, distribution (1–2 weeks)

**Goal**: ship v1.

**Sub-phases**:

**5.A — Documentation (3–5 days)**. README revamp with quick-start for VS Code, Designer, IntelliJ; architecture overview pointing at the design doc; contributor guide; troubleshooting page.

**5.B — Packaging (2–3 days)**. VS Code `.vsix` build, signed; IntelliJ `.zip` build, signed; Designer GitHub Pages workflow tested; embed `<script>` artifact published to npm and GitHub Pages.

**5.C — Marketplace submissions (2–3 days)**. VS Code Marketplace submission; JetBrains Marketplace submission; both with screenshots, descriptions, categorization.

**5.D — Performance pass (2–3 days)**. Profile parsing on the largest realistic sample; ensure cold-start LSP < 2 seconds; ensure Designer initial render < 3 seconds for a 100-node graph; tune anywhere these targets aren't hit.

**5.E — Release (1 day)**. v1.0.0 tag; GitHub Release with changelog; marketplace publish.

## v1.1+ outlook (not planned in detail)

For context, here's the rough sequence post-v1, in priority order:

- **v1.1**: Designer edit mode. The biggest single addition: the edit synthesizer becomes load-bearing; Designer gains entity/attribute/relation create/edit/delete. Estimated 4–6 weeks.
- **v1.2**: Productivity-tier LSP (completion for property names, schema kinds, references; document/workspace symbols; outline view). Estimated 3–4 weeks.
- **v1.3**: Polish-tier LSP (rename, format, code actions, code lens, semantic tokens richer rules). Estimated 3–4 weeks.
- **v1.4**: Designer's `cnc` schema + Chen / UML display variants for E-R + the natural-language pane wired to an LLM. Estimated 4–6 weeks.
- **v1.5+**: live-database integration (validate model against actual database schemas via ai-platform's metadata service), team collaboration features, etc.

## Risks

- **Edit synthesizer (v1.1) complexity**. The biggest risk in the v1.1 plan; Phase 0–v1 keeps it deferred so we don't carry it as an unknown. When we get there, expect a meaningful architectural pass before implementation starts.
- **antlr4ng maturity**. Decision locked: we use `antlr4ng` (modern, actively maintained ANTLR4 TypeScript runtime). Phase 0 surfaces any issues against our specific grammar. Contingency if a hard blocker appears: the Lezer parser system used by CodeMirror has good editor-tooling DNA but requires hand-writing the grammar (not regenerated from `.g4`) — escalate to Bora before going down that path.
- **Stock vocabulary sync drift**. ai-platform may evolve the stock vocab format. Mitigation: the sync script is small; we can adapt. Long-term, formalizing the stock-vocab as a versioned interchange format would help.
- **Browser file-system access in the Designer**. May force VS Code webview as the primary delivery for file-editing scenarios. Static site survives as a demo mode and an embed target.

## Acceptance summary

v1 ships when:

- VS Code extension is on the Marketplace, installable, and demonstrably useful on a real `.ttr` project
- Designer is at a public URL and renders the `samples/` bundle correctly
- IntelliJ plugin is on JetBrains Marketplace
- All three hosts pass the same `samples/`-based smoke tests in CI
- Documentation covers install, getting started, and the architectural shape
