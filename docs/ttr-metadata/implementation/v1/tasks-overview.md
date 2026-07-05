# ttr-metadata v1 — Task Management Overview

> Tracking document for the ttr-metadata implementation. Companions: [`plan.md`](./plan.md) · [`../../architecture/architecture.md`](../../architecture/architecture.md) (+ MD1–MD8, **all approved 2026-07-05**) · [`../../architecture/contracts.md`](../../architecture/contracts.md) · kantheon arc doc `kantheon/docs/architecture/fork/ttr-metadata-adoption.md`.
>
> **Status:** task lists cut 2026-07-05. 8 stage task lists · ~57 tasks. This feature closes TTR-P review items **R2** (M2) and **R3** (M0).

---

## Coder protocol

Same rules as the TTR-P lists: one list at a time, top to bottom; §Pre-flight first; check `[x]` IMMEDIATELY after each verification passes (never batch); TDD — first tasks define the tests; blocked ⇒ STOP, record under §Blockers there and mirror here; stage DONE ⇒ tick the row here; phase DONE ⇒ `progress-phase-MNN.md` + `/review`.

**Extraction-specific rules:** during M1–M4 the Ariadne core is **frozen** in kantheon (bugfixes dual-land with a tracking note — arc doc). M1 copies *from* kantheon but never edits it; kantheon edits happen only in M4. Library code never mints TTRP-* diagnostic ids (MD5).

## Phase gates & dependency map

```
M0 world grammar ─► M1 extraction ─► M2 world API ─► M4 kantheon swap (kantheon repo)
                          │               │
                          └──► M3 designer server + frontend
gates OUT: M2 ─► TTR-P P1.3 (R2) · M3 ─► TTR-P P5.1 host (MD8)
gates IN:  M4 needs kotlin-metadata/v0.1.0 published (M2.2) + consumer PAT
```

Intra-phase: T3.1.6 (`ttrm/getWorld`) needs M2.1's WorldResolver · M2.2's API-review walks TTR-P s1.3 + s2.2 · M4's RPC slimming is incremental with a grpc-suite gate per group.

## Master checklist

### Phase M0 · `schema world` grammar (TTR-M side; closes R3)
- [ ] [tasks-m0-s0.1-world-grammar.md](./tasks-m0-s0.1-world-grammar.md) — TTR.g4 `world` kind, all-target regen, writer round-trip, semantics, spec-version cut (7 tasks)
- [ ] **Phase DONE:** world fixture parses identically in TS + Kotlin (conformance); byte-stable round-trip

### Phase M1 · Core extraction
- [ ] [tasks-m1-s1.1-model-sources.md](./tasks-m1-s1.1-model-sources.md) — modules scaffold, typed model, sources (+`-git`), reconciler, de-proto qname shim (7 tasks)
- [ ] [tasks-m1-s1.2-core-port.md](./tasks-m1-s1.2-core-port.md) — resolve/graph/search/registry/refresher/export, MD2 pull-down, publish plumbing (7 tasks)
- [ ] **Phase DONE:** both modules green in CI; 24 moved specs pass; Maven Local consumable

### Phase M2 · World resolution + TTR-P API (closes R2)
- [ ] [tasks-m2-s2.1-world-resolver.md](./tasks-m2-s2.1-world-resolver.md) — fixtures home (testFixtures), WorldResolver, overlay, kind-typed resolve, erToDb chain (7 tasks)
- [ ] [tasks-m2-s2.2-fingerprint-publish.md](./tasks-m2-s2.2-fingerprint-publish.md) — fingerprint + property tests, API review vs s1.3/s2.2, `v0.1.0` publish (7 tasks)
- [ ] **Phase DONE:** TTR-P s1.3 pre-flight passes verbatim against `v0.1.0`; WLD/RES roster produces the mapped structured failures

### Phase M3 · Designer server + read-only frontend
- [ ] [tasks-m3-s3.1-server-host.md](./tasks-m3-s3.1-server-host.md) — `ttr-designer-server` host, `/ttrm` JSON-RPC, watcher→modelChanged, P5.1 installer seam (7 tasks)
- [ ] [tasks-m3-s3.2-frontend-adapter.md](./tasks-m3-s3.2-frontend-adapter.md) — ModelDataSource, WS adapter, read-only gating, acceptance script (7 tasks)
- [ ] **Phase DONE:** manual acceptance script passes (server on fixture repo → browse/graph/search → live reload)

### Phase M4 · Kantheon adoption (kantheon repo)
- [ ] [tasks-m4-s4.1-kantheon-swap.md](./tasks-m4-s4.1-kantheon-swap.md) — pin, rewrite, delete (24 specs + core pkgs), incremental RPC slimming, K3s smoke, drift guard (8 tasks)
- [ ] **Phase DONE:** Ariadne suite green on artifacts; gRPC contract byte-compatible (MD7); arc-doc checklist closed — **v1 complete**

---

## R · Review queue (drafting-pass findings)

Consciously-resolved-in-list unless marked open. Review before the affected stage.

| # | Item | Affects | Status |
|---|---|---|---|
| RM1 | **Core is not proto-free** as architecture §3 implied: `plan.v1.QualifiedName`/`SchemaCode` pervade model/source/…; `ariadne.v1` types sit in the search SPI + TraverseEdges | M1 | De-proto tasks added (qname shim T1.1.3; SearchQuery/graph enums T1.2.3–4) — library ends proto-free, as contracts require |
| RM2 | Grammar 4.0 spells the directive `model <code>`, not `schema <code>` — docs/fixtures say `schema world` | M0, TTR-P s1.3 fixtures | M0 uses `model world` (s1.3 licenses "whatever ttr-parser ships"); TTR-P fixture text follows |
| RM3 | Moved-spec count = **24** (23 core + 1 git), 14 stay — docs said ≈19 | M1/M4 | Doc correction applied (architecture/contracts/arc doc); deletion list enumerated in T4.1.3 |
| RM4 | contracts §8 fixture home must be `src/testFixtures/resources/` for cross-project consumption | M2.1 | Corrected in contracts (changelog v1.1); T2.1.1 implements |
| RM5 | `ModelStorage.listFiles` real surface is `(extensions, prefixes)`, not `(glob)` | M1.1 | Contracts §2 corrected (changelog v1.1) |
| RM6 | `extends` two-layer ambiguity: model-resolvable qname vs compiler-shipped manifest id (`postgres-16`) | M2.1/M2.2 | Shipped rule: dotted → in-model resolution, bare id → pass-through on `extendsRef`; **adjudicate in M2.2 API review** |
| RM7 | Overlay merge rule unspecified in docs | M2.1 | Specified (instance wins, type fills, lists replaced); **reviewable** |
| RM8 | `ttrm/getModelGraph` DTO ≠ the renderable ModelGraph the canvas consumes (rows, fk/relation, cardinalities) — contracts §4 "shaped after designer payloads" holds for browse, not canvas | M3 | v1: thin `ttrm-adapter.ts` (row-less boxes + on-demand getObject); richer wire nodes → C1-f; contracts changelog step in T3.1.7/T3.2.2 |
| RM9 | `MetadataRefresher`/`MetadataRegistry` signatures in contracts §2 drift from actual Ariadne code | M1.2/M4 | Moved surface wins; contracts changelog step; API-review row |
| RM10 | Ariadne builds against published ttr-* 0.8.4; in-repo parser is 4.x (`modelDirective`, `.ttrm`) + latent `LocalFsStorage.fetchVersion()` misses `.ttrm` | M1.1 | Port targets in-repo 4.x; version-hash bug fixed with spec. **Manifested 2026-07-05 as a live compile break in kantheon** (Source.kt used pre-4.0 `schemaDirective` API vs the 0.8.4 pin from kantheon `1eaaac8`): fixed same day (2 call sites → `modelDirective.modelCode`/`.schema`); M1.1 port copies the fixed file |
| RM11 | ariadne-mcp has no Search/Refresh tools — M4 smoke via grpcurl + existing mcp tools | M4 | Arc-doc smoke list adjusted in T4.1.7 |
| RM12 | TTRP-WLD id collision between TTR-P s1.3 (WLD-004) and s2.2 (WLD-002) for two-staging | TTR-P side | TTR-P amendment queue; library unaffected (MD5) |
| RM13 | Kotlin ttr-parser lacks TS-side 3.1 MD def kinds (conformance oddity) | outside scope | Flagged for a TTR-M QC look |
| RM14 | F-f-ii: world qname in-clear, excluded from hash | M2.2 | Shipped rule, **reviewable** (T2.2.1) |
| RM15 | **Kantheon Ariadne suite RED at the 0.8.4 baseline** (2026-07-05): compile fixed (RM10), but 12 fixtures + ~9 specs still speak pre-4.0 TTR (`schema <code>` directives, inline snippets, possible qname-form drift) | **blocks M1 start** | Review-and-fix checklist added to the arc doc ("Pre-arc baseline"); M1.1 pre-flight gates on `just test-kt ariadne` green + recorded baseline commit |

## Blockers register

_(mirror stage-list §Blockers entries here with date + list name)_

## TTR-P amendments (applied 2026-07-05, per plan §6)

R2/R3/R10 rows updated in TTR-P tasks-overview · s1.3 pre-flights point at `kotlin-metadata/v0.1.0` + M0 + shared testFixtures · s5.1 carries the MD8 supersession note (mount `/lsp` on `ttr-designer-server`, port 7270) · s2.2 references `ResolvedEngine/Executor.manifest` transport · TTR-P architecture §6 row annotated.
