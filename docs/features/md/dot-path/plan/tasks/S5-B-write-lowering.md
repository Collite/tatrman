# S5-B — write lowering & journaling (ttr-translator + E2E)

Goal: lower validated cubelet assignments to plan.v1 Store operations honoring the binding's
journaling mode (R22); spread only via declared strategies; end-to-end write-then-read proof.

Prereq: S5-A. TDD: S5-B1–B2 (red) before S5-B3–B5.

## Tasks

- [ ] **S5-B1 — red journaling goldens.** Translator goldens for the same assignment lowered
  under each binding journaling mode (brief §Journaling): **overwrite** → UPDATE-shaped Store;
  **invalidate** → valid-flag flip + append (binding must declare the `valid` column — fixture
  variant); **diff** → delta append. Plus `+=`: diff-journaled binding → direct delta append;
  overwrite binding → read-modify-write plan (Load + arithmetic + Store).
- [ ] **S5-B2 — red spread + guard goldens.** Spread case (S5-A3c, declared strategy) → the
  strategy's expansion plan (v1: whatever strategies MD phase-3 bindings can declare — likely
  proportional-to-existing or `top(1)`; take the declared vocabulary as-is from the MD binding
  contracts, do NOT invent one here); deferred-member LHS (R13/R22) → existence-guard before any
  write, guard failure aborts statement (representation per S4-A5's choice); MD-011 at bind time.
- [ ] **S5-B3 — implement Store lowering** per mode; reuse S4-A's `MdPathLowering` for the RHS
  read plan; assignment boundary reconciliation marks (S5-A6) become Aggregate (collapse) /
  keyed join (align) nodes feeding the Store.
- [ ] **S5-B4 — implement spread lowering** for declared strategies only (MDS5); anything else
  is unreachable post-S5-A (assert with a defensive error, not a silent fallback).
- [ ] **S5-B5 — E2E write round-trip (PG).** Extend the S4-B fixture: run
  `Kaufland.2026.month.*.plan.net = sales.2025.month.*.net * 1.1` (align case) on PG under each
  journaling fixture variant; then **read back** `kaufland.plan.2026.net` through the dot-path
  read pipeline and assert the expected sum; invalidate-mode run asserts old rows flipped +
  new rows valid; diff-mode asserts delta rows.
- [ ] **S5-B6 — gates.** Full Kotlin gate + conformance; un-skip remaining S5 goldens. Commit
  `md-sugar S5B: write lowering + journaling + round-trip`.

## Coder notes

_(empty — record the strategy vocabulary actually available from MD phase-3 bindings)_

## References

- Contracts §5 R22, §8 (assignment row) · brief §Journaling · MD feature contracts (binding
  strategy declarations; design.md §6.3 — inverse strategies remain deliberately thin in v1).
- Polars engine writes: **out of scope v1** (fact stores are DB-backed); record as deferred if
  tempted.
