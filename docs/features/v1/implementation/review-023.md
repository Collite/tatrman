# Review 023 — Re-review of review-022 fixes (Section I)

**Scope:** Verify the developer's claim that all of `tasks-review-022.md` is done.

## TL;DR

All eight tasks from review-022 landed and check out. The LSP severity bug is fixed via a shared helper, an LSP integration test pins the propagation, and the missing test files (`recovery-strategy.test.ts`, `samples-broken-recovery.test.ts`) now exist and exercise what the plan called for. Plus the small wording / offset cleanups.

**Section I is done.** No further fixes required.

---

## Confirmed fixed

| Task | Verification |
|---|---|
| I-1 LSP severity mapping | `server.ts:217-221` extracts `severityToLsp(s)` covering `error | warning | info`; both `publishDiagnostics` (line 190) and `toLspDiagnostic` (line 211) call it. No second drift surface. |
| I-1b Integration test for info propagation | `lsp-phase-03-custom-methods.test.ts` case 4.7: opens a recoverable doc, listens for `publishDiagnostics`, filters by `code === 'ttr/parse-recovery-info' && severity === 3`, asserts ≥1. Verified passing. |
| I-2 `recovery-strategy.test.ts` | Two cases: `recover()` and `recoverInline()` each asserted to append one `recoveryEvent` with the current token's line/column/text. Prototype-mock pattern keeps the test isolated from antlr4ng's internal state. |
| I-3 Plan's named fixtures | `recovery-fixtures.ts:55-70` adds `def-entity-no-name` and `truncated-inline-column`. Each is exercised by 3 assertions in `parser.test.ts` (parse-error + recovered-defs + recovery-info). |
| I-4 `samples-broken-recovery.test.ts` | Walks `samples/broken/*.ttr` and asserts ≥1 parse-error and ≥1 parse-recovery-info per file. 5 generated cases (one per .ttr file), all green. |
| I-5 Phase 2 deferred-table row | `progress-phase-02.md:125` flipped to `Completed in Phase 3.I (2026-05-16)`. |
| I-6 Rephrased recovery messages | `recovery.ts:16,22` now emit `parser resumed after syntax error at '<tok>'` and `parser skipped token to continue at '<tok>'` instead of antlr4ng-internal "recovered" / "inline recovery" jargon. |
| I-7 Real offsets in `RecoveryEvent` | `recovery.ts:3-9` extends the interface with `offsetStart` / `offsetEnd`; `recoveryEvent(tok, …)` populates from `tok.start` / `tok.stop + 1`; `walker.ts:175-177` writes them into the diagnostic source. `endColumn` also widens to the token width instead of the lying `column + 1`. |
| I-8 `_e` → `e` | `recovery.ts:14` renamed. |

## End-to-end verification

```bash
pnpm -r build                                # green
pnpm -r test                                 # 226 tests passed
pnpm -r lint                                 # 0 errors
pnpm -r typecheck                            # 0 errors

cd packages/parser && pnpm vitest run --reporter=verbose | grep -cE "recovery-strategy|samples-broken"
# 7  (2 strategy cases + 5 samples-broken cases)

cd tests/integration && pnpm vitest run --reporter=verbose | grep "4.7 parse-recovery-info"
# ✓  201ms — info-severity propagation green
```

Test totals at `progress-phase-03.md:136` read `226 tests total (37 parser, 48 semantics, 45 lsp, 61 designer, 6 vscode-ext, 29 integration)` — matches the actual runner output. Parser went 24 → 37 (+13: 2 strategy + 5 samples-broken + 2 fixtures × 3 assertions), integration 28 → 29 (+1 propagation case). Math checks out.

---

## Minor leftover (not blocking)

### L-1. Progress doc's Section I block doesn't annotate the review-022 fixes

`progress-phase-03.md:101-105` lists the original I.1–I.5 ticks with no `I-1 review fix:` / `I-2 review fix:` lines, unlike how the F/G blocks were annotated. Section H had the same gap. Optional archaeology aid; doesn't block.

If you want to add them, mirror the F/G shape:
```
- [x] I-1 review fix: extracted `severityToLsp(s)` helper covering error/warning/info; used by both ParseError and ValidationDiagnostic mappers
- [x] I-1b review fix: LSP integration test 4.7 asserts `parse-recovery-info` arrives with `DiagnosticSeverity.Information`
- [x] I-2 review fix: `recovery-strategy.test.ts` added — unit-tests `recover` and `recoverInline` with prototype-mocked super
- [x] I-3 review fix: 2 fixtures added (`def-entity-no-name`, `truncated-inline-column`)
- [x] I-4 review fix: `samples-broken-recovery.test.ts` walks `samples/broken/*.ttr` (5 generated cases)
- [x] I-5 review fix: Phase 2 deferred-table row flipped
- [x] I-6 review fix: user-facing recovery messages rephrased (no antlr4ng jargon)
- [x] I-7 review fix: real `offsetStart`/`offsetEnd` propagated through `RecoveryEvent`
- [x] I-8 review fix: dropped misleading `_e` underscore
```

### L-2. (carried over from earlier) `tsx` orphan devDep in `packages/designer/package.json`

Same as review-019 L-1, review-021 L-3. Two-second cleanup, independent of Section I.

---

## Verdict

Section I is **done**. Phase 3 can move on to Sections J and K. No task list this time.
