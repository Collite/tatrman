// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.plan.v1.MergeMode
import org.tatrman.plan.v1.WriteMode
import org.tatrman.ttr.md.resolve.CanonicalPath
import org.tatrman.ttr.md.resolve.Coordinate
import org.tatrman.ttr.md.resolve.MemberRef
import org.tatrman.ttr.md.resolve.PathShape
import org.tatrman.ttr.md.resolve.Selector
import org.tatrman.ttr.semantics.md.AggKind
import org.tatrman.ttr.semantics.md.AttrBinding
import org.tatrman.ttr.semantics.md.BindingShape
import org.tatrman.ttr.semantics.md.CubeletBinding
import org.tatrman.ttr.semantics.md.Journaling
import org.tatrman.ttr.semantics.md.MeasureBinding
import org.tatrman.ttr.semantics.md.fixtures.MdFixtures

/**
 * MD dot-path S5C-B.3 — [MdMergeDeleteLowering]: `C += e` (R28) upserts per journaling mode; `C -= e` (R29)
 * is a keys-only delete (anti-join / valid-flip). Structural goldens on the [org.tatrman.plan.v1.StoreNode]
 * shape (mode, merge, keys, measure, valid, delete flag) and the write-row projection (free coord → renamed
 * read column; pinned coord → constant). Over the shared `sales-model` bindings.
 */
class MdMergeDeleteLoweringSpec :
    StringSpec({

        // A wide/overwrite target `mc` (grain Customer.name × Time.day → the generated `time_day` column).
        val mc =
            CubeletBinding(
                cubelet = "mc",
                table = "db.dbo.md_mc",
                shape = BindingShape.Wide,
                attributes =
                    mapOf(
                        "Customer.name" to AttrBinding.Column("customer_name"),
                        "Time.day" to AttrBinding.Column("time_day"),
                    ),
                measures = mapOf("net" to MeasureBinding.Column("net")),
                journaling = Journaling.Overwrite,
            )
        // A long/invalidate target `ml` (grain Customer.name × Time.month), code/value + live flag.
        val ml =
            CubeletBinding(
                cubelet = "ml",
                table = "db.dbo.md_ml",
                shape = BindingShape.Long(codeColumn = "measure_code", valueColumn = "value"),
                attributes =
                    mapOf(
                        "Customer.name" to AttrBinding.Column("customer_name"),
                        "Time.month" to AttrBinding.Column("month_num"),
                    ),
                measures = mapOf("net" to MeasureBinding.Code("NET")),
                journaling = Journaling.Invalidate(validColumn = "is_current"),
            )
        val bindings =
            MdFixtures.salesBindings().let { it.copy(cubelets = it.cubelets + ("mc" to mc) + ("ml" to ml)) }
        val lowering = MdMergeDeleteLowering(bindings, MdFixtures.salesModel())

        fun star(
            dim: String,
            attr: String,
        ) = Coordinate(dim, attr, Selector.Star)

        fun pinned(
            dim: String,
            attr: String,
            member: String,
        ) = Coordinate(dim, attr, Selector.Pinned(MemberRef(member)))

        fun aliases(store: org.tatrman.plan.v1.StoreNode) =
            store.input.project.expressionsList
                .associate { it.alias to it.expression }

        "`+=` on a wide/overwrite cubelet lowers to an OVERWRITE assign upsert, renamed onto the target columns" {
            val rhs =
                CanonicalPath(
                    "sales",
                    listOf(star("Customer", "Customer.name"), star("Time", "Time.day")),
                    "net",
                    AggKind.SUM,
                )
            val store = lowering.merge("mc", rhs, PathShape(listOf("Customer.name", "Time.day")))
            store.mode shouldBe WriteMode.OVERWRITE
            store.merge shouldBe MergeMode.ASSIGN
            store.deleteKeys shouldBe false
            store.grainKeyColumnsList shouldBe listOf("customer_name", "time_day")
            store.measureColumn shouldBe "net"
            aliases(store).mapValues { it.value.columnRef.name } shouldBe
                mapOf("customer_name" to "customer_name", "time_day" to "sale_date", "net" to "net")
        }

        "a pinned grain coordinate becomes a constant column in the write row" {
            val rhs =
                CanonicalPath(
                    "sales",
                    listOf(pinned("Customer", "Customer.name", "Kaufland"), star("Time", "Time.day")),
                    "net",
                    AggKind.SUM,
                )
            val store = lowering.merge("mc", rhs, PathShape(listOf("Time.day")))
            val a = aliases(store)
            a.getValue("customer_name").literal.stringValue shouldBe "Kaufland" // pinned → constant
            a.getValue("time_day").columnRef.name shouldBe "sale_date" // free → renamed read column
        }

        "`+=` on a long/invalidate cubelet keys the code column and projects the live flag" {
            // Source `plan` is month-grained (Time.month) — the coarser grain `ml` merges from.
            val rhs =
                CanonicalPath(
                    "plan",
                    listOf(star("Customer", "Customer.name"), star("Time", "Time.month")),
                    "net",
                    AggKind.SUM,
                )
            val store = lowering.merge("ml", rhs, PathShape(listOf("Customer.name", "Time.month")))
            store.mode shouldBe WriteMode.INVALIDATE
            store.validColumn shouldBe "is_current"
            store.measureColumn shouldBe "value"
            // the measure-code column joins the match key (only NET rows supersede) + a `NET` code constant.
            store.grainKeyColumnsList shouldBe listOf("customer_name", "month_num", "measure_code")
            val a = aliases(store)
            a.getValue("measure_code").literal.stringValue shouldBe "NET"
            a.getValue("value").columnRef.name shouldBe "net"
            a.getValue("is_current").literal.boolValue shouldBe true
        }

        "`-=` on a wide/overwrite cubelet lowers to a keys-only physical delete (no measure projected)" {
            val rhs =
                CanonicalPath(
                    "sales",
                    listOf(pinned("Customer", "Customer.name", "Lidl"), star("Time", "Time.day")),
                    "net",
                    AggKind.SUM,
                )
            val store = lowering.delete("mc", rhs, PathShape(listOf("Time.day")))
            store.deleteKeys shouldBe true
            store.mode shouldBe WriteMode.OVERWRITE
            store.grainKeyColumnsList shouldBe listOf("customer_name", "time_day")
            // keys only — the measure column is NOT in the write row (R29 ignores the measure).
            aliases(store).keys shouldBe setOf("customer_name", "time_day")
        }

        "`-=` on an invalidate cubelet carries the valid column for the flip" {
            val rhs =
                CanonicalPath(
                    "plan",
                    listOf(pinned("Customer", "Customer.name", "Kaufland"), star("Time", "Time.month")),
                    "net",
                    AggKind.SUM,
                )
            val store = lowering.delete("ml", rhs, PathShape(listOf("Time.month")))
            store.deleteKeys shouldBe true
            store.mode shouldBe WriteMode.INVALIDATE
            store.validColumn shouldBe "is_current"
        }
    })
