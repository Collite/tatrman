rootProject.name = "tatrman"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":packages:kotlin:ttr-parser")
include(":packages:kotlin:ttr-writer")
include(":packages:kotlin:ttr-semantics")
