# @tatrman/designer

**Studio Viewer** — the open, read-only view of TTR (Tatrman) models (v1: schema renderer), backed by the Tatrman LSP running as a browser Web Worker. React 19 + Vite + Cytoscape.js + Tailwind. Deployed via GitHub Pages. (`@tatrman/designer` is the package/codename; **Studio Viewer** is the product name — FO-33.)

> **Screenshot:** `docs/img/designer.png` (capture from the dev server with `samples/v1-metadata/` loaded — the screenshot lands here after the first deploy).

## What's in v1

- LSP integration for model loading (browser worker transport, no server round-trip)
- `db` schema rendering: tables + foreign-key edges, cose-bilkent auto-layout
- `er` schema rendering: entities + relations with cardinality glyphs (Crow's foot)
- Inspector panel with symbol details, source-file:line copy, and clickable Referenced-by navigation
- Layout persistence: node positions, per-schema viewport, and display mode round-trip
- Demo mode via `?demo=v1-metadata` query parameter
- Layout export (browser mode) via the **Export Layout** button
- GitHub Pages deployment on push to `main` when designer / LSP / samples change

## Quick start

```bash
cd packages/designer
pnpm install
pnpm run dev      # http://localhost:5173
pnpm run build    # outputs to dist/ (with samples copied via the Vite plugin)
```

## Backend selection (worker vs. server)

Which backend serves the model is **explicit, never sniffed** (P2):

| URL | Backend | Mode |
|---|---|---|
| `http://localhost:5173/` | Worker LSP (this browser) | full edit |
| `http://localhost:5173/?demo=v1.1-mini` | Worker LSP + demo project | full edit |
| `http://localhost:5173/?server=ws://127.0.0.1:7270` | `ttr-designer-server` over WS | **read-only** |
| `http://localhost:5173/?veles=http://localhost:7260` | Veles JSON read API (dev) | **read-only (catalog)** |
| `https://…/?veles=/veles` | Veles behind the viewer's ingress (in-chart) | **read-only (catalog)** |

Precedence (see `src/data/select-data-source.ts`): (1) `?server=<ws-origin>` → the
WS `WsDesignerServerDataSource` (the client appends `/ttrm`); (2) `?veles=<base>` →
the `VelesDataSource` catalog view (SV-P4·S2·T5); (3) otherwise the worker path.
The `?server=` value must be a **loopback** WS origin (`ws://127.0.0.1…` /
`ws://localhost…`, no path — S24); anything else is a visible error, never a
guess-and-fallback. Combining `?server=`/`?veles=`/`?demo=` is an error (they select
different backends).

The `?veles=` value is EITHER a same-origin absolute path prefix (`/veles`, the
in-chart shape — the browser stays same-origin behind the viewer's ingress, no CORS)
OR a full http(s) origin (`http://localhost:7260`, dev/cross-origin — the operator's
explicit choice, CORS- and auth-gated by Veles). Unlike `?server=`, a non-loopback
origin is allowed here because Veles is the *deployed* catalog service, inherently
remote — see `docs/features/import-schema/...` / the T5 decisions note in this repo.
**Veles read mode is read-only** (a served catalog, not a modeling repo): schema/package
browse + object inspect + search; no editing, no `.ttrg`/layout.

**Server (read-only) mode** talks the `ttrm/*` JSON-RPC protocol to a locally-running
[`ttr-designer-server`](../kotlin/ttr-designer-server/README.md):

```bash
./gradlew :packages:kotlin:ttr-designer-server:run --args='--repo <abs-path-to-repo> --port 7270'
```

The server owns the repo, so there is no landing card / File System Access flow — the
index tree, graph, and read-only search come straight over the socket, and a
`ttrm/modelChanged` notification (file-watch → 200 ms debounce → reload) live-updates
the canvas. `capabilities.edit === false` hides every edit affordance.

## Loading a project (worker mode)

Two entry points, depending on the browser:

- **Load Project Folder** — hidden `<input webkitdirectory>`; works in all evergreen browsers (Chrome / Edge / Firefox / Safari). Click, select a folder containing `.ttrm` and `modeler.toml` files.
- **Open Folder** — File System Access API (Chromium-only). Direct folder access without the upload dialog. Falls back to the `webkitdirectory` path on unsupported browsers.

## Toggles

- **Schema toggle (`db` / `er`)** — switches which schema's graph is rendered. The graph for the inactive schema is cached in the reducer, so toggling back is instant (no LSP round-trip).
- **Display-mode toggle (`just-names` / `with-types` / `with-constraints`)** — controls how much detail appears inside each entity/table node. Defaults: `with-types` for `db`, `just-names` for `er`. Changing display mode does **not** trigger an auto-layout — only the labels redraw, so positions are preserved.

## Layout persistence

Layouts round-trip through the LSP's `modeler/setLayout` / `modeler/getLayout` custom methods. Behaviour depends on which transport is running:

- **Node mode (VS Code extension):** node positions are written into each `.ttrg` graph file's `layout` block (v1.1 decision D4 — there is no separate `.ttrl` sidecar). The LSP synthesizes the edit; hosts apply it.
- **Browser mode (GitHub Pages):** the browser has no filesystem. Layouts are kept in an in-memory `Map` keyed by project root and lost on tab close. Use **Export Layout** to download the current `.ttrg` for re-import or to commit alongside the project.

Save triggers (Canvas):

| Event | Debounce |
|---|---|
| `dragfreeon` (user released a dragged node) | 500ms |
| `viewport` (pan / zoom) | 750ms |
| `layoutstop` (auto-layout finished) | immediate |
| Display-mode change | immediate |

On project open, `useLayoutSync` fetches the saved layout and `applyPositions` restores node positions before auto-layout runs — if any positions exist, auto-layout is skipped.

## Demo mode

Open the deployed Designer with `?demo=v1-metadata` to load the bundled sample project from `/samples/v1-metadata/` without any upload. The landing card's **Open Demo (v1-metadata)** button does the same.

The deployed URL follows the GitHub Pages pattern: `https://<owner>.github.io/<repo>/` — fill in once the `designer-deploy.yml` workflow has run at least once on `main`.

## Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `DESIGNER_BASE_URL` | `/` | Base path for the built asset. Used by CI to set the GitHub Pages sub-path (`/<repo>/`). Worker URL resolves correctly under both local-dev and sub-path. |

Local serve of the built site:

```bash
DESIGNER_BASE_URL='/' pnpm run build
npx http-server dist -p 8080
# open http://localhost:8080/?demo=v1-metadata
```

## GitHub Pages deployment

Auto-deployed on push to `main` when `packages/designer/**`, `packages/lsp/**`, or `samples/**` change. Workflow at [.github/workflows/designer-deploy.yml](../../.github/workflows/designer-deploy.yml).

**One-time setup** (must be done manually in the repo Settings):

1. Go to **Settings → Pages**.
2. Under "Build and deployment", select **GitHub Actions** as the source.

The workflow's smoke step `curl -fI`'s the deployed `/`, `/assets/index-*.js`, `/assets/server-browser-*.js`, and `/samples/v1-metadata/index.json` — fails the deploy if any return non-200.

## Architecture notes

- LSP runs in a browser Web Worker (`@tatrman/lsp/browser?worker`). The Designer never owns model state directly — it sends LSP requests and renders the responses.
- The "Export Layout" button is only shown when `transportKind === 'browser'`. The VS Code extension has direct filesystem access via the LSP server, so the affordance isn't needed there.
- Phase-3 reducer state lives in `src/state/designer-reducer.ts`; LSP integration is split into `useProjectGraph` (fetches `getModelGraph` per active schema) and `useLayoutSync` (load on project open).

## Embedding the Designer (v1.x — not yet)

A future v1.x release will publish a `<script>`-tag embed for hosting the Designer inside third-party documentation sites. The wire-up is described in [docs/v1/design/architecture.md](../../docs/v1/design/architecture.md) §10 and [docs/v1/plan/implementation-plan.md](../../docs/v1/plan/implementation-plan.md) §11. Out of scope for Phase 3.
