# Tasks — Review 022 (Section I)

Section I is mostly built but has one CRITICAL bug (LSP downgrades `info` → `Error`) and four plan-deviation cleanups. The bug shipped because the green-bar parser tests don't exercise LSP serialization.

Work top-down. ⚠ tasks block Section I close-out; the others are cleanup.

---

## I-1 ⚠ — Map `severity: 'info'` to `DiagnosticSeverity.Information` in `publishDiagnostics`

`packages/lsp/src/server.ts:187-193` collapses everything except `'warning'` into `Error`. Recovery-info diagnostics show as red errors instead of blue info — directly opposite of `diagnostics.md`'s "Information" classification.

- [ ] Open `packages/lsp/src/server.ts`.
- [ ] Replace the inline ternary in `publishDiagnostics` (≈line 190):
  ```ts
  severity: err.severity === 'warning' ? DiagnosticSeverity.Warning : DiagnosticSeverity.Error,
  ```
  with the same three-way mapping `toLspDiagnostic` already uses:
  ```ts
  severity:
    err.severity === 'warning' ? DiagnosticSeverity.Warning :
    err.severity === 'info' ? DiagnosticSeverity.Information :
    DiagnosticSeverity.Error,
  ```
- [ ] **Recommended:** extract a `function severityToLsp(s: 'error' | 'warning' | 'info'): DiagnosticSeverity` near `toLspDiagnostic` and use it in both call sites. Otherwise the next `ParseError.severity` extension will hit the same bug again.

**Verify:** see I-1b.

## I-1b ⚠ — Add an LSP integration test that pins info-severity propagation

The plan's "Verify by running" mentioned this but no test was written. Without it the I-1 fix can regress silently.

- [ ] Add a case to `tests/integration/src/lsp-phase-03-custom-methods.test.ts` (or a new file):
  - Open a doc whose content triggers a recoverable parse error (e.g. `def entity {\n  description: "x"` — same as `recovery-fixtures.ts` first fixture).
  - Wait for the `textDocument/publishDiagnostics` notification.
  - Assert there's ≥1 diagnostic with `code === 'ttr/parse-recovery-info'` AND `severity === DiagnosticSeverity.Information` (the integer `3`).

  Sketch:
  ```ts
  it('parse-recovery-info diagnostics arrive with Information severity', async () => {
    const received: Diagnostic[] = [];
    client.onNotification('textDocument/publishDiagnostics', (params: PublishDiagnosticsParams) => {
      received.push(...params.diagnostics);
    });

    client.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///recovery-test.ttr',
        languageId: 'ttr', version: 1,
        text: 'def entity artikl {\n  description: "Test"\n',
      },
    });

    await sleep(200);
    const infoDiagnostics = received.filter(d =>
      d.code === 'ttr/parse-recovery-info' && d.severity === 3
    );
    expect(infoDiagnostics.length).toBeGreaterThanOrEqual(1);
  });
  ```
  `DiagnosticSeverity.Information === 3` per the LSP spec; assert the integer to avoid an enum import.

**Verify:**
```bash
pnpm --filter @modeler/integration-tests test
# expected: the new case green; would have failed against the pre-I-1 code
```

## I-2 ⚠ — Add `recovery-strategy.test.ts`

Plan §I tests-first listed this file. It doesn't exist; the strategy is only exercised through full parses.

- [ ] Create `packages/parser/src/__tests__/recovery-strategy.test.ts`:
  ```ts
  import { describe, it, expect } from 'vitest';
  import { RecoveryReportingStrategy } from '../recovery';

  function fakeRecognizer(line: number, column: number, text: string) {
    return {
      getCurrentToken: () => ({ line, column, text, start: 0, stop: text.length - 1 }),
      // antlr4ng's super.recover/recoverInline also touch state; stub the minimum
      // that DefaultErrorStrategy needs. If the super calls explode, mock further
      // via `vi.spyOn(DefaultErrorStrategy.prototype, 'recover').mockImplementation(() => {})`.
    } as unknown as import('antlr4ng').Parser;
  }

  describe('RecoveryReportingStrategy', () => {
    it('recover() appends one event with the current token line/column', () => {
      const s = new RecoveryReportingStrategy();
      // Mock super to no-op so the test focuses on event capture.
      const proto = Object.getPrototypeOf(Object.getPrototypeOf(s));
      const superRecover = proto.recover;
      proto.recover = () => {};
      try {
        s.recover(fakeRecognizer(5, 12, '{'), {} as never);
      } finally {
        proto.recover = superRecover;
      }
      expect(s.recoveryEvents).toHaveLength(1);
      expect(s.recoveryEvents[0].line).toBe(5);
      expect(s.recoveryEvents[0].column).toBe(12);
      expect(s.recoveryEvents[0].description).toContain("'{'");
    });

    it('recoverInline() appends one event tagged "inline recovery"', () => {
      const s = new RecoveryReportingStrategy();
      const proto = Object.getPrototypeOf(Object.getPrototypeOf(s));
      const superRI = proto.recoverInline;
      proto.recoverInline = () => ({ line: 0, column: 0 } as never);
      try {
        s.recoverInline(fakeRecognizer(8, 3, 'foo'));
      } finally {
        proto.recoverInline = superRI;
      }
      expect(s.recoveryEvents).toHaveLength(1);
      expect(s.recoveryEvents[0].description).toMatch(/inline/);
    });
  });
  ```
  The prototype-mock trick keeps the test independent of antlr4ng's internal state. If antlr4ng's `DefaultErrorStrategy.recover` blows up without a full parser, this is the cleanest way to isolate the strategy's own behaviour.

**Verify:**
```bash
pnpm --filter @modeler/parser test
# expected: 2 new cases green
```

## I-3 ⚠ — Add the plan's named fixtures

Plan named two specific recoverable inputs that aren't covered by the existing 5 fixtures.

- [ ] Open `packages/parser/src/__tests__/recovery-fixtures.ts`.
- [ ] Append:
  ```ts
  {
    name: 'def-entity-no-name',
    input: `def entity {
    description: "Test"
  }`,
    description: 'def entity with no name — recovery strategy synthesizes',
    expectedRecoveredDefs: 1,
    expectErrors: true,
  },
  {
    name: 'truncated-inline-column',
    input: `def table T { columns: [ def column `,
    description: 'truncated inline column — recovery strategy backs out',
    expectedRecoveredDefs: 1,
    expectErrors: true,
  },
  ```
- [ ] Run the fixtures test; if `expectedRecoveredDefs` doesn't match ANTLR's actual output, adjust the count rather than the fixture (the assertion already runs three checks per fixture, one of which is "produces ≥1 ttr/parse-recovery-info" — that's the case-of-interest).

**Verify:**
```bash
pnpm --filter @modeler/parser test
# expected: 6 new cases green (2 fixtures × 3 assertions each)
```

## I-4 (recommended) — Exercise `samples/broken/*.ttr` directly

The plan named that directory as the canonical source of recovery fixtures. The five files there are realer than the inline strings; using both is fine.

- [ ] Add `packages/parser/src/__tests__/samples-broken-recovery.test.ts`:
  ```ts
  import { describe, it, expect } from 'vitest';
  import { readFileSync, readdirSync } from 'node:fs';
  import { join } from 'node:path';
  import { parseString } from '../walker';
  import { DiagnosticCode } from '../diagnostics';

  const samplesBrokenDir = join(__dirname, '../../../../samples/broken');

  describe('samples/broken/*.ttr → parse-recovery-info', () => {
    const files = readdirSync(samplesBrokenDir)
      .filter((f) => f.endsWith('.ttr'));

    for (const file of files) {
      it(`${file} produces ≥1 parse-error and ≥1 parse-recovery-info`, () => {
        const content = readFileSync(join(samplesBrokenDir, file), 'utf-8');
        const result = parseString(content, file);
        const errors = result.errors.filter(e => e.code === DiagnosticCode.ParseError);
        const infos = result.errors.filter(e => e.code === DiagnosticCode.ParseRecoveryInfo);
        expect(errors.length, `${file}: expected ≥1 parse-error`).toBeGreaterThanOrEqual(1);
        expect(infos.length, `${file}: expected ≥1 parse-recovery-info`).toBeGreaterThanOrEqual(1);
      });
    }
  });
  ```
  If any specific file produces parse errors but no recovery events, document the exception in `samples/broken/README.md` and skip that file via name, rather than weakening the assertion.

**Verify:**
```bash
pnpm --filter @modeler/parser test
# expected: 5 new cases (one per .ttr file under samples/broken/)
```

## I-5 — Flip the Phase 2 "Deferred to later phases" row

`docs/plan/progress-phase-02.md:125` still reads `Phase 3 if needed`. Section H's row (line 129) was flipped during the H review; do the same here.

- [ ] Change:
  ```
  | `parse-recovery-info` emission (DefaultErrorStrategy subclass) | Phase 3 if needed |
  ```
  to:
  ```
  | `parse-recovery-info` emission (DefaultErrorStrategy subclass) | Completed in Phase 3.I (2026-05-16) |
  ```

## I-6 — Rephrase user-facing recovery messages

`recovery.ts` emits descriptions like `recovered at '{'` and `inline recovery at '{'`. Users seeing these in VS Code's Problems panel won't recognize the jargon.

- [ ] In `packages/parser/src/recovery.ts`, change the `recoveryEvent(tok, action)` call sites:
  - `'recovered'` → `'parser resumed after syntax error'`
  - `'inline recovery'` → `'parser inserted/skipped token to continue'`
  Or pick wording you like; just drop the antlr4ng-internal "recover" / "inline recovery" terminology from anything that hits a user's screen.

**Verify:** none beyond `pnpm --filter @modeler/parser test` still green (messages are descriptive, not asserted on exact text).

## I-7 — Populate real offsets in `RecoveryEvent`

`walker.ts:170-178` writes `offsetStart: 0, offsetEnd: 0`. Those fields are documented as 0-indexed source offsets in `CLAUDE.md`'s `SourceLocation` invariants; zero is a lie that could foot-gun future code paths that use them.

- [ ] In `recovery.ts`, extend `RecoveryEvent`:
  ```ts
  export interface RecoveryEvent {
    line: number;
    column: number;
    offsetStart: number;
    offsetEnd: number;
    description: string;
  }
  ```
- [ ] Populate from `tok.start` / `tok.stop + 1` in `recoveryEvent(tok, action)`:
  ```ts
  function recoveryEvent(tok: Token, action: string): RecoveryEvent {
    return {
      line: tok.line,
      column: tok.column,
      offsetStart: tok.start,
      offsetEnd: tok.stop + 1,
      description: `${action} at '${tok.text ?? ''}'`,
    };
  }
  ```
- [ ] In `walker.ts:170-178`, use `event.offsetStart` / `event.offsetEnd` instead of hard-coded zeros. Also use them to derive a more accurate `endColumn` if `tok.stop - tok.start` ≥ 0.

**Verify:** `pnpm --filter @modeler/parser test` still green; the recovery-info diagnostic now points at the actual recovery token.

## I-8 — Drop the underscore on `_e`

`recovery.ts:12`: `_e` is used (passed to `super.recover`). The leading underscore signals "unused" — confusing here.

- [ ] Rename `_e` → `e`.

---

## Final gate

After every box above:

```bash
pnpm -r build
pnpm -r test
pnpm -r lint
pnpm -r typecheck
pnpm --filter @modeler/integration-tests test    # info-severity propagation case green
```

Then update the test totals at `progress-phase-03.md`'s Test Results block from the actual run output, and tick the Section I boxes only after the LSP integration test confirms info-severity propagation. Per [MEMORY → feedback-progress-doc-skepticism]: `[x]` reflects what the runner observed, not what the developer intended.
