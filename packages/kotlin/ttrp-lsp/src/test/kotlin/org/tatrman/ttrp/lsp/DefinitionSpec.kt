// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.tatrman.ttrp.lsp.test.TtrpLspHarness

class DefinitionSpec :
    StringSpec({
        fun defineLine(
            h: TtrpLspHarness,
            uri: String,
            pos: org.eclipse.lsp4j.Position,
        ): Int {
            val result =
                h.remote.textDocumentService
                    .definition(DefinitionParams(TextDocumentIdentifier(uri), pos))
                    .get()
            val locations = result.left
            locations.size shouldBe 1 // D-b position typing → never ambiguous
            return locations
                .first()
                .range.start.line
        }

        "definition on a variable use jumps to its latest visible SSA generation" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero.ttrp"
                val text = Fixtures.text("hero.ttrp")
                h.open(uri, text)
                // The `sales` used inside `filter(sales, amount …)` — its enclosing statement
                // is gen 2, so definition jumps to gen 1 (`sales = load(…)`), never itself.
                val usePos = Fixtures.positionOf(text, "sales, amount")
                val defLine = defineLine(h, uri, usePos)
                defLine shouldBe Fixtures.positionOf(text, "sales = load").line
            }
        }

        "definition on a container-port ref jumps to the port declaration" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero.ttrp"
                val text = Fixtures.text("hero.ttrp")
                h.open(uri, text)
                // `crunch.accounts` wiring → the `accounts` in-port declared in the header.
                val usePos = Fixtures.positionOf(text, "crunch.accounts")
                val defLine = defineLine(h, uri, usePos)
                defLine shouldBe Fixtures.positionOf(text, "in accounts").line
            }
        }

        "definition on a bare container ref jumps to the container declaration" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero.ttrp"
                val text = Fixtures.text("hero.ttrp")
                h.open(uri, text)
                // `acc_prep -> crunch.accounts` — the bare `acc_prep` head.
                val usePos = Fixtures.positionOf(text, "acc_prep -> crunch")
                val defLine = defineLine(h, uri, usePos)
                defLine shouldBe Fixtures.positionOf(text, "container acc_prep").line
            }
        }
    })
