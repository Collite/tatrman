package org.tatrman.ttrp.emit

import org.tatrman.ttrp.ast.SourceLocation

/**
 * Diagnostics raised by the emit stage. `EMT` is a new contracts §8 area (changelog
 * entry queued — see progress-phase-03.md). Translator failures are mapped here so a
 * raw Calcite/translator error never escapes `ttrp-emit`; the underlying message rides
 * as [detail].
 */
enum class EmitDiagnosticId(
    val code: String,
    val summary: String,
) {
    /** Translator `parse_failed` — a SQL parse error inside the translator boundary. */
    PARSE_FAILED("TTRP-EMT-001", "SQL parse failed in the translator"),

    /** Translator `validation_failed` — validation/validator error inside the boundary. */
    VALIDATION_FAILED("TTRP-EMT-002", "SQL validation failed in the translator"),

    /** Translator validator-exception class of failure. */
    VALIDATOR_FAILED("TTRP-EMT-003", "SQL validator rejected the plan"),

    /** Translator `rel_conversion_failed` — RelNode conversion/unparse failure. */
    REL_CONVERSION_FAILED("TTRP-EMT-004", "relational conversion failed in the translator"),

    /** Internal invariant: a sugar node (Select/Calc/Distinct/HAVING) reached emit. */
    SUGAR_REACHED_EMIT("TTRP-EMT-005", "sugar node reached emit (T8 should have expanded it)"),

    /** Internal invariant: a node kind unsupported for this engine reached emit. */
    UNSUPPORTED_NODE("TTRP-EMT-006", "node kind cannot be emitted for this engine"),

    /** DialectRegistry: unknown engine type / version. */
    UNKNOWN_ENGINE("TTRP-WLD-002", "unknown engine type or version"),
}

/** Thrown when emit cannot proceed; carries the structured id + provenance for the host. */
class TtrpEmitException(
    val id: EmitDiagnosticId,
    val detail: String,
    val island: String? = null,
    val location: SourceLocation? = null,
    val suggestedAlternative: String? = null,
) : RuntimeException("${id.code}: ${id.summary}${island?.let { " [island=$it]" } ?: ""} — $detail")
