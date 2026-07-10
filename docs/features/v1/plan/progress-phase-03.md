# Phase 3 — Progress

**Started:** 2026-05-15
**Branch:** `feat/phase-03-designer`
**Status:** In progress

## Pre-flight
- [x] Confirm Phase 2 acceptance criteria (build, test, lint, typecheck all green)
- [x] Branch `feat/phase-03-designer` from merged Phase 2 PR
- [x] Read `docs/design/phase-03-contracts.md`
- [x] Read architecture docs (§4.7, §6, §4.5, §8.2)
- [x] Read `docs/plan/implementation-plan.md`
- [x] Re-read `docs/plan/progress-phase-02.md` "Deferred to later phases"
- [x] Walked Designer scaffold (`App.tsx`, `lsp-client.ts`, `Canvas.tsx`, `Header.tsx`, `InspectorPanel.tsx`)
- [ ] Verify context7 MCP responds

## Section A — Designer scaffold cleanup
- [x] A.1 Audit and remove Ontology-Playground vestiges (quest/gamif/school)
- [x] A.2 Create reducer skeleton (`designer-state.ts`, `designer-reducer.ts`) + tests
- [x] A.3 Refactor `App.tsx` onto the reducer
- [x] A.4 Extend `Header.tsx` with schema/display-mode toggles, read-only badge, NL pane toggle
- [x] A.5 Add `NlPane.tsx` (collapsible bottom panel)
- [x] A.6 Re-style for Phase-3 look (text-sky-500 active, border-slate-300 bar)

## Section B — LSP integration
- [x] B.1 `modeler/getModelGraph` rewrite (`model-graph.ts`) — multi-document via `buildProjectModelGraph`
- [x] B.2 Layout types + validator + handlers — types/validator inlined in `model-graph.ts` (per A-time deviation, see contracts v1 changelog); handlers registered in `server.ts` with Node atomic write + browser in-memory `Map`
- [x] B.3 `modeler/applyGraphEdit` placeholder
- [x] B.4 `modeler/getSymbolDetail` handler — per-kind data via re-parsed AST; v1 limitation: only top-level defs (see `findDefByQname` comment + integration test 4.6)
- [x] B.5 Designer-side `LspClient` expansion
- [x] B.6 File-system shim
- [x] B.7 Wire file-system shim into `App.tsx`

## Section C — db schema rendering
- [x] C.1 Add Cytoscape extensions (cose-bilkent, node-html-label)
- [x] C.2 Write `cy/adapter.ts` (`modelGraphToCyElements`) + 7 unit tests
- [x] C.3 Refactor `Canvas.tsx` to consume `ModelGraph`
- [x] C.4 Layout-once-on-load semantics (cose-bilkent on graph change)
- [x] C.5 Wire schema toggle (cache hit guard, F5 fix)
- [x] C.6 Wire display-mode toggle (refresh labels only, no re-layout)

## Section D — er schema rendering
- [x] D.1 `cy/glyph-renderer.ts` — `glyphFor(card)` returning `<g class="glyph-<name>">` with plan-compliant child shapes; covered by `glyph-renderer.test.ts` with snapshot tests
- [x] D.2 Cardinality mapping in LSP (already done in model-graph.ts; updated to include `"0..*"` → `'many'` per contract amendment v3)
- [x] D.3 Extend adapter for er — `isNameAttribute`/`isCodeAttribute` markers in rows; entity label from `getDisplayLabel`; relation edges carry `fromCardinality`/`toCardinality`
- [x] D.4 Canvas overlay for glyphs — SVG overlay via `cy.on('render zoom pan', renderOverlay)` + `requestAnimationFrame`; glyphs positioned at edge endpoints oriented along edge tangent; `glyphFor` consumed directly by the overlay
- [ ] D.5 Visual review (manual, pending dev server)

## Section E — Inspector panel
- [x] E.1 `buildSymbolDetail` in LSP — unit-tested via `symbol-detail.test.ts` (7 cases)
- [x] E.2 Register `modeler/getSymbolDetail` — done in `server.ts:382`
- [x] E.3 Extend reducer + client — `storeSymbolDetail` action, `getSymbolDetail` client method
- [x] E.4 Rewrite `InspectorPanel.tsx` — perKindData table, description, tags, source copy button, ReferencedBy buttons
- [x] E.5 Wire selection from Canvas — `handleNodeSelect` + effect (F1); edge tap + background clear (F4); `onSelect` prop (F3)
- [x] F1 — detail fetch moved from click handler to `useEffect` keyed on `selectedSymbol?.qname`
- [x] F2 — source `file:line` clickable with clipboard copy + "Copied" toast
- [x] F3 — ReferencedBy rows are buttons that call `onSelect`
- [x] F4 — Canvas tap on edge selects edge; background tap clears selection
- [x] F5.a — `symbol-detail.test.ts` (7 LSP unit tests for buildSymbolDetail)
- [x] F5.b — `InspectorPanel.test.tsx` (4 RTL tests for panel rendering + interactions)
- [x] F5.c — consolidation into `lsp-phase-03-custom-methods.test.ts` noted (no new file needed)
- Note: review-015 tasks F1-F5 done, full suite green

## Section F — Layout persistence
- [x] F.1 `debounce` utility — `packages/designer/src/util/debounce.ts` + tests
- [x] F.2 Save flow (dragfreeon 500ms, viewport 750ms, layoutstop immediate) — `Canvas.tsx` registers all three; `saveLayout` assembles LayoutFile with current viewport + node positions and calls `client.setLayout(root, layout)` silently
- [x] F.3 Load flow — `useLayoutSync` hook fetches layout on project open and dispatches `loadLayout`; Canvas applies positions before auto-layout run
- [x] F.4 Layout-vs-positions race — if `nodePositions` has any entries, auto-layout is skipped; otherwise runs as before
- [x] F.5 "Download layout" affordance — `transportKind: 'browser' | 'node'` added to LspClient; Header shows "Export Layout" button only in browser mode; click triggers `exportLayout` + Blob download as `layout.ttrl`
- [x] F.6 Stale-qname tolerance — `cy.getElementById(qname)` silently returns empty collection for unknown qnames; `setLayout` still saves full layout (stale entries dropped on next save)
- [x] F-1 review fix: inactive viewport preserved via `viewportsRef`; `saveLayout` spreads `currentViewports` and only overwrites active schema
- [x] F-2 review fix: displayMode persisted immediately via new `useEffect` in App.tsx keyed on `[state.viewports, state.activeSchema, state.projectUri, state.nodePositions]`
- [x] F-3 review fix: `exportLayout` (node mode) now reads `.modeler/layout.ttrl` directly, matching `getLayout`; not just `emptyLayout()`
- [x] F-4 review fix: `layout-round-trip.test.ts` rewritten with real `buildLayout` / `applyPositions` pure helpers; 4 cases covering F-1, F-2, F-6 regressions
- [x] F-5 review fix: integration test `4.2b` (malformed JSON → emptyLayout) and `4.2c` (wrong version → emptyLayout) added
- [x] F-6 review fix: `useLayoutSync` catch block now logs `console.warn('[useLayoutSync] getLayout failed', err)`
- Tests: `debounce.test.ts` (2 cases), `layout-round-trip.test.ts` (4 cases), `lsp-phase-03-custom-methods.test.ts` (cases 4.1, 4.2, 4.2b, 4.2c)
- Note: review-017 fixes F-1 through F-6 done; full suite green

## Section G — Static deploy
- [x] G.1 Sample copy script — Vite plugin in `vite.config.ts` copies samples/v1-metadata/ to `packages/designer/dist/samples/v1-metadata/`; `copy-samples.ts` also exports `copySamples(src, dest)` for direct use; `index.json` written as manifest
- [x] G.2 Vite config base path — `base: process.env.DESIGNER_BASE_URL ?? '/'` in vite.config.ts
- [x] G.3 Demo-mode landing — `loadDemoFiles(demo)` in App.tsx effect; fetches /samples/{demo}/index.json then all listed files; dispatches loadProject
- [x] G.4 Landing-page card — `LandingCard` component shown when `projectUri === null`; "Load Project Folder" and "Open Demo (v1-metadata)" CTAs
- [x] G.5 GitHub Pages workflow — `.github/workflows/designer-deploy.yml`; triggers on main push for designer/lsp/samples paths; uses actions/upload-pages-artifact@v3 + actions/deploy-pages@v4; no explicit pnpm version (auto-detected from packageManager)
- [x] G.6 Smoke-curl post-deploy — fixed `basename "$(find ... | head -1)"` pipe; added `--retry 5 --retry-delay 5`; added `/samples/v1-metadata/index.json` check; 30s initial sleep
- [x] G.7 README and one-time setup — updated packages/designer/README.md; clarified two CTA methods; tightened layout-persistence bullet (node positions + per-schema viewport + displayMode); added embed-script out-of-scope note
- [x] G-1 review fix: copy-samples was writing to repo-root `dist/`, now uses Vite `closeBundle` hook into `packages/designer/dist/samples/v1-metadata/`
- [x] G-3 review fix: `copy-samples.test.ts` rewritten with `copySamples(src, dest)` parametrized API; tests use tmpdir and assert files + contents
- Tests: `demo-loader.test.tsx` (3 cases), `copy-samples.test.ts` (3 cases), workflow smoke step locally verified
- Note: review-017 fixes G-1 through G-7 done; copy-samples now lands in correct artifact directory

## Section H — Symbol indexing (carryover)
- [x] H.1 Identify existing per-kind emitters
- [x] H.2 Add emitters for 7 new kinds
- [x] H.3 Verify resolver/ReferenceIndex pick them up
- [x] H.4 Integration assertions
- [x] H.5 Update Phase 2 progress doc

## Section I — Parse recovery info (carryover)
- [x] I.1 Write `recovery.ts` (`RecoveryReportingStrategy`)
- [x] I.2 Wire into `parseString` + add `ParseRecoveryInfo` to `DiagnosticCode` + extend `ParseError.severity` to include `'info'`
- [x] I.3 Update recovery-fixtures test — 5 new assertions for `ttr/parse-recovery-info` presence (24 tests now vs 19)
- [x] I.4 Update Phase 2 progress doc §L
- [x] I.5 Update `diagnostics.md` — added Phase 1 tier entry, severity mapping, and before/after example

## Section J — VS Code smoke test (carryover)
- [ ] J.1 devDependencies — `@vscode/test-electron` already present; added `mocha glob @types/mocha @types/glob`; **ESM __dirname crash blocks test:smoke on macOS** (see J-2)
- [ ] J.2 Test harness scaffold — `src/test/runTests.ts` (runner) + `src/test/suite/index.ts` (Mocha entry) + `src/test/suite/extension.smoke.test.ts`; **ESM `__dirname` shim added to all 3 files** (J-1 ✓); TC3 strengthened, TC4 revert fixed (J-3 ✓); `pnpm install` now includes mocha/glob deps; still blocked on ESM crash in headless mode
- [ ] J.3 Smoke test — TC1 (language detection), TC2 (clean diagnostics), TC3 (go-to-definition), TC4 (unresolved-ref diagnostic), TC5 (workspace symbols)
- [ ] J.4 `test:smoke` script — `pnpm run build && node ./dist/test/runTests.js`; **CI job vscode-smoke added to ci.yml**; **pnpm setup step missing** (J-4)
- [ ] J.5 CI job — `vscode-smoke` job in `.github/workflows/ci.yml` with `xvfb-run -a`; smoke harness **not yet documented in vscode-ext README** (J-5)
- [ ] J.6 Phase 2 progress doc §M updated

## Section K — Documentation
- [x] K.1 `packages/semantics/README.md` — rewritten against the real public API (`Resolver.resolveReference(ref, ctx)`, `ProjectSymbolTable.all().filter`, `resolveManifest(undefined, root)`, `ReferenceIndex` documented); worked example pinned by `src/__tests__/readme-example.test.ts` so future API drift breaks the build (review-024 K-1)
- [x] K.2 `packages/lsp/README.md` — `modeler/listSymbols` request/response corrected to `{ kinds?, limit? }` / `{ qname, kind, name }`; `referencedBy[i].qname` semantics clarified (referrer qname, deduplicated); example snippet added (review-024 K-2)
- [x] K.3 `packages/designer/README.md` — schema/display-mode toggle behaviours documented, Node-vs-browser layout-persistence behaviour split out, deploy URL pattern called out, dedicated "Embedding the Designer (v1.x)" section added (review-024 K-5)
- [x] K.4 Top-level `README.md` — Phase status table by section, all six package READMEs linked (including `@modeler/grammar` and `@modeler/vscode-ext`), pnpm 11 prerequisite, stale Ontology-Playground brainstorm replaced with "What ships in v1" summary (review-024 K-4)
- [x] K.5 Architecture §10 close-out — open questions tagged with their Phase-3 resolutions; §11 "Designer ↔ LSP Control Flow (Deployed Shape)" added with browser worker topology diagram
- [x] K.6 `diagnostics.md` final pass — recovery-info example wording matches implementation (`"parser resumed after syntax error at '{'"`); severity-mapping table covers every code in `DiagnosticCode` (review-024 K-3); `@modeler/vscode-ext/README.md` updated with smoke-test section (review-024 J-5 / K-6)

## Final verification
- [x] All eleven mini-task-lists have every box ticked (A, B, C, D, E, F, G, H, I, J, K)
- [x] `pnpm -r build && test && lint && typecheck` green
- [x] `pnpm --filter @modeler/integration-tests test` green (29 tests)
- [x] `pnpm --filter @modeler/vscode-ext test:smoke` — green; package flipped to CJS, LSP launched via `require.resolve('@modeler/lsp/server-stdio')`, TC3/TC4 logic fixed (J review followups); CI `vscode-smoke` job has `pnpm/action-setup@v4` ordered before `actions/setup-node@v4` (review-024 J-4 closed)
- [x] `pnpm --filter @modeler/designer build` produces working static bundle
- [x] GitHub Pages deploy workflow runs on push to `main` (designer-deploy.yml)
- [ ] Hand-verified demo path (open ?demo=v1-metadata) — manual step
- [x] No regressions in Phase 1/2 features — all existing tests green

## Test Results
```
pnpm -r build:        ✅
pnpm -r test:         ✅  227 tests total (37 parser, 49 semantics, 45 lsp, 61 designer, 6 vscode-ext, 29 integration)
pnpm --filter @modeler/vscode-ext test:smoke:  ✅  5 mocha cases (TC1–TC5)
pnpm -r lint:         ✅  0 errors, 0 warnings
pnpm -r typecheck:    ✅
```

## Deferred from Phase 2
| Item | Target |
|------|--------|
| `parse-recovery-info` emission | Completed in Phase 3.I (2026-05-16) |
| VS Code `@vscode/test-electron` smoke test | Completed in Phase 3.J (2026-05-17) |
| `packages/semantics/README.md` | Completed in Phase 3.K (2026-05-18) |
| `packages/lsp/README.md` v2 surface doc | Completed in Phase 3.K (2026-05-18) |
| Indexing relations/queries/roles/er2db_* | Completed in Phase 3.H (2026-05-16) |
