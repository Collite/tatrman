// SPDX-License-Identifier: Apache-2.0
plugins {
    base
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    `java-library`
    `maven-publish`
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    // The archive is the content-addressed transport of canon; SnapshotArchiveStorage
    // implements ttr-metadata's ModelStorage SPI (contracts §2), so ttr-metadata is `api`.
    api(project(":packages:kotlin:ttr-metadata"))
    // Deterministic tar (pinned) + zstd. Versions fixed in the catalog for byte-determinism
    // (plan.md risk #1) — the golden archive hash depends on the compressor version.
    implementation(libs.apache.commons.compress.snapshot)
    implementation(libs.zstd.jni)
    // snapshot.json codec.
    implementation(libs.kotlinx.ser.json)

    testImplementation(libs.bundles.kotest)
    // Pack/round-trip the shared erp/world fixture tree; compare loads against LocalFsStorage.
    testImplementation(testFixtures(project(":packages:kotlin:ttr-metadata")))
}

ktlint {
    filter {
        exclude("**/generated/**")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("TTR Snapshot")
                description.set("Deterministic content-addressed snapshot archives + cache (the ② seam transport)")
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
