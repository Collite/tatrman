# Tasks · M3 · Stage 3.2 — Designer frontend data-source adapter (read-only WS mode)

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Feature decisions MD1–MD8 → `../../architecture/architecture.md` §8 · TTR-P decision IDs → `../../../ttr-p/design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

`packages/designer` (pnpm package **`@modeler/designer`**, React 19 + Vite 6 + Cytoscape 3.30 + Tailwind 3, Vitest 4/jsdom) gains a pluggable **`ModelDataSource`** (contracts §6, MD6): the existing browser-worker LSP path wrapped as `WorkerLspDataSource` with **zero behavior change** (regression-pinned first), plus a new `WsDesignerServerDataSource` speaking the `ttrm/*` WS JSON-RPC protocol to `ttr-designer-server` (M3.1). Backend selection is **explicit** — `?server=ws://127.0.0.1:7270` URL param, never sniffed (P2). WS mode is read-only: `capabilities.edit === false` hides every edit affordance. Final task = the manual acceptance script that **is the M3 DONE bar** (plan M3.2).

**How the Designer gets data today** (verified 2026-07-05; this is what "zero behavior change" pins):
- `packages/designer/src/lsp-client.ts` — boots `@modeler/lsp/browser?worker` (Web Worker), `vscode-languageserver-protocol/browser` connection; custom methods consumed: `modeler/setProjectRoot`, `modeler/listGraphs {projectRoot}→{graphs: GraphMetadata[]}`, `modeler/getGraph {uri}→GetGraphResponse`, `modeler/getPackageGraph`, `modeler/getModelGraph {textDocument:{uri}, schema:'db'|'er'}→ModelGraph`, `modeler/getLayout|setLayout|exportLayout`, `modeler/addObjectToGraph|removeObjectFromGraph|createGraph|applyGraphEdit`, `modeler/getSymbolDetail {qname}`, `modeler/listSymbols {kinds?,limit?}`.
- Renderable payload: `ModelGraph` from `packages/lsp/src/model-graph.ts` — node `{qname, kind:'table'|'view'|'entity', name, schemaCode, label, sourceUri, sourceLocation, rows[]}` (row: `{name,qname,kind:'column'|'attribute',type,isKey,optional,isNameAttribute,isCodeAttribute}`), edge `{id,qname,kind:'fk'|'relation',fromNode,toNode,fromCardinality,toCardinality,sourceUri,sourceLocation}`; adapted to Cytoscape in `src/cy/adapter.ts`.
- App flow (`src/App.tsx`): project loaded via File System Access API (`src/fs/file-system.ts`) or demo loader (`src/fs/demo-loader.ts`) → all `.ttrm`/`.ttrg` opened into the worker via `didOpen` → `listGraphs` feeds `GraphPicker` → `getGraph`/`getModelGraph` feed `Canvas` → `getSymbolDetail` feeds `InspectorPanel`.

## Pre-flight (all must pass before T3.2.1)

- [ ] `pnpm --filter @modeler/designer test` green at baseline; `pnpm --filter @modeler/designer typecheck` green; `pnpm -r build` green.
- [ ] M3.1 far enough for local runs: `./gradlew :packages:kotlin:ttr-designer-server:run --args='--repo <fixture> --port 7270'` serves `ttrm/getStatus` (needed only from T3.2.7; T3.2.1–T3.2.6 run against canned fixtures — do not block early tasks on the server).
- [ ] Read contracts §4 (wire DTOs — **normative**) and §6 (`ModelDataSource` — transcribed in T3.2.2); read the DTO-delta note in `tasks-m3-s3.1-server-host.md` §References.
- [ ] Confirm Vitest environment: `packages/designer/vitest.config.ts` + `src/test-setup.ts` (jsdom, @testing-library/react) — new suites follow the existing `src/**/__tests__/*.test.ts(x)` pattern.

## Tasks

### T3.2.1 · TEST-FIRST: regression pins on the current worker data path (green *before* any refactor)

Write these against the **current code**, commit them passing, then refactor under them. They define "zero behavior change" for T3.2.3.

- [ ] `src/data/__tests__/worker-path-regression.test.ts` — mock the protocol connection (same technique as existing `src/__tests__/app-demo.test.tsx` mocks) and pin, method by method, the exact request payloads `lsp-client.ts` sends today and the response shapes the app consumes: `modeler/listGraphs {projectRoot}`, `modeler/getGraph {uri}`, `modeler/getModelGraph {textDocument:{uri}, schema}` → full `ModelGraph` field roster (node/row/edge fields listed above — assert *every* field name), `modeler/getSymbolDetail {qname}`, `modeler/listSymbols {kinds?,limit?}`.
- [ ] Snapshot-pin `src/cy/adapter.ts` input expectations: one representative `ModelGraph` fixture → Cytoscape elements (extend `src/cy/__tests__/adapter.test.ts` only if a case is missing; don't duplicate).

**Verify:** `pnpm --filter @modeler/designer test -- worker-path-regression` green on unmodified `main`-state code.

### T3.2.2 · `ModelDataSource` interface + DTO types + contracts-delta record

- [ ] `src/data/model-data-source.ts` — transcribe contracts §6 **verbatim**:

  ```ts
  interface ModelDataSource {                       // implemented by: WorkerLspDataSource (existing path),
    getModelIndex(): Promise<ModelIndex>            //                 WsDesignerServerDataSource (new)
    getModelGraph(scope?: GraphScope): Promise<ModelGraphPayload>
    getObject(qname: string): Promise<ObjectDetail>
    search(q: SearchParams): Promise<SearchHit[]>
    onModelChanged(cb: (v: string) => void): Disposable
    readonly capabilities: { edit: boolean }        // WS source: edit=false → designer hides edit affordances
  }
  ```

- [ ] `src/data/ttrm-types.ts` — TS types for the `ttrm/*` wire DTOs per contracts §4: `TtrmIndex {packages[], schemas[], areas[], counts, modelVersion}`, `TtrmGraph {nodes: {qname,kind,label,schema,pkg}[], edges: {from,to,type}[]}` (`type: 'DEFINES'|'REFERENCES'|'MAPS_TO'|'USES'`), `TtrmObjectDetail {object, sourceLocation, references[]}`, `TtrmSearchHit`, `TtrmStatus {protocolVersion, modelVersion, loadedAt, issues[], repoRoot}`.
- [ ] **Record the DTO delta — do NOT silently change either side (contracts change rule):** contracts §4's `getModelGraph` node/edge shape (`{qname,kind,label,schema,pkg}` / `{from,to,type}`) is a *dependency-graph* payload; the Canvas renders the richer `ModelGraph` (rows[], `fk|relation` edges, cardinalities — see deliverable header). §4's claim "shaped after the payload the Designer already renders (MD6)" does not hold for the canvas payload. v1 resolution implemented in T3.2.5b: WS mode renders `ttrm/getModelGraph` nodes as row-less boxes and enriches on-demand via `ttrm/getObject`; whether §4 grows renderable fields (rows, cardinalities) is a contracts decision deferred to the C1-f arc. Add the **changelog entry to `docs/ttr-metadata/architecture/contracts.md`** stating exactly this (coordinate with M3.1 T3.1.7's entry — one combined bullet is fine); note also that §6 covers only the *read* contract — graph-list/layout/edit operations stay on `LspClient`, reachable only when `capabilities.edit` (that's the gating mechanism, T3.2.6).

**Verify:** `pnpm --filter @modeler/designer typecheck` green; changelog entry present (`grep -n "M3.2" docs/ttr-metadata/architecture/contracts.md`).

### T3.2.3 · `WorkerLspDataSource` — wrap the existing path, zero behavior change

- [ ] `src/data/worker-lsp-data-source.ts` — `class WorkerLspDataSource implements ModelDataSource`, constructed *around* the existing `LspClient` (from `createLspClient()`) + project context (root, known uris). Mappings (thin, no new semantics): `getModelIndex` ← compose `modeler/listGraphs` + `modeler/getPackageGraph`; `getModelGraph(scope)` ← `modeler/getModelGraph(uri, scope.schema as 'db'|'er')`; `getObject` ← `modeler/getSymbolDetail`; `search` ← `modeler/listSymbols` + the same client-side filtering AddObjectPicker does today (pin, don't improve); `onModelChanged` → no-op `Disposable` (the worker path has no file watching — documents are pushed via `didOpen`); `capabilities = { edit: true }`. Expose `readonly lspClient: LspClient` as the edit-path escape hatch (see T3.2.2 note).
- [ ] `App.tsx` reads through the data source for the read paths it covers; all other call sites keep using `client` via the escape hatch — **no behavior change**: the T3.2.1 regression suite must stay green *unmodified* (any edit to those pins = a behavior change = STOP, blocker).
- [ ] `src/data/__tests__/worker-lsp-data-source.test.ts` — unit: each interface method issues exactly the pinned `modeler/*` request.

**Verify:** `pnpm --filter @modeler/designer test` — full suite incl. untouched `worker-path-regression` green.

### T3.2.4 · `WsDesignerServerDataSource` + JSON-RPC client + canned `ttrm/*` fixtures

- [ ] `src/data/json-rpc-ws-client.ts` — browser `WebSocket`, JSON-RPC 2.0: monotonically increasing numeric `id`, pending-request map for **request-id correlation**, per-request timeout (default 10 s → rejected Promise), notification dispatch (`method` present + no `id` → handler registry), error objects `{code,message,data}` rejected as typed `TtrmRpcError`. One message per text frame; **no batch** (matches M3.1). No reconnect logic in v1 beyond a `close` event surfacing as a toast (P2: no silent retry heuristics).
- [ ] `src/data/ws-designer-server-data-source.ts` — `implements ModelDataSource`: on connect, calls `ttrm/getStatus` and **verifies `protocolVersion === 1`** (mismatch → hard error shown to the user, contracts §4 handshake); maps `getModelIndex→ttrm/getModelIndex`, `getModelGraph→ttrm/getModelGraph`, `getObject→ttrm/getObject`, `search→ttrm/search`; `onModelChanged` subscribes to the `ttrm/modelChanged` notification (`params.modelVersion` → callback); `capabilities = { edit: false }`.
- [ ] Canned wire fixtures in `src/data/__tests__/fixtures/ttrm/` — JSON files matching contracts §4 DTOs exactly (these double as the frontend's contract record; if M3.1's server ever disagrees, one side is wrong — reconcile via contracts.md, not ad hoc):
  - `get-status.json` — `{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":1,"modelVersion":"m-3f9a","loadedAt":"2026-07-05T12:00:00Z","issues":[],"repoRoot":"/work/fixture-repo"}}`
  - `get-model-index.json` — `{"jsonrpc":"2.0","id":2,"result":{"packages":[{"name":"acme.erp"}],"schemas":[{"code":"db","name":"erp_db","pkg":"acme.erp"},{"code":"er","name":"erp_er","pkg":"acme.erp"}],"areas":[],"counts":{"objects":42,"schemas":2},"modelVersion":"m-3f9a"}}`
  - `get-model-graph.json` — `{"jsonrpc":"2.0","id":3,"result":{"nodes":[{"qname":"acme.erp.erp_db.customers","kind":"table","label":"customers","schema":"erp_db","pkg":"acme.erp"},{"qname":"acme.erp.erp_db.orders","kind":"table","label":"orders","schema":"erp_db","pkg":"acme.erp"}],"edges":[{"from":"acme.erp.erp_db.orders","to":"acme.erp.erp_db.customers","type":"REFERENCES"}]}}`
  - `get-object.json` — `{"jsonrpc":"2.0","id":4,"result":{"object":{"qname":"acme.erp.erp_db.customers","kind":"table"},"sourceLocation":{"file":"packages/erp/db.ttrm","line":12,"column":0},"references":[]}}`
  - `search.json` — `{"jsonrpc":"2.0","id":5,"result":{"hits":[{"qname":"acme.erp.erp_db.customers","kind":"table","label":"customers"}]}}`
  - `model-changed.json` — `{"jsonrpc":"2.0","method":"ttrm/modelChanged","params":{"modelVersion":"m-4b01"}}`
  - `error-not-found.json` — `{"jsonrpc":"2.0","id":6,"error":{"code":-32001,"message":"not-found","data":{"kind":"NotFound","qname":"acme.erp.erp_db.no_such"}}}`
- [ ] `src/data/__tests__/ws-designer-server-data-source.test.ts` — mock `WebSocket` (jsdom has none; a tiny `FakeWebSocket` in test-setup or per-suite) fed with the fixture frames: id correlation under out-of-order responses; timeout rejection; `protocolVersion !== 1` → connect fails; `modelChanged` → subscriber fires with `"m-4b01"`; `-32001` → `TtrmRpcError{code, data.kind==="NotFound"}`; `capabilities.edit === false`.

**Verify:** `pnpm --filter @modeler/designer test -- ws-designer-server-data-source` green.

### T3.2.5a · Explicit backend selection (P2 — never sniffed)

- [ ] `src/data/select-data-source.ts` — precedence, documented in code + `packages/designer/README.md`: **(1)** URL param `?server=ws://127.0.0.1:7270` → `WsDesignerServerDataSource` (the value is the WS origin; the client appends `/ttrm`); **(2)** otherwise → worker path (landing card exactly as today). Malformed/non-`ws://127.0.0.1`|`ws://localhost` values → visible error, no fallback-with-guessing (P2; S24 means a loopback URL is the only sane v1 value — warn on anything else, still attempt only what was explicitly given). `?demo=` keeps its current meaning (worker path); `?server=` + `?demo=` together → error, they select different backends.
- [ ] Unit test `src/data/__tests__/select-data-source.test.ts` — each precedence row, incl. the conflict case.

**Verify:** `pnpm --filter @modeler/designer test -- select-data-source` green.

### T3.2.5b · Server-mode UI wiring: browse index, render graph, read-only search

WS mode skips the landing card / File System Access flow entirely (there are no local files — the server owns the repo):

- [ ] `App.tsx`: when the selected source is WS — connect, `getModelIndex`, and feed the browse tree; reuse `GraphPicker` to list the index's schemas/packages (it lists `.ttrg` graphs today — accept the index's schema entries as the picker items in WS mode; smallest honest mapping, no fake `GraphMetadata`).
- [ ] Graph rendering: selected schema → `getModelGraph({schema})` → **new thin adapter** `src/cy/ttrm-adapter.ts` mapping the contracts-§4 node/edge DTO to Cytoscape elements (row-less node boxes labeled `label`, edge class per `type`) — do **not** force it through `src/cy/adapter.ts`'s `ModelGraph` shape (that's the recorded DTO delta, T3.2.2); node click → `getObject(qname)` → `InspectorPanel` detail.
- [ ] **Read-only search affordance:** today the only search-ish UI is inside `AddObjectPicker` (an edit affordance, hidden in WS mode) — the M3 DONE bar demands *search* in read-only mode, so add a minimal `src/components/SearchBox.tsx` in the header area (visible in WS mode; worker mode unchanged): input → `dataSource.search({query})` → hit list → click selects/centers the node.
- [ ] `onModelChanged`: re-fetch index + current graph, re-render canvas (preserve viewport), toast "model reloaded (<version>)".
- [ ] Component tests (`src/__tests__/ws-mode.test.tsx`) against the T3.2.4 fixtures: index renders; graph renders 2 nodes/1 edge; search shows the hit; a `modelChanged` frame triggers re-fetch.

**Verify:** `pnpm --filter @modeler/designer test -- ws-mode` green; `pnpm --filter @modeler/designer typecheck` green.

### T3.2.6 · `capabilities.edit === false` → hide every edit affordance

Gate on the data source's capability flag, not on transport type (the flag is the contract, §6). Affordances located in today's source (the gate list):

- [ ] `src/components/Header.tsx` — the **"+ Add object"** button (`onAddObject` prop) hidden; the layout-download button hidden in WS mode too (layout methods live on the worker `LspClient` only).
- [ ] `src/App.tsx` — never mount `AddObjectPicker` (`src/AddObjectPicker.tsx`), `MissingObjectsDrawer` (`src/MissingObjectsDrawer.tsx`) apply-actions, or `CreateGraphWizard` (`src/CreateGraphWizard.tsx`, mounted around App.tsx:322) when `!capabilities.edit`.
- [ ] `src/components/Canvas.tsx` — suppress the drag-to-persist path (the `modeler/setLayout` WorkspaceEdit apply, comment at Canvas.tsx≈42): dragging may still move nodes locally, nothing is persisted; no WorkspaceEdit code reachable in WS mode.
- [ ] Tests: `src/__tests__/read-only-gating.test.tsx` — render App with a stub WS source (`edit:false`): no "+ Add object", no wizard trigger, no drawer apply-actions; with the worker source (`edit:true`) the existing affordance suites (`src/__tests__/canvas-affordances.test.tsx`, `affordances-integration.test.tsx`) stay green **unmodified**.

**Verify:** `pnpm --filter @modeler/designer test` — full suite green, including untouched affordance + regression suites.

### T3.2.7 · Manual acceptance script — **the M3 DONE bar** (plan M3.2)

Run top-to-bottom on a clean checkout; check each box only when observed. Record the run (date, commit) at the bottom of this section.

- [ ] 1. `./gradlew :packages:kotlin:ttr-designer-server:run --args='--repo <abs-path-to-fixture-repo> --port 7270'` — server starts, logs the S24 loopback/no-auth notice. (Fixture repo = the M2 shared fixture project, contracts §8; copy it to a scratch dir first — step 8 modifies it.)
- [ ] 2. `pnpm --filter @modeler/designer dev` — Vite dev server on http://localhost:5173 (CLAUDE.md).
- [ ] 3. Open `http://localhost:5173/?server=ws://127.0.0.1:7270` — no landing card; connection succeeds; status/protocolVersion accepted.
- [ ] 4. **Browse:** the index tree shows the fixture's packages + db/er schemas.
- [ ] 5. **Graph:** select the db schema — canvas renders nodes + edges; click a node — InspectorPanel shows `getObject` detail.
- [ ] 6. **Search:** type a known object name in the SearchBox — hit appears; clicking centers/selects it on canvas.
- [ ] 7. **Read-only:** no "+ Add object" button, no create-graph wizard, no missing-objects apply actions; node drag does not persist anything (server log shows no write — it has no write methods; nothing errors).
- [ ] 8. **Live reload:** edit any `.ttrm` in the scratch fixture repo (rename a column, save) — within ~1 s (200 ms debounce + reload) the canvas updates without a browser refresh; toast shows the new model version.
- [ ] 9. Worker path unharmed: open `http://localhost:5173/` (no `?server=`) — landing card, demo loads, edit affordances present, exactly as before this stage.
- [ ] Record: run date · tatrman commit · server fixture path.

**Verify:** all nine boxes checked in one session ⇒ Stage M3.2 and Phase M3's plan DONE bar ("acceptance script passes") are met; TTR-P P5.1's "host exists" gate (MD8) confirmed live.

## Definition of DONE (stage)

- `pnpm --filter @modeler/designer test` green: worker-path regression pins (unmodified since T3.2.1), data-source units, WS client fixtures, ws-mode component suite, read-only gating; `pnpm --filter @modeler/designer typecheck` + `pnpm -r build` green.
- `ModelDataSource` matches contracts §6 verbatim; the §4↔canvas DTO delta is recorded in the contracts.md changelog (not silently patched on either side).
- Backend selection is explicit `?server=` with documented precedence; zero sniffing (P2); worker path byte-for-byte behavior-identical.
- T3.2.7 manual acceptance script fully checked = the M3 hero demo.

## Blockers

*(record here; STOP on hit)*

## References

- Plan Stage M3.2 · architecture §5 (frontend bullet) + §8 **MD6** (pluggable data source, no fork; protocol "shaped after" designer payloads — see delta below), **MD8** (host naming/gate) · contracts §4 (wire DTOs, port 7270, protocolVersion, error codes — normative), §6 (`ModelDataSource` — transcribed in T3.2.2)
- TTR-P decisions: **P2** (explicit selection, no sniffing, no retry heuristics) · **S24** (loopback URL is the only sane `?server=` value in v1)
- Current data path (pinned in T3.2.1): `packages/designer/src/lsp-client.ts` (all `modeler/*` methods + payloads) · `packages/lsp/src/model-graph.ts` (`ModelGraph`/node/row/edge shapes) · `src/cy/adapter.ts` (ModelGraph→Cytoscape) · `src/App.tsx` (flow) — package name `@modeler/designer`, Vitest 4 + jsdom (`vitest.config.ts`, `src/test-setup.ts`)
- **Recorded contract deltas (T3.2.2 changelog duty):** (1) contracts §4 `getModelGraph` DTO ≠ the renderable `ModelGraph` the canvas consumes (rows/cardinalities/fk-relation vs DEFINES/REFERENCES roster) — WS mode uses `ttrm-adapter.ts` + on-demand `getObject`; richer wire nodes deferred to C1-f; (2) contracts §6 covers the read contract only — edit/layout ops stay on `LspClient` behind `capabilities.edit`; (3) the designer has no standalone search UI today (`modeler/listSymbols` feeds the edit-only AddObjectPicker) — read-only `SearchBox` is new UI added by T3.2.5b to meet the DONE bar's "search"
- Server counterpart: [tasks-m3-s3.1-server-host.md](./tasks-m3-s3.1-server-host.md) (fixtures must stay in lockstep with its contract suite)
