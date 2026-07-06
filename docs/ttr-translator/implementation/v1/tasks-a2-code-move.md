# Tasks · Extraction arc · Stage A2 — Code move (tests first)

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Contracts: [`../../architecture/contracts.md`](../../architecture/contracts.md) §3.
> **Coder rules:** top-to-bottom; check `[x]` immediately after verification; blocked ⇒ STOP + §Blockers. **The diff vs. kantheon `f2e2efb` must be package names + build wiring ONLY — no behavioral change rides this move (TR-7).**

## Stage deliverable

`:packages:kotlin:ttr-translator` green with the complete moved suite (35 test files / 30+ Kotest specs + golden/fixture resources), root package `org.tatrman.translator.*`, source dirs normalized, test fixtures published via `java-test-fixtures`, ktlint clean, repo build green.

## Pre-flight

- [ ] Stage A1 DONE (ttr-plan-proto builds; catalog entries present).
- [ ] Kantheon pin unchanged (`git -C ~/Dev/collite-gh/kantheon rev-parse HEAD` → `f2e2efb…`; else re-pin + re-run the A1 diff loop).
- [ ] **Dependency audit** of the source lib — the arc's one open risk (kantheon CLAUDE.md §7.3 hints at transitive ttr-parser/writer consumption):
  ```bash
  cd ~/Dev/collite-gh/kantheon && ./gradlew :shared:libs:kotlin:query-translator:dependencies --configuration compileClasspath | grep -E "tatrman|project" 
  ```
  Expected: only `:shared:proto` (+ Calcite/slf4j/protobuf). If `org.tatrman:ttr-parser`/`ttr-writer`/`ttr-metadata` appear, record under §Blockers and add the corresponding `api(project(":packages:kotlin:ttr-…"))` deps in T-A2.1 — they are in-repo here, this is a wiring note not a stopper.

## Tasks

### T-A2.1 · Module scaffold

- [ ] `packages/kotlin/ttr-translator/build.gradle.kts`: plugins `kotlin-jvm`, `ktlint`, `java-library`, `java-test-fixtures`, `maven-publish`; `jvmToolchain(21)`; deps: `api(project(":packages:kotlin:ttr-plan-proto"))`, `api(libs.calcite.core)`, `implementation(libs.slf4j.api)`, `implementation(libs.protobuf.java.util)`, `testImplementation(libs.bundles.kotest)`, `testImplementation(libs.mockk)` (+ any audit findings from pre-flight). Publication block per the ttr-metadata shape, artifactId `ttr-translator`.
  - **Verify:** `./gradlew :packages:kotlin:ttr-translator:build` → green (empty module).

### T-A2.2 · Port the TEST tree FIRST (red)

- [ ] Copy `src/test/kotlin/**` and `src/test/resources/**` (and `src/testFixtures/**` if present at the pin — check; `InMemoryModelHandle` may live under test sources) from the kantheon lib into the module, then apply the package rewrite + dir normalization:
  ```bash
  # inside packages/kotlin/ttr-translator
  grep -rl "org\.tatrman\.query\.shared\.translator" src | xargs sed -i '' 's/org\.tatrman\.query\.shared\.translator/org.tatrman.translator/g'
  # kantheon kept sources under src/test/kotlin/shared/translator/** — move to package-true dirs:
  mkdir -p src/test/kotlin/org/tatrman/translator && git mv src/test/kotlin/shared/translator/* src/test/kotlin/org/tatrman/translator/ 2>/dev/null || mv src/test/kotlin/shared/translator/* src/test/kotlin/org/tatrman/translator/
  ```
  (Adapt if the source layout differs — the rule is: **directories match packages** after the move.)
- [ ] Rewrite any `org.tatrman.query` remnants (package declarations, not just imports): `grep -rn "org\.tatrman\.query" src` → zero hits.
  - **Verify:** `./gradlew :packages:kotlin:ttr-translator:test` → COMPILATION FAILS (main sources absent) — red for the right reason. Commit as `Extraction A2: test tree ported (red)`.

### T-A2.3 · Port the MAIN tree (green)

- [ ] Copy `src/main/kotlin/**` (+ `src/main/resources/**` if any), apply the identical rewrite + dir normalization. `InMemoryModelHandle` (if fixture-shaped) goes to `src/testFixtures/kotlin/…` with `testFixturesApi` wiring so downstream consumers get it (contracts §3).
  - **Verify:** `./gradlew :packages:kotlin:ttr-translator:test` → BUILD SUCCESSFUL; count specs in the report (`ls packages/kotlin/ttr-translator/build/test-results/test/ | wc -l`) and record the number here: ___ (expect ≈30+; any missing spec vs. the kantheon run = blocker).

### T-A2.4 · Parity check against the source suite

- [ ] Run the source suite once at the pin and compare test counts/names:
  ```bash
  cd ~/Dev/collite-gh/kantheon && ./gradlew :shared:libs:kotlin:query-translator:test
  # compare build/test-results XML test names against the tatrman run — same set, modulo package prefix
  ```
  Any test present there and absent here (or newly failing) = §Blockers. This is the "no behavioral change" proof.
  - **Verify:** name-set diff empty; both suites green.

### T-A2.5 · Lint + hygiene

- [ ] `./gradlew :packages:kotlin:ttr-translator:ktlintCheck` clean (run `ktlintFormat` first if the inherited style drifts; formatting-only changes are allowed in a separate commit `Extraction A2: ktlint sweep`).
- [ ] No `!!` introduced, no TODOs added, no dead kantheon references in comments that claim in-repo paths (`grep -rn "shared/libs\|shared:proto" src` → fix stragglers to artifact-speak).
  - **Verify:** commands above clean.

### T-A2.6 · Provenance README + repo docs

- [ ] `packages/kotlin/ttr-translator/README.md`: what it is (Proteus translation core, E-a α′), provenance (extracted from kantheon `shared/libs/kotlin/query-translator` @ `f2e2efb`, 2026-07-06, whole-lib per TR-1), package map (`org.tatrman.query.shared.translator` → `org.tatrman.translator`), API pointer to contracts §3, Calcite engagement rules pointer (tasks-p3-s3.1 T3.1.1 is the consumer-side canon).
- [ ] Root `CLAUDE.md` §Kotlin artifacts: add the two modules to the published-artifacts description (translator + plan-proto, lockstep tag `kotlin-translator/v*` — wording lands fully in A3, a one-line mention here is enough to not lie to the next session).
  - **Verify:** `./gradlew build` repo-green; `pnpm -r build` unaffected (no TS touched — quick sanity only).

## Definition of DONE (stage)

- [ ] Moved suite green, parity-proven vs. the kantheon pin (T-A2.4).
- [ ] Zero `org.tatrman.query` references; dirs match packages.
- [ ] ktlint clean; repo build green.
- [ ] Provenance README committed; commit series `Extraction A2: …`.
- [ ] `/review` requested (highest-risk diff of the arc).

## Blockers

_(empty)_
