# ttr-designer-server

Repo-attached backend for the TTR-M graphical Designer. It exposes the read-only
model metadata (`org.tatrman:ttr-metadata`) over a WebSocket JSON-RPC 2.0 protocol
so any Designer front-end (or a generic WS JSON-RPC client) can browse the model
index, dependency graph, objects, search, and resolved worlds.

**Not published** — internal tooling, not a library artifact (see
[`PUBLISHING.md`](../../../PUBLISHING.md)). It is also the single host onto which
TTR-P's **WS-LSP mounts at `/lsp`** (MD8 / TTR-P P5.1) via a route-only installer.

## Run

```bash
./gradlew :packages:kotlin:ttr-designer-server:run --args='--repo <path> [--port 7270]'
```

- `--repo <path>` (required) — the model repo root. If `<repo>/models` exists it is
  the storage root (package = directory under `models`, the `modeler.toml`
  convention); otherwise `<repo>` itself.
- `--port <n>` (default **7270**).

Endpoints: `ws://127.0.0.1:7270/ttrm` (TTR-M model metadata) and
`ws://127.0.0.1:7270/lsp` (TTR-P language server, below).

## Security posture (S24)

Loopback-only (`127.0.0.1`), **no auth, no token, no TLS**, single user. The
loopback bind *is* the security boundary. The server never binds `0.0.0.0`.

## Protocol

JSON-RPC 2.0 over WebSocket, **one frame = one message**. `protocolVersion` is
**1** (from `ttrm/getStatus`).

### Methods (all read-only, delegating to `MetadataQuery`/`WorldResolver` — MD2/MD5)

| Method | Params | Result |
|---|---|---|
| `ttrm/getStatus` | — | `{protocolVersion, modelVersion?, loadedAt?, repoRoot, issues[]}` — the handshake; answers even before a model is loaded (`modelVersion: null`). |
| `ttrm/getModelIndex` | — | `{packages[], schemas[], areas[], counts, modelVersion}` |
| `ttrm/getModelGraph` | `{scope?, edgeTypes?}` | `{nodes:[{qname,kind,label,schema,pkg}], edges:[{from,to,type}]}` |
| `ttrm/getObject` | `{qname}` | `{object, sourceLocation, references[]}` |
| `ttrm/search` | `{query, algorithm?, limit?}` | `{hits:[{qname,score,matchedField}]}` |
| `ttrm/refresh` | `{force?}` | `{outcome, modelVersion}` |
| `ttrm/getWorld` | `{qname?}` | no qname → `{worlds[]}`; with qname → resolved `{qname,fingerprint,engines[],executors[],storages[],staging}` |

### Notifications (server → client)

- `ttrm/modelChanged` — `{modelVersion}`. Fired on every registry swap (file-watch
  reload or a `ttrm/refresh` that produced a new version).

### Errors

`{code, message, data:{kind, …}}`. Reserved codes:

| Code | Meaning |
|---|---|
| `-32700` | parse error (malformed JSON) |
| `-32600` | invalid request — **batch (JSON array) is not supported in v1** (additive later without a protocolVersion bump) |
| `-32601` | method not found |
| `-32602` | invalid params |
| `-32603` | internal error |
| `-32000` | model not loaded |
| `-32001` | not found (object / world; `data.kind` carries the structured failure name) |
| `-32002` | bad scope (unknown package in `getModelGraph.scope`) |

## TTR-P WS-LSP at `/lsp` (TTR-P P5.1)

`ws://127.0.0.1:7270/lsp` hosts the **same** Kotlin TTR-P language server built in
Phase 4 (`org.tatrman.ttrp.lsp.TtrpLanguageServer`) — one LSP across hosts (G-b): no
LSP logic lives here, the host only bridges transports. The wire is **LSP JSON-RPC,
one message per WS text frame** (no `Content-Length` headers on the wire —
`WsJsonRpcBridge` converts to/from the framed byte streams lsp4j's `LSPLauncher`
expects). Each connection gets an independent server session; reconnect works (S24
means single *user*, not single *connection*). The project root is resolved by
walk-up from each document URI, exactly as the stdio LSP does.

Standard LSP (diagnostics, hover, definition, rename, formatting) plus the custom
`ttrp/*` methods (contracts §4): `getGraph`, `getWorld`, `transpile`, `run`,
`explain`, `validate`, `authoringContext`. `ttrp/getGraph` returns the **authored**
graph (containers, authored node kinds, ports, edges) plus a derived orchestration
overlay (islands, synthesized transfers, waves); `ttrp/getWorld` returns the
resolved world's engines/executors/storages/staging (the Designer target palette).

## The `installTtrmProtocol` / `installTtrpLsp` seam (MD8)

`Application.designerServerModule(deps)` installs the WebSockets plugin **once**,
then calls `installTtrmProtocol(deps)` and `installTtrpLsp()`, each of which **only
adds routes** (`routing { webSocket("/ttrm"|"/lsp") { … } }`) — no plugin re-install
clash. The seam is proven by `CoexistingProtocolInstallersSpec`.
