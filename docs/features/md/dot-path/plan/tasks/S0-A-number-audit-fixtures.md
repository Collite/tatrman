# S0-A — NUMBER audit & guard fixtures

Goal: before touching the grammar, pin current behavior. Inventory every `NUMBER` dependency,
freeze today's numeric-literal parses as guard fixtures, and author the (red) fixtures for the
new float/path rule (contracts §1.3) and the TTR-M `publish: members` clause (§1.4).

Prereq: arc pre-flight in `../implementation-plan.md` all checked. TDD: everything here is
red-side; S0-B turns it green.

## Tasks

- [ ] **S0-A1 — NUMBER usage inventory.** `grep -n NUMBER packages/grammar/src/TTRP.g4` (expect:
  token def ~line 219, `typeName` ~133, `literal` ~134) and every generated-parser consumer:
  `grep -rn "NUMBER" packages/kotlin/ttrp-frontend/src/main packages/kotlin/ttrp-emit/src/main
  packages/kotlin/ttrp-graph/src/main packages/lsp/src packages/parser/src` (skip `generated/`).
  Record the full table (file · line · what it does with NUMBER) **in this file** under Coder
  notes. Every row must be re-pointed in S0-B; a missed row is a review finding.
- [ ] **S0-A2 — guard fixtures for existing numeric shapes.** Add
  `tests/conformance/fixtures/40-ttrp-numeric-guard.ttrp`: a minimal program whose expressions
  use every currently-legal numeric shape — integer literal, `12.5` in a `calc`, `decimal(12,2)`
  typeName args, numeric comparison in `filter`, numeric agg arg. Capture today's parse output as
  the golden (the conformance harness's existing golden mechanism). This file must parse
  **identically** before and after S0-B.
- [ ] **S0-A3 — float/path decision fixtures (red).** Add
  `tests/conformance/fixtures/41-md-float-path.ttrp` covering **every row** of the contracts
  §1.3 table: `.25`, `25.`, `2025.06` (floats — note `.25`/`25.` are *newly legal* in S0-B;
  mark expected-parse accordingly), `sales.2025.06`, `2025.06.sales`, `2025.06.15`,
  `sales.{Kaufland, Lidl}.net`, `sales.2024..2026.net`, `x * sales.2025.net`, plus whitespace
  variant `2025 . 06` (≡ float, R2) and quoted member `sales."Kaufland K123".net`.
- [ ] **S0-A4 — TTR-M publish fixture (red).** Add
  `tests/conformance/fixtures/42-md-publish-members.ttrm`: an md-schema model with two domains,
  one carrying `publish: members`, one not.
- [ ] **S0-A4b — cubelet-statement fixtures (red).** Add
  `tests/conformance/fixtures/43-md-cubelet-stmts.ttrp` covering contracts §1.2 `cubeletStmt`:
  `C = <mdExpr>`, `C := <mdExpr>` bare, `C := <mdExpr> with { shape: long, table: dbo.f_c,
  journal: overwrite }`, `C += <mdExpr>`, `C -= <mdExpr>`, a slice assignment
  `kaufland.2026.month.*.plan.net = …` in the same program (dispatch is semantic — both must
  *parse* as `cubeletStmt`), and an ordinary TTR-P statement between them (interleaving, D20).
  Negative parse rows: `with` without `:=`; `C ==` (existing S9 EQ-001 unaffected).
- [ ] **S0-A5 — Kotest red spec.** `packages/kotlin/ttr-parser/src/test/kotlin/org/tatrman/ttr/
  parser/md/FloatPathLexSpec.kt` (load fixtures via the `ConformanceSpec.kt` resource-path
  convention): table-driven over S0-A3's rows asserting the parse-tree shape (floatLiteral vs
  mdPath vs binary-op), a `CubeletStmtParseSpec.kt` over S0-A4b (operator token, optional
  withClause object, LHS/RHS subtree shapes), and a `PublishMembersParseSpec.kt` asserting the
  domain flag surfaces on the AST. All **red**.
- [ ] **S0-A6 — TS red spec.** `packages/parser/src/__tests__/md-float-path.test.ts` mirroring
  S0-A5's table against the TS parser (Vitest). Red.
- [ ] **S0-A7 — confirm red & gates.** S0-A5/A6 fail for the *right* reason (missing grammar, not
  fixture bugs); guard fixture 40 green; both domains' gates otherwise green. Commit
  `md-sugar S0A: NUMBER audit + float/path guard fixtures (red)`.

## Coder notes

_(empty — coder records here; S0-A1 table lands here)_

## References

- Contracts §1.1–1.4 (grammar deltas), §1.3 table (normative fixture rows) — D14, D15.
- Existing fixture numbering: continue from the highest in `tests/conformance/fixtures/`.
- `docs/grammar-master/new-grammar-version-process.md` — read now; S0-B follows it.
