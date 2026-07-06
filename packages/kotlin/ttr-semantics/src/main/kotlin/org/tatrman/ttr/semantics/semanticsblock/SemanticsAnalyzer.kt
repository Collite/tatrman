package org.tatrman.ttr.semantics.semanticsblock

import org.tatrman.ttr.parser.diagnostics.DiagnosticCode
import org.tatrman.ttr.parser.model.AttributeDef
import org.tatrman.ttr.parser.model.ColumnDef
import org.tatrman.ttr.parser.model.DataType
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.SemanticsBlock
import org.tatrman.ttr.parser.model.SemanticsValue
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttr.parser.model.TableDef

/** One `semantics { … }` validation diagnostic (mirrors TS `SemanticsDiagnostic`). */
data class SemanticsDiagnostic(
    val code: DiagnosticCode,
    val message: String,
    val source: SourceLocation,
    /** Closed-vocabulary nearest match for 200/201/202, when one exists. */
    val suggestion: String? = null,
)

/** The result of analysing every `semantics` block in a document. */
data class SemanticsAnalysis(
    val diagnostics: List<SemanticsDiagnostic>,
    /** Resolved results keyed by the owning element's `source` node. */
    val resolved: Map<SourceLocation, ResolvedSemantics>,
)

/**
 * Grounding Phase 1 (grammar 4.2) — validate `semantics { … }` blocks against the
 * closed vocabulary ([Vocabulary], NORMATIVE) and produce the typed
 * [ResolvedSemantics] for diagnostics-free elements. Ported from
 * `packages/semantics/src/semantics-block/validator.ts`.
 *
 * Pipeline per element: shape (keys/values) → cross-ref resolution (`period:`
 * entity ref, `currency:` sibling-attribute ref) → type-constraint check against
 * the declared `type` → per-owner aggregation (completeness, event_date
 * cardinality, geo/valid pairs). `period:` resolves document-locally (same-file
 * entity/table kind index); cross-document resolution is a later phase.
 */
object SemanticsAnalyzer {
    private fun strOf(v: SemanticsValue?): String? = (v as? SemanticsValue.Str)?.value

    /** Map a declared TTR type name to a semantics type-constraint family. */
    fun typeFamilyOf(dt: DataType?): String? {
        if (dt == null) return null
        return when (dt.name.lowercase()) {
            "date", "datetime", "timestamp" -> "date"
            "text", "varchar", "char", "string" -> "text"
            "decimal", "number", "numeric", "int", "integer", "float", "double", "bigint", "smallint" -> "numeric"
            else -> "other"
        }
    }

    private fun constraintFamily(tc: Vocabulary.TypeConstraint): String =
        when (tc) {
            Vocabulary.TypeConstraint.Date -> "date"
            Vocabulary.TypeConstraint.Text -> "text"
            Vocabulary.TypeConstraint.Numeric -> "numeric"
        }

    private fun lastSeg(path: String): String = path.substringAfterLast('.')

    private fun didYouMean(s: String?): String = if (s != null) "; did you mean '$s'?" else ""

    private fun typeName(dt: DataType?): String = dt?.name ?: "<none>"

    private fun isAttributeOnlyKey(key: String): Boolean {
        if (Vocabulary.ATTRIBUTE_ROLES.values.any { spec -> spec.extraKeys.any { it.key == key } }) return true
        return key == "code_format" || key == "period" || key == "currency"
    }

    /** Analyse every `semantics` block in [definitions]. */
    fun analyzeSemantics(definitions: List<Definition>): SemanticsAnalysis {
        val diagnostics = mutableListOf<SemanticsDiagnostic>()
        val resolved = LinkedHashMap<SourceLocation, ResolvedSemantics>()

        fun emit(
            code: DiagnosticCode,
            source: SourceLocation,
            message: String,
            suggestion: String? = null,
        ) {
            diagnostics += SemanticsDiagnostic(code, message, source, suggestion)
        }

        // Document-local index of declared entity/table kinds (raw), for `period:`.
        val localKinds = LinkedHashMap<String, String>()
        for (def in definitions) {
            val block = semanticsOf(def)
            if ((def is EntityDef || def is TableDef) && block != null) {
                strOf(block.entries["kind"])?.let { localKinds[def.name] = it }
            }
        }

        // ---- entity/table block shape ----
        fun validateEntityBlock(block: SemanticsBlock): Pair<String?, Boolean> {
            var clean = true
            for (dup in block.duplicateProperties) {
                emit(DiagnosticCode.SemDuplicateKey, block.source, "duplicate semantics key '$dup'")
                clean = false
            }
            var kind: String? = null
            for ((key, value) in block.entries) {
                if (key == "kind") {
                    val vs = strOf(value)
                    if (vs != null && Vocabulary.ENTITY_KINDS.contains(vs)) {
                        kind = vs
                    } else {
                        val s = if (vs != null) Suggest.nearestMatch(vs, Vocabulary.ENTITY_KINDS) else null
                        emit(
                            DiagnosticCode.SemUnknownKind,
                            block.source,
                            "unknown entity/table kind '${value.display()}'${didYouMean(s)}",
                            s,
                        )
                        clean = false
                    }
                } else if (key == "role" || Vocabulary.ALL_ROLES.contains(value.display()) || isAttributeOnlyKey(key)) {
                    emit(
                        DiagnosticCode.SemMisplacedKeyword,
                        block.source,
                        "'$key' is an attribute/column key; entity/table blocks carry only 'kind'",
                    )
                    clean = false
                } else {
                    val s = Suggest.nearestMatch(key, Vocabulary.ALL_ENTITY_KEYS)
                    emit(DiagnosticCode.SemUnknownKey, block.source, "unknown semantics key '$key'${didYouMean(s)}", s)
                    clean = false
                }
            }
            return kind to clean
        }

        // period: resolution — document-local kind index only.
        fun resolvePeriodRef(
            path: String,
            source: SourceLocation,
        ): Boolean {
            val name = lastSeg(path)
            val localKind = localKinds[name]
            if (localKind != null) {
                if (localKind == "period_table") return true
                emit(
                    DiagnosticCode.SemBadPeriodRef,
                    source,
                    "period: '$path' refers to '$name', which is not a 'period_table' kind",
                )
                return false
            }
            emit(DiagnosticCode.SemBadPeriodRef, source, "period: '$path' does not resolve to any entity/table")
            return false
        }

        // currency: resolution — a sibling member with role currency_code.
        fun resolveCurrencyRef(
            path: String,
            siblings: List<Definition>,
            source: SourceLocation,
        ): Boolean {
            val name = lastSeg(path)
            val sib = siblings.firstOrNull { it.name == name }
            if (sib == null) {
                emit(
                    DiagnosticCode.SemBadCurrencyRef,
                    source,
                    "currency: '$path' does not resolve to a sibling attribute/column",
                )
                return false
            }
            if (strOf(semanticsOf(sib)?.entries?.get("role")) != "currency_code") {
                emit(
                    DiagnosticCode.SemBadCurrencyRef,
                    source,
                    "currency: '$path' must reference a sibling with role 'currency_code'",
                )
                return false
            }
            return true
        }

        // ---- attribute/column block shape + cross-refs + type ----
        data class AttrParse(
            val role: String?,
            val rawRole: String?,
            val clean: Boolean,
            val resolved: ResolvedAttributeSemantics? = null,
        )

        fun validateAttributeBlock(
            block: SemanticsBlock,
            memberType: DataType?,
            siblings: List<Definition>,
        ): AttrParse {
            var clean = true
            for (dup in block.duplicateProperties) {
                emit(DiagnosticCode.SemDuplicateKey, block.source, "duplicate semantics key '$dup'")
                clean = false
            }

            if (block.entries.containsKey("kind")) {
                emit(
                    DiagnosticCode.SemMisplacedKeyword,
                    block.source,
                    "'kind' is an entity/table key; attribute/column blocks carry 'role'",
                )
                clean = false
            }

            val rawRoleVal = block.entries["role"]
            val rawRole = strOf(rawRoleVal)
            var role: String? = null
            if (rawRole != null && Vocabulary.ATTRIBUTE_ROLES.containsKey(rawRole)) {
                role = rawRole
            } else if (rawRoleVal != null) {
                val s = if (rawRole != null) Suggest.nearestMatch(rawRole, Vocabulary.ALL_ROLES) else null
                emit(
                    DiagnosticCode.SemUnknownRole,
                    block.source,
                    "unknown semantics role '${rawRoleVal.display()}'${didYouMean(s)}",
                    s,
                )
                clean = false
            }

            val spec = role?.let { Vocabulary.ATTRIBUTE_ROLES[it] }
            val allowed = mutableSetOf("role")
            spec?.extraKeys?.forEach { allowed += it.key }
            for (key in block.entries.keys) {
                if (key == "kind") continue
                if (allowed.contains(key)) continue
                if (role != null) {
                    val s = Suggest.nearestMatch(key, allowed.toList())
                    emit(
                        DiagnosticCode.SemUnknownKey,
                        block.source,
                        "key '$key' is not valid for role '$role'${didYouMean(s)}",
                        s,
                    )
                    clean = false
                }
            }

            if (role != null && spec?.typeConstraint != null) {
                val fam = typeFamilyOf(memberType)
                val want = constraintFamily(spec.typeConstraint)
                if (fam != null && fam != want) {
                    emit(
                        DiagnosticCode.SemTypeConstraint,
                        block.source,
                        "role '$role' requires a $want type, but the declared type is '${typeName(memberType)}'",
                    )
                    clean = false
                }
            }

            var periodRef: SymbolRef? = null
            var currencyRef: SymbolRef? = null
            if (role != null) {
                val periodVal = block.entries["period"]
                if (periodVal != null && spec?.extraKeys?.any { it.key == "period" } == true) {
                    if (resolvePeriodRef(periodVal.display(), block.source)) {
                        periodRef = SymbolRef(periodVal.display())
                    } else {
                        clean = false
                    }
                }
                val currencyVal = block.entries["currency"]
                if (currencyVal != null && spec?.extraKeys?.any { it.key == "currency" } == true) {
                    if (resolveCurrencyRef(currencyVal.display(), siblings, block.source)) {
                        currencyRef = SymbolRef(currencyVal.display())
                    } else {
                        clean = false
                    }
                }
            }

            if (role == null || !clean) return AttrParse(role, rawRole, clean)
            val codeFormat =
                if (role == "period_code") strOf(block.entries["code_format"]) ?: "yyyyMM" else null
            return AttrParse(
                role = role,
                rawRole = role,
                clean = true,
                resolved = ResolvedAttributeSemantics(role, periodRef, currencyRef, codeFormat),
            )
        }

        data class Member(
            val name: String,
            val role: String?,
            val block: SemanticsBlock,
        )

        fun aggregate(
            ownerName: String,
            ownerSource: SourceLocation,
            ownerKind: String?,
            ownerClean: Boolean,
            members: List<Member>,
        ) {
            fun roleCount(r: String): Int = members.count { it.role == r }

            if (roleCount("event_date") > 1) {
                emit(
                    DiagnosticCode.SemMultipleEventDate,
                    ownerSource,
                    "entity/table '$ownerName' has more than one 'event_date' — exactly one is the default query date",
                )
            }

            val hasLat = roleCount("geo_lat") > 0
            val hasLon = roleCount("geo_lon") > 0
            if (hasLat != hasLon) {
                emit(
                    DiagnosticCode.SemGeoPair,
                    ownerSource,
                    "'$ownerName' has ${if (hasLat) "geo_lat without geo_lon" else "geo_lon without geo_lat"} — the pair is required together",
                )
            }

            val hasFrom = roleCount("valid_from") > 0
            val hasTo = roleCount("valid_to") > 0
            if (hasFrom != hasTo) {
                emit(
                    DiagnosticCode.SemValidPair,
                    ownerSource,
                    "'$ownerName' has ${if (hasFrom) "valid_from without valid_to" else "valid_to without valid_from"} — the validity pair is both-or-neither",
                )
            }

            if (ownerKind == null || !ownerClean) return

            if (ownerKind == "poi") {
                val point = roleCount("geo_point")
                val pair = if (hasLat && hasLon) 1 else 0
                if (!((point == 1 && pair == 0) || (point == 0 && pair == 1))) {
                    emit(
                        DiagnosticCode.SemGeoPair,
                        ownerSource,
                        "poi '$ownerName' must have exactly one 'geo_point' XOR one 'geo_lat' + one 'geo_lon'",
                    )
                }
            } else {
                for (clause in Vocabulary.KIND_COMPLETENESS[ownerKind] ?: emptyList()) {
                    val n = roleCount(clause.role)
                    if (n != clause.count) {
                        emit(
                            DiagnosticCode.SemCompleteness,
                            ownerSource,
                            "$ownerKind '$ownerName' requires exactly ${clause.count} '${clause.role}' (found $n)",
                        )
                    }
                }
            }
        }

        fun validateOwner(
            ownerName: String,
            ownerSource: SourceLocation,
            ownerBlock: SemanticsBlock?,
            rawMembers: List<Definition>,
        ) {
            var ownerKind: String? = null
            var ownerClean = true
            if (ownerBlock != null) {
                val (k, clean) = validateEntityBlock(ownerBlock)
                ownerKind = k
                ownerClean = clean
                if (clean && k != null) resolved[ownerBlock.source] = ResolvedEntitySemantics(k)
            }

            val members = mutableListOf<Member>()
            for (m in rawMembers) {
                val block = semanticsOf(m) ?: continue
                val parsed = validateAttributeBlock(block, typeOf(m), rawMembers)
                members += Member(m.name, parsed.role, block)
                if (parsed.clean && parsed.resolved != null) resolved[block.source] = parsed.resolved
            }

            aggregate(ownerName, ownerSource, ownerKind, ownerClean, members)
        }

        for (def in definitions) {
            when (def) {
                is EntityDef -> validateOwner(def.name, def.source, def.semantics, def.attributes)
                is TableDef -> validateOwner(def.name, def.source, def.semantics, def.columns)
                is AttributeDef -> {
                    def.semantics?.let { block ->
                        val parsed = validateAttributeBlock(block, def.type, emptyList())
                        if (parsed.clean && parsed.resolved != null) resolved[block.source] = parsed.resolved
                    }
                }
                is ColumnDef -> {
                    def.semantics?.let { block ->
                        val parsed = validateAttributeBlock(block, def.type, emptyList())
                        if (parsed.clean && parsed.resolved != null) resolved[block.source] = parsed.resolved
                    }
                }
                else -> {}
            }
        }

        return SemanticsAnalysis(diagnostics, resolved)
    }

    private fun semanticsOf(def: Definition): SemanticsBlock? =
        when (def) {
            is EntityDef -> def.semantics
            is TableDef -> def.semantics
            is AttributeDef -> def.semantics
            is ColumnDef -> def.semantics
            else -> null
        }

    private fun typeOf(def: Definition): DataType? =
        when (def) {
            is AttributeDef -> def.type
            is ColumnDef -> def.type
            else -> null
        }
}
