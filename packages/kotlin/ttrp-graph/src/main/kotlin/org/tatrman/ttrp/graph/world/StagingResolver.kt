package org.tatrman.ttrp.graph.world

import org.tatrman.ttr.metadata.world.ResolvedStorage
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.graph.capability.BoundEngine
import org.tatrman.ttrp.graph.capability.BoundWorld
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.TtrpGraph

data class StagingResult(
    val staging: ResolvedStorage?,
    val diagnostics: List<TtrpDiagnostic>,
)

/**
 * Resolves the staging storage and checks movement feasibility (D-f as a *check*,
 * never a picker). Staging = world `staging: true` else `[ttrp] staging` else — when
 * a cross-engine crossing exists — `TTRP-WLD-006`. Reachability derivation (P2, no
 * guessing): a `local_dir` storage is reachable by every data engine (Arrow-IPC
 * staging is universal); an engine-typed storage (e.g. postgres) is reachable only by
 * the engine that hosts it (`via`). Infeasible staging ⇒ `TTRP-MOV-002`; a container
 * loading a storage its engine cannot read ⇒ `TTRP-MOV-004`.
 */
class StagingResolver(
    private val bound: BoundWorld,
    private val projectStagingKey: String? = null,
) {
    fun resolve(graph: TtrpGraph): StagingResult {
        val diags = mutableListOf<TtrpDiagnostic>()
        val crossings = crossEnginePairs(graph)
        val staging =
            bound.world.staging
                ?: projectStagingKey?.let { key -> bound.storages.firstOrNull { it.qname.name == key } }

        if (crossings.isNotEmpty() && staging == null) {
            diags += diag(TtrpDiagnosticId.WLD_006, "a cross-engine crossing exists but no staging storage is declared")
        }
        if (staging != null) {
            for ((a, b) in crossings) {
                if (!reaches(a, staging) || !reaches(b, staging)) {
                    diags +=
                        diag(
                            TtrpDiagnosticId.MOV_002,
                            "cannot stage between `${a.engine.qname.name}` and `${b.engine.qname.name}` via `${staging.qname.name}`",
                        )
                }
            }
        }
        // Direct-load feasibility.
        for (container in graph.containers.values) {
            val engine = bound.engines[container.target] ?: continue
            for (id in container.memberIds) {
                val load = graph.nodes[id] as? Load ?: continue
                val storageName = load.source.substringBefore('.')
                val storage = bound.storages.firstOrNull { it.qname.name == storageName } ?: continue
                if (!reaches(engine, storage)) {
                    diags +=
                        diag(
                            TtrpDiagnosticId.MOV_004,
                            "engine `${engine.engine.qname.name}` has no read relation to storage `$storageName`",
                        )
                }
            }
        }
        return StagingResult(staging, diags)
    }

    /** Distinct unordered cross-engine container pairs joined by a DATA edge. */
    private fun crossEnginePairs(graph: TtrpGraph): List<Pair<BoundEngine, BoundEngine>> {
        val pairs = LinkedHashMap<String, Pair<BoundEngine, BoundEngine>>()
        for (e in graph.edges) {
            if (e.kind.name.startsWith("CONTROL")) continue
            val fromC = graph.containers[e.from.nodeId] ?: graph.containerOf(e.from.nodeId) ?: continue
            val toC = graph.containers[e.to.nodeId] ?: graph.containerOf(e.to.nodeId) ?: continue
            if (fromC.id == toC.id) continue
            val a = bound.engines[fromC.target] ?: continue
            val b = bound.engines[toC.target] ?: continue
            if (a.manifest.type == b.manifest.type) continue
            val key = listOf(fromC.id, toC.id).sorted().joinToString("|")
            pairs.putIfAbsent(key, a to b)
        }
        return pairs.values.toList()
    }

    private fun reaches(
        engine: BoundEngine,
        storage: ResolvedStorage,
    ): Boolean {
        if (storage.type == "local_dir") return true
        return storage.via == engine.engine.qname.name
    }

    private fun diag(
        id: TtrpDiagnosticId,
        message: String,
    ) = TtrpDiagnostic(id, Severity.ERROR, message, SourceLocation.UNKNOWN)
}
