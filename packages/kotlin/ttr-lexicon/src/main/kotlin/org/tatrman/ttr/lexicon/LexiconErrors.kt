// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon

/**
 * The `RG-LEX-*` catalogue (RV-P1.1). Ids follow the `RG-<AREA>-<NNN>` house convention used by
 * the resolution & grounding services — stable, documented, one code per condition — and are
 * mirrored in `docs/features/resolution/lexicon-schemas.md` with an example per code.
 *
 * Every rejection a caller can see is constructed here, so the catalogue cannot silently gain an
 * undocumented code. All of them are errors: each rejects a file, so none has a degraded mode.
 */
object LexiconErrors {
    const val UNKNOWN_METHOD = "RG-LEX-001"
    const val MISSING_REQUIRED = "RG-LEX-002"
    const val TYPOS_WITHOUT_DISTANCE = "RG-LEX-003"
    const val OP_NOT_PREFIXED = "RG-LEX-004"
    const val NO_TRIGGERS = "RG-LEX-005"
    const val DUPLICATE_TERM = "RG-LEX-006"
    const val UNKNOWN_KEY = "RG-LEX-007"
    const val SCHEMA_ID_MISMATCH = "RG-LEX-008"
    const val MALFORMED_FRONTMATTER = "RG-LEX-009"
    const val UNSUPPORTED_LANG = "RG-LEX-010"
    const val MALFORMED_YAML = "RG-LEX-011"

    /** Every code this library can emit — the catalogue's own index. */
    val ALL: List<String> =
        listOf(
            UNKNOWN_METHOD,
            MISSING_REQUIRED,
            TYPOS_WITHOUT_DISTANCE,
            OP_NOT_PREFIXED,
            NO_TRIGGERS,
            DUPLICATE_TERM,
            UNKNOWN_KEY,
            SCHEMA_ID_MISMATCH,
            MALFORMED_FRONTMATTER,
            UNSUPPORTED_LANG,
            MALFORMED_YAML,
        )

    fun unknownMethod(
        method: String,
        at: Provenance,
    ) = LexiconViolation(
        UNKNOWN_METHOD,
        "unknown match method '$method' — the set is EXACT | TYPOS(1..3) | TOKENS.",
        at,
    )

    fun typosWithoutDistance(
        method: String,
        at: Provenance,
    ) = LexiconViolation(
        TYPOS_WITHOUT_DISTANCE,
        "'$method' carries no edit budget — write TYPOS(1), TYPOS(2) or TYPOS(3).",
        at,
    )

    fun missingRequired(
        key: String,
        where: String,
        at: Provenance,
    ) = LexiconViolation(MISSING_REQUIRED, "required key '$key' missing at $where.", at)

    fun opNotPrefixed(
        op: String,
        at: Provenance,
    ) = LexiconViolation(
        OP_NOT_PREFIXED,
        "skill `op` value '$op' is not an `op:` ref — prefix it, e.g. `op:trend`.",
        at,
    )

    fun noTriggers(
        op: String,
        at: Provenance,
    ) = LexiconViolation(
        NO_TRIGGERS,
        "skill '$op' declares no triggers — it could never fire.",
        at,
    )

    fun duplicateTerm(
        text: String,
        lang: Lang,
        firstSeenLine: Int,
        at: Provenance,
    ) = LexiconViolation(
        DUPLICATE_TERM,
        "term \"$text\" (${lang.wire}) is declared twice in this file — first at line $firstSeenLine. " +
            "Two targets for one term in one file have no defined winner.",
        at,
    )

    fun unknownKey(
        key: String,
        where: String,
        at: Provenance,
    ) = LexiconViolation(
        UNKNOWN_KEY,
        "unknown key '$key' at $where — the lexicon schemas are closed.",
        at,
    )

    fun schemaIdMismatch(
        declared: String,
        expected: String,
        at: Provenance,
    ) = LexiconViolation(
        SCHEMA_ID_MISMATCH,
        "`schema: $declared` does not match the schema this file is validated against ($expected).",
        at,
    )

    fun unsupportedLang(
        lang: String,
        at: Provenance,
    ) = LexiconViolation(
        UNSUPPORTED_LANG,
        "unsupported lang '$lang' — use `cs`, `en`, or the both-languages form `cs|en`.",
        at,
    )

    fun missingFrontmatter(at: Provenance) =
        LexiconViolation(
            MALFORMED_FRONTMATTER,
            "no `---` frontmatter block — the body alone is not a skill.",
            at,
        )

    fun unterminatedFrontmatter(at: Provenance) =
        LexiconViolation(
            MALFORMED_FRONTMATTER,
            "frontmatter opened with `---` but never closed.",
            at,
        )

    fun malformedYaml(
        detail: String,
        at: Provenance,
    ) = LexiconViolation(MALFORMED_YAML, "not parseable as YAML: $detail", at)
}
