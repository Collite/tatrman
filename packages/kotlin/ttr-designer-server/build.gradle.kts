plugins {
    base
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    application
}

kotlin { jvmToolchain(21) }
tasks.test { useJUnitPlatform() }

application {
    mainClass = "org.tatrman.ttr.designer.server.ApplicationKt"
}

dependencies {
    implementation(project(":packages:kotlin:ttr-metadata")) // NO ttr-metadata-git (MD3)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.kotlinx.ser.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":packages:kotlin:ttr-metadata")))
}
