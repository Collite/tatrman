// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server.graph

/**
 * Plain-text `.ttrg` graph-block reader/editor (T3, TP-5). Deliberately **not**
 * an AST parser — checked the existing TS edit synthesizer
 * (`@tatrman/edit/src/graph-edits.ts`, `buildAddObjectEdit`/`buildRemoveObjectEdit`/
 * `buildCreateGraphEdit`) before assuming one was needed, and found that even the
 * real, shipped TS implementation is plain keyword/bracket text-scanning, not
 * `@tatrman/parser`-based — there is no Kotlin `.ttrg` AST walker to reuse or
 * port (only the `.ttrl` sidecar has one, `TtrlLoader`/T1). Matching the repo's
 * own actual precedent rather than inventing a new parsing layer.
 *
 * Per T3.1's ratified edit-application model, this operates directly on full
 * file content and returns full new content for the caller to write —
 * simpler than the TS side's `WorkspaceEdit` deltas (built for a remote host
 * to apply; the JVM server just writes the file itself).
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

    /** Whether the file already has an `import <packageName>` line. */
    fun hasImportFor(
        content: String,
        packageName: String,
    ): Boolean = Regex("""import\s+${Regex.escape(packageName)}\b""").containsMatchIn(content)

    /**
     * Insert [qname] into the `objects: […]` list, optionally adding an `import`
     * line for [packageToImport] if not already present. Mirrors
     * `buildAddObjectEdit`'s text logic, applied directly (no diffs).
     */
    fun addObject(
        content: String,
        qname: String,
        packageToImport: String?,
    ): String? {
        val brackets = findObjectsBrackets(content) ?: return null
        val inner = content.substring(brackets.first + 1, brackets.last)
        val insertion =
            if (inner.trim().isEmpty()) {
                qname
            } else if (inner.trimEnd().endsWith(",")) {
                qname
            } else {
                ", $qname"
            }
        var out = content.substring(0, brackets.last) + insertion + content.substring(brackets.last)

        if (packageToImport != null && !hasImportFor(content, packageToImport)) {
            val lastImportIdx = content.lastIndexOf("import ")
            val insertOffset =
                if (lastImportIdx == -1) {
                    0
                } else {
                    val lineEnd = content.indexOf('\n', lastImportIdx)
                    if (lineEnd == -1) content.length else lineEnd + 1
                }
            val importLine = if (lastImportIdx == -1) "import $packageToImport\n\n" else "import $packageToImport\n"
            out = out.substring(0, insertOffset) + importLine + out.substring(insertOffset)
        }
        return out
    }

    /**
     * Remove [qname] from the `objects: […]` list (a whole-token match — a
     * qname that's a substring of another entry is never touched). Optionally
     * prunes the `import` line for its package if no other listed object still
     * needs it. Returns null if [qname] isn't present (nothing to do).
     */
    fun removeObject(
        content: String,
        qname: String,
        pruneUnusedImport: Boolean,
    ): String? {
        val brackets = findObjectsBrackets(content) ?: return null
        val inner = content.substring(brackets.first + 1, brackets.last)
        val newInner = removeFromObjectsList(inner, qname) ?: return null

        var out = content.substring(0, brackets.first + 1) + newInner + content.substring(brackets.last)

        if (pruneUnusedImport) {
            val packageName = qname.substringBefore('.', missingDelimiterValue = "").ifEmpty { null }
            if (packageName != null && !newInner.contains("$packageName.")) {
                val importPattern = Regex("""import\s+${Regex.escape(packageName)}\b[^\n]*\n?""")
                out = importPattern.replace(out, "")
            }
        }
        return out
    }

    private fun removeFromObjectsList(
        inner: String,
        qname: String,
    ): String? {
        val entries = inner.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (qname !in entries) return null
        val remaining = entries.filter { it != qname }
        return if (remaining.isEmpty()) "" else remaining.joinToString(", ")
    }

    data class CreateGraphParams(
        val name: String,
        val schema: String,
        val packages: List<String>,
        val objects: List<String>,
        val description: String?,
        val tags: List<String>,
    )

    /** Full content for a brand-new `.ttrg` file. Mirrors `buildCreateGraphContent`. */
    fun createContent(params: CreateGraphParams): String {
        val lines = mutableListOf<String>()
        if (params.packages.isNotEmpty()) {
            params.packages.forEach { lines += "import $it" }
            lines += ""
        }
        val graphProps = mutableListOf("model: ${params.schema}")
        params.description?.let { graphProps += "description: \"$it\"" }
        if (params.tags.isNotEmpty()) graphProps += "tags: [${params.tags.joinToString(", ") { "\"$it\"" }}]"
        val objectsStr =
            if (params.objects.isNotEmpty()) {
                "objects: [${params.objects.joinToString(
                    ", ",
                )}]"
            } else {
                "objects: []"
            }

        lines += "graph ${params.name} {"
        graphProps.forEach { lines += "    $it" }
        lines += "    $objectsStr"
        lines += "}"
        return lines.joinToString("\n") + "\n"
    }
}
