# Tasks · P5 · Stage 5.1 — Designer server + WS-LSP transport

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

> **AMENDED 2026-07-05 (MD8 — ttr-metadata feature approved):** the host module `:packages:kotlin:ttr-designer-server` is **created by ttr-metadata Phase M3.1** (port 7270, `--repo`/`--port` args, `Application.designerServerModule` installing `WebSockets` once, route-only protocol installers, `/ttrm` already mounted — see `docs/ttr-metadata/architecture/contracts.md` §4 + `docs/ttr-metadata/implementation/v1/tasks-m3-s3.1-server-host.md`). This stage does **NOT** scaffold a `ttrp-designer-server` module: it contributes `installTtrpLsp(deps)` mounting the Phase-4 LSP at **`/lsp`** beside `/ttrm` (M3.1's `CoexistingProtocolInstallersSpec` proves the seam). Read any scaffold/port/arg specifics below as superseded by the M3.1 host; pre-flight gains: ttr-metadata M3.1 DONE.

## Stage deliverable

A repo-attached JVM **Designer server** (G-b) that hosts the **same** Kotlin LSP built in Phase 4 over a WebSocket transport, bound **loopback-only, no auth, single user** (S24), serving `ttrp/getGraph` and `ttrp/getWorld` (contracts §4) to any WS client. Verified by an in-process Kotest WS contract test that runs `initialize` → `didOpen(hero.ttrp)` → `ttrp/getGraph` → `ttrp/getWorld`.

**Server library choice: Ktor** (`ktor-server-cio` + `ktor-server-websockets`). The coder's reference patterns for the canonical Ktor bootstrap live in **`~/Dev/ai-platform/EXAMPLES.md` §1** — `installKtorServerBase`, the canonical `Application.kt` ≤45 lines, CIO/Netty bootstrap. Mirror that shape; do not invent a new server skeleton. (Ktor WS API confirmed current 2026-07: `install(WebSockets)`, `routing { webSocket("/lsp") { … } }`, `incoming: ReceiveChannel<Frame>`, `send(Frame.Text(…))`, artifact `io.ktor:ktor-server-websockets`.)

## Pre-flight (all must pass before T5.1.1)

- [ ] Phase 4 complete: `./gradlew :packages:kotlin:ttrp-lsp:test` green; the stdio LSP serves standard methods + `ttrp/explain`/`ttrp/transpile`/`ttrp/run`/`ttrp/validate` (plan Stage 4.2).
- [ ] The canonical hero compiles: `./gradlew :packages:kotlin:ttrp-cli:run --args='check <hero>.ttrp'` (or the Phase-1 equivalent) exits 0 on the Phase-1 golden-corpus hero fixture.
- [ ] `pnpm -r build` green (S7 `@tatrman/*` scope rename from P0 already in effect).
- [ ] Confirm the Phase-4 LSP server class name + its lsp4j wiring (expected `org.tatrman.ttrp.lsp.TtrpLanguageServer` + `LSPLauncher`); if 4.1 landed different names, substitute them consistently in T5.1.3–T5.1.5.

## Tasks

### T5.1.1 · Scaffold `:packages:kotlin:ttrp-designer-server`

Note: this module is **not** in the Phase-0 roster (`ttrp-{frontend,graph,emit,lsp,cli,conform}`) — it is added here, per architecture §6 ("Designer server | JVM, repo-attached"). TTR-P-only in v1 (C1-f).

- [ ] Create `packages/kotlin/ttrp-designer-server/` with `build.gradle.kts`: depends on `:packages:kotlin:ttrp-lsp`; add to `gradle/libs.versions.toml`: `ktor` version + `ktor-server-cio`, `ktor-server-websockets`, `ktor-client-cio` + `ktor-client-websockets` (test), Kotest (same bundle the other ttrp modules use).
- [ ] Register in `settings.gradle.kts`; add the module to the CI build job and a publish-plumbing row in `PUBLISHING.md` **only if** the artifact is published (it is repo-attached tooling — default: NOT published; record that in the PUBLISHING.md table as "internal, not published").
- [ ] Empty `src/main/kotlin/org/tatrman/ttrp/designer/Main.kt` with a `main` that prints usage and exits.

**Verify:** `./gradlew :packages:kotlin:ttrp-designer-server:build` green.

### T5.1.2 · TDD: WS transport contract test (red)

- [ ] Copy the Phase-1 golden hero into `packages/kotlin/ttrp-designer-server/src/test/resources/fixtures/hero.ttrp` (source of truth = Phase-1 corpus in `ttrp-frontend` test resources; keep the shape: `db_prep` container @ `erp_pg` + `crunch` container @ polars engine, join → aggregate → branch, `err` path, `display` leaves). Also copy the world/model fixtures it resolves against.
- [ ] Write `src/test/kotlin/org/tatrman/ttrp/designer/WsLspTransportSpec.kt` (Kotest `FunSpec`):
  - boots the server in-process on an ephemeral port (`port = 0`, read back the bound port),
  - connects `ktor-client` with WebSockets to `ws://127.0.0.1:<port>/lsp`,
  - sends `initialize` (JSON-RPC 2.0, **one message per WS text frame** — the LSP-over-WS convention; no `Content-Length` headers on the wire), awaits the response id,
  - sends `textDocument/didOpen` with the hero fixture text,
  - sends `ttrp/getGraph {uri, version: 1}` → asserts: result has `graph`, `provenance`, `derived` keys (contracts §4); graph contains containers `db_prep` and `crunch`; a synthesized transfer between them; nodes carry source ranges,
  - sends `ttrp/getWorld {uri}` → asserts `world`, `engines[]`, `executors[]`, `storages[]`, `staging` present and `engines` includes the PG + Polars engines from the fixture world.
- [ ] Write `src/test/kotlin/org/tatrman/ttrp/designer/LoopbackBindingSpec.kt`: asserts the server binds host `127.0.0.1` exactly (S24) — inspect the engine's resolved connectors; no `0.0.0.0` ever.

**Verify:** `./gradlew :packages:kotlin:ttrp-designer-server:test` — both specs FAIL (red) for the right reason (no server yet), not with compile errors.

### T5.1.3 · WS frame ⇄ LSP4J stream bridge

lsp4j's `LSPLauncher` speaks `Content-Length`-framed streams; the WS wire speaks one-JSON-RPC-message-per-text-frame. Bridge deterministically, no buffering heuristics (P2):

- [ ] `src/main/kotlin/org/tatrman/ttrp/designer/WsJsonRpcBridge.kt`:
  - **inbound:** `onMessage(text: String)` writes `"Content-Length: ${bytes.size}\r\n\r\n"` + UTF-8 payload to a `PipedOutputStream`; expose the paired `PipedInputStream` as `bridge.inputStream` for the launcher.
  - **outbound:** expose `bridge.outputStream`: an `OutputStream` that parses `Content-Length` framing and, per complete message body, calls a supplied `send(String)` callback (wired to `outgoing.trySend(Frame.Text(body))` in the session).
- [ ] `src/test/kotlin/.../WsJsonRpcBridgeSpec.kt` (Kotest): frame in → exactly one correctly framed message readable from `inputStream`; two messages written to `outputStream` (split across arbitrary `write()` boundaries) → exactly two `send` callbacks with byte-identical bodies; multi-byte UTF-8 lengths correct.

**Verify:** `./gradlew :packages:kotlin:ttrp-designer-server:test --tests '*WsJsonRpcBridgeSpec'` green.

### T5.1.4 · Ktor bootstrap: loopback WS host (S24)

- [ ] `Application.kt` (≤45 lines — mirror `~/Dev/ai-platform/EXAMPLES.md` §1's canonical shape), minimal form:

```kotlin
fun main(args: Array<String>) {
    val port = args.getOrNull(0)?.toIntOrNull() ?: 9257
    embeddedServer(CIO, host = "127.0.0.1", port = port,   // S24: loopback-only, no auth
        module = Application::designerServer).start(wait = true)
}

fun Application.designerServer() {
    install(WebSockets)
    routing {
        webSocket("/lsp") {                                 // one LSP session per connection
            val bridge = WsJsonRpcBridge { body -> outgoing.trySend(Frame.Text(body)) }
            val server = TtrpLanguageServer()               // the SAME Phase-4 server class
            val launcher = LSPLauncher.createServerLauncher(server, bridge.inputStream, bridge.outputStream)
            server.connect(launcher.remoteProxy)
            val listening = launcher.startListening()
            try { for (frame in incoming) if (frame is Frame.Text) bridge.onMessage(frame.readText()) }
            finally { listening.cancel(true) }
        }
    }
}
```

- [ ] Server takes the project root as arg 2 (repo-attached: resolves the `[ttrp]` manifest by walk-up from there, same as the stdio LSP); pass it into the server session init.
- [ ] **No auth code, no token, no TLS** — S24 is explicit: v1 = loopback-only, no auth, single user. Add a one-line startup log stating exactly that.

**Verify:** `./gradlew :packages:kotlin:ttrp-designer-server:test --tests '*LoopbackBindingSpec'` green; manual smoke: `./gradlew :packages:kotlin:ttrp-designer-server:run --args='9257 .'` starts and logs the loopback notice.

### T5.1.5 · `ttrp/getGraph` served over WS

- [ ] Ensure the Phase-4 handler registration includes `ttrp/getGraph` per contracts §4: `{uri, version}` → `{graph, provenance, derived}` — full graph **including derived containers for bare fragments**; nodes carry source ranges + er provenance (E-d). If Phase 4 stubbed it, implement it now in `ttrp-lsp` (NOT in this module — the server hosts, the LSP serves; one LSP across hosts).
- [ ] Graph payload must expose per node: ζ-able identity inputs (container path, SSA name, ordinal — consumed in 5.2), node kind (T10 roster), ports (incl. `err`/`rejects`), edges (data + control FS/SS), containers with `target`, program-level leaves (movement/store/display) — the orchestration view in 5.3 is derived from exactly this (C1-a β).
- [ ] Un-skip / enable the `getGraph` case in `WsLspTransportSpec`.

**Verify:** `./gradlew :packages:kotlin:ttrp-designer-server:test --tests '*WsLspTransportSpec'` — getGraph case green.

### T5.1.6 · `ttrp/getWorld` served over WS

- [ ] Implement/verify `ttrp/getWorld {uri}` → `{world, engines[], executors[], storages[], staging}` in `ttrp-lsp` (resolved world for the document — `uses world` pin > `[ttrp] world`, contracts §2); the Designer palette + target-assignment UI (5.4) reads engines/targets from this.
- [ ] Enable the `getWorld` case in `WsLspTransportSpec`.

**Verify:** `./gradlew :packages:kotlin:ttrp-designer-server:test` — entire suite green.

### T5.1.7 · Both transports, one server — regression + docs

- [ ] Add a Kotest case proving the stdio transport still works after the WS refactor (reuse the Phase-4 stdio harness on `initialize` + one request) — "one LSP across hosts" is the invariant; the WS host must not have forked any handler logic.
- [ ] Two WS clients connecting sequentially get independent sessions (S24 says single *user*, not single *connection* — reconnect must work); add a reconnect case to `WsLspTransportSpec`.
- [ ] `packages/kotlin/ttrp-designer-server/README.md`: how to start (`./gradlew :packages:kotlin:ttrp-designer-server:run --args='<port> <project-root>'`), the S24 posture, the WS endpoint `/lsp`, the one-frame-one-message wire rule.

**Verify:** `./gradlew :packages:kotlin:ttrp-designer-server:test :packages:kotlin:ttrp-lsp:test` green.

## Definition of DONE (stage)

- `./gradlew :packages:kotlin:ttrp-designer-server:test` green: WS contract (initialize → didOpen hero → getGraph → getWorld), bridge framing, loopback binding, reconnect, stdio regression.
- Server starts from the repo, binds `127.0.0.1` only, no auth (S24), and any generic WS JSON-RPC client can pull the hero graph.
- No LSP business logic lives in `ttrp-designer-server` — it hosts `ttrp-lsp`, nothing more.

## Blockers

*(record here; STOP on hit)*

## References

- Plan Stage 5.1 · architecture §6 (component roster) · contracts §4 (`ttrp/getGraph`, `ttrp/getWorld`)
- Decisions: G-b (WS-LSP + repo-attached server, Kotlin-only) · S24 (loopback-only no-auth) · C1-f (TTR-P-only v1 server) · C1-a (what getGraph must feed)
- Ktor bootstrap patterns: `~/Dev/ai-platform/EXAMPLES.md` §1 (installKtorServerBase, canonical Application.kt ≤45 lines, CIO/Netty bootstrap)
- Ktor WS docs: https://ktor.io/docs/server-websockets.html
