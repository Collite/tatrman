// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema

import org.tatrman.ttr.importschema.assist.NoopRelationAssistProposer
import org.tatrman.ttr.importschema.assist.RelationAssistProposer
import org.tatrman.ttr.importschema.checklist.ReviewChecklist
import org.tatrman.ttr.importschema.conventions.ConventionsFile
import org.tatrman.ttr.importschema.dbmodel.DbMirror
import org.tatrman.ttr.importschema.er.ErDeriver
import org.tatrman.ttr.importschema.er.RelationCandidate
import org.tatrman.ttr.importschema.er.RelationCascade
import org.tatrman.ttr.importschema.introspect.Dialect
import org.tatrman.ttr.importschema.introspect.IntrospectedCatalog
import org.tatrman.ttr.importschema.introspect.MetaDataReader
import org.tatrman.ttr.importschema.introspect.ScopeFilter
import org.tatrman.ttr.importschema.probe.ProbeCandidate
import org.tatrman.ttr.importschema.probe.ProbeEngine
import org.tatrman.ttr.importschema.probe.ProbeOrigin
import org.tatrman.ttr.importschema.probe.Provenance
import java.sql.Connection

/**
 * The engine seam the CLI (and, later, the platform harvest connector + the Designer import panel)
 * call: a live [Connection] → the full import (`db` mirror + `er` first cut + review checklist).
 * Kept off clikt so it is directly component-testable against a Testcontainers connection.
 *
 * Pipeline: introspect → db mirror → relation cascade → probe (Q-2/Q-5) → er derivation (graded)
 * → review checklist. The deterministic halves (mirror, cascade, derivation, checklist) are pure;
 * only introspection and probing touch the DB.
 */
class ImportSchemaRunner(
    private val dialect: Dialect,
    private val packageName: String,
    private val conventions: ConventionsFile = ConventionsFile(),
    /** F1-δ assist (S4·T6). Default OFF ⇒ byte-identical to no flag; the v1 proposer proposes nothing. */
    private val assist: Boolean = false,
    private val proposer: RelationAssistProposer = NoopRelationAssistProposer(),
) {
    private val scope = ScopeFilter(conventions.scope.include, conventions.scope.exclude)

    fun run(connection: Connection): ImportResult {
        val catalog = MetaDataReader(dialect, scope).read(connection)

        val dbResult = DbMirror(packageName).render(catalog)

        val cascade = RelationCascade(conventions).derive(catalog)
        // Assist candidates pass the same probe gate as any heuristic (GI-2 quarantine).
        val assistCandidates =
            if (assist) {
                proposer
                    .propose(catalog, conventions)
                    .map { RelationCandidate(it.child, it.parent, ProbeOrigin.HEURISTIC, "assist") }
            } else {
                emptyList()
            }
        val allCandidates =
            (cascade.candidates + assistCandidates)
                .distinctBy { "${it.child.qkey}->${it.parent.qkey}" }

        val childPks = catalog.schemas.flatMap { s -> s.tables.map { "${s.name}.${it.name}" to it.primaryKey } }.toMap()
        val probeCandidates = allCandidates.map { ProbeCandidate(it.child, it.parent, it.origin) }
        val probeResults = ProbeEngine(dialect, conventions.probes, childPks).run(connection, probeCandidates)

        val erDeriver = ErDeriver(packageName, conventions)
        val erResult = erDeriver.derive(catalog, probeResults, assistCandidates)
        val erFile = erDeriver.render(erResult, catalog)

        val coverage = coverage(catalog, cascade, probeResults)
        val reviewMd = ReviewChecklist.toMarkdown(erResult, dbResult.renames, coverage)
        val reviewJson = ReviewChecklist.toJson(erResult, dbResult.renames, coverage)

        return ImportResult(
            dbFiles = dbResult.files,
            erFile = erFile,
            reviewMarkdown = reviewMd,
            reviewJson = reviewJson,
            renames = dbResult.renames,
        )
    }

    private fun coverage(
        catalog: IntrospectedCatalog,
        cascade: RelationCascade.Cascade,
        probeResults: List<org.tatrman.ttr.importschema.probe.ProbeResult>,
    ): ReviewChecklist.Coverage {
        val tableCount = catalog.schemas.sumOf { it.tables.size }
        val probed = probeResults.count { it.provenance == Provenance.FULL || it.provenance == Provenance.SAMPLED }
        val unprobed =
            probeResults
                .filter { it.provenance == Provenance.UNPROBED_BUDGET }
                .map {
                    "${it.candidate.child.table}.${it.candidate.child.columns.joinToString(
                        ",",
                    )}→${it.candidate.parent.table}"
                }
        return ReviewChecklist.Coverage(
            tablesInScope = tableCount,
            tablesIntrospected = tableCount,
            candidatesTotal = cascade.candidates.size,
            candidatesProbed = probed,
            unprobedForBudget = unprobed,
        )
    }
}
