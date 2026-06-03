package org.tatrman.ttr.parser.walker

/**
 * Implements Python's `textwrap.dedent` algorithm.
 *
 * Removes the common leading whitespace from every non-blank line in the
 * input. Lines that consist solely of whitespace are normalised but do not
 * contribute to the common-prefix calculation. The algorithm mirrors
 * CPython's `textwrap.dedent` so triple-quoted TTR sources behave like
 * triple-quoted Python sources.
 */
object Dedent {
    fun applyTextwrapDedent(text: String): String {
        // Drop the leading newline immediately after `"""` so authors can
        // write the content starting on the next line without an extra blank.
        val withoutLeadingNewline = if (text.startsWith("\n")) text.drop(1) else text
        val lines = withoutLeadingNewline.split("\n")

        // Compute the longest common leading-whitespace prefix across all
        // non-whitespace-only lines.
        var commonPrefix: String? = null
        for (line in lines) {
            if (line.isBlank()) continue
            val leading = line.takeWhile { it == ' ' || it == '\t' }
            commonPrefix =
                when {
                    commonPrefix == null -> leading
                    else -> longestCommonPrefix(commonPrefix!!, leading)
                }
            if (commonPrefix.isEmpty()) break
        }
        val prefix = commonPrefix ?: ""
        if (prefix.isEmpty()) return withoutLeadingNewline

        return lines.joinToString("\n") { line ->
            when {
                line.isBlank() -> line.trimEnd()
                line.startsWith(prefix) -> line.removePrefix(prefix)
                else -> line
            }
        }
    }

    private fun longestCommonPrefix(
        a: String,
        b: String,
    ): String {
        var i = 0
        val limit = minOf(a.length, b.length)
        while (i < limit && a[i] == b[i]) i++
        return a.substring(0, i)
    }
}
