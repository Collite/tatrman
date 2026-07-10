package org.tatrman.ttrp.emit.sql

import org.tatrman.translate.v1.SqlDialect
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.expr.AggregateCall
import org.tatrman.ttrp.expr.CatalogId
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.expr.IsNull
import org.tatrman.ttrp.expr.Literal
import org.tatrman.ttrp.expr.LiteralValue
import org.tatrman.ttrp.graph.model.Aggregate
import org.tatrman.ttrp.graph.model.Aggregation
import org.tatrman.ttrp.graph.model.Filter
import org.tatrman.ttrp.graph.model.Join
import org.tatrman.ttrp.graph.model.JoinType
import org.tatrman.ttrp.graph.model.Project
import org.tatrman.ttrp.graph.model.Sort

/** Terse builders for graph nodes + expressions + [EmitNode]s used by the SQL golden corpus. */
object EmitFixtures {
    val loc = SourceLocation.UNKNOWN

    fun col(
        name: String,
        port: String? = null,
    ): ColumnRef = ColumnRef(port, name, loc)

    fun str(v: String): Literal = Literal(LiteralValue.Str(v), loc)

    fun num(v: String): Literal = Literal(LiteralValue.Num(v), loc)

    fun fn(
        id: String,
        vararg args: Expression,
    ): FunctionCall = FunctionCall(CatalogId(id), args.toList(), loc)

    fun isNotNull(e: Expression): IsNull = IsNull(e, negated = true, loc)

    fun agg(
        id: String,
        arg: Expression,
        distinct: Boolean = false,
    ): AggregateCall = AggregateCall(CatalogId(id), listOf(arg), distinct, loc)

    fun filter(
        id: String,
        label: String,
        pred: Expression,
    ): Filter = Filter(id, label, loc, pred)

    fun project(
        id: String,
        label: String,
        cols: List<Expression>,
    ): Project = Project(id, label, loc, cols)

    fun aggregate(
        id: String,
        label: String,
        groupBy: List<String>,
        aggregations: List<Aggregation>,
    ): Aggregate = Aggregate(id, label, loc, groupBy, aggregations)

    fun sort(
        id: String,
        label: String,
        keys: List<String>,
    ): Sort = Sort(id, label, loc, keys)

    fun join(
        id: String,
        label: String,
        type: JoinType,
        on: Expression?,
    ): Join = Join(id, label, loc, type, on)

    fun cols(vararg pairs: Pair<String, String>): List<EmitColumn> = pairs.map { EmitColumn(it.first, it.second) }

    fun base(
        namespace: String,
        name: String,
        columns: List<EmitColumn>,
    ): EmitInput.BaseTable = EmitInput.BaseTable(namespace, name, columns)

    fun cteInput(
        producerNodeId: String,
        columns: List<EmitColumn>,
    ): EmitInput.Cte = EmitInput.Cte(producerNodeId, columns)

    /** A [CtePlanner] wired to a Postgres [TranslatorFacade] over an [IslandModelHandle]. */
    fun pgPlanner(): CtePlanner =
        CtePlanner { model ->
            TranslatorFacade(IslandModelHandle(model), SqlDialect.POSTGRESQL)
        }
}
