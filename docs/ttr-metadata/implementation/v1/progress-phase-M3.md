# Progress · Phase M3 · Designer server + read-only frontend

> `[x]` = developer intent; verify against runtime before trusting.

## Status

**M3 code-complete & green locally 2026-07-05** (commits `aa2b419` M3.1, `4909126` M3.2).
The `ttr-designer-server` Ktor WS host and the frontend `ModelDataSource` adapter both
build and pass their suites (`:packages:kotlin:ttr-designer-server:test`; designer Vitest).
The one **open DONE-bar item** is the manual in-browser acceptance script (T3.2.7) — it is
a human review step (server-on-fixture → browse/graph/search → live reload) and has **not**
been run/recorded yet, so Phase M3 is code-complete but not signed off. The MD8 "host exists"
gate for TTR-P P5.1 is satisfied at the code level.

## M3.1 — Server host

- `:packages:kotlin:ttr-designer-server`: Ktor CIO, **loopback bind** (`127.0.0.1`, S24),
  `--repo`/`--port` CLI; composition root `DesignerServerDeps` (LocalFsStorage → FileBasedSource
  → MetadataLoader/Reconciler → initial swap → MetadataRefresher).
- WS JSON-RPC endpoint `/ttrm`: getStatus (handshake, `protocolVersion=1`), getModelIndex,
  getModelGraph (package + **schema** scope, edgeTypes), getObject, search, getWorld.
- File watcher (`NioRepoWatcher`, 200 ms debounce, recursive) → refresher → `ttrm/modelChanged`
  push via `NotificationBroadcaster` (concurrent fan-out, per-session timeout).
- WS contract Kotest suite with an in-process client.

## M3.2 — Frontend adapter

- `ModelDataSource` interface (contracts §6): `WorkerLspDataSource` (existing worker path, edit:true)
  + `WsDesignerServerDataSource` (`ttrm/*`, edit:false); explicit selection (P2, no sniffing).
- Read-only gating: `capabilities.edit=false` — WS mode is a dedicated read-only component
  (`WsModeApp`) with no edit machinery imported. **Recorded divergence** from the plan's
  "capability-gate the shared App" mechanism (see M3.2 commit body) — functionally equivalent,
  structurally different (GraphPicker/InspectorPanel not reused).
- Thin `ttrm-adapter.ts` (RM8): row-less boxes + on-demand getObject; canvas `ModelGraph`
  enrichment deferred to C1-f.
- Vitest against canned `ttrm/*` fixtures; `json-rpc-ws-client` id-correlation + timeout suite.

## review-025 fixes folded in

- **Security:** `/ttrm` now validates the WS handshake `Origin` (loopback/absent allowed,
  non-loopback browser origins refused) — closes the cross-site-WebSocket-hijacking vector the
  loopback bind alone did not cover.
- **Correctness:** `getModelGraph` now honors `scope.schema` end-to-end (adapter forwarded it;
  server handler filters by schema-code token) — per-schema navigation was previously a no-op
  (every schema tab rendered the full graph).
- **Robustness:** broadcaster fan-out is non-blocking with a per-session timeout (was
  `runBlocking`, a head-of-line stall on a slow client); `WsModeApp` guards out-of-order
  `getObject`/`getModelGraph` completions with generation counters and surfaces `onClose`;
  the JSON-RPC client nulls its socket ref on drop so a post-close request rejects cleanly.

## Open / deferred

- [ ] **T3.2.7 manual acceptance script** — not yet run (human review); Phase-DONE bar.
- Designer-server auth / multi-user → v2 (S24 loopback holds for v1).
- `ttrm/getModelGraph` canvas-grade node payloads (rows/fk/cardinalities) → C1-f.
