# TTR-P v1 — Implementation Plan

> **Status:** consolidated 2026-07-04. Follows the planning conventions (task ≈ ½–1 day · stage ≈ 6 tasks, ships something testable · phase ships something deployable/usable). Companions: [`../../architecture/architecture.md`](../../architecture/architecture.md) + [`../../architecture/contracts.md`](../../architecture/contracts.md). Decision IDs → [`../../design/00-control-room.md`](../../design/00-control-room.md).
>
> Per-stage task lists land beside this file as `tasks-p<phase>-s<phase.stage>-<short>.md` — **written 2026-07-05** (23 lists; Stage 2.3 split into 2.3a/2.3b). Tracking + coder protocol + review queue: [`tasks-overview.md`](./tasks-overview.md). TDD throughout: each stage's first tasks define its tests.

## v1 exit criteria (A4)

The hero scenario (accounts@PG + sales@Polars, join → aggregate → branch, error path) authored in **≥2 surfaces** (canonical text + graphical canvas; fragments/TTR-B as bonus surfaces), compiling to **one graph**, executing across **PG + Polars via bash**, producing **identical results** under `ttrp-conform` — plus the er-flavored hero variant resolving through the binding tier.

## Phase map & dependencies

```
P0 repo prep ─► P1 front-half ─► P2 graph+normalize ─► P3 emit+run ─► P4 LSP+VS Code ─► P5 Designer ─► P6 fragments ─► P7 TTR-B+assist
                                        │                   ▲
                 Proteus-extraction arc (Phase A, tatrman) ──┘   external gate: ttr-translator published ✅ v0.8.0
```

Prototype order honors C0-f (canonical → graphical → fragments → NL). P6/P7 may start once P4 is done if P5 staffing allows — they depend on the LSP, not the Designer.

---

## Phase 0 · Repo prep & scaffolding

**Deliverable:** empty-but-building TTR-P module skeleton in the monorepo; CI green; naming debt cleared.

- **Stage 0.1 · Scaffold + hygiene** — Kotlin modules `packages/kotlin/ttrp-{frontend,graph,emit,lsp,cli,conform}` wired into the Gradle build + version catalog; `@modeler/*` → `@tatrman/*` npm scope rename (S7); `TTRP.g4` seed grammar beside `TTR.g4` + antlr-ng generation task; CI job (build + test all new modules); stale §Open markers in design docs 02/03/05/06/07/08 annotated "resolved — see decision log" (doc hygiene); publish plumbing rows for the new artifacts in `PUBLISHING.md`.
  - *Pre-flight:* none. *DONE:* `./gradlew build` green including empty ttrp modules; npm workspaces renamed; CI runs on PR.

## Phase 1 · Canonical front-half

**Deliverable:** `ttrp check <file>.ttrp` — parse + resolve + typecheck the canonical hero with full named diagnostics; no graph semantics yet beyond construction.

- **Stage 1.1 · Grammar + parser** — `TTRP.g4` (γ-hybrid statements, `->`, named args + config blocks, containers, control keywords, tagged blocks lexed opaque, S10 port names, no program header per S12); Kotlin parser wrapper (parseString/parseFile, trivia attach — fragment interiors verbatim per C2-f); golden parse corpus incl. the hero; error-recovery baseline; diagnostic framework (`TTRP-<AREA>-<NNN>`, suggested-alternative field); `=`/`==` rule (S9).
- **Stage 1.2 · Expression grammar** — the one PL expression IR (T5: Expression twin, AggregateCall arm, explicit Cast, 3VL typing rules); expression concrete grammar embedded in TTRP.g4; catalogue-id function resolution (T5-c interface); static typing + coercion checks; shared keyword/operator table (S16); expression golden tests.
- **Stage 1.3 · Resolution** — `[ttrp]` manifest reader (all §2 contract keys incl. `default-imports`, S18); ttr-metadata embedding: model repo + world doc resolution (D-g, offline); qname/import resolution with position-typing (D-b); er→db early rewrite with provenance (E-d); declared-schema handling (D-c precedence, S23 types); world position checks (`target`/`load`/`store`/`schema:`).
  - *Phase pre-flight:* ttr-metadata artifact consumable from the TTR-M side of the repo. *DONE:* `ttrp check` passes the hero + er-variant; 25+ curated negative fixtures produce their named diagnostics.

## Phase 2 · Graph + normalizer

**Deliverable:** `ttrp explain` — normalized graph, placements, applied rewrites, island→payload map for the hero (no emit yet).

- **Stage 2.1 · Graph construction** — node set (T10 roster), typed ports + err/rejects, SSA variable desugar (Q7-γ), containers with port mapping, control edges FS/SS (FF = capability error), acyclicity + single-in checks, Display semantics (Q11).
- **Stage 2.2 · Manifests + world binding** — engine-type manifest format + PG/Polars/bash manifests (T6 β entries); world instance `extends` overlay; capability check (node + function granularity); invocation-binding resolution table (F-c); staging feasibility check (D-f).
- **Stage 2.3 · Rewrite engine (T8)** — sugar expansion stratum (Select/Calc/HAVING/Distinct); capability lowering (Branch→Filter etc.); T5-b escalation at node granularity (split-with-warning, `[ttrp] split-policy`); movement synthesis Store+Transfer+Load (C3-d-iv, `via` override); **termination measure + node-fission rules specified and tested** (the named B work items); container-collapse derivation of the execution graph + wave computation.
  - *DONE:* `ttrp explain` on the hero shows the exact island/wave/movement structure F-lite promised; rewrite engine property-tested for termination and determinism.

## Phase 3 · Emit, bundle, run — **the hero runs**

**Deliverable:** `ttrp build` + `ttrp run`: the hero end-to-end on PG + Polars via wave-parallel bash; `ttrp-conform` green.

- **Stage 3.1 · SQL emit** — ttr-translator integration (island → RelNode → PG dialect; CTE-per-node with SSA names, E-b; NULLS LAST, Q9-3); golden SQL corpus (home: `ttrp-emit/src/test/golden/`, per-dialect).
- **Stage 3.2 · Polars emit** — straight-line script + generated inline prelude (3VL/decimal/datetime enforcement only-as-needed, E-c); golden Python corpus; transfers as generated ADBC/connectorx scripts, Arrow IPC staging (F-c-i).
- **Stage 3.3 · Bundle + executor** — `<program>.bundle/` assembly per contracts §5 (manifest.json, sha256s, semantic world fingerprint F-f-ii); `run.sh` generation (waves, `wait -n`, exit 0/1/2, `TTR_CONN_*` pre-flight, wipe-on-restart); display file drops; `ttrp` CLI wiring (S2).
- **Stage 3.4 · Conformance** — `ttrp-conform` (S3): invoker contract per contracts §9, seven-point comparison, PG↔Polars placement-variant runs of the hero; wire as the emit regression gate in CI (dockerized PG).
  - *Phase pre-flight (EXTERNAL GATE — ✅ SATISFIED 2026-07-06):* **`org.tatrman:ttr-translator` published** (`kotlin-translator/v0.8.0` on GitHub Packages, lockstep with `ttr-plan-proto`; `ttr-plan-proto` wheel on PyPI) via the ttr-translator extraction arc **Phase A (tatrman)** — see `docs/ttr-translator/`. In-repo consumers (ttrp-emit) use the `:packages:kotlin:ttr-translator` project dep. Kantheon **Phase B** (adopt + delete) is off this critical path (TR-8). *DONE:* A4 core holds for canonical authoring — one program, two engines, identical results.

## Phase 4 · LSP + VS Code

**Deliverable:** the editing experience — stdio LSP consumed by a thin VS Code extension.

- **Stage 4.1 · LSP core** — server skeleton (stdio), didOpen/didChange + diagnostics streaming from the front-half; hover (types, er provenance), definition, rename (SSA-aware, sidecar-atomic groundwork for ζ).
- **Stage 4.2 · Formatter + methods** — formatter (γ-style rules from C3; fragment interiors untouched, C2-f); `ttrp/transpile`, `ttrp/run`, `ttrp/explain`, `ttrp/validate`, `ttrp/authoringContext` v1 (bundle schema finalized here — C4 leftover); document versioning discipline.
- **Stage 4.3 · VS Code ext** — language registration (`.ttrp` + dialect extensions), TextMate grammar, LSP client wiring, run/build commands; integration tests via the paired-connection harness pattern.
  - *DONE:* hero editable in VS Code with live diagnostics, format-on-save, one-click run.

## Phase 5 · Designer server + graphical surface — **A4's second surface**

**Deliverable:** the hero buildable on canvas (C1-d bar) against a repo-attached Designer server.

- **Stage 5.1 · Server + transport** — Designer server (JVM, repo-attached, loopback-only no-auth per S24); WS-LSP transport sharing the Phase-4 server; `ttrp/getGraph` + `ttrp/getWorld`.
- **Stage 5.2 · `.ttrl` + view state** — `.ttrl` grammar in ttr-parser (TTR-M-hosted, C1-c); sidecar read/write (`ttrp/getLayout`/`setLayout`, wholesale rewrite); ζ key computation + deterministic orphaning + pair-integrity diagnostics; binary auto/manual layout with deterministic auto algorithm.
- **Stage 5.3 · Canvas render** — Designer frontend fork; two-level view (orchestration = derived execution graph + leaves; drill-in recursion); skin roster v1 (Alteryx/KNIME + Enso, per-canvas); fragment drill-ins as read-only derived sub-graphs (auto-only).
- **Stage 5.4 · Edit + run** — β edit vocabulary → `ttrp/applyGraphEdit` → formatter-owned WorkspaceEdits; textual property panel; `ttrp/run` + `out/` watch + Arrow render (C1-e); canvas diagnostics placement (C1 leftover — decide in-stage: node badges at both levels).
  - *DONE:* hero built from empty canvas, run, results rendered; **v1 A4 exit criteria fully met at the end of this phase.**

## Phase 6 · Fragment dialects (TTR-SQL, TTR-pandas)

**Deliverable:** the hero's SQL island authored as `"""sql` and as a bare `.ttr.sql` program; a TTR-pandas island equivalent; identical graphs to canonical authoring.

- **Stage 6.1 · TTR-SQL** — `TTRSql.g4` (C2-b workhorse cut; S15 ordered-LIMIT rule; S16 shared tables); clause→node decomposition (CTE=SSA labels); reject table with suggested alternatives (`TOP`→LIMIT etc.); `SELECT *` static expansion; EXISTS/IN→semi/anti desugar.
- **Stage 6.2 · TTR-pandas** — `TTRPandas.g4` (method-chain, S17 roster, `==` synonym per S9); statement/SSA decomposition; reject table (`.apply`, lambdas, IO).
- **Stage 6.3 · Bare-fragment programs** — wrapper synthesis from `[ttrp]` defaults (target/shell/display/default-imports); marker handling (extension + comment override); document-scope resolution inside fragments (C2-d, shadowing order); graph-identity tests: bare vs embedded vs canonical hero → byte-identical normalized graphs.
  - *DONE:* `ttrp-conform` passes the hero authored three ways; drill-in rendering of fragment containers works in the Designer.

## Phase 7 · TTR-B + assist finalization

**Deliverable:** the full C0-g surface roster shipped; assist contracts exercised by a reference host.

- **Stage 7.1 · TTR-B grammar** — `TTRB.g4` (C4-b roster, anaphora, `as`-naming; `#` comments per S19; English-only per S20); verbose expression skin as closed synonym table (C4-c); sentence→node decomposition; reject table (out-of-roster → repair suggestions).
- **Stage 7.2 · Assist + eval** — `ttrp/authoringContext` content complete (diagnostics catalogue included); reference host demo (VS Code command or CLI driving generate→validate→repair with a user-supplied model); assist/agent eval corpus (NL → expected graph shape) wired `ttrp-conform`-adjacent; cursor-scoped dialect insertion (C4-d-i γ).
  - *DONE:* bare `.ttrb` hero parses, compiles, runs; eval corpus baseline recorded; **v1 complete.**

---

## Cross-cutting & external

| Item | Where | Gates |
|---|---|---|
| Proteus-extraction arc (ttr-translator; plan.v1 ownership per S25/TR-3) | **✅ Phase A DONE (tatrman)** — `kotlin-translator/v0.8.0` + `ttr-plan-proto` wheel published 2026-07-06; kantheon Phase B (adopt+delete) pending, non-blocking | Phase 3 — **gate open** |
| TTR-M `.ttrl` migration + Designer-server convergence + `modeler/*`→`ttrm/*` rename | **post-v1 arc** (C1-f) — explicitly NOT in this plan | — |
| Fork-ops residue (old-repo freeze README, `~/Dev/tatrman`→`tatrman-poc`) | anytime, trivial | — |
| Erroneous-rows-in-SQL producer semantics (unlocks C2-e-β reject taps) | v1.x design session | — |
| F proper, events, FF, retries, optimizer Z, md-sugar (D-h) | v2 register (architecture §10) | — |

## Progress tracking

Per-phase progress docs `progress-phase-NN.md` beside this file; the `/review` cadence applies (reviews verify claims against runtime; `[x]` = intent, not truth).
