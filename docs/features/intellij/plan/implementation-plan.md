# IntelliJ IDEA Plugin — Implementation Plan

**Status:** Plan v1, 2026-06-20. Feature: `intellij-plugin`.
**Design authority:** [`../design/architecture.md`](../design/architecture.md) · [`../design/contracts.md`](../design/contracts.md).

This plan refines the project-wide Phase 4 (project plan `docs/v1/plan/implementation-plan.md` §"Phase 4 — IntelliJ plugin v1") into a self-contained, stage-by-stage plan with deliverables, pre-flight conditions, and definitions of DONE. Estimate: **1–2 weeks**, one developer. The executable per-stage checklists are in [`tasks/`](./tasks/index.md). The work is sequential within itself but depends on the global Phase 2 (Core-tier LSP) being shippable.

---

## Overall shape

Five stages, executed in order:

| Stage | Goal | Est. | Depends on |
|---|---|---|---|
| 4.A | Gradle scaffold + plugin skeleton that loads in a sandbox IDE | 1–2 days | Phase 2 LSP buildable |
| 4.B | Build wiring: copy the LSP bundle + TextMate grammar into plugin resources | 1 day | 4.A |
| 4.C | LSP4IJ integration: server factory, process provider, file mapping, highlighting | 2–3 days | 4.B |
| 4.D | Settings, Node-missing UX, polish, metadata | 1–2 days | 4.C |
| 4.E | Smoke verification on Community + Ultimate against `samples/` | 1 day | 4.D |

> Note vs. the project plan: the original Phase 4 listed "4.B — Bundled runtime (per-platform Node)". Per this feature's scope decision, **Node bundling is deferred** (v1 = Node on `PATH`). The freed time goes to build wiring (new 4.B) and the Node-absence UX (4.D). Bundling becomes the first fast-follow.

---

## Pre-flight conditions (whole feature)

Before Stage 4.C can produce anything testable, all must hold:

1. **Phase 2 LSP is buildable and bundleable.** The root `justfile` `_bundle-lsp-server` esbuild recipe produces a **fully-inlined** `server-stdio.mjs` (only `vscode` external) plus `stock/*.ttr` copied from `packages/semantics/src/stock/`. The package-level `bundle-stdio` script is *not* sufficient (it externalizes `@modeler/*`). Verify by launching the inlined bundle against `samples/v1-metadata/` from a plain `node` invocation and confirming it answers `initialize`.
2. **Both grammars exist** at `packages/vscode-ext/syntaxes/ttr.tmLanguage.json` and `ttrg.tmLanguage.json` (regenerated from `TTR.g4`).
3. **JDK 17 + Gradle** available; network access to the JetBrains plugin repository and Marketplace for the IntelliJ Platform Gradle Plugin and LSP4IJ.
4. **`samples/v1-metadata/`** is the agreed acceptance fixture (same one VS Code uses).

---

## Stage 4.A — Gradle scaffold

**Goal:** an empty-but-valid plugin that launches in a sandbox IDE via `runIde`.

**Deliverables**

- `intellij-plugin/` Gradle project (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`) using the IntelliJ Platform Gradle Plugin 2.x, targeting IDEA 2024.1 (`sinceBuild` 241).
- `META-INF/plugin.xml` with id `org.tatrman.modeler.intellij`, vendor `collite`, `<depends>` on platform, LSP4IJ, and TextMate (no extensions wired yet).
- LSP4IJ added as a plugin dependency in the Gradle `intellijPlatform { plugins(...) }` block.
- Resolve open questions IJ-Q2 (min IDEA version) and the LSP4IJ version pin; record them in `contracts.md` §9.

**Pre-flight:** JDK 17, Gradle, network to JetBrains repos.

**Definition of DONE**

- `./gradlew :intellij-plugin:runIde` launches a sandbox IDEA with the plugin installed and LSP4IJ present, no errors in the log.
- `./gradlew :intellij-plugin:verifyPlugin` passes structure verification.
- The repo's root `.gitignore` excludes `intellij-plugin/src/main/resources/server/**` and `…/textmate/ttr.tmLanguage.json`.

---

## Stage 4.B — Build wiring (LSP bundle + grammar)

**Goal:** the plugin build pulls in the server and grammar artefacts deterministically.

**Deliverables**

- A root `justfile` `intellij` recipe (mirroring `vscode`) that runs the inlined `_bundle-lsp-server` esbuild with output into `intellij-plugin/src/main/resources`, then `gradle buildPlugin`.
- `copyLspBundle` Gradle task per [`contracts.md` §6](../design/contracts.md): pulls both grammars (`ttr` + `ttrg`) → `resources/textmate/`, wired as a `processResources` dependency, failing fast with an actionable message if the inlined server bundle is absent.
- CI note / script documenting the ordering: `just intellij` bundle step → `gradle buildPlugin`.

**Pre-flight:** Stage 4.A done; pre-flight condition #1 and #2 (the inlined bundle and both grammars exist).

**Definition of DONE**

- After the inlined bundle step then `./gradlew :intellij-plugin:buildPlugin`, the produced `.zip` contains `server/server-stdio.mjs`, `server/stock/*.ttr`, `textmate/ttr.tmLanguage.json`, and `textmate/ttrg.tmLanguage.json`.
- Removing the inlined server bundle and rebuilding fails with the documented "run `just intellij` first" message — not a malformed plugin.

---

## Stage 4.C — LSP4IJ integration

**Goal:** opening a `.ttr`/`.ttrg` file starts the server and lights up navigation, diagnostics, hover, completion, rename, with TextMate coloring.

**Deliverables**

- `TtrLanguageServerFactory`, `TtrStreamConnectionProvider`, `NodeResolver`, `PluginResources` per [`contracts.md` §3–§5](../design/contracts.md).
- `plugin.xml` `server` + two `fileNamePatternMapping` (`*.ttr` → `ttr`, `*.ttrg` → `ttrg`) per [`contracts.md` §2](../design/contracts.md).
- TextMate grammar registration so `.ttr`/`.ttrg` are colored.
- Confirm the server's stdio invocation argument (`--stdio` vs default) against `server-stdio.ts`.

**Pre-flight:** Stage 4.B done; Node 20+ on the build machine's `PATH` for manual verification.

**Definition of DONE**

- In `runIde`, opening `samples/v1-metadata/*.ttr`: tokens are colored (TextMate), diagnostics appear inline, Go to Declaration / Find Usages / Quick Documentation / completion / rename all work.
- The LSP4IJ console shows the server started with the expected command line and a clean `initialize` handshake.
- `.ttrg` files are recognized and served.

---

## Stage 4.D — Settings, Node UX, polish

**Goal:** graceful behavior when Node is absent, configurability, and Marketplace-ready metadata.

**Deliverables**

- `TtrSettings` (`PersistentStateComponent`) + `TtrSettingsConfigurable` page (Node path, server override, trace level) per [`contracts.md` §7](../design/contracts.md).
- Node-missing path: detection, notification with a "Configure…" action opening settings, message in `TtrBundle.properties` (`ttr.node.missing`); sub-20 version warning.
- Plugin metadata: description, change-notes, icon (`ttr.svg`), Marketplace category "Programming Languages", LSP4IJ dependency stated in the listing. Resolve IJ-Q3 (plugin ID / publisher).

**Pre-flight:** Stage 4.C done.

**Definition of DONE**

- With `node` removed from `PATH` and no override, opening a `.ttr` file shows the actionable notification (not a stack trace); setting the path in Settings and reopening starts the server.
- Settings persist across IDE restarts.
- `verifyPlugin` passes with complete metadata; icon renders in the Plugins list.

---

## Stage 4.E — Smoke verification

**Goal:** parity with VS Code confirmed on both IDEA editions.

**Deliverables**

- Manual smoke run on IntelliJ IDEA Community **and** Ultimate (clean installs): install the `.zip`, open `samples/v1-metadata/`, walk the acceptance checklist.
- Short results note appended here or under `docs/intellij/` recording IDE versions tested and any deltas vs VS Code.

**Pre-flight:** Stage 4.D done; built signed/unsigned `.zip`.

**Definition of DONE (feature acceptance)**

Mirrors the project plan's Phase 4 acceptance: *install the plugin in a clean IntelliJ; open `samples/v1-metadata/`; same highlighting, navigation, hover, find-references behavior as VS Code.* Specifically:

- Highlighting matches VS Code (same TextMate grammar).
- Diagnostics, Go to Declaration, Find Usages, Quick Documentation, completion, and rename behave identically to VS Code on the sample bundle.
- Verified on Community and Ultimate, 2024.x.

---

## Test plan (TDD ordering)

Per the planning convention, write tests first per stage. Concrete units (Kotest unless noted):

**Stage 4.C / 4.D — write these before the implementation:**

1. `NodeResolverTest` — override-wins-over-PATH; PATH lookup found; not-found throws `NodeNotFoundException`; `detectVersion` parses `vX.Y.Z`; sub-20 flagged.
2. `TtrStreamConnectionProviderTest` — given a fake Node path and resources dir, the built `GeneralCommandLine` equals `<node> <…>/server/server-stdio.mjs --stdio` with working dir = project base.
3. `TtrSettingsTest` — state round-trips through `getState`/`loadState`; defaults are blank/`off`.
4. `TtrFileMappingTest` (IntelliJ fixture) — `.ttr` and `.ttrg` resolve to `serverId="ttrLanguageServer"`.

Component-level (inter-object) coverage stops at factory→provider wiring. Full cross-host E2E (the shared `samples/` smoke suite) belongs to the global Phase 5 integration flow, not this feature.

---

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| User lacks Node on `PATH` | Actionable notification + settings override (4.D); bundling is the first fast-follow |
| LSP4IJ API drift across versions | Pin LSP4IJ + IDEA baseline in 4.A; `verifyPlugin` in CI catches incompatibilities |
| TextMate + LSP semantic-token overlap causes odd coloring | `fileNamePatternMapping` is the documented path that preserves TextMate; validate visually in 4.C, prefer TextMate as the base layer |
| Server bundle path differs in packaged vs sandbox runs | `PluginResources` resolves against the plugin's unpacked resource root; cover both in 4.C smoke |

---

## Out of scope (restated)

Graphical designer / JCEF embedding; custom `modeler/*` methods; bundled Node; non-IDEA JetBrains products; any change to `parser` / `semantics` / `lsp`. These are tracked for later widenings, not this feature.
