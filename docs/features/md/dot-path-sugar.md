# MD dot-path sugar — design note (brainstorm stage)

**Status:** **IMPLEMENTED (2026-07-20)** — this brainstorm note (2026-07-07, H session) converged into
the design + contracts under [`dot-path/`](dot-path/) and shipped across phases S0–S7 + S5C (reads,
writeback, cubelet statements, materialization, journaling roles). The authoritative artifacts are
`dot-path/{architecture,contracts}.md` + the phase plan in
[`../../../../project/tatrman/features/md/dot-path/`](dot-path/); this note is kept for the divergence/
convergence history only. Run-side verified live on ttrp-pg. The **algebra half** of Layer B (the
general filter/aggr/join/map/store operations DSL) remains deferred (design.md §11); only the dot-path
sugar shipped.

**Prior references** (the complete inventory as of this note): the brief's §Usage
([`md model brief.md`](md%20model%20brief.md)) — dot notation, free drill/filter mixing, derivable
omission, aggregation defaults; `design.md` §2 (Layer B deferred) and §11 (paradigm open);
design-space-map D3 ("own session, parked detail"); `language-design.md` D-h (md ref-kind slot
reserved; arrives as a package path, not a language change; md-catalog catalogue seat reserved);
the RAE gscript examples (`RAE/`, map-vs-list `select` sugar, member assignment). The only other
"sugar" in the MD feature — "map attribute A to B" → domain map — is **Layer A**, already
implemented (resolver 2B4), and unrelated to this note.

---

## 1. The core reframe: a path is a constraint set, not a navigation

The founding decision: **dot-path components are order-free.** `sales.Kaufland.2025.net.sum`,
`sum.Kaufland.sales.2025` (measure defaulted), `kaufland.2025.sales.sum` are all the same
expression. The dots do not mean "go into"; they mean "and". A path is an unordered **set of
selector tokens**, each resolved against the model into one of the slots:

| slot | example tokens | resolved via |
|---|---|---|
| cubelet | `sales`, `plan` | model symbol |
| dimension coordinate | `Kaufland`, `2025`, `january` | member → attribute → dimension lookup |
| attribute / level | `customer`, `month`, `zip` | model symbol |
| measure | `net`, `gross` | model symbol |
| aggregation | `sum`, `avg`, `max` | agg vocabulary |

Motivation: the notation must be constructible from natural language, and NL is order-free
("2025 Kaufland sales", "sales of Kaufland in 2025"). The NL front-end's only job is tokenization;
resolution is deterministic and model-driven (P2: no LLM in the compiler). The resolver doubles as
a service the planning agent can call, returning the canonical form or a structured ambiguity list.

Consequences:

- **Grammar is trivial; semantics does everything.** The parser sees a dotted chain and nothing
  more ("parser stays mechanical"). All meaning lives in resolution.
- **Canonical form.** Every path desugars to one explicit form —
  `sales[customer: Kaufland, time.year: 2025].net @ sum` — cubelet, dims in model order, measure,
  agg. The formatter/pretty-printer canonicalizes; authors write sloppy, tooling shows tidy,
  diffs stay stable. The order-free surface is *pure sugar* over this form (TTR-P P1).
- **Omission is the same mechanism as order-freeness.** A missing measure fills from the cubelet's
  default (`net`), a missing agg from the measure's default, derivable attribute hops are inserted
  (`Kaufland.zip` ≡ `Kaufland.address.zip`). Order-freeness + omission make resolution a small
  constraint-satisfaction / search problem over the model graph, not a lookup — acceptable at path
  sizes, but tooling must be able to **explain** the chosen interpretation (hover: "resolved as:
  cubelet `sales`, customer≔Kaufland, year≔2025, measure `net` (default), agg `sum`").
- **Ambiguity is the central design problem.** Same member name in two dimensions, member colliding
  with a measure name, `2025` as year vs product code. Policy: **deterministic error, never a
  guess** (P2), with the **qualified pair** as the escape hatch — `customer.Kaufland` is an atomic
  unit, itself order-free within the path. Priority rules (e.g. "resolve within the found cubelet's
  dimensions first") may shrink the search space but must never silently pick among live
  alternatives.

## 2. The selector spectrum

One unified concept: a dimension in a path is **pinned, restricted, or free** — differing only in
cardinality:

| form | example | meaning |
|---|---|---|
| member | `Kaufland` | pinned to one member |
| set | `{Kaufland, Lidl}` | restricted free dimension |
| range | `2024..2026` | restricted free dimension (ordered domain) |
| star | `customer.*` | fully free |

A multi-member selection is a **vector** (a mini-dimension of the result), never a filter — "if I
want a filter, I aggregate over it." Filtering is always restrict-then-collapse; there is no
separate filter concept in the sugar.

**`*` has one meaning everywhere: "this dimension is free."** What happens next is decided by the
surrounding grain, not by the symbol:

- RHS where a scalar is expected → the free dimension **collapses** via the default agg
  (`sales.2024.customer.*` = total over customers — the un-pin escape hatch from context, §4).
- RHS where the LHS is free on the same dimension → they **align** (vectorized assignment):
  `Kaufland.plan.2026.month.* .net = sales.2025.month.* * 1.1` — month by month.
- LHS freed *below* the RHS grain → a **spread**; the allocation/inverse strategy (design §6.3)
  is thereby invoked *explicitly*, satisfying strict-LHS (§3).

`*` (and sets/ranges when ambiguous) needs its dimension named — `customer.*`, `month.*` — a bare
`*` in an order-free path cannot tell which dimension it frees. This reuses the qualified-pair
syntax as the general disambiguation unit.

## 3. Reads are free, writes are strict

**Read/write asymmetry, decided:** the RHS (reads) enjoys full order-freeness, omission, and
defaults; the **LHS of a writeback is strict**. A mis-resolved read shows a wrong number; a
mis-resolved write corrupts data (cf. `SELECT *` vs no `UPDATE *`).

"Strict" = **explicit completeness, not canonical order** (order stays free on the LHS too):

- every dimension of the target cubelet's grain must be accounted for — pinned to a member,
  restricted, or **explicitly** freed with `dim.*` (which declares a spread);
- no omission, no defaults for grain dimensions, no derivable-element hops;
- the measure must be spelled out.

The real writeback danger is not order (resolution is deterministic either way) but
**underspecification** — `plan.2026.net = 100` silently writing all customers. Strict-LHS makes
"all customers" writable only as `customer.*`, i.e. on purpose. A coarse LHS (e.g. `2026` against a
monthly grain) is a spread and is only legal because freeing/coarsening is explicit; the allocation
strategy then applies.

## 4. Assignment context: the LHS scopes the RHS

The assignment target establishes a **context**; the RHS resolves within it.
**Rule: RHS = LHS context overlaid with RHS tokens; RHS wins per slot** (per dimension, and for the
cubelet/measure/agg slots). It is lexical scoping, per dimension.

```
Kaufland.2026.plan.net = sales.2025 * 1.1
```

| slot | LHS | RHS says | RHS resolves to |
|---|---|---|---|
| cubelet | plan | `sales` | sales |
| customer | Kaufland | — | Kaufland (inherited) |
| time | 2026 | `2025` | 2025 |
| measure | net | — | net (inherited) |

RHS = `sales[customer: Kaufland, year: 2025].net`. LHS context takes precedence over the cubelet's
own defaults (that is the point of context).

**Escape hatch:** `dim.*` un-pins an inherited coordinate. Share-of-total works:

```
Kaufland.2026.plan.net = sales.2025 * (Kaufland.sales.2024 / sales.2024.customer.*)
```

— the last term frees `customer` (collapses to the all-customer total via default agg) where the
context would otherwise force Kaufland and make the ratio 1.

Grain mismatch on the RHS does silent, useful work: `sales.2025` at month/transaction grain feeding
a year-grain cell aggregates via the default agg — the aggregation-defaults sugar from the brief.
These alignment rules (collapse / align / spread, §2) must be spelled out normatively in the
eventual contract.

A `with`-block falls out for free: the assignment target *is* the context. Whether a standalone
`with <path> { … }` form is also wanted is open.

## 5. Aggregation and ordered domains

- **Agg override is just another order-free token:** `sales.2025.avg` overrides the default sum.
- **`.prev` / offsets (`-1`) work on any ordered domain**, not just time. Generic mechanism:
  offset on an ordered codomain.
- **Time is special only in its stock catalog, not in the language.** The grain attribute may be
  `date`, yet `lastMonth` must work even with no time-dim table: computed properties of time are
  **calc maps** from the MD catalog (`monthOfDate`, …); `lastMonth` = calc map + offset on its
  ordered codomain. The existing `MD_CALC_CATALOG` machinery is the carrier.
- **P2 tension, flagged:** `lastMonth` is relative to *now* — the same script means something
  different tomorrow. Wanted: an explicit **`asof` anchor**, defaulting to evaluation time,
  pinnable for reproducing runs, tests, and backfills.

## 6. Embedding in TTR-P — an expression-level sublanguage, no fragment

The sugar is **not** a document-level fragment dialect (no `"""md` declaration). MD paths appear
inline wherever expressions occur, whenever the MD model is in scope — effectively an
**MD-subquery** in expression position. This holds up, with three real issues to solve in the
grammar/resolver design (all three generated parsers must agree byte-for-byte, so these are
early-grammar decisions):

1. **The lexer is the enemy, not the parser.** `sales.2025.net` — a standard lexer reads `.2025`
   as a float literal; `2024..2026` collides with `2024.`. Needs a path-context lexer mode or
   parser-side reassembly of `DOT INT`.
2. **Pure-identifier paths are grammatically invisible.** `Kaufland.sales.net` is
   indistinguishable from a qualified column/port ref (`left.x`) or model qname. Paths containing
   a number, `*`, `{}`, `..`, or a quoted member self-identify as MD; all-identifier paths cannot
   be classified at parse time — it is a **resolution-order** decision, not a parse decision.
3. **Precedence vs C3-a-iv** (TTR-P: "only input columns are in scope inside op expressions").
   MD paths widen that scope. Proposed rule: **input columns win, MD resolution second, and a
   `md/shadowed-path` diagnostic whenever both could match** — adding a column named `kaufland`
   to an input may never *silently* change a formula's meaning. Disambiguators for the shadowed
   case: the qualified pair (`customer.Kaufland…`), or a rare explicit `md:` prefix as tiebreaker
   (never required as a declaration).

Fits the reserved seams: D-h's md ref-kind package path, and the reserved md-catalog seat in the
TTR-P expression catalogue (`CompositeCatalog`).

Quoting for non-identifier members is required: `sales."Kaufland K123".net`.

## 7. Member resolution: connected / disconnected modes

Members (`Kaufland`) are **data, not model** — they live in tables, not `.ttrm`. Resolution
therefore depends on the compiler mode (same split as the TTR-P optimizer design):

- **Connected** — a metadata server provides, beyond the model: member content of dimensions for
  the compiler (this feature) and statistics for the optimizer. **TTR-M declares which domains
  publish their members** — never all of them. Connected mode resolves members fully; an unknown
  member is an error.
- **Disconnected** — fully offline. A bare member token is structurally unresolvable (its
  dimension cannot even be found). Rule to make explicit rather than emergent: disconnected mode
  **requires qualified pairs** for member tokens (`customer.Kaufland` — the dimension is checkable
  offline, member existence degrades to a bind-/run-time check), or downgrades bare-member
  resolution to a warning. Checking degrades gracefully by mode; the degradation ladder is part of
  the contract, not an accident.

Staleness of the member snapshot and caching are the metadata server's problem; the language only
needs the two modes and the ladder.

## 8. Decided vs open

**Decided in this pass** (pending the D3/D-h convergence session):

1. Order-free selector tokens; path = constraint set; one canonical desugar target.
2. Deterministic ambiguity errors + qualified pair `dim.member` as the atomic disambiguator.
3. Multi-member selection is a vector; filter = restrict + aggregate.
4. `*` = "dimension is free"; collapse/align/spread decided by grain; `dim.*` form.
5. LHS strict = explicit completeness (order still free); spreads only via explicit `dim.*`.
6. Assignment context: RHS inherits LHS per slot, RHS wins; `dim.*` un-pins.
7. Agg override as a token; `.prev`/offset generic over ordered domains; time via calc maps;
   `lastMonth` works without a time-dim table.
8. Expression-level embedding, no fragment declaration; column-first precedence +
   `md/shadowed-path`.
9. Connected/disconnected modes; TTR-M opts domains into member publication.

**Closed in the convergence session (2026-07-08):**

10. **Result typing is inferred only.** A path's shape = its set of free dimensions: zero free =
    scalar, one = vector, more = sub-cubelet. No annotation syntax in v1; tooling hover shows the
    inferred shape.
11. **Broadcasting: align shared, broadcast the rest.** Shared free dimensions align cell-by-cell;
    dimensions free on only one operand broadcast (standard array/MDX semantics). Collapse via
    default agg happens only at an assignment boundary or an explicit agg token — never silently
    inside a binary operation.
12. **One measure per path.** A second measure token is a deterministic error in v1; measure
    tuples may arrive later without breaking anything.
13. **Missing member is a hard error.** Connected mode: compile error. Disconnected: bind-time
    error. No safe navigation, no null-slices in v1 (P2).
14. **Float vs path: the whole-chain rule, decided in the parser.** The lexer's NUMBER token is
    demoted to INT (`[0-9]+`); decimals become a parser rule
    (`floatLiteral : INT DOT INT | DOT INT | INT DOT`), `..` is its own token, and `mdPath`
    requires ≥1 identifier component or ≥3 components. A dotted chain that is entirely
    float-shaped (`2025.06`) is a float; anything else is a path. Escape for float-shaped periods:
    add a qualifying token (`time.2025.06`). Whitespace-insensitive (WS is skipped; the formatter
    canonicalizes). Scientific notation is deferred.
15. **Sets require braces.** `{Kaufland, Lidl}` always; bare same-attribute repetition is an error
    suggesting braces (same-dimension *different-attribute* repetition remains drill/conjunction,
    e.g. `2025.january`).
16. **`with`-block deferred.** Assignment context (LHS scopes RHS) is the only context mechanism
    in v1.
17. **`asof` is run-level only.** One `asof` per compilation/run — a compile-time parameter
    defaulting to evaluation time, pinnable for tests/backfills. No per-expression override in v1.
18. **Disconnected mode requires qualified pairs** for member tokens (`customer.Kaufland`);
    structure checks offline, member existence checks at bind time. No warn-and-hope mode.
19. **The RAE map-vs-list `select` sugar is dropped** — superseded by path assignment + LHS
    context; projection belongs to the Layer B algebra. RAE stays inspiration only.

**Extended in the cubelet-statements session (2026-07-08):** scripts are sequences of statements
over whole cubelets, not just slices.

20. **Statement dispatch by LHS form.** A slice LHS (dot-path with coordinates + measure) keeps
    the strict-writeback semantics (10–19 above). A **bare-identifier LHS** selects the cubelet
    statement family: `C = e` (virtual), `C := e` (materialize), `C += e` (merge), `C -= e`
    (delete). Statements interleave freely in TTR-P programs — no `md script` block, no fragment
    declaration.
21. **`C = e` — virtual cubelet — is a TTR-P variable** (Q7-γ: a name on an edge) whose static
    type is a cubelet shape (free dims + measures). SSA reassignment applies. Dot-paths work over
    script variables: after `C = sales.2025.month.*`, `C.january.net` resolves — the resolver's
    cubelet namespace includes in-scope md-typed variables.
22. **`C := e [with {…}]` — materialization.** C declared in the model → truncate + overwrite
    through the existing binding; `with` forbidden (or must-match). C new → logical definition
    (grain = e's free dims, measures = e's measures) **inferred deterministically**; physical
    choices come from `with { shape: wide|long, table: …, journal: … }` (shape required). The
    definition persists as a **generated `.ttrm`** (text stays canonical); the metadata server
    reports **materialization status** ("declared but not yet real / real but drifted") —
    the dbt-ish loop.
23. **`+=` is merge (upsert) on grain keys**, never blind append; cell-collision resolution = the
    target's journaling mode. On virtual cubelets `+=`/`-=` are pure dataflow (SSA rebind of a
    merge/anti-join) with no persistence effects.
24. **`-=` is region delete by keys** — anti-join on the RHS cells' grain keys; RHS values are
    irrelevant, a measure/agg token on a `-=` RHS is a warning. It is NOT `+= -e`.
25. **Journaling = binding-level mode + role-tagged technical columns.** Mode
    (`overwrite | invalidate | diff`) is a binding property. Technical columns are a
    `semantics { role: … }` family on the backing table (grammar 4.2 — no grammar change):
    `valid_flag`, `valid_from`/`valid_to`, `version`, `authored_by`, `written_at`. Validation
    ties them: `invalidate` requires a valid role; the writer fills version (max+1 per key),
    authorship (run identity), timestamps (run clock). Per-mode `-=`: overwrite → DELETE;
    invalidate → flip valid + version bump; diff → **error** (no delete-markers in v1).
26. **Journaling changes read lowering too.** invalidate → reads wrap the Load in a valid-filter;
    diff → reads are SUM-of-deltas per grain key; and the "latest valid" default aggregation
    (the `kaufland.zip` case) **derives from the `valid_from`/`valid_to` roles** instead of
    manual configuration — this feature and the grounding/semantics-block work meet here.

**Planning artifacts** for the implementation arc (scope: resolver core, TTR-P read paths,
writeback, metadata server / connected mode, agent resolver service; Kotlin-first; own arc) live
under [`dot-path/`](dot-path/): `architecture.md`, `contracts.md`, `plan/implementation-plan.md`.
