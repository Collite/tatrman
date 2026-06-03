# 1.1.E.2 — Create-new-graph wizard

**Goal:** the Designer's third entry mode — a multi-step wizard that creates a new `.ttrg` file from a selection of packages and objects.

**Reads:** [contracts §8.5 (`modeler/createGraph`)](../../design/v1-1-contracts.md#85-modelercreategraph-new), [design doc §8.5](../../design/v1.1-packages-and-graphs.md#85-modelerdesigner), `packages/designer/src/GraphPicker.tsx` (from E1).
**Blocked by:** 1.1.E.1.
**Blocks:** E3 (canvas affordances work on graphs created by this wizard too).
**Estimated time:** 3 days.

## Tests-first

- [ ] `packages/designer/src/__tests__/create-graph-wizard.test.tsx` — RTL. Cases (one per wizard step):
  - Step 1 (pick packages): mount with a `PackageGraph` fixture; check 3 boxes; "Next" button activates only when ≥1 box checked.
  - Step 2 (review dependencies): the dep-mini-graph renders the selected packages highlighted; an "Add suggested" button auto-checks transitive deps.
  - Step 3 (pick objects): list shows objects from selected packages; checkbox per object; "Next" activates when ≥1 box checked.
  - Step 4 (pick schema): radio with `er` / `db` options (other schemas disabled in v1.1).
  - Step 5 (name + save): text input for graph name; auto-suggested filename based on name; "Save" button calls `client.createGraph(...)` with the assembled params.
  - Cancel button at any step returns to the graph picker.

## Library reference

```
mcp__context7__resolve-library-id { libraryName: "cytoscape", query: "small embedded graph render, programmatic node highlight" }
mcp__context7__query-docs         { libraryId: "<id>", query: "headless mode, node selection styling" }
```

Step 2's dep-mini-graph reuses the existing Cytoscape integration but in a smaller, embedded panel (300×200ish). Look at `Canvas.tsx` for the pattern.

## Implementation tasks

- [ ] **E2.1 — Create `<CreateGraphWizard />` shell.** New file `packages/designer/src/CreateGraphWizard.tsx`. Implements a 5-step state machine via `useReducer`. Step transitions (`next`, `back`, `cancel`). Header shows progress dots (●○○○○ → ●●○○○ → ...).
- [ ] **E2.2 — Step 1: pick packages.** Fetches `client.getPackageGraph(projectRoot)` on mount; renders a checkbox list of every package with file counts. Tracks selection in local state.
- [ ] **E2.3 — Step 2: review dependencies.** Embed a small Cytoscape canvas showing `PackageGraph` with selected packages highlighted. The user can click suggested transitive packages to add them to selection. "Continue with current selection" advances; "Add all transitive" auto-includes everything reachable.
- [ ] **E2.4 — Step 3: pick objects.** Lists every def in the selected packages, grouped by package and by schema kind. Each row has a checkbox. Bulk-select-package / bulk-select-schema controls. Live counter at bottom: "N of M objects selected".
- [ ] **E2.5 — Step 4: pick schema kind.** Radio: `er` or `db`. (Per B3 decision: one schema per `.ttrg` for v1.1.) Future-multi-schema option is shown as a disabled tooltip-explained checkbox.
- [ ] **E2.6 — Step 5: name + save.** Text input for graph name (validated against `IDENT` rules — see grammar). Auto-suggested filename `<name>.ttrg`. Directory picker (default: `<projectRoot>/graphs/`). On "Save", calls `client.createGraph({ uri, name, schema, packages, objects, description, tags })`. On success: dispatches `openGraph` with the new URI (jumps into the just-created graph).
- [ ] **E2.7 — Wire "Create new graph…" entry on the picker.** In `<GraphPicker />` (from E1), add a primary button at the top: "Create new graph…". Clicking opens the wizard. Closing the wizard returns to the picker.
- [ ] **E2.8 — Add reducer actions: `startCreateWizard`, `cancelCreateWizard`.** Tracks `state.creatingGraph: boolean`. The picker shows the wizard component when true.

## Verify by running

```bash
pnpm --filter @modeler/designer test
pnpm --filter @modeler/designer typecheck
pnpm --filter @modeler/designer build
```

All wizard test cases pass.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] The wizard's 5 steps work end-to-end on a fixture project.
- [ ] Creating a graph through the wizard produces a syntactically valid `.ttrg` (parses cleanly when re-read).
- [ ] After save, the Designer jumps into the just-created graph.
- [ ] Cancel at any step returns to the picker.
