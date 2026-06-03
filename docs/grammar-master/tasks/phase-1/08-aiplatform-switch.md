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

- [ ] **1.8.1 — Add modeler Maven repo to `settings.gradle.kts`.** Append to
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

- [ ] **1.8.2 — Add version catalog entries to `gradle/libs.versions.toml`.**
      ```toml
      [versions]
      tatrman-modeler = "0.1.0"

      [libraries]
      tatrman-ttr-parser = { module = "org.tatrman:ttr-parser", version.ref = "tatrman-modeler" }
      tatrman-ttr-writer = { module = "org.tatrman:ttr-writer", version.ref = "tatrman-modeler" }
      ```

- [ ] **1.8.3 — Delete the vendored grammar and Kotlin sources.** Remove:
      - `shared/libs/kotlin/ttr-parser/src/main/antlr/` (entire dir).
      - `shared/libs/kotlin/ttr-parser/src/main/kotlin/` (entire dir).
      - `shared/libs/kotlin/ttr-parser/src/test/` (entire dir — same tests now
        live in modeler).
      - `shared/libs/kotlin/ttr-writer/src/main/kotlin/`.
      - `shared/libs/kotlin/ttr-writer/src/test/`.
      Don't delete the `build.gradle.kts` files yet — see next task.

- [ ] **1.8.4 — Decide stub-vs-delete for the empty Gradle modules.** Two
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

- [ ] **1.8.5 — Refactor imports across ai-platform.** Mechanical
      package-rename:
      ```bash
      cd /Users/bora/Dev/ai-platform
      grep -rln "shared\\.ttr\\." --include="*.kt" --include="*.kts" \
          | xargs sed -i '' 's/shared\\.ttr\\./org.tatrman.ttr./g'
      ```
      (Adjust `sed -i ''` for GNU sed; verify with `grep -rn "shared\\.ttr\\." --include="*.kt"` returns nothing.)
      Use IDE refactor if available — safer than `sed`.

- [ ] **1.8.6 — Fix the v2.0.0 `searchable` drift on the ai-platform side.**
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

- [ ] **1.8.7 — Fix any positional `PropertyValue.*` constructor calls.** The
      Kotlin variants now carry a `source: SourceLocation` field. Any
      `PropertyValue.StringValue("x")` call becomes
      `PropertyValue.StringValue("x", source)`. Most ai-platform code uses
      walker output (which already populates source) so this should be rare;
      surfaces only in test helpers.

- [ ] **1.8.8 — Run full ai-platform test suite.**
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
- All eight tasks checked.
- `just build-kt infra/metadata` green.
- `just test-all` green.
- `just lint-all` green.
- `grep -rn "shared.ttr.parser\|shared/libs/kotlin/ttr-parser/src" --include="*.kt" --include="*.kts"`
  returns nothing.
- ai-platform PR description references this stage and the
  `modeler@kotlin/v0.1.0` tag.
