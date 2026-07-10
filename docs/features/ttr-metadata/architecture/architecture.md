# ttr-metadata — Architecture (v1)

> **Status:** designed 2026-07-05. Companions: [`contracts.md`](./contracts.md) (API, protocol, schemas) and [`../implementation/v1/plan.md`](../implementation/v1/plan.md) (phased plan). This feature resolves TTR-P review-queue items **R2** (ttr-metadata missing) and **R3** (`schema world` grammar unscheduled) — see `../../ttr-p/implementation/v1/tasks-overview.md`.
>
> Decision IDs cited from the TTR-P control room (`../../ttr-p/design/00-control-room.md`); this feature's own decisions are numbered **MD1…MD8** (§8).

---

## 1. What this feature is

Four deliverables under one arc:

1. **`org.tatrman:ttr-metadata`** — a published Kotlin library: the TTR model graph + queries + world resolution. Extracted from kantheon's **Ariadne** (`services/ariadne`, itself forked from ai-platform `infra/metadata` on 2026-06-13), with the platform-agnostic core moving *here* and Ariadne becoming a thin service wrapper — the same extraction pattern the Proteus/ttr-translator arc uses (Q6, E-a).
2. **World resolution** — the TTR-P-facing half that Ariadne never had: `schema world` parsing (the R3 grammar work, scheduled in this plan), `ResolvedWorld`, storage `hosts:`, staging validation, the semantic world fingerprint (T4/T6/D-d/D-f/F-f-ii).
3. **`ttr-designer-server`** — the repo-attached JVM Designer-server **core** (G-b), v1 scope = **read-only TTR-M model serving** over WebSocket to the existing Designer frontend. TTR-P Phase 5.1 later mounts its WS-LSP into this host instead of creating its own (supersedes TTR-P R10's `ttrp-designer-server` module name). The C1-f arc (TTR-M *editing* convergence + `.ttrl` migration) stays post-v1 — this feature builds the server C1-f will converge onto, it does not pull C1-f forward.
4. **Kantheon adoption** — Ariadne consumes the published artifacts, deletes its moved core, keeps its gRPC/HTTP/refresh-scheduling skin (planned here, executed in the kantheon repo; arc doc `kantheon/docs/architecture/fork/ttr-metadata-adoption.md`).

### 1.1 Why (the R2 chain)

TTR-P Phase 1.3 gates on ttr-metadata ("model repo + world doc resolution, D-g, offline"); the Designer server (TTR-P P5) and Ariadne both need the same component (G-c). Today the code exists only inside kantheon — the dependency arrow points the wrong way (tatrman must never depend on kantheon; contracts §10 of TTR-P). Extraction inverts it: tatrman owns the library, kantheon consumes published `org.tatrman:*` artifacts — the arrow stays one-way.

### 1.2 Out of scope (v1)

TTR-M edit mode / `.ttrl` migration (C1-f, post-v1) · the QueryParseWorker / stored-query SQL→PlanNode pipeline (stays in Ariadne — it depends on query-translator, which is the **ttr-translator** extraction arc, not this one) · gRPC serving from tatrman (Ariadne keeps the `org.tatrman.ariadne.v1` proto + facade) · auth/multi-user on the Designer server (S24: loopback-only) · md-tier catalogue integration (D-h parked; md-catalog stays a distinct axis) · runtime refresh scheduling policy (host concern; the refresher *mechanism* moves).

---

## 2. Component architecture

```
              tatrman (this repo)                          kantheon
┌─────────────────────────────────────────────┐   ┌──────────────────────────┐
│ packages/kotlin/                            │   │ services/ariadne          │
│                                             │   │  = thin wrapper:          │
│  ttr-parser ── ttr-semantics                │   │  grpc facade (14 RPCs)    │
│        │            │                       │   │  proto conversions        │
│        ▼            ▼                       │   │  QueryParseWorker         │
│  ┌───────────────────────────┐  publish     │   │  RefreshScheduler         │
│  │ ttr-metadata (core)       │─────────────►│   │  Ktor/OTel/k8s            │
│  │  model · source(SPI,fs,cp)│  org.tatrman:│   │       │ consumes          │
│  │  reconcile · resolve      │  ttr-metadata│   │       ▼                   │
│  │  graph · search · export  │  (+ -git)    │   │  ttr-metadata,            │
│  │  registry · refresh(mech) │              │   │  ttr-metadata-git         │
│  │  world (NEW: D-d/T4/T6)   │              │   └──────────────────────────┘
│  └─────────┬─────────────────┘              │
│            │ embedded by                    │        tools/ariadne-mcp
│  ┌─────────▼─────────────┐                  │        (unchanged; talks to
│  │ ttr-designer-server   │◄─── WS ────┐     │         Ariadne gRPC)
│  │ repo-attached, S24    │            │     │
│  │ v1: read-only ttrm/*  │   packages/designer
│  │ (P5.1 mounts ttrp WS- │   (frontend, WS data-
│  │  LSP here later)      │    source adapter)
│  └───────────────────────┘                  │
│  future consumer: ttrp-frontend (P1.3, D-g) │
└─────────────────────────────────────────────┘
```

**Dependency graph (one-way, as everywhere in the family):** `ttr-parser → ttr-semantics → ttr-metadata → {ttr-designer-server, ttrp-frontend, Ariadne}`. Calcite appears nowhere in this arc (query parsing stays kantheon-side until ttr-translator lands).

### 2.1 Module roster

| Module | Form | Content |
|---|---|---|
| `packages/kotlin/ttr-metadata` | Kotlin lib, published | the core (§3) + world resolution (§4). Deps: ttr-parser, ttr-writer, ttr-semantics, jgrapht-core, slf4j-api. **No Ktor, no gRPC, no OTel, no jgit.** |
| `packages/kotlin/ttr-metadata-git` | Kotlin lib, published | `GitArchiveStorage` (jgit + commons-compress) behind the core's `ModelStorage` SPI. Consumed by Ariadne only — keeps jgit out of the compiler/Designer-server classpath (**MD3**). |
| `packages/kotlin/ttr-designer-server` | JVM app module | Ktor CIO host, loopback-only (S24); WS JSON-RPC serving `ttrm/*` (contracts §4); embeds ttr-metadata over a `LocalFsStorage` on the attached model repo. |
| `packages/designer` (existing) | TS/React | gains a **WS data-source adapter** (read-only): same internal graph shape, backend selectable (browser-worker LSP ↔ WS server) (**MD6**). |

---

## 3. The extracted core (what moves from Ariadne)

Verdicts from the extraction inventory (2026-07-05; Ariadne ≈ 3,500 LOC core / ≈ 1,300 LOC service skin; seams are clean — core packages import only ttr-*, jgrapht, jgit, slf4j):

| Ariadne package | Verdict | Notes |
|---|---|---|
| `model/` (Model, ModelObject, Db/Er/CncSchema, DbTable/Column/View/Procedure/FK, Entity/Attribute, Binding, AreaRecord, SearchHints) | **CORE** | the typed model — becomes the library's public data model |
| `source/` (ModelSource, ModelStorage SPI, SourceSnapshot, LoadWarning; LocalFsStorage, ClasspathStorage, FileBasedSource, FallbackSource, BuiltinStockSource) | **CORE** | `LocalFsStorage` is exactly the repo-attached Designer-server source |
| `source/GitArchiveStorage` | **CORE, separate artifact** | → `ttr-metadata-git` (MD3) |
| `reconcile/ModelReconciler` | **CORE** | parse → typed model, load-issue collection |
| `resolve/` (ReferenceResolutionPass, Resolution, DrillMapValidator, PublishedResolverAdapter) | **CORE** | reference resolution over the typed model |
| `graph/ModelGraph` (+ core of TraverseEdgesHandler) | **CORE** | JGraphT DEFINES/REFERENCES/MAPS_TO/USES graph; traverse/cycles/topo |
| `search/` (SearchAlgorithm SPI, keyword/regex/substring/all, SearchIndexHolder, SearchPostProcessor, IndexableObjects) | **CORE** | |
| `registry/MetadataRegistry` (AtomicReference snapshot + listeners) | **CORE** | the swap/read/listen lifecycle |
| `refresh/MetadataRefresher` | **CORE (mechanism)** | mutex + try/force reload; clock/scope injectable. `RefreshScheduler` (periodic policy) **stays kantheon** |
| `export/` (ModelToDefinitions, GraphDotExporter, TtrWriter wrapper) | **CORE** | `MetadataExportRoutes` (Ktor) stays kantheon |
| `grpc/` (MetadataServiceImpl 1.4 kLOC, PageTokenCodec), `parse/` (QueryParseWorker + query-translator dep), `Application.kt`, k8s, ariadne-mcp | **KANTHEON** | the wrapper Ariadne keeps; paging codec is wire-level |

Package rename on the move: `org.tatrman.kantheon.ariadne.*` → `org.tatrman.ttr.metadata.*`. Tests move with their code (24 of 38 Kotest specs move — 23 core + 1 to `-git`; 14 grpc-layer specs stay; enumerated in tasks-m1/m4). Note (RM1, found at task-cutting): the core as it sits in Ariadne is *not* proto-free — `plan.v1` qname/schema-code types and two `ariadne.v1` types leak into it; M1 carries explicit de-proto tasks so the extracted library honors the no-proto rule below. Where Ariadne embedded query/filter logic inside `MetadataServiceImpl` (ListObjects filtering, paging windows), the logic is **pulled down** into a library `MetadataQuery` facade so the gRPC method bodies shrink to proto-conversion + delegation (**MD2**).

## 4. World resolution (the new half)

What Ariadne never needed and TTR-P requires (full obligations catalogued in contracts §2–3):

- **Grammar (resolves R3):** `schema world` becomes a TTR schema kind in `TTR.g4` (D-d-α: `def world` with nested `def engine` / `def executor` / `def storage`, `extends`, `hosts: [pkg]`, `staging: true`, named `def schema`). TTR-M-side change, cut through the grammar-master process (S6); ttr-parser/writer/semantics learn the kind; **this plan schedules it as Phase M0** — TTR-P P1.3's pre-flight then finds it done.
- **`ResolvedWorld`:** engines/executors/storages resolved from instance-`extends`-type overlays, exactly-one-staging validation (D-f), package hosting (`hosts:` → which storage serves a model package, D-d-i), world selection input (qname; `uses world` pin > `[ttrp] world` precedence is the *caller's* job — MD5).
- **Semantic world fingerprint** (F-f-ii): canonical serialization + sha256, computed here so compiler and conform harness share one implementation.
- **Kind-typed lookup** (D-b support): `resolve(qname, expectedKind)` returns the object or a structured miss carrying *both* expected and found kinds — the compiler maps syntactic position → expected kind and renders `TTRP-RES-*`; the library never invents diagnostics ids (MD5).
- **er→db binding traversal with provenance data** (E-d support): `erToDb(erQname)` → db qname + the binding chain (origin qname/name/location) so consumers can render er spellings first.

**Boundary rule (MD5): ttr-metadata is mechanism, consumers are policy.** No `modeler.toml`/`[ttrp]` reading, no TTRP-* diagnostic ids, no position table, no capability *checking* (T6 checks are compiler logic; the world doc's manifest *data* is served as-is). The library returns structured results/errors; each consumer (ttrp-frontend, Designer server, Ariadne) maps them to its own surface.

## 5. Designer server (v1: read-only TTR-M serving)

- **Form:** `ttr-designer-server` — Ktor CIO, binds `127.0.0.1` only, no auth (S24). Started with `--repo <path>` (the model repo root); walks up for `modeler.toml` the same way the TS LSP resolves project root.
- **Serves:** WS JSON-RPC methods `ttrm/getModelIndex`, `ttrm/getModelGraph`, `ttrm/getObject`, `ttrm/search`, `ttrm/getStatus`, `ttrm/refresh` (contracts §4) — read-only; deliberately shaped after the data the Designer already renders from the browser-worker LSP so the frontend adapter is thin (MD6). File watching → registry listener → `ttrm/modelChanged` notification.
- **Not an LSP** in v1: no documents are open, nothing is edited — it's a model-graph read API. When TTR-P P5.1 arrives, the *same host process* mounts the TTR-P WS-LSP endpoint beside `ttrm/*` (one server, two protocol families, G-b's "one repo-attached backend"). When C1-f arrives (post-v1), TTR-M editing joins the same host.
- **Frontend:** `packages/designer` gets a data-source interface with two implementations (existing worker LSP, new WS client). Read-only mode disables edit affordances. No fork of the designer for this feature (the TTR-P P5.3 fork decision is unaffected).

## 6. Kantheon adoption (executed in the kantheon repo)

Ariadne after the swap: `grpc/` facade + proto conversions + `PageTokenCodec` + `QueryParseWorker`(+`parse/`) + `RefreshScheduler` + `Application.kt`/Ktor/OTel — everything else deleted in favor of `org.tatrman:ttr-metadata` + `:ttr-metadata-git`. Version pinning in `gradle/libs.versions.toml` (`tatrman-ttr-metadata = "<semver>"`), consuming from GitHub Packages exactly as it consumes ttr-parser today. The **drift guard**: the 24 moved core specs run in tatrman; Ariadne's remaining grpc-layer specs must stay green against the artifact — any behavior gap surfaces as a failing Ariadne spec, fixed by a library release (never by re-forking core code into kantheon). `ariadne-mcp` and all Ariadne consumers (Golem, Shem assembly) are untouched — the gRPC contract does not change (**MD7**).

## 7. Testing strategy

Kotest throughout (repo convention). Moved specs keep their names; new coverage: world-resolution suite (positive world fixture + the WLD negative roster from TTR-P's s1.3 fixture design — shared fixtures, one home in ttr-metadata), fingerprint stability/sensitivity property tests, registry swap/listener component tests, Designer-server WS contract tests (in-process client; connect → getModelGraph on a fixture repo → modelChanged on file touch), frontend adapter Vitest tests against canned `ttrm/*` payloads. Kantheon-side: Ariadne suite green post-swap is the adoption gate. NO cross-repo live integration tests in v1 (drift guard = the two suites + shared fixtures).

## 8. Decision log (this feature)

- **MD1 · Extract to tatrman, Ariadne wraps** — the Q6/metadata-pattern applied to itself; keeps the repo dependency arrow one-way. Rejected: library lives in kantheon (tatrman can't depend on it); copy-fork (permanent drift).
- **MD2 · Query logic pulled down from the gRPC facade** into library `MetadataQuery`; facade = conversion + delegation. Rejected: move-as-is (leaves the reusable half stranded in kantheon).
- **MD3 · Two artifacts: core + `ttr-metadata-git`** — jgit/commons-compress stay off the compiler and Designer-server classpath. Rejected: single artifact (heavy deps for every consumer); git support dropped (Ariadne needs it).
- **MD4 · `schema world` grammar work is Phase M0 of this plan** (TTR-M-side, grammar-master process) — resolves R3 here rather than leaving it orphaned between plans.
- **MD5 · Mechanism/policy split** — no manifest reading, no TTRP-* ids, no position tables in the library; structured errors only. Rejected: library-owned diagnostics (couples the family library to TTR-P's surface).
- **MD6 · Designer server is not an LSP in v1; `ttrm/*` read-only protocol shaped after the existing designer payloads**; frontend gets a pluggable data source, not a fork. Rejected: full LSP now (nothing edits); bespoke REST (the family standard is WS JSON-RPC).
- **MD7 · Ariadne's gRPC contract is frozen through the swap** (`org.tatrman.ariadne.v1` untouched) — adoption is invisible to Golem/ariadne-mcp. Rejected: co-evolving the proto (couples two arcs).
- **MD8 · Module + server naming: `ttr-metadata`, `ttr-designer-server`** (family-level `ttr-` prefix, not `ttrp-`) — supersedes TTR-P R10's `ttrp-designer-server`; P5.1 mounts into this host (plan-impact note in `../implementation/v1/plan.md` §6).
