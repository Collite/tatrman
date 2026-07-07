package org.tatrman.ttrp.emit.sql

import org.tatrman.proteus.v1.SqlDialect
import org.tatrman.ttrp.emit.EmitDiagnosticId
import org.tatrman.ttrp.emit.TtrpEmitException

/**
 * Maps a TTR-P engine (`type` + major `version` from the world's engine manifest) to the
 * translator's proto [SqlDialect] enum. The published translator owns the version-aware
 * Calcite dialect construction internally (`org.tatrman.translator.dialects.Dialects.byCode`)
 * — so at the ttrp-emit boundary "version-aware" means: we validate the (type, version) pair
 * against the engines we know how to emit for, and hand the translator the proto enum it keys
 * its own dialect `Context` off. No Calcite dialect singleton is ever referenced here (that
 * would violate the translator-boundary rule); this returns the proto enum only.
 */
object DialectRegistry {
    /**
     * @throws TtrpEmitException [EmitDiagnosticId.UNKNOWN_ENGINE] (`TTRP-WLD-002`) when the
     *   engine type is not one this repo can emit SQL for, or the version is outside the
     *   supported range.
     */
    fun forEngine(
        engineType: String,
        version: String?,
    ): SqlDialect =
        when (engineType.lowercase()) {
            "postgres", "postgresql" -> {
                requireSupportedPostgresVersion(version)
                SqlDialect.POSTGRESQL
            }
            "duckdb" -> SqlDialect.DUCKDB
            "mssql", "sqlserver" -> SqlDialect.MSSQL
            "mysql", "mariadb" -> SqlDialect.MYSQL_MARIADB
            else ->
                throw TtrpEmitException(
                    EmitDiagnosticId.UNKNOWN_ENGINE,
                    detail = "no SQL dialect registered for engine type '$engineType'",
                    suggestedAlternative = "supported SQL engines: postgres, duckdb, mssql, mysql",
                )
        }

    private fun requireSupportedPostgresVersion(version: String?) {
        // The translator emits standard-Postgres SQL for any modern major; we only reject a
        // version we recognise as too old to have the constructs emit relies on (NULLS LAST,
        // GROUP BY, CTEs — all present since PG 8.4). A null/blank version is permitted
        // (world may leave it implicit); a non-numeric-major version is a config error.
        if (version.isNullOrBlank()) return
        val major =
            version.substringBefore('.').toIntOrNull()
                ?: throw TtrpEmitException(
                    EmitDiagnosticId.UNKNOWN_ENGINE,
                    detail = "postgres version '$version' has no numeric major component",
                )
        if (major < MIN_POSTGRES_MAJOR) {
            throw TtrpEmitException(
                EmitDiagnosticId.UNKNOWN_ENGINE,
                detail = "postgres major $major is below the minimum supported ($MIN_POSTGRES_MAJOR)",
            )
        }
    }

    private const val MIN_POSTGRES_MAJOR = 9
}
