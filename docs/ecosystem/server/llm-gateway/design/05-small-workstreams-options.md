# Workstreams A · E · F · G — Small-Workstreams Sweep: Option Catalogue

> Divergence output, session S8 (2026-07-11) — one compact pass over the four remaining workstreams.
> Leans marked; convergence recorded in the control-room log.

---

## A · Scope & API surface

**A1 — endpoint set:**
- **α · Chat-completions core:** `/v1/chat/completions` (+SSE) · `/v1/models` · `/v1/embeddings` · health (`/health`, `/health/live`, `/health/ready`) · `/metrics` · D-5's key API (`/admin/keys/*`, Keycloak-gated).
- **β · α + Responses API** — deferred-shaped: no current consumer; SDK default drift is the revisit trigger.
- **γ · α + 1.x async-jobs carryover** — grep found no consumer of the NATS async/webhook job endpoints; carry only if the G contract diff proves otherwise.
- **δ · Bifrost-everything** (audio/images/batches) — reject.

*Lean: α; Responses API and async jobs to the parking lot (async removal confirmed by the G contract-diff gate).*

**A2 — paths & aliases:** OpenAI compat fixes `/v1/*`. The native `/api/v1/chat/completions` alias has **one live consumer (Kleio)** — options: keep alias (α) vs migrate Kleio and drop (β). *Lean: β — one client, one-line change, one surface.*

**A3 — extension vocabulary (named once, used everywhere):**
- `usage.cost` echo (Pythia contract, GI-3) — kept on all responses incl. final SSE usage chunk and cache hits.
- **Usage-field compat wrinkle:** 1.x emits `input_tokens/output_tokens` (Responses-style); OpenAI chat-completions uses `prompt_tokens/completion_tokens`. *Lean:* 2.0 emits the OpenAI names **plus** the 1.x names as additive duplicates during migration (removal = later major).
- Actual-serving surface (C-4): response headers `X-Gateway-Provider` + `X-Gateway-Model`; same fields in the prompt log.
- Error vocabulary: rate limit → HTTP 429 `type: rate_limit_exceeded` + `Retry-After`; budget breach → HTTP 429 `type: insufficient_quota` (OpenAI's own quota code — stock SDKs handle it) with `x-gateway-reason: budget_exceeded` detail; both OpenAI-shaped bodies.
- Cache control: request header `X-Gateway-Cache: bypass|refresh` (E).

**A4 — versioning:** service = semver 2.0.0; wire surface unversioned beyond `/v1` (OpenAI's namespace, not ours); extension headers/fields documented in one compat page.

## E · Caching

- **E-α · Port 1.x exact-match Redis cache** + make it streaming-aware: cache the aggregated completion; a hit on `stream: true` replays as a **synthetic two-event stream** (one content chunk + one usage chunk, `cached: true`) — honest, SDK-legal.
- E-β semantic cache — stays parked (measure exact-match hit rate first).
- E-γ no cache — reject (working, attributed feature).

**Sub-decisions:** cache key = **logical model** (pre-routing) + normalized request hash; entry records actual provider/model (C-4 invariant). Cache hits: **do count** against request rate limits (they're requests), **do not deduct** money budgets (no upstream spend — P-2 attribution via `usage.cost` echo + `cached=true` lets Pythia decide how to book it). TTL per model in the catalog (C-2 schema). Bypass/refresh via `X-Gateway-Cache`.

*Lean: E-α with all sub-decisions as stated.*

## F · Observability & ops

- **F-T1 prompt logs:** keep Postgres + TSVECTOR scheme; new columns: virtual-key id, team, cost-center, turn-ref, actual provider/model, fallback-from, stripped-params, `estimated` usage flag, `cached`, stream duration + TTFB. (β OpenSearch / γ OTel-logs-only rejected: working system, no new infra.)
- **F-T2 tracing:** real OTel spans — transport span → per-attempt provider spans (retry/fallback visible as siblings); **continue the W3C `traceparent` Hebe already sends** (GI-1's plugin injects it today — the gateway currently drops it). OTLP export as 1.x ecosystem does.
- **F-T3 metrics:** Ktor MicrometerMetrics plugin (HTTP-level) + gateway counters: tokens/cost per team×provider×model, retries, fallbacks, circuit state, cache hit rate, budget-consumption gauges.
- **F-T4 health:** `/health/ready` = config parsed + DB + Redis reachable; per-provider health = circuit-breaker state (C-4) surfaced in `/health/providers` — **no fake probes, no `try { true }`** (ai-gateway lesson, P-1).

*Lean: all four as stated — F is ratification more than divergence.*

## G · Migration & rollout

- **G1 shape:** **α in-place** (same k8s Service, same endpoints; 2.0.0 replaces 1.x behind the Service) — *lean*, gated by: (1) a **contract-diff verification** (capture real 1.x responses for each consumer's calls, diff against 2.0 in staging — catches the usage-name wrinkle and any `output[]`/`status` field readers), (2) staging soak with Hebe/Kleio/Themis smoke calls, (3) Redis cache flush at cutover (entry format changes), prompt-log table migration additive. β parallel service / γ strangler rejected: double-ops cost buys little when consumers already speak the 2.0 surface (OpenAI-compat + static keys — they're already sending both).
- **G2 removals at 2.0.0:** gRPC `PrometheusService` (FI-5 amendment; proto files deleted, changelog entry), `/api/v1` alias (after Kleio migrates — A2), async-jobs endpoints (pending contract-diff confirmation), NATS job infra if so.
- **G3 credential cutover:** phase 1 — 2.0 *accepts* existing static keys as seeded virtual keys (imported hashes, mapped to teams); phase 2 — Bora rotates teams onto issued `ttrk-` keys via the key API at leisure. No caller changes required on day one (GI-1 headers already flow).
- **G4 (= Q-5) service name:** the "prometheus" name collides permanently with the Prometheus metrics stack (a gateway that *exposes* `/metrics` *to* Prometheus, named prometheus). Options: keep (continuity, k8s Service name unchanged) · rename (candidates in the tatrman/kantheon naming canon — e.g. keep repo-module name `ttr-llm-gateway` and drop the mythological alias entirely) · rename-at-cutover (2.0 is the natural moment). **Bora's call.**
- **G5 (= Q-2) repo home:** stays in `tatrman-server/services/ttr-llm-gateway` (α — it's there, Apache-2.0 is fine for generic gateway infra even if kantheon-internal in positioning) vs move to kantheon (β — matches FI-3 positioning; but SV-P0 just moved services *out*). **Bora's call.**

---

## Cross-links & notes
- A3's error vocabulary is what D-6 emits; A3's headers are what C-4 surfaces; E's synthetic replay uses B-3's tap-consumer interface; F-T1's new columns are exactly the D/C decision outputs.
- The G1 contract-diff gate is P-1 applied to migration.

## Open (sweep-scoped)
- **SQ-1** · Kleio migration PR (one line) before `/api/v1` alias removal — G2 sequencing item, not a design question.
- **SQ-2** · Contract-diff findings may resurrect the async-jobs carryover (A1-γ) — bounded reopen clause.
