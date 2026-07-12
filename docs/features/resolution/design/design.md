# Resolution & Grounding — Design

> **Audience: the next `/planning` session.** This is the compact, consumable result of the Resolution & Grounding design effort (call #1 of the two-call thesis). It states what was decided, the constraints those decisions place on contracts and topology, what is in and out of scope, and the exact inputs planning must carry forward. It does **not** re-argue the options — that record is the control room decision log (`00-control-room.md` §7, entries **RS-1..32**) and the option catalogues (`03-A`..`08-F`). Where a decision is cited as `RS-n`, the full rationale and rejected alternatives live there. The exhaustive prose companion for human readers is `detailed-design.md`.
>
> **Effort status:** all six workstreams 🟢; consolidation sweep closed (RS-31/RS-32). **Design is complete; planning has not started.**
>
> **What this designs:** how a user's words become bound model entities, grounded universal values (time/place/money), and searchable member vocabulary — one design across four server components (`ttr-resolver`, `ttr-fuzzy`, `ttr-nlp`, the grounding services + `ttr-grounding-mcp`) plus the TTR-M language surface they consume and the kantheon-side **Resolving Agent** that wraps them.

---

## 1. The converged architecture in one picture

```
                    ┌─────────────────────────── kantheon (agent tier, generative allowed) ──┐
                    │  Resolving Agent (⚑ Themis — placement confirm kantheon-side)           │
                    │    reuse ttr-resolver core → fully bound? done (0 LLM)                   │
                    │    else escalation ladder: local LLMs → capable models (joint inference) │
                    │  Golem consumes the Resolving Agent (no longer owns resolution)          │
                    └────────────▲──────────────────────────────────────────────────▲─────────┘
                                 │ reuse (gRPC)                                       │ grounding tools
   ═══════════ THE DOOR LINE = THE DETERMINISM LINE ═══════════════════════════════════════════
                                 │                                                    │
        ┌────────────────────────┴─────────┐                      ┌──────────────────┴───────────┐
        │  ttr-resolver  (DETERMINISTIC     │                      │  grounding services           │
        │  CORE, zero LLM)                  │                      │  chrono · geo · money         │
        │  parse → universal mapping →      │   consumes           │  + ttr-grounding-mcp          │
        │  all-spans × batch-fuzzy gating → │◄────────┐            │  on ttr-grounding-core kernel │
        │  thresholds/identity → bindings   │         │            │  (rules-first, LLM-fallback   │
        │  + HMAC resume tokens             │         │            │   off by default)             │
        └───────┬───────────────┬───────────┘         │            └──────────────────────────────┘
                │ gRPC          │ gRPC                 │ HTTP/gRPC
        ┌───────▼───────┐  ┌────▼──────────┐   ┌───────▼────────────────┐
        │  ttr-fuzzy    │  │  metadata /    │   │  ttr-nlp  (engine-free  │
        │  THE vocab    │  │  snapshot      │   │  front: contract +      │
        │  matcher      │  │  (registry +   │   │  routing + langid)      │
        │  members +    │  │  vocabulary,   │   │   ├─ MorphoDiTa backend  │
        │  declared     │  │  one snapshot- │   │   ├─ NameTag 3 backend   │
        │  vocabulary,  │  │  hash channel) │   │   ├─ Stanza backend      │
        │  source-tagged│  │                │   │   └─ spaCy backend       │
        └───────────────┘  └────────────────┘   │  (models baked per img) │
                                                 └─────────────────────────┘
                          ▲
                          │ all four are governed by
        ┌─────────────────┴───────────────────────────────────────────┐
        │  TTR-M model surface: the `lexicon` model (canonical) +      │
        │  inline `lexicon{}` sugar; `search{}` = retrieval config;    │
        │  `semantics{}` = grounding hints; member vocab = columns +   │
        │  valueLabels + declared alias_table semantics                │
        └───────────────────────────────────────────────────────────────┘
```

**The thesis in one sentence:** the model *declares what things are called*; `ttr-nlp` supplies deterministic linguistic primitives; `ttr-fuzzy` is the one Czech-aware matcher over all vocabulary; the grounding services deterministically resolve universal values; `ttr-resolver` composes these into a **deterministic, provenance-carrying** binding with zero LLM in it; and everything generative lives one layer up in the kantheon Resolving Agent — so the `resolve` door can *promise* determinism to third parties.

---

## 2. Principles carried into planning (ground truth: control room §2/§3)

- **P2 / GI-1 — no LLM in the deterministic path.** The resolver core and the grounding services are statistical-deterministic (pinned models ⇒ reproducible outputs) but never generative. Generative steps live agent-side. This is the load-bearing constraint the whole split serves.
- **GI-2 — the MCP surface is pinned and extends additively (J-v2).** `fuzzy.match:v1` is contracted; `grounding.*:v1` and `resolve.*:v1` ids are reserved and enter additively; published names never rename.
- **GI-3 — the parity bar is Czech morphology parity against the pilot conversation corpus.** The conformance suite inherits resolution/grounding assertions.
- **GI-4 — open vs commercial line:** the four components are Apache-2.0/open; **continuous harvest** of member vocabulary is the commercial tier (it plugs into the open `LoaderSource` SPI). One-shot/on-refresh loading is open.
- **GI-8 — the legacy TTR-M vocabulary surface is free to redesign** (it was YAML carry-over, `search{}` unused except `fuzzy`). This licensed the lexicon redesign (A).
- **FI-5 — vocabulary is declared in the conceptual model.** The LLM is *one* consumer of declared vocabulary, never its owner.

---

## 3. Decisions by component (what planning implements)

### A — TTR-M vocabulary surface (RS-9..12, RS-32)

- **A `lexicon` model is the canonical vocabulary form**, mirroring TTR's binding pattern (standalone `er2db_*` canonical ↔ inline `binding:` sugar). Canonical entries are `term` / `pattern` / `example`-class defs targeting `er` / `db` / `md` refs; **inline `lexicon{}` blocks on definitions are sugar** that desugar to canonical entries. `md` kinds (`measure`/`dimension`/`cubelet`) become legal targets — this closes the „utržili"→revenue gap (RS-10). *(Sketch syntax in `03-A` §2a; final syntax is a grammar-master feature, Q-13.)*
- **Boundary sentence (pin it):** *lexicon = what things are called; `search{}` = how retrieval treats them.* `search{}` slims to retrieval config (`searchable`, `fuzzy`). The in-flight search-block feature proceeds unchanged — its flag relocation *is* the end state.
- **Locale rides the lexicon unit** (per-locale lexicon files/models — the `db … schema` header precedent), and **runtime lemmatization (C's backends) bridges inflection.** The division of labor: **declare across derivation and languages; compute across inflection** (RS-11). Inline sugar defaults to the deployment/base locale.
- **Member vocabulary = three tiers** (RS-12): (α) columns floor — model declares *which* columns are member vocabularies + `nameAttribute`/`codeAttribute`, values stay data; (β) `valueLabels` = the small-set/coded vocabulary (already localized); (γ) estates with synonym/alias tables declare their meaning via `semantics{kind: alias_table}` (the fx_rate/period_table pattern — closed-vocabulary evolution, **no grammar bump**); the fuzzy loader (B) ingests declared alias tables alongside primary name columns.
- **Legacy dispositions (RS-32):** entity `aliases` + `search.{aliases,keywords}` → lexicon **term** entries; `search.patterns` → **pattern** entries; `search.examples` → **example** entries; `search.descriptions` → fold into `description` (pending a consumer check at the grammar arc). Standalone forms deprecate with the lexicon 4.3 feature.

### B — Member & entity search / `ttr-fuzzy` (RS-13..17)

- **`ttr-fuzzy` becomes THE vocabulary matcher** (RS-15): one Czech-aware engine (fold + lemma + IDF + cascade) over member values **and** declared vocabulary (lexicon terms, valueLabels), **source-tagged** categories distinguishing member-candidates (→ `EntityBinding.resolved_id`) from vocabulary-candidates (→ target refs). `meta.search` stays for catalog/browse UX; resolution-grade matching consolidates in fuzzy. Declared vocabulary reaches fuzzy via the **snapshot read at refresh** — this makes fuzzy R3-α's **first live consumer** of snapshot archives.
- **Sourcing:** live-SQL load now (hardened live shape, incl. RS-12-γ alias tables), the **built vocabulary artifact** (open indexer CLI → hash-keyed artifact; fuzzy drops DB credentials) as the named target on the existing `LoaderSource` SPI (RS-13, β = target).
- **Match engine:** port the live engine (token/lemma index + IDF + order bonus + cascade) to parity; evolve internally toward index-primary/cascade-as-rescorer once Q-17's referee corpus can gate it (RS-14). **Vectorization/semantic matching is a SEPARATE FEATURE**, out of this effort; landing points reserved (the R2-γ resolver tier seam + B2's index seam).
- **Refresh discipline** (RS-16): interval + atomic swap (hardened) · snapshot-hash-keyed reload of declared-vocabulary config (two staleness clocks) · staleness/version echoed in responses + GetStatus. **CDC/continuous freshness = the commercial harvest line** (GI-4) — a platform-tier loader on the open `LoaderSource` SPI.
- **Exposure posture (RS-17, resolves Q-16, pre-debut):** declaring a column searchable/fuzzy **is** declaring its values readable within the deployment — the model author's declaration is the consent. **No category ACLs or per-caller filtering in v1.** Duty = document prominently (modeling manual at the `search{fuzzy}` flag + security docs); import-schema/lint *may* warn on sensitive-looking searchable columns (planning detail, Q-15-adjacent).

### C — NLP service / `ttr-nlp` (RS-3..8)

- **Engine-free front over per-engine backends** (RS-3/RS-8): `ttr-nlp` = contract (`org.tatrman.nlp.v1`) + routing + langid only (no torch, no model files). **All** model-bearing engines run as backend services: MorphoDiTa, NameTag 3, Stanza, spaCy. Stanza is on the Czech hot path (cs DEP_PARSE → the resolver's span proposal consumes the dep parse).
- **Czech stack** (RS-4, FI-3): MorphoDiTa **and** NameTag 3 run as self-hosted upstream servers (MorphoDiTa `src/rest_server`, `nametag3_server.py`) as in-cluster backends; existing HTTP engine adapters just repoint. **Lindat endpoints = a labeled dev/eval tier** — a Lindat-pointed deployment is **non-conformant** for parity/determinism (question-text egress, 5/min, unpinned models).
- **Model distribution** (RS-5): models **baked into their backend service images** (each backend carries exactly its own model, digest-pinned; the front carries none). Offline by construction. Revisit trigger for the mounted-artifact scheme (RS-5-β): multilingual growth or per-estate model selection.
- **API** (RS-6): `org.tatrman.nlp.v1` = the ops-bitmap `Analyze` on **gRPC** (REST for local dev/health only) **plus a bulk/batch lemmatize call** (shape sized by Q-11, must hold at both the front and backend hops). Consumers keep caching ownership; responses echo model versions as cache keys. COMPARE demoted to a debug/eval flag outside conformance. Op-profiles = later sugar (third consumer).
- **Language architecture** (RS-7): config-routed ops per language, **validated and surfaced by a capability matrix** (lang × op → engine + model version, via GetStatus, echoed in responses). Unsupported languages get the honest degrade floor (tokenize + fold + langid). Language-plugin SPI **deferred until language #2 is real**.

### D — Grounding services (RS-18..21)

- **Extraction shape** (RS-18): the live topology (chrono/geo/money + `ttr-grounding-mcp`, J-v2-renamed into `tatrman-server`) **plus a shared `ttr-grounding-core` kernel** consolidating the per-service `RecipeBuilder`/`PlanExpr`/`SqlRenderer` triple + the S-2 fold — extracted *during the move* (the cheapest moment). Enforces the `sql_preview`-derived-not-duplicated invariant once.
- **Geo offline story** (RS-19, resolves Q-7): compose — capability-honest **Nominatim seam** (configured endpoint, fail-loud UNAVAILABLE, GetStatus surfaces absence) + **boundary cache ON by default with an install-time priming step** (warm model POIs + member-data cities) + **geo-goes-dark floor documented** (conformance geo fixtures conditional on capability; the acceptance bar never blocks on geo). The **gazetteer/RÚIAN artifact = the named CZ-first arc** and the only path to *deterministic* geo; revisit trigger = air-gapped CZ estate or parity-corpus variance from Nominatim.
- **"Other services" admission rule** (RS-20): a grounder = *client-specific but rule-computable, span-detectable, recipe-expressible*. SPI-by-convention (the generic proto **is** the SPI). Adding one = new service on the kernel + model-declared semantics roles/kinds (closed-vocab evolution) + additive `grounding.*:v1` tool + rules-first/LLM-fallback-off + the documented **three-place growth checklist** (EntityKind enum · resolver label mapping · agent routing). **No new grounder inside this effort** — duration/quantity/percent candidates go to the parking lot with the rule applied.
- **Inheritance boundary** (RS-21): inherit the `[~]` DFP stages as-is with an explicit **fix-at-rename list** (J-v2 names · proto `ResponseMessage` import wart · S-2 fold consolidation into the kernel · S-3 on operator endpoints). Boundary pinned — **server-side:** chrono/geo/money + grounding-mcp + kernel + `org.tatrman.grounding.v1`; **agent-side (kantheon):** GroundEntities node + cascade consumption; **tatrman-side:** semantics-block 4.2 (in flight) + the semantics-passthrough tooling gap (A4's blocker — lands with the import-schema/lexicon arcs). The **109 + 21** grounding eval corpus feeds the conformance extended tier.

### E — Resolver / `ttr-resolver` (RS-22..26)

- **Standalone service** (RS-22): own `org.tatrman.resolver.v1`, roster line. (The live shape already consumes NLP/fuzzy/metadata as services — standalone is continuation.)
- **The split resolver** (RS-23, resolves Q-6) — the headline decision. Server `ttr-resolver` = the **deterministic core**: parse → universal mapping → deterministic span gating (all candidate spans × batch fuzzy over RS-15's source-tagged vocabulary; score thresholds + entity-identity logic) → bindings/clarifications. **No LLM anywhere in the service.** The LLM steps move agent-side into the kantheon **Resolving Agent** (⚑ Themis — placement confirm kantheon-side; Themis already consumes resolver ENTITIES_ONLY), which **reuses `ttr-resolver`**: core fully binds ⇒ done, zero LLM; else an **escalation ladder** — local LLMs (via ttr-llm-gateway) for value-extraction-class precision, then capable models for complex joint inference. `ResolveMode.NORMAL`'s function machinery (`function_specs`, joint-inference) **leaves the service contract** — intent binding was call #2's work all along.
- **Registry** (RS-24): **snapshot-fed** (hash-keyed, B4-β refresh discipline, staleness echoed) — the same channel RS-15 committed fuzzy to. One channel, two consumers → registry and vocabulary can never drift apart. Caller-supplied per-request Registry stays; live-metadata startup reads acceptable as dev-mode step one.
- **Language** (RS-25): the resolver reads the capability matrix and **degrades honestly** (no cs NER ⇒ universal hints thin, domain path unaffected; unsupported language ⇒ fold+fuzzy floor, bindings labeled `degraded`). The resolver is the first consumer that must visibly branch on the matrix.
- **HITL** (RS-26): stateless **HMAC resume tokens + option pins** in the open contract (token schema documented in `org.tatrman.resolver.v1`; key management in the chart per S-3). Signed pins are integrity-bearing under OBO — the agent cannot fabricate "the user chose X".

### F — Exposure & orchestration (RS-27..30)

- **`resolve` is a first-class MCP door** (RS-27, resolves Q-4/RQ-4 pre-debut) exposing the **deterministic core only** (`resolve.*:v1`; exact ids via the naming ledger): bindings + universal spans + clarification options with signed resume tokens; provenance-carrying; refusal-over-guess contract-assertable. **The door line = the determinism line** — the Resolving Agent's LLM ladder is never a door.
- **Grounding exposure = kind-named tools** (RS-28): `grounding.{time,geo,money}:v1`-class over the generic proto (generic inside, ergonomic names outside — LLM tool selection favors named tools) + capability/GetStatus surfacing; geo fixtures conditional-on-capability. New grounders add tools additively per RS-20's checklist.
- **Orchestration** (RS-29): composition is **agent work**. Third parties get the resolve door + grounding primitives + the **canonical cascade documented as conformance fixtures** (the `calls:` assertion schema). GroundingContext assembly stays agent-side (turn state lives there). The Resolving Agent = the reference orchestration (kantheon code, not contract).
- **Parity/conformance = three tiers** (RS-30, resolves Q-5/RQ-5): **gating service-level** (in-repo: ENTITIES_ONLY corpus + grounding 109+21 + Q-17 match fixtures — the SV-P3 parity instrument, no DFP dependency) · **gating E2E core tier** (hand-authored resolution/grounding conversation fixtures join the RO-25 core ~25–40 @100%, authored SV-P4 with the reference Golem; refusal-over-guess + clarification round-trips) · **non-gating extended tier** (anonymized pilot corpus via RO-19 ask ③ — scores the split's E2E parity, gates nothing).

---

## 4. Cross-cutting sweep ratifications (RS-31/RS-32) — contract-shaping

These are contract-visible and therefore fixed inputs to planning, not planning choices:

- **S-1 — no server-default models on the wire.** Config pins engine + model version per NLP backend; every model-touched response echoes the version it used (`nlp.v1` responses; fuzzy `vocabulary_version` per category; resolver `EntityBinding` provenance; grounding `source` + model tag). No component selects a model by empty/default parameter. *(Kills the live MorphoDiTa empty-`model` bug; makes GI-1 replay auditable.)*
- **S-2 — one pinned normalization spec.** Fold = lowercase + NFD + strip combining marks, as a shared library + written spec; the ≥5 copies collapse onto it. Physical home (a shared `ttr-text`-class lib vs living in `ttr-grounding-core`) = a **planning placement detail**; the spec is the determinism/parity anchor — every matcher folds identically.
- **S-3 — admin-role-gated operator endpoints.** `/refresh`-class endpoints (fuzzy reload, NLP prompt reload, grounding operator hooks) require H-2 identity + role check; never unauthenticated in the open offering.
- **S-4 — one confidence contract, three producers.** Fuzzy scores, resolver binding confidence, and grounding confidence converge onto a shared `[0,1]` scale carried with a **mandatory provenance tag** (producer + method: fuzzy algorithm/tier, resolver gate, grounding `RULES|LLM`). Documented breakpoints; the door may present all three but the contract states they are **producer-tagged, not blindly cross-comparable** (normalize via the documented mapping). Threshold calibration = impl detail; the **shared range + mandatory provenance** is the pinned contract shape.

---

## 5. Contract surfaces to declare or change (the wire consequences)

Planning turns these into proto + mcp-surface + naming-ledger work:

- **`org.tatrman.nlp.v1`** (renamed from `cz.dfpartner.nlp.v1`): gRPC `Analyze` (ops-bitmap) + **batch lemmatize** call; capability matrix via GetStatus; responses echo model versions (S-1). REST kept for local dev/health only.
- **`fuzzy.match:v1`** (pinned — additive only, B-T1): candidates `locale?` on match, **batch match** (N spans × categories in one RPC — cuts the resolver's hot-path fan-out), category-discovery/GetStatus, `vocabulary_version` + source-tag + confidence-provenance (S-1/S-4) on results. **No rename of `fuzzy.match:v1`.**
- **`org.tatrman.grounding.v1`** (renamed): keep the generic `Ground`/`GetStatus`; fix the `ResponseMessage` import wart at rename; `GroundingContext` becomes server-owned proto (D-T1). MCP: **kind-named tools** `grounding.{time,geo,money}:v1` (mcp-surface §2.2 rows).
- **`org.tatrman.resolver.v1`** (reshaped — never published before, free to reshape): `function_specs`/joint-inference machinery **leaves**; `EntityBinding` gains provenance (vocabulary source tag per RS-15, algorithm + score + tier label, snapshot hash, model versions per S-1); HMAC resume-token schema documented; `AnalyzeResponse parse` passthrough stays. MCP: **`resolve.*:v1` door** (mcp-surface §2.2 row: OBO, resume-token semantics, refusal-over-guess assertion).
- **Snapshot archive schema** (RO-13 core ⚑ review): now has **two** consumers (fuzzy vocabulary + resolver registry) on one hash-keyed channel — the review's sequencing must account for both.

---

## 6. Scope boundaries

**In scope (this effort designed it; planning builds it):** the lexicon model surface; `ttr-fuzzy` as the vocabulary matcher + built-artifact target; `ttr-nlp` engine-free front + self-hosted Czech backends + baked models; the three grounding services on the `ttr-grounding-core` kernel with the geo capability posture; the split `ttr-resolver` deterministic core; the `resolve` door + kind-named grounding tools; the three-tier conformance mechanics.

**Explicitly out of scope / deferred (parking lot, with revisit triggers):**
- **Vectorization / semantic (embedding) matching** — a SEPARATE FEATURE (RS-14). Landing points reserved: the resolver R2-γ tier seam + fuzzy's B2 index seam. Revisit: after parity, when recall gaps are measured → its own design folder.
- **No new grounder** (duration/quantity/percent) — parked with the RS-20 admission rule applied.
- **Language #2+ SPI hardening** — the second production language defines the interface (RS-7/RS-25).
- **Continuous harvest (CDC) of member vocabulary** — the commercial tier on the open `LoaderSource` SPI (GI-4/Q-8).
- **Learned/teach-in vocabulary** (mine logs → propose via PR) — post-debut enrichment (RS-10-δ).
- **DeriNet derivational expansion** — new engine + licensing sibling of FI-4 (RS-11-δ).
- **UFAL model licensing/redistribution mechanics** (CC BY-NC-SA in published images) — FI-4 parked legal item, now concretely shaped (public GHCR vs restricted registry for UFAL-model backends).
- **Intent/measure vocabulary covered by the conceptual model itself** — a named future arc; RS-10 is the declared-surface step, not the endpoint.

---

## 7. Planning inputs — carry these forward

**Open questions demoted to planning inputs (not design gates):**

- **Q-8** — the open one-shot/on-refresh loading vs commercial continuous-harvest boundary (the `LoaderSource` SPI is the seam).
- **Q-9** — NameTag 3 / MorphoDiTa model distribution mechanics + versioning/pinning + size/licensing-technical constraints (image-baked per RS-5; redistribution mechanics = the FI-4 legal item).
- **Q-10** — backend spike: NameTag 3 / MorphoDiTa self-hosted server CPU memory/latency/cold-start/throughput **and protocol-parity verification** (Lindat ↔ self-hosted) so the RS-4 endpoint swap is proven. *Runnable now; phase-0 candidate.*
- **Q-11** — bulk lemmatization sizing (pilot member-vocabulary cardinalities + refresh cadence) → the `nlp.v1` batch contract shape; must hold at both front and backend hops.
- **Q-13** — grammar sequencing: the **lexicon model arc** (new model code + def kinds + inline sugar + search-block slimming + A4-β widening) as one grammar 4.3-class feature through grammar-master, after 4.2 merges; the in-flight search-block feature proceeds independently.
- **Q-15** — vocabulary-coverage lint: should conformance/model-lint assert hero-class vocabulary per locale?
- **Q-17** — `ttr-fuzzy` match-quality referee corpus (diacritics, inflection, multi-word order, typos) so B2's index evolution has a gate; seeds = RO-25 floor + ai-platform eval corpora.
- **Q-18** — fiscal-quarter coverage: verify the recognizer + period-table semantics cover quarter-class periods („fiskální čtvrtletí", `yyyyQn`-class codes) — hero-parity-relevant; small extension if absent.
- **Q-19** — Nominatim default-endpoint policy (likely: no default + self-host guidance in docs).
- **Q-20** — deterministic span-gating spike: does all-spans × batch-fuzzy score gating reach the LLM value-extraction's precision on the seed corpus (incl. sibling-column, code-vs-name)? **The one empirical gate on the split's core.** Runnable against `seed.jsonl` + `ucetnictvi_entities_only.jsonl`. *Phase-0 candidate.*
- **Q-21** — NORMAL-mode migration under the split: the kantheon Golem/Resolving-Agent re-plumb + what the reference must demonstrate for E2E parity; contract consequences (proto slimming) close in E.

**Threads riding into planning (per-workstream detail, not re-decided here):** RS-9 T1 (desugar conflict rules — copy binding's discipline), T2 (lexicon entry schema — grow additively, start minimal), T3 (canonical-form-only consumer propagation) · B-T1..T4 (additive contract candidates · normalization spec home · replica strategy · PK-skip loader report) · C4-T1..T3 (gRPC transport · batch shape · caching ownership) · D-T1..T4 (GroundingContext ownership · PostGIS capability · fiscal-quarter granularity · LLM-fallback posture) · E-T1..T4 (proto reshape · confidence semantics = now S-4 · cache hygiene, drop dead `resolutionCache` · grounding hand-off) · F-T1..T3 (naming-ledger tool ids · identity/OBO on both doors · parse passthrough).

**⚑ Cross-repo confirm:** Resolving Agent placement (Themis) is a **kantheon-side** confirmation — this design pins the *contract* (reuse `ttr-resolver`, escalation ladder, function-binding is agent-side), not the kantheon node topology.

---

## 8. Ecosystem register updates planning must apply

- **`docs/ecosystem/next-steps-260710-execution.md` item 4** — RS-2 amended the "resolver converges at SV-P3 planning" line: convergence is done here; SV-P3 planning now **consumes this `design.md`** rather than re-diverging. Update the item to point at this design.
- **`docs/ecosystem/server/design/mcp-surface.md` §2.2** — add the `resolve.*:v1` door row (OBO, resume-token semantics, refusal-over-guess assertion) and the three `grounding.{time,geo,money}:v1` tool rows (capability/GetStatus surfacing, conditional-on-capability geo fixtures). Both under J-v2 additive rules; no existing id changes. §6's schedule of grounding fixtures is satisfied by RS-28.
- **`docs/ecosystem/server/design/architecture.md` §3** — the "one undesigned component" (`ttr-resolver`) is now designed: update the roster line to the split (deterministic core service + kantheon Resolving Agent), and add `ttr-grounding-core` to the roster.
- **`docs/ecosystem/server/design/resolver-rewrite.md`** — mark superseded by this design (its R1–R4 forks + RQ-1..5 are absorbed and resolved: R1→RS-22, R2→RS-23, R3→RS-24, R4→RS-25; RQ-4→RS-27, RQ-5→RS-30).
- **RO-13 core ⚑ review (snapshot archive schema)** — record that it now has two consumers (fuzzy + resolver) on one channel.

---

## 9. Sequencing hints (for planning to shape, not binding)

The dependency order from the control room dashboard: **A and C are the feet** (everything consumes vocabulary + NLP primitives) → B, D stand on them → E composes B/C/D → F exposes E. Concretely: the lexicon grammar arc (A, Q-13) gates on 4.2 merging and runs through grammar-master independently; C's self-hosting delta (Q-10 spike) unblocks fuzzy's lemma axis (B) and the whole determinism claim; the resolver split (E) gates on Q-20's span-gating spike and on B's batch-match contract; the doors (F) and the SV-P3 gating conformance instrument gate on E's core being real. **Phase-0 spikes named as runnable now:** Q-10 (backend protocol parity + sizing) and Q-20 (deterministic span-gating precision).

---

*End of design.md. The exhaustive companion is `detailed-design.md`; the divergence/decision record is `00-control-room.md` (§7 = RS-1..32) and `01`–`08`.*
