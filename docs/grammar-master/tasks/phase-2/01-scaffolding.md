# Phase 2.1 — Scaffolding: ttr-semantics Gradle module

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1–2 hours.

**Pre-flight:**
- Phase 1 fully complete; `org.tatrman:ttr-parser:0.1.0+` published and
  consumed by ai-platform.
- At least one grammar minor bump has shipped through Phase 1's pipeline
  (proves the publishing rhythm; not strictly required but recommended).
- Fresh branch off `main`, e.g. `kotlin/phase-2-scaffolding`.

**Reference:**
- [`../../architecture.md`](../../architecture.md) §"Phase 2 architecture
  additions".
- [`../../contracts.md`](../../contracts.md) §4 (ttr-semantics public API).

**Tasks:**

- [ ] **2.1.1 — Create `packages/kotlin/ttr-semantics/build.gradle.kts`.**
      Based on `ttr-parser/build.gradle.kts` minus the ANTLR plugin (semantics
      doesn't generate any code). Dependencies:
      ```kotlin
      api(project(":packages:kotlin:ttr-parser"))
      implementation(libs.slf4j.api)
      testImplementation(libs.bundles.kotest)
      ```
      Add the `maven-publish` block as in 1.7.1 with name "TTR Semantics".

- [ ] **2.1.2 — Register the module in root `settings.gradle.kts`.**
      Add: `include(":packages:kotlin:ttr-semantics")`.

- [ ] **2.1.3 — Create the package directory skeleton.**
      `packages/kotlin/ttr-semantics/src/main/kotlin/org/tatrman/ttr/semantics/`
      with one `Placeholder.kt` so the module compiles.

- [ ] **2.1.4 — Create the resources directory for stock vocab.**
      `packages/kotlin/ttr-semantics/src/main/resources/builtin/` — empty for
      now; `cnc-stock-roles.ttr` lands in stage 2.5.

- [ ] **2.1.5 — Update the publish workflow.** Edit
      `.github/workflows/publish.yml` so the `kotlin/v*` tag publishes all
      three modules (`ttr-parser`, `ttr-writer`, `ttr-semantics`). Add
      `kotlin-semantics/v*` as a tag pattern that publishes the semantics
      module alone.

- [ ] **2.1.6 — Verify the empty build works.**
      ```bash
      ./gradlew :packages:kotlin:ttr-semantics:compileKotlin
      ./gradlew build      # all three modules + tests
      ```

**Stage DoD:**
- Six tasks checked.
- Three-module Gradle build green.
- `publish.yml` updated with the new tag-mapping and module list.
- `:packages:kotlin:ttr-semantics:publishToMavenLocal` succeeds with a stub
  publication (no real classes yet).
