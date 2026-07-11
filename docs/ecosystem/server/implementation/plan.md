# Tatrman Server — Implementation Plan (target 1.0.0 · thin by design)

> **Status:** written 2026-07-10 (design re-open session), per RO-2: the core gets a *thin* plan; the frozen PL plan ([`../../platform/implementation/plan.md`](../../platform/implementation/plan.md)) remains the operate-tier reference. This plan sequences the handover's critical path (`../../next-steps-260710.md` §5) into gated phases; mechanics it does not restate live in the **extraction inventory** (`kantheon/docs/architecture/fork/extraction-inventory-260710.md`) and the **naming ledger** ([`../../platform/design/naming-260710.md`](../../platform/design/naming-260710.md)). Companion: [`architecture.md`](../design/architecture.md).
> **Task lists are generated per phase, at phase start** (the PL corpus's own ⚠ proved the point). **SV-P0's set exists (2026-07-10): [`tasks/00-task-management.md`](./tasks/00-task-management.md), stages S1–S6**, with [`contracts.md`](../design/contracts.md) as the core contract cut. Later phases get theirs at their start.

---

## 0. Shape of the effort

This is a **publication effort over proven services**, not a build-from-design effort: the read spine is live at the pilot and extracted in the open lineage. The work is: rename → move → publish → license → fill the two real build gaps (grounding extraction, resolver rewrite) → package → document → repoint the pilot → debut. Two hard external constraints pace it: **ai-platform's engagement milestone (Nov 2026)** and **the Aricoma negotiation** (public v1 before it concludes, if at all possible — the foundation-stone logic).

**The bar** = the RO-3 acceptance statement (architecture §9). **Docs are inside the bar** ("from public artifacts and public docs alone") — the docs/DX workstream is a first-class deliverable, not garnish; the corpus currently has zero public-facing docs.

```
SV-P0 repo+naming ─► SV-P1 publish gates ─► SV-P2 Apache swap ─► SV-P3 grounding+resolver ─► SV-P4 packaging+
 (tatrman-server        (meta → translator/       (+ public-repo        (the two build gaps)      import-schema+Golem+docs
  born; N1 folded)       plan-proto → services)     hygiene)                                          │
                                                                                                      ▼
                              SV-P5 ai-platform repoint (rides gates 1–3; HARD Nov 2026) ─► SV-P6 public v1 debut
                                                                                             (acceptance run)
```

Phases overlap where their gates allow; the diagram is dependency order, not a calendar.

## SV-P0 · Repo & naming (the decided fork, executed)

**Pre-flight:** RO-1 ✅ · OQ-5 ✅ (Collite, RO-15) · OQ-9 ✅ (RO-17; `tatrman` org access recovery pending, non-blocking — interim home = Collite org).

**Deliverables:** `tatrman-server` repo bootstrapped (Apache-2.0, `org.tatrman`, P2/RO-6 dependency-rule CI); spine services moved from kantheon via history-preserving discipline **with the N1 rename folded in** (rename-on-arrival — one migration; ledger §3–§4); olymp chart/Argo names follow in the same change window; kantheon keeps agents + persona space; grep gate green (ledger §5). **Proto reconciliation rides the move (RO-20/RO-21):** relocate `kantheon.common.v1` shared messages → `org.tatrman.common.v1` (server-owned; kantheon imports it); `proteus.v1` → `translate.v1` in `ttr-plan-proto` + delete kantheon's duplicate; adopt `TableHint` into plan.v1 (un-reserve field 3, match ai-platform numbering); converge llm gateway on `llm.v1` = kantheon superset under a functional service name. Housekeeping: delete the `_to_delete/` folders in tatrman + kantheon (handover §8).

**DONE when:** `tatrman-server` builds green with zero persona strings on any wire surface; kantheon builds green without the moved services; the pilot deployment is repointed at the renamed charts (or pinned pre-move, recorded).

## SV-P1 · Publish gates

**Pre-flight:** SV-P0; **OQ-11 proto half verified 2026-07-10 (RO-20)** — plan.v1/transdsl/dfdsl already landed in `ttr-plan-proto` (tatrman), kantheon carries no copies; **blocker found: the artifact ships `org.tatrman.proteus.v1` (translator enums) → rename to `org.tatrman.translate.v1` + delete kantheon's duplicate BEFORE gate 2**; worker.proto delta vs ai-platform still to diff (needs the ai-platform repo connected); RO-13 core ⚑ review (snapshot archive, lock, stats schema, plan protos) done with Bora.

**Deliverables:** version scheme = **RO-24**: everything publishable leaves `0.0.1-LOCAL` for a **0.9.x line**, goes **1.0.0 together at SV-P6's acceptance run**, then versions independently (product version = the umbrella chart's `appVersion`). gate 1 — `ttr-metadata` → public packages (drop `0.0.1-LOCAL` → 0.9.x); gate 2 — ttr-translator + `ttr-plan-proto` transfer to tatrman under final names (TR-3 completion; FQCNs/wire frozen post-OQ-11); gate 3 — spine service artifacts published from `tatrman-server`. Registry (RO-17): **Maven Central = public** (namespace verified via `tatrman.org`, so it rides the domain transfer to Collite); **GitHub Packages = staging/pre-release only** (anonymous Maven downloads fail there) — gate 1 is thereby the *staging* step; images + the chart → GHCR (OCI); signing key = Collite-held (H-6 trust root).

**Status (2026-07-11, S1):** **gate 1 DONE (staging)** — `ttr-metadata(-git)` already on the 0.9.x line (0.9.1, via `kotlin/v0.9.1`); no separate tag needed. **gate 2 DONE (staging)** — `kotlin-translator/v0.9.0` + `python-plan/v0.9.0` published; the jar carries `org/tatrman/translate/v1/` with **0 `proteus`**, the wheel is persona-free (module set identical to 0.8.4; `translator.proto` is Kotlin-only in the plan wheel by design). Consumers repointed (tatrman-server + kantheon → 0.9.0; the SV-P0 `0.0.1-LOCAL` plan-proto/translator interim retired). Public (Central) coordinates = S4. **OBSERVED delta:** the contracts-§7 `query-translator` vendoring exception never materialized — `ttr-translator` was already fully extracted to tatrman, so gate 2 is a publish+repoint, not a code move. **gate 3a (library half) DONE (staging)** — `server-libs/v0.9.0` published the 11-module `org.tatrman:*` set to GH Packages `Collite/tatrman-server` (`capabilities-client` trimmed — no consumer); kantheon retired `mavenLocal()` and builds registry-only (clean-machine proof green, ⚑5 retired). **gate 3b (images + olymp repoint) = S3.**

**DONE when:** ai-platform (or any consumer) can resolve every spine artifact from public coordinates; nothing published carries a persona or a pre-freeze proto name.

## SV-P2 · Apache-2.0 swap + public-repo hygiene

**Pre-flight:** OQ-2 mechanics ✅ (RO-18: SPDX one-liners, minimal NOTICE, © Collite) · OQ-12 ✅ (RO-18: DCO + robot-patch sign-off rule).

**Deliverables:** LICENSE swap + file-header policy + NOTICE across `tatrman` and `tatrman-server`; README/language-doc sweep ("MIT" → superseded-markers in design corpus, plain replacement in living docs); CONTRIBUTING + contribution policy ("robots write through git" extended to external contributors); trademark sanity check executes (OQ-6 — before Aricoma diligence reads the repo).

**DONE when:** both open repos are legally coherent for an external reader: license, headers, NOTICE, contribution terms, no stale MIT claims outside decision-log history.

## SV-P3 · The two build gaps: grounding + resolver

**Deliverables:** **Grounding Fork Phase 6** — chrono/geo/money + `ttr-grounding-mcp` (+ `grounding.v1`) + GroundEntities into the open lineage after DFP validation. **`ttr-resolver` rewrite** — the one undesigned component (SPINE work, not cleanup: entity resolution is half the two-call thesis); kantheon-native rewrite, `resolver.v1`; ai-platform keeps its own until cutover. **Design note:** the resolver options pass is done (2026-07-10 → [`../design/resolver-rewrite.md`](../design/resolver-rewrite.md); leans: standalone service · live pipeline to parity with a named tier seam · snapshot-fed vocabulary · Czech-first with a documented degrade floor); **convergence = SV-P3 planning's first item**, with the ai-platform repo connected (RQ-1..5 there).

**DONE when:** a question through the MCP surface resolves entities + grounds spans using only open-lineage services; Czech morphology parity with the pilot resolver demonstrated on the pilot's conversation corpus.

## SV-P4 · Packaging, front door, reference agent, docs

**Deliverables:** **the single chart** (umbrella, all roster services + Designer viewer per RO-9; OIDC/Keycloak-integrated; values contract documented); **`ttr import-schema` arc** (STRAT-8 — db model + the E-R first-cut derivation, the valuable half); **reference Golem (Kotlin+Koog)** productionized against the MCP contract + **the conformance conversation suite** authored (RO-8's executable test — shared with the future Python reference); **docs/DX workstream — shaped by RO-27 ([`../design/docs-dx.md`](../design/docs-dx.md))**: MkDocs Material site in tatrman (`docs-site/`), four goal-shaped tracks (*Get running · Model · Connect · Operate*), the seven-step quickstart ("stranger with a database → governed answer with visible provenance" — scaffold + quickstart in SV-P4's FIRST week), Model track = manual migration + DFP wiki pattern generalized, Connect track = mcp-surface.md for consumers; latest-only versioning until the 1.0 snapshot; Data Academy separate (links the Model track). **Stretch arcs (named, non-gating):** TTR-M → Power BI/OSI projection (+ lossiness ledger) — **the first leg of the PF analysis plane, which joined the Server per RO-23** (design source: `../../platform/design/frontends/design.md` §4); Python+LangGraph Golem (unblocks on OQ-3).

**DONE when:** the RO-3 bar's mechanics all exist end-to-end on a scratch cluster deployed by someone outside the build (dry acceptance run, pre-debut).

## SV-P5 · ai-platform November repoint (parallel track; HARD deadline Nov 2026)

Per the extraction inventory: consume published artifacts as gates 1–3 open; `org.tatrman.llmgateway.v1` → `llm.v1`; delete vestigial ttr-parser/writer dirs; end-state = only DFP-specific content + v0-legacy remains. Plus **the DFP conversation (OQ-4)** — grounding/resolver contribution, founding-adopter framing, demo evidence-pack approval; everything downstream eases if yes.

**DONE when:** ai-platform builds against public coordinates only; the engagement obligation is dischargeable as "DFP = deployment instance #0 of the open standard."

## SV-P6 · Public v1 — the debut

**Pre-flight:** SV-P1..P4 done; OQ-3/5/6/9/12 all closed (they gate *publishing*, listed per phase above).

**Deliverables:** repos public; artifacts on the public registry; docs site live (`tatrman.org` held ✅); **the acceptance run executed by an outsider and recorded** as `docs/ecosystem/server/implementation/acceptance-1.0.0.md`; announcement material (the marketing lines from handover §8 are the seed). Target: **before the Aricoma negotiation concludes**, if at all possible; Q1 2027 = first channel pilots (Aricoma/Dolphin), PBI projection preferably demo-able by then (stretch).

**DONE when:** RO-3 holds verbatim, executed by someone who is not us.

## Server 1.1.0 — the frontends analysis plane (named, not scheduled — RO-23)

The **PF analysis plane** (frozen design: [`../../platform/design/frontends/design.md`](../../platform/design/frontends/design.md) §4/§6): the md→Cube/OSI **semantic projection generator** (tatrman-owned; lossiness ledger; first leg = SV-P4's stretch clause) → the **semantic layer service** (single policy carrier for external BI; Server-tier policies from the open validator store per RO-7, Perun feeds the same generator at operate wake) → the **Designer analysis viewer** (lineage-drill legs light up as their tiers wake) → the **B-author panel** (open, rides satellite (a)). The **entry plane** (`tatrman-entry` + journal/reservation/check-in/entry-record contracts) = Tatrman Platform tier — wakes on the first planning workload and rides the operate tier's program door (PF DQ-1/2).

---

## Open questions (live register = handover §7; server-plan view)

| # | Question | Gates |
|---|---|---|
| ~~OQ-1~~ | ~~spine repo topology~~ **RESOLVED 2026-07-10 → RO-1 (tatrman-server)** | — |
| ~~OQ-2~~ | ~~swap mechanics~~ **DECIDED 2026-07-10 → RO-18 (SPDX one-liners, minimal NOTICE, © Collite)**; remaining = execution | SV-P2 |
| ~~OQ-3~~ | ~~Python Golem provenance~~ **RESOLVED 2026-07-10 → RO-19: textbook rewrite is the default; DFP contribution = accelerant** | stretch clause |
| OQ-4 | the DFP conversation — **approach ratified (RO-19: four escalating asks, single decision-maker, + derived-corpus ask)**; the conversation itself pending | SV-P5 eases everywhere |
| ~~OQ-5~~ | ~~steward entity~~ **RESOLVED 2026-07-10 → RO-15: Collite** (domains transfer to it; five hats) | — |
| OQ-6 | trademark — **shaped (RO-16: word mark, EUTM direct, applicant Collite, 9+42, 41 open)**; clearance check pending (Bora, ~1 h) | file before diligence |
| ~~OQ-7~~ | ~~Server-v1 bar~~ **RESOLVED 2026-07-10 → RO-3 (trimmed)** | — |
| OQ-8 | contracts ⚑ split **DISPOSED → RO-13**; the core review itself still to run | SV-P1 |
| ~~OQ-9~~ | ~~public hosting~~ **RESOLVED 2026-07-10 → RO-17** (org `tatrman` — account is Bora's, recovery pending; Central public / GH Packages staging; GHCR; docs-as-code on tatrman.org) | recovery = pre-debut |
| ~~OQ-11~~ | **CLOSED (RO-20/RO-21)**: plan protos landed clean; `proteus.v1`→`translate.v1` rename before gate 2; worker delta = kantheon additive superset; **kantheon-common import finding → `org.tatrman.common.v1` relocation (N1 step)**; **TableHint adopted into plan.v1 pre-freeze**; llm.v1 = kantheon superset, functional service name | SV-P0/P1 |
| ~~OQ-12~~ | ~~DCO vs CLA~~ **RESOLVED 2026-07-10 → RO-18: DCO** (+ human sign-off carries DCO for agent patches) | SV-P2 |
| NEW-1 | `ttr-resolver` rewrite shape — **options catalogued 2026-07-10 → [`../design/resolver-rewrite.md`](../design/resolver-rewrite.md)** (placement/pipeline/vocabulary/language forks + leans; RQ-1..5); **convergence rides SV-P3 planning with the ai-platform + kantheon look** | SV-P3 pre-flight |
| ~~NEW-2~~ | ~~conformance conversation suite: format, corpus, pass bar~~ **CLOSED 2026-07-10 → RO-25 (`../design/mcp-surface.md` §5): declarative YAML conversation fixtures; core tier (hand-authored, ~25–40) passes 100% = conformance, DFP-derived extended tier scored non-gating; suite lives in tatrman; core authoring = SV-P4** | SV-P4 authoring |
| NEW-3 | Designer viewer: Veles data-source adapter status audit (exists vs PL-P1-planned) | SV-P4 |

## Risks & watch items

- **Scope magnetism from the frozen tier:** the PL corpus is rich and adjacent — anything not needed for RO-3 or the November repoint waits for its satellite. The satellite triggers are the only doors back in.
- **Rename-before-publish is the invariant** (naming ledger §5): anything published never renames — OQ-11 and the N1 grep gate sit *before* gate 2 for exactly this reason.
- **Docs are the least-practiced deliverable** on the team and inside the bar — start the quickstart in SV-P4's first week, not its last.
- **The resolver rewrite is the only true unknown** — time-box a design pass (NEW-1) before committing the SV-P3 schedule.
- **Aricoma timing is aspirational, November is not** — if they collide, SV-P5 wins; the debut can slip past the negotiation, the engagement milestone cannot.
