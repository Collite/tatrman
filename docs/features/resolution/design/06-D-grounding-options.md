# D · Grounding Services — Options Catalogue

> **Status: CONVERGED 2026-07-12 — RS-18..21 in the control room §7** (all four forks decided as leaned). Net shape: live topology + **`ttr-grounding-core` kernel** extracted at the move · geo = capability-honest Nominatim seam + cache-on-with-priming + documented dark floor, **gazetteer/RÚIAN artifact = named CZ-first arc** (Q-7 resolved) · "other services" admission rule (*client-specific but rule-computable, span-detectable, recipe-expressible*) + SPI-by-convention + three-place growth checklist · inherit `[~]` stages with the fix-at-rename list; server/agent/tatrman boundary pinned; corpora → conformance extended tier. Q-18/Q-19 + threads T1–T4 → planning. Fact base = `02-recon-live-reference.md` §D (the DFP grounding corpus + live chrono/geo/money) — a **converged, test-backed design** this workstream mostly inherits. D's forks are therefore the *deltas*: extraction shape, the geo offline story (Q-7), extensibility ("other services", FI-1), and the inheritance boundary. The `grounding.*:v1` MCP ids are reserved (GI-2) and enter the surface with this arc.
> Inherited invariants (not re-opened): recipe contract (`Normalized` + `ValueBinding|FilterRecipe|JoinRecipe` + derived `sql_preview` + `source{RULES|LLM}`), `reference_datetime` always from the request, rules-first with LLM fallback off by default, semantic discovery over `semantics{}` hints, catalog functions with per-dialect lowering.

---

## 1. Facts (fixed ground, not options)

- **One generic proto** (`grounding.v1`: `Ground`/`GetStatus`), three implementations; a thin generic `grounding-mcp` (3 tools) exists. Rename target `org.tatrman.grounding.v1` (J-v2); recipes embed `plan.v1` Expressions — **lockstep with the tatrman-owned plan protos** (transfer per publish gate 3).
- **Duplication debt:** `RecipeBuilder`/`PlanExpr`/`SqlRenderer`/`Diacritics` are copied per service (recon §D.1); S-2 (shared normalization spec) already covers the fold part.
- **Determinism/offline:** chrono + money fully offline-deterministic; **geo's place resolution = the single online seam** (Nominatim; 90-day Postgres boundary cache off by default; RÚIAN deferred DFP-side #137; PostGIS assumed for PG lowering, capability-surfaced).
- **DFP stage states:** A1/A2/A3/A5/A7 complete; A6/A8–A14 `[~]` code+tests done, deploy/runtime paused; **A4 df-annotation ⛔ BA-gated** (`YamlToTtrCli` lacks `semantics{}` passthrough — a tatrman-side tooling gap); T1–T6 = the tatrman semantics-block feature (grammar 4.2, in flight). Eval corpus: 109 bulk + 21 E2E.
- **Repo boundary today:** services + proto + mcp = ai-platform (extraction → `tatrman-server`); `GroundEntities` node + cascade consumption = Golem-side (kantheon/agents) — *agent* code, not server code.
- **Hero check:** „poslední fiskální čtvrtletí" — the recognizer knows fiscal *year* words; fiscal alignment is delegated to model-declared **period tables** (JoinRecipe). Whether **quarter-granularity** period semantics (period_code formats beyond `yyyyMM`, quarter arithmetic relative to `reference_datetime`) are actually covered = **Q-18**, a parity-relevant gap to verify against the corpus.

## 2. D1 · Extraction shape — three services, a kernel, or a host?

**D1-α — extract as-is (three services + proto + mcp, renamed).** The roster already lists `chrono/geo/money (+ ttr-grounding-mcp)` as separate rows; extraction = move + J-v2 rename + chart entries.
Buys: smallest delta; per-domain scaling/deps stay isolated (geo's Postgres/PostGIS/JTS never burdens chrono); matches the RS-3..8 philosophy (small single-purpose services).
Costs: the duplicated recipe kernel ships three times and drifts three ways; three images to maintain for ~200 KB of shared logic difference.

**D1-β — α + a shared `ttr-grounding-core` library.** Same topology; the duplicated triple (`PlanExpr`, `SqlRenderer`, `RecipeBuilder` scaffolding) + fold (S-2) consolidates into one server-owned lib the three services depend on.
Buys: kills the drift class the recon flagged; one place where recipe-rendering correctness lives (the `sql_preview`-derived-not-duplicated invariant enforced once); new grounders (D3) start from the kernel.
Costs: a library release cadence between the services; consolidation work during extraction (but extraction touches every file anyway — the cheapest moment it will ever have).

**D1-γ — one `ttr-grounding` host, grounders as plugins.** Single service, three in-process grounder modules behind the one proto.
Buys: one deployment, one image; the SPI story made physical.
Costs: geo's heavy deps (Postgres, JTS, Nominatim client) land in everyone's pod; per-domain scaling lost; contradicts the roster as pinned in the architecture doc (a re-open, not a delta); failure isolation lost (a geo OOM takes chrono down).

**D1-δ — fold grounding into the resolver (the weird one).** Universal spans grounded inside `resolve`.
Buys: one fewer hop for the resolve-orchestrated path.
Costs: couples E's placement fork before it converges; grounding has non-resolver consumers by design (agents call grounding tools directly today — the GroundEntities node); mixes deterministic-service concerns with E's still-open P2 split. Maps why the seam exists.

*Lean: β — α's topology with the kernel extracted during the move; γ/δ map the space.*

## 3. D2 · The geo offline story (Q-7)

**D2-α — Nominatim as documented optional dependency (live shape, capability-honest).** Deployment configures a Nominatim endpoint (public or self-hosted instance); absent/unreachable ⇒ geo grounding degrades, `GetStatus` says so.
Buys: zero new machinery; self-hosted Nominatim is a real, documented option for enterprises; fail-loud semantics already right (UNAVAILABLE, not "place doesn't exist").
Costs: public-endpoint default = egress + rate limits + a moving external on the resolution path (the C2-γ argument, geographically); air-gapped estates get no geo at all.

**D2-β — bundled gazetteer artifact.** A versioned place/boundary artifact (RÚIAN pack for CZ, OSM/GeoNames extract per region) mounted like C3's models; the geo service resolves offline against it, Nominatim demoted to fallback for exotic places.
Buys: offline + deterministic geo (pinned artifact = reproducible resolutions — the GI-1 story geo currently can't tell); CZ-first quality (RÚIAN is *the* Czech admin source; DFP already wanted it, deferred as #137); rate limits gone for the common case.
Costs: artifact build pipeline (region extracts, boundary simplification) = real new scope; freshness discipline for places (slow-moving, but not static); size (CZ boundaries manageable; "the world" is not — regional packs only).

**D2-γ — cache-primed deployments.** Boundary store ON by default (it exists — Postgres, 90-day TTL); deployments pre-seed it (an operator priming run over the estate's known places: POIs, cities in the data).
Buys: cheap; converges to offline for the places an estate actually asks about; the priming run is a natural install step ("warm the geo cache") and reuses existing code paths.
Costs: cold places still need the online seam; priming lists are folklore unless tied to the model (POIs are — cities in member data could be, via B's vocabulary); determinism only for cached places.

**D2-δ — geo-goes-dark honesty (the floor, not a strategy).** No external configured ⇒ geo capability off; documented; conformance treats geo fixtures as conditional on capability.
Buys: the acceptance bar's stranger is never blocked by geo (bar's governed-answer clause doesn't require geo); honest.
Costs: the hero's „pražských pobočkách" goes unanswered on such deployments — fine as floor, wrong as default ambition.

*Lean: compose — **α + γ + δ now** (capability-honest online seam, cache on by default + priming step, dark floor documented), **β as the named CZ-first arc** (RÚIAN artifact — it also answers determinism, which α/γ never fully do). Q-7 closes with the composition; β gets a revisit trigger (CZ enterprise estate demanding air-gapped geo, or the parity corpus showing Nominatim variance).*

## 4. D3 · "Other services" — the admission rule & extensibility mechanics

**Question.** FI-1 says "grounding (time, geo, money, **potentially other services**)". What makes a candidate a *grounder* (percent? quantity/units? duration? person-names?), and what does adding one cost?

**D3-α — closed set of three for 1.0.** New grounders = new design arcs, case by case.
Buys: zero speculative machinery; the three cover the pilot corpus.
Costs: no recorded rule means the next candidate re-litigates the category from scratch.

**D3-β — SPI-by-convention + admission rule.** The generic proto already *is* the SPI. Pin the convention: a grounder = (1) a span **kind** the resolver/NER can detect; (2) semantics **roles/kinds** the model declares (closed-vocabulary evolution, no grammar bump — RS-12-γ's path); (3) a `grounding.v1` implementation producing recipes from catalog functions with per-dialect lowering; (4) an MCP tool entry under `grounding.*:v1` (additive, GI-2); (5) deterministic rules-first, LLM fallback optional-off. **Admission rule: client-specific but rule-computable, span-detectable, and recipe-expressible.** New grounder = new service on the kernel (D1-β), no proto change.
Buys: the category is defined once; `EntityKind` enum growth is the only shared-contract touch; candidates (duration, quantity/units, percent) can be evaluated against the rule in one paragraph each.
Costs: `EntityKind` is a proto enum — additive growth is fine, but the *resolver's* universal-label mapping and the *agent's* routing must track it (a three-place change per grounder — document the checklist).

**D3-γ — grounders as model-declared functions (the weird one, from the map).** No new services: grounding semantics as TTR-level function vocabulary the translator lowers.
Buys: zero runtime cost per grounder.
Costs: recognizers (the hard 80%) have no home; conflates translation-time functions with parse-time span understanding. Maps the boundary: **catalog functions are the output vocabulary of grounding, not its replacement.**

*Lean: β — with the three-place growth checklist written down; α's caution survives as "no new grounder inside this effort's scope" (candidates go to the parking lot with the rule applied).*

## 5. D4 · The inheritance boundary — what the extraction takes, re-opens, and leaves

**D4-α — inherit `[~]` as-is.** The paused-at-deploy stages (A6, A8–A11, A14 server-side parts) extract verbatim; their test suites (70+67+57+9+18) become the open lineage's; re-open **only** what extraction itself breaks (renames, chart, `ResponseMessage` import deviation — fix at the proto rename).
**D4-β — re-verify everything against the open bar.** Treat `[~]` as unreviewed; full re-walk before extraction.
**D4-γ — cherry-pick.** Extract chrono/money now (offline-deterministic, clean), hold geo for D2's story.

Boundary facts to pin regardless (not really options):
- **Server-side** (→ `tatrman-server`): chrono, geo, money, grounding-mcp, the D1-β kernel, `org.tatrman.grounding.v1`.
- **Agent-side** (stays kantheon): `GroundEntities` node, cascade consumption, load-bearing-clarification predicate — reference-Golem material; F decides what of it the *contract* absorbs.
- **tatrman-side:** semantics-block 4.2 (in flight, T1–T6) + the **A4 gap** (`YamlToTtrCli` semantics passthrough — BA-gated DFP-side, but the *capability* is a tatrman tooling item; likely lands with the import-schema/lexicon arcs).
- **Corpora:** the 109+21 grounding eval set feeds the conformance suite's extended tier (RO-25's DFP-derived tier; RO-19 ask ③ adjacent).

*Lean: α — with the fix-at-rename list explicit (proto `ResponseMessage` import, J-v2 names, S-2 fold consolidation, S-3 on any operator endpoints) and γ rejected (geo extracts *with* its capability-honest α+γ+δ posture; holding it back just delays the fixture work).*

## 6. Threads

- **D-T1 · GroundingContext ownership:** `reference_datetime/timezone/locale/default_currency/here_place_ref/fx_policy` — becomes part of the server-owned proto; who assembles it (agent from turn state + instance config) is F/E material; the *no-clock-reads* invariant is conformance-assertable.
- **D-T2 · PostGIS capability:** "geo goes dark on PG without PostGIS" (accepted DFP decision #12) — carries into the open offering with GetStatus surfacing + docs; the chart's values document the expectation.
- **D-T3 · Fiscal-quarter granularity (Q-18):** verify period-table semantics + recognizer cover quarter-class periods („fiskální čtvrtletí", `yyyyQn`-class codes) — parity-relevant for the hero; if absent, it's a small recognizer+semantics-params extension, not a re-architecture.
- **D-T4 · LLM-fallback posture in the open offering:** default-off carries (GI-1-friendly); when enabled, `source=LLM` labeling + structural validation are conformance-assertable — the honest-tier pattern again.

## 7. Open questions raised here

- **Q-18 — fiscal-quarter coverage** (D-T3): confirm against the DFP corpus + period-table params; extend recognizer/semantics if the hero's phrase isn't covered.
- **Q-19 — Nominatim usage policy in defaults:** if the chart ships a default endpoint at all, whose? (Public OSM Nominatim has a usage policy — bulk/production use is restricted; self-host guidance is the likely answer. Feeds docs + D2-α.)
