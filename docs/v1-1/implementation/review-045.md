# Review 045 — Section E4 (Reducer + per-graph layout persistence)

**Date:** 2026-05-22
**Scope:** first review of Section 1.1.E.4 against [`E4-reducer-layout.md`](../plan/tasks/E4-reducer-layout.md). Verified against runtime: `pnpm --filter @modeler/designer typecheck` (clean), `lint` (clean), `test` (130 pass), `build` (ok), `pnpm --filter @modeler/integration-tests test` (64 pass / 1 skip), plus a full read of the changed files (`App.tsx`, `Canvas.tsx`, `useLayoutSync.ts`) and the two new test files. Companion: [`tasks-review-045.md`](tasks-review-045.md).
**Verdict:** **Not done.** The state shape is final and correct, the schema toggle is gone (with a test), and `useLayoutSync` is rewired to `graphUri` — but the layout-persistence path it was supposed to *finalize* has a **data-loss bug** (changing display mode after dragging reverts node positions on disk), the **Tests-first cases are unmet** (the new test exercises reducer actions the app never dispatches, while the actual `getLayout`/`setLayout` wire path has zero coverage), and **schema-keyed `viewports` with fabricated `db` entries still ship** — the very `.ttrl`-shaped per-schema state §11.1 and the DONE criteria say must be gone.

> All gates green, but green is misleading again: the suite covers dead reducer paths, not the save/load wiring that E4 changed.

---

## High — blockers

### H1 [High] — Display-mode change after a node drag overwrites node positions with stale data

`App.tsx:124-137` persists layout on viewport/display-mode change, writing `nodes: state.nodePositions` (`:133`). But **`state.nodePositions` is only ever set by `loadLayout`** (on graph open) — **no code anywhere dispatches `setNodePosition`** (grep confirms: dispatched only in tests). Node drags are persisted by `Canvas.saveLayout`, which reads positions straight from Cytoscape (`buildLayout(cy, …)`, `Canvas.tsx:147-155`) and writes them directly via `setLayout`, **bypassing React state**. So `state.nodePositions` goes stale the moment the user drags.

Repro:
1. Open a graph → `loadLayout` sets `nodePositions = { A: p1 }`.
2. Drag node A to `p2` → `Canvas.saveLayout` writes `{ A: p2 }` to the `.ttrg`. `state.nodePositions` is **still `{ A: p1 }`**.
3. Toggle display mode → the `App.tsx:124` effect fires (displayMode changed), and writes `nodes: state.nodePositions = { A: p1 }` back to the `.ttrg`. **The drag to `p2` is gone.**

The display-mode save is the *only* writer for display-mode changes (no cy event fires for it, so `Canvas.saveLayout` doesn't cover it), which is why it can't just be deleted — but as written it clobbers positions. This means layout does **not** round-trip after a display-mode toggle, which is exactly the E4 acceptance ("layout restored from the `.ttrg`'s `layout` block"). Fix options in the task list (H1).

### H2 [High] — Tests-first not met; the new test covers actions the app never dispatches

`E4-reducer-layout.md` requires `layout-persistence-v1.1.test.ts` to cover four behaviors, all about the **wire path**: `getLayout(graphUri)` on open → `loadLayout`; drag → `setLayout(graphUri, …)`; viewport → `setLayout`; display-mode → per-graph save. The delivered file is **pure reducer unit tests** — it asserts `loadLayout`, `setNodePosition`, `setViewport`, `setDisplayMode` state transitions in isolation. None of it touches `client.getLayout`/`client.setLayout`, `useLayoutSync`, or `Canvas.saveLayout`.

Worse, two of the tested actions — **`setNodePosition` and `setViewport` — are dead**: nothing in the app dispatches them (see H1). So the test gives false confidence: it green-lights code paths that never execute, while the paths E4 actually changed are unverified:
- `useLayoutSync` calling `getLayout(graphUri)` (the new `graphUri` arg) → untested.
- `Canvas.saveLayout` / the `App.tsx` effect calling `setLayout(graphUri, payload)` with the new payload → untested.

(The `schema-toggle-v1.1.test.tsx` file, by contrast, is fine — it asserts no `er`/`db`/`map`/`cnc` pills and that the display-mode pills remain. That part of Tests-first is met.)

---

## Medium — deviations

### M1 [Med] — Schema-keyed `viewports` with a fabricated `db` entry still ship (violates §11.1 + DONE)

Both save paths still emit a per-schema map:
- `App.tsx:132` → `viewports: { er: vp ?? …, db: { …, displayMode: 'with-types' } }`
- `Canvas.tsx:154` → `viewports: { er: vp ?? …, db: { …, displayMode: 'just-names' } }`

§11.1 is explicit: *"no per-schema viewports, no `RenderableSchemaCode`-keyed maps in the state."* DONE: *"No `.ttrl`-shaped state remains in the Designer."* Yet the Designer fabricates a bogus `db` viewport on every write (and the two writers even disagree on its `displayMode`). It's functionally inert — the LSP `setLayout` handler picks only the graph's own schema key (`server.ts:431`, `vp[schemaKey]`) and ignores the rest — but it's precisely the residue E4.3 was meant to remove ("flatten to a single `viewport`").

Root cause: `LayoutFile` is still typed `viewports: Record<RenderableSchemaCode, ViewportState>` (`packages/lsp/src/model-graph.ts:144`), forcing callers to supply the map. The read side is already flat (`getLayout` returns `GraphLayoutOutput` with a singular `viewport`, and the reducer reads `layout.viewport`). Flatten the write side to match: `LayoutFile.viewport: ViewportState` + update the `setLayout` handler and both Designer callers. (This touches `@modeler/lsp`; if that's deemed outside E4's designer-only scope, it must be tracked explicitly rather than left as fabricated `db` entries.)

### M2 [Med] — Two overlapping `setLayout` writers; React state is not the source of truth

Layout is persisted from **two** places with divergent payloads and triggers:
- `App.tsx:124` effect — fires on display-mode change, writes `state.nodePositions` (stale, see H1) + `state.currentViewport`.
- `Canvas.saveLayout` — fires on `dragfreeon` / `viewport` / `layoutstop`, writes live cy positions + a viewport built from cy.

Meanwhile `state.nodePositions` and the pan/zoom parts of `state.currentViewport` are never updated from Canvas (no `setNodePosition`/`setViewport` dispatch), so the reducer's position/viewport state is write-only-at-load and diverges from both cy and disk. E4's stated goal was to make "reducer state model final" and the persistence path coherent; instead there are two writers and a half-wired state. Consolidate to one source of truth — either (a) Canvas dispatches `setNodePosition`/`setViewport` so state stays authoritative and a single effect persists it, or (b) Canvas owns all persistence (including a display-mode save) and the `App.tsx` effect is removed. Pick one and delete the other path.

---

## Low — polish / clarification

### L1 [Low] — `getLayout(graphUri)`-on-open is conditional, not the spec'd flow

`useLayoutSync` now reads `state.currentGraphUri` and calls `getLayout(graphUri)` ✅ — but only when `currentGraph.layout.nodes` is empty (`useLayoutSync.ts:18`, the early-return guard). In the common case the graph already carries its layout via `getGraph` (`handleSelectGraph` dispatches `loadLayout` from `graph.layout`, `App.tsx:187-189`), so `getLayout` is skipped. This works, but it contradicts E4.2/E4.6 ("on graph open, fetch `getLayout(graphUri)`"; "fetch `getGraph` + `getLayout` in parallel") and leaves two load paths mediated by a guard. Decide: either drop the redundant `getLayout`/`useLayoutSync` path and document that `getGraph` carries the layout, or implement the parallel fetch as specced. Don't leave both half-wired.

### L2 [Low] — "Export Layout" downloads layout JSON, not the `.ttrg`

E4.5: *"Now it downloads the current graph's `.ttrg` file as-is."* `App.tsx`'s `onDownloadLayout` still downloads `client.exportLayout(uri)` serialized as `layout.json` (the v1 behavior). Either update it to download the `.ttrg` text, or amend E4.5 to match the shipped JSON-export behavior with a one-line rationale.

---

## What's genuinely good

- **State shape is final and matches §11.2 exactly** (`designer-state.ts`): `currentViewport: ViewportState | null`, no `viewports` map, no `activeSchema`, no `RenderableSchemaCode`-keyed fields. E4.4/E4.7 (state) are done — they were already in shape from E1, and nothing regressed.
- **Reducer actions match §11.3** one-for-one (`designer-reducer.ts`), pure, no I/O.
- **Schema toggle is correctly absent** (E4.1) and pinned by `schema-toggle-v1.1.test.tsx`; the display-mode pills (a different control) are correctly retained.
- **`useLayoutSync` is rewired to `graphUri`** with an accurate doc-comment, replacing the `projectRoot` read.
- typecheck / lint / build / integration all green.

---

## Recommendation

E4 is the section that was supposed to make layout persistence *correct and final*, and it isn't: a display-mode toggle after a drag loses node positions (H1), and the tests that should have caught it instead validate dead reducer actions (H2). Fix order: (1) make the position/viewport state authoritative or stop writing stale `nodePositions` (H1) — and consolidate the two writers while you're there (M2); (2) write real persistence tests that drive `useLayoutSync.getLayout(graphUri)` and the `setLayout(graphUri, payload)` save path, and delete or wire up the dead `setNodePosition`/`setViewport` actions (H2); (3) flatten `LayoutFile` to a single `viewport` and drop the fabricated `db` entries (M1). Then settle L1/L2. `tasks-review-045.md` has the step-by-step.
