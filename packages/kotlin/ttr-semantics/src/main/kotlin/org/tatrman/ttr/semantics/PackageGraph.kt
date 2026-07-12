// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics

import org.tatrman.ttr.parser.model.ImportStatement

data class PackageNode(
    val name: String,
    val documentUris: List<String>,
)

data class PackageEdge(
    val from: String,
    val to: String,
    val citedBy: List<String>,
)

data class PackageGraph(
    val nodes: List<PackageNode>,
    val edges: List<PackageEdge>,
)

/** The package an import targets: the wildcard target, or the named target minus its last segment. */
internal fun packageOfImport(imp: ImportStatement): String {
    if (imp.wildcard) return imp.target
    val parts = imp.target.split('.')
    return if (parts.size >= 2) parts.dropLast(1).joinToString(".") else ""
}

/**
 * Tarjan strongly-connected-components over the package graph; returns every
 * SCC of size > 1 (i.e. cycles). Ported from `findCyclesOn` in
 * `packages/semantics/src/package-graph.ts`.
 */
fun findCyclesOn(graph: PackageGraph): List<List<String>> {
    val packages = graph.nodes.map { it.name }
    val indexByPkg = packages.withIndex().associate { (i, p) -> p to i }
    val n = packages.size

    val adjacency = Array(n) { mutableListOf<Int>() }
    for (edge in graph.edges) {
        val from = indexByPkg[edge.from]
        val to = indexByPkg[edge.to]
        if (from != null && to != null) adjacency[from].add(to)
    }

    var idx = 0
    val stack = ArrayDeque<Int>()
    val onStack = BooleanArray(n)
    val index = IntArray(n) { -1 }
    val lowlink = IntArray(n)
    val sccs = mutableListOf<List<Int>>()

    fun strongConnect(v: Int) {
        index[v] = idx
        lowlink[v] = idx
        idx++
        stack.addLast(v)
        onStack[v] = true
        for (w in adjacency[v]) {
            if (index[w] == -1) {
                strongConnect(w)
                lowlink[v] = minOf(lowlink[v], lowlink[w])
            } else if (onStack[w]) {
                lowlink[v] = minOf(lowlink[v], index[w])
            }
        }
        if (lowlink[v] == index[v]) {
            val scc = mutableListOf<Int>()
            while (true) {
                val w = stack.removeLast()
                onStack[w] = false
                scc.add(w)
                if (w == v) break
            }
            sccs.add(scc)
        }
    }

    for (v in 0 until n) {
        if (index[v] == -1) strongConnect(v)
    }

    return sccs.filter { it.size > 1 }.map { scc -> scc.map { packages[it] } }
}

/**
 * Builds the package dependency graph from a [SymbolTable] plus each document's
 * import statements. Mirrors TS `PackageGraphBuilder`.
 */
class PackageGraphBuilder(
    private val symbols: SymbolTable,
    private val documentImports: Map<String, List<ImportStatement>>,
) {
    fun build(): PackageGraph {
        val packageToUris = LinkedHashMap<String, LinkedHashSet<String>>()
        val allUris = LinkedHashSet<String>()
        for (entry in symbols.all()) {
            allUris += entry.documentUri
            packageToUris.getOrPut(entry.packageName) { linkedSetOf() } += entry.documentUri
        }

        val nodes = packageToUris.map { (name, uris) -> PackageNode(name, uris.toList()) }

        val edgeMap = LinkedHashMap<String, LinkedHashMap<String, LinkedHashSet<String>>>()
        for (uri in allUris) {
            val imports = documentImports[uri] ?: continue
            val srcPackage = packageOf(uri) ?: continue
            for (imp in imports) {
                val tgtPackage = packageOfImport(imp)
                if (tgtPackage.isEmpty() || tgtPackage == srcPackage) continue
                edgeMap
                    .getOrPut(srcPackage) { LinkedHashMap() }
                    .getOrPut(tgtPackage) { linkedSetOf() }
                    .add(uri)
            }
        }

        val edges =
            edgeMap.flatMap { (from, targets) ->
                targets.map { (to, citedBy) -> PackageEdge(from, to, citedBy.toList()) }
            }

        return PackageGraph(nodes, edges)
    }

    fun findCycles(): List<List<String>> = findCyclesOn(build())

    fun getDependencies(pkg: String): List<String> {
        val graph = build()
        val seen = LinkedHashSet<String>()
        val queue = ArrayDeque(listOf(pkg))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (edge in graph.edges) {
                if (edge.from == current && seen.add(edge.to)) queue.addLast(edge.to)
            }
        }
        return seen.toList()
    }

    fun getDependents(pkg: String): List<String> {
        val graph = build()
        val seen = LinkedHashSet<String>()
        val queue = ArrayDeque(listOf(pkg))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (edge in graph.edges) {
                if (edge.to == current && seen.add(edge.from)) queue.addLast(edge.from)
            }
        }
        return seen.toList()
    }

    private fun packageOf(uri: String): String? = symbols.all().firstOrNull { it.documentUri == uri }?.packageName
}
