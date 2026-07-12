// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics

import org.tatrman.ttr.parser.loader.ParseResult
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttr.parser.model.WorldDef

/**
 * v4.1 world-model warning validators (ttr-metadata M0). Warning-severity only —
 * the hard-error twins (staging conflict) live in M2's `WorldResolver` (MD5). A
 * standalone entry point (NOT part of [Validator], and excluded from the
 * conformance dump) mirroring the TS `validateWorldDocument`. The `world/…` codes are
 * the TTR-M editor surface and a cross-target contract; the library never mints
 * TTRP-* ids.
 */
data class WorldDiagnostic(
    /** One of `world/duplicate-staging`, `world/hosts-unknown-package`, `world/wrong-model-kind`. */
    val code: String,
    val message: String,
    val severity: String, // always "warning" here
    val source: SourceLocation,
)

object WorldValidator {
    /**
     * @param result        parsed document
     * @param knownPackages package names known to the project (for `hosts:` checks);
     *                       null → `hosts:` entries are not flagged.
     */
    fun validateWorldDocument(
        result: ParseResult,
        knownPackages: Set<String>? = null,
    ): List<WorldDiagnostic> {
        val diagnostics = mutableListOf<WorldDiagnostic>()
        val isWorldFile = result.modelDirective?.modelCode == "world"

        for (def in result.definitions) {
            if (def is WorldDef) {
                if (!isWorldFile) {
                    diagnostics +=
                        WorldDiagnostic(
                            "world/wrong-model-kind",
                            "'def world ${def.name}' is only valid in a 'model world' file",
                            "warning",
                            def.source,
                        )
                }
                validateWorld(def, knownPackages, diagnostics)
            } else if (isWorldFile) {
                diagnostics +=
                    WorldDiagnostic(
                        "world/wrong-model-kind",
                        "'def ${kindOf(def)} ${def.name}' is not valid in a 'model world' file",
                        "warning",
                        def.source,
                    )
            }
        }
        return diagnostics
    }

    private fun validateWorld(
        world: WorldDef,
        knownPackages: Set<String>?,
        out: MutableList<WorldDiagnostic>,
    ) {
        val staging = world.storages.filter { it.staging }
        if (staging.size > 1) {
            out +=
                WorldDiagnostic(
                    "world/duplicate-staging",
                    "world '${world.name}' declares ${staging.size} staging storages " +
                        "(${staging.joinToString(", ") { it.name }}); exactly one is allowed",
                    "warning",
                    world.source,
                )
        }

        if (knownPackages != null) {
            for (storage in world.storages) {
                for (pkg in storage.hosts) {
                    if (pkg !in knownPackages) {
                        out +=
                            WorldDiagnostic(
                                "world/hosts-unknown-package",
                                "storage '${storage.name}' hosts unknown package '$pkg'",
                                "warning",
                                storage.source,
                            )
                    }
                }
            }
        }
    }
}
