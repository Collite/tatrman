# Stage 2E — Leaf/grain lattice + hierarchy inference

Goal: compute the emergent leaf set and grain partial order from N:1 maps, partition co-leaves via
1:1 maps, and infer hierarchy connecting-maps (with `via:` override and ambiguity error). This is
the algorithmic heart of the model.

Prereq: Stage 2D merged & green (maps validated; attribute→domain-map sugar resolved in 2B). TDD:
2E1 before 2E2–2E4. Build this as **pure functions** over the resolved symbol graph so they are
unit-testable in isolation.

References (verified):
- Algorithms: [`../../contracts.md`](../../contracts.md) §6.1 (leaf/grain), §6.2 (1:1 co-leaf),
  §6.3 (hierarchy step inference).
- Place the pure graph functions in a new `packages/semantics/src/md-lattice.ts`; call from the
  hierarchy validator in `md-validators.ts`.
- Codes: contracts §7 (`md/no-hierarchy-step`, `md/ambiguous-hierarchy-step`, `md/level-not-in-dim`,
  `md/grain-not-leaf` is emitted in 2F).

---

- [ ] **2E1 — Tests first (red), table-driven.** Construct small in-memory map graphs and assert:
  - leaves = attributes with no incoming N:1 map; a 1:1 map does **not** demote a leaf;
  - co-leaf classes from 1:1 maps (e.g. `code` ↔ `id` are co-leaves; two attributes sharing a
    domain but with no 1:1 map are **not** co-leaves);
  - grain partial order = transitive closure of N:1 edges;
  - hierarchy inference: a unique N:1 between consecutive levels is picked; `via:` overrides; zero
    connecting maps → `md/no-hierarchy-step`; two → `md/ambiguous-hierarchy-step`;
  - a level attribute not in the hierarchy's dimension → `md/level-not-in-dim`.
  - Confirm red.

- [ ] **2E2 — Lattice functions.** In `md-lattice.ts`: `computeLeaves(graph)`,
  `coLeafClasses(graph)`, `grainOrder(graph)` — pure, no LSP/IO. Operate on domain-level N:1/1:1
  maps (attribute maps already lowered to domain maps in 2B).

- [ ] **2E3 — Hierarchy inference.** For each consecutive `(lower, upper)` level (leaf→root):
  use `via:` if present (validate it's an N:1 `lower→upper`); else find the unique connecting N:1
  map (direct or catalog calc). Emit `md/no-hierarchy-step` / `md/ambiguous-hierarchy-step` /
  `md/level-not-in-dim` accordingly.

- [ ] **2E4 — Surface results.** Expose computed leaves/grain order to later consumers (the cubelet
  validator in 2F and, eventually, the Designer). Cache per project-symbol build.

- [ ] **2E5 — Verify.**
  - 2E1 tests pass, including the design's Time hierarchy (`[Day, Month, Quarter, Year]`) and the
    RAE cost-center 2:1 map (`[Account, CostCenter] → Activity`).
  - `pnpm --filter @modeler/semantics test && pnpm -r typecheck && pnpm -r lint && pnpm -r build`

- [ ] **2E6 — Commit.** `Section MD-2E: leaf/grain lattice + hierarchy inference`.
