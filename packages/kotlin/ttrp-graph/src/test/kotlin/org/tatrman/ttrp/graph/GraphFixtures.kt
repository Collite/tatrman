package org.tatrman.ttrp.graph

import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.graph.build.GraphBuilder
import org.tatrman.ttrp.graph.model.TtrpGraph
import org.tatrman.ttrp.graph.validate.StructureValidator
import org.tatrman.ttrp.project.TtrpManifest
import org.tatrman.ttrp.resolve.TtrpChecker
import java.nio.file.Files
import java.nio.file.Path

/**
 * Test driver for Stage 2.1: runs the Phase-1 front-half (TtrpChecker over the SHARED
 * erp-project world, contracts §8) then GraphBuilder + StructureValidator, exposing
 * frontend errors and graph (build + validate) diagnostics separately so specs can
 * assert an exact structural-diagnostic set with no frontend noise.
 */
object GraphFixtures {
    val root: Path by lazy {
        val rel = Path.of("packages/kotlin/ttrp-graph/src/test/resources/fixtures/graph")
        var dir: Path? = Path.of("").toAbsolutePath()
        while (dir != null) {
            val candidate = dir.resolve(rel)
            if (Files.isDirectory(candidate)) return@lazy candidate
            val local = dir.resolve("src/test/resources/fixtures/graph")
            if (Files.isDirectory(local)) return@lazy local
            dir = dir.parent
        }
        error("could not locate graph fixtures from ${Path.of("").toAbsolutePath()}")
    }

    fun program(name: String): String = Files.readString(root.resolve(name))

    private fun checker(): TtrpChecker =
        TtrpChecker(TtrpManifest(world = "acme.worlds.dev", manifestDir = root), MetadataFixtures.erpModelsRoot())

    data class GraphResult(
        val graph: TtrpGraph,
        val frontendErrors: List<TtrpDiagnostic>,
        val graphDiagnostics: List<TtrpDiagnostic>,
    ) {
        val graphErrorIds: Set<String>
            get() = errorIds(graphDiagnostics)

        /** All ERROR ids across front-half + graph (FF is a Phase-1 reject; the rest are Stage-2.1). */
        val allErrorIds: Set<String>
            get() = errorIds(frontendErrors + graphDiagnostics)

        private fun errorIds(ds: List<TtrpDiagnostic>): Set<String> =
            ds.filter { it.severity == Severity.ERROR }.map { it.id.id }.toSet()
    }

    /** Full pipeline over [source]: front-half → build → validate. */
    fun build(source: String): GraphResult {
        val report = checker().check(source, "graph.ttrp")
        val built = GraphBuilder().build(report)
        val validated = StructureValidator().validate(built.graph)
        return GraphResult(
            graph = built.graph,
            frontendErrors = report.errors,
            graphDiagnostics = built.diagnostics + validated,
        )
    }

    fun buildFixture(name: String): GraphResult = build(program(name))
}
