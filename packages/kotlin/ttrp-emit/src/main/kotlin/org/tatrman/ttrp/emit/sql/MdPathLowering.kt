// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import org.tatrman.plan.v1.AggregateCall
import org.tatrman.plan.v1.AggregateNode
import org.tatrman.plan.v1.ColumnRef
import org.tatrman.plan.v1.Expression
import org.tatrman.plan.v1.FilterNode
import org.tatrman.plan.v1.FunctionCall
import org.tatrman.plan.v1.JoinNode
import org.tatrman.plan.v1.JoinType
import org.tatrman.plan.v1.Literal
import org.tatrman.plan.v1.PlanNode
import org.tatrman.plan.v1.QualifiedName
import org.tatrman.plan.v1.SchemaCode
import org.tatrman.plan.v1.SubqueryExpression
import org.tatrman.plan.v1.TableScanNode
import org.tatrman.translator.framework.ModelColumn
import org.tatrman.translator.framework.ModelTable
import org.tatrman.ttr.md.resolve.CanonicalPath
import org.tatrman.ttr.md.resolve.MemberRef
import org.tatrman.ttr.md.resolve.PathShape
import org.tatrman.ttr.md.resolve.Selector
import org.tatrman.ttr.semantics.md.AggKind
import org.tatrman.ttr.semantics.md.AttrBinding
import org.tatrman.ttr.semantics.md.BindingShape
import org.tatrman.ttr.semantics.md.CubeletBinding
import org.tatrman.ttr.semantics.md.MdBindings
import org.tatrman.ttr.semantics.md.MdModel
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
 *   - **Hop attribute** (`AttrBinding.Hop`, a map-mediated drill column, S4-A4) → an inner `Join` of the
 *     fact table to the hop's backing table, on the grain key: the fact-side key is the cubelet's bound
 *     grain column for that dimension; the hop-side key is the grain attribute's domain source column
 *     (`md2db_domain`). Requires the [MdModel] (grain + attribute→domain); without it a hop is
 *     `md/hop-unsupported`. The hop coordinate then filters/groups on the joined table's column.
 *   - shape: scalar (no free dims) → aggregate-all (empty group keys); vector/sub-cubelet → group-by
 *     on the free dims' bound columns.
 *
 * ## Deferred (documented, not silently dropped)
 *
 *   - **`viaCalc` coordinates** (computed attribute via `MD_CALC_CATALOG`) — the calc SQL expression
 *     is S4-A5-adjacent (catalogue seat). Throws `md/calc-unsupported`.
 *   - **Journaling view** (invalidate/diff wrap of the Load, R31) — S4-A4b.
 *   - **Multi-source cubelets** — [MdBindings] already keeps last-def-wins (S4-A4).
 *   - **End-to-end SQL** — the produced `TableScan`s aren't yet registered in the island's ModelHandle,
 *     so an MD read lowers to plan.v1 but does not unparse to SQL yet (a follow-up).
 *
 * [lowerScalar] wraps the subtree as a scalar [SubqueryExpression] for an MD path in scalar expression
 * position (the canonical S3 usage, e.g. a `filter(...)` predicate operand); [lower] returns the bare
 * subtree for a path that is a whole read (an assignment RHS).
 *
 * [model] is needed only to derive hop join keys (grain + attribute→domain); hop-free reads lower with
 * a null model.
 */
class MdPathLowering(
    private val bindings: MdBindings,
    private val model: MdModel? = null,
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
        val factColumns = linkedSetOf<String>()
        val hops = linkedMapOf<String, HopJoin>() // keyed by hop table → dedup joins across coordinates

        // Long shape: the measure is one code among many in a shared value column — pre-Filter on it
        // (the code column joins the scan projection below, after the grain columns).
        val longShape = binding.shape as? BindingShape.Long
        if (longShape != null && measure is MeasureBinding.Code) {
            conditions += eq(colRef(longShape.codeColumn), strLit(measure.code))
        }

        for (coord in path.coordinates) {
            if (coord.viaCalc != null) {
                throw MdLoweringException(
                    "md/calc-unsupported",
                    "computed coordinate '${coord.attribute}' via '${coord.viaCalc}' is S4-A5 (not yet lowered)",
                )
            }
            when (val bound = binding.attributes[coord.attribute]) {
                is AttrBinding.Column -> {
                    factColumns += bound.column
                    applySelector(bound.column, coord.selector, conditions, groupKeys)
                }
                is AttrBinding.Hop -> {
                    val join = hops.getOrPut(bound.fromTable) { hopJoin(binding, coord.dimension, bound) }
                    factColumns += join.factKey
                    join.columns += bound.fromColumn
                    applySelector(bound.fromColumn, coord.selector, conditions, groupKeys)
                }
                null ->
                    throw MdLoweringException(
                        "md/unbound-attribute",
                        "attribute '${coord.attribute}' is not bound in cubelet '${binding.cubelet}'",
                    )
            }
        }
        val measureColumn = measureColumn(binding, path, measure)
        // Projection tail: (long) the measure-code column then the value column; (wide) the measure column.
        (binding.shape as? BindingShape.Long)?.let { factColumns += it.codeColumn }
        factColumns += measureColumn

        var node = tableScan(binding.table, factColumns.toList())
        for (hj in hops.values) node = join(node, hj)
        if (conditions.isNotEmpty()) node = filter(node, conjoin(conditions))
        return aggregate(node, groupKeys, path, measureColumn)
    }

    /** Apply one coordinate's selector: pinned/set/range add a [conditions] conjunct; star adds a group key. */
    private fun applySelector(
        column: String,
        selector: Selector,
        conditions: MutableList<Expression>,
        groupKeys: MutableList<String>,
    ) {
        when (selector) {
            is Selector.Pinned -> conditions += eq(colRef(column), memberLit(selector.member))
            is Selector.MemberSet -> conditions += inList(colRef(column), selector.members.map { memberLit(it) })
            is Selector.Range ->
                conditions +=
                    and(
                        ge(colRef(column), memberLit(selector.lo)),
                        le(colRef(column), memberLit(selector.hi)),
                    )
            Selector.Star -> groupKeys += column
        }
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

    /**
     * The physical `db` tables [path] reads — the fact table plus any hop backing tables — as
     * [ModelTable]s ready to register in the island's `ModelHandle` so the translator can unparse the
     * MD read to SQL. Reuses [lower] and reads the tables straight off its `TableScan` leaves (so the
     * registered schema always matches the lowered plan exactly); column types come from the binding +
     * [model] (measures numeric so `sum()` type-checks, attribute/domain columns by their domain type).
     */
    fun referencedTables(
        path: CanonicalPath,
        shape: PathShape,
    ): List<ModelTable> {
        val binding = bindings.cubelets[path.cubelet] ?: return emptyList()
        val typeByColumn = columnTypeSpellings(binding)
        return collectTableScans(lower(path, shape)).map { scan ->
            ModelTable(
                qname = scan.table,
                columns =
                    scan.outputColumnsList.map {
                        ModelColumn(it.name, TypeMapping.surfaceType(typeByColumn[it.name] ?: "text"))
                    },
            )
        }
    }

    /** Column name → db-type spelling for [binding]'s reachable columns (measures numeric; else domain type). */
    private fun columnTypeSpellings(binding: CubeletBinding): Map<String, String> {
        val types = mutableMapOf<String, String>()
        binding.measures.values.forEach { if (it is MeasureBinding.Column) types[it.column] = "decimal" }
        (binding.shape as? BindingShape.Long)?.let {
            types[it.valueColumn] = "decimal"
            types[it.codeColumn] = "text"
        }
        binding.attributes.forEach { (attr, bound) ->
            val spelling = domainTypeOf(model?.underlyingDomain(attr))
            when (bound) {
                is AttrBinding.Column -> types[bound.column] = spelling
                is AttrBinding.Hop -> types[bound.fromColumn] = spelling
            }
        }
        // Domain source columns are the hop join keys on the backing tables.
        bindings.domains.forEach { (domain, source) -> types[source.column] = domainTypeOf(domain) }
        return types
    }

    private fun domainTypeOf(domain: String?): String = domain?.let { model?.domains?.get(it)?.type } ?: "text"

    private fun collectTableScans(node: PlanNode): List<TableScanNode> =
        when (node.nodeCase) {
            PlanNode.NodeCase.TABLE_SCAN -> listOf(node.tableScan)
            PlanNode.NodeCase.FILTER -> collectTableScans(node.filter.input)
            PlanNode.NodeCase.AGGREGATE -> collectTableScans(node.aggregate.input)
            PlanNode.NodeCase.JOIN -> collectTableScans(node.join.left) + collectTableScans(node.join.right)
            else -> emptyList()
        }

    // --- §8 relational nodes ---------------------------------------------------------------------

    private fun tableScan(
        tableRef: String,
        columns: List<String>,
    ): PlanNode {
        val scan =
            TableScanNode
                .newBuilder()
                .setTable(tableQname(tableRef))
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

    /** Inner-join [left] (the fact tree so far) to [hop]'s backing table on the grain key. */
    private fun join(
        left: PlanNode,
        hop: HopJoin,
    ): PlanNode {
        val right = tableScan(hop.table, hop.columns.toList())
        // $L/$R source-alias tags route each equijoin side to the correct input on decode (the wire
        // join contract, mirrored from PlanNodeBuilder) so a future SQL unparse disambiguates the keys.
        val condition =
            eq(
                colRef(hop.factKey, PlanNodeBuilder.LEFT_INPUT_TAG),
                colRef(hop.hopKey, PlanNodeBuilder.RIGHT_INPUT_TAG),
            )
        return PlanNode
            .newBuilder()
            .setJoin(
                JoinNode
                    .newBuilder()
                    .setLeft(left)
                    .setRight(right)
                    .setJoinType(JoinType.INNER)
                    .setCondition(condition),
            ).build()
    }

    private fun aggregate(
        input: PlanNode,
        groupKeys: List<String>,
        path: CanonicalPath,
        measureColumn: String,
    ): PlanNode {
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

    /** The aggregate operand column: the long value column, else the wide measure's own column. */
    private fun measureColumn(
        binding: CubeletBinding,
        path: CanonicalPath,
        measure: MeasureBinding,
    ): String =
        when (val shape = binding.shape) {
            is BindingShape.Long -> shape.valueColumn
            BindingShape.Wide ->
                (measure as? MeasureBinding.Column)?.column
                    ?: throw MdLoweringException(
                        "md/measure-shape-mismatch",
                        "wide cubelet '${path.cubelet}' binds measure '${path.measure}' by code, not column",
                    )
        }

    /**
     * Derive the inner join reaching a hop attribute on [dimension]: the fact-side key is the cubelet's
     * bound grain column for that dimension; the hop-side key is the grain attribute's domain source
     * column ([MdBindings.domains]). Needs the [MdModel] for grain + attribute→domain resolution.
     */
    private fun hopJoin(
        binding: CubeletBinding,
        dimension: String,
        hop: AttrBinding.Hop,
    ): HopJoin {
        val model =
            model ?: throw MdLoweringException(
                "md/hop-unsupported",
                "hop attribute (via '${hop.via}') needs the MdModel to derive the join key (not injected)",
            )
        val grain =
            binding.attributes.entries.firstOrNull { (attr, bound) ->
                attr.substringBefore('.') == dimension && bound is AttrBinding.Column
            } ?: throw MdLoweringException(
                "md/hop-no-grain-key",
                "no fact-bound grain column for dimension '$dimension' to join the hop on",
            )
        val factKey = (grain.value as AttrBinding.Column).column
        val domain =
            model.underlyingDomain(grain.key)
                ?: throw MdLoweringException(
                    "md/hop-no-domain",
                    "grain attribute '${grain.key}' has no resolvable domain",
                )
        val source =
            bindings.domains[domain]
                ?: throw MdLoweringException(
                    "md/hop-no-domain-source",
                    "domain '$domain' has no md2db_domain source table to join the hop to",
                )
        if (source.table != hop.fromTable) {
            throw MdLoweringException(
                "md/hop-table-mismatch",
                "hop table '${hop.fromTable}' != domain '$domain' source table '${source.table}'",
            )
        }
        return HopJoin(
            table = hop.fromTable,
            factKey = factKey,
            hopKey = source.column,
            columns = linkedSetOf(source.column),
        )
    }

    /** A pending fact→hop-table inner join: which [table], the [factKey]/[hopKey] equijoin, and the hop [columns] it must project. */
    private class HopJoin(
        val table: String,
        val factKey: String,
        val hopKey: String,
        val columns: MutableSet<String>,
    )

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

    private fun colRef(
        name: String,
        sourceAlias: String,
    ): Expression =
        Expression
            .newBuilder()
            .setColumnRef(ColumnRef.newBuilder().setName(name).setSourceAlias(sourceAlias))
            .build()

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
