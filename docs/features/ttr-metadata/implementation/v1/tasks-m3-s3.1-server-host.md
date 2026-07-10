# Tasks · M3 · Stage 3.1 — Designer server host (`ttr-designer-server`)

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Feature decisions MD1–MD8 → `../../architecture/architecture.md` §8 · TTR-P decision IDs → `../../../ttr-p/design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

`:packages:kotlin:ttr-designer-server` (**MD8** — family-level `ttr-` prefix, *not* `ttrp-`): a repo-attached Ktor CIO host, bound **loopback-only, no auth** (S24), started with `--repo <path> --port <n>` (default port **7270**, contracts §4). It embeds `MetadataLoader`/`MetadataRegistry`/`MetadataRefresher` from `org.tatrman:ttr-metadata` over a `LocalFsStorage` on the attached model repo and serves the read-only **`ttrm/*` WS JSON-RPC protocol** (contracts §4: `getModelIndex`, `getModelGraph`, `getObject`, `search`, `getStatus`, `refresh`; server→client notification `modelChanged`; `getWorld` gated on M2.1). File watching (java.nio `WatchService`, 200 ms debounce) drives the refresher and the `ttrm/modelChanged` notification. The Ktor module exposes an **extension-function seam** (`fun Application.installTtrmProtocol(deps)`) so TTR-P P5.1 later mounts its WS-LSP at `/lsp` on this same host (plan §6 amendment of TTR-P R10; architecture §5 "one server, two protocol families").

This module is a **host, not a brain** (MD5 applied to the server): every method handler is proto-conversion + delegation to `MetadataQuery`/`WorldResolver`; the library's structured results/errors are mapped to JSON-RPC error objects here (`-32000` model-not-loaded, `-32001` not-found, `-32002` bad-scope) — the library itself stays id-free.

**Ktor API verified current via context7 (2026-07-05, `/websites/ktor_io`):** server plugin `install(WebSockets)` + `routing { webSocket("/ttrm") { for (frame in incoming) … send(Frame.Text(…)) } }` (artifact `io.ktor:ktor-server-websockets`); in-process testing via `ktor-server-test-host`'s `testApplication { application { module() }; val client = createClient { install(WebSockets) }; client.webSocket("/ttrm") { send(Frame.Text(…)); incoming.receive() } }` (client plugin from `io.ktor:ktor-client-websockets`). Snippets embedded in T3.1.1b/T3.1.3.

## Pre-flight (all must pass before T3.1.1)

- [ ] **M1.2 DONE:** `./gradlew :packages:kotlin:ttr-metadata:test` green; `MetadataLoader`, `MetadataRegistry`, `MetadataRefresher`, `MetadataQuery`, `LocalFsStorage` exist under `org.tatrman.ttr.metadata.*` (contracts §2). Kantheon paths (`kantheon/services/ariadne/src/main/kotlin/org/tatrman/kantheon/ariadne/{registry/MetadataRegistry.kt, refresh/MetadataRefresher.kt}`) are **behavior reference only** — this module depends on the library project, never on kantheon.
- [ ] **Fixture project available via test-fixtures (contracts §8):** the M2.1 fixture repo (erp db/er/binding models + `acme.worlds` world doc + `modeler.toml`) is consumable as `testImplementation(testFixtures(project(":packages:kotlin:ttr-metadata")))` and lists a loadable model repo root. Verify: a scratch Kotest spec in ttr-metadata's own suite already loads it (M2.1 DONE bar). If M2.1 is not yet merged, the M1.1 `LocalFsStorage` fixture repo (tatrman-style: `modeler.toml` + packages) is the fallback — record which one was used.
- [ ] `~/Dev/ai-platform/EXAMPLES.md` §1 read (Ktor bootstrap patterns: `installKtorServerBase` shape, canonical `Application.kt` ≤45 lines). Mirror the *spirit*; import **zero** ai-platform code.
- [ ] TTR-P `tasks-p5-s5.1-server-transport.md` read — P5.1 will mount its WS-LSP onto **this** host (its T5.1.1 scaffold of `ttrp-designer-server` is superseded by plan §6 / MD8); keep the seam in T3.1.3 compatible with its bridge-per-connection pattern.
- [ ] `./gradlew build` green at baseline (Kotlin side); `git status` clean.

## Tasks

### T3.1.1a · Minimal module scaffold (compiles empty)

*(Split from T3.1.1 so the TEST-FIRST suite in T3.1.1b has a module to live in — mirrors P5.1's T5.1.1→T5.1.2 sequence.)*

- [ ] Create `packages/kotlin/ttr-designer-server/build.gradle.kts` following the `packages/kotlin/ttr-semantics/build.gradle.kts` conventions (`kotlin-jvm` + `ktlint` plugins via `alias(libs.plugins.…)`, `kotlin { jvmToolchain(21) }`, `tasks.test { useJUnitPlatform() }`) **plus** the `application` plugin (`mainClass = "org.tatrman.ttr.designer.server.ApplicationKt"`). **Not published** — no `maven-publish`, no publication block; add a row to `PUBLISHING.md`'s module table: "`ttr-designer-server` — internal, repo-attached tooling, NOT published".
- [ ] `gradle/libs.versions.toml`: add `ktor = "<current 3.x>"` (pin the same Ktor version ai-platform's catalog uses if one exists — the CLAUDE.md version-mirroring rule; otherwise latest stable 3.x) and libraries `ktor-server-cio`, `ktor-server-websockets`, `ktor-server-test-host`, `ktor-client-websockets`, plus `kotlinx-coroutines-test` (for the virtual-time debounce specs, T3.1.5). Wire deps: `implementation(project(":packages:kotlin:ttr-metadata"))`, `implementation(libs.ktor.server.cio)`, `implementation(libs.ktor.server.websockets)`, `implementation(libs.kotlinx.ser.json)`, `implementation(libs.slf4j.api)`; `testImplementation(libs.bundles.kotest)`, `testImplementation(libs.ktor.server.test.host)`, `testImplementation(libs.ktor.client.websockets)`, `testImplementation(libs.kotlinx.coroutines.test)`, `testImplementation(testFixtures(project(":packages:kotlin:ttr-metadata")))`. **No jgit / no `ttr-metadata-git`** on this classpath (MD3).
- [ ] `settings.gradle.kts`: `include(":packages:kotlin:ttr-designer-server")`. Add the module to the Kotlin CI job in `.github/workflows/ci.yml` (same pattern as ttr-semantics).
- [ ] Empty `src/main/kotlin/org/tatrman/ttr/designer/server/Application.kt` with a `main(args)` that prints usage (`ttr-designer-server --repo <path> [--port <n>]`) and exits 2 when `--repo` is missing.

**Verify:** `./gradlew :packages:kotlin:ttr-designer-server:build` green.

### T3.1.1b · TEST-FIRST: `ttrm/*` WS contract suite (red)

All specs in `src/test/kotlin/org/tatrman/ttr/designer/server/`. Wire format: JSON-RPC 2.0, **one message per WS text frame** (contracts §4). Shared test helper `TtrmTestHarness.kt`: builds server deps over a **temp-dir copy** of the fixture repo (so specs may touch files), exposes `suspend fun wsCall(session, method, params): JsonObject` doing id-correlated request/response.

- [ ] `TtrmProtocolContractSpec.kt` (Kotest `FunSpec`), using the verified `testApplication` pattern:

  ```kotlin
  testApplication {
      application { designerServerModule(testDeps(fixtureRepoCopy)) }
      val client = createClient { install(WebSockets) }        // io.ktor.client.plugins.websocket.WebSockets
      client.webSocket("/ttrm") {
          send(Frame.Text("""{"jsonrpc":"2.0","id":1,"method":"ttrm/getStatus","params":{}}"""))
          val status = (incoming.receive() as Frame.Text).readText()
          // …parse, assert
      }
  }
  ```

  Cases:
  - `"getStatus handshake carries protocolVersion 1"` — result has `protocolVersion == 1`, `modelVersion` non-empty, `repoRoot` = the fixture copy path, `issues` is an array (contracts §4 version-handshake rule).
  - `"getModelIndex lists the fixture packages and schemas"` — result has `packages[]`, `schemas[]`, `areas[]`, `counts`, `modelVersion`; the fixture's package + `db`/`er` schemas present.
  - `"getModelGraph returns nodes and edges for the fixture repo"` — unscoped call: every node has `qname/kind/label/schema/pkg`, every edge `from/to/type` with `type ∈ {DEFINES, REFERENCES, MAPS_TO, USES}`; a scoped call (`scope: {package: <fixture pkg>}`) returns a subset.
  - `"getObject returns typed detail with sourceLocation"` — a known fixture qname → `object`, `sourceLocation`, `references[]`.
  - `"search finds a fixture object by keyword"` — `{query, algorithm: "keyword"}` → `hits[]` containing a known qname.
  - `"two sequential connections get independent sessions"` (S24 = single *user*, not single connection — reconnect must work; same case P5.1 T5.1.7 pins for `/lsp`).
- [ ] `ModelChangedNotificationSpec.kt` — connect, then **touch/rewrite a `.ttrm` file** in the fixture copy; expect a `ttrm/modelChanged` notification frame `{"jsonrpc":"2.0","method":"ttrm/modelChanged","params":{"modelVersion":…}}` with a *new* modelVersion. Drive time deterministically through the injectable watcher/clock from T3.1.5 (test emits synthetic watch events; no real-FS-timing sleeps — P2, deterministic tests).
- [ ] `JsonRpcErrorShapeSpec.kt` — error objects are `{code, message, data:{kind,…}}` (contracts §4):
  - `"-32000 model-not-loaded when the registry has no snapshot"` — boot deps with an un-swapped `MetadataRegistry`; any data method → `-32000`, `data.kind == "model-not-loaded"`.
  - `"-32001 not-found for an unknown qname"` — `ttrm/getObject {qname:"acme.db.no_such"}` → `-32001`, `data` carries the library's structured `NotFound` fields (MD5: server maps, library never mints ids).
  - `"-32002 bad-scope for an unknown package scope"` — `ttrm/getModelGraph {scope:{package:"no.such.pkg"}}` → `-32002`.
  - `"unknown method → -32601"`, `"malformed frame → -32700"`, `"batch array → -32600"` (batch **not supported** in v1 — see T3.1.2).
- [ ] `LoopbackBindingSpec.kt` — boots the *real* engine: `embeddedServer(CIO, host = "127.0.0.1", port = 0) { designerServerModule(deps) }.start(wait = false)`; assert `server.engine.resolvedConnectors().single().host == "127.0.0.1"` (S24; never `0.0.0.0`). Same assertion style as P5.1's `LoopbackBindingSpec` so both stages pin the identical posture.

**Verify:** `./gradlew :packages:kotlin:ttr-designer-server:test` — all specs FAIL red for the right reason (missing `designerServerModule` etc.), **not** with fixture/classpath errors.

### T3.1.2 · JSON-RPC 2.0 envelope + dispatcher (hand-rolled)

At six methods + one notification a hand-rolled dispatcher beats a framework dep — specify it tightly:

- [ ] `src/main/kotlin/org/tatrman/ttr/designer/server/rpc/JsonRpc.kt` — kotlinx-serialization models: `JsonRpcRequest(jsonrpc, id: JsonElement?, method, params: JsonObject?)`, `JsonRpcSuccess(id, result)`, `JsonRpcError(id, error: ErrorObject(code, message, data))`, `JsonRpcNotification(method, params)`. `id` echoed verbatim (number or string); requests without `id` are notifications (v1: none inbound → drop with a debug log).
- [ ] `rpc/JsonRpcDispatcher.kt` — `register(method: String, handler: suspend (JsonObject) -> JsonElement)`; `suspend fun dispatch(frameText: String): String?`. Rules: parse failure → `-32700` with `id: null`; **JSON array (batch) → `-32600`** — batch is *not supported* in v1, note it in KDoc and README (additive to support later without a protocolVersion bump); unknown method → `-32601`; handler throwing `TtrmRpcException(code, kind, data)` → that error; any other throwable → `-32603` internal (message only, no stack trace on the wire).
- [ ] `rpc/JsonRpcDispatcherSpec.kt` — unit-level (no Ktor): success envelope echo of numeric and string ids; each error rule above; concurrent dispatches don't interleave envelopes.

**Verify:** `./gradlew :packages:kotlin:ttr-designer-server:test --tests '*JsonRpcDispatcherSpec'` green.

### T3.1.3 · Ktor host, `--repo`/`--port`, and the `installTtrmProtocol` seam (MD8 / S24)

- [ ] `Application.kt` — **≤45 lines** (EXAMPLES.md §1 spirit: main = parse args, build deps, `embeddedServer`; everything else in modules). Args: **plain `Array<String>` parsing** for `--repo <path>` (required) and `--port <n>` (default **7270**, contracts §4) — *justification: two flags; clikt would be a new dependency for zero gain (P5.1 uses plain args too).* Resolve the model repo root by **walking up from `--repo` for `modeler.toml`**, same convention as the TS LSP (architecture §5); no `modeler.toml` found → use `--repo` as root with convention defaults.

  ```kotlin
  fun main(args: Array<String>) {
      val opts = CliOptions.parse(args)                      // --repo, --port (default 7270)
      val deps = DesignerServerDeps.forRepo(opts.repoRoot)    // storage→loader→registry→refresher→watcher
      embeddedServer(CIO, host = "127.0.0.1", port = opts.port,   // S24: loopback-only, no auth
          module = { designerServerModule(deps) }).start(wait = true)
  }
  ```

- [ ] `DesignerServerDeps.kt` — composition root: `LocalFsStorage(root)` → `ModelSource` → `MetadataLoader` → initial load → `MetadataRegistry.swap(...)` → `MetadataRefresher` (all `org.tatrman.ttr.metadata.*`; the registry's listener mechanism and the refresher's mutex/force semantics behave as the Ariadne originals — behavior reference `registry/MetadataRegistry.kt`, `refresh/MetadataRefresher.kt` in kantheon). Load issues are *reported* via `getStatus.issues`, never fatal (`LoadResult` never throws on model errors, contracts §2).
- [ ] **The P5.1 seam (explicit):** `fun Application.designerServerModule(deps)` does exactly two things: `install(WebSockets)` **once** (guarded — Ktor throws on duplicate plugin install), then calls `installTtrmProtocol(deps)`. `fun Application.installTtrmProtocol(deps)` **only adds routes**: `routing { webSocket("/ttrm") { …dispatcher loop… } }`. P5.1 later adds `installTtrpLsp(deps)` alongside (route `/lsp`) into the same module — protocol installers must never install the WebSockets plugin themselves.
- [ ] The `/ttrm` session loop: one `JsonRpcDispatcher` per connection; `for (frame in incoming) { frame as? Frame.Text ?: continue; dispatcher.dispatch(frame.readText())?.let { send(Frame.Text(it)) } }`; register the session's `send` in the notification broadcaster (T3.1.5) on open, unregister in `finally`.
- [ ] `CoexistingProtocolInstallersSpec.kt` — **proves the seam**: a test module calling `installTtrmProtocol(deps)` *and* a dummy `installStubLsp()` (`webSocket("/lsp") { echo }`) under one `install(WebSockets)`; assert `/ttrm` answers `getStatus` **and** `/lsp` echoes on the same `testApplication` host. This is the MD8 "P5.1 mounts here" gate made executable.
- [ ] **No auth, no token, no TLS** (S24 explicit for v1); one startup log line stating "loopback-only, no auth, single user (S24)".

**Verify:** `./gradlew :packages:kotlin:ttr-designer-server:test --tests '*LoopbackBindingSpec' --tests '*CoexistingProtocolInstallersSpec'` green; manual smoke: `./gradlew :packages:kotlin:ttr-designer-server:run --args='--repo packages/kotlin/ttr-metadata/src/testFixtures/resources/fixtures/<fixture-repo> --port 7270'` starts and logs the S24 notice.

### T3.1.4 · Read-method handlers: delegation to `MetadataQuery`

All in `src/main/kotlin/org/tatrman/ttr/designer/server/methods/` — each handler ≤~30 lines: decode params → capture **one registry snapshot per request** (snapshot semantics, contracts §2) → `MetadataQuery` call → encode result DTO. No query/filter/search logic in this module (MD2/MD5 — that logic lives in the library; if a needed operation is missing from `MetadataQuery`, STOP and file a blocker against M1.2, don't reimplement here).

- [ ] `ttrm/getStatus` → `{protocolVersion: 1, modelVersion, loadedAt, issues[], repoRoot}` — works even pre-snapshot? **No**: contracts §4 gives `-32000` model-not-loaded for data methods; `getStatus` is the *handshake* and must answer always — when no snapshot: `modelVersion: null`, `issues` = load issues so far. (Contracts §4 doesn't pin this corner; record the clarification in the contracts changelog entry from T3.1.7.)
- [ ] `ttrm/getModelIndex` → browse tree from the snapshot (`Model` packages/schemas/areas + counts + `modelVersion`).
- [ ] `ttrm/getModelGraph {scope?, edgeTypes?}` → `MetadataQuery.graph()` filtered to scope; node `{qname, kind, label, schema, pkg}`, edge `{from, to, type}` per contracts §4; unknown package/schema/qname in scope → `-32002` with the structured field that failed.
- [ ] `ttrm/getObject {qname}` → `MetadataQuery.getObject` + reference/binding data → `{object, sourceLocation, references[]}` (incl. er↔db bindings via `erToDb`); miss → `-32001` + structured `NotFound`.
- [ ] `ttrm/search {query, algorithm?, limit?}` → `MetadataQuery.search(SearchQuery(...))` → `{hits[]}`; unknown algorithm → `-32602` invalid params.
- [ ] `ttrm/refresh {force?}` → `MetadataRefresher.tryRefresh()/forceRefresh()` → `{outcome, modelVersion}` (map the refresher's in-flight outcome — behavior ref: Ariadne's `refresh_in_flight` semantics — to `outcome: "in-flight"`).
- [ ] Un-skip the corresponding `TtrmProtocolContractSpec` + `JsonRpcErrorShapeSpec` cases as each lands.

**Verify:** `./gradlew :packages:kotlin:ttr-designer-server:test --tests '*TtrmProtocolContractSpec' --tests '*JsonRpcErrorShapeSpec'` green.

### T3.1.5 · File watcher → refresher → `ttrm/modelChanged`

- [ ] `watch/RepoWatcher.kt` — **injectable seam**: `fun interface RepoWatcher { fun watch(root: Path, onEvent: (Path) -> Unit): Closeable }`. Production impl `NioRepoWatcher`: java.nio `WatchService`, registers the repo root **recursively** (and newly created subdirs) for `ENTRY_CREATE/ENTRY_MODIFY/ENTRY_DELETE`; forwards only `*.ttrm` / `*.ttrg` / `modeler.toml` paths.
- [ ] `watch/DebouncedRefreshTrigger.kt` — **debounce = 200 ms quiet period** (a burst of events schedules one refresh 200 ms after the *last* event; a fresh event during the window resets it). Constructor takes `(scope: CoroutineScope, quietPeriod: Duration = 200.milliseconds, onQuiet: suspend () -> Unit)` so tests inject `runTest`'s scheduler and drive **virtual time** — no wall-clock sleeps (P2).
- [ ] Wiring in `DesignerServerDeps`: watcher events → trigger → `refresher.tryRefresh()`; `registry.addListener { snapshot -> broadcaster.notifyAll("ttrm/modelChanged", {"modelVersion": snapshot.model.version}) }` — the notification rides the **registry listener** (architecture §5: "file watching → registry listener → `ttrm/modelChanged`"), so a manual `ttrm/refresh` that swaps also notifies. `NotificationBroadcaster` holds the per-session send callbacks from T3.1.3 (copy-on-write list; a dead session's failed send unregisters it).
- [ ] `watch/DebouncedRefreshTriggerSpec.kt` — virtual-time: 5 events in 100 ms → exactly one `onQuiet` at T(last)+200 ms; event at 150 ms resets the window; quiet stream → nothing.
- [ ] Un-skip `ModelChangedNotificationSpec` (uses a fake `RepoWatcher` the test fires by hand + virtual-time trigger for the debounce path, plus one end-to-end case through `NioRepoWatcher` on the temp fixture copy allowed a generous eventually-timeout — the *only* wall-clock case, tagged as such).

**Verify:** `./gradlew :packages:kotlin:ttr-designer-server:test --tests '*DebouncedRefreshTriggerSpec' --tests '*ModelChangedNotificationSpec'` green.

### T3.1.6 · `ttrm/getWorld` — **gated: lands after the M2.1 API is available**

> **Dependency note:** requires `WorldResolver`/`ResolvedWorld` from Stage M2.1 (contracts §3). M3.1 may run before/parallel to M2 (plan: "M3 needs M1, profits from M2"). If M2.1 is not merged when this task is reached, mark it deferred here with a pointer, finish T3.1.7, and return post-M2.1 — do **not** stub a fake world shape.

- [ ] Handler `ttrm/getWorld {qname?}` → no qname: `{worlds: WorldResolver.listWorlds()}`; with qname: `WorldResolver.resolve(qname)` → `ResolvedWorld` JSON (engines/executors/storages/staging/fingerprint, contracts §3) — manifest content transported verbatim (MD5: never interpreted). Failures: `WorldNotFound` → `-32001`; `NotAWorld/StagingConflict/HostsUnknownPackage/ExtendsUnresolved` → `-32001` with `data.kind` = the structured failure name.
- [ ] Contract cases in `TtrmProtocolContractSpec`: resolve the fixture's `acme.worlds` doc → engines/storages/staging + `fingerprint` matching `sha256:` prefix; bogus qname → error shape.

**Verify:** `./gradlew :packages:kotlin:ttr-designer-server:test --tests '*TtrmProtocolContractSpec'` green (incl. getWorld cases).

### T3.1.7 · README, contracts changelog, full-suite green

- [ ] `packages/kotlin/ttr-designer-server/README.md`: start command (`./gradlew :packages:kotlin:ttr-designer-server:run --args='--repo <path> [--port 7270]'`), S24 posture, endpoint `ws://127.0.0.1:7270/ttrm`, one-frame-one-message rule, protocolVersion 1, **batch not supported (v1)**, the `installTtrmProtocol` seam + pointer to TTR-P P5.1 (`/lsp` mounts here, MD8).
- [ ] `docs/ttr-metadata/architecture/contracts.md` **changelog entry** (contracts change rule): (a) `getStatus` answers pre-snapshot with `modelVersion: null` (handshake exception to `-32000`); (b) batch explicitly unsupported in v1 → `-32600`; (c) any DTO field-level shapes this stage had to pin beyond §4's table (index/search-hit element fields — coordinate with M3.2 T3.2.2's entry, one combined changelog bullet is fine).
- [ ] Cross-check against TTR-P P5.1 expectations: the host offers `install(WebSockets)`-once + route-only installers; port/args are owned by this module (P5.1's positional-args sketch is superseded — that's part of the plan-§6 amendment work, note it under §References, don't edit TTR-P docs from this task list).

**Verify:** `./gradlew :packages:kotlin:ttr-designer-server:test :packages:kotlin:ttr-metadata:test` green; `./gradlew build` green.

## Definition of DONE (stage)

- `./gradlew :packages:kotlin:ttr-designer-server:test` green: full `ttrm/*` contract (getStatus handshake pv1 → getModelIndex → getModelGraph → getObject → search → refresh → modelChanged-on-touch), JSON-RPC error shapes `-32000/-32001/-32002` (+ `-32601/-32700/-32600`), loopback binding, coexisting-installers seam, debounce virtual-time specs.
- `ttr-designer-server --repo <fixture>` starts, binds `127.0.0.1:7270` only, no auth (S24); any generic WS JSON-RPC client can pull the fixture model index/graph.
- No model/query/search/world logic in this module — handlers delegate to `org.tatrman.ttr.metadata` (`MetadataQuery`/`WorldResolver`) only (MD2/MD5).
- The `installTtrmProtocol` seam exists and is proven by test — TTR-P P5.1's "host exists" gate (MD8) is satisfiable without touching this module's bootstrap.
- `ttrm/getWorld` green, or explicitly deferred here with an M2.1 pointer (the only permitted open item).

## Blockers

*(record here; STOP on hit)*

## References

- Plan Stage M3.1 · architecture §5 (Designer server) + §8 **MD2, MD3, MD5, MD6, MD8** · contracts §4 (**normative**: methods, error codes, port 7270, protocolVersion), §2–§3 (library API), §8 (fixture home)
- TTR-P decisions: **S24** (loopback-only, no auth, single user) · **P2** (determinism — no sniffing, no timing heuristics; debounce injectable/virtual-time) · G-b/G-c (one repo-attached backend)
- P5.1 alignment: `docs/ttr-p/implementation/v1/tasks-p5-s5.1-server-transport.md` — mounts WS-LSP at `/lsp` via a route-only installer on this host; its `ttrp-designer-server` scaffold + positional args are superseded by MD8/plan §6 (amendment tracked outside this list)
- Behavior references (kantheon, read-only): `services/ariadne/src/main/kotlin/org/tatrman/kantheon/ariadne/registry/MetadataRegistry.kt` (AtomicReference swap + CopyOnWriteArrayList listeners), `…/refresh/MetadataRefresher.kt` (mutex tryLock/withLock, `refresh_in_flight`) — consumed as **library** `org.tatrman.ttr.metadata.*`, coordinates per contracts §1
- Known contract deltas (recorded, not silently fixed): contracts §4 `getModelGraph` node/edge DTO is the *dependency-graph* shape ({qname,kind,label,schema,pkg} / {from,to,type}) — the existing Designer canvas renders a richer `ModelGraph` (rows[], fk/relation edges, cardinalities; `packages/lsp/src/model-graph.ts`); resolution owned by M3.2 T3.2.2 + changelog. §4's per-element field lists for index/search results are underspecified — pinned by this stage's fixtures + changelog (T3.1.7).
- Ktor: https://ktor.io/docs/server-websockets.html · https://ktor.io/docs/server-testing.html (API verified via context7 `/websites/ktor_io`, 2026-07-05) · bootstrap spirit: `~/Dev/ai-platform/EXAMPLES.md` §1 (no code imported)
