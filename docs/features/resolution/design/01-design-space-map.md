# Resolution & Grounding — Design-Space Map

> The option tree. One section per workstream: **Question → Branches → Cross-links → Open**. Deliberately divergent — branches are added, never silently removed. Leans are marked and are not decisions. Facts from the 2026-07-12 live-repo recon are cited as `[recon §n]` (→ `02-recon-live-reference.md`).

---

## A · Model vocabulary surface (TTR-M)

**Question.** What does the conceptual model declare so that member search, grounding, and resolution are *governed by the model* rather than by service config — and where does it live in TTR-M?

**What exists today (fact base).** TTR-M already carries two purpose-built surfaces: the **`search { searchable, fuzzy, patterns }` block** (widened to all data-bearing kinds — `docs/features/search-block/`) and the **`semantics { role, kind, params }` hints** the grounding arc added (roles like `period_start/event_date/geo_lat/amount/currency_code`, kinds `period_table/poi/fx_rate` — tatrman T1–T6, grammar 4.2; consumed via metadata `ListObjects(semantic_role/kind)`) [recon §D.1/§D.5]. Fuzzy-searchable columns are model-declared and read from metadata (`fuzzy_only` → category namespace) [recon §B.1]; aliases, localized labels, search keywords ride the snapshot and `meta.search`. **So A is not greenfield** — it is: ratify these two surfaces as the standard's, and fill the gaps (measure/verb vocabulary, per-language forms, member-level vocabulary).

**Branches (initial cut — to be diverged properly):**
- **A-α — annotations on existing constructs.** Searchability, aliases, member vocabulary hints live as attributes/annotations on entities, fields, and calc-map entries where they are today.
- **A-β — a first-class `vocabulary` block.** A dedicated TTR-M construct: per-entity/per-dimension declared terms, synonyms, per-language forms, measure verbs ("utržili" → revenue), grounding hints (fiscal calendar ref, POI declarations).
- **A-γ — vocabulary as a separate document kind** (`.ttrm` module or sidecar): the conceptual model references a vocabulary package; domain glossaries versioned independently of structure.
- **A-δ — vocabulary lives outside TTR-M** (service config / Veles-side registry) — the weird one; maps why model-declared is the thesis.

**Cross-links.** Feeds B (what is searchable), C (which languages have declared forms), D (fiscal calendar, POIs — geo's ModelPoiResolver already reads model POIs), E (registry shape), F (what `meta`/`resolve` doors expose). Grammar changes ride the grammar-master process.

**Open.** Hero: where do „utržili"-class measure verbs live? Per-language alias explosion vs morphology-at-runtime (couples to C).

## B · Member & entity search

**Question.** Where does member vocabulary physically come from, how is it indexed and matched, and what is `ttr-fuzzy`'s target shape?

**What exists today (fact base)** [recon §B]. Live fuzzy-matcher: `MetadataLoaderSource` reads model-declared fuzzy columns (`ListObjects(fuzzy_only=true)`), resolves single-column PKs, composes `SELECT pk, col FROM table` against the **source DB**, loads full member-value sets into an in-memory repository; **hourly background refresh with atomic swap**, loader failure preserves the previous cache; `POST /refresh` for operator reload. Matching: diacritic fold (NFD strip) + cascade (first algorithm whose top-1 ≥ its minScore wins) where **TATRMAN = the token-based matcher** (inverted token index over surface ∪ lemma tokens, per-token Levenshtein, IDF weighting, word-order bonus). Lemmatization calls the NLP service (`cs`) at **both load time and query time** — currently disabled by default. Composite-PK and no-PK tables are silently skipped (`pkReason`). `fuzzy.match:v1` is a pinned MCP tool (GI-2).

**Branches (initial):**
- **B-α — port the live shape**: startup/scheduled SQL load from sources, in-memory index, cascade as contracted.
- **B-β — snapshot/artifact-fed vocabulary**: member values ride a versioned artifact (couples to R3-α/GI-5 and to Q-8's harvest boundary).
- **B-γ — search-engine-backed** (embedded Lucene-class index or a sidecar): tokenized/IDF/lemma-aware indexing as the primary mechanism, cascade as re-scorer.
- **B-δ — query-time passthrough to source** (LIKE/trgm pushdown) — the weird one; maps the freshness-vs-independence axis.

**Cross-links.** A (what's declared searchable), C (lemmatization at load vs query time), E (resolver is the hot consumer), GI-4/Q-8 (refresh vs commercial harvest), GI-2 (contract stability).

**Open.** Refresh discipline & staleness honesty; scale bounds (pilot's member cardinalities); category/namespace model.

## C · NLP service (`ttr-nlp`)

**Question.** The engine/language architecture, the Czech self-hosted stack (NameTag 3 + MorphoDiTa), model packaging/distribution in the open offering, and an API shaped for heavy multi-consumer use (FI-2).

**What exists today (fact base)** [recon §C]. `infra/nlp`: FastAPI Python service, engine SPI with five engines; ops = TOKENIZE, SENTENCE_SPLIT, LEMMATIZE, POS_TAG, DEP_PARSE, NER, DETECT_LANGUAGE; NORMAL mode routes per-op per-language, COMPARE fans out to all engines. Routing today: **cs** → morphodita (tokenize/lemma/POS), stanza (dep-parse), nametag (NER — no fallback; Stanza's cs bundle has no NER); **en** → stanza (+spaCy NER fallback); langid via lingua. **Critical fact: NameTag 3 and MorphoDiTa are NOT self-hosted today — the engines call UFAL's Lindat online API over HTTP (rate-limited 5/min, models `nametag3-czech`, czech-morfflex2.1-pdtc2.0 default)**; only Stanza (cs+en) and spaCy models are baked into the image (`/opt/nlp-models`, CPU-only torch). FI-3 is therefore a *delta*, not a port: bring NameTag 3 + MorphoDiTa in-process (both have Python bindings + downloadable model files) and make the offering offline. Resolver consumes the service over HTTP with a 24h LRU cache [recon §E].

**Branches (initial):**
- **C-α — port + harden the engine-SPI service**: keep the Python service, formalize `nlp.v1`, pin engine/model versions.
- **C-β — per-language model bundles as versioned artifacts**: models (NameTag 3, MorphoDiTa dicts, Stanza) distributed as pinned OCI/registry artifacts the chart mounts; image stays model-free.
- **C-γ — models baked into per-language images** (`ttr-nlp-cs`, `ttr-nlp-en`): simplest offline story, heaviest images.
- **C-δ — in-process libraries at each consumer** (no service) — the weird one; maps the R1-γ analogue for NLP.

**Cross-links.** FI-3/FI-4 (UFAL stack, legal parked), Q-9 (distribution mechanics), GI-1 (pinned models = determinism claim), B (lemmatizer client), D (chrono uses NER dates?), E (parse-before-resolve), R4 language architecture (language routing via langid).

**Open.** GPU/CPU + cold-start (Stanza cold start already forced resolver timeouts to 180s [recon §resolver-RQ6]); one service vs per-language pods; API granularity (ops bitmap vs task endpoints); CNEC label mapping brittleness [recon §resolver-RQ8].

## D · Grounding services

**Question.** How chrono/geo/money (and future grounders) enter the open lineage: the shared contract, the recipe→plan mechanism, external-data dependencies, and extensibility to "other services".

**What exists today (fact base)** [recon §D]. Far more converged than the tatrman docs suggested — a full DFP-side design corpus (`feature-grounding-*.md`, stages A1–A14, most code-complete `[~]` with deploy paused) and three live Kotlin services sharing **one** `grounding.v1` proto (`GroundingService.Ground/GetStatus`; `GroundResponse{status, GroundingResult, options[]}`; `GroundingResult` = `Normalized` + oneof `{ValueBinding | FilterRecipe | JoinRecipe}` + derived `sql_preview` + confidence + `source{RULES|LLM}`). Recipes are `plan.v1 Expression` fragments (catalog fns `period_start/period_end/geo_distance_m`, per-dialect lowering — "never LLM-composed SQL from primitives"). Semantic discovery reads the model's `semantics` hints (→ A). Determinism discipline: **`reference_datetime` always from the request, never `now()` in-service**; chrono + money fully offline-deterministic rule engines (Czech-strong: inflected months, `nad/pod/kolem/alespoň`, cs number formats); **geo's place resolution is the single online seam (Nominatim; RÚIAN deferred #137; PostGIS assumed for PG lowering; 90-day Postgres boundary cache off by default)**; LLM fallback (haiku, same JSON schema, tagged `source=LLM`) fires only on rules-miss and is **disabled by default**. Golem side: `GroundEntities` node + cascade consumption with a structural post-check that grounding conditions survived into the plan.

**Branches (initial):**
- **D-α — extract as-is** (three services + shared proto, rename to `org.tatrman.grounding.v1`), Nominatim as documented optional dependency.
- **D-β — grounding SPI**: one `ttr-grounding` host with grounder plugins (chrono/geo/money as built-ins; "other services" = new plugins), one MCP door.
- **D-γ — grounding folded into the resolver pipeline** (universal spans grounded inside resolve) — couples hard to E's topology.
- **D-δ — grounding as model-declared functions** (the weird one): grounders as TTR-level function vocabulary the translator lowers, no runtime services.

*Given the recon, the honest lean is α-shaped: the DFP corpus is a converged design with test-backed code; D's real forks are the deltas — extraction/rename mechanics, the Q-7 offline story, per-service PlanExpr/SqlRenderer duplication (extract a shared recipe lib?), and β's "other services" extensibility question.*

**Cross-links.** Q-7 (Nominatim offline story), GI-2 (`grounding.*:v1` reserved), A (fiscal calendars via `period_table` semantics, POIs via `kind:poi` — already model-declared), E (who calls grounding — agent or resolver), F (door shape; a thin `grounding-mcp` already exists, 3 tools, port 7153), plan-proto ownership (recipes emit `plan.v1` Expressions — ttr-plan-proto lockstep, tatrman-owned per TR-3).

**Open.** Fiscal-period *alignment* (recognizer treats "fiscal year" as calendar-year; true alignment = metadata period tables — is that enough for the hero's „fiskální čtvrtletí"?); FX at-current-rate policy vs only `amount_domestic` exercised at DF; which `[~]`-paused stages the extraction inherits as-is vs re-opens; "other services" (percent? quantity? duration?) admission rule.

## E · Resolver

**Question.** The rewrite's pipeline and placement — now with the recon fact that **the live resolver is LLM-in-the-loop on every path** (value-extraction on all modes, joint-inference on NORMAL) [recon §resolver-RQ1]. R1–R4 (resolver-rewrite.md) remain the fork skeleton; R2 needs re-cutting against reality.

**Fact base (recon, answers Q-1/2/3).** Pipeline: NLP parse (7 ops) → universal-label mapping (CNEC/OntoNotes → PERSON/LOCATION/ORGANIZATION/DATE/MONEY/MISC) → noun-head span proposal → LLM value-extraction filter (haiku) → parallel fuzzy per candidate column → threshold/ambiguity logic → bindings, or LLM joint-inference (sonnet) for function binding; HITL clarification with HMAC resume tokens + option pins. Topology: *consumes* NLP (HTTP), fuzzy (gRPC), metadata (gRPC, startup registry), llm-gateway (HTTP). Vocabulary: metadata `ListObjects(fuzzy_only)` + snapshot enrichment, startup-loaded, Caffeine TTL caches, no event invalidation.

**Branches.** R1 (placement: α standalone · β nlp-capability · γ library · δ fold-into-fuzzy) and R3/R4 carry from resolver-rewrite.md. **R2 (pipeline) re-cut needed:**
- **R2-α′ — deterministic re-derivation**: replace the LLM value-extraction filter with deterministic span selection (NER + POS + vocabulary-driven candidate gating); parity re-proven on the corpus. The P2-clean spine.
- **R2-ε — split resolver**: deterministic core (`resolve` = spans in → candidates/bindings out, no LLM) + the LLM steps (span filtering, joint inference) migrate agent-side (Golem's own calls #1a/#1b). Parity = end-to-end conversation parity, not service parity.
- **R2-ζ — port as-is behind the P2 line redrawn**: declare the resolver an *agent-tier* component (not deterministic spine), keep LLM calls inside it. Costs: contradicts GI-1's restatement; the two-call thesis's call #1 becomes non-deterministic by design.
- (R2-β/γ embedding tier: carried unchanged as the parked extension seam.)

**Cross-links.** Q-6 (what parity means across the split), GI-1/P2, D-γ (grounding-in-resolver), B (fuzzy hot path), C (parse), F (door shape decides who orchestrates), HITL/resume (token contract — does it survive the rewrite?).

**Open.** ENTITIES_ONLY vs NORMAL modes in the open surface; confidence semantics; clarification/HITL as part of the open contract (conformance suite already asserts refusal-over-guess).

## F · Exposure & orchestration

**Question.** Who orchestrates call #1 (agent-side cascade vs a server-side `resolve` door — Q-4/RQ-4), the MCP tool set, proto surfaces, and the parity/conformance mechanics (Q-5/RQ-5).

**What exists today (fact base).** The live shape is **F-α in production**: Golem's `GroundEntities` node fans out to the three grounding services (via a thin generic `grounding-mcp`, 3 tools), the resolver is called by agents (gRPC + an MCP `resolve` tool on the resolver's own port [recon §E.7]), and the planner cascade consumes `groundings` with a structural survival check [recon §D.6]. So a resolver MCP exposure already *exists* DFP-side — Q-4 is really "does it enter the **contracted RO-25 surface**".

**Branches (initial):**
- **F-α — agent orchestrates primitives**: doors stay `meta.search` + `fuzzy.match` + `grounding.*`; resolution pipeline is reference-Golem code; no resolve door.
- **F-β — a `resolve` MCP door**: `resolver.v1` exposed as a first-class tool (`resolve.bind:v1`?); third-party agents get call #1 without hand-rolling it; extends RO-25 surface pre-debut.
- **F-γ — a composite `ground+resolve` door**: one tool does universal grounding + domain binding (the "cascade" the DFP arc built as a Golem node, promoted server-side).
- **F-δ — resolution inside `query`** (the weird one): the query door accepts raw text and resolves internally — collapses the two-call thesis; maps why the seam exists.

**Cross-links.** GI-2 (surface extension rules), E (placement + split decide what a door can promise), D (grounding tools land in §2.2 of mcp-surface), Q-5 (corpus: `agents/resolver/eval/*.jsonl` seeds exist [recon §resolver-RQ7]), HITL over MCP (resume tokens in a tool contract?).

**Open.** Identity/OBO through resolve (H-2 applies to every door); does clarification round-trip fit MCP tool semantics; conformance fixtures for resolution (mcp-surface fixture schema already reserves `calls:` for resolution/grounding assertions).
