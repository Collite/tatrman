# IntelliJ Plugin — Task Lists (overall tracker)

This is the master index for implementing the `intellij-plugin` feature. The work is split into five stages (4.A–4.E), each a self-contained mini task list of 6–8 tasks. **Work the stages in order**; within a stage, work the blocks in order.

**Design authority:** [`../../design/architecture.md`](../../design/architecture.md) · [`../../design/contracts.md`](../../design/contracts.md) · [`../implementation-plan.md`](../implementation-plan.md)

## How to use these lists

- Every task is a checkbox. **Check the box the moment the task is done** — do not batch.
- Each task carries an inline **Verify** command or observation. Run/confirm it before checking the box.
- Tests come first (TDD): in Stages 4.C and 4.D the test tasks precede the implementation tasks that make them pass. Do not reorder.
- Do not invent behavior. If a step is ambiguous, the contract in [`contracts.md`](../../design/contracts.md) wins; if the contract is silent, stop and ask.
- Code targets: Kotlin (JVM 17), Gradle + IntelliJ Platform Gradle Plugin 2.x, LSP4IJ `com.redhat.devtools.lsp4ij`.

## Stage progress

- [x] **Stage 4.A — Gradle scaffold** → [`4A-gradle-scaffold.md`](./4A-gradle-scaffold.md)
      A valid empty plugin that launches in a sandbox IDE via `runIde`, with LSP4IJ present. *(`verifyPlugin` green 242 → 262; GUI `runIde` launch is desktop-only — see stage note.)*
- [x] **Stage 4.B — Build wiring** → [`4B-build-wiring.md`](./4B-build-wiring.md)
      The inlined LSP server bundle + both TextMate grammars land in plugin resources deterministically. *(Shipped unpacked in the plugin home; standalone bundle answers `initialize`; fail-fast verified.)*
- [x] **Stage 4.C — LSP4IJ integration** → [`4C-lsp4ij-integration.md`](./4C-lsp4ij-integration.md)
      Opening a `.ttr`/`.ttrg` file starts the server; navigation, diagnostics, hover, completion, rename, and TextMate coloring all work. *(10 tests green; `verifyPlugin` green 242→262; server diagnostics proven headlessly. IDE-action walk-through is GUI/`runIde` → 4.E.)*
- [x] **Stage 4.D — Settings, Node UX, polish** → [`4D-settings-polish.md`](./4D-settings-polish.md)
      Settings page, graceful missing-Node handling, Marketplace metadata. *(12 tests green; `verifyPlugin` green with complete metadata + icon; missing-Node logic unit-tested. Balloon/settings-UI render are GUI → 4.E.)*
- [ ] **Stage 4.E — Smoke verification** → [`4E-smoke-verification.md`](./4E-smoke-verification.md)
      Parity with VS Code confirmed on IDEA Community + Ultimate against `samples/v1-metadata/`.

## Global pre-flight (must hold before Stage 4.C produces anything testable)

- [x] Phase 2 LSP builds and the **fully-inlined** `server-stdio.mjs` bundle runs standalone (`node server-stdio.mjs --stdio` answers `initialize`). See [`implementation-plan.md` pre-flight](../implementation-plan.md).
- [x] Both `ttr.tmLanguage.json` and `ttrg.tmLanguage.json` exist under `packages/vscode-ext/syntaxes/`.
- [x] JDK 17 + Gradle available; network to JetBrains repos and Marketplace. *(JDK 21 host + foojay-provisioned toolchain; Gradle 9.5.1 wrapper.)*
- [x] `samples/v1-metadata/` is the agreed acceptance fixture.

## Definition of DONE (feature)

Install the built plugin in a clean IntelliJ IDEA; open `samples/v1-metadata/`; observe the **same** highlighting, navigation, hover, and find-references behavior as the VS Code extension. Verified on Community and Ultimate (2024.x). Tracked in [`4E-smoke-verification.md`](./4E-smoke-verification.md).
