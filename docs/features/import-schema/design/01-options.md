# `ttr import-schema` — Option Catalogue (F1–F5)

> Divergence output, 2026-07-10. Each option: what it buys, what it costs, prior art, cross-links. Hero rendering (§6) walks the pilot's ERP through the candidate chains. Constraints GI-1..5 in the control room.
>
> **RESOLUTIONS (converged same day, fork walk — ground truth = control room §4):** **F1 = β+γ, δ flagged** · **F2 = β silent + γ via checklist** · **F3 = β conventions file + γ probes, δ later** · **F4 = γ layered ownership** · **F5 = β review checklist**. The converged whole ≡ §6's "chain lean". Later arcs named: F1-δ assist, F2-δ view-mining, F3-δ teach-in, F5-δ Designer import panel.

---

## F1 · Relation derivation — where do `er` relations come from?

**F1-α — FK-only.** Read declared foreign keys; relations = FKs verbatim.
Buys: zero false positives; trivially explainable; purely catalog-driven (no data access); deterministic for free.
Costs: on the real brownfield estate (conventions, not constraints) the `er` model arrives nearly relation-less — the valuable half is missing exactly where the front door matters most. Hero: the pilot's ERP has *imperfect* FKs — α alone yields a skeleton.
Prior art: every ORM introspector (Hibernate, Prisma `db pull`, SQLAlchemy reflection) — and their shared reputation: "you get a mirror."

**F1-β — FK + name/type heuristics.** FKs first (highest confidence); then a deterministic rule cascade over names and types: `<Table>ID`/`<Table>Code` column ↔ target PK; common suffix families (`_id`, `_kod`, `_cislo`); singular/plural and prefix-strip variants; type-and-length must match; unique-index evidence upgrades confidence.
Buys: works on convention-built estates; rules are a fixed, versioned cascade ⇒ still deterministic; each derived relation carries *which rule fired* (explainability feeds F5's review artifact).
Costs: false positives are certain at estate scale; demands a confidence tiering and a human checkpoint; English-biased rule families do nothing for Czech names (→ Q-4, F3).
Prior art: SchemaSpy's implied-relationship pass; JetBrains DataGrip "virtual foreign keys"; academic schema-matching (COMA) — all convergent on "cascade + confidence".

**F1-γ — FK + heuristics + data-profiling probes.** β's candidates are *verified against the data*: inclusion-dependency probes (⊆ containment of candidate FK values in candidate PK), cardinality profiles, orphan counts. Candidates get evidence grades (declared / verified / named-only / contradicted).
Buys: evidence beats convention — catches relations names miss and kills plausible-but-wrong ones; the orphan count is itself a data-quality finding the stranger wants; probe results make the review artifact persuasive.
Costs: needs data access (not just catalog) and read permissions; runtime on big tables; **sampling threatens GI-2** — admissible only with a deterministic probe rule (full scan, or seeded/keyed sampling pinned in config → Q-2).
Prior art: Metanome/HyUCC-style inclusion-dependency discovery; profiling stages in enterprise catalog tools (Collibra, Informatica).

**F1-δ — language-assisted proposal pass (the weird one).** An LLM (or `ttr-nlp` morphology) reads identifiers, comments, and any docs, and *proposes* relation candidates in human terms ("`dodaci_misto` looks like a delivery-location of `objednavka`"); every proposal must pass γ's probes before it may enter the model; unverifiable proposals land only in the advisory section of the review artifact.
Buys: the only family that reads *meaning* — Czech names, domain vocabulary, comment text; on estates with neither FKs nor Anglo conventions this may be the difference between skeleton and first cut.
Costs: non-deterministic by nature — admissible only quarantined behind the probe gate (deterministic path never consumes an unverified LLM guess); needs an LLM egress at import time (the gateway exists, but the front door must also work offline: δ must be optional); P2's spirit (intelligence out of the spine) says δ is an *authoring assist*, never the pipeline.
Prior art: current-generation "AI schema documentation" tools; our own two-call thesis — LLM proposes, deterministic machinery validates.

*Lean: β+γ — the cascade derives, the probes grade, everything lands with evidence labels; δ as an optional, gated assist behind a flag. α alone fails the hero.*

---

## F2 · Entity-vs-table shaping — what is an entity?

**F2-α — 1:1 mirror.** Every table → one entity, every column → attribute.
Buys: predictable, zero judgment, nothing to review.
Costs: `er` ≡ `db` with different keywords — "the db model alone is a mirror" now in two files; the stranger's first impression is that the E-R layer adds nothing.
Prior art: every default ORM mapping.

**F2-β — α + junction-table collapse.** Detect pure M:N junctions (exactly two FK-ish columns forming the key, no or trivial payload) → emit a direct M:N relation instead of an entity.
Buys: the single highest-value E-R idiom, cheaply detected; instantly makes the er model *smaller* than db — visible added value.
Costs: junctions with payload (qty, valid-from) are genuinely ambiguous — need a rule (payload ⇒ keep entity + two relations; flag in review) rather than silent collapse.
Prior art: standard reverse-engineering practice (ER/Studio, PowerDesigner both do it, behind a toggle).

**F2-γ — β + header/detail folding.** Detect header/detail families (Order/OrderLine: FK + composite key + name affinity) and emit them as composition-flavored relations (or folded sub-entities where TTR-M supports it), plus master-data vs transaction hints feeding `cnc` roles later.
Buys: closest to how an analyst would actually draw it; sets up the `cnc` layer.
Costs: the most heuristic-heavy shaping; a wrong fold is worse than no fold — γ only makes sense paired with F5's review checkpoint; TTR-M's composition vocabulary must actually carry it.
Prior art: dimensional-modeling bootstrap tools; header/detail detection in ETL accelerators.

**F2-δ — shape by observed usage (the weird one).** Mine view definitions, stored procedures, and (where available) query logs for the joins that *actually happen*; shape entities and relations around observed access paths rather than structure.
Buys: reflects the estate's lived semantics — views are congealed institutional knowledge; procedures encode the joins nobody documented.
Costs: logs/procedures are unevenly available and unbounded input (determinism needs a pinned snapshot); easily overfits to reporting quirks; view-parsing is a dialect-SQL-parsing project of its own (though ttr-translator's parser front could carry the MSSQL cut).
Prior art: query-log-driven modeling in warehouse migration tooling (e.g. dbt-era lineage miners).

*Lean: β in the default path, γ behind the review checkpoint, δ's view-mining as a named later arc (its parser dependency is real). α is the floor every option degrades to when detection finds nothing.*

---

## F3 · Keys & names without FKs — the real brownfield case

**F3-α — built-in rulebook.** One fixed, versioned heuristic set shipped in the tool (PK-name patterns, suffix families, unique-index evidence, type matching).
Buys: zero-config — the stranger runs one command; deterministic.
Costs: estates differ irreconcilably (Hungarian prefixes vs Czech nouns vs AdventureWorks English); one rulebook is either bloated or wrong; tuning requires a tool release.

**F3-β — conventions file.** The rulebook is data: a reviewable conventions document per estate (skeleton = frozen PL contracts §12; GI-4), shipped with named starter profiles (`mssql-default`, `czech-erp`, …). The tool's heuristics interpret the file; the file is part of the deterministic input (GI-2 holds: same DB + same conventions ⇒ same bytes).
Buys: estates get their own truth *as a versioned artifact in git* — exactly the house pattern; profiles give the stranger a one-flag start; the conventions file outlives the import (documentation value; the continuous-harvest platform tier reads the same file).
Costs: the front door gains a concept; profile quality determines first-run experience.
Prior art: linter-config culture (`.editorconfig`, ESLint presets); dbt project conventions.

**F3-γ — probe-verified keys.** Whatever names suggest, data decides: candidate keys must pass uniqueness probes, candidate relations containment probes (F1-γ's engine), before landing as anything stronger than "advisory".
Buys: turns heuristics from guesses into verified findings; shares one profiling engine with F1-γ.
Costs: F1-γ's costs (access, runtime, Q-2 determinism rule).

**F3-δ — teach-in (the weird one).** The tool proposes on a sample; the operator confirms/corrects a handful of examples; the tool *generalizes the correction into conventions-file rules* and re-runs. The learning step writes config, not model — so the final run is still deterministic from (DB + conventions).
Buys: the fastest route to a good conventions file on a weird estate; the interaction produces a durable artifact rather than session state.
Costs: an interactive mode to build and explain; generalization quality is hard; strictly an accelerant for β, not a replacement.
Prior art: example-driven wrapper induction; "confirm these 5 matches" flows in data-prep tools (Trifacta lineage).

*Lean: β as the frame, γ as the verifier, δ as a later convenience arc. α survives only as the content of β's default profile.*

---

## F4 · Output discipline — one-shot or re-runnable, and who owns the text afterward?

**F4-α — one-shot.** Generate once; re-running overwrites; after that you own the files.
Buys: simplest possible story; matches "one-shot CLI" (GI-1) read literally.
Costs: real estates evolve — the day-two question ("a table was added") has no answer but "re-run and lose your edits"; clobbers the er first-cut the analyst refined, which is the tool's whole value.

**F4-β — re-runnable with drift detection.** First run generates; every later run introspects again, diffs against the *current model in git*, and emits **PR-shaped proposals** (GI-3) for the drift — new tables, changed columns, vanished objects.
Buys: aligns exactly with I-1/I-3's proposals-in flow; the db-mirror half becomes a living maintenance tool; nothing is ever silently overwritten (git review is the merge point).
Costs: needs stable generation order and formatting (GI-2 already demands it) so diffs are semantic, not cosmetic; needs an identity rule (how a renamed table is matched) — imperfect matching just means noisier proposals, which review absorbs.
Prior art: the platform tier's continuous-harvest design (same flow, scheduled); Terraform plan/apply mental model.

**F4-γ — layered ownership (the two halves have two lifecycles).** The `db` model = machine-territory: regenerated/PR-proposed freely forever (β's discipline). The `er` first cut = **scaffold born once**: generated on first run, human-owned from that moment; later runs never regenerate it — they only *flag* er-relevant drift ("db.table X is new and unmapped in er") as review items.
Buys: matches the halves' natures — the mirror is derivable (machine's), the meaning is authored (human's); protects exactly the artifact the analyst invests in; the flag stream keeps er honest without touching it.
Costs: two lifecycles to document; the "unmapped in er" checker is a (small) model-analysis feature of its own.
Prior art: scaffold-once generators (Rails migrations vs models); protobuf-generated-code vs hand-written wrappers.

*Lean: γ, with β's machinery as its db-half engine. α is what γ degrades to when you delete the marker — acceptable, never default.*

---

## F5 · Interactivity — where does human judgment enter?

**F5-α — pure CLI.** Flags in, files out, exit code.
Buys: scriptable, CI-able, the stranger types one line; no UI surface to build.
Costs: every E-R judgment call (collapse? fold? low-confidence relation?) is buried in defaults or flags; the stranger doesn't know what the tool wasn't sure about.

**F5-β — CLI + review-checklist artifact.** Still one command — but beside the model files it emits a structured **review checklist** (markdown + machine-readable twin): every judgment made (junctions collapsed, relations by rule-X at confidence-Y, orphan counts, unmatched tables, er-drift flags from F4-γ), each item checkable/dismissable; the IDE (VS Code extension / Designer) renders it as a walkthrough that jumps to the model text.
Buys: determinism intact (the checklist *is* output); human judgment lands where it belongs — after generation, in review, in git (the checklist rides the same PR as the proposal); the IDE story needs only rendering, not tool-driving; the checklist doubles as the acceptance-run evidence trail.
Costs: checklist format to design (small); worthless if nobody renders it (plain-markdown fallback keeps the floor).
Prior art: our own review-doc culture (this corpus); `cargo audit`/`npm audit` report-then-fix; Lighthouse reports.

**F5-γ — interactive wizard (TUI).** The run itself asks: "collapse these 12 junctions? [Y/n]" …
Buys: beginner-friendly in the demo sense.
Costs: breaks scriptability and CI; session answers are ephemeral (unless written to config — at which point this is F3-δ); two code paths to maintain; the bar's stranger is better served by good defaults + review than by 40 questions.

**F5-δ — IDE-native import.** The Designer/extension drives introspection interactively (connect, browse, pick, shape).
Buys: the richest possible experience; a future Designer panel wants this anyway.
Costs: the front door must not *require* an IDE (bar wording is CLI-shaped); the library/CLI has to exist first regardless — δ is a consumer of the same engine, later.

*Lean: β. γ rejected in spirit (its useful kernel is F3-δ's teach-in, which writes durable config); δ = a later Designer arc over the same library.*

---

## 6. Hero rendering — the pilot's ERP (MS SQL, Czech names, imperfect FKs)

**Chain α (F1-α · F2-α · F3-α · F4-α · F5-α):** stranger runs one command, gets a faithful `db` mirror and an `er` that is the same mirror minus the few real FKs. Verdict: *fails the arc's purpose* — this is the "modeling that doesn't pay" experience §1 of ecosystem.md warns about.

**Chain lean (F1-β+γ · F2-β(+γ flagged) · F3-β+γ · F4-γ · F5-β):** stranger runs `ttr import-schema --profile czech-erp` (or starts default and tunes the conventions file); catalog + cascade + probes produce a `db` mirror plus an `er` first cut where relations carry evidence grades, junctions collapsed, Order/OrderLine folds *proposed* in the checklist; the analyst walks the checklist in VS Code, accepts/corrects, commits — the er model is theirs from that moment; months later a re-run PRs the new tables into `db` and flags them "unmapped in er". Verdict: *the front door the bar's sentence describes.*

## 7. Cross-links

- F1-γ and F3-γ share one profiling engine (inclusion/uniqueness probes) — one component, two consumers; its determinism rule = Q-2.
- F4-β/γ requires GI-2 taken seriously: stable ordering, stable formatting — same demand TTR-M's writer already meets.
- F5-β's checklist is also F4-γ's drift-flag channel — one artifact, two producers.
- F1-δ and F3-δ both quarantine non-determinism into *config/advisory* space — a standing principle candidate ("assist writes config, never model").
- The platform tier's continuous harvest (I-2 commercial half) consumes the same conventions file and proposal discipline — this design is its one-shot little sibling by construction.
