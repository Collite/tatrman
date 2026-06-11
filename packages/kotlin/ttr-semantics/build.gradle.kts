plugins {
    base
    alias(libs.plugins.kotlin.jvm)
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
    // Transitively re-exports the model types so semantics consumers see
    // Definition, SourceLocation, DiagnosticCode etc. without a second dep.
    api(project(":packages:kotlin:ttr-parser"))
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.mockk)
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
                name.set("TTR Semantics")
                description.set("Resolver, symbol table, validator, and stock vocab for the TTR (Tatrman) DSL.")
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
