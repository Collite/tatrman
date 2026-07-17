// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.dbmodel

import org.tatrman.ttr.importschema.introspect.IntrospectedCatalog
import org.tatrman.ttr.importschema.introspect.IntrospectedForeignKey
import org.tatrman.ttr.importschema.introspect.IntrospectedTable
import org.tatrman.ttr.importschema.naming.IdentifierMangler
import org.tatrman.ttr.parser.model.ColumnDef
import org.tatrman.ttr.parser.model.FkDef
import org.tatrman.ttr.parser.model.PropertyValue
import org.tatrman.ttr.parser.model.Reference
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttr.parser.model.TableDef
import org.tatrman.ttr.writer.TtrRenderer

/**
 * The `db` mirror writer (SV-P4·S3·T3): an [IntrospectedCatalog] → canonical TTR-M `db`
 * documents, emitted through the canonical [TtrRenderer] (never string templates — RS-9 spirit).
 *
 * PURE and DETERMINISTIC (GI-2): the output is a total function of (catalog, packageName). The
 * catalog is canonicalised here — schemas/tables/FKs sorted by their emitted (TTR) name,
 * columns kept in physical ordinal order — so a permuted catalog (S3·T6) yields byte-identical
 * bytes regardless of the order JDBC happened to return objects in.
 *
 * File split: one document per DB schema, `db.<schema>.ttrm`, carrying `model db schema <schema>`
 * (documented rule — multi-schema-safe; the hero's single `dbo` schema → `db.dbo.ttrm`).
 */
class DbMirror(
    private val packageName: String,
) {
    fun render(catalog: IntrospectedCatalog): DbMirrorResult {
        val renames = mutableListOf<IdentifierRename>()
        val files = mutableListOf<GeneratedFile>()

        // Schema names mangled + collision-checked across the whole catalog.
        val schemaNames =
            mangleScope(catalog.schemas.map { it.name }, IdentifierRename.Kind.SCHEMA, packageName, renames)
        val schemasByTtr =
            catalog.schemas
                .map { schemaNames.getValue(it.name) to it }
                .sortedBy { it.first }

        for ((schemaTtr, schema) in schemasByTtr) {
            val defs = mutableListOf<org.tatrman.ttr.parser.model.Definition>()
            val fkDefs = mutableListOf<FkDef>()

            // Table names mangled + collision-checked within the schema.
            val tableNames = mangleScope(schema.tables.map { it.name }, IdentifierRename.Kind.TABLE, schemaTtr, renames)

            for (table in schema.tables.sortedBy { tableNames.getValue(it.name) }) {
                val tableTtr = tableNames.getValue(table.name)
                defs += tableDef(table, tableTtr, schemaTtr, renames)
                fkDefs += table.foreignKeys.map { fk -> fkDef(fk, schemaTtr, tableTtr, table, renames) }
            }
            // FKs are top-level defs in the db file; TtrRenderer orders them after tables by name.
            defs += fkDefs

            val content =
                TtrRenderer.renderFile(
                    schemaCode = "db",
                    namespace = schemaTtr,
                    definitions = defs,
                    packageName = packageName,
                )
            files += GeneratedFile(path = "db.$schemaTtr.ttrm", content = content)
        }

        return DbMirrorResult(files = files.sortedBy { it.path }, renames = renames)
    }

    private fun tableDef(
        table: IntrospectedTable,
        tableTtr: String,
        schemaTtr: String,
        renames: MutableList<IdentifierRename>,
    ): TableDef {
        val columnNames =
            mangleScope(
                table.columns.map { it.name },
                IdentifierRename.Kind.COLUMN,
                "$schemaTtr.$tableTtr",
                renames,
            )
        val pkTtr =
            table.primaryKey.map {
                columnNames[it]
                    ?: mangleName(it, IdentifierRename.Kind.COLUMN, "$schemaTtr.$tableTtr", renames)
            }
        val columns =
            table.columns
                .sortedBy { it.ordinal }
                .map { col ->
                    ColumnDef(
                        name = columnNames.getValue(col.name),
                        source = SourceLocation.UNKNOWN,
                        description = col.comment?.takeIf { it.isNotBlank() },
                        type = SqlTypeMapper.toDataType(col),
                        optional = col.nullable,
                        isKey = col.name in table.primaryKey,
                    )
                }
        return TableDef(
            name = tableTtr,
            source = SourceLocation.UNKNOWN,
            description = table.comment?.takeIf { it.isNotBlank() },
            primaryKey = pkTtr,
            columns = columns,
        )
    }

    private fun fkDef(
        fk: IntrospectedForeignKey,
        schemaTtr: String,
        tableTtr: String,
        table: IntrospectedTable,
        renames: MutableList<IdentifierRename>,
    ): FkDef {
        val nameTtr = mangleName(fk.name, IdentifierRename.Kind.FK, "$schemaTtr.$tableTtr", renames)
        val fromRefs =
            fk.columns.map { col ->
                idRef("db.$schemaTtr.$tableTtr.${mangleBare(col)}")
            }
        val targetSchemaTtr = mangleBare(fk.targetSchema)
        val targetTableTtr = mangleBare(fk.targetTable)
        val toRefs =
            fk.targetColumns.map { col ->
                idRef("db.$targetSchemaTtr.$targetTableTtr.${mangleBare(col)}")
            }
        return FkDef(
            name = nameTtr,
            source = SourceLocation.UNKNOWN,
            from = PropertyValue.ListValue(fromRefs, SourceLocation.UNKNOWN),
            to = PropertyValue.ListValue(toRefs, SourceLocation.UNKNOWN),
        )
    }

    private fun idRef(path: String): PropertyValue.IdValue =
        PropertyValue.IdValue(Reference(path), path.split("."), SourceLocation.UNKNOWN)

    /** Mangle a bare cross-reference identifier (no rename bookkeeping — the owning scope records it). */
    private fun mangleBare(source: String): String = IdentifierMangler.mangle(source).ttrName

    private fun mangleName(
        source: String,
        kind: IdentifierRename.Kind,
        qualifier: String,
        renames: MutableList<IdentifierRename>,
    ): String {
        val m = IdentifierMangler.mangle(source)
        if (m.wasMangled) renames += IdentifierRename(kind, qualifier, m.ttrName, m.original)
        return m.ttrName
    }

    /**
     * Mangle every name in a namespace, record renames, and enforce §12 rule 3: two distinct
     * sources mangling to the same TTR name is `TTRP-IMP-001` (never auto-suffixed).
     */
    private fun mangleScope(
        sources: List<String>,
        kind: IdentifierRename.Kind,
        qualifier: String,
        renames: MutableList<IdentifierRename>,
    ): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val seen = HashMap<String, String>() // ttrName -> first source that produced it
        for (source in sources) {
            val m = IdentifierMangler.mangle(source)
            val prior = seen[m.ttrName]
            if (prior != null && prior != source) {
                throw ImportSchemaException(
                    "TTRP-IMP-001",
                    "identifier collision in $qualifier: '$source' and '$prior' both mangle to '${m.ttrName}' — " +
                        "resolve via a conventions mapping entry (names are never auto-suffixed).",
                )
            }
            seen[m.ttrName] = source
            result[source] = m.ttrName
            if (m.wasMangled) renames += IdentifierRename(kind, qualifier, m.ttrName, m.original)
        }
        return result
    }
}
