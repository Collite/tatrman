# IntelliJ IDEA Plugin — Contracts

**Status:** Design v1, 2026-06-20. Companion to [`architecture.md`](./architecture.md) and [`../plan/implementation-plan.md`](../plan/implementation-plan.md).

This document specifies every interface the plugin defines or consumes: the LSP4IJ extension points, the Kotlin classes and their signatures, the process command line, the settings state schema, the resource layout, the Gradle task contract, and the exact set of LSP methods relied on. These are **contracts** — task lists implement against them verbatim.

All LSP4IJ APIs below are pinned to the documented surface of `com.redhat.devtools.lsp4ij` (Red Hat). Confirm the exact plugin version's API in Stage 4.A before coding; the shapes here match the current DeveloperGuide.

---

## 1. Module and resource layout

```
intellij-plugin/
  build.gradle.kts
  gradle.properties
  settings.gradle.kts
  src/main/
    kotlin/org/tatrman/modeler/intellij/
      TtrLanguageServerFactory.kt
      TtrStreamConnectionProvider.kt
      NodeResolver.kt
      settings/
        TtrSettings.kt                # PersistentStateComponent
        TtrSettingsConfigurable.kt    # Settings UI
    resources/
      META-INF/plugin.xml
      server/                         # COPIED at build time — gitignored
        server-stdio.mjs              # fully-inlined ESM bundle (see §6)
        stock/*.ttr                   # from packages/semantics/src/stock/
      textmate/                       # COPIED at build time — gitignored
        ttr.tmLanguage.json
        ttrg.tmLanguage.json
      messages/TtrBundle.properties
      icons/ttr.svg, ttrg.svg
  src/test/
    kotlin/org/tatrman/modeler/intellij/
      NodeResolverTest.kt
      TtrStreamConnectionProviderTest.kt
      TtrSettingsTest.kt
      TtrFileMappingTest.kt
```

**Contract:** `resources/server/**` and `resources/textmate/*.tmLanguage.json` are **never committed**. They are produced by the `copyLspBundle` Gradle task (§6), which consumes the fully-inlined server bundle and the generated grammars. `.gitignore` excludes them.

---

## 2. `plugin.xml` extension points

```xml
<idea-plugin>
  <id>org.tatrman.modeler.intellij</id>
  <name>TTR Modeler</name>
  <vendor email="…" url="https://github.com/Collite/modeler">Collite</vendor>

  <depends>com.intellij.modules.platform</depends>
  <depends>com.redhat.devtools.lsp4ij</depends>
  <depends>org.jetbrains.plugins.textmate</depends>

  <extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">
    <!-- Register the language server -->
    <server id="ttrLanguageServer"
            name="TTR Language Server"
            factoryClass="org.tatrman.modeler.intellij.TtrLanguageServerFactory">
      <description><![CDATA[
        Language support for the TTR (Tatrman) modeling language.
      ]]></description>
    </server>

    <!-- Attach the server to .ttr / .ttrg WITHOUT a native IntelliJ Language,
         preserving TextMate coloration. languageId is the LSP-side document
         languageId the server expects. -->
    <fileNamePatternMapping patterns="*.ttr"
                            serverId="ttrLanguageServer"
                            languageId="ttr"/>
    <fileNamePatternMapping patterns="*.ttrg"
                            serverId="ttrLanguageServer"
                            languageId="ttrg"/>
  </extensions>

  <extensions defaultExtensionNs="com.intellij">
    <applicationConfigurable
        parentId="language"
        instance="org.tatrman.modeler.intellij.settings.TtrSettingsConfigurable"
        id="org.tatrman.modeler.intellij.settings"
        displayName="TTR Modeler"/>
    <applicationService
        serviceImplementation="org.tatrman.modeler.intellij.settings.TtrSettings"/>
  </extensions>
</idea-plugin>
```

**Contract notes:**

- `languageId` values `ttr` / `ttrg` MUST match the `languageId` the server keys documents on (the VS Code client uses `ttr` and `ttrg` — see `documentSelector` in `packages/vscode-ext/src/extension.ts`). Keep them identical so the server treats both hosts the same.
- TextMate registration of `ttr.tmLanguage.json` is done via the standard `org.jetbrains.plugins.textmate` bundled-grammar mechanism (a `textmate/bundles` contribution); the exact element is finalized in Stage 4.C against the targeted IDEA version.

---

## 3. `LanguageServerFactory` contract

```kotlin
package org.tatrman.modeler.intellij

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider

class TtrLanguageServerFactory : LanguageServerFactory {
    override fun createConnectionProvider(project: Project): StreamConnectionProvider =
        TtrStreamConnectionProvider(project)
    // No createLanguageClient override: standard client is sufficient (v1).
    // No getServerInterface override: no custom modeler/* requests issued.
}
```

**Contract:** the factory returns a fresh provider per project. It must not throw; Node-absence is handled inside the provider's `start()` path so LSP4IJ can report it through its console rather than crashing factory instantiation.

---

## 4. `StreamConnectionProvider` and command line

```kotlin
package org.tatrman.modeler.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider

class TtrStreamConnectionProvider(private val project: Project)
    : OSProcessStreamConnectionProvider() {

    init {
        val node = NodeResolver.resolve()            // throws NodeNotFoundException if missing
        val server = PluginResources.serverEntry()    // .../resources/server/server-stdio.mjs
        commandLine = GeneralCommandLine(node, server.toString(), "--stdio").apply {
            withWorkDirectory(project.basePath)
        }
    }
}
```

**Command-line contract (exact):**

```
<node>  <plugin-resources>/server/server-stdio.mjs  --stdio
```

- `<node>`: settings override if non-blank, else first `node` resolved on `PATH`.
- Working directory: project base path, so the server's project-root walk-up (to `modeler.toml`) starts from the right place.
- Transport: stdio. **Confirmed in 4.C:** `packages/lsp/src/server-stdio.ts` wires the connection straight to `process.stdin` / `process.stdout` and never reads `process.argv`, so the server defaults to stdio and `--stdio` is a harmless marker. It is **kept** for parity with how the server is invoked elsewhere (and matches the test in `TtrStreamConnectionProviderTest`).

---

## 5. Node resolution contract

```kotlin
object NodeResolver {
    /** @return absolute path to a Node executable.
     *  Order: settings.nodePath (if non-blank & exists) → PATH lookup ("node"/"node.exe").
     *  @throws NodeNotFoundException when none found. */
    fun resolve(): String

    /** @return version string from `node --version`, or null if unparseable. */
    fun detectVersion(node: String): String?
}

class NodeNotFoundException(message: String) : Exception(message)
```

**Contract:**

- Minimum supported Node is **20** (project CLAUDE.md: "Node 20+ required"). `detectVersion` is advisory — a version below 20 produces a warning notification, not a hard failure.
- On `NodeNotFoundException`, the plugin shows a notification (group `TTR Modeler`) with text and a "Configure…" action opening `TtrSettingsConfigurable`. Wording is in `TtrBundle.properties` (key `ttr.node.missing`).

---

## 6. Server bundle source and the `copyLspBundle` Gradle task

### The server bundle must be the *fully-inlined* one

The package-level `pnpm --filter @modeler/lsp build` → `bundle-stdio` script produces `packages/lsp/dist/server-stdio.js` with `@modeler/*`, `antlr4ng`, and `vscode-languageserver` **left external** — it is *not* self-contained and will not run without a `node_modules` tree. **Do not ship that one.**

The self-contained bundle is produced by the root `justfile`'s `_bundle-lsp-server` recipe (the same artefact the packaged `.vsix` ships):

```sh
# from justfile `_bundle-lsp-server` — everything inlined, only `vscode` external
esbuild src/server-stdio.ts \
  --bundle --platform=node --format=esm --target=es2022 \
  --external:vscode \
  --banner:js="import{createRequire as ___cr}from'node:module';const require=___cr(import.meta.url);" \
  --outfile=<out>/server/server-stdio.mjs
cp packages/semantics/src/stock/*.ttr <out>/server/stock/
```

The `createRequire` banner is required (the bundled CJS deps `require()` Node builtins); the `stock/*.ttr` copy is required (the semantics stock-loader's first search path is `<server-dir>/stock/`, and without it every `cnc.role.*` reference resolves as missing).

**Recommended wiring:** add an `intellij` recipe to the root `justfile` (mirroring `vscode`) that runs `_bundle-lsp-server` with `<out>` = `intellij-plugin/src/main/resources`, then `./gradlew :intellij-plugin:buildPlugin`. The Gradle task below only *copies/verifies* and additionally pulls in the two grammars.

```kotlin
// build.gradle.kts (shape)
val copyLspBundle by tasks.registering(Copy::class) {
    val serverBundle = layout.projectDirectory.dir("src/main/resources/server")
    val grammars = rootProject.layout.projectDirectory.dir("packages/vscode-ext/syntaxes")

    from(grammars) {
        include("ttr.tmLanguage.json", "ttrg.tmLanguage.json")
        into("textmate")
    }
    into(layout.projectDirectory.dir("src/main/resources"))

    doFirst {
        require(serverBundle.file("server-stdio.mjs").asFile.exists()) {
            "Inlined LSP server bundle missing. Run `just intellij` (or the " +
            "`_bundle-lsp-server` esbuild recipe) before building the IntelliJ plugin."
        }
    }
}
tasks.named("processResources") { dependsOn(copyLspBundle) }
```

**Contract:**

- Inputs: the **fully-inlined** `server-stdio.mjs` + `stock/*.ttr` (from `_bundle-lsp-server`) and both grammars `ttr.tmLanguage.json` + `ttrg.tmLanguage.json`.
- The task **fails fast** with an actionable message if the inlined server bundle is absent. The plugin build never silently ships without a runnable server.
- CI ordering: `just intellij`'s bundle step (esbuild + stock copy) → `gradle buildPlugin`. Documented in the plan's pre-flight.

---

## 7. Settings state schema

```kotlin
@State(name = "TtrSettings", storages = [Storage("ttr-modeler.xml")])
class TtrSettings : PersistentStateComponent<TtrSettings.State> {
    data class State(
        var nodePath: String = "",        // blank = auto-discover on PATH
        var serverPathOverride: String = "", // blank = bundled server
        var traceLevel: String = "off",   // off | messages | verbose
    )
    // standard getState/loadState
    companion object { fun getInstance(): TtrSettings = service() }
}
```

**Contract:** application-level (not per-project) persistence in `ttr-modeler.xml`. `traceLevel` maps to LSP4IJ's existing trace mechanism; it does not introduce a custom logging channel.

---

## 8. LSP methods relied upon (consumed, not defined)

The plugin defines no LSP methods. It relies on LSP4IJ to surface the standard methods the server already implements (project architecture §4.5). For the record, the surface in scope:

| LSP method | IDE feature it powers |
|---|---|
| `initialize` / `initialized` / `shutdown` / `exit` | Lifecycle (LSP4IJ-managed) |
| `textDocument/didOpen` · `didChange` · `didClose` · `didSave` | Document sync |
| `textDocument/publishDiagnostics` | Inline error/warning highlighting |
| `textDocument/definition` | Go to Declaration |
| `textDocument/references` | Find Usages |
| `textDocument/hover` | Quick Documentation |
| `textDocument/completion` | Code completion |
| `textDocument/rename` · `prepareRename` | Rename refactoring |
| `textDocument/documentSymbol` · `workspace/symbol` | Structure view / symbol search |
| `textDocument/semanticTokens/*` | Semantic coloring (on top of TextMate) |
| `workspace/didChangeWatchedFiles` | External `.ttr` change notifications |

**Explicitly NOT used in this release:** the custom `modeler/getProjectInfo`, `modeler/getModelGraph` / `getGraph`, `modeler/getLayout`, `modeler/setLayout`, `modeler/applyGraphEdit`. These are Designer-only and require no IntelliJ wiring now. If a JCEF Designer is added later, a custom `LanguageServerFactory.getServerInterface()` would expose them — out of scope here.

---

## 9. Versioning and dependency pins

| Dependency | Pin (**resolved in 4.A**) |
|---|---|
| IntelliJ Platform | **IDEA 2024.2** baseline (`sinceBuild` = `242`, no `untilBuild`). Bumped from the proposed 2024.1 because **LSP4IJ requires 2024.2+** (IJ-Q2). |
| IntelliJ Platform Gradle Plugin | **2.16.0** (latest 2.x; requires Gradle 9.0+, so the plugin's standalone wrapper is Gradle **9.5.1**). |
| LSP4IJ | **0.20.1** (`since-build` 242.0; compatible with the 2024.2 baseline). |
| Kotlin (build) | Kotlin Gradle plugin **2.0.21**, `jvmToolchain(17)`; `kotlin.stdlib.default.dependency=false` (platform provides the stdlib). |
| Toolchain resolver | `org.gradle.toolchains.foojay-resolver-convention` **1.0.0** (the Gradle-9-compatible release; earlier versions reference the removed `JvmVendorSpec.IBM_SEMERU`). |
| Node (runtime, external) | 20+ on user's `PATH`. |

Notes from 4.A:
- `instrumentCode = false` — this thin launcher has no GUI forms or `@NotNull` bytecode instrumentation, and disabling it also avoids an `instrumentCode` toolchain-path failure under Gradle 9.
- `verifyPlugin` mutes `TemplateWordInPluginId` (the ID `org.tatrman.modeler.intellij` deliberately names the IntelliJ host variant — IJ-Q3) and passes against IDEs 242 → 262 (Community + Ultimate).

The plugin declares LSP4IJ as a Marketplace dependency (`<depends>`), so installing TTR Modeler prompts the user to install LSP4IJ if absent. This dependency is stated in the Marketplace listing (Stage 4.D).
