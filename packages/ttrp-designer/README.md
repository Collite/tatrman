# @tatrman/ttrp-designer

The TTR-P graphical Designer (Phase 5.3) — a React 19 + Vite + Cytoscape.js + Tailwind
front-end that connects to the repo-attached **Designer server** (`ttr-designer-server`)
over the `/lsp` WebSocket and renders a TTR-P program **read-only** (edit lands in 5.4).

Forked in spirit from `@tatrman/designer` (the TTR-M Designer): it reuses the WS JSON-RPC
client + Cytoscape rendering patterns but drops the in-browser worker LSP (the LSP is the
JVM server) and TTR-M-specific surfaces.

## The two-level view (C1-a β)

- **Orchestration** (`program` canvas): collapsed containers + program leaves
  (movement/store/display) + cross-container edges (a synthesized transfer shows its `via`
  id) — the derived execution-layer graph the author sees as waves. No inner op nodes.
- **Drill-in** (a container canvas): that container's authored op nodes + internal edges
  (child containers collapsed — the same rule at every level, `deriveContainer` recurses).
  Fragment (`"""sql`/`"""pandas`/`"""ttrb`) and derived sub-graphs render **read-only**,
  auto-only, with a "derived from `<dialect>` fragment" banner.

The canvas shows the **authored** graph (getGraph is authored-not-lowered, A4): a `Branch`
renders as `Branch`, not the polars `branch→filter` lowering.

## Skins (C1-b, per-canvas)

`alteryx-knime` (icon-per-kind, data edges prominent, flow L→R) and `enso` (text-forward,
top-down). Switching a skin changes labels/classes only — never positions (C1-b-ii). The
per-canvas choice persists to `.ttrl` via `setLayout` (Stage 5.2).

## Layout (binary auto/manual, C1-b)

Auto canvases render from the server's deterministic `autoLayout` (`{layer, index}`
abstract coords, mapped to pixels per skin orientation — `cy/orientation.ts`). The first
drag flips a canvas to manual (`snapshotToManual` → wholesale `setLayout`); reset-to-auto
drops the positions. Orphaned ζ entries (flagged by `getLayout`/`TTRP-LAY-001`) badge
"layout reset" and fall back to auto — visible, never silent.

## Run

```bash
# 1. Start the Designer server on the hero project (loopback, no auth — S24):
./gradlew :packages:kotlin:ttr-designer-server:run --args='--repo <hero-project> --port 9257'
# 2. Start the Designer (loopback pair):
pnpm --filter @tatrman/ttrp-designer dev   # http://localhost:5173
```

`VITE_TTRP_LSP_URL` (default `ws://127.0.0.1:9257/lsp`) and `VITE_TTRP_DOC_URI` override
the endpoint + document.

## Test

`pnpm --filter @tatrman/ttrp-designer test` — Vitest + jsdom + **headless Cytoscape**
(`cytoscape({ headless: true })`); no Playwright/browser E2E in v1. `hero-getGraph.json`
is the frozen 5.1↔5.3 wire contract (regenerate deliberately).
