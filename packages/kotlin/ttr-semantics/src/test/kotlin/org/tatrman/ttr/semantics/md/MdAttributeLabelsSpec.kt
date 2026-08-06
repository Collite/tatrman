// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader

/**
 * RV-P3.4 T2 — a dimension attribute's LABELS survive the build.
 *
 * `MdAttribute` carried name/dimension/domainRef/isKey/aggregation and dropped `displayLabel` and
 * `valueLabels` on the floor, even though `AttributeDef` parses both. The lexicon compiler's
 * METADATA layer is exactly those two fields, and its member index is exactly `valueLabels` — so
 * every md member term in every estate was unbuildable, not merely unbound. hartland declares five
 * distribution-centre labels and 35 return-reason labels this way and got `MEMBER = 0`.
 *
 * The model below is hartland-shaped on purpose: keys are surrogate codes ("5" = the Memphis/Brno
 * DC, the meltdown site), and the labels are bilingual because the estate is.
 */
class MdAttributeLabelsSpec :
    StringSpec({
        val source =
            """
            package hartland

            model md

            def domain DcCode { type: string }
            def domain DcName { type: string }

            def dimension DistributionCentre {
                key: dcCode,
                attributes: [
                    def attribute dcCode {
                        domain: md.DcCode,
                        isKey: true,
                        displayLabel: { cs: "Distribuční centrum", en: "Distribution centre" },
                        valueLabels: {
                            "5": { en: "Memphis DC", cs: "Brno DC" },
                            "1": { en: "Columbus DC", cs: "Praha DC" }
                        }
                    },
                    def attribute dcName { domain: md.DcName }
                ]
            }
            """.trimIndent()

        val parsed = TtrLoader.parseString(source, "md-labels.ttrm")
        val model = MdModel.from(parsed.definitions)
        val dcCode = model.attributes.getValue("DistributionCentre.dcCode")

        "the fixture parses — a spec that silently tested an empty model would pass forever" {
            parsed.ok shouldBe true
            model.dimensions.keys shouldContainExactlyInAnyOrder setOf("DistributionCentre")
        }

        "an attribute keeps its valueLabels, keyed by the member code" {
            dcCode.valueLabels.keys shouldContainExactlyInAnyOrder setOf("5", "1")
            dcCode.valueLabels.getValue("5").byLanguage["en"] shouldBe "Memphis DC"
            dcCode.valueLabels.getValue("5").byLanguage["cs"] shouldBe "Brno DC"
        }

        "an attribute keeps its displayLabel, in every declared language" {
            val label = dcCode.displayLabel.shouldNotBeNull()
            label.byLanguage["cs"] shouldBe "Distribuční centrum"
            label.byLanguage["en"] shouldBe "Distribution centre"
        }

        "an attribute that declares neither carries empty, not null — the absent case is ordinary" {
            val dcName = model.attributes.getValue("DistributionCentre.dcName")
            dcName.valueLabels shouldBe emptyMap()
            dcName.displayLabel shouldBe null
        }
    })
