# Stage 4.A — Gradle scaffold

Companion to [`../implementation-plan.md`](../implementation-plan.md) Stage 4.A. Goal: an empty-but-valid plugin that launches in a sandbox IDE via `runIde`, with LSP4IJ installed. **Check each box the moment it's done.**

**Outcome:** `./gradlew :intellij-plugin:runIde` opens a sandbox IDEA with the plugin loaded and LSP4IJ present; `verifyPlugin` passes. No language features yet — that's Stage 4.C.

Estimate: 1–2 days. Pre-flight: JDK 17, Gradle, network to JetBrains repositories.

---

## Block 1 — Module and Gradle root

- [x] **1.1 — Create the module directory and Gradle files.**
  Create `intellij-plugin/` at the repo root (sibling to `packages/`, per [`architecture.md` §2 IJ8](../../design/architecture.md)) with `settings.gradle.kts`, `gradle.properties`, `build.gradle.kts`, and a Gradle wrapper (`gradle/wrapper/`, `gradlew`).
  In `gradle.properties` set: `pluginGroup=org.tatrman.modeler`, `pluginName=TTR Modeler`, `pluginVersion=0.1.0`, `platformVersion=2024.1`.
  **Verify:** `cd intellij-plugin && ./gradlew tasks` runs without configuration errors.

- [x] **1.2 — Add the IntelliJ Platform repositories in `settings.gradle.kts`.**
  ```kotlin
  dependencyResolutionManagement {
    repositories {
      mavenCentral()
      intellijPlatform { defaultRepositories() }
    }
  }
  rootProject.name = "intellij-plugin"
  ```
  **Verify:** `./gradlew help` resolves with no repository errors.

## Block 2 — Plugin build configuration

- [x] **2.1 — Configure `build.gradle.kts` plugins + platform dependency.**
  ```kotlin
  plugins {
    id("org.jetbrains.kotlin.jvm") version "<platform-aligned>"
    id("org.jetbrains.intellij.platform") version "2.<latest>"
  }
  dependencies {
    intellijPlatform {
      intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
      testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
  }
  kotlin { jvmToolchain(17) }
  ```
  > Pin the IntelliJ Platform Gradle Plugin to the latest 2.x and the Kotlin plugin to the version bundled with the targeted IDEA. Resolve IJ-Q2 (min IDEA version) here and record it in [`contracts.md` §9](../../design/contracts.md).
  **Verify:** `./gradlew :intellij-plugin:dependencies` lists the IntelliJ IDEA Community artifact.

- [x] **2.2 — Add LSP4IJ as a Marketplace plugin dependency.**
  In the `intellijPlatform { … }` dependencies block:
  ```kotlin
  intellijPlatform {
    // …existing…
    plugins("com.redhat.devtools.lsp4ij:<lsp4ij-version>")
  }
  ```
  Pick the LSP4IJ version compatible with the IDEA baseline; record the pin in [`contracts.md` §9](../../design/contracts.md).
  **Verify:** `./gradlew :intellij-plugin:dependencies` shows the `com.redhat.devtools.lsp4ij` plugin artifact.

- [x] **2.3 — Configure `pluginConfiguration` (metadata + compatibility range).**
  ```kotlin
  intellijPlatform {
    pluginConfiguration {
      id = "org.tatrman.modeler.intellij"
      name = "TTR Modeler"
      version = providers.gradleProperty("pluginVersion")
      ideaVersion { sinceBuild = "241" }   // confirm against IJ-Q2
    }
  }
  ```
  **Verify:** `./gradlew :intellij-plugin:patchPluginXml` succeeds and the generated `plugin.xml` shows `<idea-version since-build="241"/>`.

## Block 3 — Plugin descriptor and sandbox launch

- [x] **3.1 — Write the minimal `META-INF/plugin.xml`.**
  Create `src/main/resources/META-INF/plugin.xml` with `id`, `name`, `vendor` (`Collite`, the existing VS Code publisher), and the three dependencies — **no extensions yet**:
  ```xml
  <idea-plugin>
    <id>org.tatrman.modeler.intellij</id>
    <name>TTR Modeler</name>
    <vendor email="…" url="https://github.com/Collite/modeler">Collite</vendor>
    <depends>com.intellij.modules.platform</depends>
    <depends>com.redhat.devtools.lsp4ij</depends>
    <depends>org.jetbrains.plugins.textmate</depends>
  </idea-plugin>
  ```
  **Verify:** file matches the dependency list in [`contracts.md` §2](../../design/contracts.md).

- [~] **3.2 — Launch the sandbox IDE and confirm the plugin + LSP4IJ load.**
  Run `./gradlew :intellij-plugin:runIde`. In the sandbox IDE open *Settings → Plugins* and confirm both **TTR Modeler** and **LSP4IJ** are installed and enabled.
  **Verify:** the sandbox launches with no errors in `idea.log`; both plugins are listed.
  > **Headless note:** the GUI launch itself requires an interactive desktop, so it was not run in the dev environment. The headless equivalents all pass: `verifyPlugin` loads the plugin against IDEs 242 → 262 (Community **and** Ultimate) with no structural problems, `buildPlugin` produces a valid `.zip`, and `prepareSandbox` assembles the sandbox. The LSP4IJ dependency resolves from the Marketplace at build time. **Run `runIde` once on a desktop to tick this fully** (also covered by Stage 4.E).

- [x] **3.3 — Run plugin structure verification.**
  Run `./gradlew :intellij-plugin:verifyPlugin`.
  **Verify:** the task passes (a missing-`<description>` warning is acceptable at this stage; it's filled in Stage 4.D).

## Block 4 — Repo hygiene

- [x] **4.1 — Gitignore the build-time-copied resources.**
  Add to the repo's `.gitignore`:
  ```
  intellij-plugin/src/main/resources/server/
  intellij-plugin/src/main/resources/textmate/
  intellij-plugin/build/
  intellij-plugin/.gradle/
  ```
  **Verify:** `git status` does not show those paths after a build. This matches the generated-artefact policy in [`contracts.md` §1](../../design/contracts.md).

---

### Stage 4.A definition of DONE

- [~] `runIde` launches a sandbox IDEA with the plugin + LSP4IJ, no errors. *(GUI-only; headless proxies — `verifyPlugin`/`buildPlugin`/`prepareSandbox` — all pass. Run on a desktop to confirm.)*
- [x] `verifyPlugin` passes structure verification. *(Green against IDEs 242 → 262, Community + Ultimate.)*
- [x] Generated resource paths are gitignored.
- [x] IJ-Q2 (min IDEA version) and the LSP4IJ version pin are recorded in [`contracts.md` §9](../../design/contracts.md). *(IDEA 2024.2 / `sinceBuild` 242; LSP4IJ 0.20.1.)*

When all boxes are checked, tick **Stage 4.A** in [`index.md`](./index.md) and proceed to [`4B-build-wiring.md`](./4B-build-wiring.md).
