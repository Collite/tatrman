// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect

import org.tomlj.Toml

/**
 * A versioned dialect reject table (contracts §8) loaded from a `*.rejects.toml`
 * resource. THE single source of reject messages/suggestions: the coverage spec, the
 * parser's reject scanner, and (P7) `ttrp/authoringContext`'s repair vocabulary all
 * read this — never a hard-coded string. Same TOML shape for TTR-SQL and TTR-pandas.
 */
data class RejectEntry(
    val id: String,
    val form: String,
    val example: String,
    val message: String,
    val suggest: String,
    val decision: String,
)

class RejectTable(
    val entries: List<RejectEntry>,
) {
    private val byId = entries.associateBy { it.id }

    fun entry(id: String): RejectEntry = byId[id] ?: error("no reject-table entry for $id")

    fun ids(): Set<String> = byId.keys

    companion object {
        /** Loads a reject table from a classpath resource (e.g. `/rejects/ttr-sql.rejects.toml`). */
        fun load(resourcePath: String): RejectTable {
            val text =
                RejectTable::class.java
                    .getResourceAsStream(resourcePath)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("reject table resource not found: $resourcePath")
            val toml = Toml.parse(text)
            require(toml.errors().isEmpty()) { "reject table $resourcePath parse errors: ${toml.errors()}" }
            val rejects = toml.getArrayOrEmpty("reject")
            val entries =
                (0 until rejects.size()).map { i ->
                    val t = rejects.getTable(i)

                    fun req(k: String): String =
                        t.getString(k) ?: error("reject table $resourcePath entry $i missing `$k`")
                    RejectEntry(
                        id = req("id"),
                        form = req("form"),
                        example = req("example"),
                        message = req("message"),
                        suggest = req("suggest"),
                        decision = req("decision"),
                    )
                }
            return RejectTable(entries)
        }
    }
}
