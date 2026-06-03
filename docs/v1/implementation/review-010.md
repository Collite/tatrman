# Review 010 — Phase 3, Section C (db schema rendering)

**Date:** 2026-05-15
**Branch:** `phase-03` (HEAD `528810b`); Section C is staged + working-tree edits, no new commit.
**Scope claimed:** all of Section C (C.1–C.6) per `docs/plan/phase-03/C-db-rendering.md`.
**Scope verified:** `git diff HEAD` for `packages/designer/{src,package.json}` and `pnpm-lock.yaml`, plus the new `packages/designer/src/cy/` directory and `Canvas.test.tsx`. Full pipeline.

## Verdict

**The mechanics are mostly right — the discipline isn't.** Section C delivers `modelGraphToCyElements`, the Cytoscape extension wiring, the schema-and-display-mode toggles, and a graph cache, all on a green pipeline (24 designer tests pass). But two of the three prescribed Canvas tests are missing, the contract amendment C.5 explicitly required wasn't filed, the cache is write-only (so schema-toggle still round-trips to the LSP), and `loadProject` doesn't reset `graphsBySchema`, which is the same project-scope-data-not-reset bug we caught in review-008.

Same recurring failure mode as Section B: the *shapes* are in place, the *plumbing* leaks at the joins.

## Showstoppers (must-fix)

### F1 — Only one of three prescribed Canvas-db tests is on disk

Mini-list "Tests-first" §2 named three cases:

1. *On `state.activeSchema === 'db'` and a non-null `projectUri`, App calls `client.getModelGraph(uri, 'db')` exactly once after `loadProject` settles.*
2. *Switching `displayMode` does NOT call `getModelGraph` — only `modelGraphToCyElements` is re-invoked.*
3. *Switching `activeSchema` from `db` to `er` DOES call `client.getModelGraph(uri, 'er')`.*

`packages/designer/src/components/__tests__/Canvas.test.tsx` exists (note: not `Canvas-db.test.tsx` as the mini-list named it) and contains a single test:

```ts
it('renders a container div', () => {
  render(<Canvas graph={null} displayMode="just-names" onNodeSelect={vi.fn()} />);
  expect(document.querySelector('.bg-white')).toBeInTheDocument();
});
```

That's a smoke test, not the three behavioral cases. Worse, all three named cases actually live in *App-level* logic (the `getModelGraph` `useEffect` is in `App.tsx:40-48`, not in `Canvas.tsx`) — so the test file is named for the wrong component, and the assertions that would catch F4 / F5 below don't exist.

Either rename to `App.test.tsx` and add the three cases there, or keep `Canvas.test.tsx` for the Cytoscape-mocked rendering surface and add a new `App-getModelGraph.test.tsx` for the call-count assertions. The behavior must be tested; without it, the schema-toggle regression in F5 will surface as a visible bug rather than a red test.

### F2 — Contract amendment C.5 required is missing

C.5 spells it out:

> *The reducer holds a `graphsByCachedSchema?: Record<RenderableSchemaCode, ModelGraph>` cache so flipping back is instant. **Open a contract amendment if the cache field is needed (it is).***

`docs/design/phase-03-contracts.md` §2 still describes `DesignerState` without `graphsBySchema` and `DesignerAction` without `storeGraph`. The changelog still ends at **v1**. The implementer added the field and the action (correctly), but didn't follow the contract-amendment discipline that the parent plan declared non-negotiable. Same omission as review-008 F6.

Bump to **v2**, add the field + the action under §2 verbatim, and write the changelog line.

(Naming nit: the mini-list suggested `graphsByCachedSchema`; the impl chose `graphsBySchema`. The shorter name is fine — record it in the amendment.)

### F3 — `loadProject` doesn't reset `graphsBySchema`

`designer-reducer.ts:18-23`:

```ts
case 'loadProject':
  return {
    ...state,
    projectUri: action.projectUri,
    symbolDetails: {},
  };
```

`symbolDetails` resets; `graphsBySchema` doesn't. Switching projects leaves the previous project's graphs in the cache; the next `getModelGraph` round-trip eventually replaces them, but until then the Canvas renders stale nodes/edges from the old project. Same pattern that surfaced as F5 in review-008 (where `setProjectUri` skipped the symbolDetails reset).

Same fix shape: `graphsBySchema: { db: null, er: null }` inside the `loadProject` case. Add a reducer test that pre-populates `graphsBySchema` and confirms `loadProject` clears it.

### F4 — Dead `currentGraph` `useState` + mirror effect

`App.tsx:17,50-52`:

```ts
const [currentGraph, setCurrentGraph] =
  useState<typeof initialDesignerState.graphsBySchema>({ db: null, er: null });
...
useEffect(() => {
  setCurrentGraph(state.graphsBySchema);
}, [state.graphsBySchema]);
```

This mirrors `state.graphsBySchema` into a local `useState` and re-renders on every change. Two problems:

- It's pointless: `state.graphsBySchema[state.activeSchema]` is already in scope at the render site, no mirror needed.
- It introduces a one-cycle delay: dispatch → reducer commit → effect → setCurrentGraph → re-render. The first paint after `storeGraph` shows the old `currentGraph` value.

Delete the `useState` and the effect; change `Canvas`'s prop to `graph={state.graphsBySchema[state.activeSchema]}` directly. This is the same anti-pattern as the `setProjectUri` second-state-source from review-008.

### F5 — Cache is write-only; schema-toggle still round-trips to LSP

`App.tsx:40-48`:

```ts
useEffect(() => {
  const client = clientRef.current;
  const uri = state.projectUri;
  if (!client || !uri) return;
  const schema = state.activeSchema;
  client.getModelGraph(uri, schema).then((graph) => {
    dispatch({ type: 'storeGraph', schema, graph });
  });
}, [state.projectUri, state.activeSchema]);
```

This always calls `getModelGraph` when `activeSchema` flips, even if `state.graphsBySchema[schema]` is already populated. C.5: *"so flipping back is instant."* Currently flipping back fires another LSP round-trip, then dispatches a new `storeGraph` with a new `ModelGraph` object — which (combined with Canvas's `useEffect` on `[graph]`) triggers `cy.elements().remove()` and a fresh `cose-bilkent` layout, discarding any drag positions on the previous schema.

Guard the call:

```ts
if (state.graphsBySchema[schema]) return;
client.getModelGraph(uri, schema).then(...);
```

Add the corresponding test (`Switching activeSchema back to a previously-loaded schema does NOT call getModelGraph again`) — the mini-list named three cases; this is the fourth that the cache existence implies.

## Should-fix

### F6 — Canvas layout-once-on-load is "once per `ModelGraph` reference"

`Canvas.tsx:128-150` runs `cy.elements().remove()` + a fresh `cose-bilkent` layout every time the `graph` prop's reference changes. Combined with F5, every schema toggle and every project re-load resets layout, even when the *content* is identical. After F5 is fixed (cache short-circuits the LSP call), the `useEffect` will only fire when the cache is populated for the first time — which is the intended behavior. No code change needed here once F5 lands, but pin it with a reducer/component test: assert that dispatching `storeGraph` with the *same* `ModelGraph` reference does not trigger layout (object identity check).

### F7 — Style: dark node background, no styling on HTML rows

`Canvas.tsx:57-65`:

```ts
{
  selector: 'node',
  style: {
    'background-color': '#0f172a',  // near-black
    color: '#1e293b',                // dark slate
    'font-size': '11px',
    'text-valign': 'bottom',
    ...
  },
},
```

`text-valign: 'bottom'` puts Cytoscape's own `label` field *below* the node — so the bare `def.name` renders on the white canvas in dark text. Fine.

But the actual table rows render via `cy.nodeHtmlLabel(...)` (line 100), and the `data.labelHtml` produced by `adapter.ts` uses CSS classes (`cy-node-label`, `cy-row`, `cy-row-name`, `cy-row-type`, `cy-row-badge`) **with no stylesheet binding**. The default browser text color is dark, the node background is near-black, contrast is approximately zero. Either:

- Add a stylesheet under `packages/designer/src/index.css` (or a sibling file) defining these classes with light foreground.
- Or change the node background to a light fill and keep the dark text.

Mini-list C.6 "DONE when" includes "no broken HTML in node labels"; readability isn't explicitly named but is the obvious goal. Fix before manual test passes.

### F8 — No HTML escaping in `nodeLabelHtml`

`adapter.ts:8-20` builds row HTML by interpolating `row.name`, `row.type`, and constraint strings directly into a template:

```ts
const name = `<span class="cy-row-name">${row.name}</span>`;
const type = row.type ? `<span class="cy-row-type">${row.type}</span>` : '';
```

ttr identifiers and type names are grammar-restricted today (alphanumerics, underscores, parens for `varchar(40)`), so this is safe right now. But if `description` (or any future user-supplied string field) is ever woven into `labelHtml`, this becomes an XSS surface. Add a one-line `escapeHtml(s)` helper in the adapter and apply it to every interpolation, even when the input is "safe" — it costs nothing and keeps Section D / E honest.

## Nits

- **Canvas dynamic-import chain (`Canvas.tsx:38-46`)** uses nested `.then`s plus `(mod as any).default ?? mod` for three packages. Hoist to a top-level `Promise.all([import('cytoscape'), import('cytoscape-cose-bilkent'), import('cytoscape-node-html-label')])` and write the unwrap once. Same behavior, less ceremony, fewer `any` casts.
- **`cytoscape.use(cose)` inside the effect** — React strict mode in dev calls effects twice. Cytoscape's `use` is idempotent for the same extension instance, but the assertion isn't documented anywhere in the file. Move `cytoscape.use(...)` and `nodeHtmlLabel(...)` into the module-load side of the dynamic import (a top-level Promise that registers once and resolves with the prepared cytoscape).
- **`storeGraph` action is dispatched unconditionally even when the fetch produced an empty graph.** That's fine for the cache (replacing null with an empty graph still indicates "we asked"), but the effect at line 40-48 doesn't `.catch(...)` — a rejected promise (LSP error) silently swallows. Add `.catch((e) => dispatch({ type: 'setError', message: String(e) }))`.
- **`Canvas.tsx` types Cytoscape as `any`** via `type CytoscapeInstance = any` (line 6). Cytoscape ships `@types/cytoscape`-style declarations through its own package; the explicit `any` should at most be `cytoscape.Core` for `cyRef` and `cytoscape.EventObject` for the event arg. The escape hatch is fine if specifically scoped, but workspace ESLint forbids `any` outside `generated/**` — the inline `eslint-disable` directives that allow this in `Canvas.tsx` push back against that rule for a fixable reason.
- **`packages/designer/src/cy/cytoscape-ext.d.ts`** declares `interface Core { nodeHtmlLabel(...) }` against `cytoscape`. Good — but it currently lives next to the adapter, not under `src/types/` or similar. Either move it or add a one-line comment naming why it's colocated.
- **Progress doc not ticked.** `docs/plan/progress-phase-03.md` still has every C.* box unchecked. After F1-F5 land, tick them honestly.

## What was done well

- `modelGraphToCyElements` is a clean pure function: no Cytoscape coupling, separate per-mode row rendering, edge/node element shape matches what the Canvas effect expects. The 6 adapter unit tests cover all three display modes and the edge case of empty graph / rowless node — well-scoped.
- The Canvas's *display-mode* effect (`Canvas.tsx:152-166`) correctly does *not* re-layout; it updates `data.labelHtml` per node and calls `nodeHtmlLabel('update')`. This is exactly the C.4 semantics for the display-mode toggle.
- Cytoscape extensions are added as a top-level dynamic import (`Canvas.tsx:38-40`), which lets Vite code-split them out of the main chunk. Bundle output confirms: 442KB cytoscape + 78KB cose-bilkent + 5KB node-html-label as separate chunks; main `index.js` shrunk to 332KB.
- The `cytoscape-ext.d.ts` ambient declarations are the right pattern for the two type-less extensions; no `// @ts-expect-error` litter in the source.
- The `storeGraph` action is correctly schema-keyed (`{ schema, graph }`), not single-graph — so when F5's cache guard lands, flipping back genuinely is instant.

## Verification commands run

```
pnpm -r build       → all green (designer bundle splits cytoscape into its own chunk; main 332KB)
pnpm -r test        → designer 24, lsp 35, vscode-ext 6, integration 21 — 86 total, all pass
pnpm -r lint        → all green, 0 warnings
pnpm -r typecheck   → all green
```

The designer's test count is up by 7 (17 → 24): 6 new adapter cases + 1 Canvas smoke test. The three prescribed Canvas-db behavioral tests aren't counted because they aren't on disk.

## Recommendation

One focused PR: write the three (now four, with F5's guard) App-level tests, file the contract amendment for `graphsBySchema` + `storeGraph`, fix the `loadProject` reset, delete the `currentGraph` mirror, add the cache-hit guard, fix label readability, add HTML escaping. After that Section C is honestly done and Section D's er-rendering can ride on a stable adapter + Canvas + reducer surface.
