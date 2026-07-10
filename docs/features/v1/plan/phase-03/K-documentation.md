# Phase 3.K — Documentation

**Goal:** finish every documentation artefact Phase 3 owes — including two Phase-2 carryovers — and update the top-level README and architecture doc.

**Reads:** `docs/plan/progress-phase-02.md` "Deferred to later phases".
**Blocked by:** §B (lsp surface stabilized), §C (designer surface stabilized), §E (inspector surface stabilized) so the docs aren't immediately out of date.
**Blocks:** the Phase 3 final-verification task.

## Tests-first

Documentation has no unit tests. Use these review checks in place:

- [ ] Each README's public-API surface matches the contracts doc by hand-diff. Take a checklist of every exported type/method from `docs/design/phase-03-contracts.md` and tick that it's mentioned in one of the new READMEs.
- [ ] Worked examples in each README compile when copy-pasted into a TypeScript playground (or `tsx` run). Add a comment in each README's worked-example section: "Last verified to compile: <date>".
- [ ] The top-level README screenshot is a real screenshot of the deployed Designer, not a placeholder. Take it after §G is live.

## Library reference

None. This is writing.

## Implementation tasks

- [ ] **K.1 — `packages/semantics/README.md`** (Phase 2 carryover).
  - Cover the public API: `ProjectSymbolTable`, `Resolver`, `Validator`, `loadProject`, `loadProjectFromOpenDocuments`, `loadStockVocabularies`.
  - Worked example: parse a `.ttr` string with `@modeler/parser`, build a symbol table, resolve a reference, format a hover. ~30 lines.
  - Note the Node/browser split: `@modeler/semantics/node-only` exists; the browser worker imports the browser-safe surface only.
- [ ] **K.2 — `packages/lsp/README.md`** (Phase 2 carryover).
  - Document the v1 LSP surface: standard methods (`textDocument/definition`, `references`, `hover`, `workspace/symbol`, `semanticTokens/full`, `publishDiagnostics`).
  - Document the Phase-3 custom methods per [contracts §7](../../design/phase-03-contracts.md#7-lsp-custom-method-contracts). One subsection per method with request shape, response shape, and a 5-line example.
  - Note the two transports (`server-stdio.ts` for VS Code/IntelliJ, `server-browser.ts` for the Designer).
- [ ] **K.3 — `packages/designer/README.md`.**
  - What the Designer is + a screenshot.
  - How to run dev mode (`pnpm --filter @modeler/designer dev`).
  - How to load a project (FSA + upload), what the schema and display-mode toggles do, where layout is persisted (Node vs. browser).
  - Demo URL + the `?demo=v1-metadata` query parameter.
  - Deployment notes (`DESIGNER_BASE_URL` env var, GitHub Pages one-time activation).
  - "Embed via `<script>`" placeholder section pointing at v1.x.
- [ ] **K.4 — Top-level `README.md` update.**
  - Add a Designer section with the deployed URL and a thumbnail / screenshot.
  - Link the three package READMEs.
  - Add a short "Phase status" line: Phase 3 shipping, with a link to `docs/plan/tasks-phase-03-designer.md`.
- [ ] **K.5 — `docs/design/architecture.md` §10 close-out.**
  - Re-read §10's Open questions list. Tick the ones Phase 3 has answered. Leave open ones with a Phase-4 or v1.x reference.
  - Add (or update) a short subsection at the bottom describing the Designer ↔ LSP control flow in the deployed shape — useful as a single-screen overview for v1.1+ contributors landing on the doc.
- [ ] **K.6 — `docs/design/diagnostics.md` final pass.**
  - Verify every `DiagnosticCode` listed in `packages/parser/src/diagnostics.ts` (or wherever the enum lives) is documented with: trigger, severity, example, fix.
  - `ttr/parse-recovery-info` is documented per §I.5's work.

## Verify by running

No `pnpm` command for docs. Manual review checklist:

- [ ] Every `pnpm -r build && test && lint && typecheck` command in the new READMEs is current and runs green.
- [ ] Every relative link in the new READMEs resolves (use a Markdown link-checker or hand-check).
- [ ] The top-level README screenshot loads and shows the deployed Designer.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] Five package/repo READMEs land (`semantics`, `lsp`, `designer`, top-level update, plus the `vscode-ext` README touch from §J).
- [ ] `architecture.md` §10 reflects Phase 3 reality; `diagnostics.md` is current.
- [ ] Phase 2 progress doc's two doc-carryover lines (`packages/semantics/README.md`, `packages/lsp/README.md`) are ticked.
