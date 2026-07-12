# LLM Gateway 2.0 (`ttr-llm-gateway`) — Design

> **Audience: the `/planning` session.** This is the converged technical result of the design effort
> (2026-07-11, sessions S1–S9). Rationale and rejected alternatives live in the
> [decision log](./00-control-room.md#6-decision-log-append-only) and the option catalogues
> (`02`–`05`); this document states *what* is decided and the constraints planning must honor.
> Human-readable prose version: [`detailed-design.md`](./detailed-design.md).

## 1. Identity & positioning

- **Service:** `ttr-llm-gateway` — the "prometheus" alias is **dropped** (G-4). Repo home: `tatrman-server/services/ttr-llm-gateway` (G-5). Version 2.0.0, in-place replacement of 1.x (G-1).
- **Positioning:** kantheon-internal (FI-3). No multi-tenant/product ambitions in 2.0.
- **Stack:** Kotlin + **Ktor 3 (CIO, server + client)** — de-Spring complete (FI-1); kotlinx.serialization; HOCON config; Postgres (Flyway) + Redis; Kotest/Mockk/**WireMock**; Jib; Micrometer/Prometheus + OTel. Performance is explicitly a non-goal (FI-2).
- **Surface:** REST + SSE only. gRPC `PrometheusService` is **removed** (FI-5-as-amended; zero consumers).

## 2. Ratified principles (cite by ID in planning)

- **P-1 Claims match runtime.** No capability is "done" until exercised against a live or WireMock upstream in CI. Every implementation stage needs a runtime smoke gate.
- **P-2 The gateway owns attribution.** Every token in/out attributed to an identity and priced, streaming included. Attribution-breaking features are bugs.
- **P-3 Boring dependencies, honest scope.** Prefer maintained deps where they fit; unsupported surface is documented/rejected, never stubbed to return 200.

## 3. Architecture (FI-6 layering)

```
transport (Ktor routes, OpenAI wire)  →  inference engine (routing, retry, fallback, governance hooks)
    →  provider registry  →  { passthrough handler + parse-lite tap | Anthropic converter }
```

One-way dependencies. No business logic in route handlers. Sealed `GatewayError` taxonomy with one error-converter per provider (incl. passthrough error-frame mapping) is the shared decision vocabulary for retry/fallback.

### 3.1 Provider layer (B-1…B-5)

- **Requests are always fully parsed** into an unknown-field-preserving model (kotlinx.serialization with a catch-all `JsonObject` delta). This is the **B-T2 invariant** — routing, governance, logging, and fallback-replay all depend on it. ⚠ **BQ-4 spike is mandatory before Phase 1 code**: prove faithful round-trip of unmodeled fields; contingency = openai-java SDK for the OpenAI-wire side (B-5).
- **Responses: passthrough-first.** OpenAI-wire upstreams — **Azure OpenAI, OpenAI, Gemini via its OpenAI-compat endpoint** — stream bytes through unparsed, with a **parse-lite tap** extracting SSE event boundaries, `usage`, `finish_reason`, error frames. Azure = passthrough + URL/auth/api-version/deployment rewrite from the catalog.
- **Anthropic is the single full converter** (request + response + stream-chunk + error). It is load-bearing beyond Anthropic traffic: cross-provider fallback replays parsed requests through it.
- **No Bedrock. No native Gemini API.** (Parked with revisit conditions.)
- **Stream representation is dual** (B-3): opaque frames on passthrough, normalized chunks on the converted path; **all consumers (metering, prompt log, cache, SSE writer) depend only on the unified tap/`StreamObservation` interface.**
- **SSE named requirements** (B-4, each needs a test per P-1): server side — ktor SSE plugin or `respondBytesWriter` with per-event flush, heartbeats, client-disconnect cancels the upstream call via structured concurrency; upstream side — `preparePost{}.execute{}` (never buffered `body()`), UTF-8-safe line framing across read boundaries, upstream HTTP-error and mid-stream error-frame handling.
- Embeddings ride passthrough (`/v1/embeddings`).

### 3.2 Routing & resilience (C-1…C-5)

- **Model resolution, three tiers** (C-1): alias map → literal catalog match (strict: unknown explicit name = error) → tag soft-match with **deterministic tie-break (lowest `cost`, then name)**. Namespaced `provider/model` accepted additively. Aliases are live contract ("haiku", "sonnet", "gpt-4" in kantheon callers). Catalog schema must accommodate future `tier`/`task_kind` dimensions (GI-3 — Pythia's anticipated native routing; *schema* accommodation only, not 2.0 routing logic).
- **Catalog + rules = static config files, redeploy to change** (C-2). No hot reload. Schema **DB-ready** (stable IDs, no positional semantics). Catalog row: name, aliases, provider, deployment/upstream-name, type, tags, pricing, cache TTL, fallback chain.
- **Retry** (C-3): retryable = RATE_LIMIT (honor `Retry-After`), TIMEOUT (connect/TTFB), NETWORK, PROVIDER_5XX (incl. Anthropic 529). Non-retryable = AUTH, VALIDATION, CONTEXT_LENGTH, CONTENT_FILTER. Exponential backoff + jitter; attempt cap **and wall-clock retry budget**; **retries/fallbacks only before the first token reaches the client** — after that, honest SSE error frame + close.
- **Fallback** (C-4): static ordered chains declared per logical model in the catalog. Mechanics = replay parsed request through next entry. **Circuit-breaker-lite**: per-provider consecutive-failure counter skips chain entries (deterministic skip-ahead, no reordering). Cross-provider param policy = **strip-and-log** (stripped list into the prompt log). Actual provider/model **always** recorded and surfaced (`X-Gateway-Provider`/`X-Gateway-Model` headers + log) — never silent.
- **Upstream keys** (C-5): single key per provider in 2.0, but the provider-call contract **takes a `Key` parameter from day one** (pool-ready interface; never bake keys into client construction).

### 3.3 Governance (D-1…D-6)

- **Credentials, dual** (D-1): data plane = **gateway-issued virtual keys** (`ttrk-…`, `SecureRandom`, SHA-256-hashed at rest), validated **by the gateway itself** — east-west traffic bypasses Envoy (GI-2/DQ-1), so injected identity headers are never trusted from in-cluster callers. Admin plane = Keycloak JWT (Envoy-validated). Domain model: `VirtualKey (n) → Team (1)`; `Budget`, `RateLimit` attachable — **no Customer tier, no OPA, no Key Vault**.
- **Attribution** (D-2): key→team primary; `X-Cost-Center` refines *within* the team (prefix-validated); `X-Turn-Ref` trace-only. Compatible with the existing Hebe header contract (GI-1). **`usage.cost` echo preserved on every response** — non-stream, final SSE usage chunk, and cache hits (Pythia reconciles from it, GI-3).
- **Budgets** (D-3): calendar-monthly, money-denominated (token counts also tracked); attach to team and optionally per-key, min-wins. Rate limits per key, rolling windows.
- **Enforcement** (D-4): **pre-check + post-settle**. Rate limits = Redis, hard pre-check (atomic INCR/EXPIRE). Budgets = Postgres **atomic** `UPDATE … SET used = used + ?` at request/stream end; breach blocks the *next* request; overshoot bounded by concurrent streams (documented, accepted). Usage source precedence: tap `usage` chunk → non-stream `usage` → tokenizer estimate flagged `estimated=true`.
- **Admin surface** (D-5): definitions (teams, budgets, limits) in config files; **minimal Keycloak-gated key API** — issue/revoke/list only. Workflow: Bora issues keys via CLI/curl.
- **Breach** (D-6): rate limit → 429 `rate_limit_exceeded` + `Retry-After`; budget → alert at 80%, block at 100%, per-budget `hard|soft` flag, **soft default**; blocked = 429 `insufficient_quota` + `x-gateway-reason: budget_exceeded`. All error bodies OpenAI-shaped.

### 3.4 API surface (A-1…A-3, FI-4)

- Endpoints: `POST /v1/chat/completions` (full OpenAI wire compat incl. SSE streaming + tool calls — stock SDKs must work unmodified), `GET /v1/models`, `POST /v1/embeddings`, `GET /health` + `/health/live` + `/health/ready` + `/health/providers`, `GET /metrics`, `/admin/keys/*`.
- **Not in 2.0:** Responses API, async-jobs endpoints (parked; async removal gated on the contract diff, SQ-2), `/api/v1` alias (dropped after the one-line Kleio migration), gRPC.
- Extension vocabulary: `usage.cost`; **dual usage-field names during migration** (OpenAI `prompt_tokens`/`completion_tokens` + 1.x `input_tokens`/`output_tokens` additively); `X-Gateway-Provider`/`X-Gateway-Model`; `X-Gateway-Cache: bypass|refresh`; accepted inbound: `X-Cost-Center`, `X-Turn-Ref`, `traceparent`.

### 3.5 Caching (E-1)

Exact-match Redis cache ported from 1.x, made streaming-aware: hit on `stream:true` replays as a **synthetic two-event stream** (content chunk + usage chunk, `cached:true`). Key = logical model + normalized request hash; entry records actual provider/model. Hits **count against request rate limits, do not deduct money budgets**; `usage.cost` echoed with `cached=true`. TTL per model from the catalog. Semantic cache parked.

### 3.6 Observability (F-1)

- Prompt logs: Postgres + TSVECTOR (1.x scheme) with new columns — key id, team, cost-center, turn-ref, actual provider/model, fallback-from, stripped-params, `estimated`, `cached`, TTFB, duration.
- Tracing: **real OTel spans** — transport span → per-attempt provider spans (retries/fallbacks visible as siblings); **continue the inbound W3C `traceparent`** (Hebe already sends it; 1.x drops it).
- Metrics: Ktor MicrometerMetrics plugin + counters: tokens/cost per team×provider×model, retries, fallbacks, circuit state, cache hit rate, budget-consumption gauges.
- Health: readiness = config parsed + DB + Redis reachable; `/health/providers` = circuit state. **No fake probes** (P-1).

## 4. Migration (G-1…G-3)

In-place, same k8s Service, gated by:
1. **Contract-diff verification** — capture real 1.x responses for each consumer's calls; diff against 2.0 in staging. Known wrinkles it must catch: usage-field names; any reader of 1.x's custom `output[]`/`status`/`createdAt` fields; async-jobs consumers (SQ-2).
2. **Staging soak** with Hebe/Kleio/Themis smoke calls.
3. Redis cache **flush at cutover** (entry format changes); prompt-log schema changes additive.
4. **Credential cutover:** existing static per-instance gateway keys (today decorative — 1.x doesn't validate them) imported as **seeded virtual keys** (hash + team mapping) → zero caller changes day one; teams rotate onto issued `ttrk-` keys later via the key API.
5. Removals: gRPC + protos, `/api/v1` (post-Kleio), async jobs + NATS job infra (pending SQ-2).

## 5. Constraints & invariants for planning

1. **B-T2 invariant**: parsed, unknown-field-preserving request model — everything depends on it; spike it first (BQ-4).
2. **Tap interface** is the only coupling point for metering/logging/cache/SSE — no consumer touches chunk internals.
3. **Before-first-token rule** for retries and fallbacks — no exceptions.
4. **Attribution invariants** (P-2): actual provider/model always recorded; `usage.cost` echo everywhere; cache hits attributed but not budget-deducted.
5. **P-1 gates**: per-provider WireMock conformance suites (incl. SSE framing edge cases: UTF-8 split, tool-call deltas, error frames, `[DONE]`); every phase ends with a runtime smoke test; the migration contract diff is itself a P-1 gate.
6. Providers **never** construct with a baked-in key (C-5).
7. Error bodies always OpenAI-shaped (SDK-parseable), even for gateway-originated errors (D-6, A-3).
8. 1.x code is the porting source for: rule engine semantics, Redis cache, prompt-log scheme, pricing/cost computation. The ai-gateway experiment is a **design** reference only — its code is not to be reused unverified (see `ai-gateway-review-260711`, FI-6).

## 6. Deferred (parking lot — do not plan)

MCP surface · semantic cache · admin UI · full admin/catalog API + hot reload · multi-tenant/product promotion · clustering/adaptive LB · gRPC · Gemini native API · Bedrock · key pools + rotation + per-team pinning · dynamic health-scored fallback · Responses API · async jobs (pending SQ-2 confirmation of no consumers).

## 7. Hero scenario (acceptance narrative)

Golem, using a stock OpenAI SDK with a `ttrk-` key, streams a tool-calling conversation: request parsed once (B-T2), admission-checked (rate limit Redis, budget read), routed via alias to Azure (passthrough + rewrite); Azure returns 429s → typed retry honors `Retry-After`, wall-clock budget expires → chain fallback replays the parsed request through the Anthropic converter (params stripped-and-logged); the client receives one seamless OpenAI-shaped SSE stream with `X-Gateway-Provider: anthropic`; the tap settles actual usage into the team's Postgres budget counter; the prompt log row carries fallback-from, cost-center `golem/<id>`, turn-ref, TTFB; the OTel trace (continued from Golem's `traceparent`) shows the Azure attempts and the Anthropic success as sibling spans; Pythia reconciles from the `usage.cost` echo in the final usage chunk. Every clause above is a testable requirement.

## 8. Next step

`/planning` session: consume this document → architecture doc, contracts, phased implementation plan, task lists. Suggested phase-0 items: BQ-4 spike; WireMock conformance harness skeleton; contract-diff capture harness against live 1.x; Kleio alias migration.
