# CalciteExtParser port — contracts

> Exact APIs, schemas, build wiring, and file placements. All paths are under
> `packages/kotlin/ttr-translator/` unless noted. Reference originals live under
> `~/Dev/ai-platform/shared/libs/kotlin/query-translator/` at the mirrored paths (root package
> `shared.translator.*`; **rename to `org.tatrman.translator.*` on port**, and
> `cz.dfpartner.plan.v1` → `org.tatrman.plan.v1`, `cz.dfpartner.translator.v1` →
> `org.tatrman.translate.v1`).

## 1. Version catalog additions — `gradle/libs.versions.toml`

```toml
# [versions]
fmpp   = "0.9.16"
javacc = "4.0"

# [libraries]  (Calcite-ext custom-parser codegen, ttr-translator only — decision D7)
fmpp   = { module = "net.sourceforge.fmpp:fmpp",     version.ref = "fmpp" }
javacc = { module = "net.java.dev.javacc:javacc",    version.ref = "javacc" }
```

`calcite = "1.41.0"` already present and **must match** ai-platform's (it does) — the extracted
grammar templates are version-locked to it.

## 2. Build wiring — `packages/kotlin/ttr-translator/build.gradle.kts`

Three `Configuration`s (declared before `dependencies`):

```kotlin
val codegenFmpp: Configuration by configurations.creating
val codegenJavacc: Configuration by configurations.creating
val calciteCodegenTemplates: Configuration by configurations.creating { isTransitive = false }
```

Dependencies to add:

```kotlin
codegenFmpp(libs.fmpp)
codegenJavacc(libs.javacc)
calciteCodegenTemplates(libs.calcite.core)
```

Task graph (port the reference `build.gradle.kts` codegen block verbatim, changing only
`parserPackage`):

| Task | Type | Role |
|---|---|---|
| `assembleParserCodegen` | `Sync` | extract `codegen/{templates/Parser.jj, default_config.fmpp, includes/compoundIdentifier.ftl}` from the calcite-core jar, overlay `src/main/codegen/` (our files win) → `build/calcite-ext-codegen/` |
| `generateParserGrammar` | ad-hoc `doLast` | FMPP Ant task renders `Parser.jj` → `build/calcite-ext-fmpp/`; `notCompatibleWithConfigurationCache` |
| `generateParser` | `JavaExec` (`mainClass=javacc`) | compile `Parser.jj` → `build/generated/calcite-ext-parser/<pkgPath>/`; args `-STATIC=false -LOOKAHEAD:2 -OUTPUT_DIRECTORY:…`; `notCompatibleWithConfigurationCache` |

Wiring:

```kotlin
val parserPackage = "org.tatrman.translator.parser.impl"   // was shared.translator.parser.impl
sourceSets.named("main") { java.srcDir(javaccOutDir) }
tasks.named("compileKotlin") { dependsOn(generateParser) }
tasks.matching { it.name.startsWith("runKtlint") }.configureEach { dependsOn(generateParser) }
```

**Contract:** `generateParser` MUST run before `compileKotlin`, before every `runKtlint*`, and
before the vanniktech **sources jar** task (R1 — add the sources-jar dependsOn if the dry-run shows
it missing).

## 3. Checked-in codegen templates — `src/main/codegen/`

### 3.1 `config.fmpp` (FMPP data model)
Port verbatim; change only `package: "org.tatrman.translator.parser.impl"`. Hooks used:

```
data.parser.class                    = "CalciteExtParserImpl"
data.parser.implementationFiles      = ["parserImpls.ftl"]
data.parser.extraBinaryExpressions   = ["Collate"]
data.parser.builtinFunctionCallMethods = ["DatePartFunctionCall()", "DateaddFunctionCall()", "TryConvertFunctionCall()"]
freemarkerLinks.includes             = includes/
```
Everything else falls back to the extracted `default_config.fmpp`, so the parser is a **drop-in
superset** of stock `SqlParserImpl`.

### 3.2 `includes/parserImpls.ftl` (custom productions)
Port verbatim; rename the three fully-qualified operator refs
`shared.translator.functions.{SqlCollateOperator, Dateparts, ConvertOperators}` →
`org.tatrman.translator.functions.*`. Productions:
- `void Collate(List<Object> list, ExprContext, Span)` — postfix COLLATE binary op.
- `<DEFAULT,DQID,BTID> TOKEN` block — `DATE_PART DATEADD DATEDIFF DATEPART TRY_CONVERT` keyword tokens.
- `JAVACODE SqlIntervalQualifier NormalizeDatepart(...)` — datepart → canonical `TimeUnit` via `Dateparts`.
- `SqlNode DatePartFunctionCall()` — PostgreSQL `DATE_PART(unit, dt)`.
- `SqlNode DateaddFunctionCall()` — `DATEADD/DATEDIFF/DATEPART(unit, …)` via core `TimeUnitOrName()`.
- `SqlNode TryConvertFunctionCall()` — `TRY_CONVERT(type, expr [, style])`; builds
  `ConvertOperators.TRY_CONVERT.createCall(...)`.

## 4. Operator classes — `src/main/kotlin/org/tatrman/translator/functions/`

Port these three from the reference `functions/` (package-rename only; no dep beyond Calcite +
Guava `ImmutableList`, both already on the classpath):

| File | Public API (contract) |
|---|---|
| `SqlCollateOperator.kt` | `object SqlCollateOperator : SqlBinaryOperator(name="COLLATE", SqlKind.OTHER, prec=40…)`; `INSTANCE` = the object; custom `unparse`. Return type = ARG0. |
| `Dateparts.kt` | `object Dateparts { fun toTimeUnit(name: String): TimeUnit? }` — case-insensitive T-SQL datepart-abbrev → `org.apache.calcite.avatica.util.TimeUnit`. |
| `ConvertOperators.kt` | `class SqlConvertOperator(name)` (custom `unparse`); `object ConvertOperators { @JvmField val TRY_CONVERT; val CONVERT?; val table: SqlOperatorTable = SqlOperatorTables.of(CONVERT, TRY_CONVERT); fun rewriter(): ConvertRewriter }`; `class ConvertRewriter : SqlShuttle()` — post-parse normaliser. |

New (tatrman-only) aggregator, additive — `functions/ExtOperators.kt`:

```kotlin
object ExtOperators {
    // Additive extension operator table: chained AFTER SqlStdOperatorTable ⊕ PlatformOperators.
    // - SqlCollateOperator.INSTANCE
    // - ConvertOperators.CONVERT / TRY_CONVERT (via ConvertOperators.table)
    // - SqlLibraryOperators.DATEADD / DATEDIFF / DATEPART / DATE_PART (Calcite built-ins)
    val OPERATOR_TABLE: SqlOperatorTable  // built with SqlOperatorTables.of(...) / chain(...)
}
```
> Exact construction (which `SqlLibraryOperators` list, `SqlOperatorTables.of` vs a
> `ListSqlOperatorTable`) is an implementation detail to verify against Calcite 1.41.0 — use
> `context7` (`org.apache.calcite`) and the local clone `~/Dev/view-only/calcite` (graphified). The
> reference uses `CalciteOperatorTables.permissiveUnion`; we take the additive subset instead (R4).

## 5. Parser + framework + validator wiring (the diffs)

**5.1 `codec/sql/SqlParser.kt`**
```kotlin
import org.tatrman.translator.parser.impl.CalciteExtParserImpl
// …
val config = CalciteSqlParser.config().withLex(Lex.MYSQL_ANSI)
    .withParserFactory(CalciteExtParserImpl.FACTORY)
```

**5.2 `framework/TranslatorFramework.kt`** — parser factory on `parserConfig`, and extend the
existing `operatorTable(...)` chain (append `ExtOperators.OPERATOR_TABLE`; keep
`SqlStdOperatorTable ⊕ PlatformOperators` unchanged):
```kotlin
.parserConfig(SqlParser.config().withLex(Lex.MYSQL_ANSI).withParserFactory(CalciteExtParserImpl.FACTORY))
.operatorTable(SqlOperatorTables.chain(
    SqlStdOperatorTable.instance(),
    PlatformOperators.OPERATOR_TABLE,
    ExtOperators.OPERATOR_TABLE,          // NEW — additive
))
```

**5.3 `codec/sql/SqlValidator.kt`** — post-parse `ConvertRewriter` (CEP-P2 only):
```kotlin
val rewritten = parsed.accept(ConvertOperators.rewriter()) ?: parsed
// …validate `rewritten` instead of `parsed`.
```
Mirror the reference `SqlValidator.validateAndConvert` (lines ~34–42). COLLATE/DATEADD need no
post-parse pass.

## 6. Test contracts (Kotest, mirror reference specs)

Port with package/import rename; fixtures come from the existing tatrman `FixtureModel`:
- `codec/sql/CollateSpec.kt` — parse+validate `WHERE name COLLATE Latin1_General_CI_AI = 'a'`;
  wire `FunctionCall(operation="collate", operands=[expr, charLiteral])`; MSSQL unparse round-trip.
- `codec/sql/DateaddSpec.kt` — `DATEADD/DATEDIFF/DATEPART/DATE_PART` validate to RelNode; datepart
  normalises (`dd`→`DAY`); plan.v1 round-trip (R2 — SYMBOL operand).
- `codec/sql/ConvertSpec.kt` — `CONVERT`/`TRY_CONVERT` validate; `ConvertRewriter` normalises the
  type operand; unparse.
- `detect/SchemaDetectorSpec.kt` — **the Fix B closer**: `SELECT id FROM <db-table> WHERE nazev
  COLLATE Latin1_General_CI_AI LIKE 'O%'` ⇒ `AUTODETECTED` / `SchemaCode.DB` (was `NOT_APPLICABLE`
  under the stock parser).

## 7. Consumer / publish (CEP-P3)

- Cut `kotlin-translator/v0.9.6` (ttr-translator + ttr-plan-proto lockstep, per PUBLISHING.md).
- Bump tatrman-server `gradle/libs.versions.toml` pin `ttr-translator` → `0.9.6`.
- Re-verify: veles `QueryParseWorker` (may now accept the `CONCAT(...)` form the RG audit used),
  grounding calendar round-trips, full `:services:*:test`.
