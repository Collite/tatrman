// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.search.all

import org.tatrman.ttr.metadata.search.SearchQuery

import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.registry.RegistrySnapshot
import org.tatrman.ttr.metadata.search.RebuildOutcome
import org.tatrman.ttr.metadata.search.SearchAlgorithm
import org.tatrman.ttr.metadata.search.SearchAlgorithmRegistry
import org.tatrman.ttr.metadata.search.SearchHit
import org.tatrman.ttr.metadata.search.SearchIndex
import org.tatrman.ttr.metadata.search.SearchIndexHolder

/**
 * Meta-algorithm. Runs every non-`all` registered algorithm against the
 * indexes for the request's language and merges results per owner qname,
 * keeping the highest-scoring hit. The kept hit's `algorithm`,
 * `matchedField`, `matchedValue`, etc. reflect the winning algorithm.
 *
 * No own index — relies on the holder for the others' indexes. When an
 * algorithm has no index for the request's language, its contribution is
 * simply empty (the merge is robust to that).
 */
class AllAlgorithm(
    private val registry: SearchAlgorithmRegistry,
    private val holder: SearchIndexHolder,
) : SearchAlgorithm {
    override val name: String = "all"

    override fun rebuild(
        snapshot: RegistrySnapshot,
        language: String,
    ): RebuildOutcome = RebuildOutcome(SearchIndex.Empty)

    override fun search(
        request: SearchQuery,
        index: SearchIndex,
    ): List<SearchHit> {
        val language = request.language.ifEmpty { "cs" }
        val merged = HashMap<QualifiedName, SearchHit>()
        for (algo in registry.all()) {
            if (algo.name == name) continue
            val perAlgoIndex = holder.get(algo.name, language) ?: continue
            for (hit in algo.search(request, perAlgoIndex)) {
                val current = merged[hit.ownerQname]
                if (current == null || hit.score > current.score) {
                    merged[hit.ownerQname] = hit
                }
            }
        }
        return merged.values.sortedByDescending { it.score }
    }
}
