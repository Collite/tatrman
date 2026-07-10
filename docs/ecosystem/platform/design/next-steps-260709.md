# Tatrman Platform design — FINAL HANDOFF (2026-07-09)

> Cold-start file for the **final-deliverables session**. Supersedes `next-steps-260708.md`. Read [`00-control-room.md`](./00-control-room.md) first (the decision log is ground truth), then this.

## Where the effort stands: FULLY CONVERGED

**A–K all 🟢.** All eight load-bearing forks resolved; the independent review (`review-260708.md`) done, ratified, and incorporated; C/E/F de-dirtied; consolidation sweep S1–S8 ratified; J named everything; **Q-6 ratified** (verbatim in control room §9 — it is the v1 "done" bar and must appear in `design.md`).

**The one-breath summary (updated from 260708):** a **mode-blind MIT compiler** behind a source SPI (`ttr.lock` pins canon incl. plugins; stats float per-object, recorded per-compile; hard parity; fetch-then-compile) · a **new `tatrman-platform` repo** (Gradle, `cz.tatrman:*`, license = repo boundary) hosting **Veles** (metadata server: B contract, Designer serving, **static compiler-derived column lineage**, export connectors, harvest scheduling) · **two doors, one hall** (program door behind **Radegast** the executor + **Zorya** triggers; query door = slimmed **Theseus**; hall = Argos+validator-SPI → Kyklop → Cyclopes workers; **Charon** moves) · **Perun** the PDP (Rego bundles bound to metadata qnames + the **TTR `security`-block sugar** that generates policy; PEPs evaluate locally; run records cite bundle hashes) · secrets = store-SPI + dispatch-injection, never at rest · deployment = **envelope wrapping the verbatim bundle** citing `{lock hash, compile record}` · **K composition**: project worlds explicitly extend the platform world (own git repo; contradiction = compile error) · Designer = MIT frontend, **reader-first v1** (incl. TTR-P program graphs), writes-through-git designed for phase 2 · external metadata = **"proposals in, projections out"**, OpenMetadata anchor · v1 executor targets **{bash, Airflow 3, Kestra}** · editions **Tatrman / Tatrman Platform**, chain `tatrman → platform → kantheon`.

## The task: write the two final deliverables

Per the design-skill spec:

1. **`design.md`** — compact; audience = **Claude in the next `/planning` session**. Must contain everything planning needs; reference option docs/decision log rather than restating. Checklist:
   - Q-6 acceptance statement verbatim (the scope bar).
   - The v1 service map with J's names + each service's one-line contract (from C/F/H resolutions).
   - **Contract inventory with owners** (D-3 rule): plan protos, world/manifest schemas, snapshot/lock/stats formats, **E-5 manifest schema (+ lineage section)**, deployment envelope, run/lineage event contract, door frontend contract `{start, poll/subscribe, cancel}`, and the **five SPIs** (emit E-1 · validator C-5-i · secret-store H-5 · connector E-4/I-2 · Designer Extensions D-2) + the `security` block grammar. Mark MIT (`org.tatrman`) vs platform (`cz.tatrman`) per D-3/J-5.
   - Strangler sequencing ①–⑦ (D-5) with what H/G/I added: Perun+whois at ⑤, Zorya/scheduler at ⑦, reader Designer rides ②, harvest connectors post-②, OpenMetadata pair with Veles's export organ.
   - Standing rules to cite: P1–P3 · B-4 seam legality · D-3 ownership · never-at-rest + no-secret-API (H-5) · deny-overrides (H-1) · determinism obligations (E-1 plugins, I-3 connectors) · "robots write through git" (G-1-γ/I-3).
   - Planning-stage work items (collected riders): FQ-2 run ids · FQ-4 executor manifest artifact · EQ-1 SPI surface · EQ-2 signing+determinism kit · IQ-2 qname mapping · IQ-4 export lossiness · GQ-4 session quotas · retention/window sizes (FQ-6/FQ-7) · HQ-4/HQ-5 · S7 event-spine sizing.
   - Parked items with revisit conditions (§8 of the control room) + the S1 amendment batch (feeds TTR-P/TTR-M side) + post-design kantheon arcs.
2. **`detailed-design.md`** — exhaustive, prose, for a human who wasn't in the sessions: narrative of the two-mode architecture, the hero's three lives end-to-end (each workstream doc has a hero rendering to harvest), rationale + rejected alternatives for the big calls.

**Sources, read order:** `00-control-room.md` (dashboard + decision log + §9) → `01-design-space-map.md` (incl. §K, §J resolutions) → `02`–`09` option docs (each has a Convergence status section summarizing its resolutions) → `review-260708.md` (context for the amendments). TTR-P corpus (`../../../features/ttr-p/design/`) only as needed for citations.

## After the deliverables

- Run `/planning` on `design.md` → architecture, contracts, phased plan, task lists.
- Outside this repo, queued: the **S1 TTR-P/TTR-M amendment batch** (params, error-flow, executor manifest, E-5 graduation, T6 type-manifest ownership, K's extends surface, security-block grammar — record per the `.ttrl` discipline + contracts changelog) · kantheon arcs (⑥ query-door adoption, doc sweep) · S6 CLAUDE.md line in this repo.

## Process note

A short **second review pass** over the final `design.md` (consistency against the decision log) is cheap insurance — the first review paid for itself.
