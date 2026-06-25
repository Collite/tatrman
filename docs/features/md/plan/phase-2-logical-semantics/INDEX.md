# Phase 2 — Logical-model semantics (management doc)

Status: **ready after Phase 1** · Precedes Phase 3 · Owner: editor-tooling

Phase 2 makes the binding-free logical model **mean something**: symbols, reference resolution,
leaf/grain computation, hierarchy inference, calc-catalog validation, and additivity — with the
full logical `md/*` diagnostic set wired through the LSP. See
[`../implementation-plan.md`](../implementation-plan.md) §"Phase 2" and
[`../../contracts.md`](../../contracts.md) §5–§7.

## Pre-flight

- Phase 1 DONE (parser+AST+catalog).
- `@modeler/semantics` gains a `workspace:*` dependency on `@modeler/md-catalog` (added in 2A);
  update the dependency graph in `CLAUDE.md`.
- Gates green.

## Stages (TDD-ordered mini-task-lists)

- [ ] **Stage 2A** — [Symbol table, namespaces & catalog preload](2A-symbols-and-catalog.md)
- [ ] **Stage 2B** — [Resolver & reference diagnostics](2B-resolver.md)
- [ ] **Stage 2C** — [Domain / attribute / measure validators](2C-domain-attr-measure.md)
- [ ] **Stage 2D** — [Map + calc-catalog validation](2D-map-calc.md)
- [ ] **Stage 2E** — [Leaf/grain lattice + hierarchy inference](2E-leaf-grain-hierarchy.md)
- [ ] **Stage 2F** — [Cubelet validator + LSP wiring + integration](2F-cubelet-lsp.md)

## Sequencing

**2A → 2B** first (symbols then resolver). **2C/2D** depend on 2B (need resolved refs). **2E**
depends on 2D (leaf/grain is computed from resolved N:1 maps). **2F** depends on 2E (cubelet grain
references the lattice) and closes the phase with LSP features + integration tests.

## Working rules

- **TDD:** semantics unit tests per validator come first (red); table-driven for the lattice/
  hierarchy algorithms. Then implement.
- New LSP feature tests go in **`tests/integration/`** (the `PassThrough`-paired-connection
  harness), not `packages/lsp/__tests__/` (CLAUDE.md invariant).
- Diagnostic codes are the canonical set in [`../../contracts.md`](../../contracts.md) §7 — don't
  invent new codes; amend contracts by PR if needed.

## Definition of DONE for Phase 2

1. Every **logical** `md/*` code in contracts §7 has a triggering fixture **and** a clean-file
   negative fixture.
2. Leaf/grain and hierarchy inference match hand-computed expectations on the design's Time example
   and the RAE cost-center example.
3. Catalog type-checks reject a mismatched `from`/`to` and accept the correct calendar maps.
4. Hover / go-to-definition / completion work for MD symbols (calc completion lists catalog names).
5. All four gates green; existing ER/CNC semantics unbroken.
