# Phase 1 — Catalog package + grammar foundation (management doc)

Status: **ready for implementation** · Precedes Phase 2 · Owner: editor-tooling

Phase 1 makes the parser accept every MD construct and produce a complete, located AST, and ships
the calc catalog as a typed package. **No semantic validation yet** — unresolved references stay
opaque strings (Phase 2 turns them into diagnostics). See the overall
[`../implementation-plan.md`](../implementation-plan.md) §"Phase 1", the
[`../../contracts.md`](../../contracts.md), and the [`../../grammar-md-changes.md`](../../grammar-md-changes.md)
sketch.

## Pre-flight

- Phase 0 Stages A–C merged (grammar 3.0: `schema binding`, `def area`, `.ttrm`, freed
  `domain`/`map`). Stages D/E (cross-repo) may still be in flight.
- Clean tree; all four gates green at 3.0:
  `pnpm -r typecheck && pnpm -r lint && pnpm -r build && pnpm -r test`.

## Stages (each its own mini-task-list, TDD-ordered)

- [x] **Stage 1A** — [`@modeler/md-catalog` package](1A-md-catalog-package.md)
- [x] **Stage 1B** — [Grammar 3.1 + regen + parser fixtures](1B-grammar-3-1.md)
- [x] **Stage 1C** — [AST + walker: logical objects](1C-ast-walker-logical.md)
- [x] **Stage 1D** — [AST + walker: binding objects](1D-ast-walker-binding.md)

## Sequencing

**1A ∥ 1B** may run in parallel (independent: catalog data vs. grammar). **1C** needs 1B
(walker reads generated parser). **1D** needs 1C (shares walker scaffolding). Catalog (1A) is
needed by Phase 2, not Phase 1 — but land it here so ai-platform can start lowerings in parallel.

## Working rules

- **TDD:** the test task in each stage comes first (red), then implementation (green).
- **Grammar regen is not optional** (Stage 1B): after editing `TTR.g4` run the parser `prebuild`
  (regenerates `packages/parser/src/generated/*` **and** `packages/grammar`'s property-map via
  `extract-property-map.ts`) and the vscode-ext TextMate regen. `generated/` is gitignored.
- **Don't hand-edit `packages/*/src/generated/**`.**
- **Cross-references stay opaque** at the parser layer (resolution is Phase 2).
- Check every checkbox the moment its task is done.

## Definition of DONE for Phase 1

1. Every MD construct in [`../../design.md`](../../design.md) and the catalog examples parses into
   the [`../../contracts.md`](../../contracts.md) §2 AST with accurate `SourceLocation`s.
2. `@modeler/md-catalog` builds and its tests are green; exported from `index.ts`.
3. All four gates green. No new MD diagnostics expected (opaque refs are not yet errors).
4. All existing ER attribute fixtures still parse (the shared-`attribute`-body change is additive).
