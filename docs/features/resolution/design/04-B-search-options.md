# B · Member & Entity Search — Options Catalogue

> **Status: CONVERGED 2026-07-12 — RS-13..17 in the control room §7.** Net shape: live-SQL sourcing now with the **built vocabulary artifact** as the named target (`LoaderSource` SPI) · live engine to parity, index-primary evolution behind Q-17's referee, **vectorization = a separate feature** · fuzzy = **the vocabulary matcher** (members + declared vocabulary, source-tagged; R3-α's first consumer) · interval + snapshot-keyed + staleness-echoed refresh, CDC = commercial on the open SPI · searchable = visible-by-declaration (RS-17, documented). S-2 (shared normalization spec) + S-3 (admin-role operator endpoints) ratified. Q-17 + threads T1–T4 → planning. Fact base = `02-recon-live-reference.md` §B (the live fuzzy-matcher), the open lineage (`tatrman-server/services/ttr-fuzzy`, post-SV-P0), and the A/C decisions this workstream inherits (RS-9..12: lexicon + alias tables; RS-3..8: in-cluster lemmatization + batch call).
> Scope: where member vocabulary physically comes from, how it is indexed and matched, how model-declared vocabulary (A) joins it, and the refresh/staleness discipline. The MCP contract `fuzzy.match:v1` is **pinned** (GI-2) — the service surface evolves additively or not at all.

---

## 1. Facts (fixed ground, not options)

- **Live shape** (recon §B): metadata-declared fuzzy columns → single-column-PK tables → `SELECT pk, col` against the **source DB** → full member-value sets in memory (`ConcurrentHashMap` + per-category `TokenIndex` + `DistanceCache`) → hourly background refresh, atomic swap, loader-failure keeps previous cache → unauthenticated `POST /refresh`. Matching: fold (lowercase+NFD+strip marks) → cascade (first algorithm whose top-1 ≥ minScore wins) with **TATRMAN = token/IDF/lemma matcher**; lemma axis currently off (Lindat rate limit — fixed by RS-3/4).
- **Inherited from A (new loader inputs):** RS-9/10 lexicon entries (terms/patterns/examples targeting er/db/md) reach consumers in canonical form; RS-12-γ **declared alias tables** (`semantics{kind: alias_table}`) are loader-ingestible member synonyms; RS-12-β `valueLabels` are small in-model member vocabularies. RS-11: vocabulary is per-locale.
- **Inherited from C:** lemmatization is in-cluster (MorphoDiTa backend), batch call incoming (RS-6/Q-11) — load-time lemmatization of full vocabularies becomes viable; responses echo model versions (cache keys).
- **Boundaries:** GI-2 (`fuzzy.match:v1` pinned: query/category/algorithm/limit → matches with scores; Czech diacritics contract-observable). GI-4/Q-8: one-shot/on-refresh loading is open; *continuous* harvest is commercial. GI-5/R3: snapshot archives exist as the model-side versioned seam; **no live component consumes them yet**.
- **Scale reference:** pilot categories = ERP product/customer columns; full value sets in memory, no count bound; `DistanceCache` 10k; match limit ≤ 50.

## 2. B1 · Vocabulary sourcing — where do member values come from?

**B1-α — live SQL load (the live shape, hardened).** Loader reads declared columns (+ RS-12-γ alias tables) from source DBs at startup/refresh.
Buys: proven; always at-most-refresh-interval stale; no new artifact machinery; the open/commercial line stays clean (interval load ≠ continuous harvest).
Costs: fuzzy needs source-DB credentials + network reach (a *read* path outside the governed query door — see Q-16); per-replica load storms on big estates; freshness bounded by interval, never exact.

**B1-β — built vocabulary artifact.** An open indexer job/CLI (`ttr vocab-build`-class) extracts member values + declared vocabulary into a **versioned, hash-keyed artifact**; `ttr-fuzzy` loads artifacts, never touches source DBs.
Buys: fuzzy loses DB credentials entirely (least-privilege win, Q-16 shrinks); reproducible index (same artifact ⇒ same matches — determinism story writes itself); replicas share one artifact (no load storms); offline/conformance testing trivially; the artifact is the natural carrier for lemma precomputation (batch call runs once, at build).
Costs: new artifact class + build/deploy discipline ("which vocabulary is live?"); staleness now has *two* hops (data→artifact→serve); someone must run the builder (scheduled builds = flirting with the harvest boundary — the *artifact* is open, the *scheduler* may be the platform tier).
Prior art: search-engine index snapshots; R3-α's snapshot logic applied to data vocabulary.

**B1-γ — query-time pushdown.** No copy: `match` translates to source-DB `LIKE`/trigram/full-text probes.
Buys: zero staleness; zero memory.
Costs: per-turn source-DB load on the hot path (resolution latency now owned by the slowest estate DB); Czech fuzzy semantics (fold, lemma, IDF, order bonus) don't exist in source SQL — the contracted cascade behavior (GI-2!) is unimplementable downstream; source outage = resolution outage. Maps why the copy exists.

**B1-δ — search-engine sidecar owns ingestion (the weird one).** OpenSearch/Meilisearch-class system as the vocabulary store; fuzzy becomes a facade.
Buys: mature ingestion/scale tooling.
Costs: a heavyweight stateful dependency in the chart for what is, at pilot scale, an in-memory map; the contracted cascade must be reimplemented as a plugin or faked; ops burden lands on the "stranger" (acceptance bar).

*~~Lean~~ **DECIDED 2026-07-12 as leaned — RS-13**: α now, β the named target on the `LoaderSource` SPI.*

## 3. B2 · Index & matching architecture

**B2-α — port the live engine.** In-memory repository + inverted token index (surface ∪ lemma) + IDF + order bonus + cascade; `DistanceCache`.
Buys: parity-true (the conformance suite asserts cascade behavior); known-good Czech behavior; zero new deps.
Costs: bespoke engine to maintain; IDF/global-index rebuild cost at refresh; per-category memory linear in vocabulary.

**B2-β — token/lemma index primary, cascade as re-scorer (formalize alg2's direction).** Candidate generation always via the token/IDF index; LEVENSHTEIN/JARO_WINKLER demoted to re-scoring candidates, never full scans.
Buys: predictable latency at scale (no score-all fallback); one index to reason about; contract-compatible (cascade semantics preserved as scoring gates).
Costs: recall risk where token overlap is zero but edit distance is small (typo-in-first-token class) — needs a char-ngram fallback tier; behavior drift vs parity corpus must be measured, not assumed.

**B2-γ — embedded search library (Lucene-class).** Analyzer chain (fold + cs tokenization) + fuzzy/ngram queries inside the service.
Buys: battle-tested inverted index, memory-mapped scale, ngram/typo handling for free.
Costs: the analyzer chain must reproduce *our* fold + MorphoDiTa lemmas (custom analyzer anyway — the library saves less than it seems); TATRMAN scoring (order bonus, dual-axis max) becomes custom scoring code inside someone else's engine; JVM dep weight.

**B2-δ — embedding/ANN tier (the weird one, R2-β's sibling).** Vector index over member names for paraphrase recall.
Buys: recall beyond string similarity ("hlavní město" → Praha).
Costs: P2 tension (pinned embedding model + versioned index artifact needed for determinism); explainability drop; infra on the hot path. **Belongs behind the R2-γ tier seam** — same parked slot, one design.

*~~Lean~~ **DECIDED 2026-07-12 as leaned — RS-14**: α now, β the internal evolution gated by Q-17. **Vectorization/semantic matching = a separate feature** (Bora) — δ moves out of this effort entirely; landing points reserved (R2-γ tier seam, B2 index seam).*

## 4. B3 · Vocabulary-from-model integration — does fuzzy become *the* vocabulary matcher?

**Question.** A (RS-9..12) creates three declared-vocabulary streams beside member values. Who indexes and serves what? (Today: Veles `meta.search` covers model-object names/aliases; fuzzy covers member values. The resolver consults both.)

**B3-α — fuzzy = members only (status quo hardened).** Lexicon terms stay Veles-side (`meta.search` grows lemma-aware matching?); fuzzy indexes member values + alias tables only.
Buys: clean split (model vocabulary ↔ model server; data vocabulary ↔ fuzzy); no double-indexing.
Costs: two different matching engines with two different quality bars (meta.search is plain scored search — no fold/lemma/cascade); the resolver must query two services with two semantics and merge; „utržili"→measure matching would inherit meta.search's weaker engine.

**B3-β — fuzzy = the vocabulary matcher (all strings, tagged by source).** Categories generalize: member categories (from data) + model categories (lexicon terms per target kind, valueLabels) — one engine, one quality bar, source-tagged results.
Buys: every string in the system matched by the same Czech-aware engine (fold+lemma+IDF+cascade); the resolver gets one candidate API; A's vocabulary instantly inherits B's quality; conformance asserts one behavior.
Costs: fuzzy now loads from **two sources** (Veles/snapshot for declared vocabulary + estates for members) — its availability story compounds; Veles keeps `meta.search` anyway (catalog UX), so some overlap remains; category-namespace design must distinguish member-candidates (→ EntityBinding.resolved_id) from vocabulary-candidates (→ target refs).

**B3-γ — a distinct `ttr-search` (the weird one).** A third service unifying meta.search + fuzzy into one search plane.
Buys: the "one search door" story.
Costs: re-plumbs two pinned surfaces (`meta.search`, `fuzzy.match` — GI-2) for an internal elegance gain; a rename-class change post-RO-25. Maps why the pinned contract constrains topology.

*~~Lean~~ **DECIDED 2026-07-12: β — RS-15**: fuzzy = the vocabulary matcher; `meta.search` remains for catalog/browse; declared vocabulary arrives via the snapshot read at refresh — R3-α's first live consumer.*

## 5. B4 · Refresh & staleness discipline

**B4-α — interval + atomic swap (live shape) with hardening.** Keep hourly-class refresh; **authenticate `/refresh`** (recon: deliberately unauthenticated today — fine in-mesh, not fine in the open offering: S-3).
**B4-β — model-keyed refresh.** Declared-vocabulary config (which columns, alias tables, lexicon) reloads on **snapshot change** (hash-keyed — cheap, exact); member *data* stays interval-refreshed. Two staleness clocks, each honest.
**B4-γ — staleness surfaced.** Responses/GetStatus echo `vocabulary_version` (snapshot hash + load timestamp per category) — the RS-7 capability-matrix pattern applied to B; the conformance suite can assert it.
**B4-δ — CDC/continuous (the weird one).** Change-data-capture keeps members fresh in seconds. **This is the commercial harvest line (GI-4/Q-8)** — recorded to draw it: interval/systematic-on-refresh = open; continuous sync = platform tier. The seam: `LoaderSource` is the SPI a commercial CDC loader plugs into.

*~~Lean~~ **DECIDED 2026-07-12 as leaned — RS-16**: α+β+γ composed; δ CDC = the commercial extension on the open `LoaderSource` SPI (the GI-4 line drawn).*

## 6. Threads (not forks — ride into convergence/planning)

- **B-T1 · Contract evolution under GI-2:** additive-only candidates — `locale?` on match (RS-11), batch match (the resolver fans out per-column today — one RPC with N spans×categories would cut hot-path chatter), category-discovery/GetStatus (capability matrix). None rename `fuzzy.match:v1`.
- **B-T2 · Normalization as a shared spec (S-2 candidate):** the fold logic exists ≥5× across the family (fuzzy TextNormalizer, per-grounding-service Diacritics, meta.search). One pinned normalization spec (shared lib + documented rules) — determinism and parity depend on them agreeing.
- **B-T3 · Replica strategy:** N replicas × full in-memory vocabulary is the simple story until B1-β's shared artifact; document the memory envelope per estate size (Q-11's sizing feeds this).
- **B-T4 · PK constraints honesty:** composite-PK/no-PK tables are silently skipped today (`pkReason`) — surface skips in a loader report (import-schema checklist pattern) so estates *know* which declared columns aren't actually searchable.

## 7. Open questions raised here

- ~~**Q-16 — the member-value leak surface**~~ **Resolved 2026-07-12 — RS-17:** declaring a column searchable/fuzzy **is** declaring its values readable within the deployment; the model author's act is the consent. Duty: document prominently (manual at the `search{fuzzy}` flag + security docs); lint-warn on sensitive-looking searchable columns = planning detail. ACLs/per-caller filtering rejected for v1 (revisit: a real estate demands per-role vocabularies).
- **Q-17 — matching quality referee:** the parity corpus covers resolver E2E; B needs its own match-quality fixtures (Czech diacritics, inflection, multi-word order, typo classes) so B2-β-style engine evolution has a gate. Seed: the conformance suite's "fuzzy match with diacritics" floor (RO-25) + eval corpora found in ai-platform.
