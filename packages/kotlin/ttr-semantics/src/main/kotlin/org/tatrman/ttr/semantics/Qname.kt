package org.tatrman.ttr.semantics

/**
 * A qualified name as a dotted string (contract §4.1).
 *
 * The empty value (`Qname("")`) is the default/root package. Interior empty
 * segments (e.g. a trailing dot `"a.b."` or `"a..b"`) are rejected.
 */
@JvmInline
value class Qname(
    val value: String,
) {
    init {
        require(value.isEmpty() || value.split('.').none(String::isEmpty)) { "invalid qname: '$value'" }
    }

    val segments: List<String>
        get() = if (value.isEmpty()) emptyList() else value.split('.')

    val last: String
        get() = segments.last()

    /** The qname with its last segment dropped, or null at the root (single segment / empty). */
    val parent: Qname?
        get() = if (segments.size > 1) Qname(segments.dropLast(1).joinToString(".")) else null

    /** This qname with [segment] appended (or just [segment] when this is the empty qname). */
    fun append(segment: String): Qname = if (value.isEmpty()) Qname(segment) else Qname("$value.$segment")

    override fun toString(): String = value
}
