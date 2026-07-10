# Tatrman Ecosystem — documentation map

> Restructured 2026-07-10. This folder is the home of all ecosystem-level design and implementation documentation. **Target versions: Tatrman 1.0.0 · Tatrman Server 1.0.0 · Tatrman Platform 1.0.0; the frontends surfaces (analysis + entry) are the 1.1.0 features of Server and Platform respectively.**

## General (this folder)

- [`ecosystem.md`](./ecosystem.md) — the target-state description (what each part is, how they relate). Start here.
- [`next-steps-260710.md`](./next-steps-260710.md) — the 2026-07-10 strategy-session handover (STRAT decisions, critical path, open-questions register).
- [`stewardship-checklist-260710.md`](./stewardship-checklist-260710.md) — steward/trademark/hosting execution checklist (Collite, EUTM, GitHub, registries).

## Products (one folder each: `design/` + `implementation/`)

| Folder | Product | Target | State |
|---|---|---|---|
| [`tatrman/`](./tatrman/) | The standard & toolchain (languages, formats, contracts, compiler, IDE, Designer). Its design history lives in [`../features/`](../features/) and the user manual in [`../manual/`](../manual/) — this folder holds only the ecosystem-level plan. | 1.0.0 | live/extracted; publish pending |
| [`server/`](./server/) | **Tatrman Server** — the open runtime (Apache-2.0). `design/` = architecture + contracts; `implementation/` = the thin core plan (SV-P0..P6) + SV-P0 task lists. | 1.0.0 (RO-3 bar) | SV-P0 planned, ready to execute |
| [`platform/`](./platform/) | **Tatrman Platform** — the commercial operate tier. `design/` = the full converged design corpus (control room = the decision log, **ground truth for ALL tiers**) + the frozen PL architecture/contracts + [`design/frontends/`](./platform/design/frontends/) (the PF effort, 1.1.0: analysis → Server, entry → Platform per RO-23); `implementation/` = the frozen PL plan + task lists (wake with satellite (c)). | 1.0.0 (Q-6 bar, frozen) | ❄ parked by sequence |

**Decision ground truth:** [`platform/design/00-control-room.md`](./platform/design/00-control-room.md) §7 — the append-only decision log (FRAME → A–K → STRAT-1..9 → RO-1..23), shared by every tier. The PF (frontends) log: [`platform/design/frontends/design/00-control-room.md`](./platform/design/frontends/design/00-control-room.md).
