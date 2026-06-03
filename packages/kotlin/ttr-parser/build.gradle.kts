plugins {
    base
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `java-library`
    `maven-publish`
    antlr
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

// The canonical grammar lives in the pnpm workspace; the Kotlin build reads it
// directly — no copy, no sync, no header rewriting (see docs/grammar-master/).
val canonicalGrammar = file("../../grammar/src/TTR.g4")

sourceSets["main"].antlr.setSrcDirs(listOf(canonicalGrammar.parentFile))

val generatedPackage = "org.tatrman.ttr.parser.generated"

tasks.named<org.gradle.api.plugins.antlr.AntlrTask>("generateGrammarSource") {
    source = fileTree(canonicalGrammar.parentFile) { include("TTR.g4") }
    arguments = arguments + listOf("-visitor", "-long-messages", "-package", generatedPackage)
    // NOTE: the ANTLR plugin emits the generated .java FILES flat into
    // build/generated-src/antlr/main/ regardless of `-package`; the files still
    // declare `package org.tatrman.ttr.parser.generated`, so they compile to the
    // correct package. Do NOT override `outputDirectory` to nest them to the
    // package path — on a clean rebuild ANTLR regenerates flat while the nested
    // copy lingers, producing duplicate-class compile errors. The flat source
    // layout is purely cosmetic and harmless.
}

dependencies {
    antlr(libs.antlr.tool)
    api(libs.antlr.runtime)
    implementation(libs.slf4j.api)
    implementation(libs.kotlinx.ser.json)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.reflect)
}

tasks.named("compileKotlin") { dependsOn("generateGrammarSource") }
tasks.named("compileJava") { dependsOn("generateGrammarSource") }

ktlint {
    filter {
        exclude("**/generated/**")
        exclude { it.file.path.contains("/generated-src/antlr/") }
    }
}
