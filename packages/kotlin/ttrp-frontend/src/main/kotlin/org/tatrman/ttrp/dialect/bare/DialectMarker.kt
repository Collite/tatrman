package org.tatrman.ttrp.dialect.bare

/**
 * Dialect marker resolution for bare-fragment programs (contracts §1, C0). Resolution
 * order is fully EXPLICIT — zero content sniffing (P2):
 *   1. a first-line comment override `-- ttr: dialect=sql` / `# ttr: dialect=pandas`
 *      (tolerating leading whitespace only) — WINS over the extension (C3-g-ii);
 *   2. the double extension `.ttr.sql` → sql, `.ttr.py` → pandas;
 *   3. neither → null (the caller emits TTRP-FRG-002).
 */
object DialectMarker {
    private val commentRe = Regex("""^\s*(--|#)\s*ttr:\s*dialect\s*=\s*(sql|pandas)\s*$""")

    /** The dialect for [fileName] with [content], or null if unmarked. */
    fun resolve(
        fileName: String,
        content: String,
    ): String? {
        commentOverride(content)?.let { return it }
        return extensionDialect(fileName)
    }

    fun commentOverride(content: String): String? {
        val firstLine = content.lineSequence().firstOrNull() ?: return null
        return commentRe.find(firstLine)?.groupValues?.get(2)
    }

    fun extensionDialect(fileName: String): String? {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".ttr.sql") -> "sql"
            lower.endsWith(".ttr.py") -> "pandas"
            else -> null
        }
    }

    /** True for a file that TTR-P should route to the bare-fragment path (not `.ttrp`, not `.ttrb`). */
    fun isBareFragmentFile(
        fileName: String,
        content: String,
    ): Boolean = resolve(fileName, content) != null
}
