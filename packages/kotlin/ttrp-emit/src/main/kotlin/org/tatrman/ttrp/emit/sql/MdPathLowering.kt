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
 *   - **Computed attribute** (`viaCalc`, a relative-time/calc coordinate, S4-A5) → an inner `Join` to the
 *     calc map's **case table** (`md2db_map`, e.g. `d_calendar`): the model's calc map (matched by the
 *     catalog function) names the `from`/`to` domains; the fact joins on its column in the `from` domain
 *     (the bound date grain), and the coordinate then filters/groups on the case table's `to` column
 *     (e.g. `cal_month`) — identical to a hop from there on. A calc map with no case table is the
 *     *inline* form (`calcFn(col)` as dialect SQL); that needs the translator to spell the catalog
 *     function and stays `md/calc-inline-unsupported`.
 *   - shape: scalar (no free dims) → aggregate-all (empty group keys); vector/sub-cubelet → group-by
 *     on the free dims' bound columns.
 *   - **Journaling view** (R31, S4-A4b/c) wraps the cubelet Load: overwrite → plain Load; invalidate →
 *     `Filter(Load, validColumn = true)` (SCD2 valid-flag read); diff → an inner `Aggregate` SUMming the
 *     measure per the cubelet's full grain (delta sum). Every §8 row composes on the wrapped view unchanged.
 *
 * ## Deferred (documented, not silently dropped)
 *
 *   - **Inline `viaCalc`** (a calc map with no `md2db_map` case table) — the `calcFn(col)` dialect SQL
 *     expression needs the translator to register the catalog function as an operator (like `GETDATE`);
 *     `md/calc-inline-unsupported`. The table-backed case-table form lowers fully (above).
 *   - **Non-additive / long-shape diff** — the diff view sums deltas per grain (additive, wide only);
 *     a non-SUM read is `md/diff-nonadditive-unsupported`, long-shape diff `md/diff-long-unsupported`.
 *   - **Multi-source cubelets** — [MdBindings] already keeps last-def-wins (S4-A4).
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
            // A computed (viaCalc) coordinate joins to the calc map's case table (§8); the drilled column
            // lives on that joined table, exactly like a hop, so the selector then filters/groups on it.
            val viaCalc = coord.viaCalc
            if (viaCalc != null) {
                val calc = calcJoin(binding, viaCalc, coord.attribute)
                val join = hops.getOrPut(calc.hop.table) { calc.hop }
                factColumns += join.factKey
                join.columns += calc.drillColumn
                applySelector(calc.drillColumn, coord.selector, conditions, groupKeys)
                continue
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

        var node = journaledScan(binding, factColumns.toList(), measureColumn, path.agg)
        for (hj in hops.values) node = join(node, hj)
        if (conditions.isNotEmpty()) node = filter(node, conjoin(conditions))
        return aggregate(node, groupKeys, path, measureColumn)
    }

    /**
     * The cubelet Load, wrapped per its journaling mode (R31, §8) — the single point where every §8
     * row composes on top of the *read view* of the fact table:
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
        factColumns: List<String>,
        measureColumn: String,
        agg: AggKind,
    ): PlanNode =
        when (val journaling = binding.journaling) {
            Journaling.Overwrite -> tableScan(binding.table, factColumns)
            is Journaling.Invalidate ->
                filter(
                    tableScan(binding.table, (factColumns + journaling.validColumn).distinct()),
                    eq(colRef(journaling.validColumn), boolLit(true)),
                )
            Journaling.Diff -> diffScan(binding, measureColumn, agg)
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
                .setInput(tableScan(binding.table, grainColumns + measureColumn))
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

    /**
     * Derive the inner join reaching a **computed** (viaCalc) coordinate through the calc map's *case
     * table* (§8, `md2db_map`). The model's calc map — matched by catalog function [calcFn] — names the
     * `from`/`to` domains; the case table binds a column per domain. The fact side joins on its column
     * in the calc's `from` domain (the cubelet's bound date grain, e.g. `sale_date`); the drilled column
     * is the case table's `to` column (e.g. `cal_month`), which the coordinate then filters/groups on —
     * identical to a hop from there on. Needs the [MdModel] (calc map + domain resolution). A calc map
     * with **no** `md2db_map` case table is the *inline* form (`calcFn(col)` as dialect SQL), which needs
     * the translator to spell the catalog function — `md/calc-inline-unsupported`.
     */
    private fun calcJoin(
        binding: CubeletBinding,
        calcFn: String,
        attribute: String,
    ): CalcJoin {
        val model =
            model ?: throw MdLoweringException(
                "md/calc-unsupported",
                "computed coordinate '$attribute' (via '$calcFn') needs the MdModel to reach its case table",
            )
        val map =
            model.maps.values.firstOrNull { it.kind == MapKind.CALC && it.calc == calcFn }
                ?: throw MdLoweringException(
                    "md/calc-no-map",
                    "no calc map for catalog function '$calcFn' to reach computed attribute '$attribute'",
                )
        val caseTable =
            bindings.maps[map.name]
                ?: throw MdLoweringException(
                    "md/calc-inline-unsupported",
                    "calc map '${map.name}' has no md2db_map case table; inline calc SQL " +
                        "('$calcFn(col)') needs the translator to spell the catalog function (a follow-up)",
                )
        val fromDomain =
            map.from.firstOrNull()?.let { model.underlyingDomain(it) }
                ?: throw MdLoweringException("md/calc-no-from", "calc map '${map.name}' has no resolvable from-domain")
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
        val factKey =
            binding.attributes.entries
                .firstOrNull { (attr, bound) ->
                    bound is AttrBinding.Column && model.underlyingDomain(attr) == fromDomain
                }?.let { (it.value as AttrBinding.Column).column }
                ?: throw MdLoweringException(
                    "md/calc-no-base",
                    "cubelet '${binding.cubelet}' binds no fact column in domain '$fromDomain' to join the case table",
                )
        return CalcJoin(
            HopJoin(table = caseTable.table, factKey = factKey, hopKey = joinColumn, columns = linkedSetOf(joinColumn)),
            drillColumn = drillColumn,
        )
    }

    /** A calc coordinate's derived case-table join ([hop]) plus the drilled `to` column to filter/group on. */
    private class CalcJoin(
        val hop: HopJoin,
        val drillColumn: String,
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

    private fun boolLit(v: Boolean): Expression =
        Expression
            .newBuilder()
            .setLiteral(Literal.newBuilder().setBoolValue(v).setType("bool"))
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
