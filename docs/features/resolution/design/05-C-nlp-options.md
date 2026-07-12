# C · NLP Service (`ttr-nlp`) — Options Catalogue

> **Status:** options captured 2026-07-12. **No decisions in this document — leans are leans.** Fact base = `02-recon-live-reference.md` §C (the live `infra/nlp`) + UFAL sources checked 2026-07-12 (ufal.mff.cuni.cz/nametag/3, /morphodita, github.com/ufal/nametag3).
> Scope: the engine/language architecture, the Czech self-hosted stack (FI-3), model packaging (Q-9), the `nlp.v1` API for heavy multi-consumer use (FI-2), and language routing/degrade. Consumers today: resolver (7-op parse per question), fuzzy-matcher (lemmatize at vocabulary refresh **and** per query), future: TTR-P/`import-schema` teach-in arcs (parked), conformance suite fixtures.

---

## 1. Facts (fixed ground, not options)

- **Live service** (recon §C): FastAPI Python 3.13, engine SPI, five engines, per-op-per-language config routing, ops enum {TOKENIZE, SENTENCE_SPLIT, LEMMATIZE, POS_TAG, DEP_PARSE, NER, DETECT_LANGUAGE}, NORMAL/COMPARE modes, port 7117; `cz.dfpartner.nlp.v1` proto exists (REST mirrors it); rename target `org.tatrman.nlp.v1`, "Python service stays Python" (server contracts).
- **The Czech gap:** morphodita + nametag engines call **UFAL Lindat online** (5/min rate limit, 30 s timeout). Two consequences worth naming: (a) the pilot's *question text leaves the premises* on every parse — a privacy/egress fact, not just a rate-limit nuisance; (b) MorphoDiTa requests send an **empty `model` param** (server picks its current default) — the live path is not even version-pinned. FI-3 fixes both.
- **UFAL tooling (checked 2026-07-12):**
  - **MorphoDiTa** — C++ core; **bindings on PyPI (`ufal.morphodita`) + Java + C#**; CLI; 10–200K words/s CPU; models = `czech-morfflex2.0+pdtc` line (LINDAT-hosted). Code MPL-2.0, **models CC BY-NC-SA** (legal parked, FI-4).
  - **NameTag 3** — **Python + PyTorch** (fine-tuned transformer LM; flat softmax head / nested seq2seq head); **no C++/JVM bindings**; ships as a repo with CLI + **`nametag3_server.py` REST server** (multi-model, e.g. `nametag3-czech-cnec2.0-240830`, `nametag3-multilingual-250203`); GPU optional (CUDA ≥ 7.0) — CPU works, slower. Code MPL-2.0, models CC BY-NC-SA. This is a **different beast from NameTag 2** (which had C++ bindings): self-hosting NameTag 3 means hosting a PyTorch process.
- **Packaging precedent:** the live Dockerfile already bakes Stanza cs+en and spaCy into the image (`/opt/nlp-models`, CPU-only torch) — so "models in the offering" has one working pattern already.
- **Consumer load shape:** resolver = one 7-op parse per question (cached 24 h by the consumer); fuzzy = *bulk* lemmatization of entire member vocabularies at every refresh (hourly; full column value sets) + per-query lemmas. The bulk case is the throughput driver, not the per-question case.
- **Known scars:** Stanza cold start forced the resolver's graph timeout to 180 s; cs NER has **no fallback** (Stanza cs bundle lacks NER); CNEC label mapping downstream is leading-letter heuristic (recon §E.5).

## 2. C1 · Service shape — where do NLP primitives run?

**C1-α — one `ttr-nlp` service (the live shape, hardened).** All engines in one deployment; consumers speak `nlp.v1`.
Buys: exists; one endpoint, one readiness story, one place to pin models; FI-2's "many consumers" gets one contract.
Costs: mixed resource footprints in one pod (a PyTorch transformer beside a 10 MB C++ tagger) — the heaviest engine dictates the pod; scaling is all-or-nothing; one bad engine's cold start gates the whole service's readiness.

**C1-β — thin front + heavy backends.** `ttr-nlp` keeps the contract and the routing; heavyweight engines (NameTag 3, potentially GPU-placed) run as their own in-cluster deployments the front proxies to (upstream `nametag3_server.py` is exactly this shape).
Buys: independent scaling/placement (CPU pool vs optional GPU node); cold-start isolation; morphology stays fast-in-front; the front's contract never changes as backends move.
Costs: more moving parts in the chart; two hops for NER; per-backend health/versioning discipline.
Prior art: the live system *already is* β by accident — nametag/morphodita are remote backends (just pointed at UFAL instead of in-cluster).

**C1-γ — in-process libraries at each consumer (no service).**
Buys: zero hops; MorphoDiTa has Java bindings (fuzzy could lemmatize in-process).
Costs: NameTag 3 is Python-only — the Kotlin resolver/fuzzy can't embed it; N model copies + N pinning disciplines; kills FI-2's one-contract story. Maps why the service exists.

**C1-δ — fold NLP into the resolver (the weird one).** The main consumer absorbs the primitives.
Buys: one fewer service on the resolve path.
Costs: contradicts FI-2 outright (fuzzy + future consumers re-orphaned); Python-in-Kotlin again; conflates a primitive provider with an opinionated pipeline (same argument that killed R1-β from the other side).

*Lean: α now, with β's seam named from day one (engines are already HTTP-client-shaped, so promoting NameTag 3 to an in-cluster backend later is config, not re-architecture). γ/δ map the space.*

## 3. C2 · Czech engine integration — how do NameTag 3 + MorphoDiTa actually run?

**C2-α — in-process in `ttr-nlp`.** MorphoDiTa via `ufal.morphodita` bindings; NameTag 3 vendored/imported as a library (it is a repo, not a PyPI package) with PyTorch CPU inside the service process.
Buys: fewest containers; one process to pin and observe; morphodita in-process is trivially right (C++ ext, microseconds per word).
Costs: NameTag 3 in-process means the service *is* a PyTorch app (memory, torch version coupling with Stanza's torch, cold start inside the main pod); vendoring an unpackaged upstream repo = maintenance surface.

**C2-β — upstream servers as in-cluster backends.** Run `nametag3_server.py` (and, if wanted, a MorphoDiTa server) as their own containers; the existing HTTP-client engines just repoint from `lindat.mff.cuni.cz` to in-cluster URLs.
Buys: **smallest possible code delta** — the engines already speak this protocol; upstream maintains the server; NameTag's footprint isolated (couples to C1-β); rate limit and egress gone.
Costs: extra image(s) to build/pin; the MorphoDiTa half is overkill as a server (bindings are the natural fit) — β realistically applies to NameTag 3 only.

**C2-γ — keep Lindat online (status quo — the weird one).**
Buys: zero work; always-current models.
Costs: question text leaves the deployment (egress/privacy — untenable for the acceptance bar's "stranger" enterprise); 5/min rate limit vs the fuzzy bulk case (already impossible today — which explains `nlp.enabled=false` in fuzzy); no version pinning (empty-model bug); LINDAT availability = resolution availability. Recorded to sharpen *why* FI-3 exists.

**C2-δ — replace the UFAL stack.** Train/adopt an alternative (Stanza-cs NER fine-tuned on CNEC, spaCy cs, or a small local transformer).
Buys: could unify on one engine family; potentially friendlier model licensing.
Costs: the **parity bar is defined against NameTag/CNEC behavior** (GI-3) — replacing the engine re-opens parity; CNEC-quality Czech NER is UFAL's home turf (86.4 F1 nested); training effort is real scope. Park as the escape hatch if UFAL terms fail (FI-4's revisit).

*Lean: split by tool — MorphoDiTa **α** (in-process bindings, pin `czech-morfflex2.0+pdtc` explicitly, killing the empty-model bug), NameTag 3 **β** (upstream server container, engine repointed). One spike needed before converging: NameTag 3 CPU memory/latency/cold-start under our load shape (→ Q-10).*

**Hero check (C2, either α or β):** „Kolik jsme utržili za **Octavie** v **pražských** pobočkách…" — MorphoDiTa lemmatizes `Octavie→octavia`-class inflections and `pražských→pražský`; NameTag/CNEC tags `pražských pobočkách` G-prefixed (→ LOCATION) and `Octavie` as P/M-class depending on context (→ the MISC trap: the CNEC leading-letter map sends product names to MISC — the *resolver's* domain-span path must catch what NER doesn't; cross-link E).

## 4. C3 · Model packaging & distribution (Q-9)

**C3-α — baked into the image** (the Stanza precedent, extended).
Buys: offline by construction; one artifact to pin (image digest); zero new machinery; "stranger installs chart, it works".
Costs: image size balloons (torch + Stanza cs/en + NameTag czech (+multilingual?) → multi-GB); every model bump = image rebuild; **publicly redistributing CC BY-NC-SA models inside a GHCR image is a licensing act** — mechanics land on FI-4's parked legal item.

**C3-β — models as versioned artifacts, mounted at deploy.** OCI/registry model artifacts (or chart-referenced archives) pulled by an init-container into a volume; digest-pinned in values; images stay model-free.
Buys: slim images; model updates decoupled from code releases; per-deployment model selection (a German estate pulls different bundles); air-gap = pre-seed the registry; redistribution is a *separate, access-controllable* artifact rather than baked into the public image.
Costs: a new convention to design (model-artifact naming/versioning/digests) and one more thing the chart must orchestrate; two version axes (service, models) to keep honest — mitigated by pinning both in values.

**C3-γ — download-at-deploy from LINDAT, checksum-pinned.**
Buys: **we never redistribute** — each deployment fetches from UFAL under its own license acceptance (the legally cleanest mechanics); always the canonical source.
Costs: deploy-time internet dependency (breaks air-gapped installs and makes the "stranger" flow fragile); LINDAT availability on the install path; checksum pinning mitigates but doesn't remove drift risk (upstream can retract versions).

**C3-δ — operator-supplied models (the weird one).** Documented "bring your own models" volume; the chart mounts what the operator provides.
Buys: fully air-gapped; zero redistribution questions; maximal enterprise control.
Costs: worst onboarding — the acceptance bar's stranger now has a manual model-hunting step; support surface ("which model did you actually mount?").

*Lean: β as the target (with δ documented as its air-gap degenerate case — an operator-seeded artifact store is the same mount), α acceptable as the interim that matches the existing Stanza pattern while the artifact convention is designed, γ kept as a legal fallback if redistribution terms block β. Whatever wins: **model identity pinned explicitly in config and echoed in provenance/GetStatus** — never a server-side default (GI-1). → S-1.*

## 5. C4 · API surface (`org.tatrman.nlp.v1`)

**C4-α — the ops-bitmap `Analyze` (live shape, formalized).** One RPC: text + language? + ops set + mode; response = tokens/sentences/entities (+ per-engine map in COMPARE).
Buys: consumers exist against it; one parse serves many ops cheaply (the resolver's 7-op call is *one* engine pass per engine); simple caching key.
Costs: coarse-grained SLOs; COMPARE is an eval tool living in a production contract; every consumer re-declares its op list.

**C4-β — task-shaped RPCs** (`Lemmatize`, `Recognize`, `Parse`, `DetectLanguage`).
Buys: per-task contracts, quotas, metrics; smaller payloads.
Costs: churn for the two live consumers; cross-op efficiency lost or re-hidden behind an internal cache (a lemmatize+POS+NER question costs three passes or a cache layer α gives for free).

**C4-γ — α + named op-profiles.** Profiles pinned server-side (`resolve` = the 7-op set, `vocab-refresh` = LEMMATIZE-only bulk); consumers name a profile, config tunes it.
Buys: consumer code gets simpler and centrally tunable; the op set becomes an operational knob (e.g. drop DEP_PARSE fleet-wide if unused).
Costs: an indirection layer; profile drift between deployments if not conformance-pinned.

**C4-δ — NLP as a public MCP door (the weird one).** `nlp.*:v1` joins the RO-25 surface for third-party agents.
Buys: third parties get the same primitives the reference stack uses.
Costs: extends the contracted surface (GI-2) for no acceptance-bar clause; third-party agents bring their own NLP; every door needs H-2 identity + conformance fixtures. Maps the boundary — NLP is *internal* infrastructure unless F decides otherwise (cross-link F).

**Threads (either α or γ):**
- **C4-T1 · transport** — formalize gRPC `org.tatrman.nlp.v1` as the service contract (it's in the server-owned proto inventory); keep REST for local dev/health only. The live REST/proto mirror already makes this mechanical.
- **C4-T2 · batch** — the fuzzy refresh case needs a **bulk lemmatize** call (stream or repeated-texts request): hourly re-lemmatization of full member vocabularies over per-string HTTP calls is exactly what made `nlp.enabled=false` the pilot default. Sizing input → Q-11.
- **C4-T3 · caching ownership** — today consumers cache (resolver 24 h LRU). Keep it there (the service stays stateless-primitive) or add a service-side LRU? Lean: consumers keep caching; the service documents idempotency + model-version in responses so caches can key on it.

*Lean: α formalized on gRPC (C4-T1) + the batch call (C4-T2), with γ's profiles as sugar once a third consumer exists; COMPARE demoted to a debug/eval flag outside conformance; δ out unless F finds a bar-relevant reason.*

## 6. C5 · Language architecture & degrade

**C5-α — config-routed ops per language (live shape).** `op_routing`: per-language op→engine map; langid fills missing language; `default_language` per deployment.
Buys: parity-true; zero speculative machinery; adding a *supported* language = config + models.
Costs: the routing table is folklore-shaped (nothing validates a deployment's table against what its mounted models can do).

**C5-β — language-plugin SPI.** Languages ship as bundles (`cs` = morphodita+nametag3, `en` = stanza+spacy); the registry loads bundles; ops routing derives from bundle manifests.
Buys: the standard's "any language" story gets its NLP leg; manifests kill the folklore table.
Costs: an SPI designed against two look-alike bundles is still a guess (R4-β's premature-abstraction trap — language #3 defines the interface); bar-external work.

**C5-γ — capability-honest degrade floor.** Any language gets the deterministic baseline (tokenize + fold; langid); morphology/NER are *enrichments* a deployment has models for; a **capability matrix (lang × op → engine+model-version)** is exposed via GetStatus and echoed in responses; consumers (resolver!) branch on it honestly.
Buys: the unsupported-language stranger gets degraded-but-honest behavior instead of a 500 or silence (mirrors H-8 / R4-γ); the matrix doubles as the conformance assertion of what a deployment claims.
Costs: a floor definition, not a strategy — cs parity still needs the full stack; consumers must actually read the matrix.

**C5-δ — multilingual-model route (the weird one).** Lean on `nametag3-multilingual-250203` (15+ languages) for NER everywhere; morphology only where a tagger exists.
Buys: NER breadth for one model artifact; a real answer for "German estate next quarter" *NER-wise*.
Costs: lemma/POS still per-language (Czech resolution *needs* morphology — δ alone fails GI-3); one big model where cs-only deployments wanted a small one.

*Lean: α + γ together (config routing, validated by and surfaced through γ's capability matrix); β deferred until language #2 is real (amends nothing — same as the R4 lean); δ tracked as the NER-breadth lever inside C3's artifact scheme, not as the architecture.*

## 7. Cross-links & consolidation candidates

- **C1-β's seam and C3-β's artifact scheme are the two extension points** — GPU placement, multilingual growth, and air-gap all become config, not re-architecture.
- **→ E:** the resolver's parse step consumes C4's contract; the hero's CNEC→MISC trap means E's domain-span path must not depend on NER catching product names (it doesn't today — noun-head proposal is NER-independent).
- **→ B:** fuzzy's lemma axis turns ON only when C lands in-cluster (rate limit gone) + C4-T2 exists; B's index quality is a direct beneficiary — worth a B-side assertion in the parity corpus.
- **→ D:** grounding services don't call NLP today (own recognizers); keep it that way (primitive isolation) unless D's divergence finds a reason.
- **→ F:** C4-δ's boundary question lands in F's door catalogue; the capability matrix (C5-γ) feeds `GetStatus`-style conformance fixtures.
- **S-1 (sweep item):** model identity is always explicit on the wire — config pins it, responses echo it, no server-default models anywhere (kills the live empty-`model` bug class).

## 8. Open questions raised here (control-room register)

- **Q-10 — NameTag 3 hosting spike:** CPU memory/latency/cold-start/throughput for `nametag3-czech-cnec2.0` under our load shape (per-question NER + none of the bulk path), in-process vs backend container. Decides C2's NameTag half and C1-α-vs-β sizing. *Small, runnable now — the models download freely.*
- **Q-11 — bulk lemmatization sizing:** pilot member-vocabulary cardinalities (rows × avg tokens) and refresh cadence → C4-T2's batch contract shape (unary-repeated vs streaming) and whether load-time lemmatization stays hourly or moves to on-snapshot-change (couples to B's R3 fork).
- **Q-12 — torch/base-image strategy:** one shared PyTorch base for Stanza + NameTag 3 (version coupling risk) vs per-engine images (size cost) — feeds C1/C3 convergence; partially answered by the Q-10 spike.
