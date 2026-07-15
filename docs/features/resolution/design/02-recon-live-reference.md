# Recon — the live reference internals (2026-07-12)

> Fact sheet, not decisions. Read out of the connected working trees of `~/Dev/ai-platform` (and kantheon for context) on 2026-07-12, to answer Q-1/Q-2/Q-3 (= resolver-rewrite.md RQ-1..3) and to ground workstreams B–F before divergence. Section letters match the workstreams (§B fuzzy · §C nlp · §D grounding · §E resolver). File references are ai-platform paths unless prefixed.
>
> **Headline findings:**
> 1. **The live resolver is LLM-in-the-loop on every path** (value-extraction call in all modes; joint-inference in NORMAL) — the resolver-rewrite.md premise "port the live *deterministic* pipeline" does not match reality. The P2 split question is Q-6.
> 2. **NameTag 3 + MorphoDiTa are consumed via UFAL's Lindat *online* API today** (HTTP, rate-limited 5/min) — FI-3 (self-hosted, offline) is a delta to build, not a port.
> 3. **Grounding is a converged, test-backed DFP-side design** (one `grounding.v1` proto, recipe model over `plan.v1` Expressions, semantic discovery over model-declared `semantics` hints, deterministic rules-first with LLM fallback off by default) — workstream D inherits a design corpus, not a blank page.
> 4. **Member vocabulary reality:** fuzzy loads full member-value sets by SQL against source DBs (metadata-declared fuzzy columns → `SELECT pk, col`), hourly atomic-swap refresh, in-memory index; the resolver's registry loads from metadata once at startup. Nobody consumes snapshots yet (R3-α has no live precedent).

---

## §E · Resolver (`agents/resolver`, `com.tatrman.resolver.v1`)

### E.1 Pipeline (ResolverGraph.kt — hand-rolled coroutine loop, not a Koog DSL graph)

NLP parse happens **before** the graph (`buildResolverContext`, Main.kt): ops = TOKENIZE, SENTENCE_SPLIT, LEMMATIZE, POS_TAG, DEP_PARSE, NER, DETECT_LANGUAGE. Fresh-question node order:

1. **detectLangAndParse** — marker only (parse already done).
2. **extractUniversal** — NER label → `UniversalEntityType` via `UniversalLabelMapping` (no LLM).
3. **proposeDomainSpans** — NOUN/PROPN token heads from the dep parse; TODO: multi-word nominal phrases deferred (single-head only).
4. **filterRelevantSpans** — **LLM call #1** (cheap tier / haiku, temp 0) with the `value-extraction` prompt: extracts candidate *values* tagged with fuzzy-column refs; same-table sibling columns expanded (LLM "flaky about code-vs-name" workaround).
5. **fuzzyMatchSpans** — parallel gRPC fuzzy calls per candidate column namespace (no LLM).
6. NORMAL: **jointInference** — **LLM call #2** (fast tier / sonnet) binding function + args → HITL-or-emit. ENTITIES_ONLY: **entitiesOnlyAssemble** — pure threshold/ambiguity logic (threshold 0.5, ambiguity gap 0.05, exact-match 0.9999) → bindings or `AwaitingClarification`.

**No fully LLM-free path exists** — ENTITIES_ONLY still makes call #1. LLM failure degrades (cheap→resolves nothing; fast→confidence 0.0). HITL: HMAC resume tokens (key rotation, additive `optionPins`); resume with a matching pin binds directly (confidence 1.0, no re-fuzzy).

### E.2 Topology (Q-2 answer)

The resolver **consumes services**; it embeds only orchestration + decision logic:
- **NLP** — HTTP client (`nlp-service:8000`), MCP-style `/v1/analyze`.
- **fuzzy-matcher** — gRPC (`fuzzy-matcher:7203`), `Match` with algorithm cascade `[LEVENSHTEIN min 0.98, TATRMAN min 0.91]` (config-overridable).
- **metadata** — gRPC (`metadata:7212`), **startup-only** registry load.
- **llm-gateway** — HTTP, OpenAI-style chat completions.

Embedded: span selection, sibling-column expansion, entity-identity dedup, thresholds/ambiguity, HITL token mint/verify, binding assembly.

### E.3 Vocabulary & caches (Q-3 answer)

- Registry = `MetadataClient.loadDefaultEntityTypes()`: `ListObjects(kind=column, fuzzy_only=true)` (authoritative; category = `fuzzy_matcher_namespace`) + `GetSnapshot` (best-effort enrichment: entity grouping, aliases, field labels; failure ⇒ flat registry, degrades not fails). **Loaded once at startup**; caller-supplied registry wins per-request.
- `ResolverCache` = two Caffeine caches: `nlpCache` (1000, TTL 24h, key `lang|ops|text.hash`), `resolutionCache` (1000, TTL 1h — defined but apparently unwritten in the live read paths). No event-driven invalidation anywhere — TTL/LRU only. NLP results also ride inside resume tokens (stateless resume).

### E.4 Proto surface (`resolver.proto`)

`ResolverService.Resolve(ResolveRequest) → ResolveResponse`. Request: `conversation_id`, oneof {FreshQuestion{text,locale} | ResumeAnswer{token, selected_option_id, free_text_answer}}, `Registry{function_specs, entity_types}`, `ResolveContext`, `ResolveMode{NORMAL, ENTITIES_ONLY}`. Response: `AnalyzeResponse parse` (always), oneof {Resolution | AwaitingClarification}, trace_id, elapsed_ms, `messages=99`. `Resolution{function_id, args_json, EntityBinding[], confidence, alternatives, rationale}`; `EntityBinding` = span + oneof {Universal(entity_type, raw_text, normalized_value, source_engine) | Domain(entity_type_ref, raw_text, resolved_id, resolved_label, FuzzyCandidate[])}. `UniversalEntityType` = PERSON, LOCATION, ORGANIZATION, DATE, MONEY, MISC.

### E.5 Universal label mapping

`common` (PER/LOC/ORG/DATE/TIME/MONEY/AMOUNT/MISC) + `spacy` OntoNotes extras (GPE/FAC→LOCATION; NORP/PRODUCT/…→MISC) + `nametag` **CNEC 2.0 by leading letter** (P→PERSON, G→LOCATION, I→ORGANIZATION, T→DATE, A/M/O/N/C→MISC) — explicitly a heuristic, brittle to unexpected codes (all-unknown → MISC). `normalized_value` deliberately out of scope: **grounding services own semantics**.

### E.6 Config highlights

hitl confidence-threshold 0.75, max-rounds 3; clarification max-entity-options 20 / max-intent-options 3; graph timeout 180 s (bumped for cold Stanza/UFAL); prompts git-backed (`Tatrman/ai-models.git` via JGit, bundled fallback, `POST /v1/refresh`); OTEL off by default; known config drift (README/defaults vs live conf ports).

### E.7 Eval harness (Q-5 material)

`eval/run_eval.py`: function-call accuracy (exact functionId), entity precision/recall/F1; ENTITIES_ONLY pass-rate gate 80%; CI thresholds 0.70/0.60/0.60. Corpus: `seed.jsonl` (50 Czech questions with expected tokens/lemmas/entities/function/args), `ucetnictvi_entities_only.jsonl` (12 binding cases). NORMAL cases exercised through the resolver's **MCP tool** `/mcp/v1/tools/resolve`; ENTITIES_ONLY through REST `/v1/resolve`. Gated live Kotest specs: value-extraction recall ≥ 0.8, E2E bind-rate ≥ 0.5.

### E.8 Churn / fragility signals (Q-1's "what must the rewrite do differently")

CNEC letter-mapping brittleness · multi-word span TODO · prompt-shape churn (noun→value extraction rewrite; sibling-column workaround) · config drift · REST→gRPC migration scar (GH#71) · elapsed_ms epoch bug scar · OTEL init-ordering hazard · legacy resume path · unused resolutionCache.

---

## §B · Fuzzy-matcher (`services/fuzzy-matcher`, `com.tatrman.fuzzy`)

### B.1 Vocabulary loading

Two `LoaderSource` strategies (`null` on failure ⇒ previous cache preserved): **static** (config map category→SQL — the default) and **metadata** (`ListObjects(kind=column, fuzzyOnly=true)` paginated → per-column table PK via `getTableDetail` → `SqlComposer` builds `SELECT pk, col FROM table` (identifier-regex validated; PG/MSSQL quoting) → run **against the source DB** → keyed by category). `PkResolver`: only single-column-PK tables load (`no_pk`/`composite_pk` skipped). Source guard: namespace assertion (`wrong_source` skip). **Refresh:** background loop, default 3600 s; load → lemmatise → atomic swap → rebuild indices; unauthenticated `POST /refresh` for operator/CI reload.

### B.2 Matching internals

- `AlgorithmType` = LEVENSHTEIN, DAMERAU_LEVENSHTEIN, JARO_WINKLER, HAMMING (aliased to JW), **TATRMAN = the token-based matcher** (the alg2 of `requirements-2.md`/`alg2-plan.md`).
- TATRMAN: tokenize query → inverted-index candidate seeding (fallback: score all) → per-candidate score on **two axes** (folded surface tokens, folded lemma tokens; max wins) → per-token best Levenshtein (distance threshold 0.20) → **IDF weighting** (GH#69; smoothed `ln((N+1)/(df+1))+1`, per matched candidate token; toggle default on) → **word-order bonus** `1.05^orderedPairs` capped 1.5.
- `TextNormalizer.fold()` = lowercase + NFD + strip combining marks (single normalization point; "Zákazník"→"zakaznik").
- Cascade = decision gates: first algorithm whose top-1 ≥ its minScore wins (precision-first, recall-fallback).
- `DistanceCache` 10k-entry order-independent Levenshtein cache.

### B.3 Lemmatization hook

`NlpLemmatizer` → `POST {nlp}/v1/analyze {ops:[LEMMATIZE], language: cs}`; any failure ⇒ folded surface forms (degradable). Applied at **load time** (candidate lemma tokens per refresh) *and* **query time** (query lemma tokens). **Disabled by default** (`nlp.enabled=false`) — the lemma axis is currently a no-op at the pilot config sampled.

### B.4 API & category model

gRPC `FuzzyMatcherService.Match` (port 7203; default limit 10, max 50) + secured REST `POST /match` (7103). `category` scopes to one loaded column (per-category token index + cache), lowercased-normalized; **explicit-but-unknown category returns EMPTY** (must not fall back to global — cross-column value-leak bug fixed); `null` = global index.

### B.5 Storage & scale

Pure in-memory (`ConcurrentHashMap<category, List<Candidate>>` + indices); DB is only the *source* (Hikari pool max 3, repeatable-read). Full column value sets in memory, no count bound; ERP categories at the pilot: `product` (QZBOZI_DF), `customer` (qsubjadr_df). README stale ("Python microservice" — it's Kotlin/Ktor).

---

## §C · NLP service (`infra/nlp`, Python 3.13 / FastAPI)

### C.1 Architecture

`NlpEngine` Protocol (`supported_languages`, `supports(lang,op)`, `analyze`) + `EngineRegistry` with per-op-per-language routing. `Orchestrator.analyze`: optional langid detect (fallback `cs`) → NORMAL: group ops by routed engine, run, merge/dedup/sort → COMPARE: fan out to all supporting engines (`by_engine` map). REST `POST /v1/analyze` (camelCase mirror of `com.tatrman.nlp.v1 AnalyzeRequest/Response`); `/healthz`, `/readyz`, `/version`; OTEL + Prometheus. Port 7117.

### C.2 Engines & routing (nlp-config.yaml — README's routing table is stale)

| Engine | Langs | Ops | Model source |
|---|---|---|---|
| morphodita | cs | tokenize, sentence-split, lemma, POS | **UFAL Lindat online API** (`/morphodita/api/tag`, vertical output; empty `model` param ⇒ server default czech-morfflex2.1-pdtc2.0; PDT tag → UD POS first-char map; lemma sense-suffix stripping) |
| nametag | cs, en | NER | **UFAL Lindat online API** (`/nametag/api/recognize`, BIO vertical; models `nametag3-czech`, `english-conll-200831`; **rate-limited 5/min in-process**, 30 s timeout, 3 retries) |
| stanza | cs, en | tokenize, lemma, POS, dep-parse (+ en NER) | baked in image at `/opt/nlp-models/stanza` (**cs bundle has NO NER**) |
| spacy | en | tokenize, NER | `en_core_web_md`, baked |
| langid | 11 langs | detect-language | lingua library, local |

Routing: cs → morphodita (token/lemma/POS), stanza (dep-parse), nametag (NER, **no fallback**); en → stanza (NER fallback spaCy); detect → langid. `default_language: cs`.

### C.3 Packaging

Multi-stage Dockerfile (python:3.13-slim, `uv sync --frozen`, CPU-only torch): a `models` stage downloads Stanza cs+en and spaCy at **build time**, runtime copies `/opt/nlp-models` (appuser-writable — Stanza writes locks). **NameTag/MorphoDiTa models are NOT in the image** — they are remote HTTP calls today. This is the exact gap FI-3 closes: both UFAL tools ship Python bindings (`ufal.nametag3` / `ufal.morphodita`) and downloadable licensed model packs; the design must choose bake-vs-artifact-vs-volume (Q-9).

---

## §D · Grounding (`services/{chrono,geo,money}`, `feature-grounding-*.md`, `grounding.v1`)

### D.1 Design intent & contract shape

Time/geo/money conditions are client-specific but rule-computable ⇒ resolved **deterministically**; LLM only as in-service fallback; SQL built from **named Translator catalog functions** (`geo_distance_m`, `period_start/period_end`) with per-dialect lowering — never LLM-composed from primitives. Contract = "B+preview": `GroundResponse{status: OK|AWAITING_CLARIFICATION|UNGROUNDABLE, GroundingResult, options[]}`; `GroundingResult` = `Normalized{DateTimeInterval|GeoPoint|GeoShape|MoneyValue}` + oneof `{ValueBinding | FilterRecipe | JoinRecipe}` + **derived** `sql_preview` (rendered from the same `plan.v1.Expression` tree the recipe carries) + confidence + `source{RULES|LLM}` + explanation. `GroundingContext{reference_datetime (always from the turn, never now()), timezone, locale, default_currency, here_place_ref, fx_policy, tolerance_pct}`. RecipeBuilder→PlanExpr→SqlRenderer triple is **duplicated per service** (extraction candidate).

### D.2 One proto, three implementations

`com.tatrman.grounding.v1 GroundingService{Ground, GetStatus}` shared by all three (keeps the MCP wrapper generic). `GroundRequest{span_text, question_text, EntityKind{DATE_TIME|LOCATION|MONEY}, package, context, anchor_candidates, correlation_id, continuation{clarification_answer_id}}`. Recipes reuse `com.tatrman.plan.v1` wholesale. `GetStatus` → readiness + capability map (`postgis`, `llm_fallback`, `metadata`).

### D.3 Semantic discovery (the model coupling)

Services discover groundable columns via metadata **SemanticHints**: `ListObjects(semantic_role/semantic_kind + kind + package)` then `GetObject` for entity name/params. Roles: chrono `period_start/period_end/period_code` (kind `period_table`), `event_date/due_date/posting_date/document_date`; geo `geo_lat/geo_lon`, kind `poi` + entity name-attribute; money `amount/amount_domestic/currency_code`, kind `fx_rate` (+ validity columns). Recipe shape is metadata-driven: period table present ⇒ JoinRecipe; calendar-aligned ⇒ FilterRecipe over catalog fns. Known gap: `SymbolRef.qname` unpopulated in tatrman 0.9.0 ⇒ name-only refs.

### D.4 Per-service facts

- **chrono** — Duckling-approach rule table in Kotlin (prioritized, most-specific-first, all vs `reference` date, intervals half-open). Czech-strong: inflected month stems diacritic-insensitive, `dnes/včera/letos/minulý/poslední N dní`, fiscal words `fiskální/účetní rok` (recognized as calendar-year interval, conf 0.95 — true fiscal *alignment* comes from period tables downstream). Ambiguity → conf 0.6 + alternative → clarification. Threshold 0.6.
- **geo** — `GeoSpanParser` (cs+en; Distance-with-radius vs Containment; `u/nad/pod` kept inside names — "Ústí nad Labem" survives) → place resolution: **Nominatim** (`/search`, polygon_geojson, UA-required; 429/5xx ⇒ gRPC UNAVAILABLE fail-loud) → JTS GeoJSON→WKT+bbox → optional **Postgres boundary cache** (Flyway, 90-day TTL, off by default ⇒ in-memory) → **ModelPoiResolver** chained *after* the geocoder (POIs matched at query time via `poi.name = {place}`; service never reads business rows). Recipes: distance ⇒ FilterRecipe `geo_distance_m(...) <= {r}` (JoinRecipe for POI anchor); containment ⇒ **bbox prefilter** + polygon WKT in `Normalized.shape` (precise point-in-polygon = Golem post-filter; `geo_within` deferred). RÚIAN (ČÚZK) deferred (#137); Google Maps ruled out (ToS); PostGIS assumed on PG or "geo goes dark" (accepted, capability-surfaced).
- **money** — comparators (`nad/pod/alespoň/nejvýše`, GE/LE before GT/LT), tolerance (`kolem` + `tolerance_pct` band), currencies (symbol/ISO/name table incl. Kč/korun; **no external FX feed** — FX = client's model-declared `fx_rate` table via JoinRecipe with validity window, fails loud if time-versioned without as-of), locale-aware separators (unset ⇒ cs, avoiding the 100× comma error), BigDecimal. Multiple amount columns ⇒ Clarify. At the pilot only `amount_domestic` is exercised.

### D.5 LLM usage

Each service: thin `LlmGatewayClient` (haiku default). Invoked **only** on recognizer-null or confidence < 0.6 (geo: only when the parser can't classify at all); returns the same `GroundingResult` JSON schema, structurally validated, tagged `source=LLM`; **disabled by default** (empty gateway URL ⇒ rules-only). Targets: 0 planner-LLM involvement in hero conditions; fallback rate < 10 %.

### D.6 Stage status & the Golem side (A1–A14)

A1 ariadne-swap ✔ · A2 resolver-fix ✔ · T1–T6 tatrman semantics block ⏳ external · A3 metadata-surface ✔ · A4 df-annotation ⛔ BA-gated (`YamlToTtrCli` lacks `semantics{}` passthrough) · A5 catalog-functions ✔ · A6 dialect-lowering [~] · A7 grounding-proto ✔ · A8 chrono [~] 70 tests · A9 geo [~] 67 tests · A10 money [~] 57 tests · A11 grounding-mcp [~] (thin wrapper, port 7153, 3 tools) · A12 golem GroundEntities node [~] (routing, asyncio fan-out, GroundingContext assembly, load-bearing-clarification predicate "WILL be tuned", 247+26 tests) · A13 cascade consumption [~] (recipe params merged into regex/pattern tiers — grounding wins; free-SQL tier gets `sql_preview` as hard constraint + **structural post-check** `grounding_conditions_survived` with miss penalty) · A14 eval-rollout [~] (109 bulk + 21 E2E corpus; Grafana/rollout drafts). Hard external gate: tatrman grammar 4.2 (semantics) merge.

### D.7 Determinism / offline summary

chrono + money: fully deterministic and offline (rules + metadata only, no clock reads). geo: deterministic once a place resolves; **place resolution is the single online seam** (Nominatim, mitigated by the boundary cache). LLM fallback: the only other non-determinism, off by default. ⇒ Q-7's shape: what does the *open, offline-capable* geo story look like (bundled gazetteer? RÚIAN pack? cache-primed? capability-degraded)?
