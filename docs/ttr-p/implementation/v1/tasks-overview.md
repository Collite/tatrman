# TTR-P v1 — Task Management Overview

> The single tracking document for the v1 implementation. Companion to [`plan.md`](./plan.md) (phases/stages/DONE bars), [`../../architecture/architecture.md`](../../architecture/architecture.md) and [`../../architecture/contracts.md`](../../architecture/contracts.md) (normative specs). Decision IDs → [`../../design/00-control-room.md`](../../design/00-control-room.md).
>
> **Status:** task lists drafted 2026-07-05. 23 stage task lists · ~156 tasks.

---

## How to work these lists (coder protocol)

1. **One list at a time, top to bottom.** Open the stage's task list, run its §Pre-flight checks first — all must pass before task 1.
2. **Check every checkbox `[x]` IMMEDIATELY after its verification passes.** Never batch checkbox updates; never check a box whose Verify command you did not run. `[x]` in a progress doc = intent, not truth (`/review` verifies against runtime — CLAUDE.md cadence).
3. **TDD.** The first task(s) of each list define the tests. Write them first; they fail; subsequent tasks make them pass. Unit + component tests only — full integration testing is a separate flow (the sole sanctioned live execution is dockerized PG in Stage 3.4 CI).
4. **Blocked? STOP.** Record the blocker in that list's §Blockers section and in this file's §Blockers register. Do not improvise around a blocker.
5. When a stage's §Definition of DONE is fully checked, check its row here and note the date. When a phase completes, write `progress-phase-NN.md` beside this file and request `/review`.

## Phase gates & dependency map

```
P0 ─► P1 ─► P2 ─► P3 ─► P4 ─► P5 (A4 exit criteria met at end of P5)
            │      ▲      └──► P6 ─► P7   (P6/P7 need P4's LSP, not P5's Designer —
            │      │                       may run parallel to P5 if staffed)
            │   EXTERNAL GATE: org.tatrman:ttr-translator published
            │   (Proteus-extraction arc, kantheon repo)
            └── Stage 1.3 gate: org.tatrman:ttr-metadata consumable (see R2 below)
```

Intra-phase orderings that matter: 2.3a's T8 termination note is a **review gate** before the fixpoint is coded · 5.4 depends hard on 4.2's formatter · 7.2 completes the authoringContext schema that 4.2 finalizes.

## Master checklist

Check a row only when the list's own §Definition of DONE is fully checked.

### Phase 0 · Repo prep & scaffolding
- [x] [tasks-p0-s0.1-scaffold.md](./tasks-p0-s0.1-scaffold.md) — modules, TTRP.g4 seed, S7 rename, CI, doc hygiene (7 tasks) — **DONE 2026-07-05**

### Phase 1 · Canonical front-half → `ttrp check`
- [x] [tasks-p1-s1.1-grammar-parser.md](./tasks-p1-s1.1-grammar-parser.md) — TTRP.g4, parser wrapper, diagnostics framework (7 tasks) — **DONE 2026-07-05**
- [ ] [tasks-p1-s1.2-expressions.md](./tasks-p1-s1.2-expressions.md) — expression IR (T5 twin), typing, catalogue, S16 tables (7 tasks)
- [ ] [tasks-p1-s1.3-resolution.md](./tasks-p1-s1.3-resolution.md) — `[ttrp]` manifest, ttr-metadata, world, er→db (E-d), schemas (D-c) (7 tasks)
- [ ] **Phase DONE:** `ttrp check` passes hero + er-variant; 25+ negative fixtures named

### Phase 2 · Graph + normalizer → `ttrp explain`
- [ ] [tasks-p2-s2.1-graph.md](./tasks-p2-s2.1-graph.md) — node set (T10), ports (S10), SSA (Q7-γ), containers, control (7 tasks)
- [ ] [tasks-p2-s2.2-manifests-world.md](./tasks-p2-s2.2-manifests-world.md) — engine manifests (T6 β), world overlay, capability check, invocation bindings (F-c) (7 tasks)
- [ ] [tasks-p2-s2.3a-rewrite-core.md](./tasks-p2-s2.3a-rewrite-core.md) — T8 engine + termination note (**review gate**), sugar + lowering strata, property tests (6 tasks)
- [ ] [tasks-p2-s2.3b-movement-collapse.md](./tasks-p2-s2.3b-movement-collapse.md) — fission, T5-b escalation, movement synthesis, collapse + waves, `ttrp explain` goldens (6 tasks)
- [ ] **Phase DONE:** hero `ttrp explain` shows the F-lite island/wave/movement structure; rewriter property-tested

### Phase 3 · Emit, bundle, run — **the hero runs**
- [ ] **EXTERNAL GATE:** `org.tatrman:ttr-translator` resolvable (Maven Local OK) — coder STOPS if absent
- [ ] [tasks-p3-s3.1-sql-emit.md](./tasks-p3-s3.1-sql-emit.md) — ttr-translator integration, CTE-per-node (E-b), golden SQL corpus (7 tasks)
- [ ] [tasks-p3-s3.2-polars-emit.md](./tasks-p3-s3.2-polars-emit.md) — straight-line script + inline prelude (E-c), transfers, golden Python corpus (6 tasks)
- [ ] [tasks-p3-s3.3-bundle-executor.md](./tasks-p3-s3.3-bundle-executor.md) — bundle §5, manifest.json, world fingerprint, run.sh, `ttrp` CLI (S2) (6 tasks)
- [ ] [tasks-p3-s3.4-conformance.md](./tasks-p3-s3.4-conformance.md) — `ttrp-conform` (S3/Q9 seven points), placement variants, CI + dockerized PG (6 tasks)
- [ ] **Phase DONE:** A4 core holds for canonical authoring — one program, two engines, identical results

### Phase 4 · LSP + VS Code
- [ ] [tasks-p4-s4.1-lsp-core.md](./tasks-p4-s4.1-lsp-core.md) — LSP4J stdio server, diagnostics, hover, definition, SSA-aware rename (7 tasks)
- [ ] [tasks-p4-s4.2-formatter-methods.md](./tasks-p4-s4.2-formatter-methods.md) — formatter (C3 style, C2-f untouchable interiors), `ttrp/*` methods, **authoringContext schema finalized** (7 tasks)
- [ ] [tasks-p4-s4.3-vscode-ext.md](./tasks-p4-s4.3-vscode-ext.md) — new `packages/ttrp-vscode-ext`, TextMate, client wiring, run/build commands (7 tasks)
- [ ] **Phase DONE:** hero editable in VS Code — live diagnostics, format-on-save, one-click run

### Phase 5 · Designer server + graphical surface — **A4's second surface**
- [ ] [tasks-p5-s5.1-server-transport.md](./tasks-p5-s5.1-server-transport.md) — Ktor WS-LSP host (S24 loopback), getGraph/getWorld (7 tasks)
- [ ] [tasks-p5-s5.2-ttrl-viewstate.md](./tasks-p5-s5.2-ttrl-viewstate.md) — `.ttrl` grammar (TTR-M-hosted), ζ keys, orphaning, deterministic auto-layout (7 tasks)
- [ ] [tasks-p5-s5.3-canvas.md](./tasks-p5-s5.3-canvas.md) — designer fork, two-level view, skins, fragment drill-ins (7 tasks)
- [ ] [tasks-p5-s5.4-edit-run.md](./tasks-p5-s5.4-edit-run.md) — β edit vocabulary → applyGraphEdit, property panel, run + Arrow render, hero-on-canvas acceptance (8 tasks)
- [ ] **Phase DONE:** hero built from empty canvas, run, rendered — **v1 A4 exit criteria fully met**

### Phase 6 · Fragment dialects
- [ ] [tasks-p6-s6.1-ttr-sql.md](./tasks-p6-s6.1-ttr-sql.md) — TTRSql.g4 (C2-b cut), clause→node decomposition, reject table (7 tasks)
- [ ] [tasks-p6-s6.2-ttr-pandas.md](./tasks-p6-s6.2-ttr-pandas.md) — TTRPandas.g4 (S17 roster, S9 `==`), decomposition, reject table (7 tasks)
- [ ] [tasks-p6-s6.3-bare-fragments.md](./tasks-p6-s6.3-bare-fragments.md) — wrapper synthesis, markers, scope (C2-d), **byte-identical graph-identity gate** (7 tasks)
- [ ] **Phase DONE:** `ttrp-conform` passes the hero authored three ways; drill-ins render

### Phase 7 · TTR-B + assist finalization
- [ ] [tasks-p7-s7.1-ttrb-grammar.md](./tasks-p7-s7.1-ttrb-grammar.md) — TTRB.g4 (C4-b roster), verbose skin (C4-c), sentence→node, reject table (7 tasks)
- [ ] [tasks-p7-s7.2-assist-eval.md](./tasks-p7-s7.2-assist-eval.md) — authoringContext complete, reference host demo, eval corpus + baseline (7 tasks)
- [ ] **Phase DONE:** bare `.ttrb` hero parses/compiles/runs; eval baseline recorded — **v1 complete**

---

## R · Pre-implementation review queue

Items the drafting pass surfaced that need **Bora's decision or doc updates** — resolved-in-list where noted, but consciously, not by drift. Review before (or during) the affected stage.

| # | Item | Affects | Status in lists |
|---|---|---|---|
| R1 | plan.md Stage 0.1 says "antlr-ng" for TTRP.g4, but TTR-P is Kotlin-only (G-b) — Kotlin side uses the Gradle ANTLR plugin (ttr-parser precedent) | P0 | Followed Gradle-ANTLR; plan wording stale |
| R2 | ~~`ttr-metadata` does not exist in this repo~~ | **P1.3 hard gate** | **SCHEDULED (approved 2026-07-05):** `docs/ttr-metadata/` feature, Phase M2 delivers `kotlin-metadata/v0.1.0` — s1.3 pre-flight updated to gate on it |
| R3 | ~~`schema world` grammar unscheduled~~ | P1.3 | **SCHEDULED (approved 2026-07-05):** `docs/ttr-metadata/` plan Phase M0 (MD4). Note RM2: grammar 4.0 spells it `model world` |
| R4 | Container port signature: hero writes `err rejects` (an `err`-kind port named `rejects`) vs C3-f's two distinct reserved ports | P1.1/P2.1 | Grammar follows hero; conscious call due Stage 2.1 |
| R5 | S14 ("engine-crossing movement synthesized-only") vs C3-d-iv ("explicit Transfer available for control") | P2.3b | Followed S14 (later decision); authored Transfer never required |
| R6 | FF rejection: contracts pin `TTRP-CTL-001` at graph construction; F-b frames it capability-shaped (manifest-time) | P2.1 | Followed contracts (CTL-001, Stage 2.1) |
| R7 | Engine-manifest serialization format is not fixed by contracts — lists propose JSON with pinned schemas | P2.2 | Marked reviewable choice in s2.2 |
| R8 | contracts §5 clarifications needed: (a) `schemas/*.json` full-schema vs fingerprint wording; (b) manifest.json cannot self-hash in `files{}`; (c) new `TTRP-EMT-*` area; (d) SSA→SQL identifier mangling rule (`accounts#2`→`accounts_2`) | P3, contracts changelog | Specified in-list; contracts entries queued |
| R9 | ttr-translator API granularity (whole-island unparse vs per-node drive) + `plan.v1` `.pb` emission timing — depends on the kantheon-side extraction arc | P3.1/P3.3 | Both paths specified; `.pb`-only blocker scoping |
| R10 | ~~`ttrp-designer-server` module missing from the P0 module roster~~ | P5.1 | **SUPERSEDED by MD8 (approved 2026-07-05):** host module is **`ttr-designer-server`**, created by ttr-metadata Phase M3.1; P5.1 mounts the TTR-P WS-LSP at `/lsp` on that host (see s5.1 header note) |
| R11 | contracts §4 additions needed: `autoLayout` field on getGraph; a loopback HTTP route for `out/` bytes (browser can't watch files) | P5, contracts changelog | Specified in-list; changelog entries queued |
| R12 | `.ttrl` orphaning rule needs recorded chain-length per name group — not in the C1-c inventory | P5.2 | Added (recorded-length variant) + changelog entry |
| R13 | TTR-SQL keyword case-sensitivity vs the lowercase S16 shared table — unstated | P6.1 | Blocker flag in T6.1.3; no silent fork allowed |
| R14 | Bare-fragment wrapper synthesis shape (derived in-ports + program-level Loads) is derived, not ratified | P6.3 | Flagged for `/review` sign-off |
| R15 | Diagnostic area additions to contracts §8: `FRG`, `EMT`, `LAY` | P4–P6 | Changelog-entry steps in the lists |
| R16 | Doc-hygiene naming drift: `pl/*`→`ttrp/*`, `[pl]`→`[ttrp]`, `assistContext`→`authoringContext`, `pl-conform`→`ttrp-conform` in older design docs | P0 doc hygiene | Contracts spellings used everywhere |

## Blockers register

_(coder: mirror any §Blockers entry here with date + list name)_

## Cross-cutting & external (from plan.md — not in any stage list)

- [ ] **Proteus-extraction arc** (`org.tatrman:ttr-translator`, S25 plan.v1 vendoring) — planned and executed in the **kantheon repo**; gates Phase 3
- [ ] Fork-ops residue (old-repo freeze README, `~/Dev/tatrman` → `tatrman-poc`) — anytime, trivial
- [ ] TTR-M `.ttrl` migration + Designer-server convergence + `modeler/*`→`ttrm/*` (C1-f) — **post-v1**, explicitly not here
- [ ] Erroneous-rows-in-SQL producer semantics — v1.x design session
- [ ] F proper, events, FF, retries, optimizer Z, md-sugar (D-h) — v2 register (architecture §10)
