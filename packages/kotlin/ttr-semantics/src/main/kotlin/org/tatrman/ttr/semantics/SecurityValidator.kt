// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics

import org.tatrman.ttr.parser.loader.ParseResult
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttr.parser.model.TableDef

/**
 * PL-P4.S3.T6 (grammar 0.11, H-1/H-3) — advisory validation of the document-level
 * `security { }` block. **Warning-severity ONLY**: an unresolved object reference
 * is a lint, NEVER a compile block (H-3 — access declarations must not gate a
 * build; enforcement is Perun's, at bundle build). A standalone entry point (NOT
 * part of [Validator], and excluded from the conformance dump), mirroring
 * [WorldValidator]; the `security/…` codes are the TTR-M editor surface and a
 * cross-target contract — the library never mints TTRP-* ids.
 *
 * Only OBJECT references are checked. Role / classification tokens are verbatim
 * org-policy data (contracts §11: "verbatim role names are legal; unknown roles
 * fail closed at bundle build — advisory lint standalone"), so they are not
 * resolved against the model here.
 */
data class SecurityDiagnostic(
    /** Currently only `security/unresolved-object`. */
    val code: String,
    val message: String,
    val severity: String, // always "warning" here (H-3)
    val source: SourceLocation,
)

object SecurityValidator {
    /**
     * @param result       parsed document
     * @param knownObjects project-wide resolvable object refs (names + dotted
     *                     `<object>.<member>` paths). `null` → derive the set from
     *                     THIS document only (a convenience for single-file lint;
     *                     cross-file callers inject the project-wide set).
     */
    fun validateSecurity(
        result: ParseResult,
        knownObjects: Set<String>? = null,
    ): List<SecurityDiagnostic> {
        if (result.securityBlocks.isEmpty()) return emptyList()
        val objects = knownObjects ?: documentObjects(result)
        val diagnostics = mutableListOf<SecurityDiagnostic>()
        for (block in result.securityBlocks) {
            for (stmt in block.statements) {
                if (!resolves(stmt.objectRef, objects)) {
                    diagnostics +=
                        SecurityDiagnostic(
                            "security/unresolved-object",
                            "`${stmt.verb}` references object '${stmt.objectRef}', which does not resolve to any " +
                                "known object; the grant is advisory until the reference resolves",
                            "warning",
                            stmt.source,
                        )
                }
            }
        }
        return diagnostics
    }

    /** A ref resolves if it (or its head segment — the object owning a member) is known. */
    private fun resolves(
        ref: String,
        objects: Set<String>,
    ): Boolean {
        if (ref in objects) return true
        val head = ref.substringBefore('.')
        return head in objects
    }

    /** Names + dotted member paths declared in this document (the single-file default set). */
    private fun documentObjects(result: ParseResult): Set<String> {
        val out = mutableSetOf<String>()
        for (def in result.definitions) {
            out += def.name
            when (def) {
                is TableDef -> def.columns.forEach { out += "${def.name}.${it.name}" }
                is EntityDef -> def.attributes.forEach { out += "${def.name}.${it.name}" }
                else -> Unit
            }
        }
        return out
    }
}
