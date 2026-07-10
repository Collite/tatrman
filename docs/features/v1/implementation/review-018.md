# Review 018 — Re-review of Phase 3 Sections F & G after review-017 fixes

**Scope:** Verify the developer's claim that all fixes from `tasks-review-017.md` have landed and Sections F / G are now ready.

## TL;DR

Most of review-017's findings have been addressed correctly. F-1 through F-6 are all fixed at the runtime level; G-1 (the broken sample destination) is fixed in production via a Vite `closeBundle` plugin; G-2 (the `basename` pipe) and G-5 (pnpm version) are clean.

However, **two new problems are introduced that the green test suite hides**, plus several review-017 fixes were only half-done:

1. **The new `copy-samples.test.ts` never runs.** Vitest's `include` glob is `src/**/*.test.ts`, but the file lives under `scripts/__tests__/`. Confirmed with `vitest --reporter=verbose`: the file is silently skipped. The progress doc claims "copy-samples.test.ts (3 cases)" but the runner sees zero.
2. **And inside that non-running test file, an assertion is wrong:** `expect(manifest).toHaveLength(6)` against a bundle that has 5 files. If the include glob is fixed, the test will fail. So G-3 is broken in two stacked ways.
3. **Two parallel copy implementations now exist** — the `copy-samples.ts` script *and* an inline plugin in `vite.config.ts`. The plugin is correct; the script has a latent recursion bug (`walk(srcPath, rel)` recurses without threading the deeper `destPath`, so files in subdirs would be written to the top-level dest). The prebuild hook also wastes work since Vite's `emptyOutDir` wipes the dist before the plugin re-runs the copy.
4. **G-4 was not addressed.** Review-017 asked for an App-level test asserting `loadProject` is dispatched when `?demo=v1-metadata` is set, and that no fetch happens otherwise. The `demo-loader.test.tsx` is unchanged from before — still only tests the pure helper. No mention of G-4 in the progress doc's fix list.
5. **Code duplication between `Canvas.tsx` and `save-layout.ts`.** The extracted `buildLayout` / `applyPositions` helpers exist and are unit-tested, but `Canvas.tsx` still inlines its own copies of both. They are equivalent today; they will drift, and only the helper has coverage.
6. **`lastDisplayModeRef` in App.tsx fires spurious `setLayout` calls.** Initialized to hard-coded defaults `{ db: 'with-types', er: 'just-names' }`, so after `loadLayout` (which may set a different displayMode) the effect detects a "change" and immediately fires a redundant save. Same on first switchSchema. Not a correctness defect, but it's noisy and undermines the "save on user choice" intent.
7. **Progress doc test totals are still stale** — claims 151, actual 189. The same value review-017 flagged; no one re-ran the count after merging all the new tests.

None of these are deploy-blockers (the live build now produces a working artifact and the demo path will resolve), but G-3's "tests exist but never run" is a process-level regression that needs fixing before any further `[x]` ticks should be trusted. The pattern matches [MEMORY → feedback-progress-doc-skepticism].

---

## Confirmed fixed

| Item | Verification |
|---|---|
| F-1 inactive-schema viewport preservation | `Canvas.tsx:131-166` spreads `currentViewports` and only overwrites the active slot. Unit test `layout-round-trip.test.ts` covers the regression. |
| F-2 displayMode persisted on change | `App.tsx:97-114` effect on `[state.viewports, state.activeSchema, state.projectUri, state.nodePositions]` calls `setLayout` when displayMode actually changed for the active schema. |
| F-3 `exportLayout` honest in Node mode | `server.ts:371-388` now reads `.modeler/layout.ttrl` and runs it through `validateLayout`, falling back to `emptyLayout()` only on read/validate failure. |
| F-4 `layout-round-trip.test.ts` rewritten | 4 real assertions against `buildLayout` and `applyPositions`. F-1, F-2, F-6 regressions are exercised. |
| F-5 malformed-`.ttrl` integration test | `lsp-phase-03-custom-methods.test.ts` cases 4.2b (malformed JSON) and 4.2c (wrong schema version) added — `pnpm --filter @modeler/integration-tests test` shows 24 passing. |
| F-6 `useLayoutSync` logs failures | `useLayoutSync.ts:25-28` now `console.warn`s on `getLayout` rejection. |
| G-1 sample destination at build time | `vite.config.ts:9-42` plugin writes to `packages/designer/dist/samples/v1-metadata/`. Verified by `rm -rf … && pnpm --filter @modeler/designer build`: samples land in the designer dist and the repo-root `dist/` is no longer created. |
| G-2 workflow basename pipe | `designer-deploy.yml:65-81` uses `basename "$(find … \| head -1)"`, retries, and adds the `/samples/v1-metadata/index.json` curl. |
| G-5 pnpm version | `pnpm/action-setup@v4` with no explicit version; reads from root `package.json` `"packageManager": "pnpm@11.1.1"`. |
| G-6 README cleanup | Layout-persistence bullet now mentions viewport + displayMode; CTAs distinguished between webkitdirectory and FSA API; embed-script-out-of-scope note added. |

---

## Newly introduced issues

### N-1. `scripts/__tests__/copy-samples.test.ts` is never executed (CRITICAL for G-3)

`packages/designer/vitest.config.ts`:

```ts
include: ['src/**/*.{test,spec}.{js,mjs,cjs,ts,mts,cts,jsx,tsx}'],
```

The new test file lives at `packages/designer/scripts/__tests__/copy-samples.test.ts`, which does not match. Confirmed by `pnpm vitest run --reporter=verbose`: the report lists 12 test files and 55 tests; `copy-samples.test.ts` is absent from the list.

Effect: `pnpm --filter @modeler/designer test` is green but verifies zero behaviour of the copy script. The progress doc's claim `Tests: copy-samples.test.ts (3 cases)` is false against the runner.

Fix one of:

* **Preferred** — extend the include glob:
  ```ts
  include: [
    'src/**/*.{test,spec}.{js,mjs,cjs,ts,mts,cts,jsx,tsx}',
    'scripts/**/*.{test,spec}.{js,mjs,cjs,ts,mts,cts}',
  ],
  ```
* **Alternative** — move the test under `src/__tests__/copy-samples.test.ts` and adjust the relative import.

Whichever you pick: after the fix, the test must surface in `pnpm vitest run --reporter=verbose` output.

### N-2. Assertion `toHaveLength(6)` is off by one (would fail once N-1 is fixed)

`scripts/__tests__/copy-samples.test.ts:52`:

```ts
expect(manifest).toHaveLength(6);
```

`samples/v1-metadata/` currently contains 5 files (`db.ttr, er.ttr, map.ttr, modeler.toml, query.ttr`). The manifest array is populated during `walk()` *before* `index.json` is written, so `manifest.length === 5`. The assertion will fail the moment vitest actually picks the file up.

Fix: change to `toHaveLength(5)` *and* couple it to the source state to prevent rot:

```ts
const expectedCount = fs.readdirSync(samplesSrc).filter(
  (n) => !n.startsWith('.') && fs.statSync(path.join(samplesSrc, n)).isFile()
).length;
expect(manifest).toHaveLength(expectedCount);
```

### N-3. Two parallel implementations of the copy logic, one buggy

`packages/designer/scripts/copy-samples.ts:10-35` and `packages/designer/vite.config.ts:9-42` both contain a `walk(...)` that traverses `samples/v1-metadata/` and writes to `packages/designer/dist/samples/v1-metadata/`. They differ in one important way:

* **`vite.config.ts` plugin** — signature `walk(src, dest, prefix)`; on directory recursion, passes `destPath` so subdir files end up in `dest/subdir/foo.ttr`. **Correct.**
* **`scripts/copy-samples.ts`** — signature `walk(src, prefix)`; the outer `dest` is closed-over and never advances. Subdir files would be flattened: `samples/sub/foo.ttr` ends up at `dist/foo.ttr`, and the manifest entry `sub/foo.ttr` then points at a path that doesn't exist. Latent only because `samples/v1-metadata/` is currently flat.

Worse: there's also no need for the prebuild script anymore. Vite's default `emptyOutDir: true` wipes `dist/` at the start of `vite build`, so whatever the prebuild copies is destroyed before the plugin's `closeBundle` re-copies. The prebuild hook is wasted work that produces flaky on-disk state mid-build.

Fix one of:

* **Preferred** — delete `packages/designer/scripts/copy-samples.ts` and the `prebuild` script entry; the Vite plugin is sufficient. Move the test to assert the plugin output: a build-time integration test that invokes Vite (or just imports `copySamples` from `vite.config.ts` after extracting it).
* **Alternative** — extract the copy implementation into one helper (`packages/designer/scripts/copy-samples.ts` exporting `copySamples(src, dest)` written *correctly*, i.e. passing `destPath` through recursion), import it from `vite.config.ts`'s plugin, and keep the script + `prebuild` only if a non-Vite caller exists. If both callers stay, fix the script's recursion bug.

### N-4. G-4 (App-level demo test) was not done

Review-017 G-4 asked for `packages/designer/src/__tests__/app-demo.test.tsx` that mounts `<App />`, asserts `loadProject` is dispatched when `?demo=v1-metadata`, and asserts no fetch otherwise. The progress doc does not list a G-4 review fix and `demo-loader.test.tsx` is unchanged from before review-017.

This leaves the App effect at `App.tsx:74-92` unverified end-to-end. A regression that drops the `?demo` branch entirely (or accidentally re-fires it twice) would not be caught.

Add the test per the sketch in `tasks-review-017.md` G-4. The reducer + LSP client are easy to mock.

### N-5. `lastDisplayModeRef` triggers spurious saves after load and on schema switch

`App.tsx:97-114`:

```ts
const lastDisplayModeRef = useRef<Record<RenderableSchemaCode, DisplayMode>>({
  db: 'with-types',
  er: 'just-names',
});
useEffect(() => {
  if (!state.projectUri || !clientRef.current) return;
  const active = state.activeSchema;
  const current = state.viewports[active].displayMode;
  if (lastDisplayModeRef.current[active] === current) return;
  lastDisplayModeRef.current[active] = current;
  // … assemble layout, call setLayout …
}, [state.viewports, state.activeSchema, state.projectUri, state.nodePositions]);
```

Two failure modes:

1. **After `loadLayout`:** if the loaded layout has e.g. `viewports.db.displayMode === 'with-constraints'`, the effect sees `lastDisplayModeRef.db === 'with-types'` (hard-coded init) ≠ `'with-constraints'`, fires `setLayout` immediately. The saved layout will equal the just-loaded layout (so no data loss), but you've issued a write for no reason — and any save failure here surfaces as a `console.warn` the user can't explain.
2. **On first switchSchema:** initial render has `active = 'db'`, ref is `{ db:'with-types', er:'just-names' }`. User switches to `er`. `state.viewports.er.displayMode` may differ from `'just-names'` if a load already happened — same spurious write. Even if it matches, the dependency includes `state.nodePositions`, so any later position update *while in er* re-runs the effect; it checks the displayMode equality and no-ops *only after* re-reading state. OK, but adds noise to React DevTools.

Fix: sync the ref from `state.viewports` whenever `loadLayout` runs. Cleanest path is a third "previous viewports" ref that snapshots after dispatch:

```ts
// After useLayoutSync:
const prevViewportsRef = useRef(state.viewports);
useEffect(() => {
  const prev = prevViewportsRef.current;
  prevViewportsRef.current = state.viewports;
  if (!state.projectUri || !clientRef.current) return;
  const active = state.activeSchema;
  if (prev[active].displayMode === state.viewports[active].displayMode) return;
  // fire setLayout
}, [state.viewports, state.activeSchema, state.projectUri, state.nodePositions]);
```

This compares against the previous render, not a hard-coded baseline.

### N-6. `Canvas.tsx` still inlines `buildLayout` / position application logic

Extraction was the point of F-4. `save-layout.ts` exports `buildLayout` and `applyPositions`, both unit-tested. But:

* `Canvas.tsx:134-166` reimplements the build-layout body inline rather than calling `buildLayout(cy, viewportsRef.current, activeSchemaRef.current, displayModeRef.current)`.
* `Canvas.tsx:209-219` reimplements `applyPositions` inline.

Right now they match. Their next divergence is the first bug. Refactor:

```ts
import { buildLayout, applyPositions } from '../cy/save-layout';

function saveLayout() {
  const client = lspClientRef.current;
  const root = projectRootRef.current;
  if (!client || !root || !cyRef.current) return;
  const layout = buildLayout(
    cyRef.current,
    viewportsRef.current,
    activeSchemaRef.current,
    displayModeRef.current
  );
  client.setLayout(root, layout).catch(() => {});
}
```

And in the `[graph]` effect, replace the manual `for` loop with `applyPositions(cy, positions)`.

### N-7. Progress doc test totals still stale

`docs/plan/progress-phase-03.md:136`:

```
pnpm -r test: ✅ 151 tests total (19 parser, 40 semantics, 35 lsp, 30 designer, 6 vscode-ext, 21 integration)
```

Actual (verified):

| package | claim | actual |
|---|---|---|
| parser | 19 | 19 |
| semantics | 40 | 40 |
| lsp | 35 | 45 |
| vscode-ext | 6 | 6 |
| designer | 30 | 55 (would be 58 once `copy-samples.test.ts` is picked up) |
| integration | 21 | 24 |
| **total** | **151** | **189** |

Same finding as review-017; the line wasn't refreshed.

---

## What this re-review verified, end to end

```bash
rm -rf dist packages/designer/dist
pnpm install
pnpm -r build                 # green
pnpm -r test                  # 189 tests passed (counts above)
pnpm -r lint                  # 0 errors
pnpm -r typecheck             # 0 errors
pnpm --filter @modeler/designer build
ls packages/designer/dist/samples/v1-metadata
#  db.ttr  er.ttr  index.json  map.ttr  modeler.toml  query.ttr   ✓
test ! -d dist                # repo-root dist absent ✓

pnpm vitest run --reporter=verbose | grep copy-samples
# (no output) — N-1 confirmed: test file not picked up
basename ""                   # empty output, exit 0 (workflow empty-check is safe) ✓
```

---

## Verdict

Sections F and G are **closer to done but not yet done**. The runtime behavior is correct; the test guarantees backing it are weaker than the green bar implies (N-1, N-2). Two cleanups (N-3 duplication, N-6 Canvas inline) prevent the helper-vs-runtime drift that already silently happened once. N-4 leaves an entire feature path (demo mode) without integration coverage. N-5 is cosmetic but worth fixing while you're in App.tsx.

Action list with concrete steps: `tasks-review-018.md`.
