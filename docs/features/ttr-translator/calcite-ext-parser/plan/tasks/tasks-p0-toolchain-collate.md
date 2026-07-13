# CEP-P0 — Toolchain + operator closure + COLLATE (closes Fix B)

> DoD: [`../plan.md`](../plan.md) §CEP-P0. Contracts: [`../../contracts.md`](../../contracts.md)
> §1–§6. Reference: `~/Dev/ai-platform/shared/libs/kotlin/query-translator`. All target paths under
> `packages/kotlin/ttr-translator/`. **Rename on port:** `shared.translator.*` → `org.tatrman.translator.*`.
>
> **Verify (phase):** `./gradlew :packages:kotlin:ttr-translator:test --rerun-tasks` — `generateParser`
> emits `build/generated/calcite-ext-parser/org/tatrman/translator/parser/impl/CalciteExtParserImpl.java`,
> the module compiles, the pre-existing suite is green at its baseline count **plus** `CollateSpec` and
> the SchemaDetector COLLATE case; ktlint clean.

- [ ] **T1 — Version catalog + codegen configs.** Add `fmpp = "0.9.16"` / `javacc = "4.0"` versions
  and the `fmpp` / `javacc` library entries to `gradle/libs.versions.toml` (contracts §1). In
  `build.gradle.kts`, declare the three configs `codegenFmpp` / `codegenJavacc` /
  `calciteCodegenTemplates { isTransitive = false }` and add
  `codegenFmpp(libs.fmpp)` / `codegenJavacc(libs.javacc)` / `calciteCodegenTemplates(libs.calcite.core)`
  to `dependencies` (§2). No tasks yet — just resolvable configs. Verify: `./gradlew
  :packages:kotlin:ttr-translator:dependencies --configuration codegenFmpp` resolves.

- [ ] **T2 — Codegen tasks.** Port the codegen block from the reference `build.gradle.kts` **verbatim**,
  changing only `val parserPackage = "org.tatrman.translator.parser.impl"`: `assembleParserCodegen`
  (`Sync`), `generateParserGrammar` (FMPP Ant `doLast`, `notCompatibleWithConfigurationCache`),
  `generateParser` (`JavaExec` mainClass `javacc`, `notCompatibleWithConfigurationCache`),
  `sourceSets.named("main"){ java.srcDir(javaccOutDir) }`, `compileKotlin.dependsOn(generateParser)`,
  and the `runKtlint*` dependsOn (contracts §2). Verify: `./gradlew
  :packages:kotlin:ttr-translator:generateParser --rerun-tasks` writes `CalciteExtParserImpl.java`
  under the `org/tatrman/translator/parser/impl/` path.

- [ ] **T3 — Checked-in templates.** Create `src/main/codegen/config.fmpp` and
  `src/main/codegen/includes/parserImpls.ftl` from the reference (contracts §3), renaming
  `package: "org.tatrman.translator.parser.impl"` in `config.fmpp` and the three
  `shared.translator.functions.*` refs in `parserImpls.ftl` → `org.tatrman.translator.functions.*`.
  Keep all hooks (`extraBinaryExpressions=["Collate"]`, `builtinFunctionCallMethods=[…]`). Do not edit
  the productions. Verify: `generateParserGrammar` renders a `Parser.jj` containing `Collate(` and
  `DateaddFunctionCall(`.

- [ ] **T4 — Port the operator closure.** Copy `SqlCollateOperator.kt`, `Dateparts.kt`,
  `ConvertOperators.kt` into `src/main/kotlin/org/tatrman/translator/functions/` (contracts §4;
  package-rename only — all three are needed now so the generated parser *compiles*, even though only
  COLLATE is exercised this phase). Then add the additive `functions/ExtOperators.kt` with
  `OPERATOR_TABLE` initially carrying **just** `SqlCollateOperator.INSTANCE` (DATEADD/CONVERT entries
  land in P1/P2). Verify: `./gradlew :packages:kotlin:ttr-translator:compileKotlin` green (parser +
  operators compile together).

- [ ] **T5 (test first) — CollateSpec + SchemaDetector case.** Port `codec/sql/CollateSpec.kt`
  (contracts §6; rename imports, reuse tatrman `FixtureModel`) and add the COLLATE case to
  `detect/SchemaDetectorSpec.kt` (`SELECT id FROM <db table> WHERE nazev COLLATE
  Latin1_General_CI_AI LIKE 'O%'` ⇒ `AUTODETECTED` / `SchemaCode.DB`). Run — both **RED** (the parser
  factory isn't wired yet, so COLLATE still fails).

- [ ] **T6 — Wire the parser factory.** `codec/sql/SqlParser.kt`: add
  `.withParserFactory(CalciteExtParserImpl.FACTORY)` to the parser config (contracts §5.1).
  `framework/TranslatorFramework.kt`: add the same `.withParserFactory(...)` to `parserConfig`, and
  append `ExtOperators.OPERATOR_TABLE` to the existing `SqlOperatorTables.chain(...)` (§5.2 — keep
  `SqlStdOperatorTable ⊕ PlatformOperators`, additive). Run — T5 goes **GREEN**.

- [ ] **T7 — Additive-invariant + regen check.** `./gradlew :packages:kotlin:ttr-translator:test
  --rerun-tasks` — full suite green at the **baseline count + the two new tests**, zero pre-existing
  tests changed; ktlint clean; confirm `generateParser` re-ran (not cached). If any prior test moved,
  STOP and reconcile (R4).

- [ ] **T8 — Wrap.** Tick the CEP-P0 row in `00-task-management.md`; commit
  `CEP-P0: CalciteExtParser codegen + operator closure + COLLATE (closes Fix B)`. Note in the commit
  that DATEADD/CONVERT parse but their validation lands in P1/P2. Run the phase-exit `/review`.
