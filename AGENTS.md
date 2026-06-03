# AGENTS.md

Scratchpad of durable, hard-won notes for agents and humans working on this
codebase. Bullet points only — explain *what's wrong, what's right, why*.
Organised by topic so individual sections can be lifted out as standalone
skills later.

---

## Cytoscape.js in this Designer

### Node `id` is mandatory; `qname` alone is not enough

- Cytoscape matches edges to nodes via `data.id`. Edges declare `source` and
  `target` strings that must equal a node's `data.id`. **Without an explicit
  `id`, Cytoscape autogenerates `n1`, `n2`, …** and your edges crash at
  `cy.add()` with `Can not create edge ... with nonexistent source ...`.
- Pattern: in the model→Cytoscape adapter, set `data: { id: node.qname, qname:
  node.qname, ... }`. Keep `qname` as a separate field for app-level lookups;
  use `id` only to satisfy Cytoscape's edge wiring.
- This bug is invisible to tests that `vi.mock('cytoscape')` — the real
  `cy.add()` is the only thing that throws on mismatched ids. **If you mock
  Cytoscape, you must also test against a real `cy.add()` somewhere** (or
  accept that this class of bug only surfaces at runtime).

### `height: 'label'` collapses to 0 without a `label` style mapping

- Cytoscape's `height: 'label'` (or `width: 'label'`) sizes the node to its
  rendered label text. If you don't map `label: 'data(label)'` in the style,
  there's no text to measure → height = 0 → node body invisible.
- Two correct paths:
  1. **Compute the height in the adapter** (`h = title + rows * row_height +
     padding`), expose as `data.h`, style as `height: 'data(h)'`. Same for
     width if needed. This is the right call when you're using
     `nodeHtmlLabel` to draw the actual content — Cytoscape's node body is
     just a "card" backdrop and you size it explicitly to fit the overlay.
  2. **Map `label: 'data(label)'`** and let Cytoscape draw the text; size to
     `'label'`. Combine with `text-wrap`, `text-max-width` for multi-line.

### `nodeHtmlLabel` overlay does NOT auto-size the underlying node

- `cytoscape-node-html-label` injects HTML at the node's center. It does *not*
  measure the HTML or feed its dimensions back to Cytoscape's layout. So a
  giant HTML overlay on a 0-px node just floats over neighbouring nodes.
- Make the Cytoscape node body match the overlay's natural size by computing
  width/height in the adapter (see above). Keep the overlay's CSS width
  pinned to the same value (`width: 220px; box-sizing: border-box;`).
- Hide Cytoscape's intrinsic label with `text-opacity: 0` when the HTML
  overlay carries all the visible content — otherwise you'll get the text
  rendered twice.

### Model vs rendered coordinates: pick the right endpoint API

- For SVG/DOM overlays positioned on top of the Cytoscape canvas you need
  *screen* coordinates that already include pan and zoom.
- `node.position()` / `edge.sourceEndpoint()` / `edge.targetEndpoint()` →
  **model** coordinates. Useless for an overlay; using them pins glyphs at
  the canvas top-left and they don't follow pan/zoom.
- `node.renderedPosition()` / `edge.renderedSourceEndpoint()` /
  `edge.renderedTargetEndpoint()` → **screen** coordinates. Use these for
  overlays.
- If you need to compute screen yourself: `screen = model * zoom + pan`,
  *not* `(model − pan) * zoom`.

### Crow's-foot glyphs — there is no library, SVG overlay is the standard

- Cytoscape 3.x has built-in arrow shapes (`tee`, `triangle`, `circle`,
  `vee`, `square`, `diamond`, `chevron`) and combos (`triangle-tee`,
  `circle-triangle`, …). They auto-rotate with edge angle, auto-scale with
  edge width, and inherit edge colour. **Crow's-foot is not in that list**,
  and Cytoscape 3.x has no arbitrary image-as-arrow support.
- The community pattern is an SVG overlay layered on top of the canvas:
  - Sibling `<div style="position:absolute; inset:0; pointerEvents:none">`
    inside the same `position:relative` parent as the cy container.
  - Register `cy.on('render zoom pan', handler)`; throttle with
    `requestAnimationFrame` so high-frequency events collapse to one paint.
  - In the handler, iterate `cy.edges('[kind="relation"]')`, read
    `edge.renderedSourceEndpoint()` / `renderedTargetEndpoint()`, emit
    `<g transform="translate(x,y) rotate(angle) scale(zoom)">…</g>` per
    glyph.
- Glyph geometry convention used here: local frame anchored at the
  endpoint, **+x points OUTWARD from the entity along the edge**. That way
  the same `glyphFor()` function works at both ends — at source rotate by
  `angle`, at target rotate by `angle + 180`. Crow's-foot convergence sits
  outward, fan touches the boundary.
- `scale(${cy.zoom()})` on the outer `<g>` is what makes glyphs grow/shrink
  with the canvas. Without it the glyphs stay at their authored pixel size
  while everything else scales.
- Set stroke colour **explicitly** on the SVG (`stroke="#10b981"`). The
  overlay `<div>` has no inherited `color`, so `stroke="currentColor"`
  resolves to slate-black, not the edge colour.

### `cose-bilkent` knobs that worked for ER-style cards

- 220×variable-height cards, 5–10 nodes, need significantly more spacing
  than the cose-bilkent defaults. What worked here:
  - `nodeRepulsion: 8000` (default 4500 buries cards in each other)
  - `idealEdgeLength: 280` (default ~50 looks cramped with 220-wide cards)
  - `edgeElasticity: 0.45`
  - `gravity: 0.15`
  - `padding: 30`
  - `randomize: false, animate: false` — deterministic layout, no rearrange
    animation. Persist positions to a sidecar after the first run so
    subsequent loads are stable.

### Cytoscape selectors are CSS-like but quoting matters

- `'edge[kind = "relation"]'` works. `'edge[kind=relation]'` does not when
  the value contains special characters. Always quote string attribute
  values.

---

## Vite + Web Worker + workspace packages

### `new URL('../relative/path', import.meta.url)` only works for files under the dev-server root

- The pattern `new Worker(new URL('./worker.js', import.meta.url))` is
  Vite-aware *only* when the worker lives under the Vite project root. With
  pnpm workspaces, a worker living in another package
  (`../../lsp/dist/server-browser.js`) resolves to a URL Vite's SPA fallback
  catches — the browser fetches the index.html and the Worker constructor
  fails parsing it as a module.
- Symptom: silent worker death in dev, LSP requests hang, downstream React
  rendering eventually crashes on bad/missing state.

### Use `?worker` query on the package import instead

- `import LspWorker from '@modeler/lsp/browser?worker';` — Vite resolves the
  bare specifier through its module graph (which respects pnpm-workspace
  exports), then applies `?worker` semantics, then serves the file via
  `/@fs/...` correctly in dev and bundles it as a separate chunk in prod.
- Requires the target file to be self-contained (see next bullet) OR for
  every nested import to be resolvable by Vite too.

### Self-contained worker bundles for cross-package workers

- Esbuild's `--external:<spec>` leaves bare imports in the bundle. Fine for
  Node, broken for browser workers — the browser has no module resolver
  for `@workspace/pkg`, `fuzzysort`, `vscode-languageserver/browser.js`,
  etc.
- Rule of thumb for browser-targeted worker bundles: **only externalize
  Node built-ins** (`node:fs`, `node:path`, `fs/promises`, `fs`, `path`).
  Inline everything else (workspace deps, npm deps, vscode-* packages).
  The dynamic `await import('node:fs')` lines are fine as long as control
  never reaches them at runtime (guard with a flag like `opts.layoutStore`).

### Workers don't show up in normal Vite dep optimization

- Vite's dep pre-bundling does not crawl into worker entry points by
  default. If your worker bundle has external bare imports, Vite won't
  pre-bundle them either — they'll just be 404s at runtime.
- Solution is either `?worker` (Vite processes the file through its
  pipeline) or self-contained bundle (no resolution needed).

---

## React + Cytoscape integration patterns

### `useRef` + async cy instantiation creates a stale-effect trap

- Typical pattern: instantiate Cytoscape inside an `await import(...)` chain,
  store in `cyRef.current = cy`. A *second* effect that depends on cy will
  run on mount when `cyRef.current` is still `null`, then never re-run
  (deps `[]` or stable refs).
- Fix: lift a `cyReady` boolean to React state, set it inside the
  `.then(...)` callback after `cyRef.current = cy`, and add `cyReady` to
  the dependent effect's deps. Now the effect fires once when cy actually
  becomes available.

### Always wrap Cytoscape in an `ErrorBoundary`

- Cytoscape errors (bad edge target, malformed style, layout failure) throw
  inside `useEffect`. React's default behaviour without a boundary is to
  unmount the whole subtree — the user sees a blank screen with no clue.
- A small class-component boundary around `<Canvas>` with a Dismiss button
  and a `resetKey` prop (driven by `projectUri | activeSchema`) turns
  catastrophic crashes into a recoverable inline error.

### Mocking Cytoscape misses real bugs

- `vi.mock('cytoscape')` lets you test the React wiring around Cytoscape
  but hides every Cytoscape-runtime bug:
  - Missing `data.id` → edge wiring crashes.
  - `height: 'label'` with no label → zero-size nodes.
  - Wrong endpoint API → glyphs in the wrong place.
- These bugs only surface at runtime. Treat the mocked unit tests as
  necessary-but-not-sufficient. Either keep an integration test that
  exercises a real `cytoscape` instance against a fixture, or rely on
  manual visual review and budget for "render-time bug" discovery there.

---

## Misc

### `text-opacity: 0` vs `display: none` for Cytoscape labels

- To hide Cytoscape's intrinsic node label when using `nodeHtmlLabel`,
  use `text-opacity: 0` (Cytoscape style), not CSS `display: none`. The
  former leaves the label in the layout computation (so `width: 'label'`
  / `height: 'label'` still work if you ever switch back); the latter
  removes it from the DOM and breaks those sizing modes.
