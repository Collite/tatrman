# Tasks — Review 018

Re-review of the review-017 fixes. Most are good; the items below are what's still missing or newly broken. Work top-down; each task ends with the exact command(s) to verify.

---

## N-1 — Make `scripts/__tests__/copy-samples.test.ts` actually run (CRITICAL)

The test file exists but isn't picked up by vitest because `vitest.config.ts`'s `include` glob only covers `src/**`. The runner currently sees 12 test files and 55 tests; `copy-samples` is not in that list.

- [ ] Open `packages/designer/vitest.config.ts`.
- [ ] Extend the `include` array:
  ```ts
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    include: [
      'src/**/*.{test,spec}.{js,mjs,cjs,ts,mts,cts,jsx,tsx}',
      'scripts/**/*.{test,spec}.{js,mjs,cjs,ts,mts,cts}',
    ],
    globals: true,
  },
  ```
  (No `tsx/jsx` in the scripts glob — scripts are Node-only.)

- [ ] Note that the scripts test imports `node:fs` and reads from the real filesystem. The default `environment: 'jsdom'` is still fine for it (jsdom doesn't replace Node modules), but if you ever see "fs is not defined" you can opt out with a file-level `// @vitest-environment node` comment at the top of `copy-samples.test.ts`.

**Verify:**
```bash
pnpm vitest run --reporter=verbose 2>&1 | grep copy-samples
# expected: 3 lines, each prefixed with ✓
pnpm --filter @modeler/designer test 2>&1 | grep -E "Tests +[0-9]+ passed"
# expected: 58 (not 55)
```

---

## N-2 — Fix the off-by-one assertion in `copy-samples.test.ts`

The file currently asserts `expect(manifest).toHaveLength(6)` but the bundle has 5 files. Once N-1 lets the test run, this fails.

- [ ] Open `packages/designer/scripts/__tests__/copy-samples.test.ts`.
- [ ] Replace the hard-coded length check with one derived from the source directory so it can't rot:
  ```ts
  it('returns manifest of all copied files', () => {
    const manifest = copySamples(samplesSrc, tmpDest);
    const expectedCount = fs
      .readdirSync(samplesSrc, { withFileTypes: true })
      .filter((d) => d.isFile() && !d.name.startsWith('.'))
      .length;
    expect(manifest).toHaveLength(expectedCount);
    expect(manifest).toContain('modeler.toml');
    expect(manifest).toContain('db.ttr');
    expect(manifest).toContain('er.ttr');
  });
  ```
  (This stays correct even if someone adds or removes a sample file.)

**Verify (after N-1):**
```bash
pnpm vitest run --reporter=verbose 2>&1 | grep "returns manifest"
# expected: ✓ green
```

---

## N-3 — Eliminate the duplicate copy implementation

`scripts/copy-samples.ts` and `vite.config.ts`'s plugin both walk `samples/v1-metadata/`. The plugin is correct; the script has a latent recursion bug (subdir files would be flattened to the top-level dest because the recursion never advances `dest`). The `prebuild` script is also wasted work since Vite's default `emptyOutDir` wipes the dist before the plugin re-copies.

Pick option A (preferred — less code) or option B (keep the script for non-Vite callers).

### Option A — Delete the script and prebuild hook

- [ ] Move the test target from "the script" to "the plugin's output". Either:
  - Extract `copySamples(src, dest)` into a small module imported by `vite.config.ts`'s plugin, OR
  - Replace the unit test with a build-integration test that runs `pnpm --filter @modeler/designer build` against a tmpdir-`outDir` and asserts samples landed.

  The first form is simpler:

  ```ts
  // packages/designer/scripts/copy-samples.ts (replaces existing file)
  import fs from 'node:fs';
  import path from 'node:path';

  export function copySamples(srcDir: string, dest: string): string[] {
    fs.mkdirSync(dest, { recursive: true });
    const manifest: string[] = [];
    function walk(src: string, dst: string, prefix: string): void {
      for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
        if (entry.name.startsWith('.')) continue;
        if (entry.name === '.modeler') continue;
        const rel = prefix ? `${prefix}/${entry.name}` : entry.name;
        const srcPath = path.join(src, entry.name);
        const dstPath = path.join(dst, entry.name);
        if (entry.isDirectory()) {
          fs.mkdirSync(dstPath, { recursive: true });
          walk(srcPath, dstPath, rel);   // <-- recursion threads dstPath
        } else {
          fs.copyFileSync(srcPath, dstPath);
          manifest.push(rel);
        }
      }
    }
    walk(srcDir, dest, '');
    fs.writeFileSync(path.join(dest, 'index.json'), JSON.stringify(manifest, null, 2));
    return manifest;
  }
  ```

  Then in `vite.config.ts`, import and use it:
  ```ts
  import { copySamples } from './scripts/copy-samples';

  function copySamplesPlugin() {
    return {
      name: 'copy-samples',
      closeBundle() {
        const samplesSrc = path.resolve(__dirname, '../../samples/v1-metadata');
        const samplesDest = path.resolve(__dirname, './dist/samples/v1-metadata');
        if (!fs.existsSync(samplesSrc)) return;
        copySamples(samplesSrc, samplesDest);
      },
    };
  }
  ```

  And delete the script's top-level executor lines (the `copySamples(samplesDir, destDir); console.log(...)` block).

- [ ] Remove the `"prebuild": "tsx scripts/copy-samples.ts"` line from `packages/designer/package.json` scripts. The prebuild copy is wiped by Vite's `emptyOutDir`; only the plugin's `closeBundle` survives. Removing it eliminates the wasted work and avoids future confusion.

### Option B — Keep the script for non-Vite use

- [ ] If you want `tsx scripts/copy-samples.ts` to remain runnable from CI or scripts:
  - Fix the recursion to pass `destPath` through: change `walk(src: string, prefix: string)` to `walk(src: string, dst: string, prefix: string)` and call `walk(srcPath, destPath, rel)`. Same shape as the plugin.
  - Still remove the `prebuild` hook from `package.json` — it doesn't pay rent.

**Verify (either option):**
```bash
pnpm --filter @modeler/designer build
ls packages/designer/dist/samples/v1-metadata/
# expected: db.ttr, er.ttr, index.json, map.ttr, modeler.toml, query.ttr
diff <(jq -r '.[]' packages/designer/dist/samples/v1-metadata/index.json | sort) \
     <(ls samples/v1-metadata/ | grep -v '^\.' | sort | grep -v '^modeler$')
# expected: no output (manifest matches source files)
```

If you took Option A, also verify there's no `prebuild` line and no console output of `Copied N files to …`:

```bash
grep prebuild packages/designer/package.json    # expected: empty
pnpm --filter @modeler/designer build 2>&1 | grep -c "Copied"  # expected: 0
```

---

## N-4 — Add the App-level demo test (G-4 from review-017, never landed)

This was an explicit task in `tasks-review-017.md`; the progress doc lists no G-4 fix and `demo-loader.test.tsx` is unchanged.

- [ ] Create `packages/designer/src/__tests__/app-demo.test.tsx`:

  ```tsx
  import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
  import { render, screen, waitFor } from '@testing-library/react';

  // Mock the LSP client before importing App.
  const openDocument = vi.fn().mockResolvedValue(undefined);
  const onDiagnostics = vi.fn();
  vi.mock('../lsp-client', () => ({
    createLspClient: vi.fn().mockResolvedValue({
      transportKind: 'browser',
      openDocument,
      onDiagnostics,
      dispose: vi.fn(),
      getModelGraph: vi.fn().mockResolvedValue({ nodes: [], edges: [] }),
      getLayout: vi.fn().mockResolvedValue({
        version: 1,
        viewports: {
          db: { zoom: 1, panX: 0, panY: 0, displayMode: 'with-types' },
          er: { zoom: 1, panX: 0, panY: 0, displayMode: 'just-names' },
        },
        nodes: {}, edges: {},
      }),
      getSymbolDetail: vi.fn().mockResolvedValue(null),
      setLayout: vi.fn().mockResolvedValue({ ok: true }),
      exportLayout: vi.fn(),
      applyGraphEdit: vi.fn(),
    }),
  }));

  // Import App AFTER the mock so it picks up the mocked module.
  import App from '../App';

  describe('App demo loading (G-4)', () => {
    let originalFetch: typeof fetch;

    beforeEach(() => {
      openDocument.mockClear();
      originalFetch = global.fetch;
    });
    afterEach(() => {
      global.fetch = originalFetch;
      window.history.replaceState(null, '', '/');
    });

    it('with ?demo=v1-metadata, fetches manifest + files and opens documents', async () => {
      window.history.replaceState(null, '', '/?demo=v1-metadata');
      global.fetch = vi.fn((url: string | URL | Request) => {
        const u = url.toString();
        if (u.endsWith('/samples/v1-metadata/index.json')) {
          return Promise.resolve({ ok: true, json: async () => ['modeler.toml', 'db.ttr'] });
        }
        return Promise.resolve({ ok: true, text: async () => 'content' });
      }) as unknown as typeof fetch;

      render(<App />);

      await waitFor(() => expect(openDocument).toHaveBeenCalledTimes(2));
      expect(openDocument).toHaveBeenCalledWith(
        'file:///v1-metadata/modeler.toml',
        'content',
      );
      expect(openDocument).toHaveBeenCalledWith(
        'file:///v1-metadata/db.ttr',
        'content',
      );
    });

    it('without ?demo flag, renders LandingCard and does not fetch', async () => {
      window.history.replaceState(null, '', '/');
      const fetchMock = vi.fn();
      global.fetch = fetchMock as unknown as typeof fetch;

      render(<App />);

      // LandingCard CTAs render once client is ready.
      await waitFor(() => screen.getByText('Open Demo (v1-metadata)'));
      expect(fetchMock).not.toHaveBeenCalled();
      expect(openDocument).not.toHaveBeenCalled();
    });
  });
  ```

**Verify:**
```bash
pnpm --filter @modeler/designer test -- app-demo
# expected: 2 passing
```

---

## N-5 — Stop spurious `setLayout` calls after `loadLayout`

`App.tsx:97-114`'s `lastDisplayModeRef` is initialized to hard-coded defaults. After a load whose displayMode differs from `'with-types' / 'just-names'`, the effect immediately fires `setLayout` — even though the user didn't change anything. Same on first switchSchema.

- [ ] Replace the ref + effect with a "compare against previous render's viewports" pattern:

  ```ts
  const prevViewportsRef = useRef(state.viewports);
  useEffect(() => {
    const prev = prevViewportsRef.current;
    prevViewportsRef.current = state.viewports;

    if (!state.projectUri || !clientRef.current) return;
    const active = state.activeSchema;
    if (prev[active].displayMode === state.viewports[active].displayMode) return;

    const layout: LayoutFile = {
      version: 1,
      viewports: state.viewports,
      nodes: state.nodePositions,
      edges: {},
    };
    clientRef.current.setLayout(state.projectUri, layout).catch(() => {});
  }, [state.viewports, state.activeSchema, state.projectUri, state.nodePositions]);
  ```

  - On first render, `prevViewportsRef.current === state.viewports`, so the active displayMode comparison is trivially equal → no save.
  - After `loadLayout`, `prev` is the pre-load snapshot, `state.viewports` is the loaded one; active displayMode usually equals what was loaded for the *initial* active schema (`db`), so no save unless the user actually changes mode later.
  - On switchSchema, both `prev[active]` and `state.viewports[active]` refer to the *new* active schema (because activeSchema is in the dep array, but viewports for both schemas were already in state). DisplayMode for the new active schema is whatever was saved → no save.
  - On a real user toggle, `prev[active].displayMode !== current` → save fires once.

- [ ] Delete the old `lastDisplayModeRef` block.

**Verify:** add a test in `app-demo.test.tsx` (or its sibling) asserting that loading a layout with `viewports.db.displayMode = 'with-constraints'` does **not** trigger `setLayout` immediately. Use the existing `setLayout` mock and assert `expect(setLayout).not.toHaveBeenCalled()` after `waitFor(loadLayout)`.

---

## N-6 — Make `Canvas.tsx` use the extracted helpers it already has tests for

`save-layout.ts` exports `buildLayout` and `applyPositions`. They're correct and unit-tested. `Canvas.tsx` reimplements both. Refactor:

- [ ] Add import:
  ```ts
  import { buildLayout, applyPositions } from '../cy/save-layout';
  ```
- [ ] Replace the body of the inline `saveLayout` (`Canvas.tsx:134-166`) with:
  ```ts
  function saveLayout() {
    const client = lspClientRef.current;
    const root = projectRootRef.current;
    const cy = cyRef.current;
    if (!client || !root || !cy) return;
    const layout = buildLayout(
      cy,
      viewportsRef.current,
      activeSchemaRef.current,
      displayModeRef.current,
    );
    client.setLayout(root, layout).catch(() => {});
  }
  ```
- [ ] In the `[graph]` effect (`Canvas.tsx:209-219`), replace the manual `for (const [qname, pos] …)` loop with:
  ```ts
  if (hasPositions) {
    applyPositions(cy, positions);
  }
  ```

  (`CyShim` types match cytoscape's runtime shape; the `getElementById` overlap also matches `applyPositions`'s parameter type.)

**Verify:**
```bash
pnpm --filter @modeler/designer test
pnpm --filter @modeler/designer typecheck
# expected: still green; behaviour unchanged
```

Manual sanity: open the dev server, drag a node, reload — position must restore. Same behaviour as before.

---

## N-7 — Refresh the progress doc test counts

`docs/plan/progress-phase-03.md:136` claims 151 total tests. Actual is 189 (will be 191 after N-1 picks up the 2 currently-skipped copy-samples cases; the `toHaveLength(6)` case becomes the 3rd once N-2 fixes it).

- [ ] After the rest of these tasks land, run:
  ```bash
  pnpm -r test 2>&1 | grep -E "Tests +[0-9]+ passed"
  ```
- [ ] Replace the line in `progress-phase-03.md` with the actual numbers. Keep the same format. Do this last, so the count reflects all the fixes above.

---

## Final gate

After every box above is ticked:

```bash
rm -rf dist packages/designer/dist
pnpm install
pnpm -r build
pnpm -r test
pnpm -r lint
pnpm -r typecheck

# Tests must include copy-samples now:
pnpm vitest --filter @modeler/designer run --reporter=verbose 2>&1 | grep -c copy-samples
# expected: >= 3

# Build artifact must contain samples:
test -f packages/designer/dist/samples/v1-metadata/index.json
test -f packages/designer/dist/samples/v1-metadata/db.ttr
test ! -d dist                                            # no stray repo-root dist

# No prebuild log if you took Option A in N-3:
pnpm --filter @modeler/designer build 2>&1 | grep -c "Copied"   # expected: 0
```

Then `docs/plan/progress-phase-03.md`'s F and G boxes can be considered honestly ticked, and Sections F + G can be marked done.
