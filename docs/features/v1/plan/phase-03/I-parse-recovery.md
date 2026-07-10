# Phase 3.I — `parse-recovery-info` emission

**Goal:** ANTLR's error recovery now emits a `ttr/parse-recovery-info` info-level diagnostic at every recovery point. Subclass `DefaultErrorStrategy`, capture recovery events, surface them through `parseString` as `ParseError`s with the right code and severity.

**Reads:** [contracts §10](../../design/phase-03-contracts.md#10-diagnostic-codes-phase-3-additions), `docs/design/diagnostics.md` (Phase-1 entry), `packages/parser/src/index.ts`'s `parseString`.
**Blocked by:** Pre-flight only — parallel-safe with §A.
**Blocks:** nothing (J's smoke test happens to assert other codes; this one is purely additive).

## Tests-first

- [ ] `packages/parser/src/__tests__/recovery-fixtures.test.ts` — extend the existing recovery-fixtures test.
  - For every fixture under `samples/broken/` that the parser previously produced ≥1 `ttr/parse-error` for, also assert ≥1 `ttr/parse-recovery-info` is produced.
  - Specific case: `def entity {` with no name → the recovery strategy synthesizes a name; one parse-error + ≥1 recovery-info.
  - Specific case: `def table T { columns: [ def column ` (truncated) → the recovery strategy backs out of the inline column; ≥1 parse-error + ≥1 recovery-info.
  - Assertion shape: `expect(result.errors.filter(e => e.code === 'ttr/parse-recovery-info').length).toBeGreaterThanOrEqual(1)`.

- [ ] `packages/parser/src/__tests__/recovery-strategy.test.ts` — unit-level over the strategy class itself.
  - Construct a `RecoveryReportingStrategy`. Feed it a fake `Parser` / `RecognitionException` (mock antlr4ng's `Parser.getCurrentToken()` to return a token with known `line` / `column`). Call `recover`. Expect the strategy's `recoveryEvents` array gained one entry with that line/column.
  - Same for `recoverInline`.

## Library reference

Run Context7 before subclassing:

```
mcp__context7__resolve-library-id { libraryName: "antlr4ng", query: "DefaultErrorStrategy subclass, recover recoverInline override, set parser._errHandler" }
mcp__context7__query-docs        { libraryId: "<id>", query: "DefaultErrorStrategy recover recoverInline override signatures, getCurrentToken on Parser" }
```

**Library reference (training-time approximation; verify above):**

```ts
import { DefaultErrorStrategy, Parser, RecognitionException, Token } from 'antlr4ng';

export interface RecoveryEvent {
  line: number;
  column: number;
  description: string;
}

export class RecoveryReportingStrategy extends DefaultErrorStrategy {
  public readonly recoveryEvents: RecoveryEvent[] = [];

  override recover(recognizer: Parser, e: RecognitionException): void {
    const tok = recognizer.getCurrentToken();
    this.recoveryEvents.push({
      line: tok.line,
      column: tok.column,
      description: `recovered at '${tok.text ?? ''}'`,
    });
    super.recover(recognizer, e);
  }

  override recoverInline(recognizer: Parser): Token {
    const tok = recognizer.getCurrentToken();
    this.recoveryEvents.push({
      line: tok.line,
      column: tok.column,
      description: `inline recovery at '${tok.text ?? ''}'`,
    });
    return super.recoverInline(recognizer);
  }
}
```

Note: antlr4ng's API may use `errorHandler` instead of `_errHandler`, or expose `setErrorHandler(...)`. Verify before writing the assignment in `parseString`.

## Implementation tasks

- [ ] **I.1 — Write `packages/parser/src/recovery.ts`** with `RecoveryReportingStrategy` per the snippet above. Make the strategy unit tests green.
- [ ] **I.2 — Wire into `parseString`.** In `packages/parser/src/index.ts`, after constructing the parser, install a fresh `RecoveryReportingStrategy` instance (`parser.setErrorHandler(strategy)` or whichever API antlr4ng exposes). After parsing, iterate `strategy.recoveryEvents` and push one `ParseError` per event with `code: 'ttr/parse-recovery-info'`, `severity: 'info'`, `source: 'modeler'`, the event's `line` / `column`, and the event's `description` as the message.
- [ ] **I.3 — Update recovery-fixtures test.** Add the assertion above for every recoverable fixture. If a fixture is *not* recoverable (parse hits EOF before any recovery), leave it asserting only `parse-error` — the test must not fail-loud on those.
- [ ] **I.4 — Update Phase 2 progress doc.** Tick `progress-phase-02.md` §L with `Completed in Phase 3.I` + date.
- [ ] **I.5 — Update `docs/design/diagnostics.md`.** Remove any "reserved / not yet emitted" annotation on the `ttr/parse-recovery-info` entry. Add a short before/after example showing one input that produces both `parse-error` and `parse-recovery-info`.

## Verify by running

```bash
pnpm --filter @modeler/parser test
pnpm --filter @modeler/integration-tests test    # confirms LSP propagates the info-severity correctly
```

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] Recovery-strategy unit tests green.
- [ ] Recovery-fixtures tests assert recovery-info presence on every recoverable case.
- [ ] Phase 2 §L line and `diagnostics.md` are updated.
