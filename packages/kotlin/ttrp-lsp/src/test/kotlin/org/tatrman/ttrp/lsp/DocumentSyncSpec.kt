// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.tatrman.ttrp.lsp.docs.DocumentStore

class DocumentSyncSpec :
    StringSpec({
        fun ranged(
            sl: Int,
            sc: Int,
            el: Int,
            ec: Int,
            text: String,
        ) = TextDocumentContentChangeEvent(Range(Position(sl, sc), Position(el, ec)), text)

        "open then ranged change mid-document splices correctly" {
            val store = DocumentStore()
            store.open("u", "hello\nworld\n", 1, "ttrp")
            // Replace "world" (line 1, cols 0..5) with "there".
            val updated = store.change("u", 2, listOf(ranged(1, 0, 1, 5, "there")))
            updated!!.text shouldBe "hello\nthere\n"
            updated.version shouldBe 2
        }

        "multiple changes in one didChange apply in order" {
            val store = DocumentStore()
            store.open("u", "abcXYZ", 1, "ttrp")
            // First delete "abc" (0..3), then the doc is "XYZ"; then insert "-" at 0.
            val updated =
                store.change(
                    "u",
                    2,
                    listOf(ranged(0, 0, 0, 3, ""), ranged(0, 0, 0, 0, "-")),
                )
            updated!!.text shouldBe "-XYZ"
        }

        "out-of-order (stale) version is dropped, not applied" {
            val store = DocumentStore()
            store.open("u", "x", 5, "ttrp")
            store.change("u", 3, listOf(ranged(0, 0, 0, 1, "y"))).shouldBeNull()
            store.get("u")!!.text shouldBe "x"
        }

        "surrogate-pair edit lands at the right offset" {
            val store = DocumentStore()
            // An emoji in a comment; 😀 is one code point = 2 UTF-16 units = 2 Java chars.
            store.open("u", "// 😀 tail", 1, "ttrp")
            // Insert "X" right after the emoji: LSP character index 4 (// =2, space=1, emoji=2 → col 5).
            val updated = store.change("u", 2, listOf(ranged(0, 5, 0, 5, "X")))
            updated!!.text shouldBe "// 😀X tail"
        }
    })
