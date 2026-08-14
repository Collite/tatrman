// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.writer

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.TableDef

/**
 * NLS-P10 T4 (grammar 0.13, ⚑GXP-D7) — the localised `description: { … }` form survives
 * write → reparse, and the plain form is untouched by the widening.
 *
 * The round-trip returns the form the AUTHOR wrote: the two carriers are mutually
 * exclusive in the model, so the renderer never has to choose between them and a
 * plain-string model still renders byte-identically to what 0.12 produced.
 */
class LocalizedDescriptionRoundTripSpec :
    StringSpec({

        val src =
            """
            model er schema entity
            def entity Product {
                description: { en: "a product", cs: "produkt" },
                attributes: [
                    def attribute name { type: string, description: { en: "its name", cs: "jméno" } },
                    def attribute code { type: string, description: "plain and stays plain" }
                ]
            }
            def entity Plain { description: "an old-style description", attributes: [def attribute id { type: int }] }
            """.trimIndent()

        "a model mixing both forms round-trips byte-stable" {
            val r1 = TtrLoader.parseString(src)
            r1.ok shouldBe true
            val text1 = TtrRenderer.render(r1)

            val r2 = TtrLoader.parseString(text1)
            r2.ok shouldBe true
            val text2 = TtrRenderer.render(r2)

            text2 shouldBe text1
        }

        "the rendered text carries the map form, not a folded single locale" {
            val text = TtrRenderer.render(TtrLoader.parseString(src))
            text shouldContain """description: { en: "a product", cs: "produkt" }"""
            text shouldContain """description: { en: "its name", cs: "jméno" }"""
            text shouldContain """description: "plain and stays plain""""
        }

        "both carriers survive the trip structurally" {
            val reparsed = TtrLoader.parseString(TtrRenderer.render(TtrLoader.parseString(src)))
            // by name, not by index — the renderer is free to group definitions by kind
            val product = reparsed.definitions.first { it.name == "Product" } as EntityDef
            product.description shouldBe null
            product.descriptionLocalized.byLanguage shouldBe mapOf("en" to "a product", "cs" to "produkt")
            product.attributes[0].descriptionLocalized.byLanguage shouldBe
                mapOf("en" to "its name", "cs" to "jméno")
            product.attributes[1].description shouldBe "plain and stays plain"
            product.attributes[1].descriptionLocalized.byLanguage shouldBe emptyMap()

            val plain = reparsed.definitions.first { it.name == "Plain" } as EntityDef
            plain.description shouldBe "an old-style description"
            plain.descriptionLocalized.byLanguage shouldBe emptyMap()
        }

        "a db model round-trips the map form on tables and columns" {
            val dbSrc =
                """
                model db schema dbo
                def table sales {
                    description: { en: "sales", cs: "prodeje" },
                    columns: [
                        def column amount { type: decimal, description: { cs: "částka" } }
                    ]
                }
                """.trimIndent()
            val r1 = TtrLoader.parseString(dbSrc)
            r1.ok shouldBe true
            val text1 = TtrRenderer.render(r1)
            val r2 = TtrLoader.parseString(text1)
            r2.ok shouldBe true
            TtrRenderer.render(r2) shouldBe text1

            val table = r2.definitions.first { it.name == "sales" } as TableDef
            table.descriptionLocalized.byLanguage shouldBe mapOf("en" to "sales", "cs" to "prodeje")
            table.columns[0].descriptionLocalized.byLanguage shouldBe mapOf("cs" to "částka")
        }

        "an empty map renders as nothing at all (there is no locale to write)" {
            val r =
                TtrLoader.parseString(
                    "model er schema entity\ndef entity E { description: {}, attributes: [def attribute id { type: int }] }",
                )
            r.ok shouldBe true
            val text = TtrRenderer.render(r)
            text shouldContain "def entity E"
            (text.contains("description")) shouldBe false
        }
    })
