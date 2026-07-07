package org.tatrman.ttrp.emit.sql

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.emit.EmitDiagnosticId
import org.tatrman.ttrp.emit.TtrpEmitException
import org.tatrman.ttrp.emit.sql.EmitFixtures.base
import org.tatrman.ttrp.emit.sql.EmitFixtures.col
import org.tatrman.ttrp.emit.sql.EmitFixtures.cols
import org.tatrman.ttrp.emit.sql.EmitFixtures.filter
import org.tatrman.ttrp.emit.sql.EmitFixtures.fn
import org.tatrman.ttrp.emit.sql.EmitFixtures.pgPlanner
import org.tatrman.ttrp.emit.sql.EmitFixtures.str
import org.tatrman.ttrp.graph.model.Select

/** T3.1.6 — translator failures surface as structured TTRP-EMT diagnostics, never raw Calcite. */
class EmitDiagnosticsTest :
    FunSpec({
        test("EMT-005: a sugar node reaching emit is an internal-invariant failure") {
            val select = Select("s1", "sel", SourceLocation.UNKNOWN, columns = listOf("a"))
            val scan =
                pgPlannerScan() // a throwaway base scan as the (unused) input
            val ex =
                shouldThrow<TtrpEmitException> {
                    PlanNodeBuilder().body(select, listOf(scan))
                }
            ex.id shouldBe EmitDiagnosticId.SUGAR_REACHED_EMIT
            ex.id.code shouldBe "TTRP-EMT-005"
        }

        test("EMT-004: an unresolvable column surfaces as a structured diagnostic, no Calcite class names") {
            // Filter references `nope`, which the base table does not declare → translator failure.
            val f = filter("f1", "t", fn("op.eq", col("nope"), str("x")))
            val ex =
                shouldThrow<TtrpEmitException> {
                    pgPlanner().emit(
                        listOf(
                            EmitNode(
                                "t",
                                f,
                                listOf(base("erp", "accounts", cols("status" to "text"))),
                                cols(
                                    "status" to "text",
                                ),
                            ),
                        ),
                        islandName = "diag",
                    )
                }
            ex.id shouldBe EmitDiagnosticId.REL_CONVERSION_FAILED
            ex.id.code shouldBe "TTRP-EMT-004"
            ex.message!! shouldContain "diag"
            ex.message!! shouldNotContain "org.apache.calcite"
        }
    })

private fun pgPlannerScan() =
    org.tatrman.plan.v1.PlanNode
        .newBuilder()
        .setTableScan(
            org.tatrman.plan.v1.TableScanNode
                .newBuilder()
                .setTable(
                    org.tatrman.plan.v1.QualifiedName
                        .newBuilder()
                        .setSchemaCode(org.tatrman.plan.v1.SchemaCode.DB)
                        .setNamespace("erp")
                        .setName("accounts"),
                ),
        ).build()
