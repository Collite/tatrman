// ttr-translator — the TTR-P/kantheon translation core (island ↔ RelNode ↔ SQL /
// plan.v1). Extracted whole from kantheon shared/libs/kotlin/query-translator at
// f2e2efb (2026-07-06); root package renamed shared.translator → org.tatrman.translator
// (TR-2). No behavioral change rides the move (TR-7) — package names + build wiring only.

plugins {
    base
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `java-library`
    `java-test-fixtures` // InMemoryModelHandle ships to consumers via the SPI (contracts §3)
    `maven-publish`
}

kotlin { jvmToolchain(21) }

tasks.test {
    useJUnitPlatform()
    // Calcite freezes its default literal charset (ISO-8859-1) at class-load. The
    // library promotes it to UTF-8 at its entry points, but specs touching Calcite
    // directly can load it first in arbitrary order — pin the charset for the test
    // JVM to keep Unicode-literal coverage deterministic (carried from the source lib).
    systemProperty("calcite.default.charset", "UTF-8")
    systemProperty("calcite.default.nationalcharset", "UTF-8")
    systemProperty("calcite.default.collation.name", "UTF-8\$en_US")
}

dependencies {
    api(project(":packages:kotlin:ttr-plan-proto")) // was api(project(":shared:proto")) in kantheon
    api(libs.calcite.core)
    implementation(libs.slf4j.api)
    implementation(libs.protobuf.java.util)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.mockk)

    // InMemoryModelHandle (the ModelHandle SPI test double) ships to consumers via
    // java-test-fixtures (contracts §3). Its public API references plan.v1 types, so
    // re-export the proto on the test-fixtures api classpath.
    testFixturesApi(project(":packages:kotlin:ttr-plan-proto"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "ttr-translator"
            from(components["java"])
            pom {
                name.set("TTR Translator")
                description.set(
                    "The TTR-P translation core: island ↔ RelNode ↔ SQL / plan.v1 " +
                        "(Calcite-backed). Extracted from kantheon query-translator.",
                )
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
