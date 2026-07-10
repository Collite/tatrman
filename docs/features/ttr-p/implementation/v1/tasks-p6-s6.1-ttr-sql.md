# Tasks · P6 · Stage 6.1 — TTR-SQL dialect

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

`TTRSql.g4` + Kotlin decomposition: the hero's SQL island authored as a `"""sql` tagged block parses, resolves against container in-ports, and decomposes clause-wise into the standard node set (C2-a-β) — one query expression per fragment (`WITH` + final `SELECT`, C2-b), CTE names = SSA labels (E-b's inverse), `SELECT *` expanded statically (C2-b-iii β), `EXISTS`/`IN` desugared to semi/anti Join, `LIMIT` gated by the ordered-input rule (S15 → `TTRP-SQL-014`). Every rejected form fails at parse/check time with a named diagnostic + suggested alternative, driven by a versioned reject table (contracts §8). Fragment interiors stay byte-untouchable (C2-f).

**Alignment notes (recorded at drafting, 2026-07-05):** the P0 (`tasks-p0-s0.1-scaffold.md`) and P1 (`tasks-p1-s1.1`/`s1.2`) task lists did **not exist yet** when this list was written. Everything below aligns to `plan.md` Stages 0.1/1.1/1.2 and decisions S16/C2-f/C2-g directly. Concretely assumed (verify in Pre-flight, adjust paths if P0/P1 landed differently):

- TTR-P Kotlin modules exist as `packages/kotlin/ttrp-{frontend,graph,emit,lsp,cli,conform}` (plan Stage 0.1); fragment parsing lives in **`ttrp-frontend`**, Kotlin package root `org.tatrman.ttrp.frontend`.
- Grammar generation follows the **`packages/kotlin/ttr-parser/build.gradle.kts` Gradle-ANTLR pattern** (grammar read in place from `packages/grammar/src/`, no copy; `-visitor -package <generated pkg>`; generated `.java` lands **flat** in `build/generated-src/antlr/main/` — do NOT nest to package path, see the duplicate-class warning in that file). Plan Stage 0.1 says "antlr-ng generation task" — if P0 actually wired antlr-ng instead of the Gradle plugin, follow P0's wiring; the tasks below only require *some* committed-`.g4` → generated-parser task. TTR-P is Kotlin-only (G-b): **no** TS parser regen, **no** TextMate work in this stage (CLAUDE.md's antlr-ng + `generate-tm-grammar.ts` steps are TTR-M-only; dialect editor registration is Stage 4.3's).
- The S16 **shared keyword/operator table** was built in Stage 1.2 as an importable grammar unit consumed by `TTRP.g4` (expression grammar + `and/or/not/is/null/in/=`/comparison/arithmetic tokens). `TTRSql.g4` must **consume** it (ANTLR `import` or `tokenVocab`), never re-declare those tokens.
- Diagnostic framework `TTRP-<AREA>-<NNN>` with a `suggestedAlternative` field exists from Stage 1.1.

## Pre-flight (all must pass before T6.1.1)

- [ ] Phases 1–2 green: `./gradlew :packages:kotlin:ttrp-frontend:test :packages:kotlin:ttrp-graph:test` passes.
- [ ] `ttrp check` (Phase-1 entry point) passes on the canonical hero fixture; `ttrp explain` (Phase-2) shows its island structure. Record the hero fixture path here: `_______` (needed by T6.1.2 — the fragment fixtures must use the same table/column names).
- [ ] Locate the S16 shared table artifact: `grep -rn "grammar" packages/grammar/src/*.g4 | grep -iv "^.*TTR.g4"` — note the grammar unit `TTRP.g4` imports for expressions/keywords: `_______`. If none exists (Stage 1.2 inlined everything), **STOP → Blocker**: S16 requires one shared table; splitting it out is Stage-1.2 rework, not a 6.1 improvisation.
- [ ] Locate the Stage-1.1 diagnostic framework (`grep -rn "TTRP-EQ-001" packages/kotlin/ttrp-frontend/src/main`) and its suggested-alternative field.
- [ ] Confirm tagged blocks (`"""sql`) lex opaque in `TTRP.g4` and the fragment `sourceText` + start offset are on the AST (Stage 1.1, C2-f) — `grep -rn "TAGGED_BLOCK" packages/grammar/src/TTRP.g4`.

## Tasks

### T6.1.1 · TTR-SQL reject table — the versioned fixture

The reject table is a **reviewable, versioned artifact** (contracts §8: test fixture + assist repair vocabulary). Format: TOML (human-diffable resource; the F-f-i anti-TOML ruling was about the runtime bundle manifest, not source-tree fixtures).

- [ ] Create `packages/kotlin/ttrp-frontend/src/main/resources/rejects/ttr-sql.rejects.toml` (main resources, not test — `ttrp/authoringContext` §7 ships it as repair vocabulary later). Header comment: `# TTR-SQL reject table v1 — contracts §8. Versioned fixture: every entry has a corpus fixture; every TTRP-SQL reject diagnostic has an entry. Reviewable.`
- [ ] Entries — one `[[reject]]` block each with keys `id`, `form`, `example`, `message`, `suggest`, `decision`. Populate exactly (IDs proposed here; `014` is **fixed** by S15 — do not renumber it):

| id | form | example | suggest | decision |
|---|---|---|---|---|
| TTRP-SQL-001 | DML/DDL statement | `INSERT INTO t …` | "TTR-SQL is read-only; writes go through canonical `store` (A3)" | C2-b |
| TTRP-SQL-002 | vendor row-limit `TOP n` | `SELECT TOP 10 …` | "use LIMIT 10" | C2-b |
| TTRP-SQL-003 | lock/optimizer hints | `WITH (NOLOCK)` | "generic SQL only — remove the hint" | C0 (b), C2-b |
| TTRP-SQL-004 | backtick/bracket quoting | `` `col` ``, `[col]` | "use double quotes" | C2-b |
| TTRP-SQL-005 | `::` cast | `x::int` | "use CAST(x AS int)" | C2-b, T5 explicit Cast |
| TTRP-SQL-006 | scalar/correlated subquery in an expression | `SELECT (SELECT …)` | "no subquery expressions; EXISTS/IN in WHERE are the only subquery forms" | C2-b, B-T5 |
| TTRP-SQL-007 | window function | `SUM(x) OVER (…)` | "window functions are v2" | B-T10 |
| TTRP-SQL-008 | procedural SQL | `DECLARE @x …`, `IF`, loops | "TTR-SQL is one query expression" | C2-b |
| TTRP-SQL-009 | multiple statements / `;`-chain | `SELECT …; SELECT …` | "one query expression per fragment" | C2-b |
| TTRP-SQL-010 | `SELECT INTO` | `SELECT … INTO t` | "the fragment's final SELECT is the container's default out port" | C2-b, C2-c-i |
| TTRP-SQL-011 | out-of-cut relational syntax | `PIVOT`, `GROUPING SETS`, `LATERAL` | "author this in canonical TTR-P (PIVOT is canonical-only in v1)" | C2-b-i α |
| TTRP-SQL-012 | derived table in FROM | `FROM (SELECT …) x` | "lift it into a WITH cte — CTE names become SSA labels" | C2-b, E-b |
| TTRP-SQL-013 | `NATURAL JOIN` / `USING (…)` | `a NATURAL JOIN b` | "spell the ON condition explicitly" | P2, C2-b |
| TTRP-SQL-014 | LIMIT/OFFSET on unordered input | `… LIMIT 10` with no ORDER BY in the chain | "add ORDER BY before LIMIT (deterministic results, A4/Q9)" | **S15** |

- [ ] Add a coverage spec `packages/kotlin/ttrp-frontend/src/test/kotlin/org/tatrman/ttrp/frontend/sql/TtrSqlRejectTableSpec.kt` (Kotest `StringSpec`): loads the TOML, asserts (a) ids unique + monotone area `TTRP-SQL-`, (b) every entry has non-empty `suggest`, (c) — activated in T6.1.4 — every entry id is produced by at least one corpus fixture and vice versa.
- [ ] New area codes are not introduced here (`SQL` is already in contracts §8's list) — no contracts changelog entry needed. Confirm.

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.sql.TtrSqlRejectTableSpec"` — (a)/(b) pass; (c) is `xdisabled` pending T6.1.4.

### T6.1.2 · Test corpus + failing specs (TDD anchor)

- [ ] Create corpus home `packages/kotlin/ttrp-frontend/src/test/resources/corpus/ttr-sql/` with subdirs `accept/` and `reject/`.
- [ ] **Hero SQL island, embedded** — `accept/hero-crunch.ttrp` (align table/column names to the Phase-1 hero fixture noted in Pre-flight; spelling of canonical args to the Stage-1.1/1.2 grammar):

```ttrp
uses world "acme.worlds.dev"

acc = load(erp.accounts)
sal = load(files.sales)

container crunch(in accounts, in sales, out result) target erp_pg """sql
WITH joined AS (
    SELECT accounts.account_id, accounts.customer_id, sales.amount
    FROM accounts
    JOIN sales ON accounts.account_id = sales.account_id
),
sums AS (
    SELECT customer_id, SUM(amount) AS total_amount, COUNT(*) AS sale_count
    FROM joined
    GROUP BY customer_id
)
SELECT customer_id, total_amount, sale_count
FROM sums
ORDER BY total_amount DESC NULLS LAST
LIMIT 100
"""

acc -> crunch.accounts
sal -> crunch.sales
crunch.result -> display
```

  Expected decomposition (the spec's assertion target): `joined` = Join(inner, left=in-port `accounts`, right=in-port `sales`, on = PL expression tree) → Project(3 cols); `sums` = Aggregate(by customer_id; AggregateCall sum, AggregateCall count); final SELECT = Project → Sort(desc, NULLS LAST) → Limit(100) → default out port. CTE names `joined`/`sums` = SSA labels (E-b inverse). Anonymous `display` per Q11 (single display). The hero island is also the 6.3 identity fixture — **do not restyle it later**.
- [ ] **Workhorse coverage fixtures** (each a minimal `"""sql` container in a `.ttrp` shell like above): `accept/distinct.ttrp` (`SELECT DISTINCT` → Aggregate sugar), `accept/setops.ttrp` (`UNION ALL` / `INTERSECT` / `EXCEPT`), `accept/values.ttrp` (`VALUES (…),(…)` → Values), `accept/star.ttrp` (`SELECT *` — T6.1.6), `accept/semi-anti.ttrp` (`WHERE EXISTS (…)` + `WHERE x NOT IN (SELECT …)` — T6.1.6), `accept/limit-offset.ttrp` (`ORDER BY … LIMIT 10 OFFSET 5` — both in per S15).
- [ ] **Reject fixtures** — one file per reject-table row, named by id: `reject/TTRP-SQL-002.ttrp` contains `SELECT TOP 10 * FROM accounts` and a sidecar expectation line (fixture header comment `-- expect: TTRP-SQL-002 "use LIMIT 10"`); likewise `reject/TTRP-SQL-014.ttrp` (`SELECT customer_id FROM accounts LIMIT 10` — no ORDER BY), and one per remaining row (001–013).
- [ ] Write the failing Kotest specs (package `org.tatrman.ttrp.frontend.sql`): `TtrSqlParserSpec` (accept corpus parses; CTE roster + spans byte-precise into the *host document* — fragment-interior offsets remapped by the tagged-block start offset), `TtrSqlDecompositionSpec` (hero assertion above, clause table of T6.1.5), `TtrSqlRejectSpec` (walks `reject/`, asserts the header-declared id + that the diagnostic's suggested alternative equals the table's `suggest`), `TtrSqlStarExpansionSpec` + `TtrSqlSubqueryDesugarSpec` (T6.1.6), `FragmentUntouchedSpec` (C2-f: fragment `sourceText` byte-equal to the authored interior; if the Phase-4 formatter is present, formatting `hero-crunch.ttrp` leaves interior bytes identical).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.sql.*"` — compiles, specs **fail** (red) for not-yet-implemented parsing; corpus files load.

### T6.1.3 · `TTRSql.g4` — own grammar, workhorse cut

- [ ] Create `packages/grammar/src/TTRSql.g4` (own grammar per **C2-g α**, beside `TTR.g4`/`TTRP.g4`). Top comment: canonical source; clause table = C2-b α; consumed by ttrp-frontend only.
- [ ] Grammar shape — **one query expression per fragment**: `fragmentProgram : withClause? selectStatement EOF ;` `withClause : WITH cte (',' cte)* ;` `cte : IDENT AS '(' selectStatement ')' ;` — no `;`, no statement list (rejects 008/009 become *grammar* misses surfaced by T6.1.4's error strategy, per C2-g "rejects are grammar rejects").
- [ ] Transcribe the **C2-b α clause table** (from `11-fragments-options.md`) as the rule roster — this table is the grammar's scope contract:

| SQL surface | Maps to | Notes |
|---|---|---|
| `WITH name AS (…), …` + final `SELECT` | named sub-chains; **CTE name = SSA label** | one query expression per fragment; E-b's inverse |
| `SELECT` list, aliases, expressions | Project / Calc | expressions = the one PL grammar in SQL clothing (T5-e) |
| `SELECT DISTINCT` | Aggregate sugar | B-T10 |
| `FROM a JOIN b ON …` (inner/left/right/full/cross) | Join | ON → PL expression; **position carries left/right port** (C2-b-ii) |
| `WHERE` | Filter | |
| `GROUP BY` / `HAVING` | Aggregate + Filter sugar | AggregateCall arm (B-T5) |
| `UNION [ALL]` / `INTERSECT` / `EXCEPT` | Union / Intersect / Except | |
| `ORDER BY … [NULLS FIRST\|LAST]` | Sort | default NULLS LAST, always stated at emit (Q9-3) |
| `LIMIT n [OFFSET m]` | Limit | **both in** (S15); ordered-input rule → `TTRP-SQL-014` |
| `VALUES (…), (…)` | Values | |
| `EXISTS` / `IN (subquery)` / `NOT …` in WHERE | semi/anti Join desugar | the **only** subquery forms |

- [ ] **S16 — consume, don't duplicate:** import the shared keyword/operator table unit found in Pre-flight (ANTLR `import <SharedUnit>;` or `options { tokenVocab = …; }`, matching however `TTRP.g4` consumes it). Expression positions (`SELECT` list items, `ON`, `WHERE`, `HAVING`) reference the **shared PL expression rule**, not a local one. Only SQL-clause keywords (`WITH SELECT DISTINCT FROM JOIN LEFT RIGHT FULL CROSS INNER ON WHERE GROUP BY HAVING UNION ALL INTERSECT EXCEPT ORDER ASC DESC NULLS FIRST LAST LIMIT OFFSET VALUES AS EXISTS`) are declared locally, via case-insensitive letter fragments (SQL convention). If shared-table tokens are lowercase-only and this collides (e.g. `IN`, `AND` appearing uppercase in SQL text), record the resolution in §Blockers + the review report — do **not** silently fork the table.
- [ ] Comment lexis: `--` line comments to a hidden channel (contracts §1, S19); block comments `/* */` hidden as well.
- [ ] Wire generation in `packages/kotlin/ttrp-frontend/build.gradle.kts` following the P0 wiring for `TTRP.g4` (expected: the `ttr-parser` Gradle-ANTLR pattern — add `TTRSql.g4` to the `generateGrammarSource` include set, generated package `org.tatrman.ttrp.frontend.generated.sql` or P0's equivalent). Respect the flat-output caveat. Generated sources stay **gitignored**; only the `.g4` is committed.

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:generateGrammarSource :packages:kotlin:ttrp-frontend:build` green; `git status` shows only `TTRSql.g4` + build-file changes (no generated files staged); `TtrSqlParserSpec` accept-corpus cases now parse (decomposition specs still red).

### T6.1.4 · Parser wrapper + reject diagnostics

- [ ] `org.tatrman.ttrp.frontend.sql.TtrSqlParser` — `parse(sourceText: String, hostOffset: Int, hostFile: …): TtrSqlParseResult` (AST + diagnostics; all spans remapped by `hostOffset` into the host document — byte-precise per C2-g, feeds E-d provenance + C1-b hover). Trivia/whitespace preserved verbatim; `sourceText` is canonical, the parse is derived (C2-f corollary).
- [ ] Error strategy: an ANTLR miss is **never** a bare syntax error when it matches a reject-table row. Implement a recognizer/listener layer that maps the curated forms (T6.1.1 table: `TOP`, `::`, `NOLOCK`/hints, backticks, `INSERT|UPDATE|DELETE|CREATE|…` head tokens, `OVER`, `;`, `INTO`, `NATURAL`/`USING`, `PIVOT`/`GROUPING`/`LATERAL`, derived table in FROM, subquery-in-expression) to their `TTRP-SQL-NNN` diagnostic with the table's `suggest` string in the framework's suggested-alternative field. Anything else = generic `TTRP-SQL-0xx` syntax-error id (pick the next free number, add it to the table — the table stays exhaustive).
- [ ] Load the reject table from resources at wrapper init; the wrapper's messages come **from the table** (single source: table drives tests, parser, and later `ttrp/authoringContext` repair vocabulary).
- [ ] Enable `TtrSqlRejectTableSpec` coverage assertion (c): every table id appears in `reject/` corpus ∧ every reject fixture's produced id is in the table.

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.sql.TtrSqlRejectSpec" --tests "org.tatrman.ttrp.frontend.sql.TtrSqlRejectTableSpec"` — all green, incl. `TOP 10` → `TTRP-SQL-002` with suggestion "use LIMIT 10".

### T6.1.5 · Clause→node decomposition (C2-a-β)

- [ ] `org.tatrman.ttrp.frontend.sql.TtrSqlDecomposer` — AST → standard node set (the T10 roster; **no** dialect-flavored nodes, C2-a-γ rejected):
  - Each **CTE** = a named sub-chain; CTE name becomes the **SSA label** of the sub-chain's final node (Q7-γ / ζ keys / E-b round-trip). Duplicate CTE name = SSA rebind, same as canonical reassignment.
  - **FROM/JOIN** → Join chain; join kind from the keyword (inner/left/right/full/cross; semi/anti arrive via T6.1.6). **C2-b-ii:** positional syntax carries the ports — FROM-side/first operand binds `left`, joined operand binds `right`; aliases (`FROM accounts a`) bind the alias as the column-qualifier name. Container in-ports appear as plain table names (no `left.`/`right.` required inside fragments).
  - **WHERE** → Filter (predicate lifts to the one PL expression tree, catalogue function ids per T5-c; 3VL NULL semantics canonical).
  - **GROUP BY + HAVING** → Aggregate + Filter sugar; aggregate expressions use the `AggregateCall` arm; HAVING predicate over aggregate outputs.
  - **SELECT list** → Project (pure column refs/renames) / Calc (computed items) — apply the Stage-2.3 sugar conventions; emit the node even for identity projections (normalization decides elision — the rule must be deterministic and surface-independent, that's what 6.3's identity test locks).
  - **DISTINCT** → Aggregate sugar (B-T10). **ORDER BY** → Sort (explicit NULLS placement; default NULLS LAST, Q9-3). **Set-ops** → Union/Intersect/Except (Union internal ports `in1..inN`, S11). **VALUES** → Values.
  - **LIMIT/OFFSET** → Limit; **S15 ordered-input check**: walk the Limit's input chain — if no Sort node governs it (within the fragment sub-graph), emit `TTRP-SQL-014` (this is a check on the decomposed graph, not a grammar rule).
- [ ] Name resolution hook in this stage: FROM-position table names resolve against **container in-ports only** (ports-as-tables, C2-d-iii); document imports/qnames inside fragments land in Stage 6.3 T6.3.4 — leave the resolver seam (a `FragmentScope` interface) so 6.3 plugs in without touching the decomposer.
- [ ] Every produced node carries source ranges into the host document (E-d provenance prerequisite).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.sql.TtrSqlDecompositionSpec"` green (hero shape asserted node-for-node), plus `reject/TTRP-SQL-014.ttrp` now failing with the named diagnostic in `TtrSqlRejectSpec`.

### T6.1.6 · `SELECT *` static expansion + EXISTS/IN semi/anti desugar

- [ ] **`*` expansion (C2-b-iii β):** at decomposition, expand `*`/`alias.*` against the statically-known input schemas (schemas are always declared, T7/D-c — a `*` over an unresolvable schema is an ordinary resolution error, never a guess). Deterministic column order = input schema order, left-to-right across join operands. **Authored text keeps the `*`** (expansion is internal; visible via `ttrp explain`).
- [ ] **EXISTS/IN desugar (B-T5 sweep):** `WHERE EXISTS (SELECT … FROM t WHERE corr)` → semi Join(left = outer chain, right = subquery chain, on = correlation predicate); `NOT EXISTS`/`NOT IN` → anti Join; `x IN (SELECT y FROM t)` → semi Join on `x = y`. The subquery body recurses through the same decomposer (it's a plain select-chain). These are the **only** subquery forms — anything else already rejected (006/012).
- [ ] `NOT IN` NULL semantics: document + test the 3VL behavior explicitly (anti-join with null-rejecting predicate matches canonical SQL `NOT IN` semantics; a mismatch here is an A4 conformance bug later — pin it with a fixture now: `accept/semi-anti-null.ttrp`).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.sql.TtrSqlStarExpansionSpec" --tests "org.tatrman.ttrp.frontend.sql.TtrSqlSubqueryDesugarSpec"` green; `ttrp explain` on `accept/star.ttrp` shows expanded columns while the source keeps `*`.

### T6.1.7 · Host integration — `"""sql` end to end

- [ ] Wire `TtrSqlParser` + `TtrSqlDecomposer` into the front-half pipeline: on a container whose body is a `"""sql` tagged block (tag = dialect, C3-g), hand the peeled `sourceText` + offset to the dialect parser during the parse step (architecture §4 pipeline); merge diagnostics; splice the decomposed sub-graph as the container's interior with the final SELECT bound to the container's **default out port** (single default-out, C2-c-i); container `err` port present, no rejects producers (C2-e α).
- [ ] Fragment-interior comments attach as trivia on derived nodes where line-mapping is unambiguous (leftover nicety from `11-fragments-options.md` — implement only the unambiguous case; skip silently otherwise).
- [ ] Confirm `FragmentUntouchedSpec` (C2-f) green including the formatter case (Phase-4 formatter formats the `.ttrp` around the fences, zero interior byte changes).
- [ ] Run the hero end-to-end: `ttrp check` clean on `accept/hero-crunch.ttrp`; `ttrp explain` shows the island structure with SSA labels `joined`/`sums` (these become CTE names again at emit — E-b round-trip visible).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.sql.*"` all green; `ttrp check` + `ttrp explain` on the hero fixture as above (paste the explain island section into the stage progress note).

## Definition of DONE (stage)

- [ ] All T6.1.1–T6.1.7 checkboxes checked, suites green: `./gradlew :packages:kotlin:ttrp-frontend:test`.
- [ ] Reject table `ttr-sql.rejects.toml` committed, exhaustive (coverage spec proves table ↔ corpus ↔ parser agree), `TTRP-SQL-014` = S15.
- [ ] Hero SQL island as `"""sql` passes `ttrp check`; `ttrp explain` shows full clause-wise decomposition, CTE=SSA labels.
- [ ] `SELECT *` expansion + semi/anti desugar proven by fixtures; authored `*` preserved.
- [ ] No generated parser sources committed; only `TTRSql.g4`, build wiring, Kotlin sources, fixtures, table.
- [ ] Fragment interiors byte-untouched under formatting (C2-f spec green).

## Blockers

*(record here and STOP: missing S16 shared table unit · P0 grammar wiring diverges from the ttr-parser pattern · shared-table case-sensitivity collision (T6.1.3) · Phase-1 hero fixture names conflict with the corpus fixtures)*

## References

- Primary: `docs/ttr-p/design/11-fragments-options.md` (C2-a-β, C2-b α + clause table, C2-b-ii, C2-b-iii β, C2-f, C2-g)
- Decisions: `docs/ttr-p/design/00-control-room.md` — S15 (`TTRP-SQL-014`), S16, S11, S19, E-b, Q7-γ, Q9-3, B-T5/B-T10 sweeps, C0 (b)
- Contracts: `docs/ttr-p/architecture/contracts.md` §1 (markers/tagged blocks), §3 (fragment contracts), §8 (diagnostics convention)
- Architecture: `docs/ttr-p/architecture/architecture.md` §2 (surface tiers), §4 (pipeline)
- Build pattern: `packages/kotlin/ttr-parser/build.gradle.kts` (Gradle-ANTLR, flat-output caveat) · `CLAUDE.md` §Grammar regeneration (TTR-M-side steps — explicitly *not* applicable beyond the commit-only-the-`.g4` rule)
- Plan: `docs/ttr-p/implementation/v1/plan.md` Phase 6, Stage 6.1
