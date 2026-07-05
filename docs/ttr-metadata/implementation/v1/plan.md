# ttr-metadata v1 — Implementation Plan

> **Status:** designed 2026-07-05, awaiting review. Planning conventions apply (task ≈ ½–1 day · stage ≈ 6 tasks, ships something testable · phase ships something usable). Companions: [`../../architecture/architecture.md`](../../architecture/architecture.md) + [`../../architecture/contracts.md`](../../architecture/contracts.md). Feature decisions MD1–MD8 in architecture §8; TTR-P decision IDs → `../../../ttr-p/design/00-control-room.md`.
>
> Per-stage task lists land beside this file as `tasks-m<phase>-s<phase.stage>-<short>.md` — **written 2026-07-05** (8 lists, ~57 tasks). Tracking + coder protocol + review queue: [`tasks-overview.md`](./tasks-overview.md). MD1–MD8 approved 2026-07-05. TDD throughout.

## v1 exit criteria

1. `org.tatrman:ttr-metadata` (+ `-git`) published; **TTR-P Phase 1.3's pre-flight passes** (R2 closed) — model repo + world doc resolved offline from a fixture project.
2. `schema world` parses across ttr-parser/writer/semantics (R3 closed) with a grammar-master spec-version cut.
3. `ttr-designer-server --repo <path>` serves the TTR-M model of that repo over `ttrm/*`; the existing Designer renders it read-only (browse + graph + search + live `modelChanged`).
4. Kantheon's Ariadne runs on the published artifacts with its full remaining Kotest suite green and its gRPC contract byte-compatible (MD7); moved core code deleted from kantheon.

## Phase map & dependencies

```
M0 world grammar (TTR-M side) ─► M1 core extraction ─► M2 world resolution + TTR-P API ─► M4 kantheon adoption
                                        │                        │                        (kantheon repo)
                                        └────────► M3 designer server + frontend adapter
external gates OUT: M2 ─► TTR-P P1.3 (R2) · M3 ─► TTR-P P5.1 (host exists, MD8)
```

M1 needs only ttr-parser/semantics as published/in-repo modules (exist). M3 needs M1 (core) and profits from M2 (`ttrm/getWorld`) but can start on M1. M4 needs the first published tag (after M2; M3 not required for kantheon).

---

## Phase M0 · `schema world` grammar (TTR-M side; resolves R3 — MD4)

**Deliverable:** world docs parse, render, and validate across the TTR-M toolchain.

- **Stage M0.1 · Grammar + toolchain** — `TTR.g4`: `world` schema code; `def world` with nested `def engine`/`def executor`/`def storage`/`def schema`, `extends`, `hosts: [pkg]`, `staging:` flag, free-form manifest property blocks (T6 data transported, not interpreted); regen TS + Kotlin + TextMate per CLAUDE.md; ttr-writer round-trip; ttr-semantics kind registration + validators (nested-def scoping, `hosts` package refs resolve, one `staging: true` per world — warning-level here, hard error stays in WorldResolver per MD5); golden fixture = the `acme.worlds.dev` doc from TTR-P s1.3's fixture design; spec-version cut via `docs/grammar-master/new-grammar-version-process.md` (S6); conformance corpus entries so TS/Kotlin/Python parsers agree.
  - *Pre-flight:* grammar-master process doc read; TTR-M suites green at baseline. *DONE:* world fixture parses in TS + Kotlin parsers with identical ASTs (conformance harness); round-trips byte-stable through ttr-writer.

## Phase M1 · Core extraction

**Deliverable:** `:packages:kotlin:ttr-metadata` + `:ttr-metadata-git` building in tatrman with Ariadne's core logic + its ≈19 core specs green; `publishToMavenLocal` works.

- **Stage M1.1 · Module scaffold + typed model + sources** — Gradle modules (deps per contracts §1; NO ktor/grpc/otel/jgit in core — enforced by a dependency-rules test); port `model/` + `source/` (minus GitArchiveStorage) + `reconcile/` with package rename `org.tatrman.kantheon.ariadne.*` → `org.tatrman.ttr.metadata.*`; port their specs; `LocalFsStorage` pointed at a tatrman-style model repo fixture (modeler.toml + packages) — Ariadne's model-ttr seed becomes a second fixture.
- **Stage M1.2 · Resolve + graph + search + registry + refresher + export** — port remaining core packages + specs; **MD2 pull-down**: extract ListObjects filtering/paging logic out of `MetadataServiceImpl` into `MetadataQuery` (new component tests pin the moved behaviors: filter semantics, page windows, fuzzy-attribute mapping — mirror the grpc-spec cases as library cases); `PUBLISHING.md` rows + `kotlin-metadata/v*` tag wiring; `-Pversion=0.0.1-LOCAL publishToMavenLocal` smoke.
  - *Phase pre-flight:* M0 done (world fixtures parse — M1 fixtures include world docs even though resolution lands in M2). *DONE:* both modules build/test green in tatrman CI; a scratch consumer project resolves `org.tatrman:ttr-metadata:0.0.1-LOCAL` from Maven Local.

## Phase M2 · World resolution + TTR-P-facing API — **closes R2**

**Deliverable:** the API TTR-P Stage 1.3 consumes, proven against the shared fixture project.

- **Stage M2.1 · WorldResolver + ResolvedWorld** — instance-`extends`-type overlay; exactly-one-staging (D-f) + `hosts:` mapping (D-d-i) + structured failures (contracts §3); kind-typed `resolve(qname, expectedKind)` with `KindMismatch(expected, found)` (D-b support, MD5 id-free); `erToDb` binding traversal returning the full provenance chain (E-d support); fixture home established per contracts §8 (`java-test-fixtures`, shared to ttrp-frontend later).
- **Stage M2.2 · Fingerprint + hardening** — semantic world fingerprint (contracts §5) with stability/sensitivity property tests; load-issue taxonomy finalized; API-shape review against TTR-P s1.3/s2.2 task lists (walk both lists, tick every "consumes ttr-metadata" step against the real API — divergences fixed here or recorded as TTR-P task-list amendments in §6); publish first real tag `kotlin-metadata/v0.1.0`.
  - *DONE:* TTR-P s1.3 pre-flight items R2/R3 pass verbatim against `v0.1.0`; the WLD/RES negative roster (s1.3 fixture design) produces the right structured failures.

## Phase M3 · Designer server + read-only frontend

**Deliverable:** the hero demo — `ttr-designer-server --repo <fixture>` + Designer in the browser rendering the TTR-M model read-only with live reload.

- **Stage M3.1 · Server host** — `:packages:kotlin:ttr-designer-server`: Ktor CIO, loopback bind (S24), `--repo`/`--port`; embeds MetadataLoader/Registry over LocalFsStorage; WS JSON-RPC endpoint `/ttrm` (contracts §4: getModelIndex, getModelGraph, getObject, search, getStatus, refresh); file watcher → refresher → `ttrm/modelChanged`; WS contract Kotest suite (in-process client); `ttrm/getWorld` once M2 lands.
- **Stage M3.2 · Frontend adapter** — `ModelDataSource` interface in packages/designer (contracts §6); wrap the existing worker path as `WorkerLspDataSource` (no behavior change — Vitest regression pins); `WsDesignerServerDataSource` + explicit selection (P2: no sniffing); `capabilities.edit=false` hides edit affordances; Vitest suites against canned `ttrm/*` payloads; manual acceptance script (checkboxed): start server on fixture repo → browse → graph → search → touch a `.ttrm` → canvas updates.
  - *DONE:* acceptance script passes; TTR-P P5.1's "host exists" gate satisfied (MD8).

## Phase M4 · Kantheon adoption (planned here, **executed in the kantheon repo**)

**Deliverable:** Ariadne on the published artifacts; moved code deleted; contract frozen (MD7).

- **Stage M4.1 · Swap + delete** — pin `v0.1.x` in kantheon `libs.versions.toml`; rewrite imports to `org.tatrman.ttr.metadata.*`; delete moved packages + moved specs; `MetadataServiceImpl` bodies become conversion + `MetadataQuery` delegation; `RefreshScheduler` drives the library refresher; grpc-layer suite green; `just build-kt ariadne && just test-kt ariadne`; deploy to local K3s, smoke ListObjects/Search/GetModel/ResolveArea via ariadne-mcp.
  - *Pre-flight:* `kotlin-metadata/v0.1.0` on GitHub Packages; kantheon consumer PAT per its conventions. *DONE:* Ariadne suite green; image runs; a `git grep` guard proves no `ariadne` copy of moved core classes remains; arc doc checklist closed (`kantheon/docs/architecture/fork/ttr-metadata-adoption.md`).

---

## §6 Plan impacts on TTR-P v1 (apply when task lists are cut)

| TTR-P item | Amendment |
|---|---|
| tasks-overview §R2, §R3 | point at this plan (M2, M0); flip to "scheduled" |
| tasks-p1-s1.3 pre-flights | ttr-metadata gate → `kotlin-metadata/v0.1.0` (or Maven Local); world-grammar gate → M0 DONE; fixture project consumed via test-fixtures from ttr-metadata (contracts §8), not duplicated |
| tasks-overview §R10 + tasks-p5-s5.1 | module is **`ttr-designer-server`** (MD8), created by M3.1 — P5.1 mounts the TTR-P WS-LSP at `/lsp` on this host instead of scaffolding `ttrp-designer-server` |
| tasks-p2-s2.2 | manifest *content* rides `ResolvedEngine/Executor.manifest` (contracts §3); Stage 2.2 owns the format + interpretation |
| TTR-P architecture §6 | "ttr-metadata (exists, shared with Ariadne)" becomes true at M2; note `-git` split |

## Cross-cutting & external

| Item | Where | Gates |
|---|---|---|
| Ariadne core freeze (no new core features in kantheon during M1–M4; bugfixes land twice with a tracking note) | kantheon repo, announce at M1 start | drift risk |
| ttr-translator extraction arc (QueryParseWorker's query-translator dep) | separate arc (TTR-P P3 gate) | not this feature |
| C1-f TTR-M editing convergence + `.ttrl` | post-v1, converges onto M3's server | — |
| Designer-server auth / multi-user | v2 (S24 holds for v1) | — |

## Progress tracking

Per-phase `progress-phase-MNN.md` beside this file; `/review` cadence applies ( `[x]` = intent, not truth).
