# Workstream D — Governance & Auth: Option Catalogue

> Divergence output, session S6 (2026-07-11). Leans are marked as leans.
> **CONVERGED 2026-07-11 (S7): decisions D-1…D-6 in the control-room log.** DQ-1: east-west traffic bypasses Envoy → the gateway validates data-plane credentials itself; DQ-4: Bora issues keys (CLI/curl against the minimal key API). D-5/D-6 lean-ratified, ⚑ sweep items S-1/S-2.
> Companion: [`00-control-room.md`](./00-control-room.md). Inputs: GI-1 (attribution headers), GI-2 (Envoy JWT), FI-3 (internal), FI-6 (port the governance domain model), P-2 (gateway owns attribution), B-3 (usage tap), C-4 (fallback changes actual cost).

**The question.** Who is the caller, what may they spend, how is it enforced without breaking streaming, and how does every token land in Pythia?

**Grounding:**
- **GI-2 · Envoy validates JWTs at ingress and injects the Keycloak user id downstream** (Bora, resolves Q-4). Open sub-fact (DQ-1): does *east-west* service→gateway traffic also pass Envoy, or hit the gateway directly? 1.x is itself an OAuth2 resource server (validates tokens locally) — suggesting direct in-cluster calls exist.
- **GI-1 · Hebe already sends** `Authorization: Bearer <gateway key>` + `X-Cost-Center: hebe/<instance_id>` + `X-Turn-Ref`. Note the phrase *gateway key* — the ecosystem already talks as if gateway-issued keys exist (DQ-2: what is that credential actually, today?).
- **1.x**: OAuth2 only; no budgets, no rate limits; `usage.cost` preserved on cache hits for Pythia.
- **FI-4 synergy**: stock OpenAI SDKs authenticate with a static bearer key — a long-lived gateway key is the natural SDK credential; Keycloak token refresh inside an OpenAI SDK is awkward.

---

## D1 · Credential & identity model

- **D1-α · Keycloak-only.** Every caller is a Keycloak client/service account; identity = Envoy-injected user id (north-south) or locally validated JWT (east-west). No new credential type. *Buys:* zero new infrastructure, one IdP. *Costs:* agents/experiments share service identities (no per-project revocation/budget without claim gymnastics); OpenAI SDK callers must run token-refresh side-machinery (violates the "stock SDK" hero).
- **D1-β · Virtual keys + Keycloak, dual-accepted.** Gateway-issued keys (`ttrk-…`, SecureRandom, hashed at rest — the FI-6 domain model trimmed to VirtualKey→Team + Budget + RateLimit) for agents/SDKs/projects; Keycloak JWTs accepted on the same endpoints for services that already have them; Keycloak-only for the admin surface. *Buys:* per-key budget/revocation, SDK-native auth, formalizes GI-1's de-facto "gateway key". *Costs:* key lifecycle (issue/rotate/revoke) becomes real scope; two accepted credential forms on the data plane.
- **D1-γ · Full Bifrost governance** (Customers, OPA policies, Key Vault). Named to reject: multi-tenant machinery FI-3 explicitly doesn't need.
- **D1-δ · Weird: no app-level auth at all** — trust the mesh (mTLS/NetworkPolicy), identity purely from headers. *Buys:* least code. *Costs:* every pod that can reach the Service spends money anonymously; P-2 dies. Exists to be rejected consciously.

*Lean: D1-β. It's what GI-1 callers already think they're doing.*

## D2 · What carries attribution (identity → cost bucket)

- **D2-α · Key → Team is primary**; `X-Cost-Center` *refines* within the team (sub-bucket, e.g. `hebe/<instance>`); `X-Turn-Ref` is trace-only (logged, never aggregated against). A key cannot claim another team's cost center (validated prefix).
- **D2-β · Header-only attribution** (today's de-facto): trust `X-Cost-Center` entirely. *Costs:* any caller can charge any bucket — fine at current trust level, breaks the moment budgets have teeth.
- **D2-γ · Claims-based**: cost center from JWT claims. Couples Keycloak config to billing structure; awkward for virtual keys.

*Lean: D2-α — backward-compatible with GI-1 (headers keep working, they just get validated).*

## D3 · Budget & rate-limit model

- **Entities (port of FI-6, trimmed):** `Team` (the budget owner) ← `VirtualKey` (n:1). `Budget` attachable to team *and* optionally per-key (min wins). `RateLimit` per key (and optionally per team).
- **D3-T1 windows:** (α) rolling (`PT1H`-style) · (β) calendar-aligned (monthly budgets matching billing, daily/minute rate windows) · (γ) both, per limit. *Lean: γ with calendar-monthly as the budget default — budgets answer "this month's spend", billing-shaped.*
- **D3-T2 currency:** (α) tokens · (β) money (catalog `cost`/pricing per model — 1.x already carries `cost`, Pythia thinks in money) · (γ) track both, limit on money, expose both. *Lean: γ.*

## D4 · Enforcement mechanics (the streaming problem)

A streamed response's true cost is unknown until the stream ends (and the tap may lack `usage` on some passthrough targets — BQ-3 inherited).

- **D4-α · Pre-check + post-settle:** admission check against the *current* counter (cheap read), settle actual usage atomically at request/stream end (`UPDATE … SET used = used + ?`). Breach discovered at settle → *next* request is blocked. Overshoot bounded by max concurrent streams × max response cost.
- **D4-β · Reserve-then-settle:** pre-deduct a reservation (estimate from `max_tokens`/history), refund/settle at end. *Buys:* tight budget adherence. *Costs:* reservation bookkeeping, stuck-reservation cleanup on crashes, estimates are bad (max_tokens often absent/huge).
- **D4-γ · Fully async settle:** never touch counters in the request path; settle from prompt-log/events. *Buys:* zero hot-path cost. *Costs:* enforcement lag = however stale the aggregator is; rate limits can't work this way at all.
- **D4-T1 stores:** rate limits (per-minute, sliding/token-bucket) fit **Redis** (already in stack, atomic INCR/EXPIRE, shared across replicas); budgets (monthly, must survive restarts, audited) fit **Postgres** atomic counters. Mixed store is not an inconsistency — they're different state classes.
- **D4-T2 usage source at settle:** tap `usage` chunk when present; else non-stream `usage` field; else tokenizer estimate flagged `estimated=true` in the log (resolves BQ-3's fallback).

*Lean: D4-α with D4-T1 split (Redis rate limits pre-checked hard; Postgres budgets settled post-hoc). Bounded overshoot is the right trade for an internal gateway — β's machinery serves a tightness nobody asked for.*

## D5 · Admin surface (where governance entities live)

- **D5-α · Config files** (teams/keys-hashes/budgets in files à la C-2; key material provisioned via sealed secrets). *Buys:* consistent with C-2, GitOps audit trail, no API to secure. *Costs:* key issuance = a deploy; revocation = a deploy (slow for a leaked key!).
- **D5-β · Admin REST API + DB** (the FI-6 CRUD, properly authenticated via Keycloak/Envoy this time). *Buys:* runtime issue/revoke, UI-ready, audit table. *Costs:* the scope C-2 just declined, plus authz care.
- **D5-γ · Hybrid state split:** *definitions* (teams, budgets, limits) in files; *keys* (issue/revoke — the things with urgency and secrecy) + *counters* (usage state) in DB via a minimal API. 

*Lean: D5-γ — revocation urgency is the one thing that genuinely cannot wait for a redeploy; everything else can. Minimal API = keys only (issue/revoke/list), Keycloak-admin-gated. Revisit-condition already parked for a fuller admin API.*

## D6 · Breach behavior

- **Rate limits: hard 429** with `Retry-After` (OpenAI-shaped error body — SDKs handle it natively).
- **Budgets:** (α) hard block at 100% · (β) warn-only (alert, never block) · (γ) tiered: alert at threshold (80%), block at 100%, per-budget `enforcement: hard|soft` flag with **soft as default** for internal teams. *Lean: γ — a research agent hitting a hard wall mid-month because of a pricing typo is worse than a 5% overshoot, but the flag lets hard budgets exist where wanted.*

---

## Cross-links
- **B:** D4-T2 consumes the tap (`StreamObservation`); BQ-3 lands here, resolved by the estimate fallback.
- **C:** admission runs before routing; settle records the *actual* provider/model after fallback (C-4). C5-γ (per-team upstream keys) would attach at D's Team entity if ever un-parked.
- **E:** cache hits settle at original cost with `cached=true` (1.x semantics preserved — P-2). Rate limits: cache hits count against request-rate limits (they're still requests) but not token budgets? → small fork for E/consolidation.
- **F:** budget/rate metrics per team; settle events are the natural Pythia feed (DQ-3).
- **A:** error bodies for 429/budget-blocked must be OpenAI-shaped (SDK-parseable) — name the budget-breach error `type` in A.

## Open questions (DQ-n)
- **DQ-1** · Does service→gateway (east-west) traffic pass Envoy, or call the Service directly? Decides whether the gateway keeps local JWT validation (1.x capability) alongside the injected-header trust, and whether the injected header is spoofable from in-cluster.
- ~~**DQ-2** · What credential is Hebe's "gateway key" *today*?~~ **Resolved 2026-07-11 (grep):** a **static per-instance secret** — provisioned in the instance's k8s Secret (`hebe-dev`: "PG creds, Keycloak client creds + bound user, **llm-gateway key**, …") and resolved via `SecretRef` from `llm.apiKeySecret`, separate from the Keycloak creds in the same Secret. Hebe's own docs note "the gateway may ignore them" — i.e. today the key is likely **decorative** (1.x validates OAuth2, not this key). → The ecosystem already *provisions* per-instance gateway keys; D1-β doesn't introduce a new credential, it makes an existing one real (issue/hash/validate/revoke). Migration = start validating what's already sent.
- ~~**DQ-3** · (= Q-3) Pythia's consumption contract?~~ **Resolved 2026-07-11 (grep):** Pythia does **not** ingest any gateway feed. Its `GatewayClient` is a client-side tier→tag shim with *projected* per-call USD (`project-and-reserve`, ops-tunable placeholder pricing); reconciliation relies on the response **`usage.cost` echo** (the 1.x cache preserves it for exactly this reason). → 2.0 contract: **keep the `usage.cost` echo on responses** (additive field — FI-4-compatible) including streamed responses (final usage chunk) and cache hits; settle-event feeds are additive later, not required.
- **DQ-4** · Who issues keys in practice (Bora via CLI/API call?) — sizes D5-γ's minimal API.

## Leans (not decisions)
D1-β dual credentials · D2-α key→team primary, headers refine · D3 calendar-monthly money budgets (track tokens too) · D4-α pre-check/post-settle, Redis rate limits + Postgres budgets, estimate fallback · D5-γ files for definitions, minimal API for keys · D6-γ tiered breach, soft default.

## To converge we need
1. DQ-1 + DQ-4 answered (Bora).
2. Your calls on D6 default (soft vs hard) and D5-γ's scope.
3. (DQ-2, DQ-3 resolved above — both strengthen the leans.)
