# CEP-P1 — DATEADD / DATEDIFF / DATEPART / DATE_PART

> DoD: [`../plan.md`](../plan.md) §CEP-P1. Contracts: [`../../contracts.md`](../../contracts.md)
> §4–§6. Reference spec: `query-translator/src/test/.../codec/sql/DateaddSpec.kt`. Pre-flight:
> **CEP-P0 DONE** (parser generates + compiles; `Dateparts` already ported in P0.T4).
>
> **Verify (phase):** `./gradlew :packages:kotlin:ttr-translator:test` — `DateaddSpec` green +
> pre-existing suite + P0's specs still green; ktlint clean.

- [ ] **T1 (test first) — DateaddSpec.** Port `codec/sql/DateaddSpec.kt` (rename imports; reuse
  `FixtureModel`). Include: each of `DATEADD` / `DATEDIFF` / `DATEPART` / `DATE_PART` validates to a
  RelNode; the datepart abbreviation normalises (`dd`→`DAY`, etc.) via `Dateparts`; and a full
  parse→rel→`plan.v1`→SQL round-trip for one `DATEADD` case. Run — **RED** (operators not yet in the
  table; possibly a wire gap on the SYMBOL operand).

- [ ] **T2 — Register the built-in date operators.** Add `SqlLibraryOperators.DATEADD` / `DATEDIFF` /
  `DATEPART` / `DATE_PART` to `ExtOperators.OPERATOR_TABLE`. **Verify the exact operator handles +
  table-construction against Calcite 1.41.0** (`context7 org.apache.calcite` + `~/Dev/view-only/calcite`)
  — `SqlLibraryOperators` field names and whether a `ListSqlOperatorTable` vs `SqlOperatorTables.of`
  is needed. Run DateaddSpec — the **validate** assertions should pass; the round-trip may still fail
  (T3).

- [ ] **T3 — Investigate wire SYMBOL round-trip (architecture R2).** The datepart travels as a
  SYMBOL / `SqlIntervalQualifier` operand. Check whether tatrman's `wire/` encoder-decoder
  (`PlanNodeEncoder`/`PlanNodeDecoder`/`Expressions`) already round-trips SYMBOL operands to
  `plan.v1`. Compare against the reference `wire/Expressions.kt`. **If a gap exists**, port the
  minimal SYMBOL-operand support (this is the only place the DATEADD family diverges from a plain
  function call). If no gap, mark this task done with a one-line note. Run — DateaddSpec round-trip
  goes **GREEN**.

- [ ] **T4 — Datepart coverage.** Confirm `Dateparts.toTimeUnit` covers the T-SQL abbreviations the
  spec exercises (yy/qq/mm/dd/wk/hh/mi/ss and full names); add any missing mapping **only** if the
  spec needs it (do not widen beyond the reference). Verify: the normalisation assertions in
  DateaddSpec pass for every datepart it lists.

- [ ] **T5 — Unparse round-trip.** Assert a `DATEADD(DAY, 1, col)` translates back to T-SQL-valid SQL
  (MSSQL dialect) — the interval unit unparses bare (`DAY`, not `` `DAY` ``). Add the assertion to
  DateaddSpec if the reference has it. Verify green.

- [ ] **T6 — Additive-invariant.** Full suite `--rerun-tasks` green at baseline + all P0/P1 specs;
  ktlint clean; no pre-existing test moved.

- [ ] **T7 — Wrap.** Tick CEP-P1 in `00-task-management.md`; commit `CEP-P1: DATEADD/DATEDIFF/DATEPART
  validation + wire round-trip`; run the phase-exit `/review`.
