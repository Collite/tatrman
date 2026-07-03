# C3 — Canonical Flow-DSL: Option Catalogue (DIVERGENCE)

> **Mode: divergence.** Enumerate alternatives + trade-offs. **No decisions here** — those go to the control-room decision log.
> Control surface: [`00-control-room.md`](./00-control-room.md). Surface architecture (C0, converged): [`04-surfaces-options.md`](./04-surfaces-options.md). Internal model: [`02-internal-model-options.md`](./02-internal-model-options.md).
> Opened 2026-07-03. Agenda from [`next-steps-260702b.md`](./next-steps-260702b.md).

**The question C3 must answer.** What is the concrete grammar of the **canonical flow-DSL** — the file format and full-coverage language (C0-b)? The pressure test is the hero scenario (A5) written end-to-end.

## What the grammar MUST express (constraints from B/C0 — the checklist)

1. **Containers** with author-assigned execution **targets** (v1 placement) and optional content **dialect** (`as sql` / `as pandas`).
2. **Ports**: named + default, multicast out, no implicit union in; multi-in via explicit named inputs (Join/Union).
3. **Control deps** FS / SS / FF (acyclic, static, hard-on-effect).
4. **Error ports**, both modes (signal, erroneous-rows).
5. **Movement**: Load / Store / Transfer (+ Index; Materialize is a macro — surfaced or not?).
6. **Variables** = SSA edge-sugar with reassignment, data-only (Q7-γ).
7. **Embedded fragments** (TTR-SQL / TTR-pandas) per container; bare-fragment documents live *outside* this grammar but share the dialect marker.
8. **Layout block** (G-e: layout serializes into PL text).
9. **World / model references** (compile target T6; objects resolve via TTR models — D).
10. One document = one program = one graph (T4); expressions are the one PL expression grammar, inline (never strings) (T5-e).
11. `Display` (Q11) — the results-to-frontend sink, stub or resolve here.
12. **P2 — no miracles**: anything elided must be deterministically derivable from project/world defaults.

---

## The hero scenario, rendered (the pressure test)

A5: accounts (Postgres) + sales (CSV) → join → summarize → branch on filter, error path; SQL island + Polars island, Charon between; ends in Display.

### Rendering γ · "arrow-flow hybrid" (pipelines + assignment, both first-class)

```pl
program sales_summary
uses world "acme-dev"                      // C3-h: or from project defaults

// ── SQL island ──────────────────────────────────────────────
container acc_prep target erp_pg as sql <<<
    select account_id, branch_code, region
    from erp.accounts                      -- TTR model object (D)
    where status = 'ACTIVE'
>>>
// fragment's final result = acc_prep's default out port

// ── Polars island ───────────────────────────────────────────
container crunch(in accounts, out result, out low, err rejects) target polars {

    sales = load(files.sales_2026, schema: sales_csv)          // world storage + declared schema (T7)
    sales = filter(sales, amount > 0 and customer is not null) // SSA reassignment (Q7-γ)

    j = join(accounts, sales,
             on: accounts.account_id = sales.customer
                 and accounts.branch_code = sales.branch,
             type: inner)

    sums = j -> aggregate(group: [region],
                          total: sum(amount),
                          avg_amt: avg(amount))                // chain and assignment mix freely

    b = branch(sums, total > 100000)

    result  = b.true                                           // bind container out-ports
    low     = b.false
    rejects = j.err                                            // erroneous-rows port
}

// ── program-level wiring ────────────────────────────────────
acc_prep -> crunch.accounts       // engine crossing: compiler synthesizes Store+Transfer+Load? (C3-d-iv)
crunch.result  -> display         // Q11
crunch.low     -> store(files.low_regions)
crunch.rejects -> store(files.join_errors)

// control {}  — none needed: the data edge already implies FS at collapse (C3-e)

layout <<<
    acc_prep: 120,80  crunch: 420,80                           // format TBD (C3-h)
>>>
```

### Rendering α · "Kyx-chain evolved" (`+` composition, config blocks)

```pl
program sales_summary

container acc_prep target erp_pg as sql <<< ... >>>

container crunch target polars {

    val sales =
        Load { source = files.sales_2026; schema = sales_csv } +
        Filter { amount > 0 and customer is not null }

    val j = Join(In(accounts), sales) {
        On(account_id, customer)
        And(branch_code, branch)
    }

    j + Summarize {
            Sum(amount) As total
            Avg(amount) As avg_amt
            GroupBy(region)
        } +
        Branch { total > 100000 } As b

    Out(result) = b.True
    Out(low)    = b.False
    Err(rejects) = j.Err
}

acc_prep + crunch.accounts
crunch.result + Display
```

*Honest observation:* freed from Kotlin's operator-overloading constraints, α drifts toward γ on every axis — `Expression = "…"` strings become real expressions (T5-e forces this), `Source = "SNOWFLAKE::…"` becomes world refs (P2/T6 force this), `Out(x) = …` is a clunkier port binding. What survives of Kyx is *structural*, not lexical (see the lineage table below).

### Rendering β · "assignment-only" (RAE lineage) — excerpt + why it collapses

```pl
sales = load(files.sales_2026, schema: sales_csv)
sales = filter(sales, amount > 0)
j     = join(accounts, sales, on: …)
sums  = aggregate(j, group: [region], total: sum(amount))
hi    = filter(sums, total > 100000)      // ← branch forced into two filters,
lo    = filter(sums, total <= 100000)     //   or b = branch(…); b.true anyway
```

β has no story for **multi-out** (Branch/Switch/error ports) except port projection `b.true` — at which point it *is* γ minus the pipeline form. And under C0-γ this style already exists as the **TTR-pandas fragment dialect** at container level. β as the *canonical* grammar buys nothing the fragment doesn't.

### Rendering δ · "explicit graph core" (the weird one — and the desugar target)

```pl
node f1 = filter(amount > 0)
node j1 = join(type: inner, on: left.account_id = right.customer)
edge sales.out -> f1.in
edge f1.out -> j1.left
```

Not a pleasant authoring surface — but **every candidate desugars to this** conceptually. Sub-fork: is δ *writeable* (a degenerate form inside the same grammar — useful for generated code, structured-edit round-trips, diff stability) or internal-only?

---

## Kyx lineage table (agenda item 2)

| Kyx feature | Verdict for canonical PL |
|---|---|
| `+` chaining | Candidate connector token, but was *forced* by Kotlin operator overloading → fork C3-b |
| `.True/.False` anchors | **Carries** — named ports (`b.true`, `b.false`), generalized (`j.err`); casing TBD |
| Config blocks `Filter { Expression = "…" }` | **Obsoleted** — expressions are inline PL grammar (T5-e), not strings; block *shape* may survive for big ops (Aggregate/Pivot) → C3-a-iii |
| `Input { Source = "SNOWFLAKE::…" }` | **Obsoleted** — world-referenced storage (`files.sales_2026`), P2 + T6 |
| `Join(a, b) { On(…) }` | **Carries** — multi-in via explicit arguments |
| Variables naming chain results | **Carries** + reassignment (Q7-γ) |
| `Browse` | Becomes `Display` (Q11) |
| `workflow { }` wrapper | Becomes the document envelope; **containers** are the real grouping |
| Emit to `.yxmd` | Becomes E emit via manifests |

---

## The forks

### C3-a · Statement form (load-bearing)

Q7-γ already commits to **assignment with SSA reassignment**. The real fork: does the grammar *also* have a pipeline/chain form, and which is stylistically primary?

- **α · Chain-primary** (Kyx): composition operator everywhere; variables only to name branch points. *Buys:* reads like the graph. *Costs:* reassignment (`X = filter(X,…)`) is alien to it; multi-in still needs call form; drifts to γ under our own constraints (see above).
- **β · Assignment-only** (RAE): no connector at all; edges implied by argument use. *Buys:* smallest grammar. *Costs:* multi-out awkward; duplicates the TTR-pandas fragment's niche; long linear flows become name-a-thon.
- **γ · Hybrid**: pipelines (`a -> op(…) -> op(…)`) **and** assignment, freely mixed; a variable names any edge; a chain is an expression-position source. *Buys:* linear flows chain, DAG-y parts name; matches "variables are edge-sugar" exactly. *Costs:* two ways to write everything (style-guide problem, formatter must pick); precedence/ambiguity rules needed (C3-a-iv).
- **δ · Explicit-graph as the only canonical**, all sugar in projections. Rejected in spirit by C0-b (flow-DSL = human-writable canonical), listed for completeness.

Sub-forks:
- **C3-a-iii · Op-argument surface:** named args (`aggregate(group: […], total: sum(amount))`) vs config blocks (`aggregate { group by region; total = sum(amount) }`) vs both (blocks only for wide ops: Aggregate, Pivot, Switch).
- **C3-a-iv · Composition rules** (agenda item 3): proposed baseline — precedence `=` < `->` < call; a chain is allowed anywhere a source ref is (so `join(load(f) -> filter(…), sales)` is legal but discouraged); multicast = referencing the same variable in ≥2 statements; **expression scoping**: inside op expressions, bare identifiers resolve to input columns ONLY (variables are not in expression scope — no correlation); multi-in ops qualify columns by port name (`left.x`) or connected variable name when unambiguous.

*Lean: γ.*

### C3-b · Connector token (if chain form exists)

| Token | Buys | Costs |
|---|---|---|
| `->` | Directional, edge-evocative, matches `edge a -> b` and program-level wiring; one token everywhere | Collides with lambda syntax if T5 ever grows lambdas (v1: it doesn't) |
| `\|>` | Functional-pipe pedigree, unmistakably "flow" | Two chars, shifty to type; second token still needed for explicit `edge` decls? |
| `+` | Kyx continuity | Visual collision with arithmetic in adjacent inline expressions; non-directional |
| `.op()` method-chain | pandas-familiar | Blurs canonical/TTR-pandas tier boundary (C0-γ wants the tiers distinguishable); binds chain to receiver syntax |

*Lean: `->` — same token for statement-level chains and wiring statements.*

### C3-c · Ports in text

- Reference: `node.port` (settled in spirit by Kyx/B-T2). Casing: `b.true` vs `b.True` — lean lowercase (reserved port names: `in`, `out`, `err`, `true`, `false`).
- Default-port elision: `acc_prep -> crunch.accounts` means `acc_prep.out -> …`; `a -> b` means `a.out -> b.in`.
- Multi-in: positional (`join(a, b)` = left/right) vs named (`join(left: a, right: b)`) vs both (positional canonical, named allowed).
- Switch out-ports = case labels: `s = switch(x, big: total > 1000, small: total <= 100)` → `s.big`, `s.small`, `s.else`.

### C3-d · Containers

- **i · Header:** `container <name> [( ports )] target <engine-instance> [as <dialect>] <body>`. Target names an engine **instance from the world** (T6 instance overlay).
- **ii · Port declaration:** (1) signature-style (`(in accounts, out result, err rejects)`) + body binds by name; (2) `out`/`err` statements in the body only; (3) last-value convention (deterministic, P2-legal — the bare-fragment rule "final result = default out" is exactly this). Possibly: signature canonical, last-value as the default-out sugar.
- **iii · Cross-container references:** (1) **closed functions** — bodies reference only own ports; wiring lives at program level; (2) **lexical reach** — bodies may reference outer/other-container names, compiler synthesizes the ports (deterministic). (1) is the model's own story (T9: ports mapped to internals, container = function); (2) is friendlier to quick scripts. Both P2-compatible.
- **iv · Movement synthesis:** does the author write Store/Transfer/Load, or does a cross-engine data edge (`acc_prep -> crunch.accounts`) **lower** to Store+Transfer+Load as a T8 capability rewrite? Synthesis is deterministic (staging location from project/world defaults — new D dependency: **default staging area**). Explicit forms stay available for control (staging choice, `index`, `materialize`). *Lean: synthesized, explicit override* — it's exactly what the graphical surface needs (draw a wire between containers).
- **v · Nesting:** C0-c says allowed, same rules apply. Grammar cost is low if containers are just statements.

### C3-e · Control-edge syntax

Cross-container **data** edges already imply FS at container-collapse; explicit control edges are for *non-data* dependencies — rare. Options:

- **α · Arrow family:** `a -FS-> b`, `a -SS-> b`, `a -FF-> b` (visually parallel to data `->`).
- **β · Keyword clauses:** `b after a` (FS) · `a with b` (SS) · `a finishes with b` (FF) — reads as intent, no new tokens.
- **γ · Control block:** `control { b after a; … }` — collects them in one place (graphical view parity: dashed wires layer).
- *Lean: β keywords, allowed both inline (as a container-header suffix?) and inside an optional `control {}` block.*

### C3-f · Error ports

- One port, mode a property of the node/manifest (`j.err` is rows here, signal there) — vs **two reserved names**: `node.err` (signal) and `node.rejects` (erroneous-rows; the ETL term). Two names make the mode textually visible; one name keeps the port set smaller.
- Unconnected error port = fail-fast default (F-lite pre-answer) — elision is P2-legal (deterministic default).

### C3-g · Fragment embedding + dialect marker (agenda item 4)

**Embedding lexical form:**
- **α · Fenced/heredoc block** (`<<< … >>>`, or ```` ```sql ```` fences): mode-switch on an unambiguous delimiter; robust for *any* dialect content. TTR's `sourceText` precedent.
- **β · Braces with lexer mode-switch** (`as sql { … }`): visually uniform with flow bodies; **breaks on brace-bearing content** — pandas dict literals — unless the lexer brace-counts per dialect (fragile).
- **γ · Indentation block**: whitespace-significant; alien to the TTR family.
- *Lean: α.* Note the layout block can reuse the same fence mechanism.

**Bare-fragment dialect marker (C0 commitment (a)):**
- **α · File extension:** `.ttrsql`, `.ttrpd` (or `.pl.sql`).
- **β · Magic first line in comment syntax:** `-- pl: dialect=sql` / `# pl: dialect=pandas` — the file **stays valid for foreign tooling** (an editor/DB console still reads the SQL).
- **γ · Both** (extension = default, header = override/confirmation). *Lean: β or γ — the comment form is self-describing when files travel.*

### C3-h · Document envelope

- **Program name:** `program <name>` header vs filename-derived (P2-legal). A header line is also the natural home for `uses world`.
- **World ref:** in-document `uses world "acme-dev"` vs project defaults (modeler.toml-equivalent) with optional in-document override. Bare fragments can't carry it in-band comfortably → project defaults must supply it anyway (D).
- **Layout block:** per-document (TTR v1.1 layout-block precedent; one block at the end) vs per-container (composes with nesting; fragment containers have no inner layout — their sub-view is derived or opaque, the C0 open question). *Lean: per-document for v1, keyed by node path.*
- **Q11 `Display`:** propose resolving the *node semantics* here, deferring transport: sink-only leaf; dynamic schema allowed (the T7 output exception); multiple displays per program allowed, named (`display` / `display(main_result)`); project default "bare program's final result → display". Transport (Arrow stream via Designer server, `pl/*` method) → G/E session.

---

## RESOLVED (2026-07-03) — C3 converged

All agenda forks decided in-session (full text in the control-room decision log):

- **C3-a = γ hybrid** (chains + assignment, both first-class; formatter carries the style rule) · **C3-b = `->`** (one token: chains, wiring, δ edges) · **C3-a-iii = both** (named args canonical, config block for wide ops) · **C3-a-iv baseline confirmed** (precedence `=` < `->` < call; chains in source position legal; expression scope = input columns only; port-name qualification).
- **C3-c = named-only multi-in** (`join(left: …, right: …)`; open sub-point: n-ary Union form → formatter rules).
- **C3-d-iii = closed containers** (wiring at program level) · **C3-d-iv = synthesized movement** (cross-engine edge lowers to Store+Transfer+Load; staging from project defaults → D).
- **C3-e = keyword control** (`b after a` · `a with b` · `a finishes with b`; optional `control {}` grouping).
- **C3-f = two error ports**: `err` (signal) + `rejects` (rows); unconnected ⇒ fail-fast.
- **C3-g = `"""sql` tagged block, tag-only** (TTR's `TAGGED_BLOCK_LITERAL` reused; tag IS the dialect marker; no `as` header) · **bare-fragment marker = extension + first-line comment override**.
- **C3-h: layout/view-state SIDECAR** (same name, different suffix → H; **amends G-e**; bare-fragment layout forced it) · **world = project default + `uses world` pin** · **Q11 Display semantics resolved** (sink-only leaf, dynamic schema per T7 exception, named/multiple, bare-program default sink; transport → G/E).

### The converged hero rendering

```pl
uses world "acme-prod"                      // optional pin; default from project (D)

container acc_prep target erp_pg """sql
    select account_id, branch_code, region
    from erp.accounts
    where status = 'ACTIVE'
"""

container crunch(in accounts, out result, out low, err rejects) target polars {

    sales = load(files.sales_2026, schema: sales_csv)
    sales = filter(sales, amount > 0 and customer is not null)

    j = join(left: accounts, right: sales,
             on: left.account_id = right.customer
                 and left.branch_code = right.branch,
             type: inner)

    sums = j -> aggregate {
                  group by region
                  total   = sum(amount)
                  avg_amt = avg(amount)
                }

    b = branch(sums, total > 100000)

    result  = b.true
    low     = b.false
    rejects = j.rejects                     // erroneous rows (C3-f)
}

acc_prep -> crunch.accounts                 // cross-engine edge: Store+Transfer+Load synthesized (C3-d-iv)
crunch.result  -> display(main_result)      // Q11
crunch.low     -> store(files.low_regions)
crunch.rejects -> store(files.join_errors)

// control: none needed — the data edge implies FS at collapse (C3-e)
// layout: lives in the view-state sidecar (same name, different suffix — C3-h)
```

## Open leftovers (grammar-prototype details, not forks)

- Formatter posture for γ's "two ways to write it" (when to convert assignment↔chain; chain-linear-runs rule).
- `=` for both statement binding and expression equality (context-separated) — acceptable, or force `==` in expressions? Decide when writing the expression grammar.
- Is δ (explicit node/edge form) writeable surface syntax or internal-only?
- Reserved port names list (`in`, `out`, `err`, `rejects`, `true`, `false`, `else`); port name lexical rules vs column name rules; port casing (lean lowercase).
- `materialize` is NOT surface syntax (consequence of B-T9 optimization-only — confirm when the node-vocabulary doc is written).
- n-ary Union input form (`in1:/in2:` vs list) — with formatter rules.
- `program <name>` header: keep, or filename-derived (P2-legal)? (`uses world` can stand alone either way.)
- Graphical rendering of fragment containers (sub-graph vs opaque code container) → the graphical session (C1).
- Sidecar suffix + dialect file extensions + the canonical language's NAME → H.

## Cross-links

C3 → Q11 (Display semantics land here, transport → G/E) · C3 → D (staging defaults, schema refs, world/project defaults — **urgency raised again**) · C3 → T5-e (expression grammar embedded; `=`/`==`) · C3 → C0 leftovers (fragment lexical form, layout placement — resolved here) · C3 → H (the canonical language needs a name; "pl" placeholder used in fences) · C3 → G (formatter, structured-edit round-trip prefers δ-writeable?).
