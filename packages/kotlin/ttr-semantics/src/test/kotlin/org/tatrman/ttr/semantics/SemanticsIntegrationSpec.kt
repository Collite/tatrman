package org.tatrman.ttr.semantics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * End-to-end wiring across Qname + SymbolTable + PackageInference +
 * PackageGraph (Phase 2.3.5). Establishes the pattern Phase 2.4 extends.
 */
class SemanticsIntegrationSpec :
    StringSpec({
        "parse → symbol table → package graph produces a coherent shape" {
            val builder =
                Fixtures.packageGraph(
                    "products/products.ttr" to
                        "package products\nschema er namespace entity\ndef entity produkt { attributes: [def attribute id { type: int }] }",
                    "app/app.ttr" to
                        "package app\nimport products.*\nschema er namespace entity\ndef relation r { from: produkt, to: produkt }",
                )

            val graph = builder.build()
            graph.nodes.map { it.name }.toSet() shouldBe setOf("products", "app")
            graph.edges shouldHaveSize 1
            graph.edges[0].from shouldBe "app"
            graph.edges[0].to shouldBe "products"
            builder.findCycles() shouldHaveSize 0
        }

        "inferred package matches the declared package for a nested file" {
            PackageInference.inferFromUri("/proj/products/products.ttr", "/proj/").inferred shouldBe "products"
        }

        "symbol table builds the expected qname for a nested-package entity" {
            val t =
                Fixtures.symbolTable(
                    "products/products.ttr" to
                        "package products\nschema er namespace entity\ndef entity produkt { attributes: [] }",
                )
            val produkt = t.get("products.er.entity.produkt")
            produkt.shouldNotBeNull()
            produkt.packageName shouldBe "products"
            Qname(produkt.qname).last shouldBe "produkt"
        }
    })
