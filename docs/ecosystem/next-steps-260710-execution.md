# Handover — After the Design-Gaps Session (written 2026-07-10, evening)

> **Purpose.** The design-gaps session closed all five gaps (RO-24..27; [`next-steps-260710-design-gaps.md`](./next-steps-260710-design-gaps.md) carries the ✅ ledger). **The Server 1.0.0 plan now has no known undesigned areas ahead of its next phases** — the resolver converges with SV-P3 planning by design, not by neglect. What remains is execution and the carried items below.
>
> **Cold-start reading order:** [`README.md`](./README.md) → [`server/implementation/plan.md`](./server/implementation/plan.md) (registers are current) → this document. Decision ground truth = [`platform/design/00-control-room.md`](./platform/design/00-control-room.md) §7 (log runs … RO-1..27). Project memory mirrors the state.

---

## The next session: SV-P0 execution

S1–S6 are ready ([`server/implementation/tasks/`](./server/implementation/tasks/)); the S4 list now includes **T5b** (MCP capability-manifest renames per RO-25) and the RO-24 note on T8 (interim artifacts stay `0.0.1-LOCAL`; 0.9.x enters at the SV-P1 gates). Start at S1 (bootstrap). Nothing designed this session blocks any stage.

## Carried items (in rough order of when they bite)

1. **RO-13 core ⚑ review** (snapshot archive · `ttr.lock` · stats schema · plan-proto flags — frozen PL contracts §2–§5, with Bora) — **before the SV-P1 publish gates**; schedule alongside SV-P1. Note: the resolver options lean on the snapshot archive (R3-α), so this review now has a second consumer waiting on it.
2. **The calendar / month-grid** — the prior review called it urgent once versions were fixed (RO-24 ✓) and SV-P0 starts: sequence SV-P0..P6 against **Nov 2026 (hard)** and the Aricoma window.
3. **Gaps 6–8 from the plan review:** SV-P5 kantheon-side task lists · Golem-productionization/Hartland alignment — planning passes, not design; generate at their phases' start per the family discipline.
4. **Resolver convergence** = SV-P3 planning's first item: connect `~/Dev/ai-platform` (and kantheon), answer RQ-1..5 in [`server/design/resolver-rewrite.md`](./server/design/resolver-rewrite.md) §7, converge the four forks. **RQ-4 (a `resolve` MCP door?) must be answered before the debut declares the RO-25 surface complete.**
5. **import-schema task lists** — arc is designed (RO-26); lists generate at SV-P4 start (Q-1/2/3/5 in its control room §5 resolve there).
6. **Docs execution discipline** (RO-27): site scaffold + quickstart in SV-P4's **first** week — the plan's own risk line, now with a concrete §5 checklist in [`server/design/docs-dx.md`](./server/design/docs-dx.md).

## Bora's external track (unchanged — [`stewardship-checklist-260710.md`](./stewardship-checklist-260710.md))

TMview clearance (~1 h) → EUTM filing · GitHub `tatrman` account recovery · domain transfer to Collite · the DFP conversation (RO-19's four asks — **ask ③ now also feeds the conformance suite's extended tier AND possibly the resolver parity corpus, RQ-5**).

## Explicitly NOT next

- Re-opening any RO-24..27 decision without new evidence (append-only log; amendments cite what they amend).
- Frontends (1.1.0) work — parked by RO-23's sequencing until the Server arcs call.
- Building the docs site before SV-P4 (the IA is pinned; content waits for the artifacts it documents).

## Session-end duty reminder (standing)

Decisions → control room §7 · registers in the plans · stewardship checklist if touched · project memory · a fresh handover.
