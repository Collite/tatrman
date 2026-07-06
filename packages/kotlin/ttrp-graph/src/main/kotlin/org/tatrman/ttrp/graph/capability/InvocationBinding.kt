package org.tatrman.ttrp.graph.capability

import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.graph.model.Display
import org.tatrman.ttrp.graph.model.TtrpGraph

/** A resolved (data island / channel, executor) → delivery binding (F-c). */
data class ResolvedBinding(
    val containerId: String,
    val engineType: String,
    val executor: String,
    val invocation: Invocation,
)

data class InvocationResult(
    val byContainer: Map<String, ResolvedBinding>,
    val displayBinding: Invocation?,
    val diagnostics: List<TtrpDiagnostic>,
)

/**
 * Resolves, per container, the (container data-engine type, program executor type) →
 * the executor manifest's matching [Invocation] (F-c). Display leaves resolve
 * (display, executor) → the file-drop invocation. A missing pair is `TTRP-WLD-007`.
 */
class InvocationBindingResolver(
    private val bound: BoundWorld,
) {
    fun resolve(graph: TtrpGraph): InvocationResult {
        val diags = mutableListOf<TtrpDiagnostic>()
        val executor = bound.executors.values.firstOrNull()
        val byContainer = LinkedHashMap<String, ResolvedBinding>()
        if (executor == null) {
            return InvocationResult(byContainer, null, diags)
        }
        for (container in graph.containers.values) {
            val engine = bound.engines[container.target] ?: continue
            val engineType = engine.manifest.type ?: continue
            val inv = executor.manifest.invocations.firstOrNull { it.targetEngineType == engineType }
            if (inv == null) {
                diags +=
                    diag(
                        TtrpDiagnosticId.WLD_007,
                        "no invocation binding for (`$engineType`, `${executor.executor.qname.name}`)",
                        SourceLocation.UNKNOWN,
                    )
            } else {
                byContainer[container.id] =
                    ResolvedBinding(container.id, engineType, executor.executor.qname.name, inv)
            }
        }
        val display =
            if (graph.nodes.values.any { it is Display }) {
                executor.manifest.invocations.firstOrNull { it.targetEngineType == "display" }
            } else {
                null
            }
        return InvocationResult(byContainer, display, diags)
    }

    private fun diag(
        id: TtrpDiagnosticId,
        message: String,
        location: SourceLocation,
    ) = TtrpDiagnostic(id, Severity.ERROR, message, location)
}
