# Workstream C — Routing & Resilience: Option Catalogue

> Divergence output, session S4 (2026-07-11). Leans are marked as leans.
> **CONVERGED 2026-07-11 (S5): decisions C-1…C-5 in the control-room log.** Leans ratified with one amendment: **C2-α static files (no hot reload — models change ~monthly), DB-ready schema**. CQ-2/CQ-4 resolved in C-4; CQ-3 moot under C2-α.
> Companion: [`00-control-room.md`](./00-control-room.md) · [`02-provider-layer-options.md`](./02-provider-layer-options.md) (B-1…B-5 are inputs here).

**The question.** A request arrives with a `model` string (FI-4: OpenAI-shaped). How does the gateway choose a concrete provider+deployment+key, and what happens when that choice fails — retry, fall back, or surface the error?

**Grounding bonus (feeds D, recorded here where found):** Hebe's `GatewayClient` (kantheon, "P2 Stage 2.2; contracts §5.2", PD-11) already sends an attribution header contract on every gateway call: `Authorization: Bearer <gateway key>` + **`X-Cost-Center: hebe/<instance_id>`** + **`X-Turn-Ref: <turn/job id>`**, explicitly not depending on a response echo. It's an `OpenAiCompatProvider` pointed at the gateway (FI-4's shape already assumed by consumers) with client-side retries (3×, jitter). → **An ecosystem cost-attribution identity scheme already exists**; workstream D must build on `X-Cost-Center`/`X-Turn-Ref` + gateway keys, not invent a parallel one. Registered as GI-1 in the control room.

**Grounding — what 1.x actually does** (`RuleEngine.kt`, `rules.conf`, `models.yaml`): three-tier resolution — (1) alias map from `rules.conf` (`"gpt-4" → gpt-4o`, `"fast" → gpt-4o-mini`), (2) strict literal match against the `models.yaml` catalog (unknown explicit name → null → error), (3) tag soft-match (`requiredTags` scored, max-score wins, random among ties), then first-model fallback. `reloadRules(path)` exists (hot reload). Catalog rows carry `name/fullName/provider/type/tags/cost`. No retries, no fallback chains, single key per provider. Non-streaming tool calls already pass through (Spring AI). **Semantics worth preserving: aliases and tag-routing are used features; strict-unknown-name is a good default.**

---

## C1 · What does the `model` string mean? (semantics fork)

- **C1-α · Literal only.** `model` = catalog name; unknown → 404-shaped error. Buys: zero magic, OpenAI-est behavior. Costs: loses 1.x's alias + tag routing (regression for Themis/Golem if they use tags — CQ-3).
- **C1-β · Three-tier, 1.x-compatible** (alias → literal → tag). Buys: continuity; `model: "fast"` keeps working. Costs: tag tier's `random among ties` is surprising under attribution (P-2 fine — actual model recorded) but non-deterministic for debugging.
- **C1-γ · Namespaced logical models** (Bifrost/LiteLLM-style `provider/model` or pure logical names like `smart`, mapped in catalog). Buys: explicit contract, deployment names never leak to callers. Costs: migration for existing callers sending bare names.
- **C1-δ · Weird: no routing at all** — `model` = `provider/deployment` literal, gateway is a dumb metered pipe. Named to be rejected: throws away the working rule engine and the fallback story.

*Lean: C1-β with determinism fix (ties broken by `cost` then name, not random), plus C1-γ's namespaced form accepted additively — the catalog can hold both bare and namespaced names.*

## C2 · Where do catalog + rules live? (state fork)

- **C2-α · Static files, redeploy to change** (1.x minus reload). Simplest; ops-hostile mid-incident.
- **C2-β · DB-backed catalog + management API** (Bifrost-shape). Buys: runtime edits, admin UI later, one store shared with D's governance entities. Costs: management API + authz + migration scope now; a DB read in the hot path (cacheable); FI-3 says the editor population is ~Bora.
- **C2-γ · Policy engine (OPA/CEL) evaluates routing.** Weird option: rules as policy code, hot-swappable, auditable. Costs: opaque failure modes in the hot path, new operational dependency, overkill for the rule complexity we actually have (aliases + tags + chains).
- **C2-δ · Files as source of truth + hot reload** (k8s ConfigMap + watch/SIGHUP + validate-then-atomic-swap; 1.x's `reloadRules` matured). Buys: GitOps-friendly (catalog changes are commits), zero new infra, mid-incident editable via `kubectl edit cm`. Costs: no runtime API for future UI; multi-replica consistency is eventual (ConfigMap propagation).

*Lean: C2-δ for 2.0, with the catalog schema designed DB-ready (stable IDs, no file-position semantics) so C2-β is a later migration, not a redesign. Cross-link: if D converges on virtual keys, D needs a DB + admin API anyway — revisit whether the catalog joins it then (parking-lot entry rather than deciding now).*

## C3 · Retry policy (C-T2)

Mostly a port-and-fix of the FI-6 taxonomy; the genuine forks:

- **C3-α · Retry matrix:** typed `GatewayError` categories; retryable = RATE_LIMIT (honoring `Retry-After`), TIMEOUT (connect/TTFB only), NETWORK, PROVIDER_5XX (incl. Anthropic 529 `overloaded`). Non-retryable: AUTH, VALIDATION, CONTEXT_LENGTH, CONTENT_FILTER. Exponential backoff + jitter, per-provider caps.
- **C3-T1 · Streaming constraint:** retries (and fallbacks) only **before first token is emitted to the client**; after that, the stream fails honestly (error SSE event + close). No mid-stream resurrection in 2.0 (would need response replay/dedup semantics nobody asked for).
- **C3-T2 · Retry budget shape:** (α) per-request attempt cap only; (β) attempt cap + wall-clock budget (e.g. never spend >Xs retrying before fallback). *Lean: β — bounded time matters more than bounded attempts for interactive callers.*

## C4 · Fallback chains (C-T3)

- **C4-α · None.** Fail fast, callers own resilience. Buys: simplest, honest. Costs: hero scenario dies; every consumer reimplements fallback badly.
- **C4-β · Static chains in the catalog.** A logical model/alias lists an ordered chain: `smart: [azure/gpt-4.1, anthropic/claude-sonnet-4-6]`. Trigger: retryable-and-exhausted or non-retryable-but-chain-eligible (429/5xx/timeout — not AUTH/VALIDATION). Mechanics per B: replay the **parsed request** (B-T2) through the next entry's path (passthrough rewrite or Anthropic converter); response streams to client in OpenAI wire shape either way; actual provider recorded for attribution (P-2) and surfaced in a response header + prompt log, never silently.
- **C4-γ · Dynamic: health-scored/circuit-breaker choice.** Chain order adjusted by rolling error rates; skip open-circuit providers. Buys: faster incident response. Costs: emergent behavior, harder to reason about attribution/cost mid-incident; needs the health model first.
- **C4-δ · Weird: client-directed fallback** — caller sends `fallbacks: [...]` in the request (OpenAI-compat extension field). Interesting (self-serve), but pushes routing policy to callers and pollutes the compat surface.

*Lean: C4-β for 2.0, with a **circuit-breaker-lite** on top: consecutive-failure counter per provider that short-circuits the chain head (skip-ahead, not reorder) — deterministic, explainable, feeds F-T4 health. Full C4-γ parked.*

**Param-fidelity sub-question (CQ-2):** replaying an OpenAI-shaped request against Anthropic mid-chain — `max_tokens` semantics, temperature range, unsupported params (`logit_bias`, `n>1`). Options: strip-and-log unsupported params (α) vs reject chain-entry as ineligible when params don't map (β). *Lean: α with the stripped-param list recorded in the prompt log.*

## C5 · Upstream key pools (C-T4)

- **C5-α · Single key per provider** (1.x). Fits FI-3 today.
- **C5-β · Weighted pool + rotation on 429/401** (Bifrost). Buys: quota headroom, incident tolerance. Costs: real state (per-key health), config surface, testing burden — for an internal deployment with ~2 upstream accounts.
- **C5-γ · Per-team key pinning** (team's traffic → team's upstream account for hard billing separation). Interacts with D; only meaningful if kantheon ever needs upstream-bill separation per team.

*Lean: C5-α for 2.0, but the provider-call contract takes a `Key` parameter from day one (the ai-gateway review showed what happens when providers bake the key in at construction — fallback/rotation become impossible). Pool-readiness is an interface property, not a feature.*

---

## Cross-links
- **B:** C4-β's replay contract is exactly B-T2's parsed request + B-1's converter; C3's matrix consumes B's per-provider error converters; C3-T1 aligns with the tap's "first token" event.
- **D:** governance check runs *before* routing (identity/budget), but attribution settles *after* (actual provider/model used — fallback may change cost). C2-β's admin API question merges into D's. C5-γ pinning is a D feature wearing C clothes.
- **E:** cache key = post-routing concrete model or pre-routing logical name? (If logical, a fallback-served response caches under the logical key — probably right for hit-rate, must record actual provider in the entry.) → carry to E.
- **F:** retry/fallback/circuit counters and the "actual provider" field are F metrics/log requirements; C4-β's surfacing header is an F-T1 log field + response header to name in A.

## Open questions (CQ-n)
- ~~**CQ-1** · Do Themis/Golem actually use tag-routing (`requiredTags`) and aliases today?~~ **Resolved 2026-07-11 (grep of kantheon/olymp):** callers send alias/short names — `model = "haiku"` (6×), `"sonnet"` (2×), `"gpt-4o"`, `"gpt-4"`. **Zero hits for `requiredTags`** anywhere outside the gateway itself. → **Aliases are live contract; tag-routing is a vestige** (engine feature no caller exercises). C1-β's tag tier can be kept as catalog capability or dropped without breaking anyone — convergence call.
- **CQ-2** · Param-fidelity policy on cross-provider fallback (strip-and-log vs chain-ineligible) — small fork, decide at convergence.
- **CQ-3** · Multi-replica reload semantics: is eventual consistency of ConfigMap reload acceptable (two replicas briefly routing differently), or do we need a version-gate? (Probably acceptable internally — confirm.)
- **CQ-4** · Where is the chain declared — per logical model in the catalog (lean) or per rule in `rules.conf`? Cosmetic but affects the catalog schema.

## Leans (not decisions)
C1-β + namespaced names additive, deterministic tie-break · C2-δ files+hot-reload, DB-ready schema · C3 matrix with wall-clock retry budget, before-first-token only · C4-β static chains + circuit-breaker-lite, strip-and-log params · C5-α single key behind a pool-ready interface.

## To converge we need
1. CQ-1 answered (grep kantheon callers — mechanical).
2. E's cache-key question acknowledged (doesn't block C, but the "actual provider recorded" invariant should be ratified here).
3. Your call on CQ-2/CQ-4 (one-liners at convergence).
