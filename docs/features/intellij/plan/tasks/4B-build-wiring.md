# Stage 4.B — Build wiring (LSP bundle + grammars)

Companion to [`../implementation-plan.md`](../implementation-plan.md) Stage 4.B. Goal: the plugin build pulls in the **fully-inlined** LSP server bundle and both TextMate grammars deterministically, failing fast when they're missing. **Check each box the moment it's done.**

Estimate: 1 day. Pre-flight: Stage 4.A done; the inlined `_bundle-lsp-server` recipe and both grammars are producible (global pre-flight in [`index.md`](./index.md)).

> Why this stage is delicate: the package-level `pnpm --filter @modeler/lsp build` → `bundle-stdio` script leaves `@modeler/*` external and is **not** runnable standalone. The shippable bundle is the inlined one from the root `justfile` `_bundle-lsp-server` recipe. See [`contracts.md` §6](../../design/contracts.md). Get this wrong and the plugin ships a server that throws at startup.

---

## Block 1 — Produce the inlined server bundle into plugin resources

- [ ] **1.1 — Add an `intellij` recipe to the root `justfile`.**
  Mirror the existing `vscode` recipe. It must (a) build the LSP deps, (b) run the inlined `_bundle-lsp-server` esbuild with output dir = `intellij-plugin/src/main/resources`, copying `stock/*.ttr` alongside. Concretely, parameterize or duplicate `_bundle-lsp-server` so the output is:
  ```
  intellij-plugin/src/main/resources/server/server-stdio.mjs
  intellij-plugin/src/main/resources/server/stock/*.ttr
  ```
  using the exact esbuild flags from `_bundle-lsp-server` (`--bundle --platform=node --format=esm --target=es2022 --external:vscode --banner:js=<createRequire banner>`) and `cp packages/semantics/src/stock/*.ttr <out>/server/stock/`.
  **Verify:** `just intellij` (the bundle portion) produces `intellij-plugin/src/main/resources/server/server-stdio.mjs` and a non-empty `server/stock/`.

- [ ] **1.2 — Smoke-run the produced bundle standalone.**
  ```bash
  cd intellij-plugin/src/main/resources/server
  node server-stdio.mjs --stdio   # then send an LSP initialize, or pipe a known init frame
  ```
  Confirm it starts without `Cannot find module` errors (proves the bundle is truly self-contained, not the externalized `bundle-stdio` output).
  **Verify:** process starts and responds to `initialize`; no missing-module crash.

## Block 2 — Gradle copy/verify task for grammars

- [ ] **2.1 — Add the `copyLspBundle` Gradle `Copy` task.**
  Per [`contracts.md` §6](../../design/contracts.md): copy both grammars from `packages/vscode-ext/syntaxes/` into `src/main/resources/textmate/`, and assert the inlined server bundle exists:
  ```kotlin
  val copyLspBundle by tasks.registering(Copy::class) {
    val serverBundle = layout.projectDirectory.dir("src/main/resources/server")
    val grammars = rootProject.layout.projectDirectory.dir("packages/vscode-ext/syntaxes")
    from(grammars) { include("ttr.tmLanguage.json", "ttrg.tmLanguage.json"); into("textmate") }
    into(layout.projectDirectory.dir("src/main/resources"))
    doFirst {
      require(serverBundle.file("server-stdio.mjs").asFile.exists()) {
        "Inlined LSP server bundle missing. Run `just intellij` before building the plugin."
      }
    }
  }
  tasks.named("processResources") { dependsOn(copyLspBundle) }
  ```
  **Verify:** `./gradlew :intellij-plugin:processResources` copies both `*.tmLanguage.json` into `src/main/resources/textmate/`.

- [ ] **2.2 — Confirm the fail-fast path.**
  Temporarily delete `src/main/resources/server/server-stdio.mjs`, then run `./gradlew :intellij-plugin:processResources`.
  **Verify:** the build fails with the exact "run `just intellij`" message — not a later, confusing error. Restore the bundle afterward (re-run `just intellij`).

## Block 3 — Package and inspect

- [ ] **3.1 — Build the plugin distribution.**
  Run the full ordering: the `just intellij` bundle step, then `./gradlew :intellij-plugin:buildPlugin`.
  **Verify:** `intellij-plugin/build/distributions/TTR Modeler-0.1.0.zip` (or similar) is produced.

- [ ] **3.2 — Inspect the zip contents.**
  ```bash
  unzip -l "intellij-plugin/build/distributions/"*.zip | grep -E "server-stdio.mjs|stock/|tmLanguage"
  ```
  **Verify:** the archive contains `server/server-stdio.mjs`, `server/stock/*.ttr`, `textmate/ttr.tmLanguage.json`, and `textmate/ttrg.tmLanguage.json` (matches Stage 4.B DONE in [`implementation-plan.md`](../implementation-plan.md)).

- [ ] **3.3 — Document the CI ordering.**
  Add a short note (in `intellij-plugin/README.md` or the CI workflow comments) stating: build order is `just intellij` (LSP deps + inlined bundle + stock copy) → `gradle buildPlugin`. The Gradle build never produces the server itself.
  **Verify:** the note exists and names both steps in order.

---

### Stage 4.B definition of DONE

- [ ] `just intellij`'s bundle step yields a standalone-runnable `server-stdio.mjs` + `stock/` in plugin resources.
- [ ] `copyLspBundle` brings in both grammars and fails fast when the server bundle is absent.
- [ ] The built `.zip` contains server, stock, and both grammars.
- [ ] CI ordering is documented.

When all boxes are checked, tick **Stage 4.B** in [`index.md`](./index.md) and proceed to [`4C-lsp4ij-integration.md`](./4C-lsp4ij-integration.md).
