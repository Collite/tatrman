// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp

/** Reads a fixture from `src/test/resources/fixtures/`. */
object Fixtures {
    fun text(name: String): String =
        Fixtures::class.java.getResource("/fixtures/$name")?.readText()
            ?: error("fixture not found: /fixtures/$name")

    /** 0-indexed (line, character) of the first occurrence of [needle] in [text]. */
    fun positionOf(
        text: String,
        needle: String,
        occurrence: Int = 1,
    ): org.eclipse.lsp4j.Position {
        var idx = -1
        repeat(occurrence) { idx = text.indexOf(needle, idx + 1) }
        require(idx >= 0) { "needle not found: $needle (#$occurrence)" }
        val before = text.substring(0, idx)
        val line = before.count { it == '\n' }
        val col = idx - (before.lastIndexOf('\n') + 1)
        return org.eclipse.lsp4j.Position(line, col)
    }
}
