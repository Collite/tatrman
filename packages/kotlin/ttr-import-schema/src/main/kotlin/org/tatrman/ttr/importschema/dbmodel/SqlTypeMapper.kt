// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.dbmodel

import org.tatrman.ttr.importschema.introspect.IntrospectedColumn
import org.tatrman.ttr.parser.model.DataType
import java.sql.Types

/**
 * Raw SQL type → canonical TTR-M type token. Deliberately mirrors
 * `ttr-metadata`'s `ModelToDefinitions.normalizeColumnType` so an imported `db` model uses the
 * exact same type vocabulary as the rest of the ecosystem (round-trips identically, feeds the
 * er derivation cleanly).
 *
 * The name-based table is primary (it matches the metadata layer verbatim); the `java.sql.Types`
 * code is a portable fallback for driver-specific type names the table doesn't list. The base
 * token is `DataType(name)` bare — length/precision are **not** carried in 1.0 (parity with the
 * metadata export; the physical length is a noted later-fidelity item, tasks-sv-p4-s3 findings).
 */
object SqlTypeMapper {
    fun toDataType(column: IntrospectedColumn): DataType {
        val base =
            column.sqlType
                .substringBefore("(")
                .trim()
                .lowercase()
        val byName =
            when (base) {
                "varchar", "nvarchar", "char", "nchar", "string", "text", "ntext", "clob" -> "text"
                "integer", "int", "smallint", "tinyint" -> "int"
                "bigint", "long" -> "bigint"
                "bool", "boolean", "bit" -> "bool"
                "datetime", "datetime2", "timestamp", "smalldatetime" -> "datetime"
                "date" -> "date"
                "time" -> "time"
                "numeric", "decimal", "money", "smallmoney" -> "decimal"
                "double", "real", "float" -> "float"
                "uuid", "guid", "uniqueidentifier" -> "text"
                else -> null
            }
        return DataType(byName ?: fromJdbcType(column.jdbcType))
    }

    private fun fromJdbcType(jdbc: Int): String =
        when (jdbc) {
            Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
            Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB,
            -> "text"
            Types.TINYINT, Types.SMALLINT, Types.INTEGER -> "int"
            Types.BIGINT -> "bigint"
            Types.BIT, Types.BOOLEAN -> "bool"
            Types.DECIMAL, Types.NUMERIC -> "decimal"
            Types.REAL, Types.FLOAT, Types.DOUBLE -> "float"
            Types.DATE -> "date"
            Types.TIME, Types.TIME_WITH_TIMEZONE -> "time"
            Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "datetime"
            else -> "text"
        }
}
