# `ttr-resolver` Rewrite — Options Catalogue (NEW-1)

> **Status:** options captured 2026-07-10 (design-gaps session, item 3 of `../../next-steps-260710-design-gaps.md`). **No decisions in this document — leans are leans.** Convergence is scheduled with SV-P3's planning and wants two lookups this session couldn't take: the live reference's internals (`~/Dev/ai-platform/agents/resolver`, connectable) and a kantheon-side view. NEW-1 stays open until then.
> **Why SPINE work, not cleanup:** entity resolution is half the two-call thesis — call #1 binds the user's words to modeled entities; everything downstream trusts that binding. The extraction inventory's own words.

---

## 1. Facts (fixed ground, not options)

- The live reference: ai-platform `agents/resolver` (`cz.dfpartner.resolver.v1`), in production at the pilot, **active bugfix churn (NameTag/CNEC)** — it stays DFP-side until cutover; the rewrite replaces it in the open lineage, not in place.
- **Parity bar:** the pilot's conversation corpus — Czech morphology parity demonstrated (SV-P3 DONE-when). The conformance suite (RO-25) inherits resolution assertions.
- `resolver.v1` proto name is **reserved** (naming ledger); the service will be kantheon-native-rewritten per the SV-P3 plan line.
- **P2 boundary (ecosystem.md):** no LLM in the deterministic path — but the resolver is explicitly *allowed to be non-LLM-statistical* (fixed statistical models, deterministic given pinned model files). Where an option leans on embeddings, the P2 tension is called out, not hand-waved.
- Vocabulary is model-declared by design: aliases, name attributes, fuzzy-searchable fields live in TTR-M (that is *why* resolution can be governed).

## 2. R1 · Placement — where does resolution live?

**R1-α — standalone `ttr-resolver` service.** The live shape, renamed: own service, own `resolver.v1` proto, in the chart roster.
Buys: mirrors the reference (parity work is a port, not a re-architecture); independently scalable (resolution is hot — every turn hits it); clean roster line ("what is known" = Veles, "what did you mean" = resolver); the reserved proto name suggests exactly this.
Costs: one more service in the constellation; another hop on the turn's critical path.
Cross-links: C-1 roster philosophy (β-spine+α-leaves) comfortably admits it.

**R1-β — a `ttr-nlp` capability.** Resolution folds into the existing Python NLP service (morphology + NER already live there).
Buys: resolution is NLP-adjacent (lemmatize → match); one service fewer; the MorphoDiTa/NameTag toolchain is already resident — no cross-service NLP round-trips inside the resolve path.
Costs: conflates two functions with different scaling and different consumers (nlp = primitive provider; resolver = opinionated pipeline over the *model*); couples the model-vocabulary cache into a service that today is stateless-primitive; `resolver.v1` would live inside `nlp.v1`'s house.
Prior art: the "capability, not service" pattern the kantheon side uses for tool surfaces.

**R1-γ — a library the query door (and Golems) embed.** Resolution as a JVM/Python library over a vocabulary artifact; no service at all.
Buys: zero hops — resolution at the caller; trivially offline; versioned like any artifact.
Costs: every consumer carries the vocabulary cache and its refresh discipline (N caches, N staleness stories); Czech morphology in-process means bundling native/model files into every consumer; a *library* can't be a door with its own identity/observability seam — resolution disappears from the one-question-one-trace picture unless every consumer instruments it.
Prior art: analyzer-in-client search architectures (and their cache-drift folklore).

**R1-δ — fold into `ttr-fuzzy` (the weird one).** Fuzzy matching is the resolver's core move — promote `ttr-fuzzy` into the resolver rather than keeping both.
Buys: kills an apparent overlap (match vs resolve) before a stranger asks "which one do I call?"; one vocabulary cache.
Costs: they are different layers — `fuzzy.match` is a *primitive* (string → candidates, no model semantics), resolution is a *pipeline* (spans → NER → lemmatize → candidates → model-scoped disambiguation → bound entities); folding buries the primitive third parties may want raw; renames/re-plumbs a live MCP door (RO-25 just pinned `fuzzy.match:v1`).
Verdict-shaped note: maps the space; the overlap question ("does resolver *consume* fuzzy or *contain* it?") is real and lands in RQ-2 regardless of placement.

*Lean: α — with the explicit sub-question for convergence: resolver consumes `ttr-fuzzy`/`ttr-nlp` as services (the live shape?) or embeds those steps. That's an inventory fact to read out of the reference, not a preference.*

## 3. R2 · Resolution pipeline — how does text become entities?

**R2-α — port the live deterministic pipeline.** NER-gated span detection (NameTag/CNEC via morphology) → lemmatization → dictionary lookup over model vocabulary → fuzzy cascade (the `ttr-fuzzy` algorithms) → model-scoped disambiguation and thresholds.
Buys: the parity bar is *defined against this pipeline* — porting it is the shortest credible path to "parity demonstrated"; behavior is explainable step-by-step (which rule fired, which candidate won — provenance-friendly); statistical-but-deterministic (pinned model files) sits inside P2's allowance.
Costs: inherits the reference's known churn (NameTag/CNEC bugfixes are live evidence the edge cases bite); Czech-tuned — the generalization story is R4's problem.

**R2-β — embedding-assisted candidate generation.** Vocabulary entries and query spans embedded (via the llm-gateway's `EmbedText`); ANN search proposes candidates; deterministic scoring/thresholds decide.
Buys: recall on paraphrase/synonym misses that dictionaries never list; multilingual for free (couples to R4-δ); less rule-tuning per estate.
Costs: **the P2 tension is live** — embeddings come from a model behind the gateway; "deterministic" now depends on pinning an embedding model + precomputed vectors (a version-locked index artifact could make it reproducible, but the spine grows a model-file dependency and an index build step); explainability drops ("cosine 0.83" persuades no auditor); infra cost on the hot path.
Prior art: entity-linking literature (candidate-gen via embeddings + deterministic rerank is the standard modern shape).

**R2-γ — tiered hybrid.** α's dictionary+fuzzy first (precision tier); β's embedding fallback only when tier 1 comes up empty (recall tier); every resolution labeled with its tier in provenance.
Buys: parity path preserved (tier 1 ≡ the reference); recall gains where it's safe (an empty result has nothing to lose); tier labels keep audit honest; embeddings can arrive *later* without re-architecture — γ is α plus an extension seam.
Costs: two subsystems to run once tier 2 lands; threshold interplay ("when is tier 1 'empty enough'?") needs care.

**R2-δ — LLM-in-the-loop resolution (the weird one).** Ask the LLM which entity the user meant, vocabulary in context.
Buys: nothing the two-call thesis doesn't already give — call #1 *is* LLM entity recognition **against** resolver-served vocabulary; the agent side already owns this.
Costs: puts an LLM inside the deterministic spine — categorically out by P2. Recorded to sharpen the boundary: **the resolver serves and scores vocabulary; the LLM (agent-side) proposes spans; the resolver's verdict is deterministic.**

*Lean: α to parity, architected as γ (the tier seam named from day one, tier 2 unbuilt). β alone risks the parity bar and the audit story; δ is out by principle.*

## 4. R3 · Vocabulary source — where does the dictionary come from?

**R3-α — Veles snapshot consumption.** The resolver builds its dictionary/indexes from model **snapshot archives** (the B-contract seam), keyed by snapshot hash.
Buys: versioned, reproducible (index derivable from a hash — determinism story writes itself); offline-capable; cache invalidation = hash change; the B-contract gets its second consumer (validating the seam's design).
Costs: staleness window between model commit and snapshot serve (bounded by B's lock/max-age discipline); index build step on snapshot change.
Cross-links: B-2/B-3 (snapshot serving = core work per RO-14); RO-13's core ⚑ review pins the archive schema the resolver would consume — sequencing luck: review lands before SV-P3.

**R3-β — direct Veles reads.** Live gRPC against `meta.v1` (list/search) at resolve time or on cache refresh.
Buys: always-fresh; no index-build machinery; the simplest first implementation.
Costs: runtime coupling on the hot path (Veles outage = resolution outage); freshness is bought with per-instance cache discipline anyway (nobody resolves via N gRPC calls per turn), so β converges toward "α with worse versioning".

**R3-γ — compile-emitted vocabulary artifact.** The model compile emits a dedicated resolver-vocabulary artifact (beside the lock/compile record); the resolver loads artifacts, never talks to Veles.
Buys: maximal determinism (vocabulary pinned by the same compile that pinned the model); resolution testable fully offline (the conformance suite would love it).
Costs: a new compiler output = a new tatrman-owned format inside the bar; deploy discipline ("which vocabulary artifact is live?") duplicates what snapshots already answer; couples toolchain release cadence to resolver needs.

**R3-δ — resolver watches git (the weird one).** The resolver reads the model repo directly and builds its own index.
Buys: no dependency on Veles at all.
Costs: re-implements Veles's one job (reading model source from git) in a second service — violates "one source of what is known"; drift between two git-readers is a new failure class. Recorded to underline *why* Veles exists.

*Lean: α — with β acceptable as the rewrite's step one (dev-mode reads) hardening into α before SV-P3's DONE-when. γ is the fallback if snapshot serving slips.*

## 5. R4 · Language architecture — Czech today, what tomorrow?

**R4-α — Czech-first via `ttr-nlp` (MorphoDiTa/NameTag), language as config.** The live shape: morphology and NER are cs-model-backed services; the resolver assumes one configured language.
Buys: parity-true; zero speculative machinery; the pilot (and the CEE beachhead) is Czech.
Costs: the second language arrives as a refactor, not a config change.

**R4-β — language-plugin SPI from day one.** Morphology/NER/token-normalization behind a per-language plugin interface; `cs` is plugin #1.
Buys: the standard's story ("any language over any model") gets its resolver leg; forces clean seams that even the cs-only path benefits from.
Costs: an SPI designed against one implementation is a guess (the classic premature-abstraction trap — the second language *defines* the interface); bar-external work inside SV-P3's window.

**R4-γ — language-agnostic core + optional enrichment.** The core pipeline uses only language-neutral moves (case/diacritic folding, the fuzzy cascade); morphology/NER become *optional* quality enrichments a deployment enables per language.
Buys: a stranger with an unsupported language still gets useful resolution (degraded, honest); mirrors H-8's "integrity + optional advisory" degrade pattern.
Costs: Czech parity *needs* morphology — γ alone fails the bar; it's a floor definition, not a strategy.

**R4-δ — embeddings make morphology moot (the weird one).** Multilingual embedding space handles inflection/synonymy; drop language machinery.
Buys: one mechanism for all languages.
Costs: entirely R2-β-coupled (same P2 tension, same auditability loss); Czech inflection in general-purpose multilingual embedding spaces is exactly where "mostly works" hides failures; the parity corpus would judge it, and the smart money says harshly.

*Lean: α for SV-P3, with γ's degrade floor documented (unsupported language ⇒ fold+fuzzy only, honestly labeled) and β deferred until language #2 is real — the second language defines the SPI.*

## 6. Cross-links & the standing boundary

- R2-γ's tier seam and R3-α's hash-keyed index are the two extension points that keep β-flavored futures (embeddings, more languages) additive rather than re-architectural.
- The **P2 boundary, restated for this component:** the resolver may be statistical (pinned model files, deterministic outputs) but never generative; LLM proposal lives agent-side; the resolver's verdict — vocabulary served, candidates scored, bindings returned — is deterministic and provenance-carrying.
- MCP exposure: resolution today reaches agents through the doors indirectly (`meta.search`, `fuzzy.match`). Whether `resolver.v1` warrants its **own MCP door** (a `resolve` tool third-party agents call instead of hand-rolling call #1) is RQ-4 — it would extend the RO-25 surface, so it must be decided *before* the surface is declared complete for the debut.

## 7. Questions for the convergence session (with the ai-platform + kantheon look)

- **RQ-1 — the reference inventory:** the live pipeline's actual stages, proto surface, and the NameTag/CNEC churn's root causes (bug class tells us what the rewrite must do differently, not just equally).
- **RQ-2 — primitive topology:** does the live resolver call fuzzy/NLP as services or embed them? What does that imply for R1's consume-vs-contain sub-question?
- **RQ-3 — vocabulary reality:** where does the reference's dictionary actually come from today (Veles? own store? config), and what invalidation discipline does it run?
- **RQ-4 — MCP exposure:** does `resolve` become a first-class door tool for third-party agents (extending RO-25), or is resolution a Golem-internal concern with `meta.search`+`fuzzy.match` sufficient for outsiders?
- **RQ-5 — parity corpus mechanics:** is the pilot conversation corpus usable as-is for the parity run (access, anonymization — ties to RO-19 ask ③), or does SV-P3 need a derived set first?
