package org.tatrman.ttrp.conform.eval

import org.tatrman.ttrp.graph.explain.NormalizedGraphJson
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * The deterministic half of the assist/agent eval (C4-e): scores a candidate graph against
 * an expected graph by **shape**, not bytes. Reuses `NormalizedGraphJson` (the P6 KEY-GATE
 * serializer) as the label-free node signature — SSA names never fail a match (`ssaNames:
 * ignore` is intrinsic, since the node render carries kind + params + expression trees, not
 * the label). Engines never enter (the harness scores structure only).
 */
object EvalComparator {
    /** Per-entry tolerance knobs (C4-e corpus schema). */
    data class Tolerance(
        /** true = allow interposed Calc/Project nodes (they drop out of the signature). */
        val extraCalcNodes: Boolean = false,
    )

    sealed interface Verdict {
        /** Candidate and expected share the same normalized shape. */
        data object Pass : Verdict

        /** Both compiled, but the shapes diverge — [diff] lists the extra/missing node renders. */
        data class ShapeMismatch(
            val diff: String,
        ) : Verdict

        /** The candidate did not compile clean through the front-half — carries the named diagnostics. */
        data class Invalid(
            val diagnostics: List<String>,
        ) : Verdict
    }

    fun compare(
        candidate: TtrpGraph,
        expected: TtrpGraph,
        tolerance: Tolerance = Tolerance(),
    ): Verdict {
        val c = signature(candidate, tolerance)
        val e = signature(expected, tolerance)
        if (c == e) return Verdict.Pass
        val extra = multisetMinus(c, e)
        val missing = multisetMinus(e, c)
        return Verdict.ShapeMismatch("extra=$extra missing=$missing")
    }

    /**
     * The label-free node-render multiset (sorted): each non-container node's
     * `Kind(params, exprs)` line from `NormalizedGraphJson`, with the `label = ` prefix
     * stripped so SSA renaming can't fail the match.
     */
    private fun signature(
        graph: TtrpGraph,
        tolerance: Tolerance,
    ): List<String> {
        val text = NormalizedGraphJson.write(graph)
        val renders =
            text
                .lineSequence()
                .dropWhile { it != "nodes:" }
                .drop(1)
                .takeWhile { it.startsWith("  ") }
                .map { it.trim().substringAfter(" = ") }
                .toList()
        val filtered =
            if (tolerance.extraCalcNodes) {
                renders.filterNot { it.startsWith("Calc(") || it.startsWith("Project(") }
            } else {
                renders
            }
        return filtered.sorted()
    }

    /** Multiset difference a − b (elements of [a] not covered by a matching element of [b]). */
    private fun multisetMinus(
        a: List<String>,
        b: List<String>,
    ): List<String> {
        val remaining = b.toMutableList()
        val out = mutableListOf<String>()
        for (x in a) {
            if (!remaining.remove(x)) out += x
        }
        return out.sorted()
    }
}
