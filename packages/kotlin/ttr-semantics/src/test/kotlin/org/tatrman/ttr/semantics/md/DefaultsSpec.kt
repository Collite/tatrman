// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.model.AggregationSpec
import org.tatrman.ttr.semantics.md.fixtures.MdFixtures

/**
 * S1-B1/B3 — default measure/aggregation accessors over the shared fixture (R10 default fill).
 */
class DefaultsSpec :
    StringSpec({
        val model = MdFixtures.salesModel()

        "a cubelet's default measure is its first declared measure" {
            model.cubelets.getValue("sales").defaultMeasure shouldBe "net" // measures: [net, gross]
            model.cubelets.getValue("plan").defaultMeasure shouldBe "net"
        }

        "a measure's default aggregation comes from its declared aggregation" {
            model.measures.getValue("net").defaultAgg shouldBe AggKind.SUM
            model.defaultAggOf("sales") shouldBe AggKind.SUM
        }

        "a measure with no declared aggregation falls back to SUM" {
            MdMeasure("x", domainRef = "md.Money", measureClass = null, aggregation = null, validBy = null)
                .defaultAgg shouldBe AggKind.SUM
        }

        "aggKindOf maps the closed agg set (and latestValid ⇒ MAX)" {
            aggKindOf("sum") shouldBe AggKind.SUM
            aggKindOf("avg") shouldBe AggKind.AVG
            aggKindOf("min") shouldBe AggKind.MIN
            aggKindOf("max") shouldBe AggKind.MAX
            aggKindOf("count") shouldBe AggKind.COUNT
            aggKindOf("latestValid") shouldBe AggKind.MAX // semi-additive "latest valid" (D26)
            aggKindOf("bogus") shouldBe null
        }

        "the object-form aggregation default is read" {
            val balance =
                MdMeasure(
                    "balance",
                    "md.Money",
                    "semiAdditive",
                    AggregationSpec(
                        default = "sum",
                        perDimension =
                            mapOf(
                                "time" to "latestValid",
                            ),
                    ),
                    validBy = "day",
                )
            balance.defaultAgg shouldBe AggKind.SUM
        }
    })
