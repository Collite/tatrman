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

allprojects {
    group = "org.tatrman"
    version = (findProperty("version") as String?).takeUnless { it == "unspecified" } ?: "0.0.1-LOCAL"
}
