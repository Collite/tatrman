package org.tatrman.ttr.writer

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.ParseResult
import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.SemanticsValue
import org.tatrman.ttr.parser.model.TableDef
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

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
                // Grounding Phase 1 (grammar 4.2) — `semantics { … }` on entity + attributes
                // (kind at entity level, role + refs/params at attribute level).
                "entity+semantics" to
                    """
                    def entity AccountingPeriod {
                        semantics { kind: period_table }
                        attributes: [
                            def attribute start_date { type: date, semantics { role: period_start } },
                            def attribute period { type: text, semantics { role: period_code, code_format: "yyyyMM" } },
                            def attribute amount { type: decimal, semantics { role: amount, currency: currency_code } }
                        ]
                    }
                    """.trimIndent(),
                "table+column+semantics" to
                    """
                    def table poi {
                        semantics { kind: poi }
                        columns: [
                            def column point { type: text, semantics { role: geo_point } }
                        ]
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

        // Grounding Phase 1 (grammar 4.2) — the golden 59-semantics.ttrm fixture:
        // parse → write → reparse, then assert every `semantics { … }` block (entity
        // kinds + attribute roles/refs/params) is byte-for-byte reproduced. This is
        // the AST-equal-modulo-trivia guarantee specialised to the semantics surface.
        "round-trips the 59-semantics.ttrm fixture's semantics blocks" {
            val fixture = locateFixturesDir().resolve("59-semantics.ttrm")
            val parsed1 = TtrLoader.parseFile(fixture)
            parsed1.ok shouldBe true

            val reparsed = TtrLoader.parseString(TtrRenderer.render(parsed1.definitions))
            reparsed.ok shouldBe true

            collectSemantics(reparsed) shouldBe collectSemantics(parsed1)
        }
    })

/**
 * Every `semantics { … }` block keyed by its owner path (entity/table +
 * attribute/column), reduced to `(entries, duplicateProperties)` — `source` spans
 * legitimately shift under re-rendering, so they are excluded from the compare.
 */
private fun collectSemantics(r: ParseResult): Map<String, Pair<Map<String, SemanticsValue>, List<String>>> {
    val out = LinkedHashMap<String, Pair<Map<String, SemanticsValue>, List<String>>>()
    for (def in r.definitions) {
        when (def) {
            is EntityDef -> {
                def.semantics?.let { out[def.name] = it.entries to it.duplicateProperties }
                def.attributes.forEach { a ->
                    a.semantics?.let {
                        out["${def.name}.${a.name}"] =
                            it.entries to it.duplicateProperties
                    }
                }
            }
            is TableDef -> {
                def.semantics?.let { out[def.name] = it.entries to it.duplicateProperties }
                def.columns.forEach { c ->
                    c.semantics?.let {
                        out["${def.name}.${c.name}"] =
                            it.entries to it.duplicateProperties
                    }
                }
            }
            else -> {}
        }
    }
    return out
}

private fun locateFixturesDir(): Path {
    var dir: Path? = Paths.get("").toAbsolutePath()
    while (dir != null) {
        val candidate = dir.resolve("tests/conformance/fixtures")
        if (Files.isDirectory(candidate)) return candidate
        dir = dir.parent
    }
    error("could not locate tests/conformance/fixtures")
}
