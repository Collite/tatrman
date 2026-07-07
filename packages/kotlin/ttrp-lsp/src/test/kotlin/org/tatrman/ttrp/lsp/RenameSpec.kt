package org.tatrman.ttrp.lsp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.tatrman.ttrp.lsp.RenameService
import org.tatrman.ttrp.parser.TtrpParser
import org.tatrman.ttrp.lsp.test.TtrpLspHarness

class RenameSpec :
    StringSpec({
        "rename of an SSA-reassigned variable updates every generation and reference" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero.ttrp"
                val text = Fixtures.text("hero.ttrp")
                h.open(uri, text)
                val pos = Fixtures.positionOf(text, "sales = load")

                // prepareRename is valid on the variable.
                val prep =
                    h.remote.textDocumentService
                        .prepareRename(PrepareRenameParams(TextDocumentIdentifier(uri), pos))
                        .get()
                prep.first.start.line shouldBe pos.line

                val edit =
                    h.remote.textDocumentService
                        .rename(RenameParams(TextDocumentIdentifier(uri), pos, "revenue"))
                        .get()
                val docEdit = edit.documentChanges.first().left
                // 2 assignment targets + the filter source + the join `right: sales` = 4 sites.
                docEdit.edits.size shouldBe 4
                docEdit.edits.all { it.newText == "revenue" } shouldBe true
                docEdit.textDocument.version shouldBe 1

                // ζ groundwork: one remap per SSA generation, container-path-qualified.
                h.renameParticipant.remaps.map { it.oldKey to it.newKey } shouldContainAll
                    listOf(
                        "crunch/sales#1" to "crunch/revenue#1",
                        "crunch/sales#2" to "crunch/revenue#2",
                    )
            }
        }

        "prepareRename rejects a reserved port name (S10)" {
            val text = Fixtures.text("hero.ttrp")
            val doc = TtrpParser.parseString(text, "hero.ttrp").document
            val pos =
                Fixtures.positionOf(text, "err rejects").let {
                    org.eclipse.lsp4j.Position(
                        it.line,
                        it.character + 4,
                    )
                }
            val result = RenameService().prepareRename(text, doc, pos)
            (result is RenameService.Prepare.Invalid) shouldBe true
        }

        "renaming a container whose name is a substring of the `container` keyword edits the name, not the keyword" {
            // `on` occurs inside the word `container` (c-ON-tainer); a text scan from the keyword
            // start would mangle the keyword. The name has its own AST span, so rename is exact.
            val text =
                "uses world \"w\"\n" +
                    "container on target erp_pg \"\"\"sql\nx\n\"\"\"\n" +
                    "on -> display(main)\n"
            val doc = TtrpParser.parseString(text, "x.ttrp").document
            val p = Fixtures.positionOf(text, "on -> display")
            val pos = Position(p.line, p.character)
            val edit = RenameService().rename("file:///x.ttrp", text, doc, 1, pos, "sink")
            edit.shouldNotBeNull()
            val docEdit = edit.documentChanges.first().left
            val edits = docEdit.edits
            // The declaration-name edit lands on the real `on` token (col 10), never inside `container` (col 1).
            val declEdit = edits.first { it.range.start.line == 1 }
            declEdit.range.start.character shouldBe 10
            declEdit.range.end.character shouldBe 12
            edits.all { it.newText == "sink" } shouldBe true
        }
    })
