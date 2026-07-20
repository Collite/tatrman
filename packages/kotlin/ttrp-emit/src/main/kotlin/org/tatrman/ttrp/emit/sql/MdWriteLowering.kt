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
import org.tatrman.plan.v1.StoreNode
import org.tatrman.plan.v1.ValuesNode
import org.tatrman.plan.v1.WriteMode
import org.tatrman.ttr.md.resolve.CanonicalPath
import org.tatrman.ttr.md.resolve.MemberRef
import org.tatrman.ttr.md.resolve.Selector
import org.tatrman.ttr.semantics.md.AttrBinding
import org.tatrman.ttr.semantics.md.BindingShape
import org.tatrman.plan.v1.FunctionCall
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

        val projections = linkedMapOf<String, Expression>() // physical column → its written value (order = target row)
        val grainKeys = mutableListOf<String>()

        for (coord in lhsPath.coordinates) {
            if (coord.viaCalc != null) {
                throw MdLoweringException(
                    "md/write-calc-unsupported",
                    "cannot write through a computed coordinate '${coord.attribute}' (viaCalc)",
                )
            }
            val pinned =
                coord.selector as? Selector.Pinned
                    ?: throw MdLoweringException(
                        "md/write-free-dim-unsupported",
                        "coordinate '${coord.attribute}' is not pinned; a free (dim.*) write (R21 align/spread) " +
                            "is a follow-up (S5-B.2)",
                    )
            val column =
                when (val bound = binding.attributes[coord.attribute]) {
                    is AttrBinding.Column -> bound.column
                    is AttrBinding.Hop ->
                        throw MdLoweringException(
                            "md/write-hop-unsupported",
                            "cannot write through a hop attribute '${coord.attribute}'",
                        )
                    null ->
                        throw MdLoweringException(
                            "md/unbound-attribute",
                            "attribute '${coord.attribute}' is not bound in cubelet '${binding.cubelet}'",
                        )
                }
            // Type the grain literal to its domain's physical type so the `VALUES` row column matches the
            // target column (a `date` grain member `'2025-06-20'` would otherwise be a `text` VALUES column
            // and break both the grain match `date = text` and the `text → date` insert). String/int
            // members already match their columns, so only date/decimal (etc.) get a cast.
            projections[column] = typed(memberLit(pinned.member), coord.attribute)
            grainKeys += column
        }

        // Shape tail: (long) the measure-code constant then the value column; (wide) the measure column.
        // The code column joins the match key too — only the written measure's rows are superseded/replaced.
        val measureColumn =
            when (val shape = binding.shape) {
                is BindingShape.Long -> {
                    val code =
                        (measure as? MeasureBinding.Code)?.code
                            ?: throw MdLoweringException(
                                "md/measure-shape-mismatch",
                                "long cubelet '${lhsPath.cubelet}' binds measure '${lhsPath.measure}' by column, not code",
                            )
                    projections[shape.codeColumn] = strLit(code)
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
        projections[measureColumn] = value

        val validColumn =
            (binding.journaling as? Journaling.Invalidate)?.let {
                projections[it.validColumn] = boolLit(true)
                it.validColumn
            } ?: ""

        val store =
            StoreNode
                .newBuilder()
                .setTarget(tableQname(binding.table))
                .setInput(projectRow(projections))
                .setMode(modeOf(binding.journaling))
                .addAllGrainKeyColumns(grainKeys)
                .setMeasureColumn(measureColumn)
                .setMerge(merge)
        if (validColumn.isNotEmpty()) store.setValidColumn(validColumn)
        return store.build()
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
