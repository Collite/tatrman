// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.codec.sql

import org.tatrman.translate.v1.Language
import org.tatrman.translate.v1.SqlDialect as SqlDialectProto
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContainIgnoringCase
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.plan.v1.ColumnRef
import org.tatrman.plan.v1.Expression
import org.tatrman.plan.v1.Literal
import org.tatrman.plan.v1.NamedExpression
import org.tatrman.plan.v1.PlanNode
import org.tatrman.plan.v1.ProjectNode
import org.tatrman.plan.v1.QualifiedName
import org.tatrman.plan.v1.Row
import org.tatrman.plan.v1.SchemaCode
import org.tatrman.plan.v1.StoreNode
import org.tatrman.plan.v1.ValuesNode
import org.tatrman.plan.v1.WriteMode
import org.tatrman.translator.framework.FixtureModel
import org.tatrman.translator.orchestrator.Translator
import org.tatrman.translator.orchestrator.UnparseResult

/**
 * Integration bar for the write path: a [StoreNode] whose `input` is the one-row `Project` over `Values`
 * that `MdWriteLowering` (ttrp-emit) produces must **decode** through [org.tatrman.translator.wire.PlanNodeDecoder]
 * and **unparse** to executable DML via [Translator.unparseFromRelNode] (the [StoreDmlUnparser] seam).
 * StoreDmlUnparserSpec covers the DML shape over a canned SELECT; this proves the real decode of the
 * write-row input + the interception in `Translator.unparseSql`.
 */
class StoreNodeTranslateSpec :
    StringSpec({

        fun strLit(s: String): Expression =
            Expression.newBuilder().setLiteral(Literal.newBuilder().setStringValue(s).setType("text")).build()

        fun intLit(n: Long): Expression =
            Expression.newBuilder().setLiteral(Literal.newBuilder().setIntValue(n).setType("int")).build()

        fun floatLit(d: Double): Expression =
            Expression.newBuilder().setLiteral(Literal.newBuilder().setFloatValue(d).setType("float")).build()

        fun boolLit(b: Boolean): Expression =
            Expression.newBuilder().setLiteral(Literal.newBuilder().setBoolValue(b).setType("bool")).build()

        // The one-row Project over dummy Values that MdWriteLowering emits for a pinned scalar write.
        fun writeRow(cells: List<Pair<String, Expression>>): PlanNode {
            val values =
                ValuesNode
                    .newBuilder()
                    .addOutputColumns(ColumnRef.newBuilder().setName("_d"))
                    .addRows(Row.newBuilder().addCells(Literal.newBuilder().setIntValue(0).setType("int")))
            val project = ProjectNode.newBuilder().setInput(PlanNode.newBuilder().setValues(values))
            cells.forEach { (col, e) ->
                project.addExpressions(NamedExpression.newBuilder().setExpression(e).setAlias(col))
            }
            return PlanNode.newBuilder().setProject(project).build()
        }

        fun target(name: String): QualifiedName =
            QualifiedName
                .newBuilder()
                .setSchemaCode(SchemaCode.DB)
                .setNamespace("dbo")
                .setName(name)
                .build()

        fun unparse(store: StoreNode): String {
            val plan = PlanNode.newBuilder().setStore(store).build()
            val result =
                Translator(FixtureModel.handle())
                    .unparseFromRelNode(plan, Language.SQL, SqlDialectProto.POSTGRESQL, optimize = true)
            result.shouldBeInstanceOf<UnparseResult.Success>()
            return result.output
        }

        "an INVALIDATE StoreNode over a pinned write-row unparses to a valid-flip + insert DML" {
            val store =
                StoreNode
                    .newBuilder()
                    .setTarget(target("f_plan"))
                    .setInput(
                        writeRow(
                            listOf(
                                "customer_name" to strLit("Kaufland"),
                                "month_num" to intLit(6),
                                "measure_code" to strLit("NET"),
                                "amount" to floatLit(77.0),
                                "is_current" to boolLit(true),
                            ),
                        ),
                    ).setMode(WriteMode.INVALIDATE)
                    .addAllGrainKeyColumns(listOf("customer_name", "month_num", "measure_code"))
                    .setMeasureColumn("amount")
                    .setValidColumn("is_current")
                    .build()

            val dml = unparse(store)
            dml.shouldContainIgnoringCase("insert into f_plan")
            dml.shouldContainIgnoringCase("update f_plan set is_current = false")
            dml.shouldContainIgnoringCase("with _src as")
            // the write row's constants survive into the inner SELECT
            dml.shouldContainIgnoringCase("kaufland")
        }

        "an OVERWRITE StoreNode unparses to a delete-then-insert DML" {
            val store =
                StoreNode
                    .newBuilder()
                    .setTarget(target("f_sales"))
                    .setInput(
                        writeRow(
                            listOf(
                                "customer_name" to strLit("Kaufland"),
                                "sale_date" to strLit("2025-06-20"),
                                "net" to floatLit(42.0),
                            ),
                        ),
                    ).setMode(WriteMode.OVERWRITE)
                    .addAllGrainKeyColumns(listOf("customer_name", "sale_date"))
                    .setMeasureColumn("net")
                    .build()

            val dml = unparse(store)
            dml.shouldContainIgnoringCase("delete from f_sales")
            dml.shouldContainIgnoringCase("insert into f_sales")
        }

        "a PROPORTIONAL spread StoreNode decodes its one-row input + unparses to a ratio UPDATE" {
            val store =
                StoreNode
                    .newBuilder()
                    .setTarget(target("f_sales"))
                    .setInput(
                        writeRow(
                            listOf(
                                "customer_name" to strLit("Kaufland"),
                                "net" to floatLit(1200.0),
                            ),
                        ),
                    ).setMode(WriteMode.OVERWRITE)
                    .addAllGrainKeyColumns(listOf("customer_name"))
                    .setMeasureColumn("net")
                    .setSpread(org.tatrman.plan.v1.SpreadStrategy.SPREAD_PROPORTIONAL)
                    .addAllSpreadColumns(listOf("sale_date"))
                    .build()

            val dml = unparse(store)
            dml.shouldContainIgnoringCase("update f_sales")
            dml.shouldContainIgnoringCase("sum(net)")
            dml.shouldContainIgnoringCase("nullif")
        }

        "an EQUAL spread StoreNode decodes its UNION-ALL input (N member rows) + unparses to a multi-row write" {
            fun branch(month: Long): PlanNode =
                writeRow(
                    listOf(
                        "customer_name" to strLit("Kaufland"),
                        "month_num" to intLit(month),
                        "measure_code" to strLit("NET"),
                        "amount" to floatLit(100.0),
                        "is_current" to boolLit(true),
                    ),
                )
            val union =
                PlanNode
                    .newBuilder()
                    .setUnion(
                        org.tatrman.plan.v1.UnionNode
                            .newBuilder()
                            .setAll(true)
                            .addInputs(branch(1))
                            .addInputs(branch(2))
                            .addInputs(branch(3)),
                    ).build()
            val store =
                StoreNode
                    .newBuilder()
                    .setTarget(target("f_plan"))
                    .setInput(union)
                    .setMode(WriteMode.INVALIDATE)
                    .addAllGrainKeyColumns(listOf("customer_name", "measure_code", "month_num"))
                    .setMeasureColumn("amount")
                    .setValidColumn("is_current")
                    .build()

            val dml = unparse(store)
            dml.shouldContainIgnoringCase("insert into f_plan")
            dml.shouldContainIgnoringCase("update f_plan set is_current = false")
            // The optimizer folds the UNION ALL of identical-shape one-row projects into one multi-row
            // VALUES — semantically identical, and all three enumerated month members are present.
            dml.shouldContainIgnoringCase("values")
            listOf(1, 2, 3).forEach { month -> dml.shouldContainIgnoringCase("'Kaufland', $month, 'NET'") }
        }
    })
