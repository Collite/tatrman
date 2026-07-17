// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.introspect

/** The SQL dialects the 1.0 front door introspects. Drives default schema + system-schema hiding. */
enum class Dialect(
    val cliToken: String,
    val defaultSchema: String,
    /** Schemas never introspected (catalogs, system metadata) even absent a scope filter. */
    val systemSchemas: Set<String>,
    /** Packaged conventions profile used when no `--profile` / package file is given (Q-1). */
    val defaultProfile: String,
) {
    MSSQL("mssql", "dbo", setOf("sys", "INFORMATION_SCHEMA", "guest", "db_owner", "db_accessadmin"), "mssql-default"),
    POSTGRESQL("postgresql", "public", setOf("pg_catalog", "information_schema", "pg_toast"), "mssql-default"),
    ;

    companion object {
        fun fromToken(token: String): Dialect =
            entries.firstOrNull { it.cliToken == token.lowercase() }
                ?: throw IllegalArgumentException(
                    "unknown dialect '$token' (expected: ${entries.joinToString("|") { it.cliToken }})",
                )
    }
}
