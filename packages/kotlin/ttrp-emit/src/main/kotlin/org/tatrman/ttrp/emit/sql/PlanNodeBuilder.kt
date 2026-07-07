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
import org.tatrman.ttrp.graph.model.Aggregate
import org.tatrman.ttrp.graph.model.Filter
import org.tatrman.ttrp.graph.model.Join
import org.tatrman.ttrp.graph.model.JoinType
import org.tatrman.ttrp.graph.model.Limit
import org.tatrman.ttrp.graph.model.Node
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
 * Join(inner/left/right/full). SEMI/ANTI joins and Intersect/Except have **no plan.v1
 * representation** (the wire `JoinType` enum stops at FULL) — they raise
 * [EmitDiagnosticId.UNSUPPORTED_NODE] and are a recorded Stage-3.1 deferral (no v1 hero SQL
 * island exercises them; see progress-phase-03.md).
 */
class PlanNodeBuilder {
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
        node.on?.let { b.condition = expr(it) }
        return PlanNode.newBuilder().setJoin(b).build()
    }

    // --- Expressions -----------------------------------------------------------------

    fun expr(e: Expression): PbExpression =
        when (e) {
            is ColumnRef -> {
                val ref = PbColumnRef.newBuilder().setName(e.column)
                e.port?.let { ref.sourceAlias = it }
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
                            .addAllOperands(e.args.map { expr(it) }),
                    ).build()
            is IsNull ->
                PbExpression
                    .newBuilder()
                    .setFunction(
                        PbFunctionCall
                            .newBuilder()
                            .setOperation(if (e.negated) "is_not_null" else "is_null")
                            .addOperands(expr(e.expr)),
                    ).build()
            is InList -> inList(e)
            is CaseWhen -> caseWhen(e)
            is Cast ->
                PbExpression
                    .newBuilder()
                    .setCast(
                        CastExpression
                            .newBuilder()
                            .setValue(expr(e.expr))
                            .setTargetType(e.target.canonical),
                    ).build()
            is AggregateCall ->
                throw TtrpEmitException(
                    EmitDiagnosticId.UNSUPPORTED_NODE,
                    detail = "AggregateCall is only legal inside an Aggregate node, not a scalar expression",
                    location = e.location,
                )
        }

    private fun inList(e: InList): PbExpression {
        val inCall =
            PbFunctionCall
                .newBuilder()
                .setOperation("in")
                .addOperands(expr(e.expr))
                .addAllOperands(e.items.map { expr(it) })
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

    private fun caseWhen(e: CaseWhen): PbExpression {
        // plan.v1 "case" rides a generic FunctionCall; operands are Calcite's flat
        // [when1, then1, …, else] list (the decoder maps "case" → SqlStdOperatorTable.CASE).
        val call = PbFunctionCall.newBuilder().setOperation("case")
        e.branches.forEach { (whenExpr, thenExpr) ->
            call.addOperands(expr(whenExpr))
            call.addOperands(expr(thenExpr))
        }
        call.addOperands(e.elseExpr?.let { expr(it) } ?: nullLiteralExpr())
        return PbExpression.newBuilder().setFunction(call).build()
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
