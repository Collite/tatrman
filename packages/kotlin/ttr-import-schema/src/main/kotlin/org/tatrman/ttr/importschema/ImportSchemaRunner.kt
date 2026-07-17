// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema

import org.tatrman.ttr.importschema.conventions.ConventionsFile
import org.tatrman.ttr.importschema.dbmodel.DbMirror
import org.tatrman.ttr.importschema.dbmodel.DbMirrorResult
import org.tatrman.ttr.importschema.introspect.Dialect
import org.tatrman.ttr.importschema.introspect.MetaDataReader
import org.tatrman.ttr.importschema.introspect.ScopeFilter
import java.sql.Connection

/**
 * The engine seam the CLI (and, later, the platform harvest connector and the Designer import
 * panel) call: a live [Connection] → introspection → the `db` mirror. Kept off clikt so it is
 * directly component-testable against a Testcontainers connection.
 *
 * S3 produces the `db` half + the rename ledger; S4 layers the `er` derivation and the review
 * checklist onto the same [DbMirrorResult] shape.
 */
class ImportSchemaRunner(
    private val dialect: Dialect,
    private val packageName: String,
    private val conventions: ConventionsFile = ConventionsFile(),
) {
    private val scope = ScopeFilter(conventions.scope.include, conventions.scope.exclude)

    fun run(connection: Connection): DbMirrorResult {
        val catalog = MetaDataReader(dialect, scope).read(connection)
        return DbMirror(packageName).render(catalog)
    }
}
