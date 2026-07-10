# Review 019 — Re-review of review-018 fixes

**Scope:** Verify the developer's fixes to `tasks-review-018.md` and decide whether Phase 3 Sections F and G are now actually done.

## TL;DR

Every N-1 through N-7 task from review-018 has landed correctly. Build, test, lint, and typecheck are green; the new tests are picked up by the runner and exercise the regressions they claim to. The two helpers (`buildLayout`, `applyPositions`) are now used both in `Canvas.tsx` and the standalone unit tests — no more drift surface. The progress doc's test totals match reality for the first time in this phase.

**Sections F and G are done.** One trivial cleanup remains (an orphaned dependency), and one process note worth carrying forward.

---

## Confirmed fixed

| Task | Verification |
|---|---|
| N-1 vitest includes `scripts/**` | `vitest.config.ts:9-12` adds the scripts glob; `pnpm vitest run --reporter=verbose` lists 3 `copy-samples.test.ts` cases. |
| N-2 off-by-one length assertion | `copy-samples.test.ts:47-57` now derives `expectedCount` from `readdirSync` filtered to non-hidden files — rot-proof. Passes against the real 5-file bundle. |
| N-3 duplicate copy implementation eliminated | `scripts/copy-samples.ts` is now a pure exported helper; `vite.config.ts:9` imports `copySamples` and the plugin calls it. No `prebuild` script in `package.json`. Build no longer prints `Copied N files`; the work happens once, in `closeBundle`. |
| N-4 App-level demo test | `src/__tests__/app-demo.test.tsx` mounts `<App />` with hoisted mocks for `cytoscape`, `lsp-client`, and `demo-loader`. Three cases: `?demo=v1-metadata` → `loadDemoFiles` + `openDocument` per file; no flag → landing card, no fetch; and an N-5 regression assertion. |
| N-5 `prevViewportsRef` replaces hard-coded baseline | `App.tsx:97-113` snapshots prior render's viewports and only saves when the *active* schema's displayMode actually changed render-to-render. The app-demo test's third case asserts no `setLayout` fires after `loadDemoFiles` completes — covers the regression directly. |
| N-6 Canvas uses extracted helpers | `Canvas.tsx:6` imports `buildLayout` / `applyPositions`; `saveLayout` is 14 lines of glue around `buildLayout`; the `[graph]` effect calls `applyPositions(cy, positions)`. No more inline reimplementations. |
| N-7 progress doc test totals refreshed | `progress-phase-03.md:136` now claims `195 tests total (19 parser, 40 semantics, 45 lsp, 61 designer, 6 vscode-ext, 24 integration)` — matches what `pnpm -r test` actually prints. |

---

## End-to-end verification

```bash
rm -rf packages/designer/dist dist
pnpm install
pnpm -r build                                # green
pnpm -r test                                 # 195 tests passed, distribution matches doc
pnpm -r lint                                 # 0 errors
pnpm -r typecheck                            # 0 errors

ls packages/designer/dist/samples/v1-metadata
# db.ttr  er.ttr  index.json  map.ttr  modeler.toml  query.ttr   ✓
test ! -d dist                               # no stray repo-root dist ✓

pnpm --filter @modeler/designer vitest run --reporter=verbose \
  | grep -E 'copy-samples|app-demo'
# six ✓ lines: 3 copy-samples cases + 3 app-demo cases (incl. N-5 regression)

pnpm --filter @modeler/designer build 2>&1 | grep -c "Copied"
# 0 — prebuild hook is gone; copy happens silently via the plugin
```

All checks pass.

---

## Minor leftover (not blocking)

### L-1. Orphaned `tsx` devDependency

`packages/designer/package.json:36` still lists `"tsx": "^4.19.0"` in `devDependencies`. Its only consumer was the deleted `prebuild` hook. Nothing else in `packages/designer/` invokes `tsx` (verified via grep — the only hit is a comment in `useProjectGraph.ts` that mentions `App.tsx` the filename, not the CLI tool).

Drop the line on the next pass through this `package.json`:

```bash
pnpm --filter @modeler/designer remove -D tsx
```

Not urgent — it costs a few MB in the pnpm store. Catching it here mostly so the package.json reflects what's actually used.

---

## Process note

For three review cycles in a row (017 → 018 → 019), the failure mode has been the same shape: code lands correctly but the test or build invariant is silently disabled (wrong include glob, wrong destination path, vacuous assertions). The green bar kept being misleading until something forced it to actually run. The MEMORY entry `feedback-progress-doc-skepticism` covers this — worth keeping in mind for Sections H/I/J/K too: when a `[x]` ticks, verify the runner sees the test, not just that the test file exists.

---

## Verdict

Sections F and G are **done**. No further fixes required for these sections. Phase 3 can move forward to the remaining carryover sections (H symbol indexing, I parse recovery, J VS Code smoke, K documentation) and the final close-out checklist at `progress-phase-03.md:123-131`.

No task list this time. Drop `tsx` from designer devDependencies when convenient.
