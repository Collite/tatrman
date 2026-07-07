package org.tatrman.ttrp.lsp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.tatrman.ttrp.lsp.test.TtrpLspHarness

class HoverSpec :
    StringSpec({
        "hover over an SSA variable shows its resolved port schema" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero.ttrp"
                val text = Fixtures.text("hero.ttrp")
                h.open(uri, text)
                // Hover the `sums` assignment target — its schema is the aggregate output.
                val pos = Fixtures.positionOf(text, "sums = j")
                val hover =
                    h.remote.textDocumentService
                        .hover(HoverParams(TextDocumentIdentifier(uri), pos))
                        .get()
                hover.shouldNotBeNull()
                val md = hover.contents.right.value
                md shouldContain "sums"
                md shouldContain "total"
            }
        }

        "hover over an er-sourced column shows the E-d provenance origin" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero-er.ttrp"
                val text = Fixtures.text("hero-er.ttrp")
                h.open(uri, text)
                // The er attribute `customerType` in `filter(c, customerType = 'retail')`.
                val pos = Fixtures.positionOf(text, "customerType = 'retail'")
                val hover =
                    h.remote.textDocumentService
                        .hover(HoverParams(TextDocumentIdentifier(uri), pos))
                        .get()
                hover.shouldNotBeNull()
                val md = hover.contents.right.value
                md shouldContain "customerType"
                md shouldContain "er→db"
            }
        }
    })
