# Tasks · P1 · Stage 1.2 — Expression grammar

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

The ONE PL expression layer (T5-e — every surface shares it): a Kotlin expression IR that is the twin of `plan.v1.Expression` (B-T3 "adapt"), with `AggregateCall` as a distinct arm (B-T5 sweep) and explicit `Cast`; the concrete expression grammar embedded in `TTRP.g4` (replacing Stage 1.1's provisional rule); catalogue-id function resolution behind the T5-c-β interface (two catalogues, one `CatalogEntry` contract); static typing + coercion checks under canonical SQL 3VL NULL (B-T5, forced by A4); the shared keyword/operator table (S16) as a single source that the later fragment grammars (`TTRSql.g4`, `TTRPandas.g4`, `TTRB.g4`) will be drift-tested against; expression golden tests.
**DONE bar:** every hero expression parses to the IR and typechecks against hand-declared input schemas; negative fixtures produce their named diagnostics; the S16 table is the only place keywords/operators are defined.

## Pre-flight (all must pass before T1.2.1)

- [ ] `./gradlew :packages:kotlin:ttrp-frontend:test` fully green (Stage 1.1 DONE bar).
- [ ] `grep -c 'STAGE-1.2 REPLACES' packages/grammar/src/TTRP.g4` → 1 (the provisional rule is still marked and awaiting replacement).
- [ ] Read `docs/ttr-p/design/02-internal-model-options.md` §T5 (IR arms, T5-c catalogue fork, 3VL) — the IR below implements T5 as converged.

## Tasks

### T1.2.1 · Expression corpus + spec skeletons (TEST-FIRST)

- [ ] Fixture file `packages/kotlin/ttrp-frontend/src/test/resources/expr/golden.exprs` — one expression per line, `<expr> :: <expected-type>` format, covering at least (types are TTR db-schema attribute-type spellings, S23):
  ```
  amount > 0 and customer is not null                :: bool
  left.account_id = right.customer                   :: bool
  total > 100000                                     :: bool
  sum(amount)                                        :: decimal
  avg(amount)                                        :: decimal
  cast(amount as string)                             :: string
  case when amount > 0 then 'pos' else 'neg' end     :: string
  amount + 1                                         :: decimal
  region in ('EU', 'US')                             :: bool
  -amount * 2                                        :: decimal
  coalesce(customer, 'unknown')                      :: string
  ```
  (test schema: `amount: decimal, customer: string, region: string, total: decimal, account_id: integer` on `left`/`right`/default input).
- [ ] Negative fixtures `expr/negative/` (one file each, first line the expression, second line `# expect: <id>`):
  - `eq-001.expr` — `amount == 0` → `TTRP-EQ-001`
  - `fn-001.expr` — `mean(amount)` → `TTRP-FN-001` (alias reject table → "use avg")
  - `fn-002.expr` — `sum(amount, region)` → `TTRP-FN-002` (arity)
  - `agg-001.expr` — aggregate call in a filter predicate: `filter(s, sum(amount) > 10)` (this one is a `.ttrp` snippet) → `TTRP-AGG-001`
  - `typ-001.expr` — `amount + customer` → `TTRP-TYP-001` (no implicit coercion across kinds)
  - `typ-002.expr` — `cast(customer as bool)` → `TTRP-TYP-002` (no cast rule)
  - `var-001.ttrp` — variable referenced inside an op expression: `x = load(files.a)\ny = filter(load(files.b), x > 0)` → `TTRP-EXP-001` (C3-a-iv-3: expression scope = input columns ONLY)
- [ ] Spec skeletons (package `org.tatrman.ttrp.expr`), red now:
  - `TtrpExpressionParseSpec.kt` — parses each `golden.exprs` line to an IR tree; snapshot-dumps like Stage 1.1.
  - `TtrpExpressionTypingSpec.kt` — table-driven `expr :: type` assertions against the hand-declared test schema.
  - `TtrpExpressionNegativeSpec.kt` — table-driven id assertions incl. `suggestedAlternative` non-blank.
  - `TtrpKeywordTableSpec.kt` — shell (filled in T1.2.4).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpExpression*'` runs red (fixtures load, IR/API missing).

### T1.2.2 · The expression IR — `plan.v1.Expression` twin

- [ ] `org.tatrman.ttrp.expr.Expression` — sealed interface, arms (keep the shape deliberately parallel to kantheon's `plan.v1.Expression` so the P3 lowering in ttr-translator is mechanical; do NOT import any proto — S25 vendors it in ttr-translator, not here):
  ```kotlin
  sealed interface Expression { val location: SourceLocation; val type: TtrpType? /* null until typed */ }
  data class ColumnRef(val port: String?, val column: String, ...) : Expression        // port-qualified per C3-a-iv-4 (left.x)
  data class Literal(val value: LiteralValue, ...) : Expression                        // string/number/bool/null
  data class FunctionCall(val function: CatalogId, val args: List<Expression>, ...) : Expression
  data class AggregateCall(val function: CatalogId, val args: List<Expression>,
                           val distinct: Boolean, ...) : Expression                    // DISTINCT arm, never a FunctionCall (B-T5 sweep)
  data class Cast(val expr: Expression, val target: TtrpType, ...) : Expression        // explicit only
  data class CaseWhen(val branches: List<Pair<Expression, Expression>>, val elseExpr: Expression?, ...) : Expression
  data class InList(val expr: Expression, val items: List<Expression>, val negated: Boolean, ...) : Expression
  data class IsNull(val expr: Expression, val negated: Boolean, ...) : Expression
  ```
  Binary/unary operators (`and or not = <> < <= > >= + - * / -x`) are `FunctionCall`s with catalogue ids (`op.and`, `op.eq`, `op.add`, …) — one uniform arm, like plan.v1; the parser does the folding. No lambda arm, no subquery arm (B-T5 sweep: not in v1 IR).
- [ ] `CatalogId` = value class over `String` (e.g. `"fn.avg"`, `"op.eq"`). `TtrpType` lands in T1.2.6 — stub it now as a sealed class with a `Named(name: String)` placeholder so the IR compiles.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:compileKotlin` green; `TtrpExpressionParseSpec` still red (grammar next).

### T1.2.3 · Concrete expression grammar in TTRP.g4 (replaces the provisional rule)

- [ ] Replace the `// STAGE-1.2 REPLACES` block with the final ladder (S9: `=` IS the equality inside expression context — context-separated from statement binding by grammar position; `==` remains lexable and rejected):
  ```antlr
  expr        : orExpr ;
  orExpr      : andExpr ('or' andExpr)* ;
  andExpr     : notExpr ('and' notExpr)* ;
  notExpr     : 'not' notExpr | predicate ;
  predicate   : addExpr ( ('=' | '==' | '<>' | '<' | '<=' | '>' | '>=') addExpr
                        | 'is' 'not'? 'null'
                        | 'not'? 'in' '(' expr (',' expr)* ')'
                        | 'between' addExpr 'and' addExpr
                        )? ;
  addExpr     : mulExpr (('+' | '-') mulExpr)* ;
  mulExpr     : unaryExpr (('*' | '/') unaryExpr)* ;
  unaryExpr   : '-' unaryExpr | primary ;
  primary     : literal
              | castExpr
              | caseExpr
              | functionCall
              | dottedRef                       // column or port.column (C3-a-iv-4)
              | '(' expr ')' ;
  castExpr    : 'cast' '(' expr 'as' typeName ')' ;
  caseExpr    : 'case' ('when' expr 'then' expr)+ ('else' expr)? 'end' ;
  functionCall: identifier '(' ('distinct'? expr (',' expr)*)? ')' ;   // distinct → AggregateCall only; walker rejects it on scalar fns (TTRP-FN-002)
  typeName    : identifier ('(' NUMBER (',' NUMBER)? ')')? ;           // decimal(12,2) — S23 spellings
  ```
  Note `between … and …` vs boolean `and`: the ladder placement above (predicate level, operands at `addExpr`) resolves it without predicates/backtracking — keep it there.
- [ ] Walker: fold parse tree → IR (operator folding to `op.*` catalogue ids; `distinct` + aggregate-catalogue hit → `AggregateCall`); `==` → `TTRP-EQ-001` (reuse the Stage 1.1 enum entry, now fired from the expression walker).
- [ ] Regenerate + update Stage 1.1 golden snapshots (expression subtrees change shape): `-DupdateSnapshots=true`, then eyeball the hero snapshot diff — only expression nodes may differ.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpExpressionParseSpec*'` green; `--tests '*TtrpParserGoldenSpec*'` green with the refreshed snapshots; zero ANTLR warnings.

### T1.2.4 · Shared keyword/operator table (S16) — single source

- [ ] `packages/kotlin/ttrp-frontend/src/main/kotlin/org/tatrman/ttrp/lang/KeywordTable.kt` — THE one source (object with typed entries), consumed by the checker now and drift-tested against every grammar file forever:
  ```kotlin
  object KeywordTable {
      val statementKeywords = setOf("uses","world","import","program","container","target","control","after","with","finishes","group","by","relation","schema")
      val expressionKeywords = setOf("and","or","not","is","null","in","between","case","when","then","else","end","cast","as","distinct")
      val reservedPorts = setOf("in","out","err","rejects","true","false","else")            // S10
      val operators = mapOf("=" to "op.eq", "<>" to "op.neq", "<" to "op.lt", "<=" to "op.lte",
                            ">" to "op.gt", ">=" to "op.gte", "+" to "op.add", "-" to "op.sub",
                            "*" to "op.mul", "/" to "op.div", "->" to null /* chain, not an expression op */)
      val rejectedSpellings = mapOf("==" to "TTRP-EQ-001")                                   // S9; TTR-pandas synonym handled in P6
  }
  ```
- [ ] Fill `TtrpKeywordTableSpec.kt` — the drift test: read `packages/grammar/src/TTRP.g4` from the repo (test resource-relative path), extract all single-quoted literal tokens, assert set-equality with `statementKeywords ∪ expressionKeywords ∪ operators.keys ∪ rejectedSpellings.keys ∪ {structural punctuation whitelist}`. This spec is the S16 enforcement mechanism; `TTRSql.g4`/`TTRPandas.g4`/`TTRB.g4` get sibling drift specs in P6/P7 against the SAME object — say so in the spec's KDoc.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpKeywordTableSpec*'` green; deliberately add a bogus `'xor'` literal to TTRP.g4 → spec fails → revert (proves the tripwire trips).

### T1.2.5 · Function catalogue — T5-c-β interface + builtin catalogue

- [ ] `org.tatrman.ttrp.expr.catalog`:
  ```kotlin
  interface FunctionCatalog {                                   // T5-c-β: two catalogues, ONE interface
      fun resolve(name: String): List<CatalogEntry>             // overloads; empty = unknown
      val catalogId: String                                     // "ttrp.builtin" | later "md-catalog"
  }
  data class CatalogEntry(
      val id: CatalogId,                    // stable catalogue id (T5-c) — what manifests reference (B-T6 β)
      val name: String,                     // surface spelling
      val kind: FunctionKind,               // SCALAR | AGGREGATE
      val params: List<TtrpType>,           // v1: exact-arity, coercion applied per T1.2.6 rules
      val returnType: ReturnTypeRule,       // FIXED(t) | SAME_AS_ARG(i) | PROMOTED(args)
      val nullPropagation: NullRule,        // STRICT (any NULL arg → NULL) | CUSTOM (coalesce, is-null…)
  )
  ```
- [ ] `BuiltinCatalog : FunctionCatalog` — v1 roster (keep minimal, P1; grow only when a stage needs it): operators `op.and/or/not` (Kleene — CUSTOM), `op.eq/neq/lt/lte/gt/gte` (STRICT → bool), `op.add/sub/mul/div/neg` (STRICT, PROMOTED); scalars `fn.coalesce` (CUSTOM), `fn.substring`, `fn.upper`, `fn.lower`, `fn.length`, `fn.abs`, `fn.round`; aggregates `agg.sum`, `agg.avg`, `agg.count`, `agg.min`, `agg.max`.
- [ ] Resolution in the checker: name → entries via a `CompositeCatalog(listOf(builtin))` (md-catalog slot reserved, D-h — leave a KDoc note, no code); unknown name → `TTRP-FN-001`; known name, no matching arity/types → `TTRP-FN-002`. Closed alias reject table (deterministic, P2 — never fuzzy): `mean→avg`, `substr→substring`, `nvl→coalesce`, `ifnull→coalesce`, `ucase→upper`, `lcase→lower` — alias hit → `TTRP-FN-001` with suggested alternative "use <canonical>".
- [ ] Add diagnostic ids to the Stage 1.1 enum: `FN_001("TTRP-FN-001", …)`, `FN_002("TTRP-FN-002", …)`, `AGG_001("TTRP-AGG-001", "aggregate functions are only legal inside aggregate(…) / aggregate { … } (B-T5)")`, `EXP_001("TTRP-EXP-001", "only input columns are in scope inside op expressions — variables never resolve here (C3-a-iv)")`, `TYP_001`, `TYP_002` (texts in T1.2.6).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpExpressionNegativeSpec*'` — `fn-001`, `fn-002`, `agg-001` cases green.

### T1.2.6 · Static typing, coercion, 3VL

- [ ] `TtrpType` final shape: sealed class mirroring the TTR db-schema attribute-type vocabulary **verbatim** (S23 — extract the list from `packages/grammar/src/TTR.g4` `typeValue` rule: `text int float bool datetime string boolean number integer double char varchar decimal date timestamp` + length/precision params; alias pairs like `int/integer`, `bool/boolean` normalize to one canonical form each, documented in KDoc). All types nullable by default — there is no NOT-NULL type in v1 expressions; 3VL is the semantics, not a type flag.
- [ ] `ExpressionTypechecker.check(expr: Expression, inputSchema: Map<String /*port*/, List<Column>>): TypedResult`:
  - ColumnRef resolves against input columns only (unqualified: unique across ports or ambiguity error; qualified: that port — C3-a-iv-4). Variables are NEVER in scope → `TTRP-EXP-001`.
  - Coercion lattice (implicit widening ONLY within a kind): `int → decimal → double`? **No** — keep v1 honest to Q9-4 (decimal exact): implicit `integer → decimal` allowed, `decimal → double` NEVER implicit (precision loss → explicit `Cast`), `char/varchar/string/text` unify to `string`, `date → timestamp/datetime` implicit, everything else explicit. Cross-kind (`string + int`, `bool` where number expected) → `TTRP-TYP-001` with suggested alternative "add an explicit cast(x as <type>)".
  - `Cast` legality table (numeric↔string, date/timestamp↔string, numeric↔numeric; `string→bool`, `bool→date` etc. illegal) — illegal → `TTRP-TYP-002` "no cast rule <from> → <to>".
  - 3VL: STRICT entries propagate NULL (typed as the entry's return type, nullable); `and/or` Kleene per canonical SQL; `IsNull` returns non-null bool; comparison with NULL literal typechecks but is flagged nowhere (it's legal, it's just NULL — B-T5). Predicate positions (`filter`, `branch`, `join on:`, `case when`) require type `bool` → `TTRP-TYP-001` otherwise.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpExpressionTypingSpec*'` — all `golden.exprs` lines type as annotated; `typ-001`, `typ-002`, `var-001` negatives green.

### T1.2.7 · Pipeline wiring + hero expressions end-to-end (component test)

- [ ] Wire the typechecker into `TtrpParser`'s post-parse pass behind an explicit entry point `TtrpFrontend.check(source, inputSchemas)` — Stage 1.3 replaces hand-fed schemas with resolved ones; keep the seam obvious (`interface SchemaSource { fun schemaFor(ref: DottedRef): List<Column>? }`, this stage ships `DeclaredSchemaSource(map)` only).
- [ ] Component spec `TtrpHeroExpressionsSpec.kt` (inter-class: parser → IR → catalogue → typechecker, no resolution): parse `golden/hero.ttrp`, hand-declare the accounts/sales schemas (`account_id: integer, branch_code: string, region: string`; `customer: string, branch: string, amount: decimal`), assert: every expression in the hero typechecks; `sum(amount)`/`avg(amount)` produce `AggregateCall` arms inside the aggregate config block and NOWHERE else; `b = branch(sums, total > 100000)` predicate types `bool`; zero diagnostics.
- [ ] Update the diagnostics catalogue count assertion (if any) and re-freeze Stage 1.1 snapshots one final time; commit.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test` — whole module green; run twice → no snapshot churn.

## Definition of DONE (stage)

- [ ] `golden.exprs` corpus parses + types exactly as annotated; snapshots committed.
- [ ] All 7 expression negatives produce their named ids with suggested alternatives.
- [ ] `AggregateCall` is a distinct IR arm; `Cast` explicit-only; operators are catalogue-id `FunctionCall`s.
- [ ] `TtrpKeywordTableSpec` enforces S16 against TTRP.g4 (tripwire proven).
- [ ] Hero expressions typecheck end-to-end against hand-declared schemas (`TtrpHeroExpressionsSpec`).

## Blockers

_(empty — coder records here)_

## References

- **T5** expression sublanguage: `docs/ttr-p/design/02-internal-model-options.md` §T5 · **B-T3** "own IR, adapt" — the IR is a plan.v1.Expression twin, lowered only in ttr-translator (P3) · **B-T5 sweep** AggregateCall distinct arm; no subquery exprs; compile-time params only.
- **T5-c-β** two catalogues, one `CatalogEntry` interface; md-catalog absorption deferred (D-h). `@modeler/md-catalog` (TS) is the calc-map side — NOT consumed here in v1.
- **T5-e** one expression grammar across all surfaces — the P6/P7 dialects skin THIS grammar (C4-c synonym tables), which is why S16's table must be the single source.
- **S9** `=` one equality; `==` → `TTRP-EQ-001` (TTR-pandas synonym lands with `TTRPandas.g4`, P6 — no v1.2 carve-out here). **S16** shared keyword/operator table. **S23** types = TTR db-schema attribute types verbatim (`typeValue` in `TTR.g4`, ~line 489).
- **B-T5 / Q9** canonical SQL 3VL everywhere; decimal exact / float64 declared-tolerance (Q9-4) — motivates the no-implicit-`decimal→double` rule.
- **C3-a-iv** (3) expression scope = input columns only; (4) port-name qualification `left.x`.
- Contracts §8 diagnostics convention. Diagnostic areas EQ/FN/AGG/TYP/EXP used here; area list is extensible by design ("…" in contracts §8).
- kantheon `plan.v1.Expression` (shape reference only — coder: see ai-platform repo at `~/Dev/ai-platform`, EXAMPLES.md + proto definitions; do not add any dependency).
