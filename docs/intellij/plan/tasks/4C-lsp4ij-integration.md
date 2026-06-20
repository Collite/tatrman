# Stage 4.C — LSP4IJ integration

Companion to [`../implementation-plan.md`](../implementation-plan.md) Stage 4.C. Goal: opening a `.ttr`/`.ttrg` file starts the server and lights up navigation, diagnostics, hover, completion, and rename, with TextMate coloring preserved. **Check each box the moment it's done.**

Estimate: 2–3 days. Pre-flight: Stage 4.B done; Node 20+ on the dev machine's `PATH`.

**TDD:** Block 1 writes the tests; Blocks 2–4 make them pass. Do not reorder. Tests use Kotest plus the IntelliJ Platform test framework (added in 4.A as `TestFrameworkType.Platform`).

---

## Block 1 — Tests first

- [ ] **1.1 — Write `NodeResolverTest` (Kotest).**
  Cover, per [`contracts.md` §5](../../design/contracts.md): (a) a non-blank, existing settings override wins over `PATH`; (b) when override is blank, a `node` on a stubbed `PATH` is found; (c) when neither exists, `resolve()` throws `NodeNotFoundException`; (d) `detectVersion("…")` parses `v20.11.1` → `20.11.1`; (e) a version `< 20` is flagged. Use injected lookup functions / a fake PATH dir so the test is hermetic.
  **Verify:** `./gradlew :intellij-plugin:test --tests '*NodeResolverTest'` fails (no implementation yet) — red is expected here.

- [ ] **1.2 — Write `TtrStreamConnectionProviderTest` (Kotest).**
  Given a fake Node path and a fake resources root, assert the constructed `GeneralCommandLine` equals exactly `<node> <resources>/server/server-stdio.mjs --stdio` and that the working directory is the project base path. Per [`contracts.md` §4](../../design/contracts.md).
  **Verify:** the test compiles and fails pending implementation.

- [ ] **1.3 — Write `TtrFileMappingTest` (IntelliJ fixture).**
  A light fixture test asserting that `.ttr` and `.ttrg` virtual files resolve to a language-server mapping with `serverId = "ttrLanguageServer"`. (If LSP4IJ's mapping registry is awkward to query in-test, assert instead that `plugin.xml` declares two `fileNamePatternMapping` entries with the right `patterns`/`serverId`/`languageId` by parsing the descriptor resource.)
  **Verify:** the test compiles and fails pending the `plugin.xml` extensions.

## Block 2 — Node resolution + resources

- [ ] **2.1 — Implement `NodeResolver` and `NodeNotFoundException`.**
  Implement per [`contracts.md` §5](../../design/contracts.md): settings override → `PATH` lookup (`node` / `node.exe`) → throw. `detectVersion` runs `node --version` and parses. Min supported version is 20 (CLAUDE.md: "Node 20+ required").
  **Verify:** `./gradlew :intellij-plugin:test --tests '*NodeResolverTest'` is green.

- [ ] **2.2 — Implement `PluginResources.serverEntry()`.**
  Resolve the unpacked plugin resource path to `server/server-stdio.mjs` (use the plugin's class loader / `PluginManager` plugin path, not a hardcoded sandbox path) so it works both in `runIde` and in an installed plugin.
  **Verify:** add a focused test or log the resolved path from `runIde` and confirm it points at the real `server-stdio.mjs`.

## Block 3 — Server factory + provider

- [ ] **3.1 — Implement `TtrStreamConnectionProvider`.**
  Extend `OSProcessStreamConnectionProvider`; build the `GeneralCommandLine` per [`contracts.md` §4](../../design/contracts.md) using `NodeResolver.resolve()` and `PluginResources.serverEntry()`, working dir = `project.basePath`.
  > Confirm the server's stdio argument against `packages/lsp/src/server-stdio.ts`: keep `--stdio` only if the server reads it; drop it if the server defaults to stdio. Adjust the test in 1.2 and the contract if it differs.
  **Verify:** `./gradlew :intellij-plugin:test --tests '*TtrStreamConnectionProviderTest'` is green.

- [ ] **3.2 — Implement `TtrLanguageServerFactory`.**
  Implement `com.redhat.devtools.lsp4ij.LanguageServerFactory`; `createConnectionProvider(project)` returns a new `TtrStreamConnectionProvider(project)`. No `createLanguageClient` / `getServerInterface` overrides (v1), per [`contracts.md` §3](../../design/contracts.md). It must not throw on construction.
  **Verify:** code compiles; `verifyPlugin` still passes.

## Block 4 — Descriptor wiring + highlighting

- [ ] **4.1 — Register the server and file mappings in `plugin.xml`.**
  Add the `com.redhat.devtools.lsp4ij` extensions per [`contracts.md` §2](../../design/contracts.md): one `<server id="ttrLanguageServer" … factoryClass="…TtrLanguageServerFactory">` and two `<fileNamePatternMapping>` (`*.ttr` → `languageId="ttr"`, `*.ttrg` → `languageId="ttrg"`).
  > `fileNamePatternMapping` is deliberately used instead of a custom `FileType` — it is LSP4IJ's documented way to attach a server while **preserving TextMate coloration**; a custom `FileType` would disable it.
  **Verify:** `./gradlew :intellij-plugin:test --tests '*TtrFileMappingTest'` is green; `verifyPlugin` passes.

- [ ] **4.2 — Register the TextMate bundle so `.ttr`/`.ttrg` are colored.**
  Ship the two grammars (copied in 4.B) as a TextMate bundle and register it via the `org.jetbrains.plugins.textmate` bundle mechanism so IntelliJ's TextMate engine colors `.ttr` (scope `source.ttr`) and `.ttrg`. Verify the bundle manifest associates the file extensions with the grammar scopes.
  > The exact registration element for the targeted IDEA version is confirmed here; if a bundle descriptor file is required alongside the `*.tmLanguage.json`, add it under `resources/textmate/`.
  **Verify:** in `runIde`, opening `samples/v1-metadata/*.ttr` shows colored tokens (keywords, strings, comments) identical to VS Code.

- [ ] **4.3 — End-to-end manual check of LSP features in the sandbox.**
  In `runIde`, open `samples/v1-metadata/` and exercise: inline diagnostics, Go to Declaration, Find Usages, Quick Documentation (hover), code completion, and Rename. Open the LSP4IJ console and confirm a clean `initialize` handshake and the expected command line. Open a `.ttrg` file and confirm it is recognized and served.
  **Verify:** all six features work; the LSP console shows no errors; `.ttrg` is served.

---

### Stage 4.C definition of DONE

- [ ] `NodeResolverTest`, `TtrStreamConnectionProviderTest`, `TtrFileMappingTest` all green.
- [ ] Opening a `.ttr`/`.ttrg` file in `runIde` starts the server (clean `initialize` in the LSP console).
- [ ] TextMate coloring matches VS Code; diagnostics, go-to-def, find-usages, hover, completion, rename all work.
- [ ] Server stdio argument confirmed against `server-stdio.ts` (contract updated if changed).

When all boxes are checked, tick **Stage 4.C** in [`index.md`](./index.md) and proceed to [`4D-settings-polish.md`](./4D-settings-polish.md).
