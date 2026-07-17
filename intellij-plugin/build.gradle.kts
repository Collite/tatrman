// SPDX-License-Identifier: Apache-2.0
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel

// TTR Modeler — IntelliJ IDEA plugin. A thin host shim that launches the shared
// @modeler/lsp server over stdio via LSP4IJ. The plugin owns no language logic.
// Design: docs/features/intellij/design/architecture.md + contracts.md.

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // IDEA Community baseline (IJ-Q2 = 2024.2). The plugin is product-agnostic
        // editor tooling, so Community is the build target; Ultimate is smoke-only.
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))

        // LSP4IJ — the LSP client that surfaces standard LSP features as native
        // IDE actions. Declared as a Marketplace dependency (IJ1).
        plugins("com.redhat.devtools.lsp4ij:${providers.gradleProperty("lsp4ijVersion").get()}")

        // TextMate bundle support — used to color .ttrm/.ttrg via the shared grammars.
        bundledPlugin("org.jetbrains.plugins.textmate")

        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    // No GUI forms or @NotNull bytecode instrumentation in this thin launcher,
    // so skip instrumentCode (also dodges its toolchain-path bug under Gradle 9).
    instrumentCode = false

    pluginConfiguration {
        // id/name/vendor/depends live in META-INF/plugin.xml (contracts §2).
        // patchPluginXml fails if id or name are declared in both places, so only
        // version + compatibility are injected from here.
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            // IJ-Q2: 2024.2 baseline. No untilBuild — track the latest platforms.
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }

    // JetBrains Marketplace publishing. Only `publishPlugin` / `signPlugin` read
    // these; normal builds (`buildPlugin`, used by `just _build-intellij`) don't,
    // and the env-var providers are lazy — so an unconfigured local/CI build is
    // unaffected. CI wires the env vars in release-extensions.yml, gated on a
    // `-RELEASE` tag + the token secret. First-time setup: PUBLISHING.md
    // § IntelliJ Marketplace (create the listing manually, then add the secrets).
    signing {
        // Marketplace requires signed uploads. Values are the secret CONTENTS
        // (chain/key are PEM text), passed via env in CI.
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // Stable channel. (A pre-release/EAP channel could later be derived from a
        // version suffix; today every -RELEASE tag is a stable publish.)
        channels = listOf("default")
    }

    pluginVerification {
        // IJ-Q3: the plugin ID `org.tatrman.modeler.intellij` deliberately names
        // the IntelliJ host variant (contracts §2). Mute the verifier's
        // template-word heuristic rather than deviate from the pinned ID.
        freeArgs = listOf("-mute", "TemplateWordInPluginId")

        // Fail on real breakage, but treat INTERNAL_API_USAGES as advisory:
        // locating the plugin's own home (to launch `node server-stdio.mjs`)
        // touches plugin-path APIs the platform marks @ApiStatus.Internal, and
        // every release tightens which ones. Only the 2026.2 EAP flags the
        // current call; the whole 242 → 261 supported range is clean.
        failureLevel = listOf(
            FailureLevel.COMPATIBILITY_PROBLEMS,
            FailureLevel.INVALID_PLUGIN,
            FailureLevel.NON_EXTENDABLE_API_USAGES,
            FailureLevel.OVERRIDE_ONLY_API_USAGES,
            FailureLevel.MISSING_DEPENDENCIES,
        )
    }
}

tasks.test {
    useJUnitPlatform()
}

// --- Stage 4.B: LSP server bundle + TextMate grammars ----------------------
//
// The runtime files ship UNPACKED in the plugin home, NOT inside the jar: `node`
// executes server-stdio.mjs from disk, and IntelliJ's TextMate engine reads the
// grammars from a directory. So they are (a) copied/verified here, (b) excluded
// from the jar, and (c) placed into the plugin home by prepareSandbox.
//
// The inlined server bundle (server/server-stdio.mjs + server/stock/*.ttrm) is
// produced by `just intellij` (esbuild). This task only pulls in the two
// generated grammars and fails fast if the server bundle is absent.

val pluginResources = layout.projectDirectory.dir("src/main/resources")
val serverBundleDir = pluginResources.dir("server")
val textmateDir = pluginResources.dir("textmate")
// intellij-plugin is a standalone Gradle build (rootProject == this), so the repo
// root — where the grammars live — is the parent directory.
val repoRoot = layout.projectDirectory.dir("..")

val copyLspBundle by tasks.registering(Copy::class) {
    val grammars = repoRoot.dir("packages/vscode-ext/syntaxes")
    // The committed VS Code-style bundle manifest that ties the grammars to the
    // .ttrm/.ttrg extensions for IntelliJ's TextMate engine.
    val bundleManifest = layout.projectDirectory.dir("src/main/textmate-bundle")
    from(grammars) {
        include("ttrm.tmLanguage.json", "ttrg.tmLanguage.json")
    }
    from(bundleManifest) {
        include("package.json")
    }
    // Output is scoped to textmate/ only — copying into the whole resources dir
    // overlaps patchPluginXml's input (META-INF/plugin.xml) and trips Gradle 9's
    // implicit-dependency validation.
    into(textmateDir)

    doFirst {
        require(serverBundleDir.file("server-stdio.mjs").asFile.exists()) {
            "Inlined LSP server bundle missing. Run `just intellij` before building the plugin."
        }
    }
}

tasks.named<Copy>("processResources") {
    dependsOn(copyLspBundle)
    // The server bundle + grammars are shipped unpacked (below), not jarred.
    exclude("server/**", "textmate/**")
}

tasks.withType<PrepareSandboxTask>().configureEach {
    dependsOn(copyLspBundle)
    from(serverBundleDir) { into(pluginName.map { "$it/server" }) }
    from(textmateDir) { into(pluginName.map { "$it/textmate" }) }
}
