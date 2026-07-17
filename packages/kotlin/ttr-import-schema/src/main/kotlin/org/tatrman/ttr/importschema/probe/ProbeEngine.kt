// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.probe

import org.tatrman.ttr.importschema.conventions.ProbeConfig
import org.tatrman.ttr.importschema.introspect.Dialect
import java.sql.Connection

/**
 * The data-probe engine (SV-P4·S3·T5) — the F1-γ/F3-γ ground layer the S4 er cascade consumes.
 * Impure (runs SQL); the deterministic order/tiering/budget it obeys lives in the pure
 * [ProbePlanner]. For each candidate it gathers inclusion-dependency evidence: orphan count
 * (unmatched non-null child values), null rate, distinct/uniqueness (1:1 vs 1:N).
 *
 * Determinism (Q-2): an unchanged DB re-probed yields identical numbers — exact `COUNT(*)` picks
 * the tier, full scans are exact, keyed sampling is a pure function of the PK. A sampled orphan
 * count is an estimate, but `hasOrphans` (⇒ `contradicted`) is always an exact counterexample.
 *
 * 1.0 scope: single-column candidates get the full evidence set; composite candidates get orphan
 * + null only (distinct/uniqueness over composite keys is a documented later item).
 */
class ProbeEngine(
    private val dialect: Dialect,
    private val config: ProbeConfig,
    /** `schema.table` → primary-key columns, for the keyed-sampling predicate. */
    private val childPrimaryKeys: Map<String, List<String>>,
) {
    fun run(
        connection: Connection,
        candidates: List<ProbeCandidate>,
    ): List<ProbeResult> {
        val childTables = candidates.map { childKey(it) }.distinct()
        val counts = childTables.associateWith { countRows(connection, it) }
        return ProbePlanner.plan(candidates, counts, config).map { execute(connection, it) }
    }

    private fun childKey(c: ProbeCandidate) = "${c.child.schema}.${c.child.table}"

    private fun execute(
        connection: Connection,
        planned: PlannedProbe,
    ): ProbeResult {
        val c = planned.candidate
        when (planned.tier) {
            ProbeTier.UNPROBED_BUDGET -> return ProbeResult.unprobed(c, planned.childRowCount)
            ProbeTier.SKIP -> return ProbeResult(c, Provenance.UNPROBED_OVERRIDE, childRowCount = planned.childRowCount)
            else -> Unit
        }

        val pk = childPrimaryKeys[childKey(c)].orEmpty()
        // Keyed sampling needs a PK; without one, fall back to an exact full scan (honest, not a skip).
        val sampled = planned.tier == ProbeTier.SAMPLED && pk.isNotEmpty()
        val provenance = if (sampled) Provenance.SAMPLED else Provenance.FULL

        val child = DialectSql.qualified(dialect, c.child.schema, c.child.table, "c")
        val parent = DialectSql.qualified(dialect, c.parent.schema, c.parent.table, "p")
        val sampleAnd =
            if (sampled) " AND " + DialectSql.samplePredicate(dialect, "c", pk, config.sample) else ""

        val childCols = c.child.columns.map { DialectSql.quoteIdent(dialect, it) }
        val allNonNull = childCols.joinToString(" AND ") { "c.$it IS NOT NULL" }
        val anyNull = childCols.joinToString(" OR ") { "c.$it IS NULL" }
        val joinCond =
            c.child.columns.indices.joinToString(" AND ") { i ->
                "p.${DialectSql.quoteIdent(dialect, c.parent.columns[i])} = c.${childCols[i]}"
            }

        val probedRowCount = scalarLong(connection, "SELECT COUNT(*) FROM $child WHERE 1=1$sampleAnd")
        val nullCount = scalarLong(connection, "SELECT COUNT(*) FROM $child WHERE ($anyNull)$sampleAnd")
        val orphanCount =
            scalarLong(
                connection,
                "SELECT COUNT(*) FROM $child WHERE $allNonNull AND NOT EXISTS " +
                    "(SELECT 1 FROM $parent WHERE $joinCond)$sampleAnd",
            )

        val singleCol = c.child.columns.size == 1
        val distinct =
            if (singleCol) {
                scalarLong(
                    connection,
                    "SELECT COUNT(DISTINCT c.${childCols[0]}) FROM $child WHERE $allNonNull$sampleAnd",
                )
            } else {
                0L
            }
        val nonNull = probedRowCount - nullCount
        val unique = singleCol && distinct == nonNull

        return ProbeResult(
            candidate = c,
            provenance = provenance,
            childRowCount = planned.childRowCount,
            probedRowCount = probedRowCount,
            orphanCount = orphanCount,
            hasOrphans = orphanCount > 0,
            distinctChildValues = distinct,
            childValuesUnique = unique,
            nullCount = nullCount,
        )
    }

    private fun countRows(
        connection: Connection,
        schemaTable: String,
    ): Long {
        val schema = schemaTable.substringBefore('.')
        val table = schemaTable.substringAfter('.')
        return scalarLong(connection, "SELECT COUNT(*) FROM ${DialectSql.qualified(dialect, schema, table)}")
    }

    private fun scalarLong(
        connection: Connection,
        sql: String,
    ): Long =
        connection.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                if (rs.next()) rs.getLong(1) else 0L
            }
        }
}
