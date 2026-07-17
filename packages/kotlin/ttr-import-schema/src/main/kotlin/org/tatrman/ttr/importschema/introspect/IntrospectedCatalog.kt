// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.introspect

/**
 * The internal, dialect-neutral picture of what JDBC introspection found — the single
 * intermediate between the impure JDBC edge ([org.tatrman.ttr.importschema.introspect.MetaDataReader])
 * and the pure, deterministic db-mirror writer ([org.tatrman.ttr.importschema.dbmodel.DbMirror]).
 *
 * GI-2 (same DB + same conventions ⇒ same bytes) is a property of the *pure* half: given an
 * [IntrospectedCatalog], the mirror is a pure function. JDBC guarantees no ordering, so the
 * reader must fill these lists; the writer canonicalises (sorts) again defensively so a
 * permuted catalog still yields byte-identical output (S3·T6).
 */
data class IntrospectedCatalog(
    val schemas: List<IntrospectedSchema>,
)

data class IntrospectedSchema(
    /** The DB schema/namespace — `dbo` (MSSQL), `public` (Postgres). Becomes the `model db schema <name>`. */
    val name: String,
    val tables: List<IntrospectedTable>,
)

data class IntrospectedTable(
    val name: String,
    /** Columns in physical (ordinal) order — meaningful and deterministic; the writer keeps it. */
    val columns: List<IntrospectedColumn>,
    /** PK member column names, in key order. Empty when the table has no primary key. */
    val primaryKey: List<String> = emptyList(),
    val foreignKeys: List<IntrospectedForeignKey> = emptyList(),
    val indices: List<IntrospectedIndex> = emptyList(),
    /** Table remark/comment (`REMARKS` from `getTables`), if the driver exposes one. */
    val comment: String? = null,
)

data class IntrospectedColumn(
    val name: String,
    /** Raw DB type name as the driver reports it (`varchar`, `int`, `uniqueidentifier`, `datetime2`). */
    val sqlType: String,
    /** `java.sql.Types` code — the portable fallback when the type name is dialect-specific. */
    val jdbcType: Int,
    /** COLUMN_SIZE (length for character types, precision for numerics); null when not reported. */
    val size: Int? = null,
    /** DECIMAL_DIGITS (scale); null when not reported / not applicable. */
    val decimalDigits: Int? = null,
    val nullable: Boolean,
    val comment: String? = null,
    /** 1-based ORDINAL_POSITION — the physical column order the writer preserves. */
    val ordinal: Int,
)

data class IntrospectedForeignKey(
    val name: String,
    /** Local (referencing) columns in key-sequence order. */
    val columns: List<String>,
    val targetSchema: String,
    val targetTable: String,
    /** Referenced columns in key-sequence order, positionally aligned with [columns]. */
    val targetColumns: List<String>,
)

data class IntrospectedIndex(
    val name: String,
    val columns: List<String>,
    val unique: Boolean,
)
