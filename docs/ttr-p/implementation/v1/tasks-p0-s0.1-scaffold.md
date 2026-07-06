# Tasks · P0 · Stage 0.1 — Scaffold + hygiene

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

Empty-but-building TTR-P module skeleton in the monorepo: six Kotlin Gradle modules (`packages/kotlin/ttrp-{frontend,graph,emit,lsp,cli,conform}`) wired into `settings.gradle.kts` and the version catalog; the `@modeler/*` npm scope renamed to `@tatrman/*` (S7); a `TTRP.g4` seed grammar beside `TTR.g4` with the ANTLR Gradle-plugin generation task in `ttrp-frontend` (Kotlin-only — G-b; no antlr-ng/TS generation for TTR-P); a CI job building + testing the new modules; stale `§Open` markers in design docs 02/03/05/06/07/08 annotated (not deleted); publish-plumbing rows for the new artifacts recorded in `PUBLISHING.md`.
**DONE bar (plan Stage 0.1):** `./gradlew build` green including the empty ttrp modules; npm workspaces renamed; CI runs on PR.

## Pre-flight (all must pass before T0.1.1)

- [x] `cd /Users/bora/Dev/collite-gh/tatrman && ./gradlew build` — BUILD SUCCESSFUL (existing `ttr-parser`/`ttr-writer`/`ttr-semantics` green before we touch anything).
- [x] `pnpm install && pnpm -r build && pnpm -r test` — all TS packages green.
- [x] `git status --porcelain` — clean tree (stage work lands as its own commits, `Section 0.1: …` style).

## Tasks

### T0.1.1 · Six Gradle module skeletons + smoke specs (TEST-FIRST) — ✅ DONE

- [x] Write the smoke specs FIRST (they define the DONE shape of the module skeletons). One per module, Kotest `StringSpec` (repo convention — see `packages/kotlin/ttr-parser/src/test/kotlin/org/tatrman/ttr/parser/walker/TaggedBlockSpec.kt`):
  - `packages/kotlin/ttrp-frontend/src/test/kotlin/org/tatrman/ttrp/ModuleSmokeSpec.kt`
  - same file name/pattern under `ttrp-graph` (`org.tatrman.ttrp.graph`), `ttrp-emit` (`org.tatrman.ttrp.emit`), `ttrp-lsp` (`org.tatrman.ttrp.lsp`), `ttrp-cli` (`org.tatrman.ttrp.cli`), `ttrp-conform` (`org.tatrman.ttrp.conform`).
  ```kotlin
  package org.tatrman.ttrp

  import io.kotest.core.spec.style.StringSpec
  import io.kotest.matchers.shouldBe

  class ModuleSmokeSpec : StringSpec({
      "module compiles and Kotest runs" { (1 + 1) shouldBe 2 }
  })
  ```
- [x] Create the six module dirs `packages/kotlin/ttrp-{frontend,graph,emit,lsp,cli,conform}/` each with `build.gradle.kts` modelled on `packages/kotlin/ttr-writer/build.gradle.kts` (plugins `base`, `alias(libs.plugins.kotlin.jvm)`, `alias(libs.plugins.ktlint)`, `` `java-library` ``, `` `maven-publish` ``; `kotlin { jvmToolchain(21) }`; `tasks.test { useJUnitPlatform() }`; `testImplementation(libs.bundles.kotest)`). Copy the ttr-writer `publishing {}` block, adjusting `name`/`description` per module (e.g. "TTR-P Compiler Front-Half" / "parse → resolve → typecheck for TTR-P (.ttrp)"). Do NOT add the `antlr` plugin yet (that is T0.1.2, ttrp-frontend only).
- [x] Module dependencies (workspace-internal, one-way like the TS graph): `ttrp-graph` → `api(project(":packages:kotlin:ttrp-frontend"))`; `ttrp-emit` → `api(project(":packages:kotlin:ttrp-graph"))`; `ttrp-lsp` → `implementation(project(":packages:kotlin:ttrp-frontend"))`; `ttrp-cli` → `implementation(project(":packages:kotlin:ttrp-frontend"))`; `ttrp-conform` → no ttrp deps yet.
- [x] Add to `settings.gradle.kts` (keep alphabetical-ish grouping after the ttr-* includes):
  ```kotlin
  include(":packages:kotlin:ttrp-frontend")
  include(":packages:kotlin:ttrp-graph")
  include(":packages:kotlin:ttrp-emit")
  include(":packages:kotlin:ttrp-lsp")
  include(":packages:kotlin:ttrp-cli")
  include(":packages:kotlin:ttrp-conform")
  ```
- [x] `gradle/libs.versions.toml`: no new entries needed for this task (kotest bundle + kotlin plugin already exist). Do not add speculative libraries.
  - **Verify:** `./gradlew build` → BUILD SUCCESSFUL; `./gradlew :packages:kotlin:ttrp-frontend:test :packages:kotlin:ttrp-graph:test :packages:kotlin:ttrp-emit:test :packages:kotlin:ttrp-lsp:test :packages:kotlin:ttrp-cli:test :packages:kotlin:ttrp-conform:test` → 6 × "module compiles and Kotest runs" pass.

### T0.1.2 · `TTRP.g4` seed grammar + ANTLR generation in ttrp-frontend (TEST-FIRST) — ✅ DONE

- [x] Write `packages/kotlin/ttrp-frontend/src/test/kotlin/org/tatrman/ttrp/parser/TtrpSeedGrammarSpec.kt` FIRST (red until generation is wired):
  ```kotlin
  class TtrpSeedGrammarSpec : StringSpec({
      fun parse(src: String): Int {
          val lexer = org.tatrman.ttrp.parser.generated.TTRPLexer(org.antlr.v4.runtime.CharStreams.fromString(src))
          val parser = org.tatrman.ttrp.parser.generated.TTRPParser(org.antlr.v4.runtime.CommonTokenStream(lexer))
          parser.document()
          return parser.numberOfSyntaxErrors
      }
      "empty document parses" { parse("") shouldBe 0 }
      "comment-only document parses" { parse("// hello ttrp\n") shouldBe 0 }
  })
  ```
- [x] Create `packages/grammar/src/TTRP.g4` — the SEED only (Stage 1.1 grows it; keep it deliberately tiny so P0 ships an empty-but-building skeleton):
  ```antlr
  // TTR-P canonical grammar — seed (P0 Stage 0.1). Real productions land in P1 Stage 1.1.
  // @grammar-version: 0.1  (TTR-P spec version is an integer cut via docs/grammar-master/ — S6)
  grammar TTRP;
  document      : statement* EOF ;
  statement     : ID ;                                  // placeholder — replaced in Stage 1.1
  LINE_COMMENT  : '//' ~[\r\n]* -> channel(HIDDEN) ;
  BLOCK_COMMENT : '/*' .*? '*/' -> channel(HIDDEN) ;
  ID            : [a-zA-Z_] [a-zA-Z0-9_]* ;
  WS            : [ \t\r\n]+ -> skip ;
  ```
- [x] Wire generation in `packages/kotlin/ttrp-frontend/build.gradle.kts` by copying the proven `ttr-parser` pattern **verbatim, adjusted** (see `packages/kotlin/ttr-parser/build.gradle.kts` — read its comments; both the flat-output caveat and the antlr-api-leak fix are load-bearing):
  ```kotlin
  plugins { /* existing */ antlr }

  val canonicalGrammar = file("../../grammar/src/TTRP.g4")   // read directly — no copy, no sync
  sourceSets["main"].antlr.setSrcDirs(listOf(canonicalGrammar.parentFile))
  val generatedPackage = "org.tatrman.ttrp.parser.generated"

  tasks.named<org.gradle.api.plugins.antlr.AntlrTask>("generateGrammarSource") {
      source = fileTree(canonicalGrammar.parentFile) { include("TTRP.g4") }   // TTRP.g4 ONLY — TTR.g4 belongs to ttr-parser
      arguments = arguments + listOf("-visitor", "-long-messages", "-package", generatedPackage)
  }
  // strip the antlr tool config from api's extendsFrom (POM leak) — copy the
  // configurations.api { setExtendsFrom(...) } block + comment from ttr-parser
  dependencies {
      antlr(libs.antlr.tool)
      api(libs.antlr.runtime)
  }
  tasks.named("compileKotlin") { dependsOn("generateGrammarSource") }
  tasks.named("compileJava") { dependsOn("generateGrammarSource") }
  ```
  Also copy ttr-parser's `ktlint { filter { exclude("**/generated/**"); exclude { it.file.path.contains("/generated-src/antlr/") } } }`.
- [x] Note for the record (no code): TTR-P generation is **ANTLR Gradle plugin only** — there is NO antlr-ng/TS generation task and no TextMate grammar for TTR-P in this stage (G-b Kotlin-only; the plan's "antlr-ng generation task" wording is superseded by architecture §6 — flagged in the plan review).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test` → `TtrpSeedGrammarSpec` green; `./gradlew :packages:kotlin:ttrp-frontend:generateGrammarSource` emits `TTRPLexer.java`/`TTRPParser.java` under `packages/kotlin/ttrp-frontend/build/generated-src/antlr/main/` declaring `package org.tatrman.ttrp.parser.generated`.

### T0.1.3 · npm scope rename `@modeler/*` → `@tatrman/*` (S7) — package manifests — ✅ DONE

- [x] Rename the `name` field in every workspace `package.json`. Exact roster (verified 2026-07-05):
  | File | Old name | New name |
  |---|---|---|
  | `packages/designer/package.json` | `@modeler/designer` | `@tatrman/designer` |
  | `packages/edit/package.json` | `@modeler/edit` | `@tatrman/edit` |
  | `packages/format/package.json` | `@modeler/format` | `@tatrman/format` |
  | `packages/grammar/package.json` | `@modeler/grammar` | `@tatrman/grammar` |
  | `packages/lint/package.json` | `@modeler/lint` | `@tatrman/lint` |
  | `packages/lsp/package.json` | `@modeler/lsp` | `@tatrman/lsp` |
  | `packages/md-catalog/package.json` | `@modeler/md-catalog` | `@tatrman/md-catalog` |
  | `packages/migrate/package.json` | `@modeler/migrate` | `@tatrman/migrate` |
  | `packages/parser/package.json` | `@modeler/parser` | `@tatrman/parser` |
  | `packages/semantics/package.json` | `@modeler/semantics` | `@tatrman/semantics` |
  | `packages/sql/package.json` | `@modeler/sql` | `@tatrman/sql` |
  | `tests/integration/package.json` | `@modeler/integration-tests` | `@tatrman/integration-tests` |
  | `tests/conformance/package.json` | check actual name (`@modeler/…`) | same tail under `@tatrman/` |
  `packages/vscode-ext/package.json` is named `ttr-modeler-vsc` (marketplace id) — do NOT rename it; only rewrite its `@modeler/*` **dependency keys**.
- [x] Rewrite every `"@modeler/<x>": "workspace:*"` dependency key across all `package.json` files to `"@tatrman/<x>"`.
- [x] `pnpm install` to regenerate `pnpm-lock.yaml` workspace links.
  - **Verify:** `pnpm install` exits 0; `grep -rn "@modeler/" --include="package.json" packages tests` → no hits.

### T0.1.4 · npm scope rename — source imports, CI filters, docs — ✅ DONE

- [x] Sweep TS imports (~171 files): `grep -rl "@modeler/" --include="*.ts" --include="*.tsx" packages tests | xargs sed -i '' 's|@modeler/|@tatrman/|g'` (macOS sed; review the diff — imports only, no string-literal surprises expected but eyeball `packages/lsp` and `packages/vscode-ext`).
- [x] Sweep remaining live references: `grep -rn "@modeler" --include="*.json" --include="*.yml" --include="*.yaml" --include="*.md" --include="*.mjs" --include="*.js" . | grep -v node_modules | grep -v "docs/v1"` and fix each hit in: `.github/workflows/ci.yml` (note: the `vscode-smoke` job filters `@modeler/vscode-ext` — the package is actually named `ttr-modeler-vsc`; fix the filter to `ttr-modeler-vsc` while you are here), `.github/workflows/*.yml` (others), `packages/vscode-ext/.vscode/tasks.json`, `CLAUDE.md` (command table `pnpm --filter @modeler/<pkg>`, dependency-graph section, package-conventions bullet — update to `@tatrman/*` and note the rename), any `justfile`/README hits.
- [x] Leave historical docs untouched: `docs/v1/**`, `docs/v1-1/**` review/progress artifacts keep `@modeler/` as written (they are records, not instructions).
  - **Verify:** `pnpm install && pnpm -r build && pnpm -r test && pnpm -r lint` all green; `grep -rn "@modeler/" --include="*.ts" packages tests | wc -l` → 0.

### T0.1.5 · CI job for the Kotlin ttrp modules — ✅ DONE

- [x] Add a `kotlin` job to `.github/workflows/ci.yml` (today ci.yml is pnpm-only; the Gradle pattern lives in `conformance.yml` — mirror its `setup-java`/`setup-gradle` steps exactly, java-version `'21'`):
  ```yaml
  kotlin:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin   # confirm against conformance.yml
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build
  ```
  `./gradlew build` covers ttr-parser/writer/semantics AND all six ttrp modules (build + test) — exactly the plan's "build + test all new modules".
  - **Verify:** open a PR with the stage's commits; the `kotlin` job appears and passes on the PR checks. (Local proxy before push: `./gradlew build` green.)

### T0.1.6 · Stale `§Open` markers annotated in design docs 02/03/05/06/07/08 — ✅ DONE

- [x] Per the S1–S25 consolidation note ("Stale §Open sections in older catalogues = doc hygiene, not decisions; decision log remains ground truth"), insert a blockquote directly UNDER each `## Open…` heading — do not delete or edit the list items themselves. Exact heading locations (verify with `grep -n "^#\{2,3\} Open" <file>` in case of drift):
  - `docs/ttr-p/design/02-internal-model-options.md` — `### Open questions in T9` (~line 213)
  - `docs/ttr-p/design/03-tooling-delivery-options.md` — `## Open` (~line 109)
  - `docs/ttr-p/design/05-canonical-dsl-options.md` — `## Open leftovers …` (~line 291)
  - `docs/ttr-p/design/06-model-binding-options.md` — `## Open questions (D-local)` (~line 180)
  - `docs/ttr-p/design/07-emit-options.md` — `## Open questions (E-local)` (~line 124)
  - `docs/ttr-p/design/08-orchestration-options.md` — `## Open questions (F-local)` (~line 163)
  Annotation text (identical in all six):
  ```markdown
  > **Stale as of the 2026-07-04 consolidation.** These items were resolved (or explicitly deferred) by the decision log — see `00-control-room.md` §4, in particular the S1–S25 consolidation sweep, and `../architecture/{architecture,contracts}.md`. Kept verbatim as a historical record; the decision log is ground truth.
  ```
  - **Verify:** `grep -c "Stale as of the 2026-07-04 consolidation" docs/ttr-p/design/0{2,3,5,6,7,8}-*.md` → 1 per file (6 total); `git diff --stat` shows only insertions in those six files.

### T0.1.7 · `PUBLISHING.md` rows for the ttrp artifacts + stage close-out — ✅ DONE

- [x] Append to the module table in `PUBLISHING.md` (after the `ttr-semantics` row; "Phase" column values name the TTR-P plan phase that first publishes them — plumbing rows only, no workflow changes in P0):
  ```markdown
  | `:packages:kotlin:ttrp-frontend` | `org.tatrman:ttrp-frontend` | TTR-P P3 | compiler front-half: parse → resolve → typecheck; serves `ttrp check`/`validate`/`authoringContext` (contracts §10) |
  | `:packages:kotlin:ttrp-graph`    | `org.tatrman:ttrp-graph`    | TTR-P P3 | graph construction + normalizer (T8 rewrites) |
  | `:packages:kotlin:ttrp-emit`     | `org.tatrman:ttrp-emit`     | TTR-P P3 | island codegen, movement synthesis, bundle assembly |
  | `:packages:kotlin:ttrp-lsp`      | `org.tatrman:ttrp-lsp`      | TTR-P P4 | one TTR-P LSP; stdio + WS transports |
  | `:packages:kotlin:ttrp-cli`      | `org.tatrman:ttrp-cli`      | TTR-P P3 | the `ttrp` binary (S2): build/run/explain/conform |
  | `:packages:kotlin:ttrp-conform`  | `org.tatrman:ttrp-conform`  | TTR-P P3 | Q9 conformance harness (S3) |
  ```
- [x] Add a row to the tag table: `| ttrp/v<x.y.z> | bundle: all org.tatrman:ttrp-* modules (first cut in TTR-P Phase 3; workflow wiring lands there) |`.
- [x] Close out: run the full DONE sweep below and check every box.
  - **Verify:** `git diff PUBLISHING.md` shows exactly the 7 new rows; `./gradlew -Pversion=0.0.1-LOCAL :packages:kotlin:ttrp-frontend:publishToMavenLocal` succeeds (proves the maven-publish blocks are well-formed).

## Definition of DONE (stage)

- [x] `./gradlew build` — BUILD SUCCESSFUL, all 9 Kotlin modules (3 ttr-* + 6 ttrp-*) compiled and tested.
- [x] `pnpm install && pnpm -r build && pnpm -r test && pnpm -r lint` — green under the `@tatrman/*` scope.
- [x] `grep -rn "@modeler/" --include="*.ts" --include="package.json" packages tests` → 0 hits.
- [x] CI on a PR runs the new `kotlin` job and it passes.
- [x] Six design docs carry the stale-§Open annotation; `PUBLISHING.md` carries the 6 module rows + 1 tag row.

## Blockers

_(empty — coder records here)_

## References

- **S7** — npm scope rename `@modeler/*`→`@tatrman/*`, assigned to v1 plan Phase 0 (control-room, 2026-07-04 consolidation).
- **S6** — TTR-P spec version = integer via `docs/grammar-master/` process; Maven artifacts semver per `PUBLISHING.md`.
- **G-b** — TTR-P is Kotlin-only: single parser, no TS/antlr-ng target, no cross-target conformance for TTRP.g4 (architecture §6).
- **C2-g** — fragment grammars (later) are own ANTLR grammars on the same toolchain; the seed establishes the pattern.
- Module cut ttrp-{frontend,graph,emit,lsp,cli,conform} — plan.md Phase 0 + contracts §10 last row.
- ANTLR Gradle-plugin pattern (direct `.g4` read, `-package` flag, flat-output caveat, antlr-api-leak fix): `packages/kotlin/ttr-parser/build.gradle.kts` — copy its inline comments, they document two real footguns.
- Kotest 6 (`io.kotest:kotest-runner-junit5` via `libs.bundles.kotest`, `useJUnitPlatform()`, `StringSpec`): existing specs under `packages/kotlin/ttr-parser/src/test/kotlin/`.
- CI Gradle pattern: `.github/workflows/conformance.yml` (setup-java 21 + `gradle/actions/setup-gradle@v4`).
