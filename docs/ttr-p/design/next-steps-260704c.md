# TTR-P — Next Steps (pick-up point, written 2026-07-04, after CONSOLIDATION)

> **The design effort is complete.** All workstreams 🟢 (A · B · G · C0–C4 · D · E · F-lite · H); MD-sugar (D-h) deferred to v1.x/v2; sweep S1–S25 ratified. This file supersedes [`next-steps-260704b.md`](./next-steps-260704b.md) and is the last of the design-phase series.

## The standing artefacts (read these, not the catalogues)

1. [`../architecture/architecture.md`](../architecture/architecture.md) — the consolidated v1 architecture.
2. [`../architecture/contracts.md`](../architecture/contracts.md) — file formats, `[ttrp]` manifest, `ttrp/*` LSP methods, bundle, emit payloads, diagnostics, `ttrp-conform`.
3. [`../implementation/v1/plan.md`](../implementation/v1/plan.md) — 8 phases (P0 repo prep → P7 TTR-B + assist); A4 exit criteria met at end of P5.

The design catalogues (01–12) + [`00-control-room.md`](./00-control-room.md) decision log remain the rationale record; the artefacts above are the working truth.

## What's next (in order)

1. **Bora reviews the three artefacts** — especially plan phasing (P4 LSP before P5 Designer; P6/P7 parallelizable after P4) and the S-sweep landings in contracts.
2. **Per-stage task lists** for Phase 0/1 (`tasks-p0-s0.1-scaffold.md`, `tasks-p1-s1.1-grammar.md`, …) — TDD-shaped, 6–8 tasks each, per the planning skill conventions.
3. **Kantheon-side: plan the Proteus-extraction arc** (delivers `org.tatrman:ttr-translator`; finalizes plan.v1 vendoring per S25) — **gates Phase 3.**
4. Fork-ops residue (trivial, anytime): old-modeler freeze README; `~/Dev/tatrman` → `tatrman-poc`.

## Key mental model (one paragraph)

**Tatrman** = TTR-M + TTR-P, one repo. TTR-P v1: one graph; canonical `.ttrp` (γ-hybrid, `->`, SSA); three fragment dialects under one regime (TTR-SQL / TTR-pandas / TTR-B — full decomposition, doc scope, err-only, untouchable interiors, own grammars); graphical two-level skinned canvas over `.ttrl` (ζ keys); assist = `ttrp/authoringContext` + `ttrp/validate`, LLM at the host, agents author canonical text. Compile offline against a `schema world` (model repo) under `[ttrp]` defaults; emit CTE-per-node PG SQL / straight-line Polars + prelude via `ttr-translator`; world-driven PlanNode for Kantheon targets. Execute `<program>.bundle/` wave-parallel bash, Arrow staging, `TTR_CONN_*`, fail-fast; verify with `ttrp-conform` (Q9's seven points). CLI = `ttrp`. P2 everywhere — no miracles, and now, no open forks.
