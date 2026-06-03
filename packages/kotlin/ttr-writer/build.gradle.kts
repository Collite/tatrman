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
    // Transitively re-exports the model types so writer consumers see Definition etc.
    api(project(":packages:kotlin:ttr-parser"))

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.mockk)
}

ktlint {
    filter {
        exclude("**/generated/**")
        exclude { it.file.path.contains("/generated-src/antlr/") }
    }
}
