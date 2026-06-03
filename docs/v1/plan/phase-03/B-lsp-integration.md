# Phase 3.B — LSP integration (custom methods + Designer client + file-system shim)

**Goal:** every custom `modeler/*` method needed by Phase 3 is implemented in the LSP (both `server-stdio.ts` and `server-browser.ts`); the Designer-side client wraps them in typed methods; the file-system shim loads multi-file projects from disk (Node) or upload/FSA (browser).

**Reads:** [contracts §3 / §4 / §5 / §6 / §7](../../design/phase-03-contracts.md), [architecture §4.5](../../design/architecture.md).
**Blocked by:** §A.
**Blocks:** §C (needs the new `getModelGraph` shape and the project loader), §E (needs `getSymbolDetail`), §F (needs `getLayout` / `setLayout`).

## Tests-first

- [ ] `packages/lsp/src/__tests__/model-graph.test.ts` — unit-level, no LSP wire.
  - 2-table fixture with one FK: `getModelGraph` for `db` returns `{ schemaCode: 'db', nodes.length === 2, edges.length === 1 }`; edge's `fromNode` / `toNode` are the table qnames; `nodes[0].rows` non-empty.
  - 1-entity fixture with 2 attributes, no relations: er graph has 1 node, `nodes[0].rows.length === 2`, 0 edges.
  - 1-table with a structured-type column (`varchar(40)`): the row's `type === 'varchar(40)'` (asserts `renderDataType`).
  - Unsupported schema (`'cnc'`): returns `{ schemaCode: 'cnc', nodes: [], edges: [] }` and the test asserts the LSP log mentions "schema not renderable" (use a fake logger).

- [ ] `packages/lsp/src/__tests__/layout.test.ts` — unit-level.
  - `validateLayout(emptyLayout())` returns the same shape.
  - `validateLayout({ version: 2, ... })` returns `null`.
  - `validateLayout({ version: 1, viewports: {}, nodes: {}, edges: {} })` returns `null` (missing required `db`/`er` viewports).
  - Round-trip: serialize `emptyLayout()` with `JSON.stringify`, re-parse, re-validate; expect a structurally identical `LayoutFile`.

- [ ] `tests/integration/src/lsp-phase-03-custom-methods.test.ts` — component-scope, real LSP via the `PassThrough`-paired-connection harness from `tests/integration/src/_helpers/server.ts` (the canonical pattern; see `packages/lsp/__tests__/lsp.test.ts`).
  - `modeler/getProjectInfo` returns the manifest from the samples bundle (unchanged from Phase 2).
  - `modeler/getModelGraph` with `schema: 'db'` on the samples bundle returns ≥5 edges and every edge's `fromNode` / `toNode` resolves to a node in the same response.
  - `modeler/getLayout` for a project root with no `.modeler/` returns `emptyLayout()`.
  - `modeler/setLayout` then `modeler/getLayout` round-trips the same `LayoutFile`.
  - `modeler/applyGraphEdit` returns `{ ok: false, reason: 'edit-mode-not-available-in-v1' }`.
  - `modeler/getSymbolDetail` for `er.entity.artikl` returns a non-null `SymbolDetail` with localized Czech `label` and `description`, `perKindData.kind === 'entity'`, and a non-empty `referencedBy`.

- [ ] `packages/designer/src/fs/__tests__/file-system.test.ts` — jsdom.
  - `loadProjectViaUpload` with a fake `<input>` containing two files (`er.ttr`, `modeler.toml`) returns a `ProjectFiles` whose `files` map has both entries; keys do not start with `/`.
  - `loadProjectViaUpload` filters out a `.png` (or any non-`.ttr`/`.ttrl`/`.toml`) file from the same fake input.
  - `loadProjectViaFileSystemAccessAPI` returns `null` when `window.showDirectoryPicker` is `undefined`.

## Library reference

Run Context7 before coding:

```
mcp__context7__resolve-library-id { libraryName: "vscode-languageserver-protocol", query: "browser MessageChannel transport, createProtocolConnection over BrowserMessageReader/Writer" }
mcp__context7__query-docs        { libraryId: "<id>", query: "browser transport message channel" }

mcp__context7__resolve-library-id { libraryName: "Ajv", query: "compile JSON Schema draft 2020-12, validate, error formatting" }
mcp__context7__query-docs        { libraryId: "<id>", query: "Ajv 2020 draft 2020-12 compile validate strict mode" }
```

Existing Phase-0 code at `packages/designer/src/lsp-client.ts` already shows the working `BrowserMessageReader` / `BrowserMessageWriter` / `createProtocolConnection` shape — that pattern is correct and stays; you're only adding new request handlers around it.

**Library reference (training-time, verify via Context7):** `vscode-languageserver-protocol`'s browser entry exports `BrowserMessageReader`, `BrowserMessageWriter`, and `createProtocolConnection`. Custom methods are registered via `connection.onRequest('modeler/<name>', handler)` on the server side and called via `connection.sendRequest('modeler/<name>', params)` on the client side.

`ajv` accepts draft 2020-12 via `import Ajv2020 from 'ajv/dist/2020.js'`. Compile a schema once at module load, then call `validate(unknown)`; on success it returns `true` and `validate.errors === null`; on failure returns `false` and `validate.errors` has structured detail.

## Implementation tasks

- [ ] **B.1 — `modeler/getModelGraph` rewrite.** New file `packages/lsp/src/model-graph.ts` exporting `buildModelGraph(symbolTable, resolver, ast, schema)` per [contracts §4](../../design/phase-03-contracts.md#4-shared-graph-dtos). Wire from `server.ts` so the request handler signature matches [contracts §7.2](../../design/phase-03-contracts.md#72-modelergetmodelgraph-modified). The unit tests above must turn green here.
- [ ] **B.2 — Layout types + validator + handlers.** New `packages/lsp/src/layout.ts`: `LayoutFile`, `emptyLayout`, `validateLayout` (ajv). New `packages/lsp/schemas/layout.schema.json` per [contracts §6.2](../../design/phase-03-contracts.md#62-json-schema-draft-2020-12). Register `modeler/getLayout` / `modeler/setLayout` / `modeler/exportLayout` in `server-stdio.ts` (with fs) and `server-browser.ts` (in-memory Map keyed by `projectRoot`). Make the layout unit tests + the round-trip integration test green.
- [ ] **B.3 — `modeler/applyGraphEdit` placeholder.** Register in both server entry points; handler returns `{ ok: false, reason: 'edit-mode-not-available-in-v1' }`. No side effects. Integration test asserts the refusal shape.
- [ ] **B.4 — `modeler/getSymbolDetail` handler.** New `packages/lsp/src/symbol-detail.ts`: `buildSymbolDetail(qname, symbolTable, resolver, referenceIndex, manifest, documentCache)`. Returns `null` when qname unknown. Per-kind data shaped per [contracts §5.1](../../design/phase-03-contracts.md#51-per-kind-payload). Localization picks `manifest.preferredLanguage`; fallback `'en'`; fallback to bare value.
- [ ] **B.5 — Designer-side `LspClient` expansion.** Update `packages/designer/src/lsp-client.ts` to expose all methods from [contracts §7](../../design/phase-03-contracts.md#7-lsp-custom-method-contracts). Re-export `ModelGraph`, `LayoutFile`, `SymbolDetail`, `SchemaCode`, `DisplayMode` from `@modeler/lsp` (add to `packages/lsp/src/index.ts`) so the Designer never duplicates these types.
- [ ] **B.6 — File-system shim.** New `packages/designer/src/fs/file-system.ts` per [contracts §3](../../design/phase-03-contracts.md#3-file-system-shim-types). `loadProjectViaFileSystemAccessAPI` uses `showDirectoryPicker`; `loadProjectViaUpload` uses `<input webkitdirectory multiple>`; `downloadFile` triggers a synthetic `<a>` click. Make the jsdom tests green.
- [ ] **B.7 — Wire the file-system shim into `App.tsx`.** On project load, call `Promise.all(files.map(([path, content]) => client.openDocument(uri, content)))` then dispatch `loadProject`. Document the **race-condition guard** in a comment: never call `getModelGraph` before all `openDocument`s settle, or browser-mode cross-file resolution breaks.

## Verify by running

```bash
pnpm --filter @modeler/lsp test
pnpm --filter @modeler/designer test
pnpm --filter @modeler/integration-tests test
pnpm -r typecheck
```

All exit 0. The model-graph integration test prints ≥5 edges for the samples bundle.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] `modeler/getModelGraph`, `modeler/getLayout`, `modeler/setLayout`, `modeler/exportLayout`, `modeler/applyGraphEdit`, `modeler/getSymbolDetail` are all registered in both `server-stdio.ts` and `server-browser.ts`.
- [ ] The Designer's `LspClient` exposes typed wrappers for every method.
- [ ] No type duplication between `@modeler/lsp` and `@modeler/designer` — the shared types live in lsp and are re-exported.
