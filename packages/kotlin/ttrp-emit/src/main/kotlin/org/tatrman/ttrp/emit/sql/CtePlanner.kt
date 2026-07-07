package org.tatrman.ttrp.emit.sql

import org.tatrman.plan.v1.ColumnRef as PbColumnRef
import org.tatrman.plan.v1.PlanNode
import org.tatrman.plan.v1.QualifiedName
import org.tatrman.plan.v1.SchemaCode
import org.tatrman.plan.v1.TableScanNode
import org.tatrman.translator.framework.ModelColumn
import org.tatrman.translator.framework.ModelTable
import org.tatrman.ttrp.graph.model.Node

/** One column of an island node's row type: name + a db-schema type spelling. */
data class EmitColumn(
    val name: String,
    val type: String,
)

/** Where a node's input comes from: a real base table, or an upstream node's CTE. */
sealed interface EmitInput {
    data class BaseTable(
        val namespace: String,
        val name: String,
        val columns: List<EmitColumn>,
    ) : EmitInput

    data class Cte(
        val producerNodeId: String,
        val columns: List<EmitColumn>,
    ) : EmitInput
}

/**
 * One relational transform node of an island, ready to lower: its CTE name (from
 * [org.tatrman.ttrp.emit.core.SsaNames]), the graph node, its resolved inputs (in port
 * order), and its output row type. [EmitPlan] is a topologically ordered list of these,
 * terminal last.
 */
data class EmitNode(
    val cteName: String,
    val node: Node,
    val inputs: List<EmitInput>,
    val outputColumns: List<EmitColumn>,
)

/**
 * Turns an island's ordered transform nodes into a single Postgres-dialect SQL statement:
 * **CTE-per-node** with SSA names as CTE names, the terminal node as the outer query (so a
 * terminal Sort/Limit keeps its ordering), and the **flat-trivial** rule — an island with
 * ≤1 transform node emits a plain SELECT with no `WITH` (E-b). Loads are base-table scans,
 * never their own CTE (idiomatic SQL; the ttr-translator produces the per-node bodies).
 */
class CtePlanner(
    private val facade: (model: List<ModelTable>) -> TranslatorFacade,
) {
    fun emit(
        plan: List<EmitNode>,
        islandName: String?,
    ): String {
        require(plan.isNotEmpty()) { "cannot emit an island with no transform nodes" }

        val model = buildModel(plan)
        val f = facade(model)
        val cteById = plan.associateBy { it.node.id }

        // Non-terminal nodes → CTEs; terminal → outer query.
        val terminal = plan.last()
        val ctes = plan.dropLast(1)

        val body: (EmitNode) -> String = { en ->
            val inputs = en.inputs.map { scanFor(it, cteById) }
            val raw = f.unparse(PlanNodeBuilder().body(en.node, inputs), islandName)
            stripCteNamespace(raw)
        }

        if (ctes.isEmpty()) {
            // Flat-trivial: single transform node over base tables, no WITH.
            return body(terminal)
        }

        val sb = StringBuilder()
        sb.append("WITH ")
        ctes.forEachIndexed { i, en ->
            if (i > 0) sb.append(",\n")
            sb.append('"').append(en.cteName).append("\" AS (\n")
            sb.append(indent(body(en)))
            sb.append("\n)")
        }
        sb.append('\n')
        sb.append(body(terminal))
        return sb.toString()
    }

    /** A TableScan PlanNode for an input — base table keeps its namespace; CTE uses the sentinel. */
    private fun scanFor(
        input: EmitInput,
        cteById: Map<String, EmitNode>,
    ): PlanNode {
        val (ns, name, cols) =
            when (input) {
                is EmitInput.BaseTable -> Triple(input.namespace, input.name, input.columns)
                is EmitInput.Cte -> {
                    val producer =
                        cteById[input.producerNodeId]
                            ?: error("CTE input references unknown producer ${input.producerNodeId}")
                    Triple(CTE_NAMESPACE, producer.cteName, input.columns)
                }
            }
        val scan =
            TableScanNode
                .newBuilder()
                .setTable(
                    QualifiedName
                        .newBuilder()
                        .setSchemaCode(SchemaCode.DB)
                        .setNamespace(ns)
                        .setName(name),
                )
        cols.forEach { scan.addOutputColumns(PbColumnRef.newBuilder().setName(it.name)) }
        return PlanNode.newBuilder().setTableScan(scan).build()
    }

    private fun buildModel(plan: List<EmitNode>): List<ModelTable> {
        val tables = LinkedHashMap<Pair<String, String>, ModelTable>()
        // Base tables referenced by any node's inputs.
        plan.forEach { en ->
            en.inputs.filterIsInstance<EmitInput.BaseTable>().forEach { bt ->
                tables.getOrPut(bt.namespace to bt.name) { modelTable(bt.namespace, bt.name, bt.columns) }
            }
        }
        // CTE pseudo-tables — every non-terminal node, under the sentinel namespace.
        plan.dropLast(1).forEach { en ->
            tables.getOrPut(CTE_NAMESPACE to en.cteName) {
                modelTable(CTE_NAMESPACE, en.cteName, en.outputColumns)
            }
        }
        return tables.values.toList()
    }

    private fun modelTable(
        namespace: String,
        name: String,
        columns: List<EmitColumn>,
    ): ModelTable =
        ModelTable(
            qname =
                QualifiedName
                    .newBuilder()
                    .setSchemaCode(
                        SchemaCode.DB,
                    ).setNamespace(namespace)
                    .setName(name)
                    .build(),
            columns = columns.map { ModelColumn(it.name, TypeMapping.surfaceType(it.type)) },
        )

    private fun indent(s: String): String = s.lineSequence().joinToString("\n") { "  $it" }

    /** Rewrite `"<sentinel>"."cte"` → `"cte"` so CTE references render bare. */
    private fun stripCteNamespace(sql: String): String = sql.replace("\"$CTE_NAMESPACE\".", "")

    companion object {
        /** Namespace CTE pseudo-tables register under; stripped from unparsed output. */
        const val CTE_NAMESPACE = "_ttrp_cte"
    }
}
