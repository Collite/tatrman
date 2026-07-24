// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

/**
 * The resolver's input: one parsed component of an `mdPath`. This mirrors the TTRP.g4 `pathComponent`
 * shapes (§1.2) but is **owned by this module**, not by ttrp-frontend: the grammar's `mdPath` lives
 * in ttrp-frontend, which S3 makes depend on *this* module, so the resolver must not depend back on
 * it (MDS1). Callers build a `List<PathComponent>` either from the frontend AST (S3) or, for tests
 * and the S7 agent service, from raw text via [PathText].
 *
 * [Pair] is **not** produced by [PathText] — the grammar has no pair node (a qualifier and its
 * target are two dot-separated components). It exists so a caller can hand the resolver a
 * pre-qualified, position-free pair as a single movable unit (R6 "the pair is itself order-free
 * within the path"); [PairBinder] also recognises adjacent raw `[qualifier, target]` pairs.
 */
sealed interface PathComponent {
    /** A bare identifier: a member, level, attribute, measure, agg, or cubelet name. */
    data class Ident(
        val text: String,
    ) : PathComponent

    /** A numeric literal component (`2025`, `06`) — kept as text to preserve leading zeros. */
    data class IntLit(
        val text: String,
    ) : PathComponent

    /** A quoted member (`"Kaufland K123"`) — [text] is the unquoted content. */
    data class Quoted(
        val text: String,
    ) : PathComponent

    /** A member set (`{Kaufland, Lidl}`, D15). Atoms are [Ident]/[IntLit]/[Quoted] only. */
    data class SetLit(
        val atoms: List<PathComponent>,
    ) : PathComponent

    /** An inclusive range (`2024..2026`). [lo]/[hi] are [Ident]/[IntLit]/[Quoted] atoms. */
    data class RangeLit(
        val lo: PathComponent,
        val hi: PathComponent,
    ) : PathComponent

    /** A free dimension marker (`*`). */
    data object Star : PathComponent

    /** A pre-qualified pair (`customer.Kaufland`) treated as one order-free unit. Never parsed. */
    data class Pair(
        val qualifier: PathComponent,
        val target: PathComponent,
    ) : PathComponent
}

/** The raw source text of a component, for diagnostics and explanation steps. */
fun PathComponent.sourceText(): String =
    when (this) {
        is PathComponent.Ident -> text
        is PathComponent.IntLit -> text
        is PathComponent.Quoted -> "\"$text\""
        is PathComponent.SetLit -> atoms.joinToString(", ", "{", "}") { it.sourceText() }
        is PathComponent.RangeLit -> "${lo.sourceText()}..${hi.sourceText()}"
        PathComponent.Star -> "*"
        is PathComponent.Pair -> "${qualifier.sourceText()}.${target.sourceText()}"
    }

/**
 * Split raw dot-path text into [PathComponent]s, quotes/braces/ranges aware — the same tokenizer the
 * S7 MCP `raw:` splitter reuses (§9 "split on `.` respecting quotes/braces"). Whitespace is
 * insignificant (R2). This does **not** decide float-vs-path (R1, the frontend's job in S3): it
 * assumes the input is already known to be a path.
 *
 * Not produced here: [PathComponent.Pair] — a `qualifier.target` parses as two adjacent components,
 * and pairing is a semantic step ([PairBinder]).
 */
object PathText {
    fun parse(raw: String): List<PathComponent> {
        val comps = mutableListOf<PathComponent>()
        var i = 0
        val s = raw
        val n = s.length
        while (i < n) {
            while (i < n && s[i].isWhitespace()) i++
            if (i >= n) break
            if (s[i] == '.') { // component separator ('..' only occurs inside a range, consumed whole below)
                i++
                continue
            }
            val (comp, next) = readComponent(s, i)
            comps.add(comp)
            i = next
        }
        return comps
    }

    private fun readComponent(
        s: String,
        start: Int,
    ): Pair<PathComponent, Int> {
        if (s[start] == '*') return PathComponent.Star to start + 1
        if (s[start] == '{') return readSet(s, start)
        val (atom, afterAtom) = readAtom(s, start)
        // Range? a single '.' is a separator; '..' (DOTDOT) joins two atoms into a range.
        var j = afterAtom
        while (j < s.length && s[j].isWhitespace()) j++
        if (j + 1 < s.length && s[j] == '.' && s[j + 1] == '.') {
            val (hi, afterHi) = readAtom(s, j + 2)
            return PathComponent.RangeLit(atom, hi) to afterHi
        }
        return atom to afterAtom
    }

    private fun readSet(
        s: String,
        start: Int,
    ): Pair<PathComponent, Int> {
        var i = start + 1 // skip '{'
        val atoms = mutableListOf<PathComponent>()
        while (i < s.length && s[i] != '}') {
            while (i < s.length && (s[i].isWhitespace() || s[i] == ',')) i++
            if (i >= s.length || s[i] == '}') break
            val (atom, next) = readAtom(s, i)
            atoms.add(atom)
            i = next
        }
        require(i < s.length) { "unterminated set in path text: $s" }
        return PathComponent.SetLit(atoms) to i + 1 // skip '}'
    }

    /** Read one atom (identifier / int / quoted string), stopping at a separator or delimiter. */
    private fun readAtom(
        s: String,
        start: Int,
    ): Pair<PathComponent, Int> {
        var i = start
        while (i < s.length && s[i].isWhitespace()) i++
        // T-C4: an atom expected at/after the end of the string (e.g. a trailing `..` range with no hi
        // bound: `"2024.."`) — fail as an IllegalArgumentException the callers already handle, not a
        // raw StringIndexOutOfBoundsException that escapes them.
        require(i < s.length) { "unexpected end of path text (expected a member atom): $s" }
        if (s[i] == '"') {
            val end = s.indexOf('"', i + 1)
            require(end >= 0) { "unterminated quoted member in path text: $s" }
            return PathComponent.Quoted(s.substring(i + 1, end)) to end + 1
        }
        val begin = i
        while (i < s.length && s[i] !in ".{},\"*" && !s[i].isWhitespace()) i++
        val text = s.substring(begin, i)
        require(text.isNotEmpty()) { "empty component in path text at $begin: $s" }
        val comp = if (text.all { it.isDigit() }) PathComponent.IntLit(text) else PathComponent.Ident(text)
        return comp to i
    }
}
