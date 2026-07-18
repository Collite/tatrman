// SPDX-License-Identifier: Apache-2.0
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
    /** The placed engine's rejects capability — a `domain: canonical` entry emits its native oracle
     *  in the guard instead of the canonical regex guard (contracts §3). Default: canonical guard. */
    private val rejectsSupport: org.tatrman.ttrp.graph.capability.RejectsSupport =
        org.tatrman.ttrp.graph.capability.RejectsSupport.NONE,
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
            // A reject guard carries `internal.*` validity calls, and a cast calc carries a `Cast`,
            // that the translator/Calcite path cannot render (no regex operator, CastExpression
            // decode is TODO — RJ-P3 Option E) — emit those CTEs as raw dialect SQL. Every other
            // node still routes through the translator.
            rawProjectBody(en, cteById) ?: run {
                val inputs = en.inputs.map { scanFor(it, cteById) }
                val raw = f.unparse(PlanNodeBuilder().body(en.node, inputs), islandName)
                stripCteNamespace(raw)
            }
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

    /**
     * Raw PG SQL for a [Project] the translator cannot lower — a reject guard (`internal.*`
     * validity calls) or a cast calc (a `Cast`, whose CastExpression decode is TODO in the
     * translator). Null for ordinary nodes. Shape (contracts §5/§6):
     * `SELECT <passthrough cols>, (<expr>) AS "<alias>"[, …] FROM <input>`. Internal `_ttrp_v*`
     * validity flags are never passed through (they exist only to drive the branch).
     */
    private fun rawProjectBody(
        en: EmitNode,
        cteById: Map<String, EmitNode>,
    ): String? {
        val node = en.node as? org.tatrman.ttrp.graph.model.Project ?: return null
        if (node.columns.none { RejectGuardSql.isInternal(it) || it is org.tatrman.ttrp.expr.Cast }) return null
        val input = en.inputs.singleOrNull() ?: return null
        val overridden =
            node.columns.indices
                .mapNotNull { node.aliasOf(it) }
                .toSet()
        val select = mutableListOf<String>()
        if (node.passthrough) {
            inputColumns(input)
                .filterNot { it in overridden || RejectGuardSql.isValidityFlag(it) }
                .forEach { select += "\"$it\"" }
        }
        node.columns.forEachIndexed { i, c ->
            val alias = node.aliasOf(i) ?: "_expr$i"
            val sql =
                (c as? org.tatrman.ttrp.expr.FunctionCall)
                    ?.let { RejectGuardSql.render(it, RejectGuardSql::renderArg, rejectsSupport) }
                    ?: RejectGuardSql.renderArg(c)
            select += "($sql) AS \"$alias\""
        }
        return "SELECT ${select.joinToString(", ")}\nFROM ${fromRelation(input, cteById)}"
    }

    private fun inputColumns(input: EmitInput): List<String> =
        when (input) {
            is EmitInput.BaseTable -> input.columns.map { it.name }
            is EmitInput.Cte -> input.columns.map { it.name }
        }

    private fun fromRelation(
        input: EmitInput,
        cteById: Map<String, EmitNode>,
    ): String =
        when (input) {
            is EmitInput.BaseTable -> "\"${input.name}\""
            is EmitInput.Cte -> "\"${cteById[input.producerNodeId]?.cteName ?: input.producerNodeId}\""
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
