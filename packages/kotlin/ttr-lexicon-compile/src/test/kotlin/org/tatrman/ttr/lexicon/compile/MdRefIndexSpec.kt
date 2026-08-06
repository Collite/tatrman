// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon.compile

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.lexicon.TargetClass
import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.semantics.md.MdModel

/**
 * RV-P3.4 T4 — the md half of the reference index, against the **kinded** target grammar Bora
 * ruled: `md.<kind>.<name>`, attribute depth `md.dimension.<Dim>.<attr>`, member depth
 * `md.dimension.<Dim>.<attr>.<code>` — mirroring `er.entity.<entity>.<attribute>.<code>`.
 *
 * The model is hartland's, trimmed: the three measures and two dimensions whose refs produced all
 * 38 of that estate's dangling-ref warnings, plus the DC codes its `valueLabels` declare. Every
 * "resolves" case below is a row that compiles to nothing today.
 */
class MdRefIndexSpec :
    StringSpec({
        val source =
            """
            package hartland

            model md

            def domain Money { type: decimal }
            def domain Quantity { type: decimal }
            def domain DcCode { type: string }
            def domain ItemCode { type: string }

            def measure revenue      { domain: md.Money,    class: additive, aggregation: sum }
            def measure quantity     { domain: md.Quantity, class: additive, aggregation: sum }
            def measure returnAmount { domain: md.Money,    class: additive, aggregation: sum }
            def measure onHandQty    { domain: md.Quantity, class: semiAdditive, aggregation: sum }

            def dimension Product {
                key: itemCode,
                attributes: [ def attribute itemCode { domain: md.ItemCode, isKey: true } ]
            }

            def dimension DistributionCentre {
                key: dcCode,
                attributes: [
                    def attribute dcCode {
                        domain: md.DcCode,
                        isKey: true,
                        valueLabels: { "5": { en: "Memphis DC" }, "1": { en: "Columbus DC" } }
                    }
                ]
            }

            def map dc_to_item { from: md.DcCode, to: md.ItemCode, cardinality: { from: "N", to: "1" } }

            def hierarchy productDrill { dimension: md.Product, levels: [ itemCode ] }

            def cubelet storeSales { grain: [ Product.itemCode ], measures: [ md.revenue ] }
            """.trimIndent()

        val parsed = TtrLoader.parseString(source, "md.ttrm")
        val index = ModelRefIndex.ofMd(MdModel.from(parsed.definitions))

        "the fixture parses — an empty md model would make every case below pass vacuously" {
            parsed.ok shouldBe true
            MdModel
                .from(parsed.definitions)
                .measures.keys
                .contains("revenue") shouldBe true
        }

        // ── the five refs `p3-2` is blocked on ────────────────────────────────────────────────
        "md.measure.revenue resolves as a model object" {
            index.classify("md.measure.revenue") shouldBe TargetClass.MODEL_OBJECT
        }
        "md.measure.returnAmount resolves" {
            index.classify("md.measure.returnAmount") shouldBe TargetClass.MODEL_OBJECT
        }
        "md.measure.onHandQty resolves" {
            index.classify("md.measure.onHandQty") shouldBe TargetClass.MODEL_OBJECT
        }
        "md.dimension.Product resolves" {
            index.classify("md.dimension.Product") shouldBe TargetClass.MODEL_OBJECT
        }
        "md.dimension.DistributionCentre resolves" {
            index.classify("md.dimension.DistributionCentre") shouldBe TargetClass.MODEL_OBJECT
        }

        // ── depth ────────────────────────────────────────────────────────────────────────────
        "attribute depth resolves as a model object, not a member" {
            index.classify("md.dimension.DistributionCentre.dcCode") shouldBe TargetClass.MODEL_OBJECT
        }
        "member depth resolves as MEMBER when the attribute declares that code" {
            index.classify("md.dimension.DistributionCentre.dcCode.5") shouldBe TargetClass.MEMBER
            index.classify("md.dimension.DistributionCentre.dcCode.1") shouldBe TargetClass.MEMBER
        }
        "a code the attribute never declares dangles — the member index is the DECLARED set" {
            index.classify("md.dimension.DistributionCentre.dcCode.9") shouldBe null
        }
        "an attribute that declares no valueLabels has no members" {
            index.classify("md.dimension.Product.itemCode") shouldBe TargetClass.MODEL_OBJECT
            index.classify("md.dimension.Product.itemCode.42") shouldBe null
        }

        "a cubelet resolves — the queryable fact grain is addressable (T1)" {
            index.classify("md.cubelet.storeSales") shouldBe TargetClass.MODEL_OBJECT
        }

        // ── the deliberate exclusions (T1) ───────────────────────────────────────────────────
        "a domain does NOT resolve — it is a type, not something a query selects" {
            index.classify("md.domain.Money") shouldBe null
        }
        "a hierarchy does NOT resolve — a drill path is not an object a mention denotes" {
            index.classify("md.hierarchy.productDrill") shouldBe null
        }
        "a map does NOT resolve — binding plumbing is not vocabulary" {
            index.classify("md.map.dc_to_item") shouldBe null
        }

        // ── the grammar itself ───────────────────────────────────────────────────────────────
        "a WRONG-kind ref dangles rather than falling back to another map" {
            // `Product` is a dimension. Looking it up under `measure` must not find it — six maps
            // share one namespace, and a fallback would make the kind token decorative.
            index.classify("md.measure.Product") shouldBe null
            index.classify("md.dimension.revenue") shouldBe null
        }
        "an unknown kind token dangles" {
            index.classify("md.widget.revenue") shouldBe null
        }
        "the UNKINDED form loses, and is not quietly accepted" {
            // `md.revenue` is how TTR-M writes an in-language cross-ref, and `md.account.class.
            // expense` is the shape contracts §2 used to show. Both are the losing grammar; if
            // either resolved, two spellings of one target would compile to two rows.
            index.classify("md.revenue") shouldBe null
            index.classify("md.Money") shouldBe null
            index.classify("md.DistributionCentre.dcCode.5") shouldBe null
        }
        "a bare or foreign-schema ref is not this index's business" {
            index.classify("er.entity.customer") shouldBe null
            index.classify("revenue") shouldBe null
            index.classify("md") shouldBe null
            index.classify("md.") shouldBe null
        }

        // ── composition (T5) ─────────────────────────────────────────────────────────────────
        "orElse falls through to the next index, and md never shadows it" {
            val er = ModelRefIndex { ref -> if (ref == "er.entity.customer") TargetClass.MODEL_OBJECT else null }
            val composed = index orElse er

            composed.classify("er.entity.customer") shouldBe TargetClass.MODEL_OBJECT
            composed.classify("md.measure.revenue") shouldBe TargetClass.MODEL_OBJECT
            composed.classify("md.domain.Money") shouldBe null
        }
        "orElse keeps the FIRST index's answer when both would classify" {
            val everything = ModelRefIndex { TargetClass.OPERATOR }
            (index orElse everything).classify("md.dimension.DistributionCentre.dcCode.5") shouldBe TargetClass.MEMBER
            (everything orElse index).classify("md.dimension.DistributionCentre.dcCode.5") shouldBe TargetClass.OPERATOR
        }
    })
