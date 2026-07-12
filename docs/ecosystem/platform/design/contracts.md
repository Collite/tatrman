# Tatrman Platform (PL) — Contracts

> **[superseded 2026-07-10 · STRAT-3]** license is Apache-2.0 across the open surface; MIT mentions below are historical.
> **❄ FROZEN 2026-07-10 — OPERATE-TIER REFERENCE (RO-2/RO-13). Target: Tatrman Platform 1.0.0.** Core-relevant sections (§2–§5, plan protos) enter force via the RO-13 core ⚑ review before the publish gates; operate-tier ⚑ flags are ratified at satellite (c)'s wake-up. Live near-term docs: `../../server/`.


> **Status:** consolidated 2026-07-09 from [`./design.md`](./design.md) §7 (contract inventory), the decision log ([`./00-control-room.md`](./00-control-room.md) §7), and the existing contract corpus (TTR-P contracts v1.4 · ttr-metadata contracts v1.5 · ttr-translator contracts v3). This document PINS every contract the v1 plan builds against. Items the design routed to planning (§10 of `design.md`) are resolved here and marked **⚑** — each ⚑ is a planning-session call, recorded with rationale, open for review until Phase 0 ratifies this document.
> Companions: [`architecture.md`](./architecture.md), [`plan.md`](../implementation/plan.md).
> **Changelog** at the end. Changes here require a changelog entry.

---

## 1. Ownership inventory (D-3 applied)

Rule: **toolchain-touched ⇒ tatrman-owned (MIT, `org.tatrman`); service-internal ⇒ platform-owned (`cz.tatrman`).** Kantheon owns nothing shared. The Maven group id is the license boundary (J-5).

| # | Contract | Owner | §ref | Status |
|---|---|---|---|---|
| 1 | Plan protos `plan.v1`/`transdsl.v1`/`dfdsl.v1` | tatrman (TR-3) | — | exists, frozen rules per ttr-translator contracts §2 |
| 2 | World / T6 manifest schemas (+ K `extends` surface, F-4 vocabulary) | tatrman | §3, §7, §16 | exists + S1 amendments |
| 3 | Snapshot archive format | tatrman | §2 | **new** |
| 4 | `ttr.lock` format | tatrman | §3 | **new** |
| 5 | Stats-entry schema (BQ-2) | tatrman | §4 | **new** |
| 6 | Compile record | tatrman | §5 | **new** |
| 7 | E-5 bundle-manifest schema v2 (graph + lineage + provenance, versioned) | tatrman | §6 | graduation of F-lite manifest |
| 8 | Executor capability manifests (FQ-4, T6 format pin) | tatrman | §7 | **new** |
| 9 | Emit SPI (E-1/EQ-1) + plugin trust (EQ-2/H-6) | tatrman | §8 | **new** |
| 10 | Validator SPI interface (C-5-i) | tatrman | §9 | **new** |
| 11 | Designer Extensions surface (Q-4-a) | tatrman | §10 | **new** |
| 12 | `security` block grammar + generator (H-1) | tatrman | §11 | **new** (S1 grammar) |
| 13 | `ttr import-schema` output conventions (I-2/IQ-2) | tatrman | §12 | **new** |
| 14 | Deployment envelope schema (F-2-β) | platform | §13 | **new** |
| 15 | Run ids + run/lineage/audit event contract (FQ-2/F-6-β/S7) | platform | §14 | **new** |
| 16 | Door frontend + deploy API (E-3) | platform | §15 | **new** |
| 17 | Veles server surface (B contract serving, reads, ingest, export) | platform | §16 | **new** |
| 18 | Secret-store SPI (H-5) | platform | §17 | **new** |
| 19 | Connector SPI (E-4/I-2, two output kinds) | platform | §18 | **new** |
| 20 | Policy-bundle conventions (H-1/H-4) | platform | §19 | **new** |
| 21 | Query-door proto (slimmed Theseus) · hall/worker protos (`worker.v1`, …) | platform | — | transplant + package sweep (DQ-2); no redesign in v1 (`metis.v1` stays kantheon — D-6) |
| 22 | CQ-4 capability↔routing mapping layer (T6 worker manifests → Kyklop routing vocabulary) | platform | — | **deferred into PL-P2's Kyklop transplant arc** (design: "mapping layer first, vocabulary convergence later") — the mapping table is a named PL-P2 deliverable |

Sizing defaults are collected in §20; diagnostics in §21.

## 2. Snapshot archive format (BQ-1) — MIT

A **snapshot archive** is the content-addressed transport unit of canon between Veles and the toolchain cache. One archive = one source kind's content set (world archive, model-package archive, manifest archive).

- **Container:** deterministic `tar` (**⚑** USTAR, entries sorted bytewise by path, `mtime=0`, `uid=gid=0`, mode `0644`/`0755`, no PAX extras), **zstd-compressed at fixed level 19 with checksums off** — determinism is load-bearing (B-3): same content ⇒ same bytes ⇒ same id. Rationale: tar+zstd is boring, streamable, and byte-reproducible; zip's local-header timestamps fight determinism.
- **Id:** `sha256:<hex>` over the **compressed archive bytes**. Spelled exactly like world fingerprints.
- **Layout:**

```
<archive>/
├── snapshot.json          # {"formatVersion":1, "kind":"world|models|manifests",
│                          #  "qnames":["acme.worlds.prod", …], "producedBy":"veles <semver>",
│                          #  "resolvedFrom":{"platformWorldCommit":"<git sha>", …}}   // provenance, informative
└── docs/<package-path>/<file>.ttrm     # verbatim TTR documents
```

- **World archives carry the RESOLVED composed world** (C-6/K): Veles runs the K composition (project `extends` platform world) behind its resolver and serves the *result* as TTR text, so standalone re-resolution of the same archive is the identity. `snapshot.json.resolvedFrom` records the inputs (informative only — not hashed into any fingerprint).
- **Local cache:** `<project>/.ttr/cache/sha256/<first2>/<hex>` (**⚑**; gitignored; `~/.tatrman/cache` as the shared fallback via config). Cache is immutable-by-id; eviction is by `ttr cache gc`.
- `LocalFsStorage` remains the plain-repo binding — archives are only the *transport* for the connected binding (B-1).

## 3. `ttr.lock` (BQ-3 layered + BQ-4) — MIT

Committed at the **project root** (F-7). Pins canon only; **stats never appear here**. TOML (**⚑** — matches `modeler.toml` house format; diff-reviewable).

```toml
# ttr.lock — generated by `ttr fetch`; do not hand-edit. Canon pins only.
lockVersion = 1

[world]
qname   = "acme.worlds.prod"          # the world this project compiles against
archive = "sha256:9f2c…"              # resolved composed world archive (§2)
platformWorld = { qname = "tatrman.platform.world", pin = "sha256:41aa…" }  # K: referenced platform world content hash

[models]
"shop.sales" = "sha256:77b1…"          # one entry per model package archive

[manifests]
"tatrman-executor" = "sha256:c0de…"    # executor/engine TYPE manifests in play (§7)

[plugins]                              # BQ-4: emit-plugin identity is canon
"org.tatrman:ttr-emit-bash"    = { version = "1.0.0", sha256 = "sha256:ab12…" }
"org.tatrman:ttr-emit-kestra"  = { version = "0.3.0", sha256 = "sha256:cd34…" }
```

- **`ttr fetch`** resolves the server's current canon → downloads archives into the cache → rewrites `ttr.lock`. A fetch is a reviewable lock diff ("the platform's view of the world changed"). Server URL and auth come from project config (`modeler.toml [ttrp] metadata-server`), **never** from the lock.
- **Flags:** `--frozen` (CI default): no network; fail `TTRP-LCK-002` if the cache lacks a pinned archive. `--offline`: no network; use cache; warn `TTRP-LCK-003` and record staleness in the compile record.
- Standalone projects simply have no `[world].platformWorld`/server config; the lock is legal but optional there (LocalFs binding reads the repo directly). Hard parity (B-3): compile output depends only on resolved content, never on which binding produced it.

## 4. Statistics (BQ-2) — MIT

A separate, snapshot-pinnable **source kind** (`StatisticsSource` beside models/worlds/manifests). Floats under max-age; never in the lock; absent = defined degradation to the static cost model (never an error).

```json
// stats entry — the atom, keyed PER OBJECT (BQ-2 = γ+δ, verbatim field set)
{
  "qname": "shop.sales.db.dbo.ORDER_LINE",
  "objectSchemaHash": "sha256:e4f1…",     // hash of the object's resolved shape (column set + types)
  "observedAt": "2026-07-09T02:00:00Z",
  "values": {                              // open map, well-known keys ⚑:
    "rowCount": 182000344,
    "byteSize": 21474836480,
    "ndv": { "CUSTOMER_ID": 412000 },      // number of distinct values, per column
    "nullFraction": { "DISCOUNT": 0.87 }
  }
}
```

- **Validity is per-object:** `objectSchemaHash` mismatch ⇒ discard *that* entry only ⇒ that object degrades to the static cost model; diagnostic `TTRP-STA-001` names it. The world fingerprint stays stats-free forever.
- **Max-age auto-refresh:** project config `[ttrp] stats-max-age = "15m"` (**⚑** default 15m, the design's own example); `--frozen` ⇒ stats must come from cache or be absent-and-tolerated.
- Values actually consumed by the optimizer are embedded **verbatim** in the compile record (§5).

## 5. Compile record (B-3-α / BQ-2-δ / F-7) — MIT

Emitted by every compile as a **bundle-adjacent sidecar** `<program>.compile-record.json`, beside the `.bundle/` directory — **never inside the bundle, never in the manifest's `files{}`**. The bundle stays a pure function of resolved inputs (B-3); the record *describes* those inputs and is legitimately binding-dependent (`mode`, `staleness`), so it must not be hashed into the artifact. The envelope carries it (F-7, §13): deploy uploads the sidecar and cites its sha256.

```json
{
  "recordVersion": 1,
  "toolchain": "org.tatrman:ttrp:0.9.0",
  "program": "hero.ttrp",
  "mode": "connected",                        // informative; lives in the sidecar only — never in the bundle
  "lock": { "hash": "sha256:<of ttr.lock bytes>", "path": "ttr.lock" },
  "snapshot": {                               // exactly what was resolved, by content id
    "world": "sha256:9f2c…", "models": { "shop.sales": "sha256:77b1…" },
    "manifests": { "tatrman-executor": "sha256:c0de…" }
  },
  "worldFingerprint": "sha256:<semantic-hash>",
  "plugins": [ { "id": "org.tatrman:ttr-emit-bash", "version": "1.0.0", "sha256": "sha256:ab12…" } ],
  "statsUsed": [ /* verbatim §4 entries the optimizer consumed; [] in stats-less compiles */ ],
  "staleness": { "offline": false, "statsOlderThanMaxAge": [] },
  "objectsRead": ["shop.sales.db.dbo.ORDER_LINE", "…"]   // the F-7 program-scoped provenance slice
}
```

The envelope cites `{lock hash, compile-record sha256}` (§13); the δ pointer-deploy recompiles **from `statsUsed`**, never from the lock alone (Q-7). Replay = trivial: fetch the listed archives by id, re-inject `statsUsed`, compile, byte-compare the bundle.

## 6. E-5 bundle-manifest schema v2 — MIT (the load-bearing contract)

Graduates the F-lite `manifest.json` (TTR-P contracts §5) to a **documented, versioned, stable** contract. Door execution keys on `schemaVersion`; adapters and lineage read it (provenance rides the envelope + sidecar record, §5/§13).

```json
{
  "schemaVersion": 2,                    // NEW — the E-5 version key (FQ-7 window: §20)
  "ttrpVersion": 1, "toolchain": "org.tatrman:ttrp:<semver>", "program": "<filename>",
  "world": { "qname": "acme.worlds.prod", "fingerprint": "sha256:<semantic-hash>" },
  "islands": [ { "name": "…", "engine": "…", "executor": "bash", "invocation": "psql|python3",
                 "file": "islands/….sql", "sha256": "…",
                 "retries": 2,                        // NEW (F-4) manifest-declared, transient-class
                 "onFailureOf": null,                 // NEW (F-4-iv): set ⇒ island runs iff named source failed
                 "connections": ["TTR_CONN_ERP_PG"],  // NEW: per-island (was bundle-global) — H-5 least exposure
                 "params": ["run_date"] } ],          // NEW: params this island consumes
  "transfers": [ { "from": "…", "to": "…", "via": "<staging>", "file": "…", "sha256": "…",
                   "connections": ["TTR_CONN_ERP_PG", "TTR_CONN_FILES"] } ],
  "waves": [["island-a","island-b"],["island-c"]],
  "params": [ { "name": "run_date", "type": "date", "required": false,
                "default": "@run-date" } ],           // NEW (F-4-i): declared, typed, defaulted-or-required
  "connections": ["TTR_CONN_ERP_PG","TTR_CONN_FILES"],
  "displays": [ { "name": "main_result", "file": "out/main_result.arrow" } ],
  "lineage": {                                        // NEW (CQ-5): STATIC column lineage, compile-derived
    "version": 1,
    "columns": [ { "output": { "island": "summarize", "relation": "out.main_result", "column": "total" },
                   "inputs": [ { "qname": "shop.sales.db.dbo.ORDER_LINE", "column": "AMOUNT" } ],
                   "transform": "aggregate:SUM" } ]   // transform tag vocabulary: identity|expression|aggregate:<fn>|join-key|filter-only ⚑
  },
  "files": { "<path>": "sha256:…" }
}
```

**No provenance block in the manifest** — provenance is the *envelope's* job (F-7, §13): binding-dependent content inside the artifact would break hard parity (B-3). The compile record is a sidecar (§5).

Rules: the **manifest's wave graph is authoritative; `run.sh` is a rendering** (drift = emit bug, conformance-caught). v1 manifests omit `onFailureOf`-with-`absorbs`, backoff, island-level resume, computed params — using reserved vocabulary is an ordinary compile error against worlds whose executor manifest lacks it (T6). The `lineage` section maps 1:1 onto the OpenLineage `columnLineage` facet (export mapping lives in Veles's connectors, not here; lossiness list = §18). Schema published as JSON Schema at `packages/kotlin/ttrp-emit/src/main/resources/schemas/bundle-manifest-v2.schema.json` (**⚑** location).

## 7. Executor capability manifests (FQ-4 + T6 format pin) — MIT

**Format pin ⚑ (discharges the "Stage 2.2 owns the format" debt):** engine/executor TYPE manifests are **TTR-M documents** (`schema world` vocabulary, `def executor <id> { … }` with free-form property blocks — the shape ttr-metadata already transports as `Map<String, PropertyValue>`). Shipped as classpath resources: core engines with the compiler; **executor types with their emit plugin** (T6 ownership amendment, S1). Rationale: robots write through git; TTR text is the family's one canon format; the transport is already built.

**Tatrman-the-executor's manifest (FQ-4, concrete artifact — content = F-4 verbatim):**

```ttrm
# shipped with cz.tatrman:radegast; qname tatrman.manifests.executor.tatrman
schema world

def executor tatrman {
    control:    [fs, ss],                  # FF stays reserved — absent ⇒ compile error on use
    params:     { types: [string, int, decimal, date, datetime, bool],
                  binding: trigger-time, builtins: [run-date] },
    retries:    { scope: island, classification: [transient, permanent] },
    resume:     { scope: wave, guard: snapshot-fingerprint },
    onFailure:  { scope: island, absorbs: false },
    events:     [cron, manual, upstream-run],
    invocation: { psql: true, python3: true },
    manifestSchema: { accepts: [1, 2] }    # FQ-7 window (§20)
}
```

Programs targeting a platform world compile against this manifest; vocabulary the manifest lacks ⇒ ordinary T6 compile error (P3). The bash executor-type manifest (extracted plugin, §8) declares the strict F-lite subset: `control: [fs, ss]`, no params/retries/resume/onFailure, `events: []`, `manifestSchema: { accepts: [1, 2] }`.

## 8. Emit SPI (E-1/EQ-1) + plugin distribution & trust (EQ-2/H-6) — MIT

**EQ-1 surface pin ⚑.** JVM SPI in a new `org.tatrman:ttrp-emit-spi` module (extracted from `ttrp-emit`); discovery via `java.util.ServiceLoader`; plugins own the **orchestration layer only** — island payloads are handed in finished and MUST be emitted byte-verbatim.

```kotlin
package org.tatrman.ttrp.emit.spi

interface TtrEmitPlugin {
    val targetId: String                                // "bash" | "airflow3" | "kestra" | …
    val spiVersion: Int                                 // this contract = 1
    /** TTR-M text of the executor TYPE manifest this plugin ships (§7, T6 ownership amendment). */
    fun executorTypeManifest(): String
    /** DETERMINISM IS A CONTRACT OBLIGATION: same request ⇒ byte-identical result.
     *  No timestamps, no random ids, no env reads, no filesystem/network access. Verified by the H-6 kit. */
    fun emit(request: EmitRequest): EmitResult
}

data class EmitRequest(
    val program: ProgramMeta,                           // name, qname, toolchain semver
    val graph: OrchestrationGraph,                      // waves, islands (name/engine/invocation/params/retries/onFailureOf/connections), transfer edges
    val islandPayloads: List<IslandPayload>,            // (name, relPath, bytes, sha256) — verbatim pass-through
    val transferPayloads: List<IslandPayload>,
    val executorType: ResolvedManifest,                 // this plugin's own type manifest, resolved
    val executorInstance: ResolvedManifest,             // world instance overlay (E-2 entry; connection REFS only — B-4)
    val manifestJson: String,                           // the finished E-5 §6 document — plugins embed/reference, never edit
)
data class EmitResult(
    val files: SortedMap<String, ByteArray>,            // orchestration-layer files, paths relative to bundle root
)                                                       // core writes manifest.json/islands/*/compile-record.json itself
```

- **Distribution (EQ-2 ⚑):** plugins are ordinary Maven artifacts named `ttr-emit-<target>` (group `org.tatrman` for first-party MIT; any group for third-party/commercial). Identity `{coordinates, version, artifact sha256}` pinned in `ttr.lock [plugins]` (§3). The toolchain loads plugins from an isolated classloader; no plugin classpath leaks into the compiler core.
- **Trust (H-6):** artifact **PGP publisher-signature verification — verify-if-signed in v1**, `require-signed` = project/deployment policy knob. **Determinism kit** ships in `ttrp-conform`: `ttrp conform emit-determinism --plugin <coords>` = double-compile byte-compare of `EmitResult.files` over the conformance corpus; passing it is the certification requirement for third-party plugins.
- The bash emitter is **extracted** as the proving plugin (`org.tatrman:ttr-emit-bash`) — the SPI is proven by extraction, not invented. Mode-drift suite (B-3) reuses the same double-compile harness across bindings.

## 9. Validator SPI (C-5-i) — MIT interface, platform-hosted

```kotlin
package org.tatrman.validator.spi

interface PlanValidatorPlugin {
    val id: String                                       // e.g. "kantheon-llm-guard"
    val spiVersion: Int                                  // = 1
    fun validate(ctx: ValidationContext): Verdict        // PLUGINS NEVER REWRITE PLANS
}
class ValidationContext(
    val plan: ByteArray,            // plan.v1 PlanNode bytes (D-3: plan protos are the shared tongue)
    val principal: PrincipalInfo,   // subject + roles (enrichment-never-authority)
    val worldFingerprint: String,
    val door: Door,                 // PROGRAM | QUERY
)
sealed interface Verdict {
    data object Pass : Verdict
    data class Deny(val code: String, val reason: String) : Verdict
    data class Advise(val code: String, val warning: String) : Verdict
}
```

Argos hosts plugins via ServiceLoader after its own deterministic pipeline (RLS injection, DENY/MASK, TopN, coercion — which stay Argos-internal, not SPI). No plugin installed = deterministic default (everything passes to Argos's own verdict). This SPI is the P2 clarification made physical: kantheon ships `llm-guard` as a runtime plugin on an MIT interface.

## 10. Designer Extensions surface (Q-4-a) — MIT

TS interface in `@tatrman/designer` (new entry point `@tatrman/designer/extensions`):

```ts
export interface DesignerExtension {
  id: string;                       // "cz.tatrman.runs"
  version: string;
  /** Panels contributed to the shell; MIT shell decides placement. */
  contributes: { panels?: PanelContribution[] };
  activate(ctx: ExtensionContext): void | Promise<void>;
}
export interface PanelContribution { id: string; title: string; icon?: string;
  mount(el: HTMLElement, ctx: PanelContext): () => void }   // returns dispose
export interface ExtensionContext {
  dataSource: ModelDataSource;      // the MD6 adapter in force
  backend: BackendInfo;             // { kind: 'worker'|'designer-server'|'veles', baseUrl, capabilities }
  auth: { token(): Promise<string | null> };
}
```

**Loading ⚑:** the backend advertises extensions at `GET /v1/designer/extensions → [{id, version, moduleUrl}]`; the MIT shell dynamic-`import()`s each ESM `moduleUrl` and calls `activate`. Browser-worker and loopback backends return `[]` — standalone never loads platform panels (P1). Platform panels v1: runs, column lineage (PL-P2), and the kind-parameterized registration wizard (G-4 set, shipped from the platform repo as Designer Extensions; the wizard writes commits to the platform-world repo, so it lands with the K write path at PL-P7).

## 11. `security` block grammar + generator (H-1) — MIT

TTR-M grammar amendment (S1 batch; sketch below is the contract's *shape* — exact grammar lands via the grammar-master process):

```ttrm
security {
    own       sales: team_sales                     # ownership declaration
    classify  order_line.customer_email: pii        # classifications = native grant vocabulary (HQ-1)
    grant     read on sales to accounting           # role or classification-mapped grant
    mask      order_line.customer_email             # column mask (default: for all; policy maps exceptions)
}
```

- **Generator (MIT, deterministic, one-way):** `security` blocks → standard Rego fragments + structured data, package `tatrman.generated.<sanitized-qname>`; generated fragments carry a `# GENERATED — do not edit` header and are **never hand-edited**. Same input ⇒ same bytes (I-3-grade determinism).
- **Composition = deny-overrides:** sugar grants; hand-written Rego can always take away. Row-level predicates stay Rego-side (not sugar) in v1.
- **Fingerprint-neutral:** `security` blocks are excluded from the world/T6 semantic fingerprint (access changes must not churn world verification) and never alter emitted plans.
- Verbatim role names are legal; classification→role mapping is org policy data; unknown roles **fail closed at bundle build** (Perun, §19), advisory lint standalone (H-8).

## 12. `ttr import-schema` output conventions (I-2/IQ-2) — MIT

One-shot CLI: `ttr import-schema <connection> [--dialect pg|mssql] [--out <dir>]` — introspects `INFORMATION_SCHEMA`/catalog, generates draft world + model documents, human reviews and commits. Platform harvest connectors (§18) reuse the **same mapping code**.

**IQ-2 qname-mapping discipline ⚑ (the first connector's mapping spec — normative for all connectors):**

1. External name → TTR name: lowercase; `[^a-z0-9_]` → `_`; collapse repeats; prefix `_` if leading digit. Original spelling preserved verbatim in a `source-name:` property — the mapping is **lossless and invertible via the property**, never via the mangled name.
2. Qname shape: `<package>.<schemaToken>.<namespace>.<name>` (ttr-metadata dotted-string rule). DB objects land under `db.<schema>.<table>`; the package is the CLI/connector's `--package` argument — never inferred.
3. **Collisions after mangling = hard error** (`TTRP-IMP-001`), never auto-suffixed (P3) — the operator renames via config mapping entries.
4. Determinism (I-3): same external state ⇒ same proposal bytes — objects emitted in bytewise-sorted qname order, no timestamps in doc bodies, generator version recorded in the PR/commit message only.

## 13. Deployment envelope schema (F-2-β) — platform

YAML document, stored content-addressed in Radegast's envelope store; created via `ttr deploy` / Designer / API.

```yaml
# envelope.yaml — envelopeVersion 1
envelopeVersion: 1
name: hero-nightly            # envelope identity = name@version (immutable once accepted)
version: 7
artifact:                     # exactly one of artifact: | source:
  bundleHash: "sha256:aa11…"  # content-addressed verbatim F-lite bundle (byte-identical to standalone)
# source:                     # δ pointer-deploy — later legal variant, schema reserved now (Q-7):
#   repo: "git@…/analytics.git"; commit: "<sha>"
#   MUST recompile from provenance.compileRecord's statsUsed — never from the lock alone
triggers:
  - kind: cron                # cron | manual | upstream-run  (external events arrive via connectors, later)
    schedule: "0 2 * * *"
    timezone: "Europe/Prague"
    id: nightly               # trigger id — a component of the run id (§14)
params:                       # bindings for E-5 §6 `params` (typed at deploy-time against the manifest)
  run_date: "@run-date"       # built-in; literals allowed; computed bindings = post-v1
connections:                  # program connection name → world instance entry (refs ONLY — H-5)
  warehouse_pg: arges_prod
  files: nfs_share
principal: svc-analytics-nightly     # REQUIRED envelope-named service principal (H-2-iii); no default
policy:
  deployGrant: implied               # deploy/run authz evaluated at the door against Perun bundles
retention:
  runScopedStaging: "7d"             # FQ-6 (§20 default); at expiry: resumable → restart-only
provenance:                          # F-7 — program-scoped provenance slice
  lockHash: "sha256:…"
  compileRecord: "sha256:…"          # hash of the <program>.compile-record.json SIDECAR (§5) — uploaded with deploy
```

Validation at deploy (all P3-explicit, `PLT-ENV-0xx` diagnostics): bundle hash resolves + manifest `schemaVersion` within the door's window; every manifest `params[required]` bound or defaulted; every manifest connection mapped to a world instance entry; principal exists + deployer holds a **use-grant** for it; T6 fingerprint verifies against Veles's served resolved world.

## 14. Run ids + run/lineage/audit event contract (FQ-2 / F-6-β / S7) — platform

**FQ-2 run-id schema ⚑ (adopting the recorded candidate):**

```
runId      = "<name>@<version>/<triggerId>/<fireTimeUtcIso8601Basic>"   e.g. "hero-nightly@7/nightly/20260710T020000Z"
attemptId  = "<runId>#<islandName>.<attemptN>"                          e.g. "…Z#summarize.2"
```

Natural key, human-legible, sortable; manual fires get `triggerId = "manual"` and the actual fire time; room for external-orchestrator impedance (Dagster partitions map into `triggerId`) per E-4's rider.

**Event contract — platform-internal proto `cz.tatrman.event.v1`** (D-3; new file `event.proto`, no `service` blocks, append-only field numbers):

```protobuf
package cz.tatrman.event.v1;
message EventEnvelope {                     // every event rides in one
  string event_id = 1;                      // uuid — idempotency key (at-least-once delivery)
  string occurred_at = 2;                   // RFC3339 UTC
  string source = 3;                        // "radegast" | "zorya" | "argos" | "veles" | "perun" | connector id
  oneof body { RunEvent run = 10; IslandEvent island = 11; LineageEvent lineage = 12; AuditEvent audit = 13; }
}
message RunEvent {
  string run_id = 1;
  string envelope = 2;                      // "name@version"
  string transition = 3;                    // SCHEDULED|STARTED|WAVE_STARTED|PARKED_RESUMABLE|RESUMED|
                                            // DEGRADED_RESTART_ONLY|SUCCEEDED|FAILED|CANCELLED
  string principal = 4;                     // the envelope-named service principal
  string policy_bundle_hash = 5;            // H-4 rider: the bundle in force — REQUIRED
  string bundle_hash = 6;                   // the F-lite bundle
  string world_fingerprint = 7;
}
message IslandEvent {
  string attempt_id = 1; string run_id = 2;
  string transition = 3;                    // DISPATCHED|STARTED|SUCCEEDED|FAILED_TRANSIENT|FAILED_PERMANENT|SKIPPED_RESUME
  string worker = 4;                        // "arges" | "brontes" | "steropes" | "charon"
  string log_ref = 5;                       // REFERENCE into the run store (S7) — payloads never in events
}
message LineageEvent {                      // CITES, never re-derives (CQ-5)
  string run_id = 1;
  string bundle_hash = 2;                   // + manifest section pointer:
  string manifest_lineage_ref = 3;          // "manifest.json#/lineage" (version-pinned by bundle hash)
  repeated string materialized_outputs = 4; // qnames actually written this run (runtime context)
}
message AuditEvent {                        // S7: authority decisions ride the same spine
  string principal = 1; string action = 2;  // "deploy"|"run"|"cancel"|"catalog-read"|"plan-validate"|…
  string resource = 3;                      // qname / envelope / run id
  string decision = 4;                      // "allow"|"deny"
  string policy_bundle_hash = 5;            // REQUIRED
  string pep = 6;                           // which enforcement point decided
}
```

**Transport ⚑:** emitters write to a durable **outbox table in the run store** in the same transaction as the state change; a forwarder POSTs batches to Veles `POST /v1/ingest/events` (at-least-once; Veles dedupes on `event_id`). No message broker in v1 — one fewer moving part; the outbox makes the spine replayable. Log payloads stay in the run store, referenced by `log_ref`.

## 15. Door frontend + deploy API (E-3) — platform

REST + SSE on Radegast (JSON; kotlinx; bearer JWT at ingress — H-2). The pinned frontend contract `{start, poll/subscribe, cancel}`:

```
POST /v1/envelopes                    body: envelope.yaml(json) + bundle upload ref → 201 {name, version}
GET  /v1/envelopes/{name}/{version}   → envelope (refs only — never secret material)
POST /v1/runs                         body: {"envelope":"hero-nightly@7","params":{…},"trigger":"manual"} → 202 {"runId":"…"}
GET  /v1/runs/{runId}                 → {state, waves:[{islands:[{attempt, state, worker, logRef}]}], startedAt, …}
GET  /v1/runs/{runId}/events          → SSE stream of §14 envelopes (subscribe)
POST /v1/runs/{runId}/cancel          → 202   (running islands finish — F-5: no preemption)
POST /v1/runs/{runId}/resume          → 202 | 409 PLT-RUN-005 if degraded restart-only (FQ-6)
GET  /v1/runs?envelope=…&state=…      → operational list ("what is running now" — executor-owned reads)
```

External orchestrator adapters (E-3-α-1) call exactly this surface — a door-calling DAG task = `POST /v1/runs` + poll/SSE. Zorya calls the same API (an internal frontend). Query door (Theseus) keeps its transplanted proto, package-swept only.

## 16. Veles server surface — platform

Four consumer classes, one service:

```
# compiler seam (B contract)
GET  /v1/snapshots/{sha256}                        → archive bytes (§2; content-addressed, immutable, cacheable)
POST /v1/snapshots/resolve  {"world":"acme.worlds.prod"}
     → {"world":"sha256:…","models":{…},"manifests":{…},"platformWorld":{"qname":…,"pin":"sha256:…"}}
     # assembles/reuses archives of the RESOLVED composed world (K) — the fetch step's one round-trip
GET  /v1/stats/{qname}  ·  POST /v1/stats/query {"qnames":[…]}   → §4 entries
# runtime
POST /v1/ingest/events                             ← §14 batches (id-deduped, at-least-once)
GET  /v1/worlds/current?qname=…                    → served resolved world + fingerprint (CQ-6 ad-hoc validation, T6 verify)
# Designer & catalog reads (PEP: coarse visibility)
WS   /v1/ttrm                                      → the ttrm/* JSON-RPC method surface (designer-server protocol,
                                                     + auth; same method names — getModelIndex/getModelGraph/getObject/
                                                     search/getWorld/getStatus/refresh/modelChanged)
GET  /v1/runs?…  ·  GET /v1/runs/{runId}           → ingested run history (catalog view — NOT the operational view)
GET  /v1/lineage/column?qname=…&column=…&depth=…   → column-lineage graph (from ingested LineageEvents + manifest sections)
GET  /v1/designer/extensions                       → §10 extension list
# harvest & export (refresh organ)
POST /v1/connectors/{id}/trigger  ·  GET /v1/connectors → connector runs ride the scheduling organ (§18)
```

Veles storage: canon in git repos (platform world repo + project roster from **server config**, K pin 4) via `ttr-metadata-git`; snapshot archives + stats in its own store (**⚑** PostgreSQL for stats/ingest + filesystem/S3 blob store for archives). Veles is a PEP for coarse catalog visibility (H-3).

## 17. Secret-store SPI (H-5) — platform

```kotlin
package cz.tatrman.secrets.spi

interface SecretStore {
    val scheme: String                                   // "k8s" | "vault" | "aws-sm" | …
    /** Resolve at DISPATCH TIME ONLY. Never cached to disk, never logged, never returned by any endpoint. */
    fun resolve(ref: SecretRef): SecretMaterial
}
data class SecretRef(val store: String, val path: String) {   // parsed from "secret://<store>/<path>"
    companion object { fun parse(uri: String): SecretRef }
}
class SecretMaterial(bytes: ByteArray) {                 // zeroizable; toString() = "SecretMaterial(REDACTED)"
    fun asEnv(): Map<String, String>                     // → TTR_CONN_<NAME> entries for injection
    fun zeroize()
}
```

- Bindings: `k8s` (DEFAULT — K8s Secrets), `vault`, cloud managers post-v1. Registered via ServiceLoader in Kyklop/Charon dispatch paths.
- Resolution is a **dispatch side effect**: Kyklop resolves the island's declared connections (E-5 §6 per-island `connections`) and injects `TTR_CONN_*` into the worker invocation; Charon likewise per transfer for the source×target pair. **Only declared connections are injected.**
- Invariants (CI-enforced, §21 canary suite): never at rest (envelope/run store/logs/events/artifacts) · no endpoint returns material · store-unreachable ⇒ island **pre-flight** failure `PLT-SEC-001` (retryable per manifest) · secret-zero = deployment config (K8s SA / workload identity), documented not solved. Delivery hardening (env vs file-mount — /proc caveat): **⚑ v1 = env vars** (F-lite parity verbatim); file-mount delivery recorded as a post-v1 hardening knob.

## 18. Connector SPI (E-4 / I-2, two output kinds) — platform

One frame for orchestrator run-harvest, data-engine introspection, and external catalogs:

```kotlin
package cz.tatrman.connect.spi

interface Connector {
    val id: String                                       // "ttr-connect-openmetadata", "ttr-connect-airflow3", …
    val engineKind: String                               // matches the world instance entry's type
    fun capabilities(): Set<Capability>                  // HARVEST_SCHEMA, HARVEST_STATS, HARVEST_RUNS,
                                                         // EXPORT_CATALOG, EXPORT_LINEAGE, EVENT_SUBSCRIPTION
    fun harvest(ctx: ConnectorContext): HarvestResult    // inbound
    fun export(ctx: ConnectorContext, projection: Projection): ExportReport   // outbound
}
class ConnectorContext(
    val instance: ResolvedManifest,       // the world instance entry (E-2) this connector serves
    val authRef: SecretRef,               // resolved by the platform at invocation — connector never sees raw config
    val veles: VelesClient,               // read access for export projections
)
sealed interface HarvestOutput {
    /** CANON → PR-shaped proposal: deterministic TTR docs on a branch (I-3). Robots write through git. */
    data class Proposal(val targetRepo: RepoRef, val docs: SortedMap<String, String>, val title: String) : HarvestOutput
    /** OBSERVATION → direct to the BQ-2 stats store. No review, by temperament. */
    data class Stats(val entries: List<StatsEntry>) : HarvestOutput
    /** Run harvest → §14 events (delegated runs land in the same lineage graph). */
    data class Events(val batch: List<EventEnvelope>) : HarvestOutput
}
```

- **Determinism obligation (I-3):** same external state ⇒ same proposal bytes; enforced by a replay test in the connector kit. Proposals are attributable to the connector's service principal; **never auto-merged**.
- Scheduling: Veles's refresh organ drives `harvest()` on configured cadences; the same subscription surface doubles as the external-event trigger source (post-v1 events).
- **v1 connectors:** `ttr-connect-openmetadata` (import + export pair — the I-5 anchor; export = OpenLineage column-lineage facet + catalog entities; **IQ-4 lossiness list** is a deliverable of that connector's mapping spec and must document at minimum: FS/SS edge semantics → flattened to job dependencies; world/manifest structure → custom properties; TTR types → OM primitive approximations; security blocks → not exported) · `ttr-connect-airflow3` (run harvest, PL-P5). DataHub = first follow-on, consciously not v1.

## 19. Policy-bundle conventions (H-1/H-4) — platform

- **Format:** standard **OPA bundle** (`.tar.gz`: `/.manifest`, `*.rego`, `data.json`) — whois's `roles.tar.gz` muscle transplanted. Signing = OPA bundle signing (JWT in `.signatures.json`), key = Perun's publisher key (H-6 trust root #2).
- **Qname binding:** package path = `tatrman.<kind>.<sanitized-qname>` (e.g. `tatrman.entity.shop_sales_er_entity_order`); structured data (row predicates, masks) keyed by qname in `data.json`. **Composition rule (v1.1):** generated fragments arrive under `tatrman.generated.<sanitized-qname>` (§11); Perun's bundle build aggregates them into the `tatrman.<kind>.*` query layout, so PEP queries see sugar grants and hand Rego through one path — deny-overrides applies at the aggregation point. Qname lineage on renames is Veles's job — Perun consuming rename events is **post-v1** (v1: a renamed qname surfaces as fail-closed denials until policy sources are updated — documented, not silent).
- **Build pipeline (Perun):** security-block generated fragments (from model repos, §11) + hand Rego (org policy repo) → compose under **deny-overrides** → validate (unknown roles fail closed; HQ-1) → sign → content-hash → serve. `GET /v1/policies/bundles/{scope}` + `GET /v1/policies/bundles/{scope}/hash`.
- **PEP discipline:** every PEP (doors, Argos, Veles, Designer backend) pulls bundles (standard OPA bundle-download protocol; **⚑ OPA sidecar per PEP** — no JVM Rego engine exists; sidecar is the boring, OPA-native choice), evaluates **locally**, refreshes every 60 s, and **fails closed** when its bundle is past expiry. Every decision cites `policy_bundle_hash` (§14).
- Argos v1 keeps its HOCON store verbatim (H-7 α); the rekey onto bundle-served structured data is the H-7 β follow-up arc (HQ-5 decision — mechanical translation vs re-authoring — is scheduled *in that arc*, informed by ⑤'s transplant experience).

## 20. Sizing defaults ledger (all ⚑ — one place, cited by id)

| Id | Knob | v1 default | Where set |
|---|---|---|---|
| SZ-1 | Stats max-age auto-refresh | `15m` (design's example) | `[ttrp] stats-max-age` |
| SZ-2 | FQ-6 run-scoped staging retention | `7d` | envelope `retention.runScopedStaging`; platform default config |
| SZ-3 | FQ-7 manifest-schema support window | accepts `{current, current−1}` = `{2, 1}` at v1 | Radegast config + §7 manifest |
| SZ-4 | Policy-bundle expiry / PEP refresh | `24h` expiry · `60s` refresh poll | Perun bundle `.manifest`; PEP config |
| SZ-5 | S7 event retention (Veles) | run/audit events `180d` · log payloads in run store `30d` | Veles/run-store config |
| SZ-6 | Event size cap | `64 KiB` per event; anything bigger goes by `log_ref` | ingest validation `PLT-EVT-002` |
| SZ-7 | GQ-4 edit-session quota | `3` concurrent sessions/user · `2h` idle reap | Veles/edit-workspace config |
| SZ-8 | Snapshot cache location | `<project>/.ttr/cache` (gitignored) → `~/.tatrman/cache` fallback | `[ttrp] cache-dir` |
| SZ-9 | S7 notification channels | v1 = none (Designer + API polling); webhooks post-v1 | — |

## 21. Diagnostics (house convention: fixture-backed, grouped by decade)

| Id | Severity | Meaning |
|---|---|---|
| `TTRP-LCK-001` | error | `ttr.lock` missing/unparseable where a connected binding is configured |
| `TTRP-LCK-002` | error | `--frozen`: pinned archive absent from cache — run `ttr fetch` and commit the lock diff |
| `TTRP-LCK-003` | warning | `--offline`: compiling from cache; staleness recorded in compile record |
| `TTRP-LCK-004` | error | lock pins a platform world whose declared `extends` target contradicts it (K) |
| `TTRP-STA-001` | info | stats entry discarded: object schema hash mismatch — object degrades to static cost model |
| `TTRP-IMP-001` | error | import-schema qname collision after mangling — add a rename mapping |
| `PLT-ENV-001..009` | error | envelope validation failures (§13 list: unbound param, unmapped connection, missing principal/use-grant, schema-version window, fingerprint mismatch, …) |
| `PLT-RUN-001..009` | error/info | run lifecycle (005 = resume refused: degraded restart-only after retention expiry, names the reason — P3) |
| `PLT-SEC-001` | error | secret store unreachable at dispatch — island pre-flight failure (retryable per manifest) |
| `PLT-POL-001` | error | PEP bundle past expiry — fail closed |
| `PLT-EVT-001..003` | error | ingest rejections (unknown schema, size cap, duplicate id = silently deduped, not an error) |

CI **canary suite** (H-5): plant `TTR_CONN_CANARY=canary-9f81…` at deploy of a test envelope; assert bytes appear in no envelope row, run-store row, emitted event, log line, or artifact.

---

## Changelog

- **v1.1 · 2026-07-09** — §19: added the §11→§19 package composition rule (generated `tatrman.generated.*` fragments aggregate into the `tatrman.<kind>.*` query layout at bundle build) and marked rename-event consumption post-v1 with the explicit v1 failure shape. Both from the PL-P4–P7 task-list verification pass.
- **v1 · 2026-07-09** — initial consolidation from the platform design (design.md §7/§10, decision log, option docs) + the existing contract corpus (TTR-P contracts v1.4, ttr-metadata v1.5, ttr-translator v3). §10 planning work items: resolved with ⚑ flags — FQ-2 (§14), FQ-4 (§7), EQ-1/EQ-2 (§8), IQ-2 (§12), IQ-4 (§18), GQ-4/FQ-6/FQ-7/S7 sizing (§20), F-6-β bundle-hash + lineage-citation fields (§14); explicitly deferred into their owning arcs — **CQ-4** (§1 row 22 → PL-P2 Kyklop transplant), **HQ-5** (→ H-7-β rekey arc after ⑤), **HQ-4** (→ strangler ⑥). Post-verification fix: compile record moved out of the bundle to a sidecar and the manifest's provenance block dropped — binding-dependent bytes inside the artifact would have broken B-3 hard parity (verification finding #1).
