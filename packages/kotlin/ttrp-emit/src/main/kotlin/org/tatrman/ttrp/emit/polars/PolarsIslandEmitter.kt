// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.polars

import org.tatrman.ttrp.emit.EmitDiagnosticId
import org.tatrman.ttrp.emit.TtrpEmitException
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.graph.model.Aggregate
import org.tatrman.ttrp.graph.model.Calc
import org.tatrman.ttrp.graph.model.Display
import org.tatrman.ttrp.graph.model.Distinct
import org.tatrman.ttrp.graph.model.Filter
import org.tatrman.ttrp.graph.model.Join
import org.tatrman.ttrp.graph.model.JoinType
import org.tatrman.ttrp.graph.model.Limit
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.Project
import org.tatrman.ttrp.graph.model.Select
import org.tatrman.ttrp.graph.model.Sort
import org.tatrman.ttrp.graph.model.Store
import org.tatrman.ttrp.graph.model.Union

/** How a Load node reads its input: a staged Arrow file, or a declared-schema CSV (D-c). */
sealed interface PolarsSource {
    data class Staged(
        val edge: String,
    ) : PolarsSource

    data class Csv(
        val path: String,
        val schema: List<Pair<String, String>>,
    ) : PolarsSource
}

/**
 * One straight-line statement of a Polars island: its [varName] (from
 * [org.tatrman.ttrp.emit.core.SsaNames]), the graph [node], its input variable names in port
 * order, plus [source] (for Loads) / [sinkPath] (for Store/Display sinks).
 */
data class PolarsStep(
    val varName: String,
    val node: Node,
    val inputVars: List<String> = emptyList(),
    val source: PolarsSource? = null,
    val sinkPath: String? = null,
)

data class PolarsEmitResult(
    val text: String,
    val prelude: List<String>,
)

/**
 * Emits a Polars island as a straight-line Python script — one statement per node, SSA names
 * carried as variable names, mirroring the canonical text (E-c γ). A generated inline prelude
 * (only the helpers the program needs) is prepended by [PreludeGenerator]; the script is
 * dependency-free beyond `polars` itself (F-c).
 */
class PolarsIslandEmitter {
    private val expr = PolarsExprRenderer()

    companion object {
        /**
         * Write Arrow IPC at the oldest compat level so string columns are `large_string` (utf8),
         * not Polars' default `string_view` — the latter is a newer Arrow type the conform reader
         * (Arrow Java, [org.tatrman.ttrp.conform.ArrowIo]) can't read and that mismatches the PG/
         * pyarrow side's `utf8` in the Q9-1 schema fingerprint. Applied to every Arrow sink.
         */
        const val IPC_COMPAT = ", compat_level=pl.CompatLevel.oldest()"
    }

    fun emit(
        islandName: String,
        steps: List<PolarsStep>,
    ): PolarsEmitResult {
        val prelude = PreludeGenerator().forSteps(steps)
        val sb = StringBuilder()
        sb.append("import polars as pl\n")
        if (prelude.isNotEmpty()) {
            sb.append("# --- ttrp prelude (generated; only helpers this program needs) ---\n")
            prelude.forEach { sb.append(it).append('\n') }
        }
        sb.append("# --- island: ").append(islandName).append(" ---\n")
        steps.forEach { sb.append(statement(it)).append('\n') }
        return PolarsEmitResult(sb.toString().trimEnd('\n') + "\n", prelude)
    }

    private fun statement(step: PolarsStep): String {
        val v = step.varName
        val ins = step.inputVars
        return when (val n = step.node) {
            is Load -> "$v = ${loadExpr(step)}"
            is Filter -> "$v = ${ins[0]}.filter(${expr.render(requirePred(n.predicate, n))})"
            is Project -> "$v = ${ins[0]}.select([${n.columns.joinToString(", ") { projectItem(it) }}])"
            is Aggregate -> "$v = ${aggregate(n, ins[0])}"
            is Sort -> "$v = ${sort(n, ins[0])}"
            is Union -> "$v = pl.concat([${ins.joinToString(", ")}])"
            is Join -> "$v = ${join(n, ins)}"
            is Limit -> "$v = ${ins[0]}.head(${n.count ?: error("Limit needs a count")})"
            is Store -> "${ins[0]}.write_ipc(${quote(step.sinkPath ?: "staging/$v.arrow")}$IPC_COMPAT)"
            is Display -> displayStmt(n, ins[0], step.sinkPath)
            is Select, is Calc, is Distinct ->
                throw TtrpEmitException(
                    EmitDiagnosticId.SUGAR_REACHED_EMIT,
                    detail = "sugar node ${n::class.simpleName} '${n.label}' reached Polars emit",
                    location = n.location,
                )
            else ->
                throw TtrpEmitException(
                    EmitDiagnosticId.UNSUPPORTED_NODE,
                    detail = "node ${n::class.simpleName} '${n.label}' has no Polars emit",
                    location = n.location,
                )
        }
    }

    private fun loadExpr(step: PolarsStep): String =
        when (val s = step.source) {
            is PolarsSource.Staged -> "pl.read_ipc(${quote("staging/${s.edge}.arrow")})"
            is PolarsSource.Csv -> {
                val schema = s.schema.joinToString(", ") { "${quote(it.first)}: ${csvDtype(it.second)}" }
                "pl.read_csv(${quote(s.path)}, schema={$schema})"
            }
            null ->
                throw TtrpEmitException(
                    EmitDiagnosticId.UNSUPPORTED_NODE,
                    detail = "Load '${step.node.label}' has no source binding",
                    location = step.node.location,
                )
        }

    private fun projectItem(e: Expression): String {
        val rendered = expr.render(e)
        // A bare renamed column keeps its name; anything else is anonymous (Polars keeps input name).
        return rendered
    }

    private fun aggregate(
        n: Aggregate,
        input: String,
    ): String {
        val keys = n.groupBy.joinToString(", ") { quote(it) }
        val aggs =
            n.aggregations.joinToString(", ") { a ->
                val call =
                    a.value as? org.tatrman.ttrp.expr.AggregateCall
                        ?: throw TtrpEmitException(
                            EmitDiagnosticId.UNSUPPORTED_NODE,
                            detail = "aggregation '${a.name}' is not an AggregateCall",
                            location = n.location,
                        )
                "${expr.aggregate(call)}.alias(${quote(a.name)})"
            }
        return "$input.group_by([$keys]).agg([$aggs])"
    }

    private fun sort(
        n: Sort,
        input: String,
    ): String {
        val parsed = n.keys.map { parseSortKey(it) }
        val by = parsed.joinToString(", ") { quote(it.first) }
        val desc = parsed.joinToString(", ") { if (it.second) "True" else "False" }
        return "$input.sort(by=[$by], descending=[$desc], nulls_last=True)"
    }

    private fun parseSortKey(key: String): Pair<String, Boolean> {
        val t = key.trim()
        return when {
            t.endsWith(" desc", ignoreCase = true) -> t.dropLast(5).trim() to true
            t.endsWith(" asc", ignoreCase = true) -> t.dropLast(4).trim() to false
            else -> t to false
        }
    }

    private fun join(
        n: Join,
        ins: List<String>,
    ): String {
        val how =
            when (n.type) {
                JoinType.INNER -> "inner"
                JoinType.LEFT -> "left"
                JoinType.RIGHT -> "right"
                JoinType.FULL -> "full"
                JoinType.SEMI -> "semi"
                JoinType.ANTI -> "anti"
                JoinType.CROSS -> "cross"
            }
        if (n.type == JoinType.CROSS || n.on == null) {
            return "${ins[0]}.join(${ins[1]}, how=\"$how\")"
        }
        val (left, right) = equiKeys(n, n.on!!)
        val leftOn = left.joinToString(", ") { quote(it) }
        val rightOn = right.joinToString(", ") { quote(it) }
        return "${ins[0]}.join(${ins[1]}, left_on=[$leftOn], right_on=[$rightOn], how=\"$how\")"
    }

    /** Extract equi-join key columns from a conjunction of `left.x = right.y` conditions. */
    private fun equiKeys(
        n: Join,
        on: Expression,
    ): Pair<List<String>, List<String>> {
        val left = mutableListOf<String>()
        val right = mutableListOf<String>()

        fun walk(e: Expression) {
            when {
                e is FunctionCall && e.function.name == "and" -> e.args.forEach { walk(it) }
                e is FunctionCall && e.function.name == "eq" -> {
                    val a = e.args[0] as? ColumnRef
                    val b = e.args[1] as? ColumnRef
                    if (a == null || b == null) unsupportedJoin(n)
                    // Order by port: left.* → left_on, right.* → right_on.
                    if (a!!.port == "right" || b!!.port == "left") {
                        left += b.column
                        right += a.column
                    } else {
                        left += a.column
                        right += b.column
                    }
                }
                else -> unsupportedJoin(n)
            }
        }
        walk(on)
        return left to right
    }

    private fun unsupportedJoin(n: Join): Nothing =
        throw TtrpEmitException(
            EmitDiagnosticId.UNSUPPORTED_NODE,
            detail = "Join '${n.label}' has a non-equi ON condition; only equi-joins emit to Polars in v1",
            location = n.location,
        )

    private fun displayStmt(
        n: Display,
        input: String,
        sinkPath: String?,
    ): String {
        val path = sinkPath ?: "out/${n.name}.arrow"
        return "$input.write_ipc(${quote(path)}$IPC_COMPAT)\nprint(f\"display ${n.name}: $path\")"
    }

    private fun requirePred(
        pred: Expression?,
        n: Node,
    ): Expression =
        pred ?: throw TtrpEmitException(
            EmitDiagnosticId.UNSUPPORTED_NODE,
            detail = "Filter '${n.label}' has no predicate",
            location = n.location,
        )

    private fun csvDtype(spelling: String): String =
        when (spelling.substringBefore('(').trim().lowercase()) {
            "int", "integer", "bigint", "long" -> "pl.Int64"
            "float", "double", "real" -> "pl.Float64"
            "decimal", "numeric", "number", "money" -> "pl.Decimal"
            "bool", "boolean" -> "pl.Boolean"
            "date" -> "pl.Date"
            "timestamp", "datetime" -> "pl.Datetime(\"us\", \"UTC\")"
            else -> "pl.String"
        }

    private fun quote(s: String): String = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
