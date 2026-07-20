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
import org.tatrman.plan.v1.UnionNode
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
import org.tatrman.ttr.semantics.md.Journaling
import org.tatrman.ttr.semantics.md.MapKind
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
 *   - **Computed attribute** (`viaCalc`, a relative-time/calc coordinate, S4-A5) — two forms:
 *     - **case table** (`md2db_map` bound, e.g. `d_calendar`) → an inner `Join` on the `from`-domain
 *       column (the bound date grain), then filter/group on the case table's `to` column (e.g.
 *       `cal_month`) — identical to a hop from there on.
 *     - **inline** (no case table) → the calc applied as a standard `EXTRACT(UNIT FROM baseCol)`
 *       predicate on the base date column, spelled per dialect by the translator. Supported for the
 *       date-extraction family (month/year/quarter/day/week); truncation + chained rollup are deferred.
 *   - shape: scalar (no free dims) → aggregate-all (empty group keys); vector/sub-cubelet → group-by
 *     on the free dims' bound columns.
 *   - **Journaling view** (R31, S4-A4b/c) wraps the cubelet Load: overwrite → plain Load; invalidate →
 *     `Filter(Load, validColumn = true)` (SCD2 valid-flag read); diff → an inner `Aggregate` SUMming the
 *     measure per the cubelet's full grain (delta sum). Every §8 row composes on the wrapped view unchanged.
 *   - **Multi-source** cubelet (contracts §4.1, several `md2db_cubelet` defs → [CubeletBinding.sources]) →
 *     a `UNION ALL` of each source's journaled scan (same projected columns), beneath the joins/filter/
 *     aggregate — so the §8 rows compose on the union exactly as on a single scan.
 *
 * ## Deferred (documented, not silently dropped)
 *
 *   - **Non-extraction inline `viaCalc`** — truncation (`truncTo*`) needs a `date_trunc`-style op, and a
 *     chained rollup (`quarterOfMonth` over a *computed* month, whose base isn't fact-resident) needs
 *     nested calc: `md/calc-inline-unsupported` / `md/calc-no-base`. The extraction family lowers fully.
 *   - **Free (star) inline `viaCalc`** — a computed group key needs a projection; `md/calc-inline-star-unsupported`.
 *   - **Non-additive / long-shape diff** — the diff view sums deltas per grain (additive, wide only);
 *     a non-SUM read is `md/diff-nonadditive-unsupported`, long-shape diff `md/diff-long-unsupported`.
 *   - **Heterogeneous multi-source** — the `UNION ALL` assumes sources share column bindings (the
 *     partitioned-fact case); per-source column-name remapping (align-by-alias) is a follow-up.
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
        val hops = linkedMapOf<String, HopJoin>() // keyed by join identity → dedup joins across coordinates
        // T-R1-2: which dimensions are free (group-by) keys is the RESOLVED SHAPE's call, not the raw
        // selector's — an explicit agg (`.net.sum`) collapses the shape to scalar, so a Star coordinate
        // on a collapsed dimension is aggregated over, not grouped.
        val freeDims = shape.freeDims.toSet()

        // Long shape: the measure is one code among many in a shared value column — pre-Filter on it
        // (the code column joins the scan projection below, after the grain columns).
        val longShape = binding.shape as? BindingShape.Long
        if (longShape != null && measure is MeasureBinding.Code) {
            conditions += eq(colRef(longShape.codeColumn), strLit(measure.code))
        }

        for (coord in path.coordinates) {
            val isFree = coord.attribute in freeDims
            // T-L3: type a member literal by its coordinate's domain (a text domain keeps an all-digit
            // member like "0012" a string, leading zero intact), or lexically when no model is injected.
            val memberType = memberTypeOf(coord.attribute)
            // A computed (viaCalc) coordinate lowers either via the calc map's case table (a join +
            // drill, exactly like a hop) or, when no case table is bound, inline as a date-extraction
            // expression (`EXTRACT(UNIT FROM baseCol)`) the coordinate then filters on.
            // The calc function to reach this coordinate: an explicit resolver `viaCalc` (relative-time
            // tokens like `lastMonth`), or one **derived** here for an authored coarser-than-grain
            // attribute the resolver left unqualified (`sales.year.2025` → the `date_to_year` calc). The
            // resolver can't attach it — the calc depends on the cubelet grain, which is a binding fact —
            // so a grain-direct attribute stays bound and [derivedCalcFor] returns null (no calc).
            val viaCalc = coord.viaCalc ?: derivedCalcFor(coord.attribute, binding)
            if (viaCalc != null) {
                when (val calc = calcCoordinate(binding, viaCalc, coord.dimension, coord.attribute)) {
                    is CalcLowering.CaseTable -> {
                        val join = hops.getOrPut(calc.hop.signature()) { calc.hop }
                        factColumns += join.factKey
                        join.columns += calc.drillColumn
                        applySelector(calc.drillColumn, coord.selector, isFree, memberType, conditions, groupKeys)
                    }
                    is CalcLowering.Inline -> {
                        factColumns += calc.baseColumn
                        // A free inline calc coordinate needs a computed group key (a projection) — a follow-up;
                        // a non-free Star just aggregates over all (no filter). An extraction result is an int.
                        if (isFree) {
                            throw MdLoweringException(
                                "md/calc-inline-star-unsupported",
                                "a free inline calc coordinate needs a computed group key (projection) — a follow-up",
                            )
                        }
                        if (coord.selector !is Selector.Star) {
                            applySelectorOn(
                                calc.expr,
                                coord.selector,
                                "int",
                                conditions,
                            )
                        }
                    }
                }
                continue
            }
            when (val bound = binding.attributes[coord.attribute]) {
                is AttrBinding.Column -> {
                    factColumns += bound.column
                    applySelector(bound.column, coord.selector, isFree, memberType, conditions, groupKeys)
                }
                is AttrBinding.Hop -> {
                    val hj = hopJoin(binding, coord.dimension, bound)
                    val join = hops.getOrPut(hj.signature()) { hj }
                    factColumns += join.factKey
                    join.columns += bound.fromColumn
                    applySelector(bound.fromColumn, coord.selector, isFree, memberType, conditions, groupKeys)
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

        var node = journaledBase(binding, factColumns.toList(), measureColumn, path.agg)
        for (hj in hops.values) node = join(node, hj)
        if (conditions.isNotEmpty()) node = filter(node, conjoin(conditions))
        return aggregate(node, groupKeys, path, measureColumn)
    }

    /**
     * The cubelet's base read view — the [journaledScan] of its fact table, or (multi-source, contracts
     * §4.1) a `UNION ALL` of the journaled scans of all [CubeletBinding.sources]. The sources share
     * column bindings (they agree on grain + measures), so each scan projects the same [factColumns] and
     * the union row types align; every §8 row (joins, filter, aggregate) then composes on the union
     * unchanged. `UNION ALL` (not `UNION`) — partitioned fact rows must not be deduplicated.
     */
    private fun journaledBase(
        binding: CubeletBinding,
        factColumns: List<String>,
        measureColumn: String,
        agg: AggKind,
    ): PlanNode {
        val tables = binding.sources.ifEmpty { listOf(binding.table) }
        val scans = tables.map { journaledScan(binding, it, factColumns, measureColumn, agg) }
        return scans.singleOrNull() ?: PlanNode
            .newBuilder()
            .setUnion(UnionNode.newBuilder().setAll(true).addAllInputs(scans))
            .build()
    }

    /**
     * A single source table's Load, wrapped per its journaling mode (R31, §8) — the point where every §8
     * row composes on top of the *read view* of the fact [table]:
     *   - **overwrite** → the plain `TableScan` (golden unchanged).
     *   - **invalidate** → `Filter(TableScan, validColumn = true)` — only currently-valid rows (the
     *     SCD2 valid-flag read; the temporal `valid_from ≤ asof < valid_to` variant lands with the
     *     technical-column roles in S5C).
     *   - **diff** → an inner `Aggregate` that SUMs the measure per the cubelet's full grain ([diffScan]):
     *     a diff-journaled table stores per-grain deltas, so its current value is the delta sum. The §8
     *     rows compose on the resulting one-row-per-grain view exactly as on a plain Load.
     */
    private fun journaledScan(
        binding: CubeletBinding,
        table: String,
        factColumns: List<String>,
        measureColumn: String,
        agg: AggKind,
    ): PlanNode =
        when (val journaling = binding.journaling) {
            Journaling.Overwrite -> tableScan(table, factColumns)
            is Journaling.Invalidate ->
                filter(
                    tableScan(table, (factColumns + journaling.validColumn).distinct()),
                    eq(colRef(journaling.validColumn), boolLit(true)),
                )
            Journaling.Diff -> diffScan(binding, table, measureColumn, agg)
        }

    /**
     * The diff read view (R31, §8): a diff-journaled fact table stores per-grain **deltas**, so its
     * current value is the SUM of deltas per grain key. Wrap the Load in an inner `Aggregate` that groups
     * by the cubelet's full grain and sums the measure — aliased back to the measure column so every §8
     * row (coordinate `Filter`s on grain columns, hop/calc joins on grain keys, the outer `Aggregate`)
     * composes on the one-row-per-grain view unchanged. Additive-only: a non-SUM read over a diff cubelet
     * is ill-defined (`md/diff-nonadditive-unsupported`); long-shape diff is a follow-up
     * (`md/diff-long-unsupported`).
     */
    private fun diffScan(
        binding: CubeletBinding,
        table: String,
        measureColumn: String,
        agg: AggKind,
    ): PlanNode {
        if (agg != AggKind.SUM) {
            throw MdLoweringException(
                "md/diff-nonadditive-unsupported",
                "a '$agg' read over diff-journaled cubelet '${binding.cubelet}' is ill-defined; " +
                    "the diff view sums deltas per grain (additive only)",
            )
        }
        if (binding.shape is BindingShape.Long) {
            throw MdLoweringException(
                "md/diff-long-unsupported",
                "diff journaling over a long-shape cubelet ('${binding.cubelet}') is a follow-up",
            )
        }
        val grainColumns = diffGrainColumns(binding)
        val aggBuilder =
            AggregateNode
                .newBuilder()
                .setInput(tableScan(table, grainColumns + measureColumn))
                .addAggregates(
                    AggregateCall
                        .newBuilder()
                        .setFunction("sum")
                        .addArgs(ColumnRef.newBuilder().setName(measureColumn))
                        .setAlias(measureColumn),
                )
        grainColumns.forEach { aggBuilder.addGroupKeys(ColumnRef.newBuilder().setName(it)) }
        return PlanNode.newBuilder().setAggregate(aggBuilder).build()
    }

    /** The cubelet's grain columns (fact-resident, Column-bound) — the diff SUM group key. Needs the model. */
    private fun diffGrainColumns(binding: CubeletBinding): List<String> {
        val model =
            model ?: throw MdLoweringException(
                "md/diff-unsupported",
                "diff journaling needs the MdModel to derive cubelet '${binding.cubelet}' grain",
            )
        val grain =
            model.cubelets[binding.cubelet]?.grain
                ?: throw MdLoweringException("md/diff-no-grain", "cubelet '${binding.cubelet}' has no declared grain")
        return grain.map { attr ->
            (binding.attributes[attr] as? AttrBinding.Column)?.column
                ?: throw MdLoweringException(
                    "md/diff-grain-not-column",
                    "diff cubelet '${binding.cubelet}' grain attribute '$attr' is not bound to a fact column",
                )
        }
    }

    /**
     * Apply one coordinate's selector on a bound [column]. Whether the dimension is free (a group-by
     * key) is [isFree], the RESOLVED SHAPE's call (T-R1-2), not the selector's — so an explicit-agg
     * collapse aggregates a `Star` over instead of grouping. A restricting selector (pinned/set/range)
     * ALSO adds its filter conjunct, so a restricted-but-free dimension (`name.{Kaufland, Lidl}`) both
     * filters and groups (T-L1); a plain `Star` only groups. Literals are typed by [memberType] (T-L3).
     */
    private fun applySelector(
        column: String,
        selector: Selector,
        isFree: Boolean,
        memberType: String?,
        conditions: MutableList<Expression>,
        groupKeys: MutableList<String>,
    ) {
        if (isFree) groupKeys += column
        if (selector !is Selector.Star) applySelectorOn(colRef(column), selector, memberType, conditions)
    }

    /**
     * Apply a restricting selector on an arbitrary scalar [lhs] (a column ref, or an inline calc
     * expression like `extract(YEAR FROM sale_date)`): pinned → `eq`, set → `in`, range → `ge AND le`;
     * literals are typed by [memberType] (T-L3). [Selector.Star] never reaches here — a free dimension
     * is grouped by [applySelector] and a free inline calc is rejected upstream — so it is a defensive
     * error, not a reachable path.
     */
    private fun applySelectorOn(
        lhs: Expression,
        selector: Selector,
        memberType: String?,
        conditions: MutableList<Expression>,
    ) {
        when (selector) {
            is Selector.Pinned -> conditions += eq(lhs, memberLit(selector.member, memberType))
            is Selector.MemberSet -> conditions += inList(lhs, selector.members.map { memberLit(it, memberType) })
            is Selector.Range ->
                conditions +=
                    and(ge(lhs, memberLit(selector.lo, memberType)), le(lhs, memberLit(selector.hi, memberType)))
            Selector.Star ->
                throw MdLoweringException(
                    "md/calc-inline-star-unsupported",
                    "a free (star) inline calc coordinate needs a computed group key (projection) — a follow-up",
                )
        }
    }

    /** Lower [path] as a scalar subquery [Expression] — MD path in scalar position (§8, S3 usage). */
    fun lowerScalar(
        path: CanonicalPath,
        shape: PathShape,
    ): Expression {
        // T-L5: a scalar position needs a single value. A path with free (group-by) dimensions returns a
        // column × row set — reject it here (fail closed) rather than emit an invalid multi-row subquery.
        // The frontend's MD-008 guards predicate roots; this is the backstop for any other scalar site.
        if (shape.freeDims.isNotEmpty()) {
            throw MdLoweringException(
                "md/non-scalar-in-scalar-position",
                "MD path on cubelet '${path.cubelet}' has free dimensions ${shape.freeDims} — it is not scalar",
            )
        }
        return Expression
            .newBuilder()
            .setSubquery(
                SubqueryExpression
                    .newBuilder()
                    .setSubquery(lower(path, shape))
                    .setKind(SCALAR),
            ).setResultType("float") // v1 measures are numeric (D12)
            .build()
    }

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
        // Calc-map case tables (viaCalc): each column is typed by its bound `from`/`to` domain.
        bindings.maps.values.forEach { mapBinding ->
            mapBinding.columns.forEach { (domainRef, column) ->
                types[column] =
                    domainTypeOf(model?.underlyingDomain(domainRef))
            }
        }
        return types
    }

    private fun domainTypeOf(domain: String?): String = domain?.let { model?.domains?.get(it)?.type } ?: "text"

    private fun collectTableScans(node: PlanNode): List<TableScanNode> =
        when (node.nodeCase) {
            PlanNode.NodeCase.TABLE_SCAN -> listOf(node.tableScan)
            PlanNode.NodeCase.FILTER -> collectTableScans(node.filter.input)
            PlanNode.NodeCase.AGGREGATE -> collectTableScans(node.aggregate.input)
            PlanNode.NodeCase.JOIN -> collectTableScans(node.join.left) + collectTableScans(node.join.right)
            PlanNode.NodeCase.UNION -> node.union.inputsList.flatMap { collectTableScans(it) }
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
    ) {
        /** Dedup key for the `hops` map: the equijoin identity (T-L4 — table alone collides when two
         *  dimensions hop via the same backing table on different keys). */
        fun signature(): String = "$table|$factKey|$hopKey"
    }

    /**
     * The calc catalog function that reaches [attribute] as a coarsening of the cubelet grain, or null
     * when the attribute is grain-direct (already bound — no calc) or no calc map produces its domain.
     * Mirrors the resolver's relative-time `viaCalc` for *authored* coarser-grain coordinates: a
     * coordinate the resolver produced with `viaCalc = null` on an attribute the cubelet does not bind
     * directly (e.g. `Time.year` on the `Time.day`-grained `sales`). The single-hop reachability from
     * the grain is checked downstream by [calcCoordinate] (a chained calc yields `md/calc-no-base`).
     */
    private fun derivedCalcFor(
        attribute: String,
        binding: CubeletBinding,
    ): String? {
        if (binding.attributes.containsKey(attribute)) return null // grain-direct → no calc needed
        val model = model ?: return null
        val toDomain = model.underlyingDomain(attribute) ?: return null
        return model.maps.values
            .firstOrNull { it.kind == MapKind.CALC && it.to.any { t -> model.underlyingDomain(t) == toDomain } }
            ?.calc
    }

    /**
     * Lower a **computed** (viaCalc) coordinate. The model's calc map — matched by catalog function
     * [calcFn] — names the `from`/`to` domains; the fact side always reads its column in the `from`
     * domain (the cubelet's bound date grain, e.g. `sale_date`). Then two forms:
     *   - **case table** (`md2db_map` bound): join the fact to the case table on the `from` column and
     *     drill on its `to` column (e.g. `cal_month`) — identical to a hop from there on.
     *   - **inline** (no case table): apply the calc as a date-extraction expression on the base column
     *     (`datepart(UNIT, sale_date)`), spelled per dialect by the translator. Supported for the
     *     extraction family (month/year/quarter/day/week of a date); truncation + rollup (chained calc)
     *     stay `md/calc-inline-unsupported`.
     * Needs the [MdModel] (calc map + domain resolution).
     */
    private fun calcCoordinate(
        binding: CubeletBinding,
        calcFn: String,
        dimension: String,
        attribute: String,
    ): CalcLowering {
        val model =
            model ?: throw MdLoweringException(
                "md/calc-unsupported",
                "computed coordinate '$attribute' (via '$calcFn') needs the MdModel to lower the calc",
            )
        val map =
            model.maps.values.firstOrNull { it.kind == MapKind.CALC && it.calc == calcFn }
                ?: throw MdLoweringException(
                    "md/calc-no-map",
                    "no calc map for catalog function '$calcFn' to reach computed attribute '$attribute'",
                )
        val fromDomain =
            map.from.firstOrNull()?.let { model.underlyingDomain(it) }
                ?: throw MdLoweringException("md/calc-no-from", "calc map '${map.name}' has no resolvable from-domain")
        // The fact-resident base column in the calc's `from` domain, anchored to THIS coordinate's own
        // dimension's grain (T-L4) — not a first-match across every dimension, which would pick the wrong
        // date column when a fact carries two (e.g. sale_date + delivery_date). Its absence means the base
        // isn't on the fact — a chained calc (`quarterOfMonth` over a computed month) — a follow-up.
        val baseColumn =
            binding.attributes.entries
                .firstOrNull { (attr, bound) ->
                    attr.substringBefore('.') == dimension &&
                        bound is AttrBinding.Column &&
                        model.underlyingDomain(attr) == fromDomain
                }?.let { (it.value as AttrBinding.Column).column }
                ?: throw MdLoweringException(
                    "md/calc-no-base",
                    "cubelet '${binding.cubelet}' binds no fact column for dimension '$dimension' in domain " +
                        "'$fromDomain' for calc '$calcFn' (a chained calc over a computed attribute is a follow-up)",
                )

        val caseTable = bindings.maps[map.name]
        if (caseTable == null) {
            // Inline: the calc is a date-extraction on the base column, spelled by the dialect.
            val unit =
                EXTRACTION_UNITS[calcFn]
                    ?: throw MdLoweringException(
                        "md/calc-inline-unsupported",
                        "inline calc '$calcFn' is not a date-extraction (truncation / rollup are a follow-up)",
                    )
            return CalcLowering.Inline(datePart(unit, baseColumn), baseColumn)
        }
        val toDomain =
            map.to.firstOrNull()?.let { model.underlyingDomain(it) }
                ?: throw MdLoweringException("md/calc-no-to", "calc map '${map.name}' has no resolvable to-domain")
        val caseColumns = caseTable.columns.mapKeys { it.key.substringAfterLast('.') }
        val joinColumn =
            caseColumns[fromDomain]
                ?: throw MdLoweringException(
                    "md/calc-no-case-key",
                    "case table '${caseTable.table}' binds no column for from-domain '$fromDomain'",
                )
        val drillColumn =
            caseColumns[toDomain]
                ?: throw MdLoweringException(
                    "md/calc-no-case-value",
                    "case table '${caseTable.table}' binds no column for to-domain '$toDomain'",
                )
        return CalcLowering.CaseTable(
            HopJoin(
                table = caseTable.table,
                factKey = baseColumn,
                hopKey = joinColumn,
                columns = linkedSetOf(joinColumn),
            ),
            drillColumn = drillColumn,
        )
    }

    /** How a calc coordinate lowers: a case-table [CaseTable] join, or an [Inline] date-extraction expression. */
    private sealed interface CalcLowering {
        /** Table-backed: a case-table [hop] join, drilling/filtering on its [drillColumn] (the `to` column). */
        class CaseTable(
            val hop: HopJoin,
            val drillColumn: String,
        ) : CalcLowering

        /** Inline: a date-extraction [expr] (`datepart(UNIT, baseColumn)`) the coordinate filters on. */
        class Inline(
            val expr: Expression,
            val baseColumn: String,
        ) : CalcLowering
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

    private fun colRef(
        name: String,
        sourceAlias: String,
    ): Expression =
        Expression
            .newBuilder()
            .setColumnRef(ColumnRef.newBuilder().setName(name).setSourceAlias(sourceAlias))
            .build()

    /** The db-type spelling of [attribute]'s domain when the model is known, else null (lexical fallback). */
    private fun memberTypeOf(attribute: String): String? = model?.let { domainTypeOf(it.underlyingDomain(attribute)) }

    /**
     * A member literal typed by its coordinate's domain ([memberType], T-L3): an integer literal only
     * for a numeric domain, so a text-domain member that looks numeric (`"0012"`) stays a string —
     * leading zero intact, no `text = integer` type error. When [memberType] is null (no model injected)
     * fall back to the lexical shape (digits ⇒ int) so hop-free reads are unchanged.
     */
    private fun memberLit(
        m: MemberRef,
        memberType: String?,
    ): Expression {
        val numeric = if (memberType != null) memberType.lowercase() in NUMERIC_MEMBER_TYPES else true
        val asLong = if (numeric) m.text.toLongOrNull() else null
        return if (asLong != null) intLit(asLong) else strLit(m.text)
    }

    private fun intLit(v: Long): Expression =
        Expression
            .newBuilder()
            .setLiteral(Literal.newBuilder().setIntValue(v).setType("int"))
            .build()

    private fun strLit(s: String): Expression =
        Expression
            .newBuilder()
            .setLiteral(Literal.newBuilder().setStringValue(s).setType("text"))
            .build()

    private fun boolLit(v: Boolean): Expression =
        Expression
            .newBuilder()
            .setLiteral(Literal.newBuilder().setBoolValue(v).setType("bool"))
            .build()

    /**
     * A standard date-part extraction `extract(UNIT FROM column)` (returns an int) — the inline form of a
     * computed time coordinate. The unit rides as a `symbol:TimeUnitRange` literal (the wire form the
     * translator's datetime family already round-trips, mirroring DATEADD/DATEPART); the translator maps
     * `extract` → Calcite's standard `EXTRACT`, which unparses per dialect (Postgres/ANSI `EXTRACT(unit
     * FROM x)`). Chosen over `datepart` because that unparses T-SQL-only (invalid Postgres).
     */
    private fun datePart(
        unit: String,
        column: String,
    ): Expression =
        Expression
            .newBuilder()
            .setFunction(
                FunctionCall
                    .newBuilder()
                    .setOperation("extract")
                    .addOperands(symbolLit(unit))
                    .addOperands(colRef(column)),
            ).build()

    /** A `symbol:TimeUnitRange` enum-flag literal (e.g. `YEAR`) — the datepart operand form (see [datePart]). */
    private fun symbolLit(enumName: String): Expression =
        Expression
            .newBuilder()
            .setLiteral(Literal.newBuilder().setType("symbol:TimeUnitRange").setStringValue(enumName))
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

        /** Domain type spellings that make a member an integer literal (T-L3); everything else stays a string. */
        val NUMERIC_MEMBER_TYPES: Set<String> = setOf("int", "integer", "smallint", "bigint", "long")

        /**
         * The date-extraction calc-catalog functions → their `TimeUnitRange` unit (the inline viaCalc
         * form). Only single-level extraction from a date/instant base; truncation (`truncTo*`) and
         * rollup (`quarterOfMonth`, chained over a computed attribute) are a follow-up.
         */
        val EXTRACTION_UNITS: Map<String, String> =
            mapOf(
                "monthOfDate" to "MONTH",
                "yearOfDate" to "YEAR",
                "quarterOfDate" to "QUARTER",
                "dayOfMonth" to "DAY",
                "weekOfYear" to "WEEK",
            )
    }
}

/** A structural failure lowering an MD path (unbound symbol, or a deferred §8 case). */
class MdLoweringException(
    val code: String,
    message: String,
) : RuntimeException(message)

/**
 * Union MD backing tables by qname, merging their columns (T-L2). Two MD reads of one fact table can
 * reference different columns (`net` vs `gross`, wide binding); registering only the first read's
 * [ModelTable] — a first-wins `putIfAbsent`/`distinctBy` — drops the second read's columns and fails
 * the translator's decode (`field not found`). Columns keep first-seen order.
 */
internal fun unionMdTables(tables: List<ModelTable>): List<ModelTable> {
    val byQname = LinkedHashMap<Pair<String, String>, ModelTable>()
    for (t in tables) {
        val key = t.qname.namespace to t.qname.name
        val existing = byQname[key]
        byQname[key] =
            if (existing == null) {
                t
            } else {
                val cols = LinkedHashMap<String, ModelColumn>()
                existing.columns.forEach { cols.putIfAbsent(it.name, it) }
                t.columns.forEach { cols.putIfAbsent(it.name, it) }
                existing.copy(columns = cols.values.toList())
            }
    }
    return byQname.values.toList()
}
