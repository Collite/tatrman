# S5-A — strict LHS, context overlay, grain reconciliation (ttrp-frontend)

Goal: cubelet-assignment semantics: statement recognition, strict LHS (R19), the LHS→RHS context
overlay at statement level (R20), and collapse/align/spread reconciliation (R21).

Prereq: S4-B (reads proven; S5-B lowers the writes). TDD: S5-A1–A3 (red) before S5-A4–A6.

## Tasks

- [ ] **S5-A1 — red StrictLhsSpec.** Over sales-model (target `plan`, grain customer.name ×
  time.month, measures net): (a) `Kaufland.2026.plan.net = …` — wait: `plan`'s grain is
  **month**, so bare `2026` on the LHS is coarse ⇒ spread territory; the *legal complete* LHS
  cases are `Kaufland.2026.month.*.plan.net` (explicit spread/vector) and
  `Kaufland.time.2026-03.plan.net` (pinned month) — encode both as legal; (b) missing customer →
  MD-009 listing `customer.name`; (c) measure omitted → MD-009 naming the measure slot;
  (d) derivable-hop on LHS (`Kaufland.zip… =`) → MD-009 (no hops, R19); (e) defaults do NOT fill
  (same path legal as a read, illegal as LHS — the sharpest test of the asymmetry); (f) LHS
  order-free: ≥4 permutations of a legal LHS all resolve identically.
- [ ] **S5-A2 — red ContextAssignSpec.** The design-note §4 table verbatim as a spec:
  `Kaufland.month.*.2026.plan.net = sales.2025 * 1.1` — RHS resolves
  `sales[Kaufland, 2025].net` (customer + measure inherited, time overridden, cubelet replaced);
  share-of-total with `customer.*` un-pin on the RHS; RHS explanation steps carry
  `via: "context"` for inherited slots.
- [ ] **S5-A3 — red GrainReconcileSpec (R21).** (a) collapse: RHS free on month, LHS pinned month
  → RHS collapses via default agg; (b) align: LHS `month.*` × RHS `month.*` → vectorized
  assignment, shapes align; (c) spread: LHS `month.*` with **scalar** RHS → spread — legal iff
  the plan binding declares an allocation strategy for time (add a declared-strategy variant to
  the binding fixture), else MD-010; (d) LHS free dim the RHS also can't provide + no strategy →
  MD-010 naming the dimension.
- [ ] **S5-A4 — implement statement recognition.** `mdPath = expression` (and `+=`) where the
  LHS path's cubelet slot resolves → cubelet-assignment IR statement; otherwise fall through to
  existing statement handling untouched (regression cases).
- [ ] **S5-A5 — implement R19 + R20 wiring.** LHS resolved with `strict = true` resolver flag
  (no context, no grain defaults, no hops — resolver already supports the components; add the
  strict flag to `MdPathResolver.resolve` if S2 didn't) ⇒ MD-009 aggregation of missing slots.
  Resolved LHS becomes `PathContext` for every RHS path in the statement (S2-C4 machinery).
- [ ] **S5-A6 — implement R21** at the assignment boundary in the checker: per-dimension
  set-algebra over LHS/RHS free dims → collapse marks (with agg), alignment pairs, spread
  demands checked against the binding's declared strategies.
- [ ] **S5-A7 — green + regression + gates.** Existing assignment/statement specs green;
  un-skip `pending: "S5"` goldens (MD-009/010). Kotlin gate green. Commit
  `md-sugar S5A: strict LHS + context + reconciliation`.

## Coder notes

_(empty — if S2 lacked the `strict` flag, note the resolver API addition + contracts changelog)_

## References

- Contracts §5 (R19–R21), §6 · decisions "LHS strict"/option 3, §4 context rule, D-"* = free".
- Design note §3–§4 — the spec cases ARE the acceptance tests; quote them in test names.
