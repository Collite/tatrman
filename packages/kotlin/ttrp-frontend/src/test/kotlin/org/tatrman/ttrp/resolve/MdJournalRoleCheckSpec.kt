// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.semantics.md.AttrBinding
import org.tatrman.ttr.semantics.md.BindingShape
import org.tatrman.ttr.semantics.md.CubeletBinding
import org.tatrman.ttr.semantics.md.Journaling
import org.tatrman.ttr.semantics.md.MdBindings
import org.tatrman.ttr.semantics.md.MeasureBinding
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * S5C-B.4 — [MdJournalRoleCheck]: R30 `TTRP-MD-018` (invalidate journaling needs a valid role on the backing
 * table) + the table→roles extraction that feeds it.
 */
class MdJournalRoleCheckSpec :
    StringSpec({

        fun bindingsOf(
            journaling: Journaling,
            table: String = "db.dbo.f_plan",
        ) = MdBindings(
            cubelets =
                mapOf(
                    "plan" to
                        CubeletBinding(
                            cubelet = "plan",
                            table = table,
                            shape = BindingShape.Wide,
                            attributes = mapOf("Customer.name" to AttrBinding.Column("customer_name")),
                            measures = mapOf("net" to MeasureBinding.Column("net")),
                            journaling = journaling,
                        ),
                ),
            domains = emptyMap(),
            maps = emptyMap(),
        )

        fun ids(
            journaling: Journaling,
            roles: Set<String>,
        ) = MdJournalRoleCheck.check(bindingsOf(journaling), mapOf("f_plan" to roles)).map { it.id }

        "invalidate over a table with no valid role is MD-018" {
            ids(Journaling.Invalidate("is_current"), roles = emptySet()) shouldContainExactly
                listOf(TtrpDiagnosticId.MD_018)
        }

        "a `valid_flag` role satisfies invalidate" {
            ids(Journaling.Invalidate("is_current"), roles = setOf("valid_flag")) shouldBe emptyList()
        }

        "the `valid_from` + `valid_to` pair satisfies invalidate" {
            ids(Journaling.Invalidate("is_current"), roles = setOf("valid_from", "valid_to")) shouldBe emptyList()
        }

        "only `valid_from` (half the temporal pair) is still MD-018" {
            ids(Journaling.Invalidate("is_current"), roles = setOf("valid_from")) shouldContainExactly
                listOf(TtrpDiagnosticId.MD_018)
        }

        "overwrite and diff journaling never need a valid role" {
            ids(Journaling.Overwrite, roles = emptySet()) shouldBe emptyList()
            ids(Journaling.Diff, roles = emptySet()) shouldBe emptyList()
        }

        "an invalidate binding to an UNDECLARED (external/physical-only) table is skipped, not MD-018" {
            // f_plan absent from the roles map ⇒ not a `def table` in the model ⇒ roles live in the DB.
            MdJournalRoleCheck.check(bindingsOf(Journaling.Invalidate("is_current")), tableRoles = emptyMap()) shouldBe
                emptyList()
        }

        "tableRolesOf reads each column's `semantics { role: … }` off the backing tables" {
            val defs =
                TtrLoader
                    .parseString(
                        """
                        model db schema dbo
                        def table f_plan {
                            columns: [
                                def column customer_name { type: text },
                                def column is_current { type: boolean, semantics { role: valid_flag } },
                                def column ver { type: int, semantics { role: version } }
                            ]
                        }
                        """.trimIndent(),
                        "db.ttrm",
                    ).definitions
            MdJournalRoleCheck.tableRolesOf(defs) shouldBe mapOf("f_plan" to setOf("valid_flag", "version"))
        }

        // The composition MdRepo.loadFrom runs: MdBindings.from(defs) + tableRolesOf(defs) → check.
        val mdModel =
            """
            model md
            def domain Amt { type: decimal }
            def domain Nm  { type: string }
            def dimension Customer { key: name, attributes: [ def attribute name { domain: md.Nm } ] }
            def measure net { domain: md.Amt, aggregation: sum }
            def cubelet p { grain: [Customer.name], measures: [net] }
            def md2db_cubelet p_binding {
                cubelet: md.p, target: db.dbo.f_p, shape: wide,
                attributes: { Customer.name: { column: name } },
                measures: { net: { column: net } },
                journaling: { invalidate: { validColumn: is_current } }
            }
            """.trimIndent()

        fun dbModel(validRole: String?) =
            """
            model db schema dbo
            def table f_p {
                columns: [
                    def column name { type: text },
                    def column net { type: decimal },
                    def column is_current { type: boolean${validRole?.let { " , semantics { role: $it }" } ?: ""} }
                ]
            }
            """.trimIndent()

        fun loadCheck(validRole: String?): List<TtrpDiagnosticId> {
            val defs =
                TtrLoader.parseString(mdModel, "md.ttrm").definitions +
                    TtrLoader.parseString(dbModel(validRole), "db.ttrm").definitions
            return MdJournalRoleCheck.check(MdBindings.from(defs), MdJournalRoleCheck.tableRolesOf(defs)).map { it.id }
        }

        "an invalidate cubelet over a role-less table surfaces MD-018 through the load composition" {
            loadCheck(validRole = null) shouldContainExactly listOf(TtrpDiagnosticId.MD_018)
        }

        "declaring the `valid_flag` role on the backing table clears it" {
            loadCheck(validRole = "valid_flag") shouldBe emptyList()
        }
    })
