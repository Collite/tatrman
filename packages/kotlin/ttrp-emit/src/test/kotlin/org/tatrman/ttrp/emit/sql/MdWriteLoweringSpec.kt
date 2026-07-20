// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.plan.v1.Expression
import org.tatrman.plan.v1.MergeMode
import org.tatrman.plan.v1.PlanNode
import org.tatrman.plan.v1.WriteMode
import org.tatrman.ttr.md.resolve.CanonicalPath
import org.tatrman.ttr.md.resolve.Coordinate
import org.tatrman.ttr.md.resolve.MemberRef
import org.tatrman.ttr.md.resolve.Selector
import org.tatrman.ttr.semantics.md.AggKind
import org.tatrman.ttr.semantics.md.fixtures.MdFixtures

/**
 * MD dot-path S5-B — lowering a resolved cubelet assignment to a `plan.v1` [org.tatrman.plan.v1.StoreNode]
 * ([MdWriteLowering]). Goldens at the wire-form level (the contract), over the shared `sales-model`
 * bindings ([MdFixtures.salesBindings]): `sales` wide/overwrite (`f_sales`), `plan` long/invalidate
 * (`f_plan`, code `NET` in `amount`, live flag `is_current`).
 */
class MdWriteLoweringSpec :
    StringSpec({
        val writer = MdWriteLowering(MdFixtures.salesBindings(), MdFixtures.salesModel())

        fun pinned(
            attr: String,
            member: String,
        ) = Coordinate(attr.substringBefore('.'), attr, Selector.Pinned(MemberRef(member)))

        fun path(
            cubelet: String,
            coords: List<Coordinate>,
            measure: String = "net",
        ) = CanonicalPath(cubelet, coords, measure, AggKind.SUM)

        fun row(node: PlanNode): Map<String, Expression> =
            node.project.expressionsList.associate { it.alias to it.expression }

        "a long/invalidate cubelet write lowers to an INVALIDATE StoreNode with the code+flag row shape" {
            val store =
                writer.lower(
                    path("plan", listOf(pinned("Customer.name", "Kaufland"), pinned("Time.month", "6"))),
                    MdWriteLowering.floatValue(77.0),
                    MergeMode.ASSIGN,
                )

            store.target.namespace shouldBe "dbo"
            store.target.name shouldBe "f_plan"
            store.mode shouldBe WriteMode.INVALIDATE
            store.measureColumn shouldBe "amount"
            store.validColumn shouldBe "is_current"
            store.merge shouldBe MergeMode.ASSIGN
            // grain match key = grain columns + the measure-code column (only NET rows are superseded).
            store.grainKeyColumnsList shouldBe listOf("customer_name", "month_num", "measure_code")

            val r = row(store.input)
            r.keys shouldBe setOf("customer_name", "month_num", "measure_code", "amount", "is_current")
            r["customer_name"]!!.literal.stringValue shouldBe "Kaufland"
            r["month_num"]!!.literal.intValue shouldBe 6L
            r["measure_code"]!!.literal.stringValue shouldBe "NET"
            r["amount"]!!.literal.floatValue shouldBe 77.0
            r["is_current"]!!.literal.boolValue shouldBe true
        }

        "a wide/overwrite cubelet write lowers to an OVERWRITE StoreNode (no code/flag, no valid column)" {
            val store =
                writer.lower(
                    path("sales", listOf(pinned("Customer.name", "Kaufland"), pinned("Time.day", "2025-06-20"))),
                    MdWriteLowering.floatValue(42.0),
                    MergeMode.ASSIGN,
                )

            store.target.name shouldBe "f_sales"
            store.mode shouldBe WriteMode.OVERWRITE
            store.measureColumn shouldBe "net"
            store.validColumn shouldBe ""
            store.grainKeyColumnsList shouldBe listOf("customer_name", "sale_date")

            val r = row(store.input)
            r.keys shouldBe setOf("customer_name", "sale_date", "net")
            r["customer_name"]!!.literal.stringValue shouldBe "Kaufland"
            // Time.day is a `date` domain, so the grain member is cast to DATE (else the VALUES column would
            // be text and break the date grain match + insert).
            r["sale_date"]!!.function.operation shouldBe "cast"
            r["sale_date"]!!.resultType shouldBe "date"
            r["sale_date"]!!
                .function.operandsList
                .single()
                .literal.stringValue shouldBe "2025-06-20"
            r["net"]!!.literal.floatValue shouldBe 42.0
        }

        "`+=` carries through as ACCUMULATE merge" {
            val store =
                writer.lower(
                    path("sales", listOf(pinned("Customer.name", "Kaufland"), pinned("Time.day", "2025-06-20"))),
                    MdWriteLowering.floatValue(5.0),
                    MergeMode.ACCUMULATE,
                )
            store.merge shouldBe MergeMode.ACCUMULATE
        }

        "a free (dim.*) LHS coordinate is a deferred spread/align write (S5-B.2)" {
            val ex =
                shouldThrow<MdLoweringException> {
                    writer.lower(
                        path(
                            "sales",
                            listOf(pinned("Customer.name", "Kaufland"), Coordinate("Time", "Time.day", Selector.Star)),
                        ),
                        MdWriteLowering.floatValue(1.0),
                        MergeMode.ASSIGN,
                    )
                }
            ex.code shouldBe "md/write-free-dim-unsupported"
        }

        "writing through a hop attribute is refused" {
            val ex =
                shouldThrow<MdLoweringException> {
                    writer.lower(
                        path("sales", listOf(pinned("Customer.region", "North"))),
                        MdWriteLowering.floatValue(1.0),
                        MergeMode.ASSIGN,
                    )
                }
            ex.code shouldBe "md/write-hop-unsupported"
        }

        "writing through a computed (viaCalc) coordinate is refused" {
            val ex =
                shouldThrow<MdLoweringException> {
                    writer.lower(
                        path(
                            "sales",
                            listOf(
                                pinned("Customer.name", "Kaufland"),
                                Coordinate(
                                    "Time",
                                    "Time.month",
                                    Selector.Pinned(MemberRef("6")),
                                    viaCalc = "monthOfDate",
                                ),
                            ),
                        ),
                        MdWriteLowering.floatValue(1.0),
                        MergeMode.ASSIGN,
                    )
                }
            ex.code shouldBe "md/write-calc-unsupported"
        }
    })
