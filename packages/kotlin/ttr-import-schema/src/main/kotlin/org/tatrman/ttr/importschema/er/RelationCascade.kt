// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.er

import org.tatrman.ttr.importschema.conventions.ConventionsFile
import org.tatrman.ttr.importschema.introspect.IntrospectedCatalog
import org.tatrman.ttr.importschema.probe.ColumnRef
import org.tatrman.ttr.importschema.probe.ProbeOrigin

/**
 * F1 relation derivation, candidate half (S4·T2): from an [IntrospectedCatalog] + conventions,
 * produce the ordered list of candidate relations for the probe engine to grade. PURE.
 *
 * Cascade: (1) every **declared** FK; then (2) **heuristic** candidates — a non-PK column whose
 * name equals another table's single-column primary key is a foreign-key candidate to that table
 * (the "pk-name-match" workhorse — it connects `Faktura.IDStav` to `Ciselnik_StavFaktury` where a
 * `ID<Target>` pattern can't). A column that *looks* like an FK (matches a conventions
 * `foreign-key-patterns` shape) but resolves to no table is reported [Cascade.unmatched] for the
 * checklist — never guessed into a relation.
 */
class RelationCascade(
    private val conventions: ConventionsFile,
) {
    data class Cascade(
        val candidates: List<RelationCandidate>,
        val unmatched: List<ColumnRef>,
    )

    private val fkPatterns = conventions.naming.foreignKeyPatterns.map { it.toPatternRegex() }

    fun derive(catalog: IntrospectedCatalog): Cascade {
        // Index: single-column PK name → the unique owning (schema, table), when unambiguous.
        val pkOwners = HashMap<String, MutableList<Pair<String, String>>>()
        for (schema in catalog.schemas) {
            for (t in schema.tables) {
                if (t.primaryKey.size == 1) {
                    pkOwners.getOrPut(t.primaryKey[0].lowercase()) { mutableListOf() } += (schema.name to t.name)
                }
            }
        }

        val candidates = mutableListOf<RelationCandidate>()
        val unmatched = mutableListOf<ColumnRef>()

        for (schema in catalog.schemas) {
            for (t in schema.tables) {
                val declaredChildCols =
                    t.foreignKeys
                        .flatMap { it.columns }
                        .map { it.lowercase() }
                        .toSet()
                val pkCols = t.primaryKey.map { it.lowercase() }.toSet()

                // (1) declared FKs
                for (fk in t.foreignKeys) {
                    candidates +=
                        RelationCandidate(
                            child = ColumnRef(schema.name, t.name, fk.columns),
                            parent = ColumnRef(fk.targetSchema, fk.targetTable, fk.targetColumns),
                            origin = ProbeOrigin.DECLARED,
                            rule = "declared:${fk.name}",
                        )
                }

                // (2) heuristic candidates over non-PK, non-declared-FK columns
                for (col in t.columns) {
                    val lower = col.name.lowercase()
                    if (lower in pkCols || lower in declaredChildCols) continue

                    val owners = pkOwners[lower]?.filter { it != (schema.name to t.name) }.orEmpty()
                    when {
                        owners.size == 1 -> {
                            val (uSchema, uTable) = owners[0]
                            val parentPk = pkColumnOf(catalog, uSchema, uTable)
                            candidates +=
                                RelationCandidate(
                                    child = ColumnRef(schema.name, t.name, listOf(col.name)),
                                    parent = ColumnRef(uSchema, uTable, listOf(parentPk)),
                                    origin = ProbeOrigin.HEURISTIC,
                                    rule = "pk-name-match",
                                )
                        }
                        looksLikeForeignKey(col.name) -> {
                            val patternMatch = resolveByPattern(catalog, col.name, schema.name to t.name)
                            if (patternMatch != null) {
                                candidates +=
                                    RelationCandidate(
                                        child = ColumnRef(schema.name, t.name, listOf(col.name)),
                                        parent = patternMatch,
                                        origin = ProbeOrigin.HEURISTIC,
                                        rule = "fk-pattern",
                                    )
                            } else {
                                unmatched += ColumnRef(schema.name, t.name, listOf(col.name))
                            }
                        }
                    }
                }
            }
        }
        return Cascade(candidates.sortedBy { it.orderKey() }, unmatched.sortedBy { it.qkey })
    }

    private fun RelationCandidate.orderKey() = "${origin.ordinal}|${child.qkey}->${parent.qkey}"

    private fun pkColumnOf(
        catalog: IntrospectedCatalog,
        schema: String,
        table: String,
    ): String =
        catalog.schemas
            .firstOrNull { it.name == schema }
            ?.tables
            ?.firstOrNull { it.name == table }
            ?.primaryKey
            ?.singleOrNull() ?: "id"

    private fun looksLikeForeignKey(colName: String): Boolean = fkPatterns.any { it.regex.matches(colName) }

    /** Resolve `ID<Target>` → a table whose name equals (or ends with) the captured target. */
    private fun resolveByPattern(
        catalog: IntrospectedCatalog,
        colName: String,
        self: Pair<String, String>,
    ): ColumnRef? {
        for (p in fkPatterns) {
            val target =
                p.regex
                    .matchEntire(colName)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.lowercase() ?: continue
            val matches =
                catalog.schemas.flatMap { s ->
                    s.tables
                        .filter {
                            it.name.lowercase() == target &&
                                (s.name to it.name) != self &&
                                it.primaryKey.size == 1
                        }.map { ColumnRef(s.name, it.name, listOf(it.primaryKey.single())) }
                }
            if (matches.size == 1) return matches.single()
        }
        return null
    }

    private data class PatternRegex(
        val regex: Regex,
    )

    private companion object {
        fun String.toPatternRegex(): PatternRegex {
            val sb = StringBuilder("^")
            var i = 0
            while (i < length) {
                val rest = substring(i)
                when {
                    rest.startsWith("<Target>", ignoreCase = true) -> {
                        sb.append("(.+)")
                        i += "<Target>".length
                    }
                    else -> {
                        sb.append(Regex.escape(this[i].toString()))
                        i += 1
                    }
                }
            }
            sb.append('$')
            return PatternRegex(Regex(sb.toString(), RegexOption.IGNORE_CASE))
        }
    }
}
