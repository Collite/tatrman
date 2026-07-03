# Review 046 — Section E4 re-review (after `tasks-review-045`)

**Date:** 2026-05-22
**Scope:** re-review of Section 1.1.E.4 after the developer reported the `tasks-review-045` fixes done. Verified against runtime: `pnpm --filter @modeler/designer typecheck`/`lint`/`test` (127 pass), `build` (ok), `pnpm --filter @modeler/lsp test` (53), `pnpm --filter @modeler/integration-tests test` (64/1 skip) — all green. Plus a full read of the diffs and an empirical probe of the display-mode save path. Companion: [`tasks-review-046.md`](tasks-review-046.md).
**Verdict:** **Almost — one verified bug left.** M1 (flatten `LayoutFile`), M2 (single writer), and the node-position half of H1 are genuinely fixed and verified. But the **display-mode** persistence introduced to replace the deleted App effect is **off-by-one**: toggling the display mode writes the *previous* mode (or nothing) to the `.ttrg`, so display mode doesn't round-trip (G1, verified empirically). The new tests are real wire-path tests now (big improvement) but have gaps that let G1 through (G2). Plus `useLayoutSync` is now dead code (G3).

> Suites green, but the green still hides G1 — no test asserts the *value* of the persisted `viewport.displayMode`.

---

## Fixed and verified

- **M1 — `LayoutFile` flattened to a single `viewport`.** `model-graph.ts:144` now `viewport?: ViewportState`; `emptyLayout()` and the JSON schema drop the `viewports` map; `getLayout` returns `{ viewport }`; `setLayout` reads `_params.layout.viewport` (no more schema-key picking). Both designer callers send a single `viewport`. No fabricated `db` entry remains anywhere. `@modeler/lsp` tests + integration tests updated and green. Clean.
- **H1 (node positions) — fixed.** The `App.tsx` effect that wrote stale `state.nodePositions` is **deleted**. Both save paths now read **live cy positions** via `buildLayout(cy, …)`. The original "drag, then toggle display mode → positions revert" bug is gone for node positions.
- **M2 — single writer.** `Canvas` is now the sole persistence writer (`saveLayout` on `dragfreeon`/`viewport`/`layoutstop`, plus the new display-mode effect). The competing App-level writer is gone.
- **H2 (partial) — tests are now real wire-path tests.** `layout-persistence-v1.1.test.tsx` mounts `App`, drives the project→graph→drag/toggle flow against a mocked client, and asserts `setLayout` is called with the `graphUri`. This is the right shape and a clear improvement over the dead-reducer tests. The dead `setNodePosition`/`setViewport` reducer tests were removed. `schema-toggle-v1.1.test.tsx` remains correct.

---

## High — blocker

### G1 [High] — Display-mode change persists the *previous* mode (off-by-one); display mode doesn't round-trip

The new display-mode save effect (`Canvas.tsx:72-82`) reads `currentViewportRef.current` for the viewport it writes:

```ts
useEffect(() => {
  displayModeRef.current = displayMode;
  if (cyRef.current && lspClientRef.current && projectRootRef.current) {
    const vp = currentViewportRef.current;                       // ← stale
    const { nodes } = buildLayout(cy, vp, displayModeRef.current);
    client.setLayout(graphUri, { version: 1, viewport: vp ?? undefined, nodes, edges: {} });
  }
}, [displayMode]);
```

But `currentViewportRef` is updated by a **later** effect (`Canvas.tsx:87`, `useEffect(() => { currentViewportRef.current = currentViewport }, [currentViewport])`). When the user toggles display mode, both `displayMode` and `currentViewport` props change in the same commit, and effects run in declaration order — so this effect runs **before** the ref is refreshed and reads the *previous* viewport. `buildLayout`'s freshly-computed viewport is discarded (only `nodes` is destructured); the payload uses the stale `vp`.

Empirically verified (probe mounting `App`, toggling just-names → with-types → with-constraints, capturing `setLayout` payloads):

```
PERSISTED VIEWPORTS: [ null, {"zoom":1,"panX":0,"panY":0,"displayMode":"with-types"} ]
```

- First toggle (→ with-types): persisted `viewport` is **undefined** (vp was still `null`), so the mode isn't saved at all.
- Second toggle (→ with-constraints): persisted `viewport.displayMode` is **"with-types"** — the *previous* selection.

So the display mode written to the `.ttrg` is always one change behind. On reopen the user gets the wrong mode. This is the same class of defect H1 targeted (layout not round-tripping) — fixed for node positions, still present for the viewport's display mode.

**Fix:** write the *current* values, not the stale ref. Simplest: build the viewport from the live inputs in this effect — e.g. `const vp = { ...(currentViewport ?? defaults), displayMode }` using the `displayMode` prop directly (and the latest pan/zoom from cy via `buildLayout`'s returned `viewport`), rather than `currentViewportRef.current`. Whichever way, the persisted `viewport.displayMode` must equal the just-selected mode.

---

## Medium

### G2 [Med] — Test gaps let G1 through; the H1 regression test is vacuous

The wire-path tests assert the *target URI* but not the *payload contents*, which is exactly where G1 hides:

- **H2.3** (`changing display mode calls setLayout…`) asserts only `lastCall[0] === GRAPH_URI`. The task required asserting the payload "viewport carries the **new** displayMode." Add `expect(payload.viewport?.displayMode).toBe('with-types')` — that assertion fails today and pins G1.
- **H2.2 "H1 regression"** asserts only that `setLayout` call-count increases by 1 after the toggle — not that the persisted **nodes match the dragged positions**. With the cy mock's `nodes()` returning an empty `forEach`, it cannot verify preservation, so it does not actually guard H1. Give the cy mock real node positions and assert `payload.nodes` contains them after the toggle.
- **H2.1** declares `LAYOUT_WITH_NODES` (positions + viewport) as `getLayout`'s return, but the `getGraph` fixture returns an **empty** layout (`layout: { nodes: {}, edges: {} }`) and `getLayout` is never called — so `LAYOUT_WITH_NODES` is dead, and **no test verifies a saved layout actually restores** (loadLayout → positions land in state/canvas). Make `getGraph`'s fixture return a layout with known node positions + a viewport and assert they are applied on open.

### (carryover) L2 [Low] — "Export Layout" still downloads `layout.json`, not the `.ttrg`

Unchanged from review-045: `App.tsx:263-272` still downloads `client.exportLayout(uri)` as `layout.json`. Either implement E4.5 (download the `.ttrg` as-is) or amend `E4-reducer-layout.md` to document the JSON-export behavior. Low priority, but it's still an open task-list item.

---

## Low

### G3 [Low] — `useLayoutSync` is now dead code; the L1 decision isn't recorded

`App.tsx` no longer imports or calls `useLayoutSync` (the `useLayoutSync(state, dispatch, …)` line was removed). The hook file still exists — modified to read `graphUri` — but nothing uses it (grep confirms: only its own definition). Effectively the developer chose L1 option (a) ("rely on `getGraph`'s embedded layout"), which is fine — but then the hook should be **deleted**, not left orphaned, and the decision should be noted in `E4-reducer-layout.md` (the task doc still says "fetch `getLayout` on open"). Delete `packages/designer/src/hooks/useLayoutSync.ts`, or wire it back per L1 option (b). Right now it's confusing dead code.

---

## Recommendation

So close. The hard parts — flattening the wire type and collapsing to one writer — are done correctly. What's left is small but real: fix the off-by-one so the *current* display mode is persisted (G1), then tighten the three tests so they assert payload **contents** (display mode value, dragged node positions, and that a saved layout restores) — those would have caught G1 (G2). Finally delete the dead `useLayoutSync` (G3) and resolve L2. `tasks-review-046.md` lists the steps.
