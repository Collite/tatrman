# E · Resolver — Options Catalogue

> **Status: CONVERGED 2026-07-12 — RS-22..26 in the control room §7** (all leans ratified, one refinement). Net shape: standalone `ttr-resolver` = **the deterministic core** (parse → universal mapping → batch-fuzzy span gating → bindings; zero LLM), snapshot-fed registry, capability-matrix-honest degrade, HMAC resume tokens. **The agent-side half of the split lands in the kantheon Resolving Agent — Themis (⚑ placement confirm later; its tests already consume resolver ENTITIES_ONLY) — which reuses `ttr-resolver`**: core fully binds ⇒ done, no LLM; else escalation ladder — **local LLMs** (via ttr-llm-gateway) for value-extraction-class precision, capable models for complex joint inference. `function_specs`/joint-inference leave the service contract. Q-20 gates core internals only; Q-21 = kantheon-side migration. This workstream **absorbs and re-cuts** `server/design/resolver-rewrite.md` (NEW-1, forks R1–R4) against two things the options doc didn't have: the recon facts (`02-recon-live-reference.md` §E — the live resolver is **LLM-in-the-loop on every path**) and the day's decisions RS-3..21, which pre-shape three of the four original forks.
> The hero is carried through E2, the load-bearing fork: **„Kolik jsme utržili za Octavie v pražských pobočkách za poslední fiskální čtvrtletí?"**

---

## 1. Facts & inherited shape (fixed ground)

- **Live pipeline** (recon §E.1): NLP parse (7 ops, pre-graph) → universal-label mapping → noun-head domain-span proposal → **LLM value-extraction filter (haiku, ALL modes)** → parallel fuzzy per candidate column → threshold/ambiguity logic → bindings — or **LLM joint-inference (sonnet, NORMAL mode)** binding function + args. HITL via HMAC resume tokens + option pins. **No LLM-free path exists today.**
- **Topology** (recon §E.2): consumes NLP (HTTP), fuzzy (gRPC), metadata (gRPC, startup registry), llm-gateway. Embeds only orchestration + decision logic. R1's consume-vs-contain sub-question: **answered — consume.**
- **P2 boundary (GI-1):** the deterministic spine may be statistical-deterministic, never generative; LLM proposal lives agent-side; the resolver's verdict is deterministic and provenance-carrying.
- **Parity bar (GI-3):** Czech morphology parity vs the pilot conversation corpus; eval seeds exist (50 NORMAL cases + 12 ENTITIES_ONLY cases + gated live specs).
- **Pre-shaping from RS-3..21:** registry/vocabulary channel = snapshot-fed (RS-15 built it for fuzzy — R3's lean now has a live sibling); one vocabulary matcher with source-tagged categories incl. lexicon terms (RS-15) and locale (RS-11); NLP behind a stable contract with capability matrix (RS-7/8); grounding stays separate services with the agent-side GroundEntities orchestration (RS-18/21); embedding tier = **separate feature** (RS-14), seam reserved.
- **Naming:** `resolver.v1` proto name reserved, never published — the open `org.tatrman.resolver.v1` is free to reshape (J-v2 binds published names only).

## 2. E1 · Placement (R1 revisited)

**E1-α — standalone `ttr-resolver` service.** The live shape continued: own service, own proto, chart roster line.
Buys: the recon removed the last doubt — the live resolver already *consumes* everything as services, so standalone is continuation, not re-architecture; independently scalable hot path; a service can be a door (F needs this open).
Costs: one more constellation member (already budgeted in the roster).

**E1-β — a `ttr-nlp` capability.** Now *worse* than when R1 catalogued it: RS-8 made the NLP front an engine-free router — folding an opinionated pipeline into it would un-decide RS-3.
**E1-γ — a library at consumers.** Kills the door option (F), scatters registry caches; Kotlin-only consumers assumed.
**E1-δ — fold into fuzzy.** RS-15 sharpened the layer boundary the other way: fuzzy is the *primitive* vocabulary matcher; the resolver is its biggest client.

*Lean: α — effectively a ratification; β/γ/δ carried from R1 as mapped-and-rejected-shaped.*

## 3. E2 · The pipeline re-cut (R2 + Q-6) — the load-bearing fork

**The problem, precisely.** The live resolver's two LLM calls do different jobs:
1. **Value-extraction (haiku, all modes)** — *span filtering + column tagging*: "which of these noun spans are literal values, and against which fuzzy columns should each be matched?" A precision/efficiency device — it *reduces* fuzzy fan-out and guesses target columns.
2. **Joint-inference (sonnet, NORMAL)** — *intent binding*: "which registered function does the user want, with which args?" This is genuinely call #2-shaped work (intent), living inside call #1's service.

P2 says the deterministic spine may not contain either. The fork is what the open rewrite does about each.

**E2-α′ — deterministic re-derivation (all-in-server).** Replace value-extraction with deterministic span gating: every proposed domain span (noun heads + n-gram windows) is matched via **batch fuzzy across declared categories** (B-T1's batch call makes the fan-out cheap; RS-15's source-tagged vocabulary means lexicon terms and member values gate together); score thresholds + entity-identity logic decide — exactly the ENTITIES_ONLY assembly, fed by deterministic candidate generation instead of an LLM filter. Joint-inference *also* re-derived deterministically (lexicon patterns → named/pattern queries).
Buys: the whole resolver is P2-clean; service-level parity measurable against both corpora; no LLM dependency on the resolve path at all (cost, latency, determinism all win).
Costs: the value-extraction *precision* the LLM bought must be re-earned by scoring (over-generation → more fuzzy calls + false candidates; the sibling-column and code-vs-name tricks need deterministic equivalents — **Q-20 spike**); deterministic joint-inference is a *hard* problem beyond pattern-shaped intents — this half risks the parity bar.

**E2-ε — the split resolver.** The server's `ttr-resolver` = the **deterministic core**: text/spans in → parse + universal mapping + deterministic span gating (α′'s mechanics) + fuzzy candidates + threshold/ambiguity + bindings out. **No LLM anywhere in it.** The LLM steps migrate agent-side where P2 already says they belong: the agent may pre-filter spans or post-select candidates with its own LLM calls (Golem keeps a value-extraction-shaped node if it wants the precision), and **function/intent binding (joint-inference) becomes explicitly agent-side** — it was call #2's work all along. `ResolveMode.NORMAL`'s function machinery (`function_specs`, joint-inference) leaves the service contract; the open resolver ≈ ENTITIES_ONLY, generalized and done properly.
Buys: P2-clean by *boundary*, not by re-solving hard problems — the deterministic core does what's deterministic; LLM precision lives where LLMs are allowed; the two-call thesis gets architecturally honest (resolver serves + scores + binds; agent proposes + intends); parity splits cleanly — **service-level parity on entity binding** (the ENTITIES_ONLY corpus is exactly this) + **E2E conversation parity** for the full flow (the conformance suite's job anyway); the resolve door (F) can promise determinism.
Costs: Golem changes (its resolve node re-plumbs: optional LLM pre-filter → core resolve → its own intent binding) — but Golem is kantheon-side reference code, already the case for grounding (GroundEntities precedent); "parity" for NORMAL mode is no longer measurable against the *service* alone (must be measured E2E — corpus mechanics, Q-5).
Cross-link: this is the resolver twin of what D already did — the deterministic recipe services + the agent-side orchestration node.

**E2-ζ — port as-is; the resolver is agent-tier (redraw P2).** Keep both LLM calls inside; declare the resolver a non-deterministic agent component.
Buys: fastest parity (it *is* the reference); no Golem re-plumb.
Costs: call #1 becomes non-deterministic *by design* — the architecture's headline ("deterministic after intent", provenance-carrying binding) loses its first half; the resolve door could never promise determinism to third parties; contradicts GI-1's explicit restatement for this component; the LLM dependency rides the hot path forever. Recorded to make the price of "easy" visible.

**(E2-β/γ embeddings — moved out.)** RS-14: vectorization = a separate feature; the tier seam (candidates labeled by tier, tier 2 unbuilt) is carried as a **contract shape**, not work.

**Hero through the fork.** „Kolik jsme **utržili** za **Octavie** v **pražských pobočkách** za **poslední fiskální čtvrtletí**?"
- *Universal spans:* NER tags `pražských pobočkách` (G→LOCATION), `poslední fiskální čtvrtletí` (T→DATE) — mapped, handed to grounding by the orchestrator (geo: Praha containment; chrono: fiscal quarter via period table, Q-18). Identical in α′/ε/ζ — this half never needed an LLM.
- *Domain spans:* `Octavie` (and `pobočkách` as an entity word). **α′/ε core:** all candidate spans → batch fuzzy: `octavie` scores high in the product category (lemma axis: `Octavie→octavia`), `pobočkách→pobočka` hits the branch entity's lexicon terms; thresholds bind; ambiguity → clarification candidates. **ζ:** haiku decides `Octavie` is a value for the product name column first.
- *Intent:* `utržili` → revenue measure. **ε:** the agent's LLM reads lexicon-fed context (`get_model` ships RS-10's measure vocabulary) and binds the measure in call #2 — or a deterministic pattern tier catches it first. **α′:** the server must do this deterministically (pattern/lexicon match on `utržit` — works for the declared case, brittle beyond it). **ζ:** sonnet does it in-service.

*~~Lean~~ **DECIDED 2026-07-12: ε with α′'s mechanics — RS-23**, with Bora's refinement: the agent-side half = a dedicated **Resolving Agent** in kantheon (**Themis** ⚑ — to be confirmed there; its test suite already consumes resolver ENTITIES_ONLY), not Golem. The Resolving Agent **reuses `ttr-resolver`** — deterministic core binds everything ⇒ job done, zero LLM; otherwise the **escalation ladder**: local LLMs (via ttr-llm-gateway — no cloud tier needed for span filtering) → capable models for complex joint inference. Golem consumes the Resolving Agent rather than owning resolution. ζ rejected with its price tag; embeddings = separate feature (RS-14).*

## 4. E3 · Vocabulary & registry (R3 revisited)

**E3-α — snapshot-fed registry.** The resolver builds its registry (entity types, categories, lexicon terms, locales, thresholds-relevant metadata) from **snapshot archives**, hash-keyed — the same channel RS-15 just committed fuzzy to; refresh discipline = B4-β's (snapshot-hash reload; staleness echoed).
Buys: R3-α's original case (versioned, reproducible, offline-testable) **plus** it is no longer unprecedented — one channel, two consumers, one discipline; registry and fuzzy vocabulary can never drift against each other (same snapshot hash or refuse).
Costs: unchanged from R3 (staleness window bounded by lock discipline; RO-13 review pins the archive schema — sequencing luck holds).

**E3-β — live metadata reads (startup registry).** The live shape; acceptable as the rewrite's step one (dev-mode), hardening into α before the parity gate.
**E3-γ — compile-emitted vocabulary artifact.** Falls back if snapshot serving slips (unchanged from R3-γ).
**E3-δ — resolver reads git.** Still recorded, still wrong (violates one-source-of-known).

*Lean: α, β as step one — the R3 lean ratified with RS-15 as its proof of life. Registry content addendum: caller-supplied `Registry` stays (per-request override), but the default registry is snapshot-born.*

## 5. E4 · Language architecture (R4 revisited) — ratification-shaped

RS-7/RS-8/RS-11 decided this fork's substance from the C and A sides: config-routed languages, capability matrix, per-locale lexicon, degrade floor. What E adds is the **consumer behavior**: the resolver reads the capability matrix and **degrades honestly** — no cs NER ⇒ universal-span hints thin out (domain resolution unaffected — span proposal is dep-parse/noun-head based, and even without parse, n-gram gating over fuzzy still works: the R4-γ floor, labeled in provenance); unsupported language ⇒ fold+fuzzy only, binding results labeled `degraded`.
*Lean: ratify as E4 = "R4-α+γ as already decided; the resolver is the first consumer that must visibly branch on the capability matrix." SPI deferred until language #2 (unchanged).*

## 6. E5 · HITL & the resume contract

**E5-α — keep stateless HMAC resume tokens + option pins.** The live mechanism enters the open contract: `AwaitingClarification` carries signed tokens embedding parse state + offered options; resume with a pinned option binds at confidence 1.0 without re-parse.
Buys: stateless (any replica resumes); **pins are integrity-bearing** — in the OBO world the agent is not trusted to invent options, and a signed pin proves "the user chose from what the resolver actually offered"; proven live incl. key rotation.
Costs: HMAC key management enters the open chart (secret discipline documented); token opacity (debugging).

**E5-β — server-side conversation state.** Session store keyed by conversation_id.
Costs: statefulness for no gain over α; replica affinity or shared store. Rejected-shaped.

**E5-γ — clarification fully agent-side.** The core returns ambiguity candidates + gaps as plain data; the agent owns the dialogue and re-calls resolve with explicit constraints (no tokens).
Buys: simplest proto; no keys.
Costs: loses pin integrity (the re-call's "user chose X" is agent-asserted — a fabrication surface exactly where refusal-over-guess matters); re-parse or client-cached parse on every resume.

*Lean: α — with the token schema documented as part of `org.tatrman.resolver.v1` (it is contract-visible) and γ noted as what the contract must NOT quietly become.*

## 7. Threads

- **E-T1 · Proto reshape:** `org.tatrman.resolver.v1` under the ε lean: `function_specs`/joint-inference machinery leaves; `EntityBinding` gains provenance fields (vocabulary source tag per RS-15, algorithm + score + tier label per the reserved seam, snapshot hash, model versions per S-1); `AnalyzeResponse parse` passthrough stays (agents want it — saves a double parse).
- **E-T2 · Confidence semantics:** one documented scale across fuzzy scores, binding confidence, and grounding confidence — today they're three local conventions; the door (F) will expose them side by side. Sweep-candidate (S-4?).
- **E-T3 · Caching:** nlpCache pattern stays consumer-side per RS-6/T3; the unused-resolutionCache wart from the live code dies in the rewrite (don't port dead machinery).
- **E-T4 · Grounding hand-off:** the resolver detects + maps universal spans; **who calls grounding** (agent node today; possibly F's composite door tomorrow) is F's fork — E's contract just guarantees spans + kinds are in the response either way.

## 8. Open questions raised here

- **Q-20 — deterministic span-gating spike:** does all-spans × batch-fuzzy score gating reach value-extraction's precision on the seed corpus (incl. the sibling-column and code-vs-name behaviors)? The one empirical gate on the ε lean's core. Runnable against `seed.jsonl` + `ucetnictvi_entities_only.jsonl` mechanics.
- **Q-21 — NORMAL-mode migration:** the exact Golem re-plumb under ε (which nodes appear/move; what the reference implementation must demonstrate for the E2E parity claim) — kantheon-side planning material, but the *contract* consequences (proto slimming, E-T1) close here.
