// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.plan.v1.Expression
import org.tatrman.ttr.md.resolve.CanonicalPath
import org.tatrman.ttr.md.resolve.Coordinate
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
 * S5C-B.4b — [WriteTechnical]: R31 technical-column provenance fill (`authored_by` / `written_at`). Asserts
 * the projection helper + that the write lowerings ([MaterializeLowering] / [MdMergeDeleteLowering]) stamp
 * the declared columns onto the write row.
 */
class WriteTechnicalSpec :
    StringSpec({

        "fromRoleColumns picks the authored_by / written_at columns off a role→column map" {
            val t =
                WriteTechnical.fromRoleColumns(
                    mapOf("authored_by" to "author", "written_at" to "at", "version" to "ver"),
                    authoredBy = "run-42",
                    writtenAt = "2026-07-20T12:00:00Z",
                )
            t.authoredByColumn shouldBe "author"
            t.writtenAtColumn shouldBe "at" // version is a DML-level fill (not a constant) — not carried here
            t.authoredBy shouldBe "run-42"
        }

        "addTo stamps authored_by as a text literal and written_at as a timestamp cast" {
            val projections = linkedMapOf<String, Expression>()
            WriteTechnical("author", "at", "run-42", "2026-07-20T12:00:00Z").addTo(projections)
            projections.getValue("author").literal.stringValue shouldBe "run-42"
            // written_at is a cast(<iso text> as timestamp) so it matches a timestamp column.
            val at = projections.getValue("at")
            at.function.operation shouldBe "cast"
            at.resultType shouldBe "datetime"
            at.function.operandsList
                .single()
                .literal.stringValue shouldBe "2026-07-20T12:00:00Z"
        }

        "a table with no journal roles yields NONE (the write stamps nothing)" {
            WriteTechnical.fromRoleColumns(emptyMap(), "run", "now") shouldBe
                WriteTechnical.NONE.copy(authoredBy = "run", writtenAt = "now")
        }

        "the merge lowering stamps the technical columns onto the write row" {
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
            val bindings = MdFixtures.salesBindings().let { it.copy(cubelets = it.cubelets + ("mc" to mc)) }
            val store =
                MdMergeDeleteLowering(bindings, MdFixtures.salesModel()).merge(
                    "mc",
                    CanonicalPath(
                        "sales",
                        listOf(
                            Coordinate("Customer", "Customer.name", Selector.Star),
                            Coordinate("Time", "Time.day", Selector.Star),
                        ),
                        "net",
                        AggKind.SUM,
                    ),
                    PathShape(listOf("Customer.name", "Time.day")),
                    WriteTechnical("author", "at", "run-42", "2026-07-20T12:00:00Z"),
                )
            val aliases =
                store.input.project.expressionsList
                    .associate { it.alias to it.expression }
            aliases.getValue("author").literal.stringValue shouldBe "run-42"
            aliases.getValue("at").function.operation shouldBe "cast"
        }
    })
