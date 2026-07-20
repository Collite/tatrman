// SPDX-License-Identifier: Apache-2.0
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
// MD dot-path resolver arc (docs/features/md/dot-path/, phase S2): the §2–§3 constraint
// resolver over MdModel. Standalone — must NOT depend on ttrp-frontend (S3 wires it the other way).
include(":packages:kotlin:ttr-md-resolver")
include(":packages:kotlin:ttr-metadata")
include(":packages:kotlin:ttr-metadata-git")
include(":packages:kotlin:ttr-designer-server")
// MD dot-path agent service (docs/features/md/dot-path/, phase S7, MDS6): a thin MCP shell
// over ttr-md-resolver — md_resolve / md_explain / md_list_members. Non-published app module.
include(":packages:kotlin:ttr-md-agent")

// ttr-translator extraction arc (docs/ttr-translator/): wire formats + translation
// core, extracted from kantheon shared/libs/kotlin/query-translator.
include(":packages:kotlin:ttr-plan-proto")
include(":packages:kotlin:ttr-translator")

// TTR-P toolchain (module cut per docs/ttr-p/implementation/v1/plan.md Phase 0)
include(":packages:kotlin:ttrp-frontend")
include(":packages:kotlin:ttrp-graph")
include(":packages:kotlin:ttrp-emit")
include(":packages:kotlin:ttrp-lsp")
include(":packages:kotlin:ttrp-cli")
include(":packages:kotlin:ttrp-conform")

// `ttr import-schema` — the standard's brownfield front door (STRAT-8/RO-26; SV-P4·S3/S4).
// Engine (introspection → db mirror → conventions → probes → er first cut → review checklist)
// + a thin `ttr import-schema` clikt CLI. Emits TTR-M through the canonical ttr-writer.
include(":packages:kotlin:ttr-import-schema")
