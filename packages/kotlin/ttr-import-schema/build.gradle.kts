// SPDX-License-Identifier: Apache-2.0
plugins {
    base
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    `java-library`
    application
}

kotlin {
    jvmToolchain(21)
}

// Unit tier — the PURE deterministic core (IntrospectedCatalog → TTR-M bytes, naming,
// conventions). No Docker, no live DB; runs everywhere. GI-2 (same input ⇒ same bytes) is
// proven here. The JDBC/probe edge is exercised by the componentTest tier below.
tasks.test {
    useJUnitPlatform()
    systemProperty("updateGolden", System.getProperty("updateGolden") ?: "false")
}

// componentTest tier — real-DB Testcontainers specs (introspection + probes over Postgres/
// MSSQL). Separate source set so the unit tier stays fast and Docker-free. Postgres is native
// multi-arch; MSSQL is amd64-only (emulated off-CI) and its specs self-gate — see CiOnly usage.
sourceSets {
    create("componentTest") {
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }
}
val componentTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations["testImplementation"])
}
configurations["componentTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

application {
    applicationName = "ttr"
    mainClass = "org.tatrman.ttr.importschema.cli.MainKt"
}

dependencies {
    implementation(project(":packages:kotlin:ttr-parser"))
    implementation(project(":packages:kotlin:ttr-writer"))
    implementation(project(":packages:kotlin:ttr-metadata"))
    implementation(libs.clikt)
    implementation(libs.kotlinx.ser.json)
    implementation(libs.snakeyaml)
    implementation(libs.slf4j.api)
    // JDBC drivers + pool for the introspection reader and probe engine.
    implementation(libs.hikaricp)
    runtimeOnly(libs.mssql.jdbc)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.bundles.kotest)

    componentTestImplementation(libs.bundles.kotest)
    componentTestImplementation(libs.testcontainers)
    componentTestImplementation(libs.testcontainers.postgresql)
    componentTestImplementation(libs.testcontainers.mssqlserver)
    componentTestImplementation(libs.mssql.jdbc)
    componentTestImplementation(libs.postgresql)
}

// NOT attached to `check`/`build` — Testcontainers (Docker) stays out of the default build and the
// umbrella `./gradlew build` CI job. Run explicitly (`:…:componentTest`) or via the dedicated CI job.
@Suppress("unused")
val componentTest by tasks.registering(Test::class) {
    description = "Real-dependency (Testcontainers) tier — the JDBC introspection + probe edge."
    group = "verification"
    testClassesDirs = sourceSets["componentTest"].output.classesDirs
    classpath = sourceSets["componentTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
    // MSSQL specs are amd64-only; -DmssqlLocal forces an emulated local run (else CI-only).
    systemProperty("mssqlLocal", System.getProperty("mssqlLocal") ?: "false")
}

ktlint {
    filter {
        exclude("**/generated/**")
    }
}
