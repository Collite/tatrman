package org.tatrman.ttr.semantics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Mirrors `packages/semantics/src/__tests__/symbol-table.test.ts`. The Kotlin
 * `SymbolTable` is the project-level table (TS `ProjectSymbolTable`): it builds
 * qnames from each document's schema/namespace/package the same way TS
 * `DocumentSymbolTable.makeQname` does.
 */
class SymbolTableSpec :
    StringSpec({
        "entity + 3 attributes register as 4 entries with qualified qname" {
            val t =
                Fixtures.symbolTable(
                    "file:///test.ttr" to
                        """
                        schema er namespace myns
                        def entity Order {
                          attributes: [
                            def attribute id { type: integer },
                            def attribute customer_id { type: integer },
                            def attribute total_amount { type: decimal }
                          ]
                        }
                        """.trimIndent(),
                )
            t.all() shouldHaveSize 4
            val order = t.get("er.myns.Order")
            order.shouldNotBeNull()
            order.name shouldBe "Order"
            order.kind shouldBe "entity"
            t.all().count { it.parent?.contains("Order") == true } shouldBe 3
        }

        "table + columns register as 3 entries" {
            val t =
                Fixtures.symbolTable(
                    "file:///test.ttr" to
                        """
                        schema db namespace dbo
                        def table orders {
                          columns: [
                            def column id { type: integer },
                            def column created_at { type: timestamp }
                          ]
                        }
                        """.trimIndent(),
                )
            t.all() shouldHaveSize 3
            t.get("db.dbo.orders").shouldNotBeNull()
        }

        "empty document yields no entries" {
            val t = Fixtures.symbolTable("file:///test.ttr" to "schema db\nmodel test {}")
            t.all() shouldHaveSize 0
        }

        "duplicates detects the same qname across two documents" {
            val src =
                """
                schema db
                def table users { columns: [ def column id { type: integer } ] }
                """.trimIndent()
            val t = Fixtures.symbolTable("file:///file1.ttr" to src, "file:///file2.ttr" to src)
            t.duplicates().any { it.qname == "db.table.users" } shouldBe true
        }

        "removeDocument removes all entries for a URI" {
            val t = SymbolTable()
            Fixtures.upsert(
                t,
                "file:///test.ttr",
                "schema db\ndef table users { columns: [ def column id { type: integer } ] }",
            )
            t.all() shouldHaveSize 2
            t.removeDocument("file:///test.ttr")
            t.all() shouldHaveSize 0
        }

        "findByName returns entries across documents" {
            val t =
                Fixtures.symbolTable(
                    "file:///f1.ttr" to "schema db\ndef table users { columns: [ def column id { type: integer } ] }",
                    "file:///f2.ttr" to
                        "schema er\ndef entity users { attributes: [ def attribute id { type: integer } ] }",
                )
            t.findByName("users").size shouldBe 2
        }

        "stock cnc symbols get the doubled cnc.cnc.role.* qname" {
            val t = SymbolTable()
            Fixtures.upsert(
                t,
                "stock://cnc-roles.ttr",
                "schema cnc namespace role\ndef role fact { description: \"Fact role\" }",
            )
            val fact = t.get("cnc.cnc.role.fact")
            fact.shouldNotBeNull()
            fact.documentUri shouldBe "stock://cnc-roles.ttr"
        }

        "getByPackage returns only that package's entries" {
            val t =
                Fixtures.symbolTable(
                    "billing/a.ttr" to
                        "package billing\nschema er namespace entity\ndef entity artikl { attributes: [] }",
                    "other/b.ttr" to
                        "package other\nschema er namespace entity\ndef entity produkt { attributes: [] }",
                )
            t.getByPackage("billing").all { it.packageName == "billing" } shouldBe true
            t.getByPackage("billing").any { it.name == "artikl" } shouldBe true
            t.getByPackage("billing").any { it.name == "produkt" } shouldBe false
        }

        "getBySuffix matches qnames ending in the suffix" {
            val t =
                Fixtures.symbolTable(
                    "db.ttr" to
                        "schema db namespace dbo\ndef table QSUBJEKT { columns: [ def column IDSUBJEKT { type: int } ] }",
                )
            t.getBySuffix("QSUBJEKT").any { it.qname == "db.dbo.QSUBJEKT" } shouldBe true
        }
    })
