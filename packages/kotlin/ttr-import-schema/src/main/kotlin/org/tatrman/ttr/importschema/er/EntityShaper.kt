// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.er

import org.tatrman.ttr.importschema.conventions.ConventionsFile
import org.tatrman.ttr.importschema.introspect.IntrospectedCatalog
import org.tatrman.ttr.importschema.introspect.IntrospectedTable

/**
 * F2 entity shaping (S4·T3), PURE:
 *  - **Pure M:N junctions collapse silently** — a 2-column table whose columns are exactly its
 *    composite PK and are both FK candidates to two different tables (no payload) becomes a direct
 *    M:N relation, not an entity. A payload-carrying junction stays an entity + a checklist flag.
 *  - **Header/detail folds are PROPOSED only** (checklist), never applied — a wrong silent fold
 *    misleads (F2's ratified line). Detected by name affinity: a child whose table name contains
 *    its parent's, with its own PK.
 *  - **Codebook** (`Ciselnik*`) tables are proposed as enum-like entities.
 */
class EntityShaper(
    private val conventions: ConventionsFile,
) {
    data class JunctionCollapse(
        val sourceTable: String,
        val entityA: String,
        val entityB: String,
    )

    data class Shaped(
        /** `schema.table` names that collapsed into M:N relations (not emitted as entities). */
        val collapsedTables: Set<String>,
        val junctions: List<JunctionCollapse>,
        /** (childEntity, parentEntity) header/detail folds — proposed in the checklist only. */
        val headerDetail: List<Pair<String, String>>,
        /** `schema.table` names of codebook tables (still entities, flagged enum-like). */
        val codebookTables: Set<String>,
        /** Payload-carrying junctions kept as entities (flagged). */
        val payloadJunctions: Set<String>,
    )

    fun shape(
        catalog: IntrospectedCatalog,
        candidates: List<RelationCandidate>,
    ): Shaped {
        val junctions = mutableListOf<JunctionCollapse>()
        val collapsed = mutableSetOf<String>()
        val payloadJunctions = mutableSetOf<String>()
        val codebooks = mutableSetOf<String>()
        val headerDetail = mutableListOf<Pair<String, String>>()

        for (schema in catalog.schemas) {
            for (t in schema.tables) {
                val key = "${schema.name}.${t.name}"
                if (isCodebook(t.name)) codebooks += key

                val junctionParents = junctionParentsOf(t, schema.name, candidates)
                if (junctionParents != null) {
                    val payload =
                        t.columns.map { it.name.lowercase() }.toSet() != t.primaryKey.map { it.lowercase() }.toSet()
                    if (payload) {
                        payloadJunctions += key
                    } else {
                        collapsed += key
                        junctions += JunctionCollapse(key, junctionParents.first, junctionParents.second)
                    }
                }
            }
        }

        // Header/detail proposals by name affinity over the candidate relations. Matched on a
        // Czech-aware STEM (Q-4): the detail table `PolozkaFaktury` (genitive) shares the stem
        // `faktur` with its parent `Faktura` (nominative) though neither contains the other whole.
        for (c in candidates) {
            val childTable = c.child.table
            val parentTable = c.parent.table
            if (childTable == parentTable) continue
            val childHasOwnPk = tableOf(catalog, c.child.schema, childTable)?.primaryKey?.size == 1
            val parentStem = stem(parentTable)
            if (childHasOwnPk && parentStem.length >= 4 && childTable.lowercase().contains(parentStem)) {
                headerDetail += childTable to parentTable
            }
        }

        return Shaped(
            collapsedTables = collapsed,
            junctions = junctions.sortedBy { it.sourceTable },
            headerDetail = headerDetail.distinct().sortedBy { it.first + it.second },
            codebookTables = codebooks,
            payloadJunctions = payloadJunctions,
        )
    }

    /** If [t] is a 2-column junction whose columns are its composite PK and both are FK candidates
     *  to two different parents, returns the two parent entity (table) names, else null. */
    private fun junctionParentsOf(
        t: IntrospectedTable,
        schema: String,
        candidates: List<RelationCandidate>,
    ): Pair<String, String>? {
        if (t.columns.size != 2) return null
        if (t.primaryKey.map { it.lowercase() }.toSet() != t.columns.map { it.name.lowercase() }.toSet()) return null

        val parentsByColumn =
            candidates
                .filter { it.child.schema == schema && it.child.table == t.name && it.child.columns.size == 1 }
                .associate {
                    it.child.columns
                        .single()
                        .lowercase() to it.parent.table
                }

        val col1 = t.columns[0].name.lowercase()
        val col2 = t.columns[1].name.lowercase()
        val p1 = parentsByColumn[col1] ?: return null
        val p2 = parentsByColumn[col2] ?: return null
        if (p1 == p2) return null
        // Deterministic parent ordering.
        return if (p1 <= p2) p1 to p2 else p2 to p1
    }

    private fun isCodebook(tableName: String): Boolean =
        conventions.naming.codebookPrefixes.any { tableName.startsWith(it, ignoreCase = true) }

    /** A crude Czech-aware stem: drop a trailing (possibly accented) vowel so declension variants
     *  of the same noun share a stem (`Faktura`/`Faktury` → `faktur`). */
    private fun stem(name: String): String {
        val lower = name.lowercase()
        return if (lower.length > 4 && lower.last() in CZECH_VOWELS) lower.dropLast(1) else lower
    }

    private companion object {
        val CZECH_VOWELS = "aeiouyáéíóúýůě".toSet()
    }

    private fun tableOf(
        catalog: IntrospectedCatalog,
        schema: String,
        table: String,
    ): IntrospectedTable? =
        catalog.schemas
            .firstOrNull { it.name == schema }
            ?.tables
            ?.firstOrNull { it.name == table }
}
