# TTR Modeler — IntelliJ IDEA plugin

A thin JetBrains host shim that gives `.ttrm` / `.ttrg` files the same editor
experience as the VS Code extension — highlighting, diagnostics, navigation,
hover, completion, rename — by launching the shared `@modeler/lsp` server over
stdio via [LSP4IJ](https://github.com/redhat-developer/lsp4ij). The plugin owns
no language logic; every language feature comes from the LSP.

Design, contracts, and the staged plan live under
[`docs/features/intellij/`](../docs/features/intellij/README.md).

## Build

This is a **standalone Gradle build** (its own wrapper), separate from the repo's
pnpm/TypeScript and `packages/kotlin/*` Gradle domains. It targets IntelliJ IDEA
2024.2+ (`sinceBuild` 242) with the IntelliJ Platform Gradle Plugin 2.16.0 and
pins LSP4IJ 0.20.1.

### Build order matters

The plugin ships the **fully-inlined** LSP server bundle (`server-stdio.mjs` +
`stock/*.ttrm`) and the two generated TextMate grammars, all **unpacked** in the
plugin home — `node` runs the `.mjs` from disk and IntelliJ's TextMate engine
reads the grammars from a directory. The server bundle is produced by esbuild
(not Gradle), so the build is two ordered steps:

```
1. just intellij     # builds @modeler/lsp, esbuilds the inlined server-stdio.mjs
                     #   + stock/*.ttrm into src/main/resources/server/, then runs
                     #   ./gradlew buildPlugin
   └─ (gradle) copyLspBundle pulls in ttr/ttrg.tmLanguage.json and FAILS FAST
      if src/main/resources/server/server-stdio.mjs is absent.
```

The `just intellij` recipe (root `justfile`) wraps both steps. In CI, run the
esbuild bundle step **before** Gradle — `gradle buildPlugin` never produces the
server itself and the `copyLspBundle` task aborts with an actionable message if
the inlined bundle is missing.

Manual equivalent:

```sh
pnpm --filter @modeler/lsp... build
just _bundle-lsp-server intellij-plugin/src/main/resources/server
cd intellij-plugin && ./gradlew buildPlugin
```

Output: `intellij-plugin/build/distributions/intellij-plugin-<version>.zip`.

### Common tasks

| Command (from repo root) | Purpose |
|---|---|
| `just intellij` | Full build: inlined server bundle + `buildPlugin` (the canonical path). |
| `./intellij-plugin/gradlew --project-dir intellij-plugin runIde` | Launch a sandbox IDEA with the plugin + LSP4IJ (requires a desktop). |
| `./intellij-plugin/gradlew --project-dir intellij-plugin verifyPlugin` | Plugin Verifier against the supported IDE range. |
| `./intellij-plugin/gradlew --project-dir intellij-plugin test` | Run the Kotest / platform-fixture tests. |

The `server/` and `textmate/` resource subtrees are build-time artefacts
(produced by the steps above) and are **gitignored** — never committed.
