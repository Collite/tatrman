# Tatrman (the standard & toolchain) — Ecosystem Plan (target 1.0.0)

> **Status:** written 2026-07-10 (restructure session). Thin by design: the tatrman repo's own feature work plans itself in [`docs/features/`](../../../features/); this document lists only the **ecosystem-level arcs the standard owes the 1.0.0 targets**, sequenced by the Server plan ([`../../server/implementation/plan.md`](../../server/implementation/plan.md)) whose phases they ride. Decision ground truth = [`../../platform/design/00-control-room.md`](../../platform/design/00-control-room.md) §7.

## 1.0.0 — what the standard delivers for Tatrman Server 1.0.0

| Arc | Content | Rides | Status |
|---|---|---|---|
| `ttr-plan-proto` amendments | `proteus.v1` → `translate.v1` rename · **TableHint adoption** (RO-20/21; contracts: [`../../server/design/contracts.md`](../../server/design/contracts.md) §3–§4) | SV-P0 · S2 (task list: [`../../server/implementation/tasks/tasks-sv-p0-s2-protos.md`](../../server/implementation/tasks/tasks-sv-p0-s2-protos.md) — tracked with the Server SV-P0 set) | planned |
| Publish gates, tatrman half | `ttr-metadata` off `0.0.1-LOCAL` · `ttr-translator` + `ttr-plan-proto` final publication (TR-3 completion) | SV-P1 gates 1–2 | pending |
| Apache-2.0 swap | LICENSE, SPDX headers, NOTICE, CONTRIBUTING + DCO (RO-18) across the tatrman repo | SV-P2 | decided, pending |
| Seam-schema ratification | snapshot archive · `ttr.lock` · stats · compile record (frozen text: [`../../platform/design/contracts.md`](../../platform/design/contracts.md) §2–§5) — the RO-13 **core ⚑ review** | before SV-P1 gates | pending review |
| **`ttr import-schema`** | the brownfield front door (STRAT-8): db physical model + the E-R first-cut derivation — **inside the 1.0.0 acceptance bar**. **Designed 2026-07-10 (RO-26)** → [`docs/features/import-schema/design/`](../../../features/import-schema/design/00-control-room.md): FK+cascade+probes w/ evidence grades · junction collapse + folds-via-checklist · conventions file + shipped profiles · layered ownership (db machine-owned / er scaffold-born-once) · review-checklist artifact | SV-P4 | **designed, ready for its task lists** |
| Docs/DX workstream | public language docs, quickstart, wiki pattern generalized — **inside the bar** ("from public docs alone"). **Shaped 2026-07-10 (RO-27)** → [`docs-dx.md`](../../server/design/docs-dx.md): MkDocs Material site in this repo (`docs-site/`), four goal-shaped tracks, seven-step quickstart | SV-P4 | **designed, to build** |

## 1.1.0 — the frontends demand on the standard

From the PF effort ([`../../platform/design/frontends/design.md`](../../platform/design/frontends/design.md) §7, tiered by RO-23):

- **md → Cube/OSI semantic projection generator** + lossiness ledger (tatrman-owned, deterministic; the Server 1.1.0 analysis plane's first leg — also the PBI channel bridge, STRAT-8).
- **TTR-M md package:** E-i spread/driver defaults · D-v version-dimension metadata · cubelet contract · `default_hierarchy` (CQ-19 collision-check rider).
- **`ttrl` schema additions:** form kind · saved-analysis-view kind · authored-canon vs generated-view-state lifecycle (via GQ-1; CQ-20 rider).
- **The TTR-P spread construct** + fragment generator (satellite (a) grammar work — enters when (a) wakes or when Platform 1.1.0 pulls it, whichever first).
- **`security`-block sugar → open validator config** (the RO-7 follow-up arc; open policy authoring in the model).

## Task lists

Generated per arc at its start (the family discipline). SV-P0's S2 is the only tatrman task list that exists today; it stays with the Server SV-P0 set for phase cohesion. Later tatrman-side lists land in `tasks/` beside this plan.
