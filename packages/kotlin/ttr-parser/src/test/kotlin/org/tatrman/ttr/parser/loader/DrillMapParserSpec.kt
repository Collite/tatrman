// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.parser.model.DrillMapDef

/**
 * Phase 03 (new-Golem) — `def drill_map { from, to, args, display?, override? }`
 * lands as v2.2 of the TTR grammar (modeler@10282d4, vendored).
 *
 * Spec note: the Phase 03 task list pointed to
 * `infra/metadata/.../ttr/parser/DrillMapParserSpec.kt`, but the live parser
 * (and its sibling specs) actually live in the `ttr-parser` shared library, so
 * the spec lives here. Cross-reference + arg-name validation belongs to the
 * metadata-side validator (DrillMapValidatorSpec); this spec is parser-only.
 */
class DrillMapParserSpec :
    StringSpec({

        "happy path — full drill_map block round-trips into DrillMapDef" {
            val r =
                TtrLoader.parseString(
                    """
                    package ucetnictvi
                    model query schema drill

                    def drill_map agg_strediska_na_doklad {
                        from: query.query.ucetni_zapisy_agregace_strediska,
                        to:   query.query.ucetni_doklad_detail,
                        args: { id_ucetniho_zapisu: "IDUCETZAP" },
                        display: { cs: "Detail dokladu" },
                        override: true,
                    }
                    """.trimIndent(),
                )

            r.ok shouldBe true
            r.definitions shouldHaveSize 1
            val def = r.definitions[0]
            def.shouldBeInstanceOf<DrillMapDef>()
            def.name shouldBe "agg_strediska_na_doklad"
            def.from?.path shouldBe "query.query.ucetni_zapisy_agregace_strediska"
            def.to?.path shouldBe "query.query.ucetni_doklad_detail"
            def.args["id_ucetniho_zapisu"] shouldBe "IDUCETZAP"
            def.display?.byLanguage?.get("cs") shouldBe "Detail dokladu"
            def.overrideAuto shouldBe true
        }

        "minimal drill_map — display + override default to absent/false" {
            val r =
                TtrLoader.parseString(
                    """
                    package ucetnictvi
                    model query schema drill

                    def drill_map agg_uctu_na_doklad {
                        from: query.query.ucetni_zapisy_agregace_uctu,
                        to:   query.query.ucetni_doklad_detail,
                        args: { id_ucetniho_zapisu: "IDUCETZAP" },
                    }
                    """.trimIndent(),
                )

            r.ok shouldBe true
            val def = r.definitions[0]
            def.shouldBeInstanceOf<DrillMapDef>()
            def.display shouldBe null
            def.overrideAuto shouldBe false
        }

        "unknown property key inside drill_map block surfaces as parse error" {
            val r =
                TtrLoader.parseString(
                    """
                    package ucetnictvi
                    model query schema drill

                    def drill_map x {
                        frmo: query.query.a,
                        to:   query.query.b,
                        args: { p: "C" },
                    }
                    """.trimIndent(),
                )

            r.ok shouldBe false
            r.errors.isNotEmpty() shouldBe true
        }

        "empty args block parses cleanly (validation deferred to metadata side)" {
            val r =
                TtrLoader.parseString(
                    """
                    package ucetnictvi
                    model query schema drill

                    def drill_map x {
                        from: query.query.a,
                        to:   query.query.b,
                        args: {},
                    }
                    """.trimIndent(),
                )

            r.ok shouldBe true
            val def = r.definitions[0]
            def.shouldBeInstanceOf<DrillMapDef>()
            def.args.isEmpty() shouldBe true
        }
    })
