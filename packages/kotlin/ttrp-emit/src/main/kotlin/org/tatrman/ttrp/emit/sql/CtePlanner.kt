// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import org.tatrman.plan.v1.ColumnRef as PbColumnRef
import org.tatrman.plan.v1.PlanNode
import org.tatrman.plan.v1.QualifiedName
import org.tatrman.plan.v1.SchemaCode
import org.tatrman.plan.v1.TableScanNode
import org.tatrman.translator.framework.ModelColumn
import org.tatrman.translator.framework.ModelTable
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.expr.AggregateCall
import org.tatrman.ttrp.expr.CaseWhen
import org.tatrman.ttrp.expr.Cast
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.expr.InList
import org.tatrman.ttrp.expr.IsNull
import org.tatrman.ttrp.expr.Literal
import org.tatrman.ttrp.expr.MdPath
import org.tatrman.ttrp.expr.MdResolution
import org.tatrman.ttrp.graph.model.Aggregate
import org.tatrman.ttrp.graph.model.Branch
import org.tatrman.ttrp.graph.model.Calc
import org.tatrman.ttrp.graph.model.Filter
import org.tatrman.ttrp.graph.model.Join
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.Project
import org.tatrman.ttrp.graph.model.Switch

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
    /** MD read lowering context (S4-A), threaded to every [PlanNodeBuilder]; null when no MD paths. */
    private val mdLowering: MdPathLowering? = null,
    private val mdResolutions: Map<SourceLocation, MdResolution> = emptyMap(),
    // `facade` stays last (no default) so the common `CtePlanner { model -> … }` trailing-lambda call
    // still binds the lambda here; the MD context is supplied by name when present.
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
            val raw = f.unparse(PlanNodeBuilder(mdLowering, mdResolutions).body(en.node, inputs), islandName)
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
        // MD backing tables (S4-A4 e2e): the fact/hop `db` tables read by any MdPath in the plan's node
        // expressions, so the translator can resolve them when it unparses the lowered scalar subqueries.
        val lowering = mdLowering
        if (lowering != null) {
            plan.forEach { en ->
                mdPathsIn(en.node).forEach { mdPath ->
                    val resolution = mdResolutions[mdPath.location] ?: return@forEach
                    lowering.referencedTables(resolution.path, resolution.shape).forEach { t ->
                        tables.putIfAbsent(t.qname.namespace to t.qname.name, t)
                    }
                }
            }
        }
        return tables.values.toList()
    }

    /** The `mdPath` leaves in [node]'s expressions (mirrors CapabilityChecker's node-expression walk). */
    private fun mdPathsIn(node: Node): List<MdPath> = nodeExpressions(node).flatMap { collectMdPaths(it) }

    private fun nodeExpressions(node: Node): List<Expression> =
        when (node) {
            is Filter -> listOfNotNull(node.predicate)
            is Branch -> listOfNotNull(node.predicate)
            is Join -> listOfNotNull(node.on)
            is Aggregate -> node.aggregations.map { it.value } + listOfNotNull(node.having)
            is Project -> node.columns
            is Calc -> node.assignments.map { it.value }
            is Switch -> node.cases.mapNotNull { it.second }
            else -> emptyList()
        }

    private fun collectMdPaths(e: Expression): List<MdPath> =
        when (e) {
            is MdPath -> listOf(e)
            is FunctionCall -> e.args.flatMap { collectMdPaths(it) }
            is AggregateCall -> e.args.flatMap { collectMdPaths(it) }
            is Cast -> collectMdPaths(e.expr)
            is CaseWhen ->
                e.branches.flatMap { collectMdPaths(it.first) + collectMdPaths(it.second) } +
                    (e.elseExpr?.let { collectMdPaths(it) } ?: emptyList())
            is InList -> collectMdPaths(e.expr) + e.items.flatMap { collectMdPaths(it) }
            is IsNull -> collectMdPaths(e.expr)
            is ColumnRef, is Literal -> emptyList()
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
