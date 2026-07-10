# Review 008 — Phase 3, Section B (LSP integration)

**Date:** 2026-05-15
**Branch:** `phase-03` (HEAD `528810b`; B work staged on top)
**Scope claimed:** all of Section B (B.1–B.7) per `docs/plan/phase-03/B-lsp-integration.md`.
**Scope verified:** the staged + working tree against the mini-list, contracts §2 / §3 / §4 / §5 / §6 / §7, and full `pnpm -r build && test && lint && typecheck`.

## Verdict

**Section B is not done.** The pipeline is green and many of the moving parts are in place, but the actual Designer-to-LSP flow is broken end-to-end and three of the six contract methods are either missing critical implementation detail or untested. The progress doc still shows every B box unchecked, which — for once — agrees with reality.

## Showstoppers (must-fix before Section B can be called done)

### F1 — `getModelGraph` cannot return data when called from the Designer

`packages/designer/src/App.tsx:43-51`:

```ts
const handleFileLoad = async (files: ProjectFiles) => {
  ...
  for (const [relativePath, content] of files.files) {
    const fileUri = `file:///${files.rootName}/${relativePath}`;
    await client.openDocument(fileUri, content);
  }
  dispatch({ type: 'setProjectUri', uri: `file:///${files.rootName}` });
};
```

then at `App.tsx:58-68`:

```ts
client.getModelGraph(state.projectUri, state.activeSchema)
```

`state.projectUri` is `file:///${rootName}` — a *synthetic project root URI*, never registered as an open document. On the server side, `server.ts:323`:

```ts
const doc = documents.get(params.textDocument.uri);
if (!doc) {
  return { schemaCode: params.schema, nodes: [], edges: [] };
}
```

…always returns empty for that synthetic URI because no document is registered there. The integration test at `tests/integration/src/integration.test.ts:107-139` only passes because it sends a *real* file URI (`file://${samplesDir}/v1-metadata/er.ttr`) and that single sample is rich enough to satisfy `edges.length >= 5`. The browser/Designer flow never sees that path.

This is the single bug that defeats the entire Designer-side Phase 3. Two ways to fix, pick one:

- **Server-side:** `buildModelGraph` should iterate every open document and merge nodes/edges where the schema directive matches the requested schema. Then the request param can stay shape-compatible (`textDocument.uri` = a hint) but the server does project-scoped graph building.
- **Client-side:** the Designer keeps a "primary" file URI per schema and passes that to `getModelGraph`. This regresses the "load a directory" UX the mini-list called out as the §B-5 mitigation.

The mini-list explicitly flagged this in §"Risks" of the parent plan and assigned the test to `§B-5`. Pick one path, write the test that catches the broken-URI case, then close it.

### F2 — `buildSymbolDetail` returns degraded `{ kind: 'other' }` for every symbol

`packages/lsp/src/model-graph.ts:327-347`:

```ts
export function buildSymbolDetail(
  qname: string,
  symbols: ProjectSymbolTable,
  resolver: Resolver,
  refIndex: ReferenceIndex,
  manifest: ResolvedManifest
): SymbolDetail | null {
  const symbol = symbols.get(qname);
  if (!symbol) return null;

  const schemaCode = symbol.qname.split('.')[0] ?? 'db';
  const namespace = symbol.qname.split('.')[1] ?? '';
  return buildSymbolDetailForDef(
    { kind: symbol.kind, name: symbol.name, source: symbol.source, tags: [] } as Definition,
    ...
  );
}
```

The Definition is forged from the four fields on `SymbolEntry` and a hard-coded `tags: []`. That object has no `columns`, no `attributes`, no `nameAttribute`, no `displayLabel`, no `description`, no `cardinality`. Walking through `buildSymbolDetailForDef`:

- `def.kind === 'table' && def.columns` → `def.columns` is undefined → skipped.
- `def.kind === 'view' && def.columns` → skipped.
- `def.kind === 'entity'` → enters the branch but `def.attributes ?? []` is `[]`, `def.nameAttribute` is undefined → returns `{ kind: 'entity', attributes: [], nameAttributeQname: null, codeAttributeQname: null, roleQnames: [] }` with all real data lost.
- `'description' in def && def.description` → false → `description: null`.
- `def.displayLabel` → undefined → label falls back to `def.name`.

Net result: every `getSymbolDetail` response is missing its `perKindData` body, every label is the bare name, every description is null, every tags array is empty. The contract §5 promises rich, localized per-kind data; the implementation delivers a husk. The `resolver` parameter is accepted but never used.

The fix requires holding the parsed AST per document (the `documents` `TextDocuments` collection still has the source — re-parse it, find the def by qname, and pass the *real* `Definition` to `buildSymbolDetailForDef`). The integration test at contracts §B-tests-first item 6 (`getSymbolDetail for er.entity.artikl`) would have caught this — see F4.

### F3 — Race-condition guard from B.7 is missing in spirit and in comment

B.7 is explicit:

> On project load, call `Promise.all(files.map(([path, content]) => client.openDocument(uri, content)))` then dispatch `loadProject`. Document the **race-condition guard** in a comment: never call `getModelGraph` before all `openDocument`s settle, or browser-mode cross-file resolution breaks.

The handler at `App.tsx:43-51` *does* `await` each open in sequence (so the race is structurally avoided), but:

- It uses a `for...of` loop rather than `Promise.all`. Functionally equivalent for ordering, slower for many files. Not a deviation if intentional, but the mini-list specified `Promise.all`.
- **There is no comment** explaining the guard. A future maintainer who replaces this with parallel/fire-and-forget will silently break browser-mode cross-file resolution, exactly the failure mode the mini-list called out.

Add the comment. Optionally switch to `Promise.all`.

### F4 — Four of six prescribed integration tests are missing

`docs/plan/phase-03/B-lsp-integration.md` "Tests-first" §3 prescribed a separate file `tests/integration/src/lsp-phase-03-custom-methods.test.ts` with six named test cases. None of those tests exist; instead two related cases were appended to `integration.test.ts`:

| # | Prescribed test | Status |
|---|---|---|
| 1 | `getProjectInfo` returns the manifest from samples bundle | ✅ already in Phase 2 tests |
| 2 | `getModelGraph` with `schema: 'db'` on samples bundle returns ≥5 edges | ⚠️ tested only with `schema: 'er'`; never `'db'` |
| 3 | `getLayout` for project root with no `.modeler/` returns `emptyLayout()` | ❌ missing |
| 4 | `setLayout` then `getLayout` round-trips the same `LayoutFile` | ❌ missing |
| 5 | `applyGraphEdit` returns `{ ok: false, reason: 'edit-mode-not-available-in-v1' }` | ❌ missing |
| 6 | `getSymbolDetail` for `er.entity.artikl` returns Czech label/desc, `perKindData.kind === 'entity'`, non-empty `referencedBy` | ❌ missing — *and would catch F2* |

The Phase-3 contract test pyramid is missing its only assertions on three of the six new methods. The tests-first discipline is non-negotiable per the parent plan. Without them the LSP's wire surface is unverified at the request boundary.

## Should-fix issues

### F5 — `setProjectUri` action is undocumented and skips `symbolDetails` reset

`designer-reducer.ts:80-81`:

```ts
case 'setProjectUri':
  return { ...state, projectUri: action.uri };
```

This action is not in contracts §2. App.tsx (`App.tsx:50`) dispatches it instead of the documented `loadProject`, which means switching projects no longer resets `state.symbolDetails`. Stale per-symbol data from a previous project will surface in the inspector after a project change.

Also: the existing reducer test for `'loadProject' resets symbolDetails cache` still passes — but it's now testing dead code from the App's perspective. Either:

- Delete `setProjectUri`, dispatch `loadProject` from `handleFileLoad`, and the test stays meaningful.
- Or: amend contracts §2 to add `setProjectUri`, give it the same `symbolDetails: {}` reset as `loadProject`, write a test for that, and explain why two URI-setters exist.

(There's no good reason for two — pick the first option.)

### F6 — `setGraph` action and `graph` field on `DesignerState` are undocumented

`designer-state.ts:13` adds `graph: ModelGraph | null`; `designer-reducer.ts:14, 78-79` add `setGraph`. Contracts §2 describes neither. Either amend the contracts (open `phase-03-contracts.md`, bump v1 → v2, add to §2 + changelog) or move the graph cache out of reducer state — Section C will reshape this when it wires the Cytoscape adapter. Front-running C invites a second amendment soon.

### F7 — File input lacks `webkitdirectory`; `loadProjectViaUpload` depends on it

`Header.tsx:74-81`:

```html
<input type="file" ref={fileInputRef} onChange={handleFileChange} accept=".ttr" multiple ... />
```

`file-system.ts:21-23` reads `file.webkitRelativePath` to derive `rootName` and the relative key:

```ts
const relativePath = file.webkitRelativePath
  ? file.webkitRelativePath.split('/').slice(1).join('/')
  : file.name;
```

Without `webkitdirectory` on the input, `webkitRelativePath` is empty and every file falls into the `file.name` branch. Result: `rootName` stays `'project'` (the default in `file-system.ts:11`), the synthetic `projectUri` becomes `file:///project`, and the URIs sent to `openDocument` are `file:///project/<filename>`. This is internally consistent but defeats the "open a directory" affordance the mini-list calls out.

Also, `accept=".ttr"` filters out `.toml` and `.ttrl` from the picker, so a user picking files won't have the manifest available even though the shim's filter accepts it. Either match (`accept=".ttr,.ttrl,.toml"`) or rely entirely on the directory picker.

The "Open Folder" button uses `loadProjectViaFileSystemAccessAPI`, which is what the directory flow requires. The "Load .ttr files" button is the broken one.

### F8 — `accept=".ttr"` mismatch between input and shim filter

Same root cause as F7's second paragraph; flagged separately because changing `accept` is a one-character fix and the directory-attribute change is bigger.

## Nits / observations

- **Duplicate layout tests.** `model-graph-layout.test.ts` (9 cases, from Section A) and `model-graph.test.ts:72-94` (4 cases, added by Section B) overlap on `validateLayout(emptyLayout())`, version-rejection, missing-required-key, and round-trip. The new file's layout tests should be removed; let the older file own them.
- **`buildModelGraph` early-exit on schema mismatch.** `model-graph.ts:363-365` returns an empty graph when `ast.schemaDirective.schemaCode !== schema`. Reasonable per-document. But once F1 is fixed (multi-document), the *project* doesn't have one schema directive — each document does. The right shape is "include nodes from documents whose directive matches; skip the rest." Don't carry this early-exit forward.
- **`extractFkRef` handles only the first list item.** `model-graph.ts:493-495`: a multi-column FK encoded as `from: [a.id, a.tenant_id]` only resolves the first column to a table. That's fine for the table-level edge but means the function silently truncates. A one-line comment naming the intent ("FK edges are table-to-table; pick the first column to derive the source table") would prevent a future dev from "fixing" it incorrectly.
- **Contract-amendment debt for Section A's file-collapse decision.** Contracts §6.2 / §B.2 / §B.4 prescribe `packages/lsp/schemas/layout.schema.json`, `packages/lsp/src/layout.ts`, `packages/lsp/src/symbol-detail.ts`. Section A inlined all three into `model-graph.ts`; the v1 changelog entry recorded *that* fact but Section B did not split them as B.2 / B.4 explicitly require. Consistent inlining is fine if it's deliberate; either split for B (matches the mini-list) or amend B.2 / B.4 to acknowledge the inlining.
- **Progress doc not updated.** `docs/plan/progress-phase-03.md` shows every B-box unchecked. Once F1–F4 land, tick them honestly; do not bulk-tick.
- **`server.ts` deep-imports `RenderableSchemaCode` and `LayoutFile` from `./model-graph.js`** (line 38) rather than the canonical re-export at `index.ts`. Within-package this is fine; it does mean `index.ts` is informational only and `model-graph.ts` is still the de-facto public surface. Consider a future split.

## What was done well

- The `model-graph.ts` `buildModelGraph` for the table/view/entity/FK/relation paths is clean: build a known-qname set first, then resolve each FK/relation against it, skip the edge silently when either side is unknown. This matches contracts §4.2 exactly.
- The browser/Node duality of `getLayout` / `setLayout` is captured cleanly via `opts.layoutStore`. Node mode does the atomic `tmp` + `rename`. Browser mode uses an in-memory `Map`. Both branches return `emptyLayout()` on miss as the contract requires. (The integration tests for these don't exist yet — F4 — but the code is right.)
- `applyGraphEdit` is the right one-liner.
- `LspClient` exposes typed wrappers for every method in contracts §7. Re-exports flow through `@modeler/lsp`'s barrel; no type duplication remains.
- `cardinality` is parsed correctly; `extractCardinality` is wired into the `relation` edge path.

## Verification commands run

```
pnpm -r build       → all green
pnpm -r test        → designer 16, lsp 37, vscode-ext 6, integration 15 — all pass
pnpm -r lint        → all green
pnpm -r typecheck   → all green
```

## Architectural assessment

The Section B code lays out the right shapes — DTOs, the layout-handler split between Node and browser modes, the `LspClient` wrapper surface. What's missing is the *plumbing that proves the shapes work end-to-end*: the Designer's `getModelGraph` call lands on an empty document; the symbol-detail builder discards its inputs; four of six prescribed integration tests don't exist; and three undocumented reducer additions front-run Section C.

Recommend: do not move on to Section C until F1–F4 are closed and the integration tests for *all* six methods turn green. F5–F8 are smaller but should land in the same PR series so Section C starts with a clean reducer surface.
