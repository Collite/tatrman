# Tatrman Server — Contracts (target 1.0.0)

> **Status:** written 2026-07-10 at SV-P0 planning. The core cut of the contract inventory: what SV-P0/P1 touch, pinned to the letter. The frozen PL contracts doc ([`../../platform/design/contracts.md`](../../platform/design/contracts.md)) remains the reference for the seam schemas (§2–§5 there: snapshot archive, `ttr.lock`, stats, compile record) — those enter force via the **RO-13 core ⚑ review** before the publish gates and are not restated here. Decision ground truth = control room §7 (STRAT/RO entries).
> **Version scheme (RO-24):** publishables leave `0.0.1-LOCAL` for a **0.9.x line** at the SV-P1 gates (continuing above the public 0.8.4 of parser/writer/semantics), go **1.0.0 together at the SV-P6 acceptance run**, then version independently; **the product version = the umbrella chart's `appVersion`**. Version-before-publish is a gate invariant alongside rename-before-publish.

---

## 1. Ownership (D-3 + RO-6/RO-8; ownership ≠ license tier)

| Contract | Owner (repo) | License | Status |
|---|---|---|---|
| `plan.v1` (+ `transdsl.v1`, `dfdsl.v1`, `translate.v1` enums) — `ttr-plan-proto` | tatrman | Apache-2.0 | landed; **amended in SV-P0** (§3, §4 here) |
| Model/world schemas, snapshot/lock/stats/compile-record | tatrman | Apache-2.0 | pinned in PL contracts §2–§5; RO-13 review pending |
| Service wire protos `org.tatrman.<function>.v1` (§2 map) | tatrman-server | Apache-2.0 | rename-on-arrival in SV-P0 |
| **`org.tatrman.common.v1`** (shared wire messages) | tatrman-server | Apache-2.0 | **NEW in SV-P0** (§5) |
| MCP surface (`ttr-meta-mcp`, `ttr-query-mcp`, `ttr-fuzzy-mcp`, later `ttr-grounding-mcp`) + conformance conversation suite | tatrman | Apache-2.0 | **PINNED 2026-07-10 (RO-25) → [`mcp-surface.md`](./mcp-surface.md)**: functional capability ids (S4 sweep) · OBO-only identity · J-v2 compat · tiered fixture suite (core 100% / extended scored); suite authoring = SV-P4 |
| Operate-tier contracts (envelope, event spine, door API, secret-store SPI, connector SPI, policy bundles) | tatrman-platform | commercial | ❄ parked (RO-13) |

## 2. Wire-package map after SV-P0 (J-v2 applied; personas never on the wire)

| Current package (kantheon) | Final package | Module (tatrman-server) | Notes |
|---|---|---|---|
| `org.tatrman.ariadne.v1` | `org.tatrman.meta.v1` | `services/veles` | persona survives as service name only |
| `org.tatrman.theseus.v1` | `org.tatrman.query.v1` | `services/ttr-query` | |
| `org.tatrman.proteus.v1` (service) | `org.tatrman.translate.v1` | `services/ttr-translate` | enums live in tatrman `ttr-plan-proto` under the SAME `translate.v1` package (§4) — one package, two source homes, no duplicate types (service proto imports the artifact's enums) |
| `org.tatrman.argos.v1` | `org.tatrman.validate.v1` | `services/ttr-validate` | keeps the absorbed security fold |
| `org.tatrman.security.v1` | `org.tatrman.security.v1` (unchanged — already functional) | moves with `ttr-validate` | verify importers in S4; fold into `validate.v1` only if it is validate-private |
| `org.tatrman.kyklop.v1` | `org.tatrman.dispatch.v1` | `services/ttr-dispatch` | |
| `org.tatrman.worker.v1` | `org.tatrman.worker.v1` (unchanged) | `workers/ttr-worker-{postgres,mssql,polars}` | kantheon's additive superset is canonical (RO-21) |
| `org.tatrman.echo.v1` | `org.tatrman.fuzzy.v1` | `services/ttr-fuzzy` | |
| `org.tatrman.kadmos.v1` | `org.tatrman.nlp.v1` | `services/ttr-nlp` | Python service stays Python |
| `org.tatrman.prometheus.v1` | `org.tatrman.llm.v1` | `services/ttr-llm-gateway` | superset convergence (§6) |
| `org.tatrman.kantheon.common.v1.ResponseMessage` | `org.tatrman.common.v1.ResponseMessage` | `shared/proto` (server) | **relocation** (§5); `kantheon.common.v1` keeps its copy for agent-only protos |
| (whois — no proto today) | `identity.v1` reserved, created when its API is formalized | `infra/ttr-identity` | rename only in SV-P0 |
| `org.tatrman.charon.v1` | `org.tatrman.transfer.v1` | stays **kantheon** (operate-parked) | rename rides the kantheon sweep (S5) |

Stays kantheon (not renamed in this sweep): `org.tatrman.kantheon.*` (agents, capabilities, envelope, handoff), `metis.v1`, `pinakes.v1`, `kallimachos.v1`.

## 3. `plan.v1` amendment — TableHint (RO-21, Bora 2026-07-10)

Adopted verbatim from ai-platform (`cz/dfpartner/plan/v1/plan.proto`), so the November repoint is a pure package swap for this file. In `ttr-plan-proto/src/main/proto/org/tatrman/plan/v1/plan.proto`:

```proto
message TableHint {
  string name = 1;                 // e.g. "NOLOCK"
  repeated string options = 2;     // e.g. ["0"] for INDEX(0); empty for bare hints like NOLOCK
}
```

On **both** `ScanNode` and `TableScanNode`: replace `reserved 3 to 7;` with

```proto
  repeated TableHint hints = 3;
  reserved 4 to 7;
```

Field 3 un-reservation is legal — nothing published yet (rename-before-publish invariant). Round-trip coverage extends `WireRoundTripSpec` (see tasks S2). ai-platform's missing pieces (`UnionNode` = field 16, `OverExpression` = expr field 12, `context.tenant_id` = 9) flow *to* ai-platform at repoint — no action here.

## 4. `translate.v1` (was `proteus.v1`) — enum home

`ttr-plan-proto`'s `org/tatrman/proteus/v1/translator.proto` → `org/tatrman/translate/v1/translator.proto`, package + `java_package` = `org.tatrman.translate.v1`. Content unchanged (`Language`, `SqlDialect` enums). Kantheon's byte-identical duplicate (`shared/proto/.../proteus/v1/translator.proto`) is **deleted**; the service proto (`proteus.proto` → `translate.proto`, package `translate.v1`) imports the enums from the `ttr-plan-proto` artifact.

## 5. `org.tatrman.common.v1` — NEW (RO-21 systemic finding)

All eight spine protos import `org.tatrman.kantheon.common.v1.ResponseMessage` today; open wire contracts may not depend on the intelligence suite's namespace. Fix:

- New file in tatrman-server `shared/proto`: `org/tatrman/common/v1/response_message.proto`, package `org.tatrman.common.v1` — content copied **verbatim** from kantheon's `kantheon/common/v1/response_message.proto` (message `ResponseMessage` + its enums; field numbers untouched).
- Every spine proto's import + type references switch to `org.tatrman.common.v1.ResponseMessage` (wire-compatible: proto3 binary carries field numbers, not type names).
- `kantheon.common.v1` survives kantheon-side for agent protos (handoff etc.); agent protos do NOT switch. Two source-of-truth files, two audiences, no cross-import.

## 6. `llm.v1` (was `prometheus.v1` / ai-platform `llmgateway.v1`)

Kantheon's **superset** is canonical (adds `EmbedText`, `EmbedRequest/EmbedResponse/Embedding`). Package `org.tatrman.llm.v1`; file `llm/v1/llm_gateway.proto`; **service renamed `PrometheusService` → `LlmGatewayService`** (functional); drop `option java_outer_classname = "PrometheusProto"` (default outer class). ai-platform repoints `org.tatrman.llmgateway.v1` → `llm.v1` in N4 (supersedes the extraction inventory's "repoint to prometheus.v1" line, which predates J-v2).

## 7. Build-time dependency rules (P2/RO-6 — enforced, not aspirational)

- `tatrman-server` may depend on: `org.tatrman:*` published by **tatrman** (parser/writer/semantics/metadata/plan-proto/translator) + third-party. It may NOT depend on anything `cz.tatrman:*` or any kantheon module/artifact.
- `kantheon` may depend on `org.tatrman:*` from **both** tatrman and tatrman-server. Nothing may depend on kantheon.
- No proto under tatrman-server `shared/proto` may import from `org/tatrman/kantheon/` (CI grep, S1).
- Temporary exception, explicit and tracked: `shared/libs/kotlin/query-translator` rides INTO tatrman-server as a vendored module at the move (its extraction to tatrman `ttr-translator` = SV-P1 gate 2; the module carries a `// TEMPORARY — extracts to tatrman at gate 2` banner). No arrow violation: server-internal vendoring, deleted at gate 2.
