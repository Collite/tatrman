# `ttr import-schema` — Control Room (compact)

> The design record for the standard's **brownfield front door** (STRAT-8 arc, inside the RO-3 1.0.0 bar). Opened 2026-07-10 (ecosystem design-gaps session, item 1 — see `../../../ecosystem/next-steps-260710-design-gaps.md`). Single-workstream effort: five forks, one options doc ([`01-options.md`](./01-options.md)). Decision ground truth for ecosystem-level entries stays the platform control room §7; **this doc's §4 log holds the feature-level decisions.**
> Method: the house diverge-then-converge discipline (reference: `../../ttr-p/design/00-control-room.md` §0).

---

## 1. The question

Bootstrap a TTR-M **physical model** (`db` — the mirror half, trivial) **and a first-cut E-R model** (`er` — the valuable half, the real design problem) from an existing database, such that *a stranger can run it against their estate* (the bar's wording) and get something worth keeping — because "the db model alone is a mirror," and modeling only pays when the E-R layer carries meaning.

**Hero:** the pilot's ERP — MS SQL, real naming conventions, imperfect/partial FKs, Czech identifiers. Every option is rendered against "stranger + this database → useful first cut."

## 2. Grounding inputs (constraints already decided — not this effort's to reopen)

- **GI-1 (I-2):** the one-shot introspection CLI is MIT/Apache — the open tier's tool; *continuous* harvest is the platform tier.
- **GI-2 (I-3):** deterministic generation — same input ⇒ same bytes. (Anything probabilistic must be quarantined outside the deterministic path or made deterministic by construction.)
- **GI-3 (I-3):** output = TTR-M documents **proposed through git** (PR-shaped proposals; robots write through git).
- **GI-4:** the conventions document skeleton = frozen PL contracts §12 (the conventions doc is an *input artifact*, not folklore).
- **GI-5 (RO-3):** this arc is inside the 1.0.0 acceptance bar: "stranger runs it on their existing database."

## 3. Fork dashboard

Status: ⚪ not started · 🔵 diverging · 🟡 options captured · 🟢 converged

| # | Fork | Status | Question |
|---|---|---|---|
| **F1** | Relation derivation | 🟢 | Where do `er` relations come from — FKs, name/type heuristics, data probes, language assist? |
| **F2** | Entity-vs-table shaping | 🟢 | Is an entity a table, or do junctions collapse and header/detail pairs fold? |
| **F3** | Keys & names without FKs | 🟢 | The real brownfield case — conventions instead of constraints. Built-in rules, a conventions file, probes, teach-in? |
| **F4** | Output discipline | 🟢 | One-shot vs re-runnable with drift detection; who owns generated text afterward? |
| **F5** | Interactivity | 🟢 | Pure CLI vs CLI + review artifact vs wizard vs IDE-native. |

**ALL FORKS CONVERGED 2026-07-10 — the design is closed** (ecosystem entry: RO-26). Remaining work = task-planning at SV-P4 start (Q-1/Q-2/Q-3/Q-5 resolve there).

## 4. Decision log (append-only)

- **2026-07-10 · [F1 = β+γ, δ flagged] (Bora, fork walk)** · FK + deterministic name/type cascade + data probes; every relation lands with an evidence grade (declared / verified / named-only / contradicted); LLM assist = optional flag, proposals pass the probe gate or stay advisory; tool fully functional with δ off. · Rejected: α FK-only (the hero's imperfect FKs ⇒ relation-less skeleton — fails the arc's purpose); β without probes (relations stay guesses; the checklist carries all the risk); δ unquarantined (violates GI-2).
- **2026-07-10 · [F2 = β silent + γ via checklist] (Bora)** · Pure M:N junctions collapse by default (payload-carrying ones stay entities + flagged); header/detail folds are detected but only *proposed* in the F5 checklist — the analyst accepts each. δ usage-mining = named later arc (dialect-SQL parsing dependency). · Rejected: α 1:1 mirror as the whole story (er ≡ db — "modeling that doesn't pay"); γ silent (a wrong fold silently misleads — the exact failure the review discipline exists to prevent).
- **2026-07-10 · [F3 = β conventions file + γ probes; δ later] (Bora)** · The rulebook is data: per-estate conventions file (skeleton = frozen PL contracts §12, GI-4) with shipped starter profiles (`mssql-default`, `czech-erp`, …); probe verification over whatever conventions propose; determinism holds (same DB + same conventions ⇒ same bytes). Built-in rules survive only as the default profile's content. Teach-in = named later arc (writes config, never model — the admissibility rule stands). · Rejected: α fixed rulebook (estates differ; tuning = tool release; platform harvest loses the shared config artifact); δ in 1.0.0 (an interactive generalization engine is real scope inside the bar).
- **2026-07-10 · [F4 = γ layered ownership] (Bora)** · `db` model = machine-owned: re-runs diff live DB vs git and emit PR-shaped proposals (β's engine, GI-3). `er` first cut = scaffold born once, human-owned from first touch; re-runs never regenerate it, only flag er-relevant drift into the checklist. Requires stable ordering/formatting (GI-2 already demands it). · Rejected: α one-shot (day-two has no answer but re-run-and-lose-edits); β uniform (re-proposing er competes with the analyst's authored model forever).
- **2026-07-10 · [F5 = β review-checklist artifact] (Bora)** · One command; beside the model files a structured judgment ledger (markdown + machine-readable twin): collapses, proposed folds, evidence grades, orphan counts, unmatched tables, er-drift flags. The IDE renders it as a walkthrough; it rides the same PR as the proposal; plain-markdown fallback keeps the floor. γ's useful kernel ≡ F3-δ (deferred with it); δ IDE-native = later Designer arc over the same library. · Rejected: α pure CLI (every judgment invisible); γ wizard (breaks scriptability/CI; ephemeral answers); β+δ in 1.0.0 (frontend scope inside the bar for no bar-clause gain).

## 5. Open questions (dispositions after convergence — all are task-planning material, none reopen forks)

- **Q-1 → planning:** where the conventions file lives (model package vs world vs tool config; interacts with K's world composition).
- **Q-2 → planning:** the probe determinism rule — full-scan vs seeded/keyed sampling, pinned in config; the GI-2 admissibility condition F1-γ/F3-γ depend on.
- **Q-3 → largely answered by F5-β:** the checklist is the primary carrier of confidence/evidence grades; whether model text additionally carries annotations = a writer-level planning detail.
- **Q-4 → watch item:** Czech identifiers — addressed by F3-β profiles (`czech-erp`) + the δ flag; profile authoring quality decides; `ttr-nlp`-in-a-deterministic-tool stays open for the δ/teach-in later arcs.
- **Q-5 → planning:** scale bounds (introspection paging, runtime budget, partial-run semantics).

## 6. Session index

| Date | Gear | What happened | Artifacts |
|---|---|---|---|
| 2026-07-10 | Framing + Divergence | Effort opened from the ecosystem design-gaps handover; GI-1..5 recorded; F1–F5 catalogued with leans; Q-1..5 opened | this doc, `01-options.md` |
| 2026-07-10 | **Convergence (fork walk — closed)** | **F1–F5 all ratified (Bora), each with recorded rejections → 🟢 across the board; RO-26 in the ecosystem log; arc row flipped to "designed, ready for its task lists"; Q-1/2/3/5 → SV-P4 task-planning, Q-4 → watch item; later arcs named: F1-δ assist · F2-δ view-mining · F3-δ teach-in · F5-δ Designer import panel** | this doc §4, `01-options.md` resolutions, ecosystem control room §7 |
