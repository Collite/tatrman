// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.CubeletDef
import org.tatrman.ttr.parser.model.CubeletMeasure
import org.tatrman.ttr.parser.model.DimensionDef
import org.tatrman.ttr.parser.model.HierarchyDef
import org.tatrman.ttr.parser.model.MdDomainDef
import org.tatrman.ttr.parser.model.MdMapDef
import org.tatrman.ttr.parser.model.MeasureDef

/**
 * MD dot-path S1 (step 0) — the Kotlin `ttr-parser` MD-def port. Before this arc the walker
 * dropped every `def domain/dimension/map/measure/cubelet/hierarchy` (`else -> null`); these
 * assert the six def kinds now surface as typed [org.tatrman.ttr.parser.model.Definition]s with
 * the fields `MdModel` (S1-A) and the lattice (S1-A3) read. Kind/field names mirror the TS twin.
 */
class MdDefParseSpec :
    StringSpec({
        val model =
            """
            model md
            def domain Day   { type: date }
            def domain Month { type: int, kind: calc, restrict: { range: 1..12 } }
            def domain Name  { type: string, publish: members }
            def domain Money { type: decimal }
            def dimension Customer {
              key: code,
              attributes: [
                def attribute code { domain: md.Name, isKey: true },
                def attribute name { domain: md.Name, aggregation: latestValid }
              ],
              hierarchies: [geo]
            }
            def map day_to_month { from: md.Day, to: md.Month, calc: monthOfDate }
            def map cc_map { from: md.Name, to: md.Name, cardinality: { from: "N", to: "1" } }
            def hierarchy calendar {
              dimension: md.Time,
              levels: [day, month via md.day_to_month]
            }
            def measure net { domain: md.Money, class: additive, aggregation: sum }
            def measure balance { domain: md.Money, aggregation: { default: sum, time: latestValid } }
            def cubelet sales { grain: [Customer.code], measures: [net] }
            def cubelet plan  { grain: [Customer.code], measures: [def measure gross { domain: md.Money }] }
            """.trimIndent() + "\n"

        val r = TtrLoader.parseString(model, "md-defs.ttrm")

        "the md model parses with no errors" {
            withClue("errors: ${r.errors}") { r.ok shouldBe true }
        }

        "domains surface with type, kind, and publish flag" {
            val domains = r.definitions.filterIsInstance<MdDomainDef>().associateBy { it.name }
            domains.keys shouldContainExactly setOf("Day", "Month", "Name", "Money")
            domains.getValue("Month").domainKind shouldBe "calc"
            domains.getValue("Name").publishMembers shouldBe true
            domains.getValue("Day").publishMembers shouldBe false
        }

        "a dimension carries its keyed attributes with domain refs + aggregation" {
            val cust = r.definitions.filterIsInstance<DimensionDef>().single { it.name == "Customer" }
            cust.key shouldBe "code"
            cust.attributes.map { it.name } shouldContainExactly listOf("code", "name")
            cust.attributes.single { it.name == "code" }.isKey shouldBe true
            cust.attributes
                .single { it.name == "code" }
                .domainRef
                ?.path shouldBe "md.Name"
            cust.attributes
                .single { it.name == "name" }
                .aggregation
                ?.default shouldBe "latestValid"
            cust.hierarchies.map { it.path } shouldContainExactly listOf("geo")
        }

        "maps carry from/to, calc, and normalized cardinality" {
            val maps = r.definitions.filterIsInstance<MdMapDef>().associateBy { it.name }
            maps.getValue("day_to_month").from.map { it.path } shouldContainExactly listOf("md.Day")
            maps.getValue("day_to_month").to.map { it.path } shouldContainExactly listOf("md.Month")
            maps.getValue("day_to_month").calc?.name shouldBe "monthOfDate"
            maps.getValue("day_to_month").cardinality shouldBe "N:1" // calc ⇒ implicitly N:1
            maps.getValue("cc_map").cardinality shouldBe "N:1"
            maps.getValue("cc_map").calc shouldBe null
        }

        "a hierarchy carries dimension + ordered levels with via" {
            val cal = r.definitions.filterIsInstance<HierarchyDef>().single()
            cal.dimensionRef?.path shouldBe "md.Time"
            cal.levels.map { it.attribute } shouldContainExactly listOf("day", "month")
            cal.levels
                .single { it.attribute == "month" }
                .via
                ?.path shouldBe "md.day_to_month"
            cal.levels.single { it.attribute == "day" }.via shouldBe null
        }

        "measures carry domain, class, and aggregation (bare + object)" {
            val measures = r.definitions.filterIsInstance<MeasureDef>().associateBy { it.name }
            measures.getValue("net").domainRef?.path shouldBe "md.Money"
            measures.getValue("net").measureClass shouldBe "additive"
            measures.getValue("net").aggregation?.default shouldBe "sum"
            measures.getValue("balance").aggregation?.default shouldBe "sum"
            measures.getValue("balance").aggregation?.perDimension shouldBe mapOf("time" to "latestValid")
        }

        "cubelets carry grain refs + measures (ref list and inline list)" {
            val cubelets = r.definitions.filterIsInstance<CubeletDef>().associateBy { it.name }
            val sales = cubelets.getValue("sales")
            sales.grain.map { it.path } shouldContainExactly listOf("Customer.code")
            sales.measures.filterIsInstance<CubeletMeasure.Ref>().map { it.ref.path } shouldContainExactly listOf("net")
            val inline =
                cubelets
                    .getValue("plan")
                    .measures
                    .filterIsInstance<CubeletMeasure.Inline>()
                    .single()
            inline.measure.name shouldBe "gross"
            inline.measure.domainRef?.path shouldBe "md.Money"
        }

        "the published domain flag is discoverable for the member catalog (S6 seam)" {
            r.definitions
                .filterIsInstance<MdDomainDef>()
                .single { it.name == "Name" }
                .publishMembers
                .shouldNotBeNull()
        }
    })
