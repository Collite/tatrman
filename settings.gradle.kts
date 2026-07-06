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
include(":packages:kotlin:ttr-metadata")
include(":packages:kotlin:ttr-metadata-git")
include(":packages:kotlin:ttr-designer-server")

// ttr-translator extraction arc (docs/ttr-translator/): wire formats + translation
// core, extracted from kantheon. The :ttr-translator module lands in Stage A2.
include(":packages:kotlin:ttr-plan-proto")

// TTR-P toolchain (module cut per docs/ttr-p/implementation/v1/plan.md Phase 0)
include(":packages:kotlin:ttrp-frontend")
include(":packages:kotlin:ttrp-graph")
include(":packages:kotlin:ttrp-emit")
include(":packages:kotlin:ttrp-lsp")
include(":packages:kotlin:ttrp-cli")
include(":packages:kotlin:ttrp-conform")
