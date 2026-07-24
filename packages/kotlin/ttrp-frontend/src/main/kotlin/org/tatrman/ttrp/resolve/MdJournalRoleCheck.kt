// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.resolve

import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.parser.model.TableDef
import org.tatrman.ttr.semantics.md.Journaling
import org.tatrman.ttr.semantics.md.MdBindings
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * MD dot-path S5C-B.4 — the R30 journal-role validation (`TTRP-MD-018`, MDS8). A cubelet bound with
 * **invalidate** journaling reads and writes through a *valid* technical column, so its backing table must
 * declare one: either a `valid_flag` role, or the temporal `valid_from` + `valid_to` pair (contracts §12
 * R30). A binding that declares invalidate over a table with neither is `TTRP-MD-018`.
 *
 * The role family is the semantics-block vocabulary (grammar 4.2, `Vocabulary.ATTRIBUTE_ROLES` v2): each
 * technical column carries `semantics { role: valid_flag | valid_from | valid_to | … }`. This check reads
 * those declared roles off the backing [TableDef]s and confronts them with the cubelet bindings' journaling
 * modes — a model-level cross-check the per-statement resolver can't make (it never sees the raw tables).
 *
 * A model-load-time check (not per dot-path statement): [check] takes the loaded [MdBindings] plus the
 * table→roles map [tableRolesOf] extracts from the parsed definitions.
 */
object MdJournalRoleCheck {
    /** Roles that satisfy invalidate journaling: a boolean flag, or the temporal validity pair. */
    private const val VALID_FLAG = "valid_flag"
    private const val VALID_FROM = "valid_from"
    private const val VALID_TO = "valid_to"

    /**
     * `TTRP-MD-018` for every invalidate-journaled cubelet whose backing table declares neither `valid_flag`
     * nor the `valid_from`+`valid_to` pair. [tableRoles] maps a table's simple name → its declared column
     * roles (see [tableRolesOf]). Emitted at [SourceLocation.UNKNOWN] — the binding is a model fact, not a
     * source span.
     *
     * Only tables **declared in the model** (present in [tableRoles]) are checked: an invalidate binding to
     * an external / physical-only fact table (no `def table` in the `.ttrm`, e.g. a live conformance table)
     * has its technical columns in the DB, outside the model's purview — validating roles we cannot see
     * would be a false positive, so such bindings are skipped.
     */
    fun check(
        bindings: MdBindings,
        tableRoles: Map<String, Set<String>>,
    ): List<TtrpDiagnostic> =
        bindings.cubelets.values
            .filter { it.journaling is Journaling.Invalidate }
            .mapNotNull { binding -> tableRoles[binding.table.substringAfterLast('.')]?.let { binding to it } }
            .filterNot { (_, roles) -> hasValidRole(roles) }
            .map { (binding, _) ->
                TtrpDiagnostic(
                    id = TtrpDiagnosticId.MD_018,
                    severity = Severity.ERROR,
                    message =
                        "cubelet `${binding.cubelet}` is journaled `invalidate`, but its backing table " +
                            "`${binding.table}` declares no `valid_flag` role and no `valid_from`+`valid_to` " +
                            "pair (R30)",
                    location = SourceLocation.UNKNOWN,
                )
            }

    private fun hasValidRole(roles: Set<String>): Boolean =
        VALID_FLAG in roles || (VALID_FROM in roles && VALID_TO in roles)

    /**
     * The `role`-tagged columns of every [TableDef], keyed by the table's simple name — the input to [check].
     * A column's role is its `semantics { role: … }` entry; tables with no roles map to an empty set.
     */
    fun tableRolesOf(definitions: List<Definition>): Map<String, Set<String>> =
        definitions.filterIsInstance<TableDef>().associate { table ->
            table.name to
                table.columns
                    .mapNotNull {
                        it.semantics
                            ?.entries
                            ?.get("role")
                            ?.display()
                    }.toSet()
        }
}
