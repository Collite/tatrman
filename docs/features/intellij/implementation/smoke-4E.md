# Stage 4.E — Smoke verification results

Companion to [`../plan/tasks/4E-smoke-verification.md`](../plan/tasks/4E-smoke-verification.md).

**Status: PARTIAL — automated verification complete; the manual GUI install-and-observe on clean IDEA Community + Ultimate is still PENDING (requires an interactive desktop).**

Stage 4.E is, by design, a manual install-and-observe pass on two real IDEs. The
development environment for Stages 4.A–4.D was headless, so the GUI portions
(install the `.zip`, watch coloring/navigation/balloons) could not be executed
here. Everything that does **not** require a human at a desktop was verified and
is recorded below; the remaining GUI checklist is laid out for whoever runs the
desktop pass.

---

## Build artefact under test

| | |
|---|---|
| Plugin | `intellij-plugin/build/distributions/intellij-plugin-0.1.0.zip` (~1.77 MB) |
| Built via | `just intellij` (LSP deps + inlined `server-stdio.mjs` + `gradle buildPlugin`) |
| Plugin ID / version | `org.tatrman.modeler.intellij` 0.1.0 |
| Baseline | IDEA 2024.2 (`sinceBuild` 242), no `untilBuild` |
| LSP4IJ | 0.20.1 (Marketplace dependency) |
| Toolchain | IntelliJ Platform Gradle Plugin 2.16.0, Gradle 9.5.1, Kotlin 2.0.21 / JVM 17 |

Zip contents confirmed (unpacked, not jarred): `server/server-stdio.mjs`,
`server/stock/cnc-roles.ttr`, `textmate/{package.json, ttr.tmLanguage.json,
ttrg.tmLanguage.json}`, plus the plugin jar.

## Build/dev environment used for automated checks

- macOS (arm64), JDK 21 host with a foojay-provisioned JVM 17 toolchain.
- Node.js v24.11.0 on `PATH` (server runtime).

---

## Automated verification — DONE ✅

| Check | Result |
|---|---|
| Unit + integration tests (Kotest) | **12 green**: `NodeResolverTest` (7), `TtrStreamConnectionProviderTest` (2), `TtrFileMappingTest` (1), `TtrSettingsTest` (2). |
| `verifyPlugin` (IntelliJ Plugin Verifier) | **Compatible** on 7 IDEs — Community **IC-242, IC-243, IC-251, IC-252** and Ultimate **IU-253, IU-261, IU-262**. (IU-262/2026.2-EAP reports one advisory internal-API usage — plugin-home lookup — which is non-failing; the whole 242 → 261 supported range is fully clean.) |
| Plugin structure / metadata | Valid: id, name, vendor, description (states the LSP4IJ dependency), change-notes, `pluginIcon.svg`, `sinceBuild` all present. |
| Server bundle is self-contained | `node server-stdio.mjs --stdio` answers `initialize` with full capabilities — no `Cannot find module` (proves the inlined, not externalized, bundle ships). |
| **End-to-end LSP against the fixture** | Launching the **shipped** bundle exactly as the plugin does (`node <home>/server/server-stdio.mjs --stdio`, workspace = `samples/v1-metadata/`): `initialize` succeeds and the server emits `textDocument/publishDiagnostics` for all **7** project files — **16 diagnostics on `er.ttr`**. This exercises the full parse + semantics + stock-vocab pipeline that LSP4IJ surfaces into IDE actions. |

What this establishes: the plugin **loads** across the supported IDE range, its
extension wiring (LSP server factory + `.ttr`/`.ttrg` file mappings + TextMate
bundle provider + settings + notifications) is structurally valid, and the
**language server itself works end-to-end** on the acceptance fixture through the
exact artefact the plugin ships. The only unverified surface is LSP4IJ's
in-IDE presentation of those results — which is what the manual pass covers.

---

## Manual GUI smoke — PENDING ⏳ (run on a desktop)

Run on **clean** IntelliJ IDEA **Community** and **Ultimate** (2024.2+), Node 20+
on `PATH`. Fastest path: `./intellij-plugin/gradlew --project-dir intellij-plugin runIde`
(launches a sandbox with the plugin + LSP4IJ), or install the `.zip` via
*Settings → Plugins → Install Plugin from Disk*.

Acceptance checklist (mirror of 4E Block 1.2, to compare against VS Code on the
same `samples/v1-metadata/` files):

- [ ] Syntax highlighting matches VS Code (same TextMate grammar).
- [ ] Diagnostics appear inline (e.g. an unresolved reference is flagged — the
      server reports 16 on `er.ttr`).
- [ ] Go to Declaration jumps to the definition.
- [ ] Find Usages lists references.
- [ ] Quick Documentation (hover) shows the definition's description/type/kind.
- [ ] Code completion offers expected symbols.
- [ ] Rename updates all references.
- [ ] A `.ttrg` file is recognized and served.
- [ ] LSP4IJ console shows a clean `initialize` and the expected command line.

Settings / UX (4D):

- [ ] *Settings → Languages & Frameworks → TTR Modeler* shows the three fields;
      edits persist after Apply and across an IDE restart.
- [ ] With `node` removed from `PATH` and no override, opening a `.ttr` shows the
      actionable balloon (not a stack trace); "Configure…" opens the settings
      page; setting a valid path and reopening starts the server.
- [ ] Plugin icon renders in *Settings → Plugins*.

Editions / upgrade:

- [ ] Repeat the checklist on Ultimate (expected: no deltas).
- [ ] Install 0.1.0, then install a bumped build over it; restart — upgrade
      applies cleanly and the LSP still starts.

When the desktop pass is complete, record the exact IDE build numbers and Node
version here and tick the boxes in `4E-smoke-verification.md` + `index.md`.

---

## Deltas vs VS Code

None expected by construction: the plugin reuses the **same** `server-stdio.mjs`
LSP bundle and the **same** `ttr`/`ttrg` TextMate grammars the VS Code extension
ships. Highlighting is therefore identical, and every language feature is the
same server answering the same requests. Record any observed delta here during
the desktop pass.
