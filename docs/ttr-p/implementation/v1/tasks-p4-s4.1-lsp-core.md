# Tasks · P4 · Stage 4.1 — LSP core

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

`packages/kotlin/ttrp-lsp` serves a working **stdio** LSP (G-b/G-f: one Kotlin LSP across hosts; WS transport is added in Stage 5.1 — keep transport injectable): incremental didOpen/didChange document sync, diagnostics streamed from the Phase-1 front-half (`TTRP-<AREA>-<NNN>` ids preserved), hover (expression/port types + er provenance per E-d), go-to-definition, and SSA-aware rename with the ζ sidecar-atomic-rename **interface** defined (C1-c; the `.ttrl` write itself lands in Stage 5.2). Everything test-driven through an in-memory paired-stream harness — the Kotlin twin of the TS `PassThrough` paired-connection harness in `tests/integration/`.

## Pre-flight (all must pass before T4.1.1)

- [ ] P0–P3 complete: `./gradlew build` green, including `:packages:kotlin:ttrp-lsp` (empty skeleton from Stage 0.1) and `:packages:kotlin:ttrp-frontend`.
  Verify: `cd /Users/bora/Dev/collite-gh/tatrman && ./gradlew build`
- [ ] The Phase-1 front-half API is callable as a library: a single entry point of the shape `TtrpFrontHalf.check(source: String, uri: String, project: ProjectDefaults): CheckResult` (names may differ — locate it in `packages/kotlin/ttrp-frontend/src/main/kotlin/` and record the actual FQN in §References before starting). `CheckResult` must expose diagnostics with id, range, severity, suggested alternative (contracts §8) and the resolved AST/graph handle needed for hover/definition.
  Verify: `./gradlew :packages:kotlin:ttrp-frontend:test`
- [ ] Hero fixtures exist from P1: canonical hero + er-flavored variant in the ttrp-frontend golden corpus (find them under `packages/kotlin/ttrp-frontend/src/test/resources/`; record paths in §References).
- [ ] LSP4J version confirmed current on Maven Central: `org.eclipse.lsp4j:org.eclipse.lsp4j` — 0.24.x line as of 2026-07; pin the exact latest in `gradle/libs.versions.toml` during T4.1.2.

## Tasks

### T4.1.1 · `TtrpLspHarness` test utility + failing specs (TDD anchor)

The Kotlin twin of `createServerConnection` + paired `PassThrough` streams from `tests/integration/` (see CLAUDE.md §Cross-package integration tests): two `Piped*Stream` pairs, one LSP4J launcher per side, in-process, no sockets.

- [ ] Create `packages/kotlin/ttrp-lsp/src/testFixtures/kotlin/org/tatrman/ttrp/lsp/test/TtrpLspHarness.kt` (use Gradle `java-test-fixtures` so Stage 4.2 specs reuse it) with this concrete API:

  ```kotlin
  class TtrpLspHarness : AutoCloseable {
      val server: TtrpLanguageServer               // the local service under test
      val client: RecordingLanguageClient          // captures publishDiagnostics/logMessage
      val remote: TtrpLanguageServerApi            // client-side proxy: LanguageServer + ttrp/* methods

      fun initialize(rootUri: String? = null): InitializeResult
      fun open(uri: String, text: String, languageId: String = "ttrp", version: Int = 1)
      fun change(uri: String, version: Int, range: Range, newText: String)  // incremental
      fun awaitDiagnostics(uri: String, timeoutMs: Long = 5_000): List<Diagnostic>
      override fun close()                          // shutdown/exit + stream teardown
  }
  ```

- [ ] Wiring inside the harness — paired pipes + two `Launcher.Builder`s (this is the LSP4J-sanctioned in-memory pattern; `LSPLauncher.createServerLauncher` is reserved for the real stdio `main`):

  ```kotlin
  val clientToServer = PipedOutputStream(); val serverIn = PipedInputStream(clientToServer)
  val serverToClient = PipedOutputStream(); val clientIn = PipedInputStream(serverToClient)

  val serverLauncher = Launcher.Builder<LanguageClient>()
      .setLocalService(server).setRemoteInterface(LanguageClient::class.java)
      .setInput(serverIn).setOutput(serverToClient).create()
  server.connect(serverLauncher.remoteProxy)

  val clientLauncher = Launcher.Builder<TtrpLanguageServerApi>()
      .setLocalService(client).setRemoteInterface(TtrpLanguageServerApi::class.java)
      .setInput(clientIn).setOutput(clientToServer).create()
  remote = clientLauncher.remoteProxy
  serverLauncher.startListening(); clientLauncher.startListening()
  ```

- [ ] `RecordingLanguageClient`: implements `LanguageClient`; stores `PublishDiagnosticsParams` per-uri in a `ConcurrentHashMap` + `CountDownLatch`/`CompletableDeferred` for `awaitDiagnostics`.
- [ ] Test fixtures under `packages/kotlin/ttrp-lsp/src/test/resources/fixtures/`: `hero.ttrp` and `hero-er.ttrp` (copied from the P1 corpus — record provenance in a fixture README line), plus `hero-broken.ttrp` = the hero with one filter condition changed to `amount == 0` → deterministic named diagnostic **`TTRP-EQ-001`** (S9: `==` rejected outside TTR-pandas, suggested alternative "use =").
- [ ] Write the failing Kotest specs (JUnit-platform runner, matching `libs.bundles.kotest` conventions in `packages/kotlin/ttr-parser`), package `org.tatrman.ttrp.lsp`:
  - `HarnessSpec` — initialize handshake returns capabilities; clean close.
  - `DiagnosticsRoundtripSpec` — `didOpen(hero-broken.ttrp)` → `awaitDiagnostics` contains a diagnostic with `code == "TTRP-EQ-001"`, correct range, and the suggested alternative in the message; `didOpen(hero.ttrp)` → empty diagnostics; a `didChange` fixing the `==` clears the diagnostic.
  - `HoverSpec` — (a) hover over an SSA variable shows its port schema/type; (b) on `hero-er.ttrp`, hover over the er-sourced column shows the E-d provenance origin, e.g. `CUST_TYPE ← erp.er.customer.customerType`.
  - `DefinitionSpec` — definition on a variable use jumps to its (latest-SSA) assignment; definition on a container port use jumps to the port declaration.
  - `RenameSpec` — rename of an SSA-reassigned variable (`X = …; X = filter(X, …); X -> store(…)`) updates **all** references across every SSA generation in the returned `WorkspaceEdit`, and the ζ groundwork hook reports the key remaps (e.g. `crunch/sales#1`, `crunch/sales#2` → `crunch/revenue#1`, `crunch/revenue#2`) — sidecar-atomic-ready per C1-c.

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test` — compiles, harness self-test (`HarnessSpec`) may pass, all feature specs fail for the right reason (unimplemented), none error on wiring.

### T4.1.2 · LSP4J dependency + server skeleton + stdio entry point

- [ ] `gradle/libs.versions.toml`: add `lsp4j = "0.24.0"` (pin the newest 0.24.x on Maven Central) and `lsp4j = { module = "org.eclipse.lsp4j:org.eclipse.lsp4j", version.ref = "lsp4j" }`. Note: transitively brings `org.eclipse.lsp4j.jsonrpc` — do not add it separately.
- [ ] `packages/kotlin/ttrp-lsp/build.gradle.kts`: `kotlin-jvm` + `ktlint` + `application` + `java-test-fixtures` plugins; `application { mainClass = "org.tatrman.ttrp.lsp.MainKt" }`; deps `api(libs.lsp4j)`, `implementation(project(":packages:kotlin:ttrp-frontend"))` (+ `:ttrp-graph`, `:ttrp-emit`, `:ttrp-cli` come in Stage 4.2); Kotest test deps as in `ttr-parser/build.gradle.kts`.
- [ ] Define the protocol surface in `src/main/kotlin/org/tatrman/ttrp/lsp/protocol/`:

  ```kotlin
  @JsonSegment("ttrp")                       // wire names: ttrp/<method> — contracts §4
  interface TtrpCustomApi {                  // Stage 4.1 declares; Stage 4.2 implements
      @JsonRequest fun transpile(params: TranspileParams): CompletableFuture<TranspileResult>
      @JsonRequest fun run(params: RunParams): CompletableFuture<RunResult>
      @JsonRequest fun explain(params: ExplainParams): CompletableFuture<ExplainResult>
      @JsonRequest fun validate(params: ValidateParams): CompletableFuture<ValidateResult>
      @JsonRequest fun authoringContext(params: AuthoringContextParams): CompletableFuture<AuthoringContextResult>
  }
  interface TtrpLanguageServerApi : LanguageServer, TtrpCustomApi
  ```

  Param/result data classes mirror contracts §4 exactly (`{uri, version}` on transpile/run/explain; `{source|uri, dialect?}` on validate; `{uri?, position?}` on authoringContext). Stage-4.1 bodies: `CompletableFuture.failedFuture(ResponseErrorException(ResponseError(ResponseErrorCode.MethodNotFound, "lands in Stage 4.2", null)))`.
- [ ] `TtrpLanguageServer : TtrpLanguageServerApi, LanguageClientAware` — `initialize` advertises: `TextDocumentSyncKind.Incremental`, `hoverProvider`, `definitionProvider`, `renameProvider(prepareProvider = true)`; `documentFormattingProvider` is added in Stage 4.2. `TtrpTextDocumentService`, `TtrpWorkspaceService` as separate classes.
- [ ] `Main.kt` — stdio transport (VS Code / IntelliJ hosts):

  ```kotlin
  fun main() {
      val server = TtrpLanguageServer()
      val launcher = LSPLauncher.createServerLauncher(server, System.`in`, System.out)
      server.connect(launcher.remoteProxy)
      launcher.startListening().get()
  }
  ```

  **Invariant:** nothing may write to `System.out` except the launcher (stdout is the wire) — route all logging to stderr (slf4j-simple configured `logFile=System.err`). Transport stays injectable: `Main` is 5 lines over a `createLauncher(in, out)` helper the WS transport (Stage 5.1) will reuse.
- [ ] Wire `include(":packages:kotlin:ttrp-lsp")` is present in `settings.gradle.kts` (P0 did this; verify, don't assume).

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests "org.tatrman.ttrp.lsp.HarnessSpec"` green, and `./gradlew :packages:kotlin:ttrp-lsp:installDist && echo | packages/kotlin/ttrp-lsp/build/install/ttrp-lsp/bin/ttrp-lsp` starts and exits without stack trace on EOF.

### T4.1.3 · Incremental document sync

- [ ] `DocumentStore` (`org.tatrman.ttrp.lsp.docs`): per-uri `{text: StringBuilder-backed rope or plain String, version: Int, languageId: String}`; `didOpen` seeds, `didChange` applies `TextDocumentContentChangeEvent` **by range** (LSP UTF-16 code-unit offsets — convert carefully; the front-half is byte/char oriented), `didClose` evicts. Reject out-of-order versions (`params.textDocument.version` must be > stored) — log + drop, never apply stale.
- [ ] Version bookkeeping is the foundation of the Stage-4.2 versioning discipline (contracts §4: stale `{uri, version}` ⇒ error, client replays) — expose `DocumentStore.get(uri): OpenDocument?` and `OpenDocument.version` for it now.
- [ ] `DocumentSyncSpec`: open→change(range mid-document)→content matches expected; multi-change batch in one `didChange` applies in order; UTF-16 surrogate-pair edit (emoji in a comment) lands at the right offset.

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests "org.tatrman.ttrp.lsp.DocumentSyncSpec"`

### T4.1.4 · Diagnostics streamed from the front-half

- [ ] `AnalysisScheduler` (`org.tatrman.ttrp.lsp.analysis`): on open/change, run `TtrpFrontHalf.check(...)` on a single worker executor with per-uri debounce (250 ms, latest-version-wins; a completed run for a superseded version publishes nothing). Deterministic: same text ⇒ same diagnostics (P2 — no caching shortcuts that skip re-resolution).
- [ ] Diagnostic mapping: front-half diagnostic → LSP `Diagnostic` with `code = "TTRP-<AREA>-<NNN>"` (string), `source = "ttrp"`, `severity` mapped 1:1, suggested alternative appended to `message` (contracts §8) and carried structurally in `data` for later quick-fix use. Ranges: front-half positions → LSP `Range` (mind the 1-indexed-line convention if the front-half inherited TTR-M's ANTLR-style `SourceLocation` — see CLAUDE.md §Key invariants).
- [ ] Publish via `client.publishDiagnostics(PublishDiagnosticsParams(uri, diags, version))` — include the version so hosts can drop stale sets.
- [ ] `didClose` publishes an empty set (standard LSP hygiene).
- [ ] Make `DiagnosticsRoundtripSpec` pass (broken hero → `TTRP-EQ-001` → fix clears it).

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests "org.tatrman.ttrp.lsp.DiagnosticsRoundtripSpec"`

### T4.1.5 · Hover — types + er provenance (E-d)

- [ ] `HoverService`: locate the AST/graph node at position (front-half must expose position→node lookup; if it doesn't yet, add it to `ttrp-frontend` as `CheckResult.nodeAt(line, col)` — that is front-half code, keep it there, the LSP stays thin).
- [ ] Hover content (Markdown `MarkupContent`), by node kind:
  - SSA variable / edge: name + SSA generation + port schema (column: type list, S23 types).
  - Op node: node kind, resolved arg summary, output port schema.
  - er-sourced reference (E-d mandatory provenance): show both tiers — physical name plus origin, e.g. `` `CUST_TYPE` ← `erp.er.customer.customerType` (er→db rewrite) ``. Provenance renders through the recorded origin, never re-derived (E-d: "diagnostics, graphical view, and lineage render through provenance").
- [ ] Make `HoverSpec` pass, including the er-variant case.

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests "org.tatrman.ttrp.lsp.HoverSpec"`

### T4.1.6 · Definition

- [ ] `DefinitionService` resolving, in order of node kind: variable use → its assignment (the SSA generation *visible at the use site*, Q7-γ); container port reference (`crunch.accounts`) → port declaration in the `container` header; container name → container declaration; model/world qname (`erp.db.accounts`, engine names in `target`) → the defining `.ttrm` location **when** ttr-metadata exposes source locations for model defs — if it does not, return the program-local `import`/`uses world` statement instead and record the limitation as a Stage-5 follow-up in §Blockers (do not fake locations).
- [ ] Returns `Either.forLeft(List<Location>)`; multi-result only where genuinely ambiguous (should not happen under D-b position-typing — assert single result in tests).
- [ ] Make `DefinitionSpec` pass.

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests "org.tatrman.ttrp.lsp.DefinitionSpec"`

### T4.1.7 · Rename — SSA-aware + ζ sidecar-atomic groundwork (C1-c)

- [ ] `prepareRename`: valid on variables, container names, port names; rejects keywords/reserved ports (`in,out,err,rejects,true,false,else` — S10) with a clean error.
- [ ] `RenameService`: renaming a variable renames **every SSA generation and every reference** of that source name in the document (the author sees one name, Q7-γ; SSA is desugar). Renaming a container updates all `container.port` wiring references. Emits a single `WorkspaceEdit` with `TextDocumentEdit`s carrying the document version (versioned edits — hosts reject stale).
- [ ] ζ groundwork (C1-c — the interface lands now, the `.ttrl` write lands Stage 5.2): define in `org.tatrman.ttrp.lsp.viewstate`:

  ```kotlin
  /** ζ key = SSA-qualified view-state node identity, e.g. "crunch/sales#2", "crunch/sums~1". */
  data class ZetaKeyRemap(val oldKey: String, val newKey: String)

  interface ViewStateRenameParticipant {
      /** Called with every successful rename BEFORE the WorkspaceEdit is returned.
       *  Stage 5.2 implements this with an atomic .ttrl pair rewrite (C1-c discipline:
       *  rename rewrites the sidecar atomically; changed SSA/chain length ⇒ orphan, never guess). */
      fun onRename(uri: String, remaps: List<ZetaKeyRemap>)
  }
  ```

  `RenameService` computes the `ZetaKeyRemap` list from the SSA graph (all generations of the renamed name, container-path-qualified) and invokes registered participants; Stage 4.1 registers a `RecordingParticipant` in tests only.
- [ ] Make `RenameSpec` pass, including the remap assertions.

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests "org.tatrman.ttrp.lsp.RenameSpec"` then full module: `./gradlew :packages:kotlin:ttrp-lsp:test`

## Definition of DONE (stage)

- `./gradlew :packages:kotlin:ttrp-lsp:test` fully green; `./gradlew build` green repo-wide; ktlint clean.
- `installDist` produces a runnable stdio server (`bin/ttrp-lsp`) that survives an initialize/didOpen/shutdown session driven by hand or by the Stage-4.3 harness.
- didOpen→publishDiagnostics roundtrip on `hero-broken.ttrp` yields `TTRP-EQ-001` with suggested alternative; hover on the er-variant shows E-d provenance; rename of an SSA variable updates all references and reports ζ key remaps.
- No `ttrp/*` custom method implemented yet (they error with MethodNotFound + "lands in Stage 4.2") — declared surface matches contracts §4 exactly.

## Blockers

*(record blockers here; none at authoring time)*

## References

- Plan: [plan.md](./plan.md) Phase 4 · Stage 4.1
- Contracts: [`../../architecture/contracts.md`](../../architecture/contracts.md) §4 (`ttrp/*`), §8 (diagnostics convention)
- Architecture: [`../../architecture/architecture.md`](../../architecture/architecture.md) §6 (TTR-P LSP: Kotlin, stdio + WS, one LSP across hosts)
- Decisions: G-b, G-f (Kotlin-only LSP, transports), E-d (er provenance), Q7-γ (SSA variables), C1-c (ζ keys, atomic sidecar rename), S9 (`TTRP-EQ-001`), S10 (reserved ports)
- TS-side precedent: `tests/integration/` paired-connection harness (CLAUDE.md §Cross-package integration tests); `packages/lsp/src/server.ts` for "thin host, logic in server" shape
- Kotlin service patterns: `~/Dev/ai-platform` `EXAMPLES.md` (coder: read it there; not vendored here)
- LSP4J: `org.eclipse.lsp4j:org.eclipse.lsp4j` — `LSPLauncher.createServerLauncher`, `Launcher.Builder` in-memory wiring, `@JsonSegment`/`@JsonRequest` (verified via context7, 2026-07-05)
- *(fill in during pre-flight)* front-half check API FQN: ____ · P1 hero fixture paths: ____
