# Tasks · P5 · Stage 5.3 — Canvas render (Designer frontend fork)

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

`packages/ttrp-designer` (`@tatrman/ttrp-designer`) — a fork of the TTR-M Designer (React 19 + Vite + Cytoscape.js + Tailwind) that connects to the Stage-5.1 server over WS and **renders** the hero read-only: two-level view (orchestration = container-collapsed execution graph + program leaves; drill-in recurses, C1-a β), skin roster v1 = "Alteryx/KNIME" + "Enso" per-canvas (C1-b), binary auto/manual layout wired to 5.2's contract, fragment drill-ins as read-only auto-only derived sub-graphs (C1-b-iv). Editing is Stage 5.4.

**Test discipline:** Vitest + jsdom + Cytoscape headless (`cytoscape({ headless: true })` or container-less mount, as the TTR-M fork already does in `src/cy/__tests__/`). **No browser E2E, no Playwright in v1.**

## Pre-flight (all must pass before T5.3.1)

- [ ] Stages 5.1 + 5.2 DONE (`./gradlew :packages:kotlin:ttrp-designer-server:test` green incl. ViewState E2E).
- [ ] TTR-M designer builds: `pnpm --filter @tatrman/designer build` (post-S7 name; it is the fork source at `packages/designer/`).
- [ ] Freeze one real `ttrp/getGraph` hero response from the 5.1 server (run the WsLspTransportSpec harness, dump JSON) — T5.3.2's fixture must match the live wire shape, then drift = contract break.

## Tasks

### T5.3.1 · Fork `packages/designer` → `packages/ttrp-designer`

- [ ] Copy `packages/designer/` → `packages/ttrp-designer/`; `package.json` name `@tatrman/ttrp-designer`, keep scripts (`dev`, `build`, `typecheck`, `lint`, `test`), keep deps: react 19, cytoscape, tailwind, `vscode-jsonrpc`/`vscode-languageserver-protocol`; **drop** `@modeler/lsp` workspace dep (no in-browser worker LSP — the LSP is the JVM server) and `cytoscape-cose-bilkent` (auto-layout comes from the server, T5.2.6 — no client layout engine).
- [ ] Delete TTR-M-specific surfaces: `CreateGraphWizard.tsx`, `GraphPicker.tsx`, `MissingObjectsDrawer.tsx`, `AddObjectPicker.tsx`, `NlPane.tsx`, `fs/demo-loader.ts`, their tests. Keep and adapt: `components/Canvas.tsx`, `components/Header.tsx`, `components/InspectorPanel.tsx`, `components/ErrorBoundary.tsx`, `Toast.tsx`, `cy/adapter.ts`, `cy/glyph-renderer.ts`, `state/designer-state.ts` + `designer-reducer.ts`, `util/debounce.ts`, `src/test-setup.ts`.
- [ ] Wire into `pnpm-workspace.yaml` (already globbed by `packages/*`), tsconfig extends `tsconfig.base.json`, ESLint (no `any`).

**Verify:** `pnpm --filter @tatrman/ttrp-designer build && pnpm --filter @tatrman/ttrp-designer test` green (surviving tests only), `pnpm --filter @tatrman/ttrp-designer typecheck` green.

### T5.3.2 · TDD: canned payload fixtures + failing component tests (red)

- [ ] `src/__tests__/fixtures/hero-getGraph.json` — the frozen 5.1 response (pre-flight dump). Shape sketch to assert against (trim/extend to match the live wire — the file, once committed, is the 5.1↔5.3 contract; regenerate only deliberately):

```json
{ "graph": {
    "program": "hero.ttrp",
    "containers": [
      { "path": "db_prep", "target": "erp_pg", "derived": false,
        "ports": { "out": ["accounts"] },
        "nodes": [ { "zeta": "db_prep/accounts#1", "kind": "Load", "range": {...} },
                   { "zeta": "db_prep/accounts#2", "kind": "Filter", "range": {...} } ],
        "edges": [ { "from": "db_prep/accounts#1", "to": "db_prep/accounts#2", "type": "data" } ] },
      { "path": "crunch", "target": "polars_local", "derived": false,
        "ports": { "in": ["accounts"], "out": ["high", "low"], "err": ["problems"] },
        "nodes": [ { "zeta": "crunch/sales#1", "kind": "Load" },
                   { "zeta": "crunch/joined#1", "kind": "Join" },
                   { "zeta": "crunch/sums~1", "kind": "Aggregate" },
                   { "zeta": "crunch/branch~1", "kind": "Branch" } ], "edges": [ ... ] } ],
    "leaves": [ { "zeta": "transfer~1", "kind": "Transfer", "synthesized": true },
                { "zeta": "big_customers~1", "kind": "Display" },
                { "zeta": "problems~1", "kind": "Display" } ],
    "edges": [ { "from": "db_prep.accounts", "to": "crunch.accounts", "type": "data", "via": "transfer~1" },
               { "from": "crunch.high", "to": "big_customers~1", "type": "data" } ] },
  "provenance": {}, "derived": [],
  "autoLayout": { "program": { "db_prep": {"layer":0,"index":0}, "crunch": {"layer":1,"index":0}, "big_customers~1": {"layer":2,"index":0} },
                   "crunch":  { "crunch/sales#1": {"layer":0,"index":0}, "crunch/joined#1": {"layer":1,"index":0} } } }
```

- [ ] `src/__tests__/fixtures/hero.ttrl.json` — parsed-layout twin of 5.2's `hero.ttrl` fixture (manual `program` canvas: `db_prep`/`crunch`/`big_customers~1` positions, skin `alteryx-knime`; `crunch` canvas auto + `enso`).
- [ ] Failing Vitest component tests (jsdom + headless Cytoscape):
  - `src/__tests__/orchestration-render.test.tsx` — feed hero-getGraph.json: canvas shows 2 container nodes + display leaves + the synthesized transfer edge; NO inner op nodes at top level (C1-a β).
  - `src/__tests__/drill-in.test.tsx` — enter `crunch`: 4 op nodes incl. `err` port stub; breadcrumb back; nested containers would render collapsed (assert on a synthetic nested fixture).
  - `src/__tests__/skins.test.tsx` — per-canvas skin switch alteryx-knime ⇄ enso: node classes/labels change, element count identical, manual positions unchanged (C1-b-ii/iii).
  - `src/__tests__/layout-modes.test.tsx` — auto canvas renders from `autoLayout` (orientation-mapped); simulated drag → mode flips manual + full snapshot sent via `setLayout`; reset-to-auto discards; derived canvas: drag is a no-op (C1-b-iv).

**Verify:** `pnpm --filter @tatrman/ttrp-designer test` — the four new suites fail (red) for want of implementation, not for fixture/import errors.

### T5.3.3 · WS LSP client

- [ ] `src/lsp/ws-client.ts`: replace the Web-Worker transport (`lsp-client.ts` fork source) with a WebSocket one to `ws://127.0.0.1:<port>/lsp` — one JSON-RPC message per text frame (the 5.1 wire rule). Use `vscode-ws-jsonrpc` (`toSocket`, `WebSocketMessageReader/Writer`) with `createProtocolConnection` as in the fork source; if adding the dep is undesirable, hand-roll a ~40-line `MessageReader/Writer` pair over `WebSocket` — either way, NO Content-Length framing on the wire.
- [ ] Typed client surface (mirror `LspClient` in the fork source): `initialize`, `openDocument`, `getGraph(uri, version)`, `getWorld(uri)`, `getLayout(uri)`, `setLayout(uri, layout)`, `onDiagnostics(handler)`; `applyGraphEdit`/`run` stubbed until 5.4. Port/URL from `VITE_TTRP_LSP_URL` env (default `ws://127.0.0.1:9257/lsp`).
- [ ] Unit test with a mock `WebSocket` (jsdom): request/response correlation + one-frame-one-message framing.

**Verify:** `pnpm --filter @tatrman/ttrp-designer test -- ws-client` green.

### T5.3.4 · Two-level view state + orchestration derivation

- [ ] `src/state/view-stack.ts`: canvas stack — `["program"]` (orchestration) → push container path on double-click → breadcrumb pop; current canvas key drives skin/mode lookup in the layout (keys match `.ttrl` canvas keys exactly).
- [ ] `src/graph/derive-orchestration.ts`: from the getGraph payload build the top-level element set = **collapsed containers + program-level leaves (movement/store/display) + cross-container data edges (synthesized transfers visible as edges/nodes) + control edges** — exactly the derived execution-layer graph the author sees as waves (C1-a β consequence 1). Drill-in element set = the container's nodes/edges with its **child containers collapsed** (same rule at every level, C1-a consequence 4; recursion via the same function).
- [ ] `src/state/designer-reducer.ts`: adapt actions — `graphLoaded`, `layoutLoaded`, `enterContainer`, `exitContainer`, `setSkin(canvasKey, skin)`, `positionsDragged`, `resetToAuto`.

**Verify:** `pnpm --filter @tatrman/ttrp-designer test -- orchestration-render drill-in` green.

### T5.3.5 · Cytoscape adapter + skin layer

- [ ] `src/cy/adapter.ts`: element mapping (nodes get `data: { zeta, kind, containerPath, derived, synthesized }`; ports rendered as compound children or edge endpoints per the fork source's pattern); position input = manual positions (ζ-keyed) or orientation-mapped `autoLayout` `{layer,index}` → pixels (`x = layer*220, y = index*110` L→R; transposed T↓) — mapping in `src/cy/orientation.ts`, pure + unit-tested.
- [ ] Skin contract `src/skins/types.ts`: `interface Skin { id: 'alteryx-knime' | 'enso'; orientation: 'LR' | 'TD'; style: cytoscape.StylesheetJson; nodeLabel(n): string; nodeClasses(n): string[] }`.
- [ ] `src/skins/alteryx-knime.ts`: icon-per-node-kind (glyph via the forked `cy/glyph-renderer.ts`; one glyph per T10 kind: Load/Store/Transfer/Filter/Join/Aggregate/Branch/Sort/Union/Display/Container…), **data edges prominent, control edges hidden or minimal**, orientation LR (data flows left→right).
- [ ] `src/skins/enso.ts`: text-forward nodes — label = node description if present, else the code snippet from its source range (provenance text supplied in the getGraph payload), orientation TD (control/top-down convention per C1-b).
- [ ] Skin is **per-canvas** (C1-b-iii): selection in `Header.tsx` writes `skin` for the current canvas via `setLayout` (committed shared truth, C1-c-ii); switching never touches positions (C1-b-ii).

**Verify:** `pnpm --filter @tatrman/ttrp-designer test -- skins` green.

### T5.3.6 · Binary layout modes on canvas + orphan badges

- [ ] Auto mode: render server `autoLayout`; persist nothing (P2); no per-node drag handles… drag **is** allowed — and it's the flip: on first drag, snapshot **all** rendered positions of the canvas → `setLayout` wholesale with `mode: manual` (C1-b layout decision); debounce via the forked `util/debounce.ts` for subsequent manual drags.
- [ ] "Reset to auto" affordance (per-canvas, in `Header.tsx`): `setLayout` with the canvas block rewritten `mode: auto`, nodes dropped; canvas re-renders from `autoLayout`.
- [ ] Orphaned ζ entries (flagged by 5.2's getLayout / `TTRP-LAY-001`): node badge "layout reset" + toast — visible, never silent (C1-c-i point 3); orphaned nodes render at auto positions.
- [ ] Derived canvases: drag disabled entirely (auto-only, C1-b-iv).

**Verify:** `pnpm --filter @tatrman/ttrp-designer test -- layout-modes` green.

### T5.3.7 · Fragment drill-in: read-only derived sub-graphs

- [ ] Containers whose body is a fragment (`"""sql`/`"""pandas`/`"""ttrb`) and derived containers of bare-fragment programs arrive in getGraph with `derived: true` sub-graphs — drill-in renders them in the current skin, **read-only** (no edit affordances even after 5.4), **auto-only**, with a "derived from <dialect> fragment" banner.
- [ ] Provenance hover: node hover shows its source text span (E-d provenance from the payload); no jump-to-editor in v1 (no editor pane) — display file:line instead.
- [ ] Test `src/__tests__/fragment-drill-in.test.tsx`: synthetic fixture of a `"""sql` container decomposed into Join/Filter/Project nodes → renders read-only in both skins; drag no-op; never appears in setLayout payloads.

**Verify:** `pnpm --filter @tatrman/ttrp-designer test` — full suite green; `pnpm --filter @tatrman/ttrp-designer build` green. Manual smoke: start the 5.1 server on the hero project, `pnpm --filter @tatrman/ttrp-designer dev` → orchestration view of the hero renders at `http://localhost:5173` (loopback pair).

## Definition of DONE (stage)

- Hero renders read-only against the live 5.1 server: orchestration (2 containers + transfer + displays) and drill-ins, in both skins, per-canvas skin choice persisted to `.ttrl`.
- Auto→manual flip on first drag, reset-to-auto, orphan badges, derived canvases read-only/auto-only — all Vitest-covered (jsdom + headless Cytoscape; zero Playwright).
- `hero-getGraph.json` committed as the frozen wire contract with 5.1.

## Blockers

*(record here; STOP on hit)*

## References

- Plan Stage 5.3 · architecture §7 · contracts §4 (getGraph result)
- Decisions: C1-a β (two-level), C1-b (skins, binary layout, sub-forks i–iv), C1-c-ii (committed `.ttrl`), ζ (badge visibility), E-d (provenance hover), P2
- Fork source: `packages/designer/` (structure per CLAUDE.md: React 19 + Vite + Cytoscape.js + Tailwind; `src/lsp-client.ts` is the transport pattern being replaced)
