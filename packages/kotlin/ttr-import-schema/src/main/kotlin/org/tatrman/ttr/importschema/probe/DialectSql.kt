// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.probe

import org.tatrman.ttr.importschema.conventions.SampleConfig
import org.tatrman.ttr.importschema.introspect.Dialect

/**
 * The dialect-specific SQL the probe engine needs: identifier quoting and the Q-2 keyed-sampling
 * predicate. The sample rule is `hash(md5(canonical-pk)) mod M < K` — a pure function of the data,
 * independent of physical storage (never `TABLESAMPLE`, which page-samples and breaks GI-2 on an
 * index rebuild). The Postgres predicate is exercised in the component tier; the MSSQL predicate
 * runs on the amd64-gated tier / real estates.
 */
object DialectSql {
    fun quoteIdent(
        dialect: Dialect,
        ident: String,
    ): String =
        when (dialect) {
            Dialect.POSTGRESQL -> "\"" + ident.replace("\"", "\"\"") + "\""
            Dialect.MSSQL -> "[" + ident.replace("]", "]]") + "]"
        }

    fun qualified(
        dialect: Dialect,
        schema: String,
        table: String,
        alias: String? = null,
    ): String {
        val q = "${quoteIdent(dialect, schema)}.${quoteIdent(dialect, table)}"
        return if (alias != null) "$q $alias" else q
    }

    /**
     * A boolean SQL predicate selecting the keyed sample of rows, over the primary-key columns
     * [pkColumns] of table alias [alias]. Deterministic: `md5(pk₁‖'|'‖pk₂…) mod M < K`.
     */
    fun samplePredicate(
        dialect: Dialect,
        alias: String,
        pkColumns: List<String>,
        sample: SampleConfig,
    ): String {
        val canonical = canonicalPk(dialect, alias, pkColumns)
        val m = sample.modulus.coerceAtLeast(1)
        return when (dialect) {
            // md5 → 15 hex chars (60 bits, always non-negative as bit(60)::bigint) → mod M.
            Dialect.POSTGRESQL ->
                "(( 'x' || substr(md5($canonical), 1, 15) )::bit(60)::bigint) % $m < ${sample.keep}"
            // HASHBYTES('MD5',…) → first 7 bytes (56 bits, non-negative) → mod M.
            Dialect.MSSQL ->
                "(CONVERT(BIGINT, SUBSTRING(HASHBYTES('MD5', $canonical), 1, 7))) % $m < ${sample.keep}"
        }
    }

    private fun canonicalPk(
        dialect: Dialect,
        alias: String,
        pkColumns: List<String>,
    ): String {
        val parts =
            pkColumns.map { col ->
                val ref = "$alias.${quoteIdent(dialect, col)}"
                when (dialect) {
                    Dialect.POSTGRESQL -> "coalesce($ref::text, '')"
                    Dialect.MSSQL -> "coalesce(CONVERT(NVARCHAR(4000), $ref), '')"
                }
            }
        // Postgres concatenates with `||`; MSSQL with `+`. Join the parts with a '|' separator.
        return when (dialect) {
            Dialect.POSTGRESQL -> parts.joinToString(" || '|' || ")
            Dialect.MSSQL -> parts.joinToString(" + '|' + ")
        }
    }
}
