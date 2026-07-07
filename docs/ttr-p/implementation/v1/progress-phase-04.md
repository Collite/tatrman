# Progress — Phase 4 (LSP + VS Code)

> **Status:** code-complete on `feature/ttr-p-v1-phase4`. `[x]` = intent — the reviewer verifies against runtime (CLAUDE.md cadence). Deliverable: the editing experience — a stdio Kotlin LSP consumed by a thin VS Code extension.

## Stage 4.1 — LSP core (stdio, diagnostics, hover, definition, rename) — **code-complete**

`packages/kotlin/ttrp-lsp` serves a working stdio LSP (LSP4J 0.24.0) over an injectable `ProjectResolver`:

- **Sync + diagnostics:** UTF-16-correct incremental `DocumentStore`; front-half (`TtrpChecker`) diagnostics streamed with `TTRP-<AREA>-<NNN>` codes + suggested alternative, debounced 250 ms latest-version-wins (`AnalysisScheduler`).
- **Hover:** resolved variable/port column schemas + E-d er provenance (rendered through the recorded `ErRewrite`, never re-derived).
- **Definition:** variable use → the SSA generation visible at the use site (Q7-γ); `container.port` → the port decl; bare container → the container decl; single result (D-b).
- **Rename:** SSA-aware (every generation + reference), versioned `WorkspaceEdit`, reserved names (S10) rejected; `ViewStateRenameParticipant` ζ-key-remap groundwork (C1-c) — the interface + key computation land now, the `.ttrl` write lands Stage 5.2.
- **Five `ttrp/*` methods declared** (contracts §4).
- **Front-half change:** additive `TtrpChecker.Report.schemas` (resolved var schemas for hover), default `emptyMap()` — no downstream break.

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test` (HarnessSpec, DiagnosticsRoundtripSpec, HoverSpec, DefinitionSpec, RenameSpec, DocumentSyncSpec) · `installDist` starts + exits cleanly on EOF.

## Stage 4.2 — Formatter + `ttrp/*` methods + authoringContext schema — **code-complete**

- **Formatter** (`org.tatrman.ttrp.lsp.format`): canonical reflow on the lossless parse — chain inlining of single-use linear runs, name-keeping at fanout/reassignment, width-based line breaking (`MAX_LINE=100`), multiline config blocks, and **byte-exact fragment-interior preservation (C2-f)**. Bare fragment files never formatted. Wired to `textDocument/formatting` (whole-doc edit, empty when canonical → format-on-save safe). 5 golden pairs + idempotency + `FragmentPreservationSpec` (property). *Deviations (grammar-driven): the non-grammatical `source-position-chain` fixture is dropped; the width rule governs chain line-breaking + always-multiline config blocks, config-vs-args form author-preserved — see `tasks-p4-s4.2-formatter-methods.md`.*
- **Five methods** delegate to their Phase-2/3 library (S4): validate→`TtrpChecker`, explain→`TtrpPipeline`, transpile/run→`BundleAssembler`, authoringContext→`AuthoringContextBuilder`. Versioning discipline enforced (`ContentModified` -32801 + replay data). `run` execution gated behind PG/python3.
- **authoringContext v1:** `docs/ttr-p/architecture/authoring-context.schema.json` (draft 2020-12, closed objects) + two committed example instances, validated in-test via `com.networknt:json-schema-validator`. `contracts.md` §7 normative; changelog v1.1. **The C4 leftover is closed.**

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test` (FormatterGoldenSpec, FragmentPreservationSpec, FormattingLspSpec, CustomMethodsSpec, DocumentVersioningSpec, AuthoringContextSchemaSpec).

## Stage 4.3 — VS Code extension + stdio integration tests — **code-complete**

- **New package `packages/ttrp-vscode-ext`** (`@tatrman/ttrp-vscode-ext`) — decision recorded (README §Why-a-second-extension): a *new* package (JVM lifecycle ≠ the TTR-M Node shim), not an extension of `packages/vscode-ext`.
- **Language registration** for five kinds: `ttrp` (`.ttrp`), `ttr-sql` (`*.ttr.sql` via `filenamePatterns`, does NOT claim `.sql`), `ttr-pandas` (`*.ttr.py`), `ttrb` (`.ttrb`), `ttrl` (`.ttrl`, plain registration). Per-dialect comment lexis (S19).
- **TextMate grammar** generated from `TTRP.g4` (`syntaxes/ttrp.tmLanguage.json`, committed) with **fence delegation** (`"""sql`→`source.sql`, `"""pandas`→`source.python`, `"""ttrb`) + reserved ports (S10) + `->` + control keywords. Hand-written thin `ttr-sql`/`ttr-pandas` grammars. Generator unit-tested; CI re-generates and diff-checks.
- **LSP client** (`vscode-languageclient` `Executable`) launches the Kotlin server; `resolveServerExecutable` order: `ttrp.server.path` setting → dev `installDist` launcher → packaged `dist/server/`; missing → one actionable error naming the gradle command. Thin shim — no TTR-P understanding in `extension.ts` (grep-clean).
- **Build / Run / Explain** commands forward `{uri, version}` to `ttrp/transpile` / `ttrp/run` / `ttrp/explain`, with the contracts-§4 `ContentModified` replay-once discipline; menus registered for `ttrp` only (bare-fragment compile lands P6).
- **Stdio integration suite** (`tests/integration/src/ttrp-lsp-stdio.test.ts`) drives the **real Kotlin server over real stdio**: initialize capabilities, diagnostics roundtrip (`TTRP-EQ-001`), `ttrp/validate`, stale-version `-32801`, clean shutdown/exit (no zombie JVM). Gated on the `installDist` launcher (`describe.runIf`) so the pure-Node CI job skips visibly; the dedicated `ttrp-lsp` CI job builds it and runs it.

**Verify:** `pnpm --filter @tatrman/ttrp-vscode-ext build test` · `./gradlew :packages:kotlin:ttrp-lsp:installDist && pnpm --filter @tatrman/integration-tests exec vitest run src/ttrp-lsp-stdio.test.ts` (5/5 green).

## Phase-4 DONE bar — hero editable in VS Code (manual walkthrough)

Reproduction (records the plan's DONE claim; the reviewer re-runs):

1. `pnpm install && ./gradlew :packages:kotlin:ttrp-lsp:installDist`.
2. Open `packages/ttrp-vscode-ext` in VS Code, press **F5** (Extension Development Host).
3. Open `packages/kotlin/ttrp-lsp/src/test/resources/fixtures/hero-broken.ttrp` (has an actual `modeler.toml` project? no — for a *resolved* hero, open a `.ttrp` inside a project whose `modeler.toml` points at a model repo; the shared erp fixture project is `packages/kotlin/ttr-metadata/src/testFixtures/resources/fixtures/erp-project/`). You will see:
   - **live diagnostics:** `amount == 0` squiggles `TTRP-EQ-001` ("use `=`"); fix it → clears.
   - **format-on-save:** enable `editor.formatOnSave`; uglify a chain → save reflows it; the `"""sql` interior stays byte-identical.
   - **one-click Run:** the ▶ actions (Build / Run / Explain) appear on `.ttrp`; Run drops `out/` files and toasts exit 0 (with PG up), or exits 2 with the pre-flight message (no PG) — both correct.

## Verification (run by the coder; reviewer re-runs)

- `./gradlew :packages:kotlin:ttrp-lsp:test :packages:kotlin:ttrp-lsp:ktlintCheck` — green; `installDist` runnable.
- `./gradlew :packages:kotlin:ttrp-frontend:test :packages:kotlin:ttrp-graph:test :packages:kotlin:ttrp-emit:test :packages:kotlin:ttrp-cli:test` — green (front-half `Report.schemas` additive).
- `pnpm --filter @tatrman/ttrp-vscode-ext build typecheck test` — green (generator tests: 4).
- `./gradlew :packages:kotlin:ttrp-lsp:installDist && pnpm --filter @tatrman/integration-tests exec vitest run src/ttrp-lsp-stdio.test.ts` — 5/5 green.

## Notes / deferrals (read before review)

- The unrelated `TTR-SEM-201` integration test (`did-you-mean nearest`) fails **on `master` too** — pre-existing, not Phase-4 (semantics side, untouched here).
- `run` end-to-end execution is gated behind PG/python3 (CI dockerized-PG job), consistent with the Phase-3 conformance gate.
- ζ `.ttrl` write is groundwork only (C1-c) — the sidecar rewrite lands Stage 5.2.
- authoringContext capability node/function rosters + model-object enumeration are present-but-empty (schema-final); they grow in P6/P7 (plan.md: "7.2 completes the authoringContext schema that 4.2 finalizes").
