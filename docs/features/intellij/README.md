# IntelliJ IDEA Plugin (feature docs)

Editor tooling for TTR delivered as a JetBrains plugin, reusing the existing `@modeler/lsp` server via LSP4IJ. This folder holds the design and plan for the `intellij-plugin` feature.

## Scope of this release

- **Host:** IntelliJ IDEA (Community + Ultimate), 2024.x+. Other JetBrains IDEs are a later widening.
- **Surface:** text-editor parity with the VS Code extension (highlighting, diagnostics, navigation, hover, completion, rename). No graphical designer.
- **Highlighting:** reuse the generated TextMate grammar (`ttr.tmLanguage.json`); no native PSI/parser in the plugin.
- **Node runtime:** discovered on the user's `PATH` (override in settings). Per-platform bundling is deferred to the first fast-follow.

The plugin owns no language logic — it is a thin launcher around the shared LSP. The single decision that makes it small: with TextMate + LSP4IJ `fileNamePatternMapping`, no IntelliJ `Language`, `FileType`, `Lexer`, or PSI is needed.

## Documents

| Doc | Contents |
|---|---|
| [`design/architecture.md`](./design/architecture.md) | Goal/non-goals, decisions, component model, build flow, runtime lifecycle, tech stack, open questions |
| [`design/contracts.md`](./design/contracts.md) | Module/resource layout, `plugin.xml` extension points, Kotlin class signatures, command line, Gradle task, settings schema, LSP methods used |
| [`plan/implementation-plan.md`](./plan/implementation-plan.md) | Stages 4.A–4.E with deliverables, pre-flight conditions, definitions of DONE, TDD test plan, risks |
| [`plan/tasks/index.md`](./plan/tasks/index.md) | Executable per-stage task lists (checkboxes + verify commands) — the master tracker links all five stage files |

## Relationship to the rest of the repo

This feature refines **Phase 4** of the project plan (`docs/v1/plan/implementation-plan.md`) and implements **§4.8** of the project architecture (`docs/v1/design/architecture.md`). It consumes — without modifying — the LSP bundle and TextMate grammar already produced by the pnpm build. The plugin lives at `intellij-plugin/` (repo root, its own Gradle build), separate from the pnpm/TypeScript and the `packages/kotlin/*` Gradle domains.

## Next step

Start with [`plan/tasks/4A-gradle-scaffold.md`](./plan/tasks/4A-gradle-scaffold.md) and work the stages in order, ticking boxes in [`plan/tasks/index.md`](./plan/tasks/index.md) as each completes.
