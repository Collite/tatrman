// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.parser.loader.TtrLoader

/**
 * Phase 2.5.6 — stock vocab loaded into the symbol table makes bare stock-role
 * references resolve via step 5 (auto-import).
 */
class StockAutoImportIntegrationSpec :
    StringSpec({
        "fact and dimension resolve via auto-import when stock is loaded" {
            val table = SymbolTable()

            // Load the bundled stock vocab under the canonical stock:// URI, so the
            // symbol table stores the doubled cnc.role.* qnames the resolver expects.
            val stock = StockLoader.load()
            table.upsertDocument("stock://cnc-stock-roles.ttr", stock, "cnc", "role", "")

            val src = "model er schema entity\ndef entity X { roles: [fact, dimension], attributes: [] }"
            val parsed = TtrLoader.parseString(src, "er.ttr")
            table.upsertDocument("er.ttr", parsed.definitions, "er", "entity", "")

            val resolver = Resolver(table)

            for (role in listOf("fact", "dimension")) {
                val res =
                    resolver.resolveReference(
                        Resolver.Ref(role, listOf(role)),
                        ResolutionContext(schemaCode = "er", namespace = "entity"),
                    )
                val r = res.shouldBeInstanceOf<ResolutionResult.Resolved>()
                r.viaStep shouldBe ResolutionStep.AutoImport
                r.symbol.qname shouldBe "cnc.role.$role"
            }
        }
    })
