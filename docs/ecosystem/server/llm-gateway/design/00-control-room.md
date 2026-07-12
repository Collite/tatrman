# LLM Gateway 2.0 Design — Control Room

> The single dashboard for the **llm-gateway 2.0.0** design effort ("prometheus" successor).
> Open this first every session. Companion doc: [`01-design-space-map.md`](./01-design-space-map.md).
> Method: diverge-then-converge, per the ttr-p reference (`docs/features/ttr-p/design/`).
>
> **Status: COMPLETE (2026-07-11).** All workstreams 🟢; P-1…P-3 ratified; final deliverables written: [`design.md`](./design.md) (planning-facing) + [`detailed-design.md`](./detailed-design.md) (prose manual). **Next step: a `/planning` session consuming `design.md`.**

---

## 0. How we run this

Multi-session, exploration-first. **Diverge before converging** — enumerate alternatives per workstream (≥3, incl. the weird one), no decisions during divergence, every decision lands in the append-only log with the rejected alternatives and why. Statuses: ⚪ not started · 🔵 diverging · 🟡 options captured · 🟢 converged · ⏸ parked.

**Hero scenario** (carried through every workstream): *Golem streams a tool-calling conversation through the gateway using the stock OpenAI SDK; the request is attributed to Golem's team budget; mid-conversation the primary Azure deployment starts returning 429s and the gateway falls back to Anthropic; the whole exchange appears in the prompt log and in Pythia's cost attribution.*

---

## 1. Workstream dashboard

| # | Workstream | Status | Core question |
|---|---|---|---|
| **A** | Scope & API surface | 🟢 | **Converged 2026-07-11** → A-1…A-3 (chat-completions core; single `/v1` surface; extension vocabulary). [`05-small-workstreams-options.md`](./05-small-workstreams-options.md). |
| **B** | Provider layer & streaming | 🟢 | **Converged 2026-07-11** → B-1…B-5: passthrough-first + single Anthropic converter; roster Azure/OpenAI/Gemini-compat passthrough + Anthropic; dual stream repr. with unified tap; BQ-4 spike mandatory in planning. See [`02-provider-layer-options.md`](./02-provider-layer-options.md). |
| **C** | Routing & resilience | 🟢 | **Converged 2026-07-11** → C-1…C-5: 3-tier routing (deterministic ties), static files + DB-ready schema (no hot reload — monthly cadence), typed retry matrix (before-first-token), catalog-declared fallback chains + circuit-breaker-lite, single key behind pool-ready interface. See [`03-routing-resilience-options.md`](./03-routing-resilience-options.md). |
| **D** | Governance & auth | 🟢 | **Converged 2026-07-11** → D-1…D-6: dual credentials (gateway-validated virtual keys — east-west bypasses Envoy; Keycloak admin plane), key→team attribution + validated header refinement, calendar-monthly money budgets, pre-check/post-settle w/ Redis+Postgres split, minimal key API, soft-default tiered breach (S-1/S-2 ratified by Bora). See [`04-governance-options.md`](./04-governance-options.md). |
| **E** | Caching | 🟢 | **Converged 2026-07-11** → E-1 (exact-match port, streaming-aware replay; hits don't deduct budgets). |
| **F** | Observability & ops | 🟢 | **Converged 2026-07-11** → F-1 (pg logs + new columns; real OTel continuing Hebe's traceparent; honest health). |
| **G** | Migration & rollout | 🟢 | **Converged 2026-07-11** → G-1…G-5 (in-place gated; gRPC/alias/async removals; seeded-key cutover; **name = ttr-llm-gateway, no alias**; stays in tatrman-server). |

## 2. Framing inputs

- **FI-1 · De-Spring.** 2.0 is Ktor, like every other JVM module in the ecosystem. Prometheus 1.x's Spring Boot status was a documented fork-time exception, not a choice to preserve. (Bora, 2026-07-11)
- **FI-2 · Performance is a non-goal.** Clean design over optimization; Ktor/CIO defaults acceptable. Bifrost's perf story is explicitly *not* what we're chasing. (Bora, 2026-07-11)
- **FI-3 · Kantheon-internal positioning.** The gateway serves the constellation (Themis, Golem, agents). Governance scope = internal teams/agents, not external tenants. No product/multi-tenant ambitions in 2.0. (Bora, 2026-07-11)
- **FI-4 · Full OpenAI chat-completions wire compat.** Any stock OpenAI SDK must work unmodified against `/v1/chat/completions`, including streaming and tool calls. (Bora, 2026-07-11)
- **FI-5 · ~~gRPC surface stays~~ → AMENDED same day: 2.0 is REST/SSE-only.** Initial answer ("keep it") assumed consumers existed; Bora confirmed nobody calls `PrometheusService` — it was a residuum of the "internal services should use gRPC" convention, never adopted. Dropping it removes a second transport from streaming design, P-1 verification, and migration compat. gRPC → parking lot with a revisit condition. Resolves Q-1. (Bora, 2026-07-11)
- **FI-6 · Functional inspiration = Bifrost; design salvage = ai-gateway experiment.** The 2026-07-11 review verdict: do not reuse ai-gateway code unverified; port three *designs* — the transport→engine→providers layering, the sealed GatewayError taxonomy + per-provider error converters, and the VirtualKey/Team/Budget/RateLimit domain model. See `ai-gateway-review-260711.md` (session deliverable) and memory note.

## 3. Asset inventory

- **prometheus 1.x** (`tatrman-server/services/ttr-llm-gateway`, ~2.9k LOC, Spring Boot 4 + Spring AI, deployed): works today — HOCON rule engine over `models.yaml` (tags/cost), Redis exact-match response cache with cost-preserving attribution, NATS async jobs + webhooks, Postgres prompt logs with FTS (TSVECTOR), OAuth2 resource server, gRPC + REST (with `/v1/chat/completions` alias), Pythia cost attribution. **No streaming, no tool calling, 2 live providers (Azure OpenAI, Anthropic), no budgets/rate limits, static model catalog.**
- **ai-gateway experiment** (`~/Dev/ai-gateway`, 13.1k LOC Kotlin/Ktor, agent-built 2026-04): unrunnable as reviewed, but carries the three portable designs (FI-6) plus useful negative knowledge (Ktor DoubleReceive, SSE via `respondBytesWriter`/ktor-server-sse not respondText, streaming needs `preparePost{}.execute{}`, atomic budget counters, SecureRandom keys).
- **Bifrost** (Go, maximhq): the functional blueprint — virtual keys/governance, key pools with weighted selection, cross-provider fallbacks, semantic cache, OpenAI-compatible surface, MCP. We cherry-pick functionality, not architecture-for-performance.
- **Ecosystem conventions**: Ktor + kotlinx.serialization + HOCON + Kotest/Mockk + Jib + justfile; `org.tatrman.*`; k8s deploy; sealed `llm-gateway-secrets`.
- **Known consumers**: shared `LlmGatewayClient` (Themis, Golem) posting to `/v1/chat/completions`; gRPC `PrometheusService` has **zero consumers** (confirmed by Bora 2026-07-11 — convention residuum).

## 3b. Grounding inputs

- **GI-2 · Envoy validates JWTs at ingress and injects the Keycloak user id downstream.** (Bora, 2026-07-11 — resolves Q-4.) Open refinement: whether east-west service→gateway traffic also passes Envoy (DQ-1 in workstream D).
- **GI-3 · Pythia's contracts (§5) anticipate the gateway landing native `(modality, tier, task_kind)` routing.** (Found 2026-07-11, DQ-3 grep.) Pythia today runs a client-side tier→tag shim (`strong→opus`, `cheap→haiku`, `embedding→embedding`) with placeholder pricing, "until the gateway lands native routing — every call would carry `task_kind` as metadata so the cutover is a client no-op." → Validates C-1's kept tag tier (it has a named future customer) and puts `tier`/`task_kind`-shaped dimensions on the catalog-schema requirements (A/C surface; no reopening of C). Also: Pythia reconciles via the response **`usage.cost` echo** — 2.0 must keep it (incl. streaming + cache hits). Resolves Q-3.
- **GI-1 · The ecosystem already has an attribution header contract** (found 2026-07-11 during C divergence): Hebe's `GatewayClient` (kantheon P2 Stage 2.2, contracts §5.2, PD-11) sends `Authorization: Bearer <gateway key>` + `X-Cost-Center: hebe/<instance_id>` + `X-Turn-Ref: <turn/job id>` on every gateway call, and consumers already treat the gateway as an OpenAI-compat endpoint. Workstream D designs *around* this contract (gateway keys + cost-center strings + turn refs), not a parallel identity scheme. Also partially answers Q-3's shape.

## 4. Load-bearing forks

1. **Provider layer replacement** (B): hand-rolled clients / official SDKs / langchain4j / passthrough-first proxy / Spring AI without Spring. Constrains everything: streaming, tool calls, error taxonomy, testing.
2. **Streaming architecture** (B): one internal stream representation feeding both REST SSE and gRPC server-streaming; where aggregation for cache/logs/costing happens.
3. **Auth & identity model** (D): OAuth2-only with per-identity quotas vs virtual keys vs hybrid. Decides whether the Bifrost governance domain model comes in whole or trimmed.
4. **Catalog & rules: static files vs managed state** (C/D): `models.yaml` + `rules.conf` vs DB-backed catalog with a management API.
5. **Migration shape** (G): in-place rewrite behind the same endpoints vs parallel service + cutover. Interacts with FI-5 (proto compat) and the Redis cache/prompt-log continuity.

## 5. Design principles (ratified by Bora, 2026-07-11)

- **P-1 · Claims match runtime.** No capability is "done" until exercised against a live or WireMock upstream in CI — per-stage smoke verification. (Direct lesson from the ai-gateway review: everything type-checked, nothing ran.)
- **P-2 · The gateway owns attribution.** Every token in/out is attributed to an identity (team/agent) and priced — streaming included. Features that would break attribution (unaccounted cache hits, unattributed fallbacks) are bugs by definition.
- **P-3 · Boring dependencies, honest scope.** Prefer maintained SDKs/plugins over hand-rolled protocol code where they fit (FI-2 gives us the slack); where we hand-roll, the unsupported surface is documented as unsupported, never stubbed to return 200.

## 6. Decision log (append-only)

- **2026-07-11 · B-1 · Provider layer = passthrough-first hybrid (B-δ + B-α converter).** Responses from OpenAI-wire upstreams stream through *unparsed* with a parse-lite tap (SSE framing, usage chunk, finish_reason, error frames); requests are always fully parsed into an unknown-field-preserving model (B-T2 invariant — routing/governance/logs/fallback all need it). Non-OpenAI-wire providers get a full hand-rolled converter. **Why:** FI-4 holds by construction on the passthrough path (can't corrupt what we don't parse); smallest P-1 verification matrix; converter effort spent exactly once, where cross-provider fallback makes it unavoidable anyway. **Rejected:** B-α everywhere (we'd own every provider quirk — the ai-gateway failure catalogue); B-β official SDKs (decode→re-encode round-trip; capped at Java SDK field coverage, which breaks FI-4 whenever upstream ships params first; challenger status retained, see B-5); B-γ langchain4j and B-ε Spring-AI-sans-Spring (app-framework abstractions inside a wire-fidelity proxy — two lossy translations).
- **2026-07-11 · B-2 · Provider roster for 2.0: Azure OpenAI + OpenAI (passthrough), Anthropic (the one full converter), Gemini via its OpenAI-compat endpoint (passthrough). No Bedrock.** (Bora) Resolves BQ-1. Gemini rides passthrough — a native Gemini-API converter is parked (revisit if Gemini-only features are needed); Bedrock parked (would mean a second converter).
- **2026-07-11 · B-3 · Stream representation = dual with a unified tap (B-T1-γ).** Opaque frames on the passthrough path, normalized chunks on the converted path; all consumers (metering, prompt logs, cache replay, SSE writer) depend only on the tap/`StreamObservation` interface, never on chunk internals.
- **2026-07-11 · B-4 · Sub-forks ratified with the main fork:** B-T3-α (Azure = passthrough + URL/auth/api-version/deployment rewrite from the catalog); B-T5 (embeddings ride passthrough); B-T4 SSE mechanics adopted as named requirements with tests (server: SSE plugin/`respondBytesWriter`+flush, heartbeats, disconnect-cancels-upstream; upstream: `preparePost{}.execute{}`, UTF-8-safe framing, error-frame handling).
- **2026-07-11 · C-1 · Model-string semantics = three-tier, 1.x-compatible (C1-β), determinism fixed.** Alias → literal (strict unknown-name error) → tag soft-match; ties broken by `cost` then name (never random); namespaced `provider/model` names accepted additively in the catalog. **Why:** aliases are live contract (CQ-1: "haiku"/"sonnet"/"gpt-4" in kantheon callers); tag tier is cheap to keep and harmless. **Rejected:** C1-α literal-only (breaks every current caller), C1-γ-only (migration for nothing), C1-δ dumb pipe (kills routing + fallback).
- **2026-07-11 · C-2 · Catalog + rules = static files, redeploy to change (C2-α), schema designed DB-ready.** (Bora — amends the session lean C2-δ.) Models change roughly monthly; hot reload is machinery without a customer. Stable IDs, no file-position semantics, so a DB/admin-API migration is additive later. **Rejected:** C2-δ hot-reload (no need at this cadence — also dissolves CQ-3, moot), C2-β DB + management API now (scope creep for an editor population of ~1; parked, revisit if D lands an admin API), C2-γ policy engine (opaque hot-path dependency, overkill).
- **2026-07-11 · C-3 · Retry policy: typed matrix over `GatewayError`.** Retryable: RATE_LIMIT (honoring `Retry-After`), TIMEOUT (connect/TTFB), NETWORK, PROVIDER_5XX incl. Anthropic 529. Non-retryable: AUTH, VALIDATION, CONTEXT_LENGTH, CONTENT_FILTER. Exponential backoff + jitter; **attempt cap + wall-clock retry budget** (C3-T2-β); retries/fallbacks only **before first token reaches the client** (C3-T1) — after that, honest error frame + close.
- **2026-07-11 · C-4 · Fallbacks = static chains + circuit-breaker-lite.** Chains declared **per logical model in the catalog** (resolves CQ-4); trigger = retry-exhausted or chain-eligible error class; mechanics = replay of the parsed request (B-T2) through the next entry (passthrough rewrite or Anthropic converter). Circuit-breaker-lite: consecutive-failure counter per provider skips ahead in the chain (deterministic, no reordering). Cross-provider param fidelity = **strip-and-log** (resolves CQ-2), stripped params recorded in the prompt log. Actual provider/model always recorded for attribution (P-2) and surfaced in a response header (name it in A) — never silent. **Rejected:** C4-α none (hero dies, consumers reimplement badly), C4-γ dynamic health-scoring (emergent behavior; parked pending F health model), C4-δ client-directed chains (policy leaks to callers).
- **2026-07-11 · C-5 · Upstream keys: single key per provider; pool-ready interface.** Provider-call contract takes a `Key` parameter from day one (the ai-gateway lesson: keys baked in at client construction make rotation/fallback impossible). Weighted pools + rotation (C5-β) and per-team pinning (C5-γ) parked.
- **2026-07-11 · D-1 · Credentials = dual (D1-β): virtual keys on the data plane, Keycloak on the admin plane.** Gateway-issued keys (`ttrk-…`, SecureRandom, hashed at rest; domain model VirtualKey→Team + Budget + RateLimit — no Customer tier, no OPA, no Key Vault) validated **by the gateway itself**, because east-west traffic bypasses Envoy (DQ-1, Bora): in-cluster callers hit the Service directly, so injected user-id headers are trustworthy only on the ingress path and are never accepted as identity from in-cluster traffic. Keycloak JWTs (Envoy-validated) gate the admin surface. Migration: start validating the static per-instance keys callers already send (DQ-2 — today decorative). **Rejected:** D1-α Keycloak-only (SDK token-refresh machinery, no per-project revocation), D1-γ full Bifrost (multi-tenant machinery vs FI-3), D1-δ mesh-trust (P-2 dies).
- **2026-07-11 · D-2 · Attribution: key→team primary; `X-Cost-Center` refines within the team (prefix-validated); `X-Turn-Ref` trace-only.** Backward-compatible with GI-1. The `usage.cost` echo is preserved on **all** responses — streamed (final usage chunk) and cache hits included — per GI-3/Pythia. **Rejected:** header-only attribution (any caller charges any bucket), claims-based (couples Keycloak to billing).
- **2026-07-11 · D-3 · Budgets: calendar-monthly, money-denominated (token counts tracked too), attachable to team and per-key, min-wins. Rate limits: per key, rolling windows.**
- **2026-07-11 · D-4 · Enforcement = pre-check + post-settle (D4-α) with a store split.** Rate limits: Redis, hard pre-check (atomic INCR/EXPIRE, shared across replicas). Budgets: Postgres atomic `UPDATE … SET used = used + ?` at stream/request end; breach blocks the *next* request; overshoot bounded by concurrent streams × max response cost (documented, accepted). Usage source at settle: tap `usage` chunk → non-stream `usage` → tokenizer estimate flagged `estimated=true` (closes BQ-3). **Rejected:** D4-β reserve-then-settle (reservation bookkeeping for unneeded tightness), D4-γ fully-async (rate limits impossible, unbounded lag).
- **2026-07-11 · D-5 · Admin surface = state split (D5-γ)** — *lean ratified explicitly by Bora 2026-07-11 (S-1 closed)*: definitions (teams, budgets, limits) in config files (consistent with C-2); a **minimal Keycloak-gated key API** (issue/revoke/list) + usage counters in DB — revocation is the one operation that can't wait for a redeploy. Workflow reality (DQ-4): Bora issues keys, via CLI/curl against that API. Full admin API/UI stays parked.
- **2026-07-11 · D-6 · Breach behavior** — *lean ratified explicitly by Bora 2026-07-11 (S-2 closed)*: rate limits → hard 429 with `Retry-After`, OpenAI-shaped error body; budgets → tiered (alert at 80%, block at 100%) with per-budget `hard|soft` flag, **soft default** for internal teams.
- **2026-07-11 · P-RAT · Design principles P-1 (claims match runtime), P-2 (gateway owns attribution), P-3 (boring dependencies, honest scope) ratified.** (Bora, at consolidation.)
- **2026-07-11 · A-1 · Endpoint set = chat-completions core** (A1-α): `/v1/chat/completions` (+SSE), `/v1/models`, `/v1/embeddings`, health triple, `/metrics`, `/admin/keys/*` (D-5). Responses API and async-jobs carryover → parking lot (async removal subject to the G contract-diff gate, SQ-2). **Rejected:** Bifrost-everything.
- **2026-07-11 · A-2 · `/api/v1` alias dropped after migrating its one consumer (Kleio, one-line change).** Single wire surface: `/v1/*`.
- **2026-07-11 · A-3 · Extension vocabulary:** `usage.cost` echo everywhere (incl. final SSE usage chunk + cache hits); dual usage-field names during migration (OpenAI `prompt_tokens/completion_tokens` + 1.x `input_tokens/output_tokens` as additive duplicates); `X-Gateway-Provider`/`X-Gateway-Model` response headers (C-4 surfacing); errors OpenAI-shaped — 429 `rate_limit_exceeded` + `Retry-After`, budget breach 429 `insufficient_quota` + `x-gateway-reason: budget_exceeded`; `X-Gateway-Cache: bypass|refresh` request header.
- **2026-07-11 · E-1 · Caching = 1.x exact-match Redis cache ported, streaming-aware** (E-α): hits on `stream:true` replay as a synthetic two-event stream (content chunk + usage chunk, `cached:true`); cache key = logical model + normalized request hash, entry records actual provider/model; hits count against request rate limits, **do not** deduct money budgets; TTL per model in the catalog. Semantic cache stays parked. **Rejected:** no-cache (working attributed feature).
- **2026-07-11 · F-1 · Observability:** prompt logs stay Postgres+TSVECTOR with new columns (key id, team, cost-center, turn-ref, actual provider/model, fallback-from, stripped-params, `estimated`, `cached`, TTFB/duration); real OTel spans (transport → per-attempt provider spans), **continuing the W3C `traceparent` Hebe already sends**; Ktor MicrometerMetrics + gateway counters (tokens/cost per team×provider×model, retries, fallbacks, circuit, cache, budget gauges); honest health — readiness = config+DB+Redis, `/health/providers` = circuit state, no fake probes (P-1). **Rejected:** OpenSearch move, OTel-logs-only.
- **2026-07-11 · G-1 · Migration = in-place, gated** (G1-α): same k8s Service; gates = contract-diff verification against captured 1.x consumer traffic (catches the usage-name wrinkle and any `output[]`/`status` readers) + staging soak with Hebe/Kleio/Themis smoke calls + Redis flush at cutover. **Rejected:** parallel service, strangler (double ops for consumers who already speak the 2.0 surface).
- **2026-07-11 · G-2 · Removals at 2.0.0:** gRPC `PrometheusService` + protos; `/api/v1` alias (post-Kleio); async-jobs endpoints + NATS job infra (pending SQ-2 confirmation).
- **2026-07-11 · G-3 · Credential cutover:** existing static keys imported as seeded virtual keys (hashes + team mapping) — zero caller changes day one; teams rotate onto issued `ttrk-` keys via the key API at leisure.
- **2026-07-11 · G-4 · Name: the "prometheus" alias is dropped — the service is `ttr-llm-gateway`, full stop.** (Bora — resolves Q-5.) No mythological alias; kills the permanent Prometheus-metrics-stack collision. **Rejected:** keep "prometheus", rename to a new alias.
- **2026-07-11 · G-5 · Repo home: stays `tatrman-server/services/ttr-llm-gateway`.** (Bora — resolves Q-2.) Apache-2.0 fine for generic gateway infra despite kantheon-internal positioning; no repo churn after SV-P0. **Rejected:** move to kantheon.
- **2026-07-11 · B-5 · Risk clause:** BQ-4 (unknown-field-preserving request model) is a mandatory early spike in planning; if kotlinx.serialization can't round-trip faithfully, the fallback is B-β for the OpenAI side (challenger preserved, does not reopen B-1's rejection of γ/ε). BQ-3 (usage-tap reliability per passthrough target) transfers to workstream D's settle design.

## 7. Parking lot

| Item | Why parked | Revisit when |
|---|---|---|
| MCP tool surface in the gateway | Bifrost has it; kantheon's MCP surface lives elsewhere (tatrman-server) | An agent needs gateway-mediated MCP |
| Responses API (`/v1/responses`) | A-1: no current consumer | A consumer SDK defaults to Responses and can't fall back |
| Async-jobs endpoints + NATS job infra | A-1/G-2: no consumer found; removal pending contract-diff confirmation (SQ-2) | Contract diff finds a live consumer |
| Semantic cache | Exact-match cache works and is attributed; semantic adds an embedding dependency + a vector store | Exact-match hit rate proves insufficient (measure first) |
| Admin UI | Management API comes first; UI is a consumer of it | Management API exists and a human needs it weekly |
| Multi-tenant / Tatrman Server product promotion | FI-3 says internal | Tatrman Server needs a gateway component |
| Clustering / adaptive load-balancing | Bifrost perf features; FI-2 | Never, probably |
| gRPC surface (`PrometheusService` or successor) | Zero consumers today (FI-5 amendment); second transport = double streaming/verification/migration cost | A real JVM-internal consumer wants typed streaming; add then as a thin adapter over the engine |
| Gemini native-API converter | Gemini rides its OpenAI-compat endpoint in 2.0 (B-2) | A Gemini-only feature (native context caching, etc.) is needed |
| AWS Bedrock provider | Not in the 2.0 roster (B-2); would require a second full converter | Bedrock workloads materialize |
| Catalog hot-reload / DB catalog + management API | C-2: model changes ~monthly, redeploy suffices; schema is DB-ready | Edit cadence rises, or D lands an admin API to piggyback on |
| Weighted upstream key pools + rotation; per-team key pinning | C-5: one upstream account per provider today; interface is pool-ready | Quota incidents, or per-team upstream billing separation needed |
| Dynamic health-scored fallback (C4-γ) | C-4 ships static chains + circuit-breaker-lite | F's provider-health model matures and static chains prove too slow in incidents |

## 8. Open questions

- ~~**Q-1** · Consumer inventory: who actually calls gRPC `PrometheusService` vs the REST alias today?~~ **Resolved 2026-07-11: nobody.** → FI-5 amended, gRPC dropped from 2.0.
- ~~**Q-2** · Repo/service home tension~~ **Resolved 2026-07-11 → G-5**: stays in `tatrman-server`.
- ~~**Q-3** · Pythia contract: what exactly does Pythia consume today?~~ **Resolved 2026-07-11 → GI-3**: response `usage.cost` echo + client-side projection; no ingestion pipeline. 2.0 keeps the echo (streaming + cache hits included).
- ~~**Q-4** · Ingress reality: what does the actual k8s ingress/Envoy do for authn today?~~ **Resolved 2026-07-11 → GI-2** (Envoy validates JWT, injects Keycloak user id). Residual: DQ-1 (east-west path) in workstream D.
- ~~**Q-5** · Naming: does 2.0 keep "prometheus"?~~ **Resolved 2026-07-11 → G-4**: alias dropped; the service is `ttr-llm-gateway`. (`org.tatrman.llm.v1` proto dies with gRPC per G-2.)

## 9. Session index

| Date | Session | Gear | Output |
|---|---|---|---|
| 2026-07-11 | S1 — Framing | Framing | Control room + design-space map created; FI-1…FI-6; hero scenario; A–G workstreams; Q-1…Q-5. Preceded by the ai-gateway review (same day) that produced FI-6. Same session: FI-5 amended (gRPC dropped — zero consumers), Q-1 resolved. |
| 2026-07-11 | S2 — B divergence | Divergence | `02-provider-layer-options.md`: B-α…B-ε + sub-forks B-T1…T5; lean = B-δ passthrough + single Anthropic converter (B-β SDKs = challenger); BQ-1…BQ-4. B → 🟡. |
| 2026-07-11 | S3 — B convergence | Convergence | B-1…B-5 decided (passthrough-first hybrid; roster = Azure/OpenAI/Gemini-compat + Anthropic converter, no Bedrock; dual stream repr.; SSE requirements; BQ-4 spike clause). B → 🟢. |
| 2026-07-11 | S4 — C divergence | Divergence | `03-routing-resilience-options.md`: C1 model-string semantics · C2 catalog/rules state · C3 retries · C4 fallback chains · C5 key pools; grounded in 1.x RuleEngine (3-tier resolution; non-streaming tool calls already work in 1.x). CQ-1 resolved by kantheon grep (aliases live, tags vestige); **GI-1 discovered** (X-Cost-Center/X-Turn-Ref header contract → D). C → 🟡. |
| 2026-07-11 | S5 — C convergence | Convergence | C-1…C-5 decided; Bora amendment on C2: **static files, no hot reload** (monthly cadence), DB-ready schema. CQ-2/CQ-4 resolved in C-4; CQ-3 moot. C → 🟢. |
| 2026-07-11 | S6 — D divergence | Divergence | Q-4 → GI-2 (Envoy JWT + injected user id). `04-governance-options.md`: D1…D6 forks; leans recorded; DQ-1…DQ-4. Greps resolved DQ-2 (gateway key = static per-instance secret, today likely decorative) + DQ-3/Q-3 (Pythia = `usage.cost` echo; **GI-3** tier/task_kind routing anticipated). D → 🟡; open: DQ-1, DQ-4, D5/D6 calls. |
| 2026-07-11 | S7 — D convergence | Convergence | DQ-1 (east-west bypasses Envoy → gateway validates keys itself) + DQ-4 (Bora issues keys) answered; D-1…D-6 decided; D-5/D-6 subsequently ratified explicitly by Bora (S-1/S-2 closed). D → 🟢. |
| 2026-07-11 | S8 — small-workstreams sweep | Div.+Conv. | `05-small-workstreams-options.md`; grounding greps (Kleio uses `/api/v1` alias; no async-jobs consumers; usage-name wrinkle). A-1…A-3, E-1, F-1, G-1…G-5 decided (Bora: name = **ttr-llm-gateway no alias**, stays in tatrman-server, in-place gated migration). A/E/F/G → 🟢. Q-2, Q-5 resolved. |
| 2026-07-11 | S9 — wrap-up | Consolidation | P-1…P-3 ratified (P-RAT); `design.md` + `detailed-design.md` written. **Effort COMPLETE** → hand off to `/planning`. |
