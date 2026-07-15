# Rejects in TTR-P — Detailed Design

> The exhaustive write-up of the rejects design effort (2026-07-15). Audience: a human reader who
> was not in the sessions. The compact planning input is [`design.md`](./design.md); rationale
> ground truth is the decision log in [`00-control-room.md`](./00-control-room.md) §6.

## 1. The problem

TTR-P reserves two error ports on every node (C3-f): `err`, a control-shaped signal that the node
failed, and `rejects`, a data-shaped stream of the rows it failed *on*. Leaving them unwired means
fail-fast. The language manual sells this as "the error path is ordinary data flow" — and in the
hero program, rows whose `cast` fails are diverted to a reject store instead of crashing the run.

Through v1, that promise was only half real. Fail-fast worked everywhere (it is what engines do
natively). But *producing* a rejects stream was an open design question, and both emitters
consciously skip rejects-mapped ports. The blocker was SQL: a SQL statement is atomic — one row
hitting a conversion error (PG `22P02`, MSSQL Msg 245) aborts the whole statement, and a SELECT
has no per-row error channel. There is no native way to say "cast what casts, hand me the rest."

Solving this turned out to be less about SQL tricks and more about three prior questions: what
*is* a reject, exactly; what does the stream *carry*; and — the deepest one — *who decides* that a
given row fails. This document records the answers.

## 2. What a reject is

**A reject is a row on which the node's row-level function fails to evaluate.** Nothing more.
A cast that cannot parse its input, a division whose denominator is zero, a date that does not
exist — these reject. Everything else flows normally:

- `cast(NULL)` is NULL — a *success*, per SQL three-valued logic. NULL never rejects.
- A filter predicate evaluating to false routes the row out of the stream; that is not a reject.
  Only a predicate that *cannot be evaluated* rejects.
- An inner join dropping a non-matching row is the join doing its job. Unmatched rows are not
  rejects — the language already has join types and Branch anchors for structural routing.
- A row the author considers *bad data* (negative quantity, out-of-range code) is not a reject
  either: that is a `filter` the author writes. A declarative `expect` sugar over this pattern
  was considered and parked for a later effort; it desugars cleanly onto the machinery built here.

Connecting the `rejects` wire is what flips a node from fail-fast to divert. Same failure, two
routings — the port is a *routing decision*, not a new semantic.

**The reject-capable set in v1.x** is deliberately small: casts (per type pair), division, and
datetime parsing. Arithmetic overflow is *not* reject-capable — there is no portable way to state
"this multiplication would overflow" as a total predicate without re-implementing engine
arithmetic in the guard, so overflow stays fail-fast. Aggregates are `err`-only: a `sum` that
overflows implicates a whole group, and group-level failure does not attribute to input rows
honestly. Total functions (project, sort, union, limit) cannot reject anything.

Every node keeps its reserved `rejects` port regardless (uniformity is worth more than pruning);
the compiler derives per-node rejectability from the catalogue and warns on provably-dead wires.

## 3. Who defines failure — the load-bearing decision

Consider `cast(' 12 ' as int)`. Postgres trims whitespace and parses it. Polars does not. MSSQL's
`TRY_CAST` has its own opinions, as does every engine about `'+12'`, `'1e3'`, locale decimal
separators, and the exact int64 boundary. If "this row fails" means "the executing engine raises",
then the *same program* produces *different rejects streams* depending on placement — and A4
("identical results across engines") dies, quietly, on its error path. Moving a container from
`target pg` to `target polars` would change which rows are bad.

So the effort's first principle, ratified as **R-P2: the catalogue defines failure; engines
implement it.** Every reject-capable catalogue function carries a **canonical validity domain** —
an exact spec of the inputs it accepts, per type pair (for `cast(text→int)`: optional sign,
digits, optional surrounding ASCII whitespace, value within int64 — the real spec is a design
deliverable of the first implementation phase, leaning near-PG semantics, exactly as canonical 3VL
leaned on SQL). Alongside the spec lives an **accept/reject example corpus** that doubles as the
conformance fixture set.

Engines then *implement* the canonical domain. Where a native form (`TRY_CAST`,
`pg_input_is_valid`, `cast(strict=False)`) provably matches it, the manifest says so and the form
may be used. Where it diverges, the compiler emits an **enforcing guard** (regex/bounds checks)
that coerces the engine to the canonical domain — the same move, again, as enforcing 3VL on
engines that lack it. An empirical corpus spike (R-Q9) measures the actual divergences before any
manifest claims `domain: canonical`.

## 4. The stream contract

**Schema.** A rejects stream carries the offending row exactly as the node saw it — the input
schema verbatim — plus reserved metadata: `_ttrp_reject_code`, a canonical diagnostic id from the
`TTRP-RJ-0xx` range, and `_ttrp_reject_expr`, identifying the failing expression. Never engine
message text: engine text differs per engine and would break conformance on the metadata columns
themselves. The `_ttrp_` prefix is reserved language-wide; a user column starting with it is a
compile error. Because the metadata columns are fixed and the row schema is the statically-known
in-port schema, the rejects port schema is fully static (T2-a holds).

**The partition invariant.** Per reject site, as multisets:
`in = processed ⊎ rejects` — every input row lands in exactly one bucket, none duplicated, none
dropped. This is testable (conformance counts it), it matches the mental model ("divert, don't
lose"), and the chosen producer mechanism satisfies it by construction.

**Multi-error rows.** A row failing two expressions in one `calc` rejects once, with the *first*
failing expression's code in document order — an order the emitted guard imposes explicitly (a
CASE ladder), since engines do not natively share one. A row-per-error mode and an error-set
column were considered; the former breaks the partition arithmetic, the latter needs nested types
(v2).

**Multi-input nodes.** A join's ON-expression evaluates over a candidate *pair*, which is not a
row of either input — and which pairs exist is plan-dependent, an A4 hazard. The answer is
**decompose-to-evaluate**: normalization pulls reject-capable subexpressions out of ON into
per-side `calc` nodes, making the join's own evaluation total. Only genuinely both-sided
expressions (`f(l.a, r.b)` where the failure needs both operands) fall back to a pair-schema
reject with a compile warning. A consequence worth noting: the v1 hero's `rejects = j.rejects`
re-reads as the upstream cast's rejects — which is what it always morally meant — and the fixture
gets re-authored accordingly (fixing, along the way, its `err rejects` header oddity).

## 5. How the compiler implements it — elaboration

The producer mechanism is implemented **once**, as a graph rewrite, not per emitter. A dedicated
normalization stratum — positioned *sugar ≺ reject-elaboration ≺ capability-check/placement ≺
movement-synthesis* — rewrites every rejects-**wired** node into a guard-and-branch subgraph:

```
in ──▶ guard-calc ──▶ branch(V) ──true──▶ op ──▶ out
        (adds _ttrp_v1…)    └──false──▶ project ⊕ _ttrp_reject_code ──▶ rejects
```

The guard-calc computes validity columns using **internal catalogue functions** —
`is_castable(x, T)`, `is_nonzero(y)` — which are not authorable surface syntax; they exist so the
capability checker can reason about them per engine. V is **total** (it never fails and never
returns NULL — its own proof obligation), so the branch is exhaustive and the partition invariant
holds by construction.

Three properties matter:

- **The wire triggers it, not the capability.** A node whose `rejects` port is unwired is not
  elaborated at all — the graph is unchanged, the emit is unchanged, fail-fast costs nothing
  (principle R-P3). Try-semantics are paid for only when bought.
- **It is deterministic and uniform across surfaces**, so the Phase-7 identity gate (embedded ≡
  canonical ≡ bare, byte-identical normalized graphs) is preserved.
- **Provenance is mandatory.** Every synthetic node points to its authored node. Diagnostics
  speak in authored terms; the Designer renders the authored node with the elaboration collapsed
  (drill-in available); synthetic nodes mint no `.ttrl` view-state keys; `ttrp/getGraph` serves
  the authored shape unless asked for `elaborated: true`.

Because elaboration precedes the capability check, manifests evaluate the internal validity
functions like any other capability. If an engine cannot implement V for some function (manifest
`domain: unknown`, no guard implementation), the **whole elaborated cluster escalates as one
provenance-tagged unit** to a capable engine — or the compile fails, per the project knob
`[ttrp] rejects-in-sql = produce | escalate | error`. Procedural row-at-a-time capture (PL/pgSQL
exception loops, cursors) was considered as a fallback tier and **excluded**: it is a performance
catastrophe, abandons preserved-shape emit, and under catalogue-defined failure it is not even
correct.

## 6. What the emitted code looks like

**SQL** (the hero's returns-cleaning island placed on PG; MSSQL analogous):

```sql
WITH returns_raw AS (…),
     parsed_guard AS (
       SELECT *,
              /* canonical validity for cast(text→int); native form only under
                 a manifest domain-equality claim */
              (returned_qty_raw IS NULL OR returned_qty_raw ~ '^\s*[+-]?\d+\s*$' …) AS _ttrp_v1
       FROM returns_raw
     ),
     parsed AS (
       SELECT …, CAST(returned_qty_raw AS integer) AS returned_qty
       FROM parsed_guard WHERE _ttrp_v1
     ),
     clean AS (SELECT … FROM parsed WHERE returned_qty > 0)
SELECT … FROM clean;                                   -- → out

-- second terminal statement, same chain:
SELECT …, CASE WHEN NOT _ttrp_v1 THEN 'TTRP-RJ-001' END AS _ttrp_reject_code, …
FROM parsed_guard WHERE NOT _ttrp_v1;                  -- → rejects
```

Nothing about the bundle model changed: SQL islands already emit one statement per container OUT
port over a shared CTE chain; the rejects port is simply one more, with the same ADBC/Arrow
export. Preserved-shape holds — the guard CTE is named after its SSA node, and a DBA reading the
artifact sees the program. Load-level rejects (a CSV line that does not deserialize at all) have a
portable floor — declare raw `text` columns and let the casts reject, which the hero already does —
with PG17 `COPY … ON_ERROR` / MSSQL `ERRORFILE` available as per-version manifest accelerators.

**Polars** — the same guard shape, vectorized as a mask-and-split:

```python
parsed_g = returns_raw.with_columns(
    pl.col("returned_qty_raw").cast(pl.Int64, strict=False).alias("returned_qty"))
_m = pl.col("returned_qty").is_null() & pl.col("returned_qty_raw").is_not_null()
parsed         = parsed_g.filter(~_m)
parsed_rejects = (parsed_g.filter(_m).drop("returned_qty")
                  .with_columns(pl.lit("TTRP-RJ-001").alias("_ttrp_reject_code")))
```

The `is_null & is_not_null` mask is the sentinel disambiguation every try-style form needs (a NULL
result must mean *failed*, not *was already NULL*). Where Polars' native acceptance domain differs
from the canonical one, the enforcing guard (string checks, bounds) runs before the non-strict op —
the exact mirror of the SQL side.

**Fail-fast** emits what it always did: plain `CAST`, strict cast. Any row-level failure is a
nonzero exit with a diagnostic; there is deliberately no cross-engine promise about *which* of
several failures reports first (imposing one would force guard evaluation onto the happy path).

## 7. Manifests and placement

The capability manifest gains, per engine, per reject-capable (function, type pair):
`{native_form?, domain: canonical | wider | narrower | unknown, min_version}`. This is where
`pg_input_is_valid`'s PG16 floor lives (older PG uses the canonical guard — there is no global
version floor), where `TRY_CAST` gets its domain verdict from the corpus data, and what the
escalation decision reads. Emitter *fusion* — pattern-matching the guard subgraph back into a
single native form for speed — is parked as a later optimization, permitted only under a
manifest-proven domain equality.

## 8. Conformance

The rejects stream is a **result**. Every wired rejects port exports Arrow IPC and enters the Q9
compare exactly like an OUT port: schema fingerprint, multiset row compare, canonical types. The
procedure grows an **eighth check** — the partition count `|in| = |processed| + |rejects|` per
reject site — cheap and able to catch producer bugs the per-port compare cannot. The parent
contracts document (§9) and the E-e procedure text are amended from seven points to eight. The
canonical validity corpus supplies reject-triggering fixture rows for every reject-capable
function, so the hero seal finally covers the error path it has been advertising.

## 9. Surfaces

Nothing changes in any grammar in v1.x. Canonical text already taps rejects (`parsed.rejects ->
bad`); the elaboration is invisible to authors. Fragments keep surfacing `err` only — the C2-e
graduation boundary stands for this release. The intended follow-up, once producers have shipped
and passed the seal, is **dialect-asymmetric taps**: a TTR-B verb ("Convert X to number, sending
failures to bad" — the verb-table machinery is designed for extension), while TTR-SQL and
TTR-pandas keep paste-fidelity and the graduation rule; a single-reject-site header tap
(`container clean(out ok, rejects bad) target pg """sql …"""`) is to be priced alongside, since it
needs no dialect grammar at all. Parked with that revisit condition.

New authoring diagnostics land in `TTRP-RJ-1xx`: dead rejects wire, forced escalation, `_ttrp_`
column collision, volatile function in a reject-capable position, pair-schema fallback. Row-level
codes are `TTRP-RJ-0xx`. Both ranges join the diagnostics tables and hence the test fixtures and
the assist layer's repair vocabulary.

## 10. What was deliberately not solved

Expectations (`expect` clauses) as declared rejects; store-node rejects (write-side constraint
capture — tied to the writes-scope question); orchestration reactions (quarantine thresholds,
"fail if >N% rejected" — needs F); the error-set metadata column (needs v2 nested types); overflow
as reject-capable; the pandas engine. Each sits in the parking lot with a revisit condition. The
four empirical questions — the validity-domain corpus (R-Q9), MSSQL multi-statement island
execution (R-Q10), Polars version and Decimal behavior (R-Q11/12) — ride into the implementation
plan as spike tasks.

## 11. Why this shape (rationale in brief)

The design's center of gravity is two moves. First, **defining failure canonically** (R-P2):
without it, every other choice — native try functions, conform, escalation — quietly forks per
engine, and the language's core promise (placement does not change semantics) breaks exactly where
users are handling their dirtiest data. Second, **elaborating in the graph, not the emitters**:
guard-and-branch turned out to be the same shape on every engine (CTE split in SQL, mask split in
Polars), so implementing it once as a rewrite makes the semantics testable without any engine,
keeps emitters dumb, lets the capability/escalation machinery work unmodified, and hands the
optimizer honest dataflow. Everything else — the schema metadata, the partition invariant, the
CASE-ladder ordering, the reserved prefix — is the bookkeeping that makes those two moves
verifiable.
