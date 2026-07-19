// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.md

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.AttrColumnBinding
import org.tatrman.ttr.parser.model.JournalingSpec
import org.tatrman.ttr.parser.model.Md2dbCubeletDef
import org.tatrman.ttr.parser.model.Md2dbDomainDef
import org.tatrman.ttr.parser.model.Md2dbMapDef
import org.tatrman.ttr.parser.model.Md2erCubeletDef
import org.tatrman.ttr.parser.model.MeasureColumnBinding
import org.tatrman.ttr.parser.model.ShapeSpec

/**
 * The Kotlin `ttr-parser` port of the `md2db_*` binding defs (S4 prereq — MD phase-3-binding). Before
 * this port the walker dropped every `def md2db_*` (`else -> null`); these assert the four binding
 * kinds now surface as typed [org.tatrman.ttr.parser.model.Definition]s with the structural fields
 * the read/write lowering consumes. Kind/field names mirror the TS twin (`ast.ts` `Md2Db*Def`,
 * contracts §2). Validators stay TS-side (MDS2) — the parser is mechanical (permissive superset).
 */
class Md2dbDefParseSpec :
    StringSpec({
        val model =
            """
            model binding
            def md2db_cubelet sales_fact {
              cubelet: md.sales,
              target: { table: db.dbo.f_sales },
              shape: wide,
              attributes: {
                Customer.name: { column: customer_name },
                Time.day: { column: sale_date },
                CostCenter.code: { via: md.cc_map, from: { table: db.dbo.d_cc, column: cc } }
              },
              measures: {
                net: { column: net },
                gross: { column: gross }
              }
            }
            def md2db_cubelet plan_long {
              cubelet: md.plan,
              target: db.dbo.f_plan,
              shape: { long: { codeColumn: measure_code, valueColumn: value } },
              attributes: { Customer.name: { column: customer_name }, Time.month: { column: month } },
              measures: { net: { code: NET } },
              journaling: { invalidate: { validColumn: is_valid } }
            }
            def md2db_domain name_source {
              domain: md.Name,
              source: { table: db.dbo.d_customer, column: name }
            }
            def md2db_map date_month_map {
              map: md.date_to_month,
              target: db.dbo.d_calendar,
              columns: { md.Date: cal_date, md.Month: cal_month }
            }
            def md2er_cubelet sales_er {
              cubelet: md.sales,
              target: er.SalesFact,
              attributes: { net: amount }
            }
            def md2er_cubelet bad_er {
              cubelet: md.sales,
              target: er.SalesFact,
              attributes: { net: amount },
              journaling: overwrite
            }
            """.trimIndent() + "\n"

        val r = TtrLoader.parseString(model, "md2db-defs.ttrm")

        "the binding model parses with no errors" {
            withClue("errors: ${r.errors}") { r.ok shouldBe true }
        }

        val cubelets = r.definitions.filterIsInstance<Md2dbCubeletDef>().associateBy { it.name }

        "a wide md2db_cubelet binds cubelet/table/shape/columns" {
            val b = cubelets.getValue("sales_fact")
            b.cubeletRef?.path shouldBe "md.sales"
            b.table?.path shouldBe "db.dbo.f_sales" // flattened from `target: { table }`
            b.shape shouldBe ShapeSpec.Wide
            b.attributes["Customer.name"] shouldBe AttrColumnBinding.Column("customer_name")
            b.measures["net"] shouldBe MeasureColumnBinding.Column("net")
            b.measures["gross"] shouldBe MeasureColumnBinding.Column("gross")
            b.journaling shouldBe null
        }

        "a map-mediated attribute binding surfaces via + from{table,column}" {
            val binding = cubelets.getValue("sales_fact").attributes["CostCenter.code"]
            val via = binding as? AttrColumnBinding.Via
            via.shouldNotBeNull()
            via.via.path shouldBe "md.cc_map"
            via.from.table.path shouldBe "db.dbo.d_cc"
            via.from.column shouldBe "cc"
        }

        "a long md2db_cubelet binds a code/value shape, code measures, and journaling" {
            val b = cubelets.getValue("plan_long")
            b.table?.path shouldBe "db.dbo.f_plan" // flattened from a bare-id `target:`
            b.shape shouldBe ShapeSpec.Long(codeColumn = "measure_code", valueColumn = "value")
            b.measures["net"] shouldBe MeasureColumnBinding.Code("NET")
            b.journaling shouldBe JournalingSpec.Invalidate(validColumn = "is_valid")
        }

        "an md2db_domain binds domain + source{table,column}" {
            val d = r.definitions.filterIsInstance<Md2dbDomainDef>().single()
            d.domainRef?.path shouldBe "md.Name"
            d.columnSource?.table?.path shouldBe "db.dbo.d_customer"
            d.columnSource?.column shouldBe "name"
        }

        "an md2db_map binds map + table + a from/to-domain column map" {
            val m = r.definitions.filterIsInstance<Md2dbMapDef>().single()
            m.mapRef?.path shouldBe "md.date_to_month"
            m.table?.path shouldBe "db.dbo.d_calendar"
            m.columns shouldContainExactly mapOf("md.Date" to "cal_date", "md.Month" to "cal_month")
        }

        "md2er_cubelet is structural; physical props are recorded (rejected in semantics)" {
            val ers = r.definitions.filterIsInstance<Md2erCubeletDef>().associateBy { it.name }
            val good = ers.getValue("sales_er")
            good.cubeletRef?.path shouldBe "md.sales"
            good.entity?.path shouldBe "er.SalesFact"
            good.attributes shouldContainExactly mapOf("net" to "amount")
            good.physicalProps shouldBe emptyList()
            ers.getValue("bad_er").physicalProps shouldBe listOf("journaling")
        }
    })
