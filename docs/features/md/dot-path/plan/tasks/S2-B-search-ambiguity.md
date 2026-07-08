# S2-B — constraint search, ambiguity, defaults fill

Goal: the resolver's heart — R8 search, R9 ambiguity policy, R10 defaults, R11 repetition — plus
the golden-fixture harness the whole arc runs on, and the performance bound (TTRP-MD-014).

Prereq: S2-A. TDD: S2-B1–B3 (red) before S2-B4–B6.

## Tasks

- [ ] **S2-B1 — golden fixture harness (red).** Define the contracts §10 fixture format:
  `src/test/resources/golden/<case>.json` =
  `{ model: "sales-model", members: "default"|null, asof, context?, input: string,
  expected: { status, canonical?, shape?, diagnostics?, alternatives? } }` — `input` is the raw
  path text (parsed via ttr-parser in the harness), `canonical` the §3 canonical **text** form.
  `GoldenSpec.kt` walks the directory. Write the first goldens: every path example in the design
  note §1–§5 and all **order permutations** of `sales.Kaufland.2025.net.sum` (≥8 permutations,
  identical canonical output — the "any order" acceptance test).
- [ ] **S2-B2 — red ambiguity & error goldens.** Cases: member in two dimensions (add a supplier
  dimension member `Kaufland` to the fixture members) → MD-003 with **both** alternatives as
  canonical texts, deterministically sorted; unresolvable combination → MD-002 with per-token
  reasons; two measures `sales.net.gross.2025` → MD-005 (D12); same-attribute repetition
  `sales.Kaufland.Lidl.net` → MD-006 suggesting braces (D15); same-dimension different-attribute
  `sales.2025.january.net` → legal drill (both time coordinates); derivable hop
  `Kaufland.zip` → resolves via `address` (design-note omission example).
- [ ] **S2-B3 — red bound spec.** A pathological synthetic model (30 cubelets sharing member
  names) + 12-token input → MD-014 at the documented bound, not a hang. Bound value: pick,
  document in the diag text, record here.
- [ ] **S2-B4 — implement search (R8).** `PathSearch`: pair-bound coordinates fixed (S2-A6);
  candidate sets from the classifier; prune by cubelet candidates first (each cubelet restricts
  legal dimensions/measures via the lattice); enumerate consistent assignments; hop-reachability
  via `GrainLattice.reachableFrom`. Exhaustive within the bound — **no scoring, no heuristics**
  (P2): every surviving assignment is reported.
- [ ] **S2-B5 — implement R9 + R10.** Zero → MD-002 (collect the tightest per-token failure);
  \>1 → MD-003 (all alternatives, qname-sorted); exactly 1 → defaults fill in contract order
  (measure ← cubelet default, agg ← measure default, unmentioned grain dims ← context-or-free)
  with an `ExplainStep(token=null)` per filled default.
- [ ] **S2-B6 — implement R11** inside the search's consistency check (attribute pinned twice ⇒
  MD-006 short-circuit; dimension twice via different attributes ⇒ conjunction).
- [ ] **S2-B7 — benchmark.** Kotest `PerformanceSpec` (annotation-gated, runs in CI nightly not
  per-commit if the repo has that convention — else plain): 10-token path over sales-model
  resolves < 10 ms median over 1000 iterations post-warmup. Record numbers here.
- [ ] **S2-B8 — green + gates.** All goldens green; Kotlin gate green. Commit
  `md-sugar S2B: constraint search + ambiguity + defaults`.

## Coder notes

_(empty — bound value and benchmark numbers land here)_

## References

- Contracts §2 R8–R11, §6, §10 · design note §1 (reframe), decisions 10/12/15.
- The golden format is **shared with the future TS port** — treat it as a contract, changes need
  a contracts.md changelog entry.
