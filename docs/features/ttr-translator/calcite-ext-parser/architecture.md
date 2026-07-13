# CalciteExtParser port — architecture

> Restores the extended-Calcite SQL parser (`CalciteExtParserImpl`) and its FMPP/JavaCC
> codegen pipeline into `packages/kotlin/ttr-translator`, so tatrman's translator understands
> the T-SQL extension productions **COLLATE / DATEADD / DATEDIFF / DATEPART / DATE_PART /
> (TRY_)CONVERT** — the capability ai-platform's `query-translator` has and the extraction
> (TR-2, commit `f2e2efb`) silently dropped. Reference: `~/Dev/ai-platform/shared/libs/kotlin/query-translator`.

## Why this is missing (the extraction gap)

The ttr-translator extraction moved the orchestrator/codec/wire/framework packages but **left
behind `src/main/codegen/` and most of `functions/`**. The `functions/` package was later
*partially* restored — only `PlatformOperators.kt` (grounding ops, RG-P3 commit `9fe7b2d`) — so
today tatrman's `TranslatorFramework` uses the **stock** Calcite parser
(`SqlParser.config().withLex(Lex.MYSQL_ANSI)`) with `SqlStdOperatorTable ⊕ PlatformOperators`.
The extension productions do not exist, so a query using `COLLATE`/`DATEADD`/`CONVERT` fails to
parse in both the validator path **and** `SchemaDetector` — which is the "Fix B" symptom:
detection returns inconclusive and the caller falls back to the wrong catalog.

See [[ttr-translator-extraction-gaps]] (memory) for the running list of dropped pieces.

## The codegen pipeline (decision D7, mirrored from ai-platform)

The extended parser is **generated at build time**; only two small template files are checked in.

```
 calcite-core.jar ──(zipTree, extract)──┐
   codegen/templates/Parser.jj          │
   codegen/default_config.fmpp          ├─► [assembleParserCodegen] (Sync)
   codegen/includes/compoundIdentifier.ftl                 build/calcite-ext-codegen/
                                          │        (Calcite grammar overlaid with our overrides)
 src/main/codegen/  (CHECKED IN) ────────┘                        │
   config.fmpp            (hooks: extraBinaryExpressions,         │
   includes/parserImpls.ftl  builtinFunctionCallMethods)         ▼
                                                    [generateParserGrammar] (FMPP Ant task)
                                                         build/calcite-ext-fmpp/**/Parser.jj
                                                                  │
                                                                  ▼
                                                    [generateParser] (JavaExec → javacc)
                                              build/generated/calcite-ext-parser/
                                                org/tatrman/translator/parser/impl/
                                                CalciteExtParserImpl.java  (+ support .java)
                                                                  │
                                    java.srcDir(javaccOutDir) ────┤ (main source set)
                                                                  ▼
                                              compileKotlin dependsOn generateParser
```

- **Extract-not-vendor:** Calcite's own `Parser.jj` + `default_config.fmpp` are pulled from the
  pinned `calcite-core` jar at build time (via a `calciteCodegenTemplates` non-transitive config),
  so the grammar always tracks the Calcite version (1.41.0) with no 257 KB of vendored grammar to
  maintain. Only our `config.fmpp` (~40 ln) and `parserImpls.ftl` (~180 ln) are committed.
- **FMPP** renders `Parser.jj` by layering our `config.fmpp` data model over the extracted
  `default_config.fmpp`. **JavaCC** compiles the rendered `Parser.jj` into
  `CalciteExtParserImpl.java`. Both replicate Calcite's own `buildSrc` FmppTask/JavaCCTask as plain
  Gradle tasks (we cannot depend on Calcite's buildSrc).
- The generated `.java` lands in the **main** source set so Kotlin sees `CalciteExtParserImpl`.
  ktlint only lints `.kt`, so the generated `.java` is ignored (but the task dependency is declared).

**Config-cache:** the FMPP Ant task and the JavaCC `Configuration`-classpath read are **not**
configuration-cache compatible; both tasks carry `notCompatibleWithConfigurationCache(...)`
(carried verbatim from the reference build).

## The parser → operator dependency closure

The generated parser productions reference exactly three `functions/` operators — a **closed set**
(none pull the wider `CustomOperators`/`CalciteOperatorTables`/`FunctionCatalog` web that the rest
of ai-platform's `functions/` forms):

| Operator (port target) | ~ln | Needs | Used by production |
|---|---|---|---|
| `SqlCollateOperator` (object, `SqlBinaryOperator`) | 64 | — (leaf) | `Collate()` |
| `Dateparts` (object; `toTimeUnit(name): TimeUnit?`) | 45 | `SqlCollateOperator` | `NormalizeDatepart` in `DateaddFunctionCall()` |
| `ConvertOperators` (`CONVERT`/`TRY_CONVERT` ops + `ConvertRewriter` shuttle + `rewriter()`) | 150 | `SqlCollateOperator` | `TryConvertFunctionCall()` |

`DATEADD`/`DATEDIFF`/`DATEPART`/`DATE_PART` themselves are **Calcite built-ins**
(`SqlLibraryOperators.*`) — no port needed, only table registration.

## Three wiring points

1. **`SqlParser.parseQuery`** (`codec/sql/SqlParser.kt`) — add
   `.withParserFactory(CalciteExtParserImpl.FACTORY)`. This is the literal "Fix B" one-liner; it
   aligns the standalone parser (used by `SchemaDetector`) with the framework parser so detection
   sees the same SQL the validator can compile.
2. **`TranslatorFramework`** (`framework/TranslatorFramework.kt`) — (a) `.withParserFactory(...)` on
   the parser config; (b) **additively** chain the extension operators after the current table so
   the parsed extension nodes also *validate*. We keep the existing
   `SqlStdOperatorTable ⊕ PlatformOperators` and append an `ExtOperators` table (COLLATE, CONVERT,
   TRY_CONVERT + the `SqlLibraryOperators` DATEADD/DATEDIFF/DATEPART/DATE_PART) — **not** a wholesale
   swap to ai-platform's `permissiveUnion`, to avoid regressing tatrman's current validation surface.
3. **`SqlValidator.validateAndConvert`** (`codec/sql/SqlValidator.kt`) — apply the `ConvertRewriter`
   `SqlShuttle` post-parse (`parsed.accept(ConvertOperators.rewriter())`) before sql-to-rel, so the
   `(TRY_)CONVERT` type operand is normalised to a bare string literal and never reaches sql-to-rel
   as a non-expression operand. COLLATE and DATEADD need no post-parse pass.

## Phasing (mirrors ai-platform's own increments)

The generated parser is all-or-nothing (one `Parser.jj`), so **P0 lands the whole toolchain +
all three operators + full `parserImpls.ftl`**, proving the pipeline end-to-end on the simplest
feature (COLLATE). DATEADD and CONVERT then land as validation + round-trip proofs behind their
own operator-table/rewriter wiring and specs.

- **CEP-P0 — toolchain + operator closure + COLLATE** (closes Fix B).
- **CEP-P1 — DATEADD/DATEDIFF/DATEPART/DATE_PART** validation + wire round-trip.
- **CEP-P2 — CONVERT/TRY_CONVERT** (the `ConvertRewriter` post-parse hook).
- **CEP-P3 — publish `0.9.6` + consumer** (tatrman-server pin bump; re-verify veles/grounding).

## Risks / watch-items

- **R1 — publish surface.** ttr-translator is published to Maven Central + GH Packages via
  vanniktech; the sources jar globs `java.srcDir(javaccOutDir)`, so the **generated `.java` will
  ship in the sources jar**. Acceptable (common for generated parsers) but verify the Central
  publish (`publishToMavenCentral` dry-run) still succeeds and the codegen runs before the sources
  jar task. ai-platform never faced this (it does not publish).
- **R2 — DATEADD wire round-trip.** The datepart travels as a **SYMBOL / interval-qualifier**
  operand; ai-platform's `DateaddSpec` relies on `wire.Expressions` SYMBOL support. Confirm
  tatrman's `wire/` encoder-decoder round-trips SYMBOL operands to `plan.v1`; if not, that is a
  scoped sub-port inside CEP-P1 (investigation task first).
- **R3 — Calcite version drift.** The extract-from-jar approach assumes the committed
  `parserImpls.ftl` hooks match the pinned Calcite grammar. Both repos are on **1.41.0** today, so
  the templates transfer verbatim; a future Calcite bump re-opens the grammar-hook compatibility.
- **R4 — behavioural additivity.** Chaining `ExtOperators` after the existing table must not shadow
  or reorder existing resolution. The whole pre-existing suite must stay green at each phase (the
  regression guard); if any existing test moves, stop and reconcile.

## Tech stack

Kotlin 21 (JVM toolchain 21) · Apache Calcite **1.41.0** · **FMPP 0.9.16**
(`net.sourceforge.fmpp:fmpp`) · **JavaCC 4.0** (`net.java.dev.javacc:javacc`) · Gradle (Kotlin DSL,
`java-library` + `java-test-fixtures` + vanniktech maven-publish) · Kotest.
