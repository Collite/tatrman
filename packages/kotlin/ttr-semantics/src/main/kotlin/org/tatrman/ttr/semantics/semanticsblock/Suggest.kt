// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.semanticsblock

/**
 * Nearest-match over a closed vocabulary (Levenshtein ≤ maxDistance). Used by the
 * TTR-SEM-200/201/202 diagnostics to offer "did you mean …" on an unknown key /
 * role / kind. Mirrors `semantics-block/suggest.ts`.
 */
object Suggest {
    /** Levenshtein edit distance between [a] and [b] (classic DP). */
    fun editDistance(
        a: String,
        b: String,
    ): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[n]
    }

    /**
     * The single closest candidate within [maxDistance] (default 2), or null if
     * none is close enough. Ties break to the first candidate in declaration order,
     * keeping suggestions deterministic.
     */
    fun nearestMatch(
        input: String,
        candidates: List<String>,
        maxDistance: Int = 2,
    ): String? {
        var best: String? = null
        var bestDist = maxDistance + 1
        for (c in candidates) {
            val d = editDistance(input, c)
            if (d < bestDist) {
                best = c
                bestDist = d
            }
        }
        return if (bestDist <= maxDistance) best else null
    }
}
