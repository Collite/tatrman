// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.explain

import org.tatrman.ttrp.graph.capability.BoundWorld
import org.tatrman.ttrp.graph.collapse.ExecutionGraph
import org.tatrman.ttrp.graph.model.TtrpGraph
import org.tatrman.ttrp.graph.rewrite.AppliedRewrite

/**
 * Deterministic plain-text `ttrp explain` (S4 / contracts §4): normalized graph
 * summary, placements, applied rewrites, island→payload map, and waves. Field order
 * fixed; no timestamps, no absolute paths (golden-stability). Emit payloads are P3;
 * here we render the payload *kind* (sql|python) + invocation.
 */
object ExplainRenderer {
    fun render(
        program: String,
        graph: TtrpGraph,
        exec: ExecutionGraph,
        rewrites: List<AppliedRewrite>,
        bound: BoundWorld,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("== ttrp explain: $program ==")
        sb.appendLine()

        sb.appendLine("islands:")
        for (island in exec.islands) {
            val payload = payloadKind(bound, island.engine)
            sb.appendLine(
                "  ${island.name}  engine=${island.engine}  invocation=${island.invocation ?: "-"}  payload=$payload",
            )
        }
        sb.appendLine()

        sb.appendLine("movement:")
        if (exec.transfers.isEmpty()) {
            sb.appendLine("  (none)")
        } else {
            for (t in exec.transfers) {
                val from = islandName(exec, t.fromIsland)
                val to = islandName(exec, t.toIsland)
                sb.appendLine("  $from -> $to  via=${t.via ?: "-"}  format=${t.format}")
            }
        }
        sb.appendLine()

        sb.appendLine("leaves:")
        exec.displays.forEach { sb.appendLine("  display $it") }
        exec.stores.forEach { sb.appendLine("  store $it") }
        sb.appendLine()

        sb.appendLine("applied rewrites:")
        if (rewrites.isEmpty()) {
            sb.appendLine("  (none)")
        } else {
            for (r in rewrites) {
                sb.appendLine("  ${r.rule}  (${r.engine ?: "-"}): ${r.before}")
            }
        }
        sb.appendLine()

        sb.appendLine("placements:")
        for (island in exec.islands) {
            for (memberId in island.memberIds) {
                val node = graph.nodes[memberId]
                // RJ-P1 (1.3.8): a synthesized reject node renders indented UNDER its authored node,
                // not as a top-level placement — the elaborated cluster reads as one unit.
                if (memberId in graph.synthProvenance) continue
                val label = node?.label ?: memberId
                // E-d: render the er origin (provenance) after a rewritten node, er-first.
                val prov = node?.provenance?.let { "  (er ${it.originQname})" } ?: ""
                sb.appendLine("  $label -> ${island.name}/${island.engine}$prov")
                island.memberIds
                    .filter { graph.synthProvenance[it] == memberId }
                    .forEach { childId ->
                        sb.appendLine("    ↳ ${graph.nodes[childId]?.label ?: childId} (rejects)")
                    }
            }
        }
        sb.appendLine()

        sb.appendLine("waves:")
        exec.waves.forEachIndexed { i, wave ->
            val names = wave.map { execLabel(exec, it) }
            sb.appendLine("  wave $i: ${names.joinToString(", ")}")
        }
        return sb.toString().trimEnd() + "\n"
    }

    private fun payloadKind(
        bound: BoundWorld,
        engine: String,
    ): String =
        when (bound.engines[engine]?.manifest?.type) {
            "postgres" -> "sql"
            "polars" -> "python"
            else -> "?"
        }

    private fun islandName(
        exec: ExecutionGraph,
        id: String?,
    ): String = exec.islands.firstOrNull { it.id == id }?.name ?: (id ?: "?")

    private fun execLabel(
        exec: ExecutionGraph,
        id: String,
    ): String =
        exec.islands.firstOrNull { it.id == id }?.name
            ?: exec.transfers.firstOrNull { it.id == id }?.let {
                "transfer ${islandName(exec, it.fromIsland)}→${islandName(exec, it.toIsland)}"
            }
            ?: id
}
