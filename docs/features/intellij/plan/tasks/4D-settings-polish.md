# Stage 4.D — Settings, Node UX, polish

Companion to [`../implementation-plan.md`](../implementation-plan.md) Stage 4.D. Goal: graceful behavior when Node is absent, a settings page, and Marketplace-ready metadata. **Check each box the moment it's done.**

Estimate: 1–2 days. Pre-flight: Stage 4.C done.

**TDD:** Block 1 writes the settings test first; Block 2 implements it. Blocks 3–4 are UX/metadata with manual verification.

---

## Block 1 — Settings test first

- [ ] **1.1 — Write `TtrSettingsTest` (IntelliJ fixture/Kotest).**
  Assert per [`contracts.md` §7](../../design/contracts.md): `State` defaults are `nodePath=""`, `serverPathOverride=""`, `traceLevel="off"`; values round-trip through `getState()`/`loadState()`.
  **Verify:** `./gradlew :intellij-plugin:test --tests '*TtrSettingsTest'` fails pending implementation.

## Block 2 — Settings implementation

- [ ] **2.1 — Implement `TtrSettings` (`PersistentStateComponent`).**
  Application-level service with `@State(name="TtrSettings", storages=[Storage("ttr-modeler.xml")])` and the `State` data class from [`contracts.md` §7](../../design/contracts.md). Register the `applicationService` in `plugin.xml` (com.intellij ns).
  **Verify:** `./gradlew :intellij-plugin:test --tests '*TtrSettingsTest'` is green.

- [ ] **2.2 — Implement `TtrSettingsConfigurable` (settings UI).**
  An `applicationConfigurable` under *Settings → Languages & Frameworks → TTR Modeler* (parentId `language`) with three fields: Node executable path, LSP server path override, trace level (off/messages/verbose). Wire apply/reset to `TtrSettings`. Register in `plugin.xml` per [`contracts.md` §2](../../design/contracts.md).
  **Verify:** in `runIde`, the settings page appears, edits persist after Apply, and reopening *Settings* shows the saved values.

- [ ] **2.3 — Make `NodeResolver` and provider read the settings.**
  `NodeResolver.resolve()` reads `TtrSettings.getInstance().state.nodePath`; `PluginResources`/provider honor `serverPathOverride` when non-blank. `traceLevel` maps to LSP4IJ's existing trace mechanism (no custom logging channel).
  **Verify:** set a custom Node path in settings, restart the LSP from the LSP4IJ console, and confirm the console command line uses the overridden Node.

## Block 3 — Missing-Node UX

- [ ] **3.1 — Add the notification group and message bundle.**
  Register a `notificationGroup` (id `TTR Modeler`) in `plugin.xml`. Add `messages/TtrBundle.properties` with key `ttr.node.missing` = an actionable message ("TTR Modeler needs Node.js 20+ on your PATH, or set the Node path in Settings → Languages & Frameworks → TTR Modeler.").
  **Verify:** the bundle loads; key resolves.

- [ ] **3.2 — Surface the notification with a "Configure…" action.**
  When `NodeResolver.resolve()` throws `NodeNotFoundException` (caught at server-start), show a balloon from the `TTR Modeler` group using `ttr.node.missing`, with an action that opens `TtrSettingsConfigurable`. Also emit a warning (not failure) when `detectVersion` reports `< 20`.
  **Verify:** remove `node` from `PATH`, clear the override, open a `.ttr` file → the balloon appears (no stack trace); clicking "Configure…" opens the settings page; setting a valid path and reopening starts the server.

## Block 4 — Metadata and polish

- [ ] **4.1 — Fill plugin metadata.**
  Add a `<description>` (HTML) and `<change-notes>` in `plugin.xml` (or via `pluginConfiguration`), set the Marketplace category to "Programming Languages", and state the LSP4IJ dependency in the description. Resolve IJ-Q3 (final plugin id / publisher) and record it.
  **Verify:** `./gradlew :intellij-plugin:verifyPlugin` passes with no missing-metadata warnings.

- [ ] **4.2 — Add the plugin/file icons.**
  Add `pluginIcon.svg` (plugin logo) and reuse `ttr.svg` / `ttrg.svg` from `packages/vscode-ext/icons/` for the file icons if a native file-icon provider is added (optional — IJ-Q4). Keep it cosmetic; do not add a custom `FileType` that would disable TextMate coloring.
  **Verify:** the plugin icon renders in *Settings → Plugins*.

---

### Stage 4.D definition of DONE

- [ ] `TtrSettingsTest` green; settings persist across IDE restarts.
- [ ] With Node absent and no override, opening a `.ttr` file shows the actionable notification (not a stack trace); configuring a path and reopening starts the server.
- [ ] `verifyPlugin` passes with complete metadata; plugin icon renders.
- [ ] IJ-Q3 resolved and recorded in [`contracts.md` §9](../../design/contracts.md).

When all boxes are checked, tick **Stage 4.D** in [`index.md`](./index.md) and proceed to [`4E-smoke-verification.md`](./4E-smoke-verification.md).
