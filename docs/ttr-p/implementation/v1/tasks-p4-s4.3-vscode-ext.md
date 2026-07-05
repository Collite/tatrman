# Tasks · P4 · Stage 4.3 — VS Code extension

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

A **new** thin VS Code extension `packages/ttrp-vscode-ext` (`@tatrman/ttrp-vscode-ext`): language registration for `.ttrp` / `.ttr.sql` / `.ttr.py` / `.ttrb` / `.ttrl`, generated TextMate grammar, an LSP client launching the Stage-4.1 Kotlin server over stdio, and Build/Run/Explain commands surfacing `ttrp/transpile` + `ttrp/run` + `ttrp/explain`. Integration tests drive the **real Kotlin server over real stdio** from the TS paired-connection harness pattern. Phase-4 DONE bar: hero editable in VS Code with live diagnostics, format-on-save, one-click run.

**Decide-and-do (recorded here as the decision):** NEW package, not an extension of `packages/vscode-ext`. Rationale: the TTR-M shim stays untouched (it launches a *Node* server via `TransportKind.stdio` module transport; TTR-P launches a *JVM* process — different lifecycle, settings, packaging); the two languages version and ship independently (S6/PUBLISHING.md); "one LSP across hosts" (architecture §6) is per-family, not per-editor-extension. Marketplace merge, if ever wanted, is a post-v1 packaging question. If the coder finds a hard reason to extend `vscode-ext` instead, that is a §Blockers entry, not a silent pivot.

## Pre-flight (all must pass before T4.3.1)

- [ ] Stages 4.1 + 4.2 DONE: `./gradlew :packages:kotlin:ttrp-lsp:test` green.
- [ ] Server dist builds: `./gradlew :packages:kotlin:ttrp-lsp:installDist` produces `packages/kotlin/ttrp-lsp/build/install/ttrp-lsp/bin/ttrp-lsp` (and `.bat`).
- [ ] S7 rename landed (P0): TS workspace packages are `@tatrman/*`; `pnpm install` green at repo root. (If any `@modeler/*` names linger, record in §Blockers — naming debt, do not propagate it into the new package.)
- [ ] `java` 21+ on PATH (`java -version`) — the extension and tests both need it.
- [ ] `TTRP.g4` stable at `packages/grammar/src/TTRP.g4` (Stage 1.1) — input for the TextMate generator.

## Tasks

### T4.3.1 · Scaffold `packages/ttrp-vscode-ext`

- [ ] Create `packages/ttrp-vscode-ext/` mirroring `packages/vscode-ext/` structure: `package.json` (name `@tatrman/ttrp-vscode-ext`, `displayName: "TTR-P"`, `main: ./dist/extension.js`, `engines.vscode` matching vscode-ext's floor), `tsconfig.json` extending `../../tsconfig.base.json`, `src/extension.ts` (empty activate/deactivate), `language-configuration.json` (comments `//`? NO — canonical TTR-P comment lexis comes from `TTRP.g4`; copy the comment/bracket config the grammar actually defines; per-dialect configs in T4.3.3), `LICENSE`, `README.md` stub (one paragraph + dev-run instructions placeholder).
- [ ] ESM + Node16 per repo conventions (CLAUDE.md §Conventions); `pnpm-workspace.yaml` already globs `packages/*` — verify `pnpm install` picks it up.
- [ ] Record the decide-and-do decision (header of this file) in the package README §Why-a-second-extension, 5 lines max.
- [ ] Add `typecheck`/`lint`/`build`/`test` scripts consistent with `packages/vscode-ext/package.json`.

**Verify:** `pnpm install && pnpm --filter @tatrman/ttrp-vscode-ext build && pnpm --filter @tatrman/ttrp-vscode-ext typecheck`

### T4.3.2 · Integration-test harness against the Kotlin server over stdio (TDD anchor)

**Concrete choice:** NOT `@vscode/test-electron` for LSP behavior (heavy, slow, tests VS Code more than us). Instead: the TS paired-connection harness pattern from `tests/integration/`, pointed at a **spawned JVM child process** — `vscode-jsonrpc`'s `StreamMessageReader/Writer` over the child's stdio replaces the `PassThrough` pair. This exercises the exact transport + launcher VS Code will use. (A later optional `@vscode/test-electron` smoke test is post-v1 polish, not this stage.)

- [ ] New test file in the existing integration workspace: `tests/integration/src/__tests__/ttrp-lsp-stdio.test.ts` (`@tatrman/integration-tests` — CLAUDE.md: LSP feature tests live here). Harness helper `tests/integration/src/ttrp-harness.ts`:

  ```ts
  import { spawn } from 'node:child_process';
  import { createProtocolConnection, StreamMessageReader, StreamMessageWriter,
           InitializeRequest, DidOpenTextDocumentNotification,
           PublishDiagnosticsNotification } from 'vscode-languageserver-protocol/node';

  const SERVER_BIN = new URL(
    '../../../packages/kotlin/ttrp-lsp/build/install/ttrp-lsp/bin/ttrp-lsp' +
    (process.platform === 'win32' ? '.bat' : ''), import.meta.url).pathname;

  export async function startTtrpServer() {
    const proc = spawn(SERVER_BIN, [], { stdio: ['pipe', 'pipe', 'inherit'] });
    const conn = createProtocolConnection(
      new StreamMessageReader(proc.stdout), new StreamMessageWriter(proc.stdin));
    conn.listen();
    return { proc, conn };  // caller: conn.sendRequest(InitializeRequest.type, …)
  }
  ```

- [ ] Vitest `beforeAll` guard: if `SERVER_BIN` missing, fail with the exact fix command (`./gradlew :packages:kotlin:ttrp-lsp:installDist`) — never skip silently.
- [ ] Write the failing/passing test cases (server exists, so these pass as they are written — they are the *contract* the extension client relies on):
  - initialize → capabilities include incremental sync + hover + rename + documentFormattingProvider;
  - didOpen broken hero (`==`) → `publishDiagnostics` with code `TTRP-EQ-001` (reuse fixture content from `packages/kotlin/ttrp-lsp/src/test/resources/fixtures/hero-broken.ttrp` — read the file, don't duplicate the text);
  - `ttrp/validate` custom request round-trips (send raw method string `'ttrp/validate'`);
  - `ttrp/transpile` with a stale version → JSON-RPC error `-32801` (ContentModified);
  - shutdown/exit terminates the process (assert exit within 5 s — no zombie JVMs in CI).
- [ ] Wire a root `justfile`/CI step so `installDist` precedes this suite in CI (same job that runs Gradle tests; document the ordering in the test file header).

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:installDist && pnpm --filter @tatrman/integration-tests test -- ttrp-lsp-stdio`

### T4.3.3 · Language registration — five file kinds

- [ ] `package.json` `contributes.languages` (ids fixed here; contracts §1 is the source for extensions/markers):
  | id | matching | notes |
  |---|---|---|
  | `ttrp` | `"extensions": [".ttrp"]` | canonical programs |
  | `ttr-sql` | `"filenamePatterns": ["*.ttr.sql"]` | double extension — use `filenamePatterns`, NOT `extensions` (VS Code last-segment matching would swallow plain `.sql`); do not claim `.sql` |
  | `ttr-pandas` | `"filenamePatterns": ["*.ttr.py"]` | same technique; do not claim `.py` |
  | `ttrb` | `"extensions": [".ttrb"]` | grammar content arrives P7; registration + comments now |
  | `ttrl` | `"extensions": [".ttrl"]` | family-wide sidecar; TTR-M-hosted grammar lands Stage 5.2 — plain-text-ish registration now, no LSP binding |
- [ ] Per-language `language-configuration` files with the correct comment lexis (S19/contracts §1): `ttrp` → TTRP.g4's comment tokens; `ttr-sql` → `--`; `ttr-pandas` → `#`; `ttrb` → `#`; `ttrl` → match the v1.1 layout-block grammar's comments. Brackets/auto-closing pairs for `ttrp` include `"""` fences NOT auto-closed (auto-close fights pasting fragments).
- [ ] Icons: copy the `packages/vscode-ext/icons/` pattern (one `.svg` per language id; a shared TTR-P glyph is fine v1).

**Verify:** `pnpm --filter @tatrman/ttrp-vscode-ext build` + manual F5 spot-check: opening `x.ttrp`, `x.ttr.sql`, `x.ttr.py` shows the right language id in the status bar (record a one-line note in the progress doc; `x.sql` must NOT light up as ttr-sql).

### T4.3.4 · TextMate grammar generation from `TTRP.g4`

- [ ] `packages/ttrp-vscode-ext/scripts/generate-tm-grammar.ts` following `packages/vscode-ext/scripts/generate-tm-grammar.ts` (same shape: parse lexer rules out of the `.g4`, emit scopes; same "sibling `.js` emitted by build-generator, do not hand-edit" convention): input `packages/grammar/src/TTRP.g4`, output `packages/ttrp-vscode-ext/syntaxes/ttrp.tmLanguage.json`, scope `source.ttrp`.
- [ ] TTR-P-specific additions over the TTR-M generator: `->` operator scope; reserved port names (S10) as `variable.language`; control keywords `after`/`with`/`control` (+ reserved `finishes`) as `keyword.control`; **embedded fences delegate**: `begin: """sql` → `contentName: meta.embedded.block.sql`, `patterns: [{ include: "source.sql" }]`; `"""pandas` → `source.python`; `"""ttrb` → plain string scope v1 (P7 upgrades). Fence delegation gives fragment interiors real highlighting for free — consistent with C2-f (we color them, never rewrite them).
- [ ] Minimal committed grammars for `ttr-sql` / `ttr-pandas`: thin wrappers that `include` `source.sql` / `source.python` plus the marker-comment line scope — commit these two by hand (they are 20 lines each, not generated).
- [ ] `contributes.grammars` wires all of: `source.ttrp`, `source.ttr-sql`, `source.ttr-pandas` (+ `ttrb`, `ttrl` placeholders if trivially available — else defer to P7/P5 and say so in README).
- [ ] Unit test per the vscode-ext precedent (`packages/vscode-ext/scripts/__tests__/`): generator snapshot on the current `TTRP.g4` + assert the four fence rules exist. Commit the generated `syntaxes/ttrp.tmLanguage.json` (matching TTR-M convention: generated TM grammar IS committed — CLAUDE.md §Grammar regeneration step 2/3).

**Verify:** `pnpm --filter @tatrman/ttrp-vscode-ext test` (generator tests) and `node packages/ttrp-vscode-ext/scripts/generate-tm-grammar.js && git diff --exit-code packages/ttrp-vscode-ext/syntaxes/` (committed output is current).

### T4.3.5 · LSP client wiring — launching the Kotlin server

- [ ] `src/extension.ts`: `vscode-languageclient`'s **`Executable`** server options (NOT `NodeModule` — contrast with `packages/vscode-ext/src/extension.ts` which launches a Node bundle):

  ```ts
  const serverOptions: Executable = { command: resolveServerCommand(context), args: [], options: {} };
  const client = new LanguageClient('ttrp', 'TTR-P Language Server', { run: serverOptions, debug: serverOptions }, {
    documentSelector: [
      { scheme: 'file', language: 'ttrp' },
      { scheme: 'file', language: 'ttr-sql' },
      { scheme: 'file', language: 'ttr-pandas' },
      { scheme: 'file', language: 'ttrb' },
    ],
    outputChannelName: 'TTR-P Language Server',
    synchronize: { fileEvents: vscode.workspace.createFileSystemWatcher('**/*.{ttrp,ttr.sql,ttr.py,ttrb,ttrm,toml}') },
  });
  ```

  (`.ttrm`/manifest watch: world + `[ttrp]` changes must reach the server; `ttrl` stays out of the selector until Stage 5.2.)
- [ ] `resolveServerCommand` resolution order, documented in README §Running: (1) setting `ttrp.server.path` (absolute path to launcher script or jar — if it ends `.jar`, run `java -jar <path>`); (2) **dev default**: the installDist launcher `packages/kotlin/ttrp-lsp/build/install/ttrp-lsp/bin/ttrp-lsp[.bat]` resolved relative to the workspace/monorepo root; (3) production `.vsix`: server dist bundled under `dist/server/` at package time (wire the copy step into the package script; actual marketplace packaging is post-v1 — the step just must not be architecture-hostile). Missing everything ⇒ one actionable error message naming the gradle command: `./gradlew :packages:kotlin:ttrp-lsp:installDist`.
- [ ] Settings contributed: `ttrp.server.path` (string), `ttrp.trace.server` (off/messages/verbose), `ttrp.format.onSave` documented as plain VS Code `editor.formatOnSave` guidance in README (no custom re-implementation — the server's empty-edit-when-canonical behavior from T4.2.4 makes this safe).
- [ ] Thin-shim rule (CLAUDE.md, architecture §6): `extension.ts` contains NO TTR-P understanding — registration, client lifecycle, command → LSP-request forwarding only. Anything smarter goes in the Kotlin server.

**Verify:** F5 Extension Development Host (after `installDist`): open `hero-broken.ttrp` → `TTRP-EQ-001` squiggle live; fix → clears; Format Document on an uglified hero reflows chains but leaves the `"""sql` interior bytes alone. Record the manual-check result in the phase progress doc (reviews verify claims — CLAUDE.md §Phase review cadence).

### T4.3.6 · Build / Run / Explain commands

- [ ] `contributes.commands` + implementations, each a thin forward of the active editor's `{uri, version}`:
  - `ttrp.build` ("TTR-P: Build Bundle") → `ttrp/transpile`; on success, info toast with `bundlePath` + "Reveal" button (`revealFileInOS`).
  - `ttrp.run` ("TTR-P: Run") → `ttrp/transpile` freshness is server-side; send `ttrp/run`; stream nothing (blocking request, v1) — show progress via `vscode.window.withProgress`; on completion show exit code, open the `out/` folder on 0, surface the per-island log path hint on 1, and the `TTR_CONN_*` pre-flight message on 2 (contracts §5 exit contract).
  - `ttrp.explain` ("TTR-P: Explain") → `ttrp/explain`; render result JSON in a read-only editor tab (`workspace.openTextDocument({content, language: 'json'})`) — canvas rendering is Stage 5's job, not this shim's.
  - On `-32801 ContentModified`: re-read `{uri, version}` and replay once (the contracts-§4 client discipline, exercised for real here).
- [ ] `contributes.menus`: the three commands under `editor/title/run` for `language == ttrp` (+ bare-fragment language ids — bare `.ttr.sql`/.`ttr.py` are valid programs, contracts §1, and must be runnable… but note: bare-fragment *compilation* lands P6. Guard: if the server answers with the P6-pending diagnostic, surface it honestly. Register the menu for `ttrp` only until P6; note in README).
- [ ] Command integration test in `tests/integration/src/__tests__/ttrp-lsp-stdio.test.ts` (extend, don't duplicate): transpile→run round-trip against the hero over raw stdio, gated behind the same env flag as Stage 4.2's run test (needs PG + python3; CI wires it in the dockerized job; locally SKIPPED-visible).

**Verify:** `pnpm --filter @tatrman/integration-tests test -- ttrp-lsp-stdio` green (run case may SKIP locally); F5: one-click Run on the hero drops `out/` files and toasts exit 0 (env with PG up), or exits 2 with the pre-flight message (env without) — both are correct behavior to record.

### T4.3.7 · CI wiring + hero walkthrough (stage gate)

- [ ] CI: extend the existing workflow so the P4 chain runs ordered — `./gradlew :packages:kotlin:ttrp-lsp:test` → `:installDist` → `pnpm --filter @tatrman/integration-tests test -- ttrp-lsp-stdio` → `pnpm --filter @tatrman/ttrp-vscode-ext build test`. The dockerized-PG job (Phase 3's conformance gate) additionally exports the run-test env flag.
- [ ] Write `docs/ttr-p/implementation/v1/progress-phase-04.md` walkthrough section: the Phase-4 DONE claim ("hero editable in VS Code with live diagnostics, format-on-save, one-click run") with the exact reproduction steps (gradle command, F5, which fixture, what you see) — `[x]` is intent, review verifies (CLAUDE.md cadence).
- [ ] Sweep: no `@modeler/*` references inside `packages/ttrp-vscode-ext/`; no business logic in `extension.ts` (grep for `parse|resolve|diagnost` in src/ — hits must be forwarding-only); README §Running complete for a cold-clone developer.

**Verify:** CI green on the PR; `grep -rn "@modeler/" packages/ttrp-vscode-ext/src packages/ttrp-vscode-ext/package.json` empty; a cold `git clone && pnpm install && ./gradlew :packages:kotlin:ttrp-lsp:installDist` followed by F5 reaches live diagnostics (record in progress doc).

## Definition of DONE (stage)

- `packages/ttrp-vscode-ext` builds, typechecks, lints; TTR-M's `packages/vscode-ext` untouched (git diff clean there).
- Five language ids registered with correct matching (double extensions via `filenamePatterns`); TM grammar generated from `TTRP.g4` with fence delegation to `source.sql`/`source.python`; committed and current.
- Stdio integration suite green against the real Kotlin server: initialize, diagnostics roundtrip (`TTRP-EQ-001`), `ttrp/validate`, stale-version `-32801` + replay, clean shutdown.
- **Phase-4 exit (plan):** hero editable in VS Code with live diagnostics, format-on-save (empty-edit-safe), one-click run — walkthrough recorded in `progress-phase-04.md`.

## Blockers

*(record blockers here; none at authoring time)*

## References

- Plan: [plan.md](./plan.md) Phase 4 · Stage 4.3 (+ Phase-4 DONE bar)
- Contracts: [`../../architecture/contracts.md`](../../architecture/contracts.md) §1 (file kinds/markers/comment lexis), §4 (methods, versioning discipline), §5 (exit contract, `TTR_CONN_*`)
- Architecture: [`../../architecture/architecture.md`](../../architecture/architecture.md) §6 (vscode-ext = thin shim, no business logic; one Kotlin LSP)
- Decisions: G-b/G-f (JVM server, stdio host transport), S7 (`@tatrman/*` scope), S19 (per-dialect comment lexis), S10 (reserved ports → highlighting), C2-f (fragments colored, never rewritten), C1-e (run semantics), H-2/H-2c (extensions incl. `.ttrl`)
- Precedents in-repo: `packages/vscode-ext/src/extension.ts` (shim shape; note the Node-vs-JVM transport difference), `packages/vscode-ext/scripts/generate-tm-grammar.ts` (+ its `__tests__/`), `tests/integration/` paired-connection tests (CLAUDE.md: canonical LSP-test home)
- Stage 4.1/4.2 fixtures: `packages/kotlin/ttrp-lsp/src/test/resources/fixtures/`
