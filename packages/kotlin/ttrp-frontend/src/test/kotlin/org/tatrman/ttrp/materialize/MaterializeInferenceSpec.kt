// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.materialize

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.model.AttrColumnBinding
import org.tatrman.ttr.parser.model.CubeletDef
import org.tatrman.ttr.parser.model.JournalingSpec
import org.tatrman.ttr.parser.model.MeasureColumnBinding
import org.tatrman.ttr.parser.model.Md2dbCubeletDef
import org.tatrman.ttr.parser.model.ShapeSpec
import org.tatrman.ttr.writer.TtrRenderer

/**
 * S5C-B.2 — the R27 definition **inference**: a fresh materialize (`C := e with { … }`) derives a whole
 * logical + physical model from the RHS shape and the `with` clause. Asserts both the structural defs
 * ([MaterializeSpec.toDefinitions]) and their rendered surface (through [TtrRenderer], B.1) so the
 * generated `.ttrm` is pinned exactly — the emitter ([MaterializeEmitter]) only adds a header on top.
 */
class MaterializeInferenceSpec :
    StringSpec({

        fun surface(spec: MaterializeSpec): String = TtrRenderer.render(spec.toDefinitions()).trim()

        "a wide overwrite spec infers a cubelet + column-bound wide binding" {
            val spec =
                MaterializeSpec(
                    name = "fc",
                    grain = listOf("Customer.name", "Time.day"),
                    measure = "net",
                    shape = MatShape.WIDE,
                    table = "db.dbo.md_fc",
                    journal = MatJournal.OVERWRITE,
                )
            val (cubelet, binding) = spec.toDefinitions()
            cubelet as CubeletDef
            binding as Md2dbCubeletDef

            cubelet.name shouldBe "fc"
            cubelet.grain.map { it.path } shouldBe listOf("Customer.name", "Time.day")
            binding.name shouldBe "fc_binding"
            binding.cubeletRef?.path shouldBe "md.fc"
            binding.table?.path shouldBe "db.dbo.md_fc"
            binding.shape shouldBe ShapeSpec.Wide
            binding.attributes shouldBe
                mapOf(
                    "Customer.name" to AttrColumnBinding.Column("customer_name"),
                    "Time.day" to AttrColumnBinding.Column("time_day"),
                )
            binding.measures shouldBe mapOf("net" to MeasureColumnBinding.Column("net"))
            binding.journaling shouldBe JournalingSpec.Overwrite

            surface(spec) shouldBe
                "def cubelet fc { grain: [Customer.name, Time.day], measures: [net] }\n" +
                "def md2db_cubelet fc_binding { cubelet: md.fc, target: db.dbo.md_fc, shape: wide, " +
                "attributes: { Customer.name: { column: customer_name }, Time.day: { column: time_day } }, " +
                "measures: { net: { column: net } }, journaling: overwrite }"
        }

        "a long invalidate spec infers a code/value column pair + a valid column" {
            val spec =
                MaterializeSpec(
                    name = "plan2",
                    grain = listOf("Customer.name", "Time.month"),
                    measure = "net",
                    shape = MatShape.LONG,
                    table = "db.dbo.md_plan2",
                    journal = MatJournal.INVALIDATE,
                )
            val binding = spec.toDefinitions()[1] as Md2dbCubeletDef
            binding.shape shouldBe ShapeSpec.Long(codeColumn = "measure_code", valueColumn = "value")
            binding.measures shouldBe mapOf("net" to MeasureColumnBinding.Code("NET"))
            binding.journaling shouldBe JournalingSpec.Invalidate(validColumn = "is_current")

            surface(spec) shouldBe
                "def cubelet plan2 { grain: [Customer.name, Time.month], measures: [net] }\n" +
                "def md2db_cubelet plan2_binding { cubelet: md.plan2, target: db.dbo.md_plan2, " +
                "shape: { long: { codeColumn: measure_code, valueColumn: value } }, " +
                "attributes: { Customer.name: { column: customer_name }, Time.month: { column: time_month } }, " +
                "measures: { net: { code: NET } }, journaling: { invalidate: { validColumn: is_current } } }"
        }

        "diff journaling renders as a bare id" {
            val spec =
                MaterializeSpec("d", listOf("Time.month"), "net", MatShape.WIDE, "db.dbo.md_d", MatJournal.DIFF)
            (spec.toDefinitions()[1] as Md2dbCubeletDef).journaling shouldBe JournalingSpec.Diff
            surface(spec).endsWith("journaling: diff }") shouldBe true
        }

        "`from` applies the R27 defaults: default table, overwrite journal, wide shape" {
            MaterializeSpec.from("q", listOf("Time.month"), "net", withClause = null) shouldBe
                MaterializeSpec("q", listOf("Time.month"), "net", MatShape.WIDE, "db.dbo.md_q", MatJournal.OVERWRITE)
        }
    })
