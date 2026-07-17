// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.dbmodel

/** One generated TTR-M document: its package-relative filename and its canonical text. */
data class GeneratedFile(
    val path: String,
    val content: String,
)

/**
 * A source identifier the mirror had to mangle (kept a valid TTR-M IDENT). Recorded here so the
 * F5 review checklist (S4·T4) can carry the `mangled ← original` mapping — keeping the rename
 * invertible without polluting the clean model text (Q-3).
 */
data class IdentifierRename(
    val kind: Kind,
    /** Human scope, e.g. `dbo` (table), `dbo.Faktura` (column/fk). */
    val qualifier: String,
    val ttrName: String,
    val sourceName: String,
) {
    enum class Kind { SCHEMA, TABLE, COLUMN, FK }
}

data class DbMirrorResult(
    val files: List<GeneratedFile>,
    val renames: List<IdentifierRename>,
)

/**
 * A hard, deterministic import failure. `TTRP-IMP-001` = two distinct source identifiers
 * collided after mangling within one namespace (§12 rule 3 — never auto-suffixed; the operator
 * resolves it via a conventions mapping entry).
 */
class ImportSchemaException(
    val code: String,
    message: String,
) : RuntimeException("[$code] $message")
