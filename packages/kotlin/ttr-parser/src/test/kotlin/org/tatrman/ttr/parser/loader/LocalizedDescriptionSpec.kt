// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.QueryDef
import org.tatrman.ttr.parser.model.RoleDef
import org.tatrman.ttr.parser.model.TableDef

/**
 * NLS-P10 (⚑GXP-D7, grammar 0.13) — Kotlin parity for the localised `description:`
 * map form. Mirrors the TS `localized-description.test.ts`.
 *
 * Two fields, never one: [org.tatrman.ttr.parser.model.Definition.description] keeps
 * the PLAIN form only (every existing consumer unchanged) and `descriptionLocalized`
 * carries the map. The walker does NOT fold the map to one locale — that is the
 * reader's job (Veles' D7 fallback chain).
 */
class LocalizedDescriptionSpec :
    StringSpec({

        fun entity(body: String): EntityDef {
            val r = TtrLoader.parseString("def entity Product { $body }")
            r.ok shouldBe true
            return r.definitions[0] as EntityDef
        }

        "a plain string description still lands in `description`" {
            val e = entity("""description: "the product"""")
            e.description shouldBe "the product"
            e.descriptionLocalized.byLanguage shouldBe emptyMap()
        }

        "a two-locale map lands in `descriptionLocalized`, `description` stays null" {
            val e = entity("""description: { en: "product name", cs: "Název produktu" }""")
            e.description shouldBe null
            e.descriptionLocalized.byLanguage shouldBe
                mapOf("en" to "product name", "cs" to "Název produktu")
        }

        "a single-entry map is legal" {
            entity("""description: { cs: "Produkt" }""").descriptionLocalized.byLanguage shouldBe
                mapOf("cs" to "Produkt")
        }

        "an EMPTY map parses and yields an empty entry set (no parse error)" {
            // Ruling (NLS-P10 T1): mechanical parser — the empty map is a LINT
            // warning, not a parse error; the D7 fallback chain already ends at "".
            val e = entity("description: {}")
            e.description shouldBe null
            e.descriptionLocalized.byLanguage shouldBe emptyMap()
        }

        "a triple-string value inside the map is dedented like anywhere else" {
            val r =
                TtrLoader.parseString(
                    "def entity Product { description: { en: \"\"\"\n    a\n    b\n    \"\"\" } }",
                )
            r.ok shouldBe true
            (r.definitions[0] as EntityDef).descriptionLocalized.byLanguage["en"] shouldBe "a\nb\n"
        }

        "the map form is legal on a table and its columns" {
            val r =
                TtrLoader.parseString(
                    """
                    def table sales {
                      description: { en: "sales", cs: "prodeje" },
                      columns: [
                        def column amount { type: decimal, description: { en: "amount", cs: "částka" } }
                      ]
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val t = r.definitions[0] as TableDef
            t.descriptionLocalized.byLanguage shouldBe mapOf("en" to "sales", "cs" to "prodeje")
            t.columns[0].descriptionLocalized.byLanguage shouldBe
                mapOf("en" to "amount", "cs" to "částka")
        }

        "the map form is legal on an entity and its attributes" {
            val r =
                TtrLoader.parseString(
                    """
                    def entity Product {
                      description: { en: "a product", cs: "produkt" },
                      attributes: [
                        def attribute name { type: string, description: { en: "its name", cs: "jméno" } }
                      ]
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val e = r.definitions[0] as EntityDef
            e.descriptionLocalized.byLanguage shouldBe mapOf("en" to "a product", "cs" to "produkt")
            e.attributes[0].descriptionLocalized.byLanguage shouldBe
                mapOf("en" to "its name", "cs" to "jméno")
        }

        "the map form is legal on a query" {
            val r =
                TtrLoader.parseString(
                    """def query top { description: { en: "top", cs: "nej" }, language: SQL }""",
                )
            r.ok shouldBe true
            (r.definitions[0] as QueryDef).descriptionLocalized.byLanguage shouldBe
                mapOf("en" to "top", "cs" to "nej")
        }

        "the map form is legal on a role" {
            val r =
                TtrLoader.parseString(
                    """def role customer { description: { en: "buyer", cs: "kupující" } }""",
                )
            r.ok shouldBe true
            (r.definitions[0] as RoleDef).descriptionLocalized.byLanguage shouldBe
                mapOf("en" to "buyer", "cs" to "kupující")
        }

        "both forms coexist across definitions in one document" {
            val r =
                TtrLoader.parseString(
                    """
                    def entity A { description: "plain" }
                    def entity B { description: { en: "mapped" } }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val a = r.definitions[0] as EntityDef
            val b = r.definitions[1] as EntityDef
            a.description shouldBe "plain"
            a.descriptionLocalized.byLanguage shouldBe emptyMap()
            b.description shouldBe null
            b.descriptionLocalized.byLanguage shouldBe mapOf("en" to "mapped")
        }
    })
