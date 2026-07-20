// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.plan.v1.WriteMode
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
 * MD dot-path S5C-B.2 (run side) — [MaterializeLowering]: `C := e` lowers to a full-replace [WriteMode.REPLACE]
 * StoreNode whose input renames the RHS read's source columns onto the materialized cubelet's generated
 * columns, plus the `CREATE TABLE IF NOT EXISTS` for the backing table. Over the shared `sales-model`
 * bindings (`sales` wide, grain Customer.name × Time.day, `sale_date` → the generated `time_day`).
 */
class MaterializeLoweringSpec :
    StringSpec({

        // A materialized wide cubelet `mc` over `sales` — the generated binding (B.2a conventions):
        // grain columns `dimension_attribute`, table `db.dbo.md_mc`, overwrite journaling.
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
        val lowering = MaterializeLowering(bindings, MdFixtures.salesModel())

        // RHS: `sales.name.*.day.*.net` — free on both grain dims (a whole-cubelet read).
        val rhsPath =
            CanonicalPath(
                cubelet = "sales",
                coordinates =
                    listOf(
                        Coordinate("Customer", "Customer.name", Selector.Star),
                        Coordinate("Time", "Time.day", Selector.Star),
                    ),
                measure = "net",
                agg = AggKind.SUM,
            )
        val rhsShape = PathShape(freeDims = listOf("Customer.name", "Time.day"))

        "materialize lowers to a REPLACE StoreNode targeting the generated table" {
            val store = lowering.lower("mc", rhsPath, rhsShape)
            store.mode shouldBe WriteMode.REPLACE
            store.target.name shouldBe "md_mc"
            store.measureColumn shouldBe "net"
            store.grainKeyColumnsList shouldBe listOf("customer_name", "time_day")
        }

        "the input Project renames the read's source columns onto the target columns by grain dimension" {
            val store = lowering.lower("mc", rhsPath, rhsShape)
            // alias (target column) → the read-output column it selects.
            val aliases =
                store.input.project.expressionsList
                    .associate { it.alias to it.expression.columnRef.name }
            aliases shouldBe
                mapOf(
                    "customer_name" to "customer_name", // identity (source & target agree)
                    "time_day" to "sale_date", // rename: source `sale_date` → generated `time_day`
                    "net" to "net", // the measure, aliased to its name by the read
                )
        }

        "createTableDdl derives typed columns from the generated binding (grain domains + numeric measure)" {
            lowering.createTableDdl("mc") shouldBe
                "CREATE TABLE IF NOT EXISTS md_mc (customer_name text, time_day date, net numeric)"
        }

        "a long materialize target renders a code/value column pair in the DDL and Store" {
            val ml =
                CubeletBinding(
                    cubelet = "ml",
                    table = "db.dbo.md_ml",
                    shape = BindingShape.Long(codeColumn = "measure_code", valueColumn = "value"),
                    attributes = mapOf("Customer.name" to AttrBinding.Column("customer_name")),
                    measures = mapOf("net" to MeasureBinding.Code("NET")),
                    journaling = Journaling.Overwrite,
                )
            val b = MdFixtures.salesBindings().let { it.copy(cubelets = it.cubelets + ("ml" to ml)) }
            val l = MaterializeLowering(b, MdFixtures.salesModel())
            val path =
                CanonicalPath(
                    "sales",
                    listOf(Coordinate("Customer", "Customer.name", Selector.Star)),
                    "net",
                    AggKind.SUM,
                )
            val store = l.lower("ml", path, PathShape(freeDims = listOf("Customer.name")))
            val aliases =
                store.input.project.expressionsList
                    .associate { it.alias to it.expression }
            // the code column takes a constant `NET`; the value column takes the read measure `net`.
            aliases.getValue("measure_code").literal.stringValue shouldBe "NET"
            aliases.getValue("value").columnRef.name shouldBe "net"
            store.measureColumn shouldBe "value"
            l.createTableDdl("ml") shouldBe
                "CREATE TABLE IF NOT EXISTS md_ml (customer_name text, measure_code text, value numeric)"
        }
    })
