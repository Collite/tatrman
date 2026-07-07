package org.tatrman.ttrp.bundle

import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Path

/**
 * Produces the placement variants a conform run compares (T3.4.4). Each variant builds its own
 * bundle from the same source through [BundleAssembler]; islands/waves/transfers may differ,
 * results must not.
 *
 * **v1 scope (recorded for review):** the authored variant (accounts@PG, sales+crunch@Polars) is
 * built. A true PG-heavy variant B (crunch retargeted to the PG engine) requires **SQL Join emit**,
 * which is deferred (Stage 3.1: `plan.v1` JoinType stops at FULL, and equi-join column resolution
 * through the translator is not yet threaded — no v1 hero SQL island has a join). Until that lands,
 * the hero's join-bearing crunch is Polars-only, so the two variants here are deterministic rebuilds
 * of the authored placement — enough to exercise the invoke→collect→seven-point harness end-to-end.
 * The full PG↔Polars identical-results proof is gated on SQL Join emit (see progress-phase-03.md).
 */
object PlacementVariants {
    fun build(
        source: String,
        fileName: String,
        pipelineManifest: TtrpManifest,
        modelsRoot: Path,
        outDir: Path,
        toolchainVersion: String = "0.0.0-dev",
    ): Map<String, Path> {
        val assembler = BundleAssembler(toolchainVersion)
        return linkedMapOf("authored" to "authored", "authored-b" to "authored-b").mapValues { (variant, _) ->
            assembler.build(source, fileName, pipelineManifest, modelsRoot, outDir.resolve(variant)).dir
        }
    }
}
