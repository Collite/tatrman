# Phase 3 — Binding layer (management doc)

Status: **ready after Phase 2** · Precedes Phase 4 · Owner: editor-tooling

Phase 3 binds the logical model to the physical (`db`) and structurally to `er`: shapes (wide/long),
journaling, multi-source, writeback completeness, and the bound-domain / table-map sources. See
[`../implementation-plan.md`](../implementation-plan.md) §"Phase 3" and
[`../../contracts.md`](../../contracts.md) §4, §6.6.

## Pre-flight

- Phase 2 DONE (logical symbols + leaf/grain available — completeness checks need the grain).
- Gates green.

## Stages (TDD-ordered mini-task-lists)

- [x] **Stage 3A** — [Bound-domain & table-map bindings](3A-domain-and-map-bindings.md)
- [x] **Stage 3B** — [Cubelet binding: shapes, columns, journaling](3B-cubelet-binding.md)
- [x] **Stage 3C** — [Multi-source, writeback completeness, md2er structural-only](3C-multisource-completeness.md)

## Sequencing

**3A → 3B → 3C.** 3A closes the `kind: bound` → source loop (the Phase-2
`md/bound-domain-no-source` hook) and the table-map `columns` check. 3B validates the cubelet→table
binding. 3C layers the cross-binding rules (multi-source grain union, writeback completeness) and
the thin md→er binding.

## Working rules

- **TDD** per stage; binding validators are unit-testable against fixtures plus an integration
  round-trip in `tests/integration/`.
- Reuse the `er2db_*` validator code paths as the analogue where shapes overlap (`target`, column
  maps).
- Codes are the binding subset of [`../../contracts.md`](../../contracts.md) §7 — canonical.

## Definition of DONE for Phase 3

1. Wide, long, map-mediated, and multi-source cubelet bindings validate; each binding `md/*` code
   has a triggering + clean fixture.
2. A `kind: bound` domain with a matching `md2db_domain` is clean; without it, errors.
3. A table-backed map with a complete `md2db_map` is clean; missing columns error.
4. An `md2er_cubelet` carrying shape/journaling/measures errors (`md/md2er-physical-prop`).
5. All four gates green.
