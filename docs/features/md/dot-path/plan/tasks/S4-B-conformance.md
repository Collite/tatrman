# S4-B — end-to-end engine conformance (reads)

Goal: prove the whole read pipeline — `.ttrp` program with dot-paths → compile → emit → run —
produces results identical to hand-written SQL, on both v1 engines.

Prereq: S4-A. TDD: S4-B1–B2 (goldens) before S4-B3.

## Tasks

- [ ] **S4-B1 — E2E fixture program + data.** `tests/` home per ttrp-conform's convention: seed
  data for `f_sales`/`f_plan`/`d_customer`/`customer_address` (small, hand-checkable — ~20 rows,
  incl. a Kaufland with multiple 2025 months and an address history for the `zip` latest-valid
  case); a `.ttrp` program computing: (a) `kaufland.sales.2025.net` (scalar), (b)
  `sales.2025.month.*.net` (vector), (c) share-of-total via `customer.*` collapse, (d)
  `kaufland.zip` (hop + MAX latest-valid default), (e) a calc-map month drill.
- [ ] **S4-B2 — SQL goldens.** Hand-written SQL per case against the same seed, results committed
  as goldens (values, not SQL text — engines differ).
- [ ] **S4-B3 — PG engine run.** ttrp-conform executes the program on the PostgreSQL engine;
  results == goldens. Follow the harness's existing engine-provisioning pattern.
- [ ] **S4-B4 — Polars engine parity.** Same program, Polars emit path; identical results (3VL
  and decimal rules already harmonized by TTR-P — any discrepancy here is a bug in the lowering,
  not the engines; investigate, don't tolerance-fudge).
- [ ] **S4-B5 — manifest fields.** Bundle manifest carries `mdAsof` (S3-A2) and
  `memberFingerprint: null` (placeholder until S6-B) — assert presence + shape so S6 is additive.
- [ ] **S4-B6 — gates + lockstep check.** Both domains green; confirm no `kotlin-translator/v*`
  tag is needed yet (local-only) or coordinate one per PUBLISHING.md if kantheon needs a preview.
  Commit `md-sugar S4B: read E2E conformance (PG + Polars)`.

## Coder notes

_(empty)_

## References

- `packages/kotlin/ttrp-conform` (harness) · PUBLISHING.md (lockstep policy) · contracts §8.
- The seed data + program become the demo asset for S7's smoke script — keep them readable.
