// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import org.tatrman.plan.v1.ColumnRef
import org.tatrman.plan.v1.Expression
import org.tatrman.plan.v1.Literal
import org.tatrman.plan.v1.MergeMode
import org.tatrman.plan.v1.NamedExpression
import org.tatrman.plan.v1.PlanNode
import org.tatrman.plan.v1.ProjectNode
import org.tatrman.plan.v1.QualifiedName
import org.tatrman.plan.v1.Row
import org.tatrman.plan.v1.SchemaCode
import org.tatrman.plan.v1.SpreadStrategy
import org.tatrman.plan.v1.StoreNode
import org.tatrman.plan.v1.UnionNode
import org.tatrman.plan.v1.ValuesNode
import org.tatrman.plan.v1.WriteMode
import org.tatrman.ttr.md.resolve.CanonicalPath
import org.tatrman.ttr.md.resolve.MemberRef
import org.tatrman.ttr.md.resolve.Selector
import org.tatrman.ttr.semantics.md.AllocationStrategy
import org.tatrman.ttr.semantics.md.AttrBinding
import org.tatrman.ttr.semantics.md.BindingShape
import org.tatrman.plan.v1.FunctionCall
import org.tatrman.ttr.semantics.md.CubeletBinding
import org.tatrman.ttr.semantics.md.Journaling
import org.tatrman.ttr.semantics.md.MdBindings
import org.tatrman.ttr.semantics.md.MdModel
import org.tatrman.ttr.semantics.md.MeasureBinding

/**
 * MD dot-path S5-B — lower a resolved cubelet **assignment** to a `plan.v1` [StoreNode] (writeback,
 * contracts §5/§8). The counterpart of [MdPathLowering] (which lowers a *read* to a relational subtree):
 * this maps a strict-resolved LHS ([CanonicalPath], R19) + the checked RHS value through the target
 * cubelet's [MdBindings] into the write node the translator unparses to DML (`StoreDmlUnparser`).
 *
 * The `StoreNode.input` is the RHS shaped to the physical **write row** — one projected column per
 * physical target column, named to match:
 *   - each **pinned** grain coordinate → its member as a constant, aliased to the bound grain column;
 *   - **long** shape → the measure-code constant (aliased to the code column) + the value in the value
 *     column; **wide** shape → the value in the measure's own column;
 *   - **invalidate** journaling → the live flag as a `true` constant (aliased to the `validColumn`).
 * The value itself is any [Expression] the RHS lowers to — a literal (`= 42.0`), or a scalar MD read
 * ([MdPathLowering.lowerScalar]) etc. It rides a `Project` over a one-row dummy `Values` so a computed
 * value (subquery/arithmetic) and a bare literal share one shape.
 *
 * [StoreNode] then carries the journaling `mode` (from the binding), the physical grain **match key**
 * (grain columns; +the code column for long — only the written measure's rows are superseded), the
 * measure column (for `+=` read-modify-write), the `merge` arm (`=`/`+=`), and the `validColumn`.
 *
 * ## Scope (S5-B.1)
 * Fully-**pinned** scalar writes over Column-bound grain. A **free** (`dim.*`) LHS coordinate (the R21
 * spread/align vector write) needs the RHS free-dim group keys aligned onto the target grain columns —
 * a follow-up (`md/write-free-dim-unsupported`); the spread-legal allocation strategy is S5-B.2. Writing
 * *through* a hop or a computed (viaCalc) coordinate is ill-defined (you can't write a derived column) —
 * `md/write-hop-unsupported` / `md/write-calc-unsupported`.
 */
class MdWriteLowering(
    private val bindings: MdBindings,
    private val model: MdModel? = null,
) {
    /** Lower `lhsPath <merge> value` to a [StoreNode]. [value] is the already-lowered RHS expression. */
    fun lower(
        lhsPath: CanonicalPath,
        value: Expression,
        merge: MergeMode,
    ): StoreNode {
        val binding =
            bindings.cubelets[lhsPath.cubelet]
                ?: throw MdLoweringException(
                    "md/unbound-cubelet",
                    "cubelet '${lhsPath.cubelet}' has no md2db_cubelet binding",
                )
        val measure =
            binding.measures[lhsPath.measure]
                ?: throw MdLoweringException(
                    "md/unbound-measure",
                    "measure '${lhsPath.measure}' is not bound in cubelet '${lhsPath.cubelet}'",
                )

        // Pinned grain coordinates project a constant column each; free (`dim.*`) coordinates are the
        // R21 spread dims — legal only when the binding declares an allocation strategy for them.
        val pinned = linkedMapOf<String, Expression>() // physical grain column → its pinned member value
        val grainKeys = mutableListOf<String>()
        val freeCoords = mutableListOf<FreeCoord>()

        for (coord in lhsPath.coordinates) {
            if (coord.viaCalc != null) {
                throw MdLoweringException(
                    "md/write-calc-unsupported",
                    "cannot write through a computed coordinate '${coord.attribute}' (viaCalc)",
                )
            }
            val column = columnOf(binding, coord.attribute)
            when (val sel = coord.selector) {
                is Selector.Pinned -> {
                    // Type the grain literal to its domain's physical type so the `VALUES` row column
                    // matches the target column (a `date` grain member `'2025-06-20'` would otherwise be a
                    // `text` VALUES column and break both the grain match `date = text` and the `text →
                    // date` insert). String/int members already match their columns; only date/decimal cast.
                    pinned[column] = typed(memberLit(sel.member), coord.attribute)
                    grainKeys += column
                }
                Selector.Star -> {
                    val strategy =
                        binding.allocationFor(coord.dimension)
                            ?: throw MdLoweringException(
                                "md/spread-no-strategy", // TTRP-MD-010
                                "spread over free dimension '${coord.dimension}' needs a declared allocation " +
                                    "strategy on cubelet '${binding.cubelet}' (R21); none is declared",
                            )
                    freeCoords += FreeCoord(coord.dimension, coord.attribute, column, strategy)
                }
                else ->
                    throw MdLoweringException(
                        "md/write-restricted-free-unsupported",
                        "a restricted-free (member-set / range) spread over '${coord.attribute}' is a follow-up; " +
                            "use a pinned member or a full `dim.*` spread",
                    )
            }
        }

        // Shape tail: (long) the measure-code constant joins the match key + the value column; (wide) the
        // measure column. The code column joins the match key too — only the written measure's rows change.
        var codeColumn: String? = null
        var codeValue: String? = null
        val measureColumn =
            when (val shape = binding.shape) {
                is BindingShape.Long -> {
                    val code =
                        (measure as? MeasureBinding.Code)?.code
                            ?: throw MdLoweringException(
                                "md/measure-shape-mismatch",
                                "long cubelet '${lhsPath.cubelet}' binds measure '${lhsPath.measure}' by column, not code",
                            )
                    codeColumn = shape.codeColumn
                    codeValue = code
                    grainKeys += shape.codeColumn
                    shape.valueColumn
                }
                BindingShape.Wide ->
                    (measure as? MeasureBinding.Column)?.column
                        ?: throw MdLoweringException(
                            "md/measure-shape-mismatch",
                            "wide cubelet '${lhsPath.cubelet}' binds measure '${lhsPath.measure}' by code, not column",
                        )
            }
        val validColumn = (binding.journaling as? Journaling.Invalidate)?.validColumn ?: ""

        val shapeCols = ShapeColumns(codeColumn, codeValue, measureColumn, validColumn)
        return if (freeCoords.isEmpty()) {
            pinnedStore(binding, pinned, grainKeys, shapeCols, value, merge)
        } else {
            spreadStore(binding, lhsPath, pinned, grainKeys, freeCoords, shapeCols, value, merge)
        }
    }

    /** A free (`dim.*`) spread coordinate: its dimension/attribute, bound physical column, and R21 strategy. */
    private data class FreeCoord(
        val dimension: String,
        val attribute: String,
        val column: String,
        val strategy: AllocationStrategy,
    )

    /** The physical shape tail shared by every write row: optional measure-code column + value, measure, valid. */
    private data class ShapeColumns(
        val codeColumn: String?,
        val codeValue: String?,
        val measureColumn: String,
        val validColumn: String,
    )

    /** A fully-pinned scalar write (no free dim): one constant row, no spread. The original S5-B.1 shape. */
    private fun pinnedStore(
        binding: CubeletBinding,
        pinned: Map<String, Expression>,
        grainKeys: List<String>,
        shape: ShapeColumns,
        value: Expression,
        merge: MergeMode,
    ): StoreNode {
        val projections = linkedMapOf<String, Expression>()
        projections.putAll(pinned)
        shape.codeColumn?.let { projections[it] = strLit(shape.codeValue!!) }
        projections[shape.measureColumn] = value
        if (shape.validColumn.isNotEmpty()) projections[shape.validColumn] = boolLit(true)
        return baseStore(binding, grainKeys, shape, merge)
            .setInput(projectRow(projections))
            .build()
    }

    /**
     * An R21 spread write (contracts §5): the LHS is coarser than the target grain, so [value] distributes
     * over the free dimension(s). Dispatches on the declared strategy — [AllocationStrategy.Proportional]
     * (a read-modify UPDATE keyed on the pinned coords, marked on the wire) or [AllocationStrategy.Equal]
     * (member-enumerated N-row write). Unknown strategies and unsupported combinations are refused.
     */
    private fun spreadStore(
        binding: CubeletBinding,
        lhsPath: CanonicalPath,
        pinned: Map<String, Expression>,
        grainKeys: List<String>,
        freeCoords: List<FreeCoord>,
        shape: ShapeColumns,
        value: Expression,
        merge: MergeMode,
    ): StoreNode {
        freeCoords.firstOrNull { it.strategy is AllocationStrategy.Unknown }?.let {
            val raw = (it.strategy as AllocationStrategy.Unknown).raw
            throw MdLoweringException(
                "md/spread-unknown-strategy",
                "allocation strategy '$raw' on dimension '${it.dimension}' is not a known strategy " +
                    "(proportional | equal)",
            )
        }
        val strategies = freeCoords.map { it.strategy }.toSet()
        return when {
            strategies == setOf(AllocationStrategy.Proportional) ->
                proportionalStore(binding, pinned, grainKeys, freeCoords, shape, value, merge)
            strategies == setOf(AllocationStrategy.Equal) && freeCoords.size == 1 ->
                equalStore(binding, lhsPath, pinned, grainKeys, freeCoords.single(), shape, value, merge)
            else ->
                throw MdLoweringException(
                    "md/spread-combination-unsupported",
                    "this spread combination is a follow-up: proportional supports multiple free dims, equal " +
                        "supports one; mixed proportional+equal in one write is not yet lowered",
                )
        }
    }

    /**
     * PROPORTIONAL spread: the write row is the pinned coords + the coarse [value] (the free columns are
     * absent — they are summed over in the DML). `spread = PROPORTIONAL` + `spread_columns` tell the
     * unparser to scale the target's existing rows ∝ their current values.
     */
    private fun proportionalStore(
        binding: CubeletBinding,
        pinned: Map<String, Expression>,
        grainKeys: List<String>,
        freeCoords: List<FreeCoord>,
        shape: ShapeColumns,
        value: Expression,
        merge: MergeMode,
    ): StoreNode {
        val projections = linkedMapOf<String, Expression>()
        projections.putAll(pinned)
        shape.codeColumn?.let { projections[it] = strLit(shape.codeValue!!) }
        projections[shape.measureColumn] = value
        // No valid column on a proportional read-modify (it scales live rows in place, not SCD-2 inserts).
        return baseStore(binding, grainKeys, shape.copy(validColumn = ""), merge)
            .setInput(projectRow(projections))
            .setSpread(SpreadStrategy.SPREAD_PROPORTIONAL)
            .addAllSpreadColumns(freeCoords.map { it.column })
            .build()
    }

    /**
     * EQUAL spread: enumerate the free dimension's finer members (from the domain restrict-set) and split
     * [value] evenly (`coarse / N`) across a fully-pinned row per member — a plain N-row write (no wire
     * spread flag). The member set must be enumerable from the model (a declared `restrict`); an empty set
     * is the disconnected-undeclared case (`TTRP-MD-011` territory — the members can't be existence-checked
     * or produced). The RHS must be a numeric literal (a computed-RHS even split is a follow-up).
     */
    private fun equalStore(
        binding: CubeletBinding,
        lhsPath: CanonicalPath,
        pinned: Map<String, Expression>,
        grainKeys: List<String>,
        free: FreeCoord,
        shape: ShapeColumns,
        value: Expression,
        merge: MergeMode,
    ): StoreNode {
        val coarse =
            literalNumber(value)
                ?: throw MdLoweringException(
                    "md/spread-equal-nonliteral-rhs",
                    "equal spread needs a numeric literal RHS; a computed-RHS even split is a follow-up",
                )
        val members = enumerableMembers(free.attribute)
        if (members.isEmpty()) {
            throw MdLoweringException(
                "md/spread-equal-members-unknown", // TTRP-MD-011 (deferred/unknown member set)
                "cannot enumerate members of '${free.attribute}' for an equal spread — the domain declares no " +
                    "restrict member set (disconnected), and no connected member catalog is available",
            )
        }
        val perMember = floatValue(coarse / members.size)

        // One fully-pinned write row per finer member, each pinning the free dim to that member and
        // carrying the even share. Reuses the proven one-row `Project` shape (constant, possibly-cast
        // Expressions), UNION ALL-ed into an N-row read — so cast members ride Project constants rather
        // than needing literal-typed VALUES cells. N==1 skips the union (a union needs 2+ inputs).
        val branches =
            members.map { member ->
                val projections = linkedMapOf<String, Expression>()
                projections.putAll(pinned)
                projections[free.column] = typed(memberLit(MemberRef(member)), free.attribute)
                shape.codeColumn?.let { projections[it] = strLit(shape.codeValue!!) }
                projections[shape.measureColumn] = perMember
                if (shape.validColumn.isNotEmpty()) projections[shape.validColumn] = boolLit(true)
                projectRow(projections)
            }
        val input =
            if (branches.size == 1) {
                branches.single()
            } else {
                PlanNode.newBuilder().setUnion(UnionNode.newBuilder().setAll(true).addAllInputs(branches)).build()
            }
        return baseStore(binding, grainKeys + free.column, shape, merge)
            .setInput(input)
            .build()
    }

    /** The [StoreNode] skeleton common to every write shape: target, mode, keys, measure, merge, valid. */
    private fun baseStore(
        binding: CubeletBinding,
        grainKeys: List<String>,
        shape: ShapeColumns,
        merge: MergeMode,
    ): StoreNode.Builder {
        val store =
            StoreNode
                .newBuilder()
                .setTarget(tableQname(binding.table))
                .setMode(modeOf(binding.journaling))
                .addAllGrainKeyColumns(grainKeys)
                .setMeasureColumn(shape.measureColumn)
                .setMerge(merge)
        if (shape.validColumn.isNotEmpty()) store.setValidColumn(shape.validColumn)
        return store
    }

    /** The enumerable member texts of a grain [attribute]'s domain (from its `restrict`), or empty. */
    private fun enumerableMembers(attribute: String): List<String> {
        val domain = model?.underlyingDomain(attribute) ?: return emptyList()
        return model.domains[domain]?.members ?: emptyList()
    }

    /** The physical column a grain [attribute] binds to; refuses hop / unbound attributes. */
    private fun columnOf(
        binding: CubeletBinding,
        attribute: String,
    ): String =
        when (val bound = binding.attributes[attribute]) {
            is AttrBinding.Column -> bound.column
            is AttrBinding.Hop ->
                throw MdLoweringException(
                    "md/write-hop-unsupported",
                    "cannot write through a hop attribute '$attribute'",
                )
            null ->
                throw MdLoweringException(
                    "md/unbound-attribute",
                    "attribute '$attribute' is not bound in cubelet '${binding.cubelet}'",
                )
        }

    /** The numeric value of a literal RHS (`= 1200.0` / `= 12`), or null when the RHS is computed. */
    private fun literalNumber(value: Expression): Double? =
        if (!value.hasLiteral()) {
            null
        } else {
            when (value.literal.type) {
                "float" -> value.literal.floatValue
                "int" -> value.literal.intValue.toDouble()
                else -> null
            }
        }

    /** The physical `db` tables this write touches — the single target fact table — for island registration. */
    fun referencedTable(lhsPath: CanonicalPath): QualifiedName? =
        bindings.cubelets[lhsPath.cubelet]?.let { tableQname(it.table) }

    /** A one-row `Project` producing the write row: each projected [Expression] aliased to its target column. */
    private fun projectRow(projections: Map<String, Expression>): PlanNode {
        val project =
            ProjectNode
                .newBuilder()
                .setInput(oneRow())
        projections.forEach { (column, expr) ->
            project.addExpressions(NamedExpression.newBuilder().setExpression(expr).setAlias(column))
        }
        return PlanNode.newBuilder().setProject(project).build()
    }

    /** A single-row dummy `Values` (`VALUES (0)`) — the one input row the constant/subquery projections sit on. */
    private fun oneRow(): PlanNode =
        PlanNode
            .newBuilder()
            .setValues(
                ValuesNode
                    .newBuilder()
                    .addOutputColumns(ColumnRef.newBuilder().setName("_d"))
                    .addRows(Row.newBuilder().addCells(Literal.newBuilder().setIntValue(0).setType("int"))),
            ).build()

    /**
     * Wrap [value] in a `CAST(value AS <domain type>)` when the grain [attribute]'s domain has a physical
     * type a bare `VALUES` literal would mis-type (date / decimal / instant). String and int members already
     * match their columns, so they pass through uncast. No model ⇒ no cast (best-effort, like the read path).
     */
    private fun typed(
        value: Expression,
        attribute: String,
    ): Expression {
        val tag = model?.let { castTagFor(attribute) } ?: return value
        return Expression
            .newBuilder()
            .setFunction(FunctionCall.newBuilder().setOperation("cast").addOperands(value))
            .setResultType(tag)
            .build()
    }

    /** The cast type tag for a grain attribute's domain, or null when a bare literal already matches. */
    private fun castTagFor(attribute: String): String? {
        val domain = model?.underlyingDomain(attribute) ?: return null
        return when (model.domains[domain]?.type) {
            "date" -> "date"
            "decimal" -> "decimal"
            "instant", "datetime" -> "datetime"
            else -> null // string / int members already match text / integer columns
        }
    }

    private fun modeOf(journaling: Journaling): WriteMode =
        when (journaling) {
            Journaling.Overwrite -> WriteMode.OVERWRITE
            Journaling.Diff -> WriteMode.DIFF
            is Journaling.Invalidate -> WriteMode.INVALIDATE
        }

    private fun memberLit(m: MemberRef): Expression {
        val asLong = m.text.toLongOrNull()
        return if (asLong != null) {
            Expression.newBuilder().setLiteral(Literal.newBuilder().setIntValue(asLong).setType("int")).build()
        } else {
            strLit(m.text)
        }
    }

    private fun strLit(s: String): Expression =
        Expression.newBuilder().setLiteral(Literal.newBuilder().setStringValue(s).setType("text")).build()

    private fun boolLit(v: Boolean): Expression =
        Expression.newBuilder().setLiteral(Literal.newBuilder().setBoolValue(v).setType("bool")).build()

    /** Parse a binding's physical table ref (`db.dbo.f_sales` or `dbo.f_sales`) into a DB [QualifiedName]. */
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

    companion object {
        /** A numeric literal RHS value (`= 42.0`) — v1 measures are numeric (D12). */
        fun floatValue(d: Double): Expression =
            Expression.newBuilder().setLiteral(Literal.newBuilder().setFloatValue(d).setType("float")).build()
    }
}
