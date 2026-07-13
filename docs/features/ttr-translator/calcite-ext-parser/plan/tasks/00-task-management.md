# CalciteExtParser port — task management (overall tracker)

> Master tracker for the CalciteExtParser port. Structure: **Plan → Phase → Stage**; each phase is
> a mini task list of 6–8 checkboxed tasks in its own file. Design: [`../../architecture.md`](../../architecture.md)
> · [`../../contracts.md`](../../contracts.md) · [`../plan.md`](../plan.md). Reference implementation:
> `~/Dev/ai-platform/shared/libs/kotlin/query-translator` (paths mirror; rename `shared.translator.*`
> → `org.tatrman.translator.*`).

## Rules for the coder (read before every session)

1. **Check every checkbox the moment its task is done** — here (phase level) and in the phase file
   (task level). Never batch.
2. **TDD.** Each phase lists its spec task *before* the wiring that makes it pass (COLLATE/DATEADD/
   CONVERT specs are ported first, watched red, then made green). The one exception is CEP-P0's
   toolchain tasks (T1–T4): the codegen must exist before anything compiles, so they precede the spec.
3. **Additive-only invariant.** After every phase, the **whole pre-existing ttr-translator suite
   stays green at its current count** and ktlint stays clean. If an existing test changes behaviour,
   STOP and reconcile — the parser-factory swap and `ExtOperators` chain must not shadow existing
   resolution (architecture R4).
4. **Port verbatim, rename only.** These files are a package-rename port, not a rewrite:
   `shared.translator.*` → `org.tatrman.translator.*`, `cz.dfpartner.plan.v1` → `org.tatrman.plan.v1`,
   `cz.dfpartner.translator.v1` → `org.tatrman.translate.v1`. Do not "improve" the grammar templates
   or operators — drift from the reference is a bug.
5. **Verify Calcite APIs against the pin.** For anything touching `SqlLibraryOperators` /
   `SqlOperatorTables` / the grammar hooks, confirm against **Calcite 1.41.0** via `context7`
   (`org.apache.calcite`) and the local clone `~/Dev/view-only/calcite` (graphified) — do not trust
   memory of Calcite APIs.
6. **Regen is real.** "Green" for the codegen means `generateParser --rerun-tasks` produces
   `CalciteExtParserImpl.java` and the module compiles against the freshly generated source — not a
   cached artifact.

## Pre-flight gate (verify before CEP-P0)

- [x] `packages/kotlin/ttr-translator` `calcite` pin == ai-platform's (`1.41.0`) — templates are
      version-locked; a mismatch means the extracted `Parser.jj` won't match `parserImpls.ftl` hooks.
- [x] Record the current green baseline: `./gradlew :packages:kotlin:ttr-translator:test` count
      (the additive-invariant reference number).
- [x] `fmpp` / `javacc` are resolvable from the configured repos (Maven Central).

## Phase tracker

| Phase | Mini task list | Done |
|---|---|---|
| **CEP-P0** toolchain + operator closure + COLLATE (closes Fix B) | [`tasks-p0-toolchain-collate.md`](./tasks-p0-toolchain-collate.md) | [x] |
| **CEP-P1** DATEADD / DATEDIFF / DATEPART / DATE_PART | [`tasks-p1-dateadd.md`](./tasks-p1-dateadd.md) | [x] |
| **CEP-P2** CONVERT / TRY_CONVERT | [`tasks-p2-convert.md`](./tasks-p2-convert.md) | [ ] |
| **CEP-P3** publish 0.9.6 + consumer re-point | [`tasks-p3-publish.md`](./tasks-p3-publish.md) | [ ] |

## Phase-exit reviews

House cadence: after each phase a `/review` verifies its DoD ([`../plan.md`](../plan.md)) against
runtime (regenerated parser, not cached).

- [ ] CEP-P0 review · [ ] CEP-P1 review · [ ] CEP-P2 review · [ ] CEP-P3 review

## Library reference card

- **FMPP / JavaCC codegen** — no gradle plugin; plain tasks mirror Calcite's own `buildSrc`
  FmppTask/JavaCCTask. Extract the grammar from the `calcite-core` jar (`zipTree`), overlay
  `src/main/codegen/`. The FMPP Ant task + JavaCC classpath read are **not** config-cache compatible
  (annotate). Full task bodies: copy from the reference `build.gradle.kts` codegen block, change only
  `parserPackage = "org.tatrman.translator.parser.impl"`.
- **Apache Calcite 1.41.0** — `SqlBinaryOperator`, `SqlShuttle`, `SqlLibraryOperators.{DATEADD,
  DATEDIFF,DATEPART,DATE_PART}`, `SqlOperatorTables.{of,chain}`, `TimeUnit`,
  `SqlIntervalQualifier`. Clone: `~/Dev/view-only/calcite` (graphified); docs via `context7`.
- **Existing in-repo pattern** — `functions/PlatformOperators.kt` is the precedent for an additive
  `SqlOperatorTable` object chained in `TranslatorFramework`; mirror its shape for `ExtOperators`.
