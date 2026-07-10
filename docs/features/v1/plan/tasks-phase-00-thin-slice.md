# Phase 0 — Vertical thin slice

**Status:** v0 draft, ready for Bora review then handoff to Claude Code.
**Branch:** `feat/phase-00-thin-slice`
**Time budget:** 1–2 weeks
**Dependencies:** none — first phase
**Blocks:** all subsequent phases

## Goal

Ship an end-to-end working pipeline at the lowest possible feature bar so every subsequent phase has a real consumer to validate against. After Phase 0, opening a `.ttr` file in VS Code shows highlighted text with red squigglies on syntax errors; opening the Designer shows a minimal read-only graph for one sample file. No semantic features yet — just the wire.

## Pre-flight

- [ ] Create branch `feat/phase-00-thin-slice` from `main`
- [ ] Create `docs/plan/progress-phase-00.md` to track progress and any deviations
- [ ] Confirm Node 20+ and pnpm 9+ available locally (otherwise install)
- [ ] Confirm Java 21+ available (needed for IntelliJ work in Phase 4 — verify now to surface platform issues early; not used in Phase 0 itself)
- [ ] Read `docs/design/architecture.md` end-to-end
- [ ] Read `grammar/TTR.g4` and one sample (`samples/v1-metadata/er.ttr` is a good starter)

## Section A — Monorepo scaffold

- [ ] Add `package.json` at repo root with `"private": true`, `"packageManager": "pnpm@9.x.x"`, scripts for `build`, `test`, `lint`, `format`, `typecheck` that delegate via `pnpm -r`
- [ ] Add `pnpm-workspace.yaml` listing `packages/*`
- [ ] Add `tsconfig.base.json` with `"strict": true`, `"target": "es2022"`, `"module": "node16"`, `"moduleResolution": "node16"`, `"esModuleInterop": true`, `"skipLibCheck": true`, `"declaration": true`, `"sourceMap": true`
- [ ] Add `.editorconfig`: 2-space indent, LF line endings, UTF-8, trim trailing whitespace, final newline
- [ ] Add `.gitignore` covering `node_modules/`, `dist/`, `*.tsbuildinfo`, `.vscode-test/`, `.pnpm-store/`, generated parser sources, `.ttrl` (only at the repo root for testing)
- [ ] Add Prettier config (`.prettierrc.json`): 100-column width, single-quote strings, trailing commas
- [ ] Add ESLint config (`.eslintrc.cjs`): `@typescript-eslint/recommended` + `prettier/recommended`, no `any` (with allowed escape hatches in generated code)
- [ ] Add Vitest base config at root (`vitest.config.ts`) that packages can extend
- [ ] Add a CI workflow `.github/workflows/ci.yml` running `pnpm install --frozen-lockfile`, `pnpm -r build`, `pnpm -r test`, `pnpm -r lint` on Node 20 + Ubuntu

**Acceptance**: `pnpm install` succeeds; `pnpm -r build` succeeds (with no packages yet); CI workflow runs green on a no-op commit.

## Section B — `@modeler/grammar`

- [ ] Create `packages/grammar/` with `package.json` (`"name": "@modeler/grammar"`, `"version": "0.1.0"`, `"private": true`)
- [ ] Move `grammar/TTR.g4` from repo root to `packages/grammar/src/TTR.g4`
- [ ] Update repo root `README.md` reference to grammar location
- [ ] Add `packages/grammar/scripts/generate-typescript-parser.sh` that invokes antlr4ng-cli on `src/TTR.g4` and writes output to `../parser/src/generated/` (path is the consuming package)
- [ ] Add `packages/grammar/scripts/sync-to-ai-platform.sh` that copies `src/TTR.g4` into a path passed as `$1` (intended target: `ai-platform/shared/libs/kotlin/ttr-parser/src/main/antlr/shared/ttr/parser/generated/TTR.g4`); script writes a header comment line into the destination noting "vendored from modeler@<commit>"
- [ ] Add `packages/grammar/scripts/check-sync.sh` that, given a path to ai-platform via `$1`, hashes both copies and exits non-zero on mismatch with a diff summary
- [ ] Add `packages/grammar/README.md` documenting the three scripts and the canonical-source-here policy
- [ ] Expose the grammar file path via a small `index.ts` (`export const grammarFile = path.join(__dirname, '../src/TTR.g4')`) so tooling packages can locate it without relative paths

**Acceptance**: `bash packages/grammar/scripts/generate-typescript-parser.sh` runs cleanly (after antlr4ng-cli is installed in `@modeler/parser`); the sync and check scripts work against a temporary mock target directory.

## Section C — `@modeler/parser`

- [ ] Create `packages/parser/` with `package.json`, `tsconfig.json` extending `tsconfig.base.json`
- [ ] Add dev dependency `antlr4ng-cli` (~3.x), runtime dependency `antlr4ng`
- [ ] Add a `prebuild` npm script that runs `bash ../grammar/scripts/generate-typescript-parser.sh` so the generated parser is always fresh
- [ ] Add `src/index.ts` with `parseString(content: string, fileLabel?: string): ParseResult` and `parseFile(filePath: string): Promise<ParseResult>`
- [ ] Define minimal AST types in `src/ast.ts`:
  - `Document { schemaDirective?: SchemaDirective; definitions: Definition[]; source: SourceLocation }`
  - `SchemaDirective { schemaCode: string; namespace?: string; source: SourceLocation }`
  - `Definition` as a discriminated union with `kind` discriminator (`'model' | 'table' | 'view' | 'column' | 'index' | 'constraint' | 'fk' | 'procedure' | 'entity' | 'attribute' | 'relation' | 'er2dbEntity' | 'er2dbAttribute' | 'er2dbRelation' | 'query' | 'role' | 'er2cncRole'`); each variant has `name: string` and `source: SourceLocation` only in Phase 0 (full property set deferred to Phase 2.A)
  - `SourceLocation { file: string; line: number; column: number; endLine: number; endColumn: number; offsetStart: number; offsetEnd: number }`
  - `ParseError { message: string; severity: 'error' | 'warning'; source: SourceLocation }`
  - `ParseResult { ast?: Document; errors: ParseError[]; sourceFile: string }`
- [ ] Implement a minimal `walker.ts` that walks the ANTLR parse tree and produces `Document` with stub `Definition`s (kind + name + source location only)
- [ ] ANTLR error listener integration: collect syntax errors into `ParseResult.errors` with proper source locations; never throw
- [ ] Add Vitest tests in `src/__tests__/`:
  - `parseString('')` returns empty Document with no errors
  - `parseString('schema db namespace dbo')` returns Document with `schemaDirective.schemaCode === 'db'` and `namespace === 'dbo'`
  - `parseString('def entity foo {}')` returns one `Definition` with `kind === 'entity'`, `name === 'foo'`
  - Syntax error case returns at least one `ParseError` with non-zero line/column
  - `parseFile(path)` against `samples/v1-metadata/er.ttr` returns >0 entity definitions, no errors
- [ ] Document the API in `packages/parser/README.md`

**Acceptance**: tests pass; `parseFile` against every file in `samples/` returns no errors and returns a non-empty definition list.

## Section D — `@modeler/semantics` placeholder

- [ ] Create `packages/semantics/` with `package.json`, `tsconfig.json`, dev-dep on `@modeler/parser`
- [ ] Add `src/index.ts` exporting placeholder types: `SymbolTable`, `Resolver`, `Validator` as empty interfaces with TODO comments referencing Phase 2.B/C/D
- [ ] Add a `noop()` function that returns `void` so the package isn't truly empty (avoids tsc complaining about an empty module)
- [ ] Add one trivial Vitest test confirming the package builds and exports the placeholder types
- [ ] Document the package as "intentionally minimal in Phase 0; expanded in Phase 2"

**Acceptance**: package builds; downstream packages can import from it without error.

## Section E — `@modeler/edit` placeholder

- [ ] Create `packages/edit/` with `package.json`, `tsconfig.json`
- [ ] Empty placeholder with a `WorkspaceEdit` re-export from `vscode-languageserver-types` so the type is available for downstream
- [ ] Document the package as "synthesizer lands in v1.1"

**Acceptance**: package builds.

## Section F — `@modeler/lsp`

- [ ] Create `packages/lsp/` with `package.json`, `tsconfig.json`, deps on `vscode-languageserver`, `vscode-languageserver-textdocument`, `@modeler/parser`, `@modeler/semantics`, `@modeler/edit`
- [ ] Add two entry points: `src/server-stdio.ts` (Node child-process entry) and `src/server-browser.ts` (Web Worker entry); both delegate to `src/server.ts` which contains the actual server logic
- [ ] Implement standard LSP lifecycle: `initialize`, `initialized`, `shutdown`, `exit`
- [ ] Implement `textDocument/didOpen`, `didChange`, `didClose`, `didSave` with full document sync
- [ ] On every document change, parse the document; emit `textDocument/publishDiagnostics` with one `Diagnostic` per `ParseError`
- [ ] Implement custom method `modeler/getModelGraph` that returns a stub graph: `{ nodes: [{ qname, kind, label }], edges: [] }`. In Phase 0 this is just one node per definition in the open document; resolution and edges land in Phase 2
- [ ] Add esbuild scripts to bundle each entry into a single .js file (one for stdio, one for browser; both target `es2022`)
- [ ] Add Vitest tests using `vscode-languageserver-protocol` testing utilities:
  - Server initializes and responds to `initialize`
  - `didOpen` of a malformed `.ttr` document publishes a diagnostic
  - `modeler/getModelGraph` after `didOpen` returns expected stub nodes
- [ ] Document the public surface (standard methods + custom methods) in `packages/lsp/README.md`

**Acceptance**: tests pass; the bundled `server-stdio.js` runs as `node dist/server-stdio.js` and responds to a hand-crafted JSON-RPC `initialize` request.

## Section G — `@modeler/vscode-ext`

- [ ] Create `packages/vscode-ext/` with `package.json` matching VS Code extension conventions (`"engines": { "vscode": "^1.85.0" }`, contributions for languages, command, and extension manifest fields)
- [ ] Dependencies: `vscode-languageclient`, runtime dep on `@modeler/lsp` for the bundled server file
- [ ] Add `src/extension.ts` with `activate`/`deactivate` that spawn the LSP server (`@modeler/lsp/dist/server-stdio.js`) as a Node child process and wire up the LanguageClient
- [ ] Contribute language registration for `.ttr` (id `ttr`) and `.ttrl` (id `ttrl`); the latter as a JSON-like read-only language config in Phase 0
- [ ] Contribute language configuration: bracket pairs `()`, `[]`, `{}`; line comment `//`; block comment `/* */`; auto-close pairs; surround pairs
- [ ] Generate a minimal TextMate grammar from the lexer rules in `TTR.g4` — keywords, strings, numbers, comments only. Place at `syntaxes/ttr.tmLanguage.json`. A small generator script in `scripts/generate-tm-grammar.ts` produces this from the grammar; commit the generated file (regenerate on grammar changes)
- [ ] Contribute one command: `modeler.openInDesigner` (registered but inert in Phase 0; Phase 3 wires it)
- [ ] Add a launch configuration (`.vscode/launch.json`) for "Run Extension" that opens an Extension Development Host for local testing
- [ ] Add a smoke test using `@vscode/test-electron` that opens a `.ttr` file in the test VS Code instance and asserts the language is detected as `ttr` and a diagnostic appears for a deliberately broken file

**Acceptance**: pressing F5 in VS Code launches the Extension Development Host; opening any sample `.ttr` shows highlighting; opening a deliberately-broken file shows a red squiggly with the parse error message.

## Section H — `@modeler/designer`

- [ ] Create `packages/designer/` by forking the relevant subset of Ontology Playground (`/Users/bora/Dev/view-only/Ontology-Playground/src/`)
- [ ] Initial fork includes: the Vite + React 19 + TypeScript scaffold, the Cytoscape canvas component, the right-side panel scaffold, the top menu bar, the look-and-feel (Tailwind config, shared CSS)
- [ ] Remove: the Quests system, the Ontology School routes, RDF-specific import/export code
- [ ] Replace the RDF-driven model loader with an LSP-driven one: spawn `@modeler/lsp/dist/server-browser.js` as a Web Worker; speak LSP over the `MessageChannel`-based browser transport from `vscode-languageserver-protocol/browser`
- [ ] Add a minimal "load file" UI using the File System Access API where supported, falling back to `<input type="file">` accepting `.ttr`
- [ ] On file load, send the content via `textDocument/didOpen`; subscribe to `textDocument/publishDiagnostics` and surface them as a small banner above the canvas in Phase 0
- [ ] Call `modeler/getModelGraph` and render its `nodes` as Cytoscape nodes with auto-layout. No edges in Phase 0 (Phase 3 adds them)
- [ ] Add a Vite dev script (`pnpm --filter @modeler/designer dev`) and a build script
- [ ] Add a Playwright smoke test: spin up the dev server, load `samples/v1-metadata/er.ttr` via the file picker (or via a test-only auto-load shortcut), assert that >0 nodes appear in the canvas
- [ ] Document the dev workflow in `packages/designer/README.md`

**Acceptance**: `pnpm --filter @modeler/designer dev` opens a working dev server; selecting a sample `.ttr` file from disk renders entity nodes on the canvas.

## Section I — Cross-package integration tests

- [ ] Create `tests/integration/` (own pnpm workspace, not published) with deps on every other workspace package
- [ ] Add an end-to-end test that:
  1. Imports `parseFile` from `@modeler/parser`, parses every file in `samples/`, asserts no errors
  2. Boots an LSP server in-process, sends `didOpen` for each sample, asserts no diagnostics
  3. Calls `modeler/getModelGraph` for one representative sample, asserts the response shape
- [ ] Wire this into the root CI workflow

**Acceptance**: integration test passes; serves as a regression net for Phase 1+ work.

## Section J — Documentation

- [ ] Update repo root `README.md` to point at `docs/design/architecture.md` and `docs/plan/implementation-plan.md`; add a quick "developing locally" section listing the prereqs (Node 20+, pnpm 9+) and the four key commands (`pnpm install`, `pnpm -r build`, `pnpm -r test`, `pnpm -r lint`)
- [ ] Add `CONTRIBUTING.md` with the workspace structure, how to add a new package, the test conventions
- [ ] Each package has its own `README.md` (covered above per package)

**Acceptance**: a fresh contributor can clone, install, build, test, and run the VS Code extension and Designer locally with the README as their only guide.

## Section K — Progress tracking

- [ ] Throughout Phase 0, log section completion + any deviations in `docs/plan/progress-phase-00.md`
- [ ] On Phase 0 completion, write the recap section: what shipped, what was deferred (with rationale), known issues to address in Phase 1

## Acceptance criteria for Phase 0 as a whole

- [ ] All sections A–K complete (Section K is the progress doc itself)
- [ ] `pnpm -r build` clean, `pnpm -r test` green, `pnpm -r lint` clean
- [ ] CI green on the PR
- [ ] Demo path verified by hand: open `samples/v1-metadata/er.ttr` in VS Code, see highlighting + diagnostics; introduce a typo, see a red squiggly; open the same file in the Designer, see entity nodes rendered
- [ ] Bora reviews the Phase 0 PR; merge when approved

## Risks and mitigations

- **antlr4ng or its TypeScript output has a blocking issue.** Decision is locked on `antlr4ng`; if a hard blocker surfaces during Phase 0, escalate to Bora rather than silently swapping parser libraries. Lezer is the documented fallback path but requires re-writing the grammar (not generated from `.g4`).
- **Forking Ontology Playground introduces unexpected dependencies (state libraries, GitHub-OAuth code paths).** Mitigation: the fork is selective, not a copy of the whole project; if surprises arise, consider scaffolding a fresh React+Vite app and porting only the canvas component.
- **VS Code extension's TextMate grammar generation produces incorrect highlighting.** Mitigation: TextMate is best-effort in Phase 0; semantic tokens via LSP land in Phase 1 to fill any gaps. Ship a "good enough" TextMate that doesn't break.
- **Web Worker LSP transport has a quirk we haven't anticipated.** Mitigation: there's a known-working reference in the `vscode-languageserver` examples; if our transport doesn't work, fall back to running the LSP via a local WebSocket (development-only) until we resolve the Worker issue.

## Out of scope for Phase 0 (deferred to later phases)

- Symbol table, reference resolution, semantic validation (Phase 2)
- Go-to-definition, find-references, hover (Phase 2)
- Designer edit mode and the WorkspaceEdit synthesizer (v1.1)
- Designer detail panel content (Phase 3 — only the panel scaffold survives the fork in Phase 0)
- Designer schema/detail toggles (Phase 3)
- Layout persistence (Phase 3)
- IntelliJ plugin (Phase 4)
- TextMate grammar covering all token cases (Phase 1; Phase 0 ships a minimal one)
- Localization of hover text (Phase 2.G)
- ai-platform sync of the grammar (works locally; CI integration in Phase 1)
