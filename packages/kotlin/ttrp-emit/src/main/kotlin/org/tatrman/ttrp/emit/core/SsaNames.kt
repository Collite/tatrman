package org.tatrman.ttrp.emit.core

import org.tatrman.ttrp.graph.model.Node

/**
 * Deterministic SSA-label → identifier mangling, shared by SQL (CTE names) and Polars
 * (variable names) emit — one naming story end-to-end (Q7-γ → E-b → ζ). Given the set of
 * nodes in an island, [assign] returns a stable, injective map from node id to a legal
 * SQL/Python identifier.
 *
 * Rules:
 *  - A node's SSA label is its surviving variable name, possibly with a `#k` reassignment
 *    suffix (`sales#2`) or an anonymous `~n` form. `sales#2` → `sales_2`; `sales` → `sales`.
 *  - Anonymous labels (`~3`, or empty) become `_<nodekind>_<topoIndex>` (`_filter_3`).
 *  - Any residual illegal character is replaced with `_`.
 *  - Collisions (two labels mangling to the same identifier) are broken by appending
 *    `_<topoIndex>` to the later one, escalating until unique. Injectivity is a tested invariant.
 */
object SsaNames {
    /**
     * @param ordered the island's nodes in a stable topological order (drives anonymous
     *   numbering and collision-break suffixes).
     */
    fun assign(ordered: List<Node>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val used = HashSet<String>()
        ordered.forEachIndexed { index, node ->
            var candidate = mangle(node.label, node, index)
            if (candidate in used) {
                candidate = escalate(candidate, index, used)
            }
            used += candidate
            out[node.id] = candidate
        }
        return out
    }

    /** Mangle a single label in isolation (no collision handling) — exposed for unit tests. */
    fun mangle(
        label: String,
        node: Node,
        index: Int,
    ): String {
        val anonymous = label.isBlank() || label.startsWith("~")
        val base =
            if (anonymous) {
                "_${node.kindTag()}_$index"
            } else {
                // `name#k` → `name_k`; leave a bare name alone.
                label.replace("#", "_")
            }
        return sanitize(base)
    }

    private fun escalate(
        candidate: String,
        index: Int,
        used: Set<String>,
    ): String {
        var next = "${candidate}_$index"
        var bump = index
        while (next in used) {
            bump++
            next = "${candidate}_$bump"
        }
        return next
    }

    private fun sanitize(raw: String): String {
        val cleaned =
            buildString {
                raw.forEachIndexed { i, c ->
                    val ok = c == '_' || c.isLetterOrDigit()
                    append(if (ok) c else '_')
                    if (i == 0 && c.isDigit()) insert(0, '_')
                }
            }
        return cleaned.ifEmpty { "_n" }
    }

    private fun Node.kindTag(): String = this::class.simpleName?.lowercase() ?: "node"
}
