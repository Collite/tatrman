# LLM Gateway 2.0 (`ttr-llm-gateway`) — Detailed Design

> The exhaustive write-up of the 2026-07-11 design effort, for a reader who wasn't in the sessions.
> The compact, planning-facing version is [`design.md`](./design.md); decisions and rejected
> alternatives are recorded in the [control room](./00-control-room.md); per-workstream option
> catalogues are docs `02`–`05`.

## 1. Why a 2.0 at all

The 1.x gateway ("prometheus", forked from ai-platform's `infra/llm-gateway`) works and is deployed:
rule-based routing, a Redis exact-match cache with cost attribution, Postgres prompt logs with
full-text search, NATS async jobs, OAuth2. But it is the ecosystem's only Spring Boot module — a
documented fork-time exception in an otherwise all-Ktor JVM estate — and it lacks the four things
kantheon's agent workloads now need most: **streaming**, **true OpenAI wire compatibility**,
**tool-call fidelity end-to-end**, and **governance** (per-agent keys, budgets, rate limits).

Two events preceded this design. First, an experiment: an AI agent rewrote the Go **Bifrost**
gateway to Kotlin/Ktor (~13k LOC in two days). The review verdict (2026-07-11,
`ai-gateway-review-260711`) was that the code was an unrunnable scaffold — but three of its
*designs* were worth porting: the transport→engine→providers layering, the sealed error taxonomy
with per-provider error converters, and the VirtualKey/Team/Budget/RateLimit governance domain
model. Second, that review yielded a process lesson now enshrined as principle P-1: everything in
that codebase type-checked, and almost nothing had ever been *run*. 2.0's design therefore treats
runtime verification as a first-class design constraint, not a QA afterthought.

**Framing commitments:** Ktor, not Spring (FI-1). Performance is a non-goal — clean design over
optimization (FI-2). The gateway is kantheon-internal; no multi-tenant ambitions (FI-3). Full
OpenAI chat-completions wire compat — a stock OpenAI SDK must work unmodified, streaming and tool
calls included (FI-4). REST/SSE only — the gRPC surface had zero consumers and is removed (FI-5 as
amended). The three ai-gateway designs are ported as designs, never as unverified code (FI-6).

**Ratified principles:** P-1 *claims match runtime* (no capability is done until exercised against
a live or WireMock upstream in CI); P-2 *the gateway owns attribution* (every token attributed and
priced, streaming included); P-3 *boring dependencies, honest scope* (maintained deps where they
fit; unsupported surface documented, never stubbed to 200).

## 2. The shape of the thing

Four layers, one-way dependencies:

**Transport** — Ktor routes speaking OpenAI wire format. Thin: validation, auth hand-off, SSE
writing. **Inference engine** — the orchestrator: model resolution, admission (governance),
retries, fallbacks; provider-agnostic. **Provider registry** — config-driven instances.
**Providers** — where 2.0 diverges from every framework-shaped alternative considered: a
*passthrough handler* for OpenAI-wire upstreams and exactly one full *converter*, for Anthropic.

### 2.1 The passthrough insight

The de-Spring decision costs us Spring AI, which does all of 1.x's provider work. The obvious
replacements — hand-rolling every provider client (what the ai-gateway experiment attempted, and
where it accumulated most of its bugs), the official Java SDKs (decode into SDK objects only to
re-encode nearly identical JSON, capped at the SDK's field coverage), langchain4j (two lossy
translations inside a proxy whose job is fidelity) — all share a flaw: they put a *model of the
payload* between two parties who already speak the same language.

Most of the industry is OpenAI-wire: OpenAI itself, Azure OpenAI (transport differences only:
URL, auth header, api-version), Gemini's OpenAI-compat endpoint, Groq, OpenRouter, Ollama, vLLM…
For these, 2.0 **does not parse responses at all**. It authenticates, routes (rewriting `model`,
endpoint, auth), and streams bytes through, with a **parse-lite tap** that reads only what the
gateway itself needs: SSE event boundaries, the `usage` chunk, `finish_reason`, and error frames.
Wire compatibility on this path is true *by construction* — the gateway cannot corrupt fields it
never parses, and new OpenAI parameters work the day upstream ships them.

Two asymmetries make this workable. First, **requests are always fully parsed** regardless of
path — routing needs `model`, governance needs identity, prompt logs need the messages, and
fallback needs a replayable request object. The request model preserves unknown fields (a
catch-all JSON delta alongside the typed fields), so parsing does not sacrifice compat. This is
the **B-T2 invariant**, and its feasibility is the design's single flagged risk: a spike (BQ-4)
must prove faithful round-tripping in kotlinx.serialization before Phase 1; the recorded
contingency is the official openai-java SDK for the OpenAI-wire side. Second, the **Anthropic
converter is unavoidable in every option anyway**: cross-provider fallback means replaying an
OpenAI-shaped request against Anthropic's Messages API. So the converter investment is made
exactly once, where no alternative exists.

Provider roster for 2.0: **Azure OpenAI, OpenAI, Gemini (via OpenAI-compat) — passthrough;
Anthropic — converter.** Bedrock and the native Gemini API are parked with revisit conditions.

### 2.2 Streaming, honestly this time

The stream representation is **dual**: opaque SSE frames on the passthrough path, normalized
chunks on the converted path. What keeps this from becoming two systems is the rule that **every
consumer — metering, prompt logging, cache population, the client-facing SSE writer — depends only
on a unified tap/`StreamObservation` interface**, never on chunk internals.

The ai-gateway review catalogued precisely how streaming goes wrong in Ktor, and each item is now
a named requirement with a test: server-side, use the SSE plugin or `respondBytesWriter` with
per-event flush (never `respondText` per chunk), send heartbeats, and propagate client disconnect
as cancellation of the upstream call; upstream-side, use `preparePost{}.execute{}` (never a
buffered `body()`), split lines UTF-8-safely across read boundaries, and map upstream HTTP errors
and mid-stream error frames into the error taxonomy rather than swallowing them.

## 3. Routing and resilience

**Model resolution** keeps 1.x's three tiers, because a grep of kantheon proved the first tier is
live contract: callers send `"haiku"`, `"sonnet"`, `"gpt-4"`. Resolution order: alias map →
literal catalog match (an explicitly named but unknown model is an error, never a silent
reroute) → tag soft-match, with the 1.x random-among-ties replaced by a deterministic tie-break
(lowest cost, then name). Namespaced `provider/model` identifiers are accepted additively.
Pythia's contracts anticipate the gateway eventually landing native `(modality, tier, task_kind)`
routing (GI-3) — 2.0 does not implement it, but the catalog schema leaves room for those
dimensions.

**The catalog and rules live in static config files**, changed by redeploy. Bora's call, against
the session's hot-reload lean: models change roughly monthly; reload machinery has no customer.
The schema is deliberately DB-ready (stable IDs, no positional semantics) so a database catalog
with a management API remains a migration, not a redesign — parked until an admin API exists or
the edit cadence rises. A catalog row carries: name, aliases, provider, upstream
deployment/model name, type, tags, pricing, cache TTL, and the fallback chain.

**Retries** are decided by the typed error taxonomy (ported from the experiment, with its
statusCode-propagation bug named and fixed): retryable are rate limits (honoring `Retry-After`),
connect/TTFB timeouts, network errors, and provider 5xx including Anthropic's 529; auth,
validation, context-length, and content-filter errors are not. Backoff is exponential with
jitter, bounded by both an attempt cap and a **wall-clock retry budget** — interactive callers
care about time, not attempts. **Retries and fallbacks happen only before the first token reaches
the client.** After that, the stream ends with an honest error frame; there is no mid-stream
resurrection in 2.0.

**Fallbacks** are static ordered chains declared per logical model in the catalog
(`smart: [azure/gpt-4.1, anthropic/claude-sonnet-4-6]`). The mechanics are the parsed-request
replay described above. A **circuit-breaker-lite** — a per-provider consecutive-failure counter —
lets the engine skip ahead past a provider that is currently failing, deterministically, without
reordering chains. When a replay crosses providers, parameters the target cannot honor are
**stripped and logged**, never silently mangled; and the actually-serving provider and model are
recorded in the prompt log and surfaced in `X-Gateway-Provider`/`X-Gateway-Model` response
headers. A fallback is never invisible.

**Upstream keys**: one per provider in 2.0 — but the provider-call contract takes a `Key`
parameter from day one. The experiment baked keys into client construction, which is why its
fallback sent OpenAI keys to Anthropic; pool-readiness here is an interface property, and
weighted pools/rotation/per-team pinning are parked features, not rewrites.

## 4. Governance

**Two credential planes.** The data plane uses **gateway-issued virtual keys** (`ttrk-…`,
generated with `SecureRandom`, stored as SHA-256 hashes). The gateway validates them itself,
because the decisive infrastructure fact (DQ-1) is that **east-west traffic does not pass Envoy**:
in-cluster services call the gateway Service directly, so Envoy-injected identity headers are
trustworthy only on the ingress path and are never accepted as identity from inside the cluster.
The admin plane (the key API) is gated by Keycloak JWTs, which Envoy validates at ingress (GI-2).

This formalizes rather than invents: Hebe already sends `Authorization: Bearer <gateway key>` from
a per-instance k8s Secret — a key 1.x almost certainly ignores today. 2.0 starts validating what
callers already send.

**The domain model** is the ported ai-gateway/Bifrost design, trimmed to internal scale:
`VirtualKey (n) → Team (1)`, with `Budget` and `RateLimit` attachable to teams and per-key
(min-wins). No Customer tier, no OPA, no Key Vault. **Attribution** is key→team primarily; the
existing `X-Cost-Center` header (GI-1) refines *within* the team and is prefix-validated so a key
cannot charge a foreign bucket; `X-Turn-Ref` is logged for traceability, never aggregated against.

**Budgets are calendar-monthly and money-denominated** (token counts tracked alongside), because
budgets answer "what did this month cost" — billing-shaped. Rate limits are per-key with rolling
windows.

**Enforcement is pre-check + post-settle.** Rate limits live in Redis (atomic INCR/EXPIRE, shared
across replicas) and are checked hard before admission. Budgets live in Postgres and settle at
request/stream end with a single atomic `UPDATE … SET used = used + ?` — the experiment's
read-modify-write races and wrong-table writes are the named anti-pattern here. A budget breach
discovered at settle blocks the *next* request; the overshoot is bounded by concurrent streams ×
max response cost and is documented as accepted. Usage at settle comes from the tap's `usage`
chunk when present, the non-streaming `usage` field otherwise, or a tokenizer estimate flagged
`estimated=true` — never silently absent. Reserve-then-settle was rejected as bookkeeping for a
tightness nobody asked for; fully-async settle was rejected because rate limits cannot work that
way.

**Breach behavior:** rate limits return a hard 429 with `Retry-After` and an OpenAI-shaped
`rate_limit_exceeded` body. Budgets are tiered — alert at 80%, block at 100% — with a per-budget
`hard|soft` flag defaulting to **soft** for internal teams (an agent hitting a hard wall mid-month
over a pricing typo is worse than a bounded overshoot). A blocked budget returns 429 with
OpenAI's own `insufficient_quota` code (stock SDKs handle it) plus an `x-gateway-reason` detail.

**Admin surface:** definitions (teams, budgets, limits) live in config files, consistent with the
catalog decision. The one operation that cannot wait for a redeploy — revoking a leaked key — gets
a **minimal Keycloak-gated key API**: issue, revoke, list. Bora issues keys via CLI/curl; a fuller
admin API/UI is parked.

## 5. API surface

Endpoints: `POST /v1/chat/completions` (the FI-4 surface — full compat including SSE and tool
calls), `GET /v1/models`, `POST /v1/embeddings`, the health triple plus `/health/providers`,
`/metrics`, and `/admin/keys/*`. Not present: the Responses API (no current consumer; parked with
an SDK-drift revisit trigger), the async-jobs endpoints (no consumer found; removal confirmed by
the migration contract diff), the native `/api/v1` alias (dropped after its single consumer,
Kleio, is migrated — a one-line change), and gRPC.

The extension vocabulary, named once: the **`usage.cost` echo** on every response — non-streaming,
the final SSE usage chunk, and cache hits — because Pythia reconciles project-and-reserve costing
from it (GI-3); **dual usage-field names during the migration window** (OpenAI's
`prompt_tokens`/`completion_tokens` plus 1.x's `input_tokens`/`output_tokens`, additively — 1.x
consumers read the latter); `X-Gateway-Provider`/`X-Gateway-Model` on responses;
`X-Gateway-Cache: bypass|refresh` on requests; and inbound `X-Cost-Center`, `X-Turn-Ref`,
`traceparent` honored.

## 6. Caching

The 1.x exact-match Redis cache is ported — it works and its attribution semantics are right —
and made streaming-aware: a cache hit against a `stream: true` request replays as a synthetic
two-event stream (one content chunk, one usage chunk, `cached: true`), which is honest and
SDK-legal. The key is the *logical* model plus a normalized request hash, so a fallback-served
response caches under the name callers actually use; the entry records the actual provider/model.
Cache hits count against request rate limits (they are requests) but do **not** deduct money
budgets (there was no upstream spend); the `usage.cost` echo with `cached=true` lets Pythia decide
how to book the saved bill. TTLs come from the catalog. The semantic cache stays parked until the
exact-match hit rate is measured and found wanting.

## 7. Observability

Prompt logs keep the 1.x Postgres + TSVECTOR scheme — it works and is queried — and gain the
columns the new decisions produce: key id, team, cost-center, turn-ref, actual provider/model,
fallback-from, stripped params, the `estimated` flag, `cached`, TTFB, and duration. Tracing
becomes real: OTel spans around the transport and around *each provider attempt* (so retries and
fallbacks appear as sibling spans), continuing the W3C `traceparent` that Hebe already sends and
1.x drops. Metrics: the Ktor MicrometerMetrics plugin for HTTP-level, plus gateway counters —
tokens and cost per team×provider×model, retries, fallbacks, circuit state, cache hit rate, and
budget-consumption gauges. Health endpoints tell the truth: readiness is config + DB + Redis;
`/health/providers` surfaces circuit state; there are no `try { true }` probes (P-1).

## 8. Migration

2.0.0 replaces 1.x **in place**, behind the same k8s Service, because the consumers already speak
the 2.0 surface: they post OpenAI-shaped requests with static bearer keys and attribution headers.
Three gates precede cutover. The **contract-diff verification** captures real 1.x responses for
each consumer's calls and diffs them against 2.0 in staging — it exists to catch the usage-name
wrinkle, any reader of 1.x's custom `output[]`/`status`/`createdAt` fields, and any undiscovered
async-jobs consumer. A **staging soak** runs Hebe/Kleio/Themis smoke traffic. At cutover the Redis
cache is flushed (entry format changes) and prompt-log schema changes apply additively.

Credentials cut over in two phases: existing static per-instance keys are imported as seeded
virtual keys (hash + team mapping), so day one requires zero caller changes; teams then rotate
onto issued `ttrk-` keys via the key API at leisure. Removed at 2.0.0: the gRPC service and
protos, the `/api/v1` alias (after Kleio), and the async-jobs endpoints with their NATS
infrastructure (pending the contract-diff confirmation).

**Naming:** the "prometheus" alias is dropped entirely — the service is `ttr-llm-gateway`,
ending the permanent collision with the Prometheus metrics stack. The code stays in
`tatrman-server/services/ttr-llm-gateway`.

## 9. The hero scenario, walked

Golem, holding a `ttrk-` key in a stock OpenAI SDK, streams a tool-calling conversation. The
request is parsed once into the unknown-field-preserving model; admission checks the key's Redis
rate limit and reads the team budget; alias resolution routes to Azure; the passthrough handler
rewrites URL/auth/deployment and opens the upstream stream. Azure returns 429s: the typed retry
honors `Retry-After` until the wall-clock budget expires; the chain declared in the catalog
replays the parsed request through the Anthropic converter (two unsupported params stripped and
logged); the client — which has received no tokens yet — sees a single seamless OpenAI-shaped SSE
stream with `X-Gateway-Provider: anthropic`. The tap observes the stream: heartbeats flow, the
final usage chunk carries `usage.cost`, and settle writes actual usage atomically to the team's
Postgres counter. The prompt-log row records fallback-from, cost-center `golem/<id>`, turn-ref,
TTFB. The OTel trace — continued from Golem's `traceparent` — shows the Azure attempts and the
Anthropic success as siblings. Pythia reconciles from the echo. Every clause is a test.

## 10. What 2.0 deliberately is not

Not multi-tenant. Not a Bifrost clone — no MCP, no clustering, no adaptive load balancing, no
semantic cache, no admin UI, no plugin system. Not gRPC. Not hot-reloadable. Each of these is in
the parking lot with a written revisit condition, which is the difference between *deferred* and
*forgotten*.
