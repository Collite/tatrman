// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import org.tatrman.plan.v1.AggregateCall
import org.tatrman.plan.v1.AggregateNode
import org.tatrman.plan.v1.ColumnRef
import org.tatrman.plan.v1.Expression
import org.tatrman.plan.v1.FilterNode
import org.tatrman.plan.v1.FunctionCall
import org.tatrman.plan.v1.Literal
import org.tatrman.plan.v1.PlanNode
import org.tatrman.plan.v1.QualifiedName
import org.tatrman.plan.v1.SchemaCode
import org.tatrman.plan.v1.SubqueryExpression
import org.tatrman.plan.v1.TableScanNode
import org.tatrman.ttr.md.resolve.CanonicalPath
import org.tatrman.ttr.md.resolve.Coordinate
import org.tatrman.ttr.md.resolve.MemberRef
import org.tatrman.ttr.md.resolve.PathShape
import org.tatrman.ttr.md.resolve.Selector
import org.tatrman.ttr.semantics.md.AggKind
import org.tatrman.ttr.semantics.md.AttrBinding
import org.tatrman.ttr.semantics.md.BindingShape
import org.tatrman.ttr.semantics.md.CubeletBinding
import org.tatrman.ttr.semantics.md.MdBindings
import org.tatrman.ttr.semantics.md.MeasureBinding

/**
 * MD dot-path S4-A — lower a resolved [CanonicalPath] to a `plan.v1` relational subtree per contracts
 * §8. The frontend (S3) resolves the raw `mdPath` to its canonical form + [PathShape]; this stage maps
 * that canonical form through the cubelet's [MdBindings] (the `md2db_*` physical binding) to the DB
 * nodes the translator already understands — **no new node kinds** (MDS5), no `MD` `SchemaCode`. The
 * shape mirrors [MapToPhysical]'s ER→DB rewrite: a compile-time logical read becomes a plain
 * `TableScan(DB)` → `Filter` → `Aggregate` tree.
 *
 * ## §8 mapping (this increment)
 *
 *   - cubelet → `TableScan` of the bound fact table.
 *   - `Pinned` coordinate → `Filter col = literal`; `MemberSet` → `col IN (…)`; `Range` → `col BETWEEN
 *     lo AND hi` (spelled `ge AND le`, the v1 wire has no BETWEEN operator).
 *   - `Star` (free dim) → the column becomes an `Aggregate` group-by key.
 *   - measure + agg → an `AggregateCall` over the measure column; **long-shape** adds a measure-code
 *     pre-`Filter` and aggregates the value column.
 *   - shape: scalar (no free dims) → aggregate-all (empty group keys); vector/sub-cubelet → group-by
 *     on the free dims' bound columns.
 *
 * ## Deferred (documented, not silently dropped)
 *
 *   - **Hop attributes** (`AttrBinding.Hop`, map-mediated drill columns) → the `f_sales ⋈ d_*` join
 *     path — S4-A4. Reaching one here throws [MdLoweringException] `md/hop-unsupported`.
 *   - **`viaCalc` coordinates** (computed attribute via `MD_CALC_CATALOG`) — the calc SQL expression
 *     is S4-A5-adjacent (catalogue seat). Throws `md/calc-unsupported`.
 *   - **Journaling view** (invalidate/diff wrap of the Load, R31) — S4-A4b.
 *   - **Multi-source cubelets** — [MdBindings] already keeps last-def-wins (S4-A4).
 *
 * [lowerScalar] wraps the subtree as a scalar [SubqueryExpression] for an MD path in scalar expression
 * position (the canonical S3 usage, e.g. a `filter(...)` predicate operand); [lower] returns the bare
 * subtree for a path that is a whole read (an assignment RHS).
 */
class MdPathLowering(
    private val bindings: MdBindings,
) {
    /** Lower [path] (with its inferred [shape]) to the §8 relational subtree. */
    fun lower(
        path: CanonicalPath,
        shape: PathShape,
    ): PlanNode {
        val binding =
            bindings.cubelets[path.cubelet]
                ?: throw MdLoweringException(
                    "md/unbound-cubelet",
                    "cubelet '${path.cubelet}' has no md2db_cubelet binding",
                )
        val measure =
            binding.measures[path.measure]
                ?: throw MdLoweringException(
                    "md/unbound-measure",
                    "measure '${path.measure}' is not bound in cubelet '${path.cubelet}'",
                )

        val groupKeys = mutableListOf<String>()
        val conditions = mutableListOf<Expression>()

        // Long shape: the measure is one code among many in a shared value column — pre-Filter on it.
        val longShape = binding.shape as? BindingShape.Long
        if (longShape != null && measure is MeasureBinding.Code) {
            conditions += eq(colRef(longShape.codeColumn), strLit(measure.code))
        }

        for (coord in path.coordinates) {
            val column = columnFor(binding, coord)
            when (val sel = coord.selector) {
                is Selector.Pinned -> conditions += eq(colRef(column), memberLit(sel.member))
                is Selector.MemberSet -> conditions += inList(colRef(column), sel.members.map { memberLit(it) })
                is Selector.Range ->
                    conditions +=
                        and(
                            ge(colRef(column), memberLit(sel.lo)),
                            le(colRef(column), memberLit(sel.hi)),
                        )
                Selector.Star -> groupKeys += column
            }
        }

        var node = tableScan(binding, scanColumns(binding, path, groupKeys))
        if (conditions.isNotEmpty()) node = filter(node, conjoin(conditions))
        return aggregate(node, groupKeys, path, binding, measure)
    }

    /** Lower [path] as a scalar subquery [Expression] — MD path in scalar position (§8, S3 usage). */
    fun lowerScalar(
        path: CanonicalPath,
        shape: PathShape,
    ): Expression =
        Expression
            .newBuilder()
            .setSubquery(
                SubqueryExpression
                    .newBuilder()
                    .setSubquery(lower(path, shape))
                    .setKind(SCALAR),
            ).setResultType("float") // v1 measures are numeric (D12)
            .build()

    // --- §8 relational nodes ---------------------------------------------------------------------

    private fun tableScan(
        binding: CubeletBinding,
        columns: List<String>,
    ): PlanNode {
        val scan =
            TableScanNode
                .newBuilder()
                .setTable(tableQname(binding.table))
        columns.forEach { scan.addOutputColumns(ColumnRef.newBuilder().setName(it)) }
        return PlanNode.newBuilder().setTableScan(scan).build()
    }

    private fun filter(
        input: PlanNode,
        condition: Expression,
    ): PlanNode =
        PlanNode
            .newBuilder()
            .setFilter(FilterNode.newBuilder().setInput(input).setCondition(condition))
            .build()

    private fun aggregate(
        input: PlanNode,
        groupKeys: List<String>,
        path: CanonicalPath,
        binding: CubeletBinding,
        measure: MeasureBinding,
    ): PlanNode {
        val measureColumn =
            when (val shape = binding.shape) {
                is BindingShape.Long -> shape.valueColumn
                BindingShape.Wide ->
                    (measure as? MeasureBinding.Column)?.column
                        ?: throw MdLoweringException(
                            "md/measure-shape-mismatch",
                            "wide cubelet '${path.cubelet}' binds measure '${path.measure}' by code, not column",
                        )
            }
        val agg =
            AggregateNode
                .newBuilder()
                .setInput(input)
                .addAggregates(
                    AggregateCall
                        .newBuilder()
                        .setFunction(aggName(path.agg))
                        .addArgs(ColumnRef.newBuilder().setName(measureColumn))
                        .setAlias(path.measure),
                )
        groupKeys.forEach { agg.addGroupKeys(ColumnRef.newBuilder().setName(it)) }
        return PlanNode.newBuilder().setAggregate(agg).build()
    }

    // --- binding lookups -------------------------------------------------------------------------

    /** The physical column backing [coord]'s attribute; hops/calcs are deferred (see class KDoc). */
    private fun columnFor(
        binding: CubeletBinding,
        coord: Coordinate,
    ): String {
        if (coord.viaCalc != null) {
            throw MdLoweringException(
                "md/calc-unsupported",
                "computed coordinate '${coord.attribute}' via '${coord.viaCalc}' is S4-A5 (not yet lowered)",
            )
        }
        return when (val bound = binding.attributes[coord.attribute]) {
            is AttrBinding.Column -> bound.column
            is AttrBinding.Hop ->
                throw MdLoweringException(
                    "md/hop-unsupported",
                    "map-mediated attribute '${coord.attribute}' (via '${bound.via}') is S4-A4 (join not yet lowered)",
                )
            null ->
                throw MdLoweringException(
                    "md/unbound-attribute",
                    "attribute '${coord.attribute}' is not bound in cubelet '${binding.cubelet}'",
                )
        }
    }

    /** Scan projection: the coordinate columns, the measure/value + code columns, in a stable order. */
    private fun scanColumns(
        binding: CubeletBinding,
        path: CanonicalPath,
        groupKeys: List<String>,
    ): List<String> {
        val cols = linkedSetOf<String>()
        path.coordinates.forEach { c ->
            (binding.attributes[c.attribute] as? AttrBinding.Column)?.let { cols += it.column }
        }
        cols += groupKeys
        when (val shape = binding.shape) {
            is BindingShape.Long -> {
                cols += shape.codeColumn
                cols += shape.valueColumn
            }
            BindingShape.Wide ->
                (binding.measures[path.measure] as? MeasureBinding.Column)?.let { cols += it.column }
        }
        return cols.toList()
    }

    // --- expression + qname builders -------------------------------------------------------------

    /**
     * Parse a binding's physical table ref (`db.dbo.f_sales`, or a 2-part `dbo.f_sales`) into a
     * `QualifiedName`. MD reads always target `db` tables (§8), so the schema code is DB regardless of
     * a leading `db.` token — the token is only stripped, never dispatched on.
     */
    private fun tableQname(ref: String): QualifiedName {
        val parts = ref.split('.')
        val (namespace, name) =
            when (parts.size) {
                3 -> parts[1] to parts[2]
                2 -> parts[0] to parts[1]
                else -> throw MdLoweringException("md/bad-table-ref", "cannot parse table ref '$ref'")
            }
        return QualifiedName
            .newBuilder()
            .setSchemaCode(SchemaCode.DB)
            .setNamespace(namespace)
            .setName(name)
            .build()
    }

    private fun colRef(name: String): Expression =
        Expression.newBuilder().setColumnRef(ColumnRef.newBuilder().setName(name)).build()

    private fun memberLit(m: MemberRef): Expression {
        val text = m.text
        val asLong = text.toLongOrNull()
        return if (asLong != null) {
            Expression
                .newBuilder()
                .setLiteral(Literal.newBuilder().setIntValue(asLong).setType("int"))
                .build()
        } else {
            strLit(text)
        }
    }

    private fun strLit(s: String): Expression =
        Expression
            .newBuilder()
            .setLiteral(Literal.newBuilder().setStringValue(s).setType("text"))
            .build()

    private fun binOp(
        op: String,
        lhs: Expression,
        rhs: Expression,
    ): Expression =
        Expression
            .newBuilder()
            .setFunction(
                FunctionCall
                    .newBuilder()
                    .setOperation(op)
                    .addOperands(lhs)
                    .addOperands(rhs),
            ).build()

    private fun eq(
        lhs: Expression,
        rhs: Expression,
    ) = binOp("eq", lhs, rhs)

    private fun ge(
        lhs: Expression,
        rhs: Expression,
    ) = binOp("ge", lhs, rhs)

    private fun le(
        lhs: Expression,
        rhs: Expression,
    ) = binOp("le", lhs, rhs)

    private fun and(
        lhs: Expression,
        rhs: Expression,
    ) = binOp("and", lhs, rhs)

    private fun inList(
        column: Expression,
        items: List<Expression>,
    ): Expression =
        Expression
            .newBuilder()
            .setFunction(
                FunctionCall
                    .newBuilder()
                    .setOperation("in")
                    .addOperands(column)
                    .addAllOperands(items),
            ).build()

    /** Fold conjuncts left-to-right into a single `and` tree (a lone condition passes through). */
    private fun conjoin(conditions: List<Expression>): Expression = conditions.reduce { acc, c -> and(acc, c) }

    private fun aggName(agg: AggKind): String =
        when (agg) {
            AggKind.SUM -> "sum"
            AggKind.AVG -> "avg"
            AggKind.MIN -> "min"
            AggKind.MAX -> "max"
            AggKind.COUNT -> "count"
        }

    private companion object {
        const val SCALAR = "scalar"
    }
}

/** A structural failure lowering an MD path (unbound symbol, or a deferred §8 case). */
class MdLoweringException(
    val code: String,
    message: String,
) : RuntimeException(message)
