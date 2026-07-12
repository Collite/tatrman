// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Path

/**
 * Produces the placement variants a conform run compares (T3.4.4 / S3.5). Each variant builds its
 * own bundle from the same source through [BundleAssembler]; islands/waves/transfers differ,
 * results must not.
 *
 *  - **`authored`** — the hero as written: `accounts`@PG fragment → Arrow transfer → `sales`+`crunch`
 *    on Polars.
 *  - **`crunch-pg`** — the whole `crunch` container retargeted to `erp_pg` via the build-API target
 *    override (NOT by editing the source; S3.5 T3.5.5). The join/aggregate/branch run server-side in
 *    Postgres (a decomposed ADBC island), `sales` is CSV-ingested into a temp table, and `accounts`
 *    is acc_prep's fragment SQL inlined — a genuinely different engine placement.
 *
 * Both read the same `erp.accounts` (via acc_prep) and the same `sales_2026.csv`, so identical
 * results are the A4 claim under test.
 */
object PlacementVariants {
    /** container label → engine instance for each variant's target override (empty = authored). */
    private val VARIANTS: Map<String, Map<String, String>> =
        linkedMapOf("authored" to emptyMap(), "crunch-pg" to mapOf("crunch" to "erp_pg"))

    fun build(
        source: String,
        fileName: String,
        pipelineManifest: TtrpManifest,
        modelsRoot: Path,
        outDir: Path,
        toolchainVersion: String = "0.0.0-dev",
    ): Map<String, Path> {
        val assembler = BundleAssembler(toolchainVersion)
        return VARIANTS.mapValues { (variant, overrides) ->
            assembler.build(source, fileName, pipelineManifest, modelsRoot, outDir.resolve(variant), overrides).dir
        }
    }
}
