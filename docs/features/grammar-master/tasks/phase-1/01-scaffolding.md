# Phase 1.1 — Scaffolding: Gradle + Kotlin module skeleton

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** half day.

**Pre-flight:**
- Java 21 (`temurin`) installed locally — `java -version` reports 21.x.
- Modeler repo on a fresh branch off `main`, e.g. `kotlin/phase-1-scaffolding`.
- Read [`../../architecture.md`](../../architecture.md) §"Tech stack" and
  §"Repository layout" and §"Module wiring (Gradle)".

**Reference files:**
- ai-platform `gradle/libs.versions.toml` (versions to mirror).
- ai-platform `shared/libs/kotlin/ttr-parser/build.gradle.kts` (template — but
  drop the `:shared:proto` dep; use Kotlin 2.3.0 / JVM 21).
- ai-platform `settings.gradle.kts` (multi-module pattern).

**Tasks** (check each immediately after completion):

- [x] **1.1.1 — Initialise Gradle wrapper.** From modeler repo root, run
      `gradle wrapper --gradle-version 9.5 --distribution-type bin` (requires
      a system Gradle to bootstrap; if unavailable, copy the four wrapper
      files — `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`,
      `gradle/wrapper/gradle-wrapper.properties` — from ai-platform). Commit
      these four files plus `.gitignore` entries for `.gradle/`, `build/`,
      `**/build/`. **Note:** Gradle 9.x requires a `settings.gradle.kts` to
      exist before the `wrapper` task runs — bootstrap with a one-line
      `rootProject.name = "modeler-kotlin"` settings file first, then generate
      the wrapper. The pin resolves to the 9.5 line (`gradle-9.5.1-bin.zip`).

- [x] **1.1.2 — Create `gradle/libs.versions.toml`** mirroring ai-platform's
      relevant entries (versions copied 1:1 from ai-platform's catalog;
      `kotlinx-ser = "1.10.0"` and the `kotlin-serialization` plugin added for
      the conformance dumper in stage 1.6). Required content:
      ```toml
      [versions]
      kotlin = "2.3.0"
      kotest = "6.1.2"
      mockk = "1.14.9"
      antlr-runtime = "4.13.2"
      slf4j = "2.0.17"
      ktlint = "14.0.1"
      kotlinx-ser = "1.10.0"

      [libraries]
      antlr-runtime = { module = "org.antlr:antlr4-runtime", version.ref = "antlr-runtime" }
      antlr-tool    = { module = "org.antlr:antlr4",          version.ref = "antlr-runtime" }
      slf4j-api     = { module = "org.slf4j:slf4j-api",       version.ref = "slf4j" }
      kotlinx-ser-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-ser" }
      kotest-runner = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }
      kotest-assertions = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
      kotest-property   = { module = "io.kotest:kotest-property",        version.ref = "kotest" }
      mockk         = { module = "io.mockk:mockk", version.ref = "mockk" }

      [bundles]
      kotest = ["kotest-runner", "kotest-assertions", "kotest-property"]

      [plugins]
      kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
      kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
      ktlint     = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint" }
      ```

- [x] **1.1.3 — Create root `settings.gradle.kts`.** Content:
      ```kotlin
      rootProject.name = "modeler-kotlin"
      pluginManagement {
          repositories {
              gradlePluginPortal()
              mavenCentral()
          }
      }
      dependencyResolutionManagement {
          repositories {
              mavenCentral()
          }
      }
      include(":packages:kotlin:ttr-parser")
      include(":packages:kotlin:ttr-writer")
      ```

- [x] **1.1.4 — Create root `build.gradle.kts`** with default repos and a
      common configuration block for the Kotlin subprojects (JVM toolchain 21,
      java-library defaults, group `org.tatrman`, version from
      `-Pversion=<x.y.z>` or fallback `0.0.1-LOCAL`). Keep it small — most
      config lives in the subproject `build.gradle.kts` files.
      ```kotlin
      allprojects {
          group = "org.tatrman"
          version = (findProperty("version") as String?).takeUnless { it == "unspecified" } ?: "0.0.1-LOCAL"
      }
      ```

- [x] **1.1.5 — Create `packages/kotlin/ttr-parser/build.gradle.kts`.** Based
      on ai-platform's existing file with these explicit differences:
      - **Remove** `api(project(":shared:proto"))` (stale dep — no Kotlin
        source imports proto in ai-platform's current code).
      - **Point ANTLR at the canonical grammar:**
        ```kotlin
        plugins {
            base
            alias(libs.plugins.kotlin.jvm)
            alias(libs.plugins.ktlint)
            `java-library`
            `maven-publish`
            antlr
        }
        kotlin { jvmToolchain(21) }
        tasks.test { useJUnitPlatform() }

        val canonicalGrammar = file("../../grammar/src/TTR.g4")

        sourceSets["main"].antlr.setSrcDirs(listOf(canonicalGrammar.parentFile))

        val generatedPackage = "org.tatrman.ttrm.parser.generated"
        tasks.named<org.gradle.api.plugins.antlr.AntlrTask>("generateGrammarSource") {
            source = fileTree(canonicalGrammar.parentFile) { include("TTR.g4") }
            arguments = arguments + listOf("-visitor", "-long-messages", "-package", generatedPackage)
            // NOTE: do NOT override outputDirectory to nest files under the package
            // path. ANTLR emits the .java files FLAT into generated-src/antlr/main/;
            // they declare `package org.tatrman.ttrm.parser.generated` and compile
            // correctly. Nesting via an outputDirectory override causes duplicate-
            // class errors on clean rebuilds (regressed in 1.5; reverted).
        }

        dependencies {
            antlr(libs.antlr.tool)
            api(libs.antlr.runtime)
            implementation(libs.slf4j.api)
            testImplementation(libs.bundles.kotest)
            testImplementation(libs.mockk)
        }

        tasks.named("compileKotlin") { dependsOn("generateGrammarSource") }
        tasks.named("compileJava") { dependsOn("generateGrammarSource") }
        ktlint { filter { exclude("**/generated/**"); exclude { it.file.path.contains("/generated-src/antlr/") } } }
        ```

- [x] **1.1.6 — Create `packages/kotlin/ttr-writer/build.gradle.kts`.** Same
      plugins (no `antlr`), depends on `api(project(":packages:kotlin:ttr-parser"))`.

- [x] **1.1.7 — Verify the empty build works.** Create placeholder file
      `packages/kotlin/ttr-parser/src/main/kotlin/org/tatrman/ttr/parser/Placeholder.kt`
      with `package org.tatrman.ttrm.parser`. Run
      `./gradlew :packages:kotlin:ttr-parser:compileKotlin`. **Expected:** ANTLR
      generates the parser flat into
      `packages/kotlin/ttr-parser/build/generated-src/antlr/main/` (the `.java`
      files declare `package org.tatrman.ttrm.parser.generated`), Kotlin compile
      succeeds.

- [x] **1.1.8 — Add `.gitignore`** entries (or extend existing): `.gradle/`,
      `build/`, `**/build/`, `**/.gradle/`. Commit.

**Verification commands:**
```bash
./gradlew :packages:kotlin:ttr-parser:compileKotlin
./gradlew :packages:kotlin:ttr-writer:compileKotlin
ls packages/kotlin/ttr-parser/build/generated-src/antlr/main/
# Expect (flat): TTRLexer.java, TTRParser.java, TTRListener.java, TTRBaseListener.java, TTRVisitor.java, TTRBaseVisitor.java
```

**Stage DoD:**
- All eight tasks checked.
- Both modules compile (even though only placeholder Kotlin source exists).
- Generated ANTLR Java files exist (flat in `generated-src/antlr/main/`) and
  declare the `org.tatrman.ttrm.parser.generated` package.
- `pnpm -r build` still works (Gradle changes haven't broken the TS build).
