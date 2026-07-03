# Review 007 — Phase 3, Section A final sign-off

**Date:** 2026-05-15
**Branch:** `phase-03` (HEAD `94ceefe` — *"Section A final cleanup: tasks-review-006.md all complete"*)
**Scope claimed:** every task in `tasks-review-006.md` (1.1–6.5).
**Scope verified:** `git diff f0c51ce..94ceefe` (5 files, +138 / −15) plus the full pipeline.

## Verdict

**Section A is done.** Approving.

Every must-fix and should-fix item from review-006 is closed. Build, test, lint, typecheck all green; integration tests still 15/15. The Section B implementer can start with no carried debt.

## What landed

| Review-006 task | Status |
|---|---|
| **1** — `Header.test.tsx` with 5 tests-first cases | ✅ 6 tests on disk (the read-only-badge case was correctly split into two assertions, one per `projectUri` value); designer test count is now **12** (6 reducer + 6 Header) |
| **2** — A.6 accent-palette comment at top of `Header.tsx` | ✅ Lines 1–2 verbatim |
| **3** — `nlPaneOpen` choice documented in `App.tsx` | ✅ Lines 14–16; the comment also names the migration trigger ("if the pane gains state that must persist…") |
| **4** — Dead-wire `useEffect` removed | ✅ Replaced with a §B-pointer comment at `App.tsx:39-40`; no spurious LSP round-trips on schema toggle |
| **5** — Inline string-literal types replaced | ✅ `Header.tsx:5`, `Canvas.tsx:3`, `lsp-client.ts:13` all import `RenderableSchemaCode` / `DisplayMode` from `@modeler/lsp`; the `getModelGraph` signature uses the imported type |
| **6** — Final acceptance run | ✅ Full pipeline green |

## Notes on the test work (F1)

Two small things worth noting for future test PRs, not blocking:

- The Header tests use `cleanup()` in `afterEach` rather than relying on the default. With Vitest 4 + RTL 16, the default cleanup is automatic when `globals: true` is set (which `vitest.config.ts` does). Explicit `cleanup` is harmless but redundant.
- `toHaveBeenCalledExactlyOnceWith` (used in 1.2b) is a fluent matcher available in newer Vitest releases — if a downgrade ever happens it'll need the longer `toHaveBeenCalledTimes(1)` + `toHaveBeenCalledWith(...)` form. Worth knowing if CI ever breaks.

Neither needs a follow-up PR.

## Verification commands run

```
pnpm -r build        → all green
pnpm -r test         → designer 12, lsp 25 (4 + 8 + 4 + 9), vscode-ext 6, integration 15 — all pass
pnpm -r lint         → all green
pnpm -r typecheck    → all green
```

## Architectural assessment

The Section A architecture is now exactly what the plan and contracts asked for:

- One source of truth for shared types (`packages/lsp/src/model-graph.ts`), one barrel (`index.ts`), no duplicate definitions across packages.
- Clean reducer pattern: `designer-state.ts` owns shapes + initial state; `designer-reducer.ts` owns the action union and the pure reducer; both have unit tests.
- Every typed wire that touches `RenderableSchemaCode` / `DisplayMode` / `LayoutFile` / `SymbolDetail` reaches them through `@modeler/lsp` — no inline literals left in the Designer.
- UI tests in place for Header behaviour; reducer tests in place for state transitions; LSP-side helpers covered by their own contract-prescribed unit tests.
- The Designer no longer has dead wires that overpromise Section B — the only LSP traffic on a file load is `didOpen` + diagnostics, exactly as Section A was supposed to ship.

## Recommendation

Move on to **Section B (`docs/plan/phase-03/B-lsp-integration.md`)**. Start with B's "Tests-first" section. The first server-side change should fail B's tests (the `modeler/getModelGraph` handler at `packages/lsp/src/server.ts:314` still returns the legacy `{ qname, kind, label }` shape; B.1's tests will turn red the moment they're written, which is the intended rhythm).

No follow-up task list for this review — there is nothing left to do for Section A.
