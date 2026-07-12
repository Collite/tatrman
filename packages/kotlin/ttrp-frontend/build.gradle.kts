// SPDX-License-Identifier: Apache-2.0
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
    // Golden AST snapshots: run with `-DupdateSnapshots=true` to (re)write the
    // committed snapshots under src/test/resources/golden/snapshots/.
    systemProperty("updateSnapshots", System.getProperty("updateSnapshots") ?: "false")
}

// The canonical TTR-P grammar lives in the pnpm workspace beside TTR.g4; the
// Kotlin build reads it directly — no copy, no sync (G-b: TTR-P is Kotlin-only;
// ANTLR Gradle plugin is the ONLY generation path — no antlr-ng/TS target, no
// TextMate grammar — see architecture §6, which supersedes plan.md's stale
// "antlr-ng generation task" wording).
val canonicalGrammar = file("../../grammar/src/TTRP.g4")

sourceSets["main"].antlr.setSrcDirs(listOf(canonicalGrammar.parentFile))

val generatedPackage = "org.tatrman.ttrp.parser.generated"

tasks.named<org.gradle.api.plugins.antlr.AntlrTask>("generateGrammarSource") {
    // TTRP.g4 (canonical) + the fragment dialects TTRSql.g4 / TTRPandas.g4 (P6) + TTRB.g4
    // (P7). All share ONE generated package (no class-name collision — ANTLR prefixes by
    // grammar name: TTRP*, TTRSql*, TTRPandas*, TTRB*). TTR.g4 belongs to ttr-parser, excluded.
    source = fileTree(canonicalGrammar.parentFile) { include("TTRP.g4", "TTRSql.g4", "TTRPandas.g4", "TTRB.g4") }
    arguments = arguments + listOf("-visitor", "-long-messages", "-package", generatedPackage)
    // NOTE (same footgun as ttr-parser): the ANTLR plugin emits generated .java
    // FILES flat into build/generated-src/antlr/main/ regardless of `-package`;
    // the files still declare `package org.tatrman.ttrp.parser.generated`, so they
    // compile correctly. Do NOT override `outputDirectory` to nest them — on a
    // clean rebuild ANTLR regenerates flat while the nested copy lingers, causing
    // duplicate-class compile errors.
}

// The ANTLR plugin makes the `antlr` configuration (the code-gen tool, which
// transitively pulls ST4 + antlr-runtime3) extend `api`, leaking the tool into
// the published POM as a compile dependency. Consumers only need the runtime
// (`api(libs.antlr.runtime)` below), so strip `antlr` from api's extendsFrom.
// Generation still works — the AntlrTask uses the `antlr` configuration directly.
configurations.api {
    setExtendsFrom(extendsFrom.filterNot { it.name == "antlr" })
}

dependencies {
    antlr(libs.antlr.tool)
    api(libs.antlr.runtime)

    // Stage 1.3: all model/world reading goes THROUGH ttr-metadata (D-g, offline);
    // ttrp-frontend never parses `.ttrm` directly. TOML for the `[ttrp]` manifest (S5).
    implementation(project(":packages:kotlin:ttr-metadata"))
    implementation(libs.tomlj)

    // kotlinx-serialization is TEST-ONLY (the deterministic AST snapshot dumper) —
    // kept off the published runtime classpath, same as ttr-parser's conformance dump.
    testImplementation(libs.kotlinx.ser.json)
    testImplementation(libs.bundles.kotest)
    // Shared world/model fixture project (contracts §8): consume, never duplicate.
    testImplementation(testFixtures(project(":packages:kotlin:ttr-metadata")))
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
                name.set("TTR-P Compiler Front-Half")
                description.set("parse to resolve to typecheck for TTR-P (.ttrp)")
                url.set("https://github.com/Collite/tatrman")
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
                    connection.set("scm:git:https://github.com/Collite/tatrman.git")
                    developerConnection.set("scm:git:git@github.com:Collite/tatrman.git")
                    url.set("https://github.com/Collite/tatrman")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Collite/tatrman")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
