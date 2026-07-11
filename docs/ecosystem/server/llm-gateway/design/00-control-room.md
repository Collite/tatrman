# LLM Gateway 2.0 Design — Control Room

> The single dashboard for the **llm-gateway 2.0.0** design effort ("prometheus" successor).
> Open this first every session. Companion doc: [`01-design-space-map.md`](./01-design-space-map.md).
> Method: diverge-then-converge, per the ttr-p reference (`docs/features/ttr-p/design/`).
>
> **Status:** Framing (Phase 0). Started 2026-07-11.

---

## 0. How we run this

Multi-session, exploration-first. **Diverge before converging** — enumerate alternatives per workstream (≥3, incl. the weird one), no decisions during divergence, every decision lands in the append-only log with the rejected alternatives and why. Statuses: ⚪ not started · 🔵 diverging · 🟡 options captured · 🟢 converged · ⏸ parked.

**Hero scenario** (carried through every workstream): *Golem streams a tool-calling conversation through the gateway using the stock OpenAI SDK; the request is attributed to Golem's team budget; mid-conversation the primary Azure deployment starts returning 429s and the gateway falls back to Anthropic; the whole exchange appears in the prompt log and in Pythia's cost attribution.*

---

## 1. Workstream dashboard

| # | Workstream | Status | Core question |
|---|---|---|---|
| **A** | Scope & API surface | ⚪ | Exact endpoint set, compat guarantees (FI-4 fixes ambition), versioning. gRPC dropped (FI-5 as amended). |
| **B** | Provider layer & streaming | 🟢 | **Converged 2026-07-11** → B-1…B-5: passthrough-first + single Anthropic converter; roster Azure/OpenAI/Gemini-compat passthrough + Anthropic; dual stream repr. with unified tap; BQ-4 spike mandatory in planning. See [`02-provider-layer-options.md`](./02-provider-layer-options.md). |
| **C** | Routing & resilience | ⚪ | Rule engine 2.0 (tags/cost routing), retry taxonomy, cross-provider fallback chains, upstream key pools. |
| **D** | Governance & auth | ⚪ | Virtual keys vs/plus OAuth2; budgets & rate limits (enforcement point + atomicity); team/agent attribution → Pythia. |
| **E** | Caching | ⚪ | Keep exact-match Redis, add semantic, or push caching out of the gateway? |
| **F** | Observability & ops | ⚪ | Prompt logs (keep pg+FTS?), OTel spans, token/cost metrics, health/readiness that tells the truth. |
| **G** | Migration & rollout | ⚪ | In-place 2.0.0 vs parallel service; Themis/Golem client migration; proto evolution; keep the name "prometheus"? |

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

## 4. Load-bearing forks

1. **Provider layer replacement** (B): hand-rolled clients / official SDKs / langchain4j / passthrough-first proxy / Spring AI without Spring. Constrains everything: streaming, tool calls, error taxonomy, testing.
2. **Streaming architecture** (B): one internal stream representation feeding both REST SSE and gRPC server-streaming; where aggregation for cache/logs/costing happens.
3. **Auth & identity model** (D): OAuth2-only with per-identity quotas vs virtual keys vs hybrid. Decides whether the Bifrost governance domain model comes in whole or trimmed.
4. **Catalog & rules: static files vs managed state** (C/D): `models.yaml` + `rules.conf` vs DB-backed catalog with a management API.
5. **Migration shape** (G): in-place rewrite behind the same endpoints vs parallel service + cutover. Interacts with FI-5 (proto compat) and the Redis cache/prompt-log continuity.

## 5. Design principles (proposed, pending ratification)

- **P-1 · Claims match runtime.** No capability is "done" until exercised against a live or WireMock upstream in CI — per-stage smoke verification. (Direct lesson from the ai-gateway review: everything type-checked, nothing ran.)
- **P-2 · The gateway owns attribution.** Every token in/out is attributed to an identity (team/agent) and priced — streaming included. Features that would break attribution (unaccounted cache hits, unattributed fallbacks) are bugs by definition.
- **P-3 · Boring dependencies, honest scope.** Prefer maintained SDKs/plugins over hand-rolled protocol code where they fit (FI-2 gives us the slack); where we hand-roll, the unsupported surface is documented as unsupported, never stubbed to return 200.

## 6. Decision log (append-only)

- **2026-07-11 · B-1 · Provider layer = passthrough-first hybrid (B-δ + B-α converter).** Responses from OpenAI-wire upstreams stream through *unparsed* with a parse-lite tap (SSE framing, usage chunk, finish_reason, error frames); requests are always fully parsed into an unknown-field-preserving model (B-T2 invariant — routing/governance/logs/fallback all need it). Non-OpenAI-wire providers get a full hand-rolled converter. **Why:** FI-4 holds by construction on the passthrough path (can't corrupt what we don't parse); smallest P-1 verification matrix; converter effort spent exactly once, where cross-provider fallback makes it unavoidable anyway. **Rejected:** B-α everywhere (we'd own every provider quirk — the ai-gateway failure catalogue); B-β official SDKs (decode→re-encode round-trip; capped at Java SDK field coverage, which breaks FI-4 whenever upstream ships params first; challenger status retained, see B-5); B-γ langchain4j and B-ε Spring-AI-sans-Spring (app-framework abstractions inside a wire-fidelity proxy — two lossy translations).
- **2026-07-11 · B-2 · Provider roster for 2.0: Azure OpenAI + OpenAI (passthrough), Anthropic (the one full converter), Gemini via its OpenAI-compat endpoint (passthrough). No Bedrock.** (Bora) Resolves BQ-1. Gemini rides passthrough — a native Gemini-API converter is parked (revisit if Gemini-only features are needed); Bedrock parked (would mean a second converter).
- **2026-07-11 · B-3 · Stream representation = dual with a unified tap (B-T1-γ).** Opaque frames on the passthrough path, normalized chunks on the converted path; all consumers (metering, prompt logs, cache replay, SSE writer) depend only on the tap/`StreamObservation` interface, never on chunk internals.
- **2026-07-11 · B-4 · Sub-forks ratified with the main fork:** B-T3-α (Azure = passthrough + URL/auth/api-version/deployment rewrite from the catalog); B-T5 (embeddings ride passthrough); B-T4 SSE mechanics adopted as named requirements with tests (server: SSE plugin/`respondBytesWriter`+flush, heartbeats, disconnect-cancels-upstream; upstream: `preparePost{}.execute{}`, UTF-8-safe framing, error-frame handling).
- **2026-07-11 · B-5 · Risk clause:** BQ-4 (unknown-field-preserving request model) is a mandatory early spike in planning; if kotlinx.serialization can't round-trip faithfully, the fallback is B-β for the OpenAI side (challenger preserved, does not reopen B-1's rejection of γ/ε). BQ-3 (usage-tap reliability per passthrough target) transfers to workstream D's settle design.

## 7. Parking lot

| Item | Why parked | Revisit when |
|---|---|---|
| MCP tool surface in the gateway | Bifrost has it; kantheon's MCP surface lives elsewhere (tatrman-server) | An agent needs gateway-mediated MCP |
| Semantic cache | Exact-match cache works and is attributed; semantic adds an embedding dependency + a vector store | Exact-match hit rate proves insufficient (measure first) |
| Admin UI | Management API comes first; UI is a consumer of it | Management API exists and a human needs it weekly |
| Multi-tenant / Tatrman Server product promotion | FI-3 says internal | Tatrman Server needs a gateway component |
| Clustering / adaptive load-balancing | Bifrost perf features; FI-2 | Never, probably |
| gRPC surface (`PrometheusService` or successor) | Zero consumers today (FI-5 amendment); second transport = double streaming/verification/migration cost | A real JVM-internal consumer wants typed streaming; add then as a thin adapter over the engine |
| Gemini native-API converter | Gemini rides its OpenAI-compat endpoint in 2.0 (B-2) | A Gemini-only feature (native context caching, etc.) is needed |
| AWS Bedrock provider | Not in the 2.0 roster (B-2); would require a second full converter | Bedrock workloads materialize |

## 8. Open questions

- ~~**Q-1** · Consumer inventory: who actually calls gRPC `PrometheusService` vs the REST alias today?~~ **Resolved 2026-07-11: nobody.** → FI-5 amended, gRPC dropped from 2.0.
- **Q-2** · Repo/service home tension: the service lives in `tatrman-server` (Apache-2.0 product repo) but is positioned kantheon-internal (FI-3). Does 2.0 stay there, move, or does FI-3 soften later?
- **Q-3** · Pythia contract: what exactly does Pythia consume today (response `usage.cost`? NATS events? prompt-log rows?) — the 2.0 attribution design must not break it.
- **Q-4** · Ingress reality: what does the actual k8s ingress/Envoy do for authn today? (ai-gateway assumed Envoy-injected `X-User-ID`; verify what kantheon actually runs.)
- **Q-5** · Naming: does 2.0 keep "prometheus" and `org.tatrman.llm.v1`, or is the rename part of the migration story (G)?

## 9. Session index

| Date | Session | Gear | Output |
|---|---|---|---|
| 2026-07-11 | S1 — Framing | Framing | Control room + design-space map created; FI-1…FI-6; hero scenario; A–G workstreams; Q-1…Q-5. Preceded by the ai-gateway review (same day) that produced FI-6. Same session: FI-5 amended (gRPC dropped — zero consumers), Q-1 resolved. |
| 2026-07-11 | S2 — B divergence | Divergence | `02-provider-layer-options.md`: B-α…B-ε + sub-forks B-T1…T5; lean = B-δ passthrough + single Anthropic converter (B-β SDKs = challenger); BQ-1…BQ-4. B → 🟡. |
| 2026-07-11 | S3 — B convergence | Convergence | B-1…B-5 decided (passthrough-first hybrid; roster = Azure/OpenAI/Gemini-compat + Anthropic converter, no Bedrock; dual stream repr.; SSE requirements; BQ-4 spike clause). B → 🟢. |
