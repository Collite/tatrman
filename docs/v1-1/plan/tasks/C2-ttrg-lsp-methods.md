# 1.1.C.2 — `.ttrg` LSP custom methods

**Goal:** ship the six new `modeler/*` methods and update the three existing methods to take a `graphUri` parameter. Both `server-stdio.ts` and `server-browser.ts` register them; the Designer's `LspClient` exposes typed wrappers.

**Reads:** [contracts §8 (LSP custom-method contracts)](../../design/v1-1-contracts.md#8-lsp-custom-method-contracts), `packages/lsp/src/server.ts`, `packages/lsp/src/server-stdio.ts`, `packages/lsp/src/server-browser.ts`, `packages/designer/src/lsp-client.ts`, `packages/lsp/src/model-graph.ts`. Planning context: [`docs/v1-1/plan/section-C-plan.md`](../section-C-plan.md).
**Blocked by:** 1.1.C.1.
**Blocks:** D (smoke test calls the new methods), E (Designer consumes them).
**Estimated time:** 3 days.

---

## Status & decisions (2026-05-20) — READ FIRST

This is the largest single v1.1 task (8 sub-tasks, first real use of `@modeler/edit`, touches both server entry points + the Designer). **Split the work into two reviewable changesets** so neither lands as one giant diff:

- **C2-read** — `listGraphs`, `getGraph`, `getPackageGraph`, and the read-side of the updated methods (`getModelGraph`/`getLayout` taking `graphUri`).
- **C2-write** — the three `WorkspaceEdit` builders (C2.6) + `addObjectToGraph`/`removeObjectFromGraph`/`createGraph` + `setLayout`/`exportLayout`.

**Decisions still open — settle before the matching sub-task:**

- **D3 (settle before C2.2 / C2.7) — `getModelGraph` vs `getGraph` redundancy.** Contract §8 keeps both: `modeler/getModelGraph` (v1, now `graphUri`-scoped) and the new `modeler/getGraph`. They look near-identical (both return nodes/edges for rendering). **Decide whether `getGraph` supersedes `getModelGraph`** (then deprecate/remove the latter) or they serve distinct callers. Check what the Designer (E1–E4) actually calls before shipping two parallel methods.
- **D4 (do as part of C2.7) — `.ttrl` removal blast radius.** Removing the project-wide `<root>/.modeler/layout.ttrl` reverses the documented "layout is a sidecar" invariant. Audit every `.ttrl` read/write in `model-graph.ts` and `server.ts`, confirm no v1 D/E path still depends on it, and update the invariant docs (see CC2).
- **D1 (decided) — layout keys are UNQUOTED dotted ids.** `setLayout` (C2.7) must **emit** node keys as unquoted dotted ids, e.g. `nodes: { billing.invoicing.er.entity.artikl: { x, y } }` — not quoted. (Full rationale in C1 / section-C-plan.md.)

## Tests-first

- [ ] `tests/integration/src/lsp-v1.1-graph-methods.test.ts` — new file. Cases over the `PassThrough`-paired-connection harness:
  - `modeler/listGraphs` for a fixture project with two `.ttrg` files returns `{ graphs: [..., ...] }` with the right names, schemas, and object counts.
  - `modeler/getGraph` for a `.ttrg` whose objects all resolve returns `{ schema, nodes, edges, layout, missingObjects: [] }` with edges correctly computed per C1.5.
  - `modeler/getGraph` for a `.ttrg` with one stale object returns `missingObjects: ['the.stale.qname']`.
  - `modeler/addObjectToGraph` returns a `WorkspaceEdit` that, when applied (use the LSP's text-document store to simulate), makes the next `getGraph` call include the new object. With `autoImport: true`, the `WorkspaceEdit` also adds an `import` statement.
  - `modeler/removeObjectFromGraph` round-trips analogously.
  - `modeler/createGraph` produces a `WorkspaceEdit` whose `documentChanges` include a `CreateFile` operation and an initial `TextEdit` that writes the canonical `.ttrg` content.
  - `modeler/getPackageGraph` returns the expected `{ packages, dependencies, cycles }` for a known-cyclic fixture.
- [ ] `packages/lsp/src/__tests__/graph-builders.test.ts` — new file, pure-unit. Cases for the helper functions:
  - `buildAddObjectEdit(graphDoc, qname, autoImport)` produces a `WorkspaceEdit` with the correct insertion point inside the `objects: [...]` array, preserving prevailing indentation and trailing comma.
  - `buildRemoveObjectEdit(graphDoc, qname, pruneUnusedImport)` removes the entry plus the surrounding comma; when `pruneUnusedImport: true` and no other object needs the import, the `import` line is also removed.
  - `buildCreateGraphContent(params)` produces the canonical file body matching [contracts §7.1](../../design/v1-1-contracts.md#71-concrete-syntax-example).

## Library reference

```
mcp__context7__resolve-library-id { libraryName: "vscode-languageserver-types", query: "WorkspaceEdit, CreateFile, documentChanges, TextDocumentEdit" }
mcp__context7__query-docs         { libraryId: "<id>", query: "WorkspaceEdit documentChanges resource operations" }
```

Existing edit-synthesis pattern is in `packages/edit/` (placeholder in v1). Start from the `WorkspaceEdit` factory shape there; the new builders are the first real users.

## Implementation tasks

- [ ] **C2.1 — Add `modeler/listGraphs` handler.** New file `packages/lsp/src/graph-methods.ts` (or extend an existing module). Reads the project's `.ttrg` files via the document cache; for each, returns the `GraphMetadata` shape from [contracts §8.1](../../design/v1-1-contracts.md#81-modelerlistgraphs-new). Register in both server entry points.
- [ ] **C2.2 — Add `modeler/getGraph` handler.** Calls into C1's `computeGraphEdges` + reuses the v1 per-kind row builders from `model-graph.ts` to populate `ModelGraphNode.rows`. Returns the [contracts §8.2](../../design/v1-1-contracts.md#82-modelergetgraph-new) shape. **Resolve D3 first** (getGraph vs getModelGraph) so you don't duplicate logic across two methods — share the node/edge builders regardless of the outcome.
- [ ] **C2.3 — Add `modeler/addObjectToGraph` and `modeler/removeObjectFromGraph` handlers.** Both call into `buildAddObjectEdit` / `buildRemoveObjectEdit` (C2.6). Return the resulting `WorkspaceEdit`; the host applies it via standard `workspace/applyEdit`.
- [ ] **C2.4 — Add `modeler/createGraph` handler.** Calls `buildCreateGraphContent` (C2.6). Returns a `WorkspaceEdit` with `documentChanges: [CreateFile, TextDocumentEdit]`. Validate that `uri` ends in `.ttrg` and the parent directory exists (in-memory check via the document cache).
- [ ] **C2.5 — Add `modeler/getPackageGraph` handler.** Reads from the cached `PackageGraph` produced in B3.7. Returns [contracts §8.6](../../design/v1-1-contracts.md#86-modelergetpackagegraph-new) shape.
- [ ] **C2.6 — Implement the three `WorkspaceEdit` builders.** New file `packages/edit/src/graph-edits.ts`. `buildAddObjectEdit`, `buildRemoveObjectEdit`, `buildCreateGraphContent`. Use the parser's CST view (`getHiddenTokensToLeft/Right`) to detect indentation and trailing-comma policy in the `objects: [...]` array; insert/remove cleanly.
- [ ] **C2.7 — Update `modeler/getModelGraph`, `modeler/getLayout`, `modeler/setLayout`, `modeler/exportLayout`.** Per [contracts §8.7](../../design/v1-1-contracts.md#87-updated-existing-methods), all four now take a required `graphUri`. The project-wide layout (`<project-root>/.modeler/layout.ttrl`) is gone; layout lives inside the `.ttrg`'s `layout` block. Update `model-graph.ts` accordingly and remove the `.ttrl` read/write paths (D4 — audit all `.ttrl` references first; confirm no v1 path still depends on the sidecar). **`setLayout` must emit `layout.nodes` keys as UNQUOTED dotted ids** (D1), e.g. `nodes: { billing.invoicing.er.entity.artikl: { x, y } }`. If D3 resolves to "getGraph supersedes getModelGraph", deprecate/remove `getModelGraph` here instead of adding a `graphUri` to it.
- [ ] **C2.8 — Expand `LspClient` in the Designer.** Add typed wrappers for all six new methods plus the four updated signatures. Re-export the new request/response types from `@modeler/lsp/src/index.ts` so the Designer never duplicates them.

## Verify by running

```bash
pnpm --filter @modeler/lsp test
pnpm --filter @modeler/edit test
pnpm --filter @modeler/integration-tests test
pnpm -r typecheck
```

All integration cases green; both unit-test files green.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] Shipped/reviewed as two changesets (C2-read, then C2-write) per the Status note.
- [ ] All six new `modeler/*` methods registered in both server entry points.
- [ ] All four updated methods take `graphUri`; the old `.ttrl` paths are deleted (D4) and `setLayout` emits unquoted dotted-id node keys (D1).
- [ ] D3 settled: either `getGraph` and `getModelGraph` are clearly distinct, or `getModelGraph` is deprecated/removed (no two near-identical methods).
- [ ] `LspClient` exposes typed wrappers; no type duplication between `@modeler/lsp` and `@modeler/designer`.
- [ ] No VS Code changes yet — D registers `.ttrg` as a language and updates the smoke test.

---

## Cross-cutting carry-over (paired with this section's work)

- [ ] **CC2 — Architecture-doc invariant update (do with C2.7/D4).** The "Text is canonical; layout is a sidecar `.modeler/layout.ttrl`" invariant in `CLAUDE.md` and `docs/v1/design/architecture.md` is being reversed — layout now lives inside the `.ttrg`. Update both docs when the `.ttrl` paths are removed so they don't describe a sidecar that no longer exists.
- [ ] **CC3 — Contract version bump.** If D3 changes the §8 method surface (e.g. removing `getModelGraph`), amend contracts §8 and bump the version per the amendment discipline. (The §7.1 layout-key amendment is owned by C1.7.)
