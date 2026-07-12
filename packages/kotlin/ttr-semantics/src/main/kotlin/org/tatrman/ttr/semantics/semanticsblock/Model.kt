// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.semanticsblock

/**
 * Grounding Phase 1 (grammar 4.2) — the typed, validated result of a `semantics`
 * block. Emitted by the validator ONLY when the block is diagnostics-free for that
 * element (degrade, don't fail). Mirrors `semantics-block/model.ts`.
 */
sealed interface ResolvedSemantics

/** A resolved cross-reference to another symbol (entity or sibling attribute). */
data class SymbolRef(
    /** The reference text as written (opaque id, e.g. `AccountingPeriod`). */
    val path: String,
    /** The resolved target's canonical qname, when resolution succeeded. */
    val qname: String? = null,
)

/** The resolved `semantics` block on an entity or db table. */
data class ResolvedEntitySemantics(
    val kind: String,
) : ResolvedSemantics

/** The resolved `semantics` block on an attribute or db column. */
data class ResolvedAttributeSemantics(
    val role: String,
    /** `period:` → the period-table entity; `currency:` → sibling `currency_code`. */
    val period: SymbolRef? = null,
    val currency: SymbolRef? = null,
    /** `code_format:` on `period_code` (default `"yyyyMM"`). */
    val codeFormat: String? = null,
) : ResolvedSemantics
