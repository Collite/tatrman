# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Tatrman Modeler — editor-side tooling for the TTR modeling language. Delivers a VS Code plugin, a static React graphical designer, and (later) an IntelliJ plugin, all sharing one TypeScript LSP server. TTR itself is consumed at runtime by `ai-platform` (separate repo); this repo is editor tooling only and never talks to that service.

Authoritative design and decisions for v1 live in `docs/v1/design/architecture.md` — read it before making non-trivial architectural changes. The v1 phased plan is in `docs/v1/plan/implementation-plan.md`. The v1.1 design (packages, imports, `.ttrg`) lives under `docs/v1-1/`.

## Commands

Workspace uses pnpm 11 (see `packageManager`). Node 20+ required.

| Command | Purpose |
|---|---|
| `pnpm install` | Install all workspace deps |
| `pnpm -r build` | Build every package (`tsc`, plus `esbuild` bundles for `@modeler/lsp`) |
| `pnpm -r test` | Run all Vitest suites across packages |
| `pnpm -r typecheck` | Type-check without emitting |
| `pnpm -r lint` | Lint all packages |
| `pnpm --filter @modeler/<pkg> test` | Run one package's tests |
| `pnpm --filter @modeler/<pkg> test -- <pattern>` | Run a single test file/name |
| `pnpm --filter @modeler/designer dev` | Run the Designer dev server (Vite, http://localhost:5173) |

### Grammar regeneration

`packages/grammar/src/TTR.g4` is the canonical grammar. After editing it:

1. `cd packages/parser && pnpm run prebuild` — regenerates `packages/parser/src/generated/*` via `antlr-ng` (script: `packages/grammar/scripts/generate-typescript-parser.sh`). The `prebuild` hook runs automatically before `pnpm --filter @modeler/parser build`.
2. `cd packages/vscode-ext && node scripts/generate-tm-grammar.ts` — regenerates the TextMate grammar used by the VS Code extension for syntax highlighting.
3. Commit the grammar change. `packages/grammar/src/generated/` and `packages/parser/src/generated/` are **gitignored** — they are regenerated at build time from `TTR.g4`. Only `TTR.g4`, the generation scripts, and `packages/vscode-ext/syntaxes/ttr.tmLanguage.json` are committed.

The grammar is also vendored into the `ai-platform` repo. `packages/grammar/scripts/sync-to-ai-platform.sh <ai-platform-path>` copies it; `check-sync.sh <ai-platform-path>` verifies hashes match. ai-platform's Kotlin parser regenerates from its vendored copy.

### Testing the VS Code extension

Open `packages/vscode-ext` in VS Code and press F5 to launch an Extension Development Host, then open any `.ttr` file.

### Kotlin artifacts (Gradle build)

Two build domains coexist in this repo and share **only** `packages/grammar/src/TTR.g4`:

- **pnpm / TypeScript** (`packages/*`, commands above) — LSP, VS Code ext, Designer.
- **Gradle / Kotlin** (`packages/kotlin/*`) — the published Maven artifacts
  `org.tatrman:ttr-parser` and `:ttr-writer` (and, from Phase 2, `:ttr-semantics`),
  consumed by the `ai-platform` repo. The Kotlin parser reads `TTR.g4` directly
  (no sync/copy) and is kept byte-for-byte conformant with the TS parser by the
  conformance harness (`conformance.yml`).

| Command | Purpose |
|---|---|
| `./gradlew :packages:kotlin:ttr-parser:test :packages:kotlin:ttr-writer:test` | Run the Kotlin (Kotest) suites |
| `./gradlew build` | Build + test all Kotlin modules |
| `./gradlew -Pversion=0.0.1-LOCAL :packages:kotlin:ttr-parser:publishToMavenLocal :packages:kotlin:ttr-writer:publishToMavenLocal` | Local cross-repo iteration via Maven Local |

Publishing is **tag-driven** via `.github/workflows/publish.yml`: push
`kotlin/v<x.y.z>` (bundle), `kotlin-parser/v<x.y.z>`, or
`kotlin-semantics/v<x.y.z>`. CI uses the auto-provisioned `GITHUB_TOKEN`; local
manual publishers need a PAT in `~/.gradle/gradle.properties`. Full policy,
semver rules, and the consumer/PAT setup live in [`PUBLISHING.md`](PUBLISHING.md);
the migration plan and contracts are in [`docs/grammar-master/`](docs/grammar-master/).

## Architecture

This is a pnpm workspaces monorepo. All TS packages extend `tsconfig.base.json` (strict, ES2022, Node16 modules, ESM). Source goes in `src/`, output in `dist/`. Tests live in `src/__tests__/*.test.ts` and use Vitest.

### Dependency graph (one-way)

```
grammar  →  parser  →  semantics  →  lsp  →  vscode-ext
                                      ↑   ↘
                                      edit   designer
```

- **`@modeler/grammar`** — owns `TTR.g4` and the generation/sync scripts. No runtime logic.
- **`@modeler/parser`** — wraps the generated antlr4ng parser; exposes `parseString` / `parseFile` returning `{ ast, errors, source }`. Cross-references are kept as opaque strings here (resolved later). Comments route to the lexer's hidden channel (`TTR.g4`: `LINE_COMMENT`/`BLOCK_COMMENT -> channel(HIDDEN)`; `WS` stays `skip`) and are attached to AST nodes as `Trivia` (`leadingTrivia`/`trailingTrivia`, see `src/cst/{trivia,attach}.ts`) by `attachTrivia` during the walk. This is the lossless CST/trivia layer that the formatter, linter suppression, and (P4) trivia-preserving autofix read from; the edit synthesizer will preserve trivia once autofix lands.
- **`@modeler/semantics`** — symbol table + reference resolver + per-kind validator. Pre-loads ai-platform's stock CNC vocab (`fact`, `dimension`, `structural`, `master`, `transaction`, `bridge`). This is where "unresolved reference"-class diagnostics come from.
- **`@modeler/edit`** — `WorkspaceEdit` synthesizer for structured graph operations from the Designer (placeholder until v1.1 when edit mode lands).
- **`@modeler/lsp`** — the single LSP server consumed by all hosts. Two entry points bundled with esbuild:
  - `server-stdio.ts` → Node child process for VS Code / IntelliJ
  - `server-browser.ts` → Web Worker for the Designer
  Implements standard LSP methods plus custom `modeler/*` methods (`getModelGraph`, `applyGraphEdit`, `getLayout`/`setLayout`, `getProjectInfo`) for Designer use.
- **`@modeler/vscode-ext`** — thin shim: language registration, TextMate grammar, LSP client wiring, one stub command. No business logic here — anything understanding TTR belongs in the LSP.
- **`@modeler/designer`** — React 19 + Vite + Cytoscape.js + Tailwind. Forked from the Ontology Playground project. v1 is read-only render of `db` / `er` schemas; edit mode (round-tripping through `modeler/applyGraphEdit`) lands in v1.1.

### Key invariants

- **Text is canonical.** The Designer never owns model state independently — it issues structured edits via custom LSP requests, the LSP synthesizes `WorkspaceEdit`s, the host applies them, and the LSP re-parses. Node positions live inside each `.ttrg` file's `layout` block (v1.1; see contracts §7.1): the LSP reads them via `modeler/getLayout` and writes them by synthesizing a `WorkspaceEdit` via `modeler/setLayout` that the host applies — there is no separate sidecar file. (The original v1 `<project-root>/.modeler/layout.ttrl` sidecar was removed in v1.1 — see `docs/v1-1/` decision D4.)
- **One LSP across hosts.** Don't add per-host language logic. New language features go in `parser` / `semantics` / `lsp`; hosts stay thin.
- **Parser stays mechanical.** It mirrors ai-platform's Kotlin parser. Don't add resolution logic to `@modeler/parser` — that belongs in `@modeler/semantics`.
- **Project root resolution.** Walk up looking for `modeler.toml`; otherwise treat the LSP `workspaceFolder` as root with convention defaults. Manifest schema is in §5 of the architecture doc. `.modeler/` is a build artifact — never commit it (see `.gitignore`).
- **Source locations on every AST node.** The edit synthesizer relies on file/line/column/offsets being present and accurate for surgical text patches.
- **`SourceLocation` is ANTLR-style.** `line`/`endLine` 1-indexed, `column`/`endColumn` 0-indexed, `offsetStart`/`offsetEnd` 0-indexed with `offsetEnd` exclusive. LSP consumers subtract 1 from line numbers (see `sourceLocationToRange` in `packages/lsp/src/server.ts`). For multi-token AST spans, `endColumn = stopToken.column + stopTokenLength` — **not** `startColumn + spanLength`. The latter formula was shipped once with a relaxed test that hid the bug; re-check `walker.ts`'s `makeSourceLocation` on any future change.
- **`vscode-languageserver` deep import.** Under Node16 module resolution the package's typings only expose the obscure `createConnection(connectionFactory, watchDog, factories?)` overload. Use `import { createConnection, ProposedFeatures } from 'vscode-languageserver/lib/node/main.js'` in source code to get the stream-accepting overloads. Tests can use `'vscode-languageserver/node'` because vitest's resolver is permissive. Reaching for `as any` to silence overload errors is wrong every time.

### Cross-package integration tests

`tests/integration/` is its own workspace member (`@modeler/integration-tests`); it depends on the built packages and runs end-to-end scenarios via Vitest. Run with `pnpm --filter @modeler/integration-tests test`. **Put new LSP feature tests here, not in `packages/lsp/__tests__/`** — the `PassThrough`-paired-connection harness already there is the canonical pattern: boot `createServerConnection(server)`, send `initialize` + `didOpen`, exercise the request, ~10 lines per feature.

### Phase review cadence

The repo uses a `/review`-driven review cycle. Reviews and task lists for v1 live under `docs/v1/implementation/` as numbered artifacts: `review-NNN.md` (prose findings) and `tasks-review-NNN.md` (actionable steps with verification commands). Numbering is serial across the whole project, not per-phase. Phase-progress docs (`docs/v1/plan/progress-phase-NN.md`) record the developer's claims; reviews verify them against runtime. Treat `[x]` marks in progress docs as intent, not truth — verify before agreeing.

## Conventions

- All packages are ESM (`"type": "module"`); use `.js` extensions in relative TS imports as Node16 resolution requires.
- Add a new package under `packages/<name>/`, name it `@modeler/<name>`, extend `tsconfig.base.json`, and wire workspace deps with `workspace:*`. `pnpm-workspace.yaml` already globs `packages/*`.
- Commit style follows `Section <X>: <description>` for phased plan work (see recent history). Don't squash unrelated changes.
- ESLint forbids `any` outside `generated/**` (`packages/parser/src/generated/` is exempt).
