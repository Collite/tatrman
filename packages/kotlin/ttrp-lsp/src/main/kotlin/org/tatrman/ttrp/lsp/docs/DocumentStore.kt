package org.tatrman.ttrp.lsp.docs

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import java.util.concurrent.ConcurrentHashMap

/** One open document: its current text, LSP version, and language id. */
data class OpenDocument(
    val uri: String,
    val text: String,
    val version: Int,
    val languageId: String,
)

/**
 * Per-uri open-document state with incremental [TextDocumentContentChangeEvent]
 * application (T4.1.3). Ranges are LSP positions — `character` counts UTF-16 code
 * units, which is exactly a Java `String` char index, so the conversion is index
 * arithmetic with no re-encoding (a surrogate-pair emoji is 2 units on both sides).
 *
 * Out-of-order versions are dropped, never applied: `didChange` requires the new
 * version to exceed the stored one (contracts §4 versioning discipline foundation).
 */
class DocumentStore {
    private val docs = ConcurrentHashMap<String, OpenDocument>()

    fun open(
        uri: String,
        text: String,
        version: Int,
        languageId: String,
    ) {
        docs[uri] = OpenDocument(uri, text, version, languageId)
    }

    fun get(uri: String): OpenDocument? = docs[uri]

    /** The URIs of every currently-open document. */
    fun openUris(): Set<String> = docs.keys.toSet()

    fun close(uri: String) {
        docs.remove(uri)
    }

    /**
     * Applies a batch of [changes] in order at [version]. A full-document change (null
     * range) replaces the text; a ranged change splices. Returns the updated document,
     * or null if [version] is not strictly greater than the stored version (stale drop).
     */
    fun change(
        uri: String,
        version: Int,
        changes: List<TextDocumentContentChangeEvent>,
    ): OpenDocument? {
        val current = docs[uri] ?: return null
        if (version <= current.version) return null
        var text = current.text
        for (change in changes) {
            val range = change.range
            text =
                if (range == null) {
                    change.text
                } else {
                    val start = offsetOf(text, range.start)
                    val end = offsetOf(text, range.end)
                    text.substring(0, start) + change.text + text.substring(end)
                }
        }
        val updated = current.copy(text = text, version = version)
        docs[uri] = updated
        return updated
    }

    companion object {
        /** LSP [Position] (0-indexed line, UTF-16 code-unit character) → Java `String` offset. */
        fun offsetOf(
            text: String,
            position: Position,
        ): Int {
            var line = 0
            var index = 0
            val len = text.length
            while (line < position.line && index < len) {
                val nl = text.indexOf('\n', index)
                if (nl < 0) {
                    index = len
                    break
                }
                index = nl + 1
                line++
            }
            // Clamp the character within the target line (defensive against bad ranges).
            val lineEnd = text.indexOf('\n', index).let { if (it < 0) len else it }
            return (index + position.character).coerceIn(index, lineEnd)
        }

        /** Build an empty range at a position (helper for callers). */
        fun emptyRange(position: Position): Range = Range(position, position)
    }
}
