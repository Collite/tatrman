# Tatrman Server — Architecture (target 1.0.0)

> **Status:** written 2026-07-10 at the close of the design re-open session, from the ecosystem redraw (STRAT-1..9) and the re-open ratifications (RO-1..14). Ground truth for decisions = the platform decision log ([`../../platform/design/00-control-room.md`](../../platform/design/00-control-room.md) §7 — including the "Ecosystem redraw amendment batch") — this document cites decisions by ID and does not restate their why. Companions: [`plan.md`](../implementation/plan.md) (the thin core-v1 plan), [`../../ecosystem.md`](../../ecosystem.md) (the target-state description), `kantheon/docs/architecture/fork/extraction-inventory-260710.md` (repoint mechanics). **Target version: Tatrman Server 1.0.0** (the RO-3 bar); the frontends analysis plane = **Server 1.1.0** (RO-23). The operate-tier reference is [`../../platform/implementation/`](../../platform/implementation/) (frozen, RO-2).

---

## 1. What Tatrman Server is (and is not)

**Tatrman Server is the open runtime of the Tatrman ecosystem** (Apache-2.0, STRAT-3): the deployable product an organization installs to make its modeled data available for AI consumption — **one product name, one chart, a small constellation of services** (STRAT-4). It carries the ecosystem's core promise end-to-end: a machine-consumable semantic model (TTR-M, authored as text under git) with a deterministic, governed query path underneath it, consumed by any MCP-capable agent through a contract rather than through trust.

The license rule is **"interoperate vs administrate"** (STRAT-2, supersedes A-1): everything an adopter needs to *prove* the promise is open — modeling, metadata serving, translation, validation **with RLS**, dispatch, execution of interactive queries, the MCP surface, resolver, grounding, reference agents. What an enterprise needs to *operate an estate with confidence* is commercial: scheduled program execution, data movement, fleet policy administration, continuous harvest, ops extensions.

**What Tatrman Server is not (STRAT-1):** it does not schedule, orchestrate multi-step programs, or move data between engines — that is the parked operate tier (Tatrman Platform, satellite (c)); the TTR-P processing family and the write designer are satellite (a); satellite (b) is now the **entry product only** (`tatrman-entry`) — its former analysis half joined the Server as 1.1.0 arcs (RO-23). Satellites are parked **by sequence, not by doubt**, each with a named re-open trigger (§9).

**Provenance.** The Server is not a greenfield: its read spine is **live at the pilot deployment** (client lineage) and **extracted** into the open lineage (kantheon repo, post-fork). v1 is a *publication and packaging* effort over proven services, not a build-from-design effort — the inverse of the frozen PL plan's posture.

## 2. Repo & artifact topology (RO-1, RO-6, STRAT-9)

```
tatrman (Apache-2.0)            tatrman-server (Apache-2.0, NEW — RO-1)     tatrman-platform (commercial, RESERVED)
  the standard & toolchain        the open spine, moved from kantheon          the operate tier — wakes with
  org.tatrman:* (libs, protos,    with N1 rename folded in (one migration)     satellite (c); cz.tatrman:*
  CLI, IDE, Designer frontend,    org.tatrman:* service artifacts +
  conformance)                    helm/ (the single chart)
        │ publishes                     │ publishes
        ▼                               ▼
        └── build-time deps: tatrman ──► tatrman-server ──► { tatrman-platform · kantheon }
            (RO-6: the operate tier and the agents BOTH consume the Server;
             NO build-time edge between tatrman-platform and kantheon;
             runtime plugins on published SPIs stay legal — P2 clarification)

kantheon (persona space)         ai-platform (DFP engagement)
  the intelligence suite:          deployment instance #0 (STRAT-9): refactored to
  Golem (+shem), Pythia, Iris,     CONSUME published org.tatrman artifacts by Nov 2026
  vertical/advanced agents         per the extraction inventory — never extracted FROM
```

- **License boundary = repo boundary (D-2, surviving per repo):** `tatrman` + `tatrman-server` = Apache-2.0 under `org.tatrman:*`; `tatrman-platform` = Tatrman Platform License under `cz.tatrman:*`. The group id keeps the boundary physical.
- **Naming (J-v2):** functions name contracts, modules, and repos (`ttr-<function>`, proto `org.tatrman.<function>.v1`); persona survivors = Veles, Perun (reserved), Charon (reserved), and Kantheon-internal names; personas never on the wire. Rename map: [`../../platform/design/naming-260710.md`](../../platform/design/naming-260710.md).
- **Ownership (D-3, RO-6):** toolchain-touched ⇒ tatrman-owned; service-internal ⇒ server-owned (operate-tier contracts stay platform-owned, parked). **Ownership ≠ license tier — two columns.** Kantheon owns nothing shared.
- **Packaging (RO-12):** the Server ships as one umbrella chart from `tatrman-server/helm/`; `tatry` parks with the operate tier; instance vocabulary — DFP/ai-platform = #0.

## 3. Service roster & the governed read path

The roster (all Apache-2.0; status: **live** at pilot / **extracted** in the open lineage / **planned**):

| Service | Function | Status |
|---|---|---|
| **Veles** (+ `ttr-meta-mcp`) | Metadata server: catalog, model graph, search, snapshots; reads model source from git; *the single source of what is known* (RO-5: the live extracted service, grown toward C-2-γ's four-consumer design) | live · extracted |
| `ttr-query` (+ `ttr-query-mcp`) | The query door: accepts a query, drives translate → validate → dispatch, streams typed results (Arrow); renders JSON/CSV/XLSX/Parquet | live · extracted |
| `ttr-translate` | Language ↔ plan translation over the plan hub (`plan.v1`); entity-level references mapped to physical via model bindings; dialect-aware unparse | live · extracted |
| `ttr-validate` | Security **in the plan**: RLS predicates, column allow/deny/mask, result caps, strict coercion; structurally unavoidable — both doors' traffic passes it | live · extracted |
| `ttr-dispatch` | Routes validated plans to capable engine workers | live · extracted |
| `ttr-worker-{mssql,postgres,polars}` | Per-engine executors; further engines = new workers on `worker.v1` | mssql, polars live · pg extracted |
| `ttr-resolver` | Entity/value extraction from user text against model vocabulary; language-aware (Czech morphology proven) | live (client lineage) · **open rewrite planned — the one undesigned component (plan §SV-P3)** |
| `ttr-fuzzy` · `ttr-nlp` | Fuzzy candidate matching over model-declared searchable fields; NLP primitives | live · extracted |
| `chrono` / `geo` / `money` (+ `ttr-grounding-mcp`) | Deterministic grounding of universal spans (dates/fiscal calendars, places, amounts/currencies) | live · extraction planned (Fork Phase 6) |
| `ttr-llm-gateway` | Single egress to LLM providers (`llm.v1` — dissolves the llmgateway/prometheus collision) | live · extracted |
| `ttr-identity` | IdP ↔ source-system identity mapping; optional role enrichment (RO-7: whois's open descendant; **not** a Perun precursor) | live · extracted |
| **Designer (viewer)** | Browser viewer of models incl. `.ttrl` (RO-9): catalog + model graph over Veles; ships **in the chart, outside the acceptance bar**. Writer = Server v2; TTR-P designer = Platform | frontend exists · adapter work |
| **Semantic layer service** (named at its arc) | Cube-shaped semantic model + single policy carrier for external BI (Superset = reference pairing; Power BI et al.); model projected from TTR-M `md`; row policies from the open validator policy store (RO-7; Perun feeds the same generator at operate wake) | planned — PF B/R3, 1.1.0 arc (RO-23) |
| **Designer analysis viewer** | hierarchy-true pivot + charts + lineage-aware drill (drill legs light up as their tiers wake) | planned — PF B, 1.1.0 arc (RO-23) |

**The read path (the two-call thesis, deterministic after intent):**

```
 MCP agent (Golem, or any vendor's)
   │ 1) resolve entities + ground spans       ttr-resolver · ttr-fuzzy · chrono/geo/money
   │ 2) intent as SQL over MODELED entities   (LLM speaks a language it knows; never joins/dialects/security)
   ▼
 ttr-query ──► ttr-translate ──► ttr-validate ──► ttr-dispatch ──► ttr-worker-*
 (identity:     (model bindings   (RLS + deny/mask   (routing)        (typed results,
  H-2 ingress)   → physical plan)  INTO the plan)                      streamed back)
   ▲                                                                        │
   └── Veles serves the model to everyone; one OTel trace per question ◄────┘
```

Infra components `health` and `backstage` ride the server repo too (RO-22 — "nothing to paywall there"); kantheon registers its components in the same backstage instance.

**The MCP surface is the consumption contract (RO-8)** — a named, tatrman-owned contract: the tool schemas of `ttr-meta-mcp`/`ttr-query-mcp` (+ fuzzy/grounding), per-user identity pass-through, and the **conformance conversation suite** as its executable test. Any second implementation of any component is possible by construction; the two reference Golems (STRAT-7: Kotlin+Koog in v1; Python+LangGraph as the stretch, OQ-3) exist to prove the surface, not to be the product.

## 4. The mode seam in the redrawn world (B, RO-4, RO-14)

FI-1's two first-class modes stand; **the seam is now an open↔open contract** — Veles sits on the Apache side, so the license seam no longer coincides with the mode seam. All B decisions carry unchanged: mode-blind compiler behind the source SPI (B-1), fetch-then-compile (B-5), hard parity (B-3), `ttr.lock` canon pinning (BQ-3/4), B-4 seam legality (*data + diagnostics; never identity, never side effects*).

Timing (RO-14): **schemas pin now** (snapshot archive, lock, stats — the RO-13 core ⚑ review, before the publish gates); **Veles's snapshot-archive serving = core work**; **the per-object stats organs are built with satellite (a)** — the optimizer is their only consumer, and B-2's defined degradation makes deferral safe by construction.

## 5. Security — the core cut (RO-7)

- **Identity:** the H-2 ingress module at every door — bearer-only IdP JWT · enrichment-never-authority · fail-closed; machine callers = client-credentials service principals; `ttr-identity` provides mapping/enrichment (optional, fail-closed when required).
- **Enforcement:** structural — `ttr-validate` between translation and execution, no bypass flag; RLS/deny/mask applied **in the plan** (H-7-α, the live HOCON store verbatim). *Policy-honest by construction* is the headline claim; that is why enforcement is open (STRAT-2).
- **Policy administration, open path:** **HOCON-in-git** — validator policy versioned, reviewed, and governed as git content ("robots write through git"). The TTR `security`-block sugar (H-1) targets it in a follow-up open arc (generator → validator config, unsigned, git-reviewed). **Perun** (directory sync, signed bundles, fleet audit — H-4, H-7-β) is the commercial administration layer, parked with the operate tier.
- **Secrets:** live practice ships — K8s Secrets + env injection (`TTR_CONN_*`); the H-5 secret-store SPI (Vault/cloud bindings, canary-tested never-at-rest CI) hardens with the operate tier. The no-secret-API rule applies now.
- **LLM Guard:** stays a validator-SPI plugin (C-5-i) — kantheon ships it; no plugin = deterministic default.

## 6. Contract inventory — core rows (D-3 + RO-8, cited from `../../platform/design/contracts.md` where pinned)

**tatrman-owned (Apache):** `plan.v1` protos (transfer per publish gate 3, verify OQ-11) · model/world schemas · snapshot-archive / `ttr.lock` / stats-entry schemas (§2–§4; RO-13 core review) · compile record (§5) · `ttr import-schema` output conventions (§12; STRAT-8 arc) · validator SPI (§9) · Designer Extensions surface (§10) · **the MCP surface + conformance conversation suite (RO-8 — new; pin in core planning)**.

**server-owned (Apache):** service wire protos `org.tatrman.<function>.v1` (query/translate/validate/dispatch/`worker.v1`/meta/identity/llm/fuzzy/nlp, grounding when extracted) · the chart's values contract.

**platform-owned (commercial, parked with satellite (c)):** envelope §13 · event spine §14 · door API §15 · secret-store SPI §17 · connector SPI §18 · policy bundles §19.

## 7. Registration & configuration (RO-10)

Server v1 keeps the **live config mechanics**: worker/engine connections in service config; agent instances declared as shems. **E-2-γ ("registration = world content") remains the ratified direction**, adopted when the operate tier wakes or the Designer registration wizard needs it — an explicit tiered adoption, not drift.

## 8. Invariants carried into the core (cite by ID)

1. **P1** — standalone is not a demo; no language feature gates on the Server.
2. **P2 (RO-6 chain)** — build-time deps flow `tatrman → tatrman-server → {tatrman-platform, kantheon}` only; enforced by dependency rules + CI in every repo.
3. **P3** — no miracles: explicit, or deterministically derived; otherwise an error.
4. **B-4** — the seam admits data + diagnostics; never identity, never side effects.
5. **One path to data** — every plan passes `ttr-validate`; there is no bypass flag.
6. **Bearer-only · enrichment-never-authority · fail-closed** at every ingress (H-2).
7. **"Robots write through git"** — every canon writer produces reviewable commits; this now also carries the open policy path (RO-7).
8. **Personas never on the wire** (J-v2); rename-before-publish; anything published never renames.
9. **Auditability is a property of the architecture** — every answer traceable to a validated plan; one OTel trace per question.
10. **Ownership ≠ license tier** — the D-3 column and the STRAT-2 column are kept separately.

## 9. Acceptance bar (RO-3, ratified trimmed) & what wakes when

> *"Tatrman Server v1 is done when, on a fresh single-chart deployment by someone who is not us, from public artifacts and public docs alone: a TTR-M model bootstrapped by `ttr import-schema` from an existing database and hand-refined in the IDE is served by Veles; a question asked through the MCP surface by any MCP agent — the reference Golem or a third-party one — returns a governed answer with row-level security applied in the plan and full provenance attached; the maker loop closes (edit model → commit → refresh → the agent understands); and the reference Golem passes the conformance conversation suite. All Apache-2.0."*

**Named stretch clauses (on the critical path, non-gating):** the TTR-M → Power BI semantic-model projection (STRAT-8); the second reference Golem (Python + LangGraph, OQ-3) passing the same suite.

**Satellite wake-up triggers (STRAT-1, (b) amended by RO-23):** (a) TTR-P family — first program workload; (b) the entry product `tatrman-entry` (commercial; the PF analysis plane already belongs to the Server as 1.1.0 arcs) — first planning workload; (c) operate tier — first operated estate (then the frozen PL plan re-validates against reality, per its own ⚠).
