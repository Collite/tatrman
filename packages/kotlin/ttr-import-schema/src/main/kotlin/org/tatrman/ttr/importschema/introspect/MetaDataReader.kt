// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.introspect

import java.sql.Connection
import java.sql.DatabaseMetaData

/**
 * JDBC introspection (SV-P4·S3·T2): `java.sql.DatabaseMetaData` → an [IntrospectedCatalog]. This
 * is the impure edge; the [org.tatrman.ttr.importschema.dbmodel.DbMirror] downstream is pure.
 *
 * JDBC guarantees NO row ordering, so determinism is ours: every collection is sorted explicitly
 * here (schemas/tables/FKs/indices by name; columns by ordinal; PK/FK members by key sequence).
 * The mirror sorts again defensively, so even a driver that reorders results yields identical
 * bytes (GI-2 / S3·T6). Scope (Q-5) is applied while enumerating tables; introspection always
 * completes over the admitted scope.
 */
class MetaDataReader(
    private val dialect: Dialect,
    private val scope: ScopeFilter = ScopeFilter(),
) {
    fun read(connection: Connection): IntrospectedCatalog {
        val meta = connection.metaData
        val catalog: String? = connection.catalog
        val tableRefs = enumerateTables(meta, catalog)

        val bySchema = tableRefs.groupBy { it.schema }
        val schemas =
            bySchema.entries
                .map { (schemaName, refs) ->
                    IntrospectedSchema(
                        name = schemaName,
                        tables = refs.map { readTable(meta, catalog, it) }.sortedBy { it.name },
                    )
                }.sortedBy { it.name }
        return IntrospectedCatalog(schemas)
    }

    private data class TableRef(
        val schema: String,
        val name: String,
        val comment: String?,
    )

    private fun enumerateTables(
        meta: DatabaseMetaData,
        catalog: String?,
    ): List<TableRef> {
        val out = mutableListOf<TableRef>()
        meta.getTables(catalog, null, "%", arrayOf("TABLE")).use { rs ->
            while (rs.next()) {
                val schema = rs.getString("TABLE_SCHEM") ?: dialect.defaultSchema
                val name = rs.getString("TABLE_NAME") ?: continue
                if (schema in dialect.systemSchemas) continue
                if (!scope.admits(schema, name)) continue
                out += TableRef(schema, name, rs.getString("REMARKS")?.takeIf { it.isNotBlank() })
            }
        }
        return out
    }

    private fun readTable(
        meta: DatabaseMetaData,
        catalog: String?,
        ref: TableRef,
    ): IntrospectedTable =
        IntrospectedTable(
            name = ref.name,
            columns = readColumns(meta, catalog, ref).sortedBy { it.ordinal },
            primaryKey = readPrimaryKey(meta, catalog, ref),
            foreignKeys = readForeignKeys(meta, catalog, ref).sortedBy { it.name },
            indices = readIndices(meta, catalog, ref).sortedBy { it.name },
            comment = ref.comment,
        )

    private fun readColumns(
        meta: DatabaseMetaData,
        catalog: String?,
        ref: TableRef,
    ): List<IntrospectedColumn> {
        val out = mutableListOf<IntrospectedColumn>()
        meta.getColumns(catalog, ref.schema, ref.name, "%").use { rs ->
            while (rs.next()) {
                out +=
                    IntrospectedColumn(
                        name = rs.getString("COLUMN_NAME"),
                        sqlType = rs.getString("TYPE_NAME") ?: "",
                        jdbcType = rs.getInt("DATA_TYPE"),
                        size = rs.getInt("COLUMN_SIZE").takeUnless { rs.wasNull() },
                        decimalDigits = rs.getInt("DECIMAL_DIGITS").takeUnless { rs.wasNull() },
                        nullable = rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        comment = rs.getString("REMARKS")?.takeIf { it.isNotBlank() },
                        ordinal = rs.getInt("ORDINAL_POSITION"),
                    )
            }
        }
        return out
    }

    private fun readPrimaryKey(
        meta: DatabaseMetaData,
        catalog: String?,
        ref: TableRef,
    ): List<String> {
        val members = mutableListOf<Pair<Int, String>>() // KEY_SEQ, COLUMN_NAME
        meta.getPrimaryKeys(catalog, ref.schema, ref.name).use { rs ->
            while (rs.next()) {
                members += rs.getInt("KEY_SEQ") to rs.getString("COLUMN_NAME")
            }
        }
        return members.sortedBy { it.first }.map { it.second }
    }

    private fun readForeignKeys(
        meta: DatabaseMetaData,
        catalog: String?,
        ref: TableRef,
    ): List<IntrospectedForeignKey> {
        // Group imported-key rows by FK name; each row is one (KEY_SEQ, local, target) member.
        data class FkRow(
            val seq: Int,
            val local: String,
            val targetSchema: String,
            val targetTable: String,
            val targetCol: String,
        )
        val byName = LinkedHashMap<String, MutableList<FkRow>>()
        meta.getImportedKeys(catalog, ref.schema, ref.name).use { rs ->
            while (rs.next()) {
                val fkName =
                    rs.getString("FK_NAME")?.takeIf { it.isNotBlank() }
                        ?: "fk_${ref.name}_${rs.getString("FKCOLUMN_NAME")}"
                byName.getOrPut(fkName) { mutableListOf() } +=
                    FkRow(
                        seq = rs.getInt("KEY_SEQ"),
                        local = rs.getString("FKCOLUMN_NAME"),
                        targetSchema = rs.getString("PKTABLE_SCHEM") ?: dialect.defaultSchema,
                        targetTable = rs.getString("PKTABLE_NAME"),
                        targetCol = rs.getString("PKCOLUMN_NAME"),
                    )
            }
        }
        return byName.map { (name, rows) ->
            val ordered = rows.sortedBy { it.seq }
            IntrospectedForeignKey(
                name = name,
                columns = ordered.map { it.local },
                targetSchema = ordered.first().targetSchema,
                targetTable = ordered.first().targetTable,
                targetColumns = ordered.map { it.targetCol },
            )
        }
    }

    private fun readIndices(
        meta: DatabaseMetaData,
        catalog: String?,
        ref: TableRef,
    ): List<IntrospectedIndex> {
        data class IdxRow(
            val pos: Int,
            val column: String,
        )
        val byName = LinkedHashMap<String, Pair<Boolean, MutableList<IdxRow>>>() // name -> (unique, members)
        runCatching {
            meta.getIndexInfo(catalog, ref.schema, ref.name, false, false)
        }.getOrNull()?.use { rs ->
            while (rs.next()) {
                if (rs.getShort("TYPE").toInt() == DatabaseMetaData.tableIndexStatistic.toInt()) continue
                val name = rs.getString("INDEX_NAME") ?: continue
                val column = rs.getString("COLUMN_NAME") ?: continue
                val unique = !rs.getBoolean("NON_UNIQUE")
                byName.getOrPut(name) { unique to mutableListOf() }.second +=
                    IdxRow(rs.getInt("ORDINAL_POSITION"), column)
            }
        }
        return byName.map { (name, pair) ->
            IntrospectedIndex(name, pair.second.sortedBy { it.pos }.map { it.column }, pair.first)
        }
    }
}
