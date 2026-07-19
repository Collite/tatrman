// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import org.tatrman.plan.v1.AggregateCall as PbAggregateCall
import org.tatrman.plan.v1.AggregateNode
import org.tatrman.plan.v1.CastExpression
import org.tatrman.plan.v1.ColumnRef as PbColumnRef
import org.tatrman.plan.v1.Expression as PbExpression
import org.tatrman.plan.v1.FilterNode
import org.tatrman.plan.v1.FunctionCall as PbFunctionCall
import org.tatrman.plan.v1.JoinNode
import org.tatrman.plan.v1.JoinType as PbJoinType
import org.tatrman.plan.v1.LimitOffsetNode
import org.tatrman.plan.v1.Literal as PbLiteral
import org.tatrman.plan.v1.NamedExpression
import org.tatrman.plan.v1.PlanNode
import org.tatrman.plan.v1.ProjectNode
import org.tatrman.plan.v1.SortKey
import org.tatrman.plan.v1.SortNode
import org.tatrman.plan.v1.UnionNode
import org.tatrman.ttrp.emit.EmitDiagnosticId
import org.tatrman.ttrp.emit.TtrpEmitException
import org.tatrman.ttrp.expr.AggregateCall
import org.tatrman.ttrp.expr.CaseWhen
import org.tatrman.ttrp.expr.Cast
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.expr.InList
import org.tatrman.ttrp.expr.IsNull
import org.tatrman.ttrp.expr.Literal
import org.tatrman.ttrp.expr.LiteralValue
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.expr.MdPath
import org.tatrman.ttrp.expr.MdResolution
import org.tatrman.ttrp.graph.model.Aggregate
import org.tatrman.ttrp.graph.model.Filter
import org.tatrman.ttrp.graph.model.Join
import org.tatrman.ttrp.graph.model.JoinType
import org.tatrman.ttrp.graph.model.Limit
import org.tatrman.ttrp.graph.model.Node
import org.tatrman.ttrp.graph.model.PortNames
import org.tatrman.ttrp.graph.model.Project
import org.tatrman.ttrp.graph.model.Sort
import org.tatrman.ttrp.graph.model.Union
import org.tatrman.ttrp.graph.model.Calc
import org.tatrman.ttrp.graph.model.Distinct
import org.tatrman.ttrp.graph.model.Select

/**
 * Lowers a single normalized TTR-P relational [Node] (plus its already-built input
 * [PlanNode]s) and the TTR-P [Expression] IR to the `plan.v1` wire form the translator
 * consumes. This is a purely mechanical structural map — the two IRs are deliberate twins
 * (T5). Sugar nodes are an internal-invariant failure here (T8 must have expanded them).
 *
 * Node coverage is the plan.v1-representable relational subset: Filter, Project, Aggregate,
 * Sort (always NULLS LAST unless authored otherwise — Q9-3), Limit, Union, and
 * Join(inner/left/right/full). A join's `on` condition is encoded with per-side
 * `source_alias = $L/$R` tags (from the `left`/`right` port qualifiers) so the translator
 * decoder routes each column into the correct join input via `RelBuilder.field(2, ord, name)`
 * — see [LEFT_INPUT_TAG]/[RIGHT_INPUT_TAG] and `org.tatrman.translator.wire.Expressions`.
 *
 * SEMI/ANTI joins and Intersect/Except have **no plan.v1 representation** (the wire
 * `JoinType` enum stops at FULL) — they raise [EmitDiagnosticId.UNSUPPORTED_NODE] and are a
 * recorded deferral (Intersect/Except lower to semi/anti, which SQL engines emit natively
 * but the `plan.v1` wire cannot carry; see progress-phase-03.md).
 */
class PlanNodeBuilder(
    /**
     * MD dot-path read lowering (S4-A). Null when the program has no MD paths (the common case) —
     * an `mdPath` expression then still raises [EmitDiagnosticId.UNSUPPORTED_NODE]. When present,
     * an in-scalar-position `mdPath` lowers to a scalar subquery over the §8 relational subtree.
     */
    private val mdLowering: MdPathLowering? = null,
    /** The graph-side MD resolutions (from [org.tatrman.ttrp.graph.model.TtrpGraph.mdResolutions]). */
    private val mdResolutions: Map<SourceLocation, MdResolution> = emptyMap(),
) {
    /** Build the plan.v1 body for [node] over its pre-built [inputs]. */
    fun body(
        node: Node,
        inputs: List<PlanNode>,
    ): PlanNode =
        when (node) {
            is Filter -> filter(node, single(node, inputs))
            is Project -> project(node, single(node, inputs))
            is Aggregate -> aggregate(node, single(node, inputs))
            is Sort -> sort(node, single(node, inputs))
            is Limit -> limit(node, single(node, inputs))
            is Union -> union(node, inputs)
            is Join -> join(node, inputs)
            is Select, is Calc, is Distinct ->
                throw TtrpEmitException(
                    EmitDiagnosticId.SUGAR_REACHED_EMIT,
                    detail = "sugar node ${node::class.simpleName} '${node.label}' reached SQL emit",
                    location = node.location,
                )
            else ->
                throw TtrpEmitException(
                    EmitDiagnosticId.UNSUPPORTED_NODE,
                    detail = "node ${node::class.simpleName} '${node.label}' has no plan.v1 SQL lowering",
                    location = node.location,
                )
        }

    private fun single(
        node: Node,
        inputs: List<PlanNode>,
    ): PlanNode =
        inputs.singleOrNull()
            ?: throw TtrpEmitException(
                EmitDiagnosticId.UNSUPPORTED_NODE,
                detail = "${node::class.simpleName} '${node.label}' expected 1 input, got ${inputs.size}",
                location = node.location,
            )

    private fun filter(
        node: Filter,
        input: PlanNode,
    ): PlanNode {
        val pred =
            node.predicate
                ?: throw TtrpEmitException(
                    EmitDiagnosticId.UNSUPPORTED_NODE,
                    detail = "Filter '${node.label}' has no predicate",
                    location = node.location,
                )
        return PlanNode
            .newBuilder()
            .setFilter(FilterNode.newBuilder().setInput(input).setCondition(expr(pred)))
            .build()
    }

    private fun project(
        node: Project,
        input: PlanNode,
    ): PlanNode {
        val b = ProjectNode.newBuilder().setInput(input)
        node.columns.forEach { c ->
            val alias = (c as? ColumnRef)?.column
            val ne = NamedExpression.newBuilder().setExpression(expr(c))
            if (alias != null) ne.alias = alias
            b.addExpressions(ne)
        }
        return PlanNode.newBuilder().setProject(b).build()
    }

    private fun aggregate(
        node: Aggregate,
        input: PlanNode,
    ): PlanNode {
        val b = AggregateNode.newBuilder().setInput(input)
        node.groupBy.forEach { b.addGroupKeys(PbColumnRef.newBuilder().setName(it)) }
        node.aggregations.forEach { agg ->
            val call =
                agg.value as? AggregateCall
                    ?: throw TtrpEmitException(
                        EmitDiagnosticId.UNSUPPORTED_NODE,
                        detail = "aggregation '${agg.name}' is not an AggregateCall",
                        location = node.location,
                    )
            val pb =
                PbAggregateCall
                    .newBuilder()
                    .setFunction(aggName(call))
                    .setDistinct(call.distinct)
                    .setAlias(agg.name)
            call.args.forEach { arg ->
                val ref =
                    arg as? ColumnRef
                        ?: throw TtrpEmitException(
                            EmitDiagnosticId.UNSUPPORTED_NODE,
                            detail = "aggregate arg must be a column ref (got ${arg::class.simpleName})",
                            location = node.location,
                        )
                pb.addArgs(PbColumnRef.newBuilder().setName(ref.column))
            }
            b.addAggregates(pb)
        }
        // HAVING is split into a Filter-over-Aggregate by T8 — a residual having here is a bug.
        node.having?.let {
            throw TtrpEmitException(
                EmitDiagnosticId.SUGAR_REACHED_EMIT,
                detail = "Aggregate '${node.label}' still carries a HAVING clause (T8 should have split it)",
                location = node.location,
            )
        }
        return PlanNode.newBuilder().setAggregate(b).build()
    }

    private fun sort(
        node: Sort,
        input: PlanNode,
    ): PlanNode {
        val b = SortNode.newBuilder().setInput(input)
        node.keys.forEach { key ->
            // `keys` carry the column name; descending suffixes are parsed upstream. NULLS LAST
            // is always emitted (Q9-3): nulls_first defaults false → decoder → builder.nullsLast.
            val (name, desc) = parseSortKey(key)
            b.addSortKeys(
                SortKey
                    .newBuilder()
                    .setColumn(PbColumnRef.newBuilder().setName(name))
                    .setDescending(desc)
                    .setNullsFirst(false),
            )
        }
        return PlanNode.newBuilder().setSort(b).build()
    }

    private fun parseSortKey(key: String): Pair<String, Boolean> {
        val trimmed = key.trim()
        return when {
            trimmed.endsWith(" desc", ignoreCase = true) -> trimmed.dropLast(5).trim() to true
            trimmed.endsWith(" asc", ignoreCase = true) -> trimmed.dropLast(4).trim() to false
            else -> trimmed to false
        }
    }

    private fun limit(
        node: Limit,
        input: PlanNode,
    ): PlanNode {
        val b = LimitOffsetNode.newBuilder().setInput(input)
        node.count?.let { b.limit = it }
        return PlanNode.newBuilder().setLimitOffset(b).build()
    }

    private fun union(
        node: Union,
        inputs: List<PlanNode>,
    ): PlanNode {
        val b = UnionNode.newBuilder().setAll(true) // UNION ALL — distinct-union is a config we don't author yet
        inputs.forEach { b.addInputs(it) }
        return PlanNode.newBuilder().setUnion(b).build()
    }

    private fun join(
        node: Join,
        inputs: List<PlanNode>,
    ): PlanNode {
        if (inputs.size != 2) {
            throw TtrpEmitException(
                EmitDiagnosticId.UNSUPPORTED_NODE,
                detail = "Join '${node.label}' expected 2 inputs, got ${inputs.size}",
                location = node.location,
            )
        }
        val jt =
            when (node.type) {
                JoinType.INNER -> PbJoinType.INNER
                JoinType.LEFT -> PbJoinType.LEFT
                JoinType.RIGHT -> PbJoinType.RIGHT
                JoinType.FULL -> PbJoinType.FULL
                JoinType.SEMI, JoinType.ANTI, JoinType.CROSS ->
                    throw TtrpEmitException(
                        EmitDiagnosticId.UNSUPPORTED_NODE,
                        detail =
                            "${node.type} join has no plan.v1 wire representation " +
                                "(JoinType stops at FULL) — deferred (see progress-phase-03.md)",
                        location = node.location,
                    )
            }
        val b =
            JoinNode
                .newBuilder()
                .setLeft(inputs[0])
                .setRight(inputs[1])
                .setJoinType(jt)
        // The condition is port-qualified (`left.x = right.y`); encode it in join context so
        // `left`/`right` become the `$L`/`$R` input tags the decoder routes on.
        node.on?.let { b.condition = expr(it, inJoin = true) }
        val joinNode = PlanNode.newBuilder().setJoin(b).build()

        // Polars `right_on` parity (A4 identical-results): an equi-join DROPS the right-side key
        // columns, so `join(a, b, on: a.x = b.y)` yields `a.* + b.(non-key)` — the same output
        // schema Polars produces (see hero_crunch.py `right_on=…`). Replicate in SQL with a
        // positional Project over the join: Calcite uniquifies duplicate names in the combined
        // join row (`region`/`region0`), so we select surviving columns by ordinal (unambiguous)
        // and re-alias to their names. This also keeps the emitted CTE well-formed — a bare
        // `SELECT *` over a join with a duplicated column name is an invalid CTE.
        //
        // Falls back to the bare join for non-equi / cross / null conditions or when an input's
        // columns aren't known (input isn't a scan) — no right key to drop, no dedup needed.
        val rightKeys = node.on?.let { JoinDedup.rightEquiKeys(it) }
        val leftCols = scanColumnNames(inputs[0])
        val rightCols = scanColumnNames(inputs[1])
        if (rightKeys.isNullOrEmpty() || leftCols == null || rightCols == null) {
            return joinNode
        }
        val project = ProjectNode.newBuilder().setInput(joinNode)
        JoinDedup.survivors(leftCols, rightCols, rightKeys).forEach { (ordinal, name) ->
            project.addExpressions(
                NamedExpression
                    .newBuilder()
                    .setExpression(
                        PbExpression.newBuilder().setColumnRef(PbColumnRef.newBuilder().setName("\$$ordinal")),
                    ).setAlias(name),
            )
        }
        return PlanNode.newBuilder().setProject(project).build()
    }

    /** Column names of a [PlanNode] iff it is a TableScan (CtePlanner feeds joins base/CTE scans); else null. */
    private fun scanColumnNames(input: PlanNode): List<String>? =
        if (input.nodeCase == PlanNode.NodeCase.TABLE_SCAN) {
            input.tableScan.outputColumnsList.map { it.name }
        } else {
            null
        }

    // --- Expressions -----------------------------------------------------------------

    /**
     * Lower a TTR-P [Expression] to `plan.v1`. When [inJoin] is true this expression is a join
     * `on` condition: a [ColumnRef]'s `left`/`right` port qualifier is emitted as the `$L`/`$R`
     * `source_alias` tag so the decoder resolves it against the correct join input (rather than
     * the ambiguous combined join row type). Everywhere else `inJoin` is false and a port
     * qualifier is a plain label the single-input decoder ignores.
     */
    fun expr(
        e: Expression,
        inJoin: Boolean = false,
    ): PbExpression =
        when (e) {
            is ColumnRef -> {
                val ref = PbColumnRef.newBuilder().setName(e.column)
                val alias = if (inJoin) joinInputTag(e.port) else e.port
                alias?.let { ref.sourceAlias = it }
                PbExpression.newBuilder().setColumnRef(ref).build()
            }
            is Literal -> PbExpression.newBuilder().setLiteral(literal(e.value)).build()
            is FunctionCall ->
                PbExpression
                    .newBuilder()
                    .setFunction(
                        PbFunctionCall
                            .newBuilder()
                            .setOperation(operation(e))
                            .addAllOperands(e.args.map { expr(it, inJoin) }),
                    ).build()
            is IsNull ->
                PbExpression
                    .newBuilder()
                    .setFunction(
                        PbFunctionCall
                            .newBuilder()
                            .setOperation(if (e.negated) "is_not_null" else "is_null")
                            .addOperands(expr(e.expr, inJoin)),
                    ).build()
            is InList -> inList(e, inJoin)
            is CaseWhen -> caseWhen(e, inJoin)
            is Cast ->
                PbExpression
                    .newBuilder()
                    .setCast(
                        CastExpression
                            .newBuilder()
                            .setValue(expr(e.expr, inJoin))
                            .setTargetType(e.target.canonical),
                    ).build()
            is AggregateCall ->
                throw TtrpEmitException(
                    EmitDiagnosticId.UNSUPPORTED_NODE,
                    detail = "AggregateCall is only legal inside an Aggregate node, not a scalar expression",
                    location = e.location,
                )
            is MdPath -> mdPath(e)
        }

    /**
     * Lower an `mdPath` in scalar expression position to a scalar subquery over the §8 relational
     * subtree ([MdPathLowering]). Requires both the lowering context (the cubelet bindings) and this
     * path's S3 resolution (carried on the graph, keyed by location) — absent either, the path can't
     * be lowered here and raises [EmitDiagnosticId.UNSUPPORTED_NODE] with a specific reason.
     */
    private fun mdPath(e: MdPath): PbExpression {
        val lowering =
            mdLowering ?: throw TtrpEmitException(
                EmitDiagnosticId.UNSUPPORTED_NODE,
                detail = "MD dot-path reached emit with no binding context (md2db bindings not wired)",
                location = e.location,
            )
        val resolution =
            mdResolutions[e.location] ?: throw TtrpEmitException(
                EmitDiagnosticId.UNSUPPORTED_NODE,
                detail = "MD dot-path reached emit unresolved (no S3 resolution on the graph)",
                location = e.location,
            )
        return try {
            lowering.lowerScalar(resolution.path, resolution.shape)
        } catch (ex: MdLoweringException) {
            throw TtrpEmitException(
                EmitDiagnosticId.UNSUPPORTED_NODE,
                detail = ex.message ?: ex.code,
                location = e.location,
            )
        }
    }

    private fun inList(
        e: InList,
        inJoin: Boolean,
    ): PbExpression {
        val inCall =
            PbFunctionCall
                .newBuilder()
                .setOperation("in")
                .addOperands(expr(e.expr, inJoin))
                .addAllOperands(e.items.map { expr(it, inJoin) })
        val inExpr = PbExpression.newBuilder().setFunction(inCall).build()
        return if (!e.negated) {
            inExpr
        } else {
            PbExpression
                .newBuilder()
                .setFunction(PbFunctionCall.newBuilder().setOperation("not").addOperands(inExpr))
                .build()
        }
    }

    private fun caseWhen(
        e: CaseWhen,
        inJoin: Boolean,
    ): PbExpression {
        // plan.v1 "case" rides a generic FunctionCall; operands are Calcite's flat
        // [when1, then1, …, else] list (the decoder maps "case" → SqlStdOperatorTable.CASE).
        val call = PbFunctionCall.newBuilder().setOperation("case")
        e.branches.forEach { (whenExpr, thenExpr) ->
            call.addOperands(expr(whenExpr, inJoin))
            call.addOperands(expr(thenExpr, inJoin))
        }
        call.addOperands(e.elseExpr?.let { expr(it, inJoin) } ?: nullLiteralExpr())
        return PbExpression.newBuilder().setFunction(call).build()
    }

    /**
     * Map a join `on`-condition column's `left`/`right` port qualifier to the `$L`/`$R` wire tag
     * the translator decoder resolves against the correct join input. An unqualified column
     * (null port) gets no tag — it falls to a bare `field(name)` lookup over the combined join
     * row type, which is unambiguous only when the name is unique across both inputs.
     */
    private fun joinInputTag(port: String?): String? =
        when (port) {
            PortNames.LEFT -> LEFT_INPUT_TAG
            PortNames.RIGHT -> RIGHT_INPUT_TAG
            else -> null
        }

    private fun nullLiteralExpr(): PbExpression =
        PbExpression.newBuilder().setLiteral(PbLiteral.newBuilder().setIsNull(true)).build()

    private fun literal(v: LiteralValue): PbLiteral =
        when (v) {
            is LiteralValue.Str ->
                PbLiteral
                    .newBuilder()
                    .setStringValue(v.value)
                    .setType("text")
                    .build()
            is LiteralValue.Bool ->
                PbLiteral
                    .newBuilder()
                    .setBoolValue(v.value)
                    .setType("bool")
                    .build()
            LiteralValue.Null -> PbLiteral.newBuilder().setIsNull(true).build()
            is LiteralValue.Num -> {
                val raw = v.raw
                if (raw.contains('.') || raw.contains('e') || raw.contains('E')) {
                    PbLiteral
                        .newBuilder()
                        .setFloatValue(raw.toDouble())
                        .setType("float")
                        .build()
                } else {
                    PbLiteral
                        .newBuilder()
                        .setIntValue(raw.toLong())
                        .setType("int")
                        .build()
                }
            }
        }

    /** CatalogId `.name` → plan.v1 operation string (decoder vocabulary in wire/Expressions.kt). */
    private fun operation(call: FunctionCall): String {
        val name = call.function.name
        return OP_MAP[name] ?: name
    }

    private fun aggName(call: AggregateCall): String {
        val name = call.function.name
        return when (name) {
            "sum", "count", "avg", "min", "max" -> name
            else ->
                throw TtrpEmitException(
                    EmitDiagnosticId.UNSUPPORTED_NODE,
                    detail = "aggregate function '$name' is not in the plan.v1 wire format",
                    location = call.location,
                )
        }
    }

    companion object {
        /**
         * Synthetic `source_alias` markers routing a join-condition [ColumnRef] into the left/right
         * input on decode. These MIRROR the wire contract owned by the published translator
         * (`org.tatrman.translator.wire.Expressions.LEFT_INPUT_TAG`/`RIGHT_INPUT_TAG`); kept as
         * local constants here for the same reason the operator vocabulary below is inlined — the
         * `plan.v1` wire strings are a contract, not a shared code dependency.
         */
        const val LEFT_INPUT_TAG: String = "\$L"
        const val RIGHT_INPUT_TAG: String = "\$R"

        /** TTR-P operator surface name → plan.v1 operation string (see decoder `operatorFor`). */
        private val OP_MAP =
            mapOf(
                "eq" to "eq",
                "neq" to "ne",
                "lt" to "lt",
                "lte" to "le",
                "gt" to "gt",
                "gte" to "ge",
                "and" to "and",
                "or" to "or",
                "not" to "not",
                "add" to "add",
                "sub" to "sub",
                "mul" to "mul",
                "div" to "div",
                "neg" to "-",
            )
    }
}
