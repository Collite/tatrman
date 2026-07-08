# S3-B — shape typing, broadcast, diagnostics roster (ttrp-frontend)

Goal: the type discipline: shape inference (R15), align+broadcast (R16), no-implicit-collapse
(R17), single-measure typing (R18) — plus the complete negative roster for the read side.

Prereq: S3-A. TDD: S3-B1–B3 (red) before S3-B4–B5.

## Tasks

- [ ] **S3-B1 — red ShapeSpec.** Checker-level: expressions carrying paths get shapes —
  `sales.Kaufland.2025.net` scalar; `sales.2025.month.*.net` vector[time.month]; two free dims →
  sub-cubelet; shape surfaces on the frontend API beside the explanation (S3-A6).
- [ ] **S3-B2 — red BroadcastSpec (R16).** (a) vector[month] * scalar → vector[month];
  (b) vector[month] * vector[month] → aligned vector[month]; (c) vector[month] *
  vector[customer] → sub-cubelet[month, customer] (broadcast union); (d) vector[month] +
  vector[month] where one side is a sub-path of different cubelet at same free dim → legal
  (alignment is by dimension member, not cubelet). Inner alignment note (absent cells absent) is
  runtime semantics — compile-side only shapes are checked; assert the shape algebra exactly.
- [ ] **S3-B3 — red CollapseSpec (R17).** `filter` predicate comparing a vector path to a scalar
  → MD-008; same path wrapped in explicit agg token (`….net.sum` with month still free — agg
  over the free dim collapses it) → legal scalar; binary op never implicitly collapses (assert
  shape, not error, for b/c above).
- [ ] **S3-B4 — implement** shape algebra in the checker's type flow: `PathShape` joins the
  existing expression type record; binary-op rule = free-dim union with per-dim alignment;
  scalar-position demand = `freeDims.isEmpty()` else MD-008; explicit agg token narrows shape
  (define: agg token collapses **all** free dims of that path — document in contracts changelog
  if the narrower per-dim variant is wanted later). Measures type per R18 through the existing
  numeric typing (decimal rules Q9 untouched).
- [ ] **S3-B5 — negative roster.** One checker spec case per read-side diagnostic: MD-001…008,
  MD-011 (connected unknown member — InMemory snapshot), MD-012, MD-014. Un-skip the S2-C6
  `pending: "S3"` goldens that now apply (MD-008).
- [ ] **S3-B6 — regression + gates.** TYP_001/TYP_002 and existing typing specs green; Kotlin
  gate green. Commit `md-sugar S3B: shape typing + broadcast + roster`.

## Coder notes

_(empty — the "agg token collapses all free dims" confirmation lands here + contracts changelog)_

## References

- Contracts §4 (R15–R18), §6 · decisions D10, D11, D12 · design note §2 (`*` = free; grain
  decides collapse/align/spread — the spread arm is S5).
