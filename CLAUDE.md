# CLAUDE.md

> **Task tracking moved 2026-07-14 · design docs follow 2026-07-15.** Task lists, phase plans, STATUS.md, phase-exit reviews, and control-room decision logs for this repo live in `collite-gh/project/tatrman/` — `project/` is the source-of-truth home for *everything* about an effort. This repo keeps **copies** of pre-2026-07-15 architecture/design docs, manuals, implementation notes, and examples close to the code (read here, edit in `project/`). **New design and planning efforts (from 2026-07-15 on) are authored directly and only in `project/tatrman/features/...`** — this repo gets a pointer README where the folder would have been (first instance: `docs/features/ttr-p/design/rejects/`). See `project/README.md` for the full split and `project/SOURCES.md` for exactly what moved from where. Anything removed from here sits in this repo's `_to_delete/` pending your final cleanup, not deleted outright.


> **Docs layout (2026-07-13):** all feature/effort docs live under `docs/features/<effort>/`; every live effort keeps a `STATUS.md` there (state · phase · next · blocked_on · updated) — status lives in STATUS.md and nowhere else. Cross-repo register: `project/REGISTER.md` (was design repo `ecosystem/REGISTER.md`, moved 2026-07-14).


This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Tatrman** ("table transformation manager") — the TTR language family. Two languages live here: **TTR-M** (modeling; formerly "the TTR modeling language") and **TTR-P** (processing; in design under `docs/features/ttr-p/` — for anything TTR-P, read `project/tatrman/features/ttr-p/design/00-control-room.md` first; control rooms live in `project/`, not here). This repo was **forked from `Collite/modeler` on 2026-07-03** (full history; modeler is frozen/maintenance-only). TTR-M side: a VS Code plugin, a static React graphical designer, and (later) an IntelliJ plugin, all sharing one TypeScript LSP server. TTR is consumed at runtime by `ai-platform`/`kantheon` (separate repos); this repo is editor/compiler tooling only and never talks to those services.

Naming canon (H session, 2026-07-03): **Tatrman** = the product/repo · **TTR** = the family · **TTR-M / TTR-P / TTR-B** = the languages · TTR-SQL / TTR-pandas = TTR-P fragment dialects. Extensions: `.ttrm`/`.ttrg` (TTR-M), `.ttrp` (TTR-P), `.ttr.sql`/`.ttr.py` (fragments), `.ttrl` (family-wide per-document view-state sidecar — TTR-M migrates off the v1.1 in-file layout block; amendment recorded in `docs/features/v1-1/design/v1.1-packages-and-graphs.md` §15 + contracts changelog v8; migration itself pending, gated on the TTR-P C1 session's `.ttrl` content schema).

Authoritative design and decisions for v1 live in `docs/features/v1/design/architecture.md` — read it before making non-trivial architectural changes. The v1 phased plan moved to `project/tatrman/features/v1/plan/implementation-plan.md` (see the banner above). The v1.1 design (packages, imports, `.ttrg`) lives under `docs/features/v1-1/`.

## Commands

Workspace uses pnpm 11 (see `packageManager`). Node 20+ required.

| Command | Purpose |
|---|---|
| `pnpm install` | Install all workspace deps |
| `pnpm -r build` | Build every package (`tsc`, plus `esbuild` bundles for `@tatrman/lsp`) |
| `pnpm -r test` | Run all Vitest suites across packages |
| `pnpm -r typecheck` | Type-check without emitting |
| `pnpm -r lint` | Lint all packages |
| `pnpm --filter @tatrman/<pkg> test` | Run one package's tests |
| `pnpm --filter @tatrman/<pkg> test -- <pattern>` | Run a single test file/name |
| `pnpm --filter @tatrman/designer dev` | Run the Designer dev server (Vite, http://localhost:5173) |

### Grammar regeneration

`packages/grammar/src/TTR.g4` is the canonical grammar. After editing it:

1. `cd packages/parser && pnpm run prebuild` — regenerates `packages/parser/src/generated/*` via `antlr-ng` (script: `packages/grammar/scripts/generate-typescript-parser.sh`). The `prebuild` hook runs automatically before `pnpm --filter @tatrman/parser build`.
2. `cd packages/vscode-ext && node scripts/generate-tm-grammar.ts` — regenerates the TextMate grammar used by the VS Code extension for syntax highlighting.
3. Commit the grammar change. `packages/grammar/src/generated/` and `packages/parser/src/generated/` are **gitignored** — they are regenerated at build time from `TTR.g4`. Only `TTR.g4`, the generation scripts, and `packages/vscode-ext/syntaxes/ttrm.tmLanguage.json` are committed.

`TTR.g4` is **not vendored anywhere**. It is the single canonical source for all three generated parsers (TS via `antlr-ng`, Kotlin via the ANTLR Gradle plugin, Python via the reference ANTLR jar — all reading this `.g4` directly). Downstream consumers like `ai-platform` consume the **published artifacts** (`org.tatrman:ttr-parser` on Maven, the `ttr-parser` wheel on PyPI), never a copy of the grammar. Cross-target drift is caught by the conformance harness (`conformance.yml`), not by grammar sync. The full procedure for cutting a new grammar version lives in [`docs/features/grammar-master/new-grammar-version-process.md`](docs/features/grammar-master/new-grammar-version-process.md).

### Testing the VS Code extension

Open `packages/vscode-ext` in VS Code and press F5 to launch an Extension Development Host, then open any `.ttrm` file.

### Kotlin artifacts (Gradle build)

Two build domains coexist in this repo and share **only** `packages/grammar/src/TTR.g4`:

- **pnpm / TypeScript** (`packages/*`, commands above) — LSP, VS Code ext, Designer.
- **Gradle / Kotlin** (`packages/kotlin/*`) — the published Maven artifacts
  `org.tatrman:ttr-parser` and `:ttr-writer` (and, from Phase 2, `:ttr-semantics`),
  consumed by the `ai-platform` repo. The Kotlin parser reads `TTR.g4` directly
  (no sync/copy) and is kept byte-for-byte conformant with the TS parser by the
  conformance harness (`conformance.yml`). The **ttr-translator extraction arc**
  (`docs/features/ttr-translator/`) adds two more published Kotlin artifacts —
  `org.tatrman:ttr-plan-proto` (the `plan.v1`/`transdsl.v1`/`dfdsl.v1` wire formats
  + `.proto` jar resources) and `org.tatrman:ttr-translator` (the Calcite-backed
  translation core) — released **lockstep** under the `kotlin-translator/v*` tag
  (plus a `ttr-plan-proto` PyPI wheel via `python-plan/v*`). Extracted from kantheon;
  tatrman is now the canonical owner of the plan wire format (decision TR-3 / S25).

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
the migration plan and contracts are in [`docs/features/grammar-master/`](docs/features/grammar-master/).

## Architecture

This is a pnpm workspaces monorepo. All TS packages extend `tsconfig.base.json` (strict, ES2022, Node16 modules, ESM). Source goes in `src/`, output in `dist/`. Tests live in `src/__tests__/*.test.ts` and use Vitest.

### Dependency graph (one-way)

```
grammar  →  parser  →  semantics  →  lsp  →  vscode-ext
                          ↑           ↑   ↘
              md-catalog ─┘           edit   designer
```

- **`@tatrman/grammar`** — owns `TTR.g4` and the generation/sync scripts. No runtime logic.
- **`@tatrman/md-catalog`** — data-only leaf (beside `grammar`): the built-in MD calc-map catalog (`MD_CALC_CATALOG`) + `MD_CATALOG_VERSION` (the cross-repo sync key). No runtime logic; `semantics` depends on it and pre-loads the catalog as a read-only `calc:` source. Vendored to ai-platform (Phase 4).
- **`@tatrman/parser`** — wraps the generated antlr4ng parser; exposes `parseString` / `parseFile` returning `{ ast, errors, source }`. Cross-references are kept as opaque strings here (resolved later). Comments route to the lexer's hidden channel (`TTR.g4`: `LINE_COMMENT`/`BLOCK_COMMENT -> channel(HIDDEN)`; `WS` stays `skip`) and are attached to AST nodes as `Trivia` (`leadingTrivia`/`trailingTrivia`, see `src/cst/{trivia,attach}.ts`) by `attachTrivia` during the walk. This is the lossless CST/trivia layer that the formatter, linter suppression, and (P4) trivia-preserving autofix read from; the edit synthesizer will preserve trivia once autofix lands.
- **`@tatrman/semantics`** — symbol table + reference resolver + per-kind validator. Pre-loads ai-platform's stock CNC vocab (`fact`, `dimension`, `structural`, `master`, `transaction`, `bridge`). This is where "unresolved reference"-class diagnostics come from.
- **`@tatrman/edit`** — `WorkspaceEdit` synthesizer for structured graph operations from the Designer (placeholder until v1.1 when edit mode lands).
- **`@tatrman/lsp`** — the single LSP server consumed by all hosts. Two entry points bundled with esbuild:
  - `server-stdio.ts` → Node child process for VS Code / IntelliJ
  - `server-browser.ts` → Web Worker for the Designer
  Implements standard LSP methods plus custom `modeler/*` methods (`getModelGraph`, `applyGraphEdit`, `getLayout`/`setLayout`, `getProjectInfo`) for Designer use.
- **`ttr-modeler-vsc`** — thin shim: language registration, TextMate grammar, LSP client wiring, one stub command. No business logic here — anything understanding TTR belongs in the LSP.
- **`@tatrman/designer`** — React 19 + Vite + Cytoscape.js + Tailwind. Forked from the Ontology Playground project. v1 is read-only render of `db` / `er` schemas; edit mode (round-tripping through `modeler/applyGraphEdit`) lands in v1.1.

### Key invariants

- **Text is canonical.** The Designer never owns model state independently — it issues structured edits via custom LSP requests, the LSP synthesizes `WorkspaceEdit`s, the host applies them, and the LSP re-parses. Node positions live inside each `.ttrg` file's `layout` block (v1.1; see contracts §7.1): the LSP reads them via `modeler/getLayout` and writes them by synthesizing a `WorkspaceEdit` via `modeler/setLayout` that the host applies — there is no separate sidecar file. (The original v1 `<project-root>/.modeler/layout.ttrl` sidecar was removed in v1.1 — see `docs/features/v1-1/` decision D4.)
- **One LSP across hosts.** Don't add per-host language logic. New language features go in `parser` / `semantics` / `lsp`; hosts stay thin.
- **Parser stays mechanical.** It mirrors ai-platform's Kotlin parser. Don't add resolution logic to `@tatrman/parser` — that belongs in `@tatrman/semantics`.
- **Project root resolution.** Walk up looking for `modeler.toml`; otherwise treat the LSP `workspaceFolder` as root with convention defaults. Manifest schema is in §5 of the architecture doc. `.modeler/` is a build artifact — never commit it (see `.gitignore`).
- **Source locations on every AST node.** The edit synthesizer relies on file/line/column/offsets being present and accurate for surgical text patches.
- **`SourceLocation` is ANTLR-style.** `line`/`endLine` 1-indexed, `column`/`endColumn` 0-indexed, `offsetStart`/`offsetEnd` 0-indexed with `offsetEnd` exclusive. LSP consumers subtract 1 from line numbers (see `sourceLocationToRange` in `packages/lsp/src/server.ts`). For multi-token AST spans, `endColumn = stopToken.column + stopTokenLength` — **not** `startColumn + spanLength`. The latter formula was shipped once with a relaxed test that hid the bug; re-check `walker.ts`'s `makeSourceLocation` on any future change.
- **`vscode-languageserver` deep import.** Under Node16 module resolution the package's typings only expose the obscure `createConnection(connectionFactory, watchDog, factories?)` overload. Use `import { createConnection, ProposedFeatures } from 'vscode-languageserver/lib/node/main.js'` in source code to get the stream-accepting overloads. Tests can use `'vscode-languageserver/node'` because vitest's resolver is permissive. Reaching for `as any` to silence overload errors is wrong every time.

### Cross-package integration tests

`tests/integration/` is its own workspace member (`@tatrman/integration-tests`); it depends on the built packages and runs end-to-end scenarios via Vitest. Run with `pnpm --filter @tatrman/integration-tests test`. **Put new LSP feature tests here, not in `packages/lsp/__tests__/`** — the `PassThrough`-paired-connection harness already there is the canonical pattern: boot `createServerConnection(server)`, send `initialize` + `didOpen`, exercise the request, ~10 lines per feature.

### Phase review cadence

The repo uses a `/review`-driven review cycle. Reviews and task lists for v1 live under `docs/features/v1/implementation/` as numbered artifacts: `review-NNN.md` (prose findings) and `tasks-review-NNN.md` (actionable steps with verification commands). Numbering is serial across the whole project, not per-phase. Phase-progress docs (`docs/features/v1/plan/progress-phase-NN.md`) record the developer's claims; reviews verify them against runtime. Treat `[x]` marks in progress docs as intent, not truth — verify before agreeing.

## Conventions

- All packages are ESM (`"type": "module"`); use `.js` extensions in relative TS imports as Node16 resolution requires.
- Add a new package under `packages/<name>/`, name it `@tatrman/<name>`, extend `tsconfig.base.json`, and wire workspace deps with `workspace:*`. `pnpm-workspace.yaml` already globs `packages/*`. (The npm scope was renamed `@modeler/*` → `@tatrman/*` in TTR-P Phase 0 / S7; the VS Code extension keeps its marketplace id `ttr-modeler-vsc`.)
- Commit style follows `Section <X>: <description>` for phased plan work (see recent history). Don't squash unrelated changes.
- ESLint forbids `any` outside `generated/**` (`packages/parser/src/generated/` is exempt).
