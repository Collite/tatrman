// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.md

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader

/**
 * The MD → physical binding model ([MdBindings]) built from the `md2db_*` parser defs — the
 * structural layer S4 read/write lowering consumes. Asserts the resolved (simple-name) graph: cubelet
 * → table/shape/columns/journaling, bound-domain source, and table-map columns. `md2er_cubelet` is
 * excluded (it feeds the er read path, not the db lowering).
 */
class MdBindingsSpec :
    StringSpec({
        val bindingModel =
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
              measures: { net: { column: net }, gross: { column: gross } }
            }
            def md2db_cubelet plan_long {
              cubelet: md.plan,
              target: db.dbo.f_plan,
              shape: { long: { codeColumn: measure_code, valueColumn: value } },
              attributes: { Customer.name: { column: customer_name }, Time.month: { column: month } },
              measures: { net: { code: NET } },
              journaling: { invalidate: { validColumn: is_valid } }
            }
            def md2db_domain name_source { domain: md.Name, source: { table: db.dbo.d_customer, column: name } }
            def md2db_map date_month_map {
              map: md.date_to_month,
              target: db.dbo.d_calendar,
              columns: { md.Date: cal_date, md.Month: cal_month }
            }
            def md2er_cubelet sales_er { cubelet: md.sales, target: er.SalesFact, attributes: { net: amount } }
            """.trimIndent() + "\n"

        val r = TtrLoader.parseString(bindingModel, "binding.ttrm")
        val bindings = MdBindings.from(r.definitions)

        "the binding model parses cleanly" {
            withClue("errors: ${r.errors}") { r.ok shouldBe true }
        }

        "a wide cubelet binding resolves table, shape, columns, and default journaling" {
            val b = bindings.cubelets.getValue("sales") // keyed by the logical cubelet's simple name
            b.table shouldBe "db.dbo.f_sales"
            b.shape shouldBe BindingShape.Wide
            b.attributes["Customer.name"] shouldBe AttrBinding.Column("customer_name")
            b.measures["net"] shouldBe MeasureBinding.Column("net")
            b.journaling shouldBe Journaling.Overwrite
        }

        "a map-mediated attribute resolves to a Hop (via + from table/column)" {
            bindings.cubelets.getValue("sales").attributes["CostCenter.code"] shouldBe
                AttrBinding.Hop(via = "cc_map", fromTable = "db.dbo.d_cc", fromColumn = "cc")
        }

        "a long cubelet binding resolves the code/value shape, code measures, and invalidate journaling" {
            val b = bindings.cubelets.getValue("plan")
            b.table shouldBe "db.dbo.f_plan"
            b.shape shouldBe BindingShape.Long(codeColumn = "measure_code", valueColumn = "value")
            b.measures["net"] shouldBe MeasureBinding.Code("NET")
            b.journaling shouldBe Journaling.Invalidate(validColumn = "is_valid")
        }

        "a bound-domain source resolves table + column, keyed by the domain simple name" {
            bindings.domains.getValue("Name") shouldBe
                DomainBinding(domain = "Name", table = "db.dbo.d_customer", column = "name")
        }

        "a table-backed map resolves the case table + from/to-domain column map" {
            val m = bindings.maps.getValue("date_to_month")
            m.table shouldBe "db.dbo.d_calendar"
            m.columns shouldContainExactly mapOf("md.Date" to "cal_date", "md.Month" to "cal_month")
        }

        "md2er_cubelet is not part of the db binding graph" {
            // `sales` is bound once, from the md2db_cubelet — the md2er def does not add/override it.
            bindings.cubelets.keys shouldBe setOf("sales", "plan")
        }
    })
