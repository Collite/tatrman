package org.tatrman.ttrp.diagnostics

import org.tatrman.ttrp.ast.SourceLocation

/**
 * The single TTR-P diagnostic catalogue (contracts §8): stable ids
 * `TTRP-<AREA>-<NNN>`, each rejected form carrying a suggested alternative. This
 * enum later feeds `ttrp/authoringContext`'s diagnostics table — keep messages
 * self-contained. Stages 1.2/1.3 append their own ids (EQ/FN/AGG/TYP/EXP, WLD/RES/
 * SCH/CFG/MOV, …); the area list is extensible by design.
 */
enum class TtrpDiagnosticId(
    val id: String,
    val suggestedAlternative: String?,
) {
    EQ_001("TTRP-EQ-001", "use `=` — it is the one equality operator; `==` is only a TTR-pandas synonym (S9)"),
    CTL_001(
        "TTRP-CTL-001",
        "`finishes with` (FF) is reserved and not available in v1; use `after` (FS) or `with` (SS) (F-b)",
    ),
    PRS_001("TTRP-PRS-001", null), // generic syntax error (ANTLR-reported)
    PRS_002("TTRP-PRS-002", "TTR-P has no `program` header — identity is the filename; delete this line (S12)"),
    PRS_003("TTRP-PRS-003", "multi-input ops take named inputs only: join(left: …, right: …) (C3-c)"),
    PRS_004("TTRP-PRS-004", "union takes the list form: union(a, b, c) (S11)"),
    PRS_005(
        "TTRP-PRS-005",
        "this name is a reserved port name (in, out, err, rejects, true, false, else) — choose another (S10)",
    ),
    FRG_001("TTRP-FRG-001", "supported fragment dialects: sql, pandas, ttrb (C3-g/C4-f)"),
    ;

    companion object {
        /** Guards against id-string collisions across the catalogue (Stage 1.3 DONE bar). */
        fun assertNoDuplicateIds() {
            val dupes = entries.groupBy { it.id }.filterValues { it.size > 1 }.keys
            require(dupes.isEmpty()) { "duplicate TtrpDiagnosticId id-strings: $dupes" }
        }
    }
}

enum class Severity { ERROR, WARNING, INFO }

data class TtrpDiagnostic(
    val id: TtrpDiagnosticId,
    val severity: Severity,
    val message: String,
    val location: SourceLocation,
    val suggestedAlternative: String? = id.suggestedAlternative,
) {
    /** One-line render: `FILE:LINE:COL <ID> <message>` (CLI/T1.3.7 format). */
    fun render(): String = "${location.file}:${location.line}:${location.column} ${id.id} $message"
}
