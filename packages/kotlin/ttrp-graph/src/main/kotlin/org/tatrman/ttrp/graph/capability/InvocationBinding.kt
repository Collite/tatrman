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
        val byContainer = LinkedHashMap<String, ResolvedBinding>()
        if (bound.executors.isEmpty()) {
            return InvocationResult(byContainer, null, diags)
        }
        for (container in graph.containers.values) {
            val engine = bound.engines[container.target] ?: continue
            val engineType = engine.manifest.type ?: continue
            // Bind to ANY executor whose manifest can invoke this engine type (F-c), not just
            // the first — a world may declare several executors serving disjoint engine types.
            val binding = invocationFor(engineType)
            if (binding == null) {
                diags +=
                    diag(
                        TtrpDiagnosticId.WLD_007,
                        "no invocation binding for `$engineType` across any declared executor",
                        SourceLocation.UNKNOWN,
                    )
            } else {
                val (executor, inv) = binding
                byContainer[container.id] =
                    ResolvedBinding(container.id, engineType, executor.executor.qname.name, inv)
            }
        }
        val display =
            if (graph.nodes.values.any { it is Display }) {
                invocationFor("display")?.second
            } else {
                null
            }
        return InvocationResult(byContainer, display, diags)
    }

    /** The first (executor, invocation) whose manifest can invoke [engineType], or null. */
    private fun invocationFor(engineType: String): Pair<BoundExecutor, Invocation>? =
        bound.executors.values.firstNotNullOfOrNull { ex ->
            ex.manifest.invocations
                .firstOrNull { it.targetEngineType == engineType }
                ?.let { ex to it }
        }

    private fun diag(
        id: TtrpDiagnosticId,
        message: String,
        location: SourceLocation,
    ) = TtrpDiagnostic(id, Severity.ERROR, message, location)
}
