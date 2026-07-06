package org.tatrman.ttr.semantics.semanticsblock

/**
 * Grounding Phase 1 (grammar 4.2) — the closed `semantics { … }` vocabulary.
 *
 * NORMATIVE table, mirroring `packages/semantics/src/semantics-block/vocabulary.ts`
 * and `docs/features/semantics-block/README.md` §Vocabulary exactly. It is the
 * cross-repo sync key with ai-platform's closed proto enums: the vocabulary here
 * and the proto enums version TOGETHER via [SEMANTICS_VOCABULARY_VERSION].
 */
object Vocabulary {
    /** Cross-repo sync key — bumps in lock-step with ai-platform's proto enums. */
    const val SEMANTICS_VOCABULARY_VERSION: Int = 1

    /** Entity/table kinds (`kind:`). */
    val ENTITY_KINDS: List<String> = listOf("period_table", "calendar", "poi", "fx_rate")

    /** One extra key (besides `role`) a role accepts, with its reference kind. */
    data class ExtraKey(
        val key: String,
        val kind: RefKind,
        val required: Boolean,
    )

    enum class RefKind { EntityRef, AttrRef, StringLit }

    /** The type-family a role's attribute/column must declare. */
    enum class TypeConstraint { Date, Text, Numeric }

    data class RoleSpec(
        val extraKeys: List<ExtraKey> = emptyList(),
        val typeConstraint: TypeConstraint? = null,
    )

    /** Attribute/column roles (`role:`) → their extra keys + type constraint. */
    val ATTRIBUTE_ROLES: Map<String, RoleSpec> =
        linkedMapOf(
            "period_start" to RoleSpec(typeConstraint = TypeConstraint.Date),
            "period_end" to RoleSpec(typeConstraint = TypeConstraint.Date),
            "period_code" to
                RoleSpec(
                    extraKeys = listOf(ExtraKey("code_format", RefKind.StringLit, required = false)),
                    typeConstraint = TypeConstraint.Text,
                ),
            "event_date" to
                RoleSpec(
                    extraKeys = listOf(ExtraKey("period", RefKind.EntityRef, required = false)),
                    typeConstraint = TypeConstraint.Date,
                ),
            "document_date" to
                RoleSpec(
                    extraKeys = listOf(ExtraKey("period", RefKind.EntityRef, required = false)),
                    typeConstraint = TypeConstraint.Date,
                ),
            "posting_date" to
                RoleSpec(
                    extraKeys = listOf(ExtraKey("period", RefKind.EntityRef, required = false)),
                    typeConstraint = TypeConstraint.Date,
                ),
            "due_date" to
                RoleSpec(
                    extraKeys = listOf(ExtraKey("period", RefKind.EntityRef, required = false)),
                    typeConstraint = TypeConstraint.Date,
                ),
            "valid_from" to RoleSpec(typeConstraint = TypeConstraint.Date),
            "valid_to" to RoleSpec(typeConstraint = TypeConstraint.Date),
            "calendar_date" to RoleSpec(typeConstraint = TypeConstraint.Date),
            "geo_lat" to RoleSpec(typeConstraint = TypeConstraint.Numeric),
            "geo_lon" to RoleSpec(typeConstraint = TypeConstraint.Numeric),
            "geo_point" to RoleSpec(typeConstraint = TypeConstraint.Text),
            "amount" to
                RoleSpec(
                    extraKeys = listOf(ExtraKey("currency", RefKind.AttrRef, required = false)),
                    typeConstraint = TypeConstraint.Numeric,
                ),
            "amount_domestic" to RoleSpec(typeConstraint = TypeConstraint.Numeric),
            "currency_code" to RoleSpec(typeConstraint = TypeConstraint.Text),
            "fx_from_currency" to RoleSpec(typeConstraint = TypeConstraint.Text),
            "fx_to_currency" to RoleSpec(typeConstraint = TypeConstraint.Text),
            "fx_rate" to RoleSpec(typeConstraint = TypeConstraint.Numeric),
        )

    /** A single required-role clause in a kind's completeness rule (exactly `count` times). */
    data class CompletenessClause(
        val role: String,
        val count: Int = 1,
    )

    /**
     * Per-kind completeness rules (validated on the owning entity/table). `poi` is
     * special (geo_point XOR lat/lon pair) — handled in the validator, so it maps
     * to an empty list here.
     */
    val KIND_COMPLETENESS: Map<String, List<CompletenessClause>> =
        mapOf(
            "period_table" to
                listOf(
                    CompletenessClause("period_start"),
                    CompletenessClause("period_end"),
                    CompletenessClause("period_code"),
                ),
            "calendar" to listOf(CompletenessClause("calendar_date")),
            "poi" to emptyList(),
            "fx_rate" to
                listOf(
                    CompletenessClause("fx_from_currency"),
                    CompletenessClause("fx_to_currency"),
                    CompletenessClause("fx_rate"),
                ),
        )

    val ALL_ROLES: List<String> = ATTRIBUTE_ROLES.keys.toList()

    /** The keys legal on an entity/table `semantics` block. */
    val ALL_ENTITY_KEYS: List<String> = listOf("kind")
}
