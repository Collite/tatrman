# Resolution & Grounding — Control Room

> The design record for the **understanding layer** of Tatrman Server: how a user's words become bound model entities, grounded universal values (time / place / money / …), and searchable member vocabulary — i.e. **call #1 of the two-call thesis**, designed as one effort across four components: `ttr-resolver`, `ttr-fuzzy` (+ member search), `ttr-nlp`, and the grounding services (`chrono`/`geo`/`money` + `ttr-grounding-mcp`), plus the TTR-M language surface they all consume.
>
> Opened 2026-07-12 (Bora). Method: the house diverge-then-converge discipline (reference: `../../ttr-p/design/00-control-room.md` §0; compact-feature precedent: `../../import-schema/design/00-control-room.md`). Ecosystem-level decision ground truth stays the platform control room §7 (`../../../ecosystem/platform/design/00-control-room.md`); **this doc's §7 log holds the feature-level decisions (RS-n).**
>
> **Naming note:** "resolution" here = *entity/value resolution at question time* (the `ttr-resolver` family), **not** the compiler's QName/symbol resolver (`docs/features/grammar-master/resolver-consolidation/` — unrelated arc).

---

## 1. Why this effort exists

- The Server architecture calls `ttr-resolver` **"the one undesigned component"** (`../../../ecosystem/server/design/architecture.md` §3); `../../../ecosystem/server/design/resolver-rewrite.md` captured options only (forks R1–R4, leans, RQ-1..5) and scheduled convergence for SV-P3 planning.
- Grounding was barely designed on the tatrman side at all: `ttr-grounding-mcp` is a **reserved** door, `grounding.*:v1` "pinned at SV-P3" (mcp-surface.md §2.2/§6) — but a **full DFP-side design corpus exists** (`ai-platform/feature-grounding-{architecture,contracts,plan}.md` + 14 task-stage lists) that this effort inherits as prior art (see §4 asset inventory).
- Member/dimensional-value search has no design doc anywhere; the fuzzy-matcher's loader (metadata-declared fields → SQL against source DBs) is undocumented live behavior.
- `ttr-nlp` has one written sentence of design ("Python service stays Python"). Czech strategy (NameTag 3 + MorphoDiTa, self-hosted) is decided nowhere.
- These four are **one design problem**: the resolver *consumes* fuzzy/NLP/grounding; grounding and search both hang off model-declared vocabulary; the language architecture cuts across all of them. Designing them separately would decide each fork three times.

## 2. Framing inputs (Bora, 2026-07-12 session open)

- **FI-1** — Grounding + member/entity search + resolver are designed **together**: the resolver must resolve *everything* including grounded terms, and therefore needs first-class access to search and fuzzy matching.
- **FI-2** — The NLP service becomes **heavily used** across the family (not a resolver-private helper); its design must anticipate multiple consumers.
- **FI-3** — Czech NLP = **UFAL NameTag 3 + MorphoDiTa** (https://ufal.mff.cuni.cz/nametag/3, https://ufal.mff.cuni.cz/morphodita), **downloaded and incorporated into the server offering** — self-hosted models, not UFAL's online API. English groundwork already exists (Stanza + other engines in `ai-platform/infra/nlp`).
- **FI-4** — UFAL licensing/commercial terms are **out of scope for this effort** (historically a commercial agreement existed; technicalities first, legal later — tracked as a parking-lot item, not a design input).
- **FI-5** — Specific vocabulary — dimensional members, entity names, domain terms — is **declared in the conceptual model** (TTR-M): the language surface for it is part of this design.

## 3. Grounding inputs — constraints inherited, not this effort's to reopen

- **GI-1 (P2, ecosystem.md)** — No LLM in the deterministic path. The resolver may be *statistical-deterministic* (pinned model files ⇒ deterministic outputs) but never generative; LLM proposal lives agent-side; the resolver's verdict is deterministic and provenance-carrying (resolver-rewrite.md §6 restatement).
- **GI-2 (RO-25)** — The MCP surface is pinned: `fuzzy.match:v1` exists as contracted (cascade `LEVENSHTEIN | TATRMAN | JARO_WINKLER`, Czech diacritics contract-observable); `grounding.*:v1` ids are reserved; new tools **extend** the surface under J-v2 rules — published names never rename.
- **GI-3 (SV-P3 parity bar)** — Czech morphology parity against the pilot's conversation corpus is the DONE-when; the conformance suite inherits resolution assertions.
- **GI-4 (STRAT-2)** — Resolver, fuzzy, NLP, grounding are all **open** (Apache-2.0, interoperate side of the license rule). Continuous harvest is commercial — relevant wherever member vocabulary wants "continuously refreshed from source DBs".
- **GI-5 (RO-5 / R3 context)** — Veles is the single source of *what is known*; the RO-13 core ⚑ review (snapshot archive schema) is pending and the resolver options lean on it (R3-α). Sequencing: that review now has this effort as a second consumer.
- **GI-6 (naming, J-v2)** — `resolver.v1` proto name reserved; personas never on the wire; Python service stays Python (`org.tatrman.nlp.v1` per server contracts §renames).
- **GI-7 (R1–R4 leans)** — The resolver-rewrite leans carry into divergence *as leans, not defaults*: standalone service · deterministic-to-parity with a named tier seam · snapshot-fed vocabulary · Czech-first + honest degrade floor.

## 4. Asset inventory (what already exists — recon of 2026-07-12, live repos connected)

| Asset | Where | State |
|---|---|---|
| Resolver options catalogue (R1–R4, RQ-1..5) | `docs/ecosystem/server/design/resolver-rewrite.md` | options only — absorbed here as workstream E's starting map |
| Live resolver (Koog agent graph, LLM-assisted extraction, fuzzy+metadata clients, HMAC, cache) | `ai-platform/agents/resolver/` | live at pilot; **recon report → `02-recon-live-reference.md`** |
| DFP grounding design corpus + chrono/geo/money services (recipe/PlanExpr model, semantic discovery, Nominatim geo) | `ai-platform/feature-grounding-*.md`, `ai-platform/services/{chrono,geo,money}` | live/in-rollout DFP-side; extraction planned (Fork Phase 6) |
| Fuzzy-matcher (algorithm cascade, token/IDF matcher, lemmatizer hook, metadata→SQL vocabulary loader) | `ai-platform/services/fuzzy-matcher` | live; extracted lineage = `ttr-fuzzy` |
| NLP service (engine SPI: MorphoDiTa, NameTag, Stanza, spaCy, langid; FastAPI) | `ai-platform/infra/nlp` | live groundwork (FI-3's "Stanza included") |
| Protos: `resolver.v1`, `grounding.v1`, `fuzzy.v1` (`fuzzy_matcher.proto`), `nlp.v1`, `metadata.v1` | `ai-platform/shared/proto/` | live wire truth |
| Resolver/NLP eval corpora (`seed.jsonl`, `ucetnictvi_entities_only.jsonl`) | `ai-platform/{agents/resolver,infra/nlp}/eval/` | parity-corpus seed material (RQ-5) |
| MCP surface contract | `docs/ecosystem/server/design/mcp-surface.md` | pinned (GI-2); this effort supplies §2.2 grounding entries + answers RQ-4 |
| TTR-M `search { searchable, fuzzy, patterns }` block (all data-bearing kinds) | `docs/features/search-block/` (T1–T5) | designed/planned tatrman-side — workstream A ratifies & extends |
| TTR-M `semantics { role, kind, params }` hints (grounding discovery surface; grammar 4.2) | grounding arc's T1–T6 (tatrman side) + ai-platform A3 metadata surface | in flight; A4 df-annotation BA-gated |

## 5. Hero scenario (carried through every workstream)

> **„Kolik jsme utržili za Octavie v pražských pobočkách za poslední fiskální čtvrtletí?"**
> *(How much revenue did we make on Octavias in the Prague branches in the last fiscal quarter?)*

One Czech sentence, every component firing: **„Octavie"** — inflected member value (dimension: product), needs morphology + fuzzy + member vocabulary; **„pražských pobočkách"** — geo grounding *and* a modeled entity (branch), inflected adjective form; **„poslední fiskální čtvrtletí"** — chrono grounding against the *model's* fiscal calendar, relative to "now"; **„utržili"** — measure vocabulary (revenue) declared in the conceptual model; the whole binding returned with provenance, deterministically, so call #2's SQL never guesses.

## 6. Workstream dashboard

Status: ⚪ not started · 🔵 diverging · 🟡 options captured · 🟢 converged · ⏸ parked

| WS | Name | Status | The question | Doc |
|---|---|---|---|---|
| **A** | Model vocabulary surface (TTR-M) | ⚪ | What does the conceptual model declare for the understanding layer — aliases, searchable fields, member vocabulary, measure/domain terms, per-language labels, grounding hints (fiscal calendar, POIs)? Where in TTR-M grammar/semantics does it live? | `03-A-…` |
| **B** | Member & entity search | ⚪ | Where does member vocabulary physically come from (source-DB load vs snapshot vs harvest), how is it indexed/matched (cascade, tokens, IDF, lemmas), and what is `ttr-fuzzy`'s future shape? | `04-B-…` |
| **C** | NLP service | 🟢 (RS-3..8) | Engine/language architecture of `ttr-nlp`; Czech via self-hosted NameTag 3 + MorphoDiTa; model packaging & distribution in the open server offering; the API surface (`nlp.v1`) for heavy multi-consumer use (FI-2). | [`05-C-nlp-options.md`](./05-C-nlp-options.md) |
| **D** | Grounding services | ⚪ | chrono/geo/money extraction into the open lineage + generalization ("other services"); the recipe/PlanExpr contract; external-data dependencies (Nominatim, calendars, FX) and the offline/determinism story; `grounding.*:v1`. | `06-D-…` |
| **E** | Resolver | ⚪ | The pipeline and placement (converges R1–R4 + RQ-1..3): what is deterministic spine vs agent-side LLM; consume-vs-contain topology over B/C/D; vocabulary/cache discipline; provenance. | `07-E-…` |
| **F** | Exposure & orchestration | ⚪ | Who orchestrates call #1 (agent-side cascade vs a `resolve` door — RQ-4); MCP tool set; proto surfaces; conformance/parity corpus mechanics (RQ-5). | `08-F-…` |

Load-bearing order: **A and C are the feet** (everything consumes vocabulary + NLP primitives) → B, D stand on them → E composes B/C/D → F exposes E. Divergence can run A/C in parallel first.

## 7. Decision log (append-only: `date · [id] · decision · why · rejected`)

- **2026-07-12 · [RS-1] (Bora)** · Effort opened as one design across grounding + search/fuzzy + NLP + resolver, doc home `docs/features/resolution/design/` with its own compact control room (import-schema precedent). · Why: FI-1 — the components are one design problem; separate efforts would re-decide shared forks. · Rejected: growing `ecosystem/server/design/` flat (no room for a multi-workstream option corpus); per-component efforts (cross-cutting forks — language, vocabulary, topology — would fragment).
- **2026-07-12 · [RS-2] (Bora)** · Posture = **full design now**: diverge *and* converge all workstreams in this effort; SV-P3 planning consumes the resulting `design.md`. **Amends** the "resolver converges at SV-P3 planning" line (`ecosystem/next-steps-260710-execution.md` item 4); RQ-4 is answered here, before the debut surface is declared complete. · Why: live references are connected now (ai-platform + kantheon); grounding's DFP-side design corpus makes early convergence evidence-based, not speculative. · Rejected: diverge-only with SV-P3 convergence (a second cold-start over the same material); design-only/no-plan-impact (would leave the plan's registers stale against a converged design).
- **2026-07-12 · [RS-3 = C1-β] (Bora, Claude concurring)** · `ttr-nlp` = thin front (the `nlp.v1` contract, routing, langid) + heavyweight engines as **endpoint-configured backend services**. · Why: loosest coupling; endpoint swap is pure config — self-hosted in-cluster servers for real deployments, UFAL Lindat for dev/eval; cold-start and footprint isolation; slim front image. · Rejected: α single fat service (heaviest engine dictates the pod; readiness coupling); γ in-process-at-consumers (NameTag 3 is Python-only — Kotlin consumers can't embed it; N pinning disciplines); δ fold-into-resolver (contradicts FI-2).
- **2026-07-12 · [RS-4 = C2-β for BOTH tools] (Bora, Claude concurring)** · MorphoDiTa **and** NameTag 3 run as self-hosted upstream servers (MorphoDiTa `src/rest_server` confirmed in-tree; `nametag3_server.py`) deployed as in-cluster backends; the existing HTTP engine adapters just repoint. **Riders:** (i) Lindat endpoints remain a **labeled dev/eval tier** — the capability matrix (C5-γ) must surface `remote + unpinned`; a Lindat-pointed deployment is non-conformant for parity/determinism claims (question-text egress, 5/min rate limit, no model pinning); (ii) protocol parity between Lindat and the self-hosted servers is verified in the Q-10 spike; (iii) S-1 applies — backends launched with explicit model identifiers. · Why: uniform topology (no special-casing the small tool); smallest code delta (adapters already speak the protocol); the in-cluster hop is negligible against the turn's LLM calls, and bulk lemmatize amortizes it. · Rejected: α in-process MorphoDiTa (microsecond-faster but breaks uniformity and re-couples the front to C++ deps); γ Lindat-as-production (the riders' reasons); δ replace-the-UFAL-stack (re-opens the parity bar; parked as FI-4's escape hatch).
- **2026-07-12 · [RS-5 = C3-α, relocated by RS-3/4] (Bora)** · Model files are **baked into their backend service images** (each backend carries exactly its own model, digest-pinned; the front carries none). · Why: offline by construction; one pinned artifact per backend; RS-4 already made the fat-image objection moot (per-backend images are small and single-purpose); matches the Stanza baking precedent. · Rejected: β mounted model artifacts (new convention + chart machinery for no current need — **revisit trigger:** multilingual growth or per-estate model selection makes images combinatorial); γ download-from-LINDAT at deploy (online install path breaks the stranger/air-gap flows); δ bring-your-own-models (worst onboarding). **Rider:** redistribution mechanics of CC BY-NC-SA models inside published images = the FI-4 parked legal item, now concretely shaped (public GHCR vs restricted registry for UFAL-model backends).
- **2026-07-12 · [RS-6 = C4-α + T1 + T2] (Bora, per lean)** · `nlp.v1` = the ops-bitmap `Analyze`, formalized as gRPC `org.tatrman.nlp.v1` (REST for local dev/health only), **plus a bulk/batch lemmatize call** (shape sized by Q-11, must hold at both front and backend hops); consumers keep ownership of caching (responses echo model versions as cache keys); COMPARE demoted to a debug/eval flag outside conformance; op-profiles (γ) = later sugar once a third consumer exists. · Rejected: β task-shaped RPCs (loses one-pass-many-ops economics; churns two live consumers); δ public NLP MCP door (extends the RO-25 surface for no bar clause — final boundary call stays with F).
- **2026-07-12 · [RS-7 = C5-α+γ] (Bora, per lean)** · Language architecture = config-routed ops per language (live shape) **validated and surfaced by a capability matrix** (lang × op → engine + model version, via GetStatus and echoed in responses); unsupported languages get the honest degrade floor (tokenize + fold + langid); consumers branch on the matrix. β language-plugin SPI **deferred until language #2 is real** (consistent with GI-7's R4 lean — the second language defines the interface); δ multilingual-NER (nametag3-multilingual) tracked as a model-artifact lever inside RS-5, not as architecture. · Rejected: β now (premature abstraction); δ as architecture (morphology stays per-language; cs parity needs the full stack).
- **2026-07-12 · [RS-8 — resolves Q-12, extends RS-3] (Bora + Claude)** · The `ttr-nlp` front is **fully engine-free**: contract + routing + langid only (no torch, no model files). **All** model-bearing engines run as backend services — MorphoDiTa, NameTag 3, **and Stanza + spaCy** (Stanza noted as heavy-but-not-en-only: cs DEP_PARSE routes to it, and the resolver's span proposal consumes the dep parse — it is on the Czech hot path). · Why: RS-3's coupling logic applied uniformly; the front image becomes a slim router; per-engine scaling/placement everywhere. · Rejected: Stanza-in-front interim (a second topology to migrate later for no gain).

## 8. Open questions (Q-n; RQ-1..5 inherited from resolver-rewrite.md §7)

- ~~**Q-1 (=RQ-1)** — live pipeline inventory~~ **Answered 2026-07-12** → `02-recon-live-reference.md` §E.1/§E.8. Headline: the live pipeline is LLM-in-the-loop on every path (spawns Q-6).
- ~~**Q-2 (=RQ-2)** — primitive topology~~ **Answered 2026-07-12** → recon §E.2: the resolver *consumes* NLP (HTTP), fuzzy (gRPC), metadata (gRPC), llm-gateway (HTTP); it embeds only orchestration/decision logic. R1's consume-vs-contain sub-question resolves factually to "consume".
- ~~**Q-3 (=RQ-3)** — vocabulary reality~~ **Answered 2026-07-12** → recon §E.3/§B.1: resolver registry = metadata `ListObjects(fuzzy_only)` + snapshot enrichment at startup; fuzzy loads member values by SQL against source DBs, hourly atomic-swap refresh; TTL caches only, no event invalidation; **nothing consumes snapshot archives yet** (R3-α has no live precedent).
- **Q-4 (=RQ-4)** — does `resolve` become a first-class MCP door? (Workstream F; must close before the debut surface is complete.)
- **Q-5 (=RQ-5)** — parity corpus mechanics: pilot conversation corpus usability (access/anonymization; RO-19 ask ③); eval seeds found in ai-platform (`agents/resolver/eval/`) may partially answer this.
- **Q-6** — the live resolver is a **Koog agent with LLM-assisted extraction** (prompts `value-extraction`/`joint-inference`, LlmGatewayClient): what exactly does GI-1 (P2) force the open rewrite to *split* between deterministic spine and agent-side proposal — and what does "parity" mean across that split?
- **Q-7** — geo grounding depends on **Nominatim** (external geocoder) + Postgres boundary store: what is the open, offline-capable story (GI-4: no hidden operate-tier dependency)?
- **Q-8** — member vocabulary loading runs SQL against source estates (fuzzy loader): where is the line between open one-shot/on-refresh loading and the commercial *continuous harvest* (GI-4)?
- **Q-9** — NameTag 3 / MorphoDiTa model files: distribution mechanics in the open offering (image-baked vs model-artifact volume vs init-download), versioning/pinning (determinism, GI-1), and size/licensing-technical constraints (FI-3/FI-4). **Sharpened by recon:** today's engines call UFAL's Lindat **online API** (rate-limited 5/min) — FI-3 is a build-a-delta, not a port; both tools ship Python bindings + downloadable model packs (recon §C.2/§C.3).

- **Q-10** *(now a planning input, not a design gate)* — backend spike: NameTag 3 server CPU memory/latency/cold-start/throughput under our load shape, **and protocol-parity verification** (Lindat API vs self-hosted `nametag3_server.py` / `morphodita_server`) so the RS-4 endpoint swap is proven, not assumed. Sizes the RS-3/8 backends. Runnable now.
- **Q-11** *(planning input)* — bulk lemmatization sizing: pilot member-vocabulary cardinalities + refresh cadence → the `nlp.v1` batch contract (RS-6); couples to B's vocabulary-source fork. Batch shape must hold at both front and backend hops.
- ~~**Q-12** — where does Stanza live~~ **Resolved 2026-07-12 by RS-8:** engine-free front; Stanza (and spaCy) are backends like everything else.

## 8a. Sweep register (S-n — micro-decisions to batch-ratify at the consolidation sweep)

- **S-1 (from C)** — model identity always explicit on the wire: config pins engine/model versions, responses echo them, no server-default model selection anywhere (kills the live MorphoDiTa empty-`model` bug class; GI-1).

## 9. Parking lot

- UFAL commercial/licensing terms (FI-4) — **now concretely shaped by RS-5:** the mechanics question is redistribution of CC BY-NC-SA model files inside published backend images (public GHCR vs restricted registry vs per-deployment build). Revisit when: the DFP/UFAL conversation happens (design side is closed).
- Embedding-assisted candidate tier (R2-β/γ tier 2) — revisit when: parity achieved and recall gaps are measured on the parity corpus.
- Language #2+ SPI hardening (R4-β) — revisit when: a second production language is real (the second language defines the interface).
- Semantic-layer/BI consumers of member search (PF B/R3 arc) — revisit at the 1.1.0 arcs.

## 10. Session index

| Date | Gear | What happened | Artifacts |
|---|---|---|---|
| 2026-07-12 | Framing + recon | Effort opened (RS-1/RS-2); FI-1..5, GI-1..7 recorded; workstreams A–F cut; hero scenario fixed; live-repo recon executed (ai-platform connected) — Q-1/2/3 answered, Q-6..9 sharpened; four headline findings (LLM-in-the-loop resolver · UFAL-online-API gap · grounding corpus converged DFP-side · no snapshot consumer yet) | this doc, `01-design-space-map.md`, `02-recon-live-reference.md` |
| 2026-07-12 | Divergence — C | C catalogued (C1 shape · C2 Czech engines · C3 model distribution · C4 API + threads T1 transport/T2 batch/T3 caching · C5 language/degrade), UFAL tooling facts verified (NameTag 3 = PyTorch server, MorphoDiTa = bindings; models CC BY-NC-SA); privacy-egress + empty-model findings recorded; C → 🟡; Q-10..12 + S-1 opened | [`05-C-nlp-options.md`](./05-C-nlp-options.md) |
| 2026-07-12 | Convergence — C (partial) | **RS-3 (C1-β thin front + backends) and RS-4 (C2-β for both tools, Lindat = labeled dev/eval tier)** ratified — Bora's fork, Claude concurring after verifying MorphoDiTa's in-tree `rest_server`; Q-10/Q-12 re-scoped; C3/C4/C5 remain open (Q-10 spike gates C3-adjacent sizing) | control room §7, `05-C-nlp-options.md` §2–§3 |
| 2026-07-12 | **Convergence — C (closed)** | **RS-5 (C3-α models baked per-backend) · RS-6 (C4-α + gRPC + batch) · RS-7 (C5-α+γ capability matrix, SPI deferred) · RS-8 (engine-free front — Stanza/spaCy backends too, resolving Q-12)**; C → 🟢; Q-10/Q-11 demoted to planning inputs; FI-4 parking item sharpened (image-redistribution mechanics) | control room §7, `05-C-nlp-options.md` |
