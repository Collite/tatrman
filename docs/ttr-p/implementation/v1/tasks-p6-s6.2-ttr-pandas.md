# Tasks · P6 · Stage 6.2 — TTR-pandas dialect

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

`TTRPandas.g4` + Kotlin decomposition: the hero island's TTR-pandas equivalent authored as a `"""pandas` tagged block parses and decomposes statement-wise into the standard node set (C2-a-β) — method-chain over the PL op vocabulary (C2-c β, "dataframe-shaped TTR-P"), **full-word method roster** `select calc filter join aggregate sort union limit load store display` (S17, abbreviations rejected with suggestions), assignment = SSA rebind (Q7-γ, C2-c-ii), last value = the container's single default out (C2-c-i), `==` accepted as the closed dialect synonym for `=` (S9) while staying a reject everywhere else. `.apply`/lambdas/pandas-IO/masks are parse-time rejects driven by a versioned reject table (contracts §8). Interiors byte-untouchable (C2-f).

**Alignment notes:** identical to Stage 6.1's (P0/P1 task lists absent at drafting; module = `ttrp-frontend`; Gradle-ANTLR pattern from `packages/kotlin/ttr-parser/build.gradle.kts`; S16 shared table consumed, not duplicated; TTR-P is Kotlin-only — no TS/TextMate work here). TTR-pandas is **not Python**: it is Python-*looking* for `.ttr.py` highlighting (H-2), never Python-parseable — do not reach for a Python grammar.

## Pre-flight (all must pass before T6.2.1)

- [ ] Stage 6.1 DONE (this stage reuses its host-integration seam, `FragmentScope`, corpus layout, and reject-table conventions).
- [ ] `./gradlew :packages:kotlin:ttrp-frontend:test` green.
- [ ] S16 shared expression/keyword unit + Stage-1.1 diagnostic framework located (same Pre-flight as 6.1; note paths here: `_______`).
- [ ] Confirm `TTRP.g4` lexes `"""pandas` tagged blocks opaque with tag + `sourceText` + offset available (C3-g).
- [ ] Note the exact `TTRP-EQ-001` implementation site (Stage 1.1, S9) — T6.2.6 must *not* weaken it outside TTR-pandas: `_______`.

## Tasks

### T6.2.1 · TTR-pandas reject table — the versioned fixture

- [ ] Create `packages/kotlin/ttrp-frontend/src/main/resources/rejects/ttr-pandas.rejects.toml` — same TOML shape and header as `ttr-sql.rejects.toml` (T6.1.1): `[[reject]]` with `id`, `form`, `example`, `message`, `suggest`, `decision`. Reviewable, versioned (contracts §8).
- [ ] Entries (IDs proposed here — `PD` area is already named in contracts §8, no changelog entry needed):

| id | form | example | suggest | decision |
|---|---|---|---|---|
| TTRP-PD-001 | abbreviated/out-of-roster method | `.agg(…)` | "use aggregate" (nearest-roster suggestion; `.grp`→"use aggregate"? no — suggest only on the curated abbreviation list: `agg`→aggregate, `sel`→select, `filt`→filter, `lim`→limit; otherwise "not in the TTR-pandas method roster: select calc filter join aggregate sort union limit load store display") | **S17** |
| TTRP-PD-002 | `.apply` / lambda | `.apply(lambda r: …)` | "no lambdas — write the expression directly in filter/calc (expressions are grammar, not API)" | C2-c-ii, T5-e |
| TTRP-PD-003 | pandas/Polars IO method | `.to_sql(…)`, `.read_csv(…)`, `.to_parquet(…)` | "use load()/store() — IO beyond load() is canonical-land" | C2-c-ii |
| TTRP-PD-004 | boolean-mask indexing | `df[df.amount > 0]` | "use .filter(amount > 0)" | C2-c α rejected |
| TTRP-PD-005 | Python control flow / def / import | `for …:`, `def f():`, `import pandas` | "TTR-pandas has no control flow — statements are assignment + chain" | C2-c-ii |
| TTRP-PD-006 | index ops | `.set_index(…)`, `.loc[…]`, `.iloc[…]` | "no index — tables are relational; use filter/select" | C2-c-ii |
| TTRP-PD-007 | engine-API ceremony | `pl.col("amount")`, `pd.DataFrame(…)` | "write the bare column name" | C2-c-γ rejected |
| TTRP-PD-008 | multi-out / branch in fragment | `.branch(…)` | "single default-out in fragments — branch in a canonical container (the graduation boundary)" | C2-c-i |

- [ ] Coverage spec `packages/kotlin/ttrp-frontend/src/test/kotlin/org/tatrman/ttrp/frontend/pandas/TtrPandasRejectTableSpec.kt` — same three assertions as the SQL twin (unique ids / non-empty suggests / table ↔ corpus ↔ parser coverage, the last enabled in T6.2.4).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.pandas.TtrPandasRejectTableSpec"` — (a)/(b) green, coverage `xdisabled`.

### T6.2.2 · Test corpus + failing specs (TDD anchor)

- [ ] Corpus home `packages/kotlin/ttrp-frontend/src/test/resources/corpus/ttr-pandas/` with `accept/` and `reject/`.
- [ ] **Hero island, TTR-pandas equivalent** — `accept/hero-crunch-pandas.ttrp`. Same graph as 6.1's hero island (this pair is a 6.3 identity input — node-for-node aligned, same SSA labels `joined`/`sums`, same in-port names, anonymous display). Align named-arg spellings to the Stage-1.1/1.2 canonical grammar:

```ttrp
uses world "acme.worlds.dev"

acc = load(erp.accounts)
sal = load(files.sales)

container crunch(in accounts, in sales, out result) target polars_local """pandas
joined = accounts.join(right: sales, type: inner, on: left.account_id == right.account_id)
                 .select(account_id, customer_id, amount)
sums = joined.aggregate(by: customer_id, total_amount: sum(amount), sale_count: count())
sums.sort(by: total_amount, dir: desc, nulls: last).limit(100)
"""

acc -> crunch.accounts
sal -> crunch.sales
crunch.result -> display
```

  Expected decomposition: `joined` = SSA label on Join(inner) → Project; `sums` = Aggregate (AggregateCall arm); final bare chain = Sort → Limit → default out (last-value convention, C2-c-ii). Note the target differs from 6.1's (`polars_local`) — **dialect ≠ engine**; a variant fixture `accept/hero-crunch-pandas-on-pg.ttrp` with `target erp_pg` must decompose identically (retargetability is the T5-e point; it exercises normal capability lowering, no fragment machinery).
- [ ] **Roster + SSA coverage fixtures:** `accept/ssa-rebind.ttrp` (`x = x.filter(amount > 0)` — Q7-γ rebind), `accept/roster-full.ttrp` (every roster method used once: `select calc filter join aggregate sort union limit load store display`; `union(a, b, c)` list form → internal ports `in1..inN`, S11), `accept/chain-source.ttrp` (chain as join argument: `join(right: load(files.sales).filter(amount > 0), …)`).
- [ ] **Reject fixtures** — one per table row, named by id, each with header `# expect: TTRP-PD-NNN "<suggest prefix>"` (comment lexis `#`, S19): e.g. `reject/TTRP-PD-001.ttrp` uses `.agg(total: sum(amount))`; `reject/TTRP-PD-002.ttrp` uses `.apply(lambda r: r.amount * 2)`.
- [ ] **`==` fixtures (S9):** `accept/eq-synonym.ttrp` (`.filter(status == "open")` inside `"""pandas` — accepted, decomposes to the same tree as `=`); `reject/TTRP-EQ-001.ttrp` (`filter(status == "open")` in **canonical** text — still rejected with "use ="); assertion that both `=` and `==` inside TTR-pandas produce byte-identical expression trees.
- [ ] Failing Kotest specs (package `org.tatrman.ttrp.frontend.pandas`): `TtrPandasParserSpec`, `TtrPandasDecompositionSpec`, `TtrPandasRejectSpec`, `TtrPandasEqSynonymSpec`, `TtrPandasUntouchedSpec` (C2-f, incl. formatter no-op on interiors).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.pandas.*"` — compiles, specs red.

### T6.2.3 · `TTRPandas.g4` — method-chain grammar

- [ ] Create `packages/grammar/src/TTRPandas.g4` (own grammar, C2-g α). Statement forms per **C2-c-ii** only: `fragmentProgram : statement* finalChain EOF ;` `statement : IDENT '=' chain ;` `finalChain : chain | IDENT ;` `chain : primary ('.' methodCall)* ;` `methodCall : ROSTER_METHOD '(' argList? ')' ;` — no `def`, no `import`, no control flow, no subscript `[...]` (mask reject 004 caught by a dedicated lexer token so the diagnostic can name it), no `lambda` keyword (dedicated token → 002).
- [ ] **S17 roster as grammar tokens** — exactly: `select`, `calc`, `filter`, `join`, `aggregate`, `sort`, `union`, `limit`, `load`, `store`, `display`. Full words only, lowercase only (canonical lexis — this is TTR-P wearing dataframe clothes, not SQL: no case-insensitivity here). Out-of-roster method names parse as `IDENT` in method position → T6.2.4 maps them to `TTRP-PD-001` with the curated-abbreviation suggestion.
- [ ] **S16:** expression positions (`filter(…)` predicate, `calc`/`aggregate` value exprs, `on:` conditions) consume the shared PL expression rule/table — no local operator tokens **except** the one closed synonym: a `==` token valid only in this grammar's expression adaptation (S9; see T6.2.6 for the tree-level unification). Named args `name: expr` reuse the canonical named-arg lexis.
- [ ] Comment lexis: `#` line comments → hidden channel (S19, contracts §1).
- [ ] Wire into `packages/kotlin/ttrp-frontend/build.gradle.kts` generation exactly as `TTRSql.g4` (T6.1.3; generated package `org.tatrman.ttrp.frontend.generated.pandas`); generated sources gitignored; only the `.g4` committed. No TS/TextMate steps (Kotlin-only, G-b).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:generateGrammarSource :packages:kotlin:ttrp-frontend:build` green; `TtrPandasParserSpec` accept corpus parses; `git status` clean of generated files.

### T6.2.4 · Parser wrapper + reject diagnostics

- [ ] `org.tatrman.ttrp.frontend.pandas.TtrPandasParser` — same contract as `TtrSqlParser` (T6.1.4): `parse(sourceText, hostOffset, hostFile)`, spans remapped into the host document, trivia verbatim, reject table loaded from resources as the single message source.
- [ ] Reject mapping layer: out-of-roster method → `TTRP-PD-001` with nearest-roster suggestion from the curated abbreviation list (`agg`→"use aggregate", `sel`→"use select", `filt`→"use filter", `lim`→"use limit"; unlisted names get the roster-enumeration message — **no fuzzy matching**, P2: the suggestion list is closed, like C4-c's synonym table). `lambda`/`.apply` → 002; IO names (`to_sql`, `read_csv`, `to_csv`, `to_parquet`, `read_parquet`) → 003; subscript → 004; `for`/`while`/`if`/`def`/`import` head tokens → 005; `set_index`/`reset_index`/`loc`/`iloc` → 006; `pl.`/`pd.` qualified calls → 007; `branch` in method position → 008.
- [ ] Enable `TtrPandasRejectTableSpec` coverage assertion (table ↔ corpus ↔ parser).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.pandas.TtrPandasRejectSpec" --tests "org.tatrman.ttrp.frontend.pandas.TtrPandasRejectTableSpec"` green — incl. `.agg` → `TTRP-PD-001` "use aggregate" and `.apply(lambda …)` → `TTRP-PD-002`.

### T6.2.5 · Statement/SSA decomposition (C2-a-β)

- [ ] `org.tatrman.ttrp.frontend.pandas.TtrPandasDecomposer` — AST → standard node set:
  - **Assignment** `x = chain` → the chain's node run; `x` = SSA label on the final node; reassignment `x = x.filter(…)` desugars SSA-style to a fresh instance (Q7-γ — same mechanism as canonical text; labels survive for ζ keys/E-b).
  - **Method → node**, 1:1 with the roster: `select`/`calc` → Project/Calc · `filter` → Filter · `join` → Join (named args `right:`, `type:`, `on:`; chain head = `left` — the receiver position carries the left port, the C2-b-ii analogue for method-chains; columns qualify `left.x`/`right.y` per C3-a-iv-4) · `aggregate` → Aggregate (AggregateCall arm; HAVING-equivalents are a following `.filter`) · `sort` → Sort (NULLS placement defaulting LAST, Q9-3) · `union(a, b, …)` → Union with ports `in1..inN` (S11) · `limit` → Limit · `load` → Load (source position; model/world refs via `FragmentScope`) · `store` → Store · `display` → Display (Q11 — in embedded fragments a `store`/`display` mid-island is legal only as chain-terminal; program-level wiring stays canonical).
  - **Final bare chain or bare name** = the container's default out (last-value convention, C2-c-ii; single default-out, C2-c-i). A fragment whose last statement is an assignment with no final chain/name = named diagnostic (no silent out-port guessing, P2) — add `reject/no-final-value.ttrp` + assign the next free `TTRP-PD-0xx` id in the table.
  - **S15 mirror:** `.limit()` whose input chain has no governing Sort → the same ordered-input rule as TTR-SQL. Reuse the check written in T6.1.5 (it operates on the decomposed graph, surface-independent). Diagnostic id: reuse `TTRP-SQL-014` **only if** the Stage-2 graph checks made it graph-level; otherwise mint `TTRP-PD-0xx` "add .sort() before .limit()" — record the choice in both reject tables and note it for the contracts changelog. Fixture: `reject/unordered-limit.ttrp`.
- [ ] Expression scope = input columns only (C2-d-iii); chain-head names resolve via `FragmentScope` (in-ports now; imports/qnames in 6.3).
- [ ] All nodes carry host-document source ranges.

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.pandas.TtrPandasDecompositionSpec"` green — hero shape node-for-node, SSA rebind fixture, roster-full fixture, unordered-limit reject.

### T6.2.6 · `==` synonym (S9) + expression integration

- [ ] Implement `==` as a **closed dialect synonym**: the TTR-pandas expression adaptation maps `==` to the same equality node in the one PL expression tree as `=` — same tree, same precedence, same catalogue ids (the C4-c pattern: synonym tables over one grammar, never a second grammar). Authored text keeps whatever the author wrote (C2-f).
- [ ] Regression-pin the boundary: `TTRP-EQ-001` still fires for `==` in canonical `.ttrp` text and inside `"""sql` fragments (S9's "except inside TTR-pandas" is exact) — extend `TtrPandasEqSynonymSpec` with a `"""sql`-interior `==` case asserting the SQL side rejects it through its own path (SQL's `=` is equality; `==` in TTR-SQL → generic TTR-SQL syntax reject, *not* silently accepted).
- [ ] Assert byte-identical expression-tree serialization for `=` vs `==` variants of `accept/eq-synonym.ttrp` (this is the 6.3 identity test's precondition at expression granularity).
- [ ] Qualified-column integration test: `on: left.account_id == right.account_id` lifts to the tree with port-qualified column refs (C3-a-iv-4) and er refs work through fragment expressions identically to canonical (C2-d-ii smoke: one er-variant fixture `accept/er-refs.ttrp` using `erp.er.*` imported names — full document-scope resolution lands in 6.3, this fixture may stay `xdisabled` until T6.3.4 if the resolver seam isn't reachable; note it if so).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.pandas.TtrPandasEqSynonymSpec"` green.

### T6.2.7 · Host integration — `"""pandas` end to end

- [ ] Wire parser + decomposer into the front-half pipeline for `"""pandas` tagged blocks (same seam as T6.1.7): peeled `sourceText` + offset → dialect parse → decomposed sub-graph spliced as container interior → final value bound to default out; `err` port free, no rejects producers (C2-e α).
- [ ] Confirm `TtrPandasUntouchedSpec` (C2-f) green including formatter no-op on interiors.
- [ ] Run the hero pandas island end-to-end: `ttrp check` clean on `accept/hero-crunch-pandas.ttrp`; `ttrp explain` shows the same island shape as 6.1's SQL hero (modulo target/placement); the `-on-pg` retarget variant compiles via normal capability lowering.
- [ ] Cross-dialect sanity: one document mixing a `"""sql` and a `"""pandas` container (`accept/mixed-dialects.ttrp`) — the container is the mixing unit (C0-γ); both decompose; `ttrp check` clean.

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.pandas.*"` all green; `ttrp check` + `ttrp explain` outputs on the two hero variants captured in the stage progress note.

## Definition of DONE (stage)

- [ ] All T6.2.1–T6.2.7 checkboxes checked; `./gradlew :packages:kotlin:ttrp-frontend:test` green.
- [ ] Reject table `ttr-pandas.rejects.toml` committed + coverage-proven; abbreviations rejected with closed-list suggestions (S17); `.apply`/lambdas/IO/masks/control-flow all parse-time rejects.
- [ ] `==` accepted inside TTR-pandas only (S9); `TTRP-EQ-001` regression pinned for canonical + TTR-SQL contexts.
- [ ] Hero pandas island passes `ttrp check`/`explain`; retarget variant proves dialect ≠ engine; mixed-dialect document compiles.
- [ ] Only `TTRPandas.g4` + build wiring + Kotlin + fixtures committed (no generated sources).

## Blockers

*(record here and STOP: Stage 6.1 seam not reusable as assumed · S16 table can't host the `==` synonym cleanly · S15 check turns out island-local in Stage 2 (id-minting decision needed) · canonical named-arg spelling conflicts with the fixtures)*

## References

- Primary: `docs/ttr-p/design/11-fragments-options.md` (C2-c β, C2-c-i/ii, C2-a-β, C2-f, C2-g)
- Decisions: `docs/ttr-p/design/00-control-room.md` — **S9** (`==` TTR-pandas-only), **S17** (full-word roster), S16, S11, S19, Q7-γ, C3-a-iv-4, C4-c (synonym-table pattern), E-c (dialect ≠ engine)
- Contracts: `docs/ttr-p/architecture/contracts.md` §1, §3 (method roster line), §8
- Architecture: `docs/ttr-p/architecture/architecture.md` §2, §4
- Build pattern: `packages/kotlin/ttr-parser/build.gradle.kts` · Stage 6.1 list (`tasks-p6-s6.1-ttr-sql.md`) for the shared conventions
- Plan: `docs/ttr-p/implementation/v1/plan.md` Phase 6, Stage 6.2
