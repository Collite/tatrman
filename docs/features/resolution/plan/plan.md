# Resolution & Grounding — Phased Implementation Plan

> **Status:** consolidated 2026-07-12, per the planning skill. Companions: [`architecture.md`](./architecture.md), [`contracts.md`](./contracts.md). Design ground truth: [`../design/design.md`](../design/design.md) + the decision log ([`../design/00-control-room.md`](../design/00-control-room.md) §7, RS-1..32).
>
> Per the planning skill: this document = overall plan + phases with deliverables, pre-flight conditions, and definitions of DONE. **Per-phase task lists (6–8 tasks each, TDD-ordered, with an overall task-management document) are generated phase by phase.** This session generated the tracker + **RG-P0** (spikes + scaffold) and **RG-P1** (ttr-nlp self-hosting); RG-P2..P6 are planned here and task-listed as they are reached (RG-P0's spike outcomes reshape RG-P2/P5).

---

## 0. Overall plan

Seven phases. The spine follows the design's load-bearing order — **A (lexicon) and C (nlp) are the feet**, then **B/D** stand on them, **E** composes B/C/D, **F** exposes E — with two **phase-0 spikes** run first because their outcomes gate later design assumptions (Q-10 the self-hosting delta; Q-20 the resolver split's one empirical gate).

```
RG-P0 spikes+scaffold ─► RG-P1 ttr-nlp ─► RG-P2 ttr-fuzzy ─► RG-P3 grounding ─► RG-P5 resolver ─► RG-P6 doors+conformance
   (Q-10, Q-20,            (self-hosted    (the vocab        (kernel +          (deterministic    (resolve door,
    fold lib, proto        Czech stack,     matcher,          geo posture)       core, the split)   grounding tools,
    renames)               capability mtx)  lemma axis ON)                                          three-tier parity)

RG-P4 lexicon grammar (A) ──────── parallel track through grammar-master, gated on grammar 4.2 merge ────────►
```

**Why this order.** C is first (Bora's phase-1 choice): self-hosting the Czech stack turns the whole determinism claim from aspiration to fact and unblocks fuzzy's lemma axis. B needs C's in-cluster lemmatization + batch call. D is largely inherited (test-backed DFP corpus) and independent of B/C, so it can overlap. E needs B's batch-match contract and is gated on Q-20. F needs E's core real. A (the lexicon grammar) is a grammar-master arc that runs as a parallel track — it gates on 4.2 merging, not on the service phases, and its *output* (declared vocabulary in snapshots) is what B/E consume, so its data path can be stubbed until the grammar lands.

**Global pre-flight (gates the whole plan):**

1. **SV-P2 closing** (the trigger for this planning). `tatrman-server` exists with the flat GHCR publish path (SV-P0, RO-28); `ttr-fuzzy` already lives at `tatrman-server/services/ttr-fuzzy`.
2. **RO-13 snapshot-archive schema review** — now has *two* consumers (fuzzy vocabulary + resolver registry, one channel). The review pins the archive schema RG-P2/RG-P5 read; schedule it before RG-P2's snapshot path (live-metadata startup reads are the acceptable step-one until it lands).
3. **ai-platform live references connected** for the spikes (RG-P0): the `infra/nlp` service + UFAL adapters (Q-10) and the resolver eval corpora `seed.jsonl` + `ucetnictvi_entities_only.jsonl` (Q-20). These live in the `ai-platform` repo; the spikes run where it is available and copy corpora into the tatrman spike harness.
4. **Grammar 4.2 (semantics-block)** merged before RG-P4 opens (it is in flight); the lexicon 4.3 arc sequences after it.

**Global definition of DONE (the SV-P3 parity bar):** the hero sentence — „Kolik jsme utržili za Octavie v pražských pobočkách za poslední fiskální čtvrtletí?" — resolves end-to-end through the deterministic core (parse self-hosted + offline; universal spans grounded deterministically; domain spans gated by batch fuzzy over source-tagged vocabulary) with the intent bound agent-side; the **in-repo gating conformance instrument** (ENTITIES_ONLY corpus + grounding 109+21 + fuzzy match fixtures) passes with no DFP dependency; every binding carries S-1/S-4 provenance; the `resolve` door promises and asserts determinism + refusal-over-guess; and no path below the door line calls an LLM.

---

## RG-P0 · Spikes + scaffold

**Pre-flight:** global pre-flight 1 + 3.

**Deliverables:**
- **Q-10 spike (self-hosting sizing + protocol parity).** Stand up `nametag3_server.py` (model `nametag3-czech-cnec2.0-240830`) and MorphoDiTa `src/rest_server` (model `czech-morfflex2.0+pdtc`) as throwaway containers; measure CPU memory / latency / cold-start / throughput under the pilot load shape (per-question 7-op parse **and** the bulk-lemmatize case); **verify protocol parity** Lindat API ↔ self-hosted (same input → same tokens/lemmas/entities). Output: a sizing report + a parity assertion table → sizes the RG-P1 backends and proves the RS-4 endpoint swap.
- **Q-20 spike (deterministic span-gating precision).** Harness: all candidate spans (noun heads + n-gram windows) × batch fuzzy over the seed vocabulary, score-threshold gating; compare precision/recall against the live LLM value-extraction baseline on `seed.jsonl` (50 cases) + `ucetnictvi_entities_only.jsonl` (12 binding cases), including the sibling-column and code-vs-name behaviors. Output: precision/recall numbers + a **go/no-go on the E2-ε core** (if gating underperforms, RG-P5's core design gains a documented fallback before it is built).
- **Scaffold.** The shared **normalization lib** (S-2: `fold()` + golden test-vectors) placed and published-local; the **proto rename scaffolding** (`cz.dfpartner.* → org.tatrman.*` package moves for nlp/fuzzy/grounding/resolver, build wiring, no behavior change); the `RG-*` diagnostics registry (contracts §8) with fixture stubs.

**DONE when:** both spike reports exist with numbers and an explicit go/no-go; the fold lib builds/tests/publishes-local and its golden vectors pass; the renamed protos compile across all consumers; every `RG-*` diagnostic renders from a fixture. *No production behavior ships in P0 — it de-risks P1/P2/P5.*

## RG-P1 · `ttr-nlp` self-hosting (workstream C)

**Pre-flight:** RG-P0 DONE (Q-10 sizing gates backend resource requests; the fold lib exists).

**Deliverables:** the `org.tatrman.nlp.v1` gRPC contract (Analyze + BatchLemmatize + GetStatus, contracts §1) with REST mirror for dev/health; the **engine-free front** (routing table + langid; no torch, no models); four **backend services** — MorphoDiTa (`rest_server`), NameTag 3 (`nametag3_server.py`), Stanza, spaCy — each an image with its own model baked + digest-pinned (S-1), CPU-only torch; engine adapters repointed from Lindat to in-cluster URLs (Lindat kept as a labeled `REMOTE_UNPINNED` dev/eval tier, `RG-NLP-002`); the **capability matrix** (`GetStatus` + `used[]` echoed on every response); the **batch lemmatize** path (holds at front + backend hops); the degrade floor (`RG-NLP-010`); charts/values for the front + four backends.

**DONE when:** a `cs` 7-op parse of the hero sentence runs fully self-hosted and offline (no Lindat egress), lemmatizing `Octavie→octavia`, `pražských→pražský`, `utržili→utržit`; `GetStatus` reports the matrix with `SELF_HOSTED_PINNED` tiers and real model versions; every `Analyze`/`BatchLemmatize` response echoes `used[]` (S-1 — no empty model, `RG-NLP-003` on violation); the bulk-lemmatize case sustains the Q-11 vocabulary cardinality within the Q-10 sizing envelope; protocol-parity fixtures (from Q-10) pass against the self-hosted backends; an unsupported language returns the degrade floor, labeled.

## RG-P2 · `ttr-fuzzy` — the vocabulary matcher (workstream B)

**Pre-flight:** RG-P1 DONE (lemma axis needs in-cluster lemmatization + the batch call); RO-13 snapshot schema available (or live-metadata step-one, pre-flight 2).

**Deliverables:** turn ON the lemma axis (was disabled at pilot for the Lindat rate limit); **source-tagged categories** (MEMBER vs VOCABULARY, contracts §2) so member values and declared vocabulary match through one engine; the **snapshot read** of declared vocabulary (lexicon terms + valueLabels — fuzzy becomes R3-α's first live consumer; stub the declared-vocab stream until RG-P4 lands the grammar, per pre-flight 4); alias-table ingestion (RS-12-γ) in the loader; `BatchMatch` (contracts §2); `vocabulary_version` + provenance on results (S-1/S-4); refresh discipline (interval + atomic swap for member data, snapshot-hash reload for declared config, staleness echoed); admin-gated `/refresh` (S-3, `RG-FUZ`-adjacent); the PK-skip loader report (B-T4); the `search{fuzzy}` = visible-by-declaration docs (RS-17). The built-vocabulary-artifact indexer (B1-β) is the named target — **not** in this phase unless scale pressure lands (fold onto the `LoaderSource` SPI later).

**DONE when:** the hero's `Octavie` and `pobočkách` both gate correctly through `BatchMatch` (member product value + branch entity lexicon term, source-tagged); the lemma axis bridges inflection against the Q-17 match-quality referee corpus (diacritics/inflection/multi-word-order/typos); explicit-unknown category returns EMPTY (`RG-FUZ-002` leak guard); `vocabulary_version` reflects the snapshot hash; a `/refresh` without admin identity is refused; the loader report lists PK-skipped declared columns.

## RG-P3 · Grounding extraction + kernel (workstream D)

**Pre-flight:** RG-P0 DONE (fold lib for the kernel + S-2); can overlap RG-P1/P2 (independent of B/C).

**Deliverables:** extract chrono/geo/money + `ttr-grounding-mcp` into `tatrman-server`, J-v2-renamed (`org.tatrman.grounding.v1`), inheriting the `[~]` DFP stages as-is with the fix-at-rename list (proto `ResponseMessage` import wart, S-2 fold into the kernel, S-3 on operator endpoints); the shared **`ttr-grounding-core` kernel** (RecipeBuilder/PlanExpr/SqlRenderer + fold) with the `sql_preview`-derived invariant enforced once; the **geo posture** (RS-19: capability-honest Nominatim seam + boundary cache on with an install-time priming step + documented dark floor; `RG-GND-001`); the **kind-named tools** `grounding.{time,geo,money}:v1` (RS-28) with capability/GetStatus surfacing; server-owned `GroundingContext` (D-T1); fiscal-quarter coverage verified/extended (Q-18); Nominatim default-endpoint policy documented (Q-19: no default + self-host guidance). The RÚIAN gazetteer artifact (D2-β) is the named CZ arc — **not** in this phase.

**DONE when:** the hero's „poslední fiskální čtvrtletí" grounds to a fiscal-quarter interval via the model's period table (JoinRecipe) relative to `reference_datetime` (Q-18 confirmed or the small extension shipped); „pražských pobočkách" grounds via geo containment when the capability is on and degrades honestly when dark (fixtures conditional); the three kind-named tools resolve through the generic proto; `sql_preview` derives from the `plan.v1` tree in the kernel (no per-service duplication); the 109+21 grounding eval corpus runs green as the conformance extended-tier feed.

## RG-P4 · TTR-M lexicon surface (workstream A — parallel grammar track)

**Pre-flight:** grammar 4.2 (semantics-block) merged (global pre-flight 4); a grammar-master change window (spec-version bump, per S6/grammar-master); runs in parallel with RG-P1..P3.

**Deliverables:** the **lexicon model** grammar (new model code + `term`/`pattern`/`example` def kinds targeting er/db/md, contracts §7) + inline `lexicon{}` sugar with desugar rules (RS-9 T1 — copy binding's discipline); the entry schema (RS-9 T2 — start minimal: `for`, `forms`/`match`/`text`; grow additively); `search{}` slimming to `searchable`/`fuzzy` + the legacy-disposition migration (RS-32: aliases/keywords→term, patterns→pattern, examples→example, descriptions→description, with the `descriptions` consumer check); `valueLabels` per-value alias widening (A4-β); the `semantics{kind: alias_table}` closed-vocab entry (RS-12-γ; no grammar bump); canonical-form-only consumer propagation (RS-9 T3: snapshot, EntityTypeSpec, meta doors); the vocabulary-coverage lint question (Q-15) resolved as a model-lint decision. The in-flight search-block feature proceeds independently — its flag relocation is the compatible end state.

**DONE when:** the hero's measure vocabulary (`tržba`/`obrat`/`utržit` → `md.measure.net`) is declarable both canonically and via inline sugar, desugaring identically; the legacy `search{}` sub-properties parse into lexicon forms (migration tooling + deprecation warnings); a lexicon model round-trips through the snapshot in canonical form; grammar conformance is green across the generated parsers (TS/Kotlin/Python).

## RG-P5 · `ttr-resolver` — the deterministic core / the split (workstream E)

**Pre-flight:** RG-P2 DONE (batch-match contract + source-tagged vocabulary); **Q-20 go** (RG-P0) — if Q-20 was no-go, the documented fallback is designed first; RG-P4's declared vocabulary reachable (or stubbed via snapshot).

**Deliverables:** reshape `org.tatrman.resolver.v1` (contracts §4: remove `function_specs`/joint-inference; add `EntityBinding` provenance, `degraded`, capability echo, parse passthrough); the **deterministic core** — parse → `extractUniversal` → `proposeDomainSpans` (dep-parse noun heads + n-gram windows) → **`gateSpans`** (all candidate spans × `BatchMatch` over source-tagged vocabulary; thresholds + entity-identity) → bindings | `AwaitingClarification`; **zero LLM in the service**; snapshot-fed registry (shares B's hash channel; caller-supplied override stays); capability-matrix degrade (RS-25, `RG-RES-001`); HMAC resume tokens + option pins (contracts §5, `RG-RES-002`); drop the dead `resolutionCache` (E-T3). The kantheon-side Resolving Agent re-plumb (Themis; escalation ladder) is **kantheon planning (Q-21)** — this phase pins the *contract* consequences (proto slimming) and the reuse relationship.

**DONE when:** the hero's `Octavie`/`pobočkách` bind through the deterministic core with zero LLM, provenance-carrying; the ENTITIES_ONLY corpus passes at the service level (the gating parity instrument); the span-gating precision matches Q-20's spike numbers on the seed corpus; a clarification round-trips via a signed resume token and a tampered/unsigned token is refused (`RG-RES-002`); an unsupported-language resolve returns the fold+fuzzy floor labeled `degraded`; the reshaped proto drops NORMAL-mode machinery with no consumer break.

## RG-P6 · Exposure: doors + conformance (workstream F)

**Pre-flight:** RG-P5 DONE; RG-P3 DONE (grounding tools); the mcp-surface §2.2 rows scheduled.

**Deliverables:** the **`resolve` door** (`resolve.*:v1`, contracts §4/§5) exposing the deterministic core only, with H-2 identity/OBO, resume-token semantics, and a **refusal-over-guess** conformance assertion; the kind-named `grounding.{time,geo,money}:v1` tools finalized on the surface (mcp-surface §2.2 rows); the **canonical cascade documented as conformance fixtures** (the `calls:` assertion schema — the reference orchestration, agent-side, is kantheon code not contract); the **three-tier conformance instrument** (RS-30): gating service-level (in-repo, no DFP), gating E2E core (hand-authored, joins the RO-25 core tier at SV-P4), non-gating pilot-derived extended (RO-19 ask ③); parse passthrough + capability echo at the door (F-T3); the ecosystem register updates (next-steps item 4, mcp-surface §2.2, architecture §3 roster, resolver-rewrite.md superseded).

**DONE when:** the **global DONE / SV-P3 parity bar** holds end-to-end; the resolve door promises determinism and the suite asserts refusal-over-guess + clarification round-trips; grounding tools resolve with capability-conditional geo fixtures; the in-repo gating tier is green with no DFP dependency; the registers are updated and `resolver-rewrite.md` is marked superseded by this plan.

---

## Risks & watch items

- **UFAL model redistribution (RG-P1):** CC BY-NC-SA models baked into published images = the FI-4 legal item (public GHCR vs restricted registry). Design side closed; the mechanics gate *publishing* the backend images, not building/running them. Watch: schedule the legal call before the images go public.
- **Q-20 outcome (gates RG-P5):** if deterministic span-gating underperforms the LLM value-extraction on sibling-column/code-vs-name, the core needs a documented deterministic equivalent (or a narrow agent-side pre-filter that keeps the *service* LLM-free). The spike runs first precisely so this surfaces in P0, not P5.
- **Snapshot schema (RG-P2/P5):** the RO-13 review pins the one channel both consumers read; a slip means live-metadata step-one carries longer. Watch the sequencing luck (the review already had fuzzy as a consumer; the resolver is now the second).
- **Grammar track (RG-P4):** the lexicon 4.3 arc rides grammar-master and gates on 4.2 — schedule the window early; B/E stub the declared-vocab stream until it lands so they are not blocked.
- **NameTag 3 cold start (RG-P1):** the live system bumped the resolver graph timeout to 180 s for cold Stanza/UFAL; the Q-10 spike must characterize self-hosted cold start so readiness/latency budgets are set, not discovered.
- **Batch shape at two hops (RG-P1):** the bulk-lemmatize call traverses front → MorphoDiTa backend; the Q-11 sizing must hold at *both* hops, not just the front.

## Task lists

Generated 2026-07-12 per the planning skill → [`tasks/00-task-management.md`](./tasks/00-task-management.md) (overall tracker: rules, pre-flight gate, phase/stage checkbox table, phase-exit reviews, library reference card) + this session's phase files [`tasks/tasks-p0-spikes-scaffold.md`](./tasks/tasks-p0-spikes-scaffold.md) and [`tasks/tasks-p1-nlp.md`](./tasks/tasks-p1-nlp.md). RG-P2..P6 task files are generated as each phase is reached (RG-P0 spike outcomes reshape RG-P2/P5).
