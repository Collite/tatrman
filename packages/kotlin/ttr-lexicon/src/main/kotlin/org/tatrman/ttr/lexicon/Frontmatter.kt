// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon

/** A split skill file: the YAML frontmatter, its first line, and the body below it. */
data class Frontmatter(
    val yaml: String,
    /** 1-based line of the frontmatter's first content line — provenance offsets from here. */
    val firstKeyLine: Int,
    val body: String,
)

/**
 * Splits `---`-delimited frontmatter from a markdown body (RV-P1.1 T5).
 *
 * Deliberately narrow: the opening `---` must be the first non-blank line (a BOM and
 * leading blank lines are tolerated — both arrive from real editors), and the block ends
 * at the next line that is exactly `---`. A `---` further down is a horizontal rule in the
 * body and must not be mistaken for a terminator, so only the FIRST closing fence counts.
 */
object FrontmatterSplitter {
    private const val FENCE = "---"

    // Escaped, never a literal: an invisible character in source survives no round trip
    // through a formatter or an editor that trims it.
    private const val BOM = '\uFEFF'

    fun split(markdown: String): LexiconLoad<Frontmatter> {
        // Normalise CRLF so a Windows-authored skill splits the same as a Unix one; the body
        // is re-joined with '\n' for the same reason (the compiled artifact must be stable).
        val lines =
            markdown
                .removePrefix(BOM.toString())
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .split("\n")

        val openIdx = lines.indexOfFirst { it.isNotBlank() }
        if (openIdx < 0 || lines[openIdx].trim() != FENCE) {
            return LexiconLoad.Rejected(
                listOf(LexiconErrors.missingFrontmatter(Provenance("", 1))),
            )
        }

        val closeIdx = (openIdx + 1 until lines.size).firstOrNull { lines[it].trim() == FENCE }
        if (closeIdx == null) {
            return LexiconLoad.Rejected(
                listOf(LexiconErrors.unterminatedFrontmatter(Provenance("", openIdx + 1))),
            )
        }

        return LexiconLoad.Ok(
            Frontmatter(
                yaml = lines.subList(openIdx + 1, closeIdx).joinToString("\n"),
                // 1-based line of the first line INSIDE the block.
                firstKeyLine = openIdx + 2,
                body = lines.subList(closeIdx + 1, lines.size).joinToString("\n").trim(),
            ),
        )
    }
}
