# CalciteExtParser port — phased plan

> Design: [`../architecture.md`](../architecture.md) · [`../contracts.md`](../contracts.md).
> Reference implementation (port source): `~/Dev/ai-platform/shared/libs/kotlin/query-translator`
> (Calcite 1.41.0 = tatrman's pin; root package `shared.translator.*` → `org.tatrman.translator.*`).
> Task tracker: [`tasks/00-task-management.md`](./tasks/00-task-management.md).

## Overview

Four phases. **CEP-P0 is the load-bearing one** — it stands up the whole FMPP/JavaCC toolchain,
ports the closed operator set, wires the parser factory, and proves the pipeline end-to-end on
COLLATE; completing it **closes the "Fix B" gap** (SqlParser/SchemaDetector alignment). P1–P2 add
the DATEADD and CONVERT feature families behind their own validation/round-trip proofs. P3 publishes
`0.9.6` and re-points the tatrman-server consumer.

```
CEP-P0 toolchain+COLLATE ─► CEP-P1 DATEADD trio ─► CEP-P2 CONVERT/TRY_CONVERT ─► CEP-P3 publish 0.9.6
 (codegen, 3 operators,      (SqlLibraryOperators   (ConvertRewriter post-parse   (lockstep w/ plan-proto,
  factory wiring, Fix B)      table + wire SYMBOL)    hook in SqlValidator)         tatrman-server pin bump)
```

**Global invariant (all phases):** the pre-existing ttr-translator suite stays green at its current
count and ktlint stays clean after every phase — chaining `ExtOperators` and swapping the parser
factory must be strictly additive (architecture R4). If any existing test changes behaviour, **stop
and reconcile** before proceeding.

## CEP-P0 · Toolchain + operator closure + COLLATE

**Pre-flight:** none (self-contained in tatrman). Confirm `calcite = 1.41.0` matches ai-platform's
(it does) before extracting the grammar templates.

**Deliverables:** the three codegen `Configuration`s + `assembleParserCodegen`/`generateParserGrammar`/
`generateParser` tasks + source-set/compile/ktlint wiring (contracts §2); `fmpp`/`javacc` version
catalog entries (§1); the checked-in `config.fmpp` + `includes/parserImpls.ftl` (§3, package-renamed);
the three ported operators `SqlCollateOperator`/`Dateparts`/`ConvertOperators` (§4) — all three are
needed for the generated parser to compile even though only COLLATE is exercised here; the additive
`ExtOperators.OPERATOR_TABLE` (§4); `SqlParser` + `TranslatorFramework` factory wiring (§5.1/5.2);
`CollateSpec` + the `SchemaDetectorSpec` COLLATE case (§6).

**DONE when:** `./gradlew :packages:kotlin:ttr-translator:test` regenerates `CalciteExtParserImpl`
from scratch (`--rerun-tasks` on `generateParser` produces the `.java`), the module compiles, the
**whole pre-existing suite is green + `CollateSpec` green + the SchemaDetector COLLATE case flips
`NOT_APPLICABLE → AUTODETECTED/DB`**, ktlint clean. **The "Fix B" gap is closed** — a COLLATE query
detects its catalog and validates. (COLLATE is the proving ground; DATEADD/CONVERT still parse but
their validation/round-trip is proven in P1/P2.)

## CEP-P1 · DATEADD / DATEDIFF / DATEPART / DATE_PART

**Pre-flight:** CEP-P0 DONE (parser generates + compiles).

**Deliverables:** register the Calcite `SqlLibraryOperators` DATEADD/DATEDIFF/DATEPART/DATE_PART in
`ExtOperators.OPERATOR_TABLE` (verify the exact operator list against Calcite 1.41.0 via `context7` +
`~/Dev/view-only/calcite`); confirm/port **wire SYMBOL-operand support** in `wire/` so the datepart
interval qualifier round-trips to `plan.v1` (architecture R2 — investigation task first, port only
if a gap exists); `DateaddSpec`.

**DONE when:** `DateaddSpec` green — the trio validates to a RelNode, `Dateparts` normalises T-SQL
abbreviations (`dd`→`DAY`), and a DATEADD query round-trips parse→rel→`plan.v1`→SQL; pre-existing
suite still green.

## CEP-P2 · CONVERT / TRY_CONVERT

**Pre-flight:** CEP-P0 DONE.

**Deliverables:** wire the `ConvertRewriter` post-parse `SqlShuttle` into
`SqlValidator.validateAndConvert` (contracts §5.3, mirror reference lines ~34–42); register
`ConvertOperators.table` in `ExtOperators`; `ConvertSpec`.

**DONE when:** `ConvertSpec` green — `CONVERT`/`TRY_CONVERT` validate, the rewriter normalises the
type operand to a string literal (never reaches sql-to-rel as a non-expression), and unparse
round-trips; pre-existing suite still green.

## CEP-P3 · Publish 0.9.6 + consumer re-point

**Pre-flight:** CEP-P0..P2 DONE; a green full `:packages:kotlin:ttr-translator:test` and a
`publishToMavenCentral` **dry-run** proving the generated `.java` sources jar builds (architecture R1).

**Deliverables:** cut `kotlin-translator/v0.9.6` (ttr-translator + ttr-plan-proto lockstep, per
`PUBLISHING.md`); bump the tatrman-server `ttr-translator` pin → `0.9.6`; re-run the consumer suites.

**DONE when:** `0.9.6` is on Maven Central + GH Packages; tatrman-server builds against it; veles
`QueryParseWorker` accepts the T-SQL `CONCAT(...)`-form parametrized query (the RG-audit case, now
parseable) and the grounding calendar round-trips + full `:services:*:test` stay green; the
`ttr-translator-extraction-gaps` memory + this STATUS updated (gap closed).

## Not in scope

The rest of ai-platform's `functions/` (`CustomOperators`, `CalciteOperatorTables`,
`FunctionCatalog`, `StringOperators`, `ConditionalOperators`, `DateOperators`, `CoverageEnumerator`)
— tatrman's translator works without them today; they are a separate "operator-surface parity" arc
if a future need surfaces. This port takes only the **additive** subset the extension parser
requires.
