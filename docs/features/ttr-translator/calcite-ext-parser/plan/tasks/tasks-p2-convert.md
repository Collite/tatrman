# CEP-P2 — CONVERT / TRY_CONVERT

> DoD: [`../plan.md`](../plan.md) §CEP-P2. Contracts: [`../../contracts.md`](../../contracts.md)
> §4–§6. Reference: `query-translator` `functions/ConvertOperators.kt` +
> `codec/sql/SqlValidator.kt` (lines ~34–42). Pre-flight: **CEP-P0 DONE** (`ConvertOperators`
> already ported in P0.T4; `TryConvertFunctionCall()` already in the generated parser).
>
> **Verify (phase):** `./gradlew :packages:kotlin:ttr-translator:test` — `ConvertSpec` green +
> all prior specs still green; ktlint clean.

- [x] **T1 (test first) — ConvertSpec.** Write `codec/sql/ConvertSpec.kt` mirroring the reference's
  convert coverage: `CONVERT(type, expr [, style])` and `TRY_CONVERT(type, expr [, style])` validate
  to a RelNode; the type operand is normalised (never reaches sql-to-rel as a non-expression); MSSQL
  unparse round-trip. Run — **RED** (the `ConvertRewriter` isn't wired into validation yet).

- [x] **T2 — Register the convert operators.** Add `ConvertOperators.table`
  (`SqlOperatorTables.of(CONVERT, TRY_CONVERT)`) into `ExtOperators.OPERATOR_TABLE`. Verify the
  operators resolve during validation (the "operator not found" failure disappears, replaced by the
  type-operand error T3 fixes).

- [x] **T3 — Wire the post-parse ConvertRewriter.** In `codec/sql/SqlValidator.validateAndConvert`,
  apply `parsed.accept(ConvertOperators.rewriter()) ?: parsed` **before** sql-to-rel and validate the
  rewritten node (contracts §5.3; mirror the reference SqlValidator lines ~34–42). This normalises the
  `(TRY_)CONVERT` `SqlDataTypeSpec` operand into a bare string literal. Run — ConvertSpec goes
  **GREEN**.

- [x] **T4 — MSSQL CONVERT parity.** Confirm the stock parser's core `CONVERT` (MSSQL) path and our
  `TRY_CONVERT` both flow through the same rewriter (the reference rewriter handles both). Add/keep the
  spec assertion that a plain `CONVERT(...)` still validates unchanged (no regression to the core path).

- [x] **T5 — Additive-invariant.** Full suite `--rerun-tasks` green at baseline + all P0/P1/P2 specs;
  ktlint clean; no pre-existing test moved. Pay special attention that adding the rewrite step to
  `validateAndConvert` did not alter any non-CONVERT query's validation (the shuttle must be a no-op
  for calls it doesn't match).

- [x] **T6 — Wrap.** Tick CEP-P2 in `00-task-management.md`; commit `CEP-P2: CONVERT/TRY_CONVERT via
  post-parse ConvertRewriter`; run the phase-exit `/review`.

## CEP-P2 — findings

- **Focused ConvertSpec (in-scope subset).** The reference has no dedicated ConvertSpec — CONVERT
  lives in `ConditionalConversionSpec`, which mostly exercises the wider out-of-scope `functions/`
  (IIF/CHOOSE/ISNULL/SQUARE/TRY_CAST/numeric). Wrote a focused `ConvertSpec` covering only the
  CONVERT/TRY_CONVERT (B-conv) behaviour this port delivers.
- **Operator chain reordered Ext-FIRST.** `CONVERT` collides with Calcite's standard SQL
  `CONVERT(e USING charset)`; with the standard table first, `CONVERT(VARCHAR(10), d, 120)` validated
  as the charset form (`UnsupportedCharsetException: 120`). Fixed by chaining
  `ExtOperators → PlatformOperators → SqlStdOperatorTable` (custom-first, mirroring the reference's
  `CustomOperators`-first policy) so our CONVERT wins overload resolution. Confirmed additively safe —
  full suite green (405), no pre-existing test moved.
- **ConvertRewriter wired into `SqlValidator.validateAndConvert`** (post-parse `parsed.accept(...)`).
  `SqlValidatorSpec`'s two mock-planner tests needed a `node.accept(any<SqlVisitor<SqlNode>>())`
  stub to account for the new step (bare `any()` doesn't match the generic `accept`).
- **Decode entries** `convert`/`try_convert` added to `operatorFor`.
- **Result:** ttr-translator **405 tests green** (P1 baseline 400 + ConvertSpec 5); ktlint clean;
  parser regenerated.
