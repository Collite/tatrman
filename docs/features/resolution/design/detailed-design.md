# Resolution & Grounding — Detailed Design

> **Audience:** an engineer, architect, or business analyst who was *not* in the design sessions and wants to understand the understanding layer of Tatrman Server — what it does, why it is shaped the way it is, and how the pieces fit. This document is the future manual. It is deliberately exhaustive and prose-first; where `design.md` is the compressed input to planning, this is the version you read to *learn* the system. Every major decision is cited to its control-room log entry (`RS-n`) so you can trace the reasoning and the roads not taken.
>
> **Companion documents:** `design.md` (compact, for `/planning`) · `00-control-room.md` (the decision log and dashboard) · `01-design-space-map.md` (the full option tree) · `02-recon-live-reference.md` (how the live pilot system actually works today) · `03-A`..`08-F` (per-workstream option catalogues).

---

## 1. What this layer is for

Tatrman answers natural-language questions against a modeled data estate. The architecture separates a question's journey into **two calls**. Call #1 — the subject of this document — turns a user's words into a precise, auditable set of *bindings*: this word is that dimension member, that phrase is a time interval, this verb means that measure. Call #2 takes those bindings and produces SQL. The separation is the point: because call #1 hands call #2 a resolved, provenance-carrying structure, **call #2 never has to guess**. The seam between them is what makes a Tatrman answer auditable — you can always ask "why did the system think 'Octavie' meant the Škoda Octavia product?" and get a deterministic answer.

The understanding layer is not one component but four cooperating ones, plus the language surface they all read from:

- **`ttr-nlp`** — linguistic primitives: tokenize, lemmatize, tag parts of speech, parse dependencies, recognize named entities, detect language. The Czech morphology engine.
- **`ttr-fuzzy`** — the matcher: given a span of text, which known vocabulary values does it match, and how well? Both *member* values (the actual data — product names, customer names) and *declared* vocabulary (what the model says things are called).
- **the grounding services** (`chrono`, `geo`, `money`) — deterministic resolvers for *universal* values: "poslední fiskální čtvrtletí" → a concrete date interval; "pražských pobočkách" → a place; "nad milion" → a money comparison.
- **`ttr-resolver`** — the composer: it runs the parse, maps universal spans, gates domain spans through fuzzy, applies thresholds and identity logic, and emits bindings — deterministically, with no LLM inside it.
- **the TTR-M vocabulary surface** — what the conceptual model *declares* so that all of the above is governed by the model rather than by service configuration or prompt folklore.

And one component that sits deliberately on the other side of a line:

- **the Resolving Agent** (in kantheon, the agent tier) — the generative wrapper. It reuses `ttr-resolver`'s deterministic core, and when the core cannot fully bind, it escalates to language models. This is where the LLMs live, because this is where they are *allowed* to live.

### Why these four were designed as one effort

They are one problem wearing four hats. The resolver *consumes* fuzzy, NLP, and grounding. Grounding and search both hang off model-declared vocabulary. The language architecture — how Czech morphology is handled, how a second language would be added — cuts across all four. Designing them separately would have meant deciding the same shared forks (where does vocabulary live? what is deterministic? how does a snapshot flow?) three or four times, incompatibly. So the effort opened as one design (RS-1) with a full convergence now rather than a diverge-only pass deferred to later planning (RS-2), because the live reference implementations were connected and could ground the decisions in evidence rather than speculation.

---

## 2. The load-bearing principle: the determinism line

Everything in this design orients around a single rule inherited from the platform (GI-1, principle P2): **no LLM in the deterministic path.** The components in the server tier may be *statistical-deterministic* — they run pinned model files whose outputs are reproducible — but they are never *generative*. A large language model may propose, but it may not be part of the verdict.

This sounds like a constraint on implementation. It is really a constraint on *architecture*, and it is the reason the resolver is split (see §7). The live pilot resolver, when we surveyed it (recon, `02`), turned out to be **LLM-in-the-loop on every path**: a "value-extraction" model call filters spans on all modes, and a "joint-inference" model call binds intent on the normal mode. There was no LLM-free path at all. The premise of the earlier resolver-rewrite plan — "port the live deterministic pipeline" — did not match reality, because the live pipeline was not deterministic.

The design's answer is not to make the LLM deterministic (you cannot) but to draw a **line** and put the LLM on the far side of it. Below the line: the deterministic core, the grounding services, the primitives. Above the line: the Resolving Agent. The line is not an abstraction — it becomes a physical boundary in the system, and it becomes the rule for what may be exposed as an MCP door (§8): **the door line is the determinism line.** A third party calling the `resolve` door gets a promise of determinism precisely because the door exposes only what is below the line.

---

## 3. The vocabulary surface (workstream A)

Before any component can match or ground anything, the model has to *say* what things are called. This is framing input FI-5: specific vocabulary — dimension members, entity names, domain terms, measure verbs — is declared in the conceptual model, not configured in a service or hidden in a prompt. The reason is governance: resolution is auditable *because* the vocabulary that drives it is model-declared, versioned, and reviewed.

### 3.1 What existed, and why it was redesigned

TTR-M already carried three vocabulary-bearing surfaces, but they had grown by accretion rather than design:

1. a **`search { … }` block** with sub-properties `keywords, patterns, descriptions, examples, aliases, searchable, fuzzy` — the findability surface;
2. a **`semantics { role | kind | params }` block** (grammar 4.2, in flight) — the grounding-hint surface, where the model declares that a column is a `period_start` or a `geo_lat` or an `amount`;
3. **naming and labels** — entity `aliases`, `displayLabel`, `valueLabels` (localized meanings of coded values), localized role labels.

Two gaps made this insufficient. First, **`md` kinds carried no vocabulary at all** — there was nowhere to say that the verb "utržili" (we earned/took in revenue) reaches the revenue measure. Today that binding happens LLM-side, from labels and descriptions, which is exactly the prompt-folklore FI-5 rejects. Second, the surfaces overlapped confusingly: an entity could carry `aliases`, `search{aliases}`, and `semantics{}` all at once, and the first two meant nearly the same thing.

Midway through convergence, Bora recorded a decisive piece of ground truth (GI-8): the current surface is **legacy carried over from the original YAMLs** to avoid information loss when the language was bootstrapped. It was never *designed*; the `search{}` block is in practice unused except for its `fuzzy` flag. This licensed a redesign rather than a ratify-in-place.

### 3.2 The lexicon model (RS-9)

The redesign follows a pattern TTR already uses elsewhere. Data bindings in TTR have a dual form: a standalone canonical form (`er2db_*` definitions) and an inline sugar form (a `binding:` property on a definition that desugars to the canonical entries). Vocabulary gets the same dual:

- The **canonical form is a `lexicon` model** — standalone definitions of `term`, `pattern`, and `example` classes, each targeting a reference into the `er` (entity-relational), `db` (database), or `md` (multidimensional) layers.
- The **inline form is a `lexicon{}` block** on a definition, which desugars to canonical entries.

A sketch (final syntax is a grammar-master feature, Q-13):

```ttr
// canonical form — a lexicon model, mirroring how er2db_* is binding's canonical form
model lexicon

def term    trzba { for: md.measure.net,  forms: ["tržba", "tržby", "obrat", "utržit"] }
def pattern nazev { for: db.query.by_name, match: "název .*" }
def example q1    { for: md.cubelet.sales, text: "Kolik jsme utržili za Octavie…" }

// sugar form — inline, desugars to the canonical entries above
def measure net {
  domain: md.Money, class: additive, aggregation: sum,
  lexicon { terms: ["tržba", "obrat", "utržit"] }
}
```

The governing boundary sentence, worth memorizing: **lexicon = what things are called; `search{}` = how retrieval treats them.** With vocabulary moved to the lexicon, the `search{}` block slims to pure retrieval configuration — `searchable` and `fuzzy` — and the in-flight search-block feature's flag relocation *is* its end state. The old sub-properties migrate (RS-32): `aliases` and `search.{aliases,keywords}` become lexicon **term** entries; `search.patterns` become **pattern** entries (load-bearing — pattern queries depend on them); `search.examples` become **example** entries (they feed agent prompts and conformance fixtures); `search.descriptions` fold into the single `description` prose home (pending a check that no consumer reads it distinctly).

Why the lexicon model rather than a single new `vocabulary{}` block or vocabulary-outside-the-model? A single block would have landed a third grammar surface while two were in flight, and would have lost the standalone/canonical form that glossary workflows need (a business analyst owning a domain glossary without touching structure). Vocabulary outside the model breaks FI-5 outright — it becomes ungoverned, unversioned, unreviewed. The canonical/sugar dual gives both: a place for rich standalone glossaries *and* the ergonomic inline shorthand, and it gives every downstream consumer (the snapshot, the resolver's `EntityTypeSpec`, the meta doors) exactly one canonical shape to read.

### 3.3 Measure and intent vocabulary — closing the „utržili" gap (RS-10)

`md` kinds become legal lexicon targets. A measure can carry inline terms, or a standalone `term` entry can point at it. This is consumer-agnostic by construction: the same declared vocabulary feeds retrieval (`meta.search`), the agent's prompt context, and any future deterministic binder. It respects the two-call thesis — the model *declares*, and a consumer (today an LLM, tomorrow perhaps a deterministic tier) *binds*.

A named future arc was recorded here: intent and measure vocabulary should one day be covered by the conceptual model *itself*, not merely declared alongside it. RS-10 is the declared-surface step, not the endpoint. What was rejected: typed lexicon mappings with part-of-speech and weight *now* (premature precision before evidence that flat terms underperform — the schema can grow those fields additively); LLM-only intent (prompt folklore, the FI-5 violation); and learned vocabulary mined from logs (a real idea, but an enrichment loop for after the debut, and it brushes the commercial-harvest boundary).

### 3.4 How declared vocabulary speaks Czech (RS-11)

Czech is heavily inflected. "Octavie", "Octavii", "Octavií" are all the same product; "pražských" is an inflected form of "pražský". A naive design would demand the model author list every surface form. The design instead splits the labor along a linguistic fault line:

- **Inflection is computed.** MorphoDiTa (via `ttr-nlp`) lemmatizes both the declared vocabulary (at index-build time) and the query span (at query time), so `Octavie` and `Octavii` both reduce to `octavia` and match. The model author never lists inflected forms.
- **Derivation and cross-language are declared.** Morphology does *not* bridge derivation: `utržili` lemmatizes to the verb `utržit`, never to the noun `tržba`. No runtime engine can conjure the verb→measure link; it must be declared (that is what the lexicon `forms` list is for). Likewise cross-language synonyms are declared, not computed.

The memorable formulation: **declare across derivation and languages; compute across inflection.**

Locale rides the lexicon *unit*, not individual entries: per-locale lexicon files or models, following the `db … schema` header precedent — a Czech lexicon file a Czech analyst owns, an English one beside it, the same targets, no locale keys nested inside entries. Adding a language is adding files, not reshaping every consumer's schema. Inline sugar entries default to the deployment's base locale. What was rejected: entry-level locale maps (a nested shape every consumer would pay for) and DeriNet-based derivational expansion (a new engine and a licensing sibling of the UFAL question, and it overgenerates — `tržní` is not revenue-intent).

### 3.5 What the model says about member *values* (RS-12)

Members — the actual dimension values, the hundreds of thousands of product rows — live in the estate's data, not in the model. The question is what the model may declare *about* them, between "nothing" and "everything". The answer is three tiers, composed:

- **Columns floor (α).** The model declares *which columns* are member vocabularies (the `search{fuzzy}` flag) plus which attribute is the name and which is the code. The values themselves stay data. This is scale-proof — 100,000 products never enter git.
- **`valueLabels` (β).** For small coded/enum-like attributes (status, type, category), `valueLabels` — already localized — *is* the member vocabulary. Per-value alias widening rides the lexicon arc.
- **Declared alias tables (γ).** Estates that *have* synonym or multilingual-name tables declare their *meaning* via `semantics{kind: alias_table}` — the same closed-vocabulary-evolution pattern that `fx_rate` and `period_table` already use, needing no grammar bump. The fuzzy loader ingests these declared alias tables alongside the primary name column.

What was rejected: mirroring members into model text (violates members-are-data at scale and creates a drift-versus-the-estate sync problem — though it may return as curated standard-library reference packages, never for live estates).

---

## 4. The NLP service (workstream C)

`ttr-nlp` provides the linguistic primitives. Framing input FI-2 sets its ambition: it is not a resolver-private helper but a *heavily used* service across the family, so its design must anticipate many consumers.

### 4.1 The starting reality

The live service (recon `02` §C) is a Python FastAPI service with an engine SPI and five engines, routing per-operation per-language. But the two Czech engines that matter — NameTag 3 (named-entity recognition) and MorphoDiTa (morphology) — are **not self-hosted**. They call UFAL's Lindat online API over HTTP, rate-limited to five requests per minute, with an empty `model` parameter so the server picks its own default. Two consequences: the pilot's question text *leaves the premises* on every parse (a privacy and egress fact, not merely a rate-limit nuisance), and the path is not even version-pinned. Framing input FI-3 fixes both: bring NameTag 3 and MorphoDiTa in-house, self-hosted and offline.

### 4.2 Engine-free front, per-engine backends (RS-3, RS-8)

The chosen shape is a **thin, engine-free front** — the `nlp.v1` contract, routing, and language identification, and nothing else: no torch, no model files. Every model-bearing engine — MorphoDiTa, NameTag 3, Stanza, spaCy — runs as its own backend service. The front is a slim router; each backend scales and is placed independently (a CPU pool for morphology, an optional GPU node for the transformer NER). Stanza is explicitly a backend too, not a special case, even though it is heavy — it sits on the Czech hot path because Czech dependency parsing routes to it, and the resolver's span proposal consumes that dependency parse.

Why not one fat service? Because the heaviest engine would dictate the whole pod, scaling would be all-or-nothing, and one engine's cold start would gate the entire service's readiness. Why not in-process libraries at each consumer? Because NameTag 3 is Python-only — the Kotlin resolver and fuzzy-matcher cannot embed it — and N in-process copies means N pinning disciplines. The live system, as it happens, is *already* this shape by accident: NameTag and MorphoDiTa are remote backends today, just pointed at UFAL instead of in-cluster. The design makes the accident deliberate and points the backends inward.

### 4.3 The Czech stack, self-hosted (RS-4)

Both MorphoDiTa and NameTag 3 run as self-hosted upstream servers deployed as in-cluster backends — MorphoDiTa has an in-tree `src/rest_server`, NameTag 3 ships `nametag3_server.py`. The existing HTTP engine adapters simply repoint from `lindat.mff.cuni.cz` to in-cluster URLs; it is the smallest possible code delta because the adapters already speak the protocol. The in-cluster hop is negligible against the LLM calls a turn makes, and the bulk-lemmatize case amortizes it.

Lindat does not disappear — it survives as a **labeled dev/eval tier**. But a Lindat-pointed deployment is explicitly *non-conformant* for any parity or determinism claim: the question-text egress, the 5/min limit, and the unpinned models all disqualify it. The capability matrix (§4.5) must surface `remote + unpinned` so this is visible, not silent.

### 4.4 Models baked into backend images (RS-5)

Each backend image carries exactly its own model, digest-pinned — offline by construction, one pinned artifact per backend. This extends the pattern the live Dockerfile already uses for Stanza and spaCy. The earlier lean had been toward mounted model *artifacts* (slim images, model updates decoupled from code), but the per-engine-backend decision changed the economics: with one small single-purpose image per backend, "baked in" is no longer the fat-image objection it was. The mounted-artifact scheme keeps a named revisit trigger — multilingual growth or per-estate model selection would make baked images combinatorial. The redistribution of CC BY-NC-SA UFAL models inside published images is the concrete shape of the parked FI-4 legal question (public GHCR versus a restricted registry for the UFAL-model backends).

### 4.5 The API and the capability matrix (RS-6, RS-7)

The contract is `org.tatrman.nlp.v1`: a single ops-bitmap `Analyze` call (text + optional language + a set of operations + mode), formalized on gRPC, with REST kept only for local development and health checks. One parse serves many operations in one engine pass — the resolver's seven-operation call is one pass per engine, not seven round-trips. Crucially, a **bulk/batch lemmatize** call is added: the fuzzy-matcher re-lemmatizes entire member vocabularies at every refresh, and doing that over per-string HTTP calls is exactly what forced the pilot to disable the lemma axis. The batch shape (sized by Q-11) must hold at both the front and the backend hop. Consumers keep ownership of caching; responses echo model versions so caches can key on them.

The language story is **config-routed operations validated and surfaced by a capability matrix** — a `lang × op → engine + model version` map exposed via GetStatus and echoed in responses. Any language gets an honest degrade floor (tokenize + fold + language-id); morphology and NER are enrichments a deployment has models for. Consumers — the resolver first among them — branch on the matrix rather than assuming capabilities. The language-plugin SPI is deferred until a second production language is real, because a second language is what actually defines the interface; abstracting against two look-alike bundles now would be a guess.

---

## 5. Member & entity search — `ttr-fuzzy` (workstream B)

### 5.1 The live matcher

The live fuzzy-matcher (recon `02` §B) reads model-declared fuzzy columns from metadata, resolves single-column primary keys, runs `SELECT pk, col` against the *source* database, and loads full member-value sets into an in-memory index — a per-category token index plus a distance cache — refreshed hourly with an atomic swap. Matching folds diacritics (lowercase, NFD, strip combining marks) and runs a cascade: the first algorithm whose top match clears its minimum score wins. The token-based matcher (internally "TATRMAN") builds an inverted index over surface and lemma tokens, scores per-token Levenshtein with IDF weighting and a word-order bonus. The lemma axis exists but is disabled by default because it depended on the rate-limited Lindat call — which the NLP self-hosting (C) fixes.

### 5.2 Fuzzy becomes *the* vocabulary matcher (RS-15)

The pivotal decision. Workstream A creates declared-vocabulary streams (lexicon terms, valueLabels) beside the member values. Who matches what? The design consolidates: **`ttr-fuzzy` matches everything** — member values *and* declared vocabulary — through one Czech-aware engine, with results **source-tagged** so a member-candidate (which resolves to a data row) is distinguishable from a vocabulary-candidate (which resolves to a model target). `meta.search` remains for catalog and browse UX, but *resolution-grade* matching lives in one place with one quality bar.

The alternative — fuzzy matches members, `meta.search` matches model vocabulary — would have meant two engines with two quality bars, forcing the resolver to query two services with two semantics and merge them, and "utržili"→measure matching would have inherited `meta.search`'s weaker plain scorer. Consolidation means every string in the system is matched by the same fold+lemma+IDF+cascade engine, the resolver gets one candidate API, and A's vocabulary instantly inherits B's quality.

This decision has a systemic consequence: declared vocabulary reaches fuzzy via the **snapshot read at refresh**, which makes fuzzy the **first live consumer** of snapshot archives (R3-α had no precedent before this). That, in turn, is the same channel the resolver's registry uses (§7.3) — one snapshot-hash channel, two consumers, so vocabulary and registry can never drift apart.

### 5.3 Sourcing, engine evolution, refresh (RS-13, RS-14, RS-16)

- **Sourcing:** live-SQL load now (the hardened live shape, including the RS-12-γ alias tables), with the **built vocabulary artifact** as the named target. An open indexer CLI would extract member values and declared vocabulary into a versioned, hash-keyed artifact that fuzzy loads *instead* of touching source databases — dropping DB credentials entirely (a least-privilege win), giving reproducible indexes, and letting replicas share one artifact. It plugs into the existing `LoaderSource` SPI. This is the target, not day one, because the live SQL path is proven and no new machinery should precede the scale pressure that justifies it.
- **Engine:** port the live engine to parity first; evolve internally toward index-primary matching (with the cascade demoted to a re-scorer) once a referee corpus (Q-17) can gate the change. The contract is pinned, not the engine.
- **Vectorization is out.** Embedding/semantic matching is a *separate feature* with its own future design folder. The seams are reserved (the resolver's tier label, fuzzy's index seam) but no embedding work happens here.
- **Refresh:** interval refresh with atomic swap, plus snapshot-hash-keyed reload of the declared-vocabulary configuration (two staleness clocks, each honest), with staleness and version echoed in responses and GetStatus. Continuous change-data-capture is the *commercial* harvest tier (GI-4) — it plugs into the open `LoaderSource` SPI, drawing the open/commercial line exactly at the SPI.

### 5.4 The exposure posture (RS-17, resolves Q-16)

A pre-debut safety question: declaring a column searchable exposes its values to anyone who can call the matcher. The resolution is to make the model author's act the consent: **declaring a column searchable/fuzzy is declaring its values readable within the deployment.** There are no category ACLs or per-caller filtering in v1. The duty this creates is documentation — prominently, at the `search{fuzzy}` flag in the modeling manual and in the security docs: row-level security governs *rows* through the query path, but searchable *vocabularies* are deployment-visible by declaration. Import-schema linting may warn when a column that looks sensitive is flagged searchable. The revisit trigger for real ACLs is a concrete estate that demands per-role vocabularies.

---

## 6. Grounding services (workstream D)

### 6.1 An inherited, converged design

Unlike the other workstreams, grounding did not start from a blank page. A full DFP-side design corpus already existed — architecture, contracts, and a fourteen-stage plan, most of it code-complete with tests, paused only at deployment. Three live Kotlin services (`chrono`, `geo`, `money`) share one generic `grounding.v1` proto. So workstream D's job was not to design grounding but to decide the *deltas*: how it enters the open lineage, its extensibility, and its offline story.

The inherited invariants (not reopened): a recipe contract where each result is a `Normalized` value plus one of a `ValueBinding`, `FilterRecipe`, or `JoinRecipe`, plus a **derived** `sql_preview` rendered from the same `plan.v1` expression tree the recipe carries (never LLM-composed SQL from primitives), tagged with its source (`RULES` or `LLM`); a `reference_datetime` that always comes from the request and never from a server clock read; rules-first resolution with LLM fallback *off* by default; and semantic discovery driven by the model's `semantics{}` hints.

### 6.2 The kernel (RS-18)

The extraction keeps the live topology — three separate services plus the thin `ttr-grounding-mcp`, renamed under the J-v2 naming rules into `tatrman-server` — but adds a shared **`ttr-grounding-core` kernel** that consolidates the recipe-building triple (`RecipeBuilder`, `PlanExpr`, `SqlRenderer`) plus the shared fold, which today are copied per service and drift independently. The kernel is extracted *during the move* — the cheapest moment it will ever have, since extraction touches every file anyway — and it enforces the "sql_preview is derived, not duplicated" invariant in one place. New grounders start from the kernel. A single-host-with-plugins alternative was rejected because it would put geo's heavy Postgres/PostGIS/JTS dependencies into every pod and lose per-domain scaling.

### 6.3 The geo offline story (RS-19, resolves Q-7)

Geo is the one component with an unavoidable external dependency: resolving a place name to coordinates. Chrono and money are fully offline-deterministic; geo's place resolution is the single online seam (Nominatim). The design composes four postures rather than choosing one:

- a **capability-honest Nominatim seam** — a configured endpoint, fail-loud (`UNAVAILABLE`, never "place doesn't exist"), with GetStatus surfacing its absence;
- a **boundary cache on by default with an install-time priming step** that warms the places an estate actually asks about (model POIs, cities present in member data), converging toward offline for the common case;
- a **documented "geo goes dark" floor** — a deployment with no external configured simply has geo capability off, geo conformance fixtures are conditional on capability, and the acceptance bar never blocks on geo;
- and, as the named CZ-first arc, a **gazetteer/RÚIAN artifact** — the only path to *deterministic* geo (a pinned artifact gives reproducible resolutions), with a revisit trigger of an air-gapped Czech estate or observed parity-corpus variance from Nominatim.

The public OSM Nominatim endpoint restricts production use, so the default policy is almost certainly no bundled default plus self-host guidance in the docs (Q-19).

### 6.4 Extensibility — the admission rule (RS-20)

Framing input FI-1 mentions grounding for "time, geo, money, potentially other services." What makes a candidate a grounder? The admission rule: **client-specific but rule-computable, span-detectable, and recipe-expressible.** The mechanics are SPI-by-convention — the generic proto *is* the SPI. Adding a grounder is: a span kind the resolver/NER can detect; semantics roles or kinds the model declares (closed-vocabulary evolution, no grammar bump); a `grounding.v1` implementation producing recipes from catalog functions with per-dialect lowering; an additive MCP tool under `grounding.*:v1`; and rules-first with LLM fallback optional-off. The one shared-contract cost is `EntityKind` enum growth, which forces a documented **three-place change** (the enum, the resolver's universal-label mapping, the agent's routing). No new grounder is added inside this effort — duration, quantity/units, and percent go to the parking lot with the rule already applied to them.

### 6.5 The inheritance boundary (RS-21)

The paused `[~]` DFP stages are inherited as-is — their substantial test suites become the open lineage's — with re-opening limited to what the extraction itself breaks, captured as an explicit fix-at-rename list (J-v2 names, a proto `ResponseMessage` import wart, the S-2 fold consolidation into the kernel, and S-3 on operator endpoints). The boundary is pinned three ways: server-side gets the three services, the MCP, the kernel, and the renamed proto; agent-side (kantheon) keeps the GroundEntities node and cascade consumption; tatrman-side owns the semantics-block grammar and the semantics-passthrough tooling gap (the blocker that keeps model annotations from reaching the services — it lands with the import-schema and lexicon arcs). The 109-plus-21 grounding eval corpus feeds the conformance suite's extended tier.

---

## 7. The resolver (workstream E)

### 7.1 The split (RS-23) — the design's centerpiece

The live resolver runs two different LLM calls doing two different jobs. The first, "value-extraction," is span filtering and column tagging: which of these noun spans are literal values, and against which fuzzy columns should each be matched? It is a precision-and-efficiency device — it reduces the fuzzy fan-out and guesses target columns. The second, "joint-inference," is intent binding: which registered function does the user want, with which arguments? That second one is genuinely call #2-shaped work (intent) that had been living inside call #1's service.

The determinism line (P2) forbids the deterministic spine from containing either. The design's move is the **split**:

- The server's **`ttr-resolver` is the deterministic core**: parse → universal mapping → **deterministic span gating** (every candidate span matched via batch fuzzy across the source-tagged vocabulary; score thresholds plus entity-identity logic) → bindings or clarifications. No LLM anywhere in the service. This is essentially the live "entities-only" assembly, but fed by deterministic candidate generation instead of an LLM filter.
- The **LLM steps move agent-side**, into the kantheon **Resolving Agent**, which *reuses* `ttr-resolver`. If the deterministic core fully binds, the job is done with zero LLM. Otherwise an **escalation ladder** kicks in: local LLMs (via the LLM gateway — no cloud tier is needed for span filtering) provide value-extraction-class precision, and more capable models handle complex joint inference. Function and intent binding — the `function_specs` and joint-inference machinery — leave the service contract entirely, because they were call #2's work all along.

The value of the split is that it is P2-clean *by boundary rather than by re-solving hard problems*. The deterministic core does what is genuinely deterministic; LLM precision lives where LLMs are allowed. The two-call thesis becomes architecturally honest. And parity splits cleanly: service-level entity-binding parity (measurable against the entities-only corpus) plus end-to-end conversation parity (the conformance suite's job). Critically, it lets the `resolve` door *promise* determinism (§8).

The rejected alternatives are instructive. Doing everything deterministically in the server (α′) would have forced deterministic joint-inference, a hard problem beyond pattern-shaped intents that risks the parity bar. Porting as-is and declaring the resolver an agent-tier component (ζ) would have made call #1 non-deterministic by design — the architecture's headline ("deterministic after intent, provenance-carrying") would lose its first half, and the door could never promise determinism. The split is the resolver twin of what grounding already did: deterministic services plus an agent-side orchestration node.

The escalation ladder's placement — a dedicated Resolving Agent named Themis — is flagged (⚑) as a kantheon-side confirmation. This design pins the *contract* (reuse the core, escalate through the ladder, keep function-binding agent-side); the exact kantheon node topology is confirmed on that side. Themis already consumes the resolver's entities-only mode per its test suite, so the reuse relationship exists.

### 7.2 Placement (RS-22)

`ttr-resolver` stays a standalone service with its own proto and roster line. The recon settled the old consume-versus-contain debate as a matter of fact: the live resolver already consumes NLP, fuzzy, and metadata as services and embeds only orchestration and decision logic. Standalone is continuation, not re-architecture — and a service can be a door, which the exposure workstream needs.

### 7.3 Registry and vocabulary (RS-24)

The resolver builds its registry — entity types, categories, lexicon terms, locales, threshold-relevant metadata — from **snapshot archives**, hash-keyed, with the same refresh discipline fuzzy uses. This is the same channel RS-15 committed fuzzy to, so registry and vocabulary share one snapshot hash: they refresh together or refuse together, and can never drift. Live-metadata startup reads are acceptable as a dev-mode step one; caller-supplied per-request registries still win as an override.

### 7.4 Language and HITL (RS-25, RS-26)

The resolver is the first consumer that must visibly branch on the capability matrix: no Czech NER means the universal-span hints thin out (but domain resolution is unaffected, since span proposal is dependency-parse and noun-head based, and even without a parse, n-gram gating over fuzzy still works); an unsupported language falls to the fold-plus-fuzzy floor with results labeled `degraded`. Human-in-the-loop clarification uses stateless HMAC resume tokens with option pins, carried into the open contract. The pins are integrity-bearing: in the on-behalf-of world the agent is not trusted to invent options, and a signed pin proves the user chose from what the resolver actually offered — which matters exactly where refusal-over-guess matters. The alternative of agent-owned clarification was recorded as *what the contract must not quietly become*, because it would lose that pin integrity.

---

## 8. Exposure and orchestration (workstream F)

After the resolver split, the promises are crisp: the resolver core is deterministic, grounding is deterministic rules-first, fuzzy and NLP are deterministic primitives, and everything generative is the kantheon Resolving Agent, which is not a server component. That yields F's organizing line: **doors expose the deterministic; agents own the generative.** The door line *is* the determinism line.

### 8.1 The `resolve` door (RS-27, resolves Q-4 pre-debut)

`resolve` becomes a first-class MCP door exposing the deterministic core only (the `resolve.*:v1` family): bindings, universal spans, and clarification options with signed resume tokens, all provenance-carrying, with refusal-over-guess contract-assertable. The Resolving Agent's LLM ladder is never a door. The reasoning: third parties cannot hand-roll call #1 equivalently — span gating, registry semantics, thresholds, and clarification logic are the tuned know-how the reference stack took years to build — so without the door the ecosystem's answer quality would become vendor-variable, against the conformance suite's whole purpose. The split made the determinism promise possible, so the door can be governed and deterministic. A composite `understand` door (resolve + ground in one call) was rejected for now because it would put turn-logic and grounding-context judgment on the server side, which is agent work; it stays available as an additive-later option if third-party friction proves it out.

### 8.2 Grounding tools and orchestration (RS-28, RS-29)

Grounding is exposed as **kind-named tools** — `grounding.time:v1`, `grounding.geo:v1`, `grounding.money:v1` — over the generic proto: generic inside, ergonomic outside, because LLM tool-selection favors named tools over enum parameters. Capability and status are surfaced, and geo fixtures are conditional on capability. New grounders add a tool additively per the RS-20 checklist.

Orchestration of call #1 is agent work. Third parties get the resolve door plus grounding primitives plus the **canonical cascade documented as conformance fixtures** — the `calls:` assertion schema *is* the documentation of the correct order. GroundingContext assembly stays agent-side because turn state lives there. The Resolving Agent is the reference orchestration, in kantheon code, not in the contract.

### 8.3 Conformance and parity (RS-30, resolves Q-5)

Three tiers:

- **Gating, service-level, in-repo:** the entities-only binding corpus plus the grounding eval set (109 bulk + 21 E2E) plus fuzzy's match-quality fixtures (Q-17) — self-contained, no DFP dependency, run in CI. This is the SV-P3 "parity demonstrated" instrument for the deterministic core.
- **Gating, E2E core tier, hand-authored:** resolution and grounding conversation fixtures join the core conformance tier (~25–40 fixtures at 100%), authored with the reference Golem at SV-P4; the refusal-over-guess and clarification round-trip cases live here.
- **Non-gating, extended tier, pilot-derived:** the anonymized pilot conversation corpus scores the end-to-end parity claim across the split but gates nothing, so access risk never gates the debut.

The principle: parity is measurable *without* the pilot corpus; the pilot corpus only upgrades confidence.

---

## 9. Cross-cutting concerns (the consolidation sweep, RS-31/RS-32)

Four decisions cut across every component and were batch-ratified in the sweep so none was left decided-by-drift. Each is contract-visible.

- **Model identity is always explicit on the wire (S-1).** Configuration pins the engine and model version for every NLP backend; every model-touched response echoes the version that produced it (`nlp.v1` responses, fuzzy's `vocabulary_version` per category, the resolver's `EntityBinding` provenance, grounding's source-and-model tag). No component ever selects a model by an empty or default parameter. This kills the live empty-`model` bug class and makes determinism *auditable*: a response can be replayed only if it names what produced it.
- **One normalization spec (S-2).** The fold — lowercase, NFD, strip combining marks — exists in at least five copies across the family. It collapses onto one shared library and a written spec; determinism and parity depend on every matcher folding identically. The physical home (a shared text library versus living in the grounding kernel) is a planning placement detail; the spec itself is the anchor.
- **Admin-gated operator endpoints (S-3).** The `/refresh`-class endpoints (fuzzy reload, NLP prompt reload, grounding operator hooks) require identity and a role check in the open offering; the in-mesh unauthenticated convenience of today does not survive into a stranger's deployment.
- **One confidence contract (S-4).** Fuzzy scores, resolver binding confidence, and grounding confidence converge onto a shared `[0,1]` scale carried with a mandatory provenance tag naming its producer and method (the fuzzy algorithm/tier, the resolver gate, grounding's `RULES` or `LLM`). The scale's breakpoints are documented once; the door may present all three side by side, but the contract states they are producer-tagged and *not blindly cross-comparable* — a consumer normalizes via the documented mapping rather than by numeric equality. Threshold calibration is an implementation detail; the shared range plus mandatory provenance is the pinned contract shape.

And the vocabulary sweep (RS-32): the legacy `search{}` sub-properties map to lexicon forms (aliases/keywords → terms, patterns → pattern entries, examples → example entries, descriptions folded into `description`), with the standalone forms deprecating alongside the lexicon grammar arc.

---

## 10. The hero scenario, walked end to end

The scenario carried through every workstream:

> **„Kolik jsme utržili za Octavie v pražských pobočkách za poslední fiskální čtvrtletí?"**
> *How much revenue did we make on Octavias in the Prague branches in the last fiscal quarter?*

One Czech sentence, every component firing. Here is the full path under the converged design.

**Parse (`ttr-nlp`).** The front routes the Czech text: MorphoDiTa (self-hosted backend) tokenizes and lemmatizes — `Octavie → octavia`, `pražských → pražský`, `pobočkách → pobočka`, `utržili → utržit`; Stanza (backend) supplies the dependency parse the resolver's span proposal needs; NameTag 3 (backend) tags named entities — `pražských pobočkách` as a location (CNEC G-prefix), `poslední fiskální čtvrtletí` as a date/time (T-prefix). Every response echoes the pinned model versions (S-1). No question text leaves the deployment.

**Universal spans (`ttr-resolver` core → grounding).** The resolver maps the NER labels to universal types and hands the universal spans to grounding via the orchestrator. `grounding.geo` resolves "Praha" (Nominatim seam or primed cache) and produces a containment recipe — a bounding-box prefilter plus polygon; the branch entity itself is *also* a modeled entity, so "pobočkách" additionally gates as a domain span. `grounding.time` resolves "poslední fiskální čtvrtletí" against the model's declared fiscal period table (a JoinRecipe), relative to the request's `reference_datetime` — this is exactly where Q-18 (does quarter-granularity period semantics actually exist?) must be verified. `grounding.money` is not triggered by a comparator here but the measure is a money domain. Each grounding result carries its `RULES`/`LLM` source and confidence with provenance (S-4).

**Domain spans (`ttr-resolver` core, deterministic gating).** This is the heart of the split. Every candidate span goes to **batch fuzzy** over the source-tagged vocabulary: `octavia` scores high in the product member category (the lemma axis, now live because NLP is self-hosted, bridges the inflection); `pobočka` hits the branch entity's lexicon terms. Thresholds bind the confident matches; ambiguity produces clarification candidates with signed resume tokens. No LLM is involved — where the live pilot ran a haiku call to decide "Octavie is a value for the product-name column," the core now does it by deterministic scoring. Whether that scoring reaches the LLM's precision (including the sibling-column and code-versus-name tricks) is the one empirical gate on the whole design: Q-20's span-gating spike.

**Intent (the Resolving Agent, above the line).** `utržili` must reach the revenue measure. The measure carries lexicon terms (`tržba`, `obrat`, `utržit`) declared via RS-10, shipped to the agent as prompt context through `get_model`. If a deterministic pattern tier catches it, good; otherwise the Resolving Agent binds the measure with a local LLM — *in call #2's territory, above the determinism line*, which is exactly where the design says intent binding belongs. The declared verb form `utržit` is what makes this possible: morphology alone would never bridge `utržili → tržba` (derivation, not inflection) — it had to be declared (RS-11).

**The result.** The whole binding comes back provenance-carrying and — for everything below the line — deterministic: the product member, the branch entity, the geo containment, the fiscal-quarter interval, each tagged with what produced it and how confident it is. Call #2's SQL generation never guesses, because call #1 refused to.

---

## 11. What we deliberately did not do

Deferral is a tracked outcome, not a gap. Each of these has a revisit condition in the parking lot:

- **Vectorization / semantic (embedding) matching** — a separate feature with its own future design folder; the seams are reserved but no embedding work happens here. Revisit after parity, when recall gaps are measured.
- **New grounders** (duration, quantity, percent) — parked with the admission rule already applied; revisit case by case.
- **A second language's SPI** — the second production language defines the interface; abstracting now would be a guess.
- **Continuous harvest (CDC)** of member vocabulary — the commercial tier on the open `LoaderSource` SPI.
- **Learned/teach-in vocabulary** — post-debut enrichment once conversation-corpus access lands.
- **DeriNet derivational expansion** — a new engine and a licensing sibling of the UFAL question; declaration covers the need for now.
- **Intent/measure vocabulary covered by the conceptual model itself** — a named future arc; the declared lexicon surface is the step toward it, not the endpoint.
- **The UFAL model licensing mechanics** — the design side is closed; the redistribution question (CC BY-NC-SA models in published images) is a legal item, now concretely shaped as a registry-access choice.

---

## 12. Component roster and glossary

| Component | Tier | Role | Proto / surface |
|---|---|---|---|
| `ttr-nlp` (front) | server | Engine-free router: contract, routing, langid | `org.tatrman.nlp.v1` (gRPC) |
| MorphoDiTa / NameTag 3 / Stanza / spaCy backends | server | Model-bearing NLP engines, self-hosted, models baked per image | behind the nlp front |
| `ttr-fuzzy` | server | The vocabulary matcher (members + declared vocabulary, source-tagged) | `fuzzy.match:v1` (pinned) |
| `chrono` / `geo` / `money` | server | Deterministic grounding of universal values | `org.tatrman.grounding.v1` + `grounding.{time,geo,money}:v1` |
| `ttr-grounding-core` | server | Shared kernel: RecipeBuilder / PlanExpr / SqlRenderer + fold | library |
| `ttr-grounding-mcp` | server | Thin generic MCP wrapper over grounding | MCP door |
| `ttr-resolver` | server | The deterministic core: parse → gate → bind, zero LLM | `org.tatrman.resolver.v1` + `resolve.*:v1` door |
| Resolving Agent (⚑ Themis) | agent (kantheon) | Reuses the core; escalation ladder to LLMs; owns intent binding | *not a door* |
| lexicon model | TTR-M | Canonical vocabulary (term/pattern/example) + inline sugar | grammar (4.3 arc) |

**Key terms.** *Grounding* — resolving universal values (time, place, money) deterministically from rules. *Binding* — the resolved link between a span of text and a model entity/member/measure. *Universal span* — a span whose meaning is language-universal (a date, a place); *domain span* — a span whose meaning is estate-specific (a product, a branch). *The determinism line* — the boundary below which no LLM runs; also the door line. *Source-tagged* — a fuzzy result carrying whether it is a member-candidate or a vocabulary-candidate. *Snapshot channel* — the single hash-keyed archive channel that feeds both fuzzy's vocabulary and the resolver's registry, so they cannot drift. *Fold* — the shared normalization (lowercase + NFD + strip marks). *Capability matrix* — the `lang × op → engine + model version` map that consumers branch on. *The two-call thesis* — call #1 resolves/grounds/binds; call #2 generates SQL; the seam makes answers auditable.

---

## 13. From here

The design is complete and consolidated. The next step is a `/planning` session that consumes `design.md` and produces the architecture, contracts, the phased plan, and task lists. Two spikes are runnable immediately and are named phase-0 candidates: Q-10 (self-hosted NameTag 3 / MorphoDiTa protocol-parity and sizing) and Q-20 (whether deterministic span-gating reaches the LLM value-extraction's precision on the seed corpus — the one empirical gate on the resolver split). Everything else the planning session needs — the open questions demoted to inputs, the per-workstream threads, the register updates, and the sequencing hints — is enumerated in `design.md` §7–§9.
