// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.model.RoleDef

/**
 * Mirrors the intent of `packages/semantics/src/stock-loader.ts` + the bundled
 * `stock/cnc-roles.ttr`. The six canonical stock roles are the single source of
 * truth for CNC vocabulary (contract §4.7).
 */
class StockLoaderSpec :
    StringSpec({
        val roleNames = listOf("fact", "dimension", "structural", "master", "transaction", "bridge")

        "load() returns a RoleDef per known stock role" {
            val defs = StockLoader.load()
            val roles = defs.filterIsInstance<RoleDef>().map { it.name }
            roles shouldContainAll roleNames
        }

        "stockQnames() returns the doubled cnc.role.* form that stock is stored under" {
            StockLoader.stockQnames() shouldContainAll roleNames.map { Qname("cnc.role.$it") }
        }

        "stockQnames() are exactly the qnames a SymbolTable stores stock under" {
            // Lock the contract: each returned qname must resolve to a stored stock symbol.
            val symbols = SymbolTable()
            symbols.upsertDocument("stock://cnc-roles.ttr", StockLoader.load(), "cnc", "role", "")
            StockLoader.stockQnames().forEach { q ->
                (symbols.get(q.value) != null) shouldBe true
            }
        }

        "load() parses without producing partial junk" {
            StockLoader.load().all { it is RoleDef } shouldBe true
        }
    })
