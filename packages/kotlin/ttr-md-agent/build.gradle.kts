// SPDX-License-Identifier: Apache-2.0
plugins {
    base
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization) // reuses ttr-md-resolver's @Serializable DTOs on the wire
    alias(libs.plugins.ktlint)
    application
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "org.tatrman.ttr.md.agent.MainKt"
}

// MD dot-path S7-A — the agent service (MDS6: a thin MCP shell). All language intelligence is in
// ttr-md-resolver; this module only adapts MCP ⇄ resolver DTOs (md_resolve / md_explain /
// md_list_members) over the Kotlin MCP SDK's streamable-HTTP transport. Non-published (app module,
// no maven-publish block) — it is a deployable, not a library. It depends on the resolver + the
// model loader (ttr-parser/ttr-semantics), never the other way round.
dependencies {
    implementation(project(":packages:kotlin:ttr-md-resolver")) // resolver + @Serializable DTOs
    implementation(project(":packages:kotlin:ttr-semantics")) // MdModel / MdBindings from parsed defs
    implementation(project(":packages:kotlin:ttr-parser")) // TtrLoader for the offline .ttrm load
    implementation(libs.mcp.kotlin.sdk.server)
    implementation(libs.ktor.server.cio) // the SDK does not pull an engine transitively
    implementation(libs.kotlinx.ser.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.mcp.kotlin.sdk.client) // drive the tools over a real socket in the E2E
    testImplementation(libs.ktor.client.cio) // brings the SSE client plugin the MCP transport needs
    testImplementation(libs.kotlinx.coroutines.test)
    // InMemoryMemberSnapshot (connected-mode seed) + the shared sales `.ttrm` model fixture.
    testImplementation(testFixtures(project(":packages:kotlin:ttr-md-resolver")))
    testImplementation(testFixtures(project(":packages:kotlin:ttr-semantics")))
}

ktlint {
    filter {
        exclude("**/generated/**")
    }
}
