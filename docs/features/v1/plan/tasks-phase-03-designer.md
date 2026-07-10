# Phase 3 — Designer v1 (read-only render, db + er, layout persistence)

**Status:** v1, 2026-05-15. Replaces the prior single-file task list; details now live in per-section mini-task-lists under `docs/plan/phase-03/`.
**Branch:** `feat/phase-03-designer`
**Time budget:** 3–4 weeks (Designer body) + ~3–5 days (Phase-2 carryovers).
**Dependencies:** Phase 2 merged; the Phase 2 **Acceptance criteria for Phase 2 as a whole** green.
**Blocks:** Phase 4 (IntelliJ plugin) — soft block, only on convenience.

This document is the **overall task-management document** for Phase 3. It does not contain implementation tasks itself. Implementation tasks live in numbered files under `docs/plan/phase-03/`. Each of those files is a mini-task-list of 6–8 tasks, structured TDD-first (tests defined before implementation), with embedded library references.

## Required reading before starting Phase 3

In this order:

1. **`docs/design/phase-03-contracts.md`** — every type, every LSP method shape, the `.ttrl` JSON schema. This is the single source of truth for shapes; mini-task-lists never override it.
2. **`docs/design/architecture.md`** — §4.7 (Designer), §6 (`.ttrl` format), §4.5 (custom `modeler/*` methods), §8.2 (localization).
3. **`docs/plan/implementation-plan.md`** — the Phase 3 entry plus the §"Goal" / §"Risks" entries at the top.
4. **`docs/plan/progress-phase-02.md`** "Deferred to later phases" — the three carryovers (H, I, J) come from here.

If you skip the contracts doc, you will write the wrong types and either re-do work or merge contract drift.

## How to work through Phase 3

### TDD discipline (non-negotiable)

Every mini-task-list opens with a **Tests-first** section listing concrete test cases the implementer must write **before** any implementation task. The implementation tasks below it reference those test names. Acceptance for a section is when the tests pass; not when the implementation "looks right."

Test scopes used in Phase 3:

- **Unit** — single function or class, no I/O, fast (<10 ms each).
- **Component** — inter-class or inter-object collaboration within one package; may involve a fake/stub at the package boundary; still fast (<200 ms each).
- **(Out of scope here)** end-to-end / cross-host integration — that flow lives in `tests/integration/` and runs in its own pass, not as part of these mini-task-lists.

### Checkbox discipline (non-negotiable)

Tick every checkbox the moment its task is verified done — not at the end of the day, not at PR time. Half a screen of green is the visible proof of progress and the only way the next mini-task-list dispatcher (you, future-you, or the next dev) knows what's safe to start.

If you find yourself about to tick a box for a task whose "Verify by running" command hasn't been run on a fresh tree, stop and run the command first.

### Library-reference discipline (use Context7)

You have access to the `context7` MCP server. **Before writing code against any non-trivial external library** (Cytoscape, antlr4ng, `@vscode/test-electron`, `vscode-languageserver-protocol`, etc.), run:

```
mcp__context7__resolve-library-id  { libraryName: "<lib>", query: "<what you need>" }
mcp__context7__query-docs          { libraryId: "<id>", query: "<focused question>" }
```

The mini-task-lists embed library snippets pulled from training-time knowledge — treat them as a *starting shape*, not as gospel. APIs drift; Context7 has current. If an embedded snippet disagrees with Context7, prefer Context7 and update the mini-task-list in the same PR as the implementation.

The libraries you will need Context7 for during Phase 3:

| Library | Used in | What to look up |
|---|---|---|
| `cytoscape` | C, D, F | Style selectors with `data()` predicates, layout API, viewport events |
| `cytoscape-cose-bilkent` | C | `seed`, `randomize`, `nodeRepulsion`, `idealEdgeLength` |
| `cytoscape-node-html-label` | C, D | Registration, label template config, `onRenderAvailable` |
| `antlr4ng` | I | `DefaultErrorStrategy`, `recover` / `recoverInline` signatures, `Parser._errHandler` |
| `@vscode/test-electron` | J | `runTests` options, `extensionDevelopmentPath`, Mocha runner setup |
| `vscode-languageserver-protocol` | B | Browser bootstrap, `createProtocolConnection` over `MessageChannel` |
| `ajv` | B (layout validation) | Schema compile, error format, draft 2020-12 support |

### Contract amendment discipline

If a mini-task-list reveals a contract that needs to change, the implementer edits `docs/design/phase-03-contracts.md` *first*, the mini-task-list *second*, the implementation *third*. Do not patch around a contract divergence.

## Pre-flight (do this once at the start of the phase)

- [ ] Confirm Phase 2 is merged and its acceptance criteria are green on a fresh clone: `pnpm -r build && test && lint && typecheck` exit 0; `pnpm --filter @modeler/integration-tests test` exits 0.
- [ ] Create branch `feat/phase-03-designer` from the merged Phase 2 PR.
- [ ] Read the four documents in the "Required reading" section above.
- [ ] Walk the current Designer scaffold: `packages/designer/src/{App.tsx,components/Canvas.tsx,components/Header.tsx,components/InspectorPanel.tsx,lsp-client.ts}`. Note what's already wired so you don't re-invent it.
- [ ] Verify `context7` MCP responds: run `mcp__context7__resolve-library-id { libraryName: "cytoscape", query: "smoke" }` and confirm a successful resolution comes back. If it doesn't (quota / auth), fix the credential before continuing — the mini-task-lists depend on this tool.
- [ ] Create `docs/plan/progress-phase-03.md` (or confirm it already mirrors the new structure — it should after the v1 plan restructure).

## Mini-task-list index

Sections are roughly independent. Carryovers H, I, J have no upstream blockers and can run before A; they are stand-alone parser / semantics / VS-Code work that the Designer doesn't depend on. The Designer body (A–G) is mostly sequential.

### Designer body (sequential)

| Section | File | One-line goal |
|---|---|---|
| A | [`phase-03/A-designer-scaffold.md`](phase-03/A-designer-scaffold.md) | Reshape `App.tsx` state via reducer; cleanup vestiges; add header toggles + NL pane shell |
| B | [`phase-03/B-lsp-integration.md`](phase-03/B-lsp-integration.md) | Expand `modeler/getModelGraph`; add `getLayout` / `setLayout` / `exportLayout` / `applyGraphEdit` / `getSymbolDetail`; extend Designer-side LSP client |
| C | [`phase-03/C-db-rendering.md`](phase-03/C-db-rendering.md) | Cytoscape adapter + db table/FK render + display-mode toggle |
| D | [`phase-03/D-er-rendering.md`](phase-03/D-er-rendering.md) | er entity/relation render + Crow's-foot cardinality glyphs |
| E | [`phase-03/E-inspector.md`](phase-03/E-inspector.md) | Inspector panel populated from `getSymbolDetail` |
| F | [`phase-03/F-layout-persistence.md`](phase-03/F-layout-persistence.md) | Debounced `setLayout` on drag/viewport/display-mode; `getLayout` on load |
| G | [`phase-03/G-static-deploy.md`](phase-03/G-static-deploy.md) | Demo-mode landing + GitHub Pages workflow |

### Phase-2 carryovers (parallel-safe)

| Section | File | One-line goal |
|---|---|---|
| H | [`phase-03/H-symbol-indexing.md`](phase-03/H-symbol-indexing.md) | Index `relation`, `query`, `role`, `er2db*`, `er2cnc*` as `SymbolEntry`s |
| I | [`phase-03/I-parse-recovery.md`](phase-03/I-parse-recovery.md) | `RecoveryReportingStrategy` subclass; emit `ttr/parse-recovery-info` |
| J | [`phase-03/J-vscode-smoke.md`](phase-03/J-vscode-smoke.md) | Boot real VS Code via `@vscode/test-electron`; assert Phase-1+2 surface |

### Documentation

| Section | File | One-line goal |
|---|---|---|
| K | [`phase-03/K-documentation.md`](phase-03/K-documentation.md) | `semantics/README.md`, `lsp/README.md`, `designer/README.md`, root README Designer section, architecture §10 close-out |

### Final verification

- [ ] All eleven mini-task-lists have every box ticked.
- [ ] `pnpm -r build && test && lint && typecheck` green on a fresh clone.
- [ ] `pnpm --filter @modeler/integration-tests test` green.
- [ ] `pnpm --filter @modeler/vscode-ext test:smoke` green.
- [ ] `pnpm --filter @modeler/designer build` produces a working static bundle that includes the worker.
- [ ] The GitHub Pages deploy workflow has run at least once successfully; the resulting URL renders `samples/v1-metadata/` in demo mode.
- [ ] Hand-verified demo path (see below).
- [ ] No regressions: every Phase-1 sample still highlights correctly in VS Code; every Phase-2 diagnostic code still fires for its intended trigger; Cmd-click / find-references / hover / Cmd-T still work as in Phase 2.

### Hand-verified demo path

This is the single most important acceptance check. Open the deployed Designer with `?demo=v1-metadata`:

1. The `db` schema appears with all tables and FKs rendered.
2. Toggle the display mode (just-names / with-types / with-constraints); rows re-render without an LSP round-trip.
3. Switch to `er`; entity nodes show Czech display labels (manifest says `preferred = "cs"`); attribute rows are visible inline; relation edges show Crow's-foot cardinality glyphs matching the `.ttr` source.
4. Click any node; the inspector populates with description, tags, source `file:line`, kind-specific data, and a non-empty "Related symbols" list.
5. Drag two or three nodes around in db, then switch to er and back; positions are preserved (in-memory in browser mode).
6. Reload the page. In Node mode (VS Code workspace), positions are restored. In browser/demo mode, positions reset (documented behavior).

If any of these steps fails, do not call Phase 3 done.

## Risks and mitigations

- **Cytoscape Crow's-foot glyph rendering (§D).** Cytoscape has no built-in support. Mitigation: a focused `cy/glyph-renderer.ts` module with unit tests for each glyph shape (`one` / `zero-or-one` / `many` / `one-or-many`), reviewed before §D moves on to integration.
- **Browser-mode cross-file resolution (§B).** Phase 2 `loadProjectFromOpenDocuments` only sees what's been `openDocument`'d. Mitigation: §A's empty-project state explicitly tells the user to load a directory, not a single file. Test in §B-5.
- **`cose-bilkent` layout determinism (§C).** Force-directed layouts produce different positions across runs. Mitigation: pin `randomize: false`, `seed: 1` in the layout config; first run persists to `.ttrl`; subsequent runs read from it.
- **GitHub Pages CORS for the worker (§G).** Vite's `base` setting controls the worker's URL on Pages. Mitigation: explicit task in §G to set `base: '/<repo>/'` for production builds; smoke-curl the deployed `index.html` and worker `.js` in CI before flipping Pages live.
- **Layout file corruption (§F).** Hand-edited or partially-written `.ttrl` could break the Designer. Mitigation: `validateLayout` returns `null` on shape mismatch; caller falls back to `emptyLayout()`; the next save replaces corrupt content.
- **Phase-2 carryover scope (§§H, I, J).** Three sections, three packages (parser, semantics, vscode-ext). Mitigation: do them one PR each, before §A–§G work begins. They are stand-alone.
- **Phase 3 carryover from Phase 2 progress doc (`packages/semantics/README.md`, `packages/lsp/README.md` v2).** Don't drop these. §K explicitly owns them.

## Out of scope for Phase 3 (Phase 4+ or later)

- IntelliJ plugin (Phase 4)
- Designer edit mode and the `WorkspaceEdit` synthesizer (v1.1)
- Natural-language pane wired to an LLM (v1.4)
- `cnc` schema render, Chen / UML display variants for E-R (v1.4)
- Live-database integration (v1.5+)
- VS Code webview embed of the Designer (v1.x)
- Embeddable `<script>` distribution to npm (v1.x; static-site demo is enough for Phase 3)
- Productivity-tier LSP completion (v1.2+)
- Polish-tier LSP rename / format / code actions (v1.3+)
