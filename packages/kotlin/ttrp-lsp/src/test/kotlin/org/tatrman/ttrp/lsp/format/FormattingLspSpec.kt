// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp.format

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.tatrman.ttrp.lsp.test.TtrpLspHarness
import java.nio.file.Files
import java.nio.file.Path

class FormattingLspSpec :
    StringSpec({
        val dir = Path.of("src/test/resources/format")

        "formatting a chain-linear document returns the .expected bytes" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///chain-linear.ttrp"
                val input = Files.readString(dir.resolve("chain-linear.in.ttrp"))
                val expected = Files.readString(dir.resolve("chain-linear.expected.ttrp"))
                h.open(uri, input)
                val edits =
                    h.remote.textDocumentService
                        .formatting(DocumentFormattingParams(TextDocumentIdentifier(uri), FormattingOptions(2, true)))
                        .get()
                // Applying the single whole-document edit yields the expected text.
                edits.size shouldBe 1
                edits.first().newText shouldBe expected
            }
        }

        "an already-canonical document yields no edits (format-on-save friendly)" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///canonical.ttrp"
                val expected = Files.readString(dir.resolve("chain-linear.expected.ttrp"))
                h.open(uri, expected)
                val edits =
                    h.remote.textDocumentService
                        .formatting(DocumentFormattingParams(TextDocumentIdentifier(uri), FormattingOptions(2, true)))
                        .get()
                edits shouldBe emptyList()
            }
        }

        "a bare-fragment (.ttr.sql) document is never formatted" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///island.ttr.sql"
                h.open(uri, "SELECT   1", languageId = "ttr-sql")
                val edits =
                    h.remote.textDocumentService
                        .formatting(DocumentFormattingParams(TextDocumentIdentifier(uri), FormattingOptions(2, true)))
                        .get()
                edits shouldBe emptyList()
            }
        }
    })
