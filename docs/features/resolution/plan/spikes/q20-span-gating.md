# Q-20 spike — deterministic span-gating precision

> **RG-P0.S2.** The one empirical gate on the **E2-ε resolver core** (the split):
> does deterministic *all-spans × batch-fuzzy* gating reach the live LLM
> value-extraction's precision? Spike = measurement + go/no-go, not shipped code.
> Harness: [`ai-platform/infra/nlp/spikes/q20/`](../../../../../../../ai-platform/infra/nlp/spikes/q20) (reproducible). Baseline: **gold-relative, no live LLM** (decision — the pilot evals are skipped-by-default with no recorded numbers; the LLM value-extraction's documented contract is recall-first with a `recall ≥ 0.8` floor, `ValueExtractionEvalTest`).
>
> **Verdict (one line): E2-ε core = GO-WITH-FALLBACK.** The deterministic *matcher*
> reaches LLM precision (P=1.0 on oracle spans); naive all-spans gating
> over-generates (P=0.5, 33 spurious binds); a **fully-deterministic span-relevance
> filter** (dep-parse anchoring to entity-type vocabulary) recovers precision to
> **P=1.0 and eliminates over-generation (33→0), zero LLM.** Build RG-P5's core on
> anchored span proposal, not all-spans.

## 1. Setup

- **Corpora** (copied into the harness; SHA-256 recorded in `corpus/PROVENANCE.txt`, ai-platform corpus dir @ `afd4cae`):
  `seed.jsonl` (50 Czech questions), `ucetnictvi_entities_only.jsonl` (12 binding cases, incl. 5 `expected_awaiting`), and `value-extraction.jsonl` (6 value→column-ref cases, the ValueExtractionEvalTest corpus).
- **Parse:** UFAL **UDPipe 1** (`czech-pdt-ud-2.5-191206`, 56 MB, in-process via `ufal.udpipe`) — noun-head subtrees + content n-gram windows; **lemma axis ON** (CoNLL-U lemmas). Chosen over Stanza to stay UFAL-native; the binding was already present in the S1 NameTag image. (CC BY-NC-SA — spike-only; production licensing is a separate call.)
- **Universal extraction:** the S1 self-hosted **NameTag 3** removes `g*`/`p*`/`t*`/`n*` (LOCATION/PERSON/DATE/NUMBER) spans before domain gating — mirroring the core's `extractUniversal → proposeDomainSpans`. Institutions (`i*`) stay domain-eligible (a domain value like `DF ADNAK` is tagged `io`, so NER can't be the domain filter — the fuzzy gate is).
- **Matcher:** faithful Python port of `ttr-fuzzy` **TATRMAN** (`TokenBasedMatcher`): fold (S-2 spec) → dual surface/lemma axis (keep max) → per-query-token nearest-candidate Levenshtein, `matchQuality=1−dist/maxLen` weighted by `idf(matched)` (`ln((N+1)/(df+1))+1`) → `× orderBonus (1.05^pairs, cap 1.5)`.
- **Gating thresholds:** the live ENTITIES_ONLY values (`ResolverGraph.kt:38-48`): candidate `≥ 0.5`, ambiguity gap `0.05`, exact `0.9999`. Two determinacy levels: **entity-determinate** (contenders share an entity type → column refs) vs **instance-determinate** (contenders share one value → a `resolved_id`; else → AwaitingClarification).
- **Vocabulary:** synthesized, source-tagged, DFP-light (labels only, no live DB): the 3 df-test sibling entity types (`QSTRED_DF`, `QXXUKAZMU`, `QTYPDOK`, each code+name column) populated with the real corpus values (`DF ADNAK`, `MAJETEK`, `FAP`, `VY KANCELAR`, …) + plausible distractors (42 candidates) so matching faces genuine ambiguity.

## 2. Results

Three configurations, same corpora/vocab/thresholds:

| Config | value-extraction P / R / F1 | ucetnictvi P / R | awaiting withheld | seed spurious binds |
|---|---|---|---|---|
| **A. Matcher, oracle spans** (gold span in) | **1.00 / 0.83 / 0.91** | — | — | — |
| **B. All-spans gating** (naive core) | 0.50 / 0.67 / 0.57 | 0.21 / 0.60 | 0 / 5 | **33** (26 of 50 Qs) |
| **C. Anchored gating** (deterministic fallback) | **1.00 / 0.83 / 0.91** | 0.50 / 0.80 | 1 / 5 | **0** (0 of 50 Qs) |

**Reading it — the whole Q-20 answer is here:**
- **(A) Matching is not the problem.** Given the correct span, the deterministic matcher binds it with **precision 1.0** — it reaches the LLM's precision. Recall 0.83 = 2 misses on inflected/edge values (tunable). Sibling-column expansion (a matched value → **both** KOD+NAZEV refs) works; the code-vs-name probes all bind to the right entity (`FAP`→QTYPDOK code, `DF ADNAK`→QSTRED_DF name, `MAJETEK`/`VY KANCELAR`→QXXUKAZMU).
- **(B) All-spans span proposal is the entire gap.** Feeding every noun-anchored span to the matcher collapses precision (1.0 → 0.5) and produces **33 spurious domain binds** on general questions — junk spans (`záznamy`, `roce`, `vývoj nákladů`) find *some* weak ≥0.5 match. This is exactly the over-generation the spike was built to find: **the LLM value-extraction's real contribution is span RELEVANCE (which spans to bind), not matching.**
- **(C) Span relevance is deterministically recoverable.** Gating **only** spans that are dep-parse dependents of an entity-type anchor word (`středisko`→QSTRED_DF, `ukazatel`→QXXUKAZMU, `doklad`→QTYPDOK — the lexicon's `entityAliases`), matched against that entity only, **restores value-extraction precision to 1.0** (= the oracle) and **eliminates seed over-generation entirely (33 → 0)** — with **zero LLM**. ucetnictvi jumps P 0.21→0.50, R 0.60→0.80.

## 3. The two known-hard behaviors (the LLM's tricks)

- **Sibling-column** (same-table name-vs-code): **deterministically solved.** A value match expands to both the entity's KOD and NAZEV refs (the catalog knows the sibling pair). This is a table lookup, not an inference — no LLM edge.
- **Code-vs-name disambiguation:** **solved in isolation** — an exact code (`FAP`) and an exact/near name (`DF ADNAK`) both bind to the right entity, and short codes vs descriptive names are separated by exact-match dominance. The fuzzy score + the source-tagged code/name candidates carry the distinction.

## 4. Where deterministic gating still trails the LLM (the residual)

The remaining gap is **instance-level ambiguity**, not entity binding:
- `ucetnictvi` awaiting still only 1/5 withheld under the fallback. Cases like *"středisko **Praha**"* (Praha is not a středisko but partial-matches the `SM PRAHA` distractor) and bare *"středisko **DF**"* / *"ukazatel **VY**"* (ambiguous among instances) still produce a confident-enough bind because the small-vocab TATRMAN score gives a single distinctive token a high partial score.
- This is precisely the **refuse-over-guess / AwaitingClarification** territory the design already owns (RS-26). The deterministic answer is to let the **instance-ambiguity gate** fire (contenders span multiple *values* within the 0.05 gap → clarify) rather than commit — a threshold/So-what the core tunes, not a capability it lacks.

**Caveat (rule 7):** the matcher is a faithful-but-approximate Python port of `ttr-fuzzy` and the vocabulary is a 42-item synthesis (not the live estate); the *shape* of the result (matcher OK, span-relevance is the gap, anchoring recovers it) is robust, but the exact P/R should be re-confirmed against the real `ttr-fuzzy` service + a full snapshot vocabulary in RG-P5.

## 5. Verdict + fallback shape (feeds RG-P5)

**E2-ε core = GO-WITH-FALLBACK** — and the fallback is *decided and validated*, so RG-P5 starts from a position, not a question:

1. **Do NOT build `proposeDomainSpans` as naive all-spans × fuzzy** — it over-generates (P 0.5, 33 spurious). B is the design to avoid.
2. **Build it as anchored span proposal:** propose a domain span only where the dep parse ties a content subtree to a **declared entity-type anchor** (the lexicon `term`/`entityAliases` for `er`/`db`/`md` kinds), and gate it **against that entity's vocabulary only**. This is the deterministic equivalent of the LLM's relevance filter — it recovered P=1.0 and killed over-generation, **with no LLM in the service** (the anchors come from the declared lexicon RG-P4 lands; stub until then).
3. **Keep the AwaitingClarification path for instance ambiguity** (contenders spanning multiple values within the gap) — refuse-over-guess, already in the contract (§4/§5). Tune the instance-ambiguity gate; do not chase confident instance binds on genuinely ambiguous short spans.
4. **Sibling-column + code-vs-name need no LLM** — sibling expansion is a catalog lookup; code/name separation falls out of source-tagged candidates + exact-match dominance.

Net: the split is safe. The service stays LLM-free and reaches the LLM's precision **when domain spans are proposed by declared-vocabulary anchoring rather than blindly**. The agent-side LLM ladder remains the fallback for the residual (unanchored/novel phrasings), but the *core* does not need it for the pilot's binding shapes.
