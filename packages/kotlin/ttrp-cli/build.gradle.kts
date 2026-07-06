plugins {
    base
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `java-library`
    `maven-publish`
    application
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

// The thin `ttrp check` front-half dispatch (S2 — the full build/run/explain/conform
// CLI, and its framework choice, land in P3). Hand-rolled arg dispatch, no framework.
application {
    mainClass = "org.tatrman.ttrp.cli.MainKt"
}

dependencies {
    implementation(project(":packages:kotlin:ttrp-frontend"))
    implementation(project(":packages:kotlin:ttrp-graph"))
    implementation(project(":packages:kotlin:ttr-metadata"))
    testImplementation(libs.bundles.kotest)
    // Shared world/model fixture project (contracts §8) for the CLI component test.
    testImplementation(testFixtures(project(":packages:kotlin:ttr-metadata")))
}

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
                name.set("TTR-P CLI")
                description.set("the ttrp binary (S2): build/run/explain/conform")
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
