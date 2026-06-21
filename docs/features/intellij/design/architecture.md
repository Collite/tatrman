# IntelliJ IDEA Plugin — Architecture

**Status:** Design v1, 2026-06-20. Feature: `intellij-plugin` (priority-3 deliverable).
**Scope of this release:** text-editor parity with the VS Code extension's Foundation+Core surface, delivered as a JetBrains plugin via LSP4IJ. Targets IntelliJ IDEA (Community + Ultimate) only.

This document is the authoritative design for the IntelliJ plugin feature. Read it alongside the project architecture (`docs/v1/design/architecture.md`, especially §4.8) and the v1.1 contracts (`docs/v1-1/design/v1-1-contracts.md`). The companion artefacts are [`contracts.md`](./contracts.md) (APIs, extension points, schemas) and [`../plan/implementation-plan.md`](../plan/implementation-plan.md) (phased plan).

---

## 1. Goal and non-goals

### Goal

Ship a JetBrains plugin that gives `.ttr` / `.ttrg` files the same editor experience VS Code already has — syntax highlighting, diagnostics, cross-reference resolution, go-to-definition, find-usages, hover, completion, rename — by reusing the existing `@modeler/lsp` server unchanged. The plugin is a **thin host shim**: it owns no language logic. Every language feature comes from the LSP, exactly as in VS Code.

### Non-goals (this release)

| Out of scope | Why / where it lands |
|---|---|
| Graphical designer inside the IDE | Text editing only this release. A JCEF-embedded Designer is a later feature; the custom `modeler/*` methods it needs are not surfaced here. |
| Bundled per-platform Node runtime | Deferred (design open question #4). v1 requires Node on the user's `PATH`. Bundling is a fast-follow. |
| Native PSI / ANTLR parser inside the IDE | Highlighting reuses the generated TextMate grammar. No Kotlin parser is wired into the plugin. The `packages/kotlin/*` artefacts stay ai-platform-facing and are **not** a dependency of this plugin. |
| Other JetBrains IDEs (PyCharm, GoLand, DataGrip, …) | IntelliJ IDEA only for now. The build targets the IDEA product; a platform-wide build is a later widening. |
| Custom `modeler/*` LSP methods | Designer-only; not invoked by the IntelliJ host. |

---

## 2. Decisions

| # | Decision | Choice | Why |
|---|---|---|---|
| IJ1 | LSP integration mechanism | LSP4IJ (`com.redhat.devtools.lsp4ij`), programmatic `LanguageServerFactory` | Surfaces all standard LSP features as native IDE actions with no per-feature code; mature, maintained by Red Hat |
| IJ2 | Server process | `OSProcessStreamConnectionProvider` launching `node <bundled server-stdio.mjs>` over stdio | Same transport VS Code uses (`server-stdio`); no second server build needed |
| IJ3 | Node runtime | Discover `node` on `PATH` (configurable override in settings) | Matches design open-question #4; bundling deferred |
| IJ4 | Syntax highlighting | Reuse generated `ttr.tmLanguage.json` as a bundled TextMate grammar; attach LSP via `fileNamePatternMapping` | `fileNamePatternMapping` is LSP4IJ's documented path for files **without** a native IntelliJ `Language`, and it **preserves TextMate coloration**. No custom PSI/lexer. |
| IJ5 | File-type association | Register `.ttr` and `.ttrg` as TextMate file patterns; no custom `FileType`/`Language` class | Keeps the plugin thin; avoids a parallel language definition that would drift from `TTR.g4` |
| IJ6 | LSP server bundle source | Reuse the self-contained `dist/server/server-stdio.mjs` produced for the VS Code package, copied into plugin resources at build time | Single server artefact across hosts; no IntelliJ-specific server code |
| IJ7 | Build system | Gradle + IntelliJ Platform Gradle Plugin (2.x), Kotlin, target IDEA 2024.x+ | Standard JetBrains plugin toolchain; matches §8.4 (Kotest for JVM tests) |
| IJ8 | Module location | `intellij-plugin/` at repo root (sibling to `packages/`), its own Gradle build | Already specified in project architecture §6; keeps the pnpm and Gradle build domains separate, as the existing `packages/kotlin/*` split does |

The single most important decision is **IJ4**: because we reuse TextMate and attach the LSP by file-name pattern, the plugin needs **no IntelliJ `Language`, `FileType`, `Lexer`, `ParserDefinition`, or PSI** at all. That is what collapses this from "write a language plugin" to "write a launcher."

---

## 3. Where this fits in the system

The IntelliJ plugin is a third host on the existing one-LSP-across-hosts architecture (project architecture §3). It changes nothing in `parser` / `semantics` / `lsp`; it consumes the already-bundled stdio server.

```
            ┌───────────────────────────────────────────┐
            │  @modeler/lsp  (TypeScript, unchanged)     │
            │  server-stdio.ts  ──esbuild──▶             │
            │      dist/server/server-stdio.mjs          │
            │      dist/server/stock/*.ttr               │
            └───────────────────────────────────────────┘
                 ▲ stdio (JSON-RPC)        ▲ stdio
                 │                          │
        ┌────────┴────────┐       ┌─────────┴──────────────┐
        │  VS Code ext    │       │  IntelliJ plugin (new)  │
        │  (Node child)   │       │  LSP4IJ → OSProcess     │
        └─────────────────┘       │  → node server-stdio    │
                                  └─────────────────────────┘
```

The server binary and its stock-vocabulary `.ttr` files are **the same artefact** the VS Code package already builds (project architecture §4.5, and the VS Code `justfile` bundle step). The IntelliJ build copies that artefact into the plugin's resources rather than producing a new one.

---

## 4. Component model

The plugin is small. Five concerns, no business logic:

### 4.1 `plugin.xml` (plugin descriptor)

- Declares a dependency on the LSP4IJ plugin: `<depends>com.redhat.devtools.lsp4ij</depends>` and on `com.intellij.modules.platform`.
- Registers the language server (`com.redhat.devtools.lsp4ij.server` extension) pointing at the factory class.
- Maps `.ttr` and `.ttrg` to the server (`com.redhat.devtools.lsp4ij.fileNamePatternMapping`).
- Registers the settings UI (configurable) and the bundled TextMate grammar.

Exact element shapes are in [`contracts.md` §2](./contracts.md).

### 4.2 `TtrLanguageServerFactory` (Kotlin)

Implements `com.redhat.devtools.lsp4ij.LanguageServerFactory`. Returns a `TtrStreamConnectionProvider` from `createConnectionProvider(project)`. No custom `LanguageClientImpl` or `getServerInterface()` is needed in v1 — the standard client covers all surfaced features, and no custom `modeler/*` requests are issued.

### 4.3 `TtrStreamConnectionProvider` (Kotlin)

Extends `OSProcessStreamConnectionProvider`. Builds a `GeneralCommandLine`:

```
<resolved-node>  <plugin-resources>/server/server-stdio.mjs  --stdio
```

Responsibilities:

1. Resolve the Node executable (settings override → `PATH` lookup → error).
2. Resolve the bundled server path inside the plugin's unpacked resources.
3. Fail loudly and actionably if Node is missing (see §6).

### 4.4 TextMate grammar bundle

The generated grammars `ttr.tmLanguage.json` and `ttrg.tmLanguage.json` (from `packages/vscode-ext/syntaxes/`, themselves generated from `TTR.g4` by `generate-tm-grammar.ts`) are copied into the plugin and registered so IntelliJ's built-in TextMate support colors `.ttr` and `.ttrg` respectively. These are the **same** grammars VS Code uses, so coloring is identical across hosts. `fileNamePatternMapping` (IJ4) is specifically chosen because it leaves this TextMate coloration intact while adding LSP semantics on top.

### 4.5 Settings (`Configurable`)

A single settings page under *Settings → Languages & Frameworks → TTR Modeler* exposing:

- Node executable path (blank = auto-discover on `PATH`).
- LSP server path override (blank = use bundled).
- Trace level for the LSP console (off / messages / verbose), surfaced through LSP4IJ's existing console.

Settings persist per-IDE (application level) via a `PersistentStateComponent`.

---

## 5. Build and packaging flow

Two build domains already coexist in this repo (pnpm/TypeScript and Gradle/Kotlin, see CLAUDE.md). The IntelliJ plugin is a third Gradle build that **consumes an output of the pnpm build**.

```
1. esbuild (justfile `_bundle-lsp-server`)  ──▶  fully-inlined server-stdio.mjs (+ stock/*.ttr)
2. copy  ──▶  server-stdio.mjs + stock/ + ttr.tmLanguage.json + ttrg.tmLanguage.json
             → intellij-plugin/src/main/resources/
3. gradle buildPlugin  ──▶  intellij-plugin/build/distributions/ttr-modeler-<ver>.zip
```

Step 1 must be the **fully-inlined** server bundle — the same artefact the packaged `.vsix` ships (justfile `_bundle-lsp-server`), with all `@modeler/*` + `antlr4ng` + `vscode-languageserver` folded in. The package-level `bundle-stdio` script leaves those external and is *not* runnable standalone — see [`contracts.md` §6](./contracts.md). Step 2's Gradle task (`copyLspBundle`) fails fast if the inlined bundle is absent; CI runs the bundle step first, then Gradle. The grammar JSONs (`ttr` and `ttrg`) and the server bundle are **build inputs**, never committed into `intellij-plugin/` (generated artefacts, consistent with the repo's gitignore policy for generated parser/grammar output).

Distribution: JetBrains Marketplace + a `.zip` attached to GitHub Releases (project architecture §8.2). Signing/publishing uses the IntelliJ Platform Gradle Plugin's `signPlugin` / `publishPlugin` tasks; this is wired in the global Phase 5 packaging work, not here.

---

## 6. Runtime: process lifecycle and the Node dependency

### Startup

When the user opens a `.ttr`/`.ttrg` file, LSP4IJ matches it via `fileNamePatternMapping`, calls the factory, and starts the process. The provider launches `node server-stdio.mjs --stdio`; the server runs its normal stdio initialization (project-root resolution by walking up to `modeler.toml`, stock-vocab load, project file scan — all already implemented in `server-stdio.ts`). LSP4IJ wires diagnostics, navigation, hover, completion, and rename to native IDE actions automatically.

### The Node dependency (the one real risk)

v1 does **not** bundle Node (decision IJ3). If `node` is not on `PATH` and no override is set, the provider must not fail silently:

- Detect absence before spawning.
- Surface a notification: "TTR Modeler needs Node.js 20+ on your PATH, or set the Node path in Settings → Languages & Frameworks → TTR Modeler." with an action that opens the settings page.
- The LSP console shows the resolved command line for diagnosis.

This is the principal UX wrinkle and the reason bundling is the first fast-follow after this release.

### Shutdown / restart

LSP4IJ owns lifecycle: it stops the process on project close and offers a restart action in the LSP console. The plugin adds nothing here.

---

## 7. Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin (JVM 17, IDEA 2024.x baseline) |
| Build | Gradle 8.x + IntelliJ Platform Gradle Plugin 2.x |
| LSP client | LSP4IJ `com.redhat.devtools.lsp4ij` (Marketplace dependency) |
| Highlighting | IntelliJ bundled TextMate + generated `ttr.tmLanguage.json` |
| Server | Reused `@modeler/lsp` `server-stdio.mjs` (Node 20+) |
| Tests | Kotest (unit) + IntelliJ plugin test fixtures (integration/smoke) |
| Target IDEs | IntelliJ IDEA Community + Ultimate, 2024.x+ |

---

## 8. Testing strategy (summary)

Aligned with project architecture §8.4 (Kotest for the JVM side):

- **Unit (Kotest):** Node resolution logic, command-line construction, settings persistence, missing-Node error path. These are pure-logic units with no IDE runtime.
- **Component (IntelliJ test fixture):** factory wiring — given a `.ttr` file, the correct server descriptor is selected; `fileNamePatternMapping` resolves `.ttr` and `.ttrg`.
- **Smoke (manual + CI install):** install the built `.zip` into a clean IDEA (Community and Ultimate), open `samples/v1-metadata/`, and confirm the acceptance criteria in the plan: identical highlighting, navigation, hover, and find-references to VS Code.

Full E2E across hosts is the global Phase 5 concern (same `samples/`-based smoke tests run for every host), not part of this feature's own test suite.

TDD ordering and the concrete test list are specified per stage in [`../plan/implementation-plan.md`](../plan/implementation-plan.md).

---

## 9. Open questions

| # | Question | Disposition |
|---|---|---|
| IJ-Q1 | Bundle Node per-platform | Deferred to fast-follow; v1 = PATH + override. Tracked from design open-question #4. |
| IJ-Q2 | Minimum IDEA version | **Resolved (4.A): IDEA 2024.2** (`sinceBuild` 242). LSP4IJ requires 2024.2+, so the proposed 2024.1 is not viable. LSP4IJ pinned to 0.20.1; IntelliJ Platform Gradle Plugin 2.16.0 (Gradle 9.5.1). See [`contracts.md` §9](./contracts.md). |
| IJ-Q3 | Marketplace identity | Publisher `collite` (matches the VS Code `publisher`); plugin ID `org.tatrman.modeler.intellij` to confirm. Settle in Stage 4.D. |
| IJ-Q4 | Whether to also register a tiny native `FileType` for the editor icon | Optional cosmetic; default is TextMate-only. Revisit in polish if the file icon is missing. |
