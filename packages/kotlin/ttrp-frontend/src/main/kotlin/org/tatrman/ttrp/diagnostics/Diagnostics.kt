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

    // ---- Stage 1.2 expression ids (EQ above; FN/AGG/TYP/EXP here) ----
    FN_001(
        "TTRP-FN-001",
        "unknown function — check the spelling against the builtin roster; known aliases suggest their canonical (T5-c)",
    ),
    FN_002(
        "TTRP-FN-002",
        "check the call's arity and argument types against the catalogue signature (T5-c)",
    ),
    AGG_001(
        "TTRP-AGG-001",
        "aggregate functions are only legal inside aggregate(…) / aggregate { … } (B-T5)",
    ),
    EXP_001(
        "TTRP-EXP-001",
        "only input columns are in scope inside op expressions — variables never resolve here (C3-a-iv)",
    ),
    TYP_001(
        "TTRP-TYP-001",
        "add an explicit cast(x as <type>) — there is no implicit coercion across type kinds (B-T5/Q9-4)",
    ),
    TYP_002(
        "TTRP-TYP-002",
        "no cast rule for this type pair — this coercion is undefined; there is no legal cast (B-T5)",
    ),

    // ---- Stage 1.3 resolution ids (WLD / RES / SCH / CFG / MOV) ----
    WLD_001(
        "TTRP-WLD-001",
        "select a world: set `world = \"…\"` under [ttrp] in modeler.toml, or pin one with `uses world \"…\"` (contracts §2)",
    ),
    WLD_002(
        "TTRP-WLD-002",
        "check the world qname against the model repo's `def world` declarations (S22)",
    ),
    WLD_003(
        "TTRP-WLD-003",
        "name a `def world` — this qname is a different kind of object (D-d)",
    ),
    WLD_004(
        "TTRP-WLD-004",
        "exactly one storage may declare `staging: true` in a world (D-f); remove the extra one",
    ),
    RES_001(
        "TTRP-RES-001",
        "no object of the expected kind by that name — add an `import`, use the full qname, or check the world (D-b)",
    ),
    RES_002(
        "TTRP-RES-002",
        "the name is exported by more than one import — qualify it with its full qname (C2-d/D-b, no first-wins)",
    ),
    RES_003(
        "TTRP-RES-003",
        "`target` takes an engine instance — use a `def engine` declared in the world (D-b position typing)",
    ),
    RES_004(
        "TTRP-RES-004",
        "name a `def relation` declared between the two joined entities (D-a sub-2)",
    ),
    RES_005(
        "TTRP-RES-005",
        "bind it in the model (er2db) or reference the db object directly — no er2db binding is reachable (E-d)",
    ),
    RES_006(
        "TTRP-RES-006",
        "the import path resolves to no package — check the package name against the model repo (D-b)",
    ),
    SCH_001(
        "TTRP-SCH-001",
        "only one schema is allowed at each level — remove the duplicate declaration (D-c same-level conflict)",
    ),
    SCH_002(
        "TTRP-SCH-002",
        "declare a schema: inline `schema: { … }`, a program `def schema`, or on the storage in the world (schema-on-read is banned, T7)",
    ),
    SCH_003(
        "TTRP-SCH-003",
        "use a TTR db-schema type (S23): int/integer, decimal, string/text/char/varchar, bool/boolean, float, double, number, date, timestamp, datetime, object, list",
    ),
    CFG_001(
        "TTRP-CFG-001",
        "check the allowed values for this [ttrp] key (contracts §2)",
    ),
    CFG_002(
        "TTRP-CFG-002",
        "unknown [ttrp] key — check the spelling against the contracts §2 key roster",
    ),
    MOV_001(
        "TTRP-MOV-001",
        "`store` takes a storage — use a `def storage` declared in the world (D-b position typing)",
    ),
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
