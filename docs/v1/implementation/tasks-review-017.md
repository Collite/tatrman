# Tasks — Review 017

Sections F (layout persistence) and G (static deploy) are not actually done. The two showstoppers (G-1 wrong copy dest, G-2 broken smoke step) and two correctness bugs (F-1 inactive viewport clobbered, F-2 displayMode never persisted) all need to land before `[x]` is honest. The test files claim coverage that doesn't exist; rewrite them so they would have caught the corresponding bugs.

Work through these in order. Each task ends with the exact command(s) to verify it.

---

## G-1 — Fix `copy-samples.ts` destination (CRITICAL)

- [ ] Open `packages/designer/scripts/copy-samples.ts`.
- [ ] Change line 8 from
  ```ts
  const destDir = path.join(rootDir, 'dist/samples/v1-metadata');
  ```
  to
  ```ts
  const destDir = path.join(rootDir, 'packages/designer/dist/samples/v1-metadata');
  ```
- [ ] Also ensure the dist tree exists before the copy (Vite may or may not have created it yet on first build):
  ```ts
  fs.mkdirSync(destDir, { recursive: true });
  ```
  (You already have this inside `copyDir`; the `buildManifest` writing `index.json` to `destDir` will also fail if the dir doesn't exist on a clean checkout — confirm it does.)
- [ ] Remove the stray repo-root `dist/` directory left over from prior runs:
  ```bash
  rm -rf dist/
  ```
- [ ] Add `dist/` at the repo root to `.gitignore` if it isn't already, to prevent regression of stray top-level dist trees:
  ```bash
  grep -E '^/?dist/?$' .gitignore || echo '/dist/' >> .gitignore
  ```

**Verify:**
```bash
pnpm --filter @modeler/designer build
ls packages/designer/dist/samples/v1-metadata/
# expected: db.ttr, er.ttr, index.json, map.ttr, modeler.toml, query.ttr
test ! -d dist  # repo-root dist must NOT exist
```

---

## G-3 — Make `copy-samples.test.ts` actually test the script

- [ ] Delete the current content of `packages/designer/scripts/__tests__/copy-samples.test.ts`. It uses `path.resolve(__dirname, '../../../samples/v1-metadata')` (one `..` short), `fs.existsSync` returns `false`, and both `it` blocks silently `return` — they verify nothing.
- [ ] Rewrite as: run the script against a tmpdir destination, then assert the copy. The simplest way is to factor `copy-samples.ts` so the destination is parameterised, then call it from the test. Sketch:

  In `copy-samples.ts`:
  ```ts
  export function copySamples(srcDir: string, destDir: string): string[] {
    // existing copyDir + buildManifest, but parameterised
    // return the manifest array
  }

  // top-level runner only fires when called as a script:
  if (import.meta.url === `file://${process.argv[1]}`) {
    const out = copySamples(samplesDir, destDir);
    console.log(`Copied ${out.length} files to ${destDir}`);
  }
  ```

  In the test:
  ```ts
  import { describe, it, expect, beforeEach, afterEach } from 'vitest';
  import fs from 'node:fs';
  import os from 'node:os';
  import path from 'node:path';
  import { copySamples } from '../copy-samples';

  describe('copySamples', () => {
    const repoRoot = path.resolve(__dirname, '../../../..');
    const samplesSrc = path.join(repoRoot, 'samples/v1-metadata');
    let tmpDest: string;

    beforeEach(() => {
      tmpDest = fs.mkdtempSync(path.join(os.tmpdir(), 'copy-samples-'));
    });
    afterEach(() => {
      fs.rmSync(tmpDest, { recursive: true, force: true });
    });

    it('copies all non-hidden files and writes index.json', () => {
      copySamples(samplesSrc, tmpDest);
      const got = fs.readdirSync(tmpDest).sort();
      expect(got).toContain('modeler.toml');
      expect(got).toContain('db.ttr');
      expect(got).toContain('er.ttr');
      expect(got).toContain('index.json');

      const manifest: string[] = JSON.parse(
        fs.readFileSync(path.join(tmpDest, 'index.json'), 'utf-8')
      );
      // index.json must NOT list itself, NOT list hidden files, NOT list .modeler/
      expect(manifest).not.toContain('index.json');
      expect(manifest.every((p) => !p.startsWith('.'))).toBe(true);
      expect(manifest.every((p) => !p.startsWith('.modeler'))).toBe(true);
    });

    it('modeler.toml content round-trips byte-for-byte', () => {
      copySamples(samplesSrc, tmpDest);
      const expected = fs.readFileSync(path.join(samplesSrc, 'modeler.toml'), 'utf-8');
      const actual = fs.readFileSync(path.join(tmpDest, 'modeler.toml'), 'utf-8');
      expect(actual).toBe(expected);
    });
  });
  ```

- [ ] **Important:** the test must fail if you revert task G-1. Try it: temporarily change `copy-samples.ts` to write to the wrong dir; the test should fail. Then revert.

**Verify:**
```bash
pnpm --filter @modeler/designer test -- copy-samples
# expected: at least 2 passing tests that actually invoke copySamples()
```

---

## G-2 — Fix the deploy workflow smoke step (CRITICAL)

- [ ] Open `.github/workflows/designer-deploy.yml`.
- [ ] Replace lines 67-75 with:
  ```yaml
  - name: Smoke test deployed site
    run: |
      sleep 30
      curl -fI --retry 5 --retry-delay 5 "${{ steps.deployment.outputs.page_url }}/" || exit 1

      INDEX_JS=$(basename "$(find packages/designer/dist/assets -name 'index-*.js' -type f | head -1)")
      [ -n "$INDEX_JS" ] || { echo "index-*.js not found"; exit 1; }
      curl -fI --retry 5 --retry-delay 5 "${{ steps.deployment.outputs.page_url }}assets/${INDEX_JS}" || exit 1

      WORKER_JS=$(basename "$(find packages/designer/dist/assets -name 'server-browser-*.js' -type f | head -1)")
      [ -n "$WORKER_JS" ] || { echo "server-browser-*.js not found"; exit 1; }
      curl -fI --retry 5 --retry-delay 5 "${{ steps.deployment.outputs.page_url }}assets/${WORKER_JS}" || exit 1

      DEMO_INDEX="${{ steps.deployment.outputs.page_url }}samples/v1-metadata/index.json"
      curl -fI --retry 5 --retry-delay 5 "$DEMO_INDEX" || exit 1

      echo "Smoke tests passed"
  ```

  Changes from the broken version:
  - `basename "$(... | head -1)"` (positional arg, the form basename supports)
  - explicit emptiness check so the curl URL doesn't silently become `/assets/` with no filename
  - retry/backoff to tolerate Pages propagation latency
  - additional curl against `/samples/v1-metadata/index.json` — this would have caught bug G-1 on the first deploy
  - `${{ ... page_url }}` ends with a trailing `/` in Pages outputs, so concatenating `assets/...` is correct (no leading `/`)

- [ ] Verify the basename pipe locally before pushing:
  ```bash
  pnpm --filter @modeler/designer build
  basename "$(find packages/designer/dist/assets -name 'server-browser-*.js' -type f | head -1)"
  # expected: a non-empty filename like server-browser-BbnIOoTy.js
  echo "exit=$?"   # expected: 0
  ```

---

## G-5 — Align pnpm version in the workflow

- [ ] In `.github/workflows/designer-deploy.yml` lines 32-33, either drop the `with: version: 9` block (action will read `packageManager` from `package.json`) or pin it to `11`:
  ```yaml
  uses: pnpm/action-setup@v4
  # remove the `with:` block — action will pick up packageManager from package.json
  ```
- [ ] Confirm the repo's `package.json` has `"packageManager": "pnpm@11.x.x"`. If not, fix it before relying on the action's autodetect.

**Verify:** push to a topic branch and watch the workflow's "Setup pnpm" step report `pnpm 11.*`.

---

## G-4 — Test the demo-mode App effect, not just the helper

- [ ] Keep the existing `loadDemoFiles` unit tests; they're fine as a low-level check.
- [ ] Add a new file `packages/designer/src/__tests__/app-demo.test.tsx` that mounts `<App />` with React Testing Library, mocks `createLspClient`, and asserts:
  1. With `window.history.replaceState(null, '', '/?demo=v1-metadata')` set before render and `fetch` mocked, after the demo-load microtask `client.openDocument` was called once per file and the App is no longer showing `LandingCard`.
  2. With no `?demo` query and no project loaded, no `fetch` call is made and `LandingCard` is rendered (assert via `screen.getByText('TTR Modeler Designer')` + the two CTA buttons).
- [ ] Mock pattern:
  ```ts
  vi.mock('../lsp-client', () => ({
    createLspClient: vi.fn().mockResolvedValue({
      transportKind: 'browser',
      openDocument: vi.fn().mockResolvedValue(undefined),
      onDiagnostics: vi.fn(),
      dispose: vi.fn(),
      getModelGraph: vi.fn().mockResolvedValue({ nodes: [], edges: [] }),
      getLayout: vi.fn().mockResolvedValue({
        version: 1,
        viewports: { db: {zoom:1,panX:0,panY:0,displayMode:'with-types'}, er: {zoom:1,panX:0,panY:0,displayMode:'just-names'} },
        nodes: {}, edges: {},
      }),
      getSymbolDetail: vi.fn().mockResolvedValue(null),
      setLayout: vi.fn().mockResolvedValue({ ok: true }),
      exportLayout: vi.fn(),
      applyGraphEdit: vi.fn(),
    }),
  }));
  ```

**Verify:**
```bash
pnpm --filter @modeler/designer test -- app-demo
# expected: 2 passing tests
```

---

## F-1 — Stop `saveLayout` from clobbering the inactive schema's viewport (HIGH)

- [ ] In `Canvas.tsx`, accept the full reducer viewports map as a prop instead of (or in addition to) `activeSchema`:
  ```ts
  interface CanvasProps {
    // ...
    viewports: Record<RenderableSchemaCode, ViewportState>;
    // remove standalone displayMode prop or keep but synchronise via viewports
  }
  ```
- [ ] Mirror the prop into a ref alongside the others:
  ```ts
  const viewportsRef = useRef(viewports);
  useEffect(() => { viewportsRef.current = viewports; }, [viewports]);
  ```
- [ ] Rewrite `saveLayout` so the inactive schema's viewport carries over from state, not hard-coded defaults:
  ```ts
  function saveLayout() {
    const client = lspClientRef.current;
    const root = projectRootRef.current;
    const cy = cyRef.current;
    if (!client || !root || !cy) return;

    const nodes: Record<string, { x: number; y: number }> = {};
    cy.nodes().forEach((node) => {
      const pos = node.position();
      nodes[node.data('qname') as string] = { x: pos.x, y: pos.y };
    });

    const pan = cy.pan();
    const zoom = cy.zoom();
    const active = activeSchemaRef.current;
    const mode = displayModeRef.current;

    const next = { ...viewportsRef.current };
    next[active] = { zoom, panX: pan.x, panY: pan.y, displayMode: mode };

    const layout: LayoutFile = {
      version: 1,
      viewports: next,
      nodes,
      edges: {}, // v1: edge bend persistence not in scope
    };
    client.setLayout(root, layout).catch(() => { /* best-effort */ });
  }
  ```
- [ ] Update `App.tsx` to pass `state.viewports` to `<Canvas>` and remove redundant `displayMode` prop wiring if you collapse it into viewports.

**Verify with a new unit test** in `packages/designer/src/components/__tests__/Canvas-save-layout.test.tsx` (write this test as part of task F-4 below; it must assert the inactive schema's viewport survives a save).

---

## F-2 — Persist `displayMode` immediately on change (HIGH)

- [ ] In `App.tsx`, the `onDisplayModeChange` handler currently only dispatches. It must also trigger a layout save. Easiest path:
  - Lift `saveLayout` (or just the `setLayout` call with the new layout) into a callback accessible from both `Canvas` and `App`, OR
  - Add a new dispatcher effect: when `state.viewports[state.activeSchema].displayMode` changes, fire `client.setLayout(...)` once.

  Sketch using the effect approach:
  ```ts
  // in App.tsx, after useLayoutSync:
  const lastDisplayModeRef = useRef<Record<RenderableSchemaCode, DisplayMode>>(
    initialDesignerState.viewports as never
  );
  useEffect(() => {
    if (!state.projectUri || !clientRef.current) return;
    const active = state.activeSchema;
    const current = state.viewports[active].displayMode;
    if (lastDisplayModeRef.current[active] === current) return;
    lastDisplayModeRef.current[active] = current;
    const layout: LayoutFile = {
      version: 1,
      viewports: state.viewports,
      nodes: state.nodePositions,
      edges: {},
    };
    clientRef.current.setLayout(state.projectUri, layout).catch(() => {});
  }, [state.viewports, state.activeSchema, state.projectUri, state.nodePositions]);
  ```

  Note: this is also a candidate spot to write a once-per-session save on `unload`. Out of scope for this review.

- [ ] After implementing, manually verify in the dev server: open a project, change displayMode to `with-constraints`, refresh — the chosen mode must be restored from the layout file (Node mode), or stay in-memory (browser mode).

---

## F-3 — Make `modeler/exportLayout` in Node mode honest

- [ ] In `packages/lsp/src/server.ts:371-376`, replace the body with a read-from-disk equivalent of `getLayout`, **or** explicitly throw / return the same `emptyLayout()` *plus* a `console.warn('exportLayout in node mode falls back to disk read')`. Pick one:
  - Honest fallback (recommended):
    ```ts
    connection.onRequest('modeler/exportLayout', async (params: { projectRoot: string }): Promise<LayoutFile> => {
      if (opts.layoutStore) {
        return opts.layoutStore.get(params.projectRoot) ?? emptyLayout();
      }
      // Node mode: same as getLayout — read .modeler/layout.ttrl from disk
      const { readFileSync } = await import('node:fs');
      const { join } = await import('node:path');
      try {
        const raw = readFileSync(join(params.projectRoot, '.modeler', 'layout.ttrl'), 'utf-8');
        const validated = validateLayout(JSON.parse(raw));
        if (validated) return validated;
      } catch { /* fall through */ }
      return emptyLayout();
    });
    ```
- [ ] Add a unit test alongside the existing `modeler/getLayout` tests asserting `exportLayout` returns the same data as `getLayout` after a write.

**Verify:**
```bash
pnpm --filter @modeler/integration-tests test
```

---

## F-4 — Rewrite `layout-round-trip.test.ts` to test Canvas integration (HIGH)

- [ ] Delete the existing content of `packages/designer/src/cy/__tests__/layout-round-trip.test.ts`. The current tests re-implement `debounce` and trivial `Object.keys` checks — they verify nothing about Canvas's actual save/load logic.
- [ ] Write a new suite that mounts the post-F-1 save logic with a stubbed `cy` and a stubbed `LspClient`. Since wiring all of Cytoscape into jsdom is heavy, extract the save flow into a pure helper first (see F-1 sketch — `saveLayout` already only depends on `cy`, `lspClientRef`, `projectRootRef`, `viewportsRef`, etc.). Pull it out into `packages/designer/src/cy/save-layout.ts`:

  ```ts
  export interface CyShim {
    nodes(): Array<{ position(): { x: number; y: number }; data(k: string): unknown }>;
    pan(): { x: number; y: number };
    zoom(): number;
  }

  export function buildLayout(
    cy: CyShim,
    viewports: Record<RenderableSchemaCode, ViewportState>,
    active: RenderableSchemaCode,
    displayMode: DisplayMode
  ): LayoutFile { ... }
  ```

  Use it in Canvas.tsx so the unit test can call `buildLayout` with a hand-crafted `CyShim`.

- [ ] Tests to write (each must fail if the bug it covers is reintroduced):
  1. **buildLayout preserves inactive schema's viewport** (covers F-1). Set up viewports `{ db: { zoom: 2 }, er: { zoom: 1 } }`, `active = 'er'`, `cy.zoom() = 3`. Result's `viewports.db.zoom` must equal 2; `viewports.er.zoom` must equal 3.
  2. **buildLayout includes current displayMode for active schema** (covers F-2). Set up `displayMode = 'with-constraints'`, active `'db'`. Result `viewports.db.displayMode === 'with-constraints'`.
  3. **buildLayout maps every cy node's qname to its position** (covers F-4 plan bullet). Stub `cy.nodes()` returning two nodes; result `nodes['x.y.z']` matches.
  4. **loadLayout / applyPositions ignores unknown qnames** (covers F.6). Write a tiny helper `applyPositions(cy, nodes)` (extract from `Canvas.tsx`'s `graph` effect) and assert `cy.getElementById('ghost')` was queried but `position(...)` was not called for it. Use a stubbed cy where `getElementById('ghost').length === 0`.

**Verify:**
```bash
pnpm --filter @modeler/designer test -- layout-round-trip
# expected: 4 meaningful passing tests that actually exercise buildLayout / applyPositions
```

---

## F-5 — Add the missing "malformed `.ttrl` returns emptyLayout" integration test

- [ ] In `tests/integration/src/lsp-phase-03-custom-methods.test.ts`, add a new case right after `4.2 setLayout then getLayout round-trips`:

  ```ts
  it('4.2b getLayout returns emptyLayout when .ttrl is malformed', async () => {
    const tempRoot = join(tmpdir(), `modeler-test-malformed-${Date.now()}`);
    mkdirSync(join(tempRoot, '.modeler'), { recursive: true });
    try {
      writeFileSync(join(tempRoot, '.modeler', 'layout.ttrl'), 'not json {{{', 'utf-8');
      const result = await client.sendRequest('modeler/getLayout', { projectRoot: tempRoot }) as { version: number; nodes: Record<string, unknown> };
      expect(result.version).toBe(1);
      expect(result.nodes).toEqual({});
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });
  ```

- [ ] Also add a structurally-valid-but-schema-invalid case (`{ version: 2, viewports: {}, nodes: {}, edges: {} }`) and assert the same `emptyLayout` fallback. This exercises `validateLayout`'s ajv check.

**Verify:**
```bash
pnpm --filter @modeler/integration-tests test -- lsp-phase-03-custom-methods
# expected: cases 4.2b and 4.2c added and passing
```

---

## F-6 — Log layout-load failures

- [ ] In `useLayoutSync.ts` lines 25-27, replace the empty `catch` body with `console.warn('[useLayoutSync] getLayout failed', err);`. (Use `console.warn`, not `console.error`, to match the "best-effort, do not crash UI" intent.)

---

## G-6 — README cleanup

- [ ] In `packages/designer/README.md`:
  - Tighten the layout-persistence bullet so it doesn't promise more than the code delivers:
    ```
    - Layout persistence: node positions, per-schema viewport (zoom/pan), and display mode round-trip through `modeler/setLayout` / `modeler/getLayout`.
    ```
    (This bullet is only true once F-1 and F-2 land — only ship the line once those are merged.)
  - Add a one-liner under "GitHub Pages deployment": "The embed script for hosting the designer inside third-party docs sites lands in v1.x (see implementation-plan §11) — out of scope for Phase 3."
  - Clarify the two CTAs: "Load Project Folder" uses a hidden `<input webkitdirectory>` (works on all evergreen browsers); "Open Folder" uses the File System Access API (Chromium-only). Mention the fallback path explicitly.

---

## Progress doc

- [ ] After every box above is ticked, regenerate the test totals at `docs/plan/progress-phase-03.md:127-131` from a real `pnpm -r test` run:
  ```bash
  pnpm -r test 2>&1 | grep -E "Tests +[0-9]+ passed"
  ```
- [ ] Do NOT re-tick F or G section boxes in the progress doc until **all** of G-1, G-2, G-3, G-4, F-1, F-2, F-4, F-5 (the bug fixes + the tests that would have caught the bugs) are merged. Per [MEMORY → feedback-progress-doc-skepticism]: `[x]` is meant to track ground truth, not intent.

---

## Final gate

After every checkbox above is done, run:

```bash
rm -rf dist packages/designer/dist
pnpm install
pnpm -r build
pnpm -r test
pnpm -r lint
pnpm -r typecheck
pnpm --filter @modeler/designer build
test -f packages/designer/dist/samples/v1-metadata/index.json
test -f packages/designer/dist/samples/v1-metadata/db.ttr
test ! -d dist                                        # no stray repo-root dist
basename "$(find packages/designer/dist/assets -name 'server-browser-*.js' | head -1)"
# expected: non-empty filename, exit 0
```

Then push to a topic branch and confirm the deploy workflow's smoke step reports "Smoke tests passed" against the real Pages URL. Only then is Sections F and G actually done.
