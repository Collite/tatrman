// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.er

import org.tatrman.ttr.importschema.conventions.ConventionsFile
import org.tatrman.ttr.importschema.dbmodel.DbMirror
import org.tatrman.ttr.importschema.dbmodel.GeneratedFile
import org.tatrman.ttr.importschema.introspect.IntrospectedCatalog

/**
 * F4-γ layered-ownership re-run (S4·T5). The two halves have two lifecycles:
 *  - **`db` = machine-owned.** A re-run re-introspects and diffs the freshly-rendered mirror
 *    against the committed one; any change is a **PR-shaped proposal** (the new bytes replace the
 *    old — GI-2 makes an unchanged DB a no-op). Never silently overwritten: git review is the
 *    merge point.
 *  - **`er` = born once, human-owned.** A re-run **never regenerates** it; er-relevant drift (a
 *    new table, a dropped/added FK) lands only as checklist **flags**. [RerunResult.erRegenerated]
 *    is always false — the analyst's authored model is protected.
 *
 * PURE — operates on two catalogs (old = what the committed model was built from, new = live).
 */
class ReRun(
    private val packageName: String,
    @Suppress("unused") private val conventions: ConventionsFile = ConventionsFile(),
) {
    data class RerunResult(
        val dbChanged: Boolean,
        /** The freshly rendered db documents — the proposal (equal to the old ones when unchanged). */
        val dbProposal: List<GeneratedFile>,
        val flags: List<ChecklistNote>,
        /** Always false: the er first cut is human-owned and never regenerated on re-run. */
        val erRegenerated: Boolean = false,
    )

    fun rerun(
        oldCatalog: IntrospectedCatalog,
        newCatalog: IntrospectedCatalog,
    ): RerunResult {
        val oldDb = DbMirror(packageName).render(oldCatalog).files
        val newDb = DbMirror(packageName).render(newCatalog).files
        val dbChanged = oldDb.map { it.path to it.content } != newDb.map { it.path to it.content }

        val flags = mutableListOf<ChecklistNote>()

        val oldTables = tableKeys(oldCatalog)
        val newTables = tableKeys(newCatalog)
        for (t in (newTables - oldTables).sorted()) {
            flags +=
                ChecklistNote(
                    ChecklistNote.Kind.ER_DRIFT,
                    t,
                    "new table — not yet mapped in the er model; add it in review if it carries meaning",
                )
        }
        for (t in (oldTables - newTables).sorted()) {
            flags +=
                ChecklistNote(
                    ChecklistNote.Kind.ER_DRIFT,
                    t,
                    "table removed from the database — any er entity/relation over it is now stale",
                )
        }

        val oldFks = fkKeys(oldCatalog)
        val newFks = fkKeys(newCatalog)
        for (fk in (oldFks - newFks).sorted()) {
            flags +=
                ChecklistNote(
                    ChecklistNote.Kind.ER_DRIFT,
                    fk,
                    "declared FK dropped — a relation derived from it may no longer hold",
                )
        }
        for (fk in (newFks - oldFks).sorted()) {
            flags +=
                ChecklistNote(
                    ChecklistNote.Kind.ER_DRIFT,
                    fk,
                    "new declared FK — consider adding the relation to the er model",
                )
        }

        if (dbChanged) {
            flags +=
                ChecklistNote(
                    ChecklistNote.Kind.DB_DRIFT,
                    "db",
                    "the db mirror changed — review the proposed db.*.ttrm diff and commit",
                )
        }

        return RerunResult(
            dbChanged = dbChanged,
            dbProposal = newDb,
            flags = flags.sortedBy { it.kind.name + it.subject },
        )
    }

    private fun tableKeys(catalog: IntrospectedCatalog): Set<String> =
        catalog.schemas.flatMap { s -> s.tables.map { "${s.name}.${it.name}" } }.toSet()

    private fun fkKeys(catalog: IntrospectedCatalog): Set<String> =
        catalog.schemas
            .flatMap { s ->
                s.tables.flatMap { t -> t.foreignKeys.map { "${s.name}.${t.name}.${it.name}" } }
            }.toSet()
}
