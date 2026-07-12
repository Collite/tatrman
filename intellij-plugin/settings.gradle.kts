// SPDX-License-Identifier: Apache-2.0
rootProject.name = "intellij-plugin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Auto-provisions the JVM 17 toolchain when it is not already installed
    // (the host may only carry a newer JDK); resolves toolchains via foojay.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
