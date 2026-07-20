// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import org.tatrman.plan.v1.Expression
import org.tatrman.plan.v1.Literal
import org.tatrman.plan.v1.ColumnRef
import org.tatrman.plan.v1.NamedExpression
import org.tatrman.plan.v1.PlanNode
import org.tatrman.plan.v1.ProjectNode
import org.tatrman.plan.v1.QualifiedName
import org.tatrman.plan.v1.SchemaCode
import org.tatrman.plan.v1.StoreNode
import org.tatrman.plan.v1.WriteMode
import org.tatrman.ttr.md.resolve.CanonicalPath
import org.tatrman.ttr.md.resolve.PathShape
import org.tatrman.ttr.semantics.md.AttrBinding
import org.tatrman.ttr.semantics.md.BindingShape
import org.tatrman.ttr.semantics.md.CubeletBinding
import org.tatrman.ttr.semantics.md.MdBindings
import org.tatrman.ttr.semantics.md.MdModel
import org.tatrman.ttr.semantics.md.MeasureBinding

/**
 * MD dot-path S5C-B.2 (run side) — lower a **materialize** (`C := e`, R26/R27, §8) to a full-replace
 * [StoreNode]. Materialize creates/replaces the backing table of the target cubelet [C] with the RHS
 * read `e` in full: the compile side already emitted `C`'s generated `.ttrm` (B.2a); this side produces
 * the DDL (create-table-if-new) and the write plan.
 *
 * The RHS is a whole cubelet **read** ([MdPathLowering.lower], the S4 read producer), whose output columns
 * are the **source** cubelet's physical grain columns + the measure (aliased to the measure name). Those
 * rarely match the target cubelet's columns (the generated binding names grain columns `dimension_attribute`,
 * B.2a). So the read is wrapped in a `Project` that **renames by grain dimension** onto the target columns
 * — grain attribute → target column, measure → target measure column (wide) or the value column plus the
 * measure-code constant (long) — and that Project becomes the [StoreNode.input]. The store carries
 * [WriteMode.REPLACE]: the DML clears the whole target and inserts the result ([StoreDmlUnparser]).
 *
 * Scope: **Column-bound** grain on both sides (the materialized-cubelet common case). A source grain reached
 * through a hop / calc coordinate has no plain read-output column to rename from — `md/materialize-hop-unsupported`.
 */
class MaterializeLowering(
    private val bindings: MdBindings,
    private val model: MdModel? = null,
) {
    private val read = MdPathLowering(bindings, model)

    /** Lower `<targetCubelet> := <rhsPath>` to a REPLACE [StoreNode] over the RHS read, stamping [technical]. */
    fun lower(
        targetCubelet: String,
        rhsPath: CanonicalPath,
        rhsShape: PathShape,
        technical: WriteTechnical = WriteTechnical.NONE,
    ): StoreNode {
        val target =
            bindings.cubelets[targetCubelet]
                ?: throw MdLoweringException(
                    "md/unbound-cubelet",
                    "materialize target cubelet '$targetCubelet' has no md2db_cubelet binding",
                )
        val source =
            bindings.cubelets[rhsPath.cubelet]
                ?: throw MdLoweringException(
                    "md/unbound-cubelet",
                    "materialize source cubelet '${rhsPath.cubelet}' has no md2db_cubelet binding",
                )
        val grain = grainOf(target)

        // Rename the read's source-named output columns onto the target columns. The [projections] insertion
        // order IS the target's physical column order — the Project's output row type drives the INSERT column
        // list the DML unparser assembles, so no separate column list is threaded on the wire.
        val projections = linkedMapOf<String, Expression>() // target column → read-output expression
        val grainKeys = mutableListOf<String>()
        for (attr in grain) {
            projections[columnOf(target, attr, "target")] = colRef(columnOf(source, attr, "source"))
            grainKeys += columnOf(target, attr, "target")
        }
        // Measure tail: wide → the measure column takes the read's measure (aliased to the measure name);
        // long → the value column takes it and the code column takes the measure's code constant.
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
                                "long materialize target '$targetCubelet' binds measure '${rhsPath.measure}' by column, not code",
                            )
                    projections[shape.codeColumn] = strLit(code)
                    projections[shape.valueColumn] = readMeasure
                    shape.valueColumn
                }
            }

        // R31 technical-column fill: stamp authored_by / written_at as extra write-row columns.
        technical.addTo(projections)

        val input = project(read.lower(rhsPath, rhsShape), projections)
        return StoreNode
            .newBuilder()
            .setTarget(tableQname(target.table))
            .setInput(input)
            .setMode(WriteMode.REPLACE)
            .addAllGrainKeyColumns(grainKeys)
            .setMeasureColumn(measureColumn)
            .build()
    }

    /**
     * The `CREATE TABLE IF NOT EXISTS` for a materialized cubelet's backing table, derived from its
     * generated binding (contracts §8 "create-table-if-new"): one column per grain attribute (typed by its
     * domain), the measure column (numeric), plus the long-shape code column and the invalidate valid column.
     * The table name is the bare target name (public schema), matching [StoreNode.target] and the seed tables.
     */
    fun createTableDdl(targetCubelet: String): String {
        val target =
            bindings.cubelets[targetCubelet]
                ?: throw MdLoweringException("md/unbound-cubelet", "no binding for materialize target '$targetCubelet'")
        val cols = mutableListOf<String>()
        for (attr in grainOf(target)) {
            val col = columnOf(target, attr, "target")
            cols += "$col ${pgType(model?.underlyingDomain(attr))}"
        }
        when (val shape = target.shape) {
            BindingShape.Wide -> cols += "${wideMeasureColumn(target, singleMeasure(target))} numeric"
            is BindingShape.Long -> {
                cols += "${shape.codeColumn} text"
                cols += "${shape.valueColumn} numeric"
            }
        }
        (target.journaling as? org.tatrman.ttr.semantics.md.Journaling.Invalidate)?.let {
            cols += "${it.validColumn} boolean"
        }
        return "CREATE TABLE IF NOT EXISTS ${target.table.substringAfterLast('.')} (${cols.joinToString(", ")})"
    }

    /**
     * The materialize target's grain in canonical order: the cubelet's declared grain (the generated model,
     * once loaded), else the target binding's own grain attributes (a generated binding binds exactly the
     * grain). Equals the RHS free dims by construction (R27).
     */
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
                    "md/materialize-hop-unsupported",
                    "materialize needs a column-bound grain; $side attribute '$attribute' is a hop (follow-up)",
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
                "wide materialize target '${binding.cubelet}' binds measure '$measure' by code, not column",
            )

    private fun singleMeasure(binding: CubeletBinding): String =
        binding.measures.keys.firstOrNull()
            ?: throw MdLoweringException("md/no-measure", "materialize target '${binding.cubelet}' binds no measure")

    /** Postgres column type for a grain attribute's domain (mirrors the read's cast type tags). */
    private fun pgType(domain: String?): String =
        when (domain?.let { model?.domains?.get(it)?.type }) {
            "date" -> "date"
            "decimal" -> "numeric"
            "int" -> "integer"
            "instant", "datetime" -> "timestamp"
            else -> "text" // string / unknown
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

    private fun colRef(name: String): Expression =
        Expression.newBuilder().setColumnRef(ColumnRef.newBuilder().setName(name)).build()

    private fun strLit(s: String): Expression =
        Expression.newBuilder().setLiteral(Literal.newBuilder().setStringValue(s).setType("text")).build()

    /** Parse a binding's physical table ref (`db.dbo.md_c`) into a DB [QualifiedName] (schema code DB). */
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
