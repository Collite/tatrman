// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import org.tatrman.plan.v1.Expression
import org.tatrman.plan.v1.FunctionCall
import org.tatrman.plan.v1.Literal
import org.tatrman.plan.v1.ColumnRef
import org.tatrman.plan.v1.MergeMode
import org.tatrman.plan.v1.NamedExpression
import org.tatrman.plan.v1.PlanNode
import org.tatrman.plan.v1.ProjectNode
import org.tatrman.plan.v1.QualifiedName
import org.tatrman.plan.v1.SchemaCode
import org.tatrman.plan.v1.StoreNode
import org.tatrman.plan.v1.WriteMode
import org.tatrman.ttr.md.resolve.CanonicalPath
import org.tatrman.ttr.md.resolve.MemberRef
import org.tatrman.ttr.md.resolve.PathShape
import org.tatrman.ttr.md.resolve.Selector
import org.tatrman.ttr.semantics.md.AttrBinding
import org.tatrman.ttr.semantics.md.BindingShape
import org.tatrman.ttr.semantics.md.CubeletBinding
import org.tatrman.ttr.semantics.md.Journaling
import org.tatrman.ttr.semantics.md.MdBindings
import org.tatrman.ttr.semantics.md.MdModel
import org.tatrman.ttr.semantics.md.MeasureBinding

/**
 * MD dot-path S5C-B.3 (run side) — lower a cubelet **merge** (`C += e`, R28) or **delete** (`C -= e`, R29)
 * to a [StoreNode]. Both write into an existing cubelet `C`, keyed on `C`'s grain, driven by `e`'s read
 * ([MdPathLowering.lower]); the collision / removal arm follows `C`'s journaling mode.
 *
 * The write row is built from `C`'s grain against `e`'s coordinates: a **free** (`dim.*`) coordinate is a
 * group key in `e`'s read output — renamed from the source column onto `C`'s column; a **pinned** coordinate
 * is a constant column (`e` filters it out of the read, but it IS part of `C`'s grain, so it must be
 * re-materialised as a constant). Member-set / range coordinates are a follow-up.
 *
 *  - **`+=` merge (R28)** — the write row is grain + measure; upsert per journaling: OVERWRITE replaces the
 *    colliding cell (`overwriteAssign`), INVALIDATE supersedes + appends (`invalidateAssign`, so the row
 *    also projects the live flag), DIFF appends a delta (`insertSelect`). All three DML shapes already exist
 *    ([StoreDmlUnparser]); this lowering supplies the keyed write row.
 *  - **`-=` delete (R29)** — the write row is the grain keys only (the measure is ignored, R29); the store
 *    carries `delete_keys`, so the DML is a keys-only anti-join (DELETE / valid-flip per mode).
 *
 * Scope: column-bound grain, free + pinned coordinates. Member-set / range coordinates and hop / calc grain
 * are follow-ups (`md/merge-restricted-unsupported` / `md/merge-hop-unsupported`).
 */
class MdMergeDeleteLowering(
    private val bindings: MdBindings,
    private val model: MdModel? = null,
) {
    private val read = MdPathLowering(bindings, model)

    /** Lower `<targetCubelet> += <rhsPath>` (R28) to an upsert [StoreNode] per the target's journaling mode. */
    fun merge(
        targetCubelet: String,
        rhsPath: CanonicalPath,
        rhsShape: PathShape,
        technical: WriteTechnical = WriteTechnical.NONE,
    ): StoreNode {
        val target = binding(targetCubelet, "merge target")
        val source = binding(rhsPath.cubelet, "merge source")
        val projections = linkedMapOf<String, Expression>()
        val grainKeys = mutableListOf<String>()
        grainRow(target, source, rhsPath, projections, grainKeys)

        // Measure tail: wide → the measure column; long → the code constant (joins the match key) + value.
        val readMeasure = colRef(rhsPath.measure)
        val measureColumn =
            when (val shape = target.shape) {
                BindingShape.Wide -> {
                    val col = wideMeasureColumn(target, rhsPath.measure)
                    projections[col] = readMeasure
                    col
                }
                is BindingShape.Long -> {
                    val code =
                        (target.measures[rhsPath.measure] as? MeasureBinding.Code)?.code
                            ?: throw MdLoweringException(
                                "md/measure-shape-mismatch",
                                "long merge target '$targetCubelet' binds measure '${rhsPath.measure}' by column, not code",
                            )
                    projections[shape.codeColumn] = strLit(code)
                    projections[shape.valueColumn] = readMeasure
                    grainKeys += shape.codeColumn // only the written measure's rows collide/supersede
                    shape.valueColumn
                }
            }
        // INVALIDATE upsert appends live rows — the write row carries the live flag = true.
        val validColumn = (target.journaling as? Journaling.Invalidate)?.validColumn ?: ""
        if (validColumn.isNotEmpty()) projections[validColumn] = boolLit(true)
        // R31 technical-column fill: stamp authored_by / written_at onto the appended rows.
        technical.addTo(projections)

        val store =
            StoreNode
                .newBuilder()
                .setTarget(tableQname(target.table))
                .setInput(project(read.lower(rhsPath, rhsShape), projections))
                .setMode(modeOf(target.journaling))
                .addAllGrainKeyColumns(grainKeys)
                .setMeasureColumn(measureColumn)
                .setMerge(MergeMode.ASSIGN)
        if (validColumn.isNotEmpty()) store.setValidColumn(validColumn)
        return store.build()
    }

    /** Lower `<targetCubelet> -= <rhsPath>` (R29) to a keys-only delete [StoreNode] (anti-join / valid-flip). */
    fun delete(
        targetCubelet: String,
        rhsPath: CanonicalPath,
        rhsShape: PathShape,
    ): StoreNode {
        val target = binding(targetCubelet, "delete target")
        val source = binding(rhsPath.cubelet, "delete source")
        val projections = linkedMapOf<String, Expression>()
        val grainKeys = mutableListOf<String>()
        grainRow(target, source, rhsPath, projections, grainKeys) // keys only — the measure is ignored (R29)

        val validColumn = (target.journaling as? Journaling.Invalidate)?.validColumn ?: ""
        val store =
            StoreNode
                .newBuilder()
                .setTarget(tableQname(target.table))
                .setInput(project(read.lower(rhsPath, rhsShape), projections))
                .setMode(modeOf(target.journaling))
                .addAllGrainKeyColumns(grainKeys)
                .setDeleteKeys(true)
        if (validColumn.isNotEmpty()) store.setValidColumn(validColumn)
        return store.build()
    }

    /**
     * Build the grain columns of the write row: for each of `C`'s grain attributes, a **free** RHS
     * coordinate renames the read's source group-key column onto `C`'s column; a **pinned** coordinate is a
     * constant (typed to its domain). Populates [projections] (target column → expression) and [grainKeys].
     */
    private fun grainRow(
        target: CubeletBinding,
        source: CubeletBinding,
        rhsPath: CanonicalPath,
        projections: MutableMap<String, Expression>,
        grainKeys: MutableList<String>,
    ) {
        val coordByAttr = rhsPath.coordinates.associateBy { it.attribute }
        for (attr in grainOf(target)) {
            val targetCol = columnOf(target, attr, "target")
            when (val selector = coordByAttr[attr]?.selector) {
                is Selector.Star -> projections[targetCol] = colRef(columnOf(source, attr, "source"))
                is Selector.Pinned -> projections[targetCol] = typed(memberLit(selector.member), attr)
                null ->
                    throw MdLoweringException(
                        "md/merge-grain-uncovered",
                        "target grain '$attr' is not covered by the RHS (R28)",
                    )
                else ->
                    throw MdLoweringException(
                        "md/merge-restricted-unsupported",
                        "a member-set / range coordinate on '$attr' is a merge/delete follow-up",
                    )
            }
            grainKeys += targetCol
        }
    }

    private fun binding(
        cubelet: String,
        role: String,
    ): CubeletBinding =
        bindings.cubelets[cubelet]
            ?: throw MdLoweringException("md/unbound-cubelet", "$role cubelet '$cubelet' has no md2db_cubelet binding")

    /** The target's grain in canonical order: the declared cubelet grain, else its binding's grain attributes. */
    private fun grainOf(target: CubeletBinding): List<String> =
        model
            ?.cubelets
            ?.get(target.cubelet)
            ?.grain
            ?.takeIf { it.isNotEmpty() } ?: target.attributes.keys.toList()

    private fun columnOf(
        binding: CubeletBinding,
        attribute: String,
        side: String,
    ): String =
        when (val bound = binding.attributes[attribute]) {
            is AttrBinding.Column -> bound.column
            is AttrBinding.Hop ->
                throw MdLoweringException(
                    "md/merge-hop-unsupported",
                    "merge/delete needs a column-bound grain; $side attribute '$attribute' is a hop (follow-up)",
                )
            null ->
                throw MdLoweringException(
                    "md/unbound-attribute",
                    "$side attribute '$attribute' is not bound in cubelet '${binding.cubelet}'",
                )
        }

    private fun wideMeasureColumn(
        binding: CubeletBinding,
        measure: String,
    ): String =
        (binding.measures[measure] as? MeasureBinding.Column)?.column
            ?: throw MdLoweringException(
                "md/measure-shape-mismatch",
                "wide merge target '${binding.cubelet}' binds measure '$measure' by code, not column",
            )

    private fun modeOf(journaling: Journaling): WriteMode =
        when (journaling) {
            Journaling.Overwrite -> WriteMode.OVERWRITE
            Journaling.Diff -> WriteMode.DIFF
            is Journaling.Invalidate -> WriteMode.INVALIDATE
        }

    /** Cast a pinned grain member to its domain's physical type (date / decimal / instant); else pass through. */
    private fun typed(
        value: Expression,
        attribute: String,
    ): Expression {
        val tag =
            when (model?.let { model.domains[model.underlyingDomain(attribute)]?.type }) {
                "date" -> "date"
                "decimal" -> "decimal"
                "instant", "datetime" -> "datetime"
                else -> return value // string / int members already match text / integer columns
            }
        return Expression
            .newBuilder()
            .setFunction(FunctionCall.newBuilder().setOperation("cast").addOperands(value))
            .setResultType(tag)
            .build()
    }

    private fun project(
        input: PlanNode,
        projections: Map<String, Expression>,
    ): PlanNode {
        val builder = ProjectNode.newBuilder().setInput(input)
        projections.forEach { (column, expr) ->
            builder.addExpressions(NamedExpression.newBuilder().setExpression(expr).setAlias(column))
        }
        return PlanNode.newBuilder().setProject(builder).build()
    }

    private fun memberLit(m: MemberRef): Expression {
        val asLong = m.text.toLongOrNull()
        return if (asLong != null) {
            Expression.newBuilder().setLiteral(Literal.newBuilder().setIntValue(asLong).setType("int")).build()
        } else {
            strLit(m.text)
        }
    }

    private fun colRef(name: String): Expression =
        Expression.newBuilder().setColumnRef(ColumnRef.newBuilder().setName(name)).build()

    private fun strLit(s: String): Expression =
        Expression.newBuilder().setLiteral(Literal.newBuilder().setStringValue(s).setType("text")).build()

    private fun boolLit(v: Boolean): Expression =
        Expression.newBuilder().setLiteral(Literal.newBuilder().setBoolValue(v).setType("bool")).build()

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
}
