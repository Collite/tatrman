plugins {
    base
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `java-library`
    `java-test-fixtures` // M2 — shared fixture home for ttrp-frontend (contracts §8)
    `maven-publish`
    alias(libs.plugins.maven.publish.vanniktech)
}

kotlin { jvmToolchain(21) }
tasks.test { useJUnitPlatform() }

dependencies {
    // Re-exported: consumers get one coherent ttr-* set (contracts §1).
    api(project(":packages:kotlin:ttr-parser"))
    api(project(":packages:kotlin:ttr-writer"))
    api(project(":packages:kotlin:ttr-semantics"))
    implementation(libs.jgrapht.core)
    implementation(libs.slf4j.api)
    implementation(libs.kotlinx.coroutines.core) // MetadataRefresher (M1.2)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.mockk)

    // Fixture helper code (MetadataFixtures) references library + parser types.
    testFixturesApi(project(":packages:kotlin:ttr-parser"))
    testFixturesImplementation(libs.slf4j.api)
}

// MD3 / architecture §2.1: the core stays off heavy classpaths. Gate, not convention.
val bannedDependencyGroups =
    setOf(
        "io.ktor",
        "io.grpc",
        "io.opentelemetry",
        "org.eclipse.jgit",
        // commons-compress rides the -git artifact only.
        "org.apache.commons",
        "com.google.protobuf",
    )
val dependencyRules =
    tasks.register("dependencyRules") {
        val runtime = configurations.runtimeClasspath
        doLast {
            val offenders =
                runtime
                    .get()
                    .resolvedConfiguration.resolvedArtifacts
                    .map { it.moduleVersion.id.group }
                    .filter { g -> bannedDependencyGroups.any { g == it || g.startsWith("$it.") } }
                    .distinct()
            check(offenders.isEmpty()) {
                "Banned groups on ttr-metadata runtimeClasspath: $offenders (MD3 / architecture §2.1)"
            }
        }
    }
tasks.named("check") { dependsOn(dependencyRules) }

// SV-P1 S4 — Maven Central (Central Portal) is the PUBLIC lane (RO-17). vanniktech
// owns the publication (adds the sources + javadoc jars Central requires) and the
// Central target; the GH Packages block below stays as the pre-release staging lane.
mavenPublishing {
    publishToMavenCentral()
    // Central requires signatures and the Central CI lane supplies the key
    // (ORG_GRADLE_PROJECT_signingInMemoryKey). Local builds + the GH Packages
    // staging lane don't sign — gate signAllPublications() on the key's presence
    // so it doesn't hard-fail those (it fails a non-SNAPSHOT version when unkeyed).
    if (providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent ||
        providers.gradleProperty("signingInMemoryKey").isPresent
    ) {
        signAllPublications()
    }
    coordinates("org.tatrman", "ttr-metadata", version.toString())
    pom {
        name.set("TTR Metadata")
        description.set("TTR model graph, queries and world resolution (extracted from the kantheon metadata service).")
        inceptionYear.set("2025")
        url.set("https://github.com/Collite/tatrman")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("collite")
                name.set("Collite")
                url.set("https://github.com/Collite")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/Collite/tatrman.git")
            developerConnection.set("scm:git:git@github.com:Collite/tatrman.git")
            url.set("https://github.com/Collite/tatrman")
        }
    }
}

publishing {
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
