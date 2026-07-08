# MD dot-path sugar — architecture

**Status:** v1 draft, 2026-07-08 · Companion to [`../dot-path-sugar.md`](../dot-path-sugar.md)
(the design note; all decisions 1–19 cited from there), [`contracts.md`](contracts.md), and
[`plan/implementation-plan.md`](plan/implementation-plan.md).

**Arc identity:** this is its **own feature arc** ("md-sugar"), not part of TTR-P v1 and not part
of the MD Layer A plan. It fills the reserved seams: TTR-P deferral **D-h** (md ref-kind package
path + md-catalog catalogue seat), design-space branch **D3**, and `design.md` §11's dot-notation
half of Layer B. Scheduling is deliberately open; pre-flight conditions are in the plan.

## 1. Scope

Five slices, **Kotlin-first** (the TS/LSP side — editor hovers, Designer rendering — is a later
arc and appears here only as a seam):

1. **Resolver core** — order-free selector tokens → canonical path; ambiguity machinery;
   explanation records. Pure library, engine- and surface-independent.
2. **TTR-P read paths** — grammar changes + checker integration: MD paths in expression position,
   precedence vs input columns, shape/broadcast typing, lowering of reads to `plan.v1`.
3. **Writeback & cubelet statements** — slice assignments (strict LHS, context overlay,
   pinned-grain writes, explicit spread) **and** the statement family over whole cubelets
   (D20–D26): `C = e` virtual, `C := e with {…}` materialize (+ generated-`.ttrm` emission),
   `C += e` merge, `C -= e` delete; journaling modes with `semantics{}`-role technical columns.
4. **Metadata server / connected mode** — member catalog capability in `ttr-metadata`, served over
   the existing `ttrm/*` protocol; connected/disconnected degradation ladder.
5. **Agent resolver service** — an MCP server exposing resolution to the planning agent / NL
   front-end.

Out of scope for this arc: the full Layer B transformation algebra (filter/aggr/join/map/store as
a *user* surface — reads/writes here lower to existing TTR-P nodes), MOLAP, query-backed cubelets,
measure tuples, `with`-context-blocks, safe navigation, per-expression `asof`, scientific
notation, TS/LSP tooling, Designer support, allocation strategies beyond those declared in
bindings, delete-markers for diff journaling.

## 2. Component view

```
                 packages/grammar/src/TTRP.g4  (+ TTR.g4: domain `publish members`)
                      │ (antlr-ng / Gradle ANTLR / reference jar — existing pipeline)
                      ▼
                 ttr-parser (TS · Kotlin · Python — mechanical, conformance-guarded)
                      │
                      ▼
   ┌──────────── ttr-semantics (Kotlin) ─────────────┐
   │  NEW md subpackage: MD symbol graph, grain      │   port of the TS Layer A subset the
   │  lattice, defaults, calc-catalog wiring         │   resolver needs (spec: md plan 2A/2B/2E)
   └──────────────────────┬──────────────────────────┘
                          ▼
                ttr-md-resolver (NEW Kotlin lib, published)
                token classification · constraint search · canonical form
                shape inference · explanation · ambiguity alternatives
                MemberCatalog interface (impl-agnostic)
                   │                │                     ▲
                   ▼                ▼                     │ members
        ttrp-frontend        ttr-md-agent (NEW)      ttr-metadata (+MemberCatalog
        checker: precedence, MCP server: md_resolve, capability) ⇄ ttr-designer-server
        typing, strict LHS,  md_explain,             (ttrm/getMemberDomains,
        context overlay,     md_list_members         ttrm/getMembers)
        TTRP-MD-* diags
                   │
                   ▼
        ttr-translator (Calcite): canonical path → plan.v1
        reads: Filter/Join/Aggregate over md2db bindings
        writes: Store + journaling (+ declared spread strategy)
```

Dependency arrows stay one-way and follow the existing graph: grammar → parser → semantics →
(resolver) → frontend/translator; `ttr-md-agent` and `ttr-metadata` sit beside, never above.
Kantheon consumes published artifacts only (`org.tatrman:ttr-md-resolver`), as always.

## 3. Module placement

| module | new? | role | published |
|---|---|---|---|
| `packages/grammar` (TTRP.g4, TTR.g4) | changed | NUMBER→INT, `floatLiteral`, `mdPath`, `DOTDOT`, braces sets; TTR-M `publish members` domain property | via grammar version (grammar-master process) |
| `packages/kotlin/ttr-semantics` | extended | `org.tatrman.ttr.semantics.md`: MD symbol graph, grain lattice (leaf = no N:1 map targets it), cubelet grain, default measure/agg, attribute→domain-map sugar, calc-catalog entries | yes (existing artifact) |
| `packages/kotlin/ttr-md-resolver` | **new** | the resolver core (§4 of contracts). Deps: ttr-semantics, ttr-parser. **No Ktor, no MCP SDK, no Calcite.** | yes — `org.tatrman:ttr-md-resolver` |
| `packages/kotlin/ttrp-frontend` | extended | expression checker: path recognition, precedence, shapes, strict LHS, context overlay; `TTRP-MD-*` diagnostics; `asof` compile-time parameter | (as today) |
| `packages/kotlin/ttr-translator` | extended | lowering canonical paths to `plan.v1` (reads and writes) | yes (lockstep `kotlin-translator/v*`) |
| `packages/kotlin/ttr-metadata` | extended | `MemberCatalog` capability: snapshotting, fingerprint, serverless backing | yes (existing artifact) |
| `packages/kotlin/ttr-designer-server` | extended | serves `ttrm/getMemberDomains` / `ttrm/getMembers` (WS JSON-RPC, existing host) | app module |
| `packages/kotlin/ttr-md-agent` | **new** | MCP server (Kotlin MCP SDK, streamable HTTP) wrapping resolver + member catalog | app module |

## 4. Data flow

**Compile, connected.** ttrp-frontend parses a program; expression positions yield dotted chains.
Chains that are float-shaped parse as literals (decision 14); the rest become `mdPath` nodes. The
checker first tries input-column resolution (C3-a-iv); unresolved (or shadow-flagged) chains go to
`ttr-md-resolver` with: the MD symbol graph (from ttr-semantics over the model repo), a
`MemberSnapshot` (from ttr-metadata; one immutable snapshot per compile pass, fingerprint recorded
in the bundle manifest beside the world fingerprint — mirroring the optimizer's `MetadataSource`
pattern), and the `asof` value. Result: canonical path + shape, or diagnostics. The translator
lowers canonical paths via the model's `md2db_*` bindings into `plan.v1` relational nodes.

**Compile, disconnected.** No member snapshot. Bare member tokens are illegal (decision 18);
qualified pairs (`customer.Kaufland`) resolve structurally (dimension/attribute checked), member
existence defers to bind time — the emitted plan carries the member literal into a Filter whose
value is checked when the artifact runs. Degradation ladder mirrors the optimizer's GI-19: catalog
absent at pass start in a mode that needs it ⇒ hard error; lost mid-session ⇒ held snapshot +
stale-warning diagnostic.

**Agent flow.** The planning agent (or NL front-end) tokenizes utterances and calls
`ttr-md-agent`'s `md_resolve` with raw tokens. The service runs the same resolver connected to the
same catalog and returns the canonical path, shape, and explanation — or the ambiguity
alternatives for the agent to choose from. The agent never guesses; it picks among structured
alternatives (P2 preserved end to end).

## 5. Tech stack

Kotlin 2.x / Gradle (existing build domain) · Kotest · ANTLR from the shared `.g4`s via the
existing three-target pipeline · Apache Calcite in ttr-translator (existing; see
`~/Dev/view-only/calcite` + graphify-out for RelNode APIs) · Ktor CIO in ttr-designer-server
(existing host; Ktor + serialization examples per `EXAMPLES.md` → ai-platform) · Kotlin MCP SDK
for ttr-md-agent (streamable HTTP; example in ai-platform per `EXAMPLES.md`; source at
`~/Dev/view-only/kotlin-mcp-sdk`) · kotlinx.serialization for all wire DTOs.

## 6. Architecture decisions

- **MDS1 · Resolver is its own published module** (`ttr-md-resolver`), not part of ttrp-frontend:
  three consumers (frontend, agent service, later the TS/LSP arc via a conformance-mirrored port)
  and zero surface dependencies. Keep it pure functions over immutable inputs.
- **MDS2 · MD Layer A port lives inside `ttr-semantics`** as an `md` subpackage, not a new module:
  it is exactly the "semantics" published artifact's job, and the TS implementation
  (`@tatrman/semantics`, md plan phases 2A–2F) is the behavioral spec — parity fixtures, not
  reinvention. Only the subset the resolver needs is ported (symbol graph, grain lattice,
  defaults, attribute-map sugar); validators/diagnostics stay TS-side until the tooling arc.
- **MDS3 · Member catalog rides the existing metadata stack**: capability in `ttr-metadata`,
  protocol methods on `ttr-designer-server`'s `ttrm/*` namespace, snapshot/fingerprint semantics
  copied from the optimizer's stats snapshot. No new server, no new transport.
- **MDS4 · One grammar version bump carries all syntax changes** (NUMBER→INT + floatLiteral +
  mdPath + DOTDOT + set braces + TTR-M `publish members`), cut per
  `docs/grammar-master/new-grammar-version-process.md`. The NUMBER demotion is potentially
  breaking for existing TTRP decimal literals — the floatLiteral parser rule must keep
  `12.5`-style literals parsing identically (conformance fixtures prove it).
- **MDS5 · Writeback lowers to existing plan.v1 vocabulary** (Store + journaling + the relational
  core); no new node kinds (P1). Spread emits the *declared* strategy's expansion or fails —
  never a default allocation.
- **MDS6 · The agent service is a thin shell.** All language intelligence is in the resolver;
  `ttr-md-agent` only adapts MCP ⇄ resolver DTOs. No prompt logic, no fuzzy matching (P2).
- **MDS7 · Materialization writes model text, not model state.** `C := e` on a fresh cubelet
  emits a **generated `.ttrm`** (`<project>/generated/md/<cubelet>.ttrm`, cubelet def + binding)
  through ttr-writer — deterministic and idempotent, so re-runs and diffs behave. The model repo
  stays the single canonical truth; the metadata server closes the dbt-ish loop by reporting
  materialization status (declared-only / materialized / drifted) against the live DB catalog.
- **MDS8 · Journaling rides the semantics-block feature** (grammar 4.2): technical columns are
  `semantics { role: … }` tags (`valid_flag`, `valid_from`/`valid_to`, `version`, `authored_by`,
  `written_at`) — a new role *family*, zero grammar change, validated in the semantics layer.
  Role spellings align with the ai-platform grounding contracts. This makes the semantics-block
  feature a **hard pre-flight** for the journaling stage (plan S5C).

## 7. Risks & mitigations

| risk | mitigation |
|---|---|
| Resolution search explodes combinatorially | paths are short (≤ ~10 tokens); classify-then-search with per-token candidate sets; hard cap + `TTRP-MD-` diagnostic on pathological inputs; benchmarks in resolver test suite |
| Large dimensions (millions of members) | `MemberSnapshot` is paged + interned; catalog protocol supports prefix queries; TTR-M opt-in (`publish members`) keeps unbounded domains out by default |
| NUMBER→INT grammar change breaks existing programs | conformance fixtures for every current numeric-literal shape *before* the change; grammar-master version process; TS/Python parsers regenerate from the same `.g4` |
| Parallel-arc collision: optimizer is also extending metadata (`MetadataSource`, snapshots) | this arc *reuses* the optimizer's snapshot/fingerprint pattern rather than defining its own; coordinate the `ttr-metadata` extension point in one review before S6 starts |
| Kotlin/TS MD semantics drift | the TS Layer A is the spec: golden parity fixtures (same model in, same lattice/defaults out) run in `ttrp-conform`-style harness |
| Member data staleness between compile and run | snapshot fingerprint + `asof` recorded in the bundle manifest; bind-time existence check is the final authority (decision 13) |
| Generated-`.ttrm` conflicts (hand edits, concurrent scripts, renames) | generated files carry a do-not-edit header + deterministic content; regeneration is full-file replace; hand-edited drift is a validation error, not a merge |
| Journaling read-view cost (diff = SUM per key on every read) | the view is explicit in the plan (optimizer-visible); materialized current-state tables are a named post-arc seat, not v1 |

## 8. Seams intentionally left open

The TS/LSP arc (hover explanations, Designer path rendering) consumes the same contracts — the
`Explanation` DTO is designed for direct display. The `with`-block (16), measure tuples (12),
per-expression `asof` (17), and safe navigation (13) all have named seats in the contracts and can
land without re-architecting. MOLAP bindings would add a second lowering target beside
`plan.v1`-relational; the resolver is unaffected.
