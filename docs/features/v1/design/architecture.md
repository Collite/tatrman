# Tatrman Modeler — Architecture & Design

**Status:** Design draft v1, 2026-05-14. Captures the decisions made during the brainstorm sessions; revise as items in §10 close.

## 1. Vision

The Tatrman Modeler is the developer- and modeler-facing tooling for the TTR language defined in the `ai-platform` project. The TTR language is the canonical serialization format for the AI Platform's metadata model — physical schemas, entity-relation models, mappings between them, queries, and (Phase 2.2 onward) conceptual roles. The Modeler product gives the people who write TTR a first-class authoring experience across three surfaces: a VS Code plugin, a graphical designer (forked from the Ontology Playground project), and an IntelliJ plugin. The plugins share a common semantic engine; nothing in the architecture is per-host except the host adapter layer.

Three deliverables, in priority order:

1. **VS Code plugin** — text-editor experience with highlighting, diagnostics, cross-reference resolution, navigation, and hover.
2. **Graphical designer** — a static React web application that renders TTR models as interactive graphs and (post-v1) edits them through structured operations that emit text edits into the canonical `.ttr` files.
3. **IntelliJ plugin** — the same tooling delivered as a JetBrains plugin via LSP4IJ.

The metadata service that consumes TTR at runtime is in `ai-platform`; that service stays unchanged. Modeler is editor-side tooling only.

## 2. Brainstorm decisions captured

Eight strategic decisions taken during the design conversation; everything below follows from them.

| # | Decision | Choice | Why |
|---|---|---|---|
| D1 | Parser/semantic-layer strategy | Strategy A — TypeScript LSP server consumed by all three hosts; parser regenerated from grammar with `antlr4ng` | Static-site-friendly Designer, no JVM dep for VS Code, IntelliJ via LSP4IJ; ai-platform's Kotlin parser stays for runtime, both regenerate from the same `.g4` |
| D2 | Grammar ownership | Modeler owns `grammar/TTR.g4`; ai-platform vendors a copy synced via CI | The language tooling project owns the language definition; clearer change-control |
| D3 | Designer ↔ TTR sync | Pattern γ — Designer issues structured graph edits via custom LSP requests; LSP synthesises text edits; node positions live in each `.ttrg` file's `layout` block **(v1.1: supersedes the v1 `.ttrl` sidecar — see decision D4 in `docs/v1-1/design/v1.1-packages-and-graphs.md`)** | Text remains canonical for the language; layout lives where it belongs; comments and formatting are preserved |
| D4 | Project model | Convention-by-default; optional `modeler.toml` manifest | Lowest onboarding friction; manifest available when projects grow |
| D5 | LSP / VS Code v1 scope | Foundation tier (highlighting, syntax-error diagnostics, brackets) + Core tier (cross-reference resolution, undefined-ref diagnostics, go-to-definition, find-references, hover) | Enough to deliver the "I trust this language now" moment; Productivity (completion) and Polish (rename/format/code actions) ship in v1.1+ |
| D6 | Build sequence | Sequence II — vertical thin slice end-to-end first (LSP minimum + VS Code minimum + Designer minimum), then iterate | Continuous demo-able progress; early integration learnings; avoids the over-engineering risk of "LSP-first" |
| D7 | Designer v1 scope | Minimal — read-only render of `db` and `er` schemas, single display variant per schema (Crow's foot for E-R), detail panel + toggles | Pairs cleanly with LSP Foundation+Core; edit mode and richer variants land in v1.1 |
| D8 | Repo structure | pnpm workspaces monorepo with granular packages | Clean dependency boundaries; pieces independently testable; LSP server is going to be substantial enough to warrant the structure |

## 3. High-level architecture

```
                        ┌─────────────────────────────────────┐
                        │          modeler/grammar/            │
                        │          TTR.g4 (canonical)          │
                        └─────────────────┬───────────────────┘
                                          │ generated via antlr4ng (TS) and ANTLR (Java/Kotlin)
                ┌─────────────────────────┼─────────────────────────────┐
                ▼                                                       ▼
   ┌─────────────────────────────┐                       ┌──────────────────────────────┐
   │  @modeler/parser            │                       │  ai-platform/ttr-parser       │
   │  (TS, regenerated)          │                       │  (Kotlin, regenerated;        │
   │                             │                       │   vendored grammar copy)      │
   └─────────────┬───────────────┘                       └──────────────┬───────────────┘
                 │                                                      │
                 ▼                                                      ▼
   ┌─────────────────────────────┐                       ┌──────────────────────────────┐
   │  @modeler/semantics         │                       │  metadata service             │
   │  symbol table, resolver,    │                       │  (model graph, gRPC API,      │
   │  validator, type checker    │                       │   live consumer of .ttr)      │
   └─────────────┬───────────────┘                       └──────────────────────────────┘
                 │
                 ▼
   ┌─────────────────────────────────────────┐
   │  @modeler/lsp                            │
   │  Standard LSP + custom modeler/* methods │
   │  Edit synthesizer                        │
   └────┬─────────────┬────────────────┬─────┘
        │             │                │
        ▼             ▼                ▼
   ┌─────────┐  ┌─────────────┐  ┌──────────────────┐
   │ VS Code │  │  Designer   │  │ IntelliJ (LSP4IJ) │
   │ thin    │  │  React app  │  │ thin Gradle plugin│
   │ shim    │  │  LSP in Web │  │                   │
   │         │  │  Worker      │  │                   │
   └─────────┘  └─────────────┘  └──────────────────┘
```

The same LSP server binary powers all three hosts. In VS Code and IntelliJ it runs as a Node child process; in the Designer it runs in a Web Worker, with the LSP client speaking to it over the standard `MessageChannel` transport. Standard LSP methods (`textDocument/didOpen`, `textDocument/diagnostic`, `textDocument/definition`, `textDocument/references`, `textDocument/hover`) cover most of v1; a small set of custom `modeler/*` methods carry the Designer-specific operations.

## 4. Component model

### 4.1 Grammar (`@modeler/grammar`)

The package owns the canonical `TTR.g4` plus the scripts that keep two generated parsers in sync. Two generation targets:

- **TypeScript** via `antlr4ng-cli`. Output goes into `@modeler/parser/src/generated/`.
- **Kotlin/Java** via the ANTLR Gradle plugin in ai-platform's `shared/libs/kotlin/ttr-parser/`. The grammar file is vendored there; a CI check (`scripts/check-grammar-sync.sh`) compares hashes and fails if they diverge.

The grammar is small (~330 lines) and well-tested by ai-platform's existing 17-case suite. We add no language constructs in v1; if we want to evolve the language, that change goes through the grammar repo here, then propagates to ai-platform via a coordinated PR.

### 4.2 Parser (`@modeler/parser`)

Wraps the generated ANTLR4 parser/lexer/visitor classes and exposes a stable, ergonomic TypeScript API. Public surface:

```ts
export interface ParseResult {
  ast: Document
  errors: ParseError[]
  source: SourceFile
}

export function parseString(content: string, fileLabel?: string): ParseResult
export function parseFile(filePath: string): Promise<ParseResult>

export interface Document {
  schemaDirective?: SchemaDirective
  definitions: Definition[]
  source: SourceLocation  // file-level
}

// Sealed-ish union: ModelDef | TableDef | ViewDef | ColumnDef | IndexDef | ConstraintDef
// | FkDef | ProcedureDef | EntityDef | AttributeDef | RelationDef | Er2DbEntityDef
// | Er2DbAttributeDef | Er2DbRelationDef | QueryDef | RoleDef | Er2CncRoleDef
export type Definition = ...
```

Two important design points:

- **Cross-references are opaque strings here**, identical to ai-platform's parser. Resolution happens in `@modeler/semantics`. This keeps the parser layer mechanical and easy to keep aligned with the Kotlin sibling.
- **Source locations are first-class on every node** (file, line, column, end-line, end-column, byte offsets). This is what the edit synthesizer uses to produce surgical text patches.

We also expose a **CST view** alongside the AST. The CST preserves trivia (whitespace, comments) attached to each node — needed for the edit synthesizer when it needs to know where a `def entity X { ... }` block ends, including any trailing comma or comment, in order to insert a sibling cleanly. ANTLR4's `TokenStream` gives us this via `getHiddenTokensToLeft`/`getHiddenTokensToRight`; the parser package exposes a thin wrapper.

Parser performance target: parse the full `samples/v1-metadata/db.ttr` (the 39k-token sample) in <50 ms on a developer laptop. ai-platform's Kotlin parser hits this comfortably; antlr4ng has comparable performance.

### 4.3 Semantics (`@modeler/semantics`)

The semantic layer that the parser deliberately omits. Three sub-modules:

**Symbol table**. For each `Document`, an index keyed by qname (e.g. `db.dbo.QZBOZI_DF`, `er.entity.artikl.id_artiklu`). Builds incrementally — when one file changes, only that file's contributions are rebuilt and merged into the project-wide symbol table. The index also tracks which fields produced each binding (kind, name attribute, source location).

**Reference resolver**. Takes a `Reference(path: string)` and returns either a `ResolvedSymbol` or an `UnresolvedReference` diagnostic. Resolution rules mirror what the metadata service does at runtime: dotted refs walk schema → namespace → object kind → object → sub-object; bare refs first try local scope (e.g. an attribute name within the enclosing entity), then fall through to the project's symbol table.

**Validator**. Per-kind checks — required properties present, property types match the grammar's expectations, no duplicate sibling defs, primary-key columns exist on the table, entity attributes referenced by `nameAttribute`/`codeAttribute` actually exist, etc. The validator runs on the resolved AST and produces structured diagnostics.

The semantics layer is what makes diagnostics meaningful. The grammar catches "you wrote `:` where `=` was expected." The semantics layer catches "you referred to `er.entity.artiklu` but no such entity is defined." That second class of diagnostic is by far the more useful one in practice.

**Stock vocabulary loading.** The semantics layer auto-imports `cnc.*` — every package can reference `cnc.role.*` with no explicit import declaration. Stock roles live in `package cnc`; qnames resolve internally to `cnc.cnc.role.<defName>`. The stock vocabulary is bundled with `@modeler/semantics` and loaded before any user files.

**Package model.** Every `.ttr` file belongs to exactly one package (declared via `package <qualified-name>` at the top). Files without a declaration belong to the default (root) package. The default package is represented by the absence of a declaration — not by `package ` with no name. Package declarations affect two things: (1) the qname prefix for defs in the file (`billing.invoicing.er.entity.artikl` for a file declaring `package billing.invoicing`), and (2) import-based resolution — defs in the same package are visible to each other without explicit imports; defs in other packages require either a named `import` or a wildcard `import <pkg>.*`.

### 4.4 Edit synthesizer (`@modeler/edit`)

**v1.1:** The edit synthesizer is now **load-bearing** — no longer a placeholder. Two concrete uses:

1. **`modeler/addObjectToGraph` / `modeler/removeObjectFromGraph`** — the Designer issues these when the user clicks "Add object" or "Remove from graph". The synthesizer produces a `WorkspaceEdit` that mutates the target `.ttrg` file's `objects` list.

2. **`textDocument/rename`** — when the user renames a `def` anywhere in the project, the synthesizer rewrites every `.ttrg` file that lists the renamed object's qname, plus all referencing `.ttr` files.

The synthesizer uses the parser's CST view to know exactly where blocks start and end, what the prevailing indentation is, and whether commas are used as separators in the surrounding context. The output is always a `WorkspaceEdit` object the host applies via the standard LSP path.

Operations to support in v1.1:

| Operation | Effect on TTR text |
|---|---|
| `setProperty(target, propertyName, value)` | Edit the matching property, or insert it inside the def block |
| `addAttribute(entity, attribute)` | Insert a `def attribute X { ... }` inside the entity's `attributes: [...]` list |
| `removeAttribute(entity, attributeName)` | Remove the inline `def attribute` and surrounding comma |
| `addColumn(table, column)` | Mirror of addAttribute for tables |
| `removeColumn(table, columnName)` | Mirror of removeAttribute |
| `createRelation(from, to, cardinality)` | Insert a `def relation` at the file's tail |
| `removeRelation(qname)` | Remove the `def relation` block including trailing whitespace |
| `renameSymbol(qname, newName)` | Standard LSP-style rename across all references in the project (also surfaced as standard `textDocument/rename`) |

The synthesizer uses the parser's CST view to know exactly where blocks start and end, what the prevailing indentation is, and whether commas are used as separators in the surrounding context. The output is always a `WorkspaceEdit` object the host applies via the standard LSP path. This means VS Code and IntelliJ get the same edit semantics for free if they ever expose graph-level edit commands themselves.

### 4.5 LSP server (`@modeler/lsp`)

Thin orchestrator over parser + semantics + edit synthesizer. Built on `vscode-languageserver-node`. Standard methods implemented in v1:

- `initialize`, `initialized`, `shutdown` — lifecycle
- `textDocument/didOpen`, `didChange`, `didClose`, `didSave` — document sync
- `textDocument/diagnostic` (pull model) and `textDocument/publishDiagnostics` (push model)
- `textDocument/definition` — go-to-definition for cross-references
- `textDocument/references` — find-references for any symbol
- `textDocument/hover` — show definition's `description`, type, schema kind
- `workspace/configuration` — read project-level preferences

Custom `modeler/*` methods for the Designer:

- `modeler/getModelGraph` — returns the resolved model as a JSON graph (nodes, edges, qnames, layout hints) so the Designer can render without re-parsing
- `modeler/applyGraphEdit` — accepts a `GraphEdit` operation; returns a `WorkspaceEdit` for the host to apply; the LSP also re-parses and re-validates after the host confirms application
- `modeler/getLayout` / `modeler/setLayout` — read/write the project's `.ttrl` sidecar (managed by the LSP rather than the host so layout state is consistent across hosts). **Superseded in v1.1:** these are now `graphUri`-scoped and read/write the `layout` block inside the target `.ttrg` file; `setLayout` returns a `WorkspaceEdit` the host applies (see contracts §8, decision D4).
- `modeler/getProjectInfo` — returns the resolved project root, manifest contents (or convention defaults), declared schemas, stock-vocab references

The LSP runs in two transports:

- **stdio** for VS Code and IntelliJ, spawned as a Node child process by each host
- **MessageChannel** for the Designer's Web Worker (using `vscode-languageserver-protocol/browser`)

Both transports speak the same protocol; the only difference is the message transport mechanism.

### 4.6 VS Code extension (`@modeler/vscode-ext`)

Deliberately thin. Contributes:

- Language registration for `.ttr` and `.ttrl` (file extensions, MIME types) *(v1.1: `.ttrl` is removed; `.ttrg` graph files are registered instead — see `docs/v1-1/`)*
- Language configuration (bracket pairs, comment toggle, auto-close, indentation rules)
- TextMate grammar for syntax highlighting (auto-generated from `TTR.g4` via a script in `@modeler/grammar`; covers tokens only, semantic tokens via LSP for richer cases)
- LSP client wiring — spawns the LSP server as a child process, manages lifecycle
- A single command: `Modeler: Open in Designer` (placeholder in v1; functional once Designer ships)

No business logic in the extension itself. Anything that requires understanding TTR happens in the LSP.

### 4.7 Designer (`@modeler/designer`)

Forked from Ontology Playground. Keeps:

- The Cytoscape.js canvas with its full functionality (pan, zoom, click-to-select, multi-select)
- The right-side detail panel
- The "Designer mode" UI shell (read-only in v1; edit in v1.1)
- The overall look and feel
- The top menu bar
- The "natural language" pane shell (functionally inert in v1; LLM integration in v1.x)
- The tech stack (React 19, TypeScript 5, Vite, Tailwind)

Removes:

- The Quests and gamification
- The Ontology School

Adds:

- LSP-in-Web-Worker bootstrap; LSP client over MessageChannel
- File-system shim — uses the File System Access API where supported, falls back to `<input type="file">` upload + in-memory store
- Schema/detail toggle UI (db / er; just-names / +types / +constraints)
- Layout persistence via `modeler/setLayout` round-trips
- Cytoscape adapter that converts `modeler/getModelGraph` responses into Cytoscape elements

The Designer is published as a static site (GitHub Pages or similar). It's also embeddable in a VS Code webview via the existing extension's `Modeler: Open in Designer` command — same code, different transport (postMessage instead of MessageChannel), v1.x feature.

### 4.8 IntelliJ plugin (`intellij-plugin/`)

Gradle-based, written in Kotlin, using the IntelliJ Platform Gradle Plugin and LSP4IJ. The plugin:

- Registers `.ttr` and `.ttrl` as file types *(v1.1: `.ttrl` is removed; register `.ttrg` instead — see `docs/v1-1/`)*
- Registers an LSP4IJ language server descriptor pointing at a bundled Node + LSP bundle
- Bundles a per-platform Node binary (or detects an existing one) so users don't need Node installed separately

LSP4IJ surfaces all the standard LSP features as native IntelliJ actions automatically — Go to Declaration, Find Usages, Quick Documentation, etc. all light up without per-feature plugin code. Custom `modeler/*` methods are not surfaced in IntelliJ in v1; they only matter for the Designer.

## 5. Project model and manifest

A "TTR project" is the smallest closed unit for cross-reference resolution. The LSP determines the project root for any open `.ttr` file by:

1. Walking up from the file looking for a `modeler.toml`. If found, that directory is the project root and the manifest configures the project.
2. Otherwise, treating the LSP `workspaceFolder` as the project root and using convention defaults.

**Package model (v1.1).** Every file belongs to a package. The package name is derived from its directory relative to the project root: `<root>/foo/bar/baz.ttr` → `package foo.bar`. A file can optionally declare its own package with `package <qualified-name>`; if present and the declaration does not match the path-inferred name, the validator emits `ttr/package-declaration-mismatch`. Files in the project root with no declaration are in the **default (root) package** — represented by the absence of a declaration, not by `package ` with no name.

Cross-package references require an explicit import. Same-package references always resolve without imports.

The `modeler.toml` schema (TOML chosen for editability and the small set of types we need):

```toml
# modeler.toml — optional project manifest
[project]
name = "df-erp-metadata"
version = "0.1.0"

[language]
preferred = "cs"     # BCP-47; controls hover labels and Designer display

[schemas]
declared = ["db", "er", "map"]   # which schemas this project uses; informs validation
namespaces = { db = "dbo", er = "entity", map = "er2db" }

[stock]
load = ["cnc-roles"]   # built-in vocabularies to pre-load

[lint]
strict = false                     # if true, warnings become errors
require-descriptions = false       # require `description` on every def
```

All keys are optional; missing values use the same defaults convention-by-default would apply.

## 6. Layout in `.ttrg` files (supersedes the v1 `.ttrl` sidecar)

> **Superseded in v1.1.** The standalone `.ttrl` sidecar was removed in v1.1 (decision D4). Layout now lives inside each `.ttrg` file's `layout` block. The contract: node keys are **unquoted dotted-id strings** (e.g. `billing.invoicing.er.entity.artikl: { x: 320, y: 180 }`). The `.ttrl` format below is retained only as historical record.

One `.ttrg` per graph, stored at `<project-root>/graphs/<name>.ttrg`. The `layout` block is managed by the LSP via `modeler/getLayout` / `modeler/setLayout` (both now take a `graphUri` parameter). Hosts never touch the layout directly.

Layout schema inside `.ttrg`:

```
layout: {
  viewport: { zoom: 1.0, panX: 0, panY: 0, displayMode: "with-types" },
  nodes: {
    <unquoted-qname>: { x: <number>, y: <number> },
    ...
  }
}
```

Per-`viewport.displayMode` values: `just-names | with-types | with-constraints | with-indices | full`.

## 7. Repository structure

```
modeler/
  samples/                    # existing — kept at repo root for browsability
  packages/
    grammar/                  # @modeler/grammar
      src/
        TTR.g4                # canonical grammar (moved from repo root in Phase 0)
      scripts/
        generate-typescript-parser.sh
        sync-to-ai-platform.sh
        check-sync.sh
    parser/                   # @modeler/parser — TS, regenerated from grammar
    semantics/                # @modeler/semantics — symbol table, resolver, validator
    edit/                     # @modeler/edit — WorkspaceEdit synthesizer
    lsp/                      # @modeler/lsp — LSP server, both transports
    vscode-ext/               # @modeler/vscode-ext — VS Code extension
    designer/                 # @modeler/designer — React app
  intellij-plugin/            # Gradle, Kotlin, LSP4IJ
  tests/
    integration/              # cross-package end-to-end tests
  docs/
    design/
      architecture.md         # this file
    plan/
      implementation-plan.md
      tasks-phase-00-thin-slice.md
      progress-phase-00.md    # added when work starts
  .github/
    workflows/
      ci.yml
  pnpm-workspace.yaml
  package.json
  tsconfig.base.json
  .editorconfig
  .gitignore
  README.md                   # existing
```

The original `grammar/TTR.g4` at the repo root moves into `packages/grammar/src/` during Phase 0; the move is captured in §B of the Phase 0 task list. The grammar lives inside the package that owns it rather than at the repo root, which is the more conventional layout for a pnpm workspace.

`pnpm-workspace.yaml` lists `packages/*` and `intellij-plugin` (the latter is a Gradle project; pnpm doesn't manage it but listing it keeps `pnpm <command> -r --filter` aware of its existence for orchestration scripts). The Gradle build is invoked through a top-level npm script that delegates.

## 8. Cross-cutting concerns

### 8.1 Versioning

Modeler version starts at `0.1.0` and ticks independently of ai-platform. The grammar version is tracked separately as a `version` field in the grammar's leading comment block and in `@modeler/grammar`'s `package.json`. ai-platform pins a grammar version it has tested against; bumping the grammar in modeler does not automatically force ai-platform to upgrade. The CI sync check fails only when the *vendored* file in ai-platform falls behind the version it claims to track.

### 8.2 Localization

The TTR language supports localized strings via the `{cs: "...", en: "..."}` block (Phase 2.2 of ai-platform). The Modeler:

- Hover text picks the user's preferred language from `modeler.toml` `[language].preferred`, falling back to `en`, then to the bare key
- Designer detail panel uses the same preference for entity / attribute / role labels
- Diagnostics (LSP `Diagnostic.message`) are English-only in v1; localized diagnostic messages are deferred

### 8.3 Error handling

Every public LSP method has a deterministic failure shape:

- Parse errors → diagnostics published via `textDocument/publishDiagnostics`
- Unresolved references → diagnostics with `severity: Warning` (configurable to `Error` via `[lint].strict`)
- Internal errors in the LSP → logged to the LSP's stderr (visible in VS Code Output panel) and surfaced as a single `Error` diagnostic on the offending document with a generic message + correlation ID

The Designer surfaces errors in a non-modal toast plus the right-side detail panel's "Issues" tab.

### 8.4 Testing strategy

Per-package: Vitest unit tests (TS) and Kotest (Kotlin, for IntelliJ). Cross-package: a `tests/integration/` package runs scenarios against the LSP via `vscode-languageserver-protocol`'s test transport. Designer has Playwright tests for the round-trip (open file → render → assert graph shape; later: edit → assert .ttr text).

Golden-file tests for the parser: every `.ttr` file in `samples/` parses without errors. The same fixtures run against the Kotlin parser via a CI job that pulls them into ai-platform's CI.

### 8.5 CI

A single `.github/workflows/ci.yml` runs:

- `pnpm install --frozen-lockfile`
- `pnpm -r build`
- `pnpm -r test`
- `pnpm -r lint`
- `bash grammar/scripts/check-sync.sh` (skips if ai-platform isn't a sibling checkout; runs in a separate cross-repo job that does the dual checkout)
- For PRs touching `intellij-plugin/`: Gradle build + test

### 8.6 Distribution

- VS Code extension: VS Code Marketplace + `.vsix` artifact attached to GitHub Releases
- Designer: GitHub Pages (static); also embeddable via VS Code webview (v1.x)
- IntelliJ plugin: JetBrains Marketplace + `.zip` artifact attached to GitHub Releases

## 9. Relationship to ai-platform

The Modeler stays decoupled from ai-platform at runtime:

- **Compile-time**: shares the grammar via the sync script.
- **Schema-time**: ships the same stock vocabulary as ai-platform's `BuiltinStockSource`, synced on an as-needed basis (next sync triggered when ai-platform changes it).
- **Runtime**: never connects to ai-platform's metadata service. The Modeler can always work fully offline against a local folder of `.ttr` files. The LSP does not need network access of any kind.

If a future feature wants live integration (e.g. "validate this model against the live database schema"), that's a deliberate, opt-in ai-platform integration and falls outside the v1 scope.

## 10. Open questions (to revisit during implementation)

1. **Exact CST trivia attachment policy.** ANTLR's hidden-token streams give us the raw material; the policy of "which trivia attaches to which node" affects edit fidelity. Specified during Phase 0 parser package work with concrete fixtures.
2. **Project root in multi-root VS Code workspaces.** v1 assumes one project per workspace folder. Multi-root workspaces need an explicit test pass — deferred to v1.x.
3. **TTR formatter rules.** Deferred to v1.2 with `format-document`. Rules to settle: where to break long property lists, how to reformat triple-string blocks, whether to enforce `:` or `=` as the property separator (or leave alone).
4. **Bundled Node for IntelliJ.** Deferred — v1 ships the IntelliJ plugin without bundled Node; users must have Node on PATH.
5. **Designer file-system access in browser.** Resolved in Phase 3: uses File System Access API (Chromium) with a hidden `<input webkitdirectory>` fallback for Safari and non-supporting browsers.
6. **Stock-vocabulary sync mechanism.** Resolved in v1.1: `cnc.*` is auto-imported by the semantics layer; no external sync script needed.
7. **Edit synthesizer — handling of existing comments adjacent to insertions.** Resolved in v1.1: the synthesizer uses CST-based insertion that preserves adjacent trivia.

~~8. **Layout persistence between sessions.**~~ Resolved in v1.1: layout lives in each `.ttrg` file's `layout` block; `modeler/setLayout` returns a `WorkspaceEdit` the host applies.

These don't block Phase 0; they get resolved as the relevant work lands.

---

## 11. Designer ↔ LSP Control Flow (Deployed Shape)

In the GitHub Pages deployment, the Designer is a static React app served from `modeler/`. The LSP runs entirely inside the browser as a Web Worker (`@modeler/lsp/browser?worker`).

```
┌─────────────────────────────────────────────────────────────┐
│  Browser (GitHub Pages)                                    │
│                                                             │
│  ┌─────────────┐     LSP Worker (Web Worker)                │
│  │ Designer    │ ←── postMessage ──→ │  @modeler/lsp        │
│  │ React App   │                     │  server-browser.js   │
│  └─────────────┘                    └──────────┬───────────┘
│                                                │ parseString
│                                                ▼
│                                    ┌─────────────────────┐
│                                    │  @modeler/parser     │
│                                    └─────────────────────┘
│                                                │
│                                    ┌─────────────────────┐
│                                    │  @modeler/semantics │
│                                    └─────────────────────┘
```

Communication: the LSP Web Worker is instantiated by the Designer via `new Worker(new URL('@modeler/lsp/browser?worker', import.meta.url))`. The LSP protocol (initialize, textDocument/didOpen, etc.) runs over `postMessage`. Layout persistence is handled via `modeler/setLayout` / `modeler/getLayout` with a `graphUri` parameter — reading from / writing to the `layout` block inside the target `.ttrg` file.

**Graph-centric flow (v1.1):** The Designer does not render the entire project at once. Instead, it opens one `.ttrg` file at a time, scoped to a specific schema and set of objects. The user picks a graph (or creates a new one via the wizard); the LSP's `modeler/getGraph(graphUri)` returns the graph's nodes, edges, layout, and any `missingObjects`. The Designer renders that graph.

**Add / remove object:** clicking "Add object" calls `modeler/addObjectToGraph(graphUri, qname)` → `WorkspaceEdit` that appends the qname to the target `.ttrg`'s `objects` list. The LSP re-parses and the Designer re-fetches to update the render.
