# Workstream B — Provider Layer & Streaming: Option Catalogue

> Divergence output, session S2 (2026-07-11). Leans are marked as leans.
> **CONVERGED 2026-07-11 (S3): decisions B-1…B-5 in the control-room log.** Main fork → B-δ hybridized with B-α (passthrough-first + single Anthropic converter); roster = Azure OpenAI/OpenAI/Gemini-via-OpenAI-compat passthrough + Anthropic converter, no Bedrock (resolves BQ-1); B-T1-γ dual stream representation; BQ-4 = mandatory early spike (fallback: B-β for the OpenAI side); BQ-3 → workstream D.
> Companion: [`00-control-room.md`](./00-control-room.md) · [`01-design-space-map.md`](./01-design-space-map.md).

**The question.** Spring AI leaves with Spring (FI-1). What layer produces provider calls in 2.0, and how does a token stream flow upstream → gateway → client SSE, such that FI-4 (full chat-completions compat, incl. streaming + tool calls) holds and P-2 (attribution) survives streaming?

**Evaluation criteria** (from FIs/Ps): FI-4 fidelity (can a stock OpenAI SDK client tell it's not OpenAI?) · tool-call streaming correctness · attribution tap (P-2) · P-1 verifiability (size of the surface we must test against real upstreams) · dependency posture (P-3) · coroutine fit (Ktor world) · Anthropic coverage · effort.

---

## B-α · Hand-rolled clients + converters (the ai-gateway pattern, done right)

Per-provider Ktor `HttpClient`; one internal request/response/chunk model; per-provider request converter, response converter, error converter, SSE parser.

- **Buys:** full control; one internal model that C (routing), D (metering), E (cache), F (logs) all consume uniformly; zero heavyweight deps; converters/error-taxonomy are exactly the FI-6 porting target.
- **Costs:** we own every protocol quirk — OpenAI tool-call *delta fragments* (index-keyed, id-less after the first), Anthropic `content_block_start/delta/stop` + `input_json_delta`, multimodal content parts, `stream_options.include_usage`, UTF-8-safe SSE framing. The ai-gateway review is a catalogue of exactly these mistakes. Highest P-1 test burden: a WireMock conformance suite per provider becomes mandatory, not optional.
- **Prior art:** Bifrost does this in Go (its provider layer is the bulk of the codebase); ai-gateway's converter *shape* is right even though its implementation is wrong.
- **Hero rendering:** every hop is our code: SDK → our transport parse → internal model → Azure converter → 429 → error converter → C fallback → Anthropic converter → normalized chunks → SSE writer + metering tap. Works iff our converters are actually correct — the whole scenario rests on our conformance tests.

## B-β · Official vendor SDKs (openai-java + anthropic-sdk-java)

SDKs own wire protocol, streaming, tool-call assembly (both ship stream accumulators); we adapt SDK types ↔ internal model.

- **Buys:** the hardest 20% (streaming deltas, tool calls, retries at HTTP level) maintained by the vendors, tracked as APIs evolve; openai-java covers Azure OpenAI endpoints too (verify — see BQ-2); P-3 poster child.
- **Costs:** the gateway *receives* OpenAI wire format and would immediately convert into SDK builder objects only to have the SDK re-serialize nearly the same JSON — a decode/re-encode round-trip per request with fidelity risk at the edges (unknown fields, brand-new params the SDK doesn't model yet → we're capped at the SDK's API coverage, which breaks FI-4's "any SDK client" promise whenever upstream adds a field before the Java SDK does). Java-first async (futures/callbacks), needs a coroutine bridge. Two SDK dependency trees in the request path.
- **Prior art:** typical enterprise integration choice; rimdoo/anthropic-kotlin shows the coroutine-bridge wrapper pattern.
- **Hero rendering:** works; tool-call stream assembly comes free from the SDK accumulators. The metering tap reads SDK event objects. The unknown-field fidelity risk doesn't bite Golem (it sends what SDKs send) but bites the *newest* OpenAI features first.

## B-γ · langchain4j

One third-party abstraction over many providers, Spring-free.

- **Buys:** provider breadth for near-zero effort; active community.
- **Costs:** a framework with its own opinionated model of chat/tools/streaming — we'd convert OpenAI-wire → langchain4j model → provider wire, with *two* lossy translations for a gateway whose whole job is fidelity (FI-4). Feature lag and per-provider fidelity variance are outside our control; framework-shaped dependency in the hot path is what FI-1 just removed (P-3 tension, ironically).
- **Prior art:** great for *applications* (agents, RAG); no known gateway uses it as its proxy core — a hint about fit.
- **Hero rendering:** plausible for the happy path; the fidelity and attribution taps depend on what langchain4j exposes per provider; streaming tool-call fidelity per provider would need auditing — our P-1 burden returns through the back door.

## B-δ · Passthrough-first proxy

For OpenAI-wire upstreams (Azure OpenAI, OpenAI, and most of the industry: Groq, OpenRouter, Ollama, Mistral, vLLM…), the gateway does **not** model the payload: authenticate → route (rewrite `model` + endpoint + auth header) → stream bytes through, with a **parse-lite tap** (SSE event boundaries + the `usage` chunk + `finish_reason` + error frames) for metering/logging. Only non-OpenAI-wire providers (Anthropic) get a full converter (which is B-α for one provider).

- **Buys:** FI-4 becomes true *by construction* on the passthrough path — we cannot corrupt fields we never parse; new OpenAI params work the day upstream ships them; the P-1 conformance matrix collapses to (a) the tap, (b) the single Anthropic converter; smallest hot-path code of all options. FI-2 says we don't need to touch bytes for perf reasons either way.
- **Costs:** two code paths (passthrough vs converted) — C/E/F must handle both (cache keys and prompt logs need request parsing anyway — see B-T2: the *request* is parsed once for routing/governance regardless; it's the response we can pass through). Full-fidelity *cross-provider fallback* (OpenAI-wire request replayed against Anthropic) still requires the converter to translate the request — so the converter isn't optional, just single-target. Response-side features that need full parsing (semantic cache, content filtering) would force upgrades from tap to parse — but those are parked/absent.
- **Prior art:** LiteLLM's proxy behaves this way for OpenAI-compatible backends; nginx-class LLM gateways (Kong AI, Cloudflare AI Gateway) are passthrough-with-tap by design.
- **Hero rendering:** Golem's request parsed once (routing + governance + log), streamed from Azure as raw SSE with the tap counting tokens; on 429 before first token, C replays the *parsed* request through the Anthropic converter; the client sees a seamless OpenAI-shaped stream (converter emits OpenAI-wire chunks). Attribution: tap on passthrough, converter on Anthropic. Everything in the scenario works, and the only hard converter is the one we'd need in every option.

## B-ε · Spring AI without Spring Boot (weird option)

Use Spring AI's model clients as plain libraries inside Ktor.

- **Buys:** keeps 1.x's known-working provider code; cheapest migration of provider logic.
- **Costs:** drags spring-core/context/webflux into a Ktor app — violates FI-1's spirit while keeping its letter; Reactor↔coroutine bridge; Spring AI's abstraction (like B-γ) models *applications*, not wire-fidelity proxying — FI-4 streaming compat through Spring AI's `ChatResponse` re-encoding is unproven; upgrade coupling to the Spring release train remains.
- **Verdict-shaped note:** exists to be rejected consciously — it answers "why not keep what works?" with "because what works is an app-framework client, not a gateway core."

---

## Sub-forks

### B-T1 · Internal stream representation
- **α · Normalized-everything:** all providers → one `GatewayStreamChunk`. Required by B-α/β/γ; single consumer interface for SSE writer, gRPC(-gone), logs, metering.
- **β · Opaque-with-tap:** raw SSE events + side-channel `StreamObservation` (tokens, finish, error). Natural for B-δ passthrough.
- **γ · Dual (tap interface unifies):** opaque on passthrough, normalized on converted path; *consumers* (metering, logs, cache replay) depend only on the tap/observation interface, never on chunk internals. — *Lean if B-δ wins the main fork.*

### B-T2 · Parse asymmetry (requests vs responses)
Requests are **always parsed** (routing needs `model`, governance needs identity+estimates, logs need the prompt, fallback needs a replayable form) — full `ChatCompletionRequest` model with **unknown-field preservation** (kotlinx-serialization `JsonObject` passthrough of unmodeled fields, so FI-4 survives our parse). Responses are where the passthrough/convert fork actually lives. This defuses half of B-δ's "two paths" cost: only the response side forks.

### B-T3 · Azure OpenAI specifics
(α) Azure as passthrough target with URL/auth rewrite (deployment-name mapping lives in the C catalog) · (β) Azure via openai-java's Azure support (if B-β) · (γ) dedicated Azure converter (only if evidence of wire divergence beyond auth/URL/api-version shows up). *Lean: α — Azure's chat-completions body is OpenAI-wire; divergence is transport-level.*

### B-T4 · SSE mechanics (both directions) — settled knowledge, not really a fork
Client-side: Ktor server SSE plugin or `respondBytesWriter` with explicit flush; heartbeats; client-disconnect cancels upstream call (structured concurrency). Upstream-side: `preparePost {}.execute {}` streaming body access, UTF-8-safe line framing, `[DONE]`/error-frame handling. (All four are exactly the mistakes catalogued in the ai-gateway review — they go in the design as named requirements with tests.)

### B-T5 · Embeddings path
1.x has `EmbeddingService` (Azure ada-002). In 2.0: same fork, smaller — passthrough for OpenAI-wire `/v1/embeddings` (α) vs SDK (β). Rides the main fork's decision; no independent divergence needed.

---

## Cross-links
- **C:** fallback = request-model replay through a converter → the parsed-request invariant (B-T2) is load-bearing for C. Retry taxonomy consumes the per-provider error converters (all options need error converters — even B-δ must map Anthropic + passthrough error frames to `GatewayError`).
- **D:** metering tap = the `StreamObservation` interface (B-T1-γ); reserve-then-settle needs token counts mid-stream or at stream end.
- **E:** cache replay of a streamed response = synthetic stream from the aggregate; needs either normalized chunks (α) or stored raw SSE (β — cheaper than it sounds: replay the recorded frames).
- **F:** prompt logs need the aggregated completion — tap accumulates text on both paths.
- **A:** if A-β (Responses API) is ever adopted, B-δ passthrough extends naturally (it's OpenAI-wire); B-β would wait on SDK coverage.

## Open questions (BQ-n)
- **BQ-1** · Provider roster for 2.0: is Anthropic the only non-OpenAI-wire target we care about? (Gemini has an OpenAI-compat endpoint; Bedrock does not — if Bedrock ever matters, the converter count grows.)
- **BQ-2** · Verify openai-java's Azure story hands-on (auth modes, api-version handling) before B-β could be chosen.
- **BQ-3** · Does the parse-lite tap get token usage reliably on all passthrough targets (`stream_options.include_usage` support varies) — fallback: tokenizer-estimate + settle from non-stream `usage` when absent? Feeds D's settle design.
- **BQ-4** · Request-model unknown-field preservation: confirm kotlinx.serialization pattern (catch-all `JsonObject` delta) round-trips byte-faithfully enough for FI-4.

## Leans (not decisions)
- **Main fork: B-δ hybridized with B-α** — passthrough responses for OpenAI-wire providers + one hand-rolled Anthropic converter + always-parsed requests (B-T2). Rationale: FI-4 by construction where it matters, smallest P-1 matrix, converter effort spent exactly once where it's unavoidable (cross-provider fallback needs it anyway).
- **B-β remains the strongest challenger** — if BQ-4 (unknown-field fidelity) turns out hard, SDKs beat hand-rolling the OpenAI side.
- B-γ and B-ε: lean reject, both for the same reason — app-framework abstractions in a wire-fidelity proxy.

## To converge we need
1. BQ-1 answered (roster) — sizes the converter bill.
2. BQ-4 spiked (~half a day: round-trip a bleeding-edge OpenAI request through the parse).
3. A decision on B-T1 (falls out of the main fork).
4. C's fallback semantics sketched enough to confirm the request-replay contract.
