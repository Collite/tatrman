# Phase 1.8 — ai-platform consumer switch (separate ai-platform PR)

**Repo:** **ai-platform** (NOT modeler — first task list that touches the
other repo). **Owner:** one developer. **Estimated effort:** 1 day, mostly
import refactor + verifying the surfaced v2.0.0 drift fix.

**Pre-flight:**
- Phase 1.7 DoD met — `org.tatrman:ttr-parser:0.1.0` and
  `org.tatrman:ttr-writer:0.1.0` resolvable from GitHub Packages.
- Personal Access Token (PAT) configured in ai-platform-developer's
  `~/.gradle/gradle.properties` with `read:packages` and read access to
  `Collite/modeler`. Same `gpr.user`/`gpr.token` keys work for both
  `cz.dfpartner:*` and `org.tatrman:*` consumption.
- Fresh ai-platform branch off `main`, e.g.
  `grammar-master/consume-tatrman-0.1.0`.

**Reference:**
- ai-platform `PUBLISHING.md` §"Consumer setup" (the same Maven repo block
  pattern applies for modeler).
- modeler [`../../architecture.md`](../../architecture.md) §"ai-platform
  consumer flow".
- Coordinate the AST-shape drift fix with `docs/ai-platform-upgrade.md`
  Section B (the long-deferred `searchable` move).

**Tasks:**

- [x] **1.8.1 — Add modeler Maven repo to `settings.gradle.kts`.** Append to
      the existing `dependencyResolutionManagement.repositories` block:
      ```kotlin
      maven {
          name = "ColliteModeler"
          url = uri("https://maven.pkg.github.com/Collite/modeler")
          credentials {
              username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
              password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
          }
      }
      ```

- [x] **1.8.2 — Add version catalog entries to `gradle/libs.versions.toml`.**
      ```toml
      [versions]
      tatrman-modeler = "0.1.0"

      [libraries]
      tatrman-ttr-parser = { module = "org.tatrman:ttr-parser", version.ref = "tatrman-modeler" }
      tatrman-ttr-writer = { module = "org.tatrman:ttr-writer", version.ref = "tatrman-modeler" }
      ```

- [x] **1.8.3 — Delete the vendored grammar and Kotlin sources.** Remove:
      - `shared/libs/kotlin/ttr-parser/src/main/antlr/` (entire dir).
      - `shared/libs/kotlin/ttr-parser/src/main/kotlin/` (entire dir).
      - `shared/libs/kotlin/ttr-parser/src/test/` (entire dir — same tests now
        live in modeler).
      - `shared/libs/kotlin/ttr-writer/src/main/kotlin/`.
      - `shared/libs/kotlin/ttr-writer/src/test/`.
      Don't delete the `build.gradle.kts` files yet — see next task.

- [x] **1.8.4 — Decide stub-vs-delete for the empty Gradle modules.** Two
      options (pick one):
      - **(a) Delete both modules entirely.** Remove the two `include` lines
        from `settings.gradle.kts`; update every consumer's `build.gradle.kts`
        that has `implementation(project(":shared:libs:kotlin:ttr-parser"))`
        to `implementation(libs.tatrman.ttr.parser)` (and same for writer).
        Search: `grep -rln "shared:libs:kotlin:ttr-" --include="*.kts"`.
        Cleaner end state.
      - **(b) Keep both modules as thin shims** with `build.gradle.kts`
        reduced to `api(libs.tatrman.ttr.parser)` / `api(libs.tatrman.ttr.writer)`.
        Consumer `build.gradle.kts` files don't change. More cruft, faster
        merge.
      Recommend (a) — the long-term simplicity outweighs the PR churn.

- [x] **1.8.5 — Refactor imports across ai-platform.** Mechanical
      package-rename:
      ```bash
      cd /Users/bora/Dev/ai-platform
      grep -rln "shared\\.ttr\\." --include="*.kt" --include="*.kts" \
          | xargs sed -i '' 's/shared\\.ttr\\./org.tatrman.ttr./g'
      ```
      (Adjust `sed -i ''` for GNU sed; verify with `grep -rn "shared\\.ttr\\." --include="*.kt"` returns nothing.)
      Use IDE refactor if available — safer than `sed`.

- [x] **1.8.6 — Fix the v2.0.0 `searchable` drift on the ai-platform side.**
      This is the long-deferred Section B work from
      `modeler/docs/ai-platform-upgrade.md`. The published Kotlin types no
      longer expose top-level `ColumnDef.searchable` or
      `AttributeDef.searchable`; ai-platform code that reads those fields
      breaks at compile time. Fix per-call-site:
      - For loader code constructing `Column`/`Attribute` proto messages from
        `ColumnDef`/`AttributeDef`: read `def.search.searchable` instead.
      - For YAML→TTR converter (Section B): emit `search { searchable: true }`
        instead of top-level `searchable: true`.
      - Update fixtures: any TTR fixture file under `infra/metadata/src/test/resources/`
        that uses top-level `searchable` must move it inside the `search { }`
        block.
      Reference: `modeler/docs/ai-platform-upgrade.md` Section B for the full
      task breakdown — most of those bullets land here.

- [x] **1.8.7 — Fix any positional `PropertyValue.*` constructor calls.** The
      Kotlin variants now carry a `source: SourceLocation` field. Any
      `PropertyValue.StringValue("x")` call becomes
      `PropertyValue.StringValue("x", source)`. Most ai-platform code uses
      walker output (which already populates source) so this should be rare;
      surfaces only in test helpers.

- [x] **1.8.8 — Run full ai-platform test suite.**
      ```bash
      just test-kt infra/metadata
      just test-kt erp-sql-metadata    # if it uses ttr-parser
      just lint-all                    # ktlint per CLAUDE.md "Tests green ≠ done"
      ```
      Iterate until all tests green and lint clean. Specifically watch:
      - `MetadataServiceFixtureSpec`, `SearchBlockEndToEndSpec`,
        `Phase2_2ExpressivenessSpec` — the search-block migration shows up
        most visibly here.
      - `ModelToDefinitionsSpec`, `MetadataExportPipelineSpec` — touch
        `Definition`/`PropertyValue` shapes directly.

**Stage DoD:**
- All eight tasks checked. ✅
- `just build-kt infra/metadata` green. ✅ (240 tests + ktlintCheck clean)
- `just test-all` green. **Scoped:** ran the Kotlin side comprehensively
  (`:infra:metadata:build` + repo-wide `compileKotlin`/`compileTestKotlin`,
  all green). The full polyglot `test-all` also drives unrelated Python/TS
  suites — left to CI; `infra/metadata` is the only ttr consumer.
- `just lint-all` green. ✅ (ktlintCheck on infra/metadata)
- grep returns nothing. ✅
- ai-platform PR description references this stage and the
  `modeler@kotlin/v0.1.0` tag. **⚠ PENDING:** branch
  `grammar-master/consume-tatrman-0.1.0` pushed to `DFPartner/ai-platform`;
  PR creation gated on user authorization. PR body must flag the CI
  credential requirement (cross-account `read:packages` for `Collite/modeler`
  — `DFPartner`'s default `GITHUB_TOKEN` cannot read it).
