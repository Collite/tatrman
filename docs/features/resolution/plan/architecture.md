# Resolution & Grounding — Architecture

> **Status:** consolidated 2026-07-12 from the converged design effort. Ground truth = the design docs one level up: [`../design/design.md`](../design/design.md) (the compact result, RS-1..32) and [`../design/detailed-design.md`](../design/detailed-design.md) (the manual); the append-only decision log is [`../design/00-control-room.md`](../design/00-control-room.md) §7. Companions: [`contracts.md`](./contracts.md), [`plan.md`](./plan.md).
>
> This describes the **understanding layer of Tatrman Server** — call #1 of the two-call thesis: how a user's words become bound model entities, grounded universal values, and searchable member vocabulary. Four server components (`ttr-resolver`, `ttr-fuzzy`, `ttr-nlp`, the grounding services + `ttr-grounding-mcp`), one shared kernel (`ttr-grounding-core`), the TTR-M `lexicon` surface they consume, and the kantheon-side **Resolving Agent** that wraps them.

---

## 1. What this layer is (and is not)

The layer turns natural-language input into a precise, provenance-carrying **binding structure** that call #2 (query generation) consumes without guessing. It resolves *everything* a question names: dimension members and entities (via fuzzy over model-declared vocabulary), universal values — time, place, money — (via the grounding services), and — one layer up, in the agent — intent/measure.

**It is deterministic below one line and generative above it.** The load-bearing constraint (GI-1 / principle P2): no LLM in the deterministic path. The server components are *statistical-deterministic* — pinned model files produce reproducible outputs — but never generative. Everything generative lives in the kantheon Resolving Agent. This boundary is physical, not conceptual, and it is also the rule for what may be exposed as an MCP door: **the door line is the determinism line.**

**It is not** the compiler's QName/symbol resolver (`docs/features/grammar-master/resolver-consolidation/` — unrelated). "Resolution" here = entity/value resolution at question time.

## 2. The determinism line (the topology)

```
┌──────────────────────── kantheon — AGENT TIER (generative allowed) ─────────────────────────┐
│  Resolving Agent  (⚑ Themis — placement confirmed kantheon-side)                              │
│    1. call ttr-resolver core → fully bound?  → done, ZERO LLM                                  │
│    2. else escalation ladder: local LLMs (span/value precision) → capable models (joint infer)│
│    3. intent/measure binding lives here (was call #2's work all along)                         │
│  Golem consumes the Resolving Agent (no longer owns resolution)                                │
└───────────────▲───────────────────────────────────────────────────────────▲──────────────────┘
                │ reuse (gRPC)                                                │ grounding tools (MCP)
════════════════╪═══════════ THE DOOR LINE = THE DETERMINISM LINE ═══════════╪══════════════════
                │  SERVER TIER (deterministic only — every door promises it)  │
        ┌───────┴──────────────────────────┐                     ┌───────────┴──────────────────┐
        │  ttr-resolver  — DETERMINISTIC    │                     │  grounding services           │
        │  CORE (zero LLM)                  │  consumes B/C        │  chrono · geo · money         │
        │  parse → universal mapping →      │◄──────┐             │  + ttr-grounding-mcp          │
        │  all-spans × batch-fuzzy gating → │       │             │  on ttr-grounding-core kernel │
        │  thresholds/identity → bindings   │       │             │  (rules-first, LLM fallback   │
        │  + HMAC resume tokens             │       │             │   off by default)             │
        │  proto: org.tatrman.resolver.v1   │       │             │  org.tatrman.grounding.v1     │
        │  door: resolve.*:v1               │       │             │  tools: grounding.{time,geo,  │
        └──────┬─────────────┬──────────────┘       │             │          money}:v1            │
               │ gRPC        │ gRPC (batch)         │             └───────────────────────────────┘
     ┌─────────▼──────┐  ┌───▼───────────────┐  ┌───▼─────────────────────────┐
     │  ttr-fuzzy     │  │  snapshot / Veles  │  │  ttr-nlp  (ENGINE-FREE FRONT │
     │  THE vocab     │  │  ONE hash-keyed    │  │  contract + routing + langid)│
     │  matcher       │  │  channel:          │  │   ├─ MorphoDiTa backend      │
     │  members +     │  │   • fuzzy vocab     │  │   ├─ NameTag 3 backend       │
     │  declared      │  │   • resolver registry│ │   ├─ Stanza backend          │
     │  vocab, SOURCE-│  │  (never drift)     │  │   └─ spaCy backend           │
     │  TAGGED        │  │                    │  │  models baked per img (S-1)  │
     │  fuzzy.match:v1│  │                    │  │  org.tatrman.nlp.v1 (gRPC)   │
     └────────────────┘  └────────────────────┘  └──────────────────────────────┘
               ▲ all consume model-declared vocabulary
     ┌─────────┴──────────────────────────────────────────────────────────────┐
     │  TTR-M `lexicon` model (canonical term/pattern/example) + inline sugar;  │
     │  `search{}` = retrieval config; `semantics{}` = grounding hints;         │
     │  member vocab = columns + valueLabels + declared alias_table semantics   │
     └──────────────────────────────────────────────────────────────────────────┘
```

**Load-bearing order** (the plan's spine): **A (lexicon) and C (nlp) are the feet** — everything consumes vocabulary and NLP primitives; **B (fuzzy)** and **D (grounding)** stand on them; **E (resolver)** composes B/C/D; **F (doors)** exposes E. A rides the grammar-master process as a parallel track (gated on grammar 4.2).

## 3. Component architecture

### 3.1 `ttr-nlp` — engine-free front + backends (workstream C)

```
              org.tatrman.nlp.v1 (gRPC; REST for dev/health only)
                            │
   ┌────────────────────────▼─────────────────────────┐
   │  ttr-nlp FRONT  (Python 3.13 / FastAPI + gRPC)    │   engine-free:
   │    • Analyze (ops bitmap)  • BatchLemmatize        │   NO torch,
   │    • GetStatus → capability matrix                 │   NO model files
   │    • langid (lingua, local)                        │
   │    • per-lang-per-op routing table                 │
   └───┬──────────┬───────────┬───────────┬─────────────┘
       │ HTTP     │ HTTP      │ HTTP      │ HTTP        (in-cluster backends)
  ┌────▼───┐ ┌────▼────┐ ┌────▼───┐ ┌─────▼────┐
  │Morpho- │ │NameTag 3│ │ Stanza │ │  spaCy   │   each: own image, own model
  │DiTa    │ │(PyTorch)│ │        │ │          │   baked + digest-pinned (S-1),
  │rest_srv│ │ _server │ │        │ │          │   offline by construction
  └────────┘ └─────────┘ └────────┘ └──────────┘
   cs tok/    cs/en NER    cs/en      en tok/NER
   lemma/POS               tok/lemma/
                           POS/dep-parse
```

The front holds the contract, the routing table, and language identification — nothing model-bearing. Every model-bearing engine is a backend service, scaled and placed independently. Stanza is a backend like the rest, and it is on the Czech hot path (cs `DEP_PARSE` → the resolver's span proposal reads the dependency parse). The Lindat online endpoints survive as a **labeled dev/eval tier** — a Lindat-pointed deployment is non-conformant for parity/determinism (question-text egress, 5/min, unpinned models). The **capability matrix** (`lang × op → engine + model version`) is the front's honest self-description: exposed via `GetStatus`, echoed in every response, and the thing every consumer branches on. Unsupported languages get the degrade floor: tokenize + fold + langid.

### 3.2 `ttr-fuzzy` — the vocabulary matcher (workstream B)

```
   fuzzy.match:v1 (gRPC 7203 / REST 7103; PINNED — additive only)
                            │
   ┌────────────────────────▼───────────────────────────┐
   │  ttr-fuzzy  (Kotlin / Ktor)                          │
   │   TATRMAN matcher: fold → token/lemma inverted index │
   │     → per-token Levenshtein → IDF → word-order bonus │
   │   cascade = decision gates (first alg ≥ minScore wins)│
   │   SOURCE-TAGGED categories:                          │
   │     member-candidate (→ resolved_id)                 │
   │     vocabulary-candidate (→ target ref)              │
   │   LoaderSource SPI:                                  │
   │     live-SQL now  →  built vocabulary artifact (target)│
   │   fold = shared normalization lib (S-2)              │
   └───┬──────────────────────────┬──────────────────────┘
       │ SQL (source DBs)          │ snapshot read (declared vocab)
   member values +            lexicon terms + valueLabels +
   alias tables (RS-12γ)      (R3-α's first live consumer)
```

Fuzzy matches *everything* — member values from the estate and declared vocabulary from the model — through one Czech-aware engine, with results source-tagged so a data-row match is distinguishable from a model-target match. It loads declared vocabulary from the **snapshot channel** — the same channel the resolver's registry uses (§3.5), so vocabulary and registry share one hash and cannot drift. Interval refresh + atomic swap for member data; snapshot-hash-keyed reload for declared-vocabulary config; staleness echoed. Continuous CDC freshness is the commercial tier on the open `LoaderSource` SPI. **Searchable = visible-by-declaration** (RS-17): declaring a column `fuzzy` declares its values readable in the deployment; no ACLs in v1, documented prominently.

### 3.3 grounding services + `ttr-grounding-core` (workstream D)

```
   grounding.{time,geo,money}:v1 (MCP, kind-named)   +   org.tatrman.grounding.v1 (gRPC: Ground/GetStatus)
                            │
   ┌───────────┬────────────┴────────────┬───────────┐
   │  chrono   │        geo              │   money   │   three services, one generic proto
   │ (offline) │ (Nominatim = one online │ (offline) │
   │           │  seam + cache + priming;│           │
   │           │  RÚIAN artifact = CZ arc)│          │
   └─────┬─────┴──────────┬──────────────┴─────┬─────┘
         └────────────────┼────────────────────┘
                 ┌────────▼─────────┐
                 │ ttr-grounding-core│  RecipeBuilder · PlanExpr · SqlRenderer · fold(S-2)
                 │  KERNEL           │  sql_preview DERIVED (never duplicated) from plan.v1 tree
                 └───────────────────┘
```

Each service deterministically resolves a universal value from rules (LLM fallback off by default), producing a **recipe** — a `Normalized` value plus one of `ValueBinding | FilterRecipe | JoinRecipe` plus a derived `sql_preview` rendered from the same `plan.v1` expression tree the recipe carries. `reference_datetime` always comes from the request, never a server clock. The kernel consolidates the recipe-building triple + the shared fold, extracted during the J-v2 move (the cheapest moment). Semantic discovery reads the model's `semantics{}` hints. Geo's place resolution is the single online seam, made honest by a capability-surfaced Nominatim endpoint + a boundary cache primed at install + a documented dark floor; the RÚIAN gazetteer artifact is the named CZ-first path to fully deterministic geo.

### 3.4 `ttr-resolver` — the deterministic core (workstream E)

```
   org.tatrman.resolver.v1 (gRPC)   +   resolve.*:v1 (MCP door — deterministic promise)
                            │
   ┌────────────────────────▼─────────────────────────────────────┐
   │  ttr-resolver  (Kotlin) — the DETERMINISTIC CORE, zero LLM     │
   │   1. parse (via ttr-nlp; result passthrough in response)       │
   │   2. extractUniversal   (NER label → UniversalEntityType)      │
   │   3. proposeDomainSpans (noun heads from dep parse + n-grams)  │
   │   4. gateSpans          (ALL candidate spans × BATCH fuzzy     │
   │                          over source-tagged vocabulary)        │
   │   5. thresholds + entity-identity → bindings | clarification   │
   │   registry = snapshot-fed (shares B's hash channel)            │
   │   HITL = stateless HMAC resume tokens + option pins            │
   │   degrades on the capability matrix (labels `degraded`)        │
   └────────────────────────────────────────────────────────────────┘
```

The former LLM steps (value-extraction filter, joint-inference intent binding) are **gone from the service** — they move to the Resolving Agent. What remains is the entities-only assembly, generalized, fed by deterministic candidate generation (all spans × batch fuzzy) instead of an LLM filter. Whether that scoring reaches the LLM's precision is the one empirical gate on the whole design — the **Q-20 spike** (RG-P0). The `function_specs`/joint-inference machinery leaves the contract entirely (it was call #2's work).

### 3.5 The Resolving Agent (kantheon — above the line)

Not a server component and never a door. It reuses `ttr-resolver`'s core: if the core fully binds, done with zero LLM; otherwise an escalation ladder — local LLMs (via `ttr-llm-gateway`) for value-extraction-class precision, then capable models for complex joint inference — and it owns intent/measure binding. This is the reference orchestration; third parties get the deterministic pieces + the canonical cascade documented as conformance fixtures, and build their own orchestration. Placement (Themis) is confirmed kantheon-side; this design pins the *contract* (reuse the core, escalate, agent-owns-intent).

## 4. Position in the turn pipeline

```
user turn ─► [Resolving Agent] ─► resolve (ttr-resolver core, deterministic)
                                     ├─ parse .......... ttr-nlp Analyze
                                     ├─ universal spans ─► grounding.{time,geo,money}  (deterministic recipes)
                                     ├─ domain spans ──► ttr-fuzzy BatchMatch (source-tagged)
                                     └─ bindings | AwaitingClarification (HMAC token)
                                   ─► core fully bound?  ── yes ─► hand call #2 the bindings (SQL never guesses)
                                                          └─ no ─► escalation ladder (LLMs, agent tier) ─► bind ─► call #2
```

Nothing downstream of the bindings changes by the split: call #2 consumes the same provenance-carrying structure whether the core or the ladder produced it; only the *tier* differs, and the binding is labeled with it.

## 5. Tech stack

- **`ttr-nlp` front + backends** — Python 3.13, FastAPI + gRPC; engines: MorphoDiTa (`ufal.morphodita` / `src/rest_server`), NameTag 3 (PyTorch, `nametag3_server.py`), Stanza, spaCy; langid via lingua. Models baked per backend image, CPU-only torch, digest-pinned. UFAL models CC BY-NC-SA (FI-4 legal item parked).
- **`ttr-fuzzy`, grounding services, `ttr-grounding-core`, `ttr-resolver`** — Kotlin / Ktor; gRPC + MCP (Kotlin MCP SDK, streamable HTTP — EXAMPLES.md §3); kotlinx.serialization; OTel via shared `otel-config`; Kotest. Recipes carry `plan.v1` (tatrman-owned wire format, ttr-plan-proto lockstep).
- **Resolving Agent** — kantheon (Python + LangGraph, EXAMPLES.md §5), reuses `ttr-resolver` over gRPC, LLMs via `ttr-llm-gateway`.
- **Home repo** — `tatrman-server` (Kotlin services; `ttr-fuzzy` already at `tatrman-server/services/ttr-fuzzy` post-SV-P0). The NLP service stays Python. Published protos renamed to `org.tatrman.*` (J-v2). MCP surface additive under GI-2.

## 6. Testing strategy

- **Unit** — engine-adapter parsing (vertical/BIO output → tokens/entities); capability-matrix construction; fold/normalization arithmetic (S-2 shared lib, ≥5 call-sites fold identically); fuzzy scoring folds (surface/lemma dual-axis max, IDF, order bonus) against hand numbers; resolver threshold/identity logic; recipe→`sql_preview` derivation.
- **Component** — front → backend routing (one pass, many ops); batch lemmatize at both hops; fuzzy source-tagged category resolution; resolver gate-spans over a fixture vocabulary; grounding recipe shape per metadata (period table ⇒ JoinRecipe).
- **No full E2E integration tests here** — integration is a separate flow. Conformance (`ttr-conform`-class) arrives at RG-P6 as a harness, three tiers: gating service-level (in-repo: ENTITIES_ONLY + grounding 109+21 + fuzzy match fixtures), gating E2E core (hand-authored, SV-P4), non-gating pilot-derived extended.
- **Golden fixtures** — the hero sentence rendered through each component; the seed corpora (`seed.jsonl`, `ucetnictvi_entities_only.jsonl`) as the resolver's parity instrument; every diagnostic id has a triggering fixture (house convention).
- **Spikes gate design assumptions, not code** — Q-10 (self-hosting sizing + protocol parity) and Q-20 (span-gating precision) run first (RG-P0) and their numbers are recorded as go/no-go evidence.

## 7. Invariants (additive to the platform's)

1. **No LLM below the door line.** The resolver core and grounding services are statistical-deterministic; generative steps are agent-side only. Enforced by boundary, asserted by the resolve door's determinism contract.
2. **Model identity is always on the wire (S-1).** No server-default model selection anywhere; every model-touched response echoes engine + version. A response is replayable only if it names what produced it.
3. **One normalization spec (S-2).** Every matcher folds identically (lowercase + NFD + strip marks) via the shared lib — determinism and parity depend on it.
4. **One snapshot channel, two consumers.** Fuzzy vocabulary and resolver registry read the same hash-keyed snapshot; they refresh together or refuse together, never drift.
5. **Pinned MCP surface, additive only (GI-2).** `fuzzy.match:v1` never renames; `grounding.*:v1` and `resolve.*:v1` enter additively under J-v2.
6. **Searchable = visible-by-declaration (RS-17).** Declaring a column `fuzzy` is the consent; documented, no v1 ACLs.
7. **Explicit failure, never silent degradation.** Capability gaps are labeled (`degraded`, capability matrix), model gaps named, geo-dark documented — never a silent wrong answer.
8. **Confidence is producer-tagged (S-4).** A `[0,1]` scale carried with a mandatory producer+method tag; not blindly cross-comparable across fuzzy/binding/grounding.
