// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server.graph

/**
 * Plain-text `.ttrg` graph-block READER (T3, TP-5). Deliberately **not** an AST
 * parser — checked the existing TS edit synthesizer (`@tatrman/edit/src/
 * graph-edits.ts`) before assuming one was needed, and found that even the real,
 * shipped TS implementation is plain keyword/bracket text-scanning, not
 * `@tatrman/parser`-based — there is no Kotlin `.ttrg` AST walker to reuse or
 * port (only the `.ttrl` sidecar has one, `TtrlLoader`/T1). Matching the repo's
 * own actual precedent rather than inventing a new parsing layer.
 *
 * FO-21 (FO-P0.S2.T5): this is the read-half / shared parse lib (move manifest §6).
 * The MUTATORS (`addObject`/`removeObject`/`createContent`) split out to
 * [TtrgGraphMutations] — the edit half. `parseHeader`/`findObjectsBrackets`/
 * `parseObjects` STAY here: they back the read handlers (`listGraphs`/`getGraph`)
 * AND the mutators, so they are the shared substrate both halves depend on.
 */
object TtrgGraphFile {
    private val GRAPH_HEADER = Regex("""graph\s+(\w+)\s*\{""")
    private val MODEL_PROPERTY = Regex("""\bmodel\s*:\s*(\w+)""")

    data class Header(
        val name: String,
        val schema: String?,
    )

    /** `graph <name> { model: <schema>, … }` header — null if the text isn't a graph file. */
    fun parseHeader(content: String): Header? {
        val m = GRAPH_HEADER.find(content) ?: return null
        val schema = MODEL_PROPERTY.find(content)?.groupValues?.get(1)
        return Header(name = m.groupValues[1], schema = schema)
    }

    /** The `objects: [ … ]` bracket extent (brace/bracket-depth matched from the FIRST `objects` keyword). */
    fun findObjectsBrackets(content: String): IntRange? {
        val kwIdx = content.indexOf("objects")
        if (kwIdx == -1) return null
        val openIdx = content.indexOf('[', kwIdx)
        if (openIdx == -1) return null
        var depth = 0
        var closeIdx = -1
        for (i in openIdx until content.length) {
            when (content[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        closeIdx = i
                        break
                    }
                }
            }
        }
        if (closeIdx == -1) return null
        return openIdx..closeIdx
    }

    /** The qnames currently listed in `objects: […]` (comma-split, trimmed, blanks dropped). */
    fun parseObjects(content: String): List<String> {
        val brackets = findObjectsBrackets(content) ?: return emptyList()
        val inner = content.substring(brackets.first + 1, brackets.last)
        return inner.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    // Mutators (addObject/removeObject/createContent + hasImportFor) split out to
    // TtrgGraphMutations (FO-P0.S2.T5, move manifest §6).
}
