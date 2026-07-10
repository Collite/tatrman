# Ecosystem Strategy Session — Handoff & Uncaptured Decisions (2026-07-10)

> **Purpose.** The 2026-07-10 strategy session produced committed artifacts (listed in §1) —
> but several ratified decisions, superseded design items, new workstreams, and open questions
> exist only in the session conversation and memory. This document captures ALL of them so the
> next sessions (PL amendment batch, workstream updates in tatrman + kantheon, plan updates,
> refactor kickoff) can proceed without re-deriving anything. Companion: `ecosystem.md` (the
> target-state description, same folder).

---

## 1. Where everything landed (pointer inventory)

**Committed:**
- `tatrman/docs/ecosystem/ecosystem.md` (348e77d) — the standalone target-state description; thesis ratified by Bora.
- `tatrman/docs/ecosystem/platform/design/naming-260710.md` + J-v2 amendment in `00-control-room.md` §7 (a6aefaa) — naming ledger, N0–N4 rename sweep, personas-never-on-the-wire rule.
- `kantheon/docs/architecture/fork/extraction-inventory-260710.md` (355c68d) — ai-platform ↔ kantheon/tatrman audit + November repoint plan.

**Deliberately NOT in repos (pitch material; Bora files them):** `aricoma-memo.md` ·
`aricoma-session-plan.md` v2 (90′ session incl. the maker moment, readiness tiers A/B/C) ·
`tatrman-aricoma-deck.pptx` v2 (15 slides, speaker notes). English finals; Czech = Bora's pass.

**Session memory:** project memory `ecosystem-strategy-review.md` mirrors this document.

---

## 2. Ratified this session, NOT yet in any decision log → the PL amendment batch

These supersede or amend recorded PL decisions. They need a formal, append-only amendment
entry set in `docs/ecosystem/platform/design/00-control-room.md` §7 (same pattern as J-v2) plus a
short superseded-markers pass over `design.md` / `detailed-design.md`. Proposed IDs:

1. **[STRAT-1 · core vs satellites]** The ecosystem splits into the CORE ("prepare your data
   for AI consumption" — the piloted read spine: TTR-M + metadata serving + translate +
   validate/RLS + dispatch + workers + MCP surface + resolver + grounding + reference agents)
   and three PARKED SATELLITES: (a) TTR-P processing family + graphical write designer +
   optimizer arcs, (b) entry/budgeting (the PF frontends effort), (c) **the operate tier**
   (ttr-run/Radegast, ttr-schedule/Zorya, Charon, envelopes, bundles-as-deployed-units,
   harvest scheduling, event spine). Satellites are parked **by sequence, not by doubt**;
   each re-opens on named evidence (first program workload / first planning workload / first
   operated estate).
2. **[STRAT-2 · license rule — supersedes A-1's edition boundary]** "Compile vs operate" is
   replaced by **"interoperate vs administrate"**: everything an adopter needs to prove the
   promise end-to-end is OPEN — this **moves the whole read spine to the open side**,
   including the query door (ttr-query), the validator **with RLS** (ttr-validate), the
   dispatcher, the engine workers (mssql/postgres/polars), Veles (metadata serving), the MCP
   tools, resolver, grounding, and the reference agents. Commercial remains: the operate tier,
   enterprise policy administration (Perun-the-organization; the open side keeps the
   enforcement point), continuous harvest/export connectors, Designer ops/deploy extensions,
   advanced/vertical agents. Rationale recorded in session: "policy-honest by construction"
   is the headline claim — enforcement cannot be paywalled without killing it. Rule of thumb
   ratified by Bora: **"there must be a meaningful OSS core."**
3. **[STRAT-3 · license — amends FI-2]** MIT → **Apache-2.0** for everything open (patent
   grant; costs nothing). Mechanics = a tatrman work item (§4): LICENSE swap, headers, NOTICE,
   coordinate docs. `cz.tatrman:*` = commercial tier unchanged; **the `org.tatrman` group id
   now means Apache-2.0**, and its scope grows per STRAT-2.
4. **[STRAT-4 · product naming — extends J-v2]** The open spine ships as **"Tatrman Server"**
   (one product name, one chart, many services). Brand architecture fixed at four names:
   Tatrman · Tatrman Server · Tatrman Platform (parked) · Kantheon. "Editions" vocabulary
   (Tatrman / Tatrman Platform) extended accordingly.
5. **[STRAT-5 · Q-6 superseded as the near-term bar]** The ratified PL v1 acceptance statement
   (envelope deploy, nightly runs, Airflow delegation, Kestra conformance) is **operate-tier
   scope — parked with satellite (c)**. It remains the acceptance bar for Tatrman Platform
   when that tier wakes. The near-term bar is a NEW **"Tatrman Server v1" acceptance
   statement** — strawman in §6, needs Bora's ratification.
6. **[STRAT-6 · strangler resequenced]** ①② (metadata/translator extraction; Veles v1) stand.
   ③ (hall transplant) is **done in kantheon** (per the extraction audit) and is now
   open-spine work, not commercial-platform work. ④ (Charon) and ⑦ (Zorya/scheduler) park
   with the operate tier. ⑤ splits: Argos/validate = spine (open, done); Perun-the-org =
   operate (parked). ⑥ (kantheon query-door adoption) remains, inside the new critical path.
7. **[STRAT-7 · two reference agents]** Golem ships as TWO open reference implementations:
   Python + LangGraph (pedagogical reference, aggressively documented) and Kotlin + Koog
   (Kantheon product lineage). Purpose: prove the MCP surface is the contract. (Provenance of
   the Python one = OQ-3.)
8. **[STRAT-8 · promotions]** Two formerly minor items become strategic: **`ttr import-schema`**
   (the standard's brownfield front door — promoted from I-2 one-shot-CLI footnote to a named
   arc) and **the TTR-M → Power BI / OSI semantic projection** (promoted from parked IQ-1 to
   the channel bridge product — "modeling pays twice"). The PF frontends effort's B-workstream
   δ-composite lean gains weight from this when that effort reopens.
9. **[STRAT-9 · November refactor = the strangler, merged]** The DFP contract obligation
   ("everything is DFP's, but we might use OSS"; engagement ends **Nov 2026**) is discharged by
   **inverting extraction**: ai-platform is refactored to CONSUME published `org.tatrman`
   artifacts (never by moving code out of it). DFP = deployment instance #0. Execution plan =
   the extraction inventory (committed, kantheon).

**Also affected, one-line notes:** D-3 ownership rule unchanged in form, but "toolchain-touched
⇒ tatrman-owned" now coexists with spine-services being open (ownership ≠ license tier — keep
both columns). B contract (snapshot/lock/stats) is CORE — its ⚑ contract flags stay live (OQ-8).
H-8/E-3-β standalone-security stances unchanged. P1/P2/P3 standing rules unchanged and inherited
by the core. The PF (frontends) control room needs no amendment — satellite (b) status only.

---

## 3. The repo topology fork — FIRST architecting decision (blocks publish gates)

The redraw creates a question the PL design never had to answer: **where does the open spine
live publicly?** Today it sits inside the kantheon repo (post-fork), mixed with the agents;
the PL design's `tatrman-platform` repo was conceived for the *commercial* platform.
Options to take into the next architecture session:

- **(a) New `tatrman-server` repo** (Apache-2.0, `org.tatrman`): spine services move there via
  the same history-preserving discipline; kantheon keeps agents + the commercial/experimental
  surface; `tatrman-platform` remains reserved for the operate tier later. Cleanest license
  boundary (repo = license, per D-2); one more repo to run.
- **(b) Open the kantheon repo wholesale** and rename: fastest, but drags agents/product
  experiments into the public standard and muddies the persona-space/commercial split.
- **(c) Spine into the tatrman monorepo**: one public repo, but mixes toolchain (Gradle+TS)
  with services and breaks the existing repo grammar.

Session lean (not ratified): **(a)**, with the N1 naming sweep executed as part of the move
(rename-on-arrival, same as the original fork's DQ-2 discipline — one migration, not two).
Decide before the publish gates; the GitHub org/hosting question (OQ-9) rides on it.

---

## 4. Workstream updates by repo

### tatrman (toolchain + standard)
1. **Apache-2.0 swap** (STRAT-3): LICENSE, file headers policy, NOTICE, README/language docs
   sweep ("MIT" appears throughout the design corpus — sweep with superseded-markers, don't
   rewrite history in the decision logs).
2. **PL amendment batch** (§2) into the platform control room + `design.md` markers.
3. **`ttr-plan-proto` landing** (TR-3 completion): receive plan.v1/transdsl.v1/dfdsl.v1
   ownership; FQCNs/wire frozen (verify against OQ-11 first).
4. **`ttr import-schema` arc** (STRAT-8): design + build; the E-R first-cut derivation is the
   valuable half (db model alone is a mirror).
5. **TTR-M → Power BI / OSI projection arc** (STRAT-8): revive IQ-1 with OSI as co-target;
   lossiness ledger (IQ-4 sibling); Dolphin co-investment candidate.
6. **Docs/DX/education workstream** (NEW — the corpus has zero; a standard needs a school):
   public-facing language docs, quickstart, the wiki pattern proven at DFP generalized;
   Data Academy course skeleton with Dolphin.
7. **Tatrman Server packaging** (single chart; depends on §3 decision).

### kantheon (spine until §3 resolves + agents)
1. **N1 naming sweep** (rename plan N0–N4; N0 done) — **before** publish gates; fold into the
   §3 repo move if (a) is chosen.
2. **Publish gates**: ttr-metadata GitHub Packages (drop `0.0.1-LOCAL`) · execute the
   ttr-translator + ttr-plan-proto extraction (73-file query-translator).
3. **Grounding "Fork Phase 6"**: chrono/geo/money + grounding-mcp + GroundEntities into the
   open lineage after DFP validation.
4. **Resolver rewrite → `ttr-resolver`** (SPINE work, not cleanup — entity resolution is half
   of the two-call thesis; kantheon-native rewrite, ai-platform keeps its own until cutover).
5. **Proto hygiene**: llmgateway/prometheus collision → `llm.v1` (both sides); worker.proto
   delta diff; plan-proto fate verification (OQ-11).
6. **Golem Kotlin+Koog reference** continues; conformance conversation suite shared with the
   Python reference (STRAT-7).
7. **Hartland demo build** (separate track; paces the Aricoma session tier): hartland repo
   bootstrap, cluster H1–H5, R0 freeze incl. **maker-moment deltas** (gap word + edit + typo
   frozen; gap-question into Shem counter_examples; pre-staged-branch fallback), R2 drills,
   R4 bar. Sat-G outranks Sat-D in the Aricoma cut ladder. Final names on-screen at R0.

### ai-platform (DFP engagement — November track)
Per the committed extraction inventory: consume published artifacts as gates open; repoint
`org.tatrman.llmgateway.v1` → `llm.v1`; delete vestigial ttr-parser/writer dirs; end-state =
only DFP-specific content + v0-legacy remains. **Plus:** the DFP conversation (OQ-4).

---

## 5. The new critical path (supersedes PL plan sequencing for the core)

> Decision needed: amend the PL plan in place vs. write a lightweight **core-v1 plan**
> (recommendation: the latter — PL plan + task lists stay intact as the operate-tier reference
> for when satellite (c) wakes; the core gets its own thin plan referencing the extraction
> inventory + this document).

1. Repo topology decision (§3) + steward/org decisions (OQ-5, OQ-9).
2. N0–N4 naming sweep (N0 ✅ done 2026-07-10).
3. Publish gates: ttr-metadata → ttr-translator/ttr-plan-proto → spine service artifacts.
4. Apache-2.0 swap + public-repo hygiene (headers, CONTRIBUTING, DCO/CLA per OQ-12).
5. Grounding Phase 6 + resolver rewrite.
6. Tatrman Server packaging (one chart) + import-schema arc + reference Golems.
7. ai-platform November repoint (rides gates 3–5; hard deadline Nov 2026).
8. **Public v1** — the debut (Server + reference Golem + docs) — **before the Aricoma
   negotiation concludes if at all possible** (the foundation-stone logic).
9. Q1 2027: first channel pilots (Aricoma/Dolphin); PBI projection preferably demo-able.

---

## 6. Strawman: the "Tatrman Server v1" acceptance statement (replaces Q-6 for the core — ratify or edit)

> *"Tatrman Server v1 is done when, on a fresh single-chart deployment by someone who is not
> us, from public artifacts and public docs alone: a TTR-M model bootstrapped by
> `ttr import-schema` from an existing database and hand-refined in the IDE is served by
> Veles; a question asked through the MCP surface by any MCP agent — the reference Golem or
> a third-party one — returns a governed answer with row-level security applied in the plan
> and full provenance attached; the maker loop closes (edit model → commit → refresh →
> the agent understands); the same model projects to a Power BI semantic model; and both
> reference Golems pass the same conformance conversation suite. All Apache-2.0."*

---

## 7. Open questions register (owner · resolve by)

| # | Question | Owner | Resolve by |
|---|---|---|---|
| OQ-1 | Spine repo topology (§3 a/b/c) + migration mechanics | Bora + next arch session | before publish gates |
| OQ-2 | Apache-2.0 formal ratification + swap mechanics (headers, NOTICE) | Bora | with OQ-1 |
| OQ-3 | Python reference Golem provenance: DFP contribution vs clean re-home (it is DFP work product today) | Bora + DFP | before public v1 |
| OQ-4 | The DFP conversation: contribute grounding/resolver? founding-adopter framing? demo evidence-pack approval (analyst-IDE shot preferred)? | Bora | ASAP — everything downstream eases if yes |
| OQ-5 | Steward entity (Collite?) — owns trademark, publishes artifacts, signs CLAs | Bora | before trademark filing + public v1 |
| OQ-6 | Trademark: TMview/ÚPV/EUIPO check (Bora, self-assigned) → CZ vs EUTM filing (lean: EUTM, classes 9/42) | Bora | before public v1 / Aricoma diligence |
| OQ-7 | Ratify/edit the Server-v1 acceptance strawman (§6) | Bora | at core-v1 planning |
| OQ-8 | PL contracts.md v1.1 ⚑ flags — split review: core-relevant (snapshot archive, ttr.lock, stats schema, plan protos) now; operate-tier flags (envelope, event spine, door API) parked | Bora + planning session | core flags before publish gates |
| OQ-9 | Public hosting: GitHub org (`tatrman`?), package registry (Maven Central vs GitHub Packages for public), docs site (tatrman.org held ✅) | Bora | with OQ-1 |
| OQ-10 | Pitch polish: memo §8 reword (text ready in session plan) · deck slide 8 pilot naming · slide 15 contact | Bora | before sending |
| OQ-11 | Kantheon proto verification: plan.v1/transdsl/dfdsl current location (inlined vs vendored?) + worker.proto 2.3 KB delta — direct diff needed before the ttr-plan-proto transfer freezes anything | next kantheon session | before gate 3 |
| OQ-12 | Contribution policy for public repos: DCO vs CLA; "robots write through git" extended to external contributors | Bora | before public v1 |

---

## 8. Housekeeping (small, real)

- `_to_delete/` folders in **tatrman** and **kantheon** repos hold stale git lock files
  (index.lock/HEAD.lock/maintenance.lock/tmp_obj — remote-bridge artifact); delete both folders.
- `tatrman` commits from this session (a6aefaa, 348e77d) sit on the `frontend-design` branch
  alongside unrelated frontends WIP, per Bora's instruction; fold to master at will.
- The deck generator lives in the session workspace (`deck/gen.js`) — ask for it if slide
  edits are wanted later; otherwise regenerate from the pptx directly.
- Marketing language worth keeping (currently only in pitch artifacts): *"Your data,
  answering — governed"* · *"LLMs bring the intuition; the platform brings the reason"* ·
  the **zombie-projects revival** framing · *"modeling pays twice"* · the beachhead statement
  (Czech/CEE, MS-stack, on-prem, sovereignty-minded).
