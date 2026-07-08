# S1-A — MdModel & grain lattice (ttr-semantics `md` subpackage)

Goal: give Kotlin the MD Layer A subset the resolver needs (MDS2): the MD symbol graph and the
grain lattice. The TS implementation (`@tatrman/semantics`, md plan stages 2A/2B4/2E) is the
behavioral spec — port, don't redesign.

Prereq: S0-B (parser exposes MD defs in Kotlin); TS md phases 2A/2B/2E merged.
TDD: S1-A1–A3 (red) before S1-A4–A6.

## Tasks

- [ ] **S1-A1 — fixture model.** `packages/kotlin/ttr-semantics/src/testFixtures/resources/
  fixtures/md/sales-model/` (java-test-fixtures source set, mirroring ttr-metadata's fixture
  home): a `.ttrm` md schema with — dimensions `customer` (attrs `name`, `code`, `region`;
  `address` with `zip`, time-based), `time` (grain attr `date`), `product`; N:1 maps
  (`region ← name`, month/quarter calc maps on `date`), one 1:1 map (`code ↔ name`), one
  attribute-level map (2B4 sugar); measures `net` (sum default), `gross`; cubelets `sales`
  (grain: customer.name × time.date, default measure `net`) and `plan` (customer.name ×
  time.month). Keep it the **shared fixture for the whole arc** — S2–S7 reuse it.
- [ ] **S1-A2 — red MdModelLoadSpec.** `packages/kotlin/ttr-semantics/src/test/kotlin/org/
  tatrman/ttr/semantics/md/MdModelLoadSpec.kt`: load S1-A1 via ttr-parser, build `MdModel`,
  assert object counts, qnames, dimension membership of every attribute, cubelet grains as
  declared.
- [ ] **S1-A3 — red GrainLatticeSpec.** Same package: leaf iff no N:1 map targets it (`name`,
  `date` are leaves; `region`, `month` are not); 1:1 maps do **not** demote leafness (`code`
  stays co-leaf); partial order = transitive closure of N:1 edges (assert
  `date < month < quarter`, `name < region`); `reachableFrom(attr)` for derivable hops; a
  cubelet-grain validity check (each grain attr is in the lattice). Port the TS 2E case names
  where they exist — reviewers diff the suites.
- [ ] **S1-A4 — implement MdModel.** Package `org.tatrman.ttr.semantics.md`: immutable data
  classes (`MdDomain`, `MdDimension`, `MdAttribute`, `MdMap` (+`MapKind` N1/OneOne/Calc),
  `MdMeasure`, `MdHierarchy`, `MdCubelet`, `MdModel`) + `MdModelBuilder.from(parseResults)`.
  Resolution of the 2B4 attribute→domain-map sugar happens here (mirror TS resolver stage 2B4:
  attribute-level map ref → the underlying domain map).
- [ ] **S1-A5 — implement lattice.** Pure functions over `MdModel` (`GrainLattice.of(model)`),
  exactly the TS 2E algorithm (build N:1 digraph over attributes via their domain maps; leaves;
  closure; jgrapht-core is already a ttr-semantics-adjacent dep in ttr-metadata — prefer plain
  Kotlin here unless closure perf demands otherwise).
- [ ] **S1-A6 — green + gates.** A2/A3 green; `./gradlew :packages:kotlin:ttr-semantics:test`
  green; full Kotlin gate green. Commit `md-sugar S1A: MdModel + grain lattice (Kotlin port)`.

## Coder notes

_(empty)_

## References

- Spec: `packages/semantics/src/` md stages — 2A (symbols), 2B4 (map sugar), 2E (lattice); task
  docs `docs/features/md/plan/phase-2-logical-semantics/{2B-resolver.md,2E-leaf-grain-hierarchy.md}`.
- Design: `docs/features/md/design.md` §5.3, §6.1 · contracts (MD feature) §6.1.
- Lattice invariants: leaf = no N:1 in-edge; 1:1 connects co-leaves (never demotes).
