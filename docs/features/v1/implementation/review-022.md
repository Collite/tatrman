# Review 022 — Phase 3 Section I (`parse-recovery-info` emission)

**Scope:** Review the developer's work against `docs/plan/phase-03/I-parse-recovery.md`. The progress doc shows all I.1–I.5 ticked.

## TL;DR

Section I lands the core mechanic correctly: `RecoveryReportingStrategy` overrides `recover`/`recoverInline`, `parseString` installs it via `parser.errorHandler`, and `recoveryEvents` get converted to `ParseError` entries with `code: 'ttr/parse-recovery-info'` and `severity: 'info'`. The parser tests are green (24/24) and every recoverable fixture now asserts a `parse-recovery-info` diagnostic exists.

**But there's a real product bug that nullifies the user-facing value:** the LSP server's `publishDiagnostics` only maps `severity: 'warning' | 'error'`. The new `severity: 'info'` falls through to `DiagnosticSeverity.Error`. Every recovery-info diagnostic shipped to VS Code (or the Designer) shows up red in the Problems panel — exactly the *opposite* of the "Information" intent stated in `diagnostics.md`.

Plus three plan deviations: the strategy-level unit test file the plan listed is missing, the plan's specific "no-name def entity" / "truncated inline column" fixtures weren't added, and the Phase 2 deferred-table row wasn't flipped to "completed" (only §L was).

None of the new tests would catch the LSP severity bug because the plan's own "Verify by running" mentioned an integration test that confirms LSP propagation — but no such test was written.

---

## I-1. LSP `publishDiagnostics` downgrades info → Error (CRITICAL, user-visible)

`packages/lsp/src/server.ts:187-193`:

```ts
const diagnostics: Diagnostic[] = result.errors.map((err: ParseError) => ({
  range: sourceLocationToRange(err.source),
  message: err.message,
  severity: err.severity === 'warning' ? DiagnosticSeverity.Warning : DiagnosticSeverity.Error,
  code: err.code,
  source: 'modeler',
}));
```

The ternary handles `'warning'` explicitly and treats everything else (including the new `'info'`) as Error. `RecoveryReportingStrategy`-derived `ParseError`s carry `severity: 'info'` (`walker.ts:169`), so VS Code and the Designer's diagnostics handler receive `DiagnosticSeverity.Error` for every recovery point. Result: a single recoverable syntax error generates one red squiggle for the syntax-error itself **plus** one red squiggle per recovery event. The user sees several "errors" where the file actually has one — and `parse-recovery-info` is labelled Information in `diagnostics.md:16,132` precisely so it would render as a blue circle, not red.

The same file already has the correct mapping at lines 207-213 (`toLspDiagnostic`), used for `ValidationDiagnostic`. The bug is that the ParseError-mapper was never updated when `ParseError.severity` gained `'info'`.

Fix (one line, plus reuse the helper):

```ts
const diagnostics: Diagnostic[] = result.errors.map((err: ParseError) => ({
  range: sourceLocationToRange(err.source),
  message: err.message,
  severity:
    err.severity === 'warning' ? DiagnosticSeverity.Warning
    : err.severity === 'info' ? DiagnosticSeverity.Information
    : DiagnosticSeverity.Error,
  code: err.code,
  source: 'modeler',
}));
```

Or extract the severity ternary into a shared helper so the two call sites can't drift apart again.

The plan's *Verify by running* explicitly said `pnpm --filter @modeler/integration-tests test    # confirms LSP propagates the info-severity correctly` — but no integration test was added to verify this propagation. That's how the bug shipped past the green bar.

## I-2. Missing `recovery-strategy.test.ts` (plan tests-first)

Plan §I tests-first lists two test files:

1. `recovery-fixtures.test.ts` — ✓ landed (folded into the existing `parser.test.ts > parser error recovery` block).
2. `recovery-strategy.test.ts` — **missing**.

The plan called for unit-level coverage of `RecoveryReportingStrategy` itself:

> Construct a `RecoveryReportingStrategy`. Feed it a fake `Parser` / `RecognitionException` (mock antlr4ng's `Parser.getCurrentToken()` to return a token with known `line`/`column`). Call `recover`. Expect the strategy's `recoveryEvents` array gained one entry with that line/column. Same for `recoverInline`.

The current tests only exercise the strategy *through* `parseString`. That conflates "strategy captures events" with "antlr4ng decides to invoke recovery on this fixture." If ANTLR's recovery heuristic changes between versions and stops invoking `recover` on a given input, the existing tests would all flip red simultaneously without indicating whether the regression is in the strategy or in antlr4ng's behaviour.

Add the unit test as the plan describes — stub `Parser` with a `getCurrentToken()` returning a `{ line, column, text }` shape and assert one `recoveryEvent` per call. Tiny file, ~20 lines.

## I-3. Plan's specific fixtures not added

Plan §I tests-first listed two specific cases:

* `def entity {` with no name → recovery strategy synthesizes a name; one parse-error + ≥1 recovery-info.
* `def table T { columns: [ def column ` (truncated) → recovery strategy backs out of the inline column; ≥1 parse-error + ≥1 recovery-info.

Neither is in `packages/parser/src/__tests__/recovery-fixtures.ts`. The fixtures file is unchanged from Phase 1 (no diff vs HEAD). The five existing fixtures all happen to produce recovery events, so the new assertion `expect(hasRecoveryInfo).toBe(true)` passes for all of them — but the two specific behaviours the plan called out aren't directly verified. If a future grammar change made ANTLR resync without invoking `recover` on a `def entity {` input, no test would catch it.

Add the two fixtures. If they require slightly different `expectedRecoveredDefs` values or new assertion fields, extend the `RecoveryFixture` interface; don't crowbar them into the existing shape.

## I-4. `samples/broken/` not exercised

Plan §I: "For every fixture under `samples/broken/` that the parser previously produced ≥1 `ttr/parse-error` for, also assert ≥1 `ttr/parse-recovery-info` is produced." The directory exists with five `.ttr` files (`db-missing-comma.ttr`, `db-unterminated-bracket.ttr`, `er-malformed-ref.ttr`, `er-missing-brace.ttr`, `er-unknown-property.ttr`) plus a `README.md`. Nothing in the test suite loads them; the only fixtures used are the inline strings in `recovery-fixtures.ts`.

That's a deviation, not necessarily a defect — the inline fixtures cover the same shape of input. But the plan explicitly named `samples/broken/` as the source of truth, and those files are richer (multi-statement, dotted-namespace contexts). Either:

* Add a small `samples-broken-recovery.test.ts` that walks `samples/broken/*.ttr`, parses each, and asserts ≥1 `parse-error` and ≥1 `parse-recovery-info` (skipping any file the parser actually accepts).
* Or update plan I.3 to drop the `samples/broken/` reference and say the inline fixtures are canonical.

The first is preferable — the `samples/broken/` files are the real-world recovery surface VS Code users hit; the inline fixtures are convenience tests.

## I-5. Phase 2 "Deferred" table row not flipped

`docs/plan/progress-phase-02.md:91` (§L) reads "Completed in Phase 3.I (2026-05-16)" — correct.
`docs/plan/progress-phase-02.md:125` (Deferred-to-later-phases table) still reads:

```
| `parse-recovery-info` emission (DefaultErrorStrategy subclass) | Phase 3 if needed |
```

Compare to line 129, which was flipped during Section H to `Completed in Phase 3.H (2026-05-16)`. Same update is missing for line 125. Two-second edit, but it's the same "intent vs truth" drift we keep flagging.

## I-6. Minor: parser-internal jargon in user-facing messages (LOW)

`recovery.ts:14, 20`:

```ts
this.recoveryEvents.push(recoveryEvent(tok, 'recovered'));
// →
this.recoveryEvents.push(recoveryEvent(tok, 'inline recovery'));
```

Becomes a `parse-recovery-info` diagnostic with message `recovered at '{'` or `inline recovery at '{'`. A non-parser-author reading "inline recovery at '{'" in VS Code's Problems panel will not understand. Suggest something like `parser resumed at '{'` or `partial AST: parser resynchronized at '{'`. The exact phrasing doesn't matter; "inline recovery" is jargon.

If you keep "inline recovery" because it parallels antlr4ng's `recoverInline`, add a comment explaining why. Otherwise rephrase.

## I-7. Minor: zero offsets and one-character range (LOW)

`walker.ts:170-178`:

```ts
source: {
  file: fileLabel,
  line: event.line,
  column: event.column,
  endLine: event.line,
  endColumn: event.column + 1,
  offsetStart: 0,
  offsetEnd: 0,
},
```

`offsetStart: 0, offsetEnd: 0` is a lie — those fields are 0-indexed file offsets, not "unknown." Same for `endColumn: column + 1` (assumes a one-character span, but the recovery event isn't tied to any specific token width). For an Information-severity diagnostic the LSP range probably doesn't matter much, but two issues:

* `sourceLocationToRange` in the LSP server consumes these offsets for hover/quick-fix integration. If anyone ever wires those for `parse-recovery-info`, the zero offsets will silently point at the start of the file.
* The strategy captures `tok` in `recoveryEvent(tok, …)` and discards `tok.start`, `tok.stop` (its real offsets). Propagate them: `RecoveryEvent` should carry `offsetStart`/`offsetEnd` too, populated from `tok.start` / `tok.stop + 1`. Then `walker.ts` uses them directly. Optional cleanup; right now nothing breaks because nothing reads those zeros, but the fields lie.

## I-8. Minor: `_e` parameter naming (LOW)

`recovery.ts:12`:

```ts
override recover(recognizer: Parser, _e: RecognitionException): void {
  // ...
  super.recover(recognizer, _e);
}
```

Leading underscore is the project convention for "intentionally unused"; here it *is* used (passed to `super`). Either drop the underscore or rename to `e` plain. Lint-style nit.

---

## End-to-end verification

```bash
pnpm -r build                                # green
pnpm -r test                                 # 212 tests pass (parser 24, was 19)
pnpm -r lint                                 # 0 errors
pnpm -r typecheck                            # 0 errors

cd packages/parser && pnpm vitest run --reporter=verbose | grep parse-recovery-info | wc -l
# 5 — one per recoverable fixture (matches progress doc claim of "+5 assertions")
```

The bug at I-1 is invisible to the parser tests because they consume `ParseError.severity: 'info'` directly. It only shows up when the LSP serializes diagnostics over the wire — which no test does for the info-severity case.

Quick repro of the bug (no fix yet):

```bash
# In VS Code's Extension Development Host, open samples/broken/er-missing-brace.ttr.
# Observe: the Problems panel shows the recovery-info messages with red error icons,
# not blue Information icons.
```

Or in a unit test in the LSP package:

```ts
it('parse-recovery-info diagnostics propagate as Information severity', () => {
  const errors: ParseError[] = [{
    code: 'ttr/parse-recovery-info',
    message: 'recovered at "{"',
    severity: 'info',
    source: { file: 'x', line: 1, column: 0, endLine: 1, endColumn: 1, offsetStart: 0, offsetEnd: 0 },
  }];
  // call the same mapping logic publishDiagnostics uses, assert DiagnosticSeverity.Information
});
```

That test would have failed against the current code.

---

## Verdict

Parser-side mechanics are correct. **LSP-side propagation is broken** — Section I cannot be considered done until I-1 lands and an integration test pins the propagation. I-2, I-3, I-4, I-5 are plan-deviation cleanups; do them while you're in the file.

Task list: `tasks-review-022.md`.
