# Resolution & Grounding — Contracts

> **Status:** consolidated 2026-07-12. Source of truth for every cross-component boundary in the understanding layer: proto surfaces, MCP tool surfaces, the sweep contract shapes (S-1..S-4), the lexicon grammar surface, GroundingContext, the HMAC resume-token schema, diagnostics, published artifacts. Companions: [`architecture.md`](./architecture.md), [`plan.md`](./plan.md). Decision ids reference the design decision log ([`../design/00-control-room.md`](../design/00-control-room.md) §7, RS-1..32).
>
> **Rules.** Published names follow J-v2: `org.tatrman.*` proto packages; MCP capability families `*.vN` never rename, extend additively (GI-2). Every change here needs a **changelog** entry (end of file). Signatures below are the pinned *shapes*; field-level bikeshedding that does not change the shape is a task-level detail.

---

## 1. `org.tatrman.nlp.v1` — the NLP contract (workstream C; renamed from `cz.dfpartner.nlp.v1`)

gRPC is the service contract; REST mirrors it for local dev/health only.

```proto
package org.tatrman.nlp.v1;

service NlpService {
  rpc Analyze        (AnalyzeRequest)        returns (AnalyzeResponse);
  rpc BatchLemmatize (BatchLemmatizeRequest) returns (BatchLemmatizeResponse);  // NEW (RS-6 / C4-T2)
  rpc GetStatus      (StatusRequest)         returns (StatusResponse);          // capability matrix (RS-7)
}

message AnalyzeRequest {
  string text            = 1;
  optional string language = 2;             // absent ⇒ langid detect, fallback = deployment default
  repeated Op ops        = 3;               // the ops bitmap — one pass serves many ops
  Mode mode              = 4;               // NORMAL (default) | COMPARE (debug/eval only, outside conformance)
}
enum Op { TOKENIZE=0; SENTENCE_SPLIT=1; LEMMATIZE=2; POS_TAG=3; DEP_PARSE=4; NER=5; DETECT_LANGUAGE=6; }
enum Mode { NORMAL=0; COMPARE=1; }

message AnalyzeResponse {
  repeated Token   tokens    = 1;
  repeated Sentence sentences = 2;
  repeated Entity  entities  = 3;           // NER spans
  string  detected_language  = 4;
  repeated EngineVersion used = 5;          // S-1: which engine+model produced each op (ALWAYS populated)
  map<string, EngineResult> by_engine = 6;  // COMPARE mode only
}
message Token { string text=1; string lemma=2; string pos=3; int32 head=4; string dep=5;
                int32 char_start=6; int32 char_end=7; }        // lemma/pos/head/dep present iff the op ran
message Entity { string text=1; string type=2; int32 char_start=3; int32 char_end=4; string source_engine=5; }

message BatchLemmatizeRequest  { repeated string texts=1; optional string language=2; }   // shape sized by Q-11
message BatchLemmatizeResponse { repeated LemmaList results=1; repeated EngineVersion used=2; }
message LemmaList { repeated string lemmas=1; }               // positional to `texts`

message EngineVersion { string op=1; string engine=2; string model=3; string model_version=4; }  // S-1: no empty model

message StatusResponse {
  bool ready = 1;
  repeated Capability capabilities = 2;     // the capability matrix
}
message Capability { string language=1; Op op=2; string engine=3; string model_version=4;
                     Tier tier=5; }         // Tier: SELF_HOSTED_PINNED | REMOTE_UNPINNED (Lindat = dev/eval)
```

**Rules.**
- **S-1 (no default models):** `AnalyzeRequest` never carries an empty model; the backend a route points at is launched with an explicit model id (config), and `used[]` echoes it on every response. A `REMOTE_UNPINNED` capability (Lindat) is non-conformant for parity/determinism claims.
- **Routing** is config: per-language per-op → engine (front table). `cs` → MorphoDiTa (TOKENIZE/LEMMATIZE/POS_TAG) + Stanza (DEP_PARSE) + NameTag 3 (NER); `en` → Stanza (+ spaCy NER); DETECT_LANGUAGE → langid. Any language gets the degrade floor (TOKENIZE + fold + DETECT_LANGUAGE); consumers branch on `GetStatus`.
- **Caching** stays consumer-side; `used[]` gives cache keys (model versions). COMPARE is a debug flag, excluded from conformance.
- **Backends** speak their own upstream HTTP protocol (MorphoDiTa `rest_server` vertical; NameTag 3 `nametag3_server.py` BIO); the front's engine adapters are the only thing that knows a backend's wire form. Repoint = config (Lindat URL → in-cluster URL).

## 2. `fuzzy.match:v1` — PINNED (workstream B; GI-2 additive-only)

The published tool never renames. Additive candidates (RS-15/16, B-T1):

```proto
package org.tatrman.fuzzy.v1;      // internal proto (published MCP tool id = fuzzy.match:v1)

service FuzzyMatcherService {
  rpc Match      (MatchRequest)      returns (MatchResponse);
  rpc BatchMatch (BatchMatchRequest) returns (BatchMatchResponse);   // NEW (B-T1): N spans × categories, one RPC
  rpc GetStatus  (StatusRequest)     returns (FuzzyStatusResponse);  // NEW: category discovery + staleness
}

message MatchRequest  { string query=1; optional string category=2;  // null=global; explicit-unknown ⇒ EMPTY (leak guard)
                        optional string locale=3;                     // NEW (RS-11)
                        repeated Algorithm cascade=4; int32 limit=5; }
message MatchResponse { repeated Candidate candidates=1; string vocabulary_version=2; }   // S-1: version echoed

message BatchMatchRequest  { repeated SpanQuery spans=1; optional string locale=2; }
message SpanQuery          { string query=1; repeated string categories=2; int32 limit=3; }
message BatchMatchResponse { repeated MatchResponse results=1; }      // positional to spans

message Candidate {
  string id=1; string label=2; double score=3;                       // S-4: score = [0,1]
  Provenance provenance=4;                                            // S-4: producer+method (algorithm+tier)
  SourceTag source=5;                                                 // RS-15: MEMBER | VOCABULARY
  optional string target_ref=6;                                      // set iff source=VOCABULARY (→ lexicon target)
}
enum SourceTag { MEMBER=0; VOCABULARY=1; }
message Provenance { string producer=1; string method=2; double raw_score=3; }   // e.g. producer="fuzzy", method="TATRMAN"
```

**Rules.** Cascade semantics unchanged (first algorithm whose top-1 ≥ its minScore wins; `LEVENSHTEIN | TATRMAN | JARO_WINKLER`; Czech diacritics contract-observable). Source-tagged categories: MEMBER candidates carry a data `id` (→ `resolved_id`), VOCABULARY candidates carry a `target_ref` (→ lexicon/valueLabels target). `vocabulary_version` = snapshot hash + per-category load timestamp. Fold via the shared S-2 lib.

## 3. `org.tatrman.grounding.v1` + kind-named tools (workstream D)

```proto
package org.tatrman.grounding.v1;   // renamed; fix the ResponseMessage import wart at rename (RS-21)

service GroundingService {
  rpc Ground    (GroundRequest)   returns (GroundResponse);
  rpc GetStatus (StatusRequest)   returns (GroundingStatus);   // capabilities: postgis, llm_fallback, metadata, nominatim
}

message GroundRequest {
  string span_text=1; string question_text=2;
  EntityKind kind=3;                                           // DATE_TIME | LOCATION | MONEY (+additive per RS-20)
  string package=4; GroundingContext context=5;
  repeated AnchorCandidate anchor_candidates=6; string correlation_id=7;
  optional Continuation continuation=8;                        // clarification_answer_id
}
enum EntityKind { DATE_TIME=0; LOCATION=1; MONEY=2; }          // growth = the RS-20 three-place checklist

message GroundResponse {
  Status status=1;                                             // OK | AWAITING_CLARIFICATION | UNGROUNDABLE
  GroundingResult result=2; repeated Option options=3;
}
message GroundingResult {
  Normalized normalized=1;                                     // DateTimeInterval | GeoPoint | GeoShape | MoneyValue
  oneof recipe { ValueBinding value_binding=2; FilterRecipe filter_recipe=3; JoinRecipe join_recipe=4; }
  string sql_preview=5;                                        // DERIVED from the plan.v1 tree the recipe carries — never hand-built
  double confidence=6; Provenance provenance=7;               // S-4: [0,1] + {producer, method=RULES|LLM}
  string explanation=8;
}

message GroundingContext {                                     // server-owned proto (D-T1); agent assembles from turn state
  string reference_datetime=1;                                 // ALWAYS from the request — never now() in-service
  string timezone=2; string locale=3; string default_currency=4;
  string here_place_ref=5; string fx_policy=6; double tolerance_pct=7;
}
```

**MCP tool surface (kind-named — RS-28; mcp-surface §2.2 rows):**

| Tool id | Wraps | Notes |
|---|---|---|
| `grounding.time:v1` | `Ground(kind=DATE_TIME)` | chrono; fiscal via model period tables (JoinRecipe) |
| `grounding.geo:v1`  | `Ground(kind=LOCATION)` | geo; **capability-conditional** (Nominatim/PostGIS) — fixtures skip when dark (RS-19) |
| `grounding.money:v1`| `Ground(kind=MONEY)`    | money; FX via model `fx_rate` table, fails loud if time-versioned without as-of |

Recipes embed `plan.v1` (tatrman-owned; ttr-plan-proto lockstep). `sql_preview` is derived once in `ttr-grounding-core`. LLM fallback off by default; when on, `provenance.method=LLM` + structural validation, conformance-assertable.

**Metadata semantics projection (`org.tatrman.meta.v1`; RS-33 / RG-P3.S0 — G2 closure).** Grounding discovery reads the model's grammar-4.2 `semantics{}` declarations off Veles (`meta.v1`), which projects them as **additive** fields: `EntitySemantics{kind}` on `EntityDetail`/`DbTableDetail`, `AttributeSemantics{role, code_format, period, currency_attribute}` on `AttributeDetail`/`DbColumnDetail`/`DbColumnSummary`, and `ObjectDescriptor.semantics_kind` (the `list_objects` discovery accelerator). `kind`/`role` are **strings, not enums** — the vocabulary lives in ttr-semantics and evolves without proto bumps; consumers dispatch on known values and tolerate unknown ones (J-v2). Veles serves the projection from the typed model (`Entity/DbTable.semanticsKind`, `Attribute/DbColumn.semantics: ResolvedAttributeSemantics`, ttr-metadata ≥ 0.9.4); it does **not** validate the vocabulary (upstream `TTR-SEM-2xx` owns that — invalid blocks degrade to load issues and the object is served without semantics). ai-platform's closed `com.tatrman.metadata.v1` enums stay legacy-side and die at the SV-P5 cutover. Fixture provenance: the grammar golden fixtures `59-semantics.ttrm` (er) + `60-semantics-db.ttrm` (db), vendored into Veles tests (`VelesSemanticsProjectionSpec`).

## 4. `org.tatrman.resolver.v1` + the `resolve` door (workstream E)

Reshaped (never published before — free to reshape; J-v2 binds published names only). `function_specs`/joint-inference machinery **removed** (agent-side now).

```proto
package org.tatrman.resolver.v1;

service ResolverService { rpc Resolve (ResolveRequest) returns (ResolveResponse); }

message ResolveRequest {
  string conversation_id=1;
  oneof input {
    FreshQuestion  fresh  = 2;                                 // { text, locale }
    ResumeAnswer   resume = 3;                                 // { token, selected_option_id, free_text_answer }
  }
  optional Registry registry=4;                                // per-request override; default = snapshot-fed
  ResolveContext context=5;                                    // reference info; NO ResolveMode (NORMAL machinery gone)
}

message ResolveResponse {
  AnalyzeResponse parse=1;                                     // passthrough (E-T1): agents skip a second parse
  oneof outcome { Resolution resolution=2; AwaitingClarification awaiting=3; }
  string trace_id=4; int64 elapsed_ms=5;
  Capabilities capabilities=6;                                 // RS-7 matrix echoed (what backed this resolve)
}

message Resolution { repeated EntityBinding bindings=1; double confidence=2; string rationale=3; }
message EntityBinding {
  Span span=1;
  oneof kind {
    Universal universal=2;                                     // {entity_type, raw_text, normalized_value, source_engine}
    Domain    domain=3;                                        // {entity_type_ref, raw_text, resolved_id, resolved_label, candidates[]}
  }
  BindingProvenance provenance=4;                              // S-1 + S-4 + RS-15 (see below)
  bool degraded=5;                                             // RS-25: set when the capability matrix forced a floor
}
message BindingProvenance {
  string vocabulary_source=1;                                  // RS-15 source tag
  string algorithm=2; double score=3; string tier=4;          // S-4 + reserved embedding-tier label (RS-14)
  string snapshot_hash=5;                                      // registry/vocab snapshot (one channel)
  repeated EngineVersion model_versions=6;                     // S-1
}
enum UniversalEntityType { PERSON=0; LOCATION=1; ORGANIZATION=2; DATE=3; MONEY=4; MISC=5; }

message AwaitingClarification {
  repeated Option options=1;
  string resume_token=2;                                       // HMAC — see §5
}
```

**MCP door — `resolve.*:v1` (RS-27; the door line = the determinism line):** exposes the deterministic core only. Bindings + universal spans + clarification options with signed resume tokens; provenance-carrying; **refusal-over-guess is contract-assertable**. The Resolving Agent's LLM ladder is never a door. Exact tool ids via the naming ledger (F-T1); the family `resolve.*:v1` is what this design pins. H-2 identity (bearer-only, OBO, fail-closed) on the door; RS-17's visible-by-declaration posture documented at the door too.

## 5. HMAC resume-token schema (workstream E; RS-26)

```
resume_token = base64url( payload ) "." base64url( HMAC-SHA256(key, payload) )
payload = {
  "conversation_id": "...",
  "parse_ref":       "...",        // parse state carried statelessly (no server session)
  "options":         [ {id, label, target_ref|resolved_id} ... ],   // the EXACT offered set
  "issued_at":       <epoch>,
  "key_id":          "..."         // supports rotation
}
```

**Rules.** Stateless (any replica verifies + resumes). A resume with a `selected_option_id` matching a signed option binds at confidence 1.0 with no re-fuzzy. **Signed pins are integrity-bearing under OBO** — the agent cannot fabricate "the user chose X"; the option set is signed, so a resume can only select from what the resolver actually offered. Key management lives in the chart (S-3 discipline); keys rotate by `key_id`. Agent-owned clarification (unsigned re-call) is explicitly *what the contract must not become* (RS-26).

## 6. Cross-cutting sweep contracts (S-1..S-4)

- **S-1 — model identity on the wire.** Every model-touched response carries `EngineVersion{engine, model, model_version}` (nlp `used[]`, fuzzy `vocabulary_version`, resolver `model_versions`, grounding `provenance`). No empty/default model parameter anywhere. Backends launch with explicit model ids (config + chart).
- **S-2 — one normalization spec.** `fold(text) = lowercase → NFD → strip combining marks`. Home: a shared normalization module depended on by fuzzy, the grounding kernel, and meta.search (physical home — a `ttr-text`-class lib vs living in `ttr-grounding-core` — is a P0 placement decision; the *spec* + its golden test-vectors are fixed here). Determinism and parity require byte-identical folding across all call sites.
- **S-3 — admin-gated operator endpoints.** `/refresh`-class endpoints (fuzzy vocabulary reload, NLP prompt/model reload, grounding operator hooks) require H-2 identity + an `admin` role check. Never unauthenticated in the open offering.
- **S-4 — one confidence contract.** All confidence fields are `[0,1]` and carry a `Provenance{producer, method}` tag. Documented breakpoints per producer; **producer-tagged, not blindly cross-comparable** — a consumer normalizes via the documented mapping, never numeric equality across producers. Threshold calibration is impl-level.

## 7. TTR-M lexicon surface (workstream A; grammar 4.3 arc — Q-13)

Canonical form = a `lexicon` model; inline `lexicon{}` = sugar (the binding pattern). Final syntax is a grammar-master feature; the *shape* pinned here:

```ttr
model lexicon                                   // locale rides the unit (RS-11; db…schema precedent)

def term    trzba { for: md.measure.net,  forms: ["tržba", "tržby", "obrat", "utržit"] }
def pattern nazev { for: db.query.by_name, match: "název .*" }
def example q1    { for: md.cubelet.sales, text: "Kolik jsme utržili za Octavie…" }

def measure net { /* ... */  lexicon { terms: ["tržba", "obrat", "utržit"] } }   // sugar → desugars to canonical
```

- **Targets:** `er` / `db` / `md` refs (md kinds now legal — closes the „utržili" gap, RS-10).
- **Boundary:** lexicon = what things are called; `search{}` = retrieval config (`searchable`, `fuzzy`) only. Legacy dispositions (RS-32): `aliases`+`search.{aliases,keywords}` → `term`; `search.patterns` → `pattern`; `search.examples` → `example`; `search.descriptions` → `description`. Standalone forms deprecate with this arc.
- **Member vocabulary (RS-12):** columns floor (`search{fuzzy}` + `nameAttribute`/`codeAttribute`) · `valueLabels` (small/coded) · `semantics{kind: alias_table}` for estate synonym tables (fuzzy loader ingests them — no grammar bump).
- **Consumer rule (RS-9 T3):** consumers (snapshot, resolver registry / `EntityTypeSpec`, meta doors) read the **canonical form only**; inline sugar desugars before it reaches them.

## 8. Diagnostics — `RG-*` (house convention: named, stable, fixture-backed)

| Id | Severity | Meaning |
|---|---|---|
| `RG-NLP-001` | error | no engine backend reachable for a routed (lang, op) at startup |
| `RG-NLP-002` | warning | route points at a `REMOTE_UNPINNED` (Lindat) tier — non-conformant for parity/determinism |
| `RG-NLP-003` | error | backend launched without an explicit model id (S-1 violation) |
| `RG-NLP-010` | info | unsupported (lang, op) — degrade floor applied (tokenize+fold+langid) |
| `RG-FUZ-001` | warning | declared fuzzy column skipped (composite/no PK) — surfaced in the loader report (B-T4) |
| `RG-FUZ-002` | error | explicit-but-unknown category (leak guard: returns EMPTY, never global) |
| `RG-GND-001` | warning | geo capability off (no Nominatim/PostGIS) — geo fixtures conditional (RS-19) |
| `RG-GND-002` | error | FX requested against a time-versioned `fx_rate` table without an as-of (fails loud) |
| `RG-RES-001` | info | binding `degraded` — capability matrix forced a floor (no cs NER / unsupported language) |
| `RG-RES-002` | error | resume token invalid/expired/blocked-key (HMAC verify failed) |

*(Ids are the pinned set; exact numbering finalizes with the naming ledger at implementation.)*

## 9. Published artifacts & repo layout

| Artifact | Content | Home |
|---|---|---|
| `ttr-nlp` (image + backends) | engine-free front + MorphoDiTa/NameTag3/Stanza/spaCy backend images | `tatrman-server` (Python service + backend Dockerfiles) |
| `ttr-fuzzy` | the vocabulary matcher | `tatrman-server/services/ttr-fuzzy` (exists) |
| `chrono`/`geo`/`money` + `ttr-grounding-mcp` | grounding services + thin MCP | `tatrman-server/services/*` |
| `org.tatrman:ttr-grounding-core` | RecipeBuilder/PlanExpr/SqlRenderer + fold kernel | `tatrman-server` (Kotlin lib) |
| `ttr-resolver` | the deterministic core + resolve door | `tatrman-server/services/ttr-resolver` |
| shared normalization lib (S-2) | `fold()` + golden vectors | `tatrman-server` shared libs |
| `org.tatrman:ttr-plan-proto` | plan.v1 wire format (recipes embed it) | lockstep, `python-plan/v*` + `kotlin-translator/v*` |

MCP surface deltas land in `mcp-surface.md` §2.2 (resolve door row + 3 grounding tool rows), additive under J-v2. Consumers: the Resolving Agent (kantheon), Golem, third-party MCP agents.

---

## Changelog

- **v1 · 2026-07-12** — initial consolidation from the Resolution & Grounding design convergence (RS-1..32). Proto renames to `org.tatrman.*`; `nlp.v1` gains BatchLemmatize + GetStatus/capability matrix; `fuzzy.match:v1` gains BatchMatch + source tags + provenance (additive); `resolver.v1` reshaped (NORMAL-mode machinery removed, provenance added); `resolve.*:v1` + kind-named `grounding.*:v1` MCP families reserved; S-1..S-4 contract shapes pinned; lexicon surface shape pinned (grammar arc = Q-13).
- **v2 · 2026-07-13 (RG-P4·S1/S2 — the lexicon grammar landed)** — grammar **4.4** (§7 realised): `lexicon` model code · `term`/`pattern`/`example` def kinds · `model lexicon locale <id>` header · inline `lexicon { … }` sugar on er/db + measure/dimension/cubelet carriers; `PATTERNS`/`EXAMPLES` added to `idPart` (S2) so the inline `lexicon { patterns, examples }` keys parse. **RS-32 legacy dispositions realised** as a second desugar source with named deprecation warnings (`ttr/lexicon-legacy-*`): entity `aliases` + `search { aliases, keywords }` → `term`; `search { patterns }` → `pattern`; `search { examples }` → `example`. **`search.descriptions` fold DECISION (RS-32 T3 deferred check, resolved):** grepped every tatrman consumer (LSP getSymbolDetail/search-hints, lint search-blocks, snapshot/meta doors, fuzzy loader) — **none reads `search.descriptions` distinctly from `description`**, so the default **fold** executes: `search.descriptions` deprecates (`ttr/lexicon-legacy-descriptions`) in favour of the single `description` property; no lexicon entry is produced (prose, not vocabulary). The `migrate-lexicon` codemod (`@tatrman/migrate`) rewrites `search { aliases/patterns/examples }` → inline `lexicon { … }` in place (locale-keyed `keywords` + entity top-level `aliases` are guided-manual). `search {}` slims to `searchable`/`fuzzy` (legacy sub-props still parse, now deprecated). Consumers read the **canonical** `desugarLexicon` output only (RS-9 T3), serialised by `serializeVocabularySnapshot` into RG-P2's `SnapshotVocabularySource` shape (`{ category==targetRef, targetRef, locale, values:[{id=ascii-fold, value}], patterns }`) — the alignment the RG-P2 stub→real swap targets.
  - **Q-15 coverage-lint DECISION (RS-32/T6):** the per-locale vocabulary-coverage lint ("data-bearing kind X has no lexicon coverage for deployment locale Y") is **DEFERRED** — tatrman has no configured *deployment locale* yet (the locale rides the lexicon unit, RS-11; there is no project-manifest deployment-locale key to lint against). Implementing a locale-targeted coverage warning now would hard-code an assumed locale. Revisit when the deployment-world/manifest carries a locale (ttr-metadata); a locale-agnostic "no declared vocabulary" hint is a possible future nicety. Recorded per T6's "record the decision either way".
  - **`valueLabels` A4-β widening LANDED (RS-12):** `valueLabelEntry` widened to a permissive `valueLabelValue`/`valueLabelField` object accepting BOTH the legacy `{ cs:…, en:… }` label AND `{ label: {…}, aliases: [ … ] }`; `ALIASES` added to `idPart`. Additive — legacy `valueLabels` walk + dump byte-unchanged (aliases present-only; fixtures 10/53 identical). The per-value aliases ride the snapshot as MEMBER vocabulary (origin `valueLabels`) via `serializeVocabularySnapshot` — `category = <attr ref>`, label per-locale + aliases base-locale. Cross-target byte-identical (TS↔Kotlin↔Python; fixture 64), except the pre-existing Python semantics-block gap (59/60).
