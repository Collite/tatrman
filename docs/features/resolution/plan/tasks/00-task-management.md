# Resolution & Grounding — Task Management (overall tracker)

> Master document for the understanding-layer implementation. Structure: **Plan → Phase → Stage**; each stage is a mini task list of 6–8 checkboxed tasks in its phase file. Source design: [`../architecture.md`](../architecture.md) · [`../contracts.md`](../contracts.md) · [`../plan.md`](../plan.md) · design log [`../../design/00-control-room.md`](../../design/00-control-room.md) §7 (RS-1..32).
>
> **All phase task lists generated 2026-07-12** (RG-P0..P6, after a plan-vs-design review pass). RG-P5's list assumes Q-20 = GO and carries an explicit conditional first task for GO-WITH-FALLBACK — re-check its S1 against the spike report before starting that phase. **2026-07-13: RG-P3 gained an inserted S0** ([`tasks-p3-s0-meta-semantics.md`](./tasks-p3-s0-meta-semantics.md)) — the G2 gap (Veles has no semantics wire surface) closes via an additive `meta.v1` projection + a metadata seam, decided at the mid-S1 finding; **complete S0 before resuming S1's discovery wiring**.

## Rules for the coder (read before every session)

1. **Check every checkbox the moment its task is done** — both here (stage level) and in the phase file (task level). Never batch checkbox updates.
2. **TDD is mandatory.** Each stage lists test tasks *first* — write them, watch them fail, then implement. Do not reorder. (Spike stages in RG-P0 are the one exception: a spike produces a measurement report, not shipped code — its "tests" are the parity/precision assertions the report must contain.)
3. **Run the stage's verify command** before marking the stage done here.
4. Unit tests = single class/function; component tests = inter-class/inter-service within one module. **No full E2E integration tests** — conformance arrives in RG-P6 as a harness, not as tests you write earlier.
5. Conventions:
   - **Kotlin services** (`ttr-fuzzy`, grounding, `ttr-resolver`, `ttr-grounding-core`, shared libs) under `tatrman-server/services/*` and `tatrman-server` shared libs; Kotest (mirror `ttr-fuzzy`'s existing test style); package roots `org.tatrman.*`; Ktor + gRPC per [`EXAMPLES.md`](the planning skill's EXAMPLES.md) §1/§3.
   - **Python service** (`ttr-nlp` front + backends) stays Python 3.13 / FastAPI + gRPC; the front is engine-free; backends are separate images.
   - Commit style `RG-P<n>.S<m>: <description>`.
6. If a task references a not-yet-existing component (e.g. RG-P4's declared-vocabulary stream before the grammar lands), the task says so and names the **stub/adapter** to use — do not invent couplings.
7. **Spike numbers and golden fixtures are ground truth.** Q-10 parity tables and Q-20 precision numbers are recorded once and cited; the hero-sentence expectations and the seed-corpus expected outputs are exact — if you change a fixture, recompute by hand first, then update the test, never the reverse.
8. **S-1 is a hard invariant from day one:** no code path selects a model by empty/default parameter; every model-touched response echoes engine + version. A PR that adds an un-versioned response is wrong.

## Pre-flight gate (verify before RG-P0)

- [ ] SV-P2 closed; `tatrman-server` exists with the flat GHCR publish path (SV-P0/RO-28); `ttr-fuzzy` present at `tatrman-server/services/ttr-fuzzy`.
- [ ] `ai-platform` reachable for the spikes: `infra/nlp` + UFAL adapters (Q-10) and `agents/resolver/eval/seed.jsonl` + `ucetnictvi_entities_only.jsonl` (Q-20).
- [ ] RO-13 snapshot-archive schema review scheduled (gates RG-P2/RG-P5 snapshot path; live-metadata step-one covers the gap).
- [ ] Grammar 4.2 (semantics-block) merge status known (gates RG-P4; the lexicon 4.3 window scheduled after it).

## Phase & stage tracker

| Phase | Stage | Mini task list | Done |
|---|---|---|---|
| **RG-P0** spikes+scaffold | S1 Q-10 self-hosting spike (sizing + protocol parity) | [`tasks-p0-spikes-scaffold.md#s1`](./tasks-p0-spikes-scaffold.md) | [x] |
| | S2 Q-20 span-gating precision spike | [`tasks-p0-spikes-scaffold.md#s2`](./tasks-p0-spikes-scaffold.md) | [x] |
| | S3 scaffold: fold lib (S-2), proto renames, diagnostics | [`tasks-p0-spikes-scaffold.md#s3`](./tasks-p0-spikes-scaffold.md) | [x] |
| **RG-P1** ttr-nlp | S1 `org.tatrman.nlp.v1` gRPC + capability matrix + engine-free front | [`tasks-p1-nlp.md#s1`](./tasks-p1-nlp.md) | [x] |
| | S2 MorphoDiTa + NameTag 3 backends (self-hosted, models baked) | [`tasks-p1-nlp.md#s2`](./tasks-p1-nlp.md) | [ ] |
| | S3 Stanza + spaCy backends, batch lemmatize, degrade floor, charts | [`tasks-p1-nlp.md#s3`](./tasks-p1-nlp.md) | [ ] |
| **RG-P2** ttr-fuzzy | S1 Q-17 referee corpus + source tags + lemma axis on | [`tasks-p2-fuzzy.md#s1`](./tasks-p2-fuzzy.md) | [x] |
| | S2 snapshot read + alias tables + BatchMatch + refresh/staleness | [`tasks-p2-fuzzy.md#s2`](./tasks-p2-fuzzy.md) | [x] |
| **RG-P3** grounding | **S0 (inserted 2026-07-13)** G2 closure: `meta.v1` semantics projection + metadata seam | [`tasks-p3-s0-meta-semantics.md`](./tasks-p3-s0-meta-semantics.md) | [x] |
| | S1 extraction + `ttr-grounding-core` kernel + invariants *(resume after S0)* | [`tasks-p3-grounding.md#s1`](./tasks-p3-grounding.md) | [x] |
| | S2 geo posture + kind-named tools + fiscal-quarter (Q-18) | [`tasks-p3-grounding.md#s2`](./tasks-p3-grounding.md) | [x] |
| **RG-P4** lexicon (A) | S1 lexicon grammar + inline sugar + desugar *(gated on grammar 4.2)* | [`tasks-p4-lexicon.md#s1`](./tasks-p4-lexicon.md) | [x]¹ |
| | S2 search-block slim + legacy migration + consumer propagation | [`tasks-p4-lexicon.md#s2`](./tasks-p4-lexicon.md) | [ ] |
| **RG-P5** resolver | S1 proto reshape + deterministic core *(gated on Q-20 verdict)* | [`tasks-p5-resolver.md#s1`](./tasks-p5-resolver.md) | [ ] |
| | S2 snapshot registry + HMAC tokens + degrade | [`tasks-p5-resolver.md#s2`](./tasks-p5-resolver.md) | [ ] |
| **RG-P6** doors | S1 resolve door + grounding tools on the surface | [`tasks-p6-doors.md#s1`](./tasks-p6-doors.md) | [ ] |
| | S2 three-tier conformance + fold audit + register updates | [`tasks-p6-doors.md#s2`](./tasks-p6-doors.md) | [ ] |

## Phase exit reviews

House cadence: after each phase, a `/review` pass verifies the phase's Definition of DONE ([`../plan.md`](../plan.md)) against runtime — progress-doc `[x]` marks are intent, not truth.

> ¹ RG-P4·S1 conformance is green for lexicon across TS/Kotlin/Python (TS↔Kotlin 100%); the `py-vs-ts` job carries a **pre-existing** semantics-block (grammar-4.2) gap on fixtures 59/60 that predates RG-P4 — flagged for a separate Python-semantics port.

- [ ] RG-P0 review (spike go/no-go recorded) · [x] RG-P1 review ([`rg-p1-review.md`](../reviews/rg-p1-review.md)) · [x] RG-P2 review ([`rg-p2-review.md`](../reviews/rg-p2-review.md)) · [x] RG-P3 review ([`rg-p3-review.md`](../reviews/rg-p3-review.md)) · [ ] RG-P4 review · [ ] RG-P5 review · [ ] RG-P6 review (= SV-P3 parity bar)

## Library reference card

- **Kotlin MCP SDK (streamable HTTP)** — the door/tool layer. `Server(Implementation(name,version), ServerOptions(ServerCapabilities.Tools(listChanged=false)))`; `server.addTool(name, description, inputSchema, outputSchema){ request -> … }`; mount with `mcpStreamableHttp { … }`; wrap every callback in `safeMcpTool(name, timeoutMs){ … }` (timeouts/exceptions → `CallToolResult(isError=true)`), never let it bubble. Use the SDK's `McpJson` as the only JSON instance for MCP routes. Cloned for reference: `~/Dev/view-only/kotlin-mcp-sdk` (graphified). Precedent in-repo: `tools/fuzzy-mcp` (single-tool), `tools/query-mcp` (registry). Snippets: EXAMPLES.md §3.
- **Ktor service base** — `installKtorServerBase(config)` for plain services, `installMcpKtorBase(mcpConfig, otelSdk)` for MCP services (adds the `mcp-session-id`/`mcp-protocol-version` exposed headers). Canonical `Application.kt` ≤ 45 lines. `connectionIdleTimeoutSeconds = 120` — **never 3600**. Serialization: `buildJsonObject`/`JsonPrimitive`, never `mapOf` in `call.respond`; sealed interfaces for multi-shape fields. Snippets: EXAMPLES.md §1/§2.
- **Python + FastAPI/gRPC (ttr-nlp)** — the front stays Python 3.13; engine SPI (`NlpEngine` Protocol: `supported_languages`, `supports(lang,op)`, `analyze`) + `EngineRegistry` per-op-per-language routing; `Orchestrator.analyze` groups ops by routed engine, one pass each. Backends are separate images; the front's adapters speak each backend's HTTP protocol. FastAPI async node/handler patterns (not LangGraph — the NLP service is not an agent): EXAMPLES.md §5 shows the async + MCP-client shapes; use the FastAPI/async parts, ignore the LangGraph graph assembly.
- **UFAL tooling** — MorphoDiTa: C++ core, `ufal.morphodita` PyPI bindings + in-tree `src/rest_server`; models `czech-morfflex2.0+pdtc` (LINDAT). NameTag 3: Python + PyTorch, ships `nametag3_server.py` (multi-model REST), model `nametag3-czech-cnec2.0-240830`; GPU optional, CPU works slower. Both models CC BY-NC-SA (FI-4 legal item — gates *publishing* images). Verify current server flags/model ids with `context7` or the upstream repos when writing the backend Dockerfiles.
- **OTel** — `createOpenTelemetrySdk(OtelEndpointConfig(serviceName, protocol))` once at top of `main()`; logback OTEL appender; deps via `shared:libs:kotlin:otel-config`. EXAMPLES.md §8.
- **context7 MCP** — pull latest API docs for FastAPI, grpc-python, kotlinx.serialization, Ktor, Kotlin MCP SDK before writing against them; training data may lag.
