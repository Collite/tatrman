// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.semantics.md.fixtures.MdFixtures

/**
 * S1-A2 — [MdModel] built from the shared `sales-model` fixture (MDS2 port of TS md stage 2A).
 * Asserts object inventory, dimension membership, cubelet grains, and the 2B4 attribute→domain
 * sugar (`Customer.name` ⇒ `Name`).
 */
class MdModelLoadSpec :
    StringSpec({
        val model = MdFixtures.salesModel()

        "domains load with type/kind/publish" {
            model.domains.keys shouldContainExactlyInAnyOrder
                setOf("Code", "Name", "Region", "Date", "Month", "Quarter", "Year", "ProductCode", "Money")
            model.domains.getValue("Month").kind shouldBe "calc"
            model.domains.getValue("Name").publishMembers shouldBe true
            model.domains.getValue("Code").publishMembers shouldBe false
        }

        "dimensions carry their attributes in order" {
            model.dimensions.keys shouldContainExactlyInAnyOrder setOf("Customer", "Time", "Product")
            model.dimensions
                .getValue("Customer")
                .attributes
                .map { it.name } shouldContainExactly
                listOf("code", "name", "region")
            model.dimensions
                .getValue("Time")
                .attributes
                .map { it.name } shouldContainExactly
                listOf("day", "month", "quarter", "year")
            model.dimensions.getValue("Customer").key shouldBe "code"
        }

        "every attribute is keyed by its Dimension.attribute path with a domain ref" {
            model.attributes.keys shouldContainExactlyInAnyOrder
                setOf(
                    "Customer.code",
                    "Customer.name",
                    "Customer.region",
                    "Time.day",
                    "Time.month",
                    "Time.quarter",
                    "Time.year",
                    "Product.code",
                )
            model.attributes.getValue("Customer.name").domainRef shouldBe "md.Name"
            model.attributes.getValue("Customer.code").isKey shouldBe true
        }

        "maps and measures load" {
            model.maps.keys shouldContainExactlyInAnyOrder
                setOf(
                    "name_to_region", "code_to_name", "date_to_month", "month_to_quarter",
                    "date_to_year", "region_from_attr",
                )
            model.maps.getValue("code_to_name").kind shouldBe MapKind.ONE_ONE
            model.maps.getValue("date_to_month").kind shouldBe MapKind.CALC
            model.maps.getValue("name_to_region").kind shouldBe MapKind.N1
            model.measures.keys shouldContainExactlyInAnyOrder setOf("net", "gross")
        }

        "cubelets carry grain refs and measures" {
            model.cubelets.getValue("sales").grain shouldContainExactly listOf("Customer.name", "Time.day")
            model.cubelets.getValue("sales").measures shouldContainExactly listOf("net", "gross")
            model.cubelets.getValue("plan").grain shouldContainExactly listOf("Customer.name", "Time.month")
        }

        "the 2B4 attribute→domain sugar resolves refs to their underlying domain" {
            model.underlyingDomain("md.Money") shouldBe "Money" // domain ref → itself
            model.underlyingDomain("Customer.name") shouldBe "Name" // attribute ref → its domain
            model.underlyingDomain("Customer.region") shouldBe "Region"
            model.underlyingDomain("md.Nonexistent") shouldBe null
        }
    })
