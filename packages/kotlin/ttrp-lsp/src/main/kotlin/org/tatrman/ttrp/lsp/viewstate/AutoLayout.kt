package org.tatrman.ttrp.lsp.viewstate

/** Abstract layout coordinate: the client maps `{layer, index}` to pixels per skin orientation (C1-b). */
data class AbstractCoord(
    val layer: Int,
    val index: Int,
)

/**
 * Deterministic layered (Sugiyama-lite) auto-layout, computed Kotlin-side (C1-b layout
 * decision, C1-f "view-state code once, Kotlin-side"). NO RNG, NO iteration-count
 * heuristics — identical output across runs and across any node-insertion-order
 * permutation of the same graph (P2-mandatory determinism):
 *
 *  1. `layer` = longest-path rank from sources over **data** edges (control edges ignored
 *     for ranking); the acyclic graph makes the fixpoint the exact longest-path assignment.
 *  2. in-layer order = stable sort by (upstream **barycenter as an exact rational**, then ζ
 *     key lexicographic) — layer 0 by ζ; each later layer by the mean index of its
 *     already-placed data-predecessors.
 *  3. output abstract `{layer, index}` per node; the client maps to pixels per skin
 *     orientation (Alteryx/KNIME L→R ⇒ x=layer; Enso T↓ ⇒ y=layer), so one core serves
 *     both edge-orientation conventions (C1-b-ii, no per-skin position sets).
 */
object AutoLayout {
    fun layout(
        nodes: Collection<String>,
        edges: Collection<Pair<String, String>>,
    ): Map<String, AbstractCoord> {
        if (nodes.isEmpty()) return emptyMap()
        val nodeSet = nodes.toSet()
        // Keep only edges whose endpoints are both in scope (ignore self-loops for ranking).
        val dataEdges = edges.filter { it.first in nodeSet && it.second in nodeSet && it.first != it.second }
        val preds = LinkedHashMap<String, MutableList<String>>()
        nodes.forEach { preds[it] = mutableListOf() }
        for ((from, to) in dataEdges) preds.getValue(to).add(from)

        val layer = longestPathLayers(nodes, dataEdges)
        val maxLayer = layer.values.maxOrNull() ?: 0

        val indexOf = HashMap<String, Int>()
        val coords = LinkedHashMap<String, AbstractCoord>()
        for (lvl in 0..maxLayer) {
            val inLayer = nodes.filter { (layer[it] ?: 0) == lvl }
            // Order by upstream barycenter (exact rational), tie-break by ζ lexicographic.
            val ordered = inLayer.sortedWith(BarycenterComparator(preds, indexOf).thenBy { it })
            ordered.forEachIndexed { idx, z ->
                indexOf[z] = idx
                coords[z] = AbstractCoord(lvl, idx)
            }
        }
        return coords
    }

    /** Longest-path layering via relaxation to a fixpoint (levels only increase; bounded by |nodes|). */
    private fun longestPathLayers(
        nodes: Collection<String>,
        edges: Collection<Pair<String, String>>,
    ): Map<String, Int> {
        val level = LinkedHashMap<String, Int>()
        nodes.forEach { level[it] = 0 }
        var changed = true
        var guard = nodes.size + 1
        while (changed && guard-- > 0) {
            changed = false
            for ((u, v) in edges) {
                val cand = (level[u] ?: 0) + 1
                if (cand > (level[v] ?: 0)) {
                    level[v] = cand
                    changed = true
                }
            }
        }
        return level
    }

    /**
     * Orders a layer by the exact-rational mean of a node's already-placed predecessor
     * indices. A node with no placed predecessor sorts last (barycenter = +∞); ties are
     * broken lexicographically by ζ (applied by the caller's `thenBy`).
     */
    private class BarycenterComparator(
        private val preds: Map<String, List<String>>,
        private val indexOf: Map<String, Int>,
    ) : Comparator<String> {
        override fun compare(
            a: String,
            b: String,
        ): Int {
            val (sumA, cntA) = barycenter(a)
            val (sumB, cntB) = barycenter(b)
            // No placed predecessors ⇒ sort last.
            if (cntA == 0 && cntB == 0) return 0
            if (cntA == 0) return 1
            if (cntB == 0) return -1
            // sumA/cntA vs sumB/cntB by cross-multiplication (exact, no float).
            return (sumA.toLong() * cntB).compareTo(sumB.toLong() * cntA)
        }

        private fun barycenter(z: String): Pair<Int, Int> {
            var sum = 0
            var count = 0
            for (p in preds[z].orEmpty()) {
                val idx = indexOf[p] ?: continue
                sum += idx
                count++
            }
            return sum to count
        }
    }
}
