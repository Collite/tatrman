# Handover — Design-Gaps Session (written 2026-07-10)

> **[superseded 2026-07-10 · STRAT-3]** license is Apache-2.0 across the open surface; MIT mentions below are historical.

> ✅ **COMPLETED 2026-07-10 (same day).** All five items closed: item 5 → **RO-24** · item 4 → **RO-25** + `server/design/mcp-surface.md` (NEW-2 ✓) · item 1 → **RO-26** + `docs/features/import-schema/design/` (diverged AND converged, fork-by-fork) · item 2 → **RO-27** + `server/design/docs-dx.md` · item 3 → `server/design/resolver-rewrite.md` (options only, as scoped; NEW-1 converges at SV-P3 planning). Next: [`next-steps-260710-execution.md`](./next-steps-260710-execution.md). Retained as the session's brief — do not work from it.

> **Purpose.** The plan review (2026-07-10, end of the re-open/OQ-walk/SV-P0-planning day) found that the Server 1.0.0 plan is executable at SV-P0 and policy-thin elsewhere — but **five items are genuinely missing design, not just task lists**, and two of them sit inside the acceptance bar. The next session's focus: **close gaps 1–5 below.**
>
> **Cold-start reading order:** [`README.md`](./README.md) (the docs map + version scheme) → [`server/implementation/plan.md`](./server/implementation/plan.md) (phases SV-P0..P6, the RO-3 bar) → this document. Decision ground truth = [`platform/design/00-control-room.md`](./platform/design/00-control-room.md) §7 (log runs FRAME → A–K → STRAT-1..9 → RO-1..23). Project memory mirrors the state.
>
> **Method:** items 1–3 are *design efforts* (use the diverge-then-converge discipline — mini control room or a compact options doc each); item 4 is a *contract-pinning* pass; item 5 is a *ten-minute decision* — do it first, warm up, record it as RO-24.

---

## Suggested agenda (one session, order matters)

1. **Item 5 — artifact version scheme** (decision, ~minutes)
2. **Item 4 — MCP surface pin + conformance-suite shape** (contract work, the session's core)
3. **Item 1 — `ttr import-schema`** (design divergence; converge if it flows)
4. **Item 2 — docs/DX information architecture** (structure pass)
5. **Item 3 — resolver rewrite** (options catalogue only; converging it may need a kantheon-repo look)

If the session runs short: 5 → 4 → 1 are the priority (bar-blocking or gate-blocking); 2 and 3 can carry to a follow-up.

---

## Item 5 · Artifact version scheme (decision → RO-24)

**Gap:** SV-P1's publish gates say "drop `0.0.1-LOCAL`" without saying **to what**. Rename-before-publish has a sibling invariant: version-before-publish — whatever number goes to Maven Central is permanent.
**Facts:** `ttr-{parser,writer,semantics}` already public at **0.8.4** on GitHub Packages (`Collite/tatrman`); everything else is `0.0.1-LOCAL`/mavenLocal. Product targets are **1.0.0** (README); artifacts ≠ product — they may version independently.
**Options to put to Bora:** (α) all publishables jump to a **0.x line** (e.g. 0.9.x) until the acceptance run, then 1.0.0 together with the debut · (β) **1.0.0-RC.n** immediately (signals intent, costs re-releases) · (γ) independent per-artifact semver, product version = the chart's appVersion only.
**Lean:** α with γ's chart rule (artifacts 0.9.x → 1.0.0 at debut; the umbrella chart's `appVersion` carries the product version).
**Output:** RO-24 in the control room; one line each in `server/design/contracts.md` and `server/implementation/plan.md` SV-P1; note in the S4 task list (publishToMavenLocal version).

## Item 4 · The MCP surface contract + conformance conversation suite (RO-8, NEW-2)

**Gap:** RO-8 declared the MCP surface a named tatrman-owned contract — the ecosystem's consumption contract and the debut's most load-bearing surface — but no document pins it. The conformance conversation suite (the contract's executable test; the bar's Golem clause depends on it) has no format, corpus, or pass-bar. Plan said "pin at SV-P4"; the review's verdict: **pull it earlier — now.**
**Inputs:** live tool surfaces in kantheon (`tools/ttr-query-mcp`/`theseus-mcp`, `ariadne-mcp`, `echo-mcp`; identity = H-2 bearer pass-through, `PipelineContext.auth_roles` lineage); `ecosystem.md` §5 ("the MCP surface is the consumption contract"); RO-19's ask ③ (a DFP-derived synthetic corpus is already on the DFP agenda).
**To pin:** (a) the tool inventory + schemas per MCP server (meta/query/fuzzy/grounding — grounding arrives SV-P3); (b) per-user identity pass-through semantics (what a third-party agent MUST forward; what the door guarantees back — provenance attachment); (c) versioning/compat rule for the tool schemas (they are wire, J-v2 applies); (d) the suite: format lean = **declarative conversation fixtures** (question → expected resolution/grounding calls → expected governed result shape → provenance assertions), engine-agnostic so both Golems and third-party agents run the same file set; pass-bar and corpus-size = the session's call; corpus seed = pilot-derived synthetic (OQ-4 ③) + hand-authored floor so the suite exists without DFP.
**Output:** `server/design/mcp-surface.md` (the contract) + a §row update in `server/design/contracts.md` §1; suite spec either inside it or as `server/design/conformance-suite.md`; a NEW-2 closure note in the plan's OQ register.

## Item 1 · `ttr import-schema` (STRAT-8 arc — in the 1.0.0 bar)

**Gap:** the standard's brownfield front door, promoted to strategic, **zero design**. The db-mirror half is trivial; **the E-R first-cut derivation is the valuable half** ("db model alone is a mirror") and is a real design problem.
**Forks to diverge on (≥3 options each, per the house method):** relation derivation (FK-only · FK + name/type heuristics · FK + data-profiling probes) · entity-vs-table shaping (1:1 · junction-table collapse · header/detail folding) · key/name heuristics for models without FKs (the real brownfield case — MSSQL estates with conventions instead of constraints) · output discipline (one-shot generation vs re-runnable with drift detection — I-2/I-3's PR-shaped proposals line already constrains this) · interactivity (pure CLI vs CLI + review-checklist output the IDE renders).
**Constraints already decided:** MIT/Apache one-shot CLI (I-2); deterministic generation (I-3: same input ⇒ same bytes); output = TTR-M documents proposed through git; conventions doc = frozen PL contracts §12 skeleton.
**Hero:** the pilot's ERP (MS SQL, real conventions, imperfect FKs) — render every option against "stranger runs it on their existing database" (the bar's wording).
**Output:** `docs/features/import-schema/design/` (it is a toolchain feature — the standard's design record lives in `docs/features/`, per `tatrman/design/README.md`), with a compact control room; the arc row in `tatrman/implementation/plan.md` updated from "to design + build" to "designed, ready for its task lists".

## Item 2 · Docs/DX information architecture (in the 1.0.0 bar)

**Gap:** "from public artifacts and public docs alone" is an acceptance clause and the corpus has **zero public-facing docs** and no structure for them. Least-practiced deliverable, biggest bar-weight-to-planning ratio.
**To produce (structure, not prose):** the site's information architecture — lean: four tracks: **Get running** (quickstart: stranger + database → governed answer, the bar's path verbatim) · **Model** (TTR-M language docs; generalize the DFP wiki pattern) · **Connect** (MCP surface for agent builders — feeds on item 4) · **Operate the Server** (chart, config, identity, policy-in-git per RO-7). Plus: the generator decision (MkDocs Material vs Astro Starlight — RO-17 left it open), docs-as-code repo placement (which repo hosts the site source: `tatrman` per docs-as-code + tatrman.org), versioning policy, and the Data Academy skeleton's relationship to the public docs (STRAT-8 / Dolphin).
**Output:** `server/design/docs-dx.md` (IA + decisions + the quickstart's skeleton outline); SV-P4's docs deliverable gains its shape; generator = a recorded decision (RO-2x).

## Item 3 · `ttr-resolver` rewrite (NEW-1 — the undesigned component)

**Gap:** entity resolution is half the two-call thesis; the open rewrite is SPINE work (extraction inventory's own words) and nothing pins its shape.
**Forks:** placement (standalone `ttr-resolver` service · a `ttr-nlp` capability · a library the query door embeds) · resolution pipeline (dictionary+fuzzy over Veles-served vocabulary — the live shape — vs embedding-assisted candidate generation — watch P2: no LLM in the deterministic path; ecosystem.md P2 keeps intelligence out of the spine, but the resolver is *allowed* to be non-LLM-statistical) · vocabulary source (model-declared aliases/fuzzy fields via Veles snapshots — B-contract consumer? — vs direct Veles reads) · language architecture (Czech morphology via `ttr-nlp` (MorphoDiTa) as today, with language plugins later).
**Facts:** ai-platform's resolver (`agents/resolver`, `cz.dfpartner.resolver.v1`) is the live reference with active bugfix churn (NameTag/CNEC); it stays DFP-side until cutover; parity bar = the pilot's conversation corpus; `resolver.v1` proto name reserved.
**Output for THIS session:** an options catalogue only (`server/design/resolver-rewrite.md`) — converging likely wants a look inside `~/Dev/ai-platform/agents/resolver` (folder is connectable) and a kantheon-side view; schedule the converge with SV-P3's planning.

---

## Explicitly NOT this session (so it doesn't drift)

- SV-P0 execution (S1/S2 are ready — separate session).
- Bora's external track: TMview clearance → EUTM filing · GitHub account recovery · domain transfer to Collite · the DFP conversation (OQ-4) — all on [`stewardship-checklist-260710.md`](./stewardship-checklist-260710.md).
- Gaps 6–8 from the review (SV-P5 kantheon-side task lists · the calendar/month-grid · Golem-productionization/Hartland alignment) — carry to the session after; the calendar becomes urgent once item 5 fixes versions and SV-P0 starts.
- The RO-13 core ⚑ review (snapshot/lock/stats/plan-proto flags) — wants Bora + the frozen PL contracts §2–§5 side by side; schedule alongside SV-P1, not here.

## Session-end duties (the score)

Record decisions as RO-24+ in the control room §7 (append-only, cite this handover); update the OQ/NEW register in `server/implementation/plan.md`; refresh `stewardship-checklist` if anything lands there; update project memory; write the next handover.
