# MD dot-path sugar — contracts

**Status:** v1 draft, 2026-07-08 · Normative companion to [`architecture.md`](architecture.md).
Decisions cited as **D1–D19** from [`../dot-path-sugar.md`](../dot-path-sugar.md). Rules here are
numbered **R1…** and are the source of truth for task lists and tests.

## 1. Surface grammar (TTRP.g4 changes)

One grammar version bump (MDS4) carries everything in this section.

### 1.1 Token changes

```antlr
INT          : [0-9]+ ;               // replaces NUMBER : [0-9]+ ('.' [0-9]+)? ;
DOTDOT       : '..' ;                 // must be declared BEFORE any rule that could match '.'
LBRACE       : '{' ;   RBRACE : '}' ; // if not already tokens
STAR         : '*' ;                  // exists (multiplication) — reused in path position
ASSIGN_MAT   : ':=' ;                 // materialize (D22) — declare BEFORE COLON
PLUS_ASSIGN  : '+=' ;                 // merge (D23)
MINUS_ASSIGN : '-=' ;                 // delete (D24)
```

`NUMBER` is removed. Every former `NUMBER` site is re-pointed (typeName arity args → `INT`;
literal → `numericLiteral` below).

### 1.2 Parser rules

```antlr
numericLiteral : floatLiteral | INT ;
floatLiteral   : INT DOT INT          // 12.5, 2025.06
               | DOT INT              // .25
               | INT DOT              // 25.
               ;

mdPath         : pathComponent (DOT pathComponent)+ ;
pathComponent  : IDENTIFIER                                   // member, level, measure, agg, cubelet
               | INT                                          // numeric member (2025, 06)
               | STRING                                       // quoted member: "Kaufland K123"
               | LBRACE pathAtom (COMMA pathAtom)* RBRACE     // set (D15: braces compulsory)
               | pathAtom DOTDOT pathAtom                     // range: 2024..2026
               | STAR                                         // free dimension (must follow a
               ;                                              // qualifying component — R7)
pathAtom       : IDENTIFIER | INT | STRING ;

// cubelet statements (D20) — LHS form dispatches semantics (§11 R24):
cubeletStmt    : lhs=mdPathOrIdent op=(ASSIGN | ASSIGN_MAT | PLUS_ASSIGN | MINUS_ASSIGN)
                 rhs=expression withClause? ;
withClause     : 'with' object_ ;     // free-form object; keys (shape/table/journal) checked
                                      // semantically — "parser stays mechanical" (R27)
```

### 1.3 Float/path disambiguation (D14 — normative)

- **R1.** A dotted chain consisting of exactly `INT DOT INT`, `DOT INT`, or `INT DOT` parses as
  `floatLiteral`. Any other chain (≥1 IDENTIFIER/STRING/set/range/star component, or ≥3
  components) parses as `mdPath`. Implemented by rule ordering: `floatLiteral` is tried before
  `mdPath` in expression position.
- **R2.** Whitespace is not significant (`WS` is skipped): `2025 . 06` ≡ `2025.06` (float). The
  formatter closes spaces in floats and paths.
- **R3.** Scientific notation is not in the grammar (deferred).
- **R4.** Escape for float-shaped periods: add a qualifying component (`time.2025.06`).

Conformance examples (fixtures must include all rows):

| input | parses as |
|---|---|
| `.25` · `25.` · `2025.06` | float |
| `sales.2025.06` · `2025.06.sales` · `2025.06.15` | path |
| `sales.{Kaufland, Lidl}.net` · `sales.2024..2026.net` | path |
| `x * sales.2025.net` | `x` times path (STAR binds as operator outside path position) |

### 1.4 TTR-M change (TTR.g4): domain member publication

```antlr
// inside def domain body:
publishClause : 'publish' ':' 'members' ;
```

`def domain customer_name { … publish: members }` opts the domain into the member catalog (§7).
Default: not published. This is the only Layer A change in the arc.

## 2. Token classification & resolution (the resolver core)

Input: an `mdPath`'s component list (order-free, D-"any order"), an `MdModel` (from
ttr-semantics.md), an optional `MemberSnapshot`, the `asof` instant, and an optional
`PathContext` (§5). Output: `ResolutionOutcome` (§3).

- **R5 · Classification.** Each component gets a candidate-slot set by deterministic lookup, in
  this order (order defines *candidacy*, not priority): (a) agg name (`sum`,`avg`,`min`,`max`,
  `count` — closed v1 list), (b) cubelet name, (c) measure name, (d) attribute/level name,
  (e) calc-catalog token (`lastMonth`, `month`, … from `MD_CALC_CATALOG`), (f) member — via the
  snapshot's member→(domain→attribute→dimension) index; INT components are member-candidates of
  numeric/temporal domains only. A component with zero candidates ⇒ `TTRP-MD-001`.
- **R6 · Qualified pair.** `attr.member`, `dim.member`, `dim.*`, `attr.{…}` — a component pair
  where the first names an attribute/dimension binds the second to it atomically. The pair is
  itself order-free within the path. Pairs are tried before free search and remove their tokens
  from it.
- **R7 · Star/set/range binding.** `*`, `{…}`, and ranges must be bound to an attribute — by
  qualified pair, or uniquely inferable membership (all set/range atoms resolving to the same
  attribute). Unbindable ⇒ `TTRP-MD-004`.
- **R8 · Search.** Find every consistent assignment of components to slots such that: exactly one
  cubelet (explicit, or unique cubelet compatible with all other resolved slots), ≤1 measure
  (D12; two measure-classified components ⇒ `TTRP-MD-005`), ≤1 agg, every dimension coordinate's
  attribute belongs to a dimension of the cubelet's grain lattice (derivable hops allowed:
  members/attributes reachable via N:1 or 1:1 maps from a grain attribute — design.md §6 lattice),
  and every member exists in its attribute's domain (connected; D13).
- **R9 · Ambiguity.** Zero assignments ⇒ `TTRP-MD-002` (with the per-component failure reasons);
  \>1 assignment ⇒ `TTRP-MD-003` carrying **all** alternatives as canonical paths, sorted
  deterministically (qname order). Never scored, never guessed (P2).
- **R10 · Defaults fill** (after a unique assignment, in this order): measure ← cubelet's default
  measure; agg ← measure's default aggregation; unmentioned grain dimensions ← context (§5) if
  present, else **free**. Reads may leave dimensions free (shape, §4); the LHS may not (§5).
- **R11 · Repetition.** Two components pinning the same *attribute* ⇒ `TTRP-MD-006` (suggest
  braces, D15). Two components on the same *dimension, different attributes* = conjunctive drill
  (both coordinates apply), legal.
- **R12 · Calc tokens.** Calc-catalog components (`lastMonth`, `month`, `year`…) resolve to
  coordinates over computed attributes (calc maps over the grain attribute). Evaluation-relative
  tokens take `asof` (D17) as their anchor. They work with or without a time-dim table (D-"time
  is special only in its catalog").
- **R13 · Disconnected mode** (no `MemberSnapshot`): bare member-candidates are illegal ⇒
  `TTRP-MD-007` (qualified pair required, D18); pairs resolve structurally and defer existence to
  bind time — the canonical path marks such coordinates `deferred: true`.
- **R14 · Explanation.** Every successful resolution produces an `Explanation`: one step per
  component (`token → slot, via`), plus one step per filled default. This is the hover/agent
  payload; it is part of the contract, not debug output.

## 3. Canonical form & data model

Canonical text (the desugar target; the formatter emits this ordering — cubelet, coordinates in
the cubelet's declared dimension order, measure, agg):

```
sales[customer.name: "Kaufland", time.year: 2025].net @ sum
plan[customer.name: "Kaufland", time.month: *].net @ sum    // free dimension explicit
```

Kotlin data model (module `ttr-md-resolver`, package `org.tatrman.ttr.md.resolve` — all
`@Serializable`, all immutable):

```kotlin
sealed interface Selector {
    data class Pinned(val member: MemberRef) : Selector
    data class MemberSet(val members: List<MemberRef>) : Selector      // D15
    data class Range(val lo: MemberRef, val hi: MemberRef) : Selector  // ordered domains only
    data object Star : Selector                                        // free (D-"* = free")
}
data class MemberRef(val text: String, val deferred: Boolean = false)  // deferred: R13
data class Coordinate(
    val dimension: QualifiedName, val attribute: QualifiedName,
    val selector: Selector, val viaCalc: QualifiedName? = null,        // R12
)
data class CanonicalPath(
    val cubelet: QualifiedName, val coordinates: List<Coordinate>,
    val measure: QualifiedName, val agg: AggKind,
)
data class PathShape(val freeDims: List<QualifiedName>)
// freeDims.size: 0 = scalar · 1 = vector · n = sub-cubelet   (D10)

data class Explanation(val steps: List<ExplainStep>)
data class ExplainStep(val token: String?, val slot: String, val via: String) // token=null → default

sealed interface ResolutionOutcome {
    data class Resolved(val path: CanonicalPath, val shape: PathShape,
                        val explanation: Explanation) : ResolutionOutcome
    data class Ambiguous(val alternatives: List<Resolved>) : ResolutionOutcome        // R9
    data class Failed(val diagnostics: List<MdDiagnostic>) : ResolutionOutcome
}
```

Resolver entry point:

```kotlin
interface MdPathResolver {
    fun resolve(components: List<PathComponent>, model: MdModel,
                members: MemberSnapshot?, asof: Instant,
                context: PathContext? = null): ResolutionOutcome
}
```

## 4. Typing & operator semantics

- **R15 · Shape** = free dimensions (D10). Inferred only; hover surfaces it.
- **R16 · Broadcast** (D11): for a binary op over paths/exprs with shapes A and B — shared free
  dims align by member (cell-wise); dims free on one side broadcast the other side's value.
  Result shape = union of free dims. Missing-cell alignment: absent cells are absent in the
  result (inner alignment); no null-fill in v1.
- **R17 · Collapse is never implicit inside an expression.** A free dimension collapses (via
  default agg or an explicit agg token) only: at an assignment boundary where the LHS is not free
  on it (§5), or when the path carries an explicit agg with narrower shape context. Scalar
  position requirements (e.g. a `filter` predicate comparing to a path) demand shape = scalar or
  a collapse via context — otherwise `TTRP-MD-008`.
- **R18** One measure per expression path (D12); paths in comparisons/arithmetic follow the host
  TTR-P expression typing (numeric measures, decimal-exact rules, Q9) once resolved.

## 5. Assignment (writeback)

A cubelet assignment statement is `mdPath ASSIGN expression`, recognized when the LHS mdPath's
cubelet slot resolves (else it is a normal TTR-P statement).

- **R19 · Strict LHS** (D-"LHS strict", option 3): resolved **without** context, **without**
  defaults for grain dimensions, **without** derivable hops. Every dimension of the target
  cubelet's grain must be pinned, restricted, or explicitly `dim.*`; the measure must be an
  explicit token. Violations ⇒ `TTRP-MD-009` (missing dims listed). Order remains free.
- **R20 · Context overlay**: `PathContext` = the resolved LHS. RHS paths resolve with: per
  dimension — RHS token wins, else LHS coordinate inherited; cubelet/measure/agg slots — RHS
  token wins, else inherited. `dim.*` on the RHS un-pins an inherited coordinate (D-"* escape").
- **R21 · Grain reconciliation** at the assignment boundary: RHS free dims ∖ LHS free dims →
  collapse via default agg; LHS free dims ∖ RHS free dims → **spread** — legal only if the
  target's binding declares an allocation strategy for that dimension, else `TTRP-MD-010`.
  LHS ∩ RHS free dims align (R16).
- **R22 · Write semantics**: the lowered write honors the binding's journaling mode
  (overwrite/invalidate/diff — brief §Journaling); `+=` lowers to diff-style append where the
  binding allows, else read-modify-write. Deferred members (R13) existence-check before any
  write; failure aborts the statement (D13).

## 6. Diagnostics

Area `MD` in the TTR-P diagnostic convention (contracts §8 of ttr-p; texts finalized in task
lists):

| id | meaning |
|---|---|
| TTRP-MD-001 | unknown path component (no candidate slot) |
| TTRP-MD-002 | unresolvable path (no consistent assignment; per-token reasons) |
| TTRP-MD-003 | ambiguous path (alternatives listed as canonical paths) |
| TTRP-MD-004 | `*`/set/range not bindable to an attribute |
| TTRP-MD-005 | more than one measure in a path (D12) |
| TTRP-MD-006 | bare same-attribute repetition — use `{a, b}` (D15) |
| TTRP-MD-007 | bare member token in disconnected mode — qualify (`dim.member`) (D18) |
| TTRP-MD-008 | non-scalar path in scalar-only position (R17) |
| TTRP-MD-009 | incomplete strict LHS (missing grain dimensions / measure) (R19) |
| TTRP-MD-010 | spread without a declared allocation strategy (R21) |
| TTRP-MD-011 | unknown member (connected compile, or bind time) (D13) |
| TTRP-MD-012 | path shadowed by input column — column wins; qualify to force MD (see below) |
| TTRP-MD-013 | member catalog lost mid-session — held snapshot in use (stale warning) |
| TTRP-MD-014 | path search bound exceeded (pathological input) |
| TTRP-MD-015 | `with` clause invalid: missing/unknown key, or mismatch vs existing binding (R26/R27) |
| TTRP-MD-016 | measure/agg token on a `-=` RHS — values are ignored in deletes (warning, R29) |
| TTRP-MD-017 | `-=` on a diff-journaled cubelet (R29) |
| TTRP-MD-018 | journaling mode requires a technical-column role the backing table lacks (R30) |
| TTRP-MD-019 | *(reserved)* |
| TTRP-MD-020 | `:=`/`-=` on a slice LHS — cubelet statements need a bare-identifier LHS (R24) |
| TTRP-MD-021 | `+=`/`-=` target does not exist (fresh name) (R24) |
| TTRP-MD-022 | script variable shadows a model cubelet (warning, R25) |
| TTRP-MD-023 | grain/measure mismatch between statement target and RHS shape (R26/R28) |

**Shadowing rule (R23):** in expression position, input-column resolution (C3-a-iv) is attempted
first and wins; if the same chain *also* resolves as an MD path, `TTRP-MD-012` (warning) is
emitted. Forcing MD in a shadowed spot: qualified pair, or the `md:` prefix (reserved, rare).

## 7. Member catalog

### 7.1 Library contract (ttr-metadata)

```kotlin
interface MemberCatalog {                       // backings: server-connected · serverless(=null)
    fun snapshot(asof: Instant): MemberSnapshot // one immutable snapshot per compile pass
}
interface MemberSnapshot {
    val fingerprint: String                     // "sha256:<hex>" — recorded in bundle manifest
    val asof: Instant
    fun domains(): Set<QualifiedName>           // published domains only (D-opt-in; §1.4)
    fun members(domain: QualifiedName): MemberIndex?
}
interface MemberIndex {                         // paged + interned (architecture §7)
    fun contains(text: String): Boolean
    fun lookup(prefix: String, limit: Int): List<String>
    val count: Long
}
```

Degradation ladder (mirrors optimizer GI-19): connected compile requested + no catalog at pass
start ⇒ hard error; catalog lost mid-session ⇒ continue on held snapshot + `TTRP-MD-013`.
Disconnected compile = `snapshot == null` ⇒ R13 rules.

### 7.2 Wire protocol (ttr-designer-server, WS JSON-RPC `ttrm/*`)

```
ttrm/getMemberDomains → { domains: [{ qname, attribute, dimension, count, fingerprint }] }
ttrm/getMembers       ← { domain: string, prefix?: string, cursor?: string, limit?: int ≤ 10_000 }
                      → { members: string[], nextCursor?: string, fingerprint: string }
ttrm/getMaterializationStatus                                     // the dbt-ish loop (D22)
                      ← { cubelet?: string }                      // omitted = all bound cubelets
                      → { statuses: [{ cubelet, table, status: "materialized" | "declared-only"
                            | "drifted", detail?: string }] }     // drifted = table exists but
                                                                  // shape/columns ≠ binding
```

Read-only, versioned with the existing `ttrm` handshake; errors follow the server's established
JSON-RPC error shapes. Server sources member content through the model's `md2db` binding for the
domain's backing column (SELECT DISTINCT, cached per fingerprint) — implementation detail behind
the interface, contract is the DTO above.

## 8. Lowering to plan.v1 (ttr-translator)

| canonical element | plan.v1 lowering |
|---|---|
| cubelet | Load of the bound table(s) per `md2db_cubelet` (wide/long shapes per binding) |
| `Pinned` coordinate | Filter `col = literal` (via binding column; hops → Joins along map-backing tables) |
| `MemberSet` | Filter `col IN (…)` |
| `Range` | Filter `col BETWEEN lo AND hi` (ordered domain's order) |
| `Star` (free) | column becomes a group-by key |
| `viaCalc` coordinate | the calc map's SQL/engine expression from `MD_CALC_CATALOG` entry (catalogue seat, D-h) applied before Filter/group-by |
| measure + agg | Aggregate call over the binding's measure column(s); long-shape → pre-Filter on measure-code column |
| shape (free dims) | group-by keys = free dims' bound columns; scalar = aggregate-all |
| assignment | Store to the bound table with journaling mode; spread → the declared strategy's expansion (strategy contract stays in MD binding design §6.3) |
| `asof` | substituted at compile time (compile-time parameter, D17) |
| **journaling view** (R31) | wraps every cubelet Load: invalidate → Filter on the valid role (flag, or `valid_from ≤ asof < valid_to`); diff → Aggregate SUM per grain key; overwrite → plain Load |
| `C := e` (R26/R27) | run side: (create-table-if-new per generated binding) + truncate + Store; compile side: generated-`.ttrm` emission (not in the plan) |
| `C += e` (R28) | MERGE-shaped plan on grain keys; collision arm per journaling mode |
| `C -= e` (R29) | key anti-join → DELETE (overwrite) / valid-flip UPDATE + technical-column fill (invalidate) |
| technical columns (R31) | writes project version = max+1 per key, authored_by, written_at into the Store |

No new node kinds (MDS5). Long/wide shape handling reuses the MD binding layer's definitions.

## 9. Agent service (ttr-md-agent, MCP)

Kotlin MCP SDK, streamable HTTP. Tools (JSON Schemas final in task lists; shapes normative here):

```
md_resolve       in:  { tokens: string[] | raw: string, model: string, mode: "connected"|"disconnected", asof?: iso8601 }
                 out: { status: "resolved"|"ambiguous"|"failed",
                        path?: CanonicalPathDto, shape?: string[], explanation?: ExplainStepDto[],
                        alternatives?: CanonicalPathDto[], diagnostics?: MdDiagnosticDto[] }
md_explain       in:  { path: CanonicalPathDto | raw: string, model: string }
                 out: { explanation: ExplainStepDto[], shape: string[] }
md_list_members  in:  { domain: string, prefix?: string, limit?: int }
                 out: { members: string[], truncated: boolean }
```

`raw` input is split on `.` respecting quotes/braces — no other NL processing in the service
(MDS6); tokenization from free NL is the calling agent's job. DTOs are the §3 types' serialized
forms (kotlinx.serialization, stable field names — they are a public contract from v1).

## 10. Fixtures & conformance

- **Golden path fixtures** (`ttr-md-resolver` test resources): every example in the design note
  and this doc — order permutations resolving identically, all 14 diagnostics, disconnected
  variants, context overlay, share-of-total, spread. Fixture format: `{ model, members?, asof,
  context?, input, expected }` JSON — reused later by the TS port for cross-target parity.
- **Grammar conformance**: the §1.3 table joins `tests/conformance/fixtures/` and runs on all
  three generated parsers (existing `conformance.yml` harness).
- **Kotlin/TS Layer A parity**: grain lattice + defaults fixtures — same model in, same
  lattice/defaults out as `@tatrman/semantics` (MDS2).

## 11. Cubelet statements (D20–D24)

- **R24 · Dispatch.** In `cubeletStmt`, a slice LHS (path with ≥1 coordinate or measure token)
  admits only `=`/`+=` and follows §5 (R19–R22). A **bare-identifier LHS** admits all four
  operators: an identifier resolving to a model cubelet, an in-scope md-typed variable, or (for
  `=`/`:=` only) a fresh name. `:=`/`+=`/`-=` on a slice LHS ⇒ `TTRP-MD-020`; a fresh name with
  `+=`/`-=` ⇒ `TTRP-MD-021`. Statements interleave with all other TTR-P statements (D20).
- **R25 · Virtual cubelets.** `C = e` binds a TTR-P variable (Q7-γ, SSA) whose type is
  `PathShape × measures`. The resolver's cubelet slot (R5 case b) gains a **session namespace**: an
  `MdPathResolver.resolve` overload takes `sessionCubelets: Map<Name, CubeletShape>`; script
  variables shadow model cubelets of the same name with a shadowing warning (mirror R23's
  philosophy, `TTRP-MD-022`).
- **R26 · Materialize, existing target.** `C := e` where C is a model cubelet with a binding:
  truncate + overwrite through that binding. `with` present ⇒ every key must match the existing
  binding exactly, else `TTRP-MD-015`. RHS grain/measures must equal C's declared grain/measures
  (no silent reshape) — mismatch ⇒ `TTRP-MD-023`.
- **R27 · Materialize, new target.** `C := e with {…}` where C is fresh: logical definition
  inferred — grain = e's free dims, measures = e's measure (v1: single, D12). `with` **required**;
  keys: `shape: wide|long` (required), `table:` (optional; default = project-convention name in
  the default db schema), `journal:` (optional; default `overwrite`). Missing/unknown keys ⇒
  `TTRP-MD-015`. Effect at compile time: emit/refresh a **generated `.ttrm`** (cubelet def +
  `md2db_cubelet` binding) via ttr-writer at
  `<project>/generated/md/<cubelet>.ttrm` (deterministic content; idempotent re-runs;
  re-materializing with different `with` values updates it). Effect at run time: DDL-or-truncate
  + Store (§8).
- **R28 · Merge.** `C += e`: upsert on grain keys (D23). e's shape must cover C's grain (every
  grain dim pinned or free-aligned; extra free dims collapse per R21-analog; unknown dims ⇒
  `TTRP-MD-023`). Cell collisions resolve by C's journaling mode (overwrite cell / invalidate +
  append / diff append). On a **virtual** C: pure dataflow merge, SSA rebind, no persistence.
- **R29 · Delete.** `C -= e`: anti-join C on the grain keys of e's cells (D24); e's values are
  ignored; a measure or agg token in e ⇒ warning `TTRP-MD-016`. Per mode: overwrite → physical
  delete; invalidate → flip valid role + version/authorship fill; diff → `TTRP-MD-017` (error).
  Virtual C: dataflow anti-join. `-=` is never value subtraction.

## 12. Journaling & technical columns (D25–D26)

- **R30 · Declaration.** Journaling **mode** is a binding property of the cubelet
  (`journal: overwrite | invalidate | diff`; default overwrite). **Technical columns** are
  declared on the backing table via the `semantics { }` block (grammar 4.2, semantics-block
  feature — no grammar change here), role family: `valid_flag` (bool), `valid_from` / `valid_to`
  (temporal validity), `version` (monotonic per grain key), `authored_by`, `written_at`.
  Validation: `invalidate` requires `valid_flag` or the `valid_from`/`valid_to` pair on the
  backing table ⇒ else `TTRP-MD-018`; other roles optional under every mode. Role-value
  spellings must align with the grounding contracts' conventions (ai-platform
  `feature-grounding-contracts.md`) — verify at implementation, record in changelog.
- **R31 · Semantics.** *Writes* fill declared technical columns: version = max+1 per grain key,
  authored_by = run identity from the bundle manifest, written_at = run clock (not `asof`).
  *Reads* apply the journaling view when lowering the cubelet's Load (§8): invalidate →
  valid-filter (flag true, or `valid_from ≤ asof < valid_to` when temporal); diff →
  SUM-of-deltas per grain key. The "latest valid" default aggregation derives from
  `valid_from`/`valid_to` roles where present (D26) — replacing manual per-attribute
  configuration.

## Changelog

- **v1 · 2026-07-08** — initial draft from the design note (D1–D19), post-convergence.
- **v1.1 · 2026-07-08** — cubelet statements (D20–D24 → §1 tokens/`cubeletStmt`, §11 R24–R29),
  journaling & technical-column roles (D25–D26 → §12 R30–R31), diagnostics MD-015…023, §8
  journaling-view + statement rows, §7.2 `ttrm/getMaterializationStatus`.
