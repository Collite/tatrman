// SPDX-License-Identifier: Apache-2.0
// Root build for the modeler-kotlin Gradle build. This coexists with the pnpm
// TypeScript workspace: the two builds share no artifacts, but both read the
// canonical grammar at packages/grammar/src/TTR.g4. See docs/grammar-master/.

plugins {
    // SV-P1 S4 — declare vanniktech at the root with `apply false` so the shared
    // `MavenCentralBuildService` registers once for the whole build. Without this,
    // configuring more than one Central-publishing module together (e.g. a
    // repo-wide `publishToMavenCentral`) fails to create `prepareMavenCentralPublishing`.
    // Kotlin is declared here `apply false` too because vanniktech reads the Kotlin
    // plugin classes to detect the version and requires them on the root classpath
    // (per the plugin's own error message). Each module applies both for real; the
    // root only puts them on the classpath.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.maven.publish.vanniktech) apply false
}

// AG-14 (2026-10-02) — ttrp-* publish in the `grammar` bundle, but `ttrp-emit` `api`s ttr-translator, which
// releases on its OWN line (`translator/v*`). Gradle writes a project dependency's own `version` into the POM,
// so a grammar publish would otherwise name `ttr-translator:<grammar version>` — a version nothing cut (the
// 0.10.3 lockstep trap). `-PtranslatorVersion=<x.y.z>` (publish.yml passes the latest translator tag on
// grammar tags only, after checking the translator sources still equal it) gives the two translator modules
// their PUBLISHED version for that build, so the POMs name what consumers can resolve.
val translatorModules = setOf("ttr-translator", "ttr-plan-proto")
val pinnedTranslatorVersion = findProperty("translatorVersion") as String?

allprojects {
    group = "org.tatrman"
    version =
        if (pinnedTranslatorVersion != null && name in translatorModules) {
            pinnedTranslatorVersion
        } else {
            (findProperty("version") as String?).takeUnless { it == "unspecified" } ?: "0.0.1-LOCAL"
        }
}
