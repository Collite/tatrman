// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.plan.v1.JoinType as PbJoinType
import org.tatrman.plan.v1.PlanNode
import org.tatrman.plan.v1.QualifiedName
import org.tatrman.plan.v1.SchemaCode
import org.tatrman.plan.v1.TableScanNode
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.emit.TtrpEmitException
import org.tatrman.ttrp.expr.CatalogId
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.FunctionCall
import org.tatrman.ttrp.graph.model.Join
import org.tatrman.ttrp.graph.model.JoinType

/**
 * AG-P0 — join lowering to `plan.v1`: SEMI/ANTI ride the wire natively (NX-A), CROSS is INNER on TRUE;
 * with `existenceAsJoin` (the sql-text path) an equi SEMI/ANTI becomes an INNER / LEFT(+IS NULL) join
 * against the right side's distinct keys, projected back to the left row.
 */
class PlanNodeBuilderJoinSpec :
    StringSpec({
        val loc = SourceLocation.UNKNOWN

        fun scan(
            name: String,
            vararg cols: String,
        ): PlanNode =
            PlanNode
                .newBuilder()
                .setTableScan(
                    TableScanNode
                        .newBuilder()
                        .setTable(
                            QualifiedName
                                .newBuilder()
                                .setSchemaCode(SchemaCode.DB)
                                .setNamespace("dbo")
                                .setName(name),
                        ).apply {
                            cols.forEach {
                                addOutputColumns(
                                    org.tatrman.plan.v1.ColumnRef
                                        .newBuilder()
                                        .setName(it),
                                )
                            }
                        },
                ).build()

        val l = scan("L", "id", "x")
        val r = scan("R", "id", "y")
        val eq = FunctionCall(CatalogId.EQ, listOf(ColumnRef("left", "id", loc), ColumnRef("right", "id", loc)), loc)

        fun join(
            type: JoinType,
            on: org.tatrman.ttrp.expr.Expression? = eq,
        ) = Join("j", "j", loc, type = type, on = on)

        "SEMI and ANTI map to the wire SEMI/ANTI join types (no right-key dedup projection)" {
            val semi = PlanNodeBuilder().body(join(JoinType.SEMI), listOf(l, r))
            semi.join.joinType shouldBe PbJoinType.SEMI
            PlanNodeBuilder().body(join(JoinType.ANTI), listOf(l, r)).join.joinType shouldBe PbJoinType.ANTI
        }

        "CROSS maps to an INNER join on literal TRUE" {
            val p = PlanNodeBuilder().body(join(JoinType.CROSS, on = null), listOf(l, r))
            p.join.joinType shouldBe PbJoinType.INNER
            p.join.condition.literal.boolValue shouldBe true
        }

        "existenceAsJoin: SEMI = INNER join on the distinct right keys, projected to the left row" {
            val p = PlanNodeBuilder(existenceAsJoin = true).body(join(JoinType.SEMI), listOf(l, r))
            p.nodeCase shouldBe PlanNode.NodeCase.PROJECT
            p.project.expressionsList.map { it.alias } shouldBe listOf("id", "x")
            val j = p.project.input.join
            j.joinType shouldBe PbJoinType.INNER
            j.right.aggregate.groupKeysList
                .map { it.name } shouldBe listOf("id")
        }

        "existenceAsJoin: ANTI = LEFT join on marked distinct keys, filtered to the unmatched rows" {
            val p = PlanNodeBuilder(existenceAsJoin = true).body(join(JoinType.ANTI), listOf(l, r))
            val filter = p.project.input.filter
            filter.condition.function.operation shouldBe "is_null"
            filter.condition.function.operandsList
                .single()
                .columnRef.name shouldBe PlanNodeBuilder.EXISTS_MARKER
            val j = filter.input.join
            j.joinType shouldBe PbJoinType.LEFT
            j.right.project.expressionsList
                .map { it.alias } shouldBe listOf("id", PlanNodeBuilder.EXISTS_MARKER)
        }

        "existenceAsJoin refuses a non-equi semi join with a named reason" {
            val lt =
                FunctionCall(CatalogId.LT, listOf(ColumnRef("left", "id", loc), ColumnRef("right", "id", loc)), loc)
            shouldThrow<TtrpEmitException> {
                PlanNodeBuilder(existenceAsJoin = true).body(join(JoinType.SEMI, on = lt), listOf(l, r))
            }
        }

        "a declared runtime param lowers to a ParameterRef, a same-named port-qualified ref stays a column" {
            val b = PlanNodeBuilder(params = mapOf("dnes" to PlanParam(1, "date")))
            val p = b.expr(ColumnRef(null, "dnes", loc))
            p.parameter.name shouldBe "dnes"
            p.parameter.positionalIndex shouldBe 1
            p.resultType shouldBe "date"
            b.expr(ColumnRef("left", "dnes", loc)).columnRef.name shouldBe "dnes"
        }
    })
