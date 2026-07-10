# Review 017 — Phase 3 Sections F (layout persistence) and G (static deploy)

**Scope:** Developer claims Sections F and G of `docs/plan/phase-03/` are ready. Reviewed against `F-layout-persistence.md`, `G-static-deploy.md`, and the architecture invariants in `CLAUDE.md`. Build/test/lint/typecheck all green locally, but several boxes ticked in `progress-phase-03.md` do not survive scrutiny.

## TL;DR

Two showstoppers:

1. **`copy-samples.ts` writes to the wrong directory.** Samples end up at `<repo>/dist/samples/…`, not `packages/designer/dist/samples/…`. The Pages artifact uploaded from `packages/designer/dist` therefore contains no `/samples/` tree, so `?demo=v1-metadata` will 404 on `index.json` in production. (Verified by running `pnpm --filter @modeler/designer build`: the script logs `Copied 5 files to /Users/bora/Dev/modeler/dist/…`.)
2. **The deploy workflow's smoke step is shell-broken.** `find … | head -1 | basename` pipes into `basename`, but `basename` does not read stdin on either macOS or GNU coreutils. The step exits 1 every time. (Verified locally.)

Both bugs are masked because the unit tests that were supposed to catch them do not actually exercise the code they claim to: `copy-samples.test.ts` runs against a non-existent path and short-circuits via `if (!fs.existsSync(...)) return`, and `layout-round-trip.test.ts` re-asserts `debounce` semantics instead of mounting a stubbed Cytoscape + LspClient as the plan called for.

This is the same pattern flagged in `MEMORY.md → feedback-progress-doc-skepticism`: `[x]` in the progress doc tracks intent, not runtime correctness. Sections F and G are not yet "DONE when".

---

## Section F — Layout persistence

### F-1. `saveLayout` corrupts the inactive schema's viewport (HIGH, correctness)

`packages/designer/src/components/Canvas.tsx:131-158` reads `cy.zoom()` / `cy.pan()` from the *currently active* canvas, then writes a full `LayoutFile` where the inactive schema is hard-coded to `{ zoom: 1.0, panX: 0, panY: 0, displayMode: 'with-types' | 'just-names' }`. So:

* Open db schema, zoom in to 2×, pan around.
* Switch to er. The reducer's `state.viewports.db` still holds zoom=2.
* Drag any node in er. `dragfreeon` fires → `saveLayout` writes `viewports.db = { zoom: 1.0, panX: 0, panY: 0, displayMode: 'with-types' }` to disk, silently overwriting the user's db viewport.

The reducer (`designer-reducer.ts:25-30`) already tracks both schemas' viewports correctly via `loadLayout`. `saveLayout` should pull the inactive schema's viewport from `state.viewports[otherSchema]` (e.g. via a ref) rather than synthesising defaults.

### F-2. DisplayMode changes do not trigger `saveLayout` (HIGH, missing requirement)

Plan F.2 explicitly says:

> `state.viewports[state.activeSchema].displayMode` change handler calls `saveLayout` immediately (no debounce — discrete user choice).

Nothing in `App.tsx` or `Canvas.tsx` calls `saveLayout` on `setDisplayMode`. The handler in `App.tsx:150` only dispatches the reducer action. DisplayMode only gets persisted as a side-effect of the *next* drag/viewport/layoutstop event — and per F-1 that save will clobber the inactive schema anyway.

### F-3. `modeler/exportLayout` (Node mode) lies (LOW, correctness)

`packages/lsp/src/server.ts:371-376` — when no in-memory `layoutStore` is configured (Node transport), `exportLayout` returns `emptyLayout()` unconditionally instead of reading `.modeler/layout.ttrl` like `getLayout` does. Currently inert because the Designer only runs the browser transport, but the next consumer wiring node transport will silently get empty data. Either share the read path with `getLayout`, or throw `Method not supported in node mode`. Don't pretend to succeed with empty.

### F-4. `layout-round-trip.test.ts` does not test the integration (HIGH, test quality)

`packages/designer/src/cy/__tests__/layout-round-trip.test.ts` is six tautological assertions about `debounce(fn, ms)` and a couple of `Object.keys(...).length > 0` conditionals re-implemented inline. It never imports `Canvas`, never stubs `cy`, never stubs `LspClient`, never asserts `setLayout` is called with positions matching `cy.nodes().position()`. The plan's tests-first list (F-§F bullets 1–4) is therefore unfulfilled — and these tests would not have failed against bugs F-1 or F-2.

Concretely, the test file is duplicating coverage that already lives in `debounce.test.ts` and adding nothing.

### F-5. Integration "malformed .ttrl → emptyLayout" case is missing (MEDIUM)

Plan F asked for `tests/integration/src/layout-persistence.test.ts` with two scenarios; only the round-trip case made it (folded into `lsp-phase-03-custom-methods.test.ts` cases 4.1/4.2). The second scenario — "writing a malformed `.ttrl` by hand and calling `getLayout` returns `emptyLayout()`" — does not exist. The `validateLayout` schema in `model-graph.ts` is therefore not exercised end-to-end.

### F-6. `useLayoutSync` swallows errors silently (LOW)

`packages/designer/src/hooks/useLayoutSync.ts:25-27` catches `getLayout` rejections and does nothing — not even `console.warn`. If layout fetch fails (network glitch in browser mode, schema-validation reject) the user has no signal. Match the spirit of the F.2 "catch and log" rule: at least one `console.warn` with the error.

### F-7. `Canvas.saveLayout` always writes `edges: {}` (LOW)

This is fine for v1 (edge bend persistence isn't in scope), but combined with F-1 means any third-party-written edge bend data is wiped on every save. A short comment in `Canvas.tsx` saying "edges intentionally not persisted in v1" prevents future confusion.

---

## Section G — Static deploy

### G-1. `copy-samples.ts` writes to repo-root `dist/`, not designer's `dist/` (CRITICAL, blocks demo)

`packages/designer/scripts/copy-samples.ts:5-8`:

```ts
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, '../../../');
const samplesDir = path.join(rootDir, 'samples/v1-metadata');
const destDir = path.join(rootDir, 'dist/samples/v1-metadata');
```

`__dirname = packages/designer/scripts`, so `rootDir` resolves to the repository root. `destDir` therefore points at `<repo>/dist/samples/v1-metadata` — not `packages/designer/dist/samples/v1-metadata`.

Vite then builds into `packages/designer/dist/` (no `outDir` override in `vite.config.ts`) and `actions/upload-pages-artifact` uploads `packages/designer/dist`. Result: deployed site has **no `/samples/` tree**. Hitting `/?demo=v1-metadata` will 404 on `/samples/v1-metadata/index.json` and the SPA's error path shows the user "Failed to load demo: Error: Demo manifest not found: /samples/v1-metadata/index.json".

Fix: `destDir = path.join(rootDir, 'packages/designer/dist/samples/v1-metadata')` (or compute relative to `__dirname`). Vite's `emptyOutDir` default would wipe such samples if the prebuild ran *after* `vite build`, but `prebuild` runs *before* and Vite preserves files outside the chunks it emits, so writing samples into the designer dist before Vite runs is safe.

### G-2. Smoke step in `designer-deploy.yml` is shell-broken (CRITICAL, fails every deploy)

`.github/workflows/designer-deploy.yml:67-74`:

```bash
INDEX_JS=$(find packages/designer/dist/assets -name 'index-*.js' -type f | head -1 | basename)
curl -fI "${{ steps.deployment.outputs.page_url }}/assets/${INDEX_JS}" || exit 1
WORKER_JS=$(find packages/designer/dist/assets -name 'server-browser-*.js' -type f | head -1 | basename)
```

`basename` does not accept stdin on either BSD or GNU coreutils — it requires a positional argument. Reproduced locally: `find … | head -1 | basename` prints `usage: basename string [suffix]` and exits 1.

Fix one of:

```bash
INDEX_JS=$(basename "$(find packages/designer/dist/assets -name 'index-*.js' -type f | head -1)")
# or
INDEX_JS=$(find packages/designer/dist/assets -name 'index-*.js' -type f -printf '%f\n' | head -1)
```

(Avoid the second form on macOS — GNU find only — but the workflow runs on Ubuntu so it's fine there.)

Also: `find … 'server-browser-*.js'` assumes Vite names the chunk that way. Worker chunks emitted by `import 'foo?worker'` are typically named after the import path. Once F.5 / G demo work is verified, confirm in a build artifact that the filename pattern `server-browser-*.js` is actually what Vite produces. (Local build shows it does — `dist/assets/server-browser-BbnIOoTy.js` — so the match works once the basename pipe is fixed.)

Bonus: a 5-second sleep before curl is optimistic. Pages propagation can take 30–60s on a cold environment. Either drop the smoke step (it provides almost no value) or implement a real retry loop.

### G-3. `copy-samples.test.ts` does not test `copy-samples.ts` (HIGH, test quality)

`packages/designer/scripts/__tests__/copy-samples.test.ts:6`:

```ts
const samplesDir = path.resolve(__dirname, '../../../samples/v1-metadata');
```

From `packages/designer/scripts/__tests__/`, that resolves to `packages/samples/v1-metadata` (one `..` too few — should be four levels up, not three). `fs.existsSync` returns `false`, the two `it` blocks both `return` before asserting anything, and the test file passes vacuously: green status, zero verified behaviour.

Even fixed, the test wouldn't validate the script — it'd only verify the source directory exists. The plan said:

> The script copies every file under `samples/v1-metadata/` (excluding hidden files and `.modeler/`) into `packages/designer/dist/samples/v1-metadata/`. Assert the file count matches and one specific file's content is identical.

A correct test imports (or `tsx`-runs) the script against a tmpdir destination and asserts files+contents. That test would have failed against bug G-1.

### G-4. `demo-loader.test.tsx` does not test the App effect the plan asked for (MEDIUM, test quality)

`packages/designer/src/__tests__/demo-loader.test.tsx` is a unit test for the pure `loadDemoFiles` helper. The plan asked for an RTL test of `App.tsx`:

> With `?demo=v1-metadata` in the URL, App fetches `…modeler.toml`, `…db.ttr`, etc., and dispatches `loadProject` exactly once with the discovered files.
> With no demo flag, App shows the landing-page card and does not fetch anything.

Neither assertion is present. Especially the "doesn't fetch when no flag" case — a regression that adds an accidental top-level `fetch('/samples/...')` would not be caught.

### G-5. Workflow uses pnpm 9 against a pnpm 11 repo (MEDIUM, drift risk)

`.github/workflows/designer-deploy.yml:32-33`:

```yaml
uses: pnpm/action-setup@v4
with:
  version: 9
```

Repo root `package.json` (per `CLAUDE.md`) pins `packageManager: pnpm 11`. Mismatch can cause `--frozen-lockfile` to reject the lockfile or resolve differently. Either drop the explicit `version` (the action will read `packageManager` from package.json) or set it to `11`.

### G-6. README claims overstated; one-time-setup steps thin (LOW)

`packages/designer/README.md`:

* "Layout persistence (node positions saved via `modeler/setLayout`)" — true for positions, but viewport-per-schema is broken (F-1) and displayMode doesn't trigger save (F-2). The bullet should not promise full persistence until F-1/F-2 land.
* "Open Folder" CTA mentioned in the README but the Header now also has "Load Project Folder"; clarify the relationship or remove one.
* Per plan G.7 the README should mention "where the embed script will land in v1.x (out of scope for Phase 3)." Missing.

### G-7. Stale `/<repo>/dist/` directory artifact (LOW, hygiene)

Because of bug G-1, running `pnpm --filter @modeler/designer build` creates a `/dist/` directory at the **repo root** with stray samples. It's not git-ignored and isn't cleaned up. Easy footgun. Once G-1 is fixed this goes away; until then, consider deleting the stray dir or adding it to `.gitignore`.

---

## Progress doc accuracy

`docs/plan/progress-phase-03.md:125-131`:

```
pnpm -r test: ✅ 151 tests total (19 parser, 40 semantics, 35 lsp, 30 designer, 6 vscode-ext, 21 integration)
```

Actual local run (post-build, against the current branch):

| package | claim | actual |
|---|---|---|
| parser | 19 | 19 |
| semantics | 40 | 40 |
| lsp | 35 | **45** |
| vscode-ext | 6 | 6 |
| designer | 30 | **58** |
| integration | 21 | **22** |
| **total** | **151** | **190** |

The totals haven't been kept in sync; not a defect but reinforces the rule that progress doc entries are intent, not ground truth. Tick boxes only after the verification command was re-run end-to-end.

---

## Deviations from the plan

| Plan item | Status |
|---|---|
| F.1 debounce util + tests | OK |
| F.2 save flow — dragfreeon / viewport / layoutstop | Partial: displayMode save missing, inactive-schema viewport clobbered |
| F.3 load flow via `useLayoutSync` | OK |
| F.4 layout-vs-positions race | OK |
| F.5 Download layout (browser only) | OK in code, but `exportLayout` (Node mode) returns `emptyLayout()` deceptively |
| F.6 stale-qname tolerance | OK (no JSDoc comment as plan required; minor) |
| F test: round-trip with stubbed cy + LspClient | **MISSING** — file exists but tests `debounce`, not Canvas |
| F integration: malformed `.ttrl` → emptyLayout | **MISSING** |
| G.1 copy-samples + dist destination | **BROKEN** — wrong destination |
| G.2 vite base path env var | OK |
| G.3 demo-mode landing in App | OK in code, but no App-level test |
| G.4 landing-page card | OK |
| G.5 deploy workflow | OK structure; pnpm 9/11 drift |
| G.6 smoke curl | **BROKEN** — `basename` pipe |
| G.7 README + one-time setup | Partial — embed-script note missing, overstated layout-persistence claim |
| G test: `copy-samples.test.ts` | **VACUOUS** — wrong path, silently skips |
| G test: `demo-loader.test.tsx` (App-level) | Replaced with helper-level unit; not what the plan asked for |

---

## Architecture and modularity

Outside the bugs above, the code organisation is reasonable:

* `useLayoutSync` and `useProjectGraph` as sibling hooks is clean — each owns one effect, both easy to read.
* `Canvas` uses refs (`projectRootRef`, `lspClientRef`, etc.) to keep the `cy` initialisation effect stable while still seeing current props. Standard React-with-imperative-library pattern.
* `loadDemoFiles` is a pure helper — separating it from the App effect was correct; the right tradeoff for testability is to keep the helper unit-tested *and* add a small App-level "with/without `?demo` flag" test on top.

Minor: `Canvas.tsx` has accumulated 12 ref hooks. Once F-1/F-2 land it's worth pulling the save-flow logic into a `useCanvasSync(cy, state, dispatch, client)` hook similar to `useLayoutSync` — Canvas would shrink and the save behaviour would be unit-testable without mounting Cytoscape. Not blocking for Phase 3 close-out, but flag for early v1.1.

---

## Verify-by-running

```bash
pnpm install
pnpm --filter @modeler/designer build
ls packages/designer/dist/samples 2>&1   # bug G-1: directory does not exist
ls dist/samples/v1-metadata               # samples actually landed here

find packages/designer/dist/assets -name 'index-*.js' | head -1 | basename
# usage: basename string [suffix]  ← bug G-2
```

Tasks list with concrete instructions: `tasks-review-017.md`.
