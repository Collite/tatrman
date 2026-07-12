# LLM Gateway 2.0 — Design-Space Map

> The option tree. One section per workstream: **Question → Branches → Cross-links → Open**.
> Deliberately divergent: branches are added, never silently removed. Leans are marked; they are not decisions.
> Companion: [`00-control-room.md`](./00-control-room.md).

---

## A · Scope & API surface

**Question.** Which endpoints does 2.0 serve, with what compat guarantees, and how does the gRPC surface evolve alongside?

**Fixed by framing:** full OpenAI chat-completions wire compat (FI-4); **REST/SSE-only — gRPC dropped** (FI-5 as amended 2026-07-11: zero consumers).

**Branches — endpoint set:**
- **A-α · Chat-completions core.** `/v1/chat/completions` (+streaming) + `/v1/models` + health/metrics. Embeddings kept as today (`EmbeddingService` exists in 1.x). Nothing else.
- **A-β · α + Responses API.** Add OpenAI Responses API (`/v1/responses`) — newer SDK default, conversation state. Cost: a second, stateful wire format.
- **A-γ · α + async jobs carried over.** 1.x's NATS async/webhook jobs re-exposed in 2.0 (they exist and have consumers?).
- **A-δ · Everything Bifrost serves** (audio, images, batches). Weird option — named to be rejected consciously.

**Branches — gRPC evolution:** ~~A-ε freeze v1 proto · A-ζ v2 proto with streaming parity · A-η transcoding layer~~ — **branch dissolved 2026-07-11** by FI-5 amendment (zero consumers; gRPC → parking lot). Kept struck-through for the record.

**Cross-links:** endpoint set drives B (which provider ops must exist); Responses API choice affects E (caching stateful conversations) and D (attribution per conversation).

**Open:** ~~Q-1 gates the gRPC branches~~ resolved — nobody uses gRPC. Remaining: whether the `PrometheusService` proto removal is a 2.0.0 changelog item or the proto lingers deprecated (G decides).

---

## B · Provider layer & streaming — *the de-Spring fork*

> **CONVERGED 2026-07-11** → decisions B-1…B-5 (control room §6); full catalogue in `02-provider-layer-options.md`. Passthrough-first + single Anthropic converter; roster Azure/OpenAI/Gemini-compat + Anthropic; no Bedrock.

**Question.** Spring AI leaves with Spring. What produces provider calls in 2.0, and how does a token stream flow upstream→gateway→client?

**Branches — provider abstraction:**
- **B-α · Hand-rolled clients + converters** (ai-gateway pattern, done right): per-provider Ktor client, request/response converters to one internal model, per-provider error converter. Buys: full control, one internal model, no dependency risk, converters are the porting target from FI-6. Costs: we own every provider quirk (tool-call deltas, SSE framing, multimodal); highest test burden — needs P-1 discipline.
- **B-β · Official vendor SDKs** (`openai-java`, `anthropic-java`): SDKs own wire/protocol/retry quirks; we adapt SDK types to the internal model. Buys: correctness of the hard parts (streaming deltas, tools) maintained by vendors. Costs: two heavyweight deps with different async models; adapter layer still needed; Azure via openai-java config.
- **B-γ · langchain4j**: one abstraction, many providers, works without Spring. Buys: breadth cheaply. Costs: framework-shaped dependency with its own model of the world (the thing we just escaped); streaming/tool fidelity varies per provider; another community's release cadence in the request path.
- **B-δ · Passthrough-first proxy**: for OpenAI-wire upstreams (Azure OpenAI, OpenAI, Groq/OpenRouter/Ollama…), the gateway does not model the payload — it authenticates, routes, meters, and streams bytes; only non-OpenAI-wire providers (Anthropic) get a converter. Buys: FI-4 compat becomes trivially true for passthrough targets (we can't corrupt what we don't parse); huge surface-area reduction. Costs: metering/logging needs a parse-lite tap (usage extraction from the stream); per-request transforms (routing rewrite of `model`, cache keys) need partial parsing anyway; two code paths (passthrough vs converted).
- **B-ε · Spring AI without Spring Boot** (weird option): Spring AI's model clients used as plain libraries inside Ktor. Buys: keeps 1.x's working provider code. Costs: drags spring-core/context into a Ktor app — violates the spirit of FI-1; awkward reactive bridge.

*Lean: B-δ hybridized with B-α — passthrough for OpenAI-wire providers with a metering tap, hand-rolled converter for Anthropic only. FI-2 (no perf goal) and FI-4 both push this way; it also shrinks the P-1 verification matrix.*

**Branches — internal stream representation:**
- **B-T1-α** · normalized chunk model (every provider's deltas converted to one `GatewayStreamChunk`) — required wherever converters run.
- **B-T1-β** · opaque byte/SSE-event stream with a side-channel parse for usage/finish — natural fit for B-δ passthrough.
- **B-T1-γ** · dual: opaque on the passthrough path, normalized on the converted path; gRPC streaming and prompt-logging consume a unified tap interface.

**Branches — where aggregation happens** (cache write, prompt log, cost): tee-and-aggregate in the gateway as chunks flow (α); client-side none/log-only (β); post-stream reconstruct from provider `usage` chunk (`stream_options.include_usage`) (γ).

**Cross-links:** B decides A-ζ feasibility (gRPC streaming), C's fallback mechanics (can you fall back mid-stream? — probably only before first token), D's token metering, E's cache population on streamed responses, F's log completeness.

**Open:** does Anthropic remain the only non-OpenAI-wire provider we care about (Gemini? Bedrock?) — sizes the converter investment.

---

## C · Routing & resilience

> **CONVERGED 2026-07-11** → decisions C-1…C-5 (control room §6); catalogue in `03-routing-resilience-options.md`. Three-tier routing, static files + DB-ready schema, typed retries, catalog-declared chains + circuit-breaker-lite, pool-ready key interface.

**Question.** How does a request choose a model/provider/key, and what happens when the choice fails?

**Branches — routing/rules:**
- **C-α · Keep HOCON `rules.conf` + `models.yaml`** as-is, port the engine. Buys: zero migration, known behavior. Costs: static; ops edits = redeploy.
- **C-β · DB-backed catalog + management API** (Bifrost-style): models, pricing, tags, rules in Postgres, editable at runtime. Costs: management API + authz becomes real scope (feeds D).
- **C-γ · Policy engine** (OPA/embedded CEL) evaluating routing decisions. Weird option; powerful, opaque.
- **C-δ · Files as source of truth, hot-reloaded** (watch + validate + atomic swap) — middle path.

**Branches — resilience:**
- **C-T2** retry taxonomy: typed `GatewayError` categories with per-category retryability + `Retry-After` honoring (port-of-record from FI-6, with the statusCode-propagation bug fixed).
- **C-T3** fallbacks: (α) none — fail fast, callers decide; (β) static chains per model-tag (`smart → [azure/gpt-4.1, anthropic/claude-sonnet]`); (γ) dynamic by health score/circuit breaker. Mid-stream fallback restricted to before-first-token in all options.
- **C-T4** upstream key pools: (α) single key per provider (today); (β) weighted pool + rotation on 429/401 (Bifrost); (γ) pool + per-team key pinning (interacts with D attribution).

**Cross-links:** C-β merges with D's admin surface; C-T3 needs B's error taxonomy; hero scenario exercises C-T2+T3 (Azure 429 → Anthropic).

---

## D · Governance & auth

> **CONVERGED 2026-07-11** → decisions D-1…D-6 (control room §6); catalogue in `04-governance-options.md`. Dual credentials (gateway-validated virtual keys + Keycloak admin), key→team attribution, calendar-monthly money budgets, pre-check/post-settle, minimal key API, soft-default breach.

**Question.** Who is the caller, what may they spend, and how is it enforced and attributed?

**Branches — identity:**
- **D-α · OAuth2-only** (today's model): every caller is a service identity; quotas keyed on JWT claims. Buys: no new key infrastructure, fits FI-3 (internal). Costs: agents/experiments share service identities; no per-key revocation or per-project budget without claim gymnastics.
- **D-β · Virtual keys under OAuth2**: gateway-issued keys (`ttrk-…`, hashed at rest, SecureRandom) for agents/projects, carried in `Authorization: Bearer` per OpenAI SDK convention; OAuth2 remains for service-to-service and the admin API. The Bifrost/ai-gateway domain model (VirtualKey→Team→Budget/RateLimit), enforced. Buys: per-agent budgets, revocation, OpenAI-SDK-native auth (FI-4 synergy — SDKs already send a bearer key). Costs: key lifecycle + admin API + storage become real scope.
- **D-γ · Full Bifrost governance** incl. Customers, OPA policy, Key Vault integration. Weird-for-internal option: most of it is multi-tenant machinery FI-3 says we don't need.

*Lean: D-β trimmed — VirtualKey + Team + Budget + RateLimit, no Customer level, no OPA, enforcement as atomic SQL counters in the request path; admin API OAuth2-protected.*

**Branches — enforcement point:** (α) middleware pre-flight check + post-flight atomic deduct; (β) reserve-then-settle (pre-deduct estimate, settle on actual usage — handles streaming); (γ) async settle from prompt-log events (never blocks, can overshoot).

**Cross-links:** D-β's admin API merges with C-β's catalog API; token settlement needs B's usage tap; attribution contract = Q-3 (Pythia).

**Open:** Q-4 (what does Envoy/ingress actually inject today); budget reset windows (calendar-aligned?) — decide when converging.

---

## E · Caching

**Question.** What does the gateway cache, and where?

**Branches:**
- **E-α · Keep 1.x exact-match Redis cache** (works, attributed, `cached=true` flag). Port as-is; extend to streamed responses by caching the aggregate and replaying as a synthetic stream.
- **E-β · α + semantic cache** (embedding + vector store). Parked by default (see parking lot): adds an embedding dependency and a vector store to the request path; measure exact-match hit rate first.
- **E-γ · No gateway cache** — callers cache. Weird option: simplifies attribution and streaming, throws away a working feature.

*Lean: E-α including the synthetic-stream replay question.*

**Cross-links:** stream replay design depends on B-T1; cache-hit attribution is P-2 territory (1.x already preserves `usage.cost` on hits — keep).

---

## F · Observability & ops

**Question.** What can we see, and does health tell the truth?

**Branches:**
- **F-T1** prompt logs: (α) keep Postgres+TSVECTOR FTS scheme, add streaming aggregates; (β) move to OpenSearch; (γ) OTel logs only. *Lean: α — it works and is queried today.*
- **F-T2** tracing: (α) OTel spans around provider calls + transport (real ones, unlike the ai-gateway ornament); (β) metrics-only. 
- **F-T3** metrics: token/cost counters per provider/model/team (P-2), retry/fallback counters, Ktor MicrometerMetrics plugin for HTTP-level.
- **F-T4** health: readiness = config loaded + DB/Redis reachable; provider health = real probe or last-N-errors circuit state (feeds C-T3-γ), never `try { true }`.

**Cross-links:** F-T1 streaming aggregates depend on B's tap; F-T4 feeds C fallbacks; Pythia (Q-3) consumes F outputs or D settlement events.

---

## G · Migration & rollout

**Question.** How does 2.0.0 land without breaking Themis/Golem/kantheon?

**Branches:**
- **G-α · In-place rewrite**: same service name, same endpoints, same proto; 2.0.0 replaces 1.x behind the k8s Service; REST alias + gRPC kept bit-compatible. Buys: zero client work for existing features. Costs: big-bang cutover; streaming/virtual-key features arrive as additive surface.
- **G-β · Parallel service + gradual cutover**: deploy 2.0 alongside (new name or `-v2`), move consumers one by one, retire 1.x. Buys: safety, A/B. Costs: double ops, double config, cache/log split during transition.
- **G-γ · Strangler**: 2.0 fronts 1.x, taking over endpoint-by-endpoint (streaming first, since 1.x lacks it). Weird-but-plausible: 2.0 is a gateway — it can gateway its predecessor.

**Cross-links:** FI-5 constrains all branches (proto compat); Q-5 (naming) decided here; Redis cache key compat and prompt-log schema continuity matter for α/γ.

**Open:** deployment window realities (kantheon release cadence); whether 1.x consumers use any response field 2.0's OpenAI-shaped responses would drop (needs a contract diff before converging).
