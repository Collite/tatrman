// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import org.tatrman.ttr.semantics.md.AggKind
import org.tatrman.ttr.semantics.md.MdCalcCatalog
import org.tatrman.ttr.semantics.md.MdModel

/** One candidate slot a component could fill (R5). A component may have several candidates at once. */
sealed interface SlotCandidate {
    /** An aggregation token (the closed v1 set). */
    data class Agg(
        val kind: AggKind,
    ) : SlotCandidate

    /** A cubelet name. */
    data class Cubelet(
        val name: String,
    ) : SlotCandidate

    /** A measure name. */
    data class Measure(
        val name: String,
    ) : SlotCandidate

    /** A dimension attribute / hierarchy level, identified by `Dimension.attribute`. */
    data class Attribute(
        val dimension: String,
        val attribute: String,
    ) : SlotCandidate {
        val qname: String get() = "$dimension.$attribute"
    }

    /** A calc-catalog / relative-time token (R12; full asof-anchored handling is S2-C). */
    data class Calc(
        val token: String,
    ) : SlotCandidate

    /** A member of a domain, reached member → domain → attribute → dimension via the snapshot (R5f). */
    data class Member(
        val text: String,
        val domain: String,
        val dimension: String,
        val attribute: String,
    ) : SlotCandidate {
        val qname: String get() = "$dimension.$attribute"
    }
}

/**
 * Classify one path component into its candidate slots (R5). Pure over the model and an optional
 * member [snapshot]; the snapshot is consulted **only when present** — disconnected candidacy is
 * structural (agg/cubelet/measure/attribute/calc), member candidacy needs the index (R13 enforcement
 * is S2-C). The ordering in R5 defines *candidacy, not priority*: every matching slot is returned,
 * so a name that is both a measure and a member yields both — the search (S2-B) disambiguates.
 *
 * Selectors that are not scalar tokens ([PathComponent.SetLit]/[PathComponent.RangeLit]/
 * [PathComponent.Star]/[PathComponent.Pair]) are bound by [PairBinder], not classified here; they
 * return an empty candidate set.
 */
object TokenClassifier {
    /** The closed v1 aggregation vocabulary (R5a) — a subset of what `aggKindOf` would accept. */
    private val AGG_TOKENS: Map<String, AggKind> =
        mapOf(
            "sum" to AggKind.SUM,
            "avg" to AggKind.AVG,
            "min" to AggKind.MIN,
            "max" to AggKind.MAX,
            "count" to AggKind.COUNT,
        )

    /**
     * Provisional evaluation-relative calc tokens (R12). S2-A recognises them so a `lastMonth`-style
     * token classifies as a calc slot; the full vocabulary + `asof` anchoring is S2-C's job.
     */
    private val RELATIVE_CALC_TOKENS: Set<String> =
        setOf("lastMonth", "thisMonth", "lastYear", "thisYear", "lastQuarter", "thisQuarter")

    /** Domain `type`s whose members INT components may bind to (R5f: numeric/temporal only). */
    private val NUMERIC_TEMPORAL_TYPES: Set<String> =
        setOf("int", "integer", "date", "timestamp", "instant", "datetime")

    fun classify(
        component: PathComponent,
        model: MdModel,
        snapshot: MemberSnapshot?,
    ): Set<SlotCandidate> =
        when (component) {
            is PathComponent.Ident -> classifyText(component.text, isInt = false, quoted = false, model, snapshot)
            is PathComponent.IntLit -> classifyText(component.text, isInt = true, quoted = false, model, snapshot)
            is PathComponent.Quoted -> classifyText(component.text, isInt = false, quoted = true, model, snapshot)
            is PathComponent.SetLit,
            is PathComponent.RangeLit,
            PathComponent.Star,
            is PathComponent.Pair,
            -> emptySet()
        }

    private fun classifyText(
        text: String,
        isInt: Boolean,
        quoted: Boolean,
        model: MdModel,
        snapshot: MemberSnapshot?,
    ): Set<SlotCandidate> {
        val out = mutableSetOf<SlotCandidate>()
        // A quoted token is a member only — quoting exists precisely to force member reading (§6).
        if (!quoted && !isInt) {
            AGG_TOKENS[text]?.let { out += SlotCandidate.Agg(it) } // (a)
            if (model.cubelets.containsKey(text)) out += SlotCandidate.Cubelet(text) // (b)
            if (model.measures.containsKey(text)) out += SlotCandidate.Measure(text) // (c)
            for (attr in model.attributes.values) { // (d)
                if (attr.name == text) out += SlotCandidate.Attribute(attr.dimension, attr.name)
            }
            if (MdCalcCatalog.byName(text) != null || text in RELATIVE_CALC_TOKENS) { // (e)
                out += SlotCandidate.Calc(text)
            }
        }
        out += memberCandidates(text, isInt, model, snapshot) // (f)
        return out
    }

    /** Member candidacy (R5f): text present in a published domain's index, mapped back to attributes. */
    private fun memberCandidates(
        text: String,
        isInt: Boolean,
        model: MdModel,
        snapshot: MemberSnapshot?,
    ): Set<SlotCandidate> {
        if (snapshot == null) return emptySet() // disconnected: no member index (R13 handled in S2-C)
        val out = mutableSetOf<SlotCandidate>()
        for (attr in model.attributes.values) {
            val domain = model.underlyingDomain(attr.domainRef ?: continue) ?: continue
            if (isInt && (model.domains[domain]?.type?.lowercase() !in NUMERIC_TEMPORAL_TYPES)) continue
            if (domain !in snapshot.domains()) continue
            if (snapshot.members(domain)?.contains(text) == true) {
                out += SlotCandidate.Member(text, domain, attr.dimension, attr.name)
            }
        }
        return out
    }
}
