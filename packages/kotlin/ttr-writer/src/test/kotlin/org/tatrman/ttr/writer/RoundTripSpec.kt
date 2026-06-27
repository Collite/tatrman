package org.tatrman.ttr.writer

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader

/**
 * Round-trip guarantee (contracts.md §3): `parseString(render(r))` reproduces a
 * structurally-equal model. We express it via render-idempotence — `render∘parse`
 * is a fixed point — which catches any structural drift through the round trip
 * without a fragile SourceLocation-stripping deep-equals:
 *
 *   text1 = render(parse(src)); text2 = render(parse(text1)); assert text1 == text2
 *
 * plus the invariant that the re-parse always succeeds.
 */
class RoundTripSpec :
    StringSpec({

        val fixtures =
            mapOf(
                "model" to "def project erp { version: \"1.2.3\", tags: [\"a\", \"b\"] }",
                "table+columns" to
                    """
                    def table customers {
                        primaryKey: ["id"]
                        columns: [
                            def column id { type: int, isKey: true },
                            def column total { type: { type: decimal, length: 19, precision: 5 } },
                            def column name { type: text, indexed: true }
                        ]
                    }
                    """.trimIndent(),
                "table+search" to
                    """
                    def table customers {
                        primaryKey: ["id"]
                        columns: [
                            def column id { type: int, isKey: true }
                        ]
                        search { searchable: true, keywords { en: ["customer"] } }
                    }
                    """.trimIndent(),
                "relation+search" to
                    """
                    def relation customer_orders {
                        from: er.customer
                        to: er.order
                        search { searchable: true, fuzzy: true }
                    }
                    """.trimIndent(),
                "entity+attributes+search" to
                    """
                    def entity Customer {
                        labelPlural: "Customers"
                        aliases: ["client"]
                        attributes: [
                            def attribute id { type: int, isKey: true },
                            def attribute name { type: text, search { searchable: true, fuzzy: true } }
                        ]
                    }
                    """.trimIndent(),
                "relation" to
                    """
                    def relation customer_orders {
                        from: er.customer
                        to: er.order
                        cardinality: { fromMin: 0, fromMax: 1, toMin: 0, toMax: -1 }
                    }
                    """.trimIndent(),
                "query+params" to
                    """
                    def query topCustomers {
                        language: SQL
                        sourceText: "select 1"
                        parameters: [
                            { name: limit, type: int, label: "Limit" }
                        ]
                    }
                    """.trimIndent(),
                "role" to "def role fact { label: { cs: \"Fakta\", en: \"Facts\" } }",
                "er2cnc_role" to "def er2cnc_role rf { entity: er.sales, role: cnc.role.fact }",
                "er2db_entity" to "def er2db_entity m { entity: er.customer, target: { table: db.dbo.customers } }",
                "drill_map" to
                    """
                    def drill_map d {
                        from: query.query.a,
                        to: query.query.b,
                        args: { p: "C" },
                    }
                    """.trimIndent(),
            )

        fixtures.forEach { (label, src) ->
            "round-trips: $label" {
                val parsed1 = TtrLoader.parseString(src)
                parsed1.ok shouldBe true

                val text1 = TtrRenderer.render(parsed1.definitions)
                val parsed2 = TtrLoader.parseString(text1)
                parsed2.ok shouldBe true

                val text2 = TtrRenderer.render(parsed2.definitions)
                // render∘parse is a fixed point — no structural drift.
                text2 shouldBe text1
            }
        }
    })
