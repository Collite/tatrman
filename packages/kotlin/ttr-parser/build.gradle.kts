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

// The ANTLR plugin makes the `antlr` configuration (the code-generation tool,
// which transitively pulls ST4 + antlr-runtime3) extend `api`, leaking the tool
// into the published POM as a compile dependency. Consumers only need the
// runtime (`api(libs.antlr.runtime)` below), so strip `antlr` from api's
// extendsFrom. Generation still works — the AntlrTask uses the `antlr`
// configuration directly, not `api`. See contracts.md §1.
configurations.api {
    setExtendsFrom(extendsFrom.filterNot { it.name == "antlr" })
}

dependencies {
    antlr(libs.antlr.tool)
    api(libs.antlr.runtime)
    implementation(libs.slf4j.api)

    // kotlinx-serialization is only used by the test-only conformance dumper
    // (src/test/.../conformance/ConformanceDump.kt) — keep it off the published
    // runtime classpath. See contracts.md §1.
    testImplementation(libs.kotlinx.ser.json)
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("TTR Parser")
                description.set("ANTLR-generated parser + typed AST for the TTR (Tatrman) modelling DSL.")
                url.set("https://github.com/Collite/modeler")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("Bora Perusic")
                        email.set("boraperusic@gmail.com")
                        organization.set("Collite")
                        organizationUrl.set("https://github.com/Collite")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/Collite/modeler.git")
                    developerConnection.set("scm:git:git@github.com:Collite/modeler.git")
                    url.set("https://github.com/Collite/modeler")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Collite/modeler")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
