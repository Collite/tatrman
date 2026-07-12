// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.ast

/**
 * ANTLR-style source span — the repo-wide convention (CLAUDE.md §Key invariants),
 * identical in shape to `org.tatrman.ttr.parser.model.SourceLocation` on the TTR-M
 * side so both toolchains report positions the same way.
 *
 *  - `line` / `endLine` are 1-indexed (match ANTLR `token.line`).
 *  - `column` / `endColumn` are 0-indexed (match ANTLR `charPositionInLine`);
 *    `endColumn` is one past the last character.
 *  - `offsetStart` / `offsetEnd` are 0-indexed; `offsetEnd` is exclusive.
 *
 * The multi-token-span invariant: `endColumn = stop.charPositionInLine +
 * stopText.length` — NOT `startColumn + spanLength`. LSP consumers subtract 1 from
 * `line` / `endLine`.
 */
data class SourceLocation(
    val file: String,
    val line: Int,
    val column: Int,
    val endLine: Int,
    val endColumn: Int,
    val offsetStart: Int,
    val offsetEnd: Int,
) {
    override fun toString(): String = "$file:$line:$column"

    companion object {
        val UNKNOWN = SourceLocation("<unknown>", -1, -1, -1, -1, -1, -1)
    }
}

/**
 * A comment attached to an AST node (lossless CST/trivia layer, C2-f/P4). Comments
 * ride the lexer's HIDDEN channel; [org.tatrman.ttrp.parser.TtrpParser] attaches
 * the nearest preceding hidden tokens as `leadingTrivia` and same-line following
 * comments as `trailingTrivia`. Fragment interiors are verbatim `sourceText` and
 * are never trivia-scanned.
 */
data class Trivia(
    val text: String,
    val location: SourceLocation,
)
