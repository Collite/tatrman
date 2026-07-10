# C2 · Fragment dialects (TTR-SQL / TTR-pandas) — Option Catalogue

> Workstream C2 divergence doc, opened 2026-07-04. Companion to [`00-control-room.md`](./00-control-room.md), [`04-surfaces-options.md`](./04-surfaces-options.md) (C0), [`05-canonical-dsl-options.md`](./05-canonical-dsl-options.md) (C3), [`06-model-binding-options.md`](./06-model-binding-options.md) (D).
>
> **The question C2 must answer:** what exactly *are* TTR-SQL and TTR-pandas — the statement subset each dialect covers, how names resolve inside fragments against D's rules, how `err`/`rejects` surface in fragment-land, and what the formatter may touch — such that a fragment stays a retargetable PL island (T5-e pin), never raw dialect passthrough.

**What's already constrained (not on the table):**

- Fragments are **container content** — data-flow islands, never program structure (C0-γ). No containers, control edges, movement, or wiring inside a fragment.
- **T5-e pin:** one PL expression grammar; fragment expressions lift to PL `Expression` trees; functions are catalogue ids (T5-c); NULL is canonical 3VL. TTR-SQL is a *generic* SQL — pasting `NOLOCK` is an error (C0 commitment (b)).
- Embedding = `"""sql` / `"""pandas` tagged block, tag = dialect marker (C3-g). Bare-fragment files: `report.ttr.sql` / `prep.ttr.py` extension or first-line comment override (C3-g-ii, H-2).
- Bare-fragment **source text is never rewritten** (C0); the wrapper container is desugaring. Fragment drill-ins render as **read-only derived sub-graphs**, auto-layout only (C1-b/iv).
- Fragment's final result = the container's **default out port** (C3 §container ports, last-value convention).
- No subquery-valued expressions in the v1 IR; the SQL-like surface *may* parse them and desugar to graph shapes (B-T5 sweep). Semi/anti = Join types; Distinct = sugar → Aggregate; Window → v2; Explode → v2.
- v1 model refs = db + er tiers, package-derived kinds, TTR imports, position-typed namespaces (D-a/b); er desugars early with provenance (E-d); schemas always declared (T7).
- Cross-container `err` (signal) = compile error in v1; fail-fast baseline (F-d). `rejects` crosses engines as ordinary data (F-d-i).

**Prior art:** DFDSL ("Pandish" op-chain, lowers to RelNode — kantheon) · TransDSL (declarative) · RAE (`X = filter(Y, expr)`) · Calcite's SqlParser + Babel (view-only/calcite) · dbt (SQL-select-only discipline) · E-b's CTE-per-node emit (the *inverse* of TTR-SQL's CTE parsing — same shape, both directions).

---

## C2-a · What fragment text compiles TO — decomposition granularity

Sets the frame for everything else. When `"""sql select … where … group by …` is parsed, what lands in the graph?

- **C2-a-α · One opaque island node per fragment.** The fragment is a single graph node; its text is the payload.
  - *Buys:* trivial parser (lex to end-of-block), trivial round-trip.
  - *Costs:* violates T5-e (no expression trees → no typing, no capability check, no retargeting); kills C1-b's derived sub-graph; kills E-d provenance; the "generic SQL" promise becomes unenforceable. **Effectively rejected by standing pins** — recorded for completeness.
- **C2-a-β · Full decomposition into the standard node set.** The fragment parses to ordinary PL nodes (Project/Filter/Aggregate/Sort/Join/…) exactly as if authored in canonical text — one graph vocabulary regardless of authoring surface. A SQL `SELECT` decomposes clause-wise (FROM/JOIN → Join chain · WHERE → Filter · GROUP BY/HAVING → Aggregate + Filter · SELECT list → Project/Calc · DISTINCT → Aggregate sugar · ORDER BY → Sort · LIMIT → Limit); each CTE is a named sub-chain (name = the SSA label, mirroring E-b in reverse); each pandas assignment is an SSA rebind (Q7-γ).
  - *Buys:* T5-e satisfied by construction; capability manifests, T8 rewrites, `pl-conform`, provenance, drill-in sub-graphs — all inherited with zero fragment-specific machinery; dialect ≠ engine falls out (TTR-pandas → Polars emit is just normal E-c).
  - *Costs:* the fragment grammars must cover clause→node mapping precisely; anything unmappable must be *rejected at parse/check time* (P2 — a miss is an error, not a passthrough).
- **C2-a-γ · Dialect-flavored node kinds** (`SqlSelect` node, `PandasChain` node) that lower later.
  - *Buys:* parser stays close to dialect shape; deferred mapping decisions.
  - *Costs:* second node vocabulary through checker/rewriter/manifests (anti-P1); the "later" lowering is exactly β's work anyway, plus a layer.

*Lean: β — the pins have already chosen it; C2-a just says it out loud. Sub-point (fragment nesting, C0 open): fragments are **leaf content** — no `container` inside a fragment; nesting is a canonical-text affair.*

## C2-b · TTR-SQL — the statement subset

What SQL surface ships in v1. The discipline is dbt-shaped: **one query expression per fragment** — SQL's own naming device (CTEs) provides intermediate names; there is no assignment statement in SQL-land.

**Proposed v1 subset (the α cut):**

| In | Maps to | Notes |
|---|---|---|
| `WITH name AS (…), …` + final `SELECT` | named sub-chains; CTE name = SSA label (→ ζ keys, CTE-per-node emit round-trips) | one query expression per fragment |
| `SELECT` list, aliases, expressions | Project / Calc | expressions = the one PL grammar in SQL clothing (T5-e) |
| `SELECT DISTINCT` | Aggregate sugar (B-T10) | |
| `FROM a JOIN b ON …` (inner/left/right/full/cross) | Join | ON-condition → PL expression; ports: see C2-b-ii |
| `WHERE` | Filter | |
| `GROUP BY` / `HAVING` | Aggregate + Filter sugar | AggregateCall arm (B-T5 sweep) |
| `UNION [ALL]` / `INTERSECT` / `EXCEPT` | Union / Intersect / Except nodes | |
| `ORDER BY … [NULLS FIRST\|LAST]` | Sort | emit always states NULLS placement (Q9-3); author may write it, default = NULLS LAST |
| `LIMIT n [OFFSET m]` | Limit | offset: include? (open sub-point) |
| `VALUES (…), (…)` | Values | |
| `EXISTS` / `IN (subquery)` / `NOT …` in WHERE | desugars to semi/anti Join (B-T5 sweep) | the *only* subquery forms accepted |

**Rejected forms (parse-time errors, each with a named diagnostic):** DML/DDL (`INSERT/UPDATE/DELETE/CREATE/…` — A3: no writes beyond Store); vendor syntax (hints, `NOLOCK`, `TOP n`, backtick quoting, `::` casts → `CAST(…)`); scalar/correlated subqueries in expressions (no IR home); window functions (v2); procedural SQL (variables, `IF`, loops); multiple statements / `;`-chains (one query expression per fragment); `SELECT INTO`.

- **C2-b-i · Subset sizing fork.** **α · The cut above** (workhorse SELECT algebra; everything the hero + CTE-shaped analyst SQL needs). **β · Minimal core** (single SELECT, no CTEs/set-ops — cheapest, but CTE-less SQL is unwriteable for real work and betrays the E-b round-trip symmetry). **γ · Maximal generic SQL** (add PIVOT, GROUPING SETS, LATERAL — each drags dialect-divergent semantics into "generic" SQL; Pivot exists as a node but its *SQL surface syntax* is nowhere near generic). *Lean: α; PIVOT authoring stays canonical-DSL-only in v1.*
- **C2-b-ii · Ports in FROM position.** C3-c's named-only rule is canonical-text law; SQL's own syntax *is* the port assignment: in `a JOIN b`, position carries left/right — that is SQL semantics, not a violation (the fragment grammar maps it deterministically). Container in-ports appear as plain table names in FROM; `left.`/`right.` qualification is not required inside fragments (SQL's alias mechanism does that job).
- **C2-b-iii · `SELECT *`.** **α ban** (P2-flavored: schemas are static, spell it out) vs **β allow, expand statically at compile** (schemas *are* static — `*` is deterministic, not a miracle; analyst ergonomics; diff-visible expansion available via formatter/`pl explain`). *Lean: β — determinism is what P2 demands, verbosity isn't.* (`*` survives as authored text; expansion is internal.)

## C2-c · TTR-pandas — the dialect's shape and subset

The identity question first — three candidate shapes:

- **C2-c-α · Literal pandas API subset.** `df[df.amount > 0]`, `.groupby(…).agg(…)`, boolean masks, `merge(…)`.
  - *Buys:* maximum persona familiarity.
  - *Costs:* boolean-mask indexing and lambda-bearing idioms **cannot lift to the one PL expression grammar** (T5-e breaks); needing a Python parser for a non-Python language; pandas semantics (index alignment, NaN≠NULL) contradict canonical 3VL — the dialect would *look* like pandas but *lie* about semantics. Dialect ≠ engine already told us this isn't pandas-the-library.
- **C2-c-β · Method-chain dialect over the PL op vocabulary** (DFDSL/"Pandish" descended). `sales = load(files.sales_2026).filter(amount > 0 and customer is not null)` · `j = accounts.join(right: sales, on: …, type: inner)` · `.select(…)`, `.aggregate(…)`, `.sort(…)`, `.limit(…)`; SSA reassignment per Q7-γ; expressions = the one PL grammar (bare column names, no `pl.col`/mask noise).
  - *Buys:* T5-e holds by construction; ops = the node set (C2-a-β for free); *reads* like dataframe code (the persona's muscle memory is `.verb().verb()`, not the exact pandas argnames); lexically Python-enough that `.ttr.py` highlighting works (H-2's stated payoff); this is exactly the `.op()`-chaining niche C3-b explicitly reserved for TTR-pandas when rejecting it for canonical text.
  - *Costs:* not copy-paste-compatible with real pandas scripts (accepted: neither is TTR-SQL with real T-SQL — same commitment (b) honesty).
- **C2-c-γ · Polars API subset.** `.filter(pl.col("amount") > 0)`.
  - *Buys:* closest to a v1 emit target.
  - *Costs:* `pl.col("…")` noise is exactly what the PL expression grammar exists to avoid; ties the *dialect* to one engine's API evolution (the anti-"dialect ≠ engine" move).

*Lean: β, firmly — it is the only shape all pins permit. α and γ fail T5-e on contact.*

- **C2-c-i · Method set = the op vocabulary, spelled lowercase:** `.select() .calc() .filter() .join() .aggregate() .sort() .union() .limit() .branch()`? — sub-fork: does `branch` (multi-out) exist in fragment-land, or are fragments single-result like TTR-SQL? (see C2-e; multi-out needs port surfacing at the container boundary). *Lean: single default-out in v1 for both dialects — symmetric, and multi-out is what canonical containers are for.*
- **C2-c-ii · Statement forms:** assignment (`x = expr-chain`) + a final bare chain or name = default out (same last-value convention as canonical containers / TTR-SQL's final SELECT). No control flow, no `def`, no imports of Python modules — parse-time rejects with named diagnostics (mirror of C2-b's reject list: no `.apply(lambda …)`, no `.to_sql()`/IO beyond `load()`, no index ops).

## C2-d · Name resolution inside fragments

D gave us position-typed namespaces, package-derived ref kinds, TTR qname + import rules. What reaches inside a fragment?

- **C2-d-α · Document scope flows in.** Fragments resolve names in the host document's scope: container in-ports first, then document imports (`import erp.er.*`), then full qnames. One scope story for the whole file.
  - *Buys:* zero new rules; the hero's `from erp.accounts` works because the document (or project) imported it; short names behave identically in canonical text and fragments.
  - *Costs:* a fragment's meaning depends on its host document's import block (acceptable — the same is true of every canonical statement).
- **C2-d-β · Fragments are hermetic.** Only in-ports + fully-qualified model refs resolve; imports don't penetrate.
  - *Buys:* fragment text is context-free, paste-anywhere.
  - *Costs:* qname noise in exactly the surface meant to be friendly; bare fragments (no document, no ports) would *only* speak full qnames.
- **C2-d-γ · Per-fragment import header** (dialect-flavored: `-- import erp.*`).
  - *Buys:* hermetic *and* short.
  - *Costs:* a second import mechanism; comment-syntax semantics (P2 frowns); diverges per dialect.

*Lean: α; for bare fragments "document scope" = the project defaults' implicit prelude (project-manifest key: default imports? — new key for D-e's list, or bare fragments just use qnames).* Sub-points:

- **C2-d-i · Shadowing:** port name vs model object name collision in FROM/load position — resolution order ports > imports > qnames, same-level ambiguity = error (D-b-β's position-typing discipline; deterministic, P2).
- **C2-d-ii · er refs in fragments:** `from erp.er.customer join … on relation cust_accounts` — the er tier and `on: relation` (D-a sub-2) are available inside TTR-SQL/TTR-pandas identically to canonical text; early rewrite + provenance (E-d) means the emitted SQL says `CUST_TYPE` while diagnostics say `customerType`. The two-tier test D-a wanted *runs through fragment-land too*.
- **C2-d-iii · Column scope:** C3-a-iv-3 holds — expression scope = input columns only; no variable capture from canonical text into fragment expressions (fragments see ports as *tables*, never document variables as *values*).

## C2-e · `err` / `rejects` surfacing in fragment-land

The container boundary owns the ports (C3-f); the fragment body is where rows go wrong. Who wires them?

- **C2-e-α · Fragments expose `err` only; `rejects` is canonical-land machinery in v1.** A fragment container gets the signal port for free (island fails ⇒ `err`, unconnected ⇒ fail-fast, F-d); no rejects-producing syntax inside dialects. Erroneous-row routing (the hero's `j.rejects`) lives in canonical containers.
  - *Buys:* nothing to invent; honest about B-T2's own flag ("erroneous-rows → SQL will be hard"); dbt/SQL authors don't expect reject rows from a SELECT anyway; keeps both dialect grammars closed over vanilla statement forms.
  - *Costs:* a bare-fragment program can never route rejects (must graduate to canonical text — arguably the *right* graduation pressure).
- **C2-e-β · Dialect syntax for reject taps.** e.g. TTR-SQL `WITH REJECTS name AS OF <cte>` / TTR-pandas `.rejects()` — surfacing an inner node's reject stream as an extra container out-port.
  - *Buys:* full C3-f power in every surface.
  - *Costs:* invented syntax in a "generic SQL" (violates the spirit of commitment (b)); multi-out fragments break the single-result convention (C2-c-i); v1 has no SQL-side reject *producer* semantics anyway (E/T8 unsolved) — grammar for a stream nothing fills.
- **C2-e-γ · Boundary binding in the container header** (canonical-side: `container prep(out result, rejects) target pg """sql …` + a binding clause naming an inner CTE). Splits one concern across two syntaxes; the header names fragment-internal structure (leaks through the closed-container membrane).

*Lean: α for v1 — with the explicit note that rejects-in-SQL is already flagged as an E/T8 work item; β's syntax can be designed when a producer semantics exists (v1.x, alongside erroneous-rows-in-SQL).*

## C2-f · Formatter posture inside fragments

- **C2-f-α · Fragment interiors are untouchable — everywhere.** The formatter formats canonical text *around* tagged blocks (fence placement, indentation of the fences themselves) but never a byte inside; bare-fragment files are never formatted at all. One rule: **fragment text is the author's** (extends C0's bare-fragment guarantee to embedded fragments).
  - *Buys:* one story; SQL teams keep their house style; no SQL-pretty-printer to build/argue about; C1-d's formatter-owned placement is unaffected (structured edits never target fragment interiors — drill-ins are read-only).
  - *Costs:* inconsistent look inside one document (canonical text machine-formatted, fragments hand-formatted) — accepted as the nature of a quoted block.
- **C2-f-β · Formatter owns fragments too** (deterministic keyword-case/indent/comma style per dialect). *Buys:* uniformity. *Costs:* builds two pretty-printers; rewrites authored text (collides head-on with the bare-fragment guarantee — same text formatted when embedded, untouched when bare?); zero user demand identified.
- **C2-f-γ · Opt-in per project** (`[pl] format-fragments = true`). Machinery of β behind a flag; still builds the printers.

*Lean: α. Corollary worth recording: **trivia/whitespace inside fragments is preserved verbatim in the AST** (the fragment's `sourceText` is canonical, its parse is derived) — the same lossless posture TTR-M's CST layer takes.*

## C2-g · Fragment parser ownership

- **C2-g-α · Own ANTLR grammars, sized to the subset** (`TTRSql.g4`, `TTRPandas.g4`, beside the TTR-P grammar; possibly island-mode grammars invoked from the host lexer's tagged-block token).
  - *Buys:* exact subset control (rejects are *grammar* rejects with named diagnostics — P2's "a miss is an error" wants precision); byte-precise spans for provenance/hover (E-d, C1-b); one toolchain (antlr-ng/Kotlin, same as TTR-M); LSP-grade recovery.
  - *Costs:* we write and maintain two grammars (bounded by the deliberately small subsets).
- **C2-g-β · Reuse Calcite's SqlParser (Babel) + SqlNode→PL converter.**
  - *Buys:* free, battle-tested SQL coverage.
  - *Costs:* subset control inverts (we must *reject* most of what Babel accepts — the error surface becomes a blocklist, anti-P2); SqlNode positions are statement-grained where we need token-grained; couples author-time parsing to Calcite's release cadence; and TTR-pandas gets no such gift anyway (asymmetric architecture).
- **C2-g-γ · Hand-rolled recursive-descent.** No case: ANTLR is already the family toolchain.

*Lean: α — Calcite stays where E put it (the emit boundary, via ttr-translator), not in the author-time front-half.*

---

## Converged (2026-07-04) — C2 is 🟢

- **C2-a = β · Full decomposition.** Fragment text parses to the standard PL node set (clause-wise for SQL, statement-wise SSA for pandas); one graph vocabulary regardless of authoring surface. Fragments are leaf content — no containers inside (C0 open closed). α/γ rejected per the standing pins.
- **C2-b = α · TTR-SQL workhorse cut.** One query expression per fragment (`WITH` + final `SELECT`; CTE name = SSA label — E-b's inverse); full clause table above; `EXISTS`/`IN` desugar to semi/anti Join (the only subquery forms); rejects list with named diagnostics. **C2-b-ii:** SQL's own syntax carries port assignment (JOIN position = left/right) — C3-c is canonical-text law, not fragment law. **C2-b-iii = β:** `SELECT *` allowed, expanded statically at compile (deterministic under static schemas — P2 demands determinism, not verbosity); authored text keeps the `*`.
- **C2-c = β · TTR-pandas = method-chain over the PL op vocabulary** (DFDSL-descended, "dataframe-shaped TTR-P"). Chaining `.filter(…).select(…)`, PL expressions bare (no masks, no `pl.col`), SSA reassignment per Q7-γ. The only shape whose expressions are *grammar*, not *API* — α/γ fail T5-e structurally and lie at the NaN/NULL boundary. **C2-c-i: single default-out v1** for both dialects (final SELECT / last value = the container's default out; multi-out is what canonical containers are for — the graduation boundary). **C2-c-ii:** statement forms = assignment + final chain; Python control flow / lambdas / IO-beyond-`load()` are parse-time rejects.
- **C2-d = α · Document scope flows in.** Fragments resolve in the host document's scope: in-ports > document imports > full qnames; same-level ambiguity = error (C2-d-i). er refs + `on: relation` work inside fragments identically to canonical text, early-rewrite + provenance (C2-d-ii). Expression scope = input columns only; document variables never reach fragment expressions (C2-d-iii). Bare fragments: project defaults as implicit prelude (`default-imports` key question → leftover).
- **C2-e = α · `err` only in v1.** Fragment containers expose the signal port for free (island fails ⇒ `err`; unconnected ⇒ fail-fast, F-d); no rejects-producing dialect syntax — rejects routing stays canonical-land. β's tap syntax waits for an actual SQL-side erroneous-rows producer semantics (the E/T8 work item), v1.x at earliest.
- **C2-f = α · Fragment interiors untouchable — everywhere.** Formatter never modifies a byte inside tagged blocks; bare-fragment files never formatted (extends C0's guarantee). Fragment trivia preserved verbatim; the fragment's `sourceText` is canonical, its parse is derived.
- **C2-g = α · Own ANTLR grammars** (`TTRSql.g4`, `TTRPandas.g4`, same antlr-ng/Kotlin toolchain), sized to the subsets; rejects are grammar rejects with named diagnostics; byte-precise spans for provenance/hover. Calcite stays at the emit boundary where E put it.

## Leftover sub-points (→ grammar prototyping / consolidation)

- `LIMIT … OFFSET` — in or out of the v1 TTR-SQL cut?
- Reserved-word policy: TTR-SQL keyword set vs PL expression grammar's (`and`/`or`/`is not null` shared — one keyword table or two?). Grammar-prototype detail.
- TTR-pandas method-name roster: exact spelling per op (`aggregate` vs `agg`? `calc` vs `with_column`?) — settle in grammar prototyping with the formatter rules (C3's n-ary Union sub-point rides along).
- Bare fragments + imports: does the project manifest gain a `default-imports` key (D-e list grows), or do bare fragments speak qnames only? (Small, but P2 wants it stated.)
- Fragment-internal comments → do they attach as trivia to derived nodes (canvas hover shows the author's `-- note`)? Nice-to-have; decide with the CST layer.
- Diagnostics UX for rejected dialect forms: error message names the *generic* alternative (`TOP 10` → "use LIMIT 10") — worth a small curated table per dialect?

## Cross-links

C2 → T5-e/T5-c (expression grammar + catalogue ids inside dialect clothing) · C2 → E-b (CTE names ↔ SSA labels — authoring is emit's inverse) · C2 → E-d (er provenance through fragments) · C2 → C1-b (derived sub-graphs need C2-a-β) · C2 → D-e (possible `default-imports` key) · C2 → F-d (fragment `err` = fail-fast) · C2 → H-2 (`.ttr.sql`/`.ttr.py` highlighting constrains dialect lexis) · C2 → consolidation (reject-diagnostic tables; grammar prototyping work items).
